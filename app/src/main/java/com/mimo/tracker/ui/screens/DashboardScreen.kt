package com.mimo.tracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.tracker.data.api.DailyDelta
import com.mimo.tracker.data.api.ModelDelta
import com.mimo.tracker.data.model.ModelUsage
import com.mimo.tracker.data.model.MonthlyUsage
import com.mimo.tracker.data.model.UsageData
import com.mimo.tracker.ui.DashboardState
import com.mimo.tracker.ui.theme.*

@Composable
fun DashboardScreen(
    state: DashboardState,
    onRefresh: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .background(SurfaceLight)
    ) {
        // ── Header with refresh ──
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
                Text(
                    text = "MiMo Tracker",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (state.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.Refresh,
                            "刷新",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Error
        state.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MiMoRed.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MiMoRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = error, color = MiMoRed, fontSize = 14.sp)
                }
            }
        }

        // ── Content ──
        state.usageData?.let { data ->
            // ═══════════════════════════════════════
            //  Section 1: 订阅套餐
            // ═══════════════════════════════════════
            SectionHeader(
                icon = Icons.Default.CardMembership,
                title = "订阅套餐",
                subtitle = data.planName.ifEmpty { "Token Plan" }
            )

            PlanOverviewCard(data)

            // Model usage chart for plan
            if (data.dailyDeltas.isNotEmpty()) {
                ModelBreakdownCard(data.dailyDeltas, data.planName, data.creditsUsed, data.creditsTotal)
            } else if (data.modelUsage.isNotEmpty()) {
                ModelBreakdownCardFallback(data.modelUsage, data.tokenHistory)
            }

            // ═══════════════════════════════════════
            //  Section 2: 账户账单
            // ═══════════════════════════════════════
            SectionHeader(
                icon = Icons.Default.Receipt,
                title = "账户账单",
                subtitle = "余额与消费"
            )

            BalanceCard(data)
            TokenUsageSummary(data)

            // Daily usage chart (7-day bar chart)
            if (data.dailyDeltas.isNotEmpty()) {
                DailyUsageCard(data.dailyDeltas)
            }

            // Last refresh time
            if (state.lastRefreshTime.isNotEmpty()) {
                Text(
                    text = "最后更新: ${state.lastRefreshTime}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center,
                    color = TextHint,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Loading
        if (state.isLoading && state.usageData == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MiMoOrange)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在获取数据...", color = TextSecondary, fontSize = 14.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
//  Section Header
// ═══════════════════════════════════════════

@Composable
fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiMoOrange,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = TextSecondary
        )
    }
}

// ═══════════════════════════════════════════
//  Plan Overview Card (orange gradient)
// ═══════════════════════════════════════════

@Composable
fun PlanOverviewCard(data: UsageData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(MiMoOrange, MiMoOrangeLight)
                    )
                )
                .padding(20.dp)
        ) {
            // Plan name
            Text(
                text = "${data.planName.ifEmpty { "Token Plan" }} 月度套餐",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Credits used / total
            Text(
                text = "${formatCredits(data.creditsUsed)} / ${formatCredits(data.creditsTotal)}",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Credits 已使用 ${String.format("%.1f", data.usagePercentage)}%",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((data.usagePercentage / 100).toFloat().coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expiry
            if (data.expireDate.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("到期时间", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text(
                        data.expireDate,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
//  Account Balance Card (余额)
// ═══════════════════════════════════════════

@Composable
fun BalanceCard(data: UsageData) {
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
                Text(
                    text = "账户余额",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MiMoOrange,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Total balance (large)
            Text(
                text = "¥${data.totalBalance}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BalanceItem(
                    label = "赠送余额",
                    value = "¥${data.giftBalance}",
                    color = MiMoGreen
                )
                BalanceItem(
                    label = "现金余额",
                    value = "¥${data.cashBalance}",
                    color = MiMoBlue
                )
            }
        }
    }
}

@Composable
private fun BalanceItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

// ═══════════════════════════════════════════
//  Token Usage Summary
// ═══════════════════════════════════════════

@Composable
fun TokenUsageSummary(data: UsageData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Token 用量概览",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))

            UsageStatItem(
                icon = Icons.Default.Token,
                label = "总消耗",
                value = "${formatLargeNumber(data.tokenHistory)} Tokens",
                color = MiMoBlue
            )
            UsageStatItem(
                icon = Icons.Default.Cached,
                label = "输入 (命中缓存)",
                value = formatLargeNumber(data.inputCached),
                color = MiMoGreen
            )
            UsageStatItem(
                icon = Icons.Default.Input,
                label = "输入 (未命中缓存)",
                value = formatLargeNumber(data.inputUncached),
                color = MiMoYellow
            )
            UsageStatItem(
                icon = Icons.Default.Output,
                label = "输出",
                value = formatLargeNumber(data.output),
                color = MiMoPurple
            )
            UsageStatItem(
                icon = Icons.Default.RequestQuote,
                label = "请求次数",
                value = "${data.requestCount} 次",
                color = MiMoOrange
            )
            UsageStatItem(
                icon = Icons.Default.AttachMoney,
                label = "累计消费",
                value = data.totalCost,
                color = MiMoRed,
                showDivider = false
            )
        }
    }
}

@Composable
fun UsageStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 48.dp),
                color = SurfaceLight,
                thickness = 0.5.dp
            )
        }
    }
}

// ═══════════════════════════════════════════
//  Daily Usage Bar Chart (7 days)
// ═══════════════════════════════════════════

@Composable
fun DailyUsageCard(deltas: List<DailyDelta>) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = animProgress,
        animationSpec = androidx.compose.animation.core.tween(600),
        label = "daily_bar_anim"
    )

    LaunchedEffect(deltas) {
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
                    Text("每日 Token 消耗", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
                    Text("近 ${deltas.size} 天", fontSize = 12.sp, color = TextHint)
                }
                Icon(Icons.Default.BarChart, null, tint = MiMoOrange, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(MiMoBlue, "总消耗")
                LegendDot(MiMoGreen, "输入")
                LegendDot(MiMoPurple, "输出")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bar chart
            val maxVal = deltas.maxOfOrNull { it.totalToken }?.toFloat()?.coerceAtLeast(1f) ?: 1f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    deltas.forEachIndexed { index, delta ->
                        val isSelected = selectedIndex == index

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedIndex = if (selectedIndex == index) null else index
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Stacked bars (input + output)
                            val inputHeight = (delta.inputToken / maxVal * 150f * animatedProgress)
                            val outputHeight = (delta.outputToken / maxVal * 150f * animatedProgress)

                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(inputHeight.dp.coerceAtLeast(2.dp)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(if (isSelected) MiMoGreen else MiMoGreen.copy(alpha = 0.7f))
                                )
                                // Output on top
                                if (outputHeight > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(outputHeight.dp.coerceAtLeast(1.dp))
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(if (isSelected) MiMoPurple else MiMoPurple.copy(alpha = 0.7f))
                                            .align(Alignment.TopCenter)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Date label
                            Text(
                                text = delta.dateKey.takeLast(2),
                                fontSize = 10.sp,
                                color = if (isSelected) TextPrimary else TextHint,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Detailed tooltip when a bar is selected
            selectedIndex?.let { idx ->
                if (idx < deltas.size) {
                    val d = deltas[idx]
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    d.dateKey,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Icon(
                                    Icons.Default.Close,
                                    "关闭",
                                    tint = TextHint,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { selectedIndex = null }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // Summary
                            TooltipRow("总消耗", formatLargeNumber(d.totalToken))
                            TooltipRow("输入(缓存命中)", formatLargeNumber(d.cacheToken))
                            TooltipRow("输入(未命中)", formatLargeNumber(d.uncachedInput))
                            TooltipRow("输出", formatLargeNumber(d.outputToken))
                            TooltipRow("请求", "${d.requestCount} 次")
                            if (d.cost > 0) TooltipRow("消费", "¥${String.format("%.2f", d.cost)}")

                            // Per-model breakdown
                            if (d.models.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = SurfaceLight, thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("模型明细", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextSecondary)
                                d.models.forEach { m ->
                                    if (m.totalToken > 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(m.name, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TextPrimary)
                                        TooltipRow("  Token", formatLargeNumber(m.totalToken))
                                        TooltipRow("  缓存命中", formatLargeNumber(m.cacheToken))
                                        TooltipRow("  输出", formatLargeNumber(m.outputToken))
                                        TooltipRow("  请求", "${m.requestCount} 次")
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
//  Model Breakdown (7-day per-model stacked bar chart)
// ═══════════════════════════════════════════

@Composable
fun ModelBreakdownCard(
    dailyDeltas: List<DailyDelta>,
    planName: String,
    creditsUsed: Long,
    creditsTotal: Long
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = animProgress,
        animationSpec = androidx.compose.animation.core.tween(600),
        label = "model_daily_anim"
    )

    LaunchedEffect(dailyDeltas) {
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("每日模型用量", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
                    Text("近 ${dailyDeltas.size} 天 · 点击查看详情", fontSize = 12.sp, color = TextHint)
                }
                Icon(Icons.Default.Analytics, null, tint = MiMoOrange, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(MiMoGreen, "缓存命中")
                LegendDot(MiMoYellow, "未命中")
                LegendDot(MiMoPurple, "输出")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stacked bar chart
            val maxVal = dailyDeltas.maxOfOrNull {
                it.inputToken + it.outputToken
            }?.toFloat()?.coerceAtLeast(1f) ?: 1f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    dailyDeltas.forEachIndexed { index, delta ->
                        val isSelected = selectedIndex == index

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedIndex = if (selectedIndex == index) null else index
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Three-segment stacked bar: output(bottom) + cacheMiss + cacheHit(top)
                            val cacheH = (delta.cacheToken / maxVal * 150f * animatedProgress)
                            val missH = (delta.uncachedInput / maxVal * 150f * animatedProgress)
                            val outH = (delta.outputToken / maxVal * 150f * animatedProgress)
                            val totalH = cacheH + missH + outH

                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(totalH.dp.coerceAtLeast(2.dp))
                            ) {
                                var bottomOffset = 0f

                                // Bottom segment: Output
                                if (outH > 0.5f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(outH.dp.coerceAtLeast(1.dp))
                                            .align(Alignment.BottomStart)
                                            .background(if (isSelected) MiMoPurple else MiMoPurple.copy(alpha = 0.7f))
                                    )
                                    bottomOffset += outH
                                }

                                // Middle segment: Cache miss
                                if (missH > 0.5f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(missH.dp.coerceAtLeast(1.dp))
                                            .align(Alignment.BottomStart)
                                            .offset(y = (-bottomOffset).dp)
                                            .background(if (isSelected) MiMoYellow else MiMoYellow.copy(alpha = 0.7f))
                                    )
                                    bottomOffset += missH
                                }

                                // Top segment: Cache hit
                                if (cacheH > 0.5f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(cacheH.dp.coerceAtLeast(1.dp))
                                            .align(Alignment.BottomStart)
                                            .offset(y = (-bottomOffset).dp)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(if (isSelected) MiMoGreen else MiMoGreen.copy(alpha = 0.7f))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Date label
                            Text(
                                text = delta.dateKey.takeLast(2),
                                fontSize = 10.sp,
                                color = if (isSelected) TextPrimary else TextHint,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Detail panel when a day is selected
            selectedIndex?.let { idx ->
                if (idx < dailyDeltas.size) {
                    val d = dailyDeltas[idx]
                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    d.dateKey,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Icon(
                                    Icons.Default.Close,
                                    "关闭",
                                    tint = TextHint,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { selectedIndex = null }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // Daily summary
                            val totalDay = d.inputToken + d.outputToken
                            TooltipRow("总消耗", formatLargeNumber(totalDay))
                            TooltipRow("缓存命中", "${formatLargeNumber(d.cacheToken)} (${if (d.inputToken > 0) String.format("%.1f%%", d.cacheToken * 100.0 / d.inputToken) else "0%"})")
                            TooltipRow("未命中", formatLargeNumber(d.uncachedInput))
                            TooltipRow("输出", formatLargeNumber(d.outputToken))
                            TooltipRow("请求", "${d.requestCount} 次")
                            if (d.cost > 0) TooltipRow("消费", "¥${String.format("%.2f", d.cost)}")

                            // Per-model breakdown
                            if (d.models.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = SurfaceLight, thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(8.dp))

                                Text("模型明细", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))

                                d.models.filter { it.totalToken > 0 }.forEach { m ->
                                    // Model name
                                    Text(
                                        m.name,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    // Horizontal composition bar for this model
                                    val modelMax = m.inputToken + m.outputToken
                                    if (modelMax > 0) {
                                        val cacheFrac = (m.cacheToken.toFloat() / modelMax).coerceIn(0f, 1f)
                                        val missFrac = ((m.inputToken - m.cacheToken).coerceAtLeast(0).toFloat() / modelMax).coerceIn(0f, 1f - cacheFrac)
                                        val outFrac = 1f - cacheFrac - missFrac

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(SurfaceLight)
                                        ) {
                                            // Cache hit (green, left)
                                            if (cacheFrac > 0.01f) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(cacheFrac)
                                                        .background(MiMoGreen)
                                                )
                                            }
                                            // Cache miss (yellow, middle)
                                            if (missFrac > 0.01f) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(missFrac)
                                                        .align(Alignment.CenterStart)
                                                        .offset(x = (cacheFrac * 100).toInt().dp)
                                                        .background(MiMoYellow)
                                                )
                                            }
                                            // Output (purple, right)
                                            if (outFrac > 0.01f) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(outFrac)
                                                        .align(Alignment.CenterEnd)
                                                        .background(MiMoPurple)
                                                )
                                            }
                                        }
                                    }
                                    // Numbers
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("缓存 ${formatLargeNumber(m.cacheToken)}", fontSize = 11.sp, color = MiMoGreen)
                                        Text("未命中 ${formatLargeNumber((m.inputToken - m.cacheToken).coerceAtLeast(0))}", fontSize = 11.sp, color = MiMoYellow)
                                        Text("输出 ${formatLargeNumber(m.outputToken)}", fontSize = 11.sp, color = MiMoPurple)
                                    }
                                    Text(
                                        "${m.requestCount} 次请求",
                                        fontSize = 11.sp,
                                        color = TextHint
                                    )
                                }
                            }

                            // Note about subscription vs balance
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = SurfaceLight, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            if (planName.isNotEmpty()) {
                                Text(
                                    "订阅套餐: $planName · ${formatCredits(creditsUsed)} / ${formatCredits(creditsTotal)}",
                                    fontSize = 11.sp,
                                    color = TextHint
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
//  Model Breakdown Fallback (monthly aggregate, no daily data)
// ═══════════════════════════════════════════

@Composable
fun ModelBreakdownCardFallback(models: List<ModelUsage>, totalTokens: Long) {
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
            Text("模型用量分布 (月度)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
            Text("每日刷新后可查看7天趋势", fontSize = 12.sp, color = TextHint)
            Spacer(modifier = Modifier.height(16.dp))

            val maxTokens = models.maxOfOrNull { it.totalToken }?.toFloat() ?: 1f

            models.forEachIndexed { index, model ->
                val color = chartColors[index % chartColors.size]
                val isSelected = selectedIndex == index

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(model.modelName, fontSize = 13.sp, color = if (isSelected) TextPrimary else TextSecondary)
                    }
                    Text("${formatLargeNumber(model.totalToken)} Tokens", fontSize = 13.sp, color = TextPrimary)
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
                            .fillMaxWidth((model.totalToken.toFloat() / maxTokens).coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) color else color.copy(alpha = 0.7f))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            selectedIndex?.let { idx ->
                if (idx < models.size) {
                    val m = models[idx]
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(m.modelName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            TooltipRow("输入", formatLargeNumber(m.inputToken))
                            TooltipRow("输出", formatLargeNumber(m.outputToken))
                            TooltipRow("缓存", formatLargeNumber(m.cacheToken))
                            TooltipRow("请求", "${m.requestCount} 次")
                            TooltipRow("占比", "${String.format("%.1f", m.percentage)}%")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TooltipRow(label: String, value: String, hint: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp, color = TextSecondary)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        }
        if (hint != null) {
            Text(hint, fontSize = 10.sp, color = TextHint)
        }
    }
}

// ═══════════════════════════════════════════
//  Monthly Trend (line chart with touch tooltip)
// ═══════════════════════════════════════════

@Composable
fun MonthlyTrendCard(months: List<MonthlyUsage>) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = animProgress,
        animationSpec = androidx.compose.animation.core.tween(800),
        label = "trend_anim"
    )

    LaunchedEffect(months) {
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
                    Text("Token 消耗趋势", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
                    Text("按月统计", fontSize = 12.sp, color = TextHint)
                }
                Icon(Icons.Default.ShowChart, null, tint = MiMoOrange, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendDot(MiMoBlue, "总消耗")
                LegendDot(MiMoGreen, "输入")
                LegendDot(MiMoPurple, "输出")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chart area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(months) {
                            detectTapGestures { offset ->
                                if (months.isNotEmpty()) {
                                    val stepX = size.width / (months.size - 1).coerceAtLeast(1)
                                    val idx = ((offset.x + stepX / 2) / stepX).toInt()
                                        .coerceIn(0, months.size - 1)
                                    selectedIndex = if (selectedIndex == idx) null else idx
                                }
                            }
                        }
                ) {
                    if (months.isEmpty()) return@Canvas

                    val maxVal = months.maxOf { it.totalToken }.toFloat()
                    if (maxVal == 0f) return@Canvas

                    val chartH = size.height - 30f
                    val stepX = if (months.size > 1) size.width / (months.size - 1) else size.width

                    // Draw grid lines
                    for (i in 0..4) {
                        val y = chartH * i / 4
                        drawLine(
                            Color(0xFFEEEEEE),
                            Offset(0f, y),
                            Offset(size.width, y),
                            strokeWidth = 1f
                        )
                    }

                    // Helper to draw a line series
                    fun drawSeries(values: List<Long>, color: Color) {
                        if (values.size < 2) return
                        val path = Path()
                        values.forEachIndexed { i, v ->
                            val x = i * stepX
                            val y = chartH - (v / maxVal * chartH * animatedProgress)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color, style = Stroke(width = 3f))

                        // Points
                        values.forEachIndexed { i, v ->
                            val x = i * stepX
                            val y = chartH - (v / maxVal * chartH * animatedProgress)
                            drawCircle(color, 5f, Offset(x, y))
                            drawCircle(Color.White, 3f, Offset(x, y))
                        }
                    }

                    // Draw three lines
                    drawSeries(months.map { it.totalToken }, MiMoBlue)
                    drawSeries(months.map { it.inputToken }, MiMoGreen)
                    drawSeries(months.map { it.outputToken }, MiMoPurple)

                    // Month labels
                    months.forEachIndexed { i, m ->
                        val x = i * stepX
                        drawContext.canvas.nativeCanvas.drawText(
                            m.month.takeLast(2) + "月",
                            x,
                            size.height - 2f,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor("#9CA3AF")
                                textSize = 28f
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                        )
                    }
                }

                // Tooltip overlay (tap to dismiss)
                selectedIndex?.let { idx ->
                    if (idx < months.size) {
                        val m = months[idx]
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clickable { selectedIndex = null },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black.copy(alpha = 0.85f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        m.month,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "关闭",
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { selectedIndex = null }
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                TooltipWhite("总消耗", formatLargeNumber(m.totalToken))
                                TooltipWhite("输入", formatLargeNumber(m.inputToken))
                                TooltipWhite("缓存", formatLargeNumber(m.cacheToken))
                                TooltipWhite("输出", formatLargeNumber(m.outputToken))
                                TooltipWhite("请求", "${m.requestCount} 次")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun TooltipWhite(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

// ═══════════════════════════════════════════
//  Format helpers
// ═══════════════════════════════════════════

fun formatNumber(number: Long): String {
    return java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(number)
}

fun formatCredits(number: Long): String {
    return when {
        number >= 1_000_000_000 -> String.format("%.1fB", number / 1_000_000_000.0)
        number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
        number >= 1_000 -> String.format("%.1fK", number / 1_000.0)
        else -> number.toString()
    }
}

fun formatLargeNumber(number: Long): String {
    return when {
        number >= 1_000_000_000 -> String.format("%.2fB", number / 1_000_000_000.0)
        number >= 1_000_000 -> String.format("%.2fM", number / 1_000_000.0)
        number >= 1_000 -> String.format("%.1fK", number / 1_000.0)
        else -> number.toString()
    }
}
