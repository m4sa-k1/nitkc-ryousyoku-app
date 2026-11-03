package com.m4sak1.ryousyoku

import com.m4sak1.ryousyoku.model.*
import org.junit.Assert.*
import org.junit.Test

class PersonalBackupTest {
    private val key = "a".repeat(64) + "/2026-11-03/breakfast"
    private val second = "b".repeat(64) + "/2026-11-03/lunch"
    private fun backup(records: Map<String, String> = mapOf(key to "skip")) = PersonalBackup(
        createdAt = "2026-11-03T12:00:00+09:00", breakfastDefault = BreakfastDefault.BREAD, selections = records)

    @Test fun roundTripPreservesEveryChoiceAndSourceIdentity() {
        val records = mapOf(key to "skip", second to "A", key.replace("breakfast", "dinner") to "B",
            second.replace("lunch", "dinner") to "standard")
        val original = backup(records)
        assertEquals(original, decodeBackup(encodeBackup(original)))
        assertEquals(backup(emptyMap()), decodeBackup(encodeBackup(backup(emptyMap()))))
    }

    @Test fun mergeKeepsUnrelatedRecordsButImportedConflictWins() {
        assertEquals(mapOf(key to "skip", second to "B"), restoredSelections(mapOf(key to "A", second to "B"), backup(), false))
        assertEquals(mapOf(key to "skip"), restoredSelections(mapOf(key to "A", second to "B"), backup(), true))
        assertEquals(emptyMap<String, String>(), restoredSelections(mapOf(key to "A"), backup(emptyMap()), true))
    }

    @Test fun malformedOrUnsupportedDataIsRejectedBeforeRestore() {
        val invalid = listOf(
            backup().copy(version = 2), backup().copy(format = "nutrition-export"),
            backup().copy(createdAt = "tomorrow"), backup(mapOf("invalid" to "skip")),
            backup(mapOf(key.replace("2026-11-03", "2026-02-30") to "skip")),
            backup(mapOf(key.replace("breakfast", "snack") to "skip")), backup(mapOf(key to "C")))
        invalid.forEach { data -> assertTrue(runCatching { validateBackup(data) }.isFailure) }
        val body = encodeBackup(backup())
        assertTrue(runCatching { decodeBackup(body.replace("\"version\": 1,", "")) }.isFailure)
        assertTrue(runCatching { decodeBackup(body.replace("BREAD", "NEW_OPTION")) }.isFailure)
        assertTrue(runCatching { decodeBackup(body.dropLast(1)) }.isFailure)
        assertTrue(runCatching { decodeBackup(" ".repeat(MAX_BACKUP_BYTES + 1)) }.isFailure)
        assertTrue(runCatching { decodeBackup(body.replace("\"version\": 1", "\"version\": 1, \"unknown\": true")) }.isFailure)
    }
}
