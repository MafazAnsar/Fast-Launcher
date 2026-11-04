package com.mafaz.fastlauncher;

import android.app.Activity;
import android.app.Service;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.Collator;
import java.util.*;

public class Launcher extends Activity {

    static class App {
        String label; String pkg; Drawable icon; Intent intent;
    }

    private final List<App> apps = new ArrayList<>();
    private GridView grid;
    private BaseAdapter adapter;
    private float dp;

    static final String PREFS = "fl";
    static final String KEY_LOCK = "lock";

    static boolean lockEnabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LOCK, true);
    }

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(px(24), px(32), px(24), 0);

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(56);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-thin", 0));
        root.addView(clock);

        // Long-press the clock to turn the lock screen on/off
        clock.setOnLongClickListener(v -> {
            boolean on = !lockEnabled(this);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_LOCK, on).apply();
            applyLockSetting();
            Toast.makeText(this, on ? "Lock screen on" : "Lock screen off",
                Toast.LENGTH_SHORT).show();
            return true;
        });

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextColor(0xFFAAAAAA);
        date.setTextSize(16);
        date.setPadding(0, 0, 0, px(24));
        root.addView(date);

        grid = new GridView(this);
        grid.setColumnWidth(px(110));
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(px(12));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0x22FFFFFF));
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setVerticalScrollBarEnabled(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, 0, 0, px(24));

        adapter = new BaseAdapter() {
            public int getCount() { return apps.size(); }
            public Object getItem(int p) { return apps.get(p); }
            public long getItemId(int p) { return p; }

            public View getView(int p, View v, ViewGroup parent) {
                Holder h;
                if (v == null) {
                    LinearLayout cell = new LinearLayout(Launcher.this);
                    cell.setOrientation(LinearLayout.VERTICAL);
                    cell.setGravity(Gravity.CENTER_HORIZONTAL);
                    cell.setPadding(px(4), px(8), px(4), px(8));
                    h = new Holder();
                    h.icon = new ImageView(Launcher.this);
                    h.icon.setLayoutParams(new LinearLayout.LayoutParams(px(52), px(52)));
                    h.label = new TextView(Launcher.this);
                    h.label.setTextColor(Color.WHITE);
                    h.label.setTextSize(12);
                    h.label.setGravity(Gravity.CENTER);
                    h.label.setMaxLines(1);
                    h.label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    h.label.setPadding(0, px(6), 0, 0);
                    cell.addView(h.icon);
                    cell.addView(h.label);
                    cell.setTag(h);
                    v = cell;
                } else {
                    h = (Holder) v.getTag();
                }
                App a = apps.get(p);
                h.icon.setImageDrawable(a.icon);
                h.label.setText(a.label);
                return v;
            }
        };
        grid.setAdapter(adapter);

        grid.setOnItemClickListener((parent, view, pos, id) -> {
            try { startActivity(apps.get(pos).intent); }
            catch (Exception e) { /* app may have been removed */ }
        });

        grid.setOnItemLongClickListener((parent, view, pos, id) -> {
            final App a = apps.get(pos);
            new android.app.AlertDialog.Builder(Launcher.this)
                .setTitle(a.label)
                .setItems(new String[]{"App info", "Uninstall"}, (d, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + a.pkg)));
                    } else {
                        startActivity(new Intent(Intent.ACTION_DELETE,
                            Uri.parse("package:" + a.pkg)));
                    }
                }).show();
            return true;
        });

        root.addView(grid, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);

        loadApps();
        applyLockSetting();
    }

    private void applyLockSetting() {
        Intent s = new Intent(this, LockService.class);
        if (lockEnabled(this)) startService(s); else stopService(s);
    }

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
            final List<App> out = new ArrayList<>(list.size());
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                App a = new App();
                a.pkg = pkg;
                a.label = ri.loadLabel(pm).toString();
                a.icon = ri.loadIcon(pm);
                a.intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(pkg, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                out.add(a);
            }
            final Collator c = Collator.getInstance();
            Collections.sort(out, (x, y) -> c.compare(x.label, y.label));
            runOnUiThread(() -> {
                apps.clear();
                apps.addAll(out);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (grid != null) grid.smoothScrollToPosition(0);
    }

    @Override public void onBackPressed() { }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(pkgReceiver);
    }

    private int px(int d) { return (int) (d * dp + 0.5f); }

    static class Holder { ImageView icon; TextView label; }

    // Listens for screen-off and shows the lock screen
    public static class LockService extends Service {
        private final BroadcastReceiver off = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (!lockEnabled(c)) return;
                Intent l = new Intent(c, LockActivity.class);
                l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                c.startActivity(l);
            }
        };

        @Override public void onCreate() {
            super.onCreate();
            registerReceiver(off, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        }

        @Override public int onStartCommand(Intent i, int flags, int id) {
            return START_STICKY;
        }

        @Override public IBinder onBind(Intent i) { return null; }

        @Override public void onDestroy() {
            unregisterReceiver(off);
            super.onDestroy();
        }
    }

    // The lock screen: clock, date, battery, swipe up to unlock
    public static class LockActivity extends Activity {
        private float downY;

        @Override
        protected void onCreate(Bundle b) {
            super.onCreate(b);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            final float dp = getResources().getDisplayMetrics().density;

            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setBackgroundColor(Color.BLACK);

            TextClock clock = new TextClock(this);
            clock.setFormat24Hour("HH:mm");
            clock.setFormat12Hour("h:mm");
            clock.setTextColor(Color.WHITE);
            clock.setTextSize(88);
            clock.setTypeface(android.graphics.Typeface.create("sans-serif-thin", 0));
            clock.setGravity(Gravity.CENTER);
            root.addView(clock);

            TextClock date = new TextClock(this);
            date.setFormat24Hour("EEEE, d MMMM");
            date.setFormat12Hour("EEEE, d MMMM");
            date.setTextColor(0xFFAAAAAA);
            date.setTextSize(20);
            date.setGravity(Gravity.CENTER);
            root.addView(date);

            TextView batt = new TextView(this);
            batt.setTextColor(0xFF777777);
            batt.setTextSize(14);
            batt.setGravity(Gravity.CENTER);
            batt.setPadding(0, (int) (16 * dp), 0, 0);
            Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bi != null) {
                int level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int status = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
                batt.setText((level * 100 / scale) + "%" + (charging ? " - charging" : ""));
            }
            root.addView(batt);

            TextView hint = new TextView(this);
            hint.setText("Swipe up to unlock");
            hint.setTextColor(0xFF666666);
            hint.setTextSize(14);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, (int) (96 * dp), 0, 0);
            root.addView(hint);

            root.setOnTouchListener((v, e) -> {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = e.getY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (downY - e.getY() > 120 * dp) unlock();
                        return true;
                }
                return true;
            });

            setContentView(root);
        }

        private void unlock() {
            // Removes a plain swipe lock; with a PIN/pattern the system lock appears next
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            getWindow().getDecorView().postDelayed(() -> finish(), 120);
        }

        @Override public void onBackPressed() { }
    }
}
