package com.mimo.tracker.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mimo.tracker.data.api.CookieManager as AppCookieManager
import com.mimo.tracker.ui.theme.MiMoOrange
import com.mimo.tracker.ui.theme.SurfaceLight
import com.mimo.tracker.ui.theme.TextPrimary
import com.mimo.tracker.ui.theme.TextSecondary

private const val MIMO_STUDIO_URL = "https://aistudio.xiaomimimo.com"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf("MiMo Chat") }
    var cookiesReady by remember { mutableStateOf(false) }

    // Restore cookies from DataStore before creating WebView
    LaunchedEffect(Unit) {
        val savedCookies = AppCookieManager.getCookies(context)
        if (savedCookies != null) {
            val systemCookieManager = CookieManager.getInstance()
            systemCookieManager.setAcceptCookie(true)
            val domains = listOf(
                "https://xiaomimimo.com",
                "https://aistudio.xiaomimimo.com",
                "https://platform.xiaomimimo.com"
            )
            savedCookies.split(";").forEach { cookie ->
                val parts = cookie.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    val cookieStr = "${parts[0].trim()}=${parts[1].trim()}; path=/; domain=.xiaomimimo.com"
                    for (domain in domains) {
                        systemCookieManager.setCookie(domain, cookieStr)
                    }
                }
            }
            systemCookieManager.flush()
        }
        cookiesReady = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
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
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canGoBack) {
                    IconButton(onClick = { webViewRef?.goBack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                }
                Text(
                    text = currentTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (canGoBack) 0.dp else 12.dp)
                )
                IconButton(onClick = { webViewRef?.reload() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = Color.White
                    )
                }
            }
        }

        // Progress bar
        if (isLoading) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MiMoOrange,
            )
        }

        // WebView (only create after cookies are restored)
        if (cookiesReady) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(false)
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }

                        val systemCookieManager = CookieManager.getInstance()
                        systemCookieManager.setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                                isLoading = newProgress < 100
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                canGoBack = view?.canGoBack() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                canGoBack = view?.canGoBack() == true
                                currentTitle = view?.title ?: "MiMo Chat"

                                // Save cookies after page loads
                                view?.postDelayed({
                                    AppCookieManager.extractCookiesFromWebView()
                                }, 1000)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                return if (url.contains("xiaomimimo.com") || url.contains("xiaomi.com")) {
                                    false
                                } else {
                                    true
                                }
                            }
                        }

                        webViewRef = this
                        loadUrl(MIMO_STUDIO_URL)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            // Loading indicator while cookies restore
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MiMoOrange)
            }
        }
    }
}
