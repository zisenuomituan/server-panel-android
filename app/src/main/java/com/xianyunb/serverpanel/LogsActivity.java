package com.xianyunb.serverpanel;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

public class LogsActivity extends AppCompatActivity {

    private Session session;
    private Api api;
    private LinearLayout logsContainer;
    private TextView empty;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new Session(this);
        if (!session.loggedIn()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_logs);
        api = new Api(session.baseUrl());
        api.setToken(session.token());

        logsContainer = findViewById(R.id.logsContainer);
        empty = findViewById(R.id.empty);
        empty.setText("正在加载…");
        empty.setVisibility(View.VISIBLE);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnRefresh).setOnClickListener(v -> load());

        load();
    }

    private void load() {
        if (logsContainer.getChildCount() == 0) {
            empty.setText("正在加载…");
            empty.setVisibility(View.VISIBLE);
        }
        new Thread(() -> {
            JSONArray logs = null;
            String err = null;
            try {
                logs = api.getArray("/audit");
            } catch (Exception e) {
                err = e.getMessage();
            }
            final JSONArray data = logs;
            final String error = err;
            handler.post(() -> {
                if (data != null) render(data);
                else if (error != null) toast(error);
            });
        }).start();
    }

    private void render(JSONArray logs) {
        logsContainer.removeAllViews();
        if (logs.length() == 0) {
            empty.setText("还没有日志");
            empty.setVisibility(View.VISIBLE);
            return;
        }
        empty.setVisibility(View.GONE);

        for (int i = 0; i < logs.length(); i++) {
            JSONObject l = logs.optJSONObject(i);
            if (l == null) continue;
            logsContainer.addView(logCard(l));
        }
    }

    private View logCard(JSONObject l) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        String result = l.optString("result", "");

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView time = tv(Fmt.dateTime(l.optString("ts", "")), 12, R.color.dim, true);
        TextView action = tv(actionText(l.optString("action", "")), 14, R.color.text, false);
        action.setPadding(dp(10), 0, 0, 0);
        action.setTextColor(ContextCompat.getColor(this, R.color.text));

        TextView res = tv(result, 12, result.startsWith("失败") ? R.color.red : R.color.dim, false);
        res.setPadding(dp(10), 0, 0, 0);

        row1.addView(time);
        row1.addView(action);
        row1.addView(res);
        card.addView(row1);

        String who = l.optString("username", "");
        String target = targetName(l);
        String ip = l.optString("ip", "");
        TextView row2 = tv((who.isEmpty() ? "-" : who) + " · " + target + " · " + ip, 12, R.color.dim, true);
        row2.setPadding(0, dp(5), 0, 0);
        card.addView(row2);

        String detail = l.optString("detail", "");
        if (!detail.isEmpty()) {
            TextView row3 = tv(detail, 12, R.color.term_text, true);
            row3.setPadding(0, dp(4), 0, 0);
            card.addView(row3);
        }
        return card;
    }

    private TextView tv(String text, float size, int colorRes, boolean mono) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(ContextCompat.getColor(this, colorRes));
        if (mono) v.setTypeface(android.graphics.Typeface.MONOSPACE);
        return v;
    }

    private String targetName(JSONObject l) {
        long serverId = l.optLong("server_id");
        long hostId = l.optLong("host_id");
        if (serverId > 0) return "VM#" + serverId;
        if (hostId > 0) return "宿主#" + hostId;
        return "-";
    }

    private String actionText(String a) {
        if ("login".equals(a)) return "登录";
        if ("register".equals(a)) return "注册";
        if ("bind".equals(a)) return "绑定";
        if ("unbind".equals(a)) return "解绑";
        if ("create_host".equals(a)) return "登记宿主机";
        if ("delete_host".equals(a)) return "删除宿主";
        if ("rotate_key".equals(a)) return "轮换密钥";
        if ("revoke_key".equals(a)) return "撤销密钥";
        if ("user_create".equals(a)) return "新建用户";
        if ("user_role".equals(a)) return "改角色";
        if ("user_delete".equals(a)) return "删除用户";
        if ("user_passwd".equals(a)) return "重置密码";
        if ("power".equals(a)) return "电源";
        if ("exec".equals(a)) return "执行命令";
        if ("host_exec".equals(a)) return "宿主命令";
        if ("change_password".equals(a)) return "改密";
        if ("settings".equals(a)) return "设置";
        return a;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg == null ? "请求失败" : msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
