package com.m4sak1.ryousyoku.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.work.*
import com.m4sak1.ryousyoku.MainActivity
import com.m4sak1.ryousyoku.R
import com.m4sak1.ryousyoku.model.*
import com.m4sak1.ryousyoku.repository.LoadedStructuredMenu
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

const val WIDGET_DATE_EXTRA = "com.m4sak1.ryousyoku.WIDGET_DATE"
private const val WIDGET_REFRESH = "com.m4sak1.ryousyoku.WIDGET_REFRESH"
private const val WIDGET_ADVANCE = "com.m4sak1.ryousyoku.WIDGET_ADVANCE"
private const val WIDGET_TAG = "next-meal-widget"

class NextMealWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { NextMealWidgetController.refresh(context) }
    override fun onEnabled(context: Context) { NextMealWidgetController.refresh(context) }
    override fun onDisabled(context: Context) { NextMealWidgetController.stop(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        NextMealWidgetController.render(context)
    }
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WIDGET_REFRESH, WIDGET_ADVANCE -> NextMealWidgetController.refresh(context)
            else -> super.onReceive(context, intent)
        }
    }
}

object NextMealWidgetController {
    fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, NextMealWidget::class.java))

    fun cached(context: Context, date: java.time.LocalDate): LoadedStructuredMenu? {
        val json = Json { ignoreUnknownKeys = true }
        val candidates = context.cacheDir.listFiles().orEmpty().mapNotNull { file ->
            val base = Regex("structured-(\\d{8}-\\d{8})\\.json").matchEntire(file.name)?.groupValues?.get(1) ?: return@mapNotNull null
            val menu = Menu("$base.pdf", "")
            if (!menuCoversDate(menu, date)) return@mapNotNull null
            runCatching {
                val parsed = json.decodeFromString<StructuredMenu>(file.readText())
                LoadedStructuredMenu(parsed.validate(menu.filename, parsed.source.sha256), true)
            }.getOrNull()
        }
        return candidates.singleOrNull()
    }

    fun discardCachedDate(context: Context, date: java.time.LocalDate) {
        context.cacheDir.listFiles().orEmpty().forEach { file ->
            val base = Regex("structured-(\\d{8}-\\d{8})\\.json").matchEntire(file.name)?.groupValues?.get(1)
            if (base != null && menuCoversDate(Menu("$base.pdf", ""), date)) file.delete()
        }
    }

    fun refresh(context: Context) {
        if (ids(context).isEmpty()) return
        render(context)
        schedule(context)
        val work = WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork("$WIDGET_TAG-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NextMealWidgetWorker>(30, TimeUnit.MINUTES).addTag(WIDGET_TAG).build())
        work.enqueueUniqueWork("$WIDGET_TAG-refresh", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NextMealWidgetWorker>().addTag(WIDGET_TAG).build())
    }

    fun render(context: Context, fresh: LoadedStructuredMenu? = null, unavailableForDate: java.time.LocalDate? = null) {
        val widgetIds = ids(context)
        if (widgetIds.isEmpty()) return
        val slot = nextMealSlot(ZonedDateTime.now(MenuTimeZone))
        val loaded = if (unavailableForDate == slot.date) null else (fresh?.takeIf { menuCoversDate(Menu(it.menu.source.filename, ""), slot.date) }
            ?: cached(context, slot.date))
        val meal = loaded?.menu?.days?.singleOrNull { it.date == slot.date.toString() }?.meals?.get(slot.kind.key)
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val breakfast = runCatching { BreakfastDefault.valueOf(prefs.getString("breakfast_default", "NONE") ?: "NONE") }.getOrDefault(BreakfastDefault.NONE)
        val explicit = loaded?.let { context.getSharedPreferences("meal_selections", Context.MODE_PRIVATE)
            .getString(selectionKey(it.menu, slot.date.toString(), slot.kind.key), null) }
        val content = nextMealContent(slot, meal, explicit, breakfast)
        val manager = AppWidgetManager.getInstance(context)
        widgetIds.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
            val compact = height < 180
            val views = RemoteViews(context.packageName, if (compact) R.layout.widget_next_meal_compact else R.layout.widget_next_meal)
            views.setTextViewText(R.id.widget_date, slot.date.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPAN)))
            views.setTextViewText(R.id.widget_meal, slot.kind.label)
            views.setImageViewResource(R.id.widget_icon, when(slot.kind) {
                MealKind.BREAKFAST -> R.drawable.ic_notification_symbol_breakfast_dining
                MealKind.LUNCH -> R.drawable.ic_notification_symbol_set_meal
                MealKind.DINNER -> R.drawable.ic_notification_symbol_dinner_dining
            })
            if (compact) views.setTextViewText(R.id.widget_secondary, content.compactBody) else {
                views.setTextViewText(R.id.widget_badge, content.badge)
                views.setTextViewText(R.id.widget_primary, content.primary)
                views.setViewVisibility(R.id.widget_primary, if (content.optionA == null) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.widget_options, if (content.optionA != null) View.VISIBLE else View.GONE)
                views.setTextViewText(R.id.widget_option_a, "A  ${content.optionA.orEmpty()}")
                views.setTextViewText(R.id.widget_option_b, "B  ${content.optionB.orEmpty()}")
                views.setTextViewText(R.id.widget_secondary, content.secondary)
            }
            val reserved = if (compact) 76 else if (content.optionA != null) 154 else 136
            views.setInt(R.id.widget_secondary, "setMaxLines", ((height - reserved) / if (compact) 16 else 18).coerceIn(1, 12))
            views.setTextViewText(R.id.widget_status, (if (loaded?.fromCache == true) "保存済みデータ · " else "") + "タップして献立を開く")
            val openIntent = Intent(context, MainActivity::class.java).putExtra(WIDGET_DATE_EXTRA, slot.date.toString())
                .setData(android.net.Uri.parse("ryousyoku://widget/${slot.date}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            views.setOnClickPendingIntent(android.R.id.background, PendingIntent.getActivity(context, id, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, id,
                Intent(context, NextMealWidget::class.java).setAction(WIDGET_REFRESH), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id, views)
        }
    }

    fun schedule(context: Context) {
        if (ids(context).isEmpty()) return
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = boundaryIntent(context)
        val time = nextMealSlot(ZonedDateTime.now(MenuTimeZone)).changesAt.toInstant().toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
        } catch (_: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending) }
    }

    fun stop(context: Context) {
        val pending = boundaryIntent(context)
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
        WorkManager.getInstance(context).cancelAllWorkByTag(WIDGET_TAG)
    }
    private fun boundaryIntent(context: Context) = PendingIntent.getBroadcast(context, 807,
        Intent(context, NextMealWidget::class.java).setAction(WIDGET_ADVANCE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
