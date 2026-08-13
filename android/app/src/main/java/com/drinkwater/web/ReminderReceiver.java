package com.drinkwater.web;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "drink_reminder";
    public static final String ACTION_REMINDER = "com.drinkwater.web.REMINDER";
    private static final String TAG = "DrinkWaterReminder";
    private static final String PREFS = "reminder_prefs";
    private static final String KEY_INTERVAL = "interval_ms";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive action=" + (intent != null ? intent.getAction() : "null")
                + " pid=" + android.os.Process.myPid());
        /* 设备重启：系统会清空所有闹钟，开机后自动恢复提醒（需 RECEIVE_BOOT_COMPLETED 权限） */
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "BOOT_COMPLETED: restoring reminder");
            restoreAfterReboot(context);
            /* 同时恢复每周一保活闹钟（防数据库暂停） */
            KeepAliveReceiver.ensureScheduled(context);
            return;
        }
        if (ACTION_REMINDER.equals(intent.getAction())) {
            Log.d(TAG, "showNotification() called");
            showNotification(context);
            Log.d(TAG, "showNotification() finished");
            /* 一次性精确闹钟：触发后重排下一次，保证间隔精确 */
            rescheduleNext(context);
        }
    }

    private void showNotification(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ensureChannel(nm);

        Intent launchIntent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        PendingIntent pi = PendingIntent.getActivity(context, 0, launchIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("\uD83D\uDCA7 该喝水了！")
                .setContentText("记得补充水分哦 \uD83D\uDCA7")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        nm.notify(1001, builder.build());
    }

    public static void ensureChannel(NotificationManager nm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "\u559D\u6C34\u63D0\u9192", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("\u5B9A\u65F6\u559D\u6C34\u63D0\u9192\u901A\u77E5");
            nm.createNotificationChannel(ch);
        }
    }

    /* 注册提醒：优先精确闹钟（setExactAndAllowWhileIdle，一次性，触发后自行重排）
     * Android 12+ 的 setInexactRepeating 会被系统大幅延迟（省电对齐），喝水提醒需要准时
     * alignToMinute=true  → 首次调度：对齐到下一个整分钟（固定相位）
     * alignToMinute=false → 触发后重排：保持整分钟相位 + intervalMs（避免相位漂移和间隔错乱） */
    public static void schedule(Context context, long intervalMs, boolean alignToMinute) {
        if (intervalMs <= 0) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putLong(KEY_INTERVAL, intervalMs).apply();

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        long now = System.currentTimeMillis();
        long triggerAt;
        if (alignToMinute) {
            triggerAt = ((now / 60000) + 1) * 60000;              // 首次：下一个整分钟
        } else {
            triggerAt = ((now / 60000) * 60000) + intervalMs;     // 重排：当前整分钟 + 间隔
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
            Log.d(TAG, "schedule exact: " + triggerAt + " (+" + (triggerAt - now) + "ms, interval=" + intervalMs + ")");
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            Log.d(TAG, "schedule inexact fallback: " + triggerAt);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerAt, intervalMs, pi);
        }
    }

    /* 触发后重排下一次（间隔从 SharedPreferences 读取） */
    public static void rescheduleNext(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long intervalMs = sp.getLong(KEY_INTERVAL, 0);
        Log.d(TAG, "rescheduleNext interval=" + intervalMs + "ms");
        if (intervalMs > 0) {
            schedule(context, intervalMs, false); /* 重排：保持整分钟相位 */
        }
    }

    /* App 启动时恢复提醒（设备重启后 AlarmManager 闹钟会清空，需要重新注册）
     * 保持已有相位，不重置到整分钟——避免把已排好的闹钟反复重置 */
    public static void restoreAfterReboot(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long intervalMs = sp.getLong(KEY_INTERVAL, 0);
        if (intervalMs > 0) {
            Log.d(TAG, "restoreAfterReboot interval=" + intervalMs + "ms");
            schedule(context, intervalMs, false);
        }
    }

    public static void cancel(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_INTERVAL).apply();

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        pi.cancel();
    }
}
