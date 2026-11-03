package com.m4sak1.ryousyoku.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.m4sak1.ryousyoku.model.*
import com.m4sak1.ryousyoku.notifications.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsSection() {
    val context = LocalContext.current
    val store = remember { MealNotificationPreferences(context) }
    var settings by remember { mutableStateOf(MealKind.entries.associateWith(store::get)) }
    var icon by remember { mutableStateOf(store.icon) }
    var systemVersion by remember { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf<MealKind?>(null) }
    var testing by rememberSaveable { mutableStateOf(false) }
    var pendingPreview by rememberSaveable { mutableStateOf<MealKind?>(null) }
    fun preview(kind: MealKind) = MealNotificationScheduler.enqueue(context, kind,
        LocalDate.now(MenuTimeZone).toString(), store.revision(kind), preview = true)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        systemVersion++
        MealNotificationScheduler.rescheduleAll(context)
        if (granted) pendingPreview?.let { preview(it) }
        pendingPreview = null
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            systemVersion++
            settings = MealKind.entries.associateWith(store::get)
            icon = store.icon
            MealNotificationScheduler.rescheduleAll(context)
        } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val canNotify = remember(systemVersion) { MealNotificationScheduler.canNotify(context) }
    val exact = remember(systemVersion) { MealNotificationScheduler.canScheduleExactly(context) }
    fun update(kind: MealKind, value: MealNotificationSetting) {
        store.set(kind, value)
        settings = settings + (kind to value)
        MealNotificationScheduler.rescheduleAll(context)
        if (value.enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("献立通知", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontFamily = MPlusRoundedFontFamily)
        Text("毎日・日本時間。朝食のデフォルトがある場合は、その献立と共通料理を通知します。未設定ならA/Bを両方表示します。",
            fontSize = 12.sp, fontFamily = MPlusRoundedFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
        NotificationIconSelector(icon) { selected ->
            store.setIcon(selected)
            icon = selected
            MealNotificationDelivery.refreshIcons(context)
        }
        MealKind.entries.forEach { kind ->
            val setting = settings.getValue(kind)
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .12f))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(kind.label, Modifier.weight(1f), fontFamily = MPlusRoundedFontFamily, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { editing = kind }, modifier = Modifier.semantics { contentDescription = "${kind.label}の通知時刻" }) {
                        Text(setting.timeLabel, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = MPlusRoundedFontFamily)
                    }
                    Switch(checked = setting.enabled, onCheckedChange = { update(kind, setting.copy(enabled = it)) },
                        modifier = Modifier.semantics { contentDescription = "${kind.label}の献立通知" })
                }
            }
        }
        if (!canNotify) {
            Text("通知の許可が必要です。端末の設定で「献立のお知らせ」も有効にしてください。", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary, fontFamily = MPlusRoundedFontFamily)
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("端末の通知設定を開く", fontFamily = MPlusRoundedFontFamily) }
        }
        if (!exact && Build.VERSION.SDK_INT >= 31) {
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                runCatching { context.startActivity(intent) }.onFailure {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
            }) { Text("指定時刻に通知する許可", fontFamily = MPlusRoundedFontFamily) }
        }
        Text("端末の省電力設定・時刻の許可・通信状況により通知が遅れる場合があります。データを取得できない場合は画像・PDFの確認をお知らせします。",
            fontSize = 11.sp, fontFamily = MPlusRoundedFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { testing = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text("通知を試す", fontFamily = MPlusRoundedFontFamily)
        }
    }
    editing?.let { kind ->
        val value = settings.getValue(kind)
        val time = rememberTimePickerState(value.hour, value.minute, is24Hour = true)
        NotificationDialog(onDismiss = { editing = null }) {
            Text("${kind.label}の通知時刻", fontSize = 19.sp, fontFamily = MPlusRoundedFontFamily, fontWeight = FontWeight.Bold)
            Text("毎日・日本時間", fontSize = 12.sp, fontFamily = MPlusRoundedFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TimeInput(state = time, modifier = Modifier.align(Alignment.CenterHorizontally))
            Button(onClick = { update(kind, value.copy(hour = time.hour, minute = time.minute)); editing = null },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("時刻を保存", fontFamily = MPlusRoundedFontFamily) }
            TextButton(onClick = { editing = null }, modifier = Modifier.fillMaxWidth()) { Text("キャンセル", fontFamily = MPlusRoundedFontFamily) }
        }
    }
    if (testing) NotificationDialog(onDismiss = { testing = false }) {
        Text("通知を試す", fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = MPlusRoundedFontFamily)
        Text("通知の予約を変更せず、今日の献立を通知します。", fontSize = 12.sp, fontFamily = MPlusRoundedFontFamily)
        MealKind.entries.forEach { kind ->
            OutlinedButton(onClick = {
                testing = false
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    pendingPreview = kind
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (canNotify) preview(kind) else {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("${kind.label}をテスト通知", fontFamily = MPlusRoundedFontFamily) }
        }
        TextButton(onClick = { testing = false }, modifier = Modifier.fillMaxWidth()) { Text("キャンセル", fontFamily = MPlusRoundedFontFamily) }
    }
}

@Composable
private fun NotificationDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .15f))) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}
