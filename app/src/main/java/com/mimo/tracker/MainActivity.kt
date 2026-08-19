package com.mimo.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mimo.tracker.ui.MiMoViewModel
import com.mimo.tracker.ui.screens.AnalysisScreen
import com.mimo.tracker.ui.screens.DashboardScreen
import com.mimo.tracker.ui.screens.LoginScreen
import com.mimo.tracker.ui.screens.SettingsScreen
import com.mimo.tracker.ui.theme.MiMoOrange

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MiMoViewModel = viewModel()
                    val state by viewModel.state.collectAsState()

                    if (state.isLoggedIn) {
                        MainScreen(
                            viewModel = viewModel,
                            state = state
                        )
                    } else {
                        LoginScreen(
                            onLoginSuccess = { viewModel.onLoginSuccess() }
                        )
                    }
                }
            }
        }
    }
}

enum class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    DASHBOARD("dashboard", "概览", Icons.Default.Dashboard),
    ANALYSIS("analysis", "分析", Icons.Default.Analytics),
    SETTINGS("settings", "设置", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MiMoViewModel,
    state: com.mimo.tracker.ui.DashboardState
) {
    var selectedTab by remember { mutableStateOf(BottomNavItem.DASHBOARD) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                BottomNavItem.entries.forEach { item ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        selected = selectedTab == item,
                        onClick = { selectedTab = item },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MiMoOrange,
                            selectedTextColor = MiMoOrange,
                            indicatorColor = MiMoOrange.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            BottomNavItem.DASHBOARD -> {
                DashboardScreen(
                    state = state,
                    onRefresh = { viewModel.refreshData() }
                )
            }
            BottomNavItem.ANALYSIS -> {
                AnalysisScreen(
                    dailyData7d = state.dailyApiData,
                    dailyData30d = state.dailyApiData30d,
                    isLoading = state.isLoadingDaily,
                    onRefresh = { viewModel.refreshData() }
                )
            }
            BottomNavItem.SETTINGS -> {
                SettingsScreen(
                    onLogout = { viewModel.logout() },
                    onRefresh = { viewModel.refreshData() },
                    lastRefreshTime = state.lastRefreshTime
                )
            }
        }
    }
}
