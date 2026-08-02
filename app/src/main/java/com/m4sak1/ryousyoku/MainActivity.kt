package com.m4sak1.ryousyoku

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.m4sak1.ryousyoku.ui.MainScreen
import com.m4sak1.ryousyoku.ui.RyousyokuTheme
import com.m4sak1.ryousyoku.ui.SettingsScreen
import com.m4sak1.ryousyoku.ui.RawDataScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        setContent {
            var isDarkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
            var isPredictiveBackEnabled by remember { mutableStateOf(prefs.getBoolean("predictive_back", true)) }

            val toggleTheme = {
                isDarkMode = !isDarkMode
                prefs.edit().putBoolean("dark_mode", isDarkMode).apply()
            }

            val togglePredictiveBack = {
                isPredictiveBackEnabled = !isPredictiveBackEnabled
                prefs.edit().putBoolean("predictive_back", isPredictiveBackEnabled).apply()
            }

            RyousyokuTheme(isDarkMode = isDarkMode) {
                CompositionLocalProvider(
                    LocalIndication provides NoRippleIndication
                ) {
                    val navController = rememberNavController()

                    // ── 予測型戻る (Predictive Back) トグル実装 ──
                    // ON (デフォルト):
                    //   コールバック未登録 → システムが予測型戻りアニメーション
                    //   (back-to-home等) を自動表示。
                    // OFF:
                    //   PRIORITY_OVERLAY でコールバック登録 → システムの予測型
                    //   アニメーションをバイパスし、即座に戻る。
                    PredictiveBackToggle(
                        enabled = !isPredictiveBackEnabled,
                        navController = navController,
                        activity = this@MainActivity
                    )

                    NavHost(
                        navController = navController,
                        startDestination = "main",
                        // 通常の画面遷移アニメーション無効化（ユーザー要望: カクカクした即時切り替え）
                        enterTransition = { androidx.compose.animation.EnterTransition.None },
                        exitTransition = { androidx.compose.animation.ExitTransition.None },
                        popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                        popExitTransition = { androidx.compose.animation.ExitTransition.None }
                    ) {
                        composable("main") {
                            MainScreen(
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                isDarkMode = isDarkMode,
                                onToggleTheme = toggleTheme,
                                isPredictiveBackEnabled = isPredictiveBackEnabled,
                                onTogglePredictiveBack = togglePredictiveBack,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToRawData = { type -> navController.navigate("raw_data/$type") },
                                onNavigateToLicenses = { navController.navigate("licenses") }
                            )
                        }
                        composable("raw_data/{type}") { backStackEntry ->
                            val type = backStackEntry.arguments?.getString("type") ?: "menus"
                            RawDataScreen(
                                type = type,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("licenses") {
                            com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer(
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 予測型戻りの無効化トグル。
 * enabled = true の場合、PRIORITY_OVERLAY コールバックを登録して
 * システムの予測型アニメーションをバイパスする (API 33+)。
 * enabled = false の場合、コールバックを解除してシステムに任せる。
 */
@SuppressLint("NewApi")
@Composable
private fun PredictiveBackToggle(
    enabled: Boolean,
    navController: androidx.navigation.NavController,
    activity: ComponentActivity
) {
    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        DisposableEffect(Unit) {
            val callback = android.window.OnBackInvokedCallback {
                if (!navController.popBackStack()) {
                    activity.finish()
                }
            }
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback
            )
            onDispose {
                activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
            }
        }
    }
}

object NoRippleIndication : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): androidx.compose.ui.Modifier.Node {
        return object : androidx.compose.ui.Modifier.Node() {}
    }

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -1
}
