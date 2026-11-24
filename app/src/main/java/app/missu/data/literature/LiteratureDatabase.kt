package app.missu.data.literature

import android.content.Context
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Fts5
import androidx.room3.FtsOptions
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

@Entity(
    tableName = "works",
    indices = [Index(value = ["kind", "prominence"]), Index(value = ["prominence"])],
)
data class LiteraryWorkEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val author: String,
    val text: String,
    val chapters: String,
    val source: String,
    val prominence: Int,
    val theme: String,
    val notes: String,
    val translation: String,
    val appreciation: String,
)

data class LiteraryWorkSummaryEntity(
    val id: String,
    val kind: String,
    val title: String,
    val author: String,
    val theme: String,
)

@Fts5(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "works_search")
data class LiteratureSearchEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val tokens: String,
)

@Dao
interface LiteratureDao {
    @Query("SELECT 1")
    suspend fun warmUp(): Int

    @Query("SELECT * FROM works WHERE id = :id")
    suspend fun findById(id: String): LiteraryWorkEntity?

    @Query(
        """
        SELECT id, kind, title, author, theme FROM works
        WHERE (:kind IS NULL OR kind = :kind)
        AND (:favoriteOnly = 0 OR id IN (:favorites))
        AND (:search IS NULL OR rowid IN (
            SELECT rowid FROM works_search WHERE works_search MATCH :search
        ))
        ORDER BY
        CASE :sort WHEN 'TITLE' THEN title WHEN 'AUTHOR' THEN author END COLLATE NOCASE,
        CASE WHEN :sort = 'AUTHOR' THEN title END COLLATE NOCASE,
        prominence DESC, rowid LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun queryWorkSummaries(
        kind: String?,
        favoriteOnly: Boolean,
        favorites: List<String>,
        search: String?,
        sort: String,
        limit: Int,
        offset: Int,
    ): List<LiteraryWorkSummaryEntity>
}

@Database(
    entities = [LiteraryWorkEntity::class, LiteratureSearchEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class LiteratureDatabase : RoomDatabase() {
    abstract fun dao(): LiteratureDao

    companion object {
        fun createCore(context: Context): LiteratureDatabase =
            builder(context, "literature-core.db")
                .createFromAsset("literature-core.db")
                .build()

        fun createFull(context: Context, file: File, roomName: String): LiteratureDatabase =
            builder(context, roomName)
                .createFromFile(file)
                .build()

        fun openExisting(context: Context, roomName: String): LiteratureDatabase =
            builder(context, roomName).build()

        private fun builder(context: Context, name: String) =
            Room.databaseBuilder<LiteratureDatabase>(context.applicationContext, name)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .fallbackToDestructiveMigration()
    }
}
