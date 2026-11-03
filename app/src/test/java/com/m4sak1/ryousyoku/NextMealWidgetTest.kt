package com.m4sak1.ryousyoku

import com.m4sak1.ryousyoku.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class NextMealWidgetTest {
    private fun slot(time: String) = nextMealSlot(ZonedDateTime.parse("2026-11-02T${time}+09:00[Asia/Tokyo]"))
    private val menu = Json { ignoreUnknownKeys = true }.decodeFromString<StructuredMenu>(
        requireNotNull(javaClass.getResource("/menus/20261102-20261108.json")).readText())
    private val breakfast = menu.days.first().meals.getValue("breakfast")

    @Test fun mealStaysVisibleUntilItsEndAndThenAdvances() {
        listOf("00:00:00", "07:29:59", "07:30:00", "08:29:59").forEach { assertEquals(MealKind.BREAKFAST, slot(it).kind) }
        listOf("08:30:00", "11:49:59", "11:50:00", "12:49:59").forEach { assertEquals(MealKind.LUNCH, slot(it).kind) }
        listOf("12:50:00", "17:59:59", "18:00:00", "19:59:59").forEach { assertEquals(MealKind.DINNER, slot(it).kind) }
        assertEquals(MealKind.BREAKFAST, slot("20:00:00").kind)
        assertEquals(LocalDate.parse("2026-11-03"), slot("20:00:00").date)
        assertTrue(slot("20:00:00").changesAt.isAfter(ZonedDateTime.parse("2026-11-02T20:00:00+09:00")))
    }

    @Test fun boundariesUseJapaneseCalendarAcrossYearChange() {
        val nextYear = nextMealSlot(ZonedDateTime.parse("2026-12-31T20:00:00+09:00"))
        assertEquals(LocalDate.parse("2027-01-01"), nextYear.date)
        val utc = nextMealSlot(ZonedDateTime.parse("2026-11-01T23:30:00Z"))
        assertEquals(MealKind.LUNCH, utc.kind)
        assertEquals(LocalDate.parse("2026-11-02"), utc.date)
    }

    @Test fun breakfastDefaultsAndExplicitChoicesDoNotDuplicateCommonDishes() {
        val rice = nextMealContent(slot("07:45:00"), breakfast, null, BreakfastDefault.RICE)
        assertEquals("ごはん", rice.primary)
        assertNull(rice.optionB)
        assertTrue(rice.secondary.contains("牛乳"))
        val bread = nextMealContent(slot("07:45:00"), breakfast, "B", BreakfastDefault.RICE)
        assertTrue(bread.primary.contains("パン"))
        assertFalse(bread.primary.contains("ごはん"))
        val both = nextMealContent(slot("07:45:00"), breakfast, null, BreakfastDefault.NONE)
        assertEquals("ごはん", both.optionA)
        assertTrue(both.optionB!!.contains("パン"))
        assertTrue(both.secondary.startsWith("共通："))
        assertFalse(both.compactBody.contains("7:30"))
        assertFalse(both.compactBody.contains("8:30"))
    }

    @Test fun missingAndSkippedMealsAreNotReplacedByOtherMeals() {
        val missing = nextMealContent(slot("12:00:00"), null, null, BreakfastDefault.NONE)
        assertTrue(missing.primary.contains("準備中"))
        val skipped = nextMealContent(slot("07:45:00"), breakfast, "skip", BreakfastDefault.RICE)
        assertTrue(skipped.primary.contains("欠食"))
        assertFalse(skipped.compactBody.contains("パン"))
        val notServed = breakfast.copy(status = "not_served", options = emptyList(), commonItems = emptyList())
        assertTrue(nextMealContent(slot("07:45:00"), notServed, null, BreakfastDefault.NONE).primary.contains("提供はありません"))
    }

    @Test fun dateLinksRespectPdfPeriodsAndRejectInvalidNames() {
        val short = Menu("20260930-20261004.pdf", "")
        assertTrue(menuCoversDate(short, LocalDate.parse("2026-09-30")))
        assertTrue(menuCoversDate(short, LocalDate.parse("2026-10-04")))
        assertFalse(menuCoversDate(short, LocalDate.parse("2026-10-05")))
        assertFalse(menuCoversDate(Menu("20260230-20260301.pdf", ""), LocalDate.parse("2026-03-01")))
        assertFalse(menuCoversDate(Menu("latest.pdf", ""), LocalDate.parse("2026-11-02")))
    }
}
