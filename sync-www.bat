@echo off
rem ============================================================
rem 咕嘟（Glug-water）Android 套壳 — 页面同步脚本
rem 从 Web 版仓库 (glug-water) 复制 index.html 到 APK 资源
rem 用法：Web 版更新后运行本脚本，再重新构建 APK
rem ============================================================
setlocal
set SRC=%~dp0..\glug-water\index.html
set DST=%~dp0android\app\src\main\assets\www\index.html

if not exist "%SRC%" (
  echo [错误] 找不到 Web 版页面：%SRC%
  echo        请确认 glug-water 与本目录同级。
  exit /b 1
)

copy /Y "%SRC%" "%DST%" >nul
if errorlevel 1 (
  echo [错误] 复制失败
  exit /b 1
)

echo [OK] 已同步 index.html 到 assets/www/
echo      来源: %SRC%
echo      目标: %DST%
endlocal
