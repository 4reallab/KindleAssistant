package com.kindle.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "PhotoDrop";
    private static final int REQUEST_PERMISSIONS = 7;
    private static final String SHORTCUT_PERMISSION =
            "com.android.launcher.permission.INSTALL_SHORTCUT";
    private static final String[] STORAGE_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private TextView statusText;
    private TextView urlText;
    private ImageView qrImage;
    private Button toggleButton;
    private volatile boolean serviceRunning;
    private volatile String currentUrl = "";

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? null : intent.getAction();
            if (UploadService.ACTION_STATUS.equals(action)) {
                serviceRunning = intent.getBooleanExtra("running", false);
                currentUrl = intent.getStringExtra("url");
                updateUi();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusText = findViewById(R.id.statusText);
        urlText = findViewById(R.id.urlText);
        qrImage = findViewById(R.id.qrImage);
        toggleButton = findViewById(R.id.toggleButton);

        toggleButton.setOnClickListener(v -> {
            Intent service = new Intent(MainActivity.this, UploadService.class);
            if (serviceRunning) {
                stopService(service);
                serviceRunning = false;
                updateUi();
            } else {
                if (!hasStoragePermission()) {
                    requestNeededPermissions();
                    return;
                }
                service.setAction(UploadService.ACTION_START);
                startService(service);
            }
        });

        findViewById(R.id.photoButton).setOnClickListener(v ->
                startActivity(new Intent(this, GalleryActivity.class)));
        findViewById(R.id.todoButton).setOnClickListener(v ->
                startActivity(new Intent(this, TodoActivity.class)));
        findViewById(R.id.calendarButton).setOnClickListener(v ->
                startActivity(new Intent(this, CalendarActivity.class)));
        findViewById(R.id.aboutButton).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
        findViewById(R.id.websiteLink).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.about_website_url)))));

        requestNeededPermissions();
        updateUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(receiver, new IntentFilter(UploadService.ACTION_STATUS));
        ensureService();
        createLauncherShortcut();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(receiver);
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PERMISSIONS) {
            return;
        }
        if (!hasStoragePermission()) {
            Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show();
            return;
        }
        ensureService();
        if (hasShortcutPermission()) {
            getSharedPreferences("launcher", MODE_PRIVATE).edit()
                    .putBoolean("shortcut_done", false).apply();
            createLauncherShortcut();
        }
    }

    private void requestNeededPermissions() {
        List<String> needed = new ArrayList<>();
        for (String permission : STORAGE_PERMISSIONS) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                needed.add(permission);
            }
        }
        if (checkSelfPermission(SHORTCUT_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(SHORTCUT_PERMISSION);
        }
        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), REQUEST_PERMISSIONS);
        } else {
            ensureService();
        }
    }

    private boolean hasStoragePermission() {
        for (String permission : STORAGE_PERMISSIONS) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean hasShortcutPermission() {
        return checkSelfPermission(SHORTCUT_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    private void createLauncherShortcut() {
        SharedPreferences prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        if (prefs.getBoolean("shortcut_done", false)) {
            return;
        }
        Intent open = new Intent(Intent.ACTION_MAIN);
        open.setClassName(getPackageName(), MainActivity.class.getName());
        open.addCategory(Intent.CATEGORY_LAUNCHER);

        Bitmap icon = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Drawable drawable = getResources().getDrawable(R.drawable.ic_launcher);
        drawable.setBounds(0, 0, 96, 96);
        drawable.draw(new Canvas(icon));

        ShortcutManager manager = getSystemService(ShortcutManager.class);
        if (manager != null && manager.isRequestPinShortcutSupported()) {
            ShortcutInfo info = new ShortcutInfo.Builder(this, "kindle_assistant")
                    .setShortLabel(getString(R.string.app_name))
                    .setLongLabel(getString(R.string.app_name))
                    .setIcon(Icon.createWithBitmap(icon))
                    .setIntent(open)
                    .build();
            manager.requestPinShortcut(info, null);
            Log.i(TAG, "request pin shortcut");
            prefs.edit().putBoolean("shortcut_done", true).apply();
            return;
        }

        Intent install = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        List<ResolveInfo> receivers = getPackageManager().queryBroadcastReceivers(install, 0);
        if (!receivers.isEmpty()) {
            ResolveInfo receiver = receivers.get(0);
            install.setComponent(new ComponentName(
                    receiver.activityInfo.packageName, receiver.activityInfo.name));
        } else {
            ResolveInfo launcher = getPackageManager().resolveActivity(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_DEFAULT_ONLY);
            if (launcher != null && launcher.activityInfo != null) {
                install.setComponent(new ComponentName(launcher.activityInfo.packageName,
                        "com.android.launcher3.InstallShortcutReceiver"));
            }
        }
        install.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.app_name));
        install.putExtra(Intent.EXTRA_SHORTCUT_INTENT, open);
        install.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
        install.putExtra("duplicate", false);
        Log.i(TAG, "request launcher shortcut: " + install.getComponent());
        sendBroadcast(install);
        prefs.edit().putBoolean("shortcut_done", true).apply();
    }

    private void ensureService() {
        if (!hasStoragePermission()) {
            return;
        }
        if (isServiceRunning()) {
            return;
        }
        Intent intent = new Intent(this, UploadService.class);
        intent.setAction(UploadService.ACTION_START);
        startService(intent);
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return serviceRunning;
        }
        for (ActivityManager.RunningServiceInfo info : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (UploadService.class.getName().equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void updateUi() {
        statusText.setText(serviceRunning ? R.string.service_on : R.string.service_off);
        toggleButton.setText(serviceRunning ? R.string.stop_service : R.string.start_service);
        if (serviceRunning && currentUrl != null && !currentUrl.isEmpty()) {
            urlText.setText(currentUrl);
            qrImage.setImageBitmap(QrCode.encode(currentUrl));
        } else if (serviceRunning) {
            urlText.setText("正在连接 Wi-Fi…");
            qrImage.setImageBitmap(null);
        } else {
            urlText.setText("");
            qrImage.setImageBitmap(null);
        }
    }
}
