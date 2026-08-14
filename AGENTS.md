# AGENTS.md — 「咕嘟」项目开发指导

> 本文件是开发本项目的**唯一权威指导**。修改代码前先读本文件。
> 最后更新：2026-06-18

---

## 1. 项目是什么

**喝杯水吧** — 喝水记录 Web 应用（单页 PWA），配 Oppo Watch 2 手表端（`drink-water-watch` 项目）。

| 组件 | 路径 | 说明 |
|------|------|------|
| Web 版 | `index.html` | **全部代码都在这个单文件里**（HTML + CSS + JS） |
| 手表版 | `D:\project\drink-water-watch\index.html` | Android WebView（Chrome 61） |
| 后端 | Supabase | 排行榜 + 设备配对 + 数据同步（可选配置） |
| 部署 | GitHub Pages | 推送 main 分支自动部署 |

---

## 2. 技术栈与铁律

### 技术栈

- 纯 HTML + CSS + JavaScript（ES5 为主），**零外部依赖**
- Canvas API 自绘图表（不用 Chart.js）
- Supabase 通过原生 `fetch` 通信（不用 SDK）
- Service Worker（`sw.js`）提供离线能力
- localStorage 存本地数据

### ⛔ 铁律（违反会出事故）

1. **禁止引入外部 CDN**（JS/CSS 都不行）。Safari 会拦截外部 CDN 请求导致页面打不开（历史事故 #2）。所有功能必须原生实现。
2. **禁止修改数据库表结构而不更新 `schema.sql` 和写迁移脚本**。Web 端代码 SELECT 的字段，数据库必须真的有这列，否则 Supabase 返回 400 整个排行榜拉取失败（历史事故）。
3. **禁止改 `sw.js` 后不更新 `SW_VERSION`**（文件头部）和 `index.html` 末尾的 `SW_DEPLOY`。否则旧 Service Worker 缓存不更新，用户看不到新版本。
4. **改动涉及排行榜/同步时，先确认目标数据库实际有哪些列**（`schema.sql` ≠ 线上数据库！线上可能没跑迁移）。
5. 代码风格保持与现有代码一致：`var`、`function` 声明、ES5 写法、中文注释、`\uXXXX` 转义中文避免编码问题。

---

## 3. 代码结构（index.html）

### 存储键

| 键 | 内容 |
|----|------|
| `drink_water_data` | 所有喝水记录数组 `[{id, amount, type, dateTime, dateKey, mood}]` |
| `drink_water_settings` | 设置对象 |
| `drink_water_supabase` | Supabase 配置 `{url, key}` |
| `drink_water_watch_disconnected` | 手表断开标记 |

### 关键函数地图

**同步层（515–897 行）**

| 函数 | 作用 |
|------|------|
| `sbFetch(path, options)` | 原生 fetch 封装，拼 Supabase URL |
| `debouncedSync()` | 30 秒攒批推送（喝水/吃药后触发） |
| `syncTodayToLeaderboard()` | 推送当日数据到排行榜（upsert） |
| `fetchOnlineLeaderboard()` | 拉取在线排行榜（最多 20 条） |
| `fetchUserRecords(userId)` | 按需拉某个用户今日明细 |
| `mergeCloudTodayRecords()` | 拉云端记录合并到本地（**只追加不删除**） |
| `forceSync()` | 手动全量同步（拉→合→推） |

**数据层（898–1276 行）**

| 函数 | 作用 |
|------|------|
| `getDateKey(d)` | 日期转 `yyyy-mm-dd` 字符串（本地时区） |
| `getTodayTotal(records)` | 今日喝水总量（**已过滤 pill**） |
| `getWeekStats/getMonthStats` | 周/月统计（**已过滤 pill**） |
| `calcStreak(records, goal)` | 连续达标天数 |
| `ensureCache()` | 累计值缓存（`_cacheToday`/`_cacheWeek`/`_cacheMonth`） |

**UI 层（1523–2445 行）**

- `addWater(amount)` / `addPill()` / `deleteRecord(id)` — 记录操作，都会触发同步
- `refreshUI()` — 刷新首页
- `renderLeaderboardContent()` — 排行榜渲染（含排序逻辑）
- `renderUserRecordsModal()` — 他人详情弹窗（**本地过滤非今日记录**）
- `showModal(title, body, actions)` / `showToast(msg)` — 通用弹窗/提示
- `renderHeatmap()` — 日历热力图

---

## 4. 数据模型

### 本地记录

```js
{
  id: 'uuid',              // 唯一标识（crypto.randomUUID）
  amount: 250,             // 毫升数（吃药记录为 0）
  type: '',                // 'pill' = 吃药记录，其余为空
  dateTime: 'ISO时间串',
  dateKey: '2026-06-18',   // 所属日期
  mood: ''                 // 心情标记
}
```

### Supabase 表

**leaderboard**（每用户一条，`user_id` 为主键 upsert）：

```sql
user_id TEXT PRIMARY KEY,       -- 手机↔手表共用
nickname TEXT DEFAULT '',
avatar_idx INTEGER DEFAULT 0,
date_key TEXT NOT NULL DEFAULT '',  -- 数据所属日期 yyyy-mm-dd（v1.5.2+）
today_ml INTEGER DEFAULT 0,
week_ml INTEGER DEFAULT 0,
month_ml INTEGER DEFAULT 0,
streak INTEGER DEFAULT 0,
today_records JSONB DEFAULT '[]'::jsonb,
pill_today BOOLEAN DEFAULT FALSE,   -- ⚠️ 线上可能没这列
banned BOOLEAN DEFAULT FALSE,
updated_at TIMESTAMPTZ DEFAULT NOW()
```

**device_pairing**（手机生成 6 位码，手表凭码绑定）：

```sql
pairing_code TEXT PRIMARY KEY,
user_id TEXT NOT NULL,
created_at TIMESTAMPTZ DEFAULT NOW()
```

---

## 5. 同步机制（重要）

### 流程（forceSync / 网络恢复自动）

```
① 拉取云端 today_records (GET)
② 按 id 去重合并到本地（仅追加，不删除）
③ 推送合并全集 (POST, on_conflict=user_id, merge-duplicates)
```

### 触发时机

| 动作 | 方式 | 延迟 |
|------|------|------|
| 喝水 `addWater` | `debouncedSync()` | 30 秒 |
| 吃药 `addPill` | `debouncedSync()` | 30 秒 |
| 删除记录 | 即时推送 | 0 |
| 手动刷新排行榜 | 先推再拉 | 0 |
| 网络恢复 `online` 事件 | `debouncedSync()` | 30 秒 |

### ⚠️ 已知缺陷

- **合并只追加不删除**：一端删除的记录，另一端拉取后会重新出现（P1 待修）
- **排行榜按 `date_key` 判断今日数据**：`todayMl = (u.date_key === 今天) ? 真实值 : 0`
- **`pill_today` 线上列可能缺失**：改 SELECT 前先确认，缺失会 400

---

## 6. 数据库迁移流程

1. 修改 `schema.sql`（建表脚本，保持同步）
2. 新建 `migrate-xxx.sql` 迁移脚本（`ALTER TABLE ... ADD COLUMN IF NOT EXISTS`）
3. 提醒用户去 Supabase SQL Editor 手动执行
4. **线上数据库 ≠ schema.sql**，改代码 SELECT 前必须确认线上真的有列

---

## 7. 版本管理规则

### 版本号

`主.次.修订`（v1.5.2 规则）：

- 主版本 — 重大重构/不兼容
- 次版本 — 新功能
- 修订号 — Bug 修复/小优化

### 发布流程

1. 改代码
2. **提交前必须问用户「要不要写进 CHANGELOG.md？」**
3. 更新 `CHANGELOG.md`（UTF-8 无 BOM）
4. 更新 `index.html` 的 `APP_VERSION`
5. 更新 `sw.js` 的 `SW_VERSION` + `index.html` 末尾 `SW_DEPLOY`
6. 更新 `backup/`（新版本文件夹，含 index.html + sw.js + schema.sql + manifest.json）
7. git 提交 + 打 tag（如 `v1.5.2`）+ 推送

---

## 8. 已知问题 / 待办

### 排行榜相关

| 问题 | 说明 |
|------|------|
| `date_key` 列未迁移 | 线上需跑 `migrate-add-date-key.sql` |
| `pill_today` 列可能缺失 | 💊 标记不显示；SELECT 加了会 400 |
| 没喝水的人显示 0ml | 已实现（`date_key` 不匹配 → 0），需线上迁移后生效 |

### 手表端（`drink-water-watch` 项目）

- 手机看不到手表推送的数据（merge 返回 0 条）
- 两端删除不同步（只追加不删除）
- 硬编码 Supabase 凭证
- `syncTodayToLeaderboard` 缺 `week_ml`/`month_ml` 推送
- 大量代码与 Web 版重复

---

## 9. 测试与部署

### 本地测试

直接浏览器打开 `index.html` 即可（file:// 协议可用，但 Supabase 通信建议用本地服务器）：

```bash
python -m http.server 8000
# 访问 http://localhost:8000
```

### 部署

推送到 GitHub `main` 分支 → GitHub Pages 自动部署（1–2 分钟生效）。

### 线上验证清单

1. 打开页面无报错（F12 Console）
2. Service Worker 版本号正确（`sw.js?v=xxx`）
3. 排行榜能拉到其他人数据
4. Safari 打开正常（无 CDN 依赖）

---

## 10. 安全检查

- 所有用户输入（昵称等）通过 `escapeHtml` 或 `textContent` 渲染，防 XSS
- Supabase anon key 只是公开只读凭证，不设写权限敏感操作
- 管理员封禁/删除走服务端 banned 字段，前端控制显示
