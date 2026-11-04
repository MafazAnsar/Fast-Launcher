package com.mafaz.fastlauncher;

import android.app.Activity;
import android.app.KeyguardManager;
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

    // After a correct PIN, the system lock screen may show without being covered again
    static volatile long graceUntil = 0;

    interface PinCb { void ok(String pin); }

    static boolean hasPin(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("pin", null) != null;
    }

    static String hashPin(String salt, String pin) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((salt + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static void savePin(Context c, String pin) {
        byte[] r = new byte[8];
        new java.security.SecureRandom().nextBytes(r);
        StringBuilder sb = new StringBuilder();
        for (byte x : r) sb.append(String.format("%02x", x));
        String salt = sb.toString();
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("salt", salt)
            .putString("pin", hashPin(salt, pin))
            .putInt("fails", 0)
            .putLong("lockUntil", 0)
            .apply();
    }

    static boolean checkPin(Context c, String pin) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String h = sp.getString("pin", null);
        if (h == null) return true;
        return h.equals(hashPin(sp.getString("salt", ""), pin));
    }

    static void clearPin(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("pin").remove("salt").apply();
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

        // Long-press the clock for lock screen settings
        clock.setOnLongClickListener(v -> {
            showLockMenu();
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

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    private void setLock(boolean on) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_LOCK, on).apply();
        applyLockSetting();
        toast(on ? "Lock screen on" : "Lock screen off");
    }

    private void askPin(String title, PinCb cb) {
        final EditText et = new EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setFilters(new android.text.InputFilter[]{
            new android.text.InputFilter.LengthFilter(8) });
        new android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(et)
            .setPositiveButton("OK", (d, w) -> cb.ok(et.getText().toString()))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void askNewPin() {
        askPin("New PIN (4-8 digits)", p -> {
            if (p.length() < 4) { toast("PIN too short"); return; }
            savePin(this, p);
            toast("PIN saved");
        });
    }

    private void showLockMenu() {
        final boolean on = lockEnabled(this);
        final boolean pin = hasPin(this);
        final List<String> items = new ArrayList<>();
        items.add(on ? "Turn lock screen OFF" : "Turn lock screen ON");
        items.add(pin ? "Change PIN" : "Set PIN");
        if (pin) items.add("Remove PIN");
        new android.app.AlertDialog.Builder(this)
            .setTitle("Lock screen")
            .setItems(items.toArray(new String[0]), (d, which) -> {
                if (which == 0) {
                    if (on && pin) {
                        askPin("Current PIN", cur -> {
                            if (checkPin(this, cur)) setLock(false); else toast("Wrong PIN");
                        });
                    } else {
                        setLock(!on);
                    }
                } else if (which == 1) {
                    if (pin) {
                        askPin("Current PIN", cur -> {
                            if (checkPin(this, cur)) askNewPin(); else toast("Wrong PIN");
                        });
                    } else {
                        askNewPin();
                    }
                } else {
                    askPin("Current PIN", cur -> {
                        if (checkPin(this, cur)) { clearPin(this); toast("PIN removed"); }
                        else toast("Wrong PIN");
                    });
                }
            })
            .show();
    }

    // Pressing Home while locked must not reveal the system lock screen
    @Override
    protected void onResume() {
        super.onResume();
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (lockEnabled(this) && km.isKeyguardLocked()
                && System.currentTimeMillis() > graceUntil) {
            Intent l = new Intent(this, LockActivity.class);
            l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(l);
        }
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
                graceUntil = 0;
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

    // The lock screen: clock, date, battery; swipe up (PIN if set) to reach the system lock
    public static class LockActivity extends Activity {
        private float downY;
        private float dp;
        private FrameLayout frame;
        private View clockView;
        private TextView msg, dots;
        private final StringBuilder entry = new StringBuilder();

        // Fires when the device is unlocked (fingerprint, PIN or swipe)
        private final BroadcastReceiver present = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                graceUntil = 0;
                finish();
            }
        };

        @Override
        protected void onCreate(Bundle b) {
            super.onCreate(b);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            registerReceiver(present, new IntentFilter(Intent.ACTION_USER_PRESENT));
            dp = getResources().getDisplayMetrics().density;

            frame = new FrameLayout(this);
            frame.setBackgroundColor(Color.BLACK);
            clockView = buildClock();
            setContentView(frame);
            showClock();
        }

        private View buildClock() {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);

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
                        if (downY - e.getY() > 120 * dp) swipedUp();
                        return true;
                }
                return true;
            });
            return root;
        }

        private void showClock() {
            entry.setLength(0);
            frame.removeAllViews();
            frame.addView(clockView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        private void swipedUp() {
            if (hasPin(this)) showPad(); else unlock();
        }

        private void showPad() {
            entry.setLength(0);
            LinearLayout pad = new LinearLayout(this);
            pad.setOrientation(LinearLayout.VERTICAL);
            pad.setGravity(Gravity.CENTER);

            msg = new TextView(this);
            msg.setText("Enter PIN");
            msg.setTextColor(Color.WHITE);
            msg.setTextSize(18);
            msg.setGravity(Gravity.CENTER);
            pad.addView(msg);

            dots = new TextView(this);
            dots.setText(" ");
            dots.setTextColor(Color.WHITE);
            dots.setTextSize(28);
            dots.setGravity(Gravity.CENTER);
            dots.setPadding(0, (int) (8 * dp), 0, (int) (16 * dp));
            pad.addView(dots);

            String[][] rows = {
                {"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"DEL", "0", "OK"}
            };
            int m = (int) (6 * dp);
            for (String[] r : rows) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER);
                for (String k : r) {
                    TextView key = new TextView(this);
                    key.setText(k);
                    key.setTextColor(Color.WHITE);
                    key.setTextSize(k.length() > 1 ? 16 : 24);
                    key.setGravity(Gravity.CENTER);
                    key.setBackgroundColor(0x22FFFFFF);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        (int) (84 * dp), (int) (64 * dp));
                    lp.setMargins(m, m, m, m);
                    key.setOnClickListener(v -> onKey(k));
                    row.addView(key, lp);
                }
                pad.addView(row);
            }

            TextView cancel = new TextView(this);
            cancel.setText("Cancel");
            cancel.setTextColor(0xFFAA
