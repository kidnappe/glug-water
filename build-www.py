# -*- coding: utf-8 -*-
"""咕嘟 — 网页版热更新打包脚本

用法(在仓库根目录):
    python build-www.py

产出:
    version.txt  — 内容为 index.html 里的 APP_VERSION(如 v2.3.0),供 App 比对版本
    www.zip      — index.html + sw.js + manifest.json + icons/(全部),
                   结构与 app 端 assets/www 一致,解压后即可被 WebView 加载

流程:改完 index.html(记得同步 APP_VERSION)→ 跑本脚本 → 推送 web 分支
     → GitHub Pages 自动部署 → App 下次联网启动自动拉新版。
"""
import os
import re
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))

# zip 内包含的顶层条目(与 assets/www 对齐)
ZIP_ENTRIES = [
    "index.html",
    "sw.js",
    "manifest.json",
    "icons",          # 目录,递归包含全部
]

def get_app_version():
    with open(os.path.join(ROOT, "index.html"), "r", encoding="utf-8") as f:
        html = f.read()
    m = re.search(r"APP_VERSION\s*=\s*['\"]([^'\"]+)['\"]", html)
    if not m:
        raise SystemExit("[错误] 未在 index.html 找到 APP_VERSION")
    return m.group(1)

def add_dir(zf, dirpath, arc_base):
    for dirpath_, dirnames, filenames in os.walk(dirpath):
        dirnames.sort()
        for name in sorted(filenames):
            full = os.path.join(dirpath_, name)
            arc = os.path.join(arc_base, os.path.relpath(full, dirpath))
            zf.write(full, arc)
            print("  + %s" % arc.replace("\\", "/"))

def main():
    version = get_app_version()
    print("当前版本: %s" % version)

    # 1. version.txt
    with open(os.path.join(ROOT, "version.txt"), "w", encoding="utf-8") as f:
        f.write(version)
    print("[OK] version.txt -> %s" % version)

    # 2. www.zip
    zip_path = os.path.join(ROOT, "www.zip")
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for entry in ZIP_ENTRIES:
            full = os.path.join(ROOT, entry)
            if not os.path.exists(full):
                raise SystemExit("[错误] 缺少打包条目: %s" % full)
            if os.path.isdir(full):
                add_dir(zf, full, entry)
            else:
                zf.write(full, entry)
                print("  + %s" % entry)
    size_kb = os.path.getsize(zip_path) / 1024.0
    print("[OK] www.zip -> %.1f KB" % size_kb)

    print("\n完成。推送 web 分支后,App 将从此处拉取更新:")
    print("  version.txt -> https://kidnappe.github.io/glug-water/version.txt")
    print("  www.zip     -> https://kidnappe.github.io/glug-water/www.zip")

if __name__ == "__main__":
    main()
