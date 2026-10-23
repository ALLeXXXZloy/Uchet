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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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

    // Хранилище для карт пользователя
    private val prefs = app.getSharedPreferences("uchet_cards", android.content.Context.MODE_PRIVATE)
    val savedCards = MutableStateFlow(prefs.getStringSet("cards", emptySet())?.toList()?.sorted() ?: emptyList())

    fun addCard(card: String) {
        val set = savedCards.value.toMutableSet()
        set.add(card)
        prefs.edit().putStringSet("cards", set).apply()
        savedCards.value = set.toList().sorted()
    }

    fun removeCard(card: String) {
        val set = savedCards.value.toMutableSet()
        set.remove(card)
        prefs.edit().putStringSet("cards", set).apply()
        savedCards.value = set.toList().sorted()
    }

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
    val savedCards by vm.savedCards.collectAsState()
    var editing by remember { mutableStateOf<Tx?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showCardManager by remember { mutableStateOf(false) }

    val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
    val expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    val zone = ZoneId.systemDefault()
    val byDay = txs.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }

    Scaffold(
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
            initial = editing, savedCards = savedCards,
            onDismiss = { adding = false; editing = null },
            onSave = { vm.save(it); adding = false; editing = null },
            onDelete = editing?.let { e -> { vm.delete(e); editing = null } }
        )
    }

    if (showCardManager) {
        CardManagerDialog(
            cards = savedCards,
            onAdd = { vm.addCard(it) },
            onRemove = { vm.removeCard(it) },
            onDismiss = { showCardManager = false }
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
            
            // Выводим "Наличные" если карта не указана, или *1234
            val cardText = if (t.cardLast4.isNullOrEmpty()) "Наличные" else "*${t.cardLast4}"
            
            Text(listOfNotNull(time, cardText, if (t.type == TxType.TRANSFER) "перевод" else null)
                .joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Text("$sign${t.amountMinor.money()}", color = color, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxDialog(initial: Tx?, savedCards: List<String>, onDismiss: () -> Unit, onSave: (Tx) -> Unit, onDelete: (() -> Unit)?) {
    val zone = ZoneId.systemDefault()
    val initDt = initial?.let { Instant.ofEpochMilli(it.timestamp).atZone(zone) } ?: ZonedDateTime.now(zone)

    var type by remember { mutableStateOf(initial?.type ?: TxType.EXPENSE) }
    var amount by remember { mutableStateOf(initial?.let { BigDecimal(it.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString() } ?: "") }
    // Если карта null, значит это наличные
    var selectedCard by remember { mutableStateOf(initial?.cardLast4 ?: "CASH") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var date by remember { mutableStateOf(initDt.format(DATE_FMT)) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }

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
                            // Ставим распознанную сумму
                            amount = parsedData.first.toBigDecimal().stripTrailingZeros().toPlainString()
                            desc = parsedData.third
                            
                            // Если распознали карту, и она есть в нашем списке (или просто ставим её)
                            if (parsedData.second != null) {
                                selectedCard = parsedData.second!!
                                // Автоматически добавляем карту в список, если её там нет
                                // (это произойдет при сохранении, либо можно добавить в VM, но здесь просто выберем)
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
                
                // 1. Сначала выбор типа операции
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TxType.values().forEachIndexed { i, t ->
                        SegmentedButton(type == t, { type = t },
                            SegmentedButtonDefaults.itemShape(i, TxType.values().size)) { Text(t.label) }
                    }
                }

                // 2. Кнопка сканирования
                Button(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text(if (isLoading) "Распознавание..." else "📸 Загрузить скриншот СМС")
                }

                // 3. Сумма
                OutlinedTextField(amount, { amount = it }, label = { Text("Сумма, UZS") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))

                // 4. Выбор карты (Наличные + сохраненные карты)
                Text("Способ оплаты:", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedCard == "CASH",
                        onClick = { selectedCard = "CASH" },
                        label = { Text("Наличные") }
                    )
                    savedCards.forEach { c ->
                        FilterChip(
                            selected = selectedCard == c,
                            onClick = { selectedCard = c },
                            label = { Text("*$c") }
                        )
                    }
                }

                // 5. Описание
                OutlinedTextField(desc, { desc = it }, label = { Text("Описание") })
                
                // 6. Дата
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
                    else -> {
                        val finalCard = if (selectedCard == "CASH") null else selectedCard
                        onSave(Tx(
                            id = initial?.id ?: 0, amountMinor = minor, type = type,
                            cardLast4 = finalCard, description = desc.trim(),
                            timestamp = d.atTime(initDt.toLocalTime()).atZone(zone).toInstant().toEpochMilli(),
                            source = initial?.source ?: "MANUAL"
                        ))
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

@Composable
fun CardManagerDialog(cards: List<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit, onDismiss: () -> Unit) {
    var newCard by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Мои карты") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCard,
                        onValueChange = { newCard = it.filter(Char::isDigit).take(4) },
                        label = { Text("4 цифры карты") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newCard.length == 4 && !cards.contains(newCard)) {
                                onAdd(newCard)
                                newCard = ""
                            }
                        }
                    ) { Text("Доб.") }
                }
                Spacer(Modifier.height(16.dp))
                if (cards.isEmpty()) {
                    Text("У вас пока нет добавленных карт.", color = Color.Gray)
                }
                cards.forEach { c ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("*$c")
                        IconButton({ onRemove(c) }) { Icon(Icons.Default.Delete, "Удалить", tint = RED) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

// Функция для конвертации картинки из галереи в Bitmap
fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) { null }
}

// Улучшенный парсер: ищет сумму и 4 цифры карты
fun parseSmsText(text: String): Triple<Double, String?, String>? {
    val lowerText = text.lowercase()
    
    // 1. Ищем сумму (число, после которого идет UZS, сум, руб и т.д.)
    // Поддерживает пробелы и запятые (например, 50 000.00 UZS или 50000,50 сум)
    val amountRegex = Regex("""([\d\s]+(?:[.,]\d{1,2})?)\s*(?:uzs|сум|rub|руб|\$|€)""", RegexOption.IGNORE_CASE)
    val amountMatch = amountRegex.find(text)
    val amountStr = amountMatch?.groupValues?.get(1)?.replace(" ", "")?.replace(",", ".") ?: ""
    val amount = amountStr.toDoubleOrNull()

    // 2. Ищем карту (4 цифры после *, 'карта', 'card' или 'карты')
    val cardRegex = Regex("""(?:карта|card|\*|х)\s*(\d{4})""", RegexOption.IGNORE_CASE)
    val cardMatch = cardRegex.find(text)
    val card = cardMatch?.groupValues?.get(1)?.take(4)

    // 3. Описание (первые 40 символов, чтобы не занимало весь экран)
    val description = text.take(40).replace("\n", " ").trim()

    return if (amount != null) Triple(amount, card, description) else null
}
