package com.m4sak1.ryousyoku

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.m4sak1.ryousyoku.ui.MainScreen
import com.m4sak1.ryousyoku.ui.RawDataScreen
import com.m4sak1.ryousyoku.ui.RyousyokuTheme
import com.m4sak1.ryousyoku.ui.SettingsScreen

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

                    // 予測型戻りオフ設定時のバイパス処理
                    PredictiveBackBypass(
                        disabled = !isPredictiveBackEnabled,
                        navController = navController,
                        activity = this@MainActivity
                    )

                    NavHost(
                        navController = navController,
                        startDestination = "main",
                        // 順方向遷移（画面を開く時）: 即時表示（カクカク）
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        // 逆方向遷移（戻るジェスチャー時）:
                        // NavHost 2.8+ の SeekableTransitionState により、
                        // 遷移先の画面(main/settings)が裏に同時描画されながら、
                        // 上の画面がスケールダウンして消え、裏の画面が現れる。
                        // ルート画面(main)で戻る時は OS WindowManager に委譲され、
                        // スマホの壁紙/ホーム画面への縮小アニメーションが自然に動作する。
                        popEnterTransition = {
                            scaleIn(initialScale = 0.9f) + fadeIn()
                        },
                        popExitTransition = {
                            scaleOut(targetScale = 0.9f) + fadeOut()
                        }
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
 * 予測型戻りオフ設定時、PRIORITY_OVERLAY でシステムジェスチャーを割り込み即座に戻る
 */
@SuppressLint("NewApi")
@Composable
private fun PredictiveBackBypass(
    disabled: Boolean,
    navController: androidx.navigation.NavController,
    activity: ComponentActivity
) {
    if (disabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
