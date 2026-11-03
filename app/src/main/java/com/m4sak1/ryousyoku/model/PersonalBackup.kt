package com.m4sak1.ryousyoku.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.OffsetDateTime

@Serializable
data class PersonalBackup(
    @Required val format: String = "nitkc-ryousyoku-personal-backup",
    @Required val version: Int = 1,
    @SerialName("created_at") val createdAt: String,
    @SerialName("breakfast_default") val breakfastDefault: BreakfastDefault,
    val selections: Map<String, String>
)

private val backupJson = Json { prettyPrint = true; encodeDefaults = true }
const val MAX_BACKUP_BYTES = 2 * 1024 * 1024

fun validateBackup(data: PersonalBackup): PersonalBackup {
    require(data.format == "nitkc-ryousyoku-personal-backup" && data.version == 1) { "未対応のバックアップ形式です。" }
    OffsetDateTime.parse(data.createdAt)
    require(data.selections.size <= 10000) { "記録件数が多すぎます。" }
    data.selections.forEach { (key, value) ->
        val parts = key.split("/")
        require(parts.size == 3 && parts[0].matches(Regex("[a-f0-9]{64}"))) { "記録の識別情報が不正です。" }
        require(parts[1].matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        LocalDate.parse(parts[1])
        require(parts[2] in MealKind.entries.map { it.key }) { "食事区分が不正です。" }
        require(value in setOf("A", "B", "standard", "skip")) { "選択内容が不正です。" }
    }
    return data
}

fun encodeBackup(data: PersonalBackup): String = backupJson.encodeToString(validateBackup(data))
fun decodeBackup(body: String): PersonalBackup {
    require(body.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "ファイルが大きすぎます。" }
    return validateBackup(backupJson.decodeFromString<PersonalBackup>(body))
}

/** Imported choices win only when the exact PDF hash, date and meal match. */
fun restoredSelections(current: Map<String, String>, backup: PersonalBackup, replace: Boolean): Map<String, String> {
    validateBackup(backup)
    return if (replace) backup.selections.toMap() else current + backup.selections
}
