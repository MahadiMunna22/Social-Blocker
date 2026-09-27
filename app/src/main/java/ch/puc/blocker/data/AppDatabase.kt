package ch.puc.blocker.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.DeleteTable
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.AutoMigrationSpec
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

@Dao
interface BlockerDao {
    // Tracked apps
    @Query("SELECT * FROM TrackedApp ORDER BY label")
    fun observeApps(): Flow<List<TrackedApp>>

    @Query("SELECT * FROM TrackedApp")
    suspend fun apps(): List<TrackedApp>

    @Upsert suspend fun upsertApp(app: TrackedApp)
    @Delete suspend fun deleteApp(app: TrackedApp)

    // Global settings
    @Query("SELECT * FROM GlobalSettings WHERE id = 0")
    fun observeSettings(): Flow<GlobalSettings?>

    @Query("SELECT * FROM GlobalSettings WHERE id = 0")
    suspend fun settings(): GlobalSettings?

    @Upsert suspend fun upsertSettings(s: GlobalSettings)

    // Usage
    @Query("SELECT * FROM DailyUsage WHERE date = :date")
    fun observeUsage(date: String): Flow<List<DailyUsage>>

    @Query("SELECT * FROM DailyUsage WHERE date = :date")
    suspend fun usage(date: String): List<DailyUsage>

    @Query("SELECT * FROM DailyUsage WHERE date >= :fromDate")
    fun observeUsageSince(fromDate: String): Flow<List<DailyUsage>>

    @Upsert suspend fun upsertUsage(u: List<DailyUsage>)

    @Query("DELETE FROM DailyUsage WHERE date < :date")
    suspend fun pruneUsage(date: String)

    // Day state
    @Query("SELECT * FROM DayState WHERE date = :date")
    suspend fun dayState(date: String): DayState?

    @Query("SELECT * FROM DayState WHERE date = :date")
    fun observeDayState(date: String): Flow<DayState?>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertDayState(d: DayState)

    // German question bank
    @Query("SELECT * FROM QuizItem")
    suspend fun quizItems(): List<QuizItem>

    @Query("SELECT COUNT(*) FROM QuizItem")
    suspend fun quizItemCount(): Int

    /** New bank entries are added; existing ones keep their history. */
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertQuizItems(items: List<QuizItem>)

    @Query("UPDATE QuizItem SET timesAsked = timesAsked + 1, lastAskedMs = :now, failed = :failed WHERE id = :id")
    suspend fun recordAnswer(id: String, failed: Boolean, now: Long)

    // History
    @Insert suspend fun insertChange(c: ChangeLog)
    @Insert suspend fun insertResult(r: ChallengeResult)

    @Query("SELECT * FROM ChangeLog ORDER BY timestampMs DESC LIMIT 50")
    fun observeChanges(): Flow<List<ChangeLog>>

    @Query("SELECT * FROM ChallengeResult ORDER BY timestampMs DESC LIMIT 50")
    fun observeResults(): Flow<List<ChallengeResult>>

    @Query("SELECT MAX(timestampMs) FROM ChallengeResult WHERE passed = 0")
    suspend fun lastFailureMs(): Long?

    // Wait-to-unlock
    @Query("SELECT * FROM UnlockWait WHERE id = 0")
    fun observeWait(): Flow<UnlockWait?>

    @Query("SELECT * FROM UnlockWait WHERE id = 0")
    suspend fun unlockWait(): UnlockWait?

    @Upsert suspend fun upsertWait(w: UnlockWait)

    @Query("DELETE FROM UnlockWait")
    suspend fun clearWait()

    // Tasks & rewards
    @Query("SELECT * FROM TaskItem ORDER BY dueEpochDay, createdMs")
    fun observeTasks(): Flow<List<TaskItem>>

    @Query("SELECT * FROM TaskItem WHERE doneMs = 0 ORDER BY dueEpochDay, createdMs")
    suspend fun openTasks(): List<TaskItem>

    @Query("SELECT * FROM TaskItem WHERE id = :id")
    suspend fun task(id: Long): TaskItem?

    @Upsert suspend fun upsertTask(t: TaskItem)
    @Delete suspend fun deleteTask(t: TaskItem)

    @Query("SELECT * FROM RewardUnlock")
    fun observeRewards(): Flow<List<RewardUnlock>>

    @Insert suspend fun insertReward(r: RewardUnlock)
}

/** v3 replaced the small word list with the A2/B1 question bank. */
@DeleteTable(tableName = "VocabWord")
class DropVocabTable : AutoMigrationSpec

@Database(
    entities = [TrackedApp::class, GlobalSettings::class, DailyUsage::class, DayState::class,
        QuizItem::class, ChangeLog::class, ChallengeResult::class, UnlockWait::class, TaskItem::class, RewardUnlock::class],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = DropVocabTable::class),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): BlockerDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "blocker.db")
                .build().also { instance = it }
        }
    }
}

/** Seeds defaults and the question bank from assets. New bank entries are merged in on app updates. */
suspend fun BlockerDao.seedIfEmpty(context: Context) {
    if (settings() == null) upsertSettings(GlobalSettings())
    val json = context.assets.open("german_bank.json").bufferedReader().use { it.readText() }
    val arr = JSONArray(json)
    if (quizItemCount() >= arr.length()) return
    insertQuizItems((0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        val opts = o.getJSONArray("options")
        QuizItem(
            id = o.getString("id"), kind = o.getString("kind"), level = o.getString("level"),
            prompt = o.getString("prompt"), answer = o.getString("answer"),
            options = (0 until opts.length()).joinToString("\n") { i -> opts.getString(i) },
        )
    })
}
