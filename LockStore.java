package com.mafaz.fastlauncher;

import android.content.Context;
import android.content.SharedPreferences;

// Lock screen settings and PIN storage
final class LockStore {
    private LockStore() { }

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
}
