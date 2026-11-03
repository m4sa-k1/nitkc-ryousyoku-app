package com.m4sak1.ryousyoku.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m4sak1.ryousyoku.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class StatisticsTab(val label: String) { DISHES("料理"), MENUS("献立"), NUTRITION("栄養"), RECORDS("記録") }
private data class StatisticsDetail(val title: String, val appearances: List<DishAppearance>, val selectedCount: Int? = null)
private val StatisticDate = DateTimeFormatter.ofPattern("yyyy/M/d", Locale.JAPAN)
private fun percent(count: Int, total: Int) = if (total == 0) "—" else String.format(Locale.JAPAN, "%.1f%%", count * 100.0 / total)

@Composable
fun StatisticsIcon() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(23.dp)) {
        drawArc(color.copy(alpha = .3f), -90f, 360f, false, style = Stroke(4.dp.toPx()))
        drawArc(color, -90f, 245f, false, style = Stroke(4.dp.toPx()))
    }
}

@Composable
fun StatisticsScreen(viewModel: MenuViewModel) {
    val archive by viewModel.archive.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selections by viewModel.selections.collectAsState()
    val breakfast by viewModel.breakfastDefault.collectAsState()
    var range by rememberSaveable { mutableStateOf(StatisticsRange.ALL) }
    var kind by rememberSaveable { mutableStateOf<MealKind?>(null) }
    var tab by rememberSaveable { mutableStateOf(StatisticsTab.DISHES) }
    var query by rememberSaveable { mutableStateOf("") }
    var nutrient by rememberSaveable { mutableStateOf(Nutrient.ENERGY) }
    var shown by rememberSaveable(range, kind, tab, query) { mutableIntStateOf(20) }
    var detail by remember { mutableStateOf<StatisticsDetail?>(null) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun dismissSearch() { keyboard?.hide(); focus.clearFocus() }
    val today = LocalDate.now(MenuTimeZone)
    LaunchedEffect(uiState) { if (uiState is MenuState.Success) viewModel.loadArchive() }
    val snapshot = (archive as? ArchiveState.Ready)?.snapshot
    val unavailableFiles = snapshot?.unavailableFiles.orEmpty()
    val cachedFiles = snapshot?.cachedFiles.orEmpty()
    val result = remember(snapshot, selections, breakfast, today, range, kind) {
        snapshot?.let { runCatching { makeMenuStatistics(it, selections, breakfast, today, range, kind) } }
    }
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
        fontFamily = MPlusRoundedFontFamily, color = MaterialTheme.colorScheme.onBackground)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("献立の統計", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        if (uiState is MenuState.Error) viewModel.fetchMenus() else viewModel.loadArchive(force = true)
                    }) { Text("再読み込み", fontFamily = MPlusRoundedFontFamily, fontSize = 12.sp) }
                }
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SelectionControl(StatisticsTab.entries, tab, label = { it.label }, onSelect = { dismissSearch(); tab = it })
                }
                LazyColumn(Modifier.weight(1f).testTag("statistics-list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp,
                        bottom = 20.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        SelectionControl(StatisticsRange.entries, range, label = { it.label }, onSelect = { dismissSearch(); range = it })
                    }
                    item {
                        SelectionControl(listOf(null) + MealKind.entries, kind, label = { it?.label ?: "全食" }, onSelect = { dismissSearch(); kind = it })
                    }
                    if (uiState is MenuState.Error) item { ReportCard { Text("献立一覧を取得できませんでした。再読み込みをお試しください。") } }
                    if (uiState is MenuState.Success && (uiState as MenuState.Success).allMenus.isEmpty()) item {
                        ReportCard { Text("まだ献立データがありません。") }
                    }
                    if (result?.isFailure == true) item { ReportCard { Text("日付が重複するなど、データの整合性を確認できないため集計を止めています。") } }
                    val data = result?.getOrNull()
                    if (data != null) {
                        item {
                            Text(if (data.start == null) "対象期間の献立はありません" else "${data.start.format(StatisticDate)} ～ ${data.end?.format(StatisticDate)}",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("今日までの取得済みデータを集計。未来の献立は含みません。", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (unavailableFiles.isNotEmpty()) item {
                            Text("未取得 ${unavailableFiles.size}件：取得できた範囲の部分集計です。", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                        if (cachedFiles.isNotEmpty()) item { Text("保存済みデータを含みます（${cachedFiles.size}件）。", fontSize = 11.sp) }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatisticsTile("対象日数", "${data.days}", "日", Modifier.weight(1f))
                                StatisticsTile("掲載食数", "${data.listed.size}", "食", Modifier.weight(1f))
                                StatisticsTile("料理の種類", "${data.dishes.size}", "種類", Modifier.weight(1f))
                            }
                        }
                        when (tab) {
                            StatisticsTab.DISHES -> {
                                item {
                                    ReportCard {
                                        Text("料理の登場ランキング", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        Text("1食に同じ料理がA/B・共通で重なっても1回。選択していない候補も含む、献立への掲載回数です。割合の分母は掲載食数です。", fontSize = 11.sp, lineHeight = 18.sp)
                                        StatisticLine("登場総数", "${data.appearances}回")
                                        StatisticLine("1回だけ登場した料理", "${data.dishes.count { it.count == 1 }}種類")
                                        StatisticLine("1食あたりの料理数（候補込み）", if (data.listed.isEmpty()) "—" else String.format(Locale.JAPAN, "%.1f種類", data.appearances.toDouble() / data.listed.size))
                                        val streak = data.dishes.sortedWith(compareByDescending<DishStatistic> { it.longestStreak }.thenBy { it.name }).firstOrNull()
                                        streak?.let { StatisticLine("最長の連続登場", "${it.name}・${it.longestStreak}日") }
                                        data.diversity.firstOrNull()?.let { StatisticLine("料理の種類が最多の日", "${it.first.format(StatisticDate)}・${it.second}種類") }
                                    }
                                }
                                item {
                                    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("料理名で検索") },
                                        singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { dismissSearch() }), modifier = Modifier.fillMaxWidth().testTag("statistics-search"), shape = RoundedCornerShape(12.dp))
                                }
                                val found = data.dishes.withIndex().filter { it.value.name.contains(statisticsDishKey(query), ignoreCase = true) }
                                if (found.isEmpty()) item { Text("一致する料理がありません。", fontSize = 13.sp) }
                                items(found.take(shown), key = { "dish-${it.value.name}" }) { indexed ->
                                    val dish = indexed.value
                                    StatisticsRankingRow(indexed.index + 1, dish.name, dish.count, data.listed.size) {
                                        dismissSearch()
                                        detail = StatisticsDetail(dish.name, dish.appearances, dish.selectedCount)
                                    }
                                }
                                if (shown < found.size) item { MoreStatistics(shown, found.size) { shown += 20 } }
                                item { Text("全角・半角・空白だけを統一し、別名や量の違いを推測でまとめません。行をタップすると登場日を確認できます。", fontSize = 10.sp, lineHeight = 17.sp) }
                            }
                            StatisticsTab.MENUS -> {
                                item {
                                    ReportCard {
                                        Text("献立の構成", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        StatisticLine("A/Bを選べる食事", "${data.choices}食・${percent(data.choices, data.listed.size)}")
                                        StatisticLine("固定献立の食事", "${data.listed.size - data.choices}食")
                                        StatisticLine("原本に記載なし", "${data.meals.count { it.meal.status == "not_listed" }}食")
                                        StatisticLine("提供なしと明記", "${data.meals.count { it.meal.status == "not_served" }}食")
                                        StatisticLine("献立の組み合わせ", "${data.patterns.size}種類")
                                    }
                                }
                                item { ReportCard {
                                    Text("月ごとの広がり", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    data.months.forEach { month ->
                                        StatisticLine("${month.month.year}年${month.month.monthValue}月", "${month.days}日・${month.listedMeals}食・${month.uniqueDishes}種類")
                                    }
                                } }
                                item { ReportCard {
                                    Text("曜日別の献立", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    val maximum = data.weekdays.maxOfOrNull { it.listedMeals }?.coerceAtLeast(1) ?: 1
                                    data.weekdays.forEach { day ->
                                        StatisticLine("${"月火水木金土日"[day.weekday - 1]}曜日（${day.days}日分）", if (day.days == 0) "データなし" else "掲載${day.listedMeals}食・選択食${day.choices}食")
                                        StatisticsBar(day.listedMeals.toFloat() / maximum)
                                    }
                                } }
                                item {
                                    Text("組み合わせランキング", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    Text("選択料理＋共通料理のセット。A/Bは別候補、同じ1食内の同一セットは1回として数えます。割合の分母は掲載食数です。", fontSize = 11.sp, lineHeight = 18.sp)
                                }
                                item { OutlinedTextField(query, { query = it }, label = { Text("含まれる料理で検索") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { dismissSearch() }),
                                    modifier = Modifier.fillMaxWidth().testTag("statistics-search"), shape = RoundedCornerShape(12.dp)) }
                                val found = data.patterns.withIndex().filter { item -> item.value.dishes.any { it.contains(statisticsDishKey(query), true) } }
                                if (found.isEmpty()) item { Text("一致する献立がありません。", fontSize = 13.sp) }
                                items(found.take(shown), key = { "pattern-${it.index}" }) { indexed ->
                                    val pattern = indexed.value
                                    StatisticsRankingRow(indexed.index + 1, pattern.dishes.joinToString("・"), pattern.count, data.listed.size) {
                                        dismissSearch()
                                        detail = StatisticsDetail(pattern.dishes.joinToString("・"), pattern.appearances)
                                    }
                                }
                                if (shown < found.size) item { MoreStatistics(shown, found.size) { shown += 20 } }
                            }
                            StatisticsTab.NUTRITION -> {
                                item { Text("選択肢別の栄養分布", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("A/Bをそれぞれ1候補として集計した献立の予定量です。共通料理込みの値を1回使い、実際の摂取量とは区別します。", fontSize = 11.sp, lineHeight = 18.sp) }
                                items(Nutrient.entries) { item ->
                                    val distribution = data.nutrients.getValue(item)
                                    ReportCard {
                                        Text(item.label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text(distribution.average?.let { formatAmount(it, item) + " " + item.unit } ?: "—", fontSize = 27.sp,
                                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        StatisticLine("1候補あたり平均", "${distribution.known}/${distribution.expected}候補を集計")
                                        StatisticLine("最小 ～ 最大", if (distribution.minimum == null || distribution.maximum == null) "—" else
                                            "${formatAmount(distribution.minimum, item)} ～ ${formatAmount(distribution.maximum, item)} ${item.unit}")
                                    }
                                }
                                item { SelectionControl(Nutrient.entries, nutrient, label = { when(it) { Nutrient.ENERGY -> "熱量"; Nutrient.SALT -> "食塩"; else -> it.label } }, onSelect = { nutrient = it }) }
                                item { ReportCard {
                                    Text("朝昼夕の平均${nutrient.label}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    MealKind.entries.filter { kind == null || it == kind }.forEach { mealKind ->
                                        val distribution = nutrientDistribution(data.samples.filter { it.kind == mealKind }.map { nutrient.read(it.option.nutrition) })
                                        StatisticLine(mealKind.label, distribution.average?.let { "${formatAmount(it, nutrient)} ${nutrient.unit}（${distribution.known}候補）" } ?: "未集計")
                                    }
                                } }
                                val sorted = data.samples.filter { nutrient.read(it.option.nutrition) != null }.sortedBy { nutrient.read(it.option.nutrition) }
                                if (sorted.isNotEmpty()) {
                                    item { ExtremeNutritionCard("最大値の献立", sorted.last(), nutrient) }
                                    item { ExtremeNutritionCard("最小値の献立", sorted.first(), nutrient) }
                                    item { Text("同じ値がある場合は代表の1件を表示します。不明な値は平均や最大・最小から除外します。", fontSize = 10.sp, lineHeight = 17.sp) }
                                }
                            }
                            StatisticsTab.RECORDS -> {
                                item { ReportCard {
                                    Text("選択・欠食の記録", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("献立の掲載回数と、自分の選択・既定による集計を分けて確認できます。実際に食べた量を測った記録ではありません。", fontSize = 11.sp, lineHeight = 18.sp)
                                    StatisticLine("明示的にAを選択", "${data.explicitA}食")
                                    StatisticLine("明示的にBを選択", "${data.explicitB}食")
                                    StatisticLine("朝食のデフォルトを反映", "${data.defaults}食")
                                    StatisticLine("固定献立を反映", "${data.fixed}食")
                                    StatisticLine("まだ選んでいない食事", "${data.unselected}食")
                                    StatisticLine("欠食を記録", "${data.skipped}食・${percent(data.skipped, data.listed.size)}")
                                    StatisticsBar(if (data.listed.isEmpty()) 0f else data.skipped.toFloat() / data.listed.size)
                                    Text("割合は対象期間の掲載食数が分母です。記録がない食事を欠食とは数えません。", fontSize = 10.sp, lineHeight = 17.sp)
                                } }
                                item { ReportCard {
                                    Text("選択・既定を反映した栄養", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Nutrient.entries.forEach { item ->
                                        val values = data.listed.mapNotNull { it.meal.nutrition?.let(item.read) }
                                        val total = values.fold(java.math.BigDecimal.ZERO) { sum, value -> sum.add(java.math.BigDecimal.valueOf(value)) }.toDouble()
                                        StatisticLine("総${item.label}", if (values.isEmpty()) "未集計" else "${formatAmount(total, item)} ${item.unit}")
                                        Text("${values.size}/${data.listed.size}食を集計（明示した欠食は0、未選択・不明は除外）", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } }
                                if (data.unmatchedRecords > 0) item { Text("取得済みの全データと対応を確認できない保存記録：${data.unmatchedRecords}件。未取得の週や旧版等の可能性があり、上の集計には含めていません。", fontSize = 11.sp, lineHeight = 18.sp) }
                                item { Text("自分で記録した選択・欠食", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                                if (data.records.isEmpty()) item { Text("この範囲には、明示的な選択・欠食の記録がありません。", fontSize = 13.sp) }
                                items(data.records.take(shown), key = { "record-${it.date}-${it.kind}" }) { entry ->
                                    ReportCard { StatisticLine("${entry.date.format(StatisticDate)} ${entry.kind.label}",
                                        if (entry.meal.selectedOption == "skip") "欠食" else "${entry.meal.selectedOption}を選択") }
                                }
                                if (shown < data.records.size) item { MoreStatistics(shown, data.records.size) { shown += 20 } }
                            }
                        }
                    }
                }
            }
            if (uiState is MenuState.Loading || (uiState is MenuState.Success && (uiState as MenuState.Success).allMenus.isNotEmpty() && snapshot == null)) {
                CenteredLoadingStatus("全期間の献立を集計しています…")
            }
        }
        detail?.let { selected -> StatisticsDetailDialog(selected) { detail = null } }
    }
}

@Composable
private fun StatisticsTile(label: String, value: String, unit: String, modifier: Modifier) {
    ReportCard(modifier) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(unit, fontSize = 10.sp)
    }
}

@Composable
private fun StatisticLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatisticsBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .09f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun StatisticsRankingRow(rank: Int, name: String, count: Int, meals: Int, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(13.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(rank.toString(), fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(name, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                StatisticsBar(if (meals == 0) 0f else count.toFloat() / meals)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${count}回", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(percent(count, meals), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MoreStatistics(shown: Int, total: Int, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Text("さらに20件表示（残り${total - shown}件）", fontSize = 12.sp)
    }
}

@Composable
private fun ExtremeNutritionCard(title: String, sample: NutritionSample, nutrient: Nutrient) {
    ReportCard {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("${formatAmount(nutrient.read(sample.option.nutrition)!!, nutrient)} ${nutrient.unit}", fontSize = 24.sp,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("${sample.date.format(StatisticDate)} ${sample.kind.label} ${if (sample.option.id == "standard") "固定献立" else sample.option.id}", fontSize = 12.sp)
        Text(sample.dishes.joinToString("・"), fontSize = 12.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun StatisticsDetailDialog(detail: StatisticsDetail, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(24.dp).widthIn(max = 460.dp).fillMaxWidth().fillMaxHeight(.82f).testTag("statistics-detail"),
            color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(detail.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "統計詳細を閉じる" }) { Text("×", fontSize = 25.sp) }
                }
                Text("${detail.appearances.size}回登場・${detail.appearances.map { it.date }.distinct().size}日", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                val dates = detail.appearances.map { it.date }
                Text("初登場 ${dates.minOrNull()?.format(StatisticDate)} ／ 最終 ${dates.maxOrNull()?.format(StatisticDate)}", fontSize = 11.sp)
                Text(MealKind.entries.joinToString("　") { kind -> "${kind.label} ${detail.appearances.count { it.kind == kind }}回" }, fontSize = 11.sp)
                detail.selectedCount?.let { Text("選択・既定・固定献立への反映：${it}回（摂取実績ではありません）", fontSize = 10.sp) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .15f))
                Text("登場した日と食事", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(detail.appearances.sortedWith(compareByDescending<DishAppearance> { it.date }.thenBy { it.kind.ordinal })) { appearance ->
                        StatisticLine("${appearance.date.format(StatisticDate)} ${appearance.kind.label}", appearance.labels.joinToString("・"))
                    }
                }
            }
        }
    }
}
