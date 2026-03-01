package com.rafatjah.dudatausagewidget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.telephony.SmsManager
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import java.text.SimpleDateFormat
import java.util.*

class CompactDataUsageWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_COMPACT = "com.rafatjah.dudatausagewidget.ACTION_REFRESH_COMPACT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_COMPACT) {
            refreshDataUsage(context)
        }
    }

    private fun refreshDataUsage(context: Context) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        updateWidget(context, "Updating...", null)

        val latestData = readLatestDataFromSms(context)
        val activationTime = readActivationTimeFromSms(context)
        updateWidget(context, latestData ?: "Updating...", activationTime)

        try {
            val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage("1355", null, "balance", null, null)
        } catch (e: Exception) {}
    }

    private fun readLatestDataFromSms(context: Context): String? {
        return try {
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
                    val body = it.getString(bodyIdx) ?: ""
                    val data = extractDataUsage(body)
                    if (data != null) return data
                }
            }
            null
        } catch (e: Exception) { null }
    }

    private fun readActivationTimeFromSms(context: Context): Long? {
        return try {
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
            null
        } catch (e: Exception) { null }
    }

    private fun extractDataUsage(body: String): String? {
        val regex = Regex("Remaining DATA:\\s*(.*)", RegexOption.IGNORE_CASE)
        return regex.find(body)?.groupValues?.get(1)?.trim()?.split("\n")?.firstOrNull()
    }

    private fun updateWidget(context: Context, statusText: String, activationTime: Long?) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, CompactDataUsageWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        for (appWidgetId in allWidgetIds) {
            val views = createRemoteViews(context, statusText, activationTime)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val latestData = readLatestDataFromSms(context)
            val activationTime = readActivationTimeFromSms(context)
            val views = createRemoteViews(context, latestData ?: "Data Usage", activationTime)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun createRemoteViews(context: Context, text: String, activationTime: Long?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.compact_data_usage_widget)
        views.setTextViewText(R.id.tv_data_amount, text)

        val dataRemaining = parseDataValue(text)
        if (dataRemaining != null) {
            val progress = (dataRemaining / 1000f * 1000).toInt()
            views.setProgressBar(R.id.pb_circular, 1000, progress, false)
            views.setViewVisibility(R.id.pb_circular, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.pb_circular, View.INVISIBLE)
        }

        // Load logo from assets
        try {
            val inputStream = context.assets.open("Logo.png")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            views.setImageViewBitmap(R.id.iv_logo, bitmap)
        } catch (e: Exception) {
            // Handle if logo not found
        }

        if (activationTime != null) {
            val now = System.currentTimeMillis()
            
            // Days Left (from activation message)
            val diffMillis = now - activationTime
            val daysPassed = (diffMillis / (24 * 60 * 60 * 1000L)).toInt()
            val daysLeft = 30 - daysPassed
            views.setTextViewText(R.id.tv_days_left, "$daysLeft days left")
            views.setViewVisibility(R.id.tv_days_left, View.VISIBLE)

            // Next daily renewal countdown
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
        } else {
            views.setViewVisibility(R.id.tv_days_left, View.GONE)
            views.setViewVisibility(R.id.tv_renews, View.GONE)
        }

        val refreshIntent = Intent(context, CompactDataUsageWidgetProvider::class.java).apply { action = ACTION_REFRESH_COMPACT }
        val refreshPendingIntent = PendingIntent.getBroadcast(context, 1, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

        return views
    }

    private fun parseDataValue(text: String): Float? {
        return try {
            if (text.contains("GB", ignoreCase = true)) {
                text.replace("GB", "", ignoreCase = true).trim().toFloatOrNull()?.let { it * 1000 }
            } else if (text.contains("MB", ignoreCase = true)) {
                text.replace("MB", "", ignoreCase = true).trim().toFloatOrNull()
            } else null
        } catch (e: Exception) { null }
    }
}