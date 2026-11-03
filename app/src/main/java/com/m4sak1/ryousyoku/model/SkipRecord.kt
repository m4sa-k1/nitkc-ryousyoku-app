package com.m4sak1.ryousyoku.model

import java.time.LocalDate

data class SkipRecord(val sourceHash: String, val date: LocalDate, val meal: MealKind)

fun skipRecords(selections: Map<String, String>): List<SkipRecord> =
    selections.filterValues { it == "skip" }.mapNotNull { (key, _) ->
        val parts = key.split("/")
        if (parts.size != 3 || !parts[0].matches(Regex("[a-f0-9]{64}"))) return@mapNotNull null
        val meal = MealKind.entries.find { it.key == parts[2] } ?: return@mapNotNull null
        runCatching { SkipRecord(parts[0], LocalDate.parse(parts[1]), meal) }.getOrNull()
    }.sortedWith(compareByDescending<SkipRecord> { it.date }.thenBy { it.meal.ordinal }.thenBy { it.sourceHash })
