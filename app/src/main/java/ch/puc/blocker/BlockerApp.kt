package ch.puc.blocker

import android.app.Application
import android.app.NotificationManager
import android.util.Log
import ch.puc.blocker.data.AppDatabase
import ch.puc.blocker.data.TrackedApp
import ch.puc.blocker.data.seedIfEmpty
import ch.puc.blocker.domain.RuleConfig
import ch.puc.blocker.data.GlobalSettings
import ch.puc.blocker.receiver.Alarms
import ch.puc.blocker.service.BlockerService
import ch.puc.blocker.service.TaskNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.time.LocalDate
import java.time.ZoneId

class BlockerApp : Application() {
    val db by lazy { AppDatabase.get(this) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Completes once defaults and the question bank are in the database. Await it instead of seeding again. */
    lateinit var seeded: Deferred<Unit>
        private set

    override fun onCreate() {
        super.onCreate()
        seeded = scope.async {
            runCatching { db.dao().seedIfEmpty(this@BlockerApp) }.onFailure { Log.e("BlockerApp", "Seeding failed", it) }
            Unit
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannels(BlockerService.channels() + TaskNotifications.channel())
        Alarms.scheduleMidnight(this)
    }
}

/** Local-date helpers shared by service, receivers and UI. */
object Day {
    private val zone get() = ZoneId.systemDefault()
    fun today(): String = LocalDate.now(zone).toString()
    fun daysAgo(n: Long): String = LocalDate.now(zone).minusDays(n).toString()
    fun startOfTodayMs(): Long = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    fun todayAtHourMs(hour: Int): Long = LocalDate.now(zone).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    fun startOfTomorrowMs(): Long = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}

fun ruleConfig(app: TrackedApp, s: GlobalSettings) = RuleConfig(
    morningLockoutMs = s.morningLockoutMin * 60_000L,
    sessionLimitMs = app.sessionLimitMin * 60_000L,
    cooldownMs = app.cooldownMin * 60_000L,
    dailyCapMs = app.dailyCapMin * 60_000L,
    graceMs = s.sessionGraceSec * 1_000L,
)
