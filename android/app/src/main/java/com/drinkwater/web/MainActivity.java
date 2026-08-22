package com.drinkwater.web;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> mFilePathCallback;
    private static final String TAG = "DrinkWater";
    private static final int REQ_FILE_CHOOSER = 2002;

    /* ========== 热更新配置 ==========
     * 机制：启动先加载本地版本（已下载新版 > 内置兜底版），后台比对远程 version.txt，
     *       有新版则下载 www.zip 解压到私有目录，下次加载即生效；任何失败静默回退，不影响启动。
     * 远程文件由 Web 仓库根目录 build-www.py 生成，推送后 GitHub Pages 自动部署。 */
    private static final String UPDATE_BASE = "https://kidnappe.github.io/glug-water/";
    private static final String UPDATE_VERSION_URL = UPDATE_BASE + "version.txt";
    private static final String UPDATE_ZIP_URL = UPDATE_BASE + "www.zip";
    private static final String PREFS_NAME = "drink_water_update";
    private static final String PREFS_APPLIED_VERSION = "applied_version";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge：内容延伸到状态栏/导航栏之后（必须在 setContentView 之前调用才可靠）
        // 状态栏区域由页面内容（蓝色 appbar / 深色背景）覆盖，保证颜色与页面一致
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // ========== 崩溃自诊：检查上次是否崩溃 ==========
        String crashLog = CrashHandler.readAndClearCrash(this);
        if (crashLog != null) {
            showCrashScreen(crashLog);
            return; // 显示崩溃信息，不启动 WebView
        }

        // ========== 注册全局崩溃抓取器 ==========
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));

        // ========== 正常启动 WebView ==========
        try {
            webView = new WebView(this);
            // 去掉启动白闪：WebView 背景设为与页面一致（Web 版浅色背景 #f0f4fb）
            webView.setBackgroundColor(Color.parseColor("#f0f4fb"));
            setContentView(webView);

            // WebView 配置
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);
            settings.setMinimumFontSize(8);
            settings.setTextZoom(100);
            /* 禁用 WebView 强制暗化：页面深浅完全由 CSS 控制，避免系统深色时双重暗化 */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                settings.setForceDark(WebSettings.FORCE_DARK_OFF);
            }

            String ua = settings.getUserAgentString();
            settings.setUserAgentString(ua + " DrinkWaterWeb/1.0");

            webView.setInitialScale(100);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return false;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    /* 页面加载完成后注入系统深浅状态（供 auto 模式可靠跟随） */
                    injectSystemDark();
                }

                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    Log.e(TAG, "WebView error: " + errorCode + " " + description + " @ " + failingUrl);
                }
            });

            webView.setWebChromeClient(new WebChromeClient() {
                /* 支持页面 <input type="file">：WebView 默认不处理文件选择，必须在这里接系统选择器 */
                @Override
                public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback,
                                                 FileChooserParams fileChooserParams) {
                    if (mFilePathCallback != null) {
                        mFilePathCallback.onReceiveValue(null);
                    }
                    mFilePathCallback = filePathCallback;
                    Intent intent = fileChooserParams.createIntent();
                    /* 放宽类型：部分 ROM 对 application/json 过滤后找不到选择器 */
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES,
                            new String[]{"application/json", "text/plain", "application/octet-stream"});
                    try {
                        startActivityForResult(intent, REQ_FILE_CHOOSER);
                        return true;
                    } catch (Exception e) {
                        mFilePathCallback = null;
                        Log.e(TAG, "file chooser failed", e);
                        return false;
                    }
                }
            });

            // 注册 JS 桥接
            webView.addJavascriptInterface(new ReminderBridge(this), "AndroidBridge");

            // 创建通知渠道 (API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) {
                    ReminderReceiver.ensureChannel(nm);
                }
            }

            // Android 13+ (API 33) 需要运行时请求通知权限，否则提醒通知不显示
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
                }
            }

            // 启动时恢复提醒闹钟由 BOOT_COMPLETED 广播处理（RestoreAfterRebootReceiver），
            // 这里不再重排，避免点通知打开 App 时把已排好的闹钟相位推迟
            // 但确保每周一保活闹钟已调度（幂等，防数据库暂停）
            KeepAliveReceiver.ensureScheduled(this);

            // 先加载本地可用版本（秒开），再后台检查更新
            loadLocalPage();
            checkForUpdateAsync();

        } catch (Exception e) {
            Log.e(TAG, "onCreate crashed", e);
            if (webView != null) {
                String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
                if (msg.length() > 200) msg = msg.substring(0, 200);
                webView.loadDataWithBaseURL(null,
                        "<html><body style='background:#f0f4fb;color:#e74c3c;padding:20px;font-family:sans-serif;'>" +
                                "<h2>⚠️ 启动失败</h2><p style='color:#8a9aaa;font-size:14px;'>" + msg +
                                "</p><p style='color:#5a6a7a;font-size:10px;margin-top:8px;'>" +
                                "请截图后发送给开发者</p></body></html>",
                        "text/html", "UTF-8", null);
            }
        }
    }

    /* ========== 页面加载 ========== */

    /** 加载本地可用版本：优先私有目录已下载的新版，否则回退内置 assets 版（断网兜底） */
    private void loadLocalPage() {
        File localIndex = new File(getFilesDir(), "www/index.html");
        if (localIndex.exists()) {
            webView.loadUrl(Uri.fromFile(localIndex).toString());
            Log.d(TAG, "load local updated page: " + localIndex.getAbsolutePath());
        } else {
            webView.loadUrl("file:///android_asset/www/index.html");
        }
    }

    /* ========== 热更新 ========== */

    /** 后台检查远程版本，有新版则下载解压并重载页面（失败静默，绝不影响启动） */
    private void checkForUpdateAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String remote = fetchVersion();
                    if (remote == null) return; // 网络失败/文件不存在 → 保持现状

                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    String applied = prefs.getString(PREFS_APPLIED_VERSION, null);
                    if (applied != null && compareVersion(remote, applied) <= 0) return; // 已是最新

                    File zip = new File(getCacheDir(), "www.zip");
                    if (!downloadFile(UPDATE_ZIP_URL, zip)) return;
                    boolean ok = unzipToPrivateDir(zip);
                    zip.delete();
                    if (!ok) return;

                    prefs.edit().putString(PREFS_APPLIED_VERSION, remote).apply();
                    Log.d(TAG, "update applied: " + remote);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (webView != null) {
                                /* 若当前正显示内置兜底页，重载到新版；若已是最新则无需动作 */
                                webView.reload();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "update check failed", e);
                }
            }
        }).start();
    }

    /** 拉取远程版本号（version.txt 首行），失败返回 null */
    private String fetchVersion() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(UPDATE_VERSION_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != 200) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            String line = br.readLine();
            br.close();
            return line == null ? null : line.trim();
        } catch (Exception e) {
            Log.e(TAG, "fetch version failed", e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 下载远程文件到本地，成功返回 true */
    private boolean downloadFile(String urlStr, File target) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != 200) return false;
            InputStream is = new BufferedInputStream(conn.getInputStream());
            OutputStream os = new BufferedOutputStream(new FileOutputStream(target));
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            os.close();
            is.close();
            return target.length() > 0;
        } catch (Exception e) {
            Log.e(TAG, "download failed: " + urlStr, e);
            if (target.exists()) target.delete();
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 解压 www.zip 到私有目录：先写临时目录，成功后原子替换 www/，防止半成品 */
    private boolean unzipToPrivateDir(File zip) {
        File base = getFilesDir();
        File tmp = new File(base, "www_tmp");
        File target = new File(base, "www");
        try {
            deleteRecursive(tmp);
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                /* zip slip 防护：拒绝跳出目标目录的路径 */
                if (name.contains("..")) {
                    zis.closeEntry();
                    continue;
                }
                File out = new File(tmp, name);
                if (entry.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                OutputStream os = new BufferedOutputStream(new FileOutputStream(out));
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) > 0) os.write(buf, 0, n);
                os.close();
                zis.closeEntry();
            }
            zis.close();
            if (!new File(tmp, "index.html").exists()) {
                deleteRecursive(tmp);
                return false;
            }
            deleteRecursive(target);
            return tmp.renameTo(target);
        } catch (Exception e) {
            Log.e(TAG, "unzip failed", e);
            deleteRecursive(tmp);
            return false;
        }
    }

    /** 递归删除文件/目录（只用于私有目录内） */
    private void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        f.delete();
    }

    /** 版本号比较（v1.2.3 三段数字），a>b 返回正数，a<b 返回负数，相等返回 0 */
    private int compareVersion(String a, String b) {
        int[] va = parseVersion(a);
        int[] vb = parseVersion(b);
        for (int i = 0; i < 3; i++) {
            if (va[i] != vb[i]) return va[i] < vb[i] ? -1 : 1;
        }
        return 0;
    }

    /** 解析 "v2.3.0" → [2,3,0]，容错任意格式 */
    private int[] parseVersion(String v) {
        int[] out = new int[]{0, 0, 0};
        if (v == null) return out;
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        String[] parts = s.split("\\.");
        for (int i = 0; i < parts.length && i < 3; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("\\D", ""));
            } catch (Exception e) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** 显示上次崩溃的详细信息 */
    private void showCrashScreen(String crashLog) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#f0f4fb"));
        sv.setPadding(20, 20, 20, 20);

        TextView tv = new TextView(this);
        tv.setTextColor(Color.parseColor("#e74c3c"));
        tv.setTextSize(12);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setLineSpacing(4, 1.2f);
        tv.setText("⚠️ 上次启动崩溃了\n\n" + crashLog +
                "\n\n——————————\n请截图发给开发者。\n关闭此页再打开应用即可重新尝试。");

        sv.addView(tv);
        setContentView(sv);
    }

    @Override
    public void onBackPressed() {
        /* 修复全面屏手势返回失效：页面可回退则回退，否则正常退出（回桌面） */
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        /* Android 15+ edge-to-edge 已由 WindowCompat.setDecorFitsSystemWindows(false) 处理，无需旧 flag */
    }

    /* 系统深浅切换（定时深色模式/手动切换时触发）：重新注入并让页面 auto 模式跟随 */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        injectSystemDark();
    }

    /* 读取系统当前是否为深色，注入页面 window.__setSystemDark() */
    private void injectSystemDark() {
        if (webView == null) return;
        try {
            boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            final String js = "window.__setSystemDark(" + dark + ");";
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript(js, null);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "injectSystemDark failed", e);
        }
    }

    /* 导入数据：文件选择器返回后读取 JSON，注入页面 handleNativeImport() */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        /* ① WebView <input type=file> 标准流程：结果回传给页面 input.files */
        if (requestCode == REQ_FILE_CHOOSER) {
            if (mFilePathCallback != null) {
                Uri result = (data != null && resultCode == RESULT_OK && data.getData() != null)
                        ? data.getData() : null;
                mFilePathCallback.onReceiveValue(result != null ? new Uri[]{result} : null);
                mFilePathCallback = null;
            }
            return;
        }

        /* ② AndroidBridge.importData() 原生选择器流程（兼容保留） */
        if (requestCode == ReminderBridge.REQ_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                String json = ReminderBridge.readStream(is);
                is.close();
                /* 转义后作为 JS 字符串字面量注入，避免特殊字符破坏页面 */
                String js = "handleNativeImport(" + JSONObject.quote(json) + ")";
                webView.evaluateJavascript(js, null);
                Log.d(TAG, "import injected, " + json.length() + " chars");
            } catch (Exception e) {
                Log.e(TAG, "import read failed", e);
            }
        }
    }
}
