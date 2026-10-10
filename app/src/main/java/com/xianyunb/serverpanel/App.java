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
            new Session(this).logout();
            PanelConfig.clear();
            Toast.makeText(this, "登录已过期，请重新登录", Toast.LENGTH_LONG).show();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
    }
}
