package com.example.intune.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

enum class NudgeSource { SINGLE_APP, SESSION }
enum class ActionTaken { ACCEPTED, DISMISSED, ALREADY_ALIGNED, NONE }
enum class RoutineActivityType { STILL, WALKING, IN_VEHICLE, ON_BICYCLE }
enum class TransitionType { ENTER, EXIT }
enum class RoutineStatus { PENDING_REVIEW, LABELED, DISMISSED }

@Entity(tableName = "nudge_records")
data class NudgeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val source: NudgeSource,
    val appSummary: String,
    val message: String,
    val microAction: String,
    val actionTaken: ActionTaken = ActionTaken.NONE,
    val goalsSnapshot: String,
)

@Entity(tableName = "activity_transition_records")
data class ActivityTransitionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityType: RoutineActivityType,
    val transitionType: TransitionType,
    val timestamp: Long,
)

@Entity(tableName = "routine_profiles")
data class RoutineProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityType: RoutineActivityType,
    val dayPattern: String,
    val approxStartHour: Int,
    val approxEndHour: Int,
    val label: String? = null,
    val status: RoutineStatus = RoutineStatus.PENDING_REVIEW,
)

class NudgeConverters {
    @TypeConverter fun sourceToString(value: NudgeSource) = value.name
    @TypeConverter fun stringToSource(value: String) = NudgeSource.valueOf(value)
    @TypeConverter fun actionToString(value: ActionTaken) = value.name
    @TypeConverter fun stringToAction(value: String) = ActionTaken.valueOf(value)
    @TypeConverter fun activityToString(value: RoutineActivityType) = value.name
    @TypeConverter fun stringToActivity(value: String) = RoutineActivityType.valueOf(value)
    @TypeConverter fun transitionToString(value: TransitionType) = value.name
    @TypeConverter fun stringToTransition(value: String) = TransitionType.valueOf(value)
    @TypeConverter fun statusToString(value: RoutineStatus) = value.name
    @TypeConverter fun stringToStatus(value: String) = RoutineStatus.valueOf(value)
}

@Dao
interface NudgeDao {
    @Query("SELECT * FROM nudge_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<NudgeRecord>>

    @Query("SELECT COALESCE(SUM(CASE WHEN actionTaken = 'ACCEPTED' THEN 10 ELSE 0 END), 0) FROM nudge_records")
    fun getTotalPoints(): Flow<Int>

    @Query("SELECT COUNT(*) FROM nudge_records WHERE actionTaken = 'ACCEPTED'")
    fun getAcceptedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM nudge_records")
    fun getTotalCount(): Flow<Int>

    @Query("""
        WITH RECURSIVE streak(day, count) AS (
            SELECT date('now', 'localtime'), 0
            UNION ALL
            SELECT date(day, '-1 day'), count + 1 FROM streak
            WHERE EXISTS (
                SELECT 1 FROM nudge_records
                WHERE actionTaken = 'ACCEPTED'
                AND date(timestamp / 1000, 'unixepoch', 'localtime') = day
            )
        )
        SELECT MAX(count) FROM streak
    """)
    fun getCurrentStreak(): Flow<Int>

    @Query("UPDATE nudge_records SET actionTaken = :actionTaken WHERE id = :id AND actionTaken = 'NONE'")
    suspend fun updateActionTaken(id: Long, actionTaken: ActionTaken): Int

    @Query("SELECT actionTaken FROM nudge_records WHERE id = :id LIMIT 1")
    suspend fun getActionTaken(id: Long): ActionTaken?

    @androidx.room.Insert
    suspend fun insert(record: NudgeRecord): Long
}

@Dao
interface RoutineDao {
    @androidx.room.Insert
    suspend fun insertTransition(record: ActivityTransitionRecord)

    @Query("SELECT * FROM activity_transition_records WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun transitionsSince(since: Long): List<ActivityTransitionRecord>

    @Query("SELECT * FROM routine_profiles WHERE status = 'PENDING_REVIEW' ORDER BY id ASC LIMIT 1")
    suspend fun nextPending(): RoutineProfile?

    @Query("SELECT * FROM routine_profiles WHERE status = 'LABELED' ORDER BY id ASC")
    suspend fun labeled(): List<RoutineProfile>

    @Query("SELECT COUNT(*) FROM routine_profiles WHERE activityType = :activityType AND dayPattern = :dayPattern AND approxStartHour = :hour")
    suspend fun matchingCount(activityType: RoutineActivityType, dayPattern: String, hour: Int): Int

    @androidx.room.Insert
    suspend fun insertProfile(profile: RoutineProfile)

    @Query("UPDATE routine_profiles SET status = :status, label = :label WHERE id = :id")
    suspend fun updateProfile(id: Long, status: RoutineStatus, label: String?)
}

@Database(entities = [NudgeRecord::class, ActivityTransitionRecord::class, RoutineProfile::class], version = 2, exportSchema = false)
@TypeConverters(NudgeConverters::class)
abstract class NudgeDatabase : RoomDatabase() {
    abstract fun nudgeDao(): NudgeDao
    abstract fun routineDao(): RoutineDao

    companion object {
        @Volatile private var instance: NudgeDatabase? = null
        fun get(context: Context): NudgeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NudgeDatabase::class.java, "nudge.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `activity_transition_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `activityType` TEXT NOT NULL, `transitionType` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `routine_profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `activityType` TEXT NOT NULL, `dayPattern` TEXT NOT NULL, `approxStartHour` INTEGER NOT NULL, `approxEndHour` INTEGER NOT NULL, `label` TEXT, `status` TEXT NOT NULL)")
            }
        }
    }
}

data class PendingNudge(val nudge: NudgeResponse, val recordId: Long)
