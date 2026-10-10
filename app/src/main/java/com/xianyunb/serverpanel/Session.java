package com.xianyunb.serverpanel;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

public class Session {

    private static final String PREF = "panel_session";

    private final SharedPreferences sp;

    public Session(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public String baseUrl() {
        return sp.getString("base_url", "");
    }

    public String token() {
        return sp.getString("token", "");
    }

    public String username() {
        return sp.getString("username", "");
    }

    public String role() {
        return sp.getString("role", "");
    }

    public void setBaseUrl(String url) {
        sp.edit().putString("base_url", url).apply();
    }

    public void save(String baseUrl, String token, JSONObject user) {
        SharedPreferences.Editor e = sp.edit()
                .putString("base_url", baseUrl)
                .putString("token", token)
                .putLong("renewed_at", System.currentTimeMillis());
        if (user != null) {
            e.putString("username", user.optString("username", ""));
            e.putString("role", user.optString("role", ""));
        }
        e.apply();
    }

    /** 上次拿到令牌的时间（含续期）：长效登录据此决定要不要换新令牌 */
    public long renewedAt() {
        return sp.getLong("renewed_at", 0L);
    }

    public void saveToken(String token) {
        sp.edit().putString("token", token).apply();
    }

    public void markRenewed() {
        sp.edit().putLong("renewed_at", System.currentTimeMillis()).apply();
    }

    public boolean loggedIn() {
        return !token().isEmpty() && !baseUrl().isEmpty();
    }

    /** 退出登录 / 登录过期：清掉账号信息，但保留服务器地址，省得再输一遍 */
    public void logout() {
        String base = baseUrl();
        sp.edit().clear().apply();
        if (!base.isEmpty()) {
            sp.edit().putString("base_url", base).apply();
        }
    }

    public void clear() {
        sp.edit().clear().apply();
    }
}
