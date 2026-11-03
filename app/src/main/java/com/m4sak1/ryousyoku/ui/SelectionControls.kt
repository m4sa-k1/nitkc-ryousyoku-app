package com.m4sak1.ryousyoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Equal-width choices with a single selected surface; no checkmark icons. */
@Composable
fun <T> SelectionControl(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.primary.copy(alpha = .07f))
        .padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { item ->
            val active = item == selected
            Box(Modifier.weight(1f).heightIn(min = 40.dp).clip(shape)
                .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                .border(1.dp, if (active) MaterialTheme.colorScheme.primary.copy(alpha = .3f) else Color.Transparent, shape)
                .selectable(selected = active, role = Role.Tab, onClick = { onSelect(item) })
                .padding(horizontal = 4.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                Text(label(item), fontSize = if (options.size > 3) 11.sp else 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = MPlusRoundedFontFamily, textAlign = TextAlign.Center,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun CenteredLoadingStatus(message: String) {
    Box(Modifier.fillMaxSize().testTag("nutrition-loading")) {
        CircularProgressIndicator(Modifier.size(32.dp).align(Alignment.Center), strokeWidth = 3.dp)
        Text(message, fontSize = 12.sp, fontFamily = MPlusRoundedFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).offset(y = 42.dp).padding(horizontal = 24.dp))
    }
}
