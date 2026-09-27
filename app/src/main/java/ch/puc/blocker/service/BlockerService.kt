package ch.puc.blocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import ch.puc.blocker.domain.UsageAggregator
import ch.puc.blocker.domain.UsageState
import ch.puc.blocker.overlay.BlockOverlay
import ch.puc.blocker.overlay.TimerOverlay
import ch.puc.blocker.ruleConfig
import ch.puc.blocker.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Polls UsageStatsManager every second, applies the rules and shows the overlay. */
class BlockerService : LifecycleService() {

    companion object {
        const val ACTION_REFRESH = "ch.puc.blocker.REFRESH"
        private const val CHANNEL = "blocker"
        private const val CHANNEL_UNLOCK = "unlock"
        private const val ID_UNLOCK = 2
        private const val TAG = "BlockerService"

        /** True while the service is alive; shown in the UI as a health indicator. */
        @Volatile var running = false
            private set

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
    private val pm by lazy { getSystemService(PowerManager::class.java) }
    private lateinit var overlay: BlockOverlay
    private lateinit var timer: TimerOverlay

    // Kept fresh by Room flows, so edits apply without restarting.
    @Volatile private var apps: Map<String, TrackedApp> = emptyMap()
    @Volatile private var settings: GlobalSettings? = null
    @Volatile private var waitReadyAt = 0L
    /** Open tasks, for the periodic reminder. */
    @Volatile private var openTasks: List<TaskItem> = emptyList()
    private var lastReminder = System.currentTimeMillis()
    /** Package → end of its redeemed task reward. */
    @Volatile private var rewards: Map<String, Long> = emptyMap()
    private var waitNotified = false

    private var loop: Job? = null
    private var day = ""
    private var firstUnlock: Long? = null
    private val usage = mutableMapOf<String, UsageState>()
    private var fgPackage: String? = null
    private var lastEventQuery = 0L
    private var lastTick = 0L
    private var lastSystemSync = 0L

    override fun onCreate() {
        super.onCreate()
        running = true
        overlay = BlockOverlay(this)
        timer = TimerOverlay(this)
        startInForeground()
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
        lifecycleScope.launch {
            dao.observeWait().collect {
                waitReadyAt = it?.readyAtMs ?: 0
                waitNotified = false
                if (it == null) getSystemService(NotificationManager::class.java).cancel(ID_UNLOCK)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_REFRESH) { day = ""; lastStatus = "" } // reload counters, re-post notification
        if (loop == null) loop = lifecycleScope.launch {
            while (isActive) {
                runCatching { tick() }.onFailure { Log.e(TAG, "tick failed", it) }
                delay(1_000)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        hideAll()
        super.onDestroy()
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        val today = Day.today()
        if (today != day) loadDay(today, now)

        val delta = if (lastTick == 0L) 0 else (now - lastTick).coerceIn(0, 2_000)
        lastTick = now
        if (waitReadyAt in 1..now && !waitNotified) { waitNotified = true; notifyUnlockReady() }
        if (!pm.isInteractive) { hideAll(); return }
        // Every 3 h between 9:00 and 21:59, nudge about open tasks while the phone is in use.
        if (openTasks.isNotEmpty() && now - lastReminder >= 3 * 60 * 60_000L && LocalTime.now().hour in 9..21) {
            lastReminder = now
            TaskNotifications.showReminder(this, openTasks)
        }

        if (firstUnlock == null) {
            firstUnlock = now
            dao.insertDayState(DayState(today, now))
        }
        updateForeground(now)
        if (now - lastSystemSync >= 60_000) syncSystemUsage(today, now)

        val s = settings ?: return
        val pkg = fgPackage
        val app = pkg?.let { apps[it] }
        if (!s.blockingEnabled || app == null) {
            hideAll()
            status(if (!s.blockingEnabled) "Blocking paused" else "Watching ${apps.size} app(s)")
            return
        }

        // An earned task reward lifts every limit for this app; that time isn't counted.
        val rewardUntil = rewards[pkg] ?: 0
        if (now < rewardUntil) {
            overlay.hide()
            timer.show(app.label, Remaining(rewardUntil - now, dailyLimited = false), reward = true)
            status("${app.label}: reward unlock active")
            return
        }

        val cfg = ruleConfig(app, s)
        val endOfDay = Day.startOfTomorrowMs()
        var state = usage[pkg] ?: UsageState()
        var decision = RuleEngine.evaluate(now, firstUnlock, endOfDay, cfg, state)
        if (decision == Decision.Allow) {
            state = RuleEngine.accrue(state, now, delta, cfg)
            usage[pkg] = state
            dao.upsertUsage(DailyUsage(today, pkg, state.totalMs, state.sessionMs, state.lastSeenMs, state.cooldownUntilMs))
            decision = RuleEngine.evaluate(now, firstUnlock, endOfDay, cfg, state)
        }
        if (decision is Decision.Block) {
            // Time's up: drop the countdown and cover the app until the rule expires.
            timer.hide()
            overlay.show(app.label, decision)
            status("${app.label} is blocked")
        } else {
            overlay.hide()
            timer.show(app.label, RuleEngine.remaining(cfg, state))
            status("${app.label}: ${((cfg.dailyCapMs - state.totalMs) / 60_000).coerceAtLeast(0)} min left today")
        }
    }

    private fun hideAll() {
        overlay.hide()
        timer.hide()
    }

    private suspend fun loadDay(today: String, now: Long) {
        day = today
        lastSystemSync = 0
        usage.clear()
        dao.usage(today).forEach { usage[it.packageName] = UsageState(it.totalMs, it.sessionMs, it.lastSeenMs, it.cooldownUntilMs) }
        firstUnlock = dao.dayState(today)?.firstUnlockMs
            ?: firstUnlockSince(Day.startOfTodayMs(), now)?.also { dao.insertDayState(DayState(today, it)) }
    }

    /**
     * Pulls today's screen time per tracked app from Android's usage events, the data Digital
     * Wellbeing shows, and raises our counter if the system saw more. That covers time used before
     * the app was installed or while the service was stopped.
     */
    private suspend fun syncSystemUsage(today: String, now: Long) {
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
                val next = cur.copy(totalMs = systemMs)
                usage[pkg] = next
                dao.upsertUsage(DailyUsage(today, pkg, next.totalMs, next.sessionMs, next.lastSeenMs, next.cooldownUntilMs))
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

    /** Incrementally replays usage events to track the resumed activity's package. */
    private fun updateForeground(now: Long) {
        val from = if (lastEventQuery == 0L) now - 60 * 60_000 else lastEventQuery
        val events = usm.queryEvents(from, now)
        lastEventQuery = now
        val e = UsageEvents.Event()
        while (events.getNextEvent(e)) when (e.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> fgPackage = e.packageName
            UsageEvents.Event.ACTIVITY_PAUSED -> if (e.packageName == fgPackage) fgPackage = null
        }
    }

    private var lastStatus = ""

    /** Updates the ongoing notification only when the text actually changes. */
    private fun status(text: String) {
        if (text == lastStatus) return
        lastStatus = text
        getSystemService(NotificationManager::class.java).notify(1, notification(text))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Social limits active")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(TaskNotifications.addAction(this))
            .build()
    }

    private fun notifyUnlockReady() {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        getSystemService(NotificationManager::class.java).notify(ID_UNLOCK, Notification.Builder(this, CHANNEL_UNLOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Settings ready to unlock")
            .setContentText("Your wait is over. Open Social Blocker within 15 minutes to make changes.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Blocker", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_UNLOCK, "Unlock ready", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = notification("Starting…")
        // specialUse exists from API 34; on Android 13 no type is required.
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 1, notification, type)
    }
}
