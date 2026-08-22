# 📋 咕嘟（Glug-water）- 更新日志

版本号规则：`主版本.次版本.修订号`

- **主版本** — 重大重构或完全不兼容的改动
- **次版本** — 新增功能
- **修订号** — Bug 修复或小优化

> v2.x 为 Android 套壳版（本项目）；v1.x 为套壳前的 Web 版历史（网页版 / PWA）。

---

## v2.2.1 — 应用图标重绘（2026-08-23）

### 🎨 视觉

- **应用图标重绘** — 从旧版蓝色系（`#64B5F6→#1565C0`）改为水青玻璃杯：深水青渐变底 + 完整水青玻璃杯（宽杯型 + 双层水面 + 波纹 + 气泡 + 把手）
- **App 桌面图标同步** — Android 自适应图标（`ic_launcher_foreground.xml` + `background.xml`）换成水青版，启动器圆形遮罩下完整可辨
- **PWA 主题色同步** — `manifest.json` 的 `theme_color` `#4A90E2` → `#0FA896`，`background_color` → `#0C7A6B`
- **Web 内置兜底版同步** — `assets/www` 下 4 个 SVG + 4 个 PNG 全部换成新版，离线回退时也展示新图标

---

## v2.2.0 — 网页版热更新（2026-08-23）

### 🚀 新功能

- **网页更新不再需要重新构建 App** — App 启动时后台检查 GitHub Pages 上的版本号（`version.txt`），有新版则下载 `www.zip` 解压到私有目录并加载，实现「网页推送即更新」
- **断网自动回退** — 热更新下载失败/无网络时，静默加载内置 assets 兜底版，不影响启动和日常使用
- **数据无缝保留** — 热更新前后均走 `file://` 协议，本地数据与设置不受影响
- **内置兜底版同步升级** — assets/www 从单文件 index.html 升级为完整资源树（含 icons/emoji 48 个图标），与 Web 版 v2.3.0 一致

### 🔧 变更

- `MainActivity.java` 新增热更新逻辑：版本比对（v x.y.z 三段数字）、`www.zip` 下载、临时目录解压后原子替换、失败静默回退
- `sync-www.bat` 改为同步整个 www 目录（index.html + sw.js + manifest.json + icons/）
- Web 仓库根目录新增 `build-www.py`：一键生成 `version.txt` + `www.zip`

### 📦 配套（Web 仓库）

- Web 版 v2.3.0 起，每次改版后运行 `python build-www.py` 并推送，App 即可自动跟进

---

## v2.1.2 — 深色模式跟随系统加固（2026-08-15）

### 🐛 修复

- **深色模式「跟随系统」不可靠** — 不再依赖 WebView 的 `prefers-color-scheme`（部分设备/WebView 兼容问题），改为原生读取系统 `uiMode` 注入页面：auto 模式优先使用原生判断，回退 matchMedia
- **系统深浅切换不实时跟随** — Manifest 注册 `uiMode` 配置变更监听，系统切深浅（含定时深色模式到点）时页面立即重新应用，无需重启 App
- **双重暗化** — 禁用 WebView `setForceDark`，页面深浅完全由 CSS 控制

### 🔧 技术改动

- `MainActivity`：新增 `injectSystemDark()`（onPageFinished 注入 + onConfigurationChanged 实时更新）、`setForceDark(FORCE_DARK_OFF)`
- `index.html`：新增 `window.__setSystemDark()` 入口，`applyDarkMode('auto')` 优先原生注入值

---

## v2.1.1 — 体验修复（2026-08-15）

### 🐛 修复

- **深色模式跟随系统机制检查** — `auto` 模式逻辑核对（跟随系统 `prefers-color-scheme`）；系统深浅切换监听兼容老 WebView（`addEventListener` 回退 `addListener`）
- **全面屏左滑返回失效** — `onBackPressed` 空实现导致手势返回无反应；改为页面可回退则回退、否则正常退出到桌面

### ✨ 新增

- **提醒语句多样化** — 新增 10 条喝水提醒语句（原生通知 + 浏览器通知同步），每次随机取一条，替换原固定文案「记得补充水分哦」

---

## v2.1.0 — 提醒时段 + 更名咕嘟（2026-08-14）

### ✨ 新增

- **提醒时段** — 设置新增「提醒时段」（默认 08:00-22:00，支持跨天如 22:00-08:00 夜间模式）：时段外触发自动静默跳过，并直接跳到下一个时段开始，夜间零打扰
- **应用更名** — 中文名「咕嘟」、英文名「Glug-water」（glug = 咕嘟咕嘟的喝水声）；应用显示名、通知文案、页面标题、README/CHANGELOG 全面更新
- 浏览器端提醒同样遵守提醒时段

### 🔧 技术改动

- `ReminderReceiver`：`inWindow()` 时段判断（支持跨天）+ `nextWindowStart()` 时段外跳转调度
- `ReminderBridge.scheduleReminder(interval, startHour, endHour)` 三参数
- 提醒时段存入 SharedPreferences，开机恢复/重排均带时段

---

## v2.0.1 — 提醒修复与全面屏适配（2026-08-14）

### 🐛 修复

- **提醒被系统大幅延迟** — `setInexactRepeating` 在 Android 12+ 会被系统省电对齐（实测延迟 45 秒～2 分钟）；改为 `setExactAndAllowWhileIdle` 一次性精确闹钟 + 触发后自行重排，实测误差约 10 毫秒
- **提醒相位漂移** — 首次触发时间对齐到整分钟，不再固定在设置时刻的秒数上（如 :19 秒触发）
- **间隔错乱（每分钟循环）** — 触发后重排逻辑曾无视间隔值、只排下一个整分钟；修复为「保持整分钟相位 + 间隔」，15 分钟间隔实测每 15 分钟一次
- **点通知打开 App 导致提醒重置** — 页面启动不再调用 `syncAndroidReminder()`；闹钟恢复改由 `BOOT_COMPLETED` 系统广播处理
- **顶部黑色空白条** — `windowFullscreen` + 旧沉浸式 flag 在 Android 15+ 失效；改用 `WindowCompat.setDecorFitsSystemWindows(false)` + 页面 `viewport-fit=cover` / `safe-area` 适配，状态栏区域与页面颜色一致
- **导入/导出数据在 App 内不可用** — WebView 不支持 `<a download>` 和 `<input type=file>`；原生桥接：导出写系统「下载」目录（Android 10+ 走 MediaStore），导入走 `onShowFileChooser` 系统文件选择器
- **关于页更新日志不显示** — 内嵌完整更新日志（App 的 file:// 环境无法 fetch CHANGELOG.md）

### ✨ 新增

- **开机自动恢复提醒** — `RECEIVE_BOOT_COMPLETED` 广播：设备重启后自动重新注册提醒闹钟，无需手动打开 App
- **每周一自动保活** — `KeepAliveReceiver` 每周一 9:00 自动向 Supabase 发请求，防止免费项目因 7 天无 API 请求被暂停
- **App 图标改为 Web 版风格** — 白色背景 + 蓝色渐变水杯（与 Web 版 `Icon-192.svg` 一致）
- **精确闹钟权限声明** — `SCHEDULE_EXACT_ALARM`（targetSdk<34 默认授予）；未授予时自动降级非精确闹钟

### 🎨 优化

- 状态栏/导航栏图标改白色（适配蓝色标题栏）
- 清理冗余 import

---

## v2.0.0 — Android 套壳版（2026-08-13）

- Android WebView 套壳（内嵌 Web 版全部功能：喝水记录、花园、排行榜、历史图表、设置、Supabase 同步）
- 喝水提醒：`AlarmManager` 定时通知 + JS 桥接（`scheduleReminder` / `cancelReminder`）
- Android 13+ 通知权限请求、崩溃自诊、全屏沉浸

---

## Web 版历史（v1.x，套壳前）

### v1.5.2 — Safari 稳定版

- **sw.js 还原完整版** — 修复 Safari 下导航请求返回空响应导致白屏
- **吃药记录触发云端同步**、**周/月统计过滤 pill 记录**
- **schema.sql 补齐 pill_today 字段**；清理死代码与 8.1MB twemoji 残留

### v1.5.1 — 问题修复

- 排行榜其他人不显示（移除不存在的 pill_today 字段）、吃药记录 0ml、日历日期颜色

### v1.5.0 — 吃药提醒

- 💊 吃药记录 + 排行榜药丸标记 + 记录混排

### v1.4.2 — 问题修复

- 主页记录删除叉、心情按钮、月份柱状图、喝一小口量可调等

### v1.4.1 — 自动同步 + 离线支持

- 网络监听自动推送、Service Worker 离线支持

### v1.4.0 — 日历交互 + 深色模式

- 日历日期可点/翻月、15ml 小口按钮、排行榜本周/本月、深色模式、提醒周期可调、断开手表

### v1.3.0 — 零外部依赖

- 移除 CDN（修复 Safari 打不开）、Upsert 409 修复

### v1.2.3 / v1.2.2 / v1.2.0

- 心情帮助、记录折叠、补选/更换心情、情绪记录、查看他人记录

### v1.1.1 / v1.1.0

- 排行榜刷新按钮、Supabase 在线排行榜、PWA 安装修复

### v1.0.0 — 初始版本

- 喝水记录、养成花园、排行榜、统计图表、设置、动画特效
