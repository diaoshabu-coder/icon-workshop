package com.niji.entry;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

/** Tiny launcher entry: opens the app named in the niji_target_pkg meta-data. */
public class LaunchActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String target = null;
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            if (ai.metaData != null) target = ai.metaData.getString("niji_target_pkg");
            if (target != null) {
                Intent i = getPackageManager().getLaunchIntentForPackage(target);
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(i);
                    overridePendingTransition(0, 0);
                } else {
                    Toast.makeText(this, "目标应用未安装", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
