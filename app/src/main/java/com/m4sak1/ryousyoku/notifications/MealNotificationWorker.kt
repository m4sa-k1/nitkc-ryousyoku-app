package com.m4sak1.ryousyoku.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.m4sak1.ryousyoku.MainActivity
import com.m4sak1.ryousyoku.R
import com.m4sak1.ryousyoku.model.*
import com.m4sak1.ryousyoku.repository.MenuRepository
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

class MealNotificationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val kind = MealKind.entries.find { it.key == inputData.getString("meal") } ?: return Result.failure()
        val date = runCatching { LocalDate.parse(inputData.getString("date")) }.getOrNull() ?: return Result.failure()
        MealNotificationDelivery.deliver(applicationContext, kind, date,
            inputData.getLong("revision", -1), inputData.getBoolean("preview", false))
        return Result.success()
    }
}

object MealNotificationDelivery {
    suspend fun deliver(context: Context, kind: MealKind, date: LocalDate, revision: Long, preview: Boolean = false) {
        val store = MealNotificationPreferences(context)
        fun valid(): Boolean = MealNotificationScheduler.canNotify(context) && (preview ||
            (date == LocalDate.now(MenuTimeZone) && store.get(kind).enabled && store.revision(kind) == revision))
        if (!valid()) return
        if (!preview && store.preferences.getString("${kind.key}_last_sent", null) == date.toString()) return
        val repository = MenuRepository(context.cacheDir)
        var meal: StructuredMeal? = null
        var fromCache = false
        try {
            val menus = try { repository.fetchMenus() } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                context.cacheDir.listFiles().orEmpty().mapNotNull { file ->
                    val match = Regex("structured-(\\d{8}-\\d{8})\\.json").matchEntire(file.name)
                    match?.let { Menu(it.groupValues[1] + ".pdf", "") }
                }
            }
            val matches = menus.filter { menu ->
                val parts = Regex("(\\d{4})(\\d{2})(\\d{2})-(\\d{4})(\\d{2})(\\d{2})\\.pdf").matchEntire(menu.filename)?.groupValues ?: return@filter false
                runCatching {
                    date >= LocalDate.of(parts[1].toInt(), parts[2].toInt(), parts[3].toInt()) &&
                        date <= LocalDate.of(parts[4].toInt(), parts[5].toInt(), parts[6].toInt())
                }.getOrDefault(false)
            }.distinctBy { it.filename }
            if (matches.size == 1) {
                val loaded = repository.fetchStructured(matches.single())
                meal = loaded.menu.days.singleOrNull { it.date == date.toString() }?.meals?.get(kind.key)
                fromCache = loaded.fromCache
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { /* Unavailable content is a reminder to consult the original, never another day's menu. */ }
        if (!valid()) return
        val breakfastDefault = runCatching { BreakfastDefault.valueOf(context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("breakfast_default", "NONE") ?: "NONE") }.getOrDefault(BreakfastDefault.NONE)
        val content = mealNotificationContent(date, kind, meal, breakfastDefault, fromCache)
        val open = PendingIntent.getActivity(context, kind.ordinal, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, MEAL_CHANNEL).setSmallIcon(R.drawable.ic_meal_notification)
            .setContentTitle(content.title).setContentText(content.body.replace('\n', ' '))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body)).setContentIntent(open)
            .setAutoCancel(true).setOnlyAlertOnce(!preview).build()
        try {
            context.getSystemService(NotificationManager::class.java).notify("meal-${kind.key}", kind.ordinal, notification)
            if (!preview) store.preferences.edit().putString("${kind.key}_last_sent", date.toString()).apply()
        } catch (_: SecurityException) { /* Permission can be revoked while downloading. */ }
    }
}
