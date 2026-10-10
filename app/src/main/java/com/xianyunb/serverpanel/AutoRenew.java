package com.xianyunb.serverpanel;

import org.json.JSONObject;

/**
 * 长效登录：令牌还在有效期内时，隔一段时间换一张新的（后端 POST /api/auth/renew）。
 * 面板默认令牌有效期 30 天，只要这段时间内打开过一次应用，登录状态就会自动续下去，
 * 不用反复输密码；真超过有效期没用过，则会由 {@link Api#onUnauthorized} 送回登录页。
 */
public class AutoRenew {

    /** 续期间隔：6 小时内最多续一次，避免频繁请求 */
    private static final long INTERVAL_MS = 6L * 60 * 60 * 1000;

    private static volatile boolean running;

    private AutoRenew() {
    }

    public static void maybe(final Session session) {
        if (!session.loggedIn() || running) {
            return;
        }
        if (System.currentTimeMillis() - session.renewedAt() < INTERVAL_MS) {
            return;
        }
        running = true;

        final String base = session.baseUrl();
        final String token = session.token();
        new Thread(() -> {
            try {
                Api api = new Api(base);
                api.setToken(token);
                JSONObject r = api.postObj("/auth/renew", new JSONObject());
                String t = r.optString("token", "");
                if (!t.isEmpty()) {
                    session.saveToken(t);
                    session.markRenewed();
                }
            } catch (Exception ignored) {
                // 续期失败不打扰用户：令牌真失效时下一次请求会走 401 分支
            } finally {
                running = false;
            }
        }, "panel-auto-renew").start();
    }
}
