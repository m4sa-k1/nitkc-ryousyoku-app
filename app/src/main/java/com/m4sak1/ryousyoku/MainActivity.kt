package com.m4sak1.ryousyoku

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

        // Window背景を透明にする
        // → ルート画面(main)で戻るジェスチャー時に画面が縮小した隙間から
        //   OSのホーム画面/壁紙が透けて見える
        // → アプリ内遷移時はNavHostが遷移先画面を背後に描画するため、
        //   Window背景色は見えない（遷移先画面で覆われる）
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

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
                        // 順方向（タップで画面を開く）: 即時切替
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        // 逆方向（戻るジェスチャー）:
                        //   popExit: 現在画面が縮小するだけ（フェードなし！不透明のまま！）
                        //            → 隙間から背後の遷移先画面が見える
                        //   popEnter: 遷移先画面はそのまま表示（アニメーションなし）
                        //            → NavHostが背後に同時レンダリングしてくれる
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = {
                            scaleOut(
                                targetScale = 0.85f,
                                transformOrigin = TransformOrigin(0.5f, 0.5f),
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            )
                            // fadeOut なし！ 画面は不透明のまま縮小するだけ
                        }
                    ) {
                        composable("main") {
                            // ── ルート画面のみ PredictiveBackHandler で手動アニメーション ──
                            // NavHostのバックスタックが空（ルート画面）なので、
                            // 戻るジェスチャーはNavHostを通り抜ける。
                            // PredictiveBackHandler で手動キャッチし、
                            // graphicsLayer で画面縮小＋角丸を適用。
                            // Window背景が透明なので、隙間からホーム画面が見える。
                            HomeBackWrapper(
                                enabled = isPredictiveBackEnabled,
                                onBack = { activity?.finish() }
                            ) {
                                MainScreen(
                                    onNavigateToSettings = { navController.navigate("settings") }
                                )
                            }
                        }
                        composable("settings") {
                            // NavHostが popExitTransition(scaleOut) を自動適用
                            // 背後にmain画面が同時描画される
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
 * ルート画面（main）専用：ホームへの予測型戻りアニメーション。
 * PredictiveBackHandler で指の進行度を取得し、
 * graphicsLayer で画面を縮小＋角丸化。
 * Window背景が透明なので、縮小した隙間からホーム画面が見える。
 */
@Composable
private fun HomeBackWrapper(
    enabled: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }

    if (enabled) {
        PredictiveBackHandler { progressFlow ->
            try {
                progressFlow.collect { backEvent ->
                    progress = backEvent.progress
                }
                // ジェスチャー完了 → ホームへ戻る
                onBack()
            } catch (e: CancellationException) {
                // キャンセル → 元に戻す
                progress = 0f
            }
        }
    }

    // 進行度 0→1 に応じて縮小 (1.0 → 0.85) ＋ 角丸 (0dp → 28dp)
    val scale = 1f - (progress * 0.15f)
    val cornerRadius = (progress * 28f).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
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
