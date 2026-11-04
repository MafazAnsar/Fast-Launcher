package com.mafaz.fastlauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import static com.mafaz.fastlauncher.LockStore.*;

// The lock screen: clock, date, battery; swipe up (PIN if set) to reach the system lock
public class LockActivity extends Activity {
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
        cancel.setTextColor(0xFFAAAAAA);
        cancel.setTextSize(16);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, (int) (20 * dp), 0, (int) (20 * dp));
        cancel.setOnClickListener(v -> showClock());
        pad.addView(cancel);

        frame.removeAllViews();
        frame.addView(pad, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void onKey(String k) {
        if (k.equals("OK")) {
            submit();
            return;
        }
        if (k.equals("DEL")) {
            if (entry.length() > 0) entry.setLength(entry.length() - 1);
        } else if (entry.length() < 8) {
            entry.append(k);
        }
        refreshDots();
    }

    private void refreshDots() {
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < entry.length(); i++) d.append("* ");
        dots.setText(d.length() == 0 ? " " : d.toString());
    }

    private void submit() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long until = sp.getLong("lockUntil", 0);
        if (now < until) {
            msg.setText("Too many tries. Wait " + ((until - now) / 1000 + 1) + "s");
        } else if (checkPin(this, entry.toString())) {
            sp.edit().putInt("fails", 0).apply();
            unlock();
            return;
        } else {
            int f = sp.getInt("fails", 0) + 1;
            SharedPreferences.Editor ed = sp.edit();
            if (f >= 5) {
                ed.putInt("fails", 0).putLong("lockUntil", now + 30000);
                msg.setText("Too many tries. Wait 30s");
            } else {
                ed.putInt("fails", f);
                msg.setText("Wrong PIN");
            }
            ed.apply();
        }
        entry.setLength(0);
        refreshDots();
    }

    private void unlock() {
        // Lets the system lock screen show (a plain swipe lock is removed entirely)
        graceUntil = System.currentTimeMillis() + 30000;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().getDecorView().postDelayed(() -> finish(), 120);
    }

    // Hide the status bar so it cannot be pulled down over the lock screen
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        showClock();
    }

    @Override
    protected void onStop() {
        super.onStop();
        showClock();
    }

    @Override public void onBackPressed() { }

    @Override
    protected void onDestroy() {
        unregisterReceiver(present);
        super.onDestroy();
    }
}
