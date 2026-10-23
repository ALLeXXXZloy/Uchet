package uz.uchet

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val MONTH_FMT = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))
private val GREEN = Color(0xFF2E7D32)
private val RED = Color(0xFFC62828)

fun Long.money(): String {
    val whole = String.format(Locale.US, "%,d", this / 100).replace(',', ' ')
    val frac = this % 100
    return if (frac == 0L) whole else "$whole.%02d".format(frac)
}

class VM(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    val month = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val txs = month.flatMapLatest { m ->
        val z = ZoneId.systemDefault()
        dao.observe(
            m.atDay(1).atStartOfDay(z).toInstant().toEpochMilli(),
            m.plusMonths(1).atDay(1).atStartOfDay(z).toInstant().toEpochMilli()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cards = dao.cards().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(t: Tx) { viewModelScope.launch { dao.upsert(t) } }
    fun delete(t: Tx) { viewModelScope.launch { dao.delete(t) } }
    fun shift(n: Long) { month.value = month.value.plusMonths(n) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { App() } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: VM = viewModel()) {
    val month by vm.month.collectAsState()
    val txs by vm.txs.collectAsState()
    val cards by vm.cards.collectAsState()
    var editing by remember { mutableStateOf<Tx?>(null) }
    var adding by remember { mutableStateOf(false) }

    val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
    val expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    val zone = ZoneId.systemDefault()
    val byDay = txs.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ vm.shift(-1) }) { Icon(Icons.Default.ChevronLeft, "Назад") }
                    Text(month.format(MONTH_FMT).replaceFirstChar { it.uppercase() })
                    IconButton({ vm.shift(1) }) { Icon(Icons.Default.ChevronRight, "Вперед") }
                }
            })
        },
        floatingActionButton = {
            FloatingActionButton({ adding = true }) { Icon(Icons.Default.Add, "Добавить") }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SumRow("Доходы", income, GREEN)
                        SumRow("Расходы", expense, RED)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        SumRow("Итого", income - expense, MaterialTheme.colorScheme.onSurface, bold = true)
                    }
                }
            }
            if (txs.isEmpty()) item {
                Text("Операций нет. Нажмите «+».", Modifier.padding(top = 32.dp), color = Color.Gray)
            }
            byDay.forEach { (day, list) ->
                item { Text(day.format(DATE_FMT), Modifier.padding(top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge, color = Color.Gray) }
                items(list, key = { it.id }) { t -> TxRow(t) { editing = t } }
            }
        }
    }

    if (adding || editing != null) {
        TxDialog(
            initial = editing, cards = cards,
            onDismiss = { adding = false; editing = null },
            onSave = { vm.save(it); adding = false; editing = null },
            onDelete = editing?.let { e -> { vm.delete(e); editing = null } }
        )
    }
}

@Composable
fun SumRow(label: String, v: Long, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else null)
        Text("${v.money()} UZS", color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
fun TxRow(t: Tx, onClick: () -> Unit) {
    val (sign, color) = when (t.type) {
        TxType.INCOME -> "+" to GREEN
        TxType.EXPENSE -> "−" to RED
        TxType.TRANSFER -> "" to Color.Gray
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t.description.ifBlank { t.type.label }, maxLines = 2)
            val time = Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            Text(listOfNotNull(time, t.cardLast4?.let { "*$it" }, if (t.type == TxType.TRANSFER) "перевод" else null)
                .joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Text("$sign${t.amountMinor.money()}", color = color, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxDialog(initial: Tx?, cards: List<String>, onDismiss: () -> Unit, onSave: (Tx) -> Unit, onDelete: (() -> Unit)?) {
    val zone = ZoneId.systemDefault()
    val initDt = initial?.let { Instant.ofEpochMilli(it.timestamp).atZone(zone) } ?: ZonedDateTime.now(zone)

    var type by remember { mutableStateOf(initial?.type ?: TxType.EXPENSE) }
    var amount by remember { mutableStateOf(initial?.let { BigDecimal(it.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString() } ?: "") }
    var card by remember { mutableStateOf(initial?.cardLast4 ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var date by remember { mutableStateOf(initDt.format(DATE_FMT)) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новая операция" else "Изменить") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TxType.values().forEachIndexed { i, t ->
                        SegmentedButton(type == t, { type = t },
                            SegmentedButtonDefaults.itemShape(i, TxType.values().size)) { Text(t.label) }
                    }
                }
                OutlinedTextField(amount, { amount = it }, label = { Text("Сумма, UZS") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(desc, { desc = it }, label = { Text("Описание") })
                OutlinedTextField(card, { card = it.filter(Char::isDigit).take(4) },
                    label = { Text("Карта (последние 4 цифры)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (cards.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cards.forEach { c -> FilterChip(card == c, { card = c }, label = { Text("*$c") }) }
                }
                OutlinedTextField(date, { date = it }, label = { Text("Дата (дд.мм.гггг)") }, singleLine = true)
                error?.let { Text(it, color = RED) }
            }
        },
        confirmButton = {
            TextButton({
                val minor = runCatching {
                    BigDecimal(amount.replace(" ", "").replace(',', '.')).movePointRight(2).toLong()
                }.getOrNull()
                val d = runCatching { LocalDate.parse(date.trim(), DATE_FMT) }.getOrNull()
                when {
                    minor == null || minor <= 0 -> error = "Введите сумму больше нуля"
                    d == null -> error = "Дата: дд.мм.гггг"
                    card.isNotEmpty() && card.length != 4 -> error = "Нужны ровно 4 цифры карты"
                    else -> onSave(Tx(
                        id = initial?.id ?: 0, amountMinor = minor, type = type,
                        cardLast4 = card.ifEmpty { null }, description = desc.trim(),
                        timestamp = d.atTime(initDt.toLocalTime()).atZone(zone).toInstant().toEpochMilli(),
                        source = initial?.source ?: "MANUAL"
                    ))
                }
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(it) { Text("Удалить", color = RED) } }
                TextButton(onDismiss) { Text("Отмена") }
            }
        }
    )
}
