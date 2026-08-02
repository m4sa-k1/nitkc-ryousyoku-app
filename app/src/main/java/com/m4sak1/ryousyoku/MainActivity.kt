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
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) {
                        composable("main") {
                            // ── ルート画面 (トップ) ──
                            // backContent = null → 隙間からOSのホーム画面/壁紙が見える
                            BackGestureWrapper(
                                enabled = isPredictiveBackEnabled,
                                onBack = { activity?.finish() },
                                backContent = null
                            ) {
                                MainScreen(
                                    onNavigateToSettings = { navController.navigate("settings") }
                                )
                            }
                        }
                        composable("settings") {
                            // ── 設定画面 ──
                            // backContent = MainScreen → 隙間からトップ画面が見える
                            BackGestureWrapper(
                                enabled = isPredictiveBackEnabled,
                                onBack = { navController.popBackStack() },
                                backContent = {
                                    MainScreen(onNavigateToSettings = {})
                                }
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
                            // ── データ表示画面 ──
                            // backContent = SettingsScreen → 隙間から設定画面が見える
                            BackGestureWrapper(
                                enabled = isPredictiveBackEnabled,
                                onBack = { navController.popBackStack() },
                                backContent = {
                                    SettingsScreen(
                                        isDarkMode = isDarkMode,
                                        onToggleTheme = toggleTheme,
                                        isPredictiveBackEnabled = isPredictiveBackEnabled,
                                        onTogglePredictiveBack = togglePredictiveBack,
                                        onNavigateBack = {},
                                        onNavigateToRawData = {},
                                        onNavigateToLicenses = {}
                                    )
                                }
                            ) {
                                RawDataScreen(
                                    type = type,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                        composable("licenses") {
                            // ── OSSライセンス画面 ──
                            // backContent = SettingsScreen → 隙間から設定画面が見える
                            BackGestureWrapper(
                                enabled = isPredictiveBackEnabled,
                                onBack = { navController.popBackStack() },
                                backContent = {
                                    SettingsScreen(
                                        isDarkMode = isDarkMode,
                                        onToggleTheme = toggleTheme,
                                        isPredictiveBackEnabled = isPredictiveBackEnabled,
                                        onTogglePredictiveBack = togglePredictiveBack,
                                        onNavigateBack = {},
                                        onNavigateToRawData = {},
                                        onNavigateToLicenses = {}
                                    )
                                }
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
 * 全画面共通：予測型戻りジェスチャーのアニメーションラッパー。
 *
 * 1. 引っ張った方向(swipeEdge)に応じて、画面が左右にずれる（引っ張った側の隙間が大きくなる）
 * 2. 画面が不透明のまま 1.0 → 0.85 に縮小し、角丸(0dp → 24dp)が適用される
 * 3. 背面レイヤー(backContent):
 *    - アプリ内遷移時: 遷移先画面が隙間から見え、引っ張る量に応じて暗幕(0% → 35%)が濃くなる
 *    - ホーム戻り時(backContent = null): Windowが透過テーマのためOSホーム画面/壁紙が見える
 */
@Composable
private fun BackGestureWrapper(
    enabled: Boolean,
    onBack: () -> Unit,
    backContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var swipeEdge by remember { mutableIntStateOf(0) }

    if (enabled) {
        PredictiveBackHandler { progressFlow ->
            try {
                progressFlow.collect { backEvent ->
                    progress = backEvent.progress
                    swipeEdge = backEvent.swipeEdge
                }
                onBack()
            } catch (e: CancellationException) {
                // キャンセル時
            } finally {
                progress = 0f
            }
        }
    }

    // ── アニメーション計算 ──
    val scale = 1f - (progress * 0.15f)
    val cornerRadius = (progress * 24f).dp
    val maxOffsetPx = 80f
    // swipeEdge: 0 = 左端から右へスワイプ (画面は右へ移動 → 左側の隙間が大きい)
    // swipeEdge: 1 = 右端から左へスワイプ (画面は左へ移動 → 右側の隙間が大きい)
    val offsetX = if (swipeEdge == 0) {
        progress * maxOffsetPx
    } else {
        -progress * maxOffsetPx
    }

    val scrimAlpha = progress * 0.35f

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // ── 1. 背面レイヤー（アプリ内遷移先画面＋暗幕） ──
        if (progress > 0f && backContent != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                backContent()

                // 引っ張る量(隙間)に応じて影を濃く(0% → 35%)する暗幕
                if (scrimAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = scrimAlpha))
                    )
                }
            }
        }

        // ── 2. 手前レイヤー（現在画面：縮小＋角丸＋方向偏りオフセット） ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    clip = progress > 0f
                    shape = RoundedCornerShape(cornerRadius)
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
