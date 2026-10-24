package uz.uchet

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
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
import org.json.JSONArray
import org.json.JSONObject
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
    val recurring = dao.observeRecurring().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { checkAndExecuteRecurring() }
    }

    private suspend fun checkAndExecuteRecurring() {
        val now = LocalDate.now()
        val currentMonthStr = YearMonth.now().toString()
        val currentDay = now.dayOfMonth

        val templates = dao.getRecurringList()
        for (t in templates) {
            if (t.dayOfMonth <= currentDay && t.lastExecutedMonth != currentMonthStr) {
                val tx = Tx(
                    amountMinor = t.amountMinor, type = t.type, cardLast4 = t.cardLast4,
                    description = t.description, category = t.category,
                    timestamp = now.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    source = "AUTO"
                )
                dao.upsert(tx)
                dao.upsertRecurring(t.copy(lastExecutedMonth = currentMonthStr))
            }
        }
    }

    fun save(t: Tx) { viewModelScope.launch { dao.upsert(t) } }
    fun delete(t: Tx) { viewModelScope.launch { dao.delete(t) } }
    fun shift(n: Long) { month.value = month.value.plusMonths(n) }
    fun saveCard(card: CardEntity) { viewModelScope.launch { dao.upsertCard(card) } }
    fun deleteCard(last4: String) { viewModelScope.launch { dao.deleteCard(last4) } }
    fun saveBudget(budget: BudgetEntity) { viewModelScope.launch { dao.upsertBudget(budget) } }
    fun deleteBudget(category: String) { viewModelScope.launch { dao.deleteBudget(category) } }
    fun saveRecurring(r: RecurringEntity) { viewModelScope.launch { dao.upsertRecurring(r) } }
    fun deleteRecurring(r: RecurringEntity) { viewModelScope.launch { dao.deleteRecurring(r) } }

    fun createBackup(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val allTx = allTxs.first()
                val allCards = cards.first()
                val allBudgets = budgets.first()
                val allRecurring = dao.getRecurringList()

                val root = JSONObject()

                val txsArray = JSONArray()
                allTx.forEach { t ->
                    txsArray.put(JSONObject().apply {
                        put("id", t.id); put("amountMinor", t.amountMinor); put("type", t.type.name)
                        put("cardLast4", t.cardLast4 ?: JSONObject.NULL)
                        put("description", t.description); put("category", t.category)
                        put("timestamp", t.timestamp); put("source", t.source)
                    })
                }
                root.put("txs", txsArray)

                val cardsArray = JSONArray()
                allCards.forEach { c ->
                    cardsArray.put(JSONObject().apply {
                        put("last4", c.last4); put("name", c.name); put("initialBalanceMinor", c.initialBalanceMinor)
                    })
                }
                root.put("cards", cardsArray)

                val budgetsArray = JSONArray()
                allBudgets.forEach { b ->
                    budgetsArray.put(JSONObject().apply {
                        put("category", b.category); put("limitMinor", b.limitMinor)
                    })
                }
                root.put("budgets", budgetsArray)

                val recurringArray = JSONArray()
                allRecurring.forEach { r ->
                    recurringArray.put(JSONObject().apply {
                        put("id", r.id); put("amountMinor", r.amountMinor); put("type", r.type.name)
                        put("cardLast4", r.cardLast4 ?: JSONObject.NULL)
                        put("description", r.description); put("category", r.category)
                        put("dayOfMonth", r.dayOfMonth); put("lastExecutedMonth", r.lastExecutedMonth)
                    })
                }
                root.put("recurring", recurringArray)

                onResult(root.toString(2))
            } catch (e: Exception) {
                onResult("")
            }
        }
    }

    fun restoreBackup(jsonString: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val root = JSONObject(jsonString)
                dao.clearTxs(); dao.clearCards(); dao.clearBudgets(); dao.clearRecurring()

                val txsArray = root.optJSONArray("txs")
                if (txsArray != null) {
                    for (i in 0 until txsArray.length()) {
                        val o = txsArray.getJSONObject(i)
                        dao.upsert(Tx(
                            id = o.optLong("id"), amountMinor = o.getLong("amountMinor"),
                            type = TxType.valueOf(o.getString("type")),
                            cardLast4 = if (o.isNull("cardLast4")) null else o.getString("cardLast4"),
                            description = o.optString("description"), category = o.optString("category"),
                            timestamp = o.getLong("timestamp"), source = o.optString("source")
                        ))
                    }
                }

                val cardsArray = root.optJSONArray("cards")
                if (cardsArray != null) {
                    for (i in 0 until cardsArray.length()) {
                        val o = cardsArray.getJSONObject(i)
                        dao.upsertCard(CardEntity(
                            last4 = o.getString("last4"), name = o.getString("name"),
                            initialBalanceMinor = o.getLong("initialBalanceMinor")
                        ))
                    }
                }

                val budgetsArray = root.optJSONArray("budgets")
                if (budgetsArray != null) {
                    for (i in 0 until budgetsArray.length()) {
                        val o = budgetsArray.getJSONObject(i)
                        dao.upsertBudget(BudgetEntity(category = o.getString("category"), limitMinor = o.getLong("limitMinor")))
                    }
                }

                val recurringArray = root.optJSONArray("recurring")
                if (recurringArray != null) {
                    for (i in 0 until recurringArray.length()) {
                        val o = recurringArray.getJSONObject(i)
                        dao.upsertRecurring(RecurringEntity(
                            id = o.optLong("id"), amountMinor = o.getLong("amountMinor"),
                            type = TxType.valueOf(o.getString("type")),
                            cardLast4 = if (o.isNull("cardLast4")) null else o.getString("cardLast4"),
                            description = o.optString("description"), category = o.optString("category"),
                            dayOfMonth = o.getInt("dayOfMonth"), lastExecutedMonth = o.optString("lastExecutedMonth")
                        ))
                    }
                }
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { 
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) { 
                Surface {
                    var isAuthenticated by remember { mutableStateOf(false) }
                    if (isAuthenticated) App() else LockScreen(activity = this@MainActivity, onUnlocked = { isAuthenticated = true })
                } 
            } 
        }
    }
}

@Composable
fun LockScreen(activity: FragmentActivity, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    
    val prefs = activity.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
    val savedPin = prefs.getString("pin", "0000") ?: "0000"
    val useBiometric = prefs.getBoolean("use_biometric", true) // По умолчанию включено
    
    val biometricManager = BiometricManager.from(activity)
    val canUseBiometric = useBiometric && biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Приложение заблокировано", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter { c -> c.isDigit() }.take(4); error = false },
            label = { Text("PIN-код") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true, isError = error,
            supportingText = { if (error) Text("Неверный PIN-код", color = RED) }
        )
        Spacer(Modifier.height(16.dp))
        
        Button(onClick = { if (pin == savedPin) onUnlocked() else { pin = ""; error = true } }, modifier = Modifier.fillMaxWidth()) {
            Text("Войти")
        }
        
        if (canUseBiometric) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                val executor = ContextCompat.getMainExecutor(activity)
                val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result); onUnlocked()
                    }
                })
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Вход в приложение").setSubtitle("Приложите палец")
                    .setNegativeButtonText("Использовать PIN").build()
                biometricPrompt.authenticate(promptInfo)
            }) { Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Использовать отпечаток") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: VM = viewModel()) {
    val context = LocalContext.current
    val month by vm.month.collectAsState()
    val txs by vm.txs.collectAsState()
    val allTxs by vm.allTxs.collectAsState()
    val cards by vm.cards.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val recurring by vm.recurring.collectAsState()
    
    var editing by remember { mutableStateOf<Tx?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showCardManager by remember { mutableStateOf(false) }
    var showBudgetManager by remember { mutableStateOf(false) }
    var showRecurringManager by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf("") }

    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<TxType?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val csvExportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream -> stream.write(generateCsv(allTxs).toByteArray()) }
                scope.launch { snackbarHostState.showSnackbar("CSV файл сохранен!") }
            } catch (e: Exception) { scope.launch { snackbarHostState.showSnackbar("Ошибка: ${e.message}") } }
        }
    }

    val jsonExportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let {
            vm.createBackup { jsonString ->
                if (jsonString.isNotEmpty()) {
                    try {
                        context.contentResolver.openOutputStream(it)?.use { stream -> stream.write(jsonString.toByteArray()) }
                        scope.launch { snackbarHostState.showSnackbar("Резервная копия сохранена!") }
                    } catch (e: Exception) { scope.launch { snackbarHostState.showSnackbar("Ошибка: ${e.message}") } }
                } else {
                    scope.launch { snackbarHostState.showSnackbar("Ошибка создания копии") }
                }
            }
        }
    }

    val jsonImportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val jsonString = stream.bufferedReader().readText()
                    pendingRestoreJson = jsonString
                    showRestoreConfirm = true
                }
            } catch (e: Exception) { scope.launch { snackbarHostState.showSnackbar("Ошибка чтения файла") } }
        }
    }

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

    LaunchedEffect(cardBalances) {
        val total = cardBalances.values.sum()
        val prefs = context.getSharedPreferences("widget_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putLong("total_balance", total).apply()
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val componentName = android.content.ComponentName(context, BalanceWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        appWidgetIds.forEach { id -> BalanceWidget.updateWidget(context, appWidgetManager, id) }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Восстановление данных") },
            text = { Text("Внимание! Все текущие данные будут удалены и заменены данными из резервной копии. Продолжить?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.restoreBackup(pendingRestoreJson) { success ->
                        if (success) scope.launch { snackbarHostState.showSnackbar("Данные успешно восстановлены!") }
                        else scope.launch { snackbarHostState.showSnackbar("Ошибка: Неверный формат файла") }
                    }
                    showRestoreConfirm = false
                }) { Text("Восстановить", color = RED) }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("Отмена") } }
        )
    }

    if (showSettings) {
        SettingsDialog(context, onDismiss = { showSettings = false }, snackbarHostState, scope)
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
                    IconButton(onClick = { showRecurringManager = true }) { Icon(Icons.Default.Autorenew, "Регулярные") }
                    IconButton(onClick = { showBudgetManager = true }) { Icon(Icons.Default.Savings, "Бюджеты") }
                    IconButton(onClick = { showCardManager = true }) { Icon(Icons.Default.CreditCard, "Карты") }
                    
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Еще") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Экспорт в Excel (CSV)") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = { showMenu = false; csvExportLauncher.launch("uchet_export.csv") }
                            )
                            DropdownMenuItem(
                                text = { Text("Сохранить копию (JSON)") },
                                leadingIcon = { Icon(Icons.Default.Backup, null) },
                                onClick = { showMenu = false; jsonExportLauncher.launch("uchet_backup.json") }
                            )
                            DropdownMenuItem(
                                text = { Text("Загрузить копию (JSON)") },
                                leadingIcon = { Icon(Icons.Default.Restore, null) },
                                onClick = { showMenu = false; jsonImportLauncher.launch(arrayOf("application/json")) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Настройки") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { showMenu = false; showSettings = true }
                            )
                        }
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
                    value = searchQuery, onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("Поиск...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filterType == null, onClick = { filterType = null }, label = { Text("Все") })
                    FilterChip(selected = filterType == TxType.INCOME, onClick = { filterType = TxType.INCOME }, label = { Text("Доходы") })
                    FilterChip(selected = filterType == TxType.EXPENSE, onClick = { filterType = TxType.EXPENSE }, label = { Text("Расходы") })
                }
                Spacer(Modifier.height(16.dp))
            }

            if (budgets.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Бюджеты на месяц", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            budgets.forEach { budget ->
                                val spent = txs.filter { it.type == TxType.EXPENSE && it.category == budget.category }.sumOf { it.amountMinor }
                                val progress = if (budget.limitMinor > 0) spent.toFloat() / budget.limitMinor.toFloat() else 0f
                                val progressColor = when { progress >= 1f -> RED; progress >= 0.8f -> YELLOW; else -> GREEN }
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(budget.category, fontWeight = FontWeight.Medium)
                                        Text("${spent.money()} / ${budget.limitMinor.money()}", style = MaterialTheme.typography.bodySmall, color = if (progress >= 1f) RED else Color.Unspecified)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = progressColor, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
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
                            Card(modifier = Modifier.width(140.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
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
                val expensesByCategory = txs.filter { it.type == TxType.EXPENSE }.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
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
            
            if (filteredTxs.isEmpty()) item { Text("Ничего не найдено.", Modifier.padding(top = 32.dp), color = Color.Gray) }
            
            byDay.forEach { (day, list) ->
                item { Text(day.format(DATE_FMT), Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = Color.Gray) }
                items(list, key = { it.id }) { t -> TxRow(t) { editing = t } }
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
                    val timerJob = launch { delay(5000); snackbarHostState.currentSnackbarData?.dismiss() }
                    val result = snackbarHostState.showSnackbar("Операция удалена", "Отменить", SnackbarDuration.Indefinite)
                    timerJob.cancel()
                    if (result == SnackbarResult.ActionPerformed) vm.save(e)
                }
            } }
        )
    }

    if (showCardManager) CardManagerDialog(cards, cardBalances, allTxs, { vm.saveCard(it) }, { vm.deleteCard(it) }, { showCardManager = false })
    if (showBudgetManager) BudgetManagerDialog(budgets, { vm.saveBudget(it) }, { vm.deleteBudget(it) }, { showBudgetManager = false })
    if (showRecurringManager) RecurringManagerDialog(recurring, cards, { vm.saveRecurring(it) }, { vm.deleteRecurring(it) }, { showRecurringManager = false })
}

@Composable
fun SettingsDialog(context: android.content.Context, onDismiss: () -> Unit, snackbarHostState: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
    
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var useBiometric by remember { mutableStateOf(prefs.getBoolean("use_biometric", true)) }
    
    val savedPin = prefs.getString("pin", "0000") ?: "0000"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки безопасности") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                
                Text("Смена PIN-кода", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = oldPin, onValueChange = { oldPin = it.filter(Char::isDigit).take(4) },
                    label = { Text("Текущий PIN") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(4) },
                    label = { Text("Новый PIN (4 цифры)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin, onValueChange = { confirmPin = it.filter(Char::isDigit).take(4) },
                    label = { Text("Повторите новый PIN") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        when {
                            oldPin != savedPin -> scope.launch { snackbarHostState.showSnackbar("Неверный текущий PIN") }
                            newPin.length != 4 -> scope.launch { snackbarHostState.showSnackbar("Новый PIN должен быть из 4 цифр") }
                            newPin != confirmPin -> scope.launch { snackbarHostState.showSnackbar("PIN-коды не совпадают") }
                            else -> {
                                prefs.edit().putString("pin", newPin).apply()
                                oldPin = ""; newPin = ""; confirmPin = ""
                                scope.launch { snackbarHostState.showSnackbar("PIN-код успешно изменен!") }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сменить PIN") }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Вход по отпечатку", style = MaterialTheme.typography.titleSmall)
                        Text("Использовать биометрию при запуске", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(
                        checked = useBiometric,
                        onCheckedChange = { 
                            useBiometric = it 
                            prefs.edit().putBoolean("use_biometric", it).apply()
                        }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

fun generateCsv(transactions: List<Tx>): String {
    val sb = StringBuilder("Дата,Время,Тип,Сумма (UZS),Категория,Описание,Карта,Источник\n")
    val zone = ZoneId.systemDefault()
    transactions.sortedBy { it.timestamp }.forEach { t ->
        val dt = Instant.ofEpochMilli(t.timestamp).atZone(zone)
        val amount = BigDecimal(t.amountMinor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()
        sb.append("${dt.format(DATE_FMT)},${dt.format(DateTimeFormatter.ofPattern("HH:mm"))},${t.type.label},$amount,${t.category.replace(",", " ")},${t.description.replace(",", " ").replace("\n", " ")},${if (t.cardLast4.isNullOrEmpty()) "Наличные" else "Карта *${t.cardLast4}"},${if (t.source == "AUTO") "Авто" else "Вручную"}\n")
    }
    return sb.toString()
}

@Composable
fun CategoryPieChart(data: Map<String, Long>) {
    val total = data.values.sum().toFloat(); if (total == 0f) return
    val colors = listOf(Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFB74D), Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFA1887F))
    val sortedData = data.entries.sortedByDescending { it.value }
    Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(110.dp).padding(8.dp)) {
            var startAngle = -90f
            sortedData.forEachIndexed { index, entry ->
                val sweepAngle = (entry.value / total) * 360f
                drawArc(color = colors[index % colors.size], startAngle = startAngle, sweepAngle = sweepAngle, useCenter = true)
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
    val (sign, color) = when (t.type) { TxType.INCOME -> "+" to GREEN; TxType.EXPENSE -> "−" to RED; TxType.TRANSFER -> "" to Color.Gray }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${if (t.source == "AUTO") "🔄 " else ""}${t.category}  ${t.description}".trim(), maxLines = 2)
            val time = Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
            val cardText = if (t.cardLast4.isNullOrEmpty()) "Наличные" else "*${t.cardLast4}"
            Text(listOfNotNull(time, cardText).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Text("$sign${t.amountMinor.money()}", color = color, fontWeight = FontWeight.Medium)
    }
}

// ОСТАЛЬНЫЕ ДИАЛОГИ - БЕЗ ИЗМЕНЕНИЙ, ОСТАВЛЯЕМ КАК ЕСТЬ
// Вставьте сюда коды TxDialog, RecurringManagerDialog, BudgetManagerDialog, CardManagerDialog из вашего текущего файла

// ... КОДЫ ДИАЛОГОВ ...

fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        else MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    } catch (e: Exception) { null }
}

data class ParsedSms(val amountStr: String, val card: String?, val description: String, val category: String)

fun parseSmsText(text: String): ParsedSms? {
    val lowerText = text.lowercase()
    val amountRegex = Regex("""([\d\s]+(?:[.,]\d{1,2})?)\s*(?:uzs|сум|rub|руб|\$|€)""", RegexOption.IGNORE_CASE)
    val amountStr = amountRegex.find(text)?.groupValues?.get(1)?.replace(" ", "")?.replace(",", ".") ?: return null
    if (amountStr.toBigDecimalOrNull() == null) return null
    val card = Regex("""(?:карта|card|\*|х)\s*(\d{4})""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.take(4)
    val description = text.take(40).replace("\n", " ").trim()
    val category = when {
        lowerText.contains("магнит") || lowerText.contains("пятерочка") || lowerText.contains("еда") || lowerText.contains("кафе") || lowerText.contains("ресторан") -> "🍔 Еда"
        lowerText.contains("такси") || lowerText.contains("яндекс go") || lowerText.contains("бензин") || lowerText.contains("метро") -> "🚕 Транспорт"
        lowerText.contains("аптека") || lowerText.contains("клиника") -> "💊 Здоровье"
        lowerText.contains("аренда") || lowerText.contains("квартплата") -> "🏠 Жилье"
        lowerText.contains("зарплата") || lowerText.contains("аванс") -> "💵 Зарплата"
        lowerText.contains("перевод") -> "💸 Перевод"
        else -> "📦 Другое"
    }
    return ParsedSms(amountStr, card, description, category)
}
