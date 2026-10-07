package com.xianyunb.serverpanel;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final long POLL_MS = 5000;

    private static final String[] FILTER_VALUES = {"all", "running", "stopped"};
    private static final String[] FILTER_LABELS = {"全部", "仅运行中", "仅已停止"};
    private static final String[] FILTER_SHORT = {"全部", "运行中", "已停止"};
    private static final String[] SORT_VALUES = {"host", "name", "cpu", "mem"};
    private static final String[] SORT_LABELS = {"按宿主机（默认）", "按名称", "按 CPU 占用", "按内存占用"};
    private static final String[] SORT_SHORT = {"默认", "名称", "CPU", "内存"};

    private Session session;
    private Api api;
    private LinearLayout listContainer;
    private EditText bindInput;
    private EditText searchInput;
    private View loadingBar;
    private Button btnFilter;
    private Button btnSort;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Long, ServerCard> cards = new HashMap<>();
    private String structureSig = "";
    private boolean loading;
    private boolean paused;
    private boolean firstLoadDone;
    private int alertCount;
    private long lastAlertFetch;

    // 列表视图状态：搜索 / 筛选 / 排序（只影响显示，不影响轮询到的原始数据）
    private String searchQuery = "";
    private String stateFilter = "all";
    private String sortMode = "host";
    private List<Host> lastHosts = new ArrayList<>();

    private final Runnable tick = () -> loadHosts();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new Session(this);
        if (!session.loggedIn()) {
            toLogin();
            return;
        }
        setContentView(R.layout.activity_main);
        api = new Api(session.baseUrl());
        api.setToken(session.token());

        TextView who = findViewById(R.id.who);
        who.setText(session.username() + " · " + roleText(session.role()));
        findViewById(R.id.btnMenu).setOnClickListener(this::showMenu);

        listContainer = findViewById(R.id.listContainer);
        bindInput = findViewById(R.id.bindInput);
        loadingBar = findViewById(R.id.loadingBar);
        findViewById(R.id.btnBind).setOnClickListener(v -> doBind());

        // 搜索 / 筛选 / 排序
        searchInput = findViewById(R.id.searchInput);
        btnFilter = findViewById(R.id.btnFilter);
        btnSort = findViewById(R.id.btnSort);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim().toLowerCase();
                render(lastHosts);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        btnFilter.setOnClickListener(this::showFilterMenu);
        btnSort.setOnClickListener(this::showSortMenu);
        renderToolbarText();

        // 首次进入先给出加载提示，避免一片空白
        listContainer.addView(emptyText("正在加载…", 40));

        // 自用版启动时静默检查一次更新，并清掉已安装版本的残留安装包
        if (BuildConfig.SELF_USE) {
            Updater.cleanup(this);
            Updater.check(this, true);
        }

        // 告警：申请通知权限并开启后台定时检查
        requestNotificationPermission();
        AlertWatcher.start(this);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 100);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        paused = false;
        loadHosts();
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

    private void loadHosts() {
        if (loading) return;
        loading = true;
        if (loadingBar != null) loadingBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<Host> data = null;
            String err = null;
            try {
                data = parseHosts(api.getArray("/hosts"));
            } catch (Exception e) {
                err = e.getMessage();
            }
            // 每 30 秒顺带取一次告警数，用于菜单角标
            if (System.currentTimeMillis() - lastAlertFetch > 30_000L) {
                lastAlertFetch = System.currentTimeMillis();
                try {
                    alertCount = api.getObj("/alerts/summary").optInt("active", 0);
                } catch (Exception ignored) {
                }
            }
            final List<Host> result = data;
            final String error = err;
            handler.post(() -> {
                loading = false;
                firstLoadDone = true;
                if (loadingBar != null) loadingBar.setVisibility(View.GONE);
                if (result != null) render(result);
                else if (error != null) toast(error);
                schedule();
            });
        }).start();
    }

    private List<Host> parseHosts(JSONArray arr) {
        List<Host> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(Host.from(o));
        }
        return out;
    }

    private void render(List<Host> hosts) {
        lastHosts = hosts;
        List<Host> view = applyView(hosts);

        StringBuilder sig = new StringBuilder();
        sig.append(searchQuery).append('|').append(stateFilter).append('|').append(sortMode).append('|');
        for (Host h : view) {
            sig.append('#').append(h.id);
            for (Server s : h.servers) sig.append(',').append(s.id);
            sig.append(';');
        }
        if (!sig.toString().equals(structureSig)) {
            structureSig = sig.toString();
            rebuild(view);
        } else {
            for (Host h : view) {
                for (Server s : h.servers) {
                    ServerCard card = cards.get(s.id);
                    if (card != null) card.bind(s, s.live);
                }
            }
        }
    }

    /** 按当前搜索词 / 筛选 / 排序生成要展示的列表（不改动原始数据）。 */
    private List<Host> applyView(List<Host> hosts) {
        boolean noCondition = searchQuery.isEmpty() && "all".equals(stateFilter);
        List<Host> out = new ArrayList<>();
        for (Host h : hosts) {
            List<Server> kept = new ArrayList<>();
            for (Server s : h.servers) {
                if (matchState(s) && matchQuery(h, s)) kept.add(s);
            }
            if (kept.isEmpty() && !noCondition) continue;
            Host copy = new Host();
            copy.id = h.id;
            copy.name = h.name;
            copy.sshHost = h.sshHost;
            copy.sshUser = h.sshUser;
            copy.libvirtUri = h.libvirtUri;
            copy.status = h.status;
            copy.sshPort = h.sshPort;
            copy.servers.addAll(kept);
            sortServers(copy.servers);
            out.add(copy);
        }
        if ("name".equals(sortMode)) {
            Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        }
        return out;
    }

    private boolean matchState(Server s) {
        if ("all".equals(stateFilter)) return true;
        boolean running = s.live != null && s.live.isRunning();
        return "running".equals(stateFilter) == running;
    }

    private boolean matchQuery(Host h, Server s) {
        if (searchQuery.isEmpty()) return true;
        if (hit(s.name) || hit(s.domainName) || hit(h.name) || hit(h.sshHost)) return true;
        if (s.live != null) {
            if (hit(s.live.hostname)) return true;
            for (String ip : s.live.ips) {
                if (hit(ip)) return true;
            }
        }
        return false;
    }

    private boolean hit(String v) {
        return v != null && v.toLowerCase().contains(searchQuery);
    }

    private void sortServers(List<Server> list) {
        if ("name".equals(sortMode)) {
            Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
        } else if ("cpu".equals(sortMode)) {
            Collections.sort(list, (a, b) -> Double.compare(cpuOf(b), cpuOf(a)));
        } else if ("mem".equals(sortMode)) {
            Collections.sort(list, (a, b) -> Double.compare(memOf(b), memOf(a)));
        }
    }

    private double cpuOf(Server s) {
        return s.live == null ? 0 : s.live.cpuPct();
    }

    private double memOf(Server s) {
        return s.live == null ? 0 : s.live.memPct();
    }

    private void renderToolbarText() {
        String fl = "筛选";
        String sl = "排序";
        for (int i = 0; i < FILTER_VALUES.length; i++) {
            if (FILTER_VALUES[i].equals(stateFilter) && i > 0) fl = "筛选：" + FILTER_SHORT[i];
        }
        for (int i = 0; i < SORT_VALUES.length; i++) {
            if (SORT_VALUES[i].equals(sortMode) && i > 0) sl = "排序：" + SORT_SHORT[i];
        }
        btnFilter.setText(fl);
        btnSort.setText(sl);
    }

    private void showFilterMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        if (!searchQuery.isEmpty()) {
            menu.getMenu().add(0, 1, 0, "清除搜索");
        }
        for (int i = 0; i < FILTER_VALUES.length; i++) {
            String mark = FILTER_VALUES[i].equals(stateFilter) ? "  ✓" : "";
            menu.getMenu().add(0, 10 + i, i + 1, FILTER_LABELS[i] + mark);
        }
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                searchInput.setText("");
            } else if (id >= 10 && id < 10 + FILTER_VALUES.length) {
                stateFilter = FILTER_VALUES[id - 10];
                renderToolbarText();
                render(lastHosts);
            }
            return true;
        });
        menu.show();
    }

    private void showSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        for (int i = 0; i < SORT_VALUES.length; i++) {
            String mark = SORT_VALUES[i].equals(sortMode) ? "  ✓" : "";
            menu.getMenu().add(0, 20 + i, i, SORT_LABELS[i] + mark);
        }
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id >= 20 && id < 20 + SORT_VALUES.length) {
                sortMode = SORT_VALUES[id - 20];
                renderToolbarText();
                render(lastHosts);
            }
            return true;
        });
        menu.show();
    }

    private void rebuild(List<Host> hosts) {
        listContainer.removeAllViews();
        cards.clear();
        LayoutInflater inflater = LayoutInflater.from(this);

        if (hosts.isEmpty()) {
            listContainer.addView(emptyText(viewEmptyText(), 40));
            return;
        }

        for (Host h : hosts) {
            View header = inflater.inflate(R.layout.view_host_header, listContainer, false);
            ((TextView) header.findViewById(R.id.name)).setText(h.name);
            ((TextView) header.findViewById(R.id.addr)).setText(h.sshHost + ":" + h.sshPort);
            header.findViewById(R.id.dot).getBackground().mutate()
                    .setTint(ContextCompat.getColor(this, hostDotColor(h.status)));
            header.findViewById(R.id.btnUnbind).setOnClickListener(v -> confirmUnbind(h));
            listContainer.addView(header);

            if (h.servers.isEmpty()) {
                listContainer.addView(emptyText("这台宿主机下还没有虚拟机", 6));
            }

            for (Server s : h.servers) {
                View cardView = inflater.inflate(R.layout.view_server_card, listContainer, false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(12);
                cardView.setLayoutParams(lp);
                ServerCard card = new ServerCard(cardView);
                card.bind(s, s.live);
                cardView.setClickable(true);
                cardView.setFocusable(true);
                cardView.setOnClickListener(v -> openDetail(s.id));
                listContainer.addView(cardView);
                cards.put(s.id, card);
            }

            View gap = new View(this);
            gap.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16)));
            listContainer.addView(gap);
        }
    }

    /** 列表为空时该说什么：搜索/筛选无结果、还是真的没绑定宿主机。 */
    private String viewEmptyText() {
        if (!searchQuery.isEmpty() || !"all".equals(stateFilter)) {
            return "没有符合条件的虚拟机";
        }
        return firstLoadDone ? "还没有绑定宿主机，在上方输入密钥即可绑定。" : "正在加载…";
    }

    private TextView emptyText(String text, int topDp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(this, R.color.dim));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(topDp), 0, dp(16));
        return tv;
    }

    private int hostDotColor(String status) {
        if ("running".equals(status) || "on".equals(status) || "online".equals(status)) return R.color.green;
        if ("off".equals(status) || "offline".equals(status)) return R.color.red;
        if ("warn".equals(status)) return R.color.amber;
        return R.color.dot_unknown;
    }

    private void doBind() {
        final String key = bindInput.getText().toString().trim();
        if (key.isEmpty()) return;
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("key", key);
                JSONObject r = api.postObj("/bind", body);
                handler.post(() -> {
                    bindInput.setText("");
                    toast(r.optString("message", "已绑定"));
                    structureSig = "";
                    loadHosts();
                });
            } catch (Exception e) {
                handler.post(() -> toast(e.getMessage()));
            }
        }).start();
    }

    private void confirmUnbind(Host h) {
        new AlertDialog.Builder(this)
                .setMessage("确定解绑这台宿主机？")
                .setNegativeButton("取消", null)
                .setPositiveButton("解绑", (d, w) -> doUnbind(h.id))
                .show();
    }

    private void doUnbind(long hostId) {
        new Thread(() -> {
            try {
                api.postObj("/hosts/" + hostId + "/unbind", new JSONObject());
                handler.post(() -> {
                    structureSig = "";
                    loadHosts();
                });
            } catch (Exception e) {
                handler.post(() -> toast(e.getMessage()));
            }
        }).start();
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "个人中心");
        menu.getMenu().add(0, 2, 1, alertCount > 0 ? "告警（" + alertCount + "）" : "告警");
        if ("admin".equals(session.role())) {
            menu.getMenu().add(0, 3, 2, "管理");
        }
        menu.getMenu().add(0, 4, 3, "操作日志");
        menu.getMenu().add(0, 5, 4, "退出登录");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    startActivity(new Intent(this, ProfileActivity.class));
                    break;
                case 2:
                    startActivity(new Intent(this, AlertsActivity.class));
                    break;
                case 3:
                    startActivity(new Intent(this, AdminActivity.class));
                    break;
                case 4:
                    startActivity(new Intent(this, LogsActivity.class));
                    break;
                case 5:
                    AlertWatcher.stop(this);
                    session.clear();
                    PanelConfig.clear();
                    toLogin();
                    break;
                default:
                    break;
            }
            return true;
        });
        menu.show();
    }

    private void openDetail(long serverId) {
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra("server_id", serverId);
        startActivity(i);
    }

    private String roleText(String r) {
        if ("admin".equals(r)) return "管理员";
        if ("operator".equals(r)) return "操作员";
        if ("viewer".equals(r)) return "只读";
        return r;
    }

    private void toLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg == null ? "请求失败" : msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
