<div align="center">

# 🚄 BetterRailway — 12306 智能助手

**BetterRailway** is an Android app that queries China Railway (12306) train schedules and seat availability directly — no MCP server required.

**BetterRailway** 是一款直接调用 12306 官方 API 查询余票的 Android App，无需依赖任何 MCP 服务器。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android-brightgreen)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-21%2B-success)](app/src/main/AndroidManifest.xml)

</div>

---

## ✨ Features / 功能

| English | 中文 |
|---------|------|
| 🔍 Query real-time train schedules & seat availability | 🔍 查询实时余票与列车时刻 |
| 🚉 Direct 12306 API calls, no MCP server needed | 🚉 直连 12306 官方 API，无需 MCP |
| 📅 Date picker with past-date protection | 📅 日期选择器，禁止过去日期 |
| 🎯 Train type filter (G/D/Z/T/K) | 🎯 高铁/动车/直达/特快/快速 筛选 |
| 🛤️ Route detail with all stops | 🛤️ 经停站列表，点击查看全部站点 |
| 🤖 AI analysis (optional, requires your own API key) | 🤖 AI 智能分析（可选，需自备 API Key） |
| 🌐 Works offline for station data once cached | 🌐 站点数据首次加载后缓存，离线可用 |
| 📦 Export/import config as JSON | 📦 配置导出/导入为 JSON 文件 |

## 📱 Screenshots / 截图

| Query / 查询页 | Ticket List / 车次列表 | Route Detail / 路线详情 | Settings / 设置 |
|:---:|:---:|:---:|:---:|
| ![query](screenshots/query.png) | ![list](screenshots/list.png) | ![route](screenshots/route.png) | ![settings](screenshots/settings.png) |

## 🚀 Quick Start / 快速开始

### Download / 下载

Grab the latest APK from the [Releases page](https://github.com/chengxing507/BetterRailway/releases).

从 [Releases 页面](https://github.com/chengxing507/BetterRailway/releases) 下载最新 APK。

### Setup / 配置

1. **Install** the APK on your Android device (API 21+)
2. **Optional**: Go to **Settings → AI API 设置**, fill in your LLM API endpoint & key if you want AI analysis
3. **Enter** departure & arrival station names (Chinese), pick a date, tap **查询车票**

That's it — no MCP server, no extra setup needed.

---

1. **安装** APK（Android 5.0 以上）
2. **可选**：如需 AI 分析功能，进入 **设置 → AI API 设置** 填写 API 地址和密钥
3. **输入** 出发站/到达站、选择日期，点击 **查询车票**

搞定，无需任何 MCP 服务器配置。

## 🔧 Build from Source / 自行编译

```bash
git clone https://github.com/chengxing507/BetterRailway.git
cd BetterRailway
./gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## 🏗 Architecture / 架构

```
MainActivity          →  User input & query trigger
├─ StationDataManager →  Downloads & caches station name → code mapping from 12306
├─ TicketQueryManager →  Calls 12306 API (kyfw.12306.cn) directly
├─ TicketListActivity →  Displays parsed train schedules
│  └─ RouteDetailActivity →  Shows all stops for a train
├─ SettingsActivity   →  AI API config (optional)
└─ AIAnalysisClient   →  Sends train info to your LLM API for smart recommendations
```

The app communicates directly with `kyfw.12306.cn` — there is zero dependency on any MCP middleware.

App 直接与 `kyfw.12306.cn` 通信，不依赖任何 MCP 中间件。

## 📄 License / 许可证

[MIT](LICENSE) © 2026 chengxing507