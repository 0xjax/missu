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

@Entity(
    tableName = "works",
    indices = [Index(value = ["kind"]), Index(value = ["prominence"])],
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

@Fts5(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "works_search")
data class LiteratureSearchEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val tokens: String,
)

@Dao
interface LiteratureDao {
    @Query("SELECT * FROM works WHERE id = :id")
    suspend fun findById(id: String): LiteraryWorkEntity?

    @Query(
        """
        SELECT * FROM works
        WHERE (:kind IS NULL OR kind = :kind)
        AND (:favoriteOnly = 0 OR id IN (:favorites))
        AND (:search IS NULL OR rowid IN (
            SELECT rowid FROM works_search WHERE works_search MATCH :search
        ))
        ORDER BY prominence DESC, rowid LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun queryWorks(
        kind: String?,
        favoriteOnly: Boolean,
        favorites: List<String>,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<LiteraryWorkEntity>
}

@Database(
    entities = [LiteraryWorkEntity::class, LiteratureSearchEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class LiteratureDatabase : RoomDatabase() {
    abstract fun dao(): LiteratureDao

    companion object {
        fun create(context: Context): LiteratureDatabase =
            Room.databaseBuilder<LiteratureDatabase>(context.applicationContext, "literature.db")
                .createFromAsset("literature.db")
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .fallbackToDestructiveMigration()
                .build()
    }
}
