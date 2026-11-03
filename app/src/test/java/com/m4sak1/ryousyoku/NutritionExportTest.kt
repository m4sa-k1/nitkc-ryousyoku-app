package com.m4sak1.ryousyoku

import com.m4sak1.ryousyoku.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NutritionExportTest {
    private val menu = Json { ignoreUnknownKeys = true }.decodeFromString<StructuredMenu>(
        requireNotNull(javaClass.getResource("/menus/20261102-20261108.json")).readText())
    private val snapshot = ArchiveSnapshot(listOf(menu), listOf("unavailable.pdf"), listOf(menu.source.filename))
    private fun export(default: BreakfastDefault = BreakfastDefault.RICE, selections: Map<String, String> = emptyMap()) =
        makeNutritionExport(snapshot, selections, default, LocalDate.parse("2026-11-03"), "2026-11-03T12:00:00+09:00")

    @Test fun lifetimeExcludesFutureButExportRetainsUpcomingMenus() {
        val data = export()
        assertEquals(7, data.days.size)
        assertEquals(5, data.days.count { it.isFuture })
        val total = lifetimeTotals(data)
        assertEquals("2026-11-02", total.start)
        assertEquals("2026-11-03", total.end)
        assertEquals(6, total.listedMeals)
        assertEquals(5696.0, total.nutrition.getValue(Nutrient.ENERGY).value, 0.0001)
        assertEquals(25.1, total.nutrition.getValue(Nutrient.SALT).value, 0.0001)
        assertEquals(2, total.defaultedMeals)
    }

    @Test fun skipOverridesDefaultAndRemainsExplicit() {
        val data = export(selections = mapOf(selectionKey(menu, "2026-11-03", "breakfast") to "skip"))
        val meal = data.days[1].meals.first()
        assertEquals("explicit", meal.selectionSource)
        assertEquals("skip", meal.selectedOption)
        assertEquals(emptyList<String>(), meal.dishes)
        val total = lifetimeTotals(data)
        assertEquals(1, total.skippedMeals)
        assertEquals(6, total.nutrition.getValue(Nutrient.ENERGY).knownMeals)
        assertEquals(4761.0, total.nutrition.getValue(Nutrient.ENERGY).value, 0.0001)
    }

    @Test fun unknownIsNotZeroAndAlternativesRemainAvailable() {
        val data = export(BreakfastDefault.NONE)
        val meal = data.days.first().meals.first()
        assertEquals("unselected", meal.selectionSource)
        assertNull(meal.nutrition)
        assertEquals(2, meal.options.size)
        assertTrue(meal.commonItems.isNotEmpty())
        val total = lifetimeTotals(data)
        assertEquals(4, total.nutrition.getValue(Nutrient.ENERGY).knownMeals)
        assertEquals(6, total.nutrition.getValue(Nutrient.ENERGY).expectedMeals)
    }

    @Test fun serializedExportPreservesCoverageAndSourceProvenance() {
        val data = export()
        val parsed = Json.parseToJsonElement(nutritionExportJson(data)).jsonObject
        assertEquals("1", parsed.getValue("export_version").jsonPrimitive.content)
        assertEquals("menu_planned_amounts_not_verified_intake", parsed.getValue("data_basis").jsonPrimitive.content)
        assertEquals(1, parsed.getValue("unavailable_files").jsonArray.size)
        assertEquals(menu.source.sha256, parsed.getValue("days").jsonArray.first().jsonObject.getValue("source_sha256").jsonPrimitive.content)
        val text = nutritionExportText(data)
        assertTrue(text.contains("【未来の献立】"))
        assertTrue(text.contains("未選択"))
        assertTrue(text.contains("取得できない週"))
        assertTrue(text.contains("実際の摂取量"))
    }

    @Test fun overlappingDatesAreRejectedInsteadOfDoubleCounted() {
        assertThrows(IllegalArgumentException::class.java) {
            makeNutritionExport(snapshot.copy(menus = listOf(menu, menu)), emptyMap(), BreakfastDefault.NONE,
                LocalDate.parse("2026-11-03"), "now")
        }
    }
}
