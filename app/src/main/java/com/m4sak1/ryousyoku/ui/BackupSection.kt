package com.m4sak1.ryousyoku.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun BackupSection(viewModel: MenuViewModel) {
    val preview by viewModel.backupPreview.collectAsState()
    val message by viewModel.backupMessage.collectAsState()
    val busy by viewModel.backupBusy.collectAsState()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), viewModel::saveBackup)
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::readBackup)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("バックアップ・復元", color = MaterialTheme.colorScheme.primary,
            fontFamily = MPlusRoundedFontFamily, fontWeight = FontWeight.Bold)
        Text("欠食・A/Bの選択記録と朝食のデフォルトを保存できます。献立データや表示設定は含みません。",
            fontSize = 12.sp, fontFamily = MPlusRoundedFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { viewModel.prepareBackup()?.let { save.launch(it) } }, enabled = !busy,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text("バックアップを保存", fontFamily = MPlusRoundedFontFamily)
        }
        OutlinedButton(onClick = { load.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text("バックアップを読み込む", fontFamily = MPlusRoundedFontFamily)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, fontSize = 12.sp, fontFamily = MPlusRoundedFontFamily) }
    }
    preview?.let { data ->
        var replace by rememberSaveable(data) { mutableStateOf(false) }
        Dialog(onDismissRequest = viewModel::dismissBackupPreview) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .15f))) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("バックアップを復元", fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = MPlusRoundedFontFamily)
                    val timestamp = OffsetDateTime.parse(data.createdAt).atZoneSameInstant(java.time.ZoneId.of("Asia/Tokyo"))
                        .format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm"))
                    Text("保存日時：$timestamp（日本時間）\n記録：${data.selections.size}件（欠食 ${data.selections.values.count { it == "skip" }}件）\n朝食のデフォルト：${data.breakfastDefault.label}",
                        fontSize = 12.sp, lineHeight = 22.sp, fontFamily = MPlusRoundedFontFamily)
                    SelectionControl(listOf(false, true), replace, label = { if (it) "置き換え" else "追加・統合" },
                        onSelect = { if (!busy) replace = it })
                    Text(if (replace) "現在の欠食・選択記録をすべて削除し、このバックアップの内容に置き換えます。"
                        else "今ある記録を残して追加します。同じ献立・日付・食事の記録は、バックアップの内容を優先します。",
                        fontSize = 12.sp, fontFamily = MPlusRoundedFontFamily)
                    Text("朝食のデフォルトは、どちらの方法でもバックアップの設定に変わります。献立が改訂されている場合、古い原本の記録は保持されますが集計には反映されません。",
                        fontSize = 11.sp, fontFamily = MPlusRoundedFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { viewModel.restoreBackup(replace) }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("この内容で復元", fontFamily = MPlusRoundedFontFamily)
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    message?.let { Text(it, fontSize = 12.sp, fontFamily = MPlusRoundedFontFamily) }
                    TextButton(onClick = viewModel::dismissBackupPreview, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("キャンセル", fontFamily = MPlusRoundedFontFamily)
                    }
                }
            }
        }
    }
}
