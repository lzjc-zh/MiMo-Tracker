package com.mimo.tracker.data.api

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "DailyTracker"

/**
 * Stores daily usage snapshots to compute deltas over time.
 * Since the API only provides monthly aggregates, we snapshot on each refresh
 * and compute daily deltas from consecutive snapshots.
 */
object DailyTracker {

    private val SNAPSHOTS_KEY = stringPreferencesKey("daily_snapshots")

    /**
     * Record a new snapshot for today. If one already exists for today, update it only if newer.
     * Returns the list of daily deltas for the last 7 days.
     */
    suspend fun recordAndCompute(
        context: Context,
        totalToken: Long,
        inputToken: Long,
        outputToken: Long,
        cacheToken: Long,
        requestCount: Long,
        totalCost: Double,
        modelSnapshots: List<ModelSnapshot>
    ): List<DailyDelta> {
        val now = System.currentTimeMillis()
        val today = todayKey()

        val snapshots = loadSnapshots(context).toMutableList()

        // Update or add today's snapshot
        val existingIdx = snapshots.indexOfLast { it.dateKey == today }
        val newSnapshot = DailySnapshot(
            dateKey = today,
            timestamp = now,
            totalToken = totalToken,
            inputToken = inputToken,
            outputToken = outputToken,
            cacheToken = cacheToken,
            requestCount = requestCount,
            totalCost = totalCost,
            models = modelSnapshots
        )

        if (existingIdx >= 0) {
            snapshots[existingIdx] = newSnapshot
        } else {
            snapshots.add(newSnapshot)
        }

        // Keep only last 30 days
        val cutoff = todayKey(-30)
        val filtered = snapshots.filter { it.dateKey >= cutoff }

        // Save
        saveSnapshots(context, filtered)
        Log.d(TAG, "Saved ${filtered.size} snapshots, today=$today")

        // Compute deltas for last 7 days
        return computeDeltas(filtered)
    }

    /**
     * Load existing snapshots and compute deltas without recording a new one.
     */
    suspend fun getRecentDeltas(context: Context): List<DailyDelta> {
        val snapshots = loadSnapshots(context)
        return computeDeltas(snapshots)
    }

    private fun computeDeltas(snapshots: List<DailySnapshot>): List<DailyDelta> {
        if (snapshots.size < 2) return emptyList()

        val sorted = snapshots.sortedBy { it.dateKey }
        val deltas = mutableListOf<DailyDelta>()

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]

            val deltaTotal = (curr.totalToken - prev.totalToken).coerceAtLeast(0)
            val deltaInput = (curr.inputToken - prev.inputToken).coerceAtLeast(0)
            val deltaOutput = (curr.outputToken - prev.outputToken).coerceAtLeast(0)
            val deltaCache = (curr.cacheToken - prev.cacheToken).coerceAtLeast(0)
            val deltaRequests = (curr.requestCount - prev.requestCount).coerceAtLeast(0)
            val deltaCost = (curr.totalCost - prev.totalCost).coerceAtLeast(0.0)

            // Model deltas
            val modelDeltas = mutableListOf<ModelDelta>()
            for (m in curr.models) {
                val prevModel = prev.models.find { it.name == m.name }
                val dTotal = if (prevModel != null) (m.totalToken - prevModel.totalToken).coerceAtLeast(0) else m.totalToken
                val dInput = if (prevModel != null) (m.inputToken - prevModel.inputToken).coerceAtLeast(0) else m.inputToken
                val dOutput = if (prevModel != null) (m.outputToken - prevModel.outputToken).coerceAtLeast(0) else m.outputToken
                val dCache = if (prevModel != null) (m.cacheToken - prevModel.cacheToken).coerceAtLeast(0) else m.cacheToken
                val dReq = if (prevModel != null) (m.requestCount - prevModel.requestCount).coerceAtLeast(0) else m.requestCount
                modelDeltas.add(ModelDelta(m.name, dInput, dOutput, dTotal, dCache, dReq))
            }

            deltas.add(
                DailyDelta(
                    dateKey = curr.dateKey,
                    totalToken = deltaTotal,
                    inputToken = deltaInput,
                    outputToken = deltaOutput,
                    cacheToken = deltaCache,
                    uncachedInput = (deltaInput - deltaCache).coerceAtLeast(0),
                    requestCount = deltaRequests,
                    cost = deltaCost,
                    models = modelDeltas
                )
            )
        }

        // Return last 7 days
        return deltas.takeLast(7)
    }

    private fun todayKey(offsetDays: Int = 0): String {
        val cal = java.util.Calendar.getInstance()
        if (offsetDays != 0) cal.add(java.util.Calendar.DAY_OF_YEAR, offsetDays)
        return String.format(
            "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    private suspend fun loadSnapshots(context: Context): List<DailySnapshot> {
        val json = context.dataStore.data.map { prefs ->
            prefs[SNAPSHOTS_KEY] ?: "[]"
        }.first()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val modelsArr = obj.optJSONArray("models") ?: JSONArray()
                val models = (0 until modelsArr.length()).map { j ->
                    val mo = modelsArr.optJSONObject(j)
                    ModelSnapshot(
                        name = mo?.optString("name") ?: "",
                        inputToken = mo?.optLong("inputToken") ?: 0,
                        outputToken = mo?.optLong("outputToken") ?: 0,
                        totalToken = mo?.optLong("totalToken") ?: 0,
                        cacheToken = mo?.optLong("cacheToken") ?: 0,
                        requestCount = mo?.optLong("requestCount") ?: 0
                    )
                }
                DailySnapshot(
                    dateKey = obj.optString("dateKey"),
                    timestamp = obj.optLong("timestamp"),
                    totalToken = obj.optLong("totalToken"),
                    inputToken = obj.optLong("inputToken"),
                    outputToken = obj.optLong("outputToken"),
                    cacheToken = obj.optLong("cacheToken"),
                    requestCount = obj.optLong("requestCount"),
                    totalCost = obj.optDouble("totalCost"),
                    models = models
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse snapshots", e)
            emptyList()
        }
    }

    private suspend fun saveSnapshots(context: Context, snapshots: List<DailySnapshot>) {
        val arr = JSONArray()
        for (s in snapshots) {
            val obj = JSONObject().apply {
                put("dateKey", s.dateKey)
                put("timestamp", s.timestamp)
                put("totalToken", s.totalToken)
                put("inputToken", s.inputToken)
                put("outputToken", s.outputToken)
                put("cacheToken", s.cacheToken)
                put("requestCount", s.requestCount)
                put("totalCost", s.totalCost)
                val modelsArr = JSONArray()
                for (m in s.models) {
                    modelsArr.put(JSONObject().apply {
                        put("name", m.name)
                        put("inputToken", m.inputToken)
                        put("outputToken", m.outputToken)
                        put("totalToken", m.totalToken)
                        put("cacheToken", m.cacheToken)
                        put("requestCount", m.requestCount)
                    })
                }
                put("models", modelsArr)
            }
            arr.put(obj)
        }
        context.dataStore.edit { prefs ->
            prefs[SNAPSHOTS_KEY] = arr.toString()
        }
    }
}

data class ModelSnapshot(
    val name: String,
    val inputToken: Long,
    val outputToken: Long,
    val totalToken: Long,
    val cacheToken: Long,
    val requestCount: Long
)

data class DailySnapshot(
    val dateKey: String,
    val timestamp: Long,
    val totalToken: Long,
    val inputToken: Long,
    val outputToken: Long,
    val cacheToken: Long,
    val requestCount: Long,
    val totalCost: Double,
    val models: List<ModelSnapshot>
)

data class DailyDelta(
    val dateKey: String,       // "2026-08-19"
    val totalToken: Long,
    val inputToken: Long,
    val outputToken: Long,
    val cacheToken: Long,
    val uncachedInput: Long,
    val requestCount: Long,
    val cost: Double,
    val models: List<ModelDelta>
)

data class ModelDelta(
    val name: String,
    val inputToken: Long,
    val outputToken: Long,
    val totalToken: Long,
    val cacheToken: Long,
    val requestCount: Long
)
