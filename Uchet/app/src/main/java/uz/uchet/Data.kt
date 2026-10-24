package uz.uchet

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class TxType(val label: String) {
    INCOME("Доход"),
    EXPENSE("Расход"),
    TRANSFER("Перевод")
}

@Entity(tableName = "txs")
data class Tx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val type: TxType,
    val cardLast4: String? = null,
    val description: String = "",
    val category: String = "📦 Другое",
    val timestamp: Long,
    val source: String = "MANUAL"
)

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val last4: String,
    val name: String,
    val initialBalanceMinor: Long
)

// НОВАЯ ТАБЛИЦА: Бюджеты (лимиты на категории)
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val limitMinor: Long
)

@Dao
interface TxDao {
    @Query("SELECT * FROM txs WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun observe(start: Long, end: Long): Flow<List<Tx>>

    @Query("SELECT * FROM txs")
    fun observeAll(): Flow<List<Tx>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tx: Tx)

    @Delete
    suspend fun delete(tx: Tx)

    @Query("SELECT * FROM cards")
    fun observeCards(): Flow<List<CardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: CardEntity)

    @Query("DELETE FROM cards WHERE last4 = :last4")
    suspend fun deleteCard(last4: String)

    // Методы для бюджетов
    @Query("SELECT * FROM budgets")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun deleteBudget(category: String)
}

// ОБНОВЛЕНА ВЕРСИЯ ДО 4
@Database(entities = [Tx::class, CardEntity::class, BudgetEntity::class], version = 4, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): TxDao

    companion object {
        @Volatile private var INSTANCE: AppDb? = null

        fun get(context: Context): AppDb {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDb::class.java, "uchet.db")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
