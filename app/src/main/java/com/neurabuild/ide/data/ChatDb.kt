package com.neurabuild.ide.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class MsgEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val ts: Long = System.currentTimeMillis()
)

@Dao
interface MsgDao {
    @Query("SELECT * FROM messages ORDER BY id ASC LIMIT 500") fun all(): Flow<List<MsgEntity>>
    @Query("SELECT * FROM messages ORDER BY id ASC LIMIT 500") suspend fun allOnce(): List<MsgEntity>
    @Insert suspend fun insert(m: MsgEntity)
    @Query("DELETE FROM messages") suspend fun clear()
}

@Database(entities = [MsgEntity::class], version = 1, exportSchema = false)
abstract class ChatDb : RoomDatabase() {
    abstract fun msgs(): MsgDao
}
