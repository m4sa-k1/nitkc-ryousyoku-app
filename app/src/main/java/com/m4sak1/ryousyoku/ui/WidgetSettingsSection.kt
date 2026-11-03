package com.m4sak1.ryousyoku.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m4sak1.ryousyoku.widget.NextMealWidget

@Composable
fun WidgetSettingsSection() {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ホーム画面ウィジェット", fontFamily = MPlusRoundedFontFamily, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("次の1食をホーム画面に表示します。朝食のデフォルトや日別の選択・欠食も反映し、タップでその日の献立を開けます。",
            fontFamily = MPlusRoundedFontFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = {
            val manager = AppWidgetManager.getInstance(context)
            val accepted = runCatching { manager.isRequestPinAppWidgetSupported &&
                manager.requestPinAppWidget(ComponentName(context, NextMealWidget::class.java), null, null) }.getOrDefault(false)
            message = if (accepted) "ホーム画面側で追加を確定してください。" else "ホーム画面の長押し → ウィジェットから「次の献立」を追加してください。"
        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text("ウィジェットを追加", fontFamily = MPlusRoundedFontFamily)
        }
        Text("縦横にサイズを変更できます。大きくすると料理を多く表示できます。端末の省電力設定などにより切り替えが遅れる場合があります。",
            fontFamily = MPlusRoundedFontFamily, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        message?.let { Text(it, fontFamily = MPlusRoundedFontFamily, fontSize = 12.sp) }
    }
}
