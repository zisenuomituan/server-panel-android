package com.xianyunb.serverpanel;

import org.json.JSONObject;

public class Server {

    public long id;
    public long hostId;
    public String name = "";
    public String domainName = "";
    public String sshUser = "";
    public int sshPort;
    public int vcpu;
    public int memMb;
    public Live live = new Live();

    public static Server from(JSONObject o) {
        Server s = new Server();
        s.id = o.optLong("id");
        s.hostId = o.optLong("host_id");
        s.name = o.optString("name", "");
        s.domainName = o.optString("domain_name", "");
        s.sshUser = o.optString("ssh_user", "");
        s.sshPort = o.optInt("ssh_port");
        s.vcpu = o.optInt("vcpu");
        s.memMb = o.optInt("mem_mb");
        s.live = Live.from(o.optJSONObject("live"));
        return s;
    }
}
