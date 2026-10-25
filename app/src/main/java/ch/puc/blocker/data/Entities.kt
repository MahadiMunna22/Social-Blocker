package ch.puc.blocker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-app rule values. Minutes are stored as-is for easy editing. */
@Entity
data class TrackedApp(
    @PrimaryKey val packageName: String,
    val label: String,
    val sessionLimitMin: Int = 10,
    val cooldownMin: Int = 30,
    val dailyCapMin: Int = 60,
    /** Off = keep the app in the list but don't limit it. */
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    /** On the "can't install" list: always blocked, and asked to be uninstalled if it gets installed. */
    @ColumnInfo(defaultValue = "0") val banned: Boolean = false,
    /** Sessions allowed per day; 0 = no limit. */
    @ColumnInfo(defaultValue = "0") val opensPerDay: Int = 0,
)

/** Single-row global settings (id is always 0). */
@Entity
data class GlobalSettings(
    @PrimaryKey val id: Int = 0,
    val blockingEnabled: Boolean = true,
    val morningLockoutMin: Int = 30,
    /** The lockout starts at the first unlock after this hour, so late-night use doesn't trigger it. */
    @ColumnInfo(defaultValue = "6") val morningStartHour: Int = 6,
    val sessionGraceSec: Int = 90,
    /** Adult content filter (local DNS VPN, see WebFilterService). */
    @ColumnInfo(defaultValue = "0") val webFilter: Boolean = false,
    /** Comma-separated packages routed through the filter; empty means every app. */
    @ColumnInfo(defaultValue = "") val filterApps: String = "",
    /** The filter also blocks ads and trackers (AdGuard DNS Family instead of Cloudflare for Families). */
    @ColumnInfo(defaultValue = "0") val blockAds: Boolean = false,
    /** A short breathing pause before each new session of a tracked app. */
    @ColumnInfo(defaultValue = "1") val pauseBeforeOpen: Boolean = true,
    /** Back out of YouTube Shorts and Instagram Reels (needs the tamper guard's accessibility service). */
    @ColumnInfo(defaultValue = "0") val blockShortForm: Boolean = false,
    /** Quote set for the block and pause screens: a Quotes.Source ordinal (0 popular, 1 Quran, 2 Bible). */
    @ColumnInfo(defaultValue = "0") val quoteSource: Int = 0,
    /** Ticked quote sets as Quotes.Source bits; 0 = not set yet, use [quoteSource] (older single choice). */
    @ColumnInfo(defaultValue = "0") val quoteMask: Int = 0,
    /** ui.AppTheme ordinal. */
    @ColumnInfo(defaultValue = "0") val theme: Int = 0,
    /** ui.AppearanceMode ordinal: system, light or dark. */
    @ColumnInfo(defaultValue = "0") val appearance: Int = 0,
    val challengeType: ChallengeType = ChallengeType.VOCAB,
    val mathDifficulty: Int = 2,
)

enum class ChallengeType { VOCAB, MATH }

/** Usage counters for one app on one local date (yyyy-MM-dd). A new date = fresh counters. */
@Entity(primaryKeys = ["date", "packageName"])
data class DailyUsage(
    val date: String,
    val packageName: String,
    val totalMs: Long = 0,
    val sessionMs: Long = 0,
    val lastSeenMs: Long = 0,
    val cooldownUntilMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val opens: Int = 0,
)

/** First unlock of the day, used for the morning lockout. */
@Entity
data class DayState(
    @PrimaryKey val date: String,
    val firstUnlockMs: Long,
)

/** One German question from the bundled bank, plus how you did on it (drives repeat avoidance). */
@Entity
data class QuizItem(
    @PrimaryKey val id: String,
    val kind: String,          // VOCAB, ARTICLE or GRAMMAR
    val level: String,         // A2 or B1
    val prompt: String,
    val answer: String,
    val options: String = "",  // newline-separated, empty for vocab
    val timesAsked: Int = 0,
    val lastAskedMs: Long = 0,
    val failed: Boolean = false,
)

@Entity
data class ChangeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val what: String,
    val oldValue: String,
    val newValue: String,
)

@Entity
data class ChallengeResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val type: ChallengeType,
    val score: Int,
    val total: Int,
    val passed: Boolean,
)

/** Pending "wait to unlock" request (single row, id 0). Survives the app being closed. */
@Entity
data class UnlockWait(
    @PrimaryKey val id: Int = 0,
    val readyAtMs: Long,
)

/** A to-do with a deadline and a time estimate; completed estimates earn app-unlock rewards. */
@Entity
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,        // ASSIGNMENT, HOMEWORK, PROJECT, OTHER
    val dueEpochDay: Long,       // LocalDate.toEpochDay()
    val estimateMin: Int,
    val createdMs: Long,
    val doneMs: Long = 0,        // 0 = not done
    /** Finishing it adds a fresh copy due one week later (see completeTask). */
    @ColumnInfo(defaultValue = "0") val repeatWeekly: Boolean = false,
)

/** A redeemed reward: [packageName] is free of all limits until [untilMs]. */
@Entity
data class RewardUnlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startMs: Long,
    val untilMs: Long,
    val costMin: Int,
)

/** [GlobalSettings.filterApps] as a set; empty means every app. */
val GlobalSettings.filterAppSet: Set<String> get() = filterApps.split(',').filter { it.isNotBlank() }.toSet()
