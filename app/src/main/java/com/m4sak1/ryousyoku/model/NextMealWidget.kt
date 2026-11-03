package com.m4sak1.ryousyoku.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Meal end times are used only for switching, never printed by the widget. */
data class NextMealSlot(val date: LocalDate, val kind: MealKind, val changesAt: ZonedDateTime)

fun nextMealSlot(now: ZonedDateTime): NextMealSlot {
    val jst = now.withZoneSameInstant(MenuTimeZone)
    val date = jst.toLocalDate()
    val time = jst.toLocalTime()
    return when {
        time < LocalTime.of(8, 30) -> NextMealSlot(date, MealKind.BREAKFAST, date.atTime(8, 30).atZone(MenuTimeZone))
        time < LocalTime.of(12, 50) -> NextMealSlot(date, MealKind.LUNCH, date.atTime(12, 50).atZone(MenuTimeZone))
        time < LocalTime.of(20, 0) -> NextMealSlot(date, MealKind.DINNER, date.atTime(20, 0).atZone(MenuTimeZone))
        else -> NextMealSlot(date.plusDays(1), MealKind.BREAKFAST, date.plusDays(1).atTime(8, 30).atZone(MenuTimeZone))
    }
}

fun menuCoversDate(menu: Menu, date: LocalDate): Boolean = runCatching {
    val match = Regex("(\\d{4})(\\d{2})(\\d{2})-(\\d{4})(\\d{2})(\\d{2})\\.pdf").matchEntire(menu.filename) ?: return false
    val values = match.groupValues
    date >= LocalDate.of(values[1].toInt(), values[2].toInt(), values[3].toInt()) &&
        date <= LocalDate.of(values[4].toInt(), values[5].toInt(), values[6].toInt())
}.getOrDefault(false)

data class NextMealContent(val primary: String, val secondary: String, val optionA: String? = null,
                           val optionB: String? = null, val badge: String = "次の献立") {
    val compactBody: String get() = listOfNotNull(if (optionA != null) "A：$optionA" else primary,
        optionB?.let { "B：$it" }, secondary.takeIf { it.isNotBlank() }).joinToString("\n")
}

fun nextMealContent(slot: NextMealSlot, meal: StructuredMeal?, explicit: String?, breakfastDefault: BreakfastDefault): NextMealContent {
    if (meal == null) return NextMealContent("献立データを準備中", "タップして画像・PDFで確認できます")
    if (meal.status == "not_served") return NextMealContent("この食事の提供はありません", "詳しくは原本をご確認ください", badge = "提供なし")
    if (meal.status != "listed") return NextMealContent("献立の記載がありません", "タップして画像・PDFで確認できます")
    val selected = effectiveSelection(meal, slot.kind.key, explicit, breakfastDefault)
    if (selected == "skip") return NextMealContent("欠食を記録済み", "この食事は欠食として記録されています", badge = "自分の記録")
    val option = meal.options.find { it.id == selected } ?: meal.options.singleOrNull()
    if (option == null && meal.options.size == 2) {
        return NextMealContent("", listOfNotNull(meal.commonItems.takeIf { it.isNotEmpty() }?.let { "共通：" + it.joinToString("・") },
            meal.notes.takeIf { it.isNotEmpty() }?.joinToString("・", prefix = "案内：")).joinToString("\n"),
            meal.options[0].items.joinToString("・"), meal.options[1].items.joinToString("・"), "A/Bの献立")
    }
    val items = option?.items.orEmpty()
    val primary = if (option?.id == "standard") items.firstOrNull().orEmpty() else items.joinToString("・")
    val rest = if (option?.id == "standard") items.drop(1) else emptyList()
    val badge = when {
        explicit != null && selected in listOf("A", "B") -> "${selected}を選択中"
        slot.kind == MealKind.BREAKFAST && selected in listOf("A", "B") -> "朝食のデフォルト"
        else -> "次の献立"
    }
    return NextMealContent(primary, listOfNotNull((rest + meal.commonItems).takeIf { it.isNotEmpty() }?.joinToString("・"),
        meal.notes.takeIf { it.isNotEmpty() }?.joinToString("・", prefix = "案内：")).joinToString("\n"), badge = badge)
}
