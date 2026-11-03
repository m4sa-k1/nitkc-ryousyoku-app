package com.m4sak1.ryousyoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.m4sak1.ryousyoku.repository.MenuRepository
import com.m4sak1.ryousyoku.model.skipRecords
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RawDataScreen(
    type: String,
    onNavigateBack: () -> Unit,
    viewModel: MenuViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val selectedMenu by viewModel.selectedMenu.collectAsState()
    val selections by viewModel.selections.collectAsState()
    var rawData by remember(type, selectedMenu?.filename) { mutableStateOf<String?>(null) }
    var error by remember(type, selectedMenu?.filename) { mutableStateOf<String?>(null) }
    val repository = remember { MenuRepository() }

    LaunchedEffect(type, selectedMenu?.filename) {
        if (type != "meal_skips") {
            try {
                rawData = when (type) {
                    "menus" -> repository.fetchRawMenus()
                    "skip_periods" -> repository.fetchRawSkipPeriods()
                    "structured_index" -> repository.fetchRawStructuredIndex()
                    "structured_menu" -> repository.fetchRawStructuredMenu(
                        selectedMenu ?: throw IllegalStateException("先に献立を選択してください。"))
                    else -> throw IllegalArgumentException("未対応のデータです。")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                error = e.message ?: "データを読み込めませんでした。"
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
                    text = when (type) {
                        "menus" -> "献立ファイル一覧（JSON）"
                        "skip_periods" -> "提供休止期間（JSON）"
                        "structured_index" -> "解析結果一覧（JSON）"
                        "structured_menu" -> "表示中の献立（JSON）"
                        "meal_skips" -> "欠食記録（一覧・JSON）"
                        else -> "データ確認"
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontFamily = MPlusRoundedFontFamily
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            when {
                type == "meal_skips" -> SkippedMealsContent(selections)
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
                    SelectionContainer {
                    Text(
                        text = rawData!!,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = FontFamily.Monospace, // Monospace for JSON
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("raw-json-content")
                            .verticalScroll(rememberScrollState())
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkippedMealsContent(selections: Map<String, String>) {
    val records = remember(selections) { skipRecords(selections) }
    val saved = remember(selections) { selections.filterValues { it == "skip" }.toSortedMap().toMap() }
    var showJson by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("欠食 ${saved.size}件", fontFamily = MPlusRoundedFontFamily)
            TextButton(onClick = { showJson = !showJson }) {
                Text(if (showJson) "一覧で表示" else "生JSONを表示")
            }
        }
        if (showJson) {
            SelectionContainer {
                Text(Json { prettyPrint = true }.encodeToString(saved), fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp, modifier = Modifier.fillMaxSize().testTag("skip-records-json")
                        .verticalScroll(rememberScrollState()))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (saved.isEmpty()) item {
                    Text("欠食の記録はありません。", fontFamily = MPlusRoundedFontFamily,
                        modifier = Modifier.padding(vertical = 24.dp))
                }
                if (saved.size != records.size) item {
                    Text("読み取れない記録があります。生JSONから確認できます。", fontSize = 12.sp)
                }
                items(records, key = { "${it.sourceHash}/${it.date}/${it.meal.key}" }) { record ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text("${record.date.format(DateTimeFormatter.ofPattern("yyyy/M/d（E）", Locale.JAPAN))}  ${record.meal.label}",
                            fontFamily = MPlusRoundedFontFamily, fontSize = 15.sp)
                        Text("原本ID: ${record.sourceHash.take(12)}…", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .15f))
                }
            }
        }
    }
}
