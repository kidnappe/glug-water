package com.drinkwater.web;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

/* 每周一 9:00 自动向 Supabase 发一次请求，防止免费项目因 7 天无 API 请求被暂停。
 * 保活只需任意一次 REST 请求，不写入任何假数据，不污染喝水记录。 */
public class KeepAliveReceiver extends BroadcastReceiver {
    public static final String ACTION_KEEPALIVE = "com.drinkwater.web.KEEPALIVE";
    private static final String TAG = "DrinkWaterKeepAlive";
    private static final String PREFS = "supabase_prefs";
    private static final String KEY_URL = "url";
    private static final String KEY_KEY = "key";
    private static final int REQUEST_CODE = 0;

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (!ACTION_KEEPALIVE.equals(intent.getAction())) return;

        /* 后台线程发请求（Receiver 回调运行在主线程，不能直接网络） */
        new Thread(new Runnable() {
            @Override
            public void run() {
                SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String url = sp.getString(KEY_URL, "");
                String key = sp.getString(KEY_KEY, "");
                if (url.isEmpty() || key.isEmpty()) {
                    Log.d(TAG, "skip keepalive: supabase not configured");
                    return;
                }
                try {
                    URL u = new URL(url.replaceAll("/+$", "") + "/rest/v1/leaderboard?select=user_id&limit=1");
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("apikey", key);
                    conn.setRequestProperty("Authorization", "Bearer " + key);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    int code = conn.getResponseCode();
                    Log.d(TAG, "keepalive HTTP " + code + " -> " + url);
                    conn.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "keepalive failed: " + e.getMessage());
                }
            }
        }).start();

        /* 重排下一周 */
        scheduleNext(context);
    }

    /* 排到下一个周一 9:00（一次性精确闹钟，触发后自动重排，逻辑与喝水提醒一致） */
    public static void scheduleNext(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, KeepAliveReceiver.class);
        intent.setAction(ACTION_KEEPALIVE);
        PendingIntent pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.WEEK_OF_YEAR, 1);
        }
        long triggerAt = cal.getTimeInMillis();
        Log.d(TAG, "keepalive scheduled at " + cal.getTime().toString());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    /* 配置有效时确保已调度（App 启动 / 开机恢复时调用，幂等：相同 PendingIntent 覆盖） */
    public static void ensureScheduled(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String url = sp.getString(KEY_URL, "");
        String key = sp.getString(KEY_KEY, "");
        if (url.length() > 10 && key.length() > 10) {
            scheduleNext(context);
        } else {
            cancel(context);
        }
    }

    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(context, KeepAliveReceiver.class);
        intent.setAction(ACTION_KEEPALIVE);
        PendingIntent pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        pi.cancel();
    }

    /* 保存 Supabase 配置（JS 桥调用），并同步调度/取消保活闹钟 */
    public static void saveConfig(Context context, String url, String key) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_URL, url == null ? "" : url)
                .putString(KEY_KEY, key == null ? "" : key).apply();
        ensureScheduled(context);
    }
}
