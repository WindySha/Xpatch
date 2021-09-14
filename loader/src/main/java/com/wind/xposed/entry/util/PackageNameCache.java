package com.wind.xposed.entry.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Wind
 */
public class PackageNameCache {

    private static final String TAG = PackageNameCache.class.getSimpleName();

    private Context mContext;
    private Map<String, String> mPackageNameMap = new HashMap<>();

    private static PackageNameCache instance;

    private PackageNameCache(Context context) {
        this.mContext = context;
    }

    public static PackageNameCache getInstance(Context context) {
        if (instance == null) {
            synchronized (PackageNameCache.class) {
                if (instance == null) {
                    instance = new PackageNameCache(context);
                }
            }
        }
        return instance;
    }

    public String getPackageNameByPath(String apkPath) {
        if (apkPath == null || apkPath.length() == 0) {
            return "";
        }
        String packageName = mPackageNameMap.get(apkPath);
        if (packageName != null && packageName.length() > 0) {
            return packageName;
        }
        packageName = "";
        PackageManager pm = mContext.getPackageManager();
        long startTime = System.currentTimeMillis();
        PackageInfo info = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES);
        XLog.d(TAG, "Get package name time ->  " + (System.currentTimeMillis() - startTime)
                + " apkPath -> " + apkPath);
        if (info != null) {
            packageName = info.packageName;
            mPackageNameMap.put(apkPath, packageName);
        }
        return packageName;
    }
}
