package ch.puc.blocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import ch.puc.blocker.R
import ch.puc.blocker.ui.Perms

/**
 * Tamper guard. A normal app can't refuse a force-stop, so this keeps the user away from the buttons:
 * any system screen that names Social Blocker next to a way to stop, uninstall, clear or switch it off
 * is backed out of right away. Paused for a few minutes after the unlock, so settings can still be changed.
 */
class GuardService : AccessibilityService() {

    companion object {
        /** System UIs that can stop, uninstall or disable an app. */
        private val GUARDED = setOf(
            "com.android.settings", "com.google.android.packageinstaller", "com.android.packageinstaller",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller", "com.android.systemui",
        )
        // ponytail: English wording only; add translations if the phone language changes.
        private val DANGER = listOf(
            "Force stop", "Uninstall", "Clear storage", "Clear data", "Use Social Blocker",
            "Permit usage access", "Allow display over other apps", // VPN screens stay free: the filter is optional
        )
        const val PAUSE_MS = 5 * 60_000L

        // ponytail: view IDs from github.com/atick-faisal/Shorts-Blocker; they break when the apps redesign.
        /** Package → view IDs that only exist while short-form video is on screen. */
        private val SHORT_FORM = mapOf(
            "com.google.android.youtube" to listOf("com.google.android.youtube:id/reel_progress_bar"),
            "app.revanced.android.youtube" to listOf("app.revanced.android.youtube:id/reel_progress_bar"),
            "com.instagram.android" to listOf("com.instagram.android:id/clips_tab"),
        )

        /** Mirrors the "block Shorts and Reels" setting; set by BlockerService. */
        @Volatile var blockShortForm = false

        /** Set by the app after the unlock. In-memory only: a restart turns the guard back on. */
        @Volatile var pausedUntil = 0L

        fun enabled(ctx: Context): Boolean =
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.contains("${ctx.packageName}/") == true
    }

    private var lastCheck = 0L

    private var lastShortCheck = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) BlockerService.wake.trySend(Unit)
        if (blockShortForm) SHORT_FORM[pkg]?.let { ids -> backOutOfShortForm(ids); return }
        if (pkg !in GUARDED || System.currentTimeMillis() < pausedUntil) return
        val now = System.currentTimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - lastCheck < 300) return
        lastCheck = now
        val root = rootInActiveWindow ?: return
        fun has(text: String) = root.findAccessibilityNodeInfosByText(text).isNotEmpty()
        if (!has(getString(R.string.app_name))) return
        val danger = if (event.packageName.toString() == "com.android.systemui") {
            // Quick settings "Active apps" dialog has a Stop button per app; the shade's "2 active apps" footer doesn't.
            has("Active apps") && root.findAccessibilityNodeInfosByText("Stop").any { it.text?.toString() == "Stop" }
        } else DANGER.any(::has)
        // Missing permissions must stay grantable, so the guard only acts once the blocker is fully set up.
        if (!danger || !Perms.requiredGranted(this)) return
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /** Instagram's clips_tab is always there; it only means Reels when it's the selected tab. */
    private fun backOutOfShortForm(ids: List<String>) {
        val now = System.currentTimeMillis()
        if (now - lastShortCheck < 500) return
        lastShortCheck = now
        val root = rootInActiveWindow ?: return
        val watching = ids.any { id ->
            root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser && (!id.endsWith("clips_tab") || it.isSelected) }
        }
        if (watching) performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onInterrupt() {}
}
