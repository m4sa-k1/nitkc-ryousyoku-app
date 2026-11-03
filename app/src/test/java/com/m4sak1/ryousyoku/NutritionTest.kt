package com.m4sak1.ryousyoku

import com.m4sak1.ryousyoku.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NutritionTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun load(name: String = "20261102-20261108"): StructuredMenu {
        val body = requireNotNull(javaClass.getResource("/menus/$name.json")).readText()
        return json.decodeFromString<StructuredMenu>(body)
    }

    @Test fun allReviewedWeeksDecodeAndValidate() {
        val names = listOf("20260727-20260802", "20260803-20260809", "20260930-20261004",
            "20261005-20261011", "20261012-20261018", "20261019-20261025", "20261026-20261101", "20261102-20261108")
        val menus = names.map { load(it).also { menu -> menu.validate("$it.pdf", menu.source.sha256) } }
        assertEquals(54, menus.sumOf { it.days.size })
        assertEquals(162, menus.sumOf { it.days.sumOf { day -> day.meals.size } })
    }

    @Test fun novemberSecondMatchesPrintedTotals() {
        val menu = load()
        val a = summarizeWeek(menu, emptyMap(), NutritionPlan.A).first().totals
        val b = summarizeWeek(menu, emptyMap(), NutritionPlan.B).first().totals
        assertEquals(2729.0, a.getValue(Nutrient.ENERGY).value, 0.0001)
        assertEquals(8.9, a.getValue(Nutrient.SALT).value, 0.0001)
        assertEquals(2572.0, b.getValue(Nutrient.ENERGY).value, 0.0001)
        assertEquals(10.4, b.getValue(Nutrient.SALT).value, 0.0001)
        assertEquals(79.9, a.getValue(Nutrient.PROTEIN).value, 0.0001)
    }

    @Test fun unselectedChoiceIsMissingInsteadOfZero() {
        val first = summarizeWeek(load(), emptyMap(), NutritionPlan.SELECTED).first().totals.getValue(Nutrient.ENERGY)
        assertEquals(1762.0, first.value, 0.0001)
        assertEquals(2, first.knownMeals)
        assertFalse(first.complete)
    }

    @Test fun breakfastAndDinnerCanBeSelectedIndependently() {
        val menu = load()
        val date = "2026-11-04"
        val choices = mapOf(selectionKey(menu, date, "breakfast") to "A", selectionKey(menu, date, "dinner") to "B")
        val day = summarizeWeek(menu, choices, NutritionPlan.SELECTED)[2]
        assertEquals(2774.0, day.totals.getValue(Nutrient.ENERGY).value, 0.0001)
        assertTrue(day.totals.getValue(Nutrient.ENERGY).complete)
    }

    @Test fun explicitSkipCountsAsZeroButIsNotMissing() {
        val menu = load()
        val choices = mapOf(selectionKey(menu, "2026-11-02", "breakfast") to "skip")
        val day = summarizeWeek(menu, choices, NutritionPlan.SELECTED).first()
        assertEquals(1762.0, day.totals.getValue(Nutrient.ENERGY).value, 0.0001)
        assertTrue(day.totals.getValue(Nutrient.ENERGY).complete)
    }

    @Test fun missingSaltDoesNotRemoveKnownCalories() {
        val menu = load()
        val day = menu.days.first()
        val lunch = day.meals.getValue("lunch")
        val option = lunch.options.single()
        val changed = lunch.copy(options = listOf(option.copy(nutrition = option.nutrition.copy(salt = null))))
        val partial = menu.copy(days = listOf(day.copy(meals = day.meals + ("lunch" to changed))) + menu.days.drop(1))
        val totals = summarizeWeek(partial, emptyMap(), NutritionPlan.A).first().totals
        assertEquals(3, totals.getValue(Nutrient.ENERGY).knownMeals)
        assertEquals(2, totals.getValue(Nutrient.SALT).knownMeals)
    }

    @Test fun partialPdfKeepsMondayToSundayAndMissingMeals() {
        val days = summarizeWeek(load("20260930-20261004"), emptyMap(), NutritionPlan.A)
        assertEquals("2026-09-28", days.first().date.toString())
        assertEquals(0, days.first().totals.getValue(Nutrient.ENERGY).knownMeals)
        assertEquals(1, days[2].totals.getValue(Nutrient.ENERGY).knownMeals)
        assertEquals(13, days.sumOf { it.totals.getValue(Nutrient.ENERGY).knownMeals })
    }

    @Test fun selectionsAreBoundToPdfRevision() {
        val old = load()
        val changed = old.copy(source = old.source.copy(sha256 = "0".repeat(64)))
        val choices = mapOf(selectionKey(old, "2026-11-02", "breakfast") to "A")
        assertEquals(2, summarizeWeek(changed, choices, NutritionPlan.SELECTED).first().totals.getValue(Nutrient.ENERGY).knownMeals)
    }

    @Test fun breakfastDefaultFeedsSelectedNutritionWithoutChangingDinnerChoices() {
        val menu = load()
        val rice = summarizeWeek(menu, emptyMap(), NutritionPlan.SELECTED, BreakfastDefault.RICE)
        val bread = summarizeWeek(menu, emptyMap(), NutritionPlan.SELECTED, BreakfastDefault.BREAD)
        assertEquals(2729.0, rice[0].totals.getValue(Nutrient.ENERGY).value, 0.0001)
        assertEquals(2572.0, bread[0].totals.getValue(Nutrient.ENERGY).value, 0.0001)
        // Wednesday dinner also has A/B and remains unselected.
        assertEquals(2, rice[2].totals.getValue(Nutrient.ENERGY).knownMeals)
    }

    @Test fun explicitBreakfastAndSkipOverrideDefault() {
        val menu = load()
        val key = selectionKey(menu, "2026-11-02", "breakfast")
        assertEquals(2572.0, summarizeWeek(menu, mapOf(key to "B"), NutritionPlan.SELECTED,
            BreakfastDefault.RICE)[0].totals.getValue(Nutrient.ENERGY).value, 0.0001)
        val skipped = summarizeWeek(menu, mapOf(key to "skip"), NutritionPlan.SELECTED, BreakfastDefault.RICE)[0]
        assertEquals(1762.0, skipped.totals.getValue(Nutrient.ENERGY).value, 0.0001)
        assertTrue(skipped.totals.getValue(Nutrient.ENERGY).complete)
    }

    @Test fun unsetDefaultAndSingleBreakfastKeepTheirMeaning() {
        val menu = load()
        assertEquals(2, summarizeWeek(menu, emptyMap(), NutritionPlan.SELECTED,
            BreakfastDefault.NONE)[0].totals.getValue(Nutrient.ENERGY).knownMeals)
        val toast = load("20261012-20261018").days[5].meals.getValue("breakfast")
        assertEquals("standard", effectiveSelection(toast, "breakfast", null, BreakfastDefault.BREAD))
    }

    @Test fun explicitTrialPlanIsIndependentOfBreakfastDefault() {
        val menu = load()
        assertEquals(summarizeWeek(menu, emptyMap(), NutritionPlan.A),
            summarizeWeek(menu, emptyMap(), NutritionPlan.A, BreakfastDefault.BREAD))
    }

    @Test fun skipRecordListContainsOnlyValidSkipsInDateAndMealOrder() {
        val hash = "a".repeat(64)
        val records = skipRecords(mapOf(
            "$hash/2026-11-02/dinner" to "skip",
            "$hash/2026-11-03/lunch" to "skip",
            "$hash/2026-11-03/breakfast" to "skip",
            "$hash/2026-11-04/breakfast" to "A",
            "$hash/2026-11-31/breakfast" to "skip",
            "invalid/2026-11-03/breakfast" to "skip"
        ))
        assertEquals(listOf("2026-11-03", "2026-11-03", "2026-11-02"), records.map { it.date.toString() })
        assertEquals(listOf(MealKind.BREAKFAST, MealKind.LUNCH, MealKind.DINNER), records.map { it.meal })
        assertTrue(skipRecords(emptyMap()).isEmpty())
    }

    @Test fun unsupportedVersionHashAndDuplicateDateAreRejected() {
        val menu = load()
        val invalid = listOf(menu.copy(schemaVersion = 2),
            menu.copy(source = menu.source.copy(sha256 = "0".repeat(64))),
            menu.copy(days = listOf(menu.days[1]) + menu.days.drop(1)))
        invalid.forEach {
            assertThrows(IllegalArgumentException::class.java) { it.validate(menu.source.filename, menu.source.sha256) }
        }
    }
}
