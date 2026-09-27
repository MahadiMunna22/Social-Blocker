package ch.puc.blocker.receiver

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.puc.blocker.BlockerApp
import ch.puc.blocker.Day
import ch.puc.blocker.service.BlockerService
import kotlinx.coroutines.launch

object Alarms {
    /** Exact RTC alarm at the next local midnight; falls back to inexact if exact alarms are denied. */
    @SuppressLint("MissingPermission") // USE_EXACT_ALARM declared; also guarded by canScheduleExactAlarms()
    fun scheduleMidnight(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, MidnightReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val at = Day.startOfTomorrowMs() + 1_000
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}

/** Counters are keyed by date, so "reset" = prune old rows + tell the service to drop its cache. */
class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as BlockerApp
        val pending = goAsync()
        app.scope.launch {
            // Keep a month of rows for the history chart.
            try { app.db.dao().pruneUsage(Day.daysAgo(30)) } finally { pending.finish() }
        }
        BlockerService.start(context, BlockerService.ACTION_REFRESH)
        Alarms.scheduleMidnight(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            BlockerService.start(context)
            Alarms.scheduleMidnight(context)
        }
    }
}
