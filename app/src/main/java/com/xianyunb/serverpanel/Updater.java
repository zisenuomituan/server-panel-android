package com.xianyunb.serverpanel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 自用版的应用内更新：读取更新清单，下载 APK 并交给系统安装器。
 * 清单格式：{"version":"0.3.0","versionCode":3,"notes":"...","url":"https://.../app.apk"}
 */
public class Updater {

    public interface Progress {
        void onProgress(int percent);
    }

    public static void check(Activity activity, boolean silent) {
        new Thread(() -> {
            JSONObject manifest;
            try {
                manifest = fetchManifest(BuildConfig.UPDATE_MANIFEST_URL);
            } catch (Exception e) {
                if (!silent) {
                    activity.runOnUiThread(() -> toast(activity, "检查更新失败：" + e.getMessage()));
                }
                return;
            }
            final JSONObject m = manifest;
            activity.runOnUiThread(() -> handle(activity, m, silent));
        }).start();
    }

    private static void handle(Activity activity, JSONObject m, boolean silent) {
        int remoteCode = m.optInt("versionCode", 0);
        String remoteName = m.optString("version", "");
        String url = m.optString("url", "");
        String notes = m.optString("notes", "");

        if (remoteCode <= BuildConfig.VERSION_CODE) {
            if (!silent) toast(activity, "已是最新版本 " + BuildConfig.VERSION_NAME);
            return;
        }
        if (url.isEmpty()) {
            if (!silent) toast(activity, "新版本信息不完整");
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("当前 ").append(BuildConfig.VERSION_NAME)
                .append(" → 新版本 ").append(remoteName);
        if (!notes.isEmpty()) msg.append("\n\n").append(notes);

        new AlertDialog.Builder(activity)
                .setTitle("发现新版本")
                .setMessage(msg.toString())
                .setNegativeButton("以后再说", null)
                .setPositiveButton("下载更新", (d, w) -> download(activity, url, remoteName))
                .show();
    }

    private static void download(Activity activity, String url, String version) {
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("正在下载更新")
                .setMessage("0%")
                .setCancelable(false)
                .create();
        dialog.show();

        new Thread(() -> {
            try {
                File dir = activity.getExternalFilesDir(null);
                if (dir == null) dir = activity.getCacheDir();
                File apk = new File(dir, "update-" + version + ".apk");
                fetchApk(url, apk, percent ->
                        activity.runOnUiThread(() -> dialog.setMessage(percent + "%")));

                activity.runOnUiThread(() -> {
                    dialog.dismiss();
                    if (!canInstall(activity)) {
                        new AlertDialog.Builder(activity)
                                .setMessage("需要先允许本应用安装未知来源的应用")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("去设置", (d, w) -> openInstallSettings(activity))
                                .show();
                        return;
                    }
                    install(activity, apk);
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    dialog.dismiss();
                    toast(activity, "下载失败：" + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * 清理下载残留：已装上（版本不高于当前）的安装包直接删掉；
     * 比当前版本新的保留，可能还在等待用户安装。
     */
    public static void cleanup(Context context) {
        cleanupDir(context.getExternalFilesDir(null));
        cleanupDir(context.getCacheDir());
    }

    private static void cleanupDir(File dir) {
        if (dir == null) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            if ("update.apk".equals(name)) {
                f.delete();
                continue;
            }
            if (!name.startsWith("update-") || !name.endsWith(".apk")) continue;
            String version = name.substring("update-".length(), name.length() - ".apk".length());
            if (compareVersion(version, BuildConfig.VERSION_NAME) <= 0) {
                f.delete();
            }
        }
    }

    private static int compareVersion(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? parseIntSafe(pa[i]) : 0;
            int vb = i < pb.length ? parseIntSafe(pb[i]) : 0;
            if (va != vb) return va < vb ? -1 : 1;
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static JSONObject fetchManifest(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        if (code >= 400) {
            c.disconnect();
            throw new Exception("请求失败 " + code);
        }
        String text = readAll(c.getInputStream());
        c.disconnect();
        return new JSONObject(text);
    }

    private static void fetchApk(String url, File dest, Progress progress) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        int code = c.getResponseCode();
        if (code >= 400) {
            c.disconnect();
            throw new Exception("请求失败 " + code);
        }
        int total = c.getContentLength();
        InputStream in = c.getInputStream();
        FileOutputStream out = new FileOutputStream(dest);
        byte[] buf = new byte[8192];
        long got = 0;
        int last = -1;
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            got += n;
            if (total > 0 && progress != null) {
                int percent = (int) (got * 100 / total);
                if (percent != last) {
                    last = percent;
                    progress.onProgress(percent);
                }
            }
        }
        out.close();
        in.close();
        c.disconnect();
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) bo.write(buf, 0, n);
        in.close();
        return new String(bo.toByteArray(), "UTF-8");
    }

    private static boolean canInstall(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    private static void openInstallSettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }

    private static void install(Context context, File apk) {
        Uri uri = FileProvider.getUriForFile(
                context, context.getPackageName() + ".fileprovider", apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

    private static void toast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }
}
