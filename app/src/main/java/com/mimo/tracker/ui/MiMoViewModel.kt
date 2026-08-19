package com.mimo.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mimo.tracker.data.api.CookieManager
import com.mimo.tracker.data.api.DataScraper
import com.mimo.tracker.data.model.DailyDataPoint
import com.mimo.tracker.data.model.UsageData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val usageData: UsageData? = null,
    val lastRefreshTime: String = "",
    // Daily API data for analysis
    val dailyApiData: List<DailyDataPoint> = emptyList(),
    val dailyApiData30d: List<DailyDataPoint> = emptyList(),
    val isLoadingDaily: Boolean = false
)

class MiMoViewModel(application: Application) : AndroidViewModel(application) {

    private val dataScraper = DataScraper(application)
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        checkLoginState()
    }

    private fun checkLoginState() {
        viewModelScope.launch {
            val cookies = CookieManager.getCookies(getApplication())
            _state.value = _state.value.copy(isLoggedIn = cookies != null)
            if (cookies != null) {
                refreshData()
            }
        }
    }

    fun onLoginSuccess() {
        viewModelScope.launch {
            val cookies = CookieManager.extractCookiesFromWebView()
            if (cookies != null) {
                CookieManager.saveCookies(getApplication(), cookies)
                _state.value = _state.value.copy(isLoggedIn = true, error = null)
                refreshData()
            } else {
                _state.value = _state.value.copy(error = "登录失败，无法获取认证信息")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            CookieManager.clearCookies(getApplication())
            dataScraper.destroy()
            _state.value = DashboardState()
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)

            try {
                val result = dataScraper.scrapeUsageData()
                result.onSuccess { data ->
                    _state.value = _state.value.copy(
                        usageData = data,
                        lastRefreshTime = java.text.SimpleDateFormat(
                            "HH:mm:ss",
                            java.util.Locale.getDefault()
                        ).format(java.util.Date())
                    )
                    // Also fetch daily data for analysis
                    fetchDailyData()
                }.onFailure { e ->
                    _state.value = _state.value.copy(error = "获取数据失败: ${e.message}")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "刷新失败: ${e.message}")
            } finally {
                _state.value = _state.value.copy(isLoading = false, isRefreshing = false)
            }
        }
    }

    /**
     * Fetch daily data for the last N days using the /api/v1/usage/detail?year=YYYY&month=MM endpoint.
     */
    fun fetchDailyData(days: Int = 30) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingDaily = true)
            try {
                val cal = java.util.Calendar.getInstance()
                val currentYear = cal.get(java.util.Calendar.YEAR)
                val currentMonth = cal.get(java.util.Calendar.MONTH) + 1

                val allPoints = mutableListOf<DailyDataPoint>()

                // Fetch current month
                val result1 = dataScraper.fetchDailyData(currentYear, currentMonth)
                result1.onSuccess { allPoints.addAll(it) }

                // If we need more days, fetch previous month too
                if (days > 15) {
                    val prevMonth = if (currentMonth > 1) currentMonth - 1 else 12
                    val prevYear = if (currentMonth > 1) currentYear else currentYear - 1
                    val result2 = dataScraper.fetchDailyData(prevYear, prevMonth)
                    result2.onSuccess { allPoints.addAll(it) }
                }

                // Sort by day key and take last N days
                val sorted = allPoints.sortedBy { it.dayKey }.takeLast(days)
                val last7 = sorted.takeLast(7)

                _state.value = _state.value.copy(
                    dailyApiData = last7,
                    dailyApiData30d = sorted,
                    isLoadingDaily = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoadingDaily = false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dataScraper.destroy()
    }
}
