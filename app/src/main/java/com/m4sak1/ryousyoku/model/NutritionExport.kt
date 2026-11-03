package com.m4sak1.ryousyoku.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate

data class ArchiveSnapshot(
    val menus: List<StructuredMenu>,
    val unavailableFiles: List<String>,
    val cachedFiles: List<String>
)

@Serializable
data class ExportMeal(
    val kind: String,
    val status: String,
    @SerialName("selection_source") val selectionSource: String,
    @SerialName("selected_option") val selectedOption: String?,
    val dishes: List<String>,
    val nutrition: Nutrition?,
    @SerialName("common_items") val commonItems: List<String>,
    val options: List<MealOption>,
    val notes: List<String>
)

@Serializable
data class ExportDay(
    val date: String,
    @SerialName("is_future") val isFuture: Boolean,
    @SerialName("source_filename") val sourceFilename: String,
    @SerialName("source_sha256") val sourceSha256: String,
    val meals: List<ExportMeal>
)

@Serializable
data class NutritionExport(
    @SerialName("export_version") val exportVersion: Int = 1,
    @SerialName("generated_at") val generatedAt: String,
    @SerialName("cutoff_date") val cutoffDate: String,
    val timezone: String = "Asia/Tokyo",
    @SerialName("data_basis") val dataBasis: String = "menu_planned_amounts_not_verified_intake",
    @SerialName("breakfast_default") val breakfastDefault: String,
    val notes: List<String>,
    @SerialName("unavailable_files") val unavailableFiles: List<String>,
    @SerialName("cached_files") val cachedFiles: List<String>,
    @SerialName("recorded_selections") val recordedSelections: Map<String, String>,
    val days: List<ExportDay>
)

fun makeNutritionExport(snapshot: ArchiveSnapshot, selections: Map<String, String>, breakfastDefault: BreakfastDefault,
                        today: LocalDate, generatedAt: String): NutritionExport {
    // Never silently add overlapping revisions of the same day.
    val days = snapshot.menus.flatMap { menu ->
        menu.days.map { day ->
            ExportDay(day.date, LocalDate.parse(day.date).isAfter(today), menu.source.filename, menu.source.sha256,
                MealKind.entries.map { kind ->
                    val meal = day.meals.getValue(kind.key)
                    val explicit = selections[selectionKey(menu, day.date, kind.key)]
                    val id = effectiveSelection(meal, kind.key, explicit, breakfastDefault)
                    val option = meal.options.find { it.id == id }
                    val origin = when {
                        meal.status != "listed" -> meal.status
                        explicit != null && id != null -> "explicit"
                        id == "standard" -> "fixed_menu"
                        id != null -> "breakfast_default"
                        else -> "unselected"
                    }
                    ExportMeal(kind.key, meal.status, origin, id,
                        if (option == null) emptyList() else option.items + meal.commonItems,
                        if (id == "skip") Nutrition(0.0, 0.0, 0.0, 0.0) else option?.nutrition,
                        meal.commonItems, meal.options, meal.notes)
                })
        }
    }.sortedBy { it.date }
    require(days.map { it.date }.distinct().size == days.size) { "Overlapping menu dates" }
    return NutritionExport(
        generatedAt = generatedAt, cutoffDate = today.toString(), breakfastDefault = breakfastDefault.name,
        notes = listOf(
            "献立に記載された予定量です。実際の摂取量を保証する記録ではありません。",
            "朝食デフォルトと日別選択を区別しています。日別の選択・欠食が優先されます。",
            "nutritionとoptions内のnutritionは共通料理込みの1食全体の栄養値です。共通料理を二重加算しないでください。",
            "未選択・記載なし・栄養値不明は0ではありません。明示的な欠食のみ0として扱います。",
            "外食・間食・残した量は含みません。未来の献立はis_futureで区別しています。",
            "unavailable_filesは取得できなかった週です。総量はその週を含まない部分集計です。"
        ),
        unavailableFiles = snapshot.unavailableFiles, cachedFiles = snapshot.cachedFiles,
        recordedSelections = selections.toSortedMap().toMap(), days = days
    )
}

data class LifetimeTotals(
    val start: String?,
    val end: String?,
    val listedMeals: Int,
    val knownMeals: Int,
    val skippedMeals: Int,
    val defaultedMeals: Int,
    val nutrition: Map<Nutrient, NutrientTotal>,
    val topDish: Pair<String, Int>?
)

fun lifetimeTotals(export: NutritionExport): LifetimeTotals {
    val history = export.days.filterNot { it.isFuture }
    val meals = history.flatMap { it.meals }.filter { it.status == "listed" }
    val totals = Nutrient.entries.associateWith { nutrient ->
        val values = meals.mapNotNull { it.nutrition?.let(nutrient.read) }
        val sum = values.fold(BigDecimal.ZERO) { total, v -> total.add(BigDecimal.valueOf(v)) }
        NutrientTotal(sum.toDouble(), values.size, meals.size)
    }
    val dishes = meals.flatMap { it.dishes }.groupingBy { it }.eachCount()
    return LifetimeTotals(history.firstOrNull()?.date, history.lastOrNull()?.date, meals.size,
        meals.count { it.nutrition?.values()?.any { value -> value != null } == true }, meals.count { it.selectedOption == "skip" },
        meals.count { it.selectionSource == "breakfast_default" }, totals,
        dishes.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.let { it.key to it.value })
}

fun nutritionExportJson(export: NutritionExport): String =
    Json { prettyPrint = true; encodeDefaults = true }.encodeToString(export)

fun nutritionExportText(export: NutritionExport): String = buildString {
    appendLine("寮食の献立をもとに、今後の食事調整を相談したいです。")
    appendLine("体格・活動量・目標・外食や間食の情報が必要なら、最初に確認してください。")
    appendLine("記録のない値を0や実際に食べた量とみなさず、選択済み・デフォルト・未選択・未来を区別して検討してください。")
    appendLine("作成日時: ${export.generatedAt} / 累計の対象日: ${export.cutoffDate}まで（日本時間）")
    export.notes.forEach { appendLine("・$it") }
    if (export.unavailableFiles.isNotEmpty()) appendLine("取得できない週: ${export.unavailableFiles.joinToString()}")
    if (export.cachedFiles.isNotEmpty()) appendLine("保存済みデータを使用した週: ${export.cachedFiles.joinToString()}")
    val totals = lifetimeTotals(export)
    appendLine("\n今日までの献立ベース累計（実測の摂取量ではありません）")
    Nutrient.entries.forEach { nutrient ->
        val value = totals.nutrition.getValue(nutrient)
        appendLine("${nutrient.label}: ${value.value} ${nutrient.unit}（${value.knownMeals}/${value.expectedMeals}食を集計）")
    }
    export.days.forEach { day ->
        appendLine("\n${day.date}${if (day.isFuture) "【未来の献立】" else ""} / 原本: ${day.sourceFilename}")
        day.meals.forEach { meal ->
            val kind = MealKind.entries.first { it.key == meal.kind }.label
            val source = when (meal.selectionSource) {
                "explicit" -> "日別に指定"
                "breakfast_default" -> "朝食のデフォルト"
                "fixed_menu" -> "選択肢なしの予定量"
                "unselected" -> "未選択"
                "not_served" -> "提供なし"
                else -> "記載なし"
            }
            appendLine("$kind: $source / ${meal.selectedOption ?: "未確定"}")
            if (meal.selectedOption == "skip") appendLine("欠食を記録")
            if (meal.dishes.isNotEmpty()) appendLine(meal.dishes.joinToString("・"))
            val n = meal.nutrition
            appendLine("カロリー ${n?.energy ?: "不明"} kcal / たんぱく質 ${n?.protein ?: "不明"} g / 脂質 ${n?.fat ?: "不明"} g / 食塩相当量 ${n?.salt ?: "不明"} g")
            if (meal.options.size > 1) {
                meal.options.forEach { option ->
                    val v = option.nutrition
                    appendLine("  ${option.id}: ${option.items.joinToString("・")}（共通料理込み: ${v.energy ?: "不明"} kcal / たんぱく質 ${v.protein ?: "不明"} g / 脂質 ${v.fat ?: "不明"} g / 食塩 ${v.salt ?: "不明"} g）")
                }
                appendLine("  共通料理: ${meal.commonItems.joinToString("・")}")
            }
            meal.notes.forEach { appendLine("案内: $it") }
        }
    }
}
