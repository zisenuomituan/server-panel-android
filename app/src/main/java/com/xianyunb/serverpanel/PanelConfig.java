package com.xianyunb.serverpanel;

import org.json.JSONObject;

/**
 * 面板运行时配置（GET /api/config），带进程内缓存。
 * 用于告诉用户面板侧是否开着告警、是否允许命令行，避免 App 单方面显示。
 */
public class PanelConfig {

    private static PanelConfig cached;

    public boolean loaded;
    public String panelVersion = "";
    public boolean enableExec = true;

    public boolean alertsEnabled = true;
    public int alertCpu = 90;
    public int alertMem = 90;
    public int alertDisk = 90;
    public int alertConsecutive = 3;
    public boolean alertOnStop = true;

    public static PanelConfig from(JSONObject c) {
        PanelConfig p = new PanelConfig();
        if (c != null) {
            p.panelVersion = c.optString("version", "");
            p.enableExec = c.optBoolean("enable_exec", true);
            JSONObject a = c.optJSONObject("alerts");
            if (a != null) {
                p.alertsEnabled = a.optBoolean("enabled", true);
                p.alertCpu = a.optInt("cpu", 90);
                p.alertMem = a.optInt("mem", 90);
                p.alertDisk = a.optInt("disk", 90);
                p.alertConsecutive = a.optInt("consecutive", 3);
                p.alertOnStop = a.optBoolean("on_stop", true);
            }
        }
        p.loaded = true;
        return p;
    }

    /** 进程内缓存的配置，可能为 null。 */
    public static PanelConfig cached() {
        return cached;
    }

    /** 拉取并缓存；失败返回 null，不覆盖已有缓存。 */
    public static PanelConfig fetch(Api api) {
        try {
            PanelConfig p = from(api.getObj("/config"));
            cached = p;
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    public static void clear() {
        cached = null;
    }

    public String alertRule() {
        return "CPU ≥" + alertCpu + "% / 内存 ≥" + alertMem + "% / 磁盘 ≥" + alertDisk
                + "%，连续 " + alertConsecutive + " 次";
    }

    public String alertSwitchText() {
        return alertsEnabled ? "已开启" : "已关闭";
    }

    /** 面板侧告警的说明文案（开关关掉时说明为什么收不到）。 */
    public String alertDetail() {
        if (!alertsEnabled) {
            return "面板未开启告警，App 不会收到新的告警提醒（需在面板服务器的 center.json 里把 alert_enabled 设为 true）。";
        }
        return alertRule() + (alertOnStop ? "；虚拟机停机也告警" : "");
    }

    public String execDetail() {
        return enableExec ? "面板允许执行命令" : "面板已关闭命令行执行";
    }
}
