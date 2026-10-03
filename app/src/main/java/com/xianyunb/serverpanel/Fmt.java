package com.xianyunb.serverpanel;

import java.util.Locale;

public class Fmt {

    public static String bytes(long n) {
        if (n <= 0) return "0";
        String[] u = {"B", "K", "M", "G", "T"};
        double v = n;
        int i = 0;
        while (v >= 1024 && i < u.length - 1) {
            v /= 1024;
            i++;
        }
        if (v >= 10 || i == 0) return String.format(Locale.US, "%.0f", v) + u[i];
        return String.format(Locale.US, "%.1f", v) + u[i];
    }

    public static String rate(long n) {
        return bytes(n) + "/s";
    }

    public static String uptime(long sec) {
        if (sec <= 0) return "-";
        long d = sec / 86400;
        long h = (sec % 86400) / 3600;
        long m = (sec % 3600) / 60;
        if (d > 0) return d + "天" + h + "时";
        if (h > 0) return h + "时" + m + "分";
        return m + "分";
    }

    public static String pct(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    public static String memText(Live l) {
        if (l.memTotalMb <= 0) return "-";
        return bytes(l.memUsedMb * 1048576L) + " / " + bytes(l.memTotalMb * 1048576L);
    }

    public static String diskText(Live l) {
        if (l.diskTotal <= 0) return "-";
        return bytes(l.diskUsed) + " / " + bytes(l.diskTotal);
    }

    public static String stateText(String state) {
        if (state == null || state.isEmpty()) return "未知";
        if ("running".equals(state)) return "运行中";
        if ("paused".equals(state)) return "重启中";
        if ("shut off".equals(state)) return "已停止";
        return "未知";
    }

    public static int dotColorRes(String state) {
        if ("running".equals(state)) return R.color.green;
        if ("paused".equals(state)) return R.color.amber;
        if (state == null || state.isEmpty()) return R.color.dot_unknown;
        return R.color.red;
    }

    public static int barColorRes(double v) {
        if (v >= 85) return R.color.red;
        if (v >= 65) return R.color.amber;
        return R.color.blue;
    }

    public static String time(String iso) {
        String wall = wall(iso);
        return wall.length() >= 19 ? wall.substring(11, 19) : wall;
    }

    public static String dateTime(String iso) {
        String wall = wall(iso);
        return wall.isEmpty() ? "-" : wall;
    }

    private static String wall(String iso) {
        if (iso == null || iso.isEmpty()) return "-";
        String s = iso.replace('T', ' ');
        int dot = s.indexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        if (s.length() > 19) s = s.substring(0, 19);
        return s;
    }
}
