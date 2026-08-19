package com.mimo.tracker.data.model

import com.mimo.tracker.data.api.DailyDelta

/**
 * Data models for MiMo Platform usage data.
 */
data class UsageData(
    // ── Token usage (from /api/v1/usage) ──
    val totalCost: String = "¥0.00",
    val tokenHistory: Long = 0,
    val inputCached: Long = 0,
    val inputUncached: Long = 0,
    val output: Long = 0,
    val requestCount: Long = 0,

    // ── Subscription plan (from /api/v1/tokenPlan/detail + usage) ──
    val planName: String = "",
    val creditsUsed: Long = 0,
    val creditsTotal: Long = 0,
    val usagePercentage: Double = 0.0,
    val expireDate: String = "",

    // ── Account balance (from /api/v1/balance) ──
    val totalBalance: String = "0.00",
    val giftBalance: String = "0.00",
    val cashBalance: String = "0.00",

    // ── Charts ──
    val monthlyUsage: List<MonthlyUsage> = emptyList(),
    val modelUsage: List<ModelUsage> = emptyList(),
    // Daily deltas (computed from local snapshots)
    val dailyDeltas: List<DailyDelta> = emptyList()
)

data class MonthlyUsage(
    val month: String,         // "2026-08"
    val inputToken: Long,
    val outputToken: Long,
    val totalToken: Long,
    val cacheToken: Long,
    val requestCount: Long
)

data class ModelUsage(
    val modelName: String,
    val inputToken: Long,
    val outputToken: Long,
    val totalToken: Long,
    val cacheToken: Long,
    val requestCount: Long,
    val percentage: Double = 0.0
)

// ── Daily API data ──

data class DailyDataPoint(
    val dayKey: String,        // "08-17"
    val inputToken: Long,
    val outputToken: Long,
    val totalToken: Long,
    val cacheToken: Long,
    val requestCount: Long,
    val models: List<DayModelData> = emptyList()
) {
    val uncachedInput: Long get() = (inputToken - cacheToken).coerceAtLeast(0)
    val cacheHitRate: Double get() = if (inputToken > 0) cacheToken.toDouble() / inputToken else 0.0
}

data class DayModelData(
    val name: String,
    val data: ModelDayData
)

data class ModelDayData(
    val inputToken: Long = 0,
    val outputToken: Long = 0,
    val totalToken: Long = 0,
    val cacheToken: Long = 0,
    val requestCount: Long = 0
) {
    val uncachedInput: Long get() = (inputToken - cacheToken).coerceAtLeast(0)
}
