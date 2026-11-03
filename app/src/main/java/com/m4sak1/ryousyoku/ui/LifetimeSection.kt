package com.m4sak1.ryousyoku.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m4sak1.ryousyoku.model.*
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun LifetimeSection(viewModel: MenuViewModel) {
    val archive by viewModel.archive.collectAsState()
    val structured by viewModel.structured.collectAsState()
    val selections by viewModel.selections.collectAsState()
    val breakfastDefault by viewModel.breakfastDefault.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), viewModel::saveExport)
    val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain"), viewModel::saveExport)
    LaunchedEffect(structured) {
        if (structured !is StructuredState.Loading) viewModel.loadArchive()
    }
    when (val state = archive) {
        is ArchiveState.Idle, is ArchiveState.Loading -> Unit
        is ArchiveState.Ready -> {
            val today = LocalDate.now(ZoneId.of("Asia/Tokyo"))
            val export = remember(state.snapshot, selections, breakfastDefault, today) {
                runCatching {
                    makeNutritionExport(state.snapshot, selections, breakfastDefault, today, today.toString())
                }.getOrNull()
            }
            if (export == null || export.days.isEmpty()) {
                Text("集計できるデータがありません。", fontSize = 14.sp)
                TextButton(onClick = { viewModel.loadArchive(force = true) }) { Text("再読み込み") }
            } else {
                val totals = remember(export) { lifetimeTotals(export) }
                Text("これまでの献立、総まとめ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${totals.start ?: "—"} – ${totals.end ?: "—"}（今日まで）", fontSize = 12.sp)
                Text("献立ベースの予定量です。実際に食べた量を示すものではありません。",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val entries = Nutrient.entries.toList()
                entries.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { nutrient ->
                            ReportCard(modifier = Modifier.weight(1f)) {
                                val value = totals.nutrition.getValue(nutrient)
                                Text("総${nutrient.label}", fontSize = 12.sp)
                                Text(if (value.knownMeals == 0) "—" else formatAmount(value.value, nutrient),
                                    fontSize = 25.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(nutrient.unit, fontSize = 12.sp)
                                Text("${value.knownMeals}/${value.expectedMeals}食を集計", fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                ReportCard {
                    Text("食堂の記録帳", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("献立のある食数　${totals.listedMeals}食", fontSize = 14.sp)
                    Text("集計できた食数　${totals.knownMeals}食（欠食を含む）", fontSize = 13.sp)
                    Text("欠食の記録　${totals.skippedMeals}食", fontSize = 13.sp)
                    Text("朝食の既定を使った食数　${totals.defaultedMeals}食", fontSize = 13.sp)
                    totals.topDish?.let { (dish, count) ->
                        Text("最多登場　「$dish」$count 回", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("未選択の食事は総量に含めていません。最多登場は、集計対象の料理名から数えています。",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.snapshot.unavailableFiles.isNotEmpty()) {
                    Text("未取得 ${state.snapshot.unavailableFiles.size}週：累計は取得できた週だけの部分集計です。",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                if (state.snapshot.cachedFiles.isNotEmpty()) {
                    Text("保存済みデータを含みます（${state.snapshot.cachedFiles.size}週）。", fontSize = 11.sp)
                }
                ReportCard {
                    Text("食事調整の相談用に書き出す", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("全期間の献立・選択・欠食・栄養値と、未記録の区別を含みます。未来の献立も別扱いで含めます。",
                        fontSize = 12.sp, lineHeight = 20.sp)
                    OutlinedButton(onClick = {
                        viewModel.prepareExport(asJson = false)?.let { saveText.launch(it) }
                    }, modifier = Modifier.fillMaxWidth()) { Text("AI相談用テキストを書き出す", fontSize = 12.sp) }
                    OutlinedButton(onClick = {
                        viewModel.prepareExport(asJson = true)?.let { saveJson.launch(it) }
                    }, modifier = Modifier.fillMaxWidth()) { Text("JSONを書き出す", fontSize = 12.sp) }
                    Text("保存先は端末の画面で選べます。AIサービスへの自動送信は行いません。",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    exportMessage?.let { Text(it, fontSize = 12.sp) }
                }
            }
        }
    }
}
