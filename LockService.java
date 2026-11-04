package com.mafaz.fastlauncher;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

import static com.mafaz.fastlauncher.LockStore.*;

// Listens for screen-off and shows the lock screen
public class LockService extends Service {
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
