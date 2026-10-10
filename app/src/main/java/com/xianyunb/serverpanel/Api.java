package com.xianyunb.serverpanel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Api {

    /** 登录过期（401）时的统一回调，由 App 在启动时挂上：清会话 + 回登录页 */
    public static volatile Runnable onUnauthorized;

    private final String baseUrl;
    private String token;

    public Api(String baseUrl) {
        this.baseUrl = normalize(baseUrl);
    }

    public void setToken(String token) {
        this.token = token;
    }

    public static String normalize(String url) {
        if (url == null) return "";
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    public JSONObject getObj(String path) throws Exception {
        return toObj(request("GET", path, null));
    }

    public JSONObject postObj(String path, JSONObject body) throws Exception {
        return toObj(request("POST", path, body));
    }

    public JSONArray getArray(String path) throws Exception {
        return toArray(request("GET", path, null));
    }

    public JSONObject obj(String method, String path, JSONObject body) throws Exception {
        return toObj(request(method, path, body));
    }

    public JSONArray arr(String method, String path, JSONObject body) throws Exception {
        return toArray(request(method, path, body));
    }

    private static JSONObject toObj(String text) throws Exception {
        if (text == null || text.isEmpty()) return new JSONObject();
        return new JSONObject(text);
    }

    private static JSONArray toArray(String text) throws Exception {
        if (text == null || text.isEmpty()) return new JSONArray();
        return new JSONArray(text);
    }

    public String request(String method, String path, JSONObject body) throws Exception {
        URL url = new URL(baseUrl + "/api" + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept", "application/json");
        if (token != null && !token.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream os = c.getOutputStream();
            os.write(data);
            os.close();
        }

        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = read(is);
        c.disconnect();

        if (code >= 400) {
            // 登录/注册自己的 401 是「账号密码不对」，不算登录过期
            boolean authEntry = "/auth/login".equals(path) || "/auth/register".equals(path);
            if (code == 401 && !authEntry && onUnauthorized != null) {
                onUnauthorized.run();
            }
            String msg = "请求失败 " + code;
            try {
                JSONObject o = new JSONObject(text);
                if (o.has("error")) msg = o.getString("error");
            } catch (Exception ignored) {
            }
            throw new ApiException(msg, code);
        }
        return text;
    }

    private static String read(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            bo.write(buf, 0, n);
        }
        is.close();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    public static class ApiException extends Exception {
        public final int code;

        public ApiException(String message, int code) {
            super(message);
            this.code = code;
        }
    }
}
