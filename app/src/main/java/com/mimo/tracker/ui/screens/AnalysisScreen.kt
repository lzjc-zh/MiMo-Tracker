package com.mimo.tracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.tracker.data.model.DailyDataPoint
import com.mimo.tracker.ui.theme.*

@Composable
fun AnalysisScreen(
    dailyData7d: List<DailyDataPoint>,
    dailyData30d: List<DailyDataPoint>,
    isLoading: Boolean,
    onRefresh: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .background(SurfaceLight)
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MiMoOrange,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("数据分析", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        if (dailyData7d.isEmpty() && !isLoading) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxWidth().padding(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Analytics, null, modifier = Modifier.size(48.dp), tint = TextHint)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("暂无每日数据", fontSize = 16.sp, color = TextSecondary)
                    Text("刷新后自动获取近7天和30天趋势", fontSize = 13.sp, color = TextHint)
                }
            }
        } else {
            // 7-day trend with per-model breakdown
            if (dailyData7d.isNotEmpty()) {
                DailyTrendCard(dailyData7d, "近 7 天趋势", "7d")
            }

            // 30-day trend
            if (dailyData30d.isNotEmpty()) {
                DailyTrendCard(dailyData30d, "近 30 天趋势", "30d")
            }

            // Model comparison (7-day)
            if (dailyData7d.isNotEmpty()) {
                ModelComparisonCard(dailyData7d)
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// ═══════════════════════════════════════════
//  Daily Trend Card (reusable for 7d and 30d)
// ═══════════════════════════════════════════

@Composable
fun DailyTrendCard(data: List<DailyDataPoint>, title: String, tag: String) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = animProgress,
        animationSpec = androidx.compose.animation.core.tween(600),
        label = "trend_anim_$tag"
    )

    LaunchedEffect(data) {
        animProgress = 0f
        animProgress = 1f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
                    Text("${data.size} 天", fontSize = 12.sp, color = TextHint)
                }
                Icon(Icons.Default.TrendingUp, null, tint = MiMoOrange, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(MiMoGreen, "缓存命中")
                LegendDot(MiMoYellow, "未命中")
                LegendDot(MiMoPurple, "输出")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chart
            val maxVal = data.maxOfOrNull { it.inputToken + it.outputToken }?.toFloat()?.coerceAtLeast(1f) ?: 1f
            val barWidth = if (tag == "30d") 10 else 24

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    data.forEachIndexed { index, point ->
                        val isSelected = selectedIndex == index

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedIndex = if (selectedIndex == index) null else index },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val cacheH = (point.cacheToken / maxVal * 170f * animatedProgress)
                            val missH = (point.uncachedInput / maxVal * 170f * animatedProgress)
                            val outH = (point.outputToken / maxVal * 170f * animatedProgress)
                            val totalH = cacheH + missH + outH

                            Box(
                                modifier = Modifier
                                    .width(barWidth.dp)
                                    .height(totalH.dp.coerceAtLeast(2.dp))
                            ) {
                                var bottom = 0f

                                if (outH > 0.5f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(outH.dp.coerceAtLeast(1.dp))
                                            .align(Alignment.BottomStart)
                                            .background(if (isSelected) MiMoPurple else MiMoPurple.copy(alpha = 0.7f))
                                    )
                                    bottom += outH
                                }
                                if (missH > 0.5f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(missH.dp.coerceAtLeast(1.dp))
                                            .align(Alignment.BottomStart)
                                            .offset(y = (-bottom).dp)
                                            .background(if (isSelected) MiMoYellow else MiMoYellow.copy(alpha = 0.7f))
                                    )
                                    bottom += missH
                                }
                                if (cacheH > 0.5f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(cacheH.dp.coerceAtLeast(1.dp))
                                            .align(Alignment.BottomStart)
                                            .offset(y = (-bottom).dp)
                                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                            .background(if (isSelected) MiMoGreen else MiMoGreen.copy(alpha = 0.7f))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Date label (show fewer labels for30d)
                            val showLabel = tag == "7d" || index % 3 == 0 || index == data.size - 1
                            if (showLabel) {
                                Text(
                                    text = point.dayKey.takeLast(2),
                                    fontSize = if (tag == "30d") 8.sp else 10.sp,
                                    color = if (isSelected) TextPrimary else TextHint,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            } else {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }

            // Tooltip
            selectedIndex?.let { idx ->
                if (idx < data.size) {
                    val d = data[idx]
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(d.dayKey, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                Icon(Icons.Default.Close, "关闭", tint = TextHint,
                                    modifier = Modifier.size(16.dp).clickable { selectedIndex = null })
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            TooltipRow("总消耗", formatLargeNumber(d.totalToken))
                            TooltipRow("缓存命中", "${formatLargeNumber(d.cacheToken)} (${String.format("%.1f%%", d.cacheHitRate * 100)})")
                            TooltipRow("未命中", formatLargeNumber(d.uncachedInput))
                            TooltipRow("输出", formatLargeNumber(d.outputToken))
                            TooltipRow("请求", "${d.requestCount} 次")

                            // Per-model
                            if (d.models.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = SurfaceLight, thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("模型明细", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextSecondary)
                                d.models.forEach { m ->
                                    if (m.data.totalToken > 0) {
                                        Text(m.name, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TextPrimary,
                                            modifier = Modifier.padding(top = 4.dp))
                                        TooltipRow("  Token", formatLargeNumber(m.data.totalToken))
                                        TooltipRow("  缓存", formatLargeNumber(m.data.cacheToken))
                                        TooltipRow("  输出", formatLargeNumber(m.data.outputToken))
                                        TooltipRow("  请求", "${m.data.requestCount} 次")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
//  Model Comparison Card (7-day totals per model)
// ═══════════════════════════════════════════

@Composable
fun ModelComparisonCard(data: List<DailyDataPoint>) {
    // Aggregate per-model totals from daily data
    val modelTotals = mutableMapOf<String, com.mimo.tracker.data.model.ModelDayData>()
    data.forEach { point ->
        point.models.forEach { m ->
            val existing = modelTotals.getOrDefault(m.name, com.mimo.tracker.data.model.ModelDayData())
            modelTotals[m.name] = existing.copy(
                inputToken = existing.inputToken + m.data.inputToken,
                outputToken = existing.outputToken + m.data.outputToken,
                totalToken = existing.totalToken + m.data.totalToken,
                cacheToken = existing.cacheToken + m.data.cacheToken,
                requestCount = existing.requestCount + m.data.requestCount
            )
        }
    }

    if (modelTotals.isEmpty()) return

    val chartColors = listOf(
        Color(0xFFFF6B00), Color(0xFF4C87FF), Color(0xFF00C853),
        Color(0xFF7C4DFF), Color(0xFFFFD740),
    )

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("模型对比 (${data.size}天)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(16.dp))

            val models = modelTotals.entries.toList()
            val maxTokens = models.maxOfOrNull { it.value.totalToken }?.toFloat() ?: 1f

            models.forEachIndexed { index, (name, total) ->
                val color = chartColors[index % chartColors.size]
                val isSelected = selectedIndex == index

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(name, fontSize = 13.sp, color = if (isSelected) TextPrimary else TextSecondary)
                    }
                    Text(formatLargeNumber(total.totalToken), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SurfaceLight)
                        .clickable { selectedIndex = if (selectedIndex == index) null else index }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((total.totalToken / maxTokens).coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) color else color.copy(alpha = 0.7f))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Detail
                if (isSelected) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            TooltipRow("输入(缓存)", formatLargeNumber(total.cacheToken))
                            TooltipRow("输入(未命中)", formatLargeNumber(total.uncachedInput))
                            TooltipRow("输出", formatLargeNumber(total.outputToken))
                            TooltipRow("请求", "${total.requestCount} 次")
                            TooltipRow("缓存命中率", if (total.inputToken > 0)
                                String.format("%.1f%%", total.cacheToken * 100.0 / total.inputToken) else "0%",
                                hint = "缓存Token ÷ 输入Token")
                        }
                    }
                }
            }
        }
    }
}

// Reuse helpers from DashboardScreen
@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}
