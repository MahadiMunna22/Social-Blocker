package ch.puc.blocker

import android.app.Application
import ch.puc.blocker.data.AppDatabase
import ch.puc.blocker.data.TrackedApp
import ch.puc.blocker.data.seedIfEmpty
import ch.puc.blocker.domain.RuleConfig
import ch.puc.blocker.data.GlobalSettings
import ch.puc.blocker.receiver.Alarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class BlockerApp : Application() {
    val db by lazy { AppDatabase.get(this) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch { db.dao().seedIfEmpty(this@BlockerApp) }
        Alarms.scheduleMidnight(this)
    }
}

/** Local-date helpers shared by service, receivers and UI. */
object Day {
    private val zone get() = ZoneId.systemDefault()
    fun today(): String = LocalDate.now(zone).toString()
    fun daysAgo(n: Long): String = LocalDate.now(zone).minusDays(n).toString()
    fun startOfTodayMs(): Long = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    fun startOfTomorrowMs(): Long = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}

fun ruleConfig(app: TrackedApp, s: GlobalSettings) = RuleConfig(
    morningLockoutMs = s.morningLockoutMin * 60_000L,
    sessionLimitMs = app.sessionLimitMin * 60_000L,
    cooldownMs = app.cooldownMin * 60_000L,
    dailyCapMs = app.dailyCapMin * 60_000L,
    graceMs = s.sessionGraceSec * 1_000L,
)
