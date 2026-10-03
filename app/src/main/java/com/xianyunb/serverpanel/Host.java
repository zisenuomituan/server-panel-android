package com.xianyunb.serverpanel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Host {

    public long id;
    public String name = "";
    public String sshHost = "";
    public String sshUser = "";
    public String libvirtUri = "";
    public String status = "";
    public int sshPort;
    public final List<Server> servers = new ArrayList<>();

    public static Host from(JSONObject o) {
        Host h = new Host();
        h.id = o.optLong("id");
        h.name = o.optString("name", "");
        h.sshHost = o.optString("ssh_host", "");
        h.sshUser = o.optString("ssh_user", "");
        h.libvirtUri = o.optString("libvirt_uri", "");
        h.status = o.optString("status", "");
        h.sshPort = o.optInt("ssh_port");
        JSONArray arr = o.optJSONArray("servers");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject s = arr.optJSONObject(i);
                if (s != null) h.servers.add(Server.from(s));
            }
        }
        return h;
    }
}
