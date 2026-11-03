package com.m4sak1.ryousyoku.model

import java.math.BigDecimal
import java.math.MathContext
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth

enum class StatisticsRange(val label: String, val days: Long?) {
    ALL("全期間", null), MONTH("直近30日", 30), WEEK("直近7日", 7)
}

fun statisticsDishKey(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFKC).replace(Regex("\\s+"), "")

data class DishAppearance(val date: LocalDate, val kind: MealKind, val labels: Set<String>)
data class DishStatistic(val name: String, val appearances: List<DishAppearance>, val selectedCount: Int) {
    val count get() = appearances.size
    val dates get() = appearances.map { it.date }.distinct().sorted()
    val longestStreak: Int get() {
        var longest = 0
        var current = 0
        var previous: LocalDate? = null
        dates.forEach { date ->
            current = if (previous?.plusDays(1) == date) current + 1 else 1
            longest = maxOf(longest, current)
            previous = date
        }
        return longest
    }
}
data class MenuPatternStatistic(val dishes: List<String>, val appearances: List<DishAppearance>) {
    val count get() = appearances.size
}
data class StatisticsMeal(val date: LocalDate, val kind: MealKind, val meal: ExportMeal)
data class NutritionSample(val date: LocalDate, val kind: MealKind, val option: MealOption, val dishes: List<String>)
data class NutrientDistribution(val expected: Int, val known: Int, val average: Double?, val minimum: Double?, val maximum: Double?)
data class MonthlyMenuStatistic(val month: YearMonth, val days: Int, val listedMeals: Int, val uniqueDishes: Int, val skipped: Int)
data class WeekdayMenuStatistic(val weekday: Int, val days: Int, val listedMeals: Int, val choices: Int, val skipped: Int)
data class MenuStatistics(
    val start: LocalDate?, val end: LocalDate?, val days: Int, val meals: List<StatisticsMeal>,
    val dishes: List<DishStatistic>, val patterns: List<MenuPatternStatistic>,
    val samples: List<NutritionSample>, val nutrients: Map<Nutrient, NutrientDistribution>,
    val months: List<MonthlyMenuStatistic>, val weekdays: List<WeekdayMenuStatistic>,
    val diversity: List<Pair<LocalDate, Int>>, val unmatchedRecords: Int
) {
    val listed get() = meals.filter { it.meal.status == "listed" }
    val choices get() = listed.count { it.meal.options.size > 1 }
    val skipped get() = listed.count { it.meal.selectedOption == "skip" }
    val explicitA get() = listed.count { it.meal.selectionSource == "explicit" && it.meal.selectedOption == "A" }
    val explicitB get() = listed.count { it.meal.selectionSource == "explicit" && it.meal.selectedOption == "B" }
    val defaults get() = listed.count { it.meal.selectionSource == "breakfast_default" }
    val fixed get() = listed.count { it.meal.selectionSource == "fixed_menu" }
    val unselected get() = listed.count { it.meal.selectionSource == "unselected" }
    val appearances get() = dishes.sumOf { it.count }
    val records get() = listed.filter { it.meal.selectionSource == "explicit" }.sortedWith(compareByDescending<StatisticsMeal> { it.date }.thenBy { it.kind.ordinal })
}

fun nutrientDistribution(values: List<Double?>): NutrientDistribution {
    val known = values.filterNotNull()
    val average = if (known.isEmpty()) null else known.fold(BigDecimal.ZERO) { sum, v -> sum.add(BigDecimal.valueOf(v)) }
        .divide(BigDecimal.valueOf(known.size.toLong()), MathContext.DECIMAL64).toDouble()
    return NutrientDistribution(values.size, known.size, average, known.minOrNull(), known.maxOrNull())
}

fun makeMenuStatistics(snapshot: ArchiveSnapshot, selections: Map<String, String>, breakfastDefault: BreakfastDefault,
                       today: LocalDate, range: StatisticsRange = StatisticsRange.ALL, kind: MealKind? = null): MenuStatistics {
    val export = makeNutritionExport(snapshot, selections, breakfastDefault, today, today.toString())
    val lower = range.days?.let { today.minusDays(it - 1) }
    val days = export.days.filter { !it.isFuture && (lower == null || LocalDate.parse(it.date) >= lower) }
    val meals = days.flatMap { day -> day.meals.map { StatisticsMeal(LocalDate.parse(day.date), MealKind.entries.first { k -> k.key == it.kind }, it) } }
        .filter { kind == null || it.kind == kind }
    val listed = meals.filter { it.meal.status == "listed" }
    val occurrences = linkedMapOf<String, MutableList<DishAppearance>>()
    val selected = mutableMapOf<String, Int>()
    val patterns = linkedMapOf<List<String>, MutableList<DishAppearance>>()
    listed.forEach { entry ->
        val roles = linkedMapOf<String, MutableSet<String>>()
        entry.meal.commonItems.forEach { roles.getOrPut(statisticsDishKey(it)) { linkedSetOf() }.add("共通") }
        entry.meal.options.forEach { option -> option.items.forEach {
            roles.getOrPut(statisticsDishKey(it)) { linkedSetOf() }.add(if (option.id == "standard") "固定" else option.id)
        } }
        roles.forEach { (name, labels) -> occurrences.getOrPut(name) { mutableListOf() }.add(DishAppearance(entry.date, entry.kind, labels)) }
        entry.meal.dishes.map(::statisticsDishKey).distinct().forEach { selected[it] = selected.getOrDefault(it, 0) + 1 }
        val slotPatterns = entry.meal.options.groupBy { option -> (option.items + entry.meal.commonItems).map(::statisticsDishKey).distinct().sorted() }
        slotPatterns.forEach { (names, options) ->
            patterns.getOrPut(names) { mutableListOf() }.add(DishAppearance(entry.date, entry.kind,
                options.map { if (it.id == "standard") "固定" else it.id }.toSet()))
        }
    }
    val dishes = occurrences.map { (name, dates) -> DishStatistic(name, dates, selected.getOrDefault(name, 0)) }
        .sortedWith(compareByDescending<DishStatistic> { it.count }.thenBy { it.name })
    val candidates = listed.flatMap { entry -> entry.meal.options.map { option ->
        NutritionSample(entry.date, entry.kind, option, option.items + entry.meal.commonItems)
    } }
    val validRecordKeys = snapshot.menus.flatMap { menu -> menu.days.flatMap { day ->
        day.meals.filterValues { it.status == "listed" }.map { (meal, _) -> selectionKey(menu, day.date, meal) }
    } }.toSet()
    val months = meals.groupBy { YearMonth.from(it.date) }.toSortedMap().map { (month, entries) ->
        val active = entries.filter { it.meal.status == "listed" }
        val names = active.flatMap { it.meal.commonItems + it.meal.options.flatMap { option -> option.items } }.map(::statisticsDishKey).toSet()
        MonthlyMenuStatistic(month, entries.map { it.date }.distinct().size, active.size, names.size, active.count { it.meal.selectedOption == "skip" })
    }
    val weekdays = (1..7).map { weekday ->
        val entries = meals.filter { it.date.dayOfWeek.value == weekday }
        val active = entries.filter { it.meal.status == "listed" }
        WeekdayMenuStatistic(weekday, entries.map { it.date }.distinct().size, active.size,
            active.count { it.meal.options.size > 1 }, active.count { it.meal.selectedOption == "skip" })
    }
    val diversity = meals.groupBy { it.date }.map { (date, entries) ->
        date to entries.filter { it.meal.status == "listed" }.flatMap { it.meal.commonItems + it.meal.options.flatMap { option -> option.items } }
            .map(::statisticsDishKey).distinct().size
    }.sortedWith(compareByDescending<Pair<LocalDate, Int>> { it.second }.thenBy { it.first })
    return MenuStatistics(days.firstOrNull()?.date?.let(LocalDate::parse), days.lastOrNull()?.date?.let(LocalDate::parse), days.size,
        meals, dishes, patterns.map { MenuPatternStatistic(it.key, it.value) }.sortedWith(compareByDescending<MenuPatternStatistic> { it.count }.thenBy { it.dishes.joinToString() }),
        candidates, Nutrient.entries.associateWith { nutrient -> nutrientDistribution(candidates.map { nutrient.read(it.option.nutrition) }) },
        months, weekdays, diversity, selections.keys.count { it !in validRecordKeys })
}
