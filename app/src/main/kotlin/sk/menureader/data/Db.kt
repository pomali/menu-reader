package sk.menureader.data

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

/**
 * A stored snapshot. The snapshot itself is kept as JSON (it's read-only and replaced as a
 * whole when re-extracted); the other columns are for the history list.
 * [ocrJson] is the raw OCR, so extraction can be re-run and exported as a Phase 0 fixture.
 */
@Entity(tableName = "snapshots")
data class SnapshotEntity(
    @PrimaryKey val id: String,
    val capturedAt: Long,
    val restaurantName: String?,
    val quality: String,
    val dishCount: Int,
    val snapshotJson: String,
    val ocrJson: String,
)

data class SnapshotRow(
    val id: String,
    val capturedAt: Long,
    val restaurantName: String?,
    val quality: String,
    val dishCount: Int,
)

@Dao
interface SnapshotDao {
    @Query("SELECT id, capturedAt, restaurantName, quality, dishCount FROM snapshots ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<SnapshotRow>>

    @Query("SELECT * FROM snapshots WHERE id = :id")
    fun observe(id: String): Flow<SnapshotEntity?>

    @Query("SELECT * FROM snapshots WHERE id = :id")
    suspend fun get(id: String): SnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: SnapshotEntity)

    @Query("UPDATE snapshots SET restaurantName = :name WHERE id = :id")
    suspend fun setRestaurantName(id: String, name: String?)

    @Query("DELETE FROM snapshots WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [SnapshotEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun snapshots(): SnapshotDao

    companion object {
        fun create(ctx: Context) = Room.databaseBuilder(ctx, AppDatabase::class.java, "menu-reader.db").build()
    }
}
