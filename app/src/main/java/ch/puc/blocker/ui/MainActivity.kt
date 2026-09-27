package ch.puc.blocker.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import ch.puc.blocker.service.BlockerService

class MainActivity : ComponentActivity() {
    private val vm: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            // Re-read permission state every time we come back from system settings.
            var permTick by rememberSaveable { mutableIntStateOf(0) }
            LifecycleResumeEffect(Unit) { permTick++; onPauseOrDispose { } }
            var showSetup by rememberSaveable { mutableIntStateOf(-1) } // -1 = decide automatically
            val ready = permTick >= 0 && Perms.requiredGranted(ctx)
            val setup = if (showSetup == -1) !ready else showSetup == 1

            BlockerTheme {
                AnimatedContent(targetState = when {
                    vm.unlockStep != null -> 0
                    setup -> 1
                    else -> 2
                }, label = "screen") { screen ->
                    when (screen) {
                        0 -> UnlockFlow(vm)
                        1 -> {
                            BackHandler { showSetup = 0 } // back from setup returns to Today
                            SetupScreen(permTick, onDone = { showSetup = 0 })
                        }
                        else -> MainScaffold(vm, openSetup = { showSetup = 1 })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Perms.requiredGranted(this)) BlockerService.start(this)
    }

    /** Leaving the app ends the edit window; the challenge is required again next time. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.lock()
    }
}

object Perms {
    fun usageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    fun overlay(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun notifications(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun batteryExempt(ctx: Context) =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun requiredGranted(ctx: Context) = usageAccess(ctx) && overlay(ctx)

    private fun pkgUri(ctx: Context) = "package:${ctx.packageName}".toUri()

    /** Tries each intent in order; OEM settings apps don't all support the package-specific pages. */
    private fun open(ctx: Context, vararg intents: Intent) {
        for (i in intents) {
            try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: ActivityNotFoundException) {}
        }
    }

    fun openUsageAccess(ctx: Context) = open(ctx,
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkgUri(ctx)),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        appDetails(ctx))

    fun openOverlay(ctx: Context) = open(ctx,
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri(ctx)),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        appDetails(ctx))

    @SuppressLint("BatteryLife") // personal-use app whose core function needs a long-running service
    fun openBattery(ctx: Context) = open(ctx,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(ctx)),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    fun openAppInfo(ctx: Context) = open(ctx, appDetails(ctx))

    private fun appDetails(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx))

    /** Fallback when the device refuses to let the toggles be switched on. */
    fun adbCommands(ctx: Context) = listOf(
        "adb shell appops set ${ctx.packageName} GET_USAGE_STATS allow",
        "adb shell appops set ${ctx.packageName} SYSTEM_ALERT_WINDOW allow",
        "adb shell appops set ${ctx.packageName} ACCESS_RESTRICTED_SETTINGS allow",
    ).joinToString("\n")
}
