package com.mimo.tracker.data.api

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mimo_cookies")

object CookieManager {
    private val COOKIE_KEY = stringPreferencesKey("session_cookies")

    private const val BASE_URL = "https://platform.xiaomimimo.com"
    private const val LOGIN_URL = "$BASE_URL/api/v1/genLoginUrl?currentPath=/console/usage"

    private val requiredCookies = listOf(
        "api-platform_ph",
        "api-platform_serviceToken",
        "api-platform_slh",
        "userId"
    )

    suspend fun saveCookies(context: Context, cookies: String) {
        context.dataStore.edit { prefs ->
            prefs[COOKIE_KEY] = cookies
        }
    }

    suspend fun getCookies(context: Context): String? {
        return context.dataStore.data.map { prefs ->
            prefs[COOKIE_KEY]
        }.first()
    }

    suspend fun clearCookies(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(COOKIE_KEY)
        }
    }

    /**
     * Extract cookies from WebView's CookieManager.
     * Returns the cookie string if all required cookies are found, null otherwise.
     */
    fun extractCookiesFromWebView(): String? {
        val cookieManager = CookieManager.getInstance()
        val allCookies = cookieManager.getCookie(BASE_URL) ?: return null

        val cookieMap = mutableMapOf<String, String>()
        allCookies.split(";").forEach { cookie ->
            val parts = cookie.trim().split("=", limit = 2)
            if (parts.size == 2) {
                cookieMap[parts[0].trim()] = parts[1].trim()
            }
        }

        val hasRequired = requiredCookies.all { cookieMap.containsKey(it) }
        if (!hasRequired) return null

        return requiredCookies.joinToString("; ") { "$it=${cookieMap[it]}" }
    }

    fun getLoginUrl(): String = LOGIN_URL

    fun getBaseUrl(): String = BASE_URL
}
