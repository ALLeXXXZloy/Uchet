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
import java.math.BigDecimal

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val fullText = messages.joinToString("") { it.messageBody ?: "" }
            val sender = messages.firstOrNull()?.originatingAddress ?: ""

            val isBank = sender.contains("bank", true) || sender.contains("kapital", true) || 
                         sender.contains("uzum", true) || sender.contains("agrobank", true) || 
                         sender.contains("sqb", true) || sender.contains("tez", true) || sender.length <= 6
                         
            if (isBank && fullText.isNotEmpty()) {
                val parsed = parseAutoSms(fullText)
                if (parsed != null) {
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
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

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

data class AutoParsedSms(val amountMinor: Long, val type: TxType, val card: String?, val description: String, val category: String)

fun parseAutoSms(text: String): AutoParsedSms? {
    val lowerText = text.lowercase()
    
    // Определяем тип операции (учитываем Kapitalbank, Tez и стандартные)
    val isIncome = lowerText.contains("пополнение") || lowerText.contains("зачисление") || lowerText.contains("перевод от") || 
                   lowerText.contains("kirdi") || lowerText.contains("поступление") || 
                   lowerText.contains("popolneniye") || lowerText.contains("kartaga o'tkazma")
                   
    val isExpense = lowerText.contains("списание") || lowerText.contains("оплата") || lowerText.contains("покупка") || 
                    lowerText.contains("перевод") || lowerText.contains("chyqim") || 
                    lowerText.contains("snyatiye") || lowerText.contains("xarid") || 
                    lowerText.contains("pokupka") || lowerText.contains("chiqim")
                    
    if (!isIncome && !isExpense) return null
    val type = if (isIncome) TxType.INCOME else TxType.EXPENSE

    // Ищем сумму. Отсеиваем "Баланс" и "Кешбэк"
    val lines = text.lines()
    var amountMinor: Long? = null
    
    for (line in lines) {
        val lowerLine = line.lowercase()
        if (lowerLine.contains("баланс") || lowerLine.contains("кешбэк") || lowerLine.contains("qoldiq")) continue
        
        val amountRegex = Regex("""(-?[\d\s]+[.,]\d{2})""")
        val match = amountRegex.find(line)
        if (match != null) {
            val amountStr = match.groupValues[1].replace(" ", "").replace(",", ".")
            amountMinor = amountStr.toBigDecimalOrNull()?.abs()?.movePointRight(2)?.toLong()
            if (amountMinor != null && amountMinor > 0) break
        }
    }
    
    // Если не нашли в строках без "Баланс", берем первое совпадение (на всякий случай)
    if (amountMinor == null) {
        val amountRegex = Regex("""(-?[\d\s]+[.,]\d{2})""")
        val match = amountRegex.find(text)
        if (match != null) {
            val amountStr = match.groupValues[1].replace(" ", "").replace(",", ".")
            amountMinor = amountStr.toBigDecimalOrNull()?.abs()?.movePointRight(2)?.toLong()
        }
    }

    if (amountMinor == null || amountMinor == 0L) return null

    // Ищем карту: (*0981), ***5684, *1226
    val cardRegex = Regex("""(?:karta|карта|\*+|х)\s*\(?\*+(\d{4})\)?""", RegexOption.IGNORE_CASE)
    val cardMatch = cardRegex.find(text)
    val card = cardMatch?.groupValues?.get(1)?.take(4)

    // Категория
    val category = when {
        lowerText.contains("магнит") || lowerText.contains("пятерочка") || lowerText.contains("еда") || lowerText.contains("кафе") || lowerText.contains("ресторан") || lowerText.contains("market") || lowerText.contains("atto") -> "🍔 Еда"
        lowerText.contains("такси") || lowerText.contains("яндекс go") || lowerText.contains("бензин") || lowerText.contains("метро") || lowerText.contains("uzumbank") -> "🚕 Транспорт"
        lowerText.contains("аптека") || lowerText.contains("клиника") -> "💊 Здоровье"
        lowerText.contains("аренда") || lowerText.contains("квартплата") -> "🏠 Жилье"
        lowerText.contains("зарплата") || lowerText.contains("аванс") || lowerText.contains("самозанятому") -> "💵 Зарплата"
        lowerText.contains("перевод") || lowerText.contains("o'tkazma") -> "💸 Перевод"
        else -> "📦 Другое"
    }

    val description = text.take(50).replace("\n", " ").trim()
    return AutoParsedSms(amountMinor, type, card, description, category)
}
