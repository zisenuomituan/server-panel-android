package com.xianyunb.serverpanel;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

public class AlertsActivity extends AppCompatActivity {

    private Session session;
    private Api api;
    private LinearLayout alertsContainer;
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
        setContentView(R.layout.activity_alerts);
        api = new Api(session.baseUrl());
        api.setToken(session.token());

        alertsContainer = findViewById(R.id.alertsContainer);
        empty = findViewById(R.id.empty);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnRefresh).setOnClickListener(v -> load());
        findViewById(R.id.btnAckAll).setOnClickListener(v -> ackAll());

        load();
    }

    private void load() {
        if (alertsContainer.getChildCount() == 0) {
            empty.setText("正在加载…");
            empty.setVisibility(View.VISIBLE);
        }
        new Thread(() -> {
            JSONArray data = null;
            String err = null;
            try {
                data = api.getArray("/alerts");
            } catch (Exception e) {
                err = e.getMessage();
            }
            final JSONArray result = data;
            final String error = err;
            handler.post(() -> {
                if (result != null) render(result);
                else if (error != null) toast(error);
            });
        }).start();
    }

    private void render(JSONArray arr) {
        alertsContainer.removeAllViews();
        if (arr.length() == 0) {
            empty.setText("没有告警，一切正常");
            empty.setVisibility(View.VISIBLE);
            return;
        }
        empty.setVisibility(View.GONE);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) alertsContainer.addView(card(Alert.from(o)));
        }
    }

    private View card(final Alert a) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        dot.setBackgroundResource(R.drawable.bg_dot);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dot.setLayoutParams(dotLp);
        dot.getBackground().mutate().setTint(ContextCompat.getColor(this, dotColor(a)));

        TextView title = tv(a.kindText(), 14, R.color.text, false);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(8), 0, 0, 0);

        TextView target = tv(a.target, 12, R.color.dim, true);
        target.setPadding(dp(8), 0, 0, 0);

        row1.addView(dot);
        row1.addView(title);
        row1.addView(target);

        if (a.count > 1) {
            TextView badge = tv("×" + a.count, 11, R.color.amber, true);
            badge.setBackgroundResource(R.drawable.bg_badge);
            badge.setPadding(dp(8), dp(1), dp(8), dp(1));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.leftMargin = dp(8);
            badge.setLayoutParams(blp);
            row1.addView(badge);
        }

        if (!a.isActive()) {
            TextView done = tv("已恢复", 11, R.color.green, false);
            done.setPadding(dp(8), 0, 0, 0);
            row1.addView(done);
        } else if (a.acked) {
            TextView done = tv("已确认", 11, R.color.dim, false);
            done.setPadding(dp(8), 0, 0, 0);
            row1.addView(done);
        }

        card.addView(row1);

        TextView msg = tv(a.message, 14, R.color.text, false);
        msg.setPadding(0, dp(8), 0, 0);
        card.addView(msg);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setGravity(Gravity.CENTER_VERTICAL);
        row3.setPadding(0, dp(8), 0, 0);

        String time = a.updatedAt.isEmpty() ? a.createdAt : a.updatedAt;
        TextView ts = tv(Fmt.time(time), 12, R.color.dim, true);
        ts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row3.addView(ts);

        if (a.isActive() && !a.acked) {
            Button ack = new Button(this);
            ack.setText("确认");
            ack.setTextSize(12);
            ack.setAllCaps(false);
            ack.setBackgroundResource(R.drawable.bg_btn);
            ack.setTextColor(ContextCompat.getColor(this, R.color.text));
            ack.setMinHeight(dp(30));
            ack.setPadding(dp(12), 0, dp(12), 0);
            ack.setOnClickListener(v -> ackOne(a.id));
            row3.addView(ack);
        }

        card.addView(row3);
        return card;
    }

    private int dotColor(Alert a) {
        if (!a.isActive()) return R.color.green;
        return a.isCritical() ? R.color.red : R.color.amber;
    }

    private void ackOne(final long id) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("ids", new JSONArray().put(id));
                api.postObj("/alerts/ack", body);
                handler.post(this::load);
            } catch (Exception e) {
                handler.post(() -> toast(e.getMessage()));
            }
        }).start();
    }

    private void ackAll() {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("all", true);
                api.postObj("/alerts/ack", body);
                handler.post(() -> {
                    toast("已全部确认");
                    load();
                });
            } catch (Exception e) {
                handler.post(() -> toast(e.getMessage()));
            }
        }).start();
    }

    private TextView tv(String text, float size, int colorRes, boolean mono) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(ContextCompat.getColor(this, colorRes));
        if (mono) v.setTypeface(Typeface.MONOSPACE);
        return v;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg == null ? "请求失败" : msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
