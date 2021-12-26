package com.wind.xposed.entry.util;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.util.Set;

public class PluginNativeLibExtractor {

    private static final String TAG = "NativeLibExtractor";
    private static final String SHARE_PREF_FILE_NAME = "xpatch_module_native_lib_config";

    private static SharedPreferences sharedPreferences;

    public static void copySoFileIfNeeded(Context context, String libPath, String pluginApkPath) {
        boolean isMainProcess = XpatchUtils.isMainProcess(context);
        if (!isMainProcess) {
            return;
        }

        Set<String> abiSet = NativeLibraryHelperCompat.getSupportAbiList(pluginApkPath);
        if (abiSet.isEmpty()) {
            Log.i(TAG, " plugin: " + pluginApkPath + " do not contains any so files.");
            return;
        }

        XpatchUtils.ensurePathExist(libPath);

        Log.i(TAG, " copySoFileIfNeeded procecess = " + XpatchUtils.getCurProcessName(context) + " isMainProcess = " + XpatchUtils.isMainProcess(context));
        Log.i(TAG, " copyPluginSoFile libPath = " + libPath + " pluginApkPath = " + pluginApkPath);
        if (sharedPreferences == null) {
            sharedPreferences = context.getSharedPreferences(SHARE_PREF_FILE_NAME, MODE_PRIVATE);
        }
        String savedMd5 = getSavedApkFileMd5(sharedPreferences, pluginApkPath);
        String curMd5 = XpatchUtils.getFileMD5(new File(pluginApkPath));
        Log.i(TAG, " copyPluginSoFile savedMd5 = " + savedMd5 + " curMd5 = " + curMd5);
        if (savedMd5 == null || savedMd5.isEmpty() || !savedMd5.equals(curMd5)) {
            NativeLibraryHelperCompat.copyNativeBinaries(new File(pluginApkPath), new File(libPath));
            saveApkFileMd5(sharedPreferences, pluginApkPath, curMd5);
        } else {
            Log.d(TAG, "plugin is not changed, no need to copy so file again!");
        }
    }

    private static String getSavedApkFileMd5(SharedPreferences sp, String key) {
        return sp.getString(key, "");
    }

    private static void saveApkFileMd5(SharedPreferences sp, String key, String md5) {
        sp.edit().putString(key, md5).apply();
    }
}
