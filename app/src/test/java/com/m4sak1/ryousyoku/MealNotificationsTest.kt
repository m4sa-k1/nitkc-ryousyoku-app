package com.m4sak1.ryousyoku

import com.m4sak1.ryousyoku.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class MealNotificationsTest {
    private val menu = Json { ignoreUnknownKeys = true }.decodeFromString<StructuredMenu>(
        requireNotNull(javaClass.getResource("/menus/20261102-20261108.json")).readText())
    private val date = LocalDate.parse("2026-11-02")
    private val breakfast = menu.days.first().meals.getValue("breakfast")

    @Test fun breakfastDefaultIncludesOnlyChosenOptionAndCommonDishes() {
        val rice = mealNotificationContent(date, MealKind.BREAKFAST, breakfast, BreakfastDefault.RICE).body
        assertTrue(rice.contains("ごはん"))
        assertTrue(rice.contains("牛乳（200ml）"))
        assertFalse(rice.contains("ジャム"))
        val bread = mealNotificationContent(date, MealKind.BREAKFAST, breakfast, BreakfastDefault.BREAD).body
        assertTrue(bread.contains("パン"))
        assertFalse(bread.contains("ごはん"))
        assertTrue(bread.contains("牛乳（200ml）"))
    }

    @Test fun unsetBreakfastAndOtherMealsRetainBothChoices() {
        val unset = mealNotificationContent(date, MealKind.BREAKFAST, breakfast, BreakfastDefault.NONE).body
        assertTrue(unset.contains("A：ごはん"))
        assertTrue(unset.contains("B：パン"))
        assertTrue(unset.contains("共通："))
        val dinner = menu.days[2].meals.getValue("dinner")
        val content = mealNotificationContent(date.plusDays(2), MealKind.DINNER, dinner, BreakfastDefault.RICE).body
        assertTrue(content.contains("A："))
        assertTrue(content.contains("B："))
    }

    @Test fun fixedBreakfastIsNotHiddenByBreadDefault() {
        val fixedWeek = Json { ignoreUnknownKeys = true }.decodeFromString<StructuredMenu>(
            requireNotNull(javaClass.getResource("/menus/20261012-20261018.json")).readText())
        val fixed = fixedWeek.days[5].meals.getValue("breakfast")
        val content = mealNotificationContent(date.plusDays(5), MealKind.BREAKFAST, fixed, BreakfastDefault.BREAD)
        assertTrue(content.body.isNotBlank())
        fixed.options.single().items.forEach { assertTrue(content.body.contains(it)) }
    }

    @Test fun missingDataAndSavedDataAreClearlyLabelled() {
        val unavailable = mealNotificationContent(date, MealKind.LUNCH, null, BreakfastDefault.NONE)
        assertTrue(unavailable.body.contains("画像・PDF"))
        val saved = mealNotificationContent(date, MealKind.BREAKFAST, breakfast, BreakfastDefault.RICE, true)
        assertTrue(saved.body.contains("保存済み"))
        val absent = breakfast.copy(status = "not_served", options = emptyList(), commonItems = emptyList())
        assertTrue(mealNotificationContent(date, MealKind.LUNCH, absent, BreakfastDefault.NONE).body.contains("提供はありません"))
    }

    @Test fun nextAlarmUsesJapaneseDateAndMovesPassedOrEqualTimeToTomorrow() {
        val setting = MealNotificationSetting(enabled = true, hour = 7, minute = 15)
        val before = ZonedDateTime.parse("2026-11-02T21:00:00Z")
        assertEquals("2026-11-03T07:15+09:00[Asia/Tokyo]", nextMealNotification(setting, before).toString())
        val equal = ZonedDateTime.parse("2026-11-03T07:15:00+09:00[Asia/Tokyo]")
        assertEquals("2026-11-04T07:15+09:00[Asia/Tokyo]", nextMealNotification(setting, equal).toString())
        assertFalse(defaultNotificationSetting(MealKind.BREAKFAST).enabled)
        assertEquals("12:00", defaultNotificationSetting(MealKind.LUNCH).timeLabel)
        assertTrue(runCatching { MealNotificationSetting(hour = 24) }.isFailure)
    }
}
