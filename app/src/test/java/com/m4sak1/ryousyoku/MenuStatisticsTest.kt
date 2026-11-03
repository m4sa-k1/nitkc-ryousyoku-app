package com.m4sak1.ryousyoku

import com.m4sak1.ryousyoku.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MenuStatisticsTest {
    private val today = LocalDate.parse("2026-11-03")
    private fun n(energy: Double) = Nutrition(energy, 2.0, 3.0, 1.0)
    private fun fixed(vararg dishes: String, energy: Double = 500.0) = StructuredMeal("listed", emptyList(), listOf(MealOption("standard", dishes.toList(), n(energy))))
    private val absent = StructuredMeal("not_listed", emptyList(), emptyList())
    private fun day(date: String, breakfast: StructuredMeal, lunch: StructuredMeal, dinner: StructuredMeal) =
        MenuDay(date, mapOf("breakfast" to breakfast, "lunch" to lunch, "dinner" to dinner))
    private val breakfast = StructuredMeal("listed", listOf("牛乳（200ml）", "同じ料理"), listOf(
        MealOption("A", listOf("ごはん", "同じ料理"), n(1000.0)), MealOption("B", listOf("パン", "同じ料理"), n(800.0))))
    private val choiceDinner = StructuredMeal("listed", listOf("汁"), listOf(MealOption("A", listOf("麺"), n(700.0)), MealOption("B", listOf("肉"), n(900.0))))
    private val menu = StructuredMenu(1, MenuSource("20261101-20261104.pdf", "a".repeat(64)), MenuPeriod("2026-11-01", "2026-11-04", "Asia/Tokyo"), listOf(
        day("2026-11-01", breakfast, fixed("ごはん", "魚"), absent),
        day("2026-11-02", fixed("ごはん", "牛乳 (200ml)", energy = 900.0), fixed("魚", "魚", energy = 600.0), choiceDinner),
        day("2026-11-03", fixed("ごはん", "牛乳（200ml）", energy = 950.0), absent.copy(status = "not_served"), fixed("魚", energy = 700.0)),
        day("2026-11-04", fixed("未来料理"), fixed("未来料理"), fixed("未来料理"))))
    private val snapshot = ArchiveSnapshot(listOf(menu), emptyList(), emptyList())

    @Test fun countsEachDishOncePerMealAcrossOptionsAndCommonItems() {
        val stats = makeMenuStatistics(snapshot, emptyMap(), BreakfastDefault.NONE, today)
        assertEquals(3, stats.days)
        assertEquals(7, stats.listed.size)
        assertEquals(8, stats.dishes.size)
        assertEquals(15, stats.appearances)
        assertEquals(4, stats.dishes.single { it.name == "ごはん" }.count)
        assertEquals(1, stats.dishes.single { it.name == "同じ料理" }.count)
        assertEquals(3, stats.dishes.single { it.name == "魚" }.count)
        assertEquals(3, stats.dishes.single { it.name == "牛乳(200ml)" }.count)
        assertFalse(stats.dishes.any { it.name == "未来料理" })
        assertEquals(3, stats.dishes.single { it.name == "ごはん" }.longestStreak)
        assertEquals(6, stats.diversity.first().second)
        assertEquals(LocalDate.parse("2026-11-02"), stats.diversity.first().first)
    }

    @Test fun identicalSetsIgnoreOrderingAndHalfwidthDifferencesWithoutConflatingNames() {
        val stats = makeMenuStatistics(snapshot, emptyMap(), BreakfastDefault.NONE, today)
        assertEquals(2, stats.patterns.single { it.dishes == listOf("ごはん", "牛乳(200ml)").sorted() }.count)
        assertEquals(statisticsDishKey("牛乳（200ml）"), statisticsDishKey("牛乳 (200ml)"))
        assertNotEquals(statisticsDishKey("ごはん"), statisticsDishKey("麦ごはん"))
        assertNotEquals(statisticsDishKey("牛乳200ml"), statisticsDishKey("牛乳100ml"))
    }

    @Test fun breakfastFilterAndCalendarRangeBoundariesAreInclusive() {
        val filtered = makeMenuStatistics(snapshot, emptyMap(), BreakfastDefault.NONE, today, kind = MealKind.BREAKFAST)
        assertEquals(3, filtered.listed.size)
        assertEquals(3, filtered.dishes.single { it.name == "ごはん" }.count)
        val old = menu.copy(source = MenuSource("20261027-20261028.pdf", "b".repeat(64)),
            period = MenuPeriod("2026-10-27", "2026-10-28", "Asia/Tokyo"), days = listOf(
                day("2026-10-27", fixed("境界外"), absent, absent), day("2026-10-28", fixed("境界内"), absent, absent)))
        val stats = makeMenuStatistics(snapshot.copy(menus = listOf(old, menu)), emptyMap(), BreakfastDefault.NONE, today, StatisticsRange.WEEK)
        assertEquals(4, stats.days)
        assertTrue(stats.dishes.any { it.name == "境界内" })
        assertFalse(stats.dishes.any { it.name == "境界外" })
    }

    @Test fun catalogueCountsDoNotChangeWhenChoicesOrSkipsChangeButPersonalCountsDo() {
        val choices = mapOf(selectionKey(menu, "2026-11-01", "breakfast") to "B",
            selectionKey(menu, "2026-11-02", "lunch") to "skip", ("f".repeat(64) + "/2026-11-01/breakfast") to "A")
        val stats = makeMenuStatistics(snapshot, choices, BreakfastDefault.RICE, today)
        assertEquals(1, stats.explicitB)
        assertEquals(1, stats.skipped)
        assertEquals(1, stats.unselected)
        assertEquals(4, stats.fixed)
        assertEquals(1, stats.unmatchedRecords)
        assertEquals(3, stats.dishes.single { it.name == "ごはん" }.selectedCount)
        assertEquals(2, stats.dishes.single { it.name == "魚" }.selectedCount)
        assertEquals(15, stats.appearances)
        assertEquals(3, stats.months.single().days)
        assertEquals(7, stats.weekdays.sumOf { it.listedMeals })
        assertEquals(2, stats.weekdays.sumOf { it.choices })
        assertEquals(1, stats.weekdays.sumOf { it.skipped })
    }

    @Test fun nutritionUsesWholeMealOptionsOnceAndDoesNotConvertUnknownToZero() {
        val stats = makeMenuStatistics(snapshot, emptyMap(), BreakfastDefault.NONE, today)
        val energy = stats.nutrients.getValue(Nutrient.ENERGY)
        assertEquals(9, energy.known)
        assertEquals(7050.0 / 9, energy.average!!, .000001)
        assertEquals(500.0, energy.minimum!!, 0.0)
        assertEquals(1000.0, energy.maximum!!, 0.0)
        val missing = nutrientDistribution(listOf(null, 0.0, 2.0))
        assertEquals(3, missing.expected)
        assertEquals(2, missing.known)
        assertEquals(1.0, missing.average!!, 0.0)
        assertNull(nutrientDistribution(listOf(null)).average)
    }

    @Test fun allEightWeeksRemainCoveredAndOverlappingRevisionsAreRejected() {
        val names = listOf("20260727-20260802", "20260803-20260809", "20260930-20261004", "20261005-20261011", "20261012-20261018", "20261019-20261025", "20261026-20261101", "20261102-20261108")
        val json = Json { ignoreUnknownKeys = true }
        val menus = names.map { json.decodeFromString<StructuredMenu>(requireNotNull(javaClass.getResource("/menus/$it.json")).readText()) }
        val all = ArchiveSnapshot(menus, emptyList(), emptyList())
        val stats = makeMenuStatistics(all, emptyMap(), BreakfastDefault.NONE, LocalDate.parse("2026-11-08"))
        assertEquals(54, stats.days)
        assertEquals(160, stats.listed.size)
        assertEquals(53, makeMenuStatistics(all, emptyMap(), BreakfastDefault.NONE, LocalDate.parse("2026-11-08"), kind = MealKind.BREAKFAST).listed.size)
        assertTrue(stats.dishes.all { it.count <= stats.listed.size })
        assertTrue(runCatching { makeMenuStatistics(snapshot.copy(menus = listOf(menu, menu)), emptyMap(), BreakfastDefault.NONE, today) }.isFailure)
    }
}
