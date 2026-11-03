package com.m4sak1.ryousyoku.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.m4sak1.ryousyoku.model.BreakfastDefault
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    isPredictiveBackEnabled: Boolean,
    onTogglePredictiveBack: () -> Unit,
    onNavigateToRawData: (String) -> Unit,
    onNavigateToLicenses: () -> Unit,
    isTextMode: Boolean = false,
    onTextModeChange: (Boolean) -> Unit = {},
    breakfastDefault: BreakfastDefault = BreakfastDefault.NONE,
    onBreakfastDefaultChange: (BreakfastDefault) -> Unit = {},
    backupViewModel: MenuViewModel? = null,
    onNavigateToStatistics: () -> Unit = {}
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp)
            ) {
                Text(
                    text = "設定",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MPlusRoundedFontFamily
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "アプリ設定",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontFamily = MPlusRoundedFontFamily
            )

            // Theme Toggle
            Row(Modifier.fillMaxWidth().clickable { onTextModeChange(!isTextMode) }.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("文字で献立を表示", fontFamily = MPlusRoundedFontFamily)
                    Text("オフにすると献立画像を表示します", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = MPlusRoundedFontFamily)
                }
                Switch(checked = isTextMode, onCheckedChange = onTextModeChange)
            }

            var breakfastMenuExpanded by remember { mutableStateOf(false) }
            Box {
                Row(Modifier.fillMaxWidth().clickable { breakfastMenuExpanded = true }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("朝食のデフォルト", fontFamily = MPlusRoundedFontFamily)
                        Text("日ごとの選択・欠食の記録を優先します", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = MPlusRoundedFontFamily)
                    }
                    Text("${breakfastDefault.label} ▾", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                        fontFamily = MPlusRoundedFontFamily)
                }
                DropdownMenu(expanded = breakfastMenuExpanded, onDismissRequest = { breakfastMenuExpanded = false }) {
                    BreakfastDefault.entries.forEach { value ->
                        DropdownMenuItem(text = { Text(value.label, fontFamily = MPlusRoundedFontFamily) },
                            onClick = { onBreakfastDefaultChange(value); breakfastMenuExpanded = false })
                    }
                }
            }
            if (breakfastDefault == BreakfastDefault.BREAD) {
                Text("パン等（B）はホットケーキなどのB献立も含みます。", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = MPlusRoundedFontFamily)
            }

            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggleTheme() }.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ダークモード", color = MaterialTheme.colorScheme.onBackground, fontFamily = MPlusRoundedFontFamily)
                Switch(checked = isDarkMode, onCheckedChange = { onToggleTheme() })
            }

            // Predictive Back Toggle
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onTogglePredictiveBack() }.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("予測型戻る (Predictive Back)", color = MaterialTheme.colorScheme.onBackground, fontFamily = MPlusRoundedFontFamily)
                    Text("戻る操作時に戻り先をプレビュー表示 (Android 13+)", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = MPlusRoundedFontFamily)
                }
                Switch(checked = isPredictiveBackEnabled, onCheckedChange = { onTogglePredictiveBack() })
            }

            if (backupViewModel != null) {
                NotificationSettingsSection()
                Spacer(Modifier.height(8.dp))
                BackupSection(backupViewModel)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "データ確認",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontFamily = MPlusRoundedFontFamily
            )

            SettingsItem(text = "献立の統計") { onNavigateToStatistics() }

            // Raw Data
            SettingsItem(text = "献立ファイル一覧（JSON）") { onNavigateToRawData("menus") }
            SettingsItem(text = "解析結果一覧（JSON）") { onNavigateToRawData("structured_index") }
            SettingsItem(text = "表示中の献立（JSON）") { onNavigateToRawData("structured_menu") }
            SettingsItem(text = "欠食記録（一覧・JSON）") { onNavigateToRawData("meal_skips") }
            SettingsItem(text = "提供休止期間（JSON）") { onNavigateToRawData("skip_periods") }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "アプリ情報",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontFamily = MPlusRoundedFontFamily
            )

            SettingsItem(text = "GitHub リポジトリ") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/m4sa-k1/nitkc-ryousyoku-app")))
            }
            SettingsItem(text = "運営者情報") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://m4sak1.me")))
            }
            SettingsItem(text = "オープンソースライセンス") {
                onNavigateToLicenses()
            }
            SettingsItem(text = "お問い合わせ（me@m4sak1.me）") {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:me@m4sak1.me")
                }
                context.startActivity(intent)
            }
        }
    }
}

@Composable
fun SettingsItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground,
        fontFamily = MPlusRoundedFontFamily,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    )
}
