package com.m4sak1.ryousyoku

import android.content.Context
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
        // Windowを透明にし、ホーム画面戻り時に壁紙が見えるようにする
        window.setBackgroundDrawableResource(android.R.color.transparent)

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

                    NavHost(
                        navController = navController,
                        startDestination = "main",
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) {
                        composable("main") {
                            PredictiveBackWrapper(
                                enabled = isPredictiveBackEnabled,
                                isRoot = true,
                                onBack = { activity?.finish() }
                            ) {
                                MainScreen(
                                    onNavigateToSettings = { navController.navigate("settings") }
                                )
                            }
                        }
                        composable("settings") {
                            PredictiveBackWrapper(
                                enabled = isPredictiveBackEnabled,
                                isRoot = false,
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
                            PredictiveBackWrapper(
                                enabled = isPredictiveBackEnabled,
                                isRoot = false,
                                onBack = { navController.popBackStack() }
                            ) {
                                RawDataScreen(
                                    type = type,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                        composable("licenses") {
                            PredictiveBackWrapper(
                                enabled = isPredictiveBackEnabled,
                                isRoot = false,
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
 * 予測型戻る (Predictive Back) アニメーションラッパー
 * 戻るジェスチャーの進行度(progress)に合わせて、現在の画面をカード状に縮小(Scale down)し、
 * 角丸(Rounded corners)をつける標準的なエフェクト(ith-back-to-home-animation)。
 */
@Composable
fun PredictiveBackWrapper(
    enabled: Boolean,
    isRoot: Boolean = false,
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
                onBack()
            } catch (e: CancellationException) {
                progress = 0f
            }
        }
    }

    val scale = 1f - (progress * 0.12f)
    val cornerRadius = (progress * 28).dp

    val themeBg = MaterialTheme.colorScheme.background
    val outerBg = if (isRoot) {
        if (progress > 0f) Color.Black.copy(alpha = 0.3f * (1f - progress)) else Color.Transparent
    } else {
        themeBg
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(outerBg)
    ) {
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
}

object NoRippleIndication : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): androidx.compose.ui.Modifier.Node {
        return object : androidx.compose.ui.Modifier.Node() {}
    }

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -1
}
