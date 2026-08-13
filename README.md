# 💧 喝杯水吧 — Android 套壳 App

Web 版喝水记录应用（[drink-water-web](https://github.com/kidnappe/drink-water-web)）的安卓 WebView 套壳，页面与 Web 版**完全同源**，喝水提醒使用**原生 AlarmManager**（WebView 内 Notification API 不可靠）。

> 与手表端（`drink-water-watch`）平行：手表端面向 Oppo Watch 2（Chrome 61），本工程面向**正常安卓手机**（系统 WebView 自动更新，无兼容性负担）。

---

## 功能

- ✅ 与 Web 版功能完全一致（喝水记录、花园、排行榜、历史图表、设置、Supabase 同步）
- ✅ **喝水提醒走原生 AlarmManager**：`setInexactRepeating` 定时通知 + 通知渠道，App 被杀也生效
- ✅ 提醒间隔 1-60 分钟可调（设置 → 喝水提醒 / 提醒周期）
- ✅ Android 13+ 自动请求通知权限
- ✅ 启动崩溃自诊（上次闪退下次启动直接显示原因）
- ✅ 全屏沉浸式界面

## 项目结构

```
drink-water-app/
├── android/                          ← Android WebView 壳工程（Gradle）
│   ├── app/src/main/
│   │   ├── assets/www/index.html     ← Web 版页面（⚠️ 从 drink-water-web 同步，勿直接改）
│   │   ├── java/com/drinkwater/web/
│   │   │   ├── MainActivity.java     # WebView 入口 + 全屏 + 通知权限请求
│   │   │   ├── ReminderBridge.java   # JS ↔ Java 桥（scheduleReminder / cancelReminder / vibrate）
│   │   │   ├── ReminderReceiver.java # AlarmManager 定时通知广播接收器
│   │   │   └── CrashHandler.java     # 全局崩溃抓取器
│   │   └── res/                      # 主题、字符串、图标
│   └── gradlew.bat                   # 构建入口
├── sync-www.bat                      # 从 Web 仓库同步 index.html 到 assets
└── README.md
```

## 构建

```bash
cd android
gradlew.bat assembleDebug
# 产物：android/app/build/outputs/apk/debug/app-debug.apk
```

要求：JDK 17、Android SDK（`android/local.properties` 里 `sdk.dir` 指向本机 SDK）。

## 页面同步（重要）

`assets/www/index.html` 是 Web 版 `index.html` 的**副本**，Web 版每次更新后运行：

```bash
sync-www.bat
```

再重新构建 APK。Web 版源码里的 `AndroidBridge` 桥接逻辑（`isAndroidApp()` / `syncAndroidReminder()`）在浏览器环境自动跳过（`typeof AndroidBridge === 'undefined'`），无任何副作用，因此两端可长期共用同一份页面源码。

## 喝水提醒实现

| 层 | 实现 |
|----|------|
| 设置入口 | 页面内设置 → 保存时调 `AndroidBridge.scheduleReminder(间隔分钟)` / `cancelReminder()` |
| JS 桥 | `ReminderBridge.java`（`@JavascriptInterface`） |
| 调度 | `ReminderReceiver.schedule()` → `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` 一次性精确闹钟 |
| 续排 | 触发后 `rescheduleNext()` 读 SharedPreferences 中的间隔自动重排下一次（间隔精确） |
| 通知 | 通知渠道 `drink_reminder` + `NotificationCompat`（"💧 该喝水了！"） |
| 权限 | Android 13+ 启动时请求 `POST_NOTIFICATIONS`；Manifest 声明 `SCHEDULE_EXACT_ALARM`（targetSdk<34 默认授予） |
| 重启恢复 | `BOOT_COMPLETED` 广播自动重排（设备重启后系统会清空闹钟），无需手动打开 App |

⚠️ 兼容降级：若系统未授予精确闹钟权限（`canScheduleExactAlarms()` 为 false），自动降级为 `setInexactRepeating`（会略有延迟）；MIUI/HyperOS 用户建议在「省电策略」中设为无限制、允许自启动。

> 为什么不用 `setInexactRepeating`？Android 12+ 会对重复的非精确闹钟做省电对齐，实测延迟可达 45 秒～数分钟。改为「一次性精确闹钟 + 触发后自行重排」后，实测触发误差约 10 毫秒。

## 版本

- v2.0.1 — 提醒修复与全面屏适配（2026-08）：精确闹钟、整分钟对齐、开机自动恢复、每周一自动保活、edge-to-edge 无黑条、Web 版图标、导入导出原生化、更新日志继承 Web 版历史
- v2.0.0 — Android 套壳版（2026-08）：WebView 套壳 + AlarmManager 喝水提醒
- v1.x — Web 版历史（套壳前，见 `CHANGELOG.md`）
