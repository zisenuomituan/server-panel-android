package com.xianyunb.serverpanel;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DetailActivity extends AppCompatActivity {

    private static final long POLL_MS = 5000;

    private Session session;
    private Api api;
    private long serverId;

    private TextView title, badge, subtitle, cpuVal, memVal, diskVal, netRx, netTx, up;
    private BarView cpuBar, memBar, diskBar;
    private LinearLayout kvGrid, diskList;
    private View diskPanel;
    private GridLayout userGrid;
    private LineChart chartRes, chartNet;

    private View commandPanel;
    private ScrollView termScroll;
    private TextView termOut;
    private EditText cmdInput;

    private final List<String> cmdHistory = new ArrayList<>();
    private int histIdx;
    private boolean cmdBusy;
    private boolean termStarted;

    private Server server;
    private Host host;

    private final List<Float> hCpu = new ArrayList<>();
    private final List<Float> hMem = new ArrayList<>();
    private final List<Float> hRx = new ArrayList<>();
    private final List<Float> hTx = new ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = () -> loadDetail();
    private boolean loading;
    private boolean paused;
    private boolean firstRender = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new Session(this);
        if (!session.loggedIn()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_detail);
        serverId = getIntent().getLongExtra("server_id", 0);
        api = new Api(session.baseUrl());
        api.setToken(session.token());

        title = findViewById(R.id.title);
        badge = findViewById(R.id.badge);
        subtitle = findViewById(R.id.subtitle);
        cpuVal = findViewById(R.id.cpuVal);
        memVal = findViewById(R.id.memVal);
        diskVal = findViewById(R.id.diskVal);
        netRx = findViewById(R.id.netRx);
        netTx = findViewById(R.id.netTx);
        up = findViewById(R.id.up);
        cpuBar = findViewById(R.id.cpuBar);
        memBar = findViewById(R.id.memBar);
        diskBar = findViewById(R.id.diskBar);
        kvGrid = findViewById(R.id.kvGrid);
        diskList = findViewById(R.id.diskList);
        diskPanel = findViewById(R.id.diskPanel);
        userGrid = findViewById(R.id.userGrid);
        chartRes = findViewById(R.id.chartRes);
        chartNet = findViewById(R.id.chartNet);

        commandPanel = findViewById(R.id.commandPanel);
        termScroll = findViewById(R.id.termScroll);
        termOut = findViewById(R.id.termOut);
        cmdInput = findViewById(R.id.cmdInput);
        termOut.setText("在下面输入命令，回车执行。");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnStart).setOnClickListener(v -> confirmPower("start"));
        findViewById(R.id.btnReboot).setOnClickListener(v -> confirmPower("reboot"));
        findViewById(R.id.btnShutdown).setOnClickListener(v -> confirmPower("shutdown"));
        findViewById(R.id.btnForceOff).setOnClickListener(v -> confirmPower("force-off"));

        findViewById(R.id.btnRun).setOnClickListener(v -> runCmd());
        findViewById(R.id.btnHistPrev).setOnClickListener(v -> histPrev());
        findViewById(R.id.btnHistNext).setOnClickListener(v -> histNext());
        findViewById(R.id.btnClear).setOnClickListener(v -> clearTerm());
        cmdInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                runCmd();
                return true;
            }
            return false;
        });

        loadHistory();
        loadConfig();
    }

    private void loadConfig() {
        new Thread(() -> {
            boolean enabled = true;
            try {
                JSONObject c = api.getObj("/config");
                enabled = c.optBoolean("enable_exec", true);
            } catch (Exception ignored) {
            }
            final boolean en = enabled;
            handler.post(() -> {
                boolean canExec = !"viewer".equals(session.role()) && en;
                commandPanel.setVisibility(canExec ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    private void confirmPower(String action) {
        String name = powerName(action);
        new AlertDialog.Builder(this)
                .setMessage("确认对 " + (server != null ? server.name : "") + " 执行「" + name + "」？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> doPower(action))
                .show();
    }

    private String powerName(String action) {
        switch (action) {
            case "start": return "开机";
            case "shutdown": return "关机";
            case "force-off": return "强制关机";
            case "reboot": return "重启";
            default: return action;
        }
    }

    private void doPower(String action) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("action", action);
                api.postObj("/servers/" + serverId + "/power", body);
                handler.post(() -> {
                    toast("指令已下发");
                    handler.postDelayed(this::loadDetail, 900);
                });
            } catch (Exception e) {
                handler.post(() -> toast(e.getMessage()));
            }
        }).start();
    }

    private void runCmd() {
        final String command = cmdInput.getText().toString().trim();
        if (command.isEmpty() || cmdBusy) return;
        cmdBusy = true;
        cmdHistory.add(command);
        histIdx = cmdHistory.size();
        cmdInput.setText("");
        appendTerm("$ " + command + "\n");
        scrollTerm();
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("command", command);
                JSONObject r = api.postObj("/servers/" + serverId + "/exec", body);
                String out = r.optString("output", "");
                int code = r.optInt("exit_code", 0);
                StringBuilder sb = new StringBuilder(out);
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
                if (code != 0) sb.append("[退出码 ").append(code).append("]\n");
                final String text = sb.toString();
                handler.post(() -> {
                    cmdBusy = false;
                    appendTerm(text);
                    scrollTerm();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    cmdBusy = false;
                    appendTerm("错误: " + e.getMessage() + "\n");
                    scrollTerm();
                });
            }
        }).start();
    }

    private void appendTerm(String s) {
        if (!termStarted) {
            termOut.setText("");
            termStarted = true;
        }
        termOut.append(s);
    }

    private void scrollTerm() {
        termScroll.post(() -> termScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void histPrev() {
        if (cmdHistory.isEmpty()) return;
        histIdx = Math.max(0, histIdx - 1);
        cmdInput.setText(cmdHistory.get(histIdx));
        cmdInput.setSelection(cmdInput.getText().length());
    }

    private void histNext() {
        if (cmdHistory.isEmpty()) return;
        histIdx = Math.min(cmdHistory.size(), histIdx + 1);
        cmdInput.setText(histIdx >= cmdHistory.size() ? "" : cmdHistory.get(histIdx));
        cmdInput.setSelection(cmdInput.getText().length());
    }

    private void clearTerm() {
        termOut.setText("在下面输入命令，回车执行。");
        termStarted = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        paused = false;
        loadDetail();
    }

    @Override
    protected void onPause() {
        super.onPause();
        paused = true;
        handler.removeCallbacks(tick);
    }

    private void schedule() {
        handler.removeCallbacks(tick);
        if (!paused) handler.postDelayed(tick, POLL_MS);
    }

    private void loadDetail() {
        if (loading) return;
        loading = true;
        new Thread(() -> {
            JSONObject d = null;
            String err = null;
            try {
                d = api.getObj("/servers/" + serverId);
            } catch (Exception e) {
                err = e.getMessage();
            }
            final JSONObject detail = d;
            final String error = err;
            handler.post(() -> {
                loading = false;
                if (detail != null) render(detail);
                else if (error != null) toast(error);
                schedule();
            });
        }).start();
    }

    private void loadHistory() {
        new Thread(() -> {
            try {
                JSONArray pts = api.getArray("/servers/" + serverId + "/history?limit=120");
                final List<Float> cpu = new ArrayList<>();
                final List<Float> mem = new ArrayList<>();
                final List<Float> rx = new ArrayList<>();
                final List<Float> tx = new ArrayList<>();
                for (int i = pts.length() - 1; i >= 0; i--) {
                    JSONObject p = pts.optJSONObject(i);
                    if (p == null) continue;
                    double c = p.optDouble("cpu", 0);
                    long mu = p.optLong("mem_used_mb");
                    long mt = p.optLong("mem_total_mb");
                    cpu.add((float) c);
                    mem.add((float) (mt > 0 ? Math.min(100.0, mu * 100.0 / mt) : 0));
                    rx.add((float) p.optLong("net_rx_rate"));
                    tx.add((float) p.optLong("net_tx_rate"));
                }
                handler.post(() -> {
                    hCpu.clear();
                    hMem.clear();
                    hRx.clear();
                    hTx.clear();
                    hCpu.addAll(cpu);
                    hMem.addAll(mem);
                    hRx.addAll(rx);
                    hTx.addAll(tx);
                    drawCharts();
                });
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void render(JSONObject d) {
        server = Server.from(d.optJSONObject("server"));
        host = Host.from(d.optJSONObject("host"));
        Live live = Live.from(d.optJSONObject("live"));

        title.setText(server.name);
        badge.setText(Fmt.stateText(live.state));
        int stateColor = ContextCompat.getColor(this, Fmt.dotColorRes(live.state));
        badge.setTextColor(stateColor);

        String hostName = host != null ? host.name : "";
        subtitle.setText(hostName + " / " + server.domainName);

        bindLive(live);
        buildKv(live);
        buildDisks(live);
        buildUsers(live);

        if (!firstRender) {
            pushPoint(live);
        }
        firstRender = false;
    }

    private void bindLive(Live l) {
        double cpu = l.cpuPct();
        cpuVal.setText(Fmt.pct(cpu) + "%");
        cpuBar.setValue(cpu, Fmt.barColorRes(cpu));

        double mem = l.memPct();
        memVal.setText(Fmt.memText(l));
        memBar.setValue(mem, Fmt.barColorRes(mem));

        double disk = l.diskPct();
        diskVal.setText(Fmt.diskText(l));
        diskBar.setValue(disk, Fmt.barColorRes(disk));

        netRx.setText("↓ " + Fmt.rate(l.netRxRate));
        netTx.setText("↑ " + Fmt.rate(l.netTxRate));
        up.setText("运行 " + Fmt.uptime(l.uptime));
    }

    private void buildKv(Live l) {
        kvGrid.removeAllViews();
        List<String[]> items = new ArrayList<>();
        items.add(new String[]{"vCPU", server.vcpu > 0 ? String.valueOf(server.vcpu) : "-"});
        items.add(new String[]{"分配内存", server.memMb > 0 ? server.memMb + " MB" : "-"});
        items.add(new String[]{"域名", server.domainName});
        items.add(new String[]{"宿主机", host != null ? host.name : "-"});
        String guestSsh = server.sshPort > 0 && host != null ? host.sshHost + ":" + server.sshPort : "未配置";
        items.add(new String[]{"Guest SSH", guestSsh});
        items.add(new String[]{"主机名", orDash(l.hostname)});
        items.add(new String[]{"系统", orDash(l.os)});
        items.add(new String[]{"内核", orDash(l.kernel)});
        items.add(new String[]{"架构", orDash(l.arch)});
        items.add(new String[]{"内网 IP", l.ips.isEmpty() ? "-" : join(l.ips)});
        items.add(new String[]{"最后更新", Fmt.time(l.updated)});

        for (int i = 0; i < items.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(kvCell(items.get(i)[0], items.get(i)[1]));
            if (i + 1 < items.size()) {
                row.addView(kvCell(items.get(i + 1)[0], items.get(i + 1)[1]));
            } else {
                View filler = new View(this);
                filler.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
                row.addView(filler);
            }
            kvGrid.addView(row);
        }
    }

    private LinearLayout kvCell(String k, String v) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.bottomMargin = dp(10);
        cell.setLayoutParams(lp);

        TextView kk = new TextView(this);
        kk.setText(k);
        kk.setTextColor(ContextCompat.getColor(this, R.color.dim));
        kk.setTextSize(12);
        cell.addView(kk);

        TextView vv = new TextView(this);
        vv.setText(v);
        vv.setTextColor(ContextCompat.getColor(this, R.color.text));
        vv.setTextSize(14);
        vv.setTypeface(Typeface.MONOSPACE);
        cell.addView(vv);
        return cell;
    }

    private void buildDisks(Live l) {
        diskList.removeAllViews();
        if (l.disks.isEmpty()) {
            diskPanel.setVisibility(View.GONE);
            return;
        }
        diskPanel.setVisibility(View.VISIBLE);
        for (Live.Disk d : l.disks) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            row.setPadding(0, dp(4), 0, dp(4));

            row.addView(diskCell(d.mount, 1f, Gravity.START));
            row.addView(diskCell(Fmt.bytes(d.used), 0f, Gravity.END));
            row.addView(diskCell(Fmt.bytes(d.total), 0f, Gravity.END));
            double pct = d.total > 0 ? d.used * 100.0 / d.total : 0;
            TextView p = new TextView(this);
            p.setText(String.format(java.util.Locale.US, "%.1f%%", pct));
            p.setTextColor(ContextCompat.getColor(this, R.color.text));
            p.setTextSize(13);
            p.setTypeface(Typeface.MONOSPACE);
            p.setGravity(Gravity.END);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setLayoutParams(plp);
            row.addView(p);

            diskList.addView(row);
        }
    }

    private TextView diskCell(String text, float weight, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(this, R.color.text));
        tv.setTextSize(13);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setGravity(gravity);
        if (weight > 0) {
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        } else {
            tv.setLayoutParams(new LinearLayout.LayoutParams(dp(78), LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        return tv;
    }

    private void buildUsers(Live l) {
        userGrid.removeAllViews();
        if (l.users.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("没有检测到登录中的用户");
            tv.setTextColor(ContextCompat.getColor(this, R.color.dim));
            tv.setTextSize(13);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.columnSpec = GridLayout.spec(0, 3);
            tv.setLayoutParams(lp);
            userGrid.addView(tv);
            return;
        }
        for (String u : l.users) {
            TextView badge = new TextView(this);
            badge.setText(u);
            badge.setTextColor(ContextCompat.getColor(this, R.color.dim));
            badge.setTextSize(12);
            badge.setBackgroundResource(R.drawable.bg_badge);
            badge.setPadding(dp(10), dp(2), dp(10), dp(2));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMargins(0, 0, dp(8), dp(8));
            badge.setLayoutParams(lp);
            userGrid.addView(badge);
        }
    }

    private void pushPoint(Live l) {
        hCpu.add((float) l.cpuPct());
        hMem.add((float) l.memPct());
        hRx.add((float) l.netRxRate);
        hTx.add((float) l.netTxRate);
        while (hCpu.size() > 150) {
            hCpu.remove(0);
            hMem.remove(0);
            hRx.remove(0);
            hTx.remove(0);
        }
        drawCharts();
    }

    private void drawCharts() {
        chartRes.setData(
                Arrays.asList(toArray(hCpu), toArray(hMem)),
                Arrays.asList(R.color.blue, R.color.green),
                100f, false, true);
        chartNet.setData(
                Arrays.asList(toArray(hRx), toArray(hTx)),
                Arrays.asList(R.color.blue, R.color.amber),
                0f, true, false);
    }

    private static float[] toArray(List<Float> in) {
        float[] out = new float[in.size()];
        for (int i = 0; i < in.size(); i++) out[i] = in.get(i);
        return out;
    }

    private static String orDash(String s) {
        return s == null || s.isEmpty() ? "-" : s;
    }

    private static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg == null ? "请求失败" : msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
