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
import kotlinx.coroutines.flow.Flow

enum class NudgeSource { SINGLE_APP, SESSION }
enum class ActionTaken { ACCEPTED, DISMISSED, ALREADY_ALIGNED, NONE }

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

class NudgeConverters {
    @TypeConverter fun sourceToString(value: NudgeSource) = value.name
    @TypeConverter fun stringToSource(value: String) = NudgeSource.valueOf(value)
    @TypeConverter fun actionToString(value: ActionTaken) = value.name
    @TypeConverter fun stringToAction(value: String) = ActionTaken.valueOf(value)
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

@Database(entities = [NudgeRecord::class], version = 1, exportSchema = false)
@TypeConverters(NudgeConverters::class)
abstract class NudgeDatabase : RoomDatabase() {
    abstract fun nudgeDao(): NudgeDao

    companion object {
        @Volatile private var instance: NudgeDatabase? = null
        fun get(context: Context): NudgeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NudgeDatabase::class.java, "nudge.db")
                .build().also { instance = it }
        }
    }
}

data class PendingNudge(val nudge: NudgeResponse, val recordId: Long)
