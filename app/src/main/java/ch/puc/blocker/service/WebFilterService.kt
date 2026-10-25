package ch.puc.blocker.service

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import ch.puc.blocker.domain.DnsPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Adult content filter: a local VPN that only carries DNS. Every lookup is forwarded to Cloudflare for
 * Families (or AdGuard DNS Family with ad blocking on), which answers 0.0.0.0 for blocked sites; all other traffic skips the tunnel.
 */
class WebFilterService : VpnService() {

    companion object {
        private const val TAG = "WebFilter"
        private const val TUN_ADDRESS = "10.111.222.2"
        private const val FAKE_DNS = "10.111.222.1"
        // ponytail: filtering is the provider's list; bundle our own blocklist if it misses too much.
        private val ADULT_ONLY = listOf("1.1.1.3", "1.0.0.3")         // Cloudflare for Families
        private val ADULT_AND_ADS = listOf("94.140.14.15", "94.140.15.16") // AdGuard DNS Family: adult, ads, trackers
        /** Read per lookup, so switching it needs no reconnect. */
        @Volatile private var upstream = ADULT_ONLY

        @Volatile var running = false
            private set
        /** Packages the running tunnel carries; empty means every app. */
        @Volatile private var routed: Set<String> = emptySet()
        private const val EXTRA_APPS = "apps"

        /**
         * The Private DNS server in strict mode (e.g. Kahf's), or null. Strict mode sends every lookup there over
         * encrypted DNS, which skips this filter and fails inside its VPN, leaving the phone with "no internet".
         */
        @Suppress("DEPRECATION") // allNetworks: the VPN hides the physical network from activeNetwork
        fun privateDnsServer(context: Context): String? {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            return cm.allNetworks.firstNotNullOfOrNull { cm.getLinkProperties(it)?.privateDnsServerName }
        }

        /**
         * Starts, stops or re-routes the filter to match the setting. Only [apps] go through it (all apps when empty),
         * so apps that can't work with it (e.g. Duolingo) can stay outside. Needs VPN consent first.
         */
        fun sync(context: Context, on: Boolean, apps: Set<String>, blockAds: Boolean) {
            upstream = if (blockAds) ADULT_AND_ADS else ADULT_ONLY
            val intent = Intent(context, WebFilterService::class.java)
            if (!on || privateDnsServer(context) != null) { if (running) context.stopService(intent); return }
            if ((running && apps == routed) || prepare(context) != null) return
            runCatching { context.startService(intent.putExtra(EXTRA_APPS, apps.toTypedArray())) }
                .onFailure { Log.w(TAG, "Could not start filter", it) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apps = intent?.getStringArrayExtra(EXTRA_APPS)?.toSet() ?: routed
        if (tun == null || apps != routed) {
            // A new tunnel replaces the old one; the old pump stops once its descriptor is closed.
            val old = tun
            tun = Builder()
                .setSession("Adult content filter")
                .addAddress(TUN_ADDRESS, 32)
                .addDnsServer(FAKE_DNS)
                .addRoute(FAKE_DNS, 32) // only DNS enters the tunnel
                .apply { apps.forEach { runCatching { addAllowedApplication(it) } } } // uninstalled ones are skipped
                .establish()
            runCatching { old?.close() }
            routed = apps
            tun?.let { fd -> running = true; scope.launch { pump(fd) } } ?: stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        routed = emptySet()
        scope.cancel()
        runCatching { tun?.close() }
        tun = null
        super.onDestroy()
    }

    /** Reads DNS queries from the tunnel and answers each one from upstream. Polls so it can stop within a second. */
    private suspend fun pump(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val poll = StructPollfd().apply { this.fd = fd.fileDescriptor; events = OsConstants.POLLIN.toShort() }
        val buf = ByteArray(32_767)
        while (scope.isActive) {
            val ready = runCatching { Os.poll(arrayOf(poll), 1_000) }.getOrElse { if (scope.isActive) continue else return }
            if (ready <= 0) continue
            val n = runCatching { input.read(buf) }.getOrElse { return } // tunnel closed
            if (n <= 0) continue
            val packet = buf.copyOf(n)
            val query = DnsPacket.query(packet) ?: continue
            scope.launch {
                val answer = forward(query) ?: return@launch
                runCatching { synchronized(output) { output.write(DnsPacket.reply(packet, answer)) } }
            }
        }
    }

    private fun forward(query: ByteArray): ByteArray? {
        for (server in upstream) runCatching {
            DatagramSocket().use { s ->
                protect(s) // otherwise the lookup would loop back into the tunnel
                s.soTimeout = 3_000
                s.send(DatagramPacket(query, query.size, InetAddress.getByName(server), 53))
                val r = DatagramPacket(ByteArray(4_096), 4_096)
                s.receive(r)
                return r.data.copyOf(r.length)
            }
        }.onFailure { Log.w(TAG, "DNS via $server failed: $it") }
        return null
    }
}
