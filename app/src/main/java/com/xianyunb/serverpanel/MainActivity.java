package com.xianyunb.serverpanel;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final long POLL_MS = 5000;

    private Session session;
    private Api api;
    private LinearLayout listContainer;
    private EditText bindInput;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Long, ServerCard> cards = new HashMap<>();
    private String structureSig = "";
    private boolean loading;
    private boolean paused;

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
        findViewById(R.id.btnBind).setOnClickListener(v -> doBind());

        // 自用版启动时静默检查一次更新
        if (BuildConfig.SELF_USE) {
            Updater.check(this, true);
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
        new Thread(() -> {
            List<Host> data = null;
            String err = null;
            try {
                data = parseHosts(api.getArray("/hosts"));
            } catch (Exception e) {
                err = e.getMessage();
            }
            final List<Host> result = data;
            final String error = err;
            handler.post(() -> {
                loading = false;
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
        StringBuilder sig = new StringBuilder();
        for (Host h : hosts) {
            sig.append('#').append(h.id);
            for (Server s : h.servers) sig.append(',').append(s.id);
            sig.append(';');
        }
        if (!sig.toString().equals(structureSig)) {
            structureSig = sig.toString();
            rebuild(hosts);
        } else {
            for (Host h : hosts) {
                for (Server s : h.servers) {
                    ServerCard card = cards.get(s.id);
                    if (card != null) card.bind(s, s.live);
                }
            }
        }
    }

    private void rebuild(List<Host> hosts) {
        listContainer.removeAllViews();
        cards.clear();
        LayoutInflater inflater = LayoutInflater.from(this);

        if (hosts.isEmpty()) {
            listContainer.addView(emptyText("还没有绑定宿主机，在上方输入密钥即可绑定。", 40));
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
        menu.getMenu().add("个人中心");
        if ("admin".equals(session.role())) {
            menu.getMenu().add("管理");
        }
        menu.getMenu().add("操作日志");
        menu.getMenu().add("退出登录");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("个人中心".equals(title)) {
                startActivity(new Intent(this, ProfileActivity.class));
            } else if ("管理".equals(title)) {
                startActivity(new Intent(this, AdminActivity.class));
            } else if ("操作日志".equals(title)) {
                startActivity(new Intent(this, LogsActivity.class));
            } else if ("退出登录".equals(title)) {
                session.clear();
                toLogin();
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
