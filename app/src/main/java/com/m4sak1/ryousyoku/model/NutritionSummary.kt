package com.m4sak1.ryousyoku.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

enum class NutritionPlan(val label: String) {
    SELECTED("選択内容"), A("Aで試算"), B("Bで試算")
}
enum class BreakfastDefault(val label: String, val optionId: String?) {
    NONE("未設定", null), RICE("ごはん（A）", "A"), BREAD("パン等（B）", "B")
}

/** A day-specific choice (including skip) takes priority over the breakfast preference. */
fun effectiveSelection(meal: StructuredMeal, kind: String, explicit: String?, breakfastDefault: BreakfastDefault): String? {
    if (meal.status != "listed") return null
    if (explicit != null) return explicit.takeIf { it == "skip" || meal.options.any { option -> option.id == it } }
    if (meal.options.size == 1) return meal.options.single().id
    return breakfastDefault.optionId?.takeIf { kind == "breakfast" && meal.options.any { option -> option.id == it } }
}
data class NutrientTotal(val value: Double, val knownMeals: Int, val expectedMeals: Int = 3) {
    val complete get() = knownMeals == expectedMeals
}
data class DayNutrition(val date: LocalDate, val totals: Map<Nutrient, NutrientTotal>)

fun selectionKey(menu: StructuredMenu, date: String, meal: String) =
    "${menu.source.sha256}/$date/$meal"

/** Missing/unselected meals stay missing; only explicitly recorded skips count as zero. */
fun summarizeWeek(menu: StructuredMenu, selections: Map<String, String>, plan: NutritionPlan,
                  breakfastDefault: BreakfastDefault = BreakfastDefault.NONE): List<DayNutrition> {
    val monday = LocalDate.parse(menu.period.start).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val byDate = menu.days.associateBy { it.date }
    return (0L..6L).map { offset ->
        val date = monday.plusDays(offset)
        val day = byDate[date.toString()]
        DayNutrition(date, Nutrient.entries.associateWith { nutrient ->
            var total = BigDecimal.ZERO
            var known = 0
            MealKind.entries.forEach { kind ->
                val meal = day?.meals?.get(kind.key)
                if (meal?.status == "listed") {
                    val saved = selections[selectionKey(menu, date.toString(), kind.key)]
                    val id = when {
                        plan == NutritionPlan.SELECTED -> effectiveSelection(meal, kind.key, saved, breakfastDefault)
                        meal.options.size == 1 -> "standard"
                        plan == NutritionPlan.A -> "A"
                        plan == NutritionPlan.B -> "B"
                        else -> null
                    }
                    if (id == "skip") {
                        known++
                    } else {
                        meal.options.find { it.id == id }?.nutrition?.let(nutrient.read)?.let {
                            total = total.add(BigDecimal.valueOf(it))
                            known++
                        }
                    }
                }
            }
            NutrientTotal(total.toDouble(), known)
        })
    }
}
