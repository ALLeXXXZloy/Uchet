package uz.uchet

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class TxType(val label: String) { INCOME("Доход"), EXPENSE("Расход"), TRANSFER("Перевод") }

@Entity(tableName = "transactions")
data class Tx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,          // тийины, без Double
    val type: TxType,
    val cardLast4: String?,
    val description: String,
    val timestamp: Long,
    val source: String = "MANUAL"   // позже: SMS / NOTIFICATION
)

class Converters {
    @TypeConverter fun toType(s: String) = TxType.valueOf(s)
    @TypeConverter fun fromType(t: TxType) = t.name
}

@Dao
interface TxDao {
    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observe(from: Long, to: Long): Flow<List<Tx>>

    @Query("SELECT DISTINCT cardLast4 FROM transactions WHERE cardLast4 IS NOT NULL ORDER BY cardLast4")
    fun cards(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(tx: Tx)
    @Delete suspend fun delete(tx: Tx)
}

@Database(entities = [Tx::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): TxDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "uchet.db")
                .build().also { inst = it }
        }
    }
}
