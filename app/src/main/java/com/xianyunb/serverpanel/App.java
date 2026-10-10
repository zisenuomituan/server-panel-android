package com.xianyunb.serverpanel;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * 应用入口：把「登录过期」的处理挂到 {@link Api#onUnauthorized}。
 * 任何请求（含后台线程）拿到 401 时，统一清掉会话并回到登录页，
 * 用户看到的是一句明确提示，而不是满屏的「请求失败 401」。
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        final Handler ui = new Handler(Looper.getMainLooper());
        Api.onUnauthorized = () -> ui.post(() -> {
            Session s = new Session(this);
            if (!s.loggedIn()) {
                // 已经登出过了：后台告警任务每 5 分钟还会再试一次，不能每次都弹提示
                return;
            }
            s.logout();
            AlertWatcher.stop(this); // 令牌失效后停掉后台轮询，等用户重新登录再开
            PanelConfig.clear();
            Toast.makeText(this, "登录已过期，请重新登录", Toast.LENGTH_LONG).show();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
    }
}
