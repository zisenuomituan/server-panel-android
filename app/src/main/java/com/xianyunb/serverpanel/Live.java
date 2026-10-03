package com.xianyunb.serverpanel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Live {

    public long serverId;
    public long hostId;
    public String name = "";
    public String state = "";
    public double cpu;
    public long memUsedMb;
    public long memTotalMb;
    public long diskUsed;
    public long diskTotal;
    public long netRxRate;
    public long netTxRate;
    public long netRx;
    public long netTx;
    public long uptime;
    public String updated = "";

    public String hostname = "";
    public String os = "";
    public String kernel = "";
    public String arch = "";
    public final List<String> ips = new ArrayList<>();
    public final List<String> users = new ArrayList<>();
    public final List<Disk> disks = new ArrayList<>();

    public static class Disk {
        public String mount = "";
        public long total;
        public long used;
    }

    public static Live from(JSONObject o) {
        Live l = new Live();
        if (o == null) return l;
        l.serverId = o.optLong("server_id");
        l.hostId = o.optLong("host_id");
        l.name = o.optString("name", "");
        l.state = o.optString("state", "");
        l.cpu = o.optDouble("cpu", 0);
        l.memUsedMb = o.optLong("mem_used_mb");
        l.memTotalMb = o.optLong("mem_total_mb");
        l.diskUsed = o.optLong("disk_used");
        l.diskTotal = o.optLong("disk_total");
        l.netRxRate = o.optLong("net_rx_rate");
        l.netTxRate = o.optLong("net_tx_rate");
        l.netRx = o.optLong("net_rx");
        l.netTx = o.optLong("net_tx");
        l.uptime = o.optLong("uptime");
        l.updated = o.optString("updated", "");
        l.hostname = o.optString("hostname", "");
        l.os = o.optString("os", "");
        l.kernel = o.optString("kernel", "");
        l.arch = o.optString("arch", "");

        JSONArray ips = o.optJSONArray("ips");
        if (ips != null) {
            for (int i = 0; i < ips.length(); i++) l.ips.add(ips.optString(i));
        }
        JSONArray users = o.optJSONArray("users");
        if (users != null) {
            for (int i = 0; i < users.length(); i++) l.users.add(users.optString(i));
        }
        JSONArray disks = o.optJSONArray("disks");
        if (disks != null) {
            for (int i = 0; i < disks.length(); i++) {
                JSONObject d = disks.optJSONObject(i);
                if (d == null) continue;
                Disk disk = new Disk();
                disk.mount = d.optString("mount", "");
                disk.total = d.optLong("total");
                disk.used = d.optLong("used");
                l.disks.add(disk);
            }
        }
        return l;
    }

    public boolean isRunning() {
        return "running".equals(state);
    }

    public double cpuPct() {
        return clamp(cpu);
    }

    public double memPct() {
        return memTotalMb > 0 ? clamp(memUsedMb * 100.0 / memTotalMb) : 0;
    }

    public double diskPct() {
        return diskTotal > 0 ? clamp(diskUsed * 100.0 / diskTotal) : 0;
    }

    private static double clamp(double v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }
}
