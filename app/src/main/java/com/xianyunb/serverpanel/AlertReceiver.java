package com.xianyunb.serverpanel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** AlarmManager 到点后在这里拉一次告警。 */
public class AlertReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult result = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                AlertWatcher.check(app);
            } catch (Exception ignored) {
            } finally {
                result.finish();
            }
        }).start();
    }
}
