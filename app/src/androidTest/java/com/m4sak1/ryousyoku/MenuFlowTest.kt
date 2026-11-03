package com.m4sak1.ryousyoku

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.test.espresso.Espresso
import androidx.lifecycle.ViewModelProvider
import com.m4sak1.ryousyoku.ui.MenuViewModel
import android.net.Uri
import java.io.File
import org.junit.Assert.*
import com.m4sak1.ryousyoku.model.*

/** Run against the local reviewed-JSON server using the debug menuBaseUrl property. */
class MenuFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityScenario<MainActivity>
    private var originalRecords: Map<String, String> = emptyMap()
    private var originalSettings: Map<String, Any?> = emptyMap()

    @Before fun start() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        originalRecords = context.getSharedPreferences("meal_selections", Context.MODE_PRIVATE).all.mapValues { it.value as String }
        originalSettings = listOf("text_menu", "predictive_back", "breakfast_default").associateWith { preferences.all[it] }
        preferences.edit()
            .putBoolean("text_menu", false).putBoolean("predictive_back", true)
            .putString("breakfast_default", "NONE").apply()
        activity = ActivityScenario.launch(MainActivity::class.java)
        waitFor("寮食献立")
    }

    @After fun close() {
        if (::activity.isInitialized) activity.close()
        val editor = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
        originalSettings.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                else -> editor.remove(key)
            }
        }
        editor.commit()
        val recordsEditor = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("meal_selections", Context.MODE_PRIVATE).edit().clear()
        originalRecords.forEach { (key, value) -> recordsEditor.putString(key, value) }
        recordsEditor.commit()
    }

    private fun waitFor(text: String) {
        compose.waitUntil(30_000) {
            runCatching { compose.onNodeWithText(text).assertIsDisplayed(); true }.getOrDefault(false)
        }
        compose.waitForIdle()
    }

    @Test fun switchPersistsAllMealsFitAndNutritionTabsWork() {
        compose.onNodeWithContentDescription("設定").performClick()
        waitFor("文字で献立を表示")
        compose.onAllNodesWithText("← 戻る").assertCountEquals(0)
        compose.onNodeWithText("文字で献立を表示").performClick()
        compose.onAllNodes(isToggleable()).onFirst().assertIsOn()
        compose.onNodeWithText("朝食のデフォルト").performClick()
        compose.onNodeWithText("ごはん（A）").performClick()
        Espresso.pressBack()
        waitFor("朝食")
        compose.onNodeWithText("昼食").assertIsDisplayed()
        compose.onNodeWithText("夕食").assertIsDisplayed()
        compose.onAllNodes(hasText("✓", substring = true)).assertCountEquals(0)
        compose.onNodeWithTag("menu-feed").performScrollToNode(hasText("過去の献立一覧"))
        compose.onAllNodes(hasText("📄 ", substring = true)).onFirst().performClick()
        waitFor("献立の表示方法")
        compose.onNodeWithText("画像で表示").assertIsDisplayed()
        compose.onNodeWithText("文字で表示").performClick()
        waitFor("朝食")

        activity.recreate()
        waitFor("朝食")
        compose.onNodeWithText("夕食").assertIsDisplayed()
        compose.onNodeWithContentDescription("設定").performClick()
        waitFor("朝食のデフォルト")
        compose.onNodeWithText("ごはん（A） ▾").assertIsDisplayed()
        compose.onNodeWithText("朝食のデフォルト").performClick()
        compose.onNodeWithText("未設定").performClick()
        Espresso.pressBack()
        waitFor("朝食")
        compose.onNodeWithContentDescription("栄養レポート").performClick()
        waitFor("集計する献立")
        waitFor("カロリーの推移")
        compose.onNodeWithText("カロリーの推移").assertIsDisplayed()
        compose.onNodeWithText("食塩").performClick()
        compose.onNodeWithText("食塩相当量の推移").assertIsDisplayed()
        compose.onNodeWithText("たんぱく質").performClick()
        compose.onNodeWithText("たんぱく質の推移").assertIsDisplayed()
        compose.onNodeWithText("選択内容").performClick()
        compose.onNodeWithText("選択した献立の予定量です。実際の摂取量とは異なります。").assertIsDisplayed()

        compose.onNodeWithText("累計・書き出し").performScrollTo().performClick()
        waitFor("これまでの献立、総まとめ")
        compose.onNodeWithText("AI相談用テキストを書き出す").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("JSONを書き出す").performScrollTo().assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exported = File.createTempFile("nutrition-export-test-", ".json", context.cacheDir)
        try {
            activity.onActivity { host ->
                val vm = ViewModelProvider(host)[MenuViewModel::class.java]
                assertNotNull(vm.prepareExport(asJson = true))
                vm.saveExport(Uri.fromFile(exported))
            }
            compose.waitUntil(30_000) {
                runCatching { Json.parseToJsonElement(exported.readText()).toString().contains("export_version") }.getOrDefault(false)
            }
        } finally {
            exported.delete()
        }
        Espresso.pressBack()
        waitFor("朝食")
        compose.onNodeWithContentDescription("設定").performClick()
        waitFor("文字で献立を表示")
        compose.onNodeWithText("表示中の献立（JSON）").performScrollTo().performClick()
        waitFor("表示中の献立（JSON）")
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("raw-json-content").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("raw-json-content").assertTextContains("\"parser_version\"", substring = true)
        Espresso.pressBack()
        compose.onNodeWithText("欠食記録（一覧・JSON）").performScrollTo().performClick()
        waitFor("欠食記録（一覧・JSON）")
        compose.onNodeWithText("生JSONを表示").performClick()
        val stored = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("meal_selections", Context.MODE_PRIVATE).all
            .filterValues { it == "skip" }.mapValues { it.value as String }.toSortedMap().toMap()
        compose.onNodeWithTag("skip-records-json").assertTextEquals(Json { prettyPrint = true }.encodeToString(stored))
    }
    @Test fun backupRoundTripPreviewCancelMergeReplaceAndInvalidFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val records = context.getSharedPreferences("meal_selections", Context.MODE_PRIVATE)
        val file = File(context.cacheDir, "personal-backup-test.json")
        lateinit var vm: MenuViewModel
        activity.onActivity { vm = ViewModelProvider(it)[MenuViewModel::class.java] }
        compose.onNodeWithContentDescription("設定").performClick()
        compose.onNodeWithText("バックアップを読み込む").performScrollTo().assertIsDisplayed()
        try {
            file.delete()
            activity.onActivity {
                assertNotNull(vm.prepareBackup())
                vm.saveBackup(Uri.fromFile(file))
            }
            compose.waitUntil(30_000) { vm.backupMessage.value == "バックアップを保存しました。" }
            val saved = decodeBackup(file.readText())
            assertEquals(originalRecords, saved.selections)
            assertEquals(BreakfastDefault.NONE, saved.breakfastDefault)
            val key = "c".repeat(64) + "/2040-01-01/breakfast"
            val sample = saved.copy(breakfastDefault = BreakfastDefault.BREAD, selections = mapOf(key to "skip"))
            file.writeText(encodeBackup(sample))
            activity.onActivity { vm.readBackup(Uri.fromFile(file)) }
            waitFor("バックアップを復元")
            compose.onNodeWithText("キャンセル").performClick()
            assertEquals(originalRecords, records.all)
            activity.onActivity { vm.readBackup(Uri.fromFile(file)) }
            waitFor("バックアップを復元")
            compose.onNodeWithText("この内容で復元").performClick()
            compose.waitUntil(30_000) { vm.backupPreview.value == null && !vm.backupBusy.value }
            assertEquals(originalRecords + (key to "skip"), records.all)
            assertEquals(BreakfastDefault.BREAD, vm.breakfastDefault.value)
            activity.recreate()
            activity.onActivity { vm = ViewModelProvider(it)[MenuViewModel::class.java] }
            assertEquals(originalRecords + (key to "skip"), vm.selections.value)
            assertEquals(BreakfastDefault.BREAD, vm.breakfastDefault.value)
            file.writeText("{broken")
            activity.onActivity { vm.readBackup(Uri.fromFile(file)) }
            compose.waitUntil(30_000) { !vm.backupBusy.value }
            assertNull(vm.backupPreview.value)
            assertTrue(vm.backupMessage.value.orEmpty().contains("読み込めませんでした"))
            assertEquals(originalRecords + (key to "skip"), records.all)
            file.writeText(encodeBackup(saved.copy(selections = emptyMap())))
            activity.onActivity { vm.readBackup(Uri.fromFile(file)) }
            waitFor("バックアップを復元")
            compose.onNodeWithText("置き換え").performClick()
            compose.onNodeWithText("この内容で復元").performClick()
            compose.waitUntil(30_000) { vm.backupPreview.value == null && !vm.backupBusy.value }
            assertTrue(records.all.isEmpty())
            assertEquals(BreakfastDefault.NONE, vm.breakfastDefault.value)
        } finally { file.delete() }
    }

}
