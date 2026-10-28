package dev.lfm.mobile.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turnId: String,
    val role: String,
    val content: String,
    val observedAt: Long = System.currentTimeMillis(),
    val contextJson: String? = null,
)
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY id") fun observe(): Flow<List<ChatMessage>>
    @Query("SELECT * FROM messages ORDER BY id") suspend fun all(): List<ChatMessage>
    @Insert suspend fun insert(message: ChatMessage): Long
    @Query("DELETE FROM messages") suspend fun clear()
}
@Database(entities = [ChatMessage::class], version = 1, exportSchema = true)
abstract class ChatDatabase : RoomDatabase() { abstract fun messages(): MessageDao }
