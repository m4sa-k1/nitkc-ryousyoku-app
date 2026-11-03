package com.m4sak1.ryousyoku.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m4sak1.ryousyoku.model.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.math.BigDecimal

private val Japanese = Locale.JAPAN
private val DateLabel = DateTimeFormatter.ofPattern("M月d日 E曜日", Japanese)
private val ShortDate = DateTimeFormatter.ofPattern("M/d", Japanese)
private val Weekday = DateTimeFormatter.ofPattern("E", Japanese)

@Composable
fun NutritionIcon() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(24.dp)) {
        val width = size.width / 6
        listOf(0.45f, 0.85f, 0.65f).forEachIndexed { i, height ->
            drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(width * (i * 2), size.height * (1 - height)),
                size = androidx.compose.ui.geometry.Size(width, size.height * height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        }
    }
}

@Composable
fun StructuredMenuBlock(viewModel: MenuViewModel, onImage: () -> Unit) {
    val state by viewModel.structured.collectAsState()
    val selections by viewModel.selections.collectAsState()
    val breakfastDefault by viewModel.breakfastDefault.collectAsState()
    val preferredDate by viewModel.preferredDate.collectAsState()
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(14.dp)) {
        when (val ready = state) {
            is StructuredState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
            is StructuredState.Unavailable -> {
                Text("この週の文字データはまだ準備されていません。",
                    modifier = Modifier.padding(top = 20.dp), fontSize = 14.sp)
                TextButton(onClick = onImage) { Text("画像で確認する") }
            }
            is StructuredState.Ready -> {
                val data = ready.data
                var selectedDate by rememberSaveable(data.source.sha256) {
                    val today = LocalDate.now(ZoneId.of("Asia/Tokyo")).toString()
                    mutableStateOf(data.days.find { it.date == preferredDate }?.date ?: data.days.find { it.date == today }?.date ?: data.days.first().date)
                }
                LaunchedEffect(data.source.sha256, preferredDate) {
                    if (data.days.any { it.date == preferredDate }) selectedDate = preferredDate!!
                }
                if (ready.fromCache) Text("保存済みの献立を表示中", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    data.days.forEach { day ->
                        val date = LocalDate.parse(day.date)
                        val selected = day.date == selectedDate
                        Column(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(9.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))
                            .clickable { selectedDate = day.date }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            val color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                            Text(date.format(Weekday), color = color, fontSize = 10.sp, lineHeight = 12.sp)
                            Text(date.dayOfMonth.toString(), color = color, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                val day = data.days.first { it.date == selectedDate }
                Text(LocalDate.parse(day.date).format(DateLabel), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp))
                MealKind.entries.forEach { kind ->
                    val meal = day.meals.getValue(kind.key)
                    val explicit = selections[selectionKey(data, day.date, kind.key)]
                    val selected = effectiveSelection(meal, kind.key, explicit, breakfastDefault)
                    MealCard(kind, meal, selected,
                        isDefault = kind == MealKind.BREAKFAST && explicit == null && meal.options.size > 1 && selected != null) {
                        viewModel.selectOption(day.date, kind.key, it)
                    }
                }
                data.notices.forEach { Text(it, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    }
}

@Composable
private fun MealCard(kind: MealKind, meal: StructuredMeal, selected: String?, isDefault: Boolean = false, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(kind.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
            if (isDefault) Text("既定", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            if (meal.status == "listed") {
                TextButton(onClick = { onSelect("skip") }, modifier = Modifier.height(32.dp).semantics { this.selected = selected == "skip" },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text(if (selected == "skip") "欠食を記録済み" else "欠食を記録", fontSize = 11.sp,
                        color = if (selected == "skip") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (meal.status != "listed") {
            Text(if (meal.status == "not_served") "提供なし" else "献立の記載がありません", fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            if (meal.options.size > 1) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            meal.options.forEach { option ->
                val active = selected == option.id
                    Row(Modifier.weight(1f).fillMaxHeight().heightIn(min = 42.dp).padding(top = 4.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = .1f) else MaterialTheme.colorScheme.surface)
                        .border(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .2f), RoundedCornerShape(9.dp))
                        .clickable { onSelect(option.id) }.semantics { this.selected = active }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(option.id, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(20.dp))
                        Text(option.items.joinToString("・"), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                    }
            }
            }
            } else meal.options.forEach { DishList(it.items) }
            if (meal.commonItems.isNotEmpty()) {
                Text("共通の料理", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
                DishList(meal.commonItems)
            }
            meal.notes.forEach {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = .08f), RoundedCornerShape(8.dp)).padding(10.dp))
            }
        }
        HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .15f))
    }
}

@Composable
private fun DishList(items: List<String>) {
    Text(items.joinToString("・"), fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun NutritionScreen(viewModel: MenuViewModel) {
    val state by viewModel.structured.collectAsState()
    val menu by viewModel.selectedMenu.collectAsState()
    val archive by viewModel.archive.collectAsState()
    val selections by viewModel.selections.collectAsState()
    val breakfastDefault by viewModel.breakfastDefault.collectAsState()
    var plan by rememberSaveable { mutableStateOf(NutritionPlan.SELECTED) }
    var nutrient by rememberSaveable { mutableStateOf(Nutrient.ENERGY) }
    var showLifetime by rememberSaveable { mutableStateOf(false) }
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
        fontFamily = MPlusRoundedFontFamily, color = MaterialTheme.colorScheme.onBackground)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("栄養レポート", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .windowInsetsPadding(WindowInsets.navigationBars), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SelectionControl(listOf(false, true), showLifetime,
                    label = { if (it) "累計・書き出し" else "週間レポート" }, onSelect = { showLifetime = it })
                if (!showLifetime) menu?.let { Text(it.period, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (showLifetime) LifetimeSection(viewModel) else when (val current = state) {
                    is StructuredState.Loading -> Unit
                    is StructuredState.Unavailable -> ReportCard {
                        Text("この週の栄養データはまだ準備されていません。", fontSize = 14.sp)
                        Text("献立は画像・PDFで確認できます。", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    is StructuredState.Ready -> {
                        if (current.fromCache) Text("保存済みの献立から集計しています", fontSize = 12.sp)
                        Text("集計する献立", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        SelectionControl(NutritionPlan.entries, plan, label = { it.label }, onSelect = { plan = it })
                        Text(if (plan == NutritionPlan.SELECTED && breakfastDefault != BreakfastDefault.NONE)
                                "朝食の既定と日別の選択を反映した予定量です。実際の摂取量とは異なります。"
                             else if (plan == NutritionPlan.SELECTED) "選択した献立の予定量です。実際の摂取量とは異なります。"
                             else "すべての選択食で${if (plan == NutritionPlan.A) "A" else "B"}を選んだ場合の試算です。",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val week = summarizeWeek(current.data, selections, plan, breakfastDefault)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SummaryTile("カロリー", week, Nutrient.ENERGY, Modifier.weight(1f))
                            SummaryTile("食塩相当量", week, Nutrient.SALT, Modifier.weight(1f))
                        }
                        ReportCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${nutrient.label}の推移", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(nutrient.unit, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SelectionControl(Nutrient.entries, nutrient,
                                label = { when (it) { Nutrient.ENERGY -> "熱量"; Nutrient.SALT -> "食塩"; else -> it.label } },
                                onSelect = { nutrient = it })
                            WeeklyChart(week, nutrient)
                        }
                        ReportCard {
                            Text("日別の${nutrient.label}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            week.forEach { day ->
                                val value = day.totals.getValue(nutrient)
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(day.date.format(DateTimeFormatter.ofPattern("M/d（E）", Japanese)), fontSize = 13.sp)
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(if (value.knownMeals == 0) "未集計" else "${formatAmount(value.value, nutrient)} ${nutrient.unit}", fontWeight = FontWeight.Bold)
                                        if (!value.complete) Text("${value.knownMeals}/3食の部分合計", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .12f))
                            }
                        }
                        Text("原本にない栄養素は推定していません。未選択・記載なし・不明な値は0として数えません。",
                            fontSize = 11.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        if (if (showLifetime) archive is ArchiveState.Idle || archive is ArchiveState.Loading else state is StructuredState.Loading) {
            CenteredLoadingStatus(if (showLifetime) "全期間の献立を確認しています…" else "栄養データを読み込んでいます…")
        }
        }
    }
}

@Composable
fun ReportCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun SummaryTile(label: String, week: List<DayNutrition>, nutrient: Nutrient, modifier: Modifier) {
    val values = week.map { it.totals.getValue(nutrient) }
    val count = values.sumOf { it.knownMeals }
    val total = values.fold(BigDecimal.ZERO) { sum, v -> sum.add(BigDecimal.valueOf(v.value)) }.toDouble()
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp)) {
            Text("週間合計 · $label", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (count == 0) "—" else formatAmount(total, nutrient), fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
            Text(nutrient.unit, fontSize = 12.sp)
            Text(if (count == 21) "21食の予定量" else "$count/21食の部分合計", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun WeeklyChart(week: List<DayNutrition>, nutrient: Nutrient) {
    val maximum = week.maxOfOrNull { it.totals.getValue(nutrient).value }?.coerceAtLeast(1.0) ?: 1.0
    val color = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        week.forEach { day ->
            val value = day.totals.getValue(nutrient)
            Column(Modifier.weight(1f).semantics {
                contentDescription = "${day.date} ${nutrient.label} ${if (value.knownMeals == 0) "データなし" else formatAmount(value.value, nutrient) + nutrient.unit}、${value.knownMeals}食分"
            }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (value.knownMeals == 0) "—" else formatAmount(value.value, nutrient), fontSize = 10.sp, maxLines = 1,
                    fontWeight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 6.dp), contentAlignment = Alignment.BottomCenter) {
                    if (value.knownMeals > 0 && value.value > 0) {
                        Box(Modifier.fillMaxWidth().height((value.value / maximum * 110).toFloat().coerceAtLeast(2f).dp)
                            .background(color.copy(alpha = if (value.complete) 1f else .35f), RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
                    }
                }
                Text(day.date.format(Weekday), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Text(day.date.format(ShortDate), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text("薄い棒は一部の食事だけの合計です。", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

fun formatAmount(value: Double, nutrient: Nutrient) =
    if (nutrient == Nutrient.ENERGY) String.format(Japanese, "%,.0f", value) else String.format(Japanese, "%.1f", value)
