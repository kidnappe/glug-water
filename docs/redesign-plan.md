# 咕嘟「水感治愈」美化方案

> 版本：v2.1.2 → v2.2（视觉升级）
> 状态：方案待确认，未动代码
> 适用文件：`index.html`（单文件，HTML + CSS + JS）
> 铁律约束：零外部依赖、ES5 语法、中文注释、`\uXXXX` 转义、单文件交付、改完需更新 `APP_VERSION` / `SW_VERSION` / `SW_DEPLOY` 并备份

---

## 0. 一句话结论

把「模板蓝 + 白卡片 + 统一阴影」的通用工具风，升级为**「水感治愈」**——主色落在水的蓝与植物的绿之间（青碧），让首页（水）与花园（植物）共享同一条色带；把签名元素「水杯」从线框描边升级为有质感、有高光的玻璃杯；统一圆角/阴影/字号/动效，补齐全站无障碍与 `prefers-reduced-motion` 兜底。

---

## 1. 现状诊断（基于 index.html 逐行审查）

### 1.1 视觉问题（高优先级）

| # | 问题 | 位置 | 说明 |
|---|------|------|------|
| V1 | 主色 `#4A90E2` 是教科书级模板蓝，无品牌记忆点 | `index.html:15` | 与"水"的联想割裂；花园页的绿（植物）与主色无色彩逻辑关系 |
| V2 | 所有卡片统一 `box-shadow: 0 2px 8px rgba(0,0,0,.06)` | `index.html:36,116` | 单层灰阴影 = 无层级感；深色模式下 `rgba(0,0,0,.3)` 更脏 |
| V3 | 圆角一刀切：卡片 14px、按钮 10px、弹窗 16px、内嵌元素 6-8px | 全文件 | 无同心圆关系，内层和外层半径不匹配 |
| V4 | 水杯是 3px 线框 + 单色水面 | `index.html:122-139` | 签名元素未发光，与"治愈"气质不符 |
| V5 | 内联样式泛滥（30+ 处 `style="..."`） | 首页/设置/排行榜 | 无设计系统，改一个色要改十几处 |
| V6 | emoji 大小混乱（1.25rem/1.375rem/1.5rem 混用） | `index.html:34,105,176,195` | 视觉噪音 |
| V7 | 深色模式 `#1a1a2e` 紫蓝色调与主色无关 | `index.html:51` | 应同一色相、只调明度 |
| V8 | 热力图高亮色 `#1565C0` 与主色不一致 | `index.html:220` | token 不统一 |

### 1.2 无障碍 / 合规问题（web-design-guidelines 审查）

| # | 问题 | 位置 | 修复方向 |
|---|------|------|---------|
| A1 | 图标按钮缺 `aria-label`：排行榜刷新 `⟳`、月份切换 `◀▶`、心情问号 `?` | `index.html:39,47,353,355,483` | 补 `aria-label` |
| A2 | `<div>`/`<span>` 带 `onclick` 当按钮用：心情折叠、排行榜说明、吃药入口、管理删除 | `index.html:272,305,489,490,113` | 改 `<button>` |
| A3 | 表单输入无 `<label>`，弹窗输入框裸奔 | `index.html:208` | 加 `aria-label` 或 `<label>` |
| A4 | 全站无 `prefers-reduced-motion` 兜底，动画极多 | `index.html:129-232` | 全局 `@media (prefers-reduced-motion: reduce)` 关闭位移动画 |
| A5 | `transition: all` 至少 9 处 | `index.html:39,45,46,170,172,187,188,200,202` | 明确 transition 属性 |
| A6 | 动态数字无 `tabular-nums`，喝水时数字跳动 | `index.html:141,109,331` | 加 `font-variant-numeric: tabular-nums` |
| A7 | 深色模式未声明 `color-scheme: dark` | `index.html:51` | 根节点补 `color-scheme` |
| A8 | 无 `meta theme-color` 深色适配、无 `<html>` 级 `color-scheme` | `index.html:4-10` | 补两套 theme-color + 媒体查询 |
| A9 | Toast 无 `aria-live="polite"` | `index.html:503,214` | 补 aria-live |
| A10 | 弹窗无 `role="dialog"` / `aria-modal` / 焦点管理 / Esc 关闭 / 滚动锁定 | `index.html:495-501` | 补 a11y 契约 |
| A11 | 无 `touch-action: manipulation`、无 `-webkit-tap-highlight-color` | `index.html:28` | body 补两条 |
| A12 | 装饰性 emoji 未 `aria-hidden` | 底部导航/心情按钮 | 包裹 `<span aria-hidden="true">` |

### 1.3 动效问题（emil-design-eng 审查）

| # | 问题 | 位置 | 修复方向 |
|---|------|------|---------|
| M1 | 按钮 `:active` 只改 `opacity`，无按压缩放 | `index.html:165` | `transform: scale(0.97)`，opacity 不动 |
| M2 | Toast 只有 opacity 淡入淡出 | `index.html:214` | 位移 + 透明度，200ms ease-out |
| M3 | 弹窗 `display:none → flex` 硬切，无过渡 | `index.html:204-206` | 背景淡入 + 弹窗 scale(0.95→1) 250ms |
| M4 | 心情按钮只有 hover 无 active 反馈 | `index.html:170-172` | 补 `:active` scale |
| M5 | 弹性缓动 `cubic-bezier(0.34,1.56,0.64,1)` 已符合用户偏好 | `index.html:125,147,148` | ✅ 保留，抽成 CSS 变量统一复用 |

---

## 2. 设计方向：水感治愈

### 2.1 设计上下文（impeccable 确认）

- **Target audience**：自己/家人日常使用（手机 + Oppo Watch 2 手表联动），低频高频结合——每天打开 3-10 次记录喝水。
- **Use cases**：快速记录喝水/吃药 → 看进度 → 花园养成 → 排行榜互动 → 历史统计。核心动线是"抬手就能记"。
- **Brand personality**："咕嘟"——喝水声的拟声词。应该是**清凉、治愈、有点可爱**，像夏日清晨的一杯清水，不是严肃的健身追踪器。

### 2.2 领域探索（interface-design）

| 维度 | 内容 |
|------|------|
| Domain | 水、杯子、气泡、涟漪、口渴、补水、植物浇水、连续达标、排行榜 |
| Color world | 水的蓝、植物的绿、玻璃的透、气泡的白、杯壁高光 |
| Signature | **玻璃水杯**（首页主体）：杯壁半透明 + 水面渐变 + 波纹 + 气泡 + 侧高光。全站唯一视觉锚点 |
| Rejecting | 模板蓝 `#4A90E2` → 水青 `#0FA896`；单层灰阴影 → 分层水感阴影；线框杯 → 玻璃杯；纯白卡片 → 微水色卡片 |

### 2.3 签名元素：玻璃杯

现状（`index.html:122-139`）是 3px 纯色线框杯。升级后：

- 杯壁：`rgba(255,255,255,.6)` 半透明 + 1.5px 青边，模拟玻璃
- 水面：`linear-gradient(180deg, #4FD1C5, #0FA896)` 渐变 + 波纹 SVG（现有 `.cup-wave` 保留）+ 气泡（现有 `.bubble` 保留）
- 高光：左侧竖条 `rgba(255,255,255,.8)` 圆角条，模拟玻璃折射
- 水位线：水面上沿加一条浅色波纹线，强化"空气在上、水在下"
- 达标时杯身微弹 + 水面泛光（庆祝感，低频动画）

---

## 3. 设计 Token 系统

### 3.1 色彩（`index.html:14-27` 重写 :root）

| Token | 值 | 用途 |
|-------|-----|------|
| `--water-50` | `#EEFBF8` | 页面背景（替代 `#f0f4fb`） |
| `--water-100` | `#D7EBE4` | 边框/分隔线（替代 `#e0e0e0`） |
| `--water-500` | `#0FA896` | 主色/主按钮（替代 `#4A90E2`） |
| `--water-600` | `#0B7285` | 深水/强调文字（替代 `#3D7BD5`） |
| `--water-300` | `#4FD1C5` | 水面浅色/渐变起点 |
| `--sun-500` | `#F59E0B` | 暖橙：自定义水量、吃药（替代 `#FF9800`） |
| `--leaf-500` | `#2F9E6E` | 花园绿：达标/连续（新增，只用于花园与达标态） |
| `--danger-500` | `#E5484D` | 删除/警示（替代 `#e74c3c`） |
| `--ink-900` | `#17212B` | 主文字（替代 `#333`） |
| `--ink-500` | `#5B6B78` | 次要文字（替代 `#999`） |
| `--ink-300` | `#8A9AA5` | 弱化文字/占位（替代 `#aaa`） |
| `--glass` | `rgba(255,255,255,.6)` | 玻璃杯壁 |

色彩分配遵循 60/30/10：浅水底 + 白卡为主体（60%），水青为结构色（30%），暖橙/花园绿只做语义点缀（10%）。**颜色只表达语义，不做装饰。**

### 3.2 字体与排版（约束：禁止 CDN，只能用系统字体栈）

- 字体栈保持系统级：`-apple-system, 'PingFang SC', 'Segoe UI', 'Microsoft YaHei', sans-serif`
- **数字一律 `font-variant-numeric: tabular-nums`**（今日量、排行 ml、连续天数）——防跳动
- 层级用「字重 + 颜色」分层，不靠字号堆：
  - 主数字：22px / 600 / 墨色
  - 卡片标题：15px / 600 / 墨色
  - 正文：14px / 400 / 墨色
  - 辅助：12px / 400 / 次要色
  - 弱化：11px / 400 / 弱化色

### 3.3 圆角系统（同心圆）

| 层级 | 半径 | 适用 |
|------|------|------|
| 弹窗/底部面板 | 20px | modal |
| 卡片 | 16px | `.card`, `.lb-card` |
| 卡片内按钮/输入 | 12px | `.btn-water`, `.modal input`（外层 16 - 内距 4 = 12） |
| 微控件/标签 | 8px | tabs、tag、小按钮 |

规则：`外层半径 = 内层半径 + padding`，禁止同半径嵌套（V3 修复）。

### 3.4 阴影系统（替代单层灰阴影）

```css
--shadow-card: 0 1px 2px rgba(15,168,150,.06), 0 4px 12px rgba(15,168,150,.08);
--shadow-pop:  0 2px 4px rgba(15,168,150,.08), 0 12px 32px rgba(15,168,150,.14);
```

浅色用「透明青阴影」分层；深色模式**弃用阴影，改用 1px 亮边框**区分层级（`rgba(255,255,255,.08)`）。

### 3.5 动效 Token（emil-design-eng 规范）

```css
--ease-spring: cubic-bezier(0.34,1.56,0.64,1); /* 用户已拍板接受弹性，用于水面/进度条 */
--ease-out:    cubic-bezier(0.23,1,0.32,1);    /* UI 进出场 */
--dur-press:   120ms;  /* 按钮按压 */
--dur-pop:     200ms;  /* 弹窗/小面板 */
--dur-water:   600ms;  /* 水面上涨 */
--dur-slow:    300ms;  /* 大位移/页面过渡 */
```

---

## 4. 组件改造清单

### 4.1 首页进度卡（V1-V5）

| Before | After | Why |
|--------|-------|-----|
| `#4A90E2` 线框杯 + 平涂水面 | 玻璃杯：半透杯壁 + 水面渐变 + 高光条 | 签名元素升级为全站记忆点 |
| 数字 22px 普通字体 | `tabular-nums` 等宽数字 | 记录喝水时数字不跳 |
| 卡片 `0 2px 8px rgba(0,0,0,.06)` | `--shadow-card` 透明青分层阴影 | 更柔和、与主色同源 |
| 「还差 X ml」灰字 | 深水色 `--water-600` 引导文案 | 主信息用品牌色强调 |
| 达标红字 `#e74c3c` | 花园绿 `--leaf-500` + 🎉 | 达标是正面事件，用绿不用红 |

### 4.2 水量按钮（M1）

| Before | After | Why |
|--------|-------|-----|
| `:active { opacity: .7 }` | `:active { transform: scale(0.97); }` + 120ms | 物理按压反馈，不靠透明度 |
| 四个按钮同色（250/350/500/1000 全蓝） | 1000ml 用暖橙（`--sun-500`） | 大剂量是"特殊操作"，用色区分层级 |
| 圆角 10px | 圆角 12px（同心圆） | 与卡片 16px 匹配 |

### 4.3 底部导航（A2/A12）

- emoji 包 `<span aria-hidden="true">`，文字标签保留
- 激活态从「纯变色」→「变色 + 水波纹小圆点指示器」（纯 CSS `::after`）
- 图标区高度保持 ≥ 44px 命中区（A11 修复）

### 4.4 排行榜（V6/A1/A6）

| 位置 | 现状 | 改为 |
|------|------|------|
| `index.html:483` 刷新按钮 | `⟳` 无标签 | `<button aria-label="刷新排行榜">` |
| `index.html:109` 水量 | 普通数字 | `tabular-nums` |
| `index.html:101-104` 金银铜 | 色字 | 前三名徽章胶囊（圆角 8px 浅底 + 深色字） |
| `index.html:2611` 管理删除 ✕ | `<span onclick>` | `<button>` + aria-label="删除用户" |
| 我的行 `index.html:111` | 蓝底高亮 | 水青边框 + `--water-50` 底 + 2px 内发光 |

### 4.5 花园页（V7）

- 深色背景卡片 → 与全局一致的水青主题
- 「连续达标」大数字加 `tabular-nums`，达标数字用 `--leaf-500`
- 植物 emoji 保留（这是可爱感来源），但配件动画（`index.html:155-162`）加 `prefers-reduced-motion` 降级

### 4.6 历史页

- 图表切换按钮（`index.html:186-188`）：`transition: all` → 指定属性；激活态用水青
- 热力图高亮色 `#1565C0` → `--water-500`（V8）
- 统计数字全部 `tabular-nums`

### 4.7 弹窗 / Toast（A3/A9/A10/M2/M3）

| 现状 | 改为 |
|------|------|
| `display:none → flex` 硬切 | 遮罩淡入 200ms + 弹窗 `scale(0.95→1)` 250ms `--ease-out` |
| 无 a11y | `role="dialog" aria-modal="true"` + Esc 关闭 + 焦点回到触发元素 + `overscroll-behavior: contain` |
| 输入框无标签 | `<label>` 或 `aria-label` |
| Toast 仅 opacity | `translateY(8px) + opacity` 200ms；`aria-live="polite"` |
| 弹窗圆角 16px | 20px（弹窗层最大半径） |

### 4.8 设置页（V5）

- 30+ 处内联样式 → 抽取为类：`.setting-icon`、`.setting-arrow` 已存在，补 `.danger-label`、`.switch` 等
- 危险操作（重置、断开）统一 `--danger-500` token，删除裸 hex

---

## 5. 深色模式重构（V7/A7/A8）

现状 `index.html:51-96` 是"另一套紫蓝色"，且逐条覆盖。重构原则：

1. **同一色相，只调明度**：深色主色 = 水青暗化（如 `#0B3B38`），页面底 `#0F1715`，卡片 `#16211F`，边框 `rgba(255,255,255,.08)`
2. 根节点声明 `color-scheme: dark`（修滚动条/原生控件）
3. `meta theme-color` 两套：`#0FA896`（浅）/ `#0F1715`（深），用 `media="(prefers-color-scheme: dark)"` 切换
4. 阴影弃用 → 亮边框分层（`index.html:56-58` 的 `rgba(0,0,0,.3)` 阴影全部移除）
5. 语义色深色版降饱和度（红/橙/绿各 -10% 饱和度）

---

## 6. 无障碍修复清单（web-design-guidelines 汇总）

| 位置 | 修复 |
|------|------|
| `index.html:39,47,353,355,483` | 图标按钮补 `aria-label` |
| `index.html:272,305,489,490,113` | `div/span onclick` → `<button>` |
| `index.html:208` | 输入框补 label |
| 全站动画 | `@media (prefers-reduced-motion: reduce)`：关闭位移/缩放动画，保留透明度过渡 |
| `index.html:39,45,46,170,172,187,188,200,202` | `transition: all` → 明确属性 |
| `index.html:141,109,331` + 全部动态数字 | `tabular-nums` |
| `index.html:28` | 加 `touch-action: manipulation; -webkit-tap-highlight-color: transparent;` |
| `index.html:495-501` | 弹窗完整 a11y 契约 |
| `index.html:503` | Toast `aria-live="polite"` |
| 装饰性 emoji | `aria-hidden` |

---

## 7. 实施路线图

### P0 — 设计系统落地（一次提交，改动最大）
1. 重写 `:root` token（色彩/阴影/动效/圆角变量）
2. 全部裸 hex 替换为 token（grep `#[0-9a-fA-F]{6}` 逐个替换）
3. 深色模式重构 + `color-scheme` + 双 theme-color
4. 更新 `APP_VERSION` → v2.2.0、`SW_VERSION`、`SW_DEPLOY`、备份到 `backup/v2.2.0/`、写 CHANGELOG

### P1 — 组件升级
5. 玻璃水杯（签名元素）
6. 按钮按压反馈 + 1000ml 橙色
7. 底部导航激活态水波纹
8. 弹窗/Toast 动效 + a11y
9. 排行榜徽章 + 我的行高亮

### P2 — 合规收尾
10. div→button 全部替换
11. aria-label / aria-live / aria-hidden 补齐
12. `prefers-reduced-motion` 全局兜底
13. `tabular-nums` 全站数字
14. 内联样式抽取收尾

> 每次提交前问用户「要不要写进 CHANGELOG.md？」（遵循 AGENTS.md 发布流程）
> 手表端（`drink-water-watch` 项目）不在本次范围，但排行榜/同步逻辑不动，两端兼容不受影响。

---

## 8. 风险与约束

| 风险 | 应对 |
|------|------|
| 单文件巨大（2.4 万行级别） | 分 P0/P1/P2 三次提交，每次改完本地 `python -m http.server` 验证 |
| ES5 + `\uXXXX` 约束 | 新代码保持 `var`/`function` 风格；emoji 文案用 `\uXXXX` |
| 禁止外部 CDN | 字体只用系统栈；所有装饰纯 CSS 实现 |
| 手表端兼容 | 不动同步/数据逻辑；只改视觉，`date_key`/`pill_today` 相关 SELECT 不碰 |
| 深色模式回归 | P0 后逐一页面走查（F12 切深色模式） |
| Safari 拦截 | 无 CDN 引入，无此风险 |

---

*方案由 UI 五件套协同产出：impeccable（方向）、interface-design（系统）、web-design-guidelines（合规）、emil-design-eng（动效）、make-interfaces-feel-better（细节）。*
