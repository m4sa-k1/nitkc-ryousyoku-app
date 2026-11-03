package com.m4sak1.ryousyoku.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ArchiveDisplayDialog(period: String, onText: () -> Unit, onImage: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 420.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp), shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .12f))) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("過去の献立", fontSize = 11.sp, fontFamily = MPlusRoundedFontFamily,
                                color = MaterialTheme.colorScheme.primary)
                            Text("献立の表示方法", fontSize = 19.sp, fontFamily = MPlusRoundedFontFamily,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "閉じる" }) {
                            Text("×", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(period, fontSize = 12.sp, fontFamily = MPlusRoundedFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                    Spacer(Modifier.height(2.dp))
                    ArchiveDisplayOption("文字で表示", "日付ごとに料理を読みやすく", true, onText)
                    ArchiveDisplayOption("画像で表示", "献立表をそのまま拡大して確認", false, onImage)
                }
            }
        }
    }
}

@Composable
private fun ArchiveDisplayOption(title: String, subtitle: String, textMode: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
        color = primary.copy(alpha = .035f), border = BorderStroke(1.dp, primary.copy(alpha = .18f))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).background(primary.copy(alpha = .1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(24.dp)) {
                    val stroke = 1.7.dp.toPx()
                    if (textMode) {
                        listOf(.25f, .5f, .75f).forEachIndexed { index, y ->
                            drawLine(primary, Offset(size.width * .08f, size.height * y),
                                Offset(size.width * if (index == 2) .7f else .92f, size.height * y), stroke)
                        }
                    } else {
                        drawRoundRect(primary, Offset(1.dp.toPx(), 1.dp.toPx()),
                            Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = Stroke(stroke))
                        drawCircle(primary, radius = 2.dp.toPx(), center = Offset(size.width * .7f, size.height * .3f))
                        drawLine(primary, Offset(size.width * .15f, size.height * .78f),
                            Offset(size.width * .42f, size.height * .48f), stroke)
                        drawLine(primary, Offset(size.width * .42f, size.height * .48f),
                            Offset(size.width * .85f, size.height * .8f), stroke)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = MPlusRoundedFontFamily)
                Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = MPlusRoundedFontFamily, lineHeight = 16.sp)
            }
            Text("›", fontSize = 24.sp, color = primary)
        }
    }
}
