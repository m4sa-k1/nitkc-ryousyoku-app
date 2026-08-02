package com.m4sak1.ryousyoku.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    onNavigateBack: () -> Unit,
    onNavigateToRawData: (String) -> Unit,
    onNavigateToLicenses: () -> Unit
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
                    text = "← 戻る",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontFamily = MPlusRoundedFontFamily,
                    modifier = Modifier.clickable { onNavigateBack() }
                )
                Spacer(modifier = Modifier.width(16.dp))
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

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "データ確認",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontFamily = MPlusRoundedFontFamily
            )

            // Raw Data
            SettingsItem(text = "menus.json を確認") { onNavigateToRawData("menus") }
            SettingsItem(text = "skip_periods.json を確認") { onNavigateToRawData("skip_periods") }

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
            SettingsItem(text = "運営者情報 (m4sak1)") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://rin.gonyo.net")))
            }
            SettingsItem(text = "オープンソースライセンス") {
                onNavigateToLicenses()
            }
            SettingsItem(text = "お問い合わせ (mail@ringonyo.net)") {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:mail@ringonyo.net")
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
