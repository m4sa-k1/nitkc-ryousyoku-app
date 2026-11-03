package com.m4sak1.ryousyoku.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.m4sak1.ryousyoku.model.*
import java.time.ZonedDateTime

const val MEAL_CHANNEL = "meal_menus"

class MealNotificationPreferences(context: Context) {
    val preferences = context.getSharedPreferences("meal_notifications", Context.MODE_PRIVATE)
    fun get(kind: MealKind): MealNotificationSetting {
        val default = defaultNotificationSetting(kind)
        return MealNotificationSetting(preferences.getBoolean("${kind.key}_enabled", false),
            preferences.getInt("${kind.key}_hour", default.hour).coerceIn(0, 23),
            preferences.getInt("${kind.key}_minute", 0).coerceIn(0, 59))
    }
    val icon: MealNotificationIcon get() = MealNotificationIcon.fromKey(preferences.getString("notification_icon", null))
    fun setIcon(value: MealNotificationIcon) { preferences.edit().putString("notification_icon", value.key).apply() }
    fun revision(kind: MealKind) = preferences.getLong("${kind.key}_revision", 0)
    fun set(kind: MealKind, setting: MealNotificationSetting) {
        preferences.edit().putBoolean("${kind.key}_enabled", setting.enabled)
            .putInt("${kind.key}_hour", setting.hour).putInt("${kind.key}_minute", setting.minute)
            .putLong("${kind.key}_revision", revision(kind) + 1).apply()
    }
}

object MealNotificationScheduler {
    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(MEAL_CHANNEL, "献立のお知らせ", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "設定した時刻に朝食・昼食・夕食の献立をお知らせします"
            })
    }
    fun canNotify(context: Context): Boolean {
        createChannel(context)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(MEAL_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun canScheduleExactly(context: Context) = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun rescheduleAll(context: Context) {
        val store = MealNotificationPreferences(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        MealKind.entries.forEach { kind ->
            val intent = Intent(context, MealAlarmReceiver::class.java).setAction("${context.packageName}.MEAL_${kind.key}")
            val existing = PendingIntent.getBroadcast(context, kind.ordinal, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            existing?.let { manager.cancel(it); it.cancel() }
            val setting = store.get(kind)
            if (setting.enabled && canNotify(context)) {
                val next = nextMealNotification(setting, ZonedDateTime.now(MenuTimeZone))
                intent.putExtra("meal", kind.key).putExtra("date", next.toLocalDate().toString())
                    .putExtra("revision", store.revision(kind))
                val pending = PendingIntent.getBroadcast(context, kind.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val time = next.toInstant().toEpochMilli()
                try {
                    if (canScheduleExactly(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
                    else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
                } catch (_: SecurityException) {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
                }
            } else if (!setting.enabled) {
                context.getSystemService(NotificationManager::class.java).cancel("meal-${kind.key}", kind.ordinal)
            }
        }
    }

    fun enqueue(context: Context, kind: MealKind, date: String, revision: Long, preview: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<MealNotificationWorker>()
            .setInputData(workDataOf("meal" to kind.key, "date" to date, "revision" to revision, "preview" to preview))
            .addTag("meal-${kind.key}")
            .apply { if (Build.VERSION.SDK_INT >= 31) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
            .build()
        val key = if (preview) "meal-preview-${System.nanoTime()}" else "meal-$date-${kind.key}-$revision"
        WorkManager.getInstance(context).enqueueUniqueWork(key, ExistingWorkPolicy.KEEP, request)
    }
}

class MealAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MealNotificationScheduler.rescheduleAll(context)
        val kind = MealKind.entries.find { it.key == intent.getStringExtra("meal") } ?: return
        val date = intent.getStringExtra("date") ?: return
        MealNotificationScheduler.enqueue(context, kind, date, intent.getLongExtra("revision", -1))
    }
}

class MealAlarmRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MealNotificationScheduler.rescheduleAll(context)
        com.m4sak1.ryousyoku.widget.NextMealWidgetController.refresh(context)
    }
}
