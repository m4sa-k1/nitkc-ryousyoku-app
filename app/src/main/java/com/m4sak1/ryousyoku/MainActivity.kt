package com.m4sak1.ryousyoku

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
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
                        // ── 順方向（タップ）: 即時切替 ──
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        // ── 逆方向（戻るジェスチャー）: ──
                        // NavHost 2.8+ SeekableTransitionState により
                        // 上の画面(popExit)と遷移先画面(popEnter)が同時描画される
                        // → 縮小した隙間から実際の遷移先画面が見える
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = {
                            scaleOut(
                                targetScale = 0.85f,
                                transformOrigin = TransformOrigin(0.5f, 0.5f),
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            )
                            // fadeOut なし！ 不透明のまま縮小
                        }
                    ) {
                        composable("main") {
                            // ── ルート画面: ホーム戻りのみ PredictiveBackHandler ──
                            // NavHost のバックスタックが空なので、戻るジェスチャーは
                            // NavHost を通り抜ける。PredictiveBackHandler でキャッチ。
                            //
                            // 同時に、設定→トップ等の back ではこの画面が「遷移先」として
                            // NavHost に同時描画される。ScreenWrapper の暗幕が適用される。
                            ScreenWrapper {
                                HomeBackWrapper(
                                    enabled = isPredictiveBackEnabled,
                                    onBack = { activity?.finish() }
                                ) {
                                    MainScreen(
                                        onNavigateToSettings = { navController.navigate("settings") }
                                    )
                                }
                            }
                        }
                        composable("settings") {
                            // NavHost が popExitTransition(scaleOut) を適用
                            // + ScreenWrapper が角丸と暗幕を付加
                            ScreenWrapper {
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
                            ScreenWrapper {
                                RawDataScreen(
                                    type = type,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                        composable("licenses") {
                            ScreenWrapper {
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
 * NavHost のトランジション進行度に連動して角丸＋暗幕を適用するラッパー。
 *
 * - この画面が「出ていく側」(popExit) のとき:
 *   exitProgress が 0→1 に進み、角丸が 0dp→24dp に変化
 *   (scaleOut は NavHost が適用するため、ここでは角丸のみ担当)
 *
 * - この画面が「遷移先」(popEnter) のとき:
 *   enterScrim が 1→0 に進み、暗幕が 15%→0% に変化
 *   (遷移先画面を少し暗くして、手前の画面と区別する)
 */
@Composable
private fun AnimatedContentScope.ScreenWrapper(
    content: @Composable () -> Unit
) {
    // ── 出ていく側の進行度（角丸用） ──
    // Visible → PostExit: 0f → 1f
    val exitProgress by transition.animateFloat(
        transitionSpec = { tween(300, easing = FastOutSlowInEasing) },
        label = "exitProgress"
    ) { state ->
        when (state) {
            androidx.compose.animation.EnterExitState.Visible -> 0f
            androidx.compose.animation.EnterExitState.PostExit -> 1f
            androidx.compose.animation.EnterExitState.PreEnter -> 0f
        }
    }

    // ── 遷移先側の暗幕進行度 ──
    // PreEnter → Visible: 1f → 0f
    val enterScrim by transition.animateFloat(
        transitionSpec = { tween(300, easing = FastOutSlowInEasing) },
        label = "enterScrim"
    ) { state ->
        when (state) {
            androidx.compose.animation.EnterExitState.PreEnter -> 1f
            androidx.compose.animation.EnterExitState.Visible -> 0f
            androidx.compose.animation.EnterExitState.PostExit -> 0f
        }
    }

    // 角丸: exitProgress に応じて 0dp → 24dp
    val cornerRadius = exitProgress * 24f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (exitProgress > 0f) {
                    clip = true
                    shape = RoundedCornerShape(cornerRadius.dp)
                }
            }
    ) {
        content()

        // ── 暗幕（遷移先として表示中のとき） ──
        // 戻るジェスチャー中、この画面が背後に見えている遷移先のとき
        // 少し暗くして手前の画面と区別する
        if (enterScrim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = enterScrim * 0.15f))
            )
        }
    }
}

/**
 * ルート画面（main）専用：ホームへの予測型戻りアニメーション。
 * PredictiveBackHandler で指の進行度と方向を取得し、
 * graphicsLayer で画面を縮小＋角丸＋方向オフセット。
 * テーマ(Theme.Ryousyoku)で windowIsTranslucent=true のため、
 * 縮小した隙間からホーム画面/壁紙が見える。
 */
@Composable
private fun HomeBackWrapper(
    enabled: Boolean,
    onBack: () -> Unit,
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
                // キャンセル
            } finally {
                progress = 0f
            }
        }
    }

    val scale = 1f - (progress * 0.15f)
    val cornerRadius = (progress * 24f).dp
    val maxOffsetPx = 80f
    val offsetX = if (swipeEdge == 0) {
        progress * maxOffsetPx   // EDGE_LEFT → 右にずれる
    } else {
        -progress * maxOffsetPx  // EDGE_RIGHT → 左にずれる
    }

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
