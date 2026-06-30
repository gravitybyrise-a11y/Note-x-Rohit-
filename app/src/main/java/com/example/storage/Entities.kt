package com.example.storage

import androidx.room.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

enum class BrushType {
    PEN,
    PENCIL,
    MARKER,
    CALLIGRAPHY,
    HIGHLIGHTER
}

enum class ShapeType {
    NONE,
    LINE,
    RECTANGLE,
    CIRCLE,
    TRIANGLE,
    ARROW
}

data class CanvasPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f
)

data class Stroke(
    val points: List<CanvasPoint>,
    val color: Int,
    val width: Float,
    val alpha: Float,
    val brushType: BrushType,
    val shapeType: ShapeType = ShapeType.NONE
)

class StrokesConverter {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val strokeListType = Types.newParameterizedType(List::class.java, Stroke::class.java)
    private val adapter = moshi.adapter<List<Stroke>>(strokeListType)

    @TypeConverter
    fun fromJson(json: String): List<Stroke>? {
        return try {
            adapter.fromJson(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun toJson(strokes: List<Stroke>): String {
        return try {
            adapter.toJson(strokes)
        } catch (e: Exception) {
            "[]"
        }
    }
}

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val created: Long = System.currentTimeMillis(),
    val modified: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("notebookId")]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val notebookId: Int,
    val pageIndex: Int,
    val backgroundType: String = "BLACK_CANVAS", // "BLANK", "GRID", "RULED", "DOTTED", "GRAPH", "BLACK_CANVAS"
    val backgroundColor: Int = 0xFF000000.toInt(),
    val backgroundOpacity: Float = 1.0f,
    val strokesJson: String = "[]"
) {
    @Ignore
    private var cachedStrokes: List<Stroke>? = null

    fun getStrokes(converter: StrokesConverter): List<Stroke> {
        val cached = cachedStrokes
        if (cached != null) return cached
        val decoded = converter.fromJson(strokesJson) ?: emptyList()
        cachedStrokes = decoded
        return decoded
    }
}

@Dao
interface NoteXDao {
    @Query("SELECT * FROM notebooks ORDER BY modified DESC")
    fun getAllNotebooks(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getNotebookById(id: Int): NotebookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebook(notebook: NotebookEntity): Long

    @Update
    suspend fun updateNotebook(notebook: NotebookEntity)

    @Delete
    suspend fun deleteNotebook(notebook: NotebookEntity)

    @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY pageIndex ASC")
    fun getPagesForNotebook(notebookId: Int): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY pageIndex ASC")
    suspend fun getPagesForNotebookSync(notebookId: Int): List<PageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity): Long

    @Update
    suspend fun updatePage(page: PageEntity)

    @Delete
    suspend fun deletePage(page: PageEntity)

    @Query("DELETE FROM pages WHERE notebookId = :notebookId")
    suspend fun deletePagesForNotebook(notebookId: Int)
}

@Database(entities = [NotebookEntity::class, PageEntity::class], version = 1, exportSchema = false)
@TypeConverters(StrokesConverter::class)
abstract class NoteXDatabase : RoomDatabase() {
    abstract fun dao(): NoteXDao

    companion object {
        @Volatile
        private var INSTANCE: NoteXDatabase? = null

        fun getDatabase(context: android.content.Context): NoteXDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteXDatabase::class.java,
                    "notex_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
