package com.xianyunb.serverpanel;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

public class ProfileActivity extends AppCompatActivity {

    private Session session;
    private Api api;
    private LinearLayout kvContainer;
    private LinearLayout panelContainer;
    private EditText pwdOld, pwdNew;

    private View updatePanel;
    private LinearLayout versionContainer;
    private String panelVersion = "";

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new Session(this);
        if (!session.loggedIn()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_profile);
        api = new Api(session.baseUrl());
        api.setToken(session.token());

        kvContainer = findViewById(R.id.kvContainer);
        kvContainer.addView(loadingHint());
        panelContainer = findViewById(R.id.panelContainer);
        panelContainer.addView(loadingHint());
        pwdOld = findViewById(R.id.pwdOld);
        pwdNew = findViewById(R.id.pwdNew);
        updatePanel = findViewById(R.id.updatePanel);
        versionContainer = findViewById(R.id.versionContainer);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnChangePwd).setOnClickListener(v -> changePassword());

        // 面板侧配置（告警开关、命令行开关）两种版本都要显示：它决定 App 能做什么
        loadConfig();

        // 版本信息与检查更新只在自用版启用
        if (BuildConfig.SELF_USE) {
            updatePanel.setVisibility(View.VISIBLE);
            renderVersions();
            findViewById(R.id.btnCheckUpdate).setOnClickListener(v -> Updater.check(this, false));
        }

        loadMe();
    }

    private void loadConfig() {
        new Thread(() -> {
            final PanelConfig p = PanelConfig.fetch(api);
            handler.post(() -> {
                if (p != null) {
                    panelVersion = p.panelVersion;
                    renderVersions();
                }
                renderPanel(p);
            });
        }).start();
    }

    private void renderPanel(PanelConfig p) {
        panelContainer.removeAllViews();
        if (p == null) {
            addRowInto(panelContainer, "面板版本", "读取失败", R.color.red);
            panelContainer.addView(hintText("拉取面板配置失败，请检查网络或登录状态。"));
            return;
        }
        addRowInto(panelContainer, "面板版本", p.panelVersion.isEmpty() ? "-" : p.panelVersion, 0);
        addRowInto(panelContainer, "告警通知", p.alertSwitchText(),
                p.alertsEnabled ? R.color.green : R.color.amber);
        panelContainer.addView(hintText(p.alertDetail()));
        addRowInto(panelContainer, "命令行", p.enableExec ? "已开启" : "已关闭",
                p.enableExec ? R.color.green : R.color.amber);
        panelContainer.addView(hintText(p.execDetail()));
    }

    private void renderVersions() {
        versionContainer.removeAllViews();
        addRowInto(versionContainer, "App 版本",
                BuildConfig.VERSION_NAME + "（" + BuildConfig.VERSION_CODE + "）", 0);
        addRowInto(versionContainer, "面板版本",
                panelVersion.isEmpty() ? "-" : panelVersion, 0);
    }

    private void loadMe() {
        new Thread(() -> {
            JSONObject me = null;
            String err = null;
            try {
                me = api.getObj("/me");
            } catch (Exception e) {
                err = e.getMessage();
            }
            final JSONObject u = me;
            final String error = err;
            handler.post(() -> {
                if (u != null) renderAccount(u);
                else if (error != null) toast(error);
            });
        }).start();
    }

    private TextView loadingHint() {
        TextView tv = new TextView(this);
        tv.setText("正在加载…");
        tv.setTextColor(ContextCompat.getColor(this, R.color.dim));
        tv.setTextSize(14);
        return tv;
    }

    private TextView hintText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(this, R.color.dim));
        tv.setTextSize(12);
        tv.setLineSpacing(dp(2), 1f);
        tv.setPadding(0, dp(2), 0, dp(6));
        return tv;
    }

    private void renderAccount(JSONObject u) {
        kvContainer.removeAllViews();
        addRow("用户名", u.optString("username", "-"));
        addRow("角色", roleText(u.optString("role", "")));
        String email = u.optString("email", "");
        addRow("邮箱", email.isEmpty() ? "-" : email);
        addRow("创建时间", Fmt.dateTime(u.optString("created_at", "")));
    }

    private void addRow(String k, String v) {
        addRowInto(kvContainer, k, v, 0);
    }

    private void addRowInto(LinearLayout container, String k, String v) {
        addRowInto(container, k, v, 0);
    }

    private void addRowInto(LinearLayout container, String k, String v, int colorRes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView kk = new TextView(this);
        kk.setText(k);
        kk.setTextColor(ContextCompat.getColor(this, R.color.dim));
        kk.setTextSize(12);
        kk.setLayoutParams(new LinearLayout.LayoutParams(dp(84), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView vv = new TextView(this);
        vv.setText(v);
        vv.setTextColor(ContextCompat.getColor(this, colorRes == 0 ? R.color.text : colorRes));
        vv.setTextSize(14);

        row.addView(kk);
        row.addView(vv);
        container.addView(row);
    }

    private void changePassword() {
        final String oldPwd = pwdOld.getText().toString();
        final String newPwd = pwdNew.getText().toString();
        if (newPwd.length() < 8) {
            toast("新密码至少 8 位");
            return;
        }
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("old", oldPwd);
                body.put("new", newPwd);
                api.postObj("/me/password", body);
                handler.post(() -> {
                    pwdOld.setText("");
                    pwdNew.setText("");
                    toast("密码已修改");
                });
            } catch (Exception e) {
                handler.post(() -> toast(e.getMessage()));
            }
        }).start();
    }

    private String roleText(String r) {
        if ("admin".equals(r)) return "管理员";
        if ("operator".equals(r)) return "操作员";
        if ("viewer".equals(r)) return "只读";
        return r;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg == null ? "请求失败" : msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
