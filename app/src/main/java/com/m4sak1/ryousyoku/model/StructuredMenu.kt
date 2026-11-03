package com.m4sak1.ryousyoku.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class StructuredIndex(
    @SerialName("schema_version") val schemaVersion: Int,
    val menus: List<StructuredEntry>
)
@Serializable
data class StructuredEntry(
    val filename: String,
    @SerialName("source_sha256") val sourceSha256: String,
    val status: String,
    val path: String? = null
)
@Serializable
data class MenuSource(val filename: String, val sha256: String)
@Serializable
data class MenuPeriod(val start: String, val end: String, val timezone: String)
@Serializable
data class StructuredMenu(
    @SerialName("schema_version") val schemaVersion: Int,
    val source: MenuSource,
    val period: MenuPeriod,
    val days: List<MenuDay>,
    val notices: List<String> = emptyList()
) {
    fun validate(expectedFilename: String, expectedHash: String): StructuredMenu {
        require(schemaVersion == 1) { "Unsupported menu version" }
        require(source.filename == expectedFilename && source.sha256 == expectedHash) { "Menu revision mismatch" }
        require(expectedHash.matches(Regex("[a-f0-9]{64}"))) { "Invalid source hash" }
        require(period.timezone == "Asia/Tokyo") { "Unexpected time zone" }
        val start = LocalDate.parse(period.start)
        val end = LocalDate.parse(period.end)
        require(!end.isBefore(start) && end <= start.plusDays(6)) { "Invalid period" }
        require(expectedFilename == "${start.toString().replace("-", "")}-${end.toString().replace("-", "")}.pdf")
        val expected = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.map { it.toString() }.toList()
        require(days.map { it.date } == expected) { "Missing or duplicate dates" }
        days.forEach { day ->
            require(day.meals.keys == MealKind.entries.map { it.key }.toSet()) { "Missing meals" }
            day.meals.values.forEach { meal ->
                require(meal.status in setOf("listed", "not_listed", "not_served"))
                val ids = meal.options.map { it.id }
                if (meal.status == "listed") {
                    require(ids == listOf("standard") || ids == listOf("A", "B")) { "Invalid meal options" }
                    require(meal.options.all { it.items.isNotEmpty() && it.items.all(String::isNotBlank) })
                    require(meal.commonItems.isEmpty() || ids == listOf("A", "B"))
                } else {
                    require(meal.options.isEmpty() && meal.commonItems.isEmpty())
                }
                meal.options.forEach { option ->
                    require(option.nutrition.values().all { it == null || (it.isFinite() && it >= 0) })
                }
            }
        }
        return this
    }
}
@Serializable
data class MenuDay(val date: String, val meals: Map<String, StructuredMeal>)
@Serializable
data class StructuredMeal(
    val status: String,
    @SerialName("common_items") val commonItems: List<String>,
    val options: List<MealOption>,
    val notes: List<String> = emptyList()
)
@Serializable
data class MealOption(val id: String, val items: List<String>, val nutrition: Nutrition)
@Serializable
data class Nutrition(
    @SerialName("energy_kcal") val energy: Double?,
    @SerialName("protein_g") val protein: Double?,
    @SerialName("fat_g") val fat: Double?,
    @SerialName("salt_g") val salt: Double?
) {
    fun values() = listOf(energy, protein, fat, salt)
}
enum class MealKind(val key: String, val label: String) {
    BREAKFAST("breakfast", "朝食"), LUNCH("lunch", "昼食"), DINNER("dinner", "夕食")
}
enum class Nutrient(val label: String, val unit: String, val read: (Nutrition) -> Double?) {
    ENERGY("カロリー", "kcal", { it.energy }),
    SALT("食塩相当量", "g", { it.salt }),
    PROTEIN("たんぱく質", "g", { it.protein }),
    FAT("脂質", "g", { it.fat })
}
