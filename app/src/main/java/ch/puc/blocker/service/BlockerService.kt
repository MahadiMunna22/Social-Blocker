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
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import ch.puc.blocker.BlockerApp
import ch.puc.blocker.Day
import ch.puc.blocker.R
import ch.puc.blocker.data.filterAppSet
import ch.puc.blocker.data.DailyUsage
import ch.puc.blocker.data.DayState
import ch.puc.blocker.data.GlobalSettings
import ch.puc.blocker.data.TaskItem
import ch.puc.blocker.data.TrackedApp
import ch.puc.blocker.domain.Decision
import ch.puc.blocker.domain.FgEvent
import ch.puc.blocker.domain.Remaining
import ch.puc.blocker.domain.RuleEngine
import ch.puc.blocker.domain.Quotes
import ch.puc.blocker.domain.StatusText
import ch.puc.blocker.domain.UsageAggregator
import ch.puc.blocker.domain.UsageState
import ch.puc.blocker.overlay.BlockOverlay
import ch.puc.blocker.overlay.PauseOverlay
import ch.puc.blocker.overlay.TimerOverlay
import ch.puc.blocker.overlay.goHome
import ch.puc.blocker.ruleConfig
import ch.puc.blocker.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
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
        private const val CHANNEL_BANNED = "banned"
        private const val ID_STATUS = 1
        private const val ID_UNLOCK = 2
        private const val TAG = "BlockerService"
        private const val FLUSH_MS = 15_000L
        private const val SYSTEM_SYNC_MS = 60_000L
        /** Don't re-send the same app home while the launcher is still coming up. */
        private const val ENFORCE_RETRY_MS = 3_000L
        /** A normal app switch pauses then stops within a moment; paused this long without a stop means PiP. */
        private const val PIP_SETTLE_MS = 1_500L
        /** How late a usage event may show up after its timestamp; that window is replayed every tick. */
        private const val EVENT_SETTLE_MS = 10_000L

        /** True while the service is alive; shown in the UI as a health indicator. */
        @Volatile var running = false
            private set

        /** Nudged by the accessibility service on every window change, so a switch is handled at once, not next second. */
        val wake = Channel<Unit>(Channel.CONFLATED)

        fun channels() = listOf(
            NotificationChannel(CHANNEL, "Blocker", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_UNLOCK, "Unlock ready", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_BANNED, "Blocked installs", NotificationManager.IMPORTANCE_HIGH),
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
    private lateinit var pause: PauseOverlay

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

    /** Android can't stop a Play Store install, so a banned app that lands gets an uninstall prompt right away. */
    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            apps[pkg]?.takeIf { it.banned }?.let(::askUninstall)
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
    /** Package of the most recently resumed activity that is still resumed. */
    private var fgPackage: String? = null
    /** "package/class" → when it paused without stopping yet (still visible, e.g. picture-in-picture). */
    private var pausedVisible: Map<String, Long> = emptyMap()
    /** Activity state as of [settledAt]. Events before it are assumed delivered; later ones are replayed each tick. */
    private var settledAt = 0L
    private var settledResumed: Map<String, Long> = emptyMap()
    private var settledPaused: Map<String, Long> = emptyMap()
    private var lastTick = 0L
    private var lastSystemSync = 0L

    /** Block card currently on screen (app to block); stays over the home screen until dismissed. */
    private var shownBlock: Pair<TrackedApp, Decision.Block>? = null
    private var enforcedPkg: String? = null
    private var enforcedAt = 0L

    private var lastStatus = ""
    private var lastNotification: Notification? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        overlay = BlockOverlay(this) { shownBlock = null }
        pause = PauseOverlay(this, onOpen = { pkg ->
            usage[pkg] = RuleEngine.open(usage[pkg] ?: UsageState(), System.currentTimeMillis())
            dirty += pkg
            lifecycleScope.launch { flush() } // an open must survive a restart
        }, onClose = { runCatching { goHome() } })
        timer = TimerOverlay(this)
        startInForeground()
        interactive.value = getSystemService(PowerManager::class.java).isInteractive
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, RECEIVER_NOT_EXPORTED)
        registerReceiver(installReceiver, IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }, RECEIVER_NOT_EXPORTED)
        lifecycleScope.launch { dao.observeApps().collect { list -> apps = list.filter { it.enabled }.associateBy { it.packageName }; lastSystemSync = 0 } }
        lifecycleScope.launch {
            dao.observeSettings().collect {
                settings = it
                GuardService.blockShortForm = it?.blockShortForm == true
                Quotes.sources(it?.quoteMask ?: 0, it?.quoteSource ?: 0).let { q -> overlay.quoteSources = q; pause.quoteSources = q }
                WebFilterService.sync(this@BlockerService, it?.webFilter == true, it?.filterAppSet.orEmpty(), it?.blockAds == true)
            }
        }
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
                withTimeoutOrNull(1_000) { wake.receive(); delay(150) } // give the usage log a moment to catch up
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(installReceiver) }
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
        // Screen-off pauses every activity, so start clean: this drops any app whose process died without a pause event.
        settledResumed = emptyMap(); settledPaused = emptyMap(); settledAt = System.currentTimeMillis() - 2_000
        WebFilterService.sync(this, settings?.webFilter == true, settings?.filterAppSet.orEmpty(), settings?.blockAds == true) // Private DNS may have changed meanwhile
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
        val others = alsoVisibleApps(now)
        // Full-day scan only when it can matter: on (re)start, after screen-on and while a tracked app is open.
        if (lastSystemSync == 0L || ((app != null || others.isNotEmpty()) && now - lastSystemSync >= SYSTEM_SYNC_MS)) syncSystemUsage(now)

        if (!s.blockingEnabled) {
            hideAll()
            shownBlock = null
            status("Blocking paused", now, s, inUse = null)
            return
        }

        if (app == null && others.isEmpty()) { timer.hide(); enforcedPkg = null } // reopening it right away gets closed again
        if (pause.shownFor != null && pause.shownFor != app?.packageName) pause.hide() // left before deciding
        // A PiP window is still in use: it counts, shows the timer and gets closed on timeout.
        val fgLine = app?.let { apply(it, s, now, delta, pip = false, showTimer = true) }
        val otherLines = others.mapIndexed { i, a -> apply(a, s, now, delta, pip = true, showTimer = app == null && i == 0) }
        val inUse = fgLine ?: otherLines.firstNotNullOfOrNull { it }

        // Keep the block card (now over the home screen) counting down until dismissed or expired.
        shownBlock?.let { (app, block) -> if (now >= block.untilMs) { shownBlock = null; overlay.hide() } else overlay.show(app.label, block, app.packageName) }
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
        if (decision == Decision.Allow && !pip && RuleEngine.isNewSession(state, now, cfg)) {
            if (s.pauseBeforeOpen) {
                // New session: a breath first. Nothing counts until Open, and its video is paused meanwhile.
                if (showTimer) timer.hide()
                shownBlock = null
                overlay.hide()
                if (pause.shownFor != pkg) stopPlayback()
                pause.show(pkg, app.label, if (cfg.opensPerDay > 0) cfg.opensPerDay - state.opens else null)
                return "${app.label} · taking a breath"
            }
            state = RuleEngine.open(state, now)
            usage[pkg] = state
            dirty += pkg
        }
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
        shownBlock = app to block
        overlay.show(app.label, block, app.packageName)
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
        pause.hide()
    }

    private fun takeDirty(): List<DailyUsage> {
        if (day.isEmpty() || dirty.isEmpty()) return emptyList()
        val rows = dirty.mapNotNull { pkg ->
            usage[pkg]?.let { DailyUsage(day, pkg, it.totalMs, it.sessionMs, it.lastSeenMs, it.cooldownUntilMs, it.opens) }
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
        dao.usage(today).forEach { usage[it.packageName] = UsageState(it.totalMs, it.sessionMs, it.lastSeenMs, it.cooldownUntilMs, it.opens) }
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
     * Tracked apps on screen besides [fgPackage]: paused but not stopped for [PIP_SETTLE_MS]
     * (picture-in-picture, or half-covered by a dialog). An app still resumed under the launcher
     * (half swipe to Recents) doesn't count: it's not being used, and it's back as [fgPackage] on release.
     */
    private fun alsoVisibleApps(now: Long): List<TrackedApp> =
        pausedVisible.filterValues { now - it >= PIP_SETTLE_MS }.keys.map { it.substringBefore('/') }
            .distinct().filter { it != fgPackage }.mapNotNull { apps[it] }

    /**
     * Replays usage events to track every resumed activity and the paused-but-visible ones.
     * Android can log an event a moment after its timestamp, so querying only "since last tick" can
     * miss an app opening for good (timer gone, nothing counted). Instead, the last [EVENT_SETTLE_MS]
     * are replayed every tick from a settled snapshot, so late events still land in order.
     */
    private fun updateForeground(now: Long) {
        if (settledAt == 0L) settledAt = now - 60 * 60_000
        val settleBefore = (now - EVENT_SETTLE_MS).coerceAtLeast(settledAt)
        val res = settledResumed.toMutableMap()
        val paused = settledPaused.toMutableMap()
        var snapped = false
        fun snap() { settledResumed = res.toMap(); settledPaused = paused.toMap(); snapped = true }
        val events = usm.queryEvents(settledAt, now)
        val e = UsageEvents.Event()
        while (events.getNextEvent(e)) {
            if (!snapped && e.timeStamp >= settleBefore) snap()
            val key = "${e.packageName}/${e.className}"
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> { res[key] = e.timeStamp; paused.remove(key) }
                UsageEvents.Event.ACTIVITY_PAUSED -> { res.remove(key); paused[key] = e.timeStamp }
                UsageEvents.Event.ACTIVITY_STOPPED -> paused.remove(key)
            }
        }
        if (!snapped) snap()
        settledAt = settleBefore
        // Several can be resumed at once (a half swipe resumes the launcher over the app); the newest is in front,
        // and the one underneath sends no new event when it's back, so the whole set is kept.
        fgPackage = res.maxByOrNull { it.value }?.key?.substringBefore('/')
        pausedVisible = paused
    }

    /** Apps blocked right now, soonest unblock first. Rewarded apps are free, so they're left out. */
    private fun blockedApps(now: Long, s: GlobalSettings): List<StatusText.Blocked> {
        val endOfDay = Day.startOfTomorrowMs()
        return apps.values.mapNotNull { app ->
            // Banned apps are always blocked, usually not even installed: listing them would just be noise.
            if (app.banned || now < (rewards[app.packageName] ?: 0)) return@mapNotNull null
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
        // Nothing blocked or in use: the notification is about tasks, so it opens Tasks; otherwise Today.
        val screen = if (override == null && inUse == null && blocked.isEmpty()) MainActivity.SCREEN_TASKS else MainActivity.SCREEN_TODAY
        nm.notify(ID_STATUS, notification(title, text, lines, screen))
    }

    private fun notification(title: String, text: String, lines: List<String> = emptyList(), screen: String? = null): Notification {
        val open = MainActivity.open(this, 0, screen)
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

    private fun askUninstall(app: TrackedApp) {
        val uninstall = PendingIntent.getActivity(this, app.packageName.hashCode(),
            Intent(Intent.ACTION_DELETE, "package:${app.packageName}".toUri()), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(app.packageName.hashCode(), Notification.Builder(this, CHANNEL_BANNED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${app.label} is on your can't-install list")
            .setContentText("It stays blocked. Tap to uninstall it.")
            .setContentIntent(uninstall)
            .setAutoCancel(true)
            .build())
    }

    private fun notifyUnlockReady() {
        val open = MainActivity.open(this, 1, MainActivity.SCREEN_UNLOCK)
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
