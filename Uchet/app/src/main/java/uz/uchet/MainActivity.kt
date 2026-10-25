package uz.uchet

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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
private val BLUE = Color(0xFF1976D2)

val DEFAULT_EXPENSE_CATEGORIES = listOf("🍔 Еда", "🚕 Транспорт", "🏠 Жилье", "🛒 Покупки", "💊 Здоровье", "🎉 Развлечения", "📦 Другое")
val DEFAULT_INCOME_CATEGORIES = listOf("💵 Зарплата", "🎁 Подарок", "💸 Перевод", "📈 Инвестиции", "📦 Другое")

enum class AppTheme { SYSTEM, LIGHT, DARK }
enum class AppLanguage { RU, UZ, EN, TG, KAA, KK }

object Translations {
    private val dict = mapOf(
        "ru" to mapOf(
            "my_accounts" to "Мои счета", "exp_by_cat" to "Расходы по категориям", "daily_chart" to "Динамика расходов (по дням)", "monthly_chart" to "Расходы по месяцам (за год)",
            "budgets_month" to "Бюджеты на месяц", "income" to "Доходы", "expense" to "Расходы", "total" to "Итого", "search" to "Поиск...", "all" to "Все", "add" to "Добавить",
            "new_tx" to "Новая операция", "edit" to "Изменить", "amount" to "Сумма (UZS)", "category" to "Категория", "account" to "Счет оплаты", "other" to "Другая...",
            "desc" to "Описание", "date" to "Дата (дд.мм.гггг)", "save" to "Сохранить", "cancel" to "Отмена", "delete" to "Удалить", "settings" to "Настройки",
            "theme" to "Тема оформления", "language" to "Язык / Til / Language", "categories" to "Категории", "recurring" to "Регулярные", "budgets" to "Бюджеты",
            "export" to "Экспорт в Excel (CSV)", "backup" to "Сохранить копию (JSON)", "restore" to "Загрузить копию (JSON)", "biometric" to "Вход по отпечатку",
            "change_pin" to "Сменить PIN", "locked" to "Приложение заблокировано", "login" to "Войти", "fingerprint" to "Использовать отпечаток",
            "sms_title" to "Проверьте операцию из СМС", "sms_data" to "Данные из СМС", "confirm" to "Подтвердить", "reject" to "Отклонить", "pin" to "PIN-код",
            "wrong_pin" to "Неверный PIN-код", "no_found" to "Ничего не найдено", "limit" to "Лимит", "day" to "День месяца (1-31)", "set_limit" to "Сохранить лимит",
            "add_cat" to "Добавить категорию", "add_temp" to "Добавить шаблон", "sys" to "Системная", "light" to "Светлая", "dark" to "Темная",
            "cash" to "Наличные", "card" to "Карта", "upload_sms" to "Загрузить СМС или чек",
            "goals" to "Копилка (Цели)", "add_goal" to "Добавить цель", "goal_name" to "Название цели", "target_amount" to "Целевая сумма",
            "deposit" to "Пополнить", "deposit_amount" to "Сумма пополнения", "source_card" to "Списать с карты", "completed" to "Достигнуто!",
            "import_csv" to "Импорт из CSV", "import_pdf" to "Импорт из PDF", "import_confirm" to "Найдено транзакций: "
        ),
        "uz" to mapOf(
            "my_accounts" to "Mening hisoblarim", "exp_by_cat" to "Kategoriyalar bo'yicha xarajatlar", "daily_chart" to "Xarajatlar dinamikasi (kunlik)", "monthly_chart" to "Xarajatlar (oylik)",
            "budgets_month" to "Oylik byudjetlar", "income" to "Daromadlar", "expense" to "Xarajatlar", "total" to "Jami", "search" to "Qidirish...", "all" to "Barchasi", "add" to "Qo'shish",
            "new_tx" to "Yangi operatsiya", "edit" to "O'zgartirish", "amount" to "Summa (UZS)", "category" to "Kategoriya", "account" to "To'lov hisobi", "other" to "Boshqa...",
            "desc" to "Tavsif", "date" to "Sana (kk.oo.yyyy)", "save" to "Saqlash", "cancel" to "Bekor qilish", "delete" to "O'chirish", "settings" to "Sozlamalar",
            "theme" to "Mavzu", "language" to "Язык / Til / Language", "categories" to "Kategoriyalar", "recurring" to "Muntazam", "budgets" to "Byudjetlar",
            "export" to "Excel (CSV) ga eksport", "backup" to "Zaxira nusxasini saqlash (JSON)", "restore" to "Zaxirani yuklash (JSON)", "biometric" to "Barmoq izi bilan kirish",
            "change_pin" to "PIN kodni o'zgartirish", "locked" to "Ilova bloklangan", "login" to "Kirish", "fingerprint" to "Barmoq izidan foydalanish",
            "sms_title" to "SMS operatsiyasini tekshiring", "sms_data" to "SMS ma'lumotlari", "confirm" to "Tasdiqlash", "reject" to "Rad etish", "pin" to "PIN kod",
            "wrong_pin" to "Noto'g'ri PIN kod", "no_found" to "Hech narsa topilmadi", "limit" to "Limit", "day" to "Oy kuni (1-31)", "set_limit" to "Limitni saqlash",
            "add_cat" to "Kategoriya qo'shish", "add_temp" to "Shablon qo'shish", "sys" to "Tizim", "light" to "Yorug'", "dark" to "Qorong'i",
            "cash" to "Naqd", "card" to "Karta", "upload_sms" to "SMS yoki chekni yuklash",
            "goals" to "Jamg'arma (Maqsadlar)", "add_goal" to "Maqsad qo'shish", "goal_name" to "Maqsad nomi", "target_amount" to "Maqsad summasi",
            "deposit" to "To'ldirish", "deposit_amount" to "To'ldirish summasi", "source_card" to "Kartadan yechish", "completed" to "Erishildi!",
            "import_csv" to "CSV dan import", "import_pdf" to "PDF dan import", "import_confirm" to "Topilgan operatsiyalar: "
        ),
        "en" to mapOf(
            "my_accounts" to "My Accounts", "exp_by_cat" to "Expenses by Category", "daily_chart" to "Daily Expense Dynamics", "monthly_chart" to "Monthly Expenses (Year)",
            "budgets_month" to "Monthly Budgets", "income" to "Income", "expense" to "Expenses", "total" to "Total", "search" to "Search...", "all" to "All", "add" to "Add",
            "new_tx" to "New Transaction", "edit" to "Edit", "amount" to "Amount (UZS)", "category" to "Category", "account" to "Payment Account", "other" to "Other...",
            "desc" to "Description", "date" to "Date (dd.mm.yyyy)", "save" to "Save", "cancel" to "Cancel", "delete" to "Delete", "settings" to "Settings",
            "theme" to "Theme", "language" to "Язык / Til / Language", "categories" to "Categories", "recurring" to "Recurring", "budgets" to "Budgets",
            "export" to "Export to Excel (CSV)", "backup" to "Save Backup (JSON)", "restore" to "Load Backup (JSON)", "biometric" to "Biometric Login",
            "change_pin" to "Change PIN", "locked" to "App is Locked", "login" to "Login", "fingerprint" to "Use Fingerprint",
            "sms_title" to "Check SMS Transaction", "sms_data" to "SMS Data", "confirm" to "Confirm", "reject" to "Reject", "pin" to "PIN Code",
            "wrong_pin" to "Wrong PIN Code", "no_found" to "Nothing found", "limit" to "Limit", "day" to "Day of month (1-31)", "set_limit" to "Save Limit",
            "add_cat" to "Add Category", "add_temp" to "Add Template", "sys" to "System", "light" to "Light", "dark" to "Dark",
            "cash" to "Cash", "card" to "Card", "upload_sms" to "Upload SMS or Receipt",
            "goals" to "Savings Goals", "add_goal" to "Add Goal", "goal_name" to "Goal Name", "target_amount" to "Target Amount",
            "deposit" to "Deposit", "deposit_amount" to "Deposit Amount", "source_card" to "Source Account", "completed" to "Completed!",
            "import_csv" to "Import from CSV", "import_pdf" to "Import from PDF", "import_confirm" to "Found transactions: "
        ),
        "tg" to mapOf(
            "my_accounts" to "Ҳисобҳои ман", "exp_by_cat" to "Хароҷот аз рӯи категорияҳо", "daily_chart" to "Динамикаи хароҷот (рӯзона)", "monthly_chart" to "Хароҷот аз рӯи моҳҳо",
            "budgets_month" to "Буҷетҳои моҳона", "income" to "Даромадҳо", "expense" to "Хароҷот", "total" to "Ҷамъ", "search" to "Ҷустуҷӯ...", "all" to "Ҳама", "add" to "Илова кардан",
            "new_tx" to "Амалиёти нав", "edit" to "Тағйир додан", "amount" to "Маблағ (UZS)", "category" to "Категория", "account" to "Ҳисоб барои пардохт", "other" to "Дигар...",
            "desc" to "Тавсиф", "date" to "Сана (рӯз.моҳ.сол)", "save" to "Нигоҳ доштан", "cancel" to "Бекор кардан", "delete" to "Нест кардан", "settings" to "Танзимот",
            "theme" to "Мавзӯъ", "language" to "Язык / Til / Language", "categories" to "Категорияҳо", "recurring" to "Мунтазам", "budgets" to "Буҷетҳо",
            "export" to "Содирот ба Excel (CSV)", "backup" to "Нусхаи эҳтиётӣ (JSON)", "restore" to "Барқарор кардан (JSON)", "biometric" to "Вуруд бо изи ангуштон",
            "change_pin" to "Тағйири PIN-код", "locked" to "Барнома баста шудааст", "login" to "Даромадан", "fingerprint" to "Истифодаи изи ангуштон",
            "sms_title" to "Амалиётро аз SMS тафтиш кунед", "sms_data" to "Маълумот аз SMS", "confirm" to "Тасдиқ кардан", "reject" to "Рад кардан", "pin" to "PIN-код",
            "wrong_pin" to "PIN-коди нодуруст", "no_found" to "Ҳеҷ чиз ёфт нашуд", "limit" to "Ҳудуд", "day" to "Рӯзи моҳ (1-31)", "set_limit" to "Нигоҳ доштани ҳудуд",
            "add_cat" to "Иловаи категория", "add_temp" to "Иловаи қолаб", "sys" to "Системавӣ", "light" to "Равшан", "dark" to "Торик",
            "cash" to "Нақдӣ", "card" to "Корт", "upload_sms" to "Боркунии SMS ё чек",
            "goals" to "Хазина (Мақсадҳо)", "add_goal" to "Иловаи мақсад", "goal_name" to "Номи мақсад", "target_amount" to "Маблағи мақсад",
            "deposit" to "Пур кардан", "deposit_amount" to "Маблағи пуркунӣ", "source_card" to "Аз корт гирифтан", "completed" to "Иҷро шуд!",
            "import_csv" to "Импорт аз CSV", "import_pdf" to "Импорт аз PDF", "import_confirm" to "Амалиётҳои ёфтшуда: "
        ),
        "kaa" to mapOf(
            "my_accounts" to "Meniń esap-layıqlarım", "exp_by_cat" to "Kategoriyalar boyınsha shıǵınlar", "daily_chart" to "Shıǵınlar dinamikası (kúnlik)", "monthly_chart" to "Shıǵınlar (aylıq)",
            "budgets_month" to "Aylıq byudjetler", "income" to "Kirey", "expense" to "Shıǵın", "total" to "Jámi", "search" to "Izlew...", "all" to "Barlıǵı", "add" to "Qosıw",
            "new_tx" to "Jańa operaciya", "edit" to "Ózgertiw", "amount" to "Summa (UZS)", "category" to "Kategoriya", "account" to "Tólem esabı", "other" to "Basqa...",
            "desc" to "Túsindirme", "date" to "Sáne (kk.oo.jjjj)", "save" to "Saqlaw", "cancel" to "Biykaralaw", "delete" to "Óshiriw", "settings" to "Sazlawlar",
            "theme" to "Tema", "language" to "Язык / Til / Language", "categories" to "Kategoriyalar", "recurring" to "Turaqlı", "budgets" to "Byudjetler",
            "export" to "Excel (CSV) ke eksport", "backup" to "Zárúrlik nusqasın saqlaw (JSON)", "restore" to "Zárúrlik nusqasın júklew (JSON)", "biometric" to "Barmaq izi menen kiriw",
            "change_pin" to "PIN kodtı ózgertiw", "locked" to "Ilova qulplanǵan", "login" to "Kiriw", "fingerprint" to "Barmaq izin qollanıw",
            "sms_title" to "SMS operaciyasın tekseriń", "sms_data" to "SMS maǵlıwmatları", "confirm" to "Tastıyıqlaw", "reject" to "Biykaralaw", "pin" to "PIN kod",
            "wrong_pin" to "Nádurıs PIN kod", "no_found" to "Hesh nárse tabılmadı", "limit" to "Limit", "day" to "Ay kúni (1-31)", "set_limit" to "Limitti saqlaw",
            "add_cat" to "Kategoriya qosıw", "add_temp" to "Shablon qosıw", "sys" to "Sistema", "light" to "Jaqtı", "dark" to "Qarańǵı",
            "cash" to "Naq pul", "card" to "Karta", "upload_sms" to "SMS yamasa chek júklew",
            "goals" to "Jıynaǵısh (Máksler)", "add_goal" to "Mákset qosıw", "goal_name" to "Mákset atı", "target_amount" to "Mákset summası",
            "deposit" to "Toltırıw", "deposit_amount" to "Toltırıw summası", "source_card" to "Kartadan alıw", "completed" to "Orınlandı!",
            "import_csv" to "CSV dan import", "import_pdf" to "PDF dan import", "import_confirm" to "Tabılǵan operaciyalar: "
        ),
        "kk" to mapOf(
            "my_accounts" to "Менің шоттарым", "exp_by_cat" to "Санаттар бойынша шығыстар", "daily_chart" to "Шығыстар динамикасы (күнделікті)", "monthly_chart" to "Шығыстар (айлық)",
            "budgets_month" to "Айлық бюджеттер", "income" to "Кірістер", "expense" to "Шығыстар", "total" to "Жиыны", "search" to "Іздеу...", "all" to "Барлығы", "add" to "Қосу",
            "new_tx" to "Жаңа операция", "edit" to "Өзгерту", "amount" to "Сома (UZS)", "category" to "Санат", "account" to "Төлем шоты", "other" to "Басқа...",
            "desc" to "Сипаттама", "date" to "Күні (кк.аа.жжжж)", "save" to "Сақтау", "cancel" to "Болдырмау", "delete" to "Жою", "settings" to "Параметрлер",
            "theme" to "Тақырып", "language" to "Язык / Til / Language", "categories" to "Санаттар", "recurring" to "Тұрақты", "budgets" to "Бюджеттер",
            "export" to "Excel-ге экспорттау (CSV)", "backup" to "Сақтық көшірме (JSON)", "restore" to "Сақтық көшірмені жүктеу (JSON)", "biometric" to "Саусақ ізімен кіру",
            "change_pin" to "PIN-кодты өзгерту", "locked" to "Қолданба құлыпталған", "login" to "Кіру", "fingerprint" to "Саусақ ізін пайдалану",
            "sms_title" to "SMS операциясын тексеріңіз", "sms_data" to "SMS деректері", "confirm" to "Растау", "reject" to "Қабылдамау", "pin" to "PIN-код",
            "wrong_pin" to "Қате PIN-код", "no_found" to "Ештеңе табылмады", "limit" to "Лимит", "day" to "Ай күні (1-31)", "set_limit" to "Лимитті сақтау",
            "add_cat" to "Санат қосу", "add_temp" to "Үлгі қосу", "sys" to "Жүйелік", "light" to "Ашық", "dark" to "Қараңғы",
            "cash" to "Қолма-қол", "card" to "Карта", "upload_sms" to "SMS немесе чекті жүктеу",
            "goals" to "Жинақ (Мақсаттар)", "add_goal" to "Мақсат қосу", "goal_name" to "Мақсат атауы", "target_amount" to "Мақсат сомасы",
            "deposit" to "Толықтыру", "deposit_amount" to "Толықтыру сомасы", "source_card" to "Картадан алу", "completed" to "Орындалды!",
            "import_csv" to "CSV-тен импорт", "import_pdf" to "PDF-тен импорт", "import_confirm" to "Табылған операциялар: "
        )
    )

    fun t(key: String, lang: AppLanguage): String {
        val langKey = when(lang) { 
            AppLanguage.RU -> "ru"; AppLanguage.UZ -> "uz"; AppLanguage.EN -> "en"
            AppLanguage.TG -> "tg"; AppLanguage.KAA -> "kaa"; AppLanguage.KK -> "kk"
        }
        return dict[langKey]?.get(key) ?: key
    }

    fun translateCategory(cat: String, lang: AppLanguage): String {
        if (lang == AppLanguage.RU) return cat
        val isUz = lang == AppLanguage.UZ; val isEn = lang == AppLanguage.EN
        val isTg = lang == AppLanguage.TG; val isKaa = lang == AppLanguage.KAA; val isKk = lang == AppLanguage.KK
        return when(cat) {
            "🍔 Еда" -> if(isUz) "🍔 Ovqat" else if(isEn) "🍔 Food" else if(isTg) "🍔 Озуқаворӣ" else if(isKaa) "🍔 Awqat" else if(isKk) "🍔 Тамақ" else cat
            "🚕 Транспорт" -> if(isUz) "🚕 Transport" else if(isEn) "🚕 Transport" else if(isTg) "🚕 Нақлиёт" else if(isKaa) "🚕 Transport" else if(isKk) "🚕 Көлік" else cat
            "🏠 Жилье" -> if(isUz) "🏠 Uy-joy" else if(isEn) "🏠 Housing" else if(isTg) "🏠 Манзил" else if(isKaa) "🏠 Turar jay" else if(isKk) "🏠 Тұрғын үй" else cat
            "🛒 Покупки" -> if(isUz) "🛒 Xaridlar" else if(isEn) "🛒 Shopping" else if(isTg) "🛒 Харидҳо" else if(isKaa) "🛒 Xarıdlar" else if(isKk) "🛒 Сатып алулар" else cat
            "💊 Здоровье" -> if(isUz) "💊 Salomatlik" else if(isEn) "💊 Health" else if(isTg) "💊 Тандурустӣ" else if(isKaa) "💊 Salawmatlıq" else if(isKk) "💊 Денсаулық" else cat
            "🎉 Развлечения" -> if(isUz) "🎉 Ko'ngilochar" else if(isEn) "🎉 Entertainment" else if(isTg) "🎉 Фароғат" else if(isKaa) "🎉 Oynap-kúlw" else if(isKk) "🎉 Ойын-сауық" else cat
            "📦 Другое" -> if(isUz) "📦 Boshqa" else if(isEn) "📦 Other" else if(isTg) "📦 Дигар" else if(isKaa) "📦 Basqa" else if(isKk) "📦 Басқа" else cat
            "💵 Зарплата" -> if(isUz) "💵 Maosh" else if(isEn) "💵 Salary" else if(isTg) "💵 Маош" else if(isKaa) "💵 Maydı" else if(isKk) "💵 Жалақы" else cat
            "🎁 Подарок" -> if(isUz) "🎁 Sovg'a" else if(isEn) "🎁 Gift" else if(isTg) "🎁 Тӯҳфа" else if(isKaa) "🎁 Sawǵa" else if(isKk) "🎁 Сыйлық" else cat
            "💸 Перевод" -> if(isUz) "💸 O'tkazma" else if(isEn) "💸 Transfer" else if(isTg) "💸 Интиқол" else if(isKaa) "💸 Awdarıw" else if(isKk) "💸 Аударым" else cat
            "📈 Инвестиции" -> if(isUz) "📈 Investitsiya" else if(isEn) "📈 Investment" else if(isTg) "📈 Сармоягузорӣ" else if(isKaa) "📈 Investiciya" else if(isKk) "📈 Инвестициялар" else cat
            else -> cat
        }
    }
}

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
        dao.observe(m.atDay(1).atStartOfDay(z).toInstant().toEpochMilli(), m.plusMonths(1).atDay(1).atStartOfDay(z).toInstant().toEpochMilli())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTxs = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cards = dao.observeCards().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val budgets = dao.observeBudgets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recurring = dao.observeRecurring().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = dao.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<CategoryEntity>())
    val goals = dao.observeGoals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<SavingsGoalEntity>())

    init {
        viewModelScope.launch { checkAndExecuteRecurring(); seedDefaultCategories() }
    }

    private suspend fun seedDefaultCategories() {
        if (dao.getCategoriesList().isEmpty()) {
            DEFAULT_EXPENSE_CATEGORIES.forEach { dao.upsertCategory(CategoryEntity(it, TxType.EXPENSE)) }
            DEFAULT_INCOME_CATEGORIES.forEach { dao.upsertCategory(CategoryEntity(it, TxType.INCOME)) }
        }
    }

    private suspend fun checkAndExecuteRecurring() {
        val now = LocalDate.now()
        val currentMonthStr = YearMonth.now().toString()
        val currentDay = now.dayOfMonth
        val templates = dao.getRecurringList()
        for (t in templates) {
            if (t.dayOfMonth <= currentDay && t.lastExecutedMonth != currentMonthStr) {
                val tx = Tx(amountMinor = t.amountMinor, type = t.type, cardLast4 = t.cardLast4, description = t.description, category = t.category, timestamp = now.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), source = "AUTO")
                dao.upsert(tx)
                dao.upsertRecurring(t.copy(lastExecutedMonth = currentMonthStr))
            }
        }
    }

    fun save(t: Tx) { viewModelScope.launch { dao.upsert(t) } }
    fun bulkSave(txs: List<Tx>) { viewModelScope.launch { dao.insertAll(txs) } }
    fun delete(t: Tx) { viewModelScope.launch { dao.delete(t) } }
    fun shift(n: Long) { month.value = month.value.plusMonths(n) }
    fun saveCard(card: CardEntity) { viewModelScope.launch { dao.upsertCard(card) } }
    fun deleteCard(last4: String) { viewModelScope.launch { dao.deleteCard(last4) } }
    fun saveBudget(budget: BudgetEntity) { viewModelScope.launch { dao.upsertBudget(budget) } }
    fun deleteBudget(category: String) { viewModelScope.launch { dao.deleteBudget(category) } }
    fun saveRecurring(r: RecurringEntity) { viewModelScope.launch { dao.upsertRecurring(r) } }
    fun deleteRecurring(r: RecurringEntity) { viewModelScope.launch { dao.deleteRecurring(r) } }
    fun saveCategory(c: CategoryEntity) { viewModelScope.launch { dao.upsertCategory(c) } }
    fun deleteCategory(name: String) { viewModelScope.launch { dao.deleteCategory(name) } }

    fun saveGoal(goal: SavingsGoalEntity) { viewModelScope.launch { dao.upsertGoal(goal) } }
    fun deleteGoal(goal: SavingsGoalEntity) { viewModelScope.launch { dao.deleteGoal(goal) } }

    fun depositToGoal(goal: SavingsGoalEntity, cardLast4: String, amountMinor: Long) {
        viewModelScope.launch {
            val tx = Tx(
                amountMinor = amountMinor,
                type = TxType.TRANSFER,
                cardLast4 = cardLast4,
                description = goal.name,
                category = "🏦 Копилка",
                timestamp = System.currentTimeMillis(),
                source = "SAVINGS"
            )
            dao.upsert(tx)
            dao.upsertGoal(goal.copy(currentAmountMinor = goal.currentAmountMinor + amountMinor))
        }
    }

    fun createBackup(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val allTx = allTxs.first(); val allCards = cards.first(); val allBudgets = budgets.first(); val allRecurring = dao.getRecurringList(); val allCats = dao.getCategoriesList(); val allGoals = goals.first()
                val root = JSONObject()
                val txsArray = JSONArray(); allTx.forEach { t -> txsArray.put(JSONObject().apply { put("id", t.id); put("amountMinor", t.amountMinor); put("type", t.type.name); put("cardLast4", t.cardLast4 ?: JSONObject.NULL); put("description", t.description); put("category", t.category); put("timestamp", t.timestamp); put("source", t.source) }) }; root.put("txs", txsArray)
                val cardsArray = JSONArray(); allCards.forEach { c -> cardsArray.put(JSONObject().apply { put("last4", c.last4); put("name", c.name); put("initialBalanceMinor", c.initialBalanceMinor) }) }; root.put("cards", cardsArray)
                val budgetsArray = JSONArray(); allBudgets.forEach { b -> budgetsArray.put(JSONObject().apply { put("category", b.category); put("limitMinor", b.limitMinor) }) }; root.put("budgets", budgetsArray)
                val recurringArray = JSONArray(); allRecurring.forEach { r -> recurringArray.put(JSONObject().apply { put("id", r.id); put("amountMinor", r.amountMinor); put("type", r.type.name); put("cardLast4", r.cardLast4 ?: JSONObject.NULL); put("description", r.description); put("category", r.category); put("dayOfMonth", r.dayOfMonth); put("lastExecutedMonth", r.lastExecutedMonth) }) }; root.put("recurring", recurringArray)
                val catsArray = JSONArray(); allCats.forEach { c -> catsArray.put(JSONObject().apply { put("name", c.name); put("type", c.type.name) }) }; root.put("categories", catsArray)
                val goalsArray = JSONArray(); allGoals.forEach { g -> goalsArray.put(JSONObject().apply { put("id", g.id); put("name", g.name); put("targetAmountMinor", g.targetAmountMinor); put("currentAmountMinor", g.currentAmountMinor) }) }; root.put("goals", goalsArray)
                onResult(root.toString(2))
            } catch (e: Exception) { onResult("") }
        }
    }

    fun restoreBackup(jsonString: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val root = JSONObject(jsonString)
                dao.clearTxs(); dao.clearCards(); dao.clearBudgets(); dao.clearRecurring()
                val txsArray = root.optJSONArray("txs"); if (txsArray != null) { for (i in 0 until txsArray.length()) { val o = txsArray.getJSONObject(i); dao.upsert(Tx(id = o.optLong("id"), amountMinor = o.getLong("amountMinor"), type = TxType.valueOf(o.getString("type")), cardLast4 = if (o.isNull("cardLast4")) null else o.getString("cardLast4"), description = o.optString("description"), category = o.optString("category"), timestamp = o.getLong("timestamp"), source = o.optString("source"))) } }
                val cardsArray = root.optJSONArray("cards"); if (cardsArray != null) { for (i in 0 until cardsArray.length()) { val o = cardsArray.getJSONObject(i); dao.upsertCard(CardEntity(last4 = o.getString("last4"), name = o.getString("name"), initialBalanceMinor = o.getLong("initialBalanceMinor"))) } }
                val budgetsArray = root.optJSONArray("budgets"); if (budgetsArray != null) { for (i in 0 until budgetsArray.length()) { val o = budgetsArray.getJSONObject(i); dao.upsertBudget(BudgetEntity(category = o.getString("category"), limitMinor = o.getLong("limitMinor"))) } }
                val recurringArray = root.optJSONArray("recurring"); if (recurringArray != null) { for (i in 0 until recurringArray.length()) { val o = recurringArray.getJSONObject(i); dao.upsertRecurring(RecurringEntity(id = o.optLong("id"), amountMinor = o.getLong("amountMinor"), type = TxType.valueOf(o.getString("type")), cardLast4 = if (o.isNull("cardLast4")) null else o.getString("cardLast4"), description = o.optString("description"), category = o.optString("category"), dayOfMonth = o.getInt("dayOfMonth"), lastExecutedMonth = o.optString("lastExecutedMonth"))) } }
                val catsArray = root.optJSONArray("categories"); if (catsArray != null) { for (i in 0 until catsArray.length()) { val o = catsArray.getJSONObject(i); dao.upsertCategory(CategoryEntity(name = o.getString("name"), type = TxType.valueOf(o.getString("type")))) } }
                val goalsArray = root.optJSONArray("goals"); if (goalsArray != null) { for (i in 0 until goalsArray.length()) { val o = goalsArray.getJSONObject(i); dao.upsertGoal(SavingsGoalEntity(id = o.optLong("id"), name = o.getString("name"), targetAmountMinor = o.getLong("targetAmountMinor"), currentAmountMinor = o.getLong("currentAmountMinor"))) } }
                onResult(true)
            } catch (e: Exception) { e.printStackTrace(); onResult(false) }
        }
    }
}

class MainActivity : FragmentActivity() {
    private val pendingCheckTrigger = mutableStateOf(0)
    private val currentTheme = mutableStateOf(AppTheme.SYSTEM)
    private val currentLang = mutableStateOf(AppLanguage.RU)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        val prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        currentTheme.value = try { AppTheme.valueOf(prefs.getString("app_theme", "SYSTEM") ?: "SYSTEM") } catch (e: Exception) { AppTheme.SYSTEM }
        currentLang.value = try { AppLanguage.valueOf(prefs.getString("app_lang", "RU") ?: "RU") } catch (e: Exception) { AppLanguage.RU }

        setContent { 
            val theme by currentTheme
            val lang by currentLang
            val colorScheme = when (theme) {
                AppTheme.LIGHT -> lightColorScheme()
                AppTheme.DARK -> darkColorScheme()
                AppTheme.SYSTEM -> if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            }
            MaterialTheme(colorScheme = colorScheme) { 
                Surface {
                    var isAuthenticated by remember { mutableStateOf(false) }
                    if (isAuthenticated) App(pendingCheckTrigger = pendingCheckTrigger, currentTheme = currentTheme, currentLang = currentLang) 
                    else LockScreen(activity = this@MainActivity, lang = lang, onUnlocked = { isAuthenticated = true })
                } 
            } 
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingCheckTrigger.value++
    }
}

@Composable
fun LockScreen(activity: FragmentActivity, lang: AppLanguage, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val prefs = activity.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
    val savedPin = prefs.getString("pin", "0000") ?: "0000"
    val useBiometric = prefs.getBoolean("use_biometric", true)
    val biometricManager = BiometricManager.from(activity)
    val canUseBiometric = useBiometric && biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    val t = { key: String -> Translations.t(key, lang) }

    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp)); Text(t("locked"), style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(4); error = false }, label = { Text(t("pin")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, isError = error, supportingText = { if (error) Text(t("wrong_pin"), color = RED) })
        Spacer(Modifier.height(16.dp))
        Button(onClick = { if (pin == savedPin) onUnlocked() else { pin = ""; error = true } }, modifier = Modifier.fillMaxWidth()) { Text(t("login")) }
        if (canUseBiometric) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                val executor = ContextCompat.getMainExecutor(activity)
                val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { super.onAuthenticationSucceeded(result); onUnlocked() }
                })
                val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle(t("login")).setSubtitle(t("fingerprint")).setNegativeButtonText(t("pin")).build()
                biometricPrompt.authenticate(promptInfo)
            }) { Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text(t("fingerprint")) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: VM = viewModel(), pendingCheckTrigger: MutableState<Int>? = null, currentTheme: MutableState<AppTheme>, currentLang: MutableState<AppLanguage>) {
    val context = LocalContext.current
    val month by vm.month.collectAsState()
    val txs by vm.txs.collectAsState()
    val allTxs by vm.allTxs.collectAsState()
    val cards by vm.cards.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val recurring by vm.recurring.collectAsState()
    val goals by vm.goals.collectAsState()
    
    val categories by vm.categories.collectAsState()
    val expenseCategories = categories.filter { it.type == TxType.EXPENSE }.map { it.name }
    val incomeCategories = categories.filter { it.type == TxType.INCOME }.map { it.name }
    val t = { key: String -> Translations.t(key, currentLang.value) }

    var editing by remember { mutableStateOf<Tx?>(null) }
    var adding by remember { mutableStateOf(false) }
    var isPendingSms by remember { mutableStateOf(false) }
    var showCardManager by remember { mutableStateOf(false) }
    var showBudgetManager by remember { mutableStateOf(false) }
    var showRecurringManager by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var showGoalManager by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf("") }

    var importedTxs by remember { mutableStateOf<List<Tx>?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<TxType?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { _ -> }
    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }

    fun checkPendingSms() {
        if (editing != null || adding) return
        val prefs = context.getSharedPreferences("pending_sms", android.content.Context.MODE_PRIVATE)
        val pendingJson = prefs.getString("pending", null) ?: return
        try {
            val obj = JSONObject(pendingJson)
            val pendingTx = Tx(id = 0, amountMinor = obj.getLong("amountMinor"), type = TxType.valueOf(obj.getString("type")), cardLast4 = if (obj.isNull("card")) null else obj.getString("card"), description = obj.optString("description"), category = obj.optString("category"), timestamp = obj.optLong("timestamp"), source = "SMS")
            editing = pendingTx
            isPendingSms = true
        } catch (e: Exception) { prefs.edit().remove("pending").apply() }
    }

    val trigger = pendingCheckTrigger?.value ?: 0
    LaunchedEffect(trigger) { checkPendingSms() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) checkPendingSms() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let { try { context.contentResolver.openOutputStream(it)?.use { stream -> stream.write(generateCsv(allTxs).toByteArray()) }; scope.launch { snackbarHostState.showSnackbar("CSV OK!") } } catch (e: Exception) { scope.launch { snackbarHostState.showSnackbar("Error: ${e.message}") } } }
    }

    val jsonExportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let { vm.createBackup { jsonString -> if (jsonString.isNotEmpty()) { try { context.contentResolver.openOutputStream(it)?.use { stream -> stream.write(jsonString.toByteArray()) }; scope.launch { snackbarHostState.showSnackbar("JSON OK!") } } catch (e: Exception) { scope.launch { snackbarHostState.showSnackbar("Error: ${e.message}") } } } else { scope.launch { snackbarHostState.showSnackbar("Error") } } } }
    }

    val jsonImportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { try { context.contentResolver.openInputStream(it)?.use { stream -> pendingRestoreJson = stream.bufferedReader().readText(); showRestoreConfirm = true } } catch (e: Exception) { scope.launch { snackbarHostState.showSnackbar("Error") } } }
    }

    val fileImportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val mimeType = context.contentResolver.getType(it) ?: ""
                    val text = if (mimeType == "application/pdf") extractTextFromPdf(context, it) else context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""
                    val parsedTxs = parseImportedFile(text)
                    if (parsedTxs.isNotEmpty()) {
                        importedTxs = parsedTxs
                        showImportConfirm = true
                    } else {
                        snackbarHostState.showSnackbar("Не найдено транзакций в файле")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Ошибка чтения файла: ${e.message}")
                }
            }
        }
    }

    val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
    val expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    val zone = ZoneId.systemDefault()
    val filteredTxs = txs.filter { tx -> (searchQuery.isEmpty() || tx.description.contains(searchQuery, true) || Translations.translateCategory(tx.category, currentLang.value).contains(searchQuery, true)) && (filterType == null || tx.type == filterType) }
    val byDay = filteredTxs.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }

    val cardBalances = cards.associate { card ->
        val cardTxs = allTxs.filter { it.cardLast4 == card.last4 || (card.last4 == "CASH" && it.cardLast4 == null) }
        val cardIncome = cardTxs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
        val cardExpense = cardTxs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
        val cardTransfer = cardTxs.filter { it.type == TxType.TRANSFER }.sumOf { it.amountMinor }
        card.last4 to (card.initialBalanceMinor + cardIncome - cardExpense - cardTransfer)
    }

    // ИСПРАВЛЕНИЕ: Теперь виджет и общий баланс учитывают деньги в Копилке
    LaunchedEffect(cardBalances, goals) {
        val cardsTotal = cardBalances.values.sum()
        val goalsTotal = goals.sumOf { it.currentAmountMinor }
        val total = cardsTotal + goalsTotal
        
        val prefs = context.getSharedPreferences("widget_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putLong("total_balance", total).apply()
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val componentName = android.content.ComponentName(context, BalanceWidget::class.java)
        appWidgetManager.getAppWidgetIds(componentName).forEach { id -> BalanceWidget.updateWidget(context, appWidgetManager, id) }
    }

    if (showRestoreConfirm) {
        AlertDialog(onDismissRequest = { showRestoreConfirm = false }, title = { Text(t("restore")) }, text = { Text(t("restore") + "?") },
            confirmButton = { TextButton(onClick = { vm.restoreBackup(pendingRestoreJson) { success -> if (success) scope.launch { snackbarHostState.showSnackbar("OK!") } else scope.launch { snackbarHostState.showSnackbar("Error") } }; showRestoreConfirm = false }) { Text(t("confirm"), color = RED) } },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text(t("cancel")) } })
    }

    if (showImportConfirm && importedTxs != null) {
        AlertDialog(onDismissRequest = { showImportConfirm = false; importedTxs = null }, title = { Text("Импорт данных") }, 
            text = { Text("${t("import_confirm")} ${importedTxs!!.size}") },
            confirmButton = { TextButton(onClick = { 
                vm.bulkSave(importedTxs!!)
                scope.launch { snackbarHostState.showSnackbar("Импортировано: ${importedTxs!!.size}") }
                showImportConfirm = false; importedTxs = null 
            }) { Text(t("confirm"), color = GREEN) } },
            dismissButton = { TextButton(onClick = { showImportConfirm = false; importedTxs = null }) { Text(t("cancel")) } })
    }

    if (showSettings) SettingsDialog(context, onDismiss = { showSettings = false }, snackbarHostState, scope, currentTheme, currentLang)

    if (showCategoryManager) {
        CategoryManagerDialog(categories, currentLang.value, onSave = { vm.saveCategory(it) }, onDelete = { vm.deleteCategory(it) }, onDismiss = { showCategoryManager = false })
    }

    if (showGoalManager) {
        GoalManagerDialog(goals, cards, currentLang.value, 
            onSaveGoal = { vm.saveGoal(it) }, 
            onDeleteGoal = { vm.deleteGoal(it) }, 
            onDeposit = { goal, card, amount -> vm.depositToGoal(goal, card, amount) }, 
            onDismiss = { showGoalManager = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically) { IconButton({ vm.shift(-1) }) { Icon(Icons.Default.ChevronLeft, t("cancel")) }; Text(month.format(MONTH_FMT).replaceFirstChar { it.uppercase() }); IconButton({ vm.shift(1) }) { Icon(Icons.Default.ChevronRight, t("add")) } } },
                actions = {
                    IconButton(onClick = { showGoalManager = true }) { Icon(Icons.Default.Savings, t("goals")) }
                    IconButton(onClick = { showCategoryManager = true }) { Icon(Icons.Default.Category, t("categories")) }
                    IconButton(onClick = { showRecurringManager = true }) { Icon(Icons.Default.Autorenew, t("recurring")) }
                    IconButton(onClick = { showBudgetManager = true }) { Icon(Icons.Default.AccountBalanceWallet, t("budgets")) }
                    IconButton(onClick = { showCardManager = true }) { Icon(Icons.Default.CreditCard, t("my_accounts")) }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(t("import_csv")) }, leadingIcon = { Icon(Icons.Default.FileDownload, null) }, onClick = { showMenu = false; fileImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv")) })
                            DropdownMenuItem(text = { Text(t("import_pdf")) }, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }, onClick = { showMenu = false; fileImportLauncher.launch(arrayOf("application/pdf")) })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(t("export")) }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { showMenu = false; csvExportLauncher.launch("uchet_export.csv") })
                            DropdownMenuItem(text = { Text(t("backup")) }, leadingIcon = { Icon(Icons.Default.Backup, null) }, onClick = { showMenu = false; jsonExportLauncher.launch("uchet_backup.json") })
                            DropdownMenuItem(text = { Text(t("restore")) }, leadingIcon = { Icon(Icons.Default.Restore, null) }, onClick = { showMenu = false; jsonImportLauncher.launch(arrayOf("application/json")) })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(t("settings")) }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { showMenu = false; showSettings = true })
                        }
                    }
                }
            )
        },
        floatingActionButton = { FloatingActionButton({ adding = true }) { Icon(Icons.Default.Add, t("add")) } }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp)) {
            item {
                OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(t("search")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filterType == null, onClick = { filterType = null }, label = { Text(t("all")) })
                    FilterChip(selected = filterType == TxType.INCOME, onClick = { filterType = TxType.INCOME }, label = { Text(t("income")) })
                    FilterChip(selected = filterType == TxType.EXPENSE, onClick = { filterType = TxType.EXPENSE }, label = { Text(t("expense")) })
                }
                Spacer(Modifier.height(16.dp))
            }

            if (goals.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Savings, null, tint = MaterialTheme.colorScheme.onSecondaryContainer); Spacer(Modifier.width(8.dp)); Text(t("goals"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.height(12.dp))
                            goals.forEach { goal ->
                                val progress = if (goal.targetAmountMinor > 0) goal.currentAmountMinor.toFloat() / goal.targetAmountMinor.toFloat() else 0f
                                val isDone = progress >= 1f
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(goal.name, fontWeight = FontWeight.Medium)
                                        Text("${goal.currentAmountMinor.money()} / ${goal.targetAmountMinor.money()}", style = MaterialTheme.typography.bodySmall, color = if (isDone) GREEN else Color.Unspecified)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp), color = if (isDone) GREEN else BLUE, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                    if (isDone) Text(t("completed"), color = GREEN, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (budgets.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(t("budgets_month"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp))
                        budgets.forEach { budget ->
                            val spent = txs.filter { it.type == TxType.EXPENSE && it.category == budget.category }.sumOf { it.amountMinor }
                            val progress = if (budget.limitMinor > 0) spent.toFloat() / budget.limitMinor.toFloat() else 0f
                            val progressColor = when { progress >= 1f -> RED; progress >= 0.8f -> YELLOW; else -> GREEN }
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(Translations.translateCategory(budget.category, currentLang.value), fontWeight = FontWeight.Medium); Text("${spent.money()} / ${budget.limitMinor.money()}", style = MaterialTheme.typography.bodySmall, color = if (progress >= 1f) RED else Color.Unspecified) }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = progressColor, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (cards.isNotEmpty()) item {
                Text(t("my_accounts"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cards.forEach { card ->
                        val currentBalance = cardBalances[card.last4] ?: card.initialBalanceMinor
                        Card(modifier = Modifier.width(140.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(12.dp)) { Text(card.name, style = MaterialTheme.typography.labelMedium); Text("${currentBalance.money()} UZS", fontWeight = FontWeight.Bold, color = if (currentBalance >= 0) GREEN else RED) }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            
            item {
                if (txs.any { it.type == TxType.EXPENSE }) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(t("daily_chart"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(16.dp))
                            DailyExpenseChart(txs = txs, yearMonth = month)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            item {
                if (allTxs.any { it.type == TxType.EXPENSE }) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(t("monthly_chart"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(16.dp))
                            MonthlyExpenseChart(allTxs = allTxs)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            
            item {
                val expensesByCategory = txs.filter { it.type == TxType.EXPENSE }.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
                if (expensesByCategory.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) { Text(t("exp_by_cat"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)); CategoryPieChart(expensesByCategory, currentLang.value) }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SumRow(t("income"), income, GREEN); SumRow(t("expense"), expense, RED); HorizontalDivider(Modifier.padding(vertical = 8.dp)); SumRow(t("total"), income - expense, MaterialTheme.colorScheme.onSurface, bold = true)
                    }
                }
            }
            if (filteredTxs.isEmpty()) item { Text(t("no_found"), Modifier.padding(top = 32.dp), color = Color.Gray) }
            byDay.forEach { (day, list) ->
                item { Text(day.format(DATE_FMT), Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = Color.Gray) }
                items(list, key = { it.id }) { tx -> TxRow(tx, currentLang.value) { editing = tx } }
            }
        }
    }

    if (adding || editing != null) {
        TxDialog(initial = editing, cards = cards, expenseCategories = expenseCategories, incomeCategories = incomeCategories, currentLang = currentLang.value, isPendingSms = isPendingSms,
            onDismiss = { if (isPendingSms) { context.getSharedPreferences("pending_sms", android.content.Context.MODE_PRIVATE).edit().remove("pending").apply(); isPendingSms = false }; adding = false; editing = null },
            onSave = { vm.save(it); if (isPendingSms) { context.getSharedPreferences("pending_sms", android.content.Context.MODE_PRIVATE).edit().remove("pending").apply(); isPendingSms = false }; adding = false; editing = null },
            onDelete = if (isPendingSms) null else editing?.let { e -> { 
                vm.delete(e); editing = null; 
                scope.launch { 
                    val timerJob = launch { delay(5000); snackbarHostState.currentSnackbarData?.dismiss() }
                    val result = snackbarHostState.showSnackbar(message = "Deleted", actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
                    timerJob.cancel(); 
                    if (result == SnackbarResult.ActionPerformed) vm.save(e) 
                } 
            } }
        )
    }

    if (showCardManager) CardManagerDialog(cards, cardBalances, allTxs, { vm.saveCard(it) }, { vm.deleteCard(it) }, currentLang.value, { showCardManager = false })
    if (showBudgetManager) BudgetManagerDialog(budgets, expenseCategories, { vm.saveBudget(it) }, { vm.deleteBudget(it) }, currentLang.value, { showBudgetManager = false })
    if (showRecurringManager) RecurringManagerDialog(recurring, cards, expenseCategories, incomeCategories, currentLang.value, { vm.saveRecurring(it) }, { vm.deleteRecurring(it) }, { showRecurringManager = false })
}

fun extractTextFromPdf(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val doc = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(doc)
            doc.close()
            text
        } ?: ""
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}

fun parseImportedFile(text: String): List<Tx> {
    val result = mutableListOf<Tx>()
    val lines = text.lines()
    val dateRegex = Regex("""(\d{2}[./]\d{2}[./]\d{4}|\d{4}-\d{2}-\d{2})""")
    val amountRegex = Regex("""(-?\d[\d\s]*[.,]?\d{0,2})\s*(?:uzs|сум|rub|руб|\$|€)?$""", RegexOption.IGNORE_CASE)

    for (line in lines) {
        if (line.isBlank()) continue
        val dateMatch = dateRegex.find(line)
        val amountMatch = amountRegex.find(line.trim())
        
        if (dateMatch != null && amountMatch != null) {
            val dateStr = dateMatch.value.replace(".", "-").replace("/", "-")
            val cleanAmountStr = amountMatch.groupValues[1].replace(" ", "").replace(",", ".")
            val amountDouble = cleanAmountStr.toDoubleOrNull() ?: continue
            val isIncome = !line.contains("-") && amountDouble > 0 && (line.contains("пополнение", true) || line.contains("зачисление", true) || line.contains("income", true))
            
            val amountMinor = kotlin.math.abs(amountDouble).toBigDecimal().movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
            if (amountMinor <= 0) continue

            val timestamp = try {
                val parts = dateStr.split("-")
                val (year, month, day) = if (parts[0].length == 4) Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()) else Triple(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) { System.currentTimeMillis() }

            val description = line.replace(dateMatch.value, "").replace(amountMatch.value, "").trim().replace("\\s+".toRegex(), " ").take(40)
            val category = if (isIncome) "💵 Зарплата" else "📦 Другое"

            result.add(Tx(
                amountMinor = amountMinor,
                type = if (isIncome) TxType.INCOME else TxType.EXPENSE,
                description = description.ifBlank { "Импорт" },
                category = category,
                timestamp = timestamp,
                source = "IMPORT"
            ))
        }
    }
    return result
}

@Composable
fun GoalManagerDialog(
    goals: List<SavingsGoalEntity>, 
    cards: List<CardEntity>,
    lang: AppLanguage,
    onSaveGoal: (SavingsGoalEntity) -> Unit, 
    onDeleteGoal: (SavingsGoalEntity) -> Unit, 
    onDeposit: (SavingsGoalEntity, String, Long) -> Unit, 
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    
    var depositGoal by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    var depositAmount by remember { mutableStateOf("") }
    var depositCard by remember { mutableStateOf(cards.firstOrNull()?.last4 ?: "CASH") }
    
    val t = { key: String -> Translations.t(key, lang) }

    val currentDepositGoal = depositGoal

    if (currentDepositGoal != null) {
        AlertDialog(
            onDismissRequest = { depositGoal = null },
            title = { Text("${t("deposit")}: ${currentDepositGoal.name}") },
            text = {
                Column {
                    OutlinedTextField(value = depositAmount, onValueChange = { depositAmount = it }, label = { Text(t("deposit_amount")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(t("source_card") + ":", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cards.forEach { c -> FilterChip(selected = depositCard == c.last4, onClick = { depositCard = c.last4 }, label = { Text(c.name) }) }
                        FilterChip(selected = depositCard == "CASH", onClick = { depositCard = "CASH" }, label = { Text(t("cash")) })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amountMinor = runCatching { BigDecimal(depositAmount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull()
                    if (amountMinor != null && amountMinor > 0) {
                        onDeposit(currentDepositGoal, depositCard, amountMinor)
                        depositGoal = null; depositAmount = ""
                    }
                }) { Text(t("deposit")) }
            },
            dismissButton = { TextButton(onClick = { depositGoal = null }) { Text(t("cancel")) } }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss, title = { Text(t("goals")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(t("add_goal") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t("goal_name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = targetAmount, onValueChange = { targetAmount = it }, label = { Text(t("target_amount")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val targetMinor = runCatching { BigDecimal(targetAmount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull()
                        if (name.isNotBlank() && targetMinor != null && targetMinor > 0) {
                            onSaveGoal(SavingsGoalEntity(name = name.trim(), targetAmountMinor = targetMinor))
                            name = ""; targetAmount = ""
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(t("add_goal")) }
                    
                    Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                    Text(t("goals") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                    
                    if (goals.isEmpty()) Text(t("no_found"), color = Color.Gray)
                    
                    goals.forEach { goal ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { 
                                Text(goal.name, fontWeight = FontWeight.Bold)
                                Text("${goal.currentAmountMinor.money()} / ${goal.targetAmountMinor.money()}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) 
                            }
                            IconButton(onClick = { depositGoal = goal }) { Icon(Icons.Default.Add, t("deposit"), tint = GREEN) }
                            IconButton(onClick = { onDeleteGoal(goal) }) { Icon(Icons.Default.Delete, t("delete"), tint = RED) }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
        )
    }
}

@Composable
fun DailyExpenseChart(txs: List<Tx>, yearMonth: YearMonth) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val zone = ZoneId.systemDefault()
    val dailyExpenses = (1..daysInMonth).associateWith { day ->
        txs.filter { val date = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate(); date.dayOfMonth == day && it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    }
    val maxExpense = (dailyExpenses.values.maxOrNull() ?: 1L).toFloat()
    val barColor = MaterialTheme.colorScheme.primary

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val width = size.width; val height = size.height; val barWidth = (width / daysInMonth) * 0.6f; val step = width / daysInMonth
            dailyExpenses.forEach { (day, amount) ->
                if (amount > 0) {
                    val barHeight = (amount / maxExpense) * height
                    val x = (day - 1) * step + (step - barWidth) / 2
                    drawRoundRect(color = barColor, topLeft = Offset(x, height - barHeight), size = Size(barWidth, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("15", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("$daysInMonth", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun MonthlyExpenseChart(allTxs: List<Tx>) {
    val currentYear = Year.now()
    val zone = ZoneId.systemDefault()
    val monthlyExpenses = (1..12).associateWith { m ->
        allTxs.filter { val date = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate(); date.year == currentYear.value && date.monthValue == m && it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    }
    val maxExpense = (monthlyExpenses.values.maxOrNull() ?: 1L).toFloat()
    val barColor = MaterialTheme.colorScheme.secondary

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val width = size.width; val height = size.height; val step = width / 12; val barWidth = step * 0.6f
            monthlyExpenses.forEach { (month, amount) ->
                if (amount > 0) {
                    val barHeight = (amount / maxExpense) * height
                    val x = (month - 1) * step + (step - barWidth) / 2
                    drawRoundRect(color = barColor, topLeft = Offset(x, height - barHeight), size = Size(barWidth, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("4", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("7", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("10", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("12", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerDialog(categories: List<CategoryEntity>, lang: AppLanguage, onSave: (CategoryEntity) -> Unit, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    var isIncome by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val t = { key: String -> Translations.t(key, lang) }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(t("categories")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(t("add_cat") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!isIncome, { isIncome = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(t("expense")) }
                    SegmentedButton(isIncome, { isIncome = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(t("income")) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t("category")) }, placeholder = { Text("🐶") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { if (name.isNotBlank() && !categories.any { it.name == name }) { onSave(CategoryEntity(name = name.trim(), type = if (isIncome) TxType.INCOME else TxType.EXPENSE)); name = "" } }, modifier = Modifier.fillMaxWidth()) { Text(t("add")) }

                Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                
                Text(t("expense") + ":", style = MaterialTheme.typography.titleSmall, color = RED)
                categories.filter { it.type == TxType.EXPENSE }.forEach { c ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(Translations.translateCategory(c.name, lang), style = MaterialTheme.typography.bodyLarge)
                        IconButton({ onDelete(c.name) }) { Icon(Icons.Default.Delete, "Delete", tint = RED) }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Text(t("income") + ":", style = MaterialTheme.typography.titleSmall, color = GREEN)
                categories.filter { it.type == TxType.INCOME }.forEach { c ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(Translations.translateCategory(c.name, lang), style = MaterialTheme.typography.bodyLarge)
                        IconButton({ onDelete(c.name) }) { Icon(Icons.Default.Delete, "Delete", tint = RED) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(context: android.content.Context, onDismiss: () -> Unit, snackbarHostState: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope, currentTheme: MutableState<AppTheme>, currentLang: MutableState<AppLanguage>) {
    val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
    val settingsPrefs = context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
    var oldPin by remember { mutableStateOf("") }; var newPin by remember { mutableStateOf("") }; var confirmPin by remember { mutableStateOf("") }
    var useBiometric by remember { mutableStateOf(prefs.getBoolean("use_biometric", true)) }
    val savedPin = prefs.getString("pin", "0000") ?: "0000"
    val t = { key: String -> Translations.t(key, currentLang.value) }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(t("settings")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                
                Text(t("language"), style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = currentLang.value == AppLanguage.RU, onClick = { currentLang.value = AppLanguage.RU; settingsPrefs.edit().putString("app_lang", "RU").apply() }, label = { Text("Русский") })
                    FilterChip(selected = currentLang.value == AppLanguage.UZ, onClick = { currentLang.value = AppLanguage.UZ; settingsPrefs.edit().putString("app_lang", "UZ").apply() }, label = { Text("O'zbekcha") })
                    FilterChip(selected = currentLang.value == AppLanguage.EN, onClick = { currentLang.value = AppLanguage.EN; settingsPrefs.edit().putString("app_lang", "EN").apply() }, label = { Text("English") })
                    FilterChip(selected = currentLang.value == AppLanguage.TG, onClick = { currentLang.value = AppLanguage.TG; settingsPrefs.edit().putString("app_lang", "TG").apply() }, label = { Text("Тоҷикӣ") })
                    FilterChip(selected = currentLang.value == AppLanguage.KAA, onClick = { currentLang.value = AppLanguage.KAA; settingsPrefs.edit().putString("app_lang", "KAA").apply() }, label = { Text("Qaraqalpaqsha") })
                    FilterChip(selected = currentLang.value == AppLanguage.KK, onClick = { currentLang.value = AppLanguage.KK; settingsPrefs.edit().putString("app_lang", "KK").apply() }, label = { Text("Қазақша") })
                }

                Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))

                Text(t("theme"), style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = currentTheme.value == AppTheme.SYSTEM, onClick = { currentTheme.value = AppTheme.SYSTEM; settingsPrefs.edit().putString("app_theme", "SYSTEM").apply() }, label = { Text(t("sys")) })
                    FilterChip(selected = currentTheme.value == AppTheme.LIGHT, onClick = { currentTheme.value = AppTheme.LIGHT; settingsPrefs.edit().putString("app_theme", "LIGHT").apply() }, label = { Text(t("light")) })
                    FilterChip(selected = currentTheme.value == AppTheme.DARK, onClick = { currentTheme.value = AppTheme.DARK; settingsPrefs.edit().putString("app_theme", "DARK").apply() }, label = { Text(t("dark")) })
                }
                
                Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                
                Text(t("change_pin"), style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = oldPin, onValueChange = { oldPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("pin")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("pin") + " (new)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = confirmPin, onValueChange = { confirmPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("pin") + " (repeat)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    when { oldPin != savedPin -> scope.launch { snackbarHostState.showSnackbar(t("wrong_pin")) }; newPin.length != 4 -> scope.launch { snackbarHostState.showSnackbar("PIN: 4") }; newPin != confirmPin -> scope.launch { snackbarHostState.showSnackbar("PIN Error") }; else -> { prefs.edit().putString("pin", newPin).apply(); oldPin = ""; newPin = ""; confirmPin = ""; scope.launch { snackbarHostState.showSnackbar("PIN OK!") } } }
                }, modifier = Modifier.fillMaxWidth()) { Text(t("change_pin")) }
                
                Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(t("biometric"), style = MaterialTheme.typography.titleSmall) }
                    Switch(checked = useBiometric, onCheckedChange = { useBiometric = it; prefs.edit().putBoolean("use_biometric", it).apply() })
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}

fun generateCsv(transactions: List<Tx>): String {
    val sb = StringBuilder("Дата,Время,Тип,Сумма (UZS),Категория,Описание,Карта,Источник\n")
    val zone = ZoneId.systemDefault()
    transactions.sortedBy { it.timestamp }.forEach { t ->
        val dt = Instant.ofEpochMilli(t.timestamp).atZone(zone)
        val amount = BigDecimal(t.amountMinor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()
        sb.append("${dt.format(DATE_FMT)},${dt.format(DateTimeFormatter.ofPattern("HH:mm"))},${t.type.label},$amount,${t.category.replace(",", " ")},${t.description.replace(",", " ").replace("\n", " ")},${if (t.cardLast4.isNullOrEmpty()) "Наличные" else "Карта *${t.cardLast4}"},${if (t.source == "AUTO") "Авто" else if (t.source == "SMS") "СМС" else if (t.source == "SAVINGS") "Копилка" else if (t.source == "IMPORT") "Импорт" else "Вручную"}\n")
    }
    return sb.toString()
}

@Composable
fun CategoryPieChart(data: Map<String, Long>, lang: AppLanguage) {
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
                    Box(Modifier.size(10.dp).background(colors[index % colors.size], shape = CircleShape)); Spacer(Modifier.width(6.dp))
                    Text("${Translations.translateCategory(entry.key, lang)}: ${(entry.value / total * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
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
fun TxRow(t: Tx, lang: AppLanguage, onClick: () -> Unit) {
    val (sign, color) = when (t.type) { TxType.INCOME -> "+" to GREEN; TxType.EXPENSE -> "−" to RED; TxType.TRANSFER -> "→" to BLUE }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val icon = when (t.source) { "AUTO" -> "🔄 "; "SMS" -> "✉️ "; "SAVINGS" -> "🏦 "; "IMPORT" -> "📥 "; else -> "" }
            Text("$icon${Translations.translateCategory(t.category, lang)}  ${t.description}".trim(), maxLines = 2)
            val time = Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
            val cardText = if (t.cardLast4.isNullOrEmpty()) Translations.t("cash", lang) else "*${t.cardLast4}"
            Text(listOfNotNull(time, cardText).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Text("$sign${t.amountMinor.money()}", color = color, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxDialog(initial: Tx?, cards: List<CardEntity>, expenseCategories: List<String>, incomeCategories: List<String>, currentLang: AppLanguage, isPendingSms: Boolean = false, onDismiss: () -> Unit, onSave: (Tx) -> Unit, onDelete: (() -> Unit)?) {
    val zone = ZoneId.systemDefault()
    val initDt = initial?.let { Instant.ofEpochMilli(it.timestamp).atZone(zone) } ?: ZonedDateTime.now(zone)
    var type by remember { mutableStateOf(initial?.type ?: TxType.EXPENSE) }
    var category by remember { mutableStateOf(initial?.category ?: expenseCategories.lastOrNull() ?: "📦 Другое") }
    var amount by remember { mutableStateOf(initial?.let { BigDecimal(it.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString() } ?: "") }
    var selectedCard by remember { mutableStateOf(initial?.cardLast4 ?: "CASH") }
    var isCustomCard by remember { mutableStateOf(initial?.cardLast4?.let { !cards.map { c -> c.last4 }.contains(it) } ?: false) }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var date by remember { mutableStateOf(initDt.format(DATE_FMT)) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    val t = { key: String -> Translations.t(key, currentLang) }

    LaunchedEffect(type) {
        val currentList = if (type == TxType.INCOME) incomeCategories else expenseCategories
        if (!currentList.contains(category)) category = currentList.lastOrNull() ?: "📦 Другое"
    }

    val imagePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            isLoading = true; error = null
            val bitmap = uriToBitmap(context, it)
            bitmap?.let { bmp ->
                val image = InputImage.fromBitmap(bmp, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image).addOnSuccessListener { visionText ->
                    val parsedData = parseSmsText(visionText.text)
                    if (parsedData != null) {
                        amount = parsedData.amountStr; desc = parsedData.description; 
                        val listToSearch = if (type == TxType.INCOME) incomeCategories else expenseCategories
                        if (listToSearch.contains(parsedData.category)) category = parsedData.category
                        
                        if (parsedData.card != null) { selectedCard = parsedData.card; if (cards.none { it.last4 == selectedCard }) isCustomCard = true }
                    } else { error = "Error" }
                    isLoading = false
                }.addOnFailureListener { isLoading = false; error = "Error" }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when { isPendingSms -> t("sms_title"); initial == null -> t("new_tx"); else -> t("edit") }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isPendingSms) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(12.dp)) { Text(t("sms_data"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text(t("sms_title"), style = MaterialTheme.typography.bodySmall) }
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TxType.values().forEachIndexed { i, tt -> SegmentedButton(type == tt, { type = tt }, SegmentedButtonDefaults.itemShape(i, TxType.values().size)) { Text(if(tt == TxType.INCOME) t("income") else if(tt == TxType.EXPENSE) t("expense") else "→") } }
                }
                if (!isPendingSms) Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) { Text(if (isLoading) "..." else "📸 " + t("upload_sms")) }
                OutlinedTextField(amount, { amount = it }, label = { Text(t("amount")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text(t("category") + ":", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currentCategories = if (type == TxType.INCOME) incomeCategories else expenseCategories
                    currentCategories.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(Translations.translateCategory(c, currentLang)) }) }
                }
                Text(t("account") + ":", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cards.forEach { c -> FilterChip(selected = selectedCard == c.last4 && !isCustomCard, onClick = { selectedCard = c.last4; isCustomCard = false }, label = { Text(c.name) }) }
                    FilterChip(selected = isCustomCard, onClick = { isCustomCard = true; selectedCard = "" }, label = { Text(t("other")) })
                }
                if (isCustomCard) OutlinedTextField(value = selectedCard, onValueChange = { selectedCard = it.filter(Char::isDigit).take(4) }, label = { Text(t("account")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text(t("desc")) })
                OutlinedTextField(date, { date = it }, label = { Text(t("date")) }, singleLine = true)
                error?.let { Text(it, color = RED) }
            }
        },
        confirmButton = {
            TextButton({
                val minor = runCatching { BigDecimal(amount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull()
                val d = runCatching { LocalDate.parse(date.trim(), DATE_FMT) }.getOrNull()
                when {
                    minor == null || minor <= 0 -> error = "Error"
                    d == null -> error = "Error"
                    isCustomCard && selectedCard.length != 4 -> error = "Error"
                    else -> {
                        val finalCard = if (selectedCard.isEmpty() || selectedCard == "CASH") null else selectedCard
                        onSave(Tx(id = initial?.id ?: 0, amountMinor = minor, type = type, cardLast4 = finalCard, description = desc.trim(), category = category, timestamp = d.atTime(initDt.toLocalTime()).atZone(zone).toInstant().toEpochMilli(), source = initial?.source ?: if (isPendingSms) "SMS" else "MANUAL"))
                    }
                }
            }) { Text(if (isPendingSms) t("confirm") else t("save")) }
        },
        dismissButton = { Row { onDelete?.let { TextButton(it) { Text(t("delete"), color = RED) } }; TextButton(onDismiss) { Text(if (isPendingSms) t("reject") else t("cancel")) } } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringManagerDialog(recurring: List<RecurringEntity>, cards: List<CardEntity>, expenseCategories: List<String>, incomeCategories: List<String>, currentLang: AppLanguage, onSave: (RecurringEntity) -> Unit, onDelete: (RecurringEntity) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var category by remember { mutableStateOf(expenseCategories.lastOrNull() ?: "📦 Другое") }
    var amount by remember { mutableStateOf("") }; var selectedCard by remember { mutableStateOf("CASH") }; var desc by remember { mutableStateOf("") }; var dayOfMonth by remember { mutableStateOf("1") }
    val t = { key: String -> Translations.t(key, currentLang) }
    
    LaunchedEffect(type) {
        val currentList = if (type == TxType.INCOME) incomeCategories else expenseCategories
        if (!currentList.contains(category)) category = currentList.lastOrNull() ?: "📦 Другое"
    }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(t("recurring")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(t("add_temp") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { TxType.values().forEachIndexed { i, tt -> SegmentedButton(type == tt, { type = tt }, SegmentedButtonDefaults.itemShape(i, TxType.values().size)) { Text(if(tt == TxType.INCOME) t("income") else if(tt == TxType.EXPENSE) t("expense") else "→") } } }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text(t("amount")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(t("desc")) }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = dayOfMonth, onValueChange = { dayOfMonth = it.filter(Char::isDigit).take(2) }, label = { Text(t("day")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); Spacer(Modifier.height(8.dp))
                Text(t("category") + ":", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { val currentCategories = if (type == TxType.INCOME) incomeCategories else expenseCategories; currentCategories.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(Translations.translateCategory(c, currentLang)) }) } }
                Spacer(Modifier.height(8.dp))
                Text(t("account") + ":", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { cards.forEach { c -> FilterChip(selected = selectedCard == c.last4, onClick = { selectedCard = c.last4 }, label = { Text(c.name) }) } }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { val minor = runCatching { BigDecimal(amount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull(); val day = dayOfMonth.toIntOrNull(); if (minor != null && minor > 0 && day != null && day in 1..31) { onSave(RecurringEntity(amountMinor = minor, type = type, cardLast4 = if(selectedCard == "CASH") null else selectedCard, description = desc.trim(), category = category, dayOfMonth = day)); amount = ""; desc = "" } }, modifier = Modifier.fillMaxWidth()) { Text(t("add_temp")) }
                Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                Text(t("recurring") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                if (recurring.isEmpty()) Text(t("no_found"), color = Color.Gray)
                recurring.forEach { r -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(r.description.ifBlank { Translations.translateCategory(r.category, currentLang) }, fontWeight = FontWeight.Bold); Text("${r.amountMinor.money()} UZS · ${r.dayOfMonth}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; IconButton({ onDelete(r) }) { Icon(Icons.Default.Delete, "Delete", tint = RED) } } }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}

@Composable
fun BudgetManagerDialog(budgets: List<BudgetEntity>, expenseCategories: List<String>, onSave: (BudgetEntity) -> Unit, onDelete: (String) -> Unit, currentLang: AppLanguage, onDismiss: () -> Unit) {
    var selectedCategory by remember { mutableStateOf(expenseCategories.firstOrNull() ?: "📦 Другое") }
    var limitInput by remember { mutableStateOf("") }
    val t = { key: String -> Translations.t(key, currentLang) }
    
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(t("budgets")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(t("set_limit") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { expenseCategories.forEach { c -> FilterChip(selected = selectedCategory == c, onClick = { selectedCategory = c }, label = { Text(Translations.translateCategory(c, currentLang)) }) } }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = limitInput, onValueChange = { limitInput = it }, label = { Text(t("limit")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); Spacer(Modifier.height(8.dp))
                Button(onClick = { val limitMinor = runCatching { BigDecimal(limitInput.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull(); if (limitMinor != null && limitMinor > 0) { onSave(BudgetEntity(category = selectedCategory, limitMinor = limitMinor)); limitInput = "" } }, modifier = Modifier.fillMaxWidth()) { Text(t("set_limit")) }
                Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                Text(t("budgets") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                if (budgets.isEmpty()) Text(t("no_found"), color = Color.Gray)
                budgets.forEach { b -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(Translations.translateCategory(b.category, currentLang), fontWeight = FontWeight.Bold); Text("${t("limit")}: ${b.limitMinor.money()} UZS", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; IconButton({ onDelete(b.category) }) { Icon(Icons.Default.Delete, "Delete", tint = RED) } } }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}

@Composable
fun CardManagerDialog(cards: List<CardEntity>, cardBalances: Map<String, Long>, allTxs: List<Tx>, onSave: (CardEntity) -> Unit, onDelete: (String) -> Unit, currentLang: AppLanguage, onDismiss: () -> Unit) {
    var last4 by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var initialBalance by remember { mutableStateOf("") }
    var editingCard by remember { mutableStateOf<CardEntity?>(null) }; var actualBalanceInput by remember { mutableStateOf("") }
    val t = { key: String -> Translations.t(key, currentLang) }

    if (editingCard != null) {
        val card = editingCard!!; val currentMathBalance = cardBalances[card.last4] ?: card.initialBalanceMinor
        AlertDialog(onDismissRequest = { editingCard = null }, title = { Text(card.name) },
            text = { Column { Text("${currentMathBalance.money()} UZS", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(8.dp)); Text(t("amount") + ":", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = actualBalanceInput, onValueChange = { actualBalanceInput = it }, label = { Text(t("amount")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()) } },
            confirmButton = { TextButton(onClick = { val actualMinor = runCatching { BigDecimal(actualBalanceInput.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull(); if (actualMinor != null) { onSave(card.copy(initialBalanceMinor = card.initialBalanceMinor + (actualMinor - currentMathBalance))); editingCard = null; actualBalanceInput = "" } }) { Text(t("save")) } },
            dismissButton = { TextButton(onClick = { editingCard = null }) { Text(t("cancel")) } })
    } else {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(t("my_accounts")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(t("add") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t("desc")) }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = last4, onValueChange = { last4 = it.uppercase() }, label = { Text(t("account")) }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = initialBalance, onValueChange = { initialBalance = it }, label = { Text(t("amount")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); Spacer(Modifier.height(8.dp))
                    Button(onClick = { val balanceMinor = runCatching { BigDecimal(initialBalance.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull() ?: 0L; if (name.isNotBlank() && last4.isNotBlank()) { onSave(CardEntity(last4 = last4, name = name, initialBalanceMinor = balanceMinor)); name = ""; last4 = ""; initialBalance = "" } }, modifier = Modifier.fillMaxWidth()) { Text(t("add")) }
                    Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                    Text(t("my_accounts") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                    if (cards.isEmpty()) Text(t("no_found"), color = Color.Gray)
                    cards.forEach { c ->
                        val currentBal = cardBalances[c.last4] ?: c.initialBalanceMinor
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(c.name, fontWeight = FontWeight.Bold); Text("${currentBal.money()} UZS", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                            IconButton(onClick = { editingCard = c; actualBalanceInput = BigDecimal(currentBal).movePointLeft(2).stripTrailingZeros().toPlainString() }) { Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary) }
                            IconButton({ onDelete(c.last4) }) { Icon(Icons.Default.Delete, "Delete", tint = RED) }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } })
    }
}

fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        else MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    } catch (e: Exception) { null }
}

data class ParsedSms(val amountStr: String, val card: String?, val description: String, val category: String)

fun parseSmsText(text: String): ParsedSms? {
    val lowerText = text.lowercase()
    val totalRegex = Regex("""(?:итого|сумма|total|жами)[\s:]*([\d\s]+(?:[.,]\d{1,2})?)\s*(?:uzs|сум|rub|руб|\$|€)?""", RegexOption.IGNORE_CASE)
    val amountRegex = Regex("""([\d\s]+(?:[.,]\d{1,2})?)\s*(?:uzs|сум|rub|руб|\$|€)""", RegexOption.IGNORE_CASE)
    
    var amountStr = totalRegex.find(text)?.groupValues?.get(1)?.replace(" ", "")?.replace(",", ".")
    if (amountStr == null) {
        amountStr = amountRegex.find(text)?.groupValues?.get(1)?.replace(" ", "")?.replace(",", ".")
    }
    
    if (amountStr == null || amountStr.toBigDecimalOrNull() == null) return null
    
    val card = Regex("""(?:карта|card|\*|х)\s*(\d{4})""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.take(4)
    val description = text.take(40).replace("\n", " ").trim()
    val category = when {
        lowerText.contains("магнит") || lowerText.contains("пятерочка") || lowerText.contains("еда") || lowerText.contains("кафе") || lowerText.contains("ресторан") || lowerText.contains("market") -> "🍔 Еда"
        lowerText.contains("такси") || lowerText.contains("яндекс go") || lowerText.contains("бензин") || lowerText.contains("метро") -> "🚕 Транспорт"
        lowerText.contains("аптека") || lowerText.contains("клиника") -> "💊 Здоровье"
        lowerText.contains("аренда") || lowerText.contains("квартплата") -> "🏠 Жилье"
        lowerText.contains("зарплата") || lowerText.contains("аванс") -> "💵 Зарплата"
        lowerText.contains("перевод") -> "💸 Перевод"
        else -> "📦 Другое"
    }
    return ParsedSms(amountStr, card, description, category)
}
