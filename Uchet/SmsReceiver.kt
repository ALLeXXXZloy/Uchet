package uz.uchet

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val fullText = messages.joinToString("") { it.messageBody ?: "" }
            val sender = messages.firstOrNull()?.originatingAddress ?: ""

            // Проверяем, что это банк (по имени отправителя или короткому номеру)
            val isBank = sender.contains("bank", true) || 
                         sender.contains("kapital", true) || 
                         sender.contains("uzum", true) || 
                         sender.contains("agrobank", true) || 
                         sender.contains("sqb", true) || 
                         sender.length <= 6
                         
            if (isBank && fullText.isNotEmpty()) {
                val parsed = parseAutoSms(fullText)
                if (parsed != null) {
                    // 1. Сохраняем во временное хранилище (ожидает подтверждения)
                    val prefs = context.getSharedPreferences("pending_sms", Context.MODE_PRIVATE)
                    val pendingJson = JSONObject().apply {
                        put("amountMinor", parsed.amountMinor)
                        put("type", parsed.type.name)
                        put("card", parsed.card ?: JSONObject.NULL)
                        put("description", parsed.description)
                        put("category", parsed.category)
                        put("timestamp", System.currentTimeMillis())
                    }
                    prefs.edit().putString("pending", pendingJson.toString()).apply()

                    // 2. Показываем уведомление
                    showNotification(context, parsed)
                }
            }
        }
    }

    private fun showNotification(context: Context, parsed: AutoParsedSms) {
        val channelId = "sms_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "SMS операции", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return // Нет разрешения на уведомления
        }

        // При клике на уведомление открываем приложение
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountStr = "${parsed.amountMinor / 100}.${String.format("%02d", parsed.amountMinor % 100)}"
        val typeStr = if (parsed.type == TxType.INCOME) "Доход" else "Расход"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Новая операция из СМС")
            .setContentText("$typeStr: $amountStr UZS. Нажмите для проверки")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$typeStr: $amountStr UZS\n${parsed.description}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }
}

// Модель для распознанных данных
data class AutoParsedSms(val amountMinor: Long, val type: TxType, val card: String?, val description: String, val category: String)

// Умный парсер для автоматических СМС
fun parseAutoSms(text: String): AutoParsedSms? {
    val lowerText = text.lowercase()
    
    // 1. Определяем тип
    val isIncome = lowerText.contains("зачисление") || lowerText.contains("пополнение") || 
                   lowerText.contains("перевод от") || lowerText.contains("kirdi") || lowerText.contains("поступление")
    val isExpense = lowerText.contains("списание") || lowerText.contains("оплата") || 
                    lowerText.contains("покупка") || lowerText.contains("перевод") || lowerText.contains("chyqim")
                    
    if (!isIncome && !isExpense) return null
    val type = if (isIncome) TxType.INCOME else TxType.EXPENSE

    // 2. Ищем сумму
    val amountRegex = Regex("""([\d\s]+(?:[.,]\d{1,2})?)\s*(?:uzs|сум|rub|руб|\$|€)""", RegexOption.IGNORE_CASE)
    val amountMatch = amountRegex.find(text)
    val amountStr = amountMatch?.groupValues?.get(1)?.replace(" ", "")?.replace(",", ".") ?: return null
    val amountMinor = amountStr.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: return null

    // 3. Ищем карту
    val cardRegex = Regex("""(?:карта|card|\*|х|card)\s*(\d{4})""", RegexOption.IGNORE_CASE)
    val card = cardRegex.find(text)?.groupValues?.get(1)?.take(4)

    // 4. Определяем категорию
    val category = when {
        lowerText.contains("магнит") || lowerText.contains("пятерочка") || lowerText.contains("корзинка") || lowerText.contains("еда") || lowerText.contains("кафе") || lowerText.contains("ресторан") -> "🍔 Еда"
        lowerText.contains("такси") || lowerText.contains("яндекс go") || lowerText.contains("бензин") || lowerText.contains("метро") || lowerText.contains("транспорт") -> "🚕 Транспорт"
        lowerText.contains("аптека") || lowerText.contains("клиника") || lowerText.contains("здоровье") -> "💊 Здоровье"
        lowerText.contains("аренда") || lowerText.contains("квартплата") || lowerText.contains("коммунал") -> "🏠 Жилье"
        lowerText.contains("зарплата") || lowerText.contains("аванс") -> "💵 Зарплата"
        lowerText.contains("перевод") -> "💸 Перевод"
        else -> "📦 Другое"
    }

    val description = text.take(40).replace("\n", " ").trim()
    return AutoParsedSms(amountMinor, type, card, description, category)
}
