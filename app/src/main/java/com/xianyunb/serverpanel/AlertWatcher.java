package com.xianyunb.serverpanel;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 告警的后台检查与本地通知。
 *
 * 面板本身不推送（自建服务没有推送通道），所以由 App 定期拉取
 * /api/alerts/summary，发现有未确认的告警就发一条本地通知。
 * 用 AlarmManager 定时唤醒，不需要常驻前台服务。
 */
public class AlertWatcher {

    private static final long INTERVAL_MS = 5 * 60 * 1000L;
    private static final int REQUEST_CODE = 4201;
    private static final int NOTIFY_ID = 1001;
    private static final String CHANNEL = "panel_alerts";
    private static final String PREF = "alert_watcher";

    public static void start(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(context);
        am.cancel(pi);
        am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 30_000L,
                INTERVAL_MS,
                pi);
    }

    public static void stop(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pending(context));
    }

    private static PendingIntent pending(Context context) {
        Intent intent = new Intent(context, AlertReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }

    /** 拉一次告警；有新的未确认告警就发通知。可在后台线程调用。 */
    public static void check(Context context) throws Exception {
        Session session = new Session(context);
        if (!session.loggedIn()) return;

        Api api = new Api(session.baseUrl());
        api.setToken(session.token());
        JSONObject summary;
        try {
            summary = api.getObj("/alerts/summary");
        } catch (Api.ApiException e) {
            if (e.code == 401) {
                stop(context); // 登录已过期：别再每 5 分钟白跑一趟
            }
            throw e;
        }

        int unacked = summary.optInt("unacked", 0);
        long maxId = summary.optLong("max_id", 0);
        if (unacked <= 0 || maxId <= 0) return;

        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (maxId <= sp.getLong("last_notified_id", 0)) return;
        sp.edit().putLong("last_notified_id", maxId).apply();

        notifyAlert(context, summary.optJSONArray("items"), unacked);
    }

    private static void notifyAlert(Context context, JSONArray items, int unacked) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        ensureChannel(context);

        String text = "有 " + unacked + " 条未确认告警";
        if (items != null && items.length() > 0) {
            JSONObject first = items.optJSONObject(0);
            if (first != null) {
                text = first.optString("message", text);
                if (items.length() > 1) text += " 等 " + items.length() + " 条";
            }
        }

        Intent open = new Intent(context, AlertsActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(context, 0, open, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("服务器面板告警")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFY_ID, builder.build());
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "面板告警", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("虚拟机停止、CPU/内存/磁盘超阈值、宿主机失联");
        nm.createNotificationChannel(channel);
    }
}
