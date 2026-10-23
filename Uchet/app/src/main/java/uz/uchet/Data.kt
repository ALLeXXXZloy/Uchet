package uz.uchet

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
    val cardLast4: String? = null, // null означает "Наличные"
    val description: String = "",
    val timestamp: Long,
    val source: String = "MANUAL"
)

// Новая таблица для карт и кошельков
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val last4: String, // "CASH" для наличных, или 4 цифры карты
    val name: String,
    val initialBalanceMinor: Long
)

@Dao
interface TxDao {
    @Query("SELECT * FROM txs WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun observe(start: Long, end: Long): Flow<List<Tx>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tx: Tx)

    @Delete
    suspend fun delete(tx: Tx)

    @Query("SELECT * FROM txs")
    suspend fun getAll(): List<Tx>

    // Методы для работы с картами
    @Query("SELECT * FROM cards")
    fun observeCards(): Flow<List<CardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: CardEntity)

    @Query("DELETE FROM cards WHERE last4 = :last4")
    suspend fun deleteCard(last4: String)
}
