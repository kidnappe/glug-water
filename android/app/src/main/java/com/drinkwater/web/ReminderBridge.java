package com.drinkwater.web;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.JavascriptInterface;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class ReminderBridge {
    public static final int REQ_IMPORT = 2001;
    private static final String TAG = "DrinkWaterBridge";

    private final Context context;
    private final Activity activity;

    public ReminderBridge(Activity activity) {
        this.activity = activity;
        this.context = activity.getApplicationContext();
    }

    /** 安排提醒：首次调度对齐到整分钟，触发后由 Receiver 自动重排；
     *  startHour/endHour 为提醒时段（支持跨天，如 22-8 表示夜间提醒） */
    @JavascriptInterface
    public void scheduleReminder(int intervalMinutes, int startHour, int endHour) {
        long intervalMs = intervalMinutes * 60 * 1000L;
        ReminderReceiver.cancel(context);
        ReminderReceiver.schedule(context, intervalMs, true, startHour, endHour); /* 首次：对齐整分钟 */
    }

    /** 取消所有待提醒 */
    @JavascriptInterface
    public void cancelReminder() {
        ReminderReceiver.cancel(context);
    }

    /** 短振动反馈 (watch haptic) */
    @JavascriptInterface
    public void vibrate() {
        Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vib == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vib.vibrate(60);
        }
    }

    /** 导出数据：把 JSON 备份写到系统「下载」目录（WebView 不支持 <a download>） */
    @JavascriptInterface
    public void exportData(String fileName, String json) {
        if (fileName == null || fileName.isEmpty()) fileName = "drink_water_backup.json";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                /* Android 10+：MediaStore 写公共 Downloads，无需存储权限 */
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("create MediaStore entry failed");
                OutputStream os = context.getContentResolver().openOutputStream(uri);
                if (os == null) throw new IOException("openOutputStream failed");
                os.write(json.getBytes("UTF-8"));
                os.flush();
                os.close();
                Log.d(TAG, "exported to MediaStore: " + fileName);
            } else {
                /* Android 8.1/9：直接写公共 Downloads（Manifest 已声明 WRITE_EXTERNAL_STORAGE maxSdk 28） */
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, fileName);
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(json.getBytes("UTF-8"));
                fos.flush();
                fos.close();
                Log.d(TAG, "exported to file: " + f.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "export failed", e);
        }
    }

    /** 导入数据：打开系统文件选择器选 JSON，读回内容回调页面 handleNativeImport(json) */
    @JavascriptInterface
    public void importData() {
        if (activity == null) return;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        /* 用通配类型确保任何文件管理器都能响应；JSON 备份可能被识别为 text/plain 等 */
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            activity.startActivityForResult(intent, REQ_IMPORT);
            Log.d(TAG, "import picker opened");
        } catch (Exception e) {
            Log.e(TAG, "open file picker failed", e);
        }
    }

    /** 保存 Supabase 配置到原生（JS 保存配置时调用）：用于每周一自动保活防数据库暂停 */
    @JavascriptInterface
    public void setSupabaseConfig(String url, String key) {
        KeepAliveReceiver.saveConfig(context, url, key);
    }

    /* MainActivity.onActivityResult 读文件后调用：把 JSON 内容注入页面 */
    public static String readStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        return sb.toString();
    }
}
