package com.m4sak1.ryousyoku

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.m4sak1.ryousyoku.model.*
import com.m4sak1.ryousyoku.notifications.*
import com.m4sak1.ryousyoku.ui.MenuViewModel
import org.junit.*
import org.junit.Assert.*

class MealNotificationFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityScenario<MainActivity>
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var originals: Map<String, Any?> = emptyMap()
    private var breakfast: String? = null

    @Before fun start() {
        val store = MealNotificationPreferences(context)
        originals = store.preferences.all.toMap()
        breakfast = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("breakfast_default", null)
        store.preferences.edit().clear().commit()
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("設定").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("設定").performClick()
    }

    @After fun close() {
        if (::activity.isInitialized) activity.close()
        val editor = MealNotificationPreferences(context).preferences.edit().clear()
        originals.forEach { (key, value) -> when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is String -> editor.putString(key, value)
        } }
        editor.commit()
        val app = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
        if (breakfast == null) app.remove("breakfast_default") else app.putString("breakfast_default", breakfast)
        app.commit()
        MealKind.entries.forEach { kind ->
            WorkManager.getInstance(context).cancelAllWorkByTag("meal-${kind.key}")
            context.getSystemService(NotificationManager::class.java).cancel("meal-${kind.key}", kind.ordinal)
        }
        MealNotificationScheduler.rescheduleAll(context)
    }

    @Test fun perMealSwitchTimePersistenceAndBreakfastNotification() {
        compose.onNodeWithContentDescription("朝食の献立通知").performScrollTo().assertIsOff().performClick().assertIsOn()
        val store = MealNotificationPreferences(context)
        assertTrue(store.get(MealKind.BREAKFAST).enabled)
        assertFalse(store.get(MealKind.LUNCH).enabled)
        assertFalse(store.get(MealKind.DINNER).enabled)
        fun pending() = PendingIntent.getBroadcast(context, MealKind.BREAKFAST.ordinal,
            Intent(context, MealAlarmReceiver::class.java).setAction("${context.packageName}.MEAL_breakfast"),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        assertNotNull(pending())
        compose.onNodeWithContentDescription("朝食の通知時刻").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("08")
        compose.onAllNodes(hasSetTextAction()).onLast().performTextReplacement("15")
        compose.onNodeWithText("時刻を保存").performClick()
        assertEquals("08:15", store.get(MealKind.BREAKFAST).timeLabel)
        activity.recreate()
        compose.onNodeWithContentDescription("朝食の献立通知").performScrollTo().assertIsOn()
        compose.onNodeWithContentDescription("朝食の献立通知").performClick().assertIsOff()
        assertNull(pending())
        activity.onActivity { ViewModelProvider(it)[MenuViewModel::class.java].setBreakfastDefault(BreakfastDefault.RICE) }
        compose.onNodeWithText("通知を試す").performScrollTo().performClick()
        compose.onNodeWithText("朝食をテスト通知").performClick()
        val manager = context.getSystemService(NotificationManager::class.java)
        compose.waitUntil(45_000) { manager.activeNotifications.any { it.tag == "meal-breakfast" } }
        val posted = manager.activeNotifications.single { it.tag == "meal-breakfast" }.notification
        val body = posted.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertTrue(body.contains("ごはん"))
        assertFalse(body.contains("B："))
        assertFalse(body.contains("ジャム"))
    }
}
