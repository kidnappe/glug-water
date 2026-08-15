# 第二台设备初始化与多设备同步开发文档

> 给第二台设备上的 AI 执行。目标：把第二台设备搭成与主设备一致的「三分支三文件夹」开发环境，并与 GitHub 双向同步。
> 主设备已把最新进度推送到 GitHub 仓库 `kidnappe/glug-water`。

---

## 〇、总体结构（先理解再动手）

GitHub 仓库 `kidnappe/glug-water` 有 3 个分支，对应 3 个本地文件夹，**每个文件夹固定检出 1 个分支，互不干扰**：

| 分支 | 本地文件夹 | 内容 |
|------|-----------|------|
| web | `E:\code\glug-water` | 网页版（PWA）：index.html、sw.js、manifest.json、icons/ 等 |
| app | `E:\code\glug-water-app` | 手机套壳版：android/ 工程、README、CHANGELOG |
| watch | `E:\code\drink-water-watch` | 手表版（Oppo Watch 2）：index.html、android/ 等 |

**核心规则：三个文件夹各守一个分支，不切分支、不合并，各自 Commit + Push。**

---

## 一、前置检查

1. 已安装 Git（命令行可用：打开 PowerShell 输入 `git --version` 有输出）
2. 已安装 GitHub Desktop 并登录（`kidnappe` 账号）
3. 能访问 GitHub（网络波动时重试）

---

## 二、初始化三个文件夹（一次性）

在 PowerShell 中逐条执行（在 `E:\code` 下操作）：

```powershell
cd E:\code

git clone -b web   https://github.com/kidnappe/glug-water.git glug-water
git clone -b app   https://github.com/kidnappe/glug-water.git glug-water-app
git clone -b watch https://github.com/kidnappe/glug-water.git drink-water-watch
```

> `-b 分支` = 克隆时直接锁定该分支，下载完即该分支最新内容，无需切换。
> 若 `E:\code` 下已有同名文件夹且不为空，先改名或移动到别处再克隆。

---

## 三、验证（每步都要确认）

```powershell
# 1. 三个文件夹都存在
cd E:\code
dir

# 2. 每个仓库分支正确、历史一致（应看到 web / app / watch）
cd glug-water;       git branch; git log --oneline -3
cd ..\glug-water-app; git branch; git log --oneline -3
cd ..\drink-water-watch; git branch; git log --oneline -3

# 3. remote 正确（应显示 https://github.com/kidnappe/glug-water.git）
git -C E:\code\glug-water remote -v
```

验证点：
- `glug-water\index.html` 存在，且文件顶部标题为「咕嘟」
- `glug-water-app\android` 存在
- `drink-water-watch\index.html` 存在

---

## 四、⚠️ keystore 拷贝（构建签名 APK 必需，文件不在 git 里）

`app-release.keystore` 与 `keystore.properties` 被 .gitignore 忽略、**不会随克隆下载**。需要从主设备拷贝到第二台设备：

```
源（主设备）：
  E:\code\glug-water-app\android\app-release.keystore
  E:\code\glug-water-app\android\keystore.properties

目标（第二台设备）：
  E:\code\glug-water-app\android\app-release.keystore
  E:\code\glug-water-app\android\keystore.properties
```

- 拷贝方式：U 盘 / 网盘 / 聊天传输均可
- **密码从 keystore.properties 里读，不要外发到公开渠道**
- 没有 keystore 时只能构建 debug 版（`gradlew assembleDebug`），release 构建会失败

---

## 五、日常同步流程（两台设备都一样）

```
开工：Pull（下载最新）
   cd E:\code\glug-water; git pull origin web
   cd E:\code\glug-water-app; git pull origin app
   cd E:\code\drink-water-watch; git pull origin watch

干完活：Push（上传）
   git add -A
   git commit -m "描述改了什么"
   git push origin <当前分支>
```

- **永远不要 force push**（`git push -f`）——会覆盖另一台设备的提交
- Push 被拒绝（non-fast-forward）= 远端有新提交 → 先 `git pull` 合并 → 再 `git push`
- Pull 冲突 = 两台改了同一文件 → 手动解决，保留需要的版本
- 建议：**页面源 index.html 固定在一台设备改**；app 仓库的 `assets/www/index.html` 由 `sync-www.bat` 从 `..\glug-water\index.html` 同步（跑 `E:\code\glug-water-app\sync-www.bat`）

---

## 六、常见问题

| 现象 | 原因 | 处理 |
|------|------|------|
| Pull/Push 报 "Repository not found" | remote 还是旧地址 drink-water-web | `git remote set-url origin https://github.com/kidnappe/glug-water.git` |
| Pull 报 "local changes would be overwritten" | 本地有未提交改动 | `git stash` 暂存改动，Pull 后再 `git stash pop` |
| 找不到文件 | GitHub Desktop 界面不显示文件列表 | `Repository → Show in Explorer` 打开真实文件夹 |
| 想重来 | 环境搞乱了 | 删除对应文件夹后重新 clone（本地未推送的改动会丢，先确认） |

---

## 七、三个仓库的构建命令（需要时）

```powershell
# 手机套壳版（app）
cd E:\code\glug-water-app\android
.\gradlew.bat assembleDebug      # 或 assembleRelease（需 keystore）

# 手表版（watch）
cd E:\code\drink-water-watch\android
.\gradlew.bat assembleDebug      # 或 assembleRelease
```

---

## 八、完成后自检清单

- [ ] 三个文件夹都在 `E:\code` 下，分支分别是 web / app / watch
- [ ] 三个仓库 `git log --oneline -3` 与主设备一致（能看到「咕嘟」相关提交）
- [ ] keystore 已拷贝到 `glug-water-app\android\`
- [ ] remote 全部指向 `kidnappe/glug-water.git`
- [ ] 明白日常流程：开工 Pull，收工 Push，禁止 force push

执行完毕后向用户汇报：三个仓库初始化结果 + 自检清单勾选情况。
