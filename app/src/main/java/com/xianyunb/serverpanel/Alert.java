package com.xianyunb.serverpanel;

import org.json.JSONObject;

public class Alert {

    public long id;
    public String kind = "";
    public String level = "warn";
    public long hostId;
    public long serverId;
    public String target = "";
    public String message = "";
    public double value;
    public String status = "active";
    public int count;
    public String createdAt = "";
    public String updatedAt = "";
    public boolean acked;

    public static Alert from(JSONObject o) {
        Alert a = new Alert();
        if (o == null) return a;
        a.id = o.optLong("id");
        a.kind = o.optString("kind", "");
        a.level = o.optString("level", "warn");
        a.hostId = o.optLong("host_id");
        a.serverId = o.optLong("server_id");
        a.target = o.optString("target", "");
        a.message = o.optString("message", "");
        a.value = o.optDouble("value", 0);
        a.status = o.optString("status", "active");
        a.count = o.optInt("count", 1);
        a.createdAt = o.optString("created_at", "");
        a.updatedAt = o.optString("updated_at", "");
        a.acked = !o.isNull("ack_at");
        return a;
    }

    public boolean isActive() {
        return "active".equals(status);
    }

    public boolean isCritical() {
        return "crit".equals(level);
    }

    public String kindText() {
        switch (kind) {
            case "cpu": return "CPU 过高";
            case "mem": return "内存过高";
            case "disk": return "磁盘将满";
            case "stopped": return "虚拟机停止";
            case "host_offline": return "宿主机失联";
            default: return kind;
        }
    }
}
