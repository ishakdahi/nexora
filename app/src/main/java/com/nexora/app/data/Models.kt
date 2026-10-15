package com.nexora.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Un scénario = déclencheur + conditions + action. */
@Entity(tableName = "scenarios")
data class Scenario(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** MANUAL, TIME, POWER_CONNECTED, POWER_DISCONNECTED, BATTERY_LOW */
    val triggerType: String,
    val hour: Int = 8,
    val minute: Int = 0,
    /** bit0 = lundi ... bit6 = dimanche */
    val daysMask: Int = 127,
    /** AND ou OR */
    val logic: String = "AND",
    val condBatteryBelow: Int? = null,
    val condCharging: Boolean? = null,
    val condWifi: Boolean? = null,
    /** NOTIFICATION ou REMINDER */
    val actionType: String = "NOTIFICATION",
    val title: String,
    val message: String,
    val enabled: Boolean = true,
    val lastRunAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** Historique : status = SUCCESS / SKIPPED / ERROR, detail = code (OK, KILL, RATE, COND, NO_NOTIF, ERROR:...) */
@Entity(tableName = "logs")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scenarioId: Long,
    val scenarioName: String,
    val ts: Long = System.currentTimeMillis(),
    val status: String,
    val detail: String
)

@Dao
interface ScenarioDao {
    @Query("SELECT * FROM scenarios ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Scenario>>

    @Query("SELECT * FROM scenarios")
    suspend fun all(): List<Scenario>

    @Query("SELECT * FROM scenarios WHERE id = :id")
    suspend fun get(id: Long): Scenario?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: Scenario): Long

    @Query("DELETE FROM scenarios WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE scenarios SET lastRunAt = :t WHERE id = :id")
    suspend fun setLastRun(id: Long, t: Long)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY ts DESC LIMIT 200")
    fun observe(): Flow<List<LogEntry>>

    @Insert
    suspend fun insert(l: LogEntry)

    @Query("DELETE FROM logs")
    suspend fun clear()

    @Query("DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY ts DESC LIMIT 500)")
    suspend fun trim()
}

@Database(entities = [Scenario::class, LogEntry::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun scenarios(): ScenarioDao
    abstract fun logs(): LogDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(ctx: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "nexora.db")
                .build().also { instance = it }
        }
    }
}
