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
