package uz.uchet

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TxType(val label: String) {
    INCOME("Доход"),
    EXPENSE("Расход"),
    TRANSFER("Перевод себе"),
    ADJUSTMENT("Корректировка")
}

@Entity(tableName = "txs")
data class Tx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val type: TxType,
    val cardId: String? = null,
    val toCardId: String? = null, // Добавлено для переводов между картами
    val description: String = "",
    val category: String = "",
    val timestamp: Long,
    val source: String = "MANUAL",
    val currency: String = "UZS",
    val photoPath: String? = null
)

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: String,
    val last4: String,
    val name: String,
    val currency: String,
    val initialBalanceMinor: Long
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val limitMinor: Long
)

@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val type: TxType,
    val cardId: String? = null,
    val description: String = "",
    val category: String = "",
    val dayOfMonth: Int,
    val lastExecutedMonth: String? = null
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val type: TxType
)

@Entity(tableName = "goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetAmountMinor: Long,
    val currentAmountMinor: Long = 0
)

@Entity(tableName = "rates")
data class ExchangeRateEntity(
    @PrimaryKey val currency: String,
    val rateToUzs: Double
)
