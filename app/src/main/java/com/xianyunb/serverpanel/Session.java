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
                .putString("token", token);
        if (user != null) {
            e.putString("username", user.optString("username", ""));
            e.putString("role", user.optString("role", ""));
        }
        e.apply();
    }

    public boolean loggedIn() {
        return !token().isEmpty() && !baseUrl().isEmpty();
    }

    public void clear() {
        sp.edit().clear().apply();
    }
}
