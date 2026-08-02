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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
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

                    // ── 予測型戻る (Predictive Back) トグル ──
                    // OFF の場合: PRIORITY_OVERLAY コールバックで戻るジェスチャーを
                    // インターセプトし、予測型アニメーションなしで即座に戻る。
                    // ON の場合: コールバック未登録 → NavHost が pop 遷移アニメーションを
                    // ジェスチャーの進行度に連動させてスクラブ表示（予測型戻り）。
                    PredictiveBackToggle(
                        shouldIntercept = !isPredictiveBackEnabled,
                        navController = navController,
                        activity = this@MainActivity
                    )

                    NavHost(
                        navController = navController,
                        startDestination = "main",
                        // 順方向遷移（ボタンタップ等）: アニメーションなし（即時切替）
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        // 逆方向遷移（戻る）: スライド+フェードアニメーション
                        // 予測型戻りジェスチャー時にこのアニメーションが
                        // 指の位置に連動してスクラブ再生される。
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn()
                        },
                        popExitTransition = {
                            slideOutHorizontally(targetOffsetX = { it / 3 }) + fadeOut()
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
 * 予測型戻りの無効化トグル。
 *
 * shouldIntercept = true の場合:
 *   OnBackInvokedDispatcher に PRIORITY_OVERLAY でコールバックを登録。
 *   これにより戻るジェスチャーが NavHost に到達する前にインターセプトされ、
 *   予測型アニメーションなしで即座に popBackStack() / finish() する。
 *
 * shouldIntercept = false の場合:
 *   コールバックを解除（または登録しない）。
 *   NavHost が通常通り予測型戻りを処理し、pop 遷移アニメーションを
 *   ジェスチャーに連動してスクラブ表示する。
 */
@SuppressLint("NewApi")
@Composable
private fun PredictiveBackToggle(
    shouldIntercept: Boolean,
    navController: NavController,
    activity: ComponentActivity
) {
    if (shouldIntercept && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
