package com.xianyunb.serverpanel;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private EditText addr, user, pass, bind, ruser, remail, rpass, invite;
    private View loginBox, registerBox;
    private TextView tabLogin, tabRegister, error;
    private Button btnLogin, btnRegister;
    private View loginProgress, registerProgress;
    private Session session;
    private boolean registerMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new Session(this);
        if (session.loggedIn()) {
            openMain();
            return;
        }
        setContentView(R.layout.activity_login);

        addr = findViewById(R.id.addr);
        user = findViewById(R.id.user);
        pass = findViewById(R.id.pass);
        bind = findViewById(R.id.bind);
        ruser = findViewById(R.id.ruser);
        remail = findViewById(R.id.remail);
        rpass = findViewById(R.id.rpass);
        invite = findViewById(R.id.invite);
        loginBox = findViewById(R.id.loginBox);
        registerBox = findViewById(R.id.registerBox);
        tabLogin = findViewById(R.id.tabLogin);
        tabRegister = findViewById(R.id.tabRegister);
        error = findViewById(R.id.error);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        loginProgress = findViewById(R.id.loginProgress);
        registerProgress = findViewById(R.id.registerProgress);

        addr.setText(session.baseUrl().isEmpty() ? BuildConfig.DEFAULT_SERVER_URL : session.baseUrl());

        TextView brand = findViewById(R.id.brand);
        SpannableString ss = new SpannableString("panel://kvm");
        ss.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.green)),
                5, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        brand.setText(ss);

        tabLogin.setOnClickListener(v -> setTab(false));
        tabRegister.setOnClickListener(v -> setTab(true));
        btnLogin.setOnClickListener(v -> doLogin());
        btnRegister.setOnClickListener(v -> doRegister());
        setTab(false);
    }

    private void setTab(boolean register) {
        registerMode = register;
        loginBox.setVisibility(register ? View.GONE : View.VISIBLE);
        registerBox.setVisibility(register ? View.VISIBLE : View.GONE);
        tabLogin.setSelected(!register);
        tabRegister.setSelected(register);
        tabLogin.setTextColor(ContextCompat.getColor(this, register ? R.color.dim : R.color.text));
        tabRegister.setTextColor(ContextCompat.getColor(this, register ? R.color.text : R.color.dim));
        error.setText("");
    }

    private void doLogin() {
        final String base = Api.normalize(addr.getText().toString());
        if (base.isEmpty()) {
            error.setText("请填写服务器地址");
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("username", user.getText().toString().trim());
            body.put("password", pass.getText().toString());
            body.put("bind_key", bind.getText().toString().trim());
        } catch (Exception ignored) {
        }
        setBusy(true);
        error.setText("");
        new Thread(() -> {
            try {
                Api api = new Api(base);
                JSONObject r = api.obj("POST", "/auth/login", body);
                finishLogin(base, r);
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }).start();
    }

    private void doRegister() {
        final String base = Api.normalize(addr.getText().toString());
        if (base.isEmpty()) {
            error.setText("请填写服务器地址");
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("username", ruser.getText().toString().trim());
            body.put("email", remail.getText().toString().trim());
            body.put("password", rpass.getText().toString());
            body.put("invite", invite.getText().toString().trim());
        } catch (Exception ignored) {
        }
        setBusy(true);
        error.setText("");
        new Thread(() -> {
            try {
                Api api = new Api(base);
                JSONObject r = api.obj("POST", "/auth/register", body);
                finishLogin(base, r);
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }).start();
    }

    private void finishLogin(String base, JSONObject r) {
        String token = r.optString("token", "");
        JSONObject u = r.optJSONObject("user");
        session.save(base, token, u);
        runOnUiThread(this::openMain);
    }

    private void fail(String msg) {
        runOnUiThread(() -> {
            setBusy(false);
            error.setText(msg == null ? "请求失败" : msg);
        });
    }

    private void setBusy(boolean busy) {
        btnLogin.setEnabled(!busy);
        btnRegister.setEnabled(!busy);
        btnLogin.setText(busy && !registerMode ? "登录中…" : "进入面板");
        btnRegister.setText(busy && registerMode ? "创建中…" : "创建账号");
        loginProgress.setVisibility(busy && !registerMode ? View.VISIBLE : View.GONE);
        registerProgress.setVisibility(busy && registerMode ? View.VISIBLE : View.GONE);
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
