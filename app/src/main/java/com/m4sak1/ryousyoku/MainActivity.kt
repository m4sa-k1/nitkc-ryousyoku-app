package com.m4sak1.ryousyoku

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.m4sak1.ryousyoku.ui.MainScreen
import com.m4sak1.ryousyoku.ui.RawDataScreen
import com.m4sak1.ryousyoku.ui.RyousyokuTheme
import com.m4sak1.ryousyoku.ui.SettingsScreen
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // テーマ(Theme.Ryousyoku)で windowIsTranslucent=true, windowBackground=transparent
        // を設定済みなので、ホーム戻り時にランチャーが透けて見える

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
                    val activity = LocalContext.current as? ComponentActivity

                    // 予測型戻りOFF時: PRIORITY_OVERLAY で即座に戻る
                    if (!isPredictiveBackEnabled) {
                        PredictiveBackBypass(
                            navController = navController,
                            activity = this@MainActivity
                        )
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "main",
                        // 全方向のトランジションを None に設定
                        // → PredictiveBackHandler で手動アニメーション
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) {
                        composable("main") {
                            // ── ルート画面: ホームへの戻り ──
                            BackGestureWrapper(
                                enabled = isPredictiveBackEnabled,
                                isHomeBack = true,
                                onBack = { activity?.finish() }
                            ) {
                                MainScreen(
                                    onNavigateToSettings = { navController.navigate("settings") }
                                )
                            }
                        }
                        composable("settings") {
                            // ── アプリ内戻り: 設定→トップ ──
                            BackGestureWrapper(
                                enabled = isPredictiveBackEnabled,
                                isHomeBack = false,
                                onBack = { navController.popBackStack() }
                            ) {
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
                        }
                        composable("raw_data/{type}") { backStackEntry ->
                            val type = backStackEntry.arguments?.getString("type") ?: "menus"
                            // ── アプリ内戻り: データ→設定 ──
                            BackGestureWrapper(
                                enabled = isPredictiveBackEnabled,
                                isHomeBack = false,
                                onBack = { navController.popBackStack() }
                            ) {
                                RawDataScreen(
                                    type = type,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                        composable("licenses") {
                            // ── アプリ内戻り: OSS→設定 ──
                            BackGestureWrapper(
                                enabled = isPredictiveBackEnabled,
                                isHomeBack = false,
                                onBack = { navController.popBackStack() }
                            ) {
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
}

/**
 * 予測型戻りジェスチャーのアニメーションラッパー。
 *
 * 戻るジェスチャー中に:
 * - 現在の画面を縮小 (不透明のまま、フェードなし)
 * - 角丸を適用
 * - スワイプ方向に応じて画面をオフセット（引っ張った側の隙間が大きくなる）
 * - isHomeBack=false の場合、背景を少し暗くして遷移先との区別をつける
 * - isHomeBack=true の場合、背景は透明（Windowが半透明なのでホーム画面が見える）
 */
@Composable
private fun BackGestureWrapper(
    enabled: Boolean,
    isHomeBack: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }
    // 0 = EDGE_LEFT, 1 = EDGE_RIGHT
    var swipeEdge by remember { mutableIntStateOf(0) }

    if (enabled) {
        PredictiveBackHandler { progressFlow ->
            try {
                progressFlow.collect { backEvent ->
                    progress = backEvent.progress
                    swipeEdge = backEvent.swipeEdge
                }
                // ジェスチャー完了 → 戻る
                onBack()
            } catch (e: CancellationException) {
                // キャンセル → 元に戻す
            } finally {
                progress = 0f
            }
        }
    }

    // ── アニメーションパラメータ ──
    // 進行度に応じて縮小: 1.0 → 0.85
    val scale = 1f - (progress * 0.15f)
    // 角丸: 0dp → 24dp
    val cornerRadius = progress * 24f
    // オフセット: スワイプ方向に応じて画面を左右にずらす
    // 左端からスワイプ → 画面が右にずれる（左側の隙間が大きい）
    // 右端からスワイプ → 画面が左にずれる（右側の隙間が大きい）
    val maxOffsetPx = 80f  // 最大オフセット(px)
    val offsetX = if (swipeEdge == 0) {
        // EDGE_LEFT: 画面を右にずらす
        progress * maxOffsetPx
    } else {
        // EDGE_RIGHT: 画面を左にずらす
        -progress * maxOffsetPx
    }

    // 背景を少し暗くする暗幕の透明度 (アプリ内遷移時のみ)
    val scrimAlpha = if (!isHomeBack) progress * 0.15f else 0f

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // ── 暗幕レイヤー（遷移先画面の上に重なる暗い半透明） ──
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
            )
        }

        // ── 現在画面レイヤー（縮小＋角丸＋オフセット） ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    clip = progress > 0f
                    shape = RoundedCornerShape(cornerRadius.dp)
                }
        ) {
            content()
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
