package com.xianyunb.serverpanel;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

public class AdminActivity extends AppCompatActivity {

    private static final String[] ROLE_LABELS = {"操作员", "只读", "管理员"};
    private static final String[] ROLE_VALUES = {"operator", "viewer", "admin"};

    private Session session;
    private Api api;

    private CheckBox cbExec;
    private EditText hostName, hostAddr, hostPort, hostUser, hostUri;
    private View hostKeyBox;
    private TextView hostKeyOut;
    private EditText nuName, nuPass, nuEmail;
    private Spinner nuRole;
    private LinearLayout usersContainer, keysContainer;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean settingLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new Session(this);
        if (!session.loggedIn() || !"admin".equals(session.role())) {
            finish();
            return;
        }
        setContentView(R.layout.activity_admin);
        api = new Api(session.baseUrl());
        api.setToken(session.token());

        cbExec = findViewById(R.id.cbExec);
        hostName = findViewById(R.id.hostName);
        hostAddr = findViewById(R.id.hostAddr);
        hostPort = findViewById(R.id.hostPort);
        hostUser = findViewById(R.id.hostUser);
        hostUri = findViewById(R.id.hostUri);
        hostKeyBox = findViewById(R.id.hostKeyBox);
        hostKeyOut = findViewById(R.id.hostKeyOut);
        nuName = findViewById(R.id.nuName);
        nuPass = findViewById(R.id.nuPass);
        nuEmail = findViewById(R.id.nuEmail);
        nuRole = findViewById(R.id.nuRole);
        usersContainer = findViewById(R.id.usersContainer);
        keysContainer = findViewById(R.id.keysContainer);

        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, ROLE_LABELS);
        roleAdapter.setDropDownViewResource(R.layout.spinner_item);
        nuRole.setAdapter(roleAdapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCreateHost).setOnClickListener(v -> createHost());
        findViewById(R.id.btnCopyKey).setOnClickListener(v -> copyKey());
        findViewById(R.id.btnCreateUser).setOnClickListener(v -> createUser());
        cbExec.setOnCheckedChangeListener((v, checked) -> {
            if (settingLoaded) saveSettings(checked);
        });

        loadSettings();
        loadUsers();
        loadKeys();
    }

    private void loadSettings() {
        new Thread(() -> {
            JSONObject s = null;
            String err = null;
            try {
                s = api.getObj("/admin/settings");
            } catch (Exception e) {
                err = e.getMessage();
            }
            final JSONObject o = s;
            final String error = err;
            handler.post(() -> {
                if (o != null) {
                    settingLoaded = false;
                    cbExec.setChecked(o.optBoolean("enable_exec", true));
                    settingLoaded = true;
                } else if (error != null) {
                    toast(error);
                }
            });
        }).start();
    }

    private void saveSettings(final boolean enabled) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("enable_exec", enabled);
                api.postObj("/admin/settings", body);
                handler.post(() -> toast("设置已保存"));
            } catch (Exception e) {
                handler.post(() -> {
                    toast(e.getMessage());
                    loadSettings();
                });
            }
        }).start();
    }

    private void createHost() {
        final String name = hostName.getText().toString().trim();
        final String addr = hostAddr.getText().toString().trim();
        final String port = hostPort.getText().toString().trim();
        final String user = hostUser.getText().toString().trim();
        final String uri = hostUri.getText().toString().trim();
        if (name.isEmpty() || addr.isEmpty() || port.isEmpty()) {
            toast("名称、地址、端口都要填");
            return;
        }
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("ssh_host", addr);
                body.put("ssh_port", Integer.parseInt(port));
                body.put("ssh_user", user);
                body.put("libvirt_uri", uri);
                JSONObject r = api.postObj("/admin/hosts", body);
                final String formatted = r.optString("formatted", r.optString("bind_key", ""));
                handler.post(() -> {
                    showKey(formatted);
                    hostName.setText("");
                    hostAddr.setText("");
                    hostPort.setText("");
                    hostUser.setText("");
                    hostUri.setText("");
                    loadKeys();
                });
            } catch (Exception e) {
                handler.post(() -> toast(e.getMessage()));
            }
        }).start();
    }

    private void copyKey() {
        String key = hostKeyOut.getText().toString().trim();
        if (key.isEmpty()) {
            toast("还没有可复制的密钥");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null) {
            toast("系统剪贴板不可用");
            return;
        }
        cm.setPrimaryClip(ClipData.newPlainText("bind key", key));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            toast("密钥已复制");
        }
    }

    private void showKey(String formatted) {
        if (formatted == null || formatted.isEmpty()) return;
        hostKeyBox.setVisibility(View.VISIBLE);
        hostKeyOut.setText(formatted);
    }

    private void createUser() {
        final String name = nuName.getText().toString().trim();
        final String pass = nuPass.getText().toString();
        final String email = nuEmail.getText().toString().trim();
        final String role = ROLE_VALUES[Math.max(0, nuRole.getSelectedItemPosition())];
        if (name.isEmpty() || pass.isEmpty()) {
            toast("用户名和密码都要填");
            return;
        }
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("username", name);
                body.put("password", pass);
                body.put("role", role);
                body.put("email", email);
                api.postObj("/admin/users", body);
                handler.post(() -> {
                    nuName.setText("");
                    nuPass.setText("");
                    nuEmail.setText("");
                    toast("用户已创建");
                    loadUsers();
                });
            } catch (Exception e) {
                handler.post(() -> toast(e.getMessage()));
            }
        }).start();
    }

    private void loadUsers() {
        new Thread(() -> {
            JSONArray users = null;
            String err = null;
            try {
                users = api.getArray("/admin/users");
            } catch (Exception e) {
                err = e.getMessage();
            }
            final JSONArray data = users;
            final String error = err;
            handler.post(() -> {
                if (data != null) renderUsers(data);
                else if (error != null) toast(error);
            });
        }).start();
    }

    private void renderUsers(JSONArray users) {
        usersContainer.removeAllViews();
        if (users.length() == 0) {
            usersContainer.addView(hint("还没有用户"));
            return;
        }
        for (int i = 0; i < users.length(); i++) {
            JSONObject u = users.optJSONObject(i);
            if (u != null) usersContainer.addView(userRow(u));
        }
    }

    private View userRow(final JSONObject u) {
        final long id = u.optLong("id");
        final String username = u.optString("username", "");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = tv("#" + id + " " + username, 14, R.color.text, false);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView email = tv(u.optString("email", ""), 12, R.color.dim, true);
        email.setPadding(dp(8), 0, dp(8), 0);

        Spinner role = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, R.layout.spinner_item, ROLE_LABELS);
        ad.setDropDownViewResource(R.layout.spinner_item);
        role.setAdapter(ad);
        role.setSelection(roleIndex(u.optString("role", "")));
        role.setLayoutParams(new LinearLayout.LayoutParams(dp(108), LinearLayout.LayoutParams.WRAP_CONTENT));
        role.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first = true;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long itemId) {
                if (first) {
                    first = false;
                    return;
                }
                setRole(id, username, ROLE_VALUES[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        top.addView(name);
        top.addView(email);
        top.addView(role);
        row.addView(top);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(6), 0, 0);

        Button reset = smallButton("重置密码");
        reset.setOnClickListener(v -> promptResetPassword(id, username));
        Button del = smallButton("删除");
        del.setTextColor(ContextCompat.getColor(this, R.color.danger_text));
        del.setOnClickListener(v -> confirmDeleteUser(id, username));
        actions.addView(reset);
        actions.addView(del);
        row.addView(actions);

        return row;
    }

    private int roleIndex(String role) {
        for (int i = 0; i < ROLE_VALUES.length; i++) {
            if (ROLE_VALUES[i].equals(role)) return i;
        }
        return 0;
    }

    private void setRole(final long id, final String username, final String role) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("role", role);
                api.obj("PATCH", "/admin/users/" + id, body);
                handler.post(() -> {
                    toast(username + " 角色已更新");
                    loadUsers();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    toast(e.getMessage());
                    loadUsers();
                });
            }
        }).start();
    }

    private void promptResetPassword(final long id, final String username) {
        final EditText input = new EditText(this);
        input.setHint("新密码，至少 8 位");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTextColor(ContextCompat.getColor(this, R.color.text));
        input.setHintTextColor(ContextCompat.getColor(this, R.color.placeholder));

        new AlertDialog.Builder(this)
                .setTitle("重置 " + username + " 的密码")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> resetPassword(id, input.getText().toString()))
                .show();
    }

    private void resetPassword(final long id, final String password) {
        if (password.length() < 8) {
            toast("密码至少 8 位");
            return;
        }
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("password", password);
                api.postObj("/admin/users/" + id + "/password", body);
                handler.post(() -> toast("密码已重置"));
            } catch (Exception e) {
                handler.post(() -> toast(e.getMessage()));
            }
        }).start();
    }

    private void confirmDeleteUser(final long id, final String username) {
        new AlertDialog.Builder(this)
                .setMessage("确定删除用户 " + username + "？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> new Thread(() -> {
                    try {
                        api.obj("DELETE", "/admin/users/" + id, null);
                        handler.post(() -> {
                            toast("已删除");
                            loadUsers();
                        });
                    } catch (Exception e) {
                        handler.post(() -> toast(e.getMessage()));
                    }
                }).start())
                .show();
    }

    private void loadKeys() {
        new Thread(() -> {
            JSONArray groups = null;
            String err = null;
            try {
                groups = api.getArray("/admin/keys");
            } catch (Exception e) {
                err = e.getMessage();
            }
            final JSONArray data = groups;
            final String error = err;
            handler.post(() -> {
                if (data != null) renderKeys(data);
                else if (error != null) toast(error);
            });
        }).start();
    }

    private void renderKeys(JSONArray groups) {
        keysContainer.removeAllViews();
        if (groups.length() == 0) {
            keysContainer.addView(hint("还没有登记宿主机"));
            return;
        }
        for (int i = 0; i < groups.length(); i++) {
            JSONObject g = groups.optJSONObject(i);
            if (g != null) keysContainer.addView(hostKeyGroup(g));
        }
    }

    private View hostKeyGroup(JSONObject g) {
        final JSONObject host = g.optJSONObject("host");
        final long hostId = host != null ? host.optLong("id") : 0;
        final String hostName = host != null ? host.optString("name", "") : "";
        final String addr = host != null
                ? host.optString("ssh_host", "") + ":" + host.optInt("ssh_port")
                : "";

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = tv(hostName, 15, R.color.text, false);
        name.setTypeface(null, Typeface.BOLD);
        TextView ad = tv(addr, 12, R.color.dim, true);
        ad.setPadding(dp(10), 0, 0, 0);
        head.addView(name);
        head.addView(ad);
        box.addView(head);

        box.addView(hint("宿主级密钥 · 绑定后可管理该宿主下所有虚拟机"));

        JSONArray hostKeys = g.optJSONArray("host_keys");
        boolean anyKey = false;
        if (hostKeys != null) {
            for (int i = 0; i < hostKeys.length(); i++) {
                JSONObject k = hostKeys.optJSONObject(i);
                if (k != null) {
                    box.addView(keyRow(k));
                    anyKey = true;
                }
            }
        }
        if (!anyKey) box.addView(hint("暂无宿主级密钥"));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, dp(8), 0, 0);
        Button rotate = smallButton("轮换宿主密钥");
        rotate.setOnClickListener(v -> rotateHostKey(hostId));
        Button del = smallButton("删除宿主");
        del.setTextColor(ContextCompat.getColor(this, R.color.danger_text));
        del.setOnClickListener(v -> confirmDeleteHost(hostId, hostName));
        btns.addView(rotate);
        btns.addView(del);
        box.addView(btns);

        JSONArray servers = g.optJSONArray("servers");
        if (servers != null && servers.length() > 0) {
            TextView sh = hint("单机密钥 · 绑定后只能管理那一台虚拟机");
            sh.setPadding(0, dp(14), 0, 0);
            box.addView(sh);

            for (int i = 0; i < servers.length(); i++) {
                final JSONObject sv = servers.optJSONObject(i);
                if (sv == null) continue;
                final long serverId = sv.optLong("id");

                LinearLayout srow = new LinearLayout(this);
                srow.setOrientation(LinearLayout.HORIZONTAL);
                srow.setGravity(Gravity.CENTER_VERTICAL);
                srow.setPadding(0, dp(8), 0, 0);

                TextView sname = tv(sv.optString("name", ""), 14, R.color.text, false);
                sname.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                Button b = smallButton("生成/轮换单机密钥");
                b.setOnClickListener(v -> rotateServerKey(serverId));
                srow.addView(sname);
                srow.addView(b);
                box.addView(srow);

                JSONArray keys = sv.optJSONArray("keys");
                boolean any = false;
                if (keys != null) {
                    for (int j = 0; j < keys.length(); j++) {
                        JSONObject k = keys.optJSONObject(j);
                        if (k != null) {
                            box.addView(keyRow(k));
                            any = true;
                        }
                    }
                }
                if (!any) box.addView(hint("暂无单机密钥"));
            }
        }
        return box;
    }

    private View keyRow(JSONObject k) {
        final long keyId = k.optLong("id");
        String prefix = k.optString("prefix", "");
        String status = k.optString("status", "");
        String expires = k.isNull("expires_at") ? "长期" : Fmt.dateTime(k.optString("expires_at", ""));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView p = tv(prefix + "…", 12, R.color.text, true);
        p.setLayoutParams(new LinearLayout.LayoutParams(dp(100), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView s = tv("active".equals(status) ? "可用" : "已撤销", 12, R.color.dim, false);
        s.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView e = tv(expires, 12, R.color.dim, true);

        row.addView(p);
        row.addView(s);
        row.addView(e);

        if ("active".equals(status)) {
            Button revoke = smallButton("撤销");
            revoke.setTextColor(ContextCompat.getColor(this, R.color.danger_text));
            revoke.setOnClickListener(v -> revokeKey(keyId));
            row.addView(revoke);
        }
        return row;
    }

    private void revokeKey(final long keyId) {
        new Thread(() -> {
            try {
                api.postObj("/admin/keys/" + keyId + "/revoke", new JSONObject());
                handler.post(this::loadKeys);
            } catch (Exception e) {
                handler.post(() -> toast(e.getMessage()));
            }
        }).start();
    }

    private void rotateHostKey(final long hostId) {
        new AlertDialog.Builder(this)
                .setMessage("轮换后旧密钥立即失效，确定继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> new Thread(() -> {
                    try {
                        JSONObject r = api.postObj("/admin/hosts/" + hostId + "/rotate", new JSONObject());
                        final String formatted = r.optString("formatted", "");
                        handler.post(() -> {
                            showKey(formatted);
                            loadKeys();
                        });
                    } catch (Exception e) {
                        handler.post(() -> toast(e.getMessage()));
                    }
                }).start())
                .show();
    }

    private void rotateServerKey(final long serverId) {
        new AlertDialog.Builder(this)
                .setMessage("轮换后该虚拟机旧的单机密钥立即失效，确定继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> new Thread(() -> {
                    try {
                        JSONObject r = api.postObj("/admin/servers/" + serverId + "/rotate", new JSONObject());
                        final String formatted = r.optString("formatted", "");
                        handler.post(() -> {
                            showKey(formatted);
                            loadKeys();
                        });
                    } catch (Exception e) {
                        handler.post(() -> toast(e.getMessage()));
                    }
                }).start())
                .show();
    }

    private void confirmDeleteHost(final long hostId, final String hostName) {
        new AlertDialog.Builder(this)
                .setMessage("删除宿主会一并移除它的虚拟机、指标和绑定，确定？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> new Thread(() -> {
                    try {
                        api.obj("DELETE", "/admin/hosts/" + hostId, null);
                        handler.post(() -> {
                            toast("已删除");
                            loadKeys();
                        });
                    } catch (Exception e) {
                        handler.post(() -> toast(e.getMessage()));
                    }
                }).start())
                .show();
    }

    private TextView tv(String text, float size, int colorRes, boolean mono) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(ContextCompat.getColor(this, colorRes));
        if (mono) v.setTypeface(Typeface.MONOSPACE);
        return v;
    }

    private TextView hint(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(12);
        v.setTextColor(ContextCompat.getColor(this, R.color.dim));
        v.setPadding(0, dp(4), 0, dp(4));
        return v;
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setBackgroundResource(R.drawable.bg_btn);
        b.setTextColor(ContextCompat.getColor(this, R.color.text));
        b.setMinHeight(dp(32));
        b.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg == null ? "请求失败" : msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
