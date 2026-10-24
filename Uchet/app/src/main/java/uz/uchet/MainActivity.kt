package uz.uchet

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val MONTH_FMT = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))
private val GREEN = Color(0xFF2E7D32)
private val RED = Color(0xFFC62828)
private val YELLOW = Color(0xFFF9A825)

val EXPENSE_CATEGORIES = listOf("🍔 Еда", "🚕 Транспорт", "🏠 Жилье", "🛒 Покупки", "💊 Здоровье", "🎉 Развлечения", "📦 Другое")
val INCOME_CATEGORIES = listOf("💵 Зарплата", "🎁 Подарок", "💸 Перевод", "📈 Инвестиции", "📦 Другое")

fun Long.money(): String {
    val abs = kotlin.math.abs(this)
    val whole = String.format(Locale.US, "%,d", abs / 100).replace(',', ' ')
    val frac = abs % 100
    val sign = if (this < 0) "-" else ""
    return "$sign$whole.%02d".format(frac)
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

    val allTxs = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cards = dao.observeCards().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val budgets = dao.observeBudgets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(t: Tx) { viewModelScope.launch { dao.upsert(t) } }
    fun delete(t: Tx) { viewModelScope.launch { dao.delete(t) } }
    fun shift(n: Long) { month.value = month.value.plusMonths(n) }
    
    fun saveCard(card: CardEntity) { viewModelScope.launch { dao.upsertCard(card) } }
    fun deleteCard(last4: String) { viewModelScope.launch { dao.deleteCard(last4) } }
    
    fun saveBudget(budget: BudgetEntity) { viewModelScope.launch { dao.upsertBudget(budget) } }
    fun deleteBudget(category: String) { viewModelScope.launch { dao.deleteBudget(category) } }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { 
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
            
            MaterialTheme(colorScheme = colorScheme) { 
                Surface { App() } 
            } 
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: VM = viewModel()) {
    val month by vm.month.collectAsState()
    val txs by vm.txs.collectAsState()
    val allTxs by vm.allTxs.collectAsState()
    val cards by vm.cards.collectAsState()
    val budgets by vm.budgets.collectAsState()
    
    var editing by remember { mutableStateOf<Tx?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showCardManager by remember { mutableStateOf(false) }
    var showBudgetManager by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<TxType?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
    val expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    val zone = ZoneId.systemDefault()
    
    val filteredTxs = txs.filter { t ->
        (searchQuery.isEmpty() || t.description.contains(searchQuery, true) || t.category.contains(searchQuery, true)) &&
        (filterType == null || t.type == filterType)
    }
    val byDay = filteredTxs.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }

    val cardBalances = cards.associate { card ->
        val cardTxs = allTxs.filter { it.cardLast4 == card.last4 || (card.last4 == "CASH" && it.cardLast4 == null) }
        val cardIncome = cardTxs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
        val cardExpense = cardTxs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
        card.last4 to (card.initialBalanceMinor + cardIncome - cardExpense)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ vm.shift(-1) }) { Icon(Icons.Default.ChevronLeft, "Назад") }
                        Text(month.format(MONTH_FMT).replaceFirstChar { it.uppercase() })
                        IconButton({ vm.shift(1) }) { Icon(Icons.Default.ChevronRight, "Вперед") }
                    }
                },
                actions = {
                    // Кнопка Бюджетов
                    IconButton(onClick = { showBudgetManager = true }) {
                        Icon(Icons.Default.Savings, contentDescription = "Бюджеты")
                    }
                    IconButton(onClick = { showCardManager = true }) {
                        Icon(Icons.Default.CreditCard, contentDescription = "Мои карты")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton({ adding = true }) { Icon(Icons.Default.Add, "Добавить") }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp)) {
            
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск по операциям...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filterType == null, onClick = { filterType = null }, label = { Text("Все") })
                    FilterChip(selected = filterType == TxType.INCOME, onClick = { filterType = TxType.INCOME }, label = { Text("Доходы") })
                    FilterChip(selected = filterType == TxType.EXPENSE, onClick = { filterType = TxType.EXPENSE }, label = { Text("Расходы") })
                }
                Spacer(Modifier.height(16.dp))
            }

            // БЛОК БЮДЖЕТОВ
            if (budgets.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Бюджеты на месяц", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            
                            budgets.forEach { budget ->
                                val spent = txs.filter { it.type == TxType.EXPENSE && it.category == budget.category }.sumOf { it.amountMinor }
                                val progress = if (budget.limitMinor > 0) spent.toFloat() / budget.limitMinor.toFloat() else 0f
                                val progressColor = when {
                                    progress >= 1f -> RED
                                    progress >= 0.8f -> YELLOW
                                    else -> GREEN
                                }
                                
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(budget.category, fontWeight = FontWeight.Medium)
                                        Text("${spent.money()} / ${budget.limitMinor.money()}", style = MaterialTheme.typography.bodySmall, color = if (progress >= 1f) RED else Color.Unspecified)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { progress.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(8.dp),
                                        color = progressColor,
                                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (cards.isNotEmpty()) {
                item {
                    Text("Мои счета", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cards.forEach { card ->
                            val currentBalance = cardBalances[card.last4] ?: card.initialBalanceMinor
                            Card(
                                modifier = Modifier.width(140.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(card.name, style = MaterialTheme.typography.labelMedium)
                                    Text("${currentBalance.money()} UZS", fontWeight = FontWeight.Bold, color = if (currentBalance >= 0) GREEN else RED)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            item {
                val expensesByCategory = txs.filter { it.type == TxType.EXPENSE }
                    .groupBy { it.category }
                    .mapValues { (_, list) -> list.sumOf { it.amountMinor } }
                
                if (expensesByCategory.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Расходы по категориям", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(16.dp))
                            CategoryPieChart(expensesByCategory)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SumRow("Доходы за месяц", income, GREEN)
                        SumRow("Расходы за месяц", expense, RED)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        SumRow("Итого за месяц", income - expense, MaterialTheme.colorScheme.onSurface, bold = true)
                    }
                }
            }
            
            if (filteredTxs.isEmpty()) item {
                Text("Ничего не найдено.", Modifier.padding(top = 32.dp), color = Color.Gray)
            }
            
            byDay.forEach { (day, list) ->
                item { Text(day.format(DATE_FMT), Modifier.padding(top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge, color = Color.Gray) }
                items(list, key = { it.id }) { t -> 
                    TxRow(t) { editing = t } 
                }
            }
        }
    }

    if (adding || editing != null) {
        TxDialog(
            initial = editing, cards = cards,
            onDismiss = { adding = false; editing = null },
            onSave = { vm.save(it); adding = false; editing = null },
            onDelete = editing?.let { e -> { 
                vm.delete(e); editing = null 
                scope.launch { 
                    val timerJob = launch {
                        delay(5000)
                        snackbarHostState.currentSnackbarData?.dismiss()
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = "Операция удалена",
                        actionLabel = "Отменить",
                        duration = SnackbarDuration.Indefinite
                    )
                    timerJob.cancel()
                    if (result == SnackbarResult.ActionPerformed) vm.save(e)
                }
            } }
        )
    }

    if (showCardManager) {
        CardManagerDialog(
            cards = cards,
            cardBalances = cardBalances,
            allTxs = allTxs,
            onSave = { vm.saveCard(it) },
            onDelete = { vm.deleteCard(it) },
            onDismiss = { showCardManager = false }
        )
    }

    if (showBudgetManager) {
        BudgetManagerDialog(
            budgets = budgets,
            onSave = { vm.saveBudget(it) },
            onDelete = { vm.deleteBudget(it) },
            onDismiss = { showBudgetManager = false }
        )
    }
}

@Composable
fun CategoryPieChart(data: Map<String, Long>) {
    val total = data.values.sum().toFloat()
    if (total == 0f) return
    
    val colors = listOf(
        Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), 
        Color(0xFFFFB74D), Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFA1887F)
    )
    val sortedData = data.entries.sortedByDescending { it.value }

    Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(110.dp).padding(8.dp)) {
            var startAngle = -90f
            sortedData.forEachIndexed { index, entry ->
                val sweepAngle = (entry.value / total) * 360f
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true
                )
                startAngle += sweepAngle
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            sortedData.take(4).forEachIndexed { index, entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(colors[index % colors.size], shape = CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("${entry.key}: ${(entry.value / total * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
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
            Text("${t.category}  ${t.description}".trim(), maxLines = 2)
            val time = Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            val cardText = if (t.cardLast4.isNullOrEmpty()) "Наличные" else "*${t.cardLast4}"
            Text(listOfNotNull(time, cardText).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Text("$sign${t.amountMinor.money()}", color = color, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxDialog(initial: Tx?, cards: List<CardEntity>, onDismiss: () -> Unit, onSave: (Tx) -> Unit, onDelete: (() -> Unit)?) {
    val zone = ZoneId.systemDefault()
    val initDt = initial?.let { Instant.ofEpochMilli(it.timestamp).atZone(zone) } ?: ZonedDateTime.now(zone)

    var type by remember { mutableStateOf(initial?.type ?: TxType.EXPENSE) }
    var category by remember { mutableStateOf(initial?.category ?: EXPENSE_CATEGORIES.last()) }
    var amount by remember { mutableStateOf(initial?.let { BigDecimal(it.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString() } ?: "") }
    var selectedCard by remember { mutableStateOf(initial?.cardLast4 ?: "CASH") }
    var isCustomCard by remember { mutableStateOf(false) }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var date by remember { mutableStateOf(initDt.format(DATE_FMT)) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(type) {
        val currentList = if (type == TxType.INCOME) INCOME_CATEGORIES else EXPENSE_CATEGORIES
        if (!currentList.contains(category)) category = currentList.last()
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isLoading = true
            error = null
            val bitmap = uriToBitmap(context, it)
            bitmap?.let { bmp ->
                val image = InputImage.fromBitmap(bmp, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val parsedData = parseSmsText(visionText.text)
                        if (parsedData != null) {
                            amount = parsedData.amountStr 
                            desc = parsedData.description
                            category = parsedData.category
                            if (parsedData.card != null) {
                                selectedCard = parsedData.card
                                if (cards.none { it.last4 == selectedCard }) isCustomCard = true
                            }
                        } else {
                            error = "Не удалось найти сумму в тексте"
                        }
                        isLoading = false
                    }
                    .addOnFailureListener { 
                        isLoading = false
                        error = "Не удалось распознать текст"
                    }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новая операция" else "Изменить") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TxType.values().forEachIndexed { i, t ->
                        SegmentedButton(type == t, { type = t },
                            SegmentedButtonDefaults.itemShape(i, TxType.values().size)) { Text(t.label) }
                    }
                }

                Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
                    Text(if (isLoading) "Распознавание..." else "📸 Загрузить скриншот СМС")
                }

                OutlinedTextField(amount, { amount = it }, label = { Text("Сумма (UZS)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))

                Text("Категория:", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currentCategories = if (type == TxType.INCOME) INCOME_CATEGORIES else EXPENSE_CATEGORIES
                    currentCategories.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }

                Text("Счет оплаты:", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cards.forEach { c ->
                        FilterChip(selected = selectedCard == c.last4 && !isCustomCard, onClick = { selectedCard = c.last4; isCustomCard = false }, label = { Text(c.name) })
                    }
                    FilterChip(selected = isCustomCard, onClick = { isCustomCard = true; selectedCard = "" }, label = { Text("Другая...") })
                }
                
                if (isCustomCard) {
                    OutlinedTextField(value = selectedCard, onValueChange = { selectedCard = it.filter(Char::isDigit).take(4) },
                        label = { Text("Последние 4 цифры карты") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }

                OutlinedTextField(desc, { desc = it }, label = { Text("Описание") })
                OutlinedTextField(date, { date = it }, label = { Text("Дата (дд.мм.гггг)") }, singleLine = true)
                error?.let { Text(it, color = RED) }
            }
        },
        confirmButton = {
            TextButton({
                val minor = runCatching { 
                    BigDecimal(amount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() 
                }.getOrNull()
                
                val d = runCatching { LocalDate.parse(date.trim(), DATE_FMT) }.getOrNull()
                when {
                    minor == null || minor <= 0 -> error = "Введите сумму больше нуля"
                    d == null -> error = "Дата: дд.мм.гггг"
                    isCustomCard && selectedCard.length != 4 -> error = "Нужны ровно 4 цифры карты"
                    else -> {
                        val finalCard = if (selectedCard.isEmpty() || selectedCard == "CASH") null else selectedCard
                        onSave(Tx(id = initial?.id ?: 0, amountMinor = minor, type = type, cardLast4 = finalCard,
                            description = desc.trim(), category = category,
                            timestamp = d.atTime(initDt.toLocalTime()).atZone(zone).toInstant().toEpochMilli(),
                            source = initial?.source ?: "MANUAL"))
                    }
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

// НОВЫЙ ДИАЛОГ ДЛЯ НАСТРОЙКИ БЮДЖЕТОВ
@Composable
fun BudgetManagerDialog(
    budgets: List<BudgetEntity>,
    onSave: (BudgetEntity) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(EXPENSE_CATEGORIES.first()) }
    var limitInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Бюджеты (лимиты)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Установить лимит на месяц:", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                
                // Выбор категории
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EXPENSE_CATEGORIES.forEach { c ->
                        FilterChip(selected = selectedCategory == c, onClick = { selectedCategory = c }, label = { Text(c) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                
                // Ввод суммы
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it },
                    label = { Text("Лимит (UZS)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        val limitMinor = runCatching { 
                            BigDecimal(limitInput.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() 
                        }.getOrNull()
                        if (limitMinor != null && limitMinor > 0) {
                            onSave(BudgetEntity(category = selectedCategory, limitMinor = limitMinor))
                            limitInput = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сохранить лимит") }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                
                Text("Ваши лимиты:", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                if (budgets.isEmpty()) {
                    Text("Лимиты не установлены.", color = Color.Gray)
                }
                budgets.forEach { b ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.category, fontWeight = FontWeight.Bold)
                            Text("Лимит: ${b.limitMinor.money()} UZS", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        IconButton({ onDelete(b.category) }) { Icon(Icons.Default.Delete, "Удалить", tint = RED) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
fun CardManagerDialog(
    cards: List<CardEntity>,
    cardBalances: Map<String, Long>,
    allTxs: List<Tx>,
    onSave: (CardEntity) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var last4 by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var initialBalance by remember { mutableStateOf("") }
    
    var editingCard by remember { mutableStateOf<CardEntity?>(null) }
    var actualBalanceInput by remember { mutableStateOf("") }

    if (editingCard != null) {
        val card = editingCard!!
        val currentMathBalance = cardBalances[card.last4] ?: card.initialBalanceMinor
        
        AlertDialog(
            onDismissRequest = { editingCard = null },
            title = { Text("Корректировка: ${card.name}") },
            text = {
                Column {
                    Text("Приложение считает: ${currentMathBalance.money()} UZS", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Введите РЕАЛЬНЫЙ остаток:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = actualBalanceInput,
                        onValueChange = { actualBalanceInput = it },
                        label = { Text("Реальный баланс (UZS)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Разница пойдет в стартовый баланс.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val actualMinor = runCatching { 
                        BigDecimal(actualBalanceInput.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() 
                    }.getOrNull()
                    
                    if (actualMinor != null) {
                        val difference = actualMinor - currentMathBalance
                        val newInitial = card.initialBalanceMinor + difference
                        onSave(card.copy(initialBalanceMinor = newInitial))
                        editingCard = null
                        actualBalanceInput = ""
                    }
                }) { Text("Применить") }
            },
            dismissButton = { TextButton(onClick = { editingCard = null }) { Text("Отмена") } }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Мои счета и карты") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Добавить новый:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название (напр. Наличные)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = last4, onValueChange = { last4 = it.uppercase() }, label = { Text("Номер (CASH или 4 цифры)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = initialBalance, onValueChange = { initialBalance = it }, label = { Text("Начальный баланс (UZS)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val balanceMinor = runCatching { 
                                BigDecimal(initialBalance.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() 
                            }.getOrNull() ?: 0L
                            
                            if (name.isNotBlank() && last4.isNotBlank()) {
                                onSave(CardEntity(last4 = last4, name = name, initialBalanceMinor = balanceMinor))
                                name = ""; last4 = ""; initialBalance = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Добавить") }

                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    
                    Text("Ваши счета:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    if (cards.isEmpty()) {
                        Text("У вас пока нет добавленных счетов.", color = Color.Gray)
                    }
                    cards.forEach { c ->
                        val currentBal = cardBalances[c.last4] ?: c.initialBalanceMinor
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.Bold)
                                Text("Текущий баланс: ${currentBal.money()} UZS", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            IconButton(onClick = { 
                                editingCard = c
                                actualBalanceInput = BigDecimal(currentBal).movePointLeft(2).stripTrailingZeros().toPlainString()
                            }) { 
                                Icon(Icons.Default.Edit, "Корректировать", tint = MaterialTheme.colorScheme.primary) 
                            }
                            IconButton({ onDelete(c.last4) }) { Icon(Icons.Default.Delete, "Удалить", tint = RED) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
        )
    }
}

fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) { null }
}

data class ParsedSms(val amountStr: String, val card: String?, val description: String, val category: String)

fun parseSmsText(text: String): ParsedSms? {
    val lowerText = text.lowercase()
    val amountRegex = Regex("""([\d\s]+(?:[.,]\d{1,2})?)\s*(?:uzs|сум|rub|руб|\$|€)""", RegexOption.IGNORE_CASE)
    val amountMatch = amountRegex.find(text)
    
    val amountStr = amountMatch?.groupValues?.get(1)?.replace(" ", "")?.replace(",", ".") ?: return null
    if (amountStr.toBigDecimalOrNull() == null) return null

    val cardRegex = Regex("""(?:карта|card|\*|х)\s*(\d{4})""", RegexOption.IGNORE_CASE)
    val cardMatch = cardRegex.find(text)
    val card = cardMatch?.groupValues?.get(1)?.take(4)

    val description = text.take(40).replace("\n", " ").trim()
    val category = when {
        lowerText.contains("магнит") || lowerText.contains("пятерочка") || lowerText.contains("корзинка") || lowerText.contains("еда") || lowerText.contains("кафе") || lowerText.contains("ресторан") -> "🍔 Еда"
        lowerText.contains("такси") || lowerText.contains("яндекс go") || lowerText.contains("бензин") || lowerText.contains("метро") || lowerText.contains("транспорт") -> "🚕 Транспорт"
        lowerText.contains("аптека") || lowerText.contains("клиника") || lowerText.contains("здоровье") -> "💊 Здоровье"
        lowerText.contains("аренда") || lowerText.contains("квартплата") || lowerText.contains("коммунал") -> "🏠 Жилье"
        lowerText.contains("зарплата") || lowerText.contains("аванс") -> "💵 Зарплата"
        lowerText.contains("перевод") -> "💸 Перевод"
        else -> "📦 Другое"
    }
    return ParsedSms(amountStr, card, description, category)
}
