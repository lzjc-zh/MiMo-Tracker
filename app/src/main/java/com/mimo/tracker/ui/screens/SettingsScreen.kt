package com.mimo.tracker.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.tracker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    lastRefreshTime: String
) {
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
            Text(
                text = "设置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Account Section
        SettingsSection(title = "账号") {
            SettingsItem(
                icon = Icons.Default.Person,
                title = "当前账号",
                subtitle = "小米账号",
                onClick = {}
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Data Section
        SettingsSection(title = "数据") {
            SettingsItem(
                icon = Icons.Default.Refresh,
                title = "刷新数据",
                subtitle = if (lastRefreshTime.isNotEmpty()) "上次更新: $lastRefreshTime" else "点击获取最新数据",
                onClick = onRefresh
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About Section
        val context = LocalContext.current
        val repoUrl = "https://github.com/lzjc-zh/MiMo-Tracker"
        val releasesUrl = "$repoUrl/releases"

        SettingsSection(title = "关于") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "MiMo Tracker",
                subtitle = "版本 1.0.0 · 点击检查更新",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releasesUrl)))
                }
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = "开源地址",
                subtitle = "github.com/lzjc-zh/MiMo-Tracker",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Logout Button
        Button(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MiMoRed
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Default.Logout,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "退出登录",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.background(Color.White)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MiMoOrange.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MiMoOrange,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextHint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
