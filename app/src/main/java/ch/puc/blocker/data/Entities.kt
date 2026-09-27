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
)

/** Single-row global settings (id is always 0). */
@Entity
data class GlobalSettings(
    @PrimaryKey val id: Int = 0,
    val blockingEnabled: Boolean = true,
    val morningLockoutMin: Int = 30,
    val sessionGraceSec: Int = 90,
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
