package com.m4sak1.ryousyoku.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m4sak1.ryousyoku.model.MealNotificationIcon

@Composable
fun NotificationIconSelector(selected: MealNotificationIcon, onSelect: (MealNotificationIcon) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { query = ""; expanded = true },
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .12f))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(selected.drawableRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("通知アイコン", fontFamily = MPlusRoundedFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${selected.label} · ${selected.title}", fontFamily = MPlusRoundedFontFamily, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("変更 ›", color = MaterialTheme.colorScheme.primary, fontFamily = MPlusRoundedFontFamily, fontSize = 12.sp)
        }
    }
    if (expanded) Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(24.dp).widthIn(max = 460.dp).fillMaxWidth().fillMaxHeight(.82f), shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .15f))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("通知アイコン", fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = MPlusRoundedFontFamily, modifier = Modifier.weight(1f))
                    IconButton(onClick = { expanded = false }, modifier = Modifier.semantics { contentDescription = "アイコン選択を閉じる" }) {
                        Text("×", fontSize = 25.sp)
                    }
                }
                Text("朝昼夕で共通。通知欄と通知カードに反映します。", fontSize = 11.sp,
                    fontFamily = MPlusRoundedFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("名前で検索") },
                    modifier = Modifier.fillMaxWidth().testTag("notification-icon-search"), shape = RoundedCornerShape(12.dp))
                val found = MealNotificationIcon.entries.filter { it.label.contains(query, true) || it.title.contains(query, true) || it.key.contains(query, true) }
                if (found.isEmpty()) Text("一致するアイコンがありません。", fontSize = 12.sp, fontFamily = MPlusRoundedFontFamily)
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f).selectableGroup().testTag("notification-icon-grid"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(found, key = { it.key }) { icon ->
                        val active = selected == icon
                        Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .selectable(selected = active, role = Role.RadioButton, onClick = { keyboard?.hide(); focus.clearFocus(); onSelect(icon); expanded = false })
                            .semantics { contentDescription = "${icon.label}（${icon.title}）" },
                            shape = RoundedCornerShape(12.dp), color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = .08f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .3f),
                            border = BorderStroke(if (active) 2.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .15f))) {
                            Column(Modifier.heightIn(min = 108.dp).padding(horizontal = 5.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(painterResource(icon.drawableRes), contentDescription = null, modifier = Modifier.size(30.dp),
                                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(icon.label, fontFamily = MPlusRoundedFontFamily, fontSize = 10.sp, textAlign = TextAlign.Center)
                                Text(icon.title, fontFamily = MPlusRoundedFontFamily, fontSize = 9.sp, textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 12.sp)
                            }
                        }
                    }
                }
                Text("Google Material Symbols · 28種類", fontSize = 10.sp, fontFamily = MPlusRoundedFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
