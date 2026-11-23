package com.niji.iconchanger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final int REQ_PICK = 41;

    private static final Map<String, String> PRESETS = new HashMap<>();
    static {
        PRESETS.put("com.openai.chatgpt", "chatgpt.png");
        PRESETS.put("com.anthropic.claude", "claude.png");
        PRESETS.put("com.deepseek.chat", "deepseek.png");
        PRESETS.put("ai.x.grok", "grok.png");
        PRESETS.put("com.google.android.apps.bard", "gemini.png");
    }

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<AppEntry> apps = new ArrayList<>();

    private ListView listView;
    private TextView status;
    private Button shizukuBtn;
    private AppAdapter adapter;
    private SharedPreferences prefs;
    private ProgressDialog progress;
    private String pendingTarget;

    private final Shizuku.OnRequestPermissionResultListener permListener =
            (requestCode, grantResult) -> ui.post(this::updateShizukuUi);

    static class AppEntry {
        String pkg;
        String label;
        Drawable icon;
        boolean hasEntry;
        boolean selected;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("entries", MODE_PRIVATE);

        listView = findViewById(R.id.appList);
        status = findViewById(R.id.status);
        shizukuBtn = findViewById(R.id.shizukuBtn);
        Button refreshBtn = findViewById(R.id.refreshBtn);

        adapter = new AppAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            AppEntry entry = apps.get(position);
            entry.selected = true;
            showActions(entry);
        });

        shizukuBtn.setOnClickListener(v -> requestShizuku());
        refreshBtn.setOnClickListener(v -> loadApps());

        Shizuku.addRequestPermissionResultListener(permListener);
        try {
            Shizuku.addBinderReceivedListenerSticky(() -> ui.post(this::updateShizukuUi));
        } catch (Throwable ignored) { }

        updateShizukuUi();
        loadApps();
    }

    @Override
    protected void onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(permListener); } catch (Throwable ignored) { }
        pool.shutdownNow();
        super.onDestroy();
    }

    private void updateShizukuUi() {
        boolean ready = ShizukuShell.available();
        boolean granted = ready && ShizukuShell.granted();
        if (!ready) {
            status.setText("Shizuku：未运行（请先打开 Shizuku 应用并启动服务）");
            shizukuBtn.setVisibility(View.VISIBLE);
            shizukuBtn.setText("重新检测");
        } else if (!granted) {
            status.setText("Shizuku：已连接，等待授权");
            shizukuBtn.setVisibility(View.VISIBLE);
            shizukuBtn.setText("授权 Shizuku");
        } else {
            status.setText("Shizuku：已授权（ADB 身份），可以静默安装图标");
            shizukuBtn.setVisibility(View.GONE);
        }
    }

    private void requestShizuku() {
        if (!ShizukuShell.available()) {
            updateShizukuUi();
            Toast.makeText(this, "请先启动 Shizuku 服务", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ShizukuShell.granted()) {
            updateShizukuUi();
            return;
        }
        try {
            ShizukuShell.requestPermission();
            status.setText("已发送授权请求，请在 Shizuku 弹窗中允许");
        } catch (Throwable t) {
            Toast.makeText(this, "请求失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadApps() {
        pool.execute(() -> {
            PackageManager pm = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN, null);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolves = pm.queryIntentActivities(main, 0);
            Set<String> seen = new HashSet<>();
            List<AppEntry> list = new ArrayList<>();
            for (ResolveInfo ri : resolves) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || pkg.startsWith("com.niji.entry.")) continue;
                if (!seen.add(pkg)) continue;
                AppEntry e = new AppEntry();
                e.pkg = pkg;
                e.label = String.valueOf(ri.loadLabel(pm));
                e.icon = ri.loadIcon(pm);
                e.hasEntry = prefs.contains("e_" + pkg);
                list.add(e);
            }
            Collections.sort(list, Comparator.comparing(a -> a.label, String.CASE_INSENSITIVE_ORDER));
            ui.post(() -> {
                apps.clear();
                apps.addAll(list);
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void showActions(AppEntry entry) {
        List<String> items = new ArrayList<>();
        items.add("选择图片，创建图标");
        if (PRESETS.containsKey(entry.pkg)) items.add("使用内置头像");
        if (entry.hasEntry) {
            items.add("打开自定义图标");
            items.add("删除自定义图标");
        }
        items.add("取消");

        new AlertDialog.Builder(this)
                .setTitle(entry.label)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    String choice = items.get(which);
                    if (choice.equals("选择图片，创建图标")) {
                        pickImage(entry);
                    } else if (choice.equals("使用内置头像")) {
                        applyPreset(entry);
                    } else if (choice.equals("打开自定义图标")) {
                        openEntry(entry);
                    } else if (choice.equals("删除自定义图标")) {
                        deleteEntry(entry);
                    }
                })
                .show();
    }

    private void pickImage(AppEntry entry) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, REQ_PICK);
            pendingTarget = entry.pkg;
            pendingLabel = entry.label;
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的图片选择器", Toast.LENGTH_LONG).show();
        }
    }

    private String pendingLabel;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            final String target = pendingTarget;
            final String label = pendingLabel;
            if (target == null) return;
            showProgress("正在生成图标…");
            pool.execute(() -> {
                try {
                    Bitmap bmp = IconGen.loadFromUri(MainActivity.this, uri);
                    byte[] apk = EntryBuilder.build(MainActivity.this, target, label, bmp);
                    bmp.recycle();
                    installApk(target, apk);
                    ui.post(() -> {
                        hideProgress();
                        markEntry(target);
                        toast("已创建图标：" + label);
                    });
                } catch (Exception e) {
                    ui.post(() -> {
                        hideProgress();
                        showError("创建失败", e);
                    });
                }
            });
        }
    }

    private void applyPreset(AppEntry entry) {
        String asset = PRESETS.get(entry.pkg);
        if (asset == null) return;
        showProgress("正在使用内置头像…");
        pool.execute(() -> {
            try {
                Bitmap bmp;
                try (InputStream is = getAssets().open("presets/" + asset)) {
                    bmp = BitmapFactory.decodeStream(is);
                }
                if (bmp == null) throw new Exception("内置头像读取失败");
                Bitmap master = IconGen.centerCrop(bmp, 512);
                if (master != bmp) bmp.recycle();
                byte[] apk = EntryBuilder.build(this, entry.pkg, entry.label, master);
                master.recycle();
                installApk(entry.pkg, apk);
                ui.post(() -> {
                    hideProgress();
                    markEntry(entry.pkg);
                    toast("已创建图标：" + entry.label);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    hideProgress();
                    showError("创建失败", e);
                });
            }
        });
    }

    private void installApk(String target, byte[] apk) throws Exception {
        if (!ShizukuShell.available() || !ShizukuShell.granted()) {
            throw new Exception("Shizuku 未授权，无法静默安装");
        }
        ShizukuShell.Result r = ShizukuShell.install(apk);
        if (!r.ok() || !r.output.contains("Success")) {
            throw new Exception("安装失败：" + r.output.trim());
        }
    }

    private void markEntry(String targetPkg) {
        String entryPkg = EntryBuilder.entryPackageFor(targetPkg);
        prefs.edit().putString("e_" + targetPkg, entryPkg).apply();
        for (AppEntry a : apps) if (a.pkg.equals(targetPkg)) a.hasEntry = true;
        adapter.notifyDataSetChanged();
    }

    private void openEntry(AppEntry entry) {
        String entryPkg = prefs.getString("e_" + entry.pkg, null);
        if (entryPkg == null) return;
        Intent i = getPackageManager().getLaunchIntentForPackage(entryPkg);
        if (i == null) {
            toast("图标入口不存在，请重新创建");
            return;
        }
        startActivity(i);
    }

    private void deleteEntry(AppEntry entry) {
        final String entryPkg = prefs.getString("e_" + entry.pkg, null);
        if (entryPkg == null) return;
        showProgress("正在删除…");
        pool.execute(() -> {
            try {
                ShizukuShell.Result r = ShizukuShell.uninstall(entryPkg);
                ui.post(() -> {
                    hideProgress();
                    if (r.ok() || r.output.contains("Success")) {
                        prefs.edit().remove("e_" + entry.pkg).apply();
                        entry.hasEntry = false;
                        adapter.notifyDataSetChanged();
                        toast("已删除自定义图标");
                    } else {
                        toast("删除失败：" + r.output.trim());
                    }
                });
            } catch (Exception e) {
                ui.post(() -> {
                    hideProgress();
                    showError("删除失败", e);
                });
            }
        });
    }

    private void showProgress(String message) {
        hideProgress();
        progress = new ProgressDialog(this);
        progress.setMessage(message);
        progress.setCancelable(false);
        progress.show();
    }

    private void hideProgress() {
        if (progress != null && progress.isShowing()) {
            try { progress.dismiss(); } catch (Exception ignored) { }
        }
        progress = null;
    }

    private void showError(String title, Exception e) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(String.valueOf(e.getMessage()))
                .setPositiveButton("好", null)
                .show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int position) { return apps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int pad = (int) (10 * getResources().getDisplayMetrics().density);
                row.setPadding(pad, pad, pad, pad);

                ImageView iv = new ImageView(MainActivity.this);
                int size = (int) (48 * getResources().getDisplayMetrics().density);
                row.addView(iv, new LinearLayout.LayoutParams(size, size));

                LinearLayout texts = new LinearLayout(MainActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(pad, 0, 0, 0);
                TextView name = new TextView(MainActivity.this);
                name.setTextSize(16);
                name.setTextColor(0xFF222222);
                TextView sub = new TextView(MainActivity.this);
                sub.setTextSize(11);
                sub.setTextColor(0xFF888888);
                texts.addView(name);
                texts.addView(sub);
                row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView badge = new TextView(MainActivity.this);
                badge.setTextColor(0xFF2E7D32);
                badge.setTextSize(12);
                row.addView(badge);

                row.setTag(new Object[]{iv, name, sub, badge});
            } else {
                row = (LinearLayout) convertView;
            }
            Object[] tags = (Object[]) row.getTag();
            AppEntry e = apps.get(position);
            ((ImageView) tags[0]).setImageDrawable(e.icon);
            ((TextView) tags[1]).setText(e.label);
            ((TextView) tags[2]).setText(e.pkg);
            ((TextView) tags[3]).setText(e.hasEntry ? "✔ 已建图标" : "");
            return row;
        }
    }
}
