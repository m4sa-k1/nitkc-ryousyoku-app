package com.m4sak1.ryousyoku.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

val MenuTimeZone: ZoneId = ZoneId.of("Asia/Tokyo")

data class MealNotificationSetting(val enabled: Boolean = false, val hour: Int, val minute: Int = 0) {
    init { require(hour in 0..23 && minute in 0..59) }
    val timeLabel: String get() = hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
}

fun defaultNotificationSetting(kind: MealKind) = MealNotificationSetting(hour = when (kind) {
    MealKind.BREAKFAST -> 7; MealKind.LUNCH -> 12; MealKind.DINNER -> 18
})

fun nextMealNotification(setting: MealNotificationSetting, now: ZonedDateTime): ZonedDateTime {
    val jst = now.withZoneSameInstant(MenuTimeZone)
    val candidate = jst.toLocalDate().atTime(setting.hour, setting.minute).atZone(MenuTimeZone)
    return if (candidate.isAfter(jst)) candidate else candidate.plusDays(1)
}

data class MealNotificationContent(val title: String, val body: String)

fun mealNotificationContent(date: LocalDate, kind: MealKind, meal: StructuredMeal?,
    breakfastDefault: BreakfastDefault, fromCache: Boolean = false): MealNotificationContent {
    val title = "${date.monthValue}月${date.dayOfMonth}日 ${kind.label}の献立"
    val body = when {
        meal == null -> "文字の献立データを取得できませんでした。アプリで画像・PDFをご確認ください。"
        meal.status == "not_served" -> "この食事の提供はありません。"
        meal.status != "listed" -> "献立の記載がありません。アプリで画像・PDFをご確認ください。"
        else -> {
            val defaultId = if (kind == MealKind.BREAKFAST) breakfastDefault.optionId else null
            val options = if (meal.options.size > 1 && defaultId != null) meal.options.filter { it.id == defaultId } else meal.options
            buildList {
                options.forEach { option ->
                    val prefix = if (options.size > 1) "${option.id}：" else ""
                    add(prefix + option.items.joinToString("・"))
                }
                if (meal.commonItems.isNotEmpty()) add((if (options.size > 1) "共通：" else "") + meal.commonItems.joinToString("・"))
                addAll(meal.notes)
            }.joinToString("\n")
        }
    }
    return MealNotificationContent(title, body + if (fromCache) "\n（保存済みの献立）" else "")
}
