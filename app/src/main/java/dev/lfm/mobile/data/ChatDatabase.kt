package dev.lfm.mobile.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

const val DEFAULT_CONVERSATION = "default"

@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turnId: String,
    val role: String,
    val content: String,
    val observedAt: Long = System.currentTimeMillis(),
    val contextJson: String? = null,
    @ColumnInfo(defaultValue = "'default'") val conversationId: String = DEFAULT_CONVERSATION,
    val metricsJson: String? = null,
)
@Entity(tableName = "conversations")
data class Conversation(@PrimaryKey val id: String, val title: String, val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "memories")
data class MemoryFact(@PrimaryKey(autoGenerate = true) val id: Long = 0, val content: String, val createdAt: Long = System.currentTimeMillis())

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY id") fun observe(): Flow<List<ChatMessage>>
    @Query("SELECT * FROM messages ORDER BY id") suspend fun all(): List<ChatMessage>
    @Query("SELECT * FROM messages WHERE conversationId = :conversation ORDER BY id") fun observeConversation(conversation: String): Flow<List<ChatMessage>>
    @Query("SELECT * FROM messages WHERE conversationId = :conversation ORDER BY id") suspend fun inConversation(conversation: String): List<ChatMessage>
    @Insert suspend fun insert(message: ChatMessage): Long
    @Query("DELETE FROM messages") suspend fun clear()
    @Query("DELETE FROM messages WHERE conversationId = :conversation") suspend fun clearConversation(conversation: String)
    @Transaction suspend fun clearChat(conversation: String) { clearConversation(conversation); renameConversation(conversation, "New chat") }
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun createConversation(conversation: Conversation)
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC") fun conversations(): Flow<List<Conversation>>
    @Query("SELECT * FROM conversations WHERE id = :id") suspend fun conversation(id: String): Conversation?
    @Query("UPDATE conversations SET title = :title WHERE id = :id") suspend fun renameConversation(id: String, title: String)
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC") suspend fun allConversations(): List<Conversation>
    @Query("SELECT DISTINCT c.* FROM conversations c LEFT JOIN messages m ON c.id = m.conversationId WHERE instr(lower(c.title), lower(:query)) > 0 OR instr(lower(m.content), lower(:query)) > 0 ORDER BY c.createdAt DESC") fun searchConversations(query: String): Flow<List<Conversation>>
    @Query("SELECT * FROM messages WHERE (:conversation IS NULL OR conversationId = :conversation) ORDER BY id LIMIT :limit OFFSET :offset") suspend fun exportPage(conversation: String?, limit: Int, offset: Int): List<ChatMessage>
    @Query("DELETE FROM conversations WHERE id = :id") suspend fun removeConversation(id: String)
    @Transaction suspend fun deleteChat(id: String) { clearConversation(id); removeConversation(id) }
    @Insert suspend fun saveMemory(memory: MemoryFact)
    @Query("SELECT * FROM memories ORDER BY id") fun observeMemories(): Flow<List<MemoryFact>>
    @Query("SELECT * FROM memories ORDER BY id") suspend fun allMemories(): List<MemoryFact>
    @Query("DELETE FROM memories WHERE id = :id") suspend fun deleteMemory(id: Long)
    @Query("UPDATE memories SET content = :content WHERE id = :id") suspend fun editMemory(id: Long, content: String)
}
@Database(entities = [ChatMessage::class, Conversation::class, MemoryFact::class], version = 2, exportSchema = true)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN conversationId TEXT NOT NULL DEFAULT 'default'")
                db.execSQL("ALTER TABLE messages ADD COLUMN metricsJson TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS memories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("INSERT OR IGNORE INTO conversations (id, title, createdAt) VALUES ('default', 'Earlier conversation', 0)")
            }
        }
    }
}
