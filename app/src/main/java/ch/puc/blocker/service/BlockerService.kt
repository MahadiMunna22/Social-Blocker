package ch.puc.blocker.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import ch.puc.blocker.BlockerApp
import ch.puc.blocker.Day
import ch.puc.blocker.R
import ch.puc.blocker.data.DailyUsage
import ch.puc.blocker.data.DayState
import ch.puc.blocker.data.GlobalSettings
import ch.puc.blocker.data.TaskItem
import ch.puc.blocker.data.TrackedApp
import ch.puc.blocker.domain.Decision
import ch.puc.blocker.domain.FgEvent
import ch.puc.blocker.domain.Remaining
import ch.puc.blocker.domain.RuleEngine
import ch.puc.blocker.domain.StatusText
import ch.puc.blocker.domain.UsageAggregator
import ch.puc.blocker.domain.UsageState
import ch.puc.blocker.overlay.BlockOverlay
import ch.puc.blocker.overlay.TimerOverlay
import ch.puc.blocker.overlay.goHome
import ch.puc.blocker.ruleConfig
import ch.puc.blocker.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Polls UsageStatsManager every second while the screen is on, applies the rules, closes blocked
 * apps and keeps the ongoing notification up to date. It sleeps while the screen is off.
 */
class BlockerService : LifecycleService() {

    companion object {
        /** Day changed (midnight): save counters, then reload them for the new date. */
        const val ACTION_REFRESH = "ch.puc.blocker.REFRESH"
        /** Only re-post the ongoing notification, e.g. to clear a notification reply spinner. */
        const val ACTION_REFRESH_NOTIFICATION = "ch.puc.blocker.REFRESH_NOTIFICATION"
        private const val CHANNEL = "blocker"
        private const val CHANNEL_UNLOCK = "unlock"
        private const val ID_STATUS = 1
        private const val ID_UNLOCK = 2
        private const val TAG = "BlockerService"
        private const val FLUSH_MS = 15_000L
        private const val SYSTEM_SYNC_MS = 60_000L
        /** Don't re-send the same app home while the launcher is still coming up. */
        private const val ENFORCE_RETRY_MS = 3_000L
        /** A normal app switch pauses then stops within a moment; paused this long without a stop means PiP. */
        private const val PIP_SETTLE_MS = 1_500L

        /** True while the service is alive; shown in the UI as a health indicator. */
        @Volatile var running = false
            private set

        fun channels() = listOf(
            NotificationChannel(CHANNEL, "Blocker", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_UNLOCK, "Unlock ready", NotificationManager.IMPORTANCE_DEFAULT),
        )

        fun start(context: Context, action: String? = null) {
            try {
                context.startForegroundService(Intent(context, BlockerService::class.java).setAction(action))
            } catch (e: Exception) { // e.g. ForegroundServiceStartNotAllowedException
                Log.w(TAG, "Could not start service", e)
            }
        }
    }

    private val dao by lazy { (application as BlockerApp).db.dao() }
    private val usm by lazy { getSystemService(UsageStatsManager::class.java) }
    private val nm by lazy { getSystemService(NotificationManager::class.java) }
    private lateinit var overlay: BlockOverlay
    private lateinit var timer: TimerOverlay

    // Kept fresh by Room flows, so edits apply without restarting.
    @Volatile private var apps: Map<String, TrackedApp> = emptyMap()
    @Volatile private var settings: GlobalSettings? = null
    /** Open tasks, for the notification and the periodic reminder. */
    @Volatile private var openTasks: List<TaskItem> = emptyList()
    private var lastReminder = System.currentTimeMillis()
    /** Package → end of its redeemed task reward. */
    @Volatile private var rewards: Map<String, Long> = emptyMap()

    /** Screen on/off, pushed by [screenReceiver] so the loop can sleep instead of polling. */
    private val interactive = MutableStateFlow(true)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            interactive.value = intent.action != Intent.ACTION_SCREEN_OFF
        }
    }

    private var loop: Job? = null
    private var day = ""
    private var reloadDay = false
    private var firstUnlock: Long? = null
    private val usage = mutableMapOf<String, UsageState>()
    /** Packages whose [usage] changed since the last write to Room. */
    private val dirty = mutableSetOf<String>()
    private var lastFlush = 0L
    private var fgPackage: String? = null
    /** Package → when it paused without stopping yet (still visible, e.g. picture-in-picture). */
    private val pausedVisible = mutableMapOf<String, Long>()
    private var lastEventQuery = 0L
    private var lastTick = 0L
    private var lastSystemSync = 0L

    /** Block card currently on screen (label to block); stays over the home screen until dismissed. */
    private var shownBlock: Pair<String, Decision.Block>? = null
    private var enforcedPkg: String? = null
    private var enforcedAt = 0L

    private var lastStatus = ""
    private var lastNotification: Notification? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        overlay = BlockOverlay(this) { shownBlock = null }
        timer = TimerOverlay(this)
        startInForeground()
        interactive.value = getSystemService(PowerManager::class.java).isInteractive
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, RECEIVER_NOT_EXPORTED)
        lifecycleScope.launch { dao.observeApps().collect { list -> apps = list.filter { it.enabled }.associateBy { it.packageName }; lastSystemSync = 0 } }
        lifecycleScope.launch { dao.observeSettings().collect { settings = it } }
        lifecycleScope.launch {
            dao.observeTasks().collect { list -> openTasks = list.filter { it.doneMs == 0L } }
        }
        lifecycleScope.launch {
            dao.observeRewards().collect { list ->
                rewards = list.groupBy { it.packageName }.mapValues { (_, v) -> v.maxOf { it.untilMs } }
            }
        }
        // One timed wake-up per pending wait instead of checking it every tick.
        lifecycleScope.launch {
            dao.observeWait().collectLatest { w ->
                if (w == null) { nm.cancel(ID_UNLOCK); return@collectLatest }
                delay((w.readyAtMs - System.currentTimeMillis()).coerceAtLeast(0))
                notifyUnlockReady()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Every startForegroundService() must be answered with startForeground(), even when already running.
        startInForeground()
        when (intent?.action) {
            ACTION_REFRESH -> { reloadDay = true; lastStatus = "" }
            ACTION_REFRESH_NOTIFICATION -> lastStatus = ""
        }
        if (loop == null) loop = lifecycleScope.launch {
            while (isActive) {
                if (!interactive.value) sleepUntilScreenOn()
                runCatching { tick() }.onFailure { Log.e(TAG, "tick failed", it) }
                delay(1_000)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { unregisterReceiver(screenReceiver) }
        hideAll()
        // lifecycleScope is cancelled from here on, so hand the last write to the app scope.
        val pending = takeDirty()
        if (pending.isNotEmpty()) (application as BlockerApp).scope.launch { dao.upsertUsage(pending) }
        super.onDestroy()
    }

    private suspend fun sleepUntilScreenOn() {
        hideAll()
        shownBlock = null
        flush()
        interactive.first { it }
        lastTick = 0 // no time passes in a tracked app while the screen is off
        lastSystemSync = 0 // catch up on anything used while we slept
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        val today = Day.today()
        if (today != day || reloadDay) {
            flush() // saved under the old date
            reloadDay = false
            loadDay(today)
        }

        val delta = if (lastTick == 0L) 0 else (now - lastTick).coerceIn(0, 2_000)
        lastTick = now
        // Every 3 h between 9:00 and 21:59, nudge about open tasks while the phone is in use.
        if (openTasks.isNotEmpty() && now - lastReminder >= 3 * 60 * 60_000L && LocalTime.now().hour in 9..21) {
            lastReminder = now
            TaskNotifications.showReminder(this, openTasks)
        }

        val previousFg = fgPackage
        updateForeground(now)
        if (fgPackage != previousFg) flush()

        val s = settings ?: return
        updateFirstUnlock(today, now, s)
        val app = fgPackage?.let { apps[it] }
        val pip = pictureInPictureApp(now)?.takeIf { it != app }
        // Full-day scan only when it can matter: on (re)start, after screen-on and while a tracked app is open.
        if (lastSystemSync == 0L || ((app != null || pip != null) && now - lastSystemSync >= SYSTEM_SYNC_MS)) syncSystemUsage(now)

        if (!s.blockingEnabled) {
            hideAll()
            shownBlock = null
            status("Blocking paused", now, s, inUse = null)
            return
        }

        if (app == null && pip == null) timer.hide()
        if (app == null && pip == null) enforcedPkg = null // reopening it right away gets closed again
        // A picture-in-picture window is still in use: it counts, shows the timer and gets closed on timeout.
        val fgLine = app?.let { apply(it, s, now, delta, pip = false, showTimer = true) }
        val pipLine = pip?.let { apply(it, s, now, delta, pip = true, showTimer = app == null) }
        val inUse = fgLine ?: pipLine

        // Keep the block card (now over the home screen) counting down until dismissed or expired.
        shownBlock?.let { (label, block) -> if (now >= block.untilMs) { shownBlock = null; overlay.hide() } else overlay.show(label, block) }
        if (now - lastFlush >= FLUSH_MS) flush()
        status(null, now, s, inUse)
    }

    /**
     * Applies the rules to one tracked app on screen: counts its time, shows its timer or closes it.
     * Returns its line for the notification, or null once it's blocked.
     */
    private suspend fun apply(app: TrackedApp, s: GlobalSettings, now: Long, delta: Long, pip: Boolean, showTimer: Boolean): String? {
        val pkg = app.packageName
        val rewardUntil = rewards[pkg] ?: 0
        if (now < rewardUntil) {
            // An earned task reward lifts every limit for this app; that time isn't counted.
            if (!pip) { shownBlock = null; overlay.hide() }
            if (showTimer) timer.show(app.label, Remaining(rewardUntil - now, dailyLimited = false), reward = true)
            return StatusText.reward(app.label, rewardUntil - now)
        }
        val cfg = ruleConfig(app, s)
        var state = usage[pkg] ?: UsageState()
        var decision = RuleEngine.evaluate(now, firstUnlock, Day.startOfTomorrowMs(), cfg, state)
        if (decision == Decision.Allow) {
            val next = RuleEngine.accrue(state, now, delta, cfg)
            usage[pkg] = next
            dirty += pkg
            if (next.cooldownUntilMs != state.cooldownUntilMs) flush() // a cooldown must survive a restart
            state = next
            decision = RuleEngine.evaluate(now, firstUnlock, Day.startOfTomorrowMs(), cfg, state)
        }
        if (decision is Decision.Block) {
            if (showTimer) timer.hide()
            enforceBlock(app, decision, now, pip)
            return null
        }
        if (!pip) { shownBlock = null; overlay.hide() }
        val r = RuleEngine.remaining(cfg, state)
        if (showTimer) timer.show(app.label, r)
        return StatusText.inUse(app.label, r) + if (pip) " (picture-in-picture)" else ""
    }

    /**
     * Closes a blocked app: show the block card, send the user home, pause its media and kill its process.
     * The card goes up first because Android 15+ only lets an overlay app start activities while its window is visible.
     * A picture-in-picture window is first brought back to full screen, because going home doesn't close it.
     */
    private fun enforceBlock(app: TrackedApp, block: Decision.Block, now: Long, pip: Boolean) {
        shownBlock = app.label to block
        overlay.show(app.label, block)
        if (app.packageName == enforcedPkg && now - enforcedAt < ENFORCE_RETRY_MS) return
        enforcedPkg = app.packageName
        enforcedAt = now
        stopPlayback()
        lifecycleScope.launch {
            if (pip) {
                // Relaunching the app expands its PiP window behind the block card.
                runCatching { packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    .onFailure { Log.w(TAG, "Could not expand picture-in-picture", it) }
                delay(600)
            }
            runCatching { goHome() }.onFailure { Log.w(TAG, "Could not open home screen", it) }
            delay(800) // the app must be in the background before it can be killed
            // Works up to Android 13; from 14 on Android only allows this for our own app, so going home is the close.
            runCatching { getSystemService(ActivityManager::class.java).killBackgroundProcesses(app.packageName) }
        }
    }

    /** Taking audio focus makes well-behaved apps pause their video or audio; we hand it back right away. */
    private fun stopPlayback() {
        val am = getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .build()
        if (am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            lifecycleScope.launch { delay(500); am.abandonAudioFocusRequest(request) }
        }
    }

    private fun hideAll() {
        overlay.hide()
        timer.hide()
    }

    private fun takeDirty(): List<DailyUsage> {
        if (day.isEmpty() || dirty.isEmpty()) return emptyList()
        val rows = dirty.mapNotNull { pkg ->
            usage[pkg]?.let { DailyUsage(day, pkg, it.totalMs, it.sessionMs, it.lastSeenMs, it.cooldownUntilMs) }
        }
        dirty.clear()
        return rows
    }

    /** Writes changed counters in one transaction instead of every second. */
    private suspend fun flush() {
        lastFlush = System.currentTimeMillis()
        val rows = takeDirty()
        if (rows.isNotEmpty()) dao.upsertUsage(rows)
    }

    private suspend fun loadDay(today: String) {
        day = today
        lastSystemSync = 0
        usage.clear()
        dirty.clear()
        dao.usage(today).forEach { usage[it.packageName] = UsageState(it.totalMs, it.sessionMs, it.lastSeenMs, it.cooldownUntilMs) }
        firstUnlock = dao.dayState(today)?.firstUnlockMs
    }

    /**
     * The morning lockout counts from the first unlock after the morning start (default 6:00), not midnight.
     * An unlock stored before that hour (night use, or an older version of the app) is ignored.
     */
    private suspend fun updateFirstUnlock(today: String, now: Long, s: GlobalSettings) {
        val morning = Day.todayAtHourMs(s.morningStartHour)
        if (firstUnlock?.let { it < morning } == true) firstUnlock = null
        if (firstUnlock != null || now < morning) return
        // The earliest unlock since then survives a service restart; if the phone stayed unlocked, it's now.
        val at = firstUnlockSince(morning, now) ?: now
        firstUnlock = at
        dao.upsertDayState(DayState(today, at))
    }

    /**
     * Pulls today's screen time per tracked app from Android's usage events, the data Digital
     * Wellbeing shows, and raises our counter if the system saw more. That covers time used before
     * the app was installed or while the service was stopped.
     */
    private fun syncSystemUsage(now: Long) {
        lastSystemSync = now
        val tracked = apps.keys
        if (tracked.isEmpty()) return
        val events = mutableListOf<FgEvent>()
        val it = usm.queryEvents(Day.startOfTodayMs(), now)
        val e = UsageEvents.Event()
        while (it.getNextEvent(e)) {
            if (e.packageName !in tracked) continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> events += FgEvent(e.packageName, true, e.timeStamp)
                UsageEvents.Event.ACTIVITY_PAUSED -> events += FgEvent(e.packageName, false, e.timeStamp)
            }
        }
        UsageAggregator.foregroundMs(events, Day.startOfTodayMs(), now).forEach { (pkg, systemMs) ->
            val cur = usage[pkg] ?: UsageState()
            if (systemMs > cur.totalMs) {
                usage[pkg] = cur.copy(totalMs = systemMs)
                dirty += pkg
            }
        }
    }

    /** Earliest keyguard dismissal today, so a service restart mid-day keeps the real first unlock. */
    private fun firstUnlockSince(from: Long, to: Long): Long? {
        val events = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        while (events.getNextEvent(e)) if (e.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) return e.timeStamp
        return null
    }

    /**
     * The tracked app showing a picture-in-picture window, if any. PiP activities are paused but not stopped,
     * so a pause with no stop after [PIP_SETTLE_MS] means the app is still on screen.
     * (An app half-covered by another app's dialog looks the same, and counts as in use too.)
     */
    private fun pictureInPictureApp(now: Long): TrackedApp? = pausedVisible.entries
        .firstOrNull { (pkg, pausedAt) -> pkg != fgPackage && now - pausedAt >= PIP_SETTLE_MS && pkg in apps }
        ?.let { apps[it.key] }

    /** Incrementally replays usage events to track the resumed activity's package and paused-but-visible ones. */
    private fun updateForeground(now: Long) {
        val from = if (lastEventQuery == 0L) now - 60 * 60_000 else lastEventQuery
        val events = usm.queryEvents(from, now)
        lastEventQuery = now
        val e = UsageEvents.Event()
        while (events.getNextEvent(e)) when (e.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> { fgPackage = e.packageName; pausedVisible.remove(e.packageName) }
            UsageEvents.Event.ACTIVITY_PAUSED -> { if (e.packageName == fgPackage) fgPackage = null; pausedVisible[e.packageName] = e.timeStamp }
            UsageEvents.Event.ACTIVITY_STOPPED -> pausedVisible.remove(e.packageName)
        }
    }

    /** Apps blocked right now, soonest unblock first. Rewarded apps are free, so they're left out. */
    private fun blockedApps(now: Long, s: GlobalSettings): List<StatusText.Blocked> {
        val endOfDay = Day.startOfTomorrowMs()
        return apps.values.mapNotNull { app ->
            if (now < (rewards[app.packageName] ?: 0)) return@mapNotNull null
            val d = RuleEngine.evaluate(now, firstUnlock, endOfDay, ruleConfig(app, s), usage[app.packageName] ?: UsageState())
            (d as? Decision.Block)?.let { StatusText.Blocked(app.label, it.reason, it.untilMs) }
        }.sortedBy { it.untilMs }
    }

    /**
     * Ongoing notification. Title: the app in use, else which apps are blocked and for how long.
     * Text: the next task. Expanded: every blocked app, then every open task.
     * Only re-posted when the wording changes, so about once a minute.
     */
    private fun status(override: String?, now: Long, s: GlobalSettings, inUse: String?) {
        val blocked = if (s.blockingEnabled) blockedApps(now, s) else emptyList()
        val title = override ?: inUse ?: StatusText.headline(blocked, now)
        val tasks = openTasks.sortedWith(compareBy({ it.dueEpochDay }, { it.createdMs }))
        val text = TaskNotifications.nextLine(tasks) ?: "No open tasks"
        val lines = buildList {
            blocked.forEach { add(StatusText.blockedLine(it, now)) }
            if (tasks.isEmpty()) add("No open tasks. Tap ➕ to add one.")
            else {
                add("${TaskNotifications.headline(tasks)}:")
                tasks.forEach { add(TaskNotifications.line(it)) }
            }
        }
        val key = "$title\n$text\n${lines.joinToString("\n")}"
        if (key == lastStatus) return
        lastStatus = key
        nm.notify(ID_STATUS, notification(title, text, lines))
    }

    private fun notification(title: String, text: String, lines: List<String> = emptyList()): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .apply { if (lines.isNotEmpty()) style = Notification.BigTextStyle().setBigContentTitle(title).bigText(lines.joinToString("\n")) }
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(TaskNotifications.addAction(this))
            .build()
            .also { lastNotification = it }
    }

    private fun notifyUnlockReady() {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(ID_UNLOCK, Notification.Builder(this, CHANNEL_UNLOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Settings ready to unlock")
            .setContentText("Your wait is over. Open Social Blocker within 15 minutes to make changes.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }

    private fun startInForeground() {
        val notification = lastNotification ?: notification("Social limits active", "Starting…")
        // specialUse exists from API 34; on Android 13 no type is required.
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ID_STATUS, notification, type)
    }
}
