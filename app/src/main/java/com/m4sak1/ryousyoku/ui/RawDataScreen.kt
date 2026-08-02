package com.m4sak1.ryousyoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m4sak1.ryousyoku.repository.MenuRepository
import kotlinx.coroutines.launch

@Composable
fun RawDataScreen(
    type: String,
    onNavigateBack: () -> Unit
) {
    var rawData by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { MenuRepository() }

    LaunchedEffect(type) {
        coroutineScope.launch {
            try {
                rawData = if (type == "menus") {
                    repository.fetchRawMenus()
                } else {
                    repository.fetchRawSkipPeriods()
                }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Simple Header
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
                    text = if (type == "menus") "menus.json" else "skip_periods.json",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontFamily = MPlusRoundedFontFamily
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when {
                error != null -> {
                    Text(
                        text = "エラー: $error",
                        color = Color.Red,
                        fontFamily = MPlusRoundedFontFamily,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                rawData == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    Text(
                        text = rawData!!,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = FontFamily.Monospace, // Monospace for JSON
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
