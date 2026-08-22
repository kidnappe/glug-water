@echo off
rem ============================================================
rem 咕嘟（Glug-water）Android 套壳 — 页面同步脚本
rem 从 Web 版仓库 (glug-water) 复制整个 www 目录到 APK 资源
rem （index.html + sw.js + manifest.json + icons/ 完整资源树）
rem 用法：Web 版更新后运行本脚本，再重新构建 APK
rem 说明：assets/www 是内置兜底版；联网后 App 会从 GitHub Pages
rem       热更新到最新版，本目录只需保持"最后一次打包时"的版本。
rem ============================================================
setlocal
set SRC_DIR=%~dp0..\glug-water
set DST_DIR=%~dp0android\app\src\main\assets\www

if not exist "%SRC_DIR%\index.html" (
  echo [错误] 找不到 Web 版页面：%SRC_DIR%\index.html
  echo        请确认 glug-water 与本目录同级。
  exit /b 1
)

rem 清空旧的 assets/www，再整目录复制，保证与 Web 版一致
if exist "%DST_DIR%" rd /s /q "%DST_DIR%"
xcopy /E /I /Y "%SRC_DIR%\index.html" "%DST_DIR%" >nul
xcopy /E /I /Y "%SRC_DIR%\sw.js" "%DST_DIR%" >nul
xcopy /E /I /Y "%SRC_DIR%\manifest.json" "%DST_DIR%" >nul
xcopy /E /I /Y "%SRC_DIR%\icons" "%DST_DIR%\icons" >nul
if errorlevel 1 (
  echo [错误] 复制失败
  exit /b 1
)

echo [OK] 已同步完整 www 目录到 assets/www/
echo      来源: %SRC_DIR%
echo      目标: %DST_DIR%
endlocal
