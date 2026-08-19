# MiMo Tracker

小米 MiMo API 平台用量追踪 Android 客户端。通过逆向平台内部 API，提供 Token 消耗、余额、套餐进度、每日趋势、模型对比等数据的可视化展示。

## 功能

- **概览页** — 套餐进度、余额、Token 汇总、7天消耗图表、模型分布
- **分析页** — 7天/30天趋势（堆叠柱状图）、模型对比、缓存命中率
- **设置页** — 退出登录、手动刷新、版本信息

## 技术栈

- Kotlin + Jetpack Compose
- OkHttp (内部 API 调用)
- kotlinx-serialization
- DataStore (Cookie 持久化 + 每日快照)
- WebView (登录流程)
- Canvas 自绘图表（无第三方图表库）

## 工作原理

1. 通过 WebView 登录小米账号，提取 session cookies
2. Cookies 存入 DataStore 持久化
3. 使用 OkHttp 调用 `platform.xiaomimimo.com` 内部 API 获取数据
4. 自动获取近7天/30天每日粒度数据

## 构建

```bash
# 需要 JDK 17 + Android SDK
./gradlew assembleDebug
# APK 输出: app/build/outputs/apk/debug/app-debug.apk
```

## 注意事项

- 本项目通过逆向工程获取 MiMo 平台内部 API，非官方 SDK
- API 端点可能随平台更新而变化
- 仅供个人学习和使用
