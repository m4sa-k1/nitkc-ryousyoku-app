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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
        // NOTE: window背景は変更しない。
        // back-to-home アニメーション (https://developer.android.com/about/versions/16/images/back-to-home.mp4)
        // は android:enableOnBackInvokedCallback="true" (AndroidManifest.xml) を設定するだけで
        // Android 14+ (API 34+) においてシステムが自動的に提供する。
        // window.setBackgroundDrawable(transparent) は逆にシステムアニメーションを壊すため不要。

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

                    // 予測型戻りOFF設定時のバイパス (API33+)
                    if (!isPredictiveBackEnabled) {
                        PredictiveBackBypass(
                            navController = navController,
                            activity = this@MainActivity
                        )
                    }

                    // ─── NavHost 2.8+ の SeekableTransitionState による予測型戻り実装 ───
                    //
                    // 通常の forward 遷移 (タップ): None (即時カクカク)
                    //
                    // 戻る遷移 (popEnter/popExit):
                    //   - NavHost 2.8+ はバックスタックの前の画面(popEnter)と
                    //     現在の画面(popExit)を同時に描画する (SeekableTransitionState)
                    //   - 指のスワイプ進行度に合わせてリアルタイムにスクラブ再生される
                    //   - popExitTransition: 現在画面が縮小＋フェードアウト
                    //   - popEnterTransition: 遷移先画面が背後で見えている（縮小状態から等倍へ）
                    //
                    // ホーム戻り (バックスタックが空になる場合):
                    //   - NavHost を通り抜けてシステムへ委譲
                    //   - AndroidManifest の enableOnBackInvokedCallback="true" が機能し、
                    //     Android 14+ でシステムが back-to-home アニメーションを自動表示
                    NavHost(
                        navController = navController,
                        startDestination = "main",
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = {
                            // 遷移先画面（背後）: 少し小さい状態から等倍に戻る
                            scaleIn(
                                initialScale = 0.9f,
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(300))
                        },
                        popExitTransition = {
                            // 現在画面（手前）: 縮小しながらフェードアウト
                            scaleOut(
                                targetScale = 0.8f,
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(300))
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

@SuppressLint("NewApi")
@Composable
private fun PredictiveBackBypass(
    navController: androidx.navigation.NavController,
    activity: ComponentActivity
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        DisposableEffect(Unit) {
            val callback = android.window.OnBackInvokedCallback {
                if (!navController.popBackStack()) activity.finish()
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
