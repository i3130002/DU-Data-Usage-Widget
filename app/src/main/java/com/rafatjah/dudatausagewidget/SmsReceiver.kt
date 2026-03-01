package com.rafatjah.dudatausagewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Telephony
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import java.text.SimpleDateFormat
import java.util.*

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                val sender = message.displayOriginatingAddress ?: ""
                val body = message.displayMessageBody ?: ""

                if (sender.contains("du", ignoreCase = true) || sender.contains("135")) {
                    val dataUsage = extractDataUsage(body)
                    if (dataUsage != null) {
                        val activationTime = readActivationTimeFromSms(context)
                        updateAllWidgets(context, dataUsage, activationTime)
                    } else if (body.contains("bundle has been activated", ignoreCase = true)) {
                        val latestData = readLatestDataFromSms(context)
                        updateAllWidgets(context, latestData ?: "Data Usage", message.timestampMillis)
                    }
                }
            }
        }
    }

    private fun readLatestDataFromSms(context: Context): String? {
        val cursor = context.contentResolver.query(
            "content://sms/inbox".toUri(),
            arrayOf("body"),
            "address LIKE ? OR address LIKE ?",
            arrayOf("%du%", "%135%"),
            "date DESC LIMIT 20"
        )
        cursor?.use {
            val bodyIdx = it.getColumnIndex("body")
            while (it.moveToNext()) {
                val data = extractDataUsage(it.getString(bodyIdx) ?: "")
                if (data != null) return data
            }
        }
        return null
    }

    private fun readActivationTimeFromSms(context: Context): Long? {
        val cursor = context.contentResolver.query(
            "content://sms/inbox".toUri(),
            arrayOf("body", "date"),
            "address LIKE ?",
            arrayOf("%du%"),
            "date DESC LIMIT 50"
        )
        cursor?.use {
            val bodyIdx = it.getColumnIndex("body")
            val dateIdx = it.getColumnIndex("date")
            while (it.moveToNext()) {
                val body = it.getString(bodyIdx) ?: ""
                if (body.contains("bundle has been activated", ignoreCase = true)) return it.getLong(dateIdx)
            }
        }
        return null
    }

    private fun extractDataUsage(body: String): String? {
        val regex = Regex("Remaining DATA:\\s*(.*)", RegexOption.IGNORE_CASE)
        val match = regex.find(body)
        return match?.groupValues?.get(1)?.trim()?.split("\n")?.firstOrNull()
    }

    private fun updateAllWidgets(context: Context, text: String, activationTime: Long?) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        
        // Update Wide Widget
        val wideWidget = ComponentName(context, DataUsageWidgetProvider::class.java)
        val wideIds = appWidgetManager.getAppWidgetIds(wideWidget)
        for (id in wideIds) {
            val views = createWideRemoteViews(context, text, activationTime)
            appWidgetManager.updateAppWidget(id, views)
        }

        // Update Compact Widget
        val compactWidget = ComponentName(context, CompactDataUsageWidgetProvider::class.java)
        val compactIds = appWidgetManager.getAppWidgetIds(compactWidget)
        for (id in compactIds) {
            val views = createCompactRemoteViews(context, text, activationTime)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun createWideRemoteViews(context: Context, text: String, activationTime: Long?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.data_usage_widget)
        views.setTextViewText(R.id.tv_data_amount, text)

        val dataRemaining = parseDataValue(text)
        if (dataRemaining != null) {
            val progress = (dataRemaining / 1000f * 100).toInt()
            views.setProgressBar(R.id.pb_data, 100, progress, false)
            views.setViewVisibility(R.id.pb_data, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.pb_data, View.INVISIBLE)
        }

        if (activationTime != null) {
            val now = System.currentTimeMillis()
            val diffMillis = now - activationTime
            val daysLeft = 30 - (diffMillis / (24 * 60 * 60 * 1000L)).toInt()
            views.setTextViewText(R.id.tv_days_left, "$daysLeft days left")
            views.setViewVisibility(R.id.tv_days_left, View.VISIBLE)

            val calendar = Calendar.getInstance().apply { timeInMillis = activationTime }
            val renewalCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, calendar.get(Calendar.MINUTE))
                set(Calendar.SECOND, calendar.get(Calendar.SECOND))
            }
            if (renewalCalendar.timeInMillis < now) renewalCalendar.add(Calendar.DAY_OF_YEAR, 1)

            val millisUntilRenewal = renewalCalendar.timeInMillis - now
            val hours = millisUntilRenewal / (60 * 60 * 1000)
            val minutes = (millisUntilRenewal % (60 * 60 * 1000)) / (60 * 1000)
            views.setTextViewText(R.id.tv_renews, "Renews in ${hours}h ${minutes}m")
            views.setViewVisibility(R.id.tv_renews, View.VISIBLE)
        }

        val refreshIntent = Intent(context, DataUsageWidgetProvider::class.java).apply { action = DataUsageWidgetProvider.ACTION_REFRESH }
        val refreshPendingIntent = PendingIntent.getBroadcast(context, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

        return views
    }

    private fun createCompactRemoteViews(context: Context, text: String, activationTime: Long?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.compact_data_usage_widget)
        views.setTextViewText(R.id.tv_data_amount, text)

        val dataRemaining = parseDataValue(text)
        if (dataRemaining != null) {
            val progress = (dataRemaining / 1000f * 100).toInt()
            views.setProgressBar(R.id.pb_circular, 1000, progress, false)
            views.setViewVisibility(R.id.pb_circular, View.VISIBLE)
        }

        // Load logo from assets
        try {
            val inputStream = context.assets.open("Logo.png")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            views.setImageViewBitmap(R.id.iv_logo, bitmap)
        } catch (e: Exception) {}

        if (activationTime != null) {
            val now = System.currentTimeMillis()
            val diffMillis = now - activationTime
            val daysLeft = 30 - (diffMillis / (24 * 60 * 60 * 1000L)).toInt()
            views.setTextViewText(R.id.tv_days_left, "$daysLeft days left")
            
            val calendar = Calendar.getInstance().apply { timeInMillis = activationTime }
            val renewalCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, calendar.get(Calendar.MINUTE))
                set(Calendar.SECOND, calendar.get(Calendar.SECOND))
            }
            if (renewalCalendar.timeInMillis < now) renewalCalendar.add(Calendar.DAY_OF_YEAR, 1)

            val millisUntilRenewal = renewalCalendar.timeInMillis - now
            val hours = millisUntilRenewal / (60 * 60 * 1000)
            val minutes = (millisUntilRenewal % (60 * 60 * 1000)) / (60 * 1000)
            views.setTextViewText(R.id.tv_renews, "Renews in ${hours}h ${minutes}m")
        }

        val refreshIntent = Intent(context, CompactDataUsageWidgetProvider::class.java).apply { action = CompactDataUsageWidgetProvider.ACTION_REFRESH_COMPACT }
        val refreshPendingIntent = PendingIntent.getBroadcast(context, 1, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

        return views
    }

    private fun parseDataValue(text: String): Float? {
        try {
            if (text.contains("GB", ignoreCase = true)) {
                val value = text.replace("GB", "", ignoreCase = true).trim().toFloatOrNull()
                return if (value != null) value * 1000 else null
            } else if (text.contains("MB", ignoreCase = true)) {
                return text.replace("MB", "", ignoreCase = true).trim().toFloatOrNull()
            }
        } catch (e: Exception) {}
        return null
    }
}