package com.webautomator.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val actionsJson: String,
    val intervalMinutes: Int = 60,
    val enabled: Boolean = false,
    val onlyWithInternet: Boolean = true,
    val dailyLimit: Int = 0,
    val notifyMode: String = NotifyMode.ON_CHANGE.name,
    val conditionType: String = ConditionType.NONE.name,
    val conditionKey: String = "",
    val conditionValue: String = "",
    val notifyOnFailure: Boolean = true,
    val lastRunAt: Long = 0,
    val status: String = Status.IDLE.name,
    val lastMessage: String = "",
    val runsToday: Int = 0,
    val runsDay: String = ""
)

@Entity(tableName = "runs", indices = [Index("scriptId")])
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scriptId: Long,
    val scriptName: String,
    val timestamp: Long,
    val success: Boolean,
    val valuesJson: String,
    val changeSummary: String,
    val error: String,
    val changed: Boolean
)

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts ORDER BY id DESC")
    fun observeAll(): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts")
    suspend fun getAll(): List<ScriptEntity>

    @Query("SELECT * FROM scripts WHERE id = :id")
    suspend fun get(id: Long): ScriptEntity?

    @Insert
    suspend fun insert(s: ScriptEntity): Long

    @Query("DELETE FROM scripts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE scripts SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE scripts SET url = :url WHERE id = :id")
    suspend fun updateUrl(id: Long, url: String)

    @Query("UPDATE scripts SET actionsJson = :json WHERE id = :id")
    suspend fun updateActions(id: Long, json: String)

    @Query("UPDATE scripts SET intervalMinutes = :interval, enabled = :enabled, onlyWithInternet = :onlyNet, dailyLimit = :dailyLimit WHERE id = :id")
    suspend fun updateSchedule(id: Long, interval: Int, enabled: Boolean, onlyNet: Boolean, dailyLimit: Int)

    @Query("UPDATE scripts SET notifyMode = :mode, conditionType = :ct, conditionKey = :ck, conditionValue = :cv, notifyOnFailure = :nf WHERE id = :id")
    suspend fun updateNotify(id: Long, mode: String, ct: String, ck: String, cv: String, nf: Boolean)

    @Query("UPDATE scripts SET lastRunAt = :t, status = :s, lastMessage = :m, runsToday = :r, runsDay = :d WHERE id = :id")
    suspend fun updateRunInfo(id: Long, t: Long, s: String, m: String, r: Int, d: String)

    @Query("UPDATE scripts SET status = :s WHERE id = :id")
    suspend fun updateStatus(id: Long, s: String)
}

@Dao
interface RunDao {
    @Insert
    suspend fun insert(r: RunEntity): Long

    @Query("SELECT * FROM runs ORDER BY timestamp DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE scriptId = :id ORDER BY timestamp DESC LIMIT :limit")
    fun observeForScript(id: Long, limit: Int): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE scriptId = :id AND success = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastSuccess(id: Long): RunEntity?

    @Query("SELECT * FROM runs WHERE scriptId = :id ORDER BY timestamp DESC")
    suspend fun allForScript(id: Long): List<RunEntity>

    @Query("SELECT * FROM runs ORDER BY timestamp DESC")
    suspend fun all(): List<RunEntity>

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM runs WHERE scriptId = :id")
    suspend fun deleteForScript(id: Long)

    @Query("DELETE FROM runs WHERE scriptId = :id AND id NOT IN (SELECT id FROM runs WHERE scriptId = :id ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun trim(id: Long, keep: Int)
}

@Database(entities = [ScriptEntity::class, RunEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scripts(): ScriptDao
    abstract fun runs(): RunDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "automator.db"
                ).build().also { instance = it }
            }
        }
    }
}
