package com.m4sak1.ryousyoku

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import org.junit.*

class StatisticsFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityScenario<MainActivity>
    @Before fun start() { activity = ActivityScenario.launch(MainActivity::class.java) }
    @After fun close() { if (::activity.isInitialized) activity.close() }
    private fun waitFor(text: String) {
        compose.waitUntil(45_000) { runCatching { compose.onNodeWithText(text).assertIsDisplayed(); true }.getOrDefault(false) }
    }
    private fun scrollTo(text: String) { compose.onNodeWithTag("statistics-list").performScrollToNode(hasText(text)) }

    @Test fun rankingsSearchDetailsAllTabsAndFiltersWork() {
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("献立の統計").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("献立の統計").performClick()
        waitFor("料理の登場ランキング")
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-search"))
        compose.onNodeWithTag("statistics-search").performTextReplacement("ごはん")
        val riceRow = hasText("ごはん") and !hasSetTextAction()
        compose.onNodeWithTag("statistics-list").performScrollToNode(riceRow)
        compose.onNode(riceRow).performClick()
        waitFor("登場した日と食事")
        compose.onNodeWithTag("statistics-detail").assertExists()
        compose.onNodeWithContentDescription("統計詳細を閉じる").performClick()
        compose.onNodeWithText("献立").performClick()
        scrollTo("献立の構成")
        compose.onNodeWithText("献立の構成").assertIsDisplayed()
        scrollTo("曜日別の献立")
        compose.onNodeWithText("曜日別の献立").assertIsDisplayed()
        compose.onNodeWithText("栄養").performClick()
        scrollTo("選択肢別の栄養分布")
        compose.onNodeWithText("選択肢別の栄養分布").assertIsDisplayed()
        compose.onNodeWithText("記録").performClick()
        scrollTo("選択・欠食の記録")
        compose.onNodeWithText("選択・欠食の記録").assertIsDisplayed()
        scrollTo("直近7日")
        compose.onNodeWithText("直近7日").performClick()
        compose.onNodeWithText("朝食").performClick()
        activity.recreate()
        compose.onNodeWithText("直近7日").assertIsSelected()
        compose.onNodeWithText("朝食").assertIsSelected()
        Espresso.pressBack()
        compose.onNodeWithContentDescription("設定").performClick()
        compose.onNodeWithText("献立の統計").performScrollTo().performClick()
        waitFor("料理の登場ランキング")
    }
}
