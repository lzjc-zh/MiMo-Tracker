package com.mimo.tracker.data.api

import android.content.Context
import android.util.Log
import com.mimo.tracker.data.model.DailyDataPoint
import com.mimo.tracker.data.model.DayModelData
import com.mimo.tracker.data.model.ModelDayData
import com.mimo.tracker.data.model.ModelUsage
import com.mimo.tracker.data.model.MonthlyUsage
import com.mimo.tracker.data.model.UsageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

private const val TAG = "DataScraper"
private const val BASE_URL = "https://platform.xiaomimimo.com"

/**
 * Fetches usage data from MiMo platform using API endpoints.
 *
 * Endpoints used:
 *   GET /api/v1/tokenPlan/detail     -> plan name, expiry, credits
 *   GET /api/v1/tokenPlan/usage      -> monthly credit usage & limit
 *   GET /api/v1/usage                -> overall token totals & cost
 *   GET /api/v1/usage/detail?year=YYYY -> monthly breakdown + per-model stats
 */
class DataScraper(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.SECONDS))
        .build()

    suspend fun scrapeUsageData(): Result<UsageData> = withContext(Dispatchers.IO) {
        try {
            val cookies = CookieManager.getCookies(context)
            if (cookies == null) {
                Log.e(TAG, "No cookies found")
                return@withContext Result.failure(Exception("未登录，请先登录"))
            }

            Log.d(TAG, "Fetching data with cookies...")

            // Fetch all endpoints in parallel-ish
            val planDetail = fetchApi("/api/v1/tokenPlan/detail", cookies)
            Log.d(TAG, "Plan detail code: ${planDetail?.optInt("code")}")

            val planUsage = fetchApi("/api/v1/tokenPlan/usage", cookies)
            Log.d(TAG, "Plan usage code: ${planUsage?.optInt("code")}")

            val usage = fetchApi("/api/v1/usage", cookies)
            Log.d(TAG, "Usage code: ${usage?.optInt("code")}")

            val year = Calendar.getInstance().get(Calendar.YEAR)
            val usageDetail = fetchApi("/api/v1/usage/detail?year=$year", cookies)
            Log.d(TAG, "Usage detail code: ${usageDetail?.optInt("code")}")

            val balance = fetchApi("/api/v1/balance", cookies)
            Log.d(TAG, "Balance code: ${balance?.optInt("code")}")

            val data = parseApiData(planDetail, planUsage, usage, usageDetail, balance)

            // Record daily snapshot and compute deltas
            val modelSnapshots = data.modelUsage.map {
                ModelSnapshot(it.modelName, it.inputToken, it.outputToken, it.totalToken, it.cacheToken, it.requestCount)
            }
            val dailyDeltas = DailyTracker.recordAndCompute(
                context,
                totalToken = data.tokenHistory,
                inputToken = data.inputCached + data.inputUncached,
                outputToken = data.output,
                cacheToken = data.inputCached,
                requestCount = data.requestCount,
                totalCost = data.totalCost.replace("¥", "").toDoubleOrNull() ?: 0.0,
                modelSnapshots = modelSnapshots
            )
            val dataWithDaily = data.copy(dailyDeltas = dailyDeltas)
            Log.d(TAG, "Daily deltas: ${dailyDeltas.size} days")

            Result.success(dataWithDaily)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch data", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch daily usage data for a specific month.
     * Uses /api/v1/usage/detail?year=YYYY&month=MM which returns daily granularity.
     */
    suspend fun fetchDailyData(year: Int, month: Int): Result<List<DailyDataPoint>> = withContext(Dispatchers.IO) {
        try {
            val cookies = CookieManager.getCookies(context) ?: return@withContext Result.failure(Exception("未登录"))
            val response = fetchApi("/api/v1/usage/detail?year=$year&month=$month", cookies)
                ?: return@withContext Result.failure(Exception("API 请求失败"))

            val data = response.optJSONObject("data") ?: return@withContext Result.failure(Exception("无数据"))
            val points = parseDailyData(data)
            Result.success(points)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch daily data", e)
            Result.failure(e)
        }
    }

    private fun parseDailyData(data: JSONObject): List<DailyDataPoint> {
        val points = mutableListOf<DailyDataPoint>()

        // Parse daily token usage: [["08-17", input, output, total, cache], ...]
        val tokenArr = data.optJSONArray("tokenUsage")
        val requestArr = data.optJSONArray("requests")

        // Build request map
        val reqMap = mutableMapOf<String, Long>()
        if (requestArr != null) {
            for (i in 0 until requestArr.length()) {
                val arr = requestArr.optJSONArray(i) ?: continue
                if (arr.length() >= 2) reqMap[arr.optString(0)] = arr.optLong(1)
            }
        }

        // Parse model daily data
        val modelTokenArr = data.optJSONArray("modelTokenUsage")
        val modelReqArr = data.optJSONArray("modelRequests")
        val modelReqMap = mutableMapOf<String, Map<String, Long>>()
        if (modelReqArr != null) {
            for (i in 0 until modelReqArr.length()) {
                val obj = modelReqArr.optJSONObject(i) ?: continue
                val model = obj.optString("model")
                val detail = obj.optJSONArray("requestsDetail") ?: continue
                val dayMap = mutableMapOf<String, Long>()
                for (j in 0 until detail.length()) {
                    val arr = detail.optJSONArray(j) ?: continue
                    if (arr.length() >= 2) dayMap[arr.optString(0)] = arr.optLong(1)
                }
                modelReqMap[model] = dayMap
            }
        }

        // Build per-model per-day map
        val modelDayMap = mutableMapOf<String, MutableMap<String, ModelDayData>>()
        if (modelTokenArr != null) {
            for (i in 0 until modelTokenArr.length()) {
                val obj = modelTokenArr.optJSONObject(i) ?: continue
                val model = obj.optString("model")
                val detail = obj.optJSONArray("usageDetail") ?: continue
                val dayMap = mutableMapOf<String, ModelDayData>()
                for (j in 0 until detail.length()) {
                    val arr = detail.optJSONArray(j) ?: continue
                    if (arr.length() >= 5) {
                        val day = arr.optString(0)
                        val mReqs = modelReqMap[model]?.get(day) ?: 0L
                        dayMap[day] = ModelDayData(
                            inputToken = arr.optLong(1),
                            outputToken = arr.optLong(2),
                            totalToken = arr.optLong(3),
                            cacheToken = arr.optLong(4),
                            requestCount = mReqs
                        )
                    }
                }
                modelDayMap[model] = dayMap
            }
        }

        // Combine into DailyDataPoint list
        if (tokenArr != null) {
            for (i in 0 until tokenArr.length()) {
                val arr = tokenArr.optJSONArray(i) ?: continue
                if (arr.length() >= 5) {
                    val day = arr.optString(0)
                    val models = modelDayMap.map { (name, dayMap) ->
                        dayMap[day] ?: ModelDayData()
                    }.let { modelList ->
                        modelDayMap.keys.map { name ->
                            DayModelData(
                                name = name,
                                data = modelDayMap[name]?.get(day) ?: ModelDayData()
                            )
                        }
                    }

                    points.add(
                        DailyDataPoint(
                            dayKey = day,
                            inputToken = arr.optLong(1),
                            outputToken = arr.optLong(2),
                            totalToken = arr.optLong(3),
                            cacheToken = arr.optLong(4),
                            requestCount = reqMap[day] ?: 0L,
                            models = models
                        )
                    )
                }
            }
        }

        return points
    }

    private fun fetchApi(endpoint: String, cookies: String, retries: Int = 2): JSONObject? {
        Log.d(TAG, "GET $BASE_URL$endpoint")

        for (attempt in 0..retries) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL$endpoint")
                    .addHeader("Cookie", cookies)
                    .addHeader("Accept", "application/json")
                    .addHeader("Accept-Language", "zh-CN")
                    .addHeader("x-timeZone", "Asia/Shanghai")
                    .addHeader("Connection", "keep-alive")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                Log.d(TAG, "  -> ${response.code}: ${body.take(200)}")

                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed: ${response.code}")
                    return null
                }
                return JSONObject(body)
            } catch (e: java.io.EOFException) {
                Log.w(TAG, "EOFException on $endpoint (attempt ${attempt + 1}/${retries + 1}), retrying...")
                if (attempt < retries) {
                    Thread.sleep(500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network error for $endpoint", e)
                return null
            }
        }
        Log.e(TAG, "All retries exhausted for $endpoint")
        return null
    }

    private fun parseApiData(
        planDetail: JSONObject?,
        planUsage: JSONObject?,
        usage: JSONObject?,
        usageDetail: JSONObject?,
        balance: JSONObject?
    ): UsageData {
        // ── Plan detail ──
        val planData = planDetail?.optJSONObject("data")
        val planName = planData?.optString("planName", "") ?: ""
        val expireDate = planData?.optString("currentPeriodEnd", "") ?: ""
        val cleanExpireDate = expireDate.split(" ").firstOrNull() ?: expireDate

        // ── Plan usage (credit progress) ──
        val planUsageData = planUsage?.optJSONObject("data")
        val monthUsageObj = planUsageData?.optJSONObject("monthUsage")
        val monthItems = monthUsageObj?.optJSONArray("items")

        var creditsUsed = 0L
        var creditsTotal = 0L
        var usagePercent = 0.0

        if (monthItems != null) {
            for (i in 0 until monthItems.length()) {
                val item = monthItems.optJSONObject(i)
                if (item?.optString("name") == "month_total_token") {
                    creditsUsed = item.optLong("used", 0)
                    creditsTotal = item.optLong("limit", 0)
                    usagePercent = item.optDouble("percent", 0.0) * 100.0
                    break
                }
            }
        }

        // ── Overall usage (/api/v1/usage) ──
        val usageData = usage?.optJSONObject("data")
        val tokenUsage = usageData?.optJSONObject("tokenUsage")
        val totalInputToken = tokenUsage?.optLong("inputToken", 0) ?: 0
        val outputToken = tokenUsage?.optLong("outputToken", 0) ?: 0
        val cacheToken = tokenUsage?.optLong("cacheToken", 0) ?: 0
        val totalToken = tokenUsage?.optLong("totalToken", 0) ?: 0
        val inputUncached = totalInputToken - cacheToken

        val costUsage = usageData?.optJSONObject("costUsage")
        val totalCost = costUsage?.optString("totalCost", "0.00") ?: "0.00"

        // ── Detail breakdown (/api/v1/usage/detail) ──
        val detailData = usageDetail?.optJSONObject("data")

        // Monthly token usage: [[month, input, output, total, cache], ...]
        val monthlyUsageList = mutableListOf<MonthlyUsage>()
        val tokenUsageArr = detailData?.optJSONArray("tokenUsage")
        val requestsArr = detailData?.optJSONArray("requests")

        // Build request count map: month -> count
        val requestMap = mutableMapOf<String, Long>()
        if (requestsArr != null) {
            for (i in 0 until requestsArr.length()) {
                val arr = requestsArr.optJSONArray(i)
                if (arr != null && arr.length() >= 2) {
                    requestMap[arr.optString(0)] = arr.optLong(1)
                }
            }
        }

        var totalRequests = 0L
        if (tokenUsageArr != null) {
            for (i in 0 until tokenUsageArr.length()) {
                val arr = tokenUsageArr.optJSONArray(i)
                if (arr != null && arr.length() >= 5) {
                    val month = arr.optString(0)
                    val input = arr.optLong(1)
                    val output = arr.optLong(2)
                    val total = arr.optLong(3)
                    val cache = arr.optLong(4)
                    val requests = requestMap[month] ?: 0L
                    totalRequests += requests
                    monthlyUsageList.add(
                        MonthlyUsage(month, input, output, total, cache, requests)
                    )
                }
            }
        }

        // Model breakdown
        val modelUsageList = mutableListOf<ModelUsage>()
        val modelTokenArr = detailData?.optJSONArray("modelTokenUsage")
        val modelReqArr = detailData?.optJSONArray("modelRequests")

        // Build model request map
        val modelReqMap = mutableMapOf<String, Long>()
        if (modelReqArr != null) {
            for (i in 0 until modelReqArr.length()) {
                val obj = modelReqArr.optJSONObject(i) ?: continue
                val model = obj.optString("model")
                val detail = obj.optJSONArray("requestsDetail")
                if (detail != null && detail.length() > 0) {
                    val arr = detail.optJSONArray(0)
                    if (arr != null && arr.length() >= 2) {
                        modelReqMap[model] = arr.optLong(1)
                    }
                }
            }
        }

        if (modelTokenArr != null) {
            for (i in 0 until modelTokenArr.length()) {
                val obj = modelTokenArr.optJSONObject(i) ?: continue
                val model = obj.optString("model")
                val detail = obj.optJSONArray("usageDetail")
                if (detail != null && detail.length() > 0) {
                    val arr = detail.optJSONArray(0)
                    if (arr != null && arr.length() >= 5) {
                        val input = arr.optLong(1)
                        val output = arr.optLong(2)
                        val total = arr.optLong(3)
                        val cache = arr.optLong(4)
                        val requests = modelReqMap[model] ?: 0L
                        modelUsageList.add(
                            ModelUsage(model, input, output, total, cache, requests)
                        )
                    }
                }
            }
        }

        // Calculate model percentages
        val grandTotal = modelUsageList.sumOf { it.totalToken }.toDouble()
        if (grandTotal > 0) {
            modelUsageList.forEachIndexed { idx, m ->
                modelUsageList[idx] = m.copy(percentage = m.totalToken / grandTotal * 100.0)
            }
        }

        // If totalRequests from monthly data is 0, try from detail requests
        val finalRequestCount = if (totalRequests > 0) totalRequests
            else (detailData?.optJSONArray("requests")?.let { arr ->
                var sum = 0L
                for (i in 0 until arr.length()) {
                    val a = arr.optJSONArray(i)
                    if (a != null && a.length() >= 2) sum += a.optLong(1)
                }
                sum
            } ?: 0L)

        // ── Account balance (/api/v1/balance) ──
        val balanceData = balance?.optJSONObject("data")
        val totalBalance = balanceData?.optString("balance", "0.00") ?: "0.00"
        val giftBalance = balanceData?.optString("giftBalance", "0.00") ?: "0.00"
        val cashBalance = balanceData?.optString("cashBalance", "0.00") ?: "0.00"

        return UsageData(
            totalCost = "¥$totalCost",
            tokenHistory = totalToken,
            inputCached = cacheToken,
            inputUncached = inputUncached.coerceAtLeast(0),
            output = outputToken,
            requestCount = finalRequestCount,
            planName = planName,
            creditsUsed = creditsUsed,
            creditsTotal = creditsTotal,
            usagePercentage = usagePercent,
            expireDate = cleanExpireDate,
            totalBalance = totalBalance,
            giftBalance = giftBalance,
            cashBalance = cashBalance,
            monthlyUsage = monthlyUsageList,
            modelUsage = modelUsageList
        )
    }

    fun destroy() {
        // OkHttp client is shared; nothing to destroy
    }
}
