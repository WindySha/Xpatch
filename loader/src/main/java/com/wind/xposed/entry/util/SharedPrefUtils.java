package com.wind.xposed.entry.util;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPrefUtils {

    private static Context appContext;

    private static final String SHARED_PREFERENE_FILE_PATH = "xpatch_wl_shared_pref";

    public static void init(Context context) {
        appContext = context;
    }

    public static long getLong() {
        if (appContext == null) {
            return 0L;
        }
        SharedPreferences sharedPreferences = appContext.getSharedPreferences(SHARED_PREFERENE_FILE_PATH, Context.MODE_PRIVATE);
        long result = sharedPreferences.getLong("time", 0L);
        return result;
    }

    public static void putLong(long data) {
        if (appContext == null) {
            return;
        }
        SharedPreferences sharedPreferences = appContext.getSharedPreferences(SHARED_PREFERENE_FILE_PATH, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("time", data);
        editor.apply();
    }
}
