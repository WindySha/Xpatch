package com.wind.xposed.entry;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.wind.xposed.entry.util.FileUtils;
import com.wind.xposed.entry.util.PackageNameCache;
import com.wind.xposed.entry.util.SharedPrefUtils;
import com.wind.xposed.entry.util.XLog;
import com.wind.xposed.entry.util.XpatchUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XposedHelper;
import me.weishu.reflection.Reflection;

/**
 * Created by Wind
 */
public class XposedModuleEntry {

    private static final String TAG = XposedModuleEntry.class.getSimpleName();

    private static AtomicBoolean hasInited = new AtomicBoolean(false);

    private static final String DIR_BASE = Environment.getExternalStorageDirectory().getAbsolutePath();

    private static final String XPOSED_MODULE_FILE_PATH = "xposed_config/modules.list";

    private static Context appContext;

    private static final String XPOSED_MODULE_FILE_NAME_PREFIX = "libxpatch_xp_module_";

    public static void init() {
        if (!hasInited.compareAndSet(false, true)) {
            return;
        }

        Context context = XpatchUtils.createAppContext();
        Reflection.unseal(context);
        SandHookInitialization.init(context);
        init(context);
    }

    public static void init(Context context) {

        if (context == null) {
            android.util.Log.e(TAG, "try to init XposedModuleEntry, but create app context failed !!!!");
            return;
        }

        appContext = context;

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            if (!FileUtils.isFilePermissionGranted(context)) {
                android.util.Log.e(TAG, "File permission is not granted, can not control xposed module by file ->" +
                        XPOSED_MODULE_FILE_PATH);
            }
        }

        initSELinux(context);

        SharedPrefUtils.init(context);

        ClassLoader originClassLoader = context.getClassLoader();

        // 加载代码本身的hook功能，一般情况下用不到
        XposedModuleLoader.startInnerHook(context.getApplicationInfo(), originClassLoader);

        List<String> modulePathList = new ArrayList<>();

        List<String> installedModulePathList = loadAllInstalledModule(context);

        insertXposedModulesFromLibPath(context, modulePathList);

        // 过滤掉已经打包在apk中的module，避免同一个module被加载了两次
        if (installedModulePathList != null && installedModulePathList.size() > 0) {
            List<String> packedModulePakcageNameList = null;

            for (String apkPath : modulePathList) {
                if (packedModulePakcageNameList == null) {
                    packedModulePakcageNameList = new ArrayList<>();
                }
                String packageName = getPackageNameByPath(context, apkPath);
                XLog.d(TAG, "Current packed module path ----> " + apkPath + " packageName = " + packageName);
                packedModulePakcageNameList.add(packageName);
            }

            if (packedModulePakcageNameList == null || packedModulePakcageNameList.size() == 0) {
                modulePathList.addAll(installedModulePathList);
            } else {
                for (String apkPath : installedModulePathList) {
                    String packageName = getPackageNameByPath(context, apkPath);
                    XLog.d(TAG, "Current installed module path ----> " + apkPath + " packageName = " + packageName);
                    if (!packedModulePakcageNameList.contains(packageName)) {
                        modulePathList.add(apkPath);
                    }
                }
            }
        }

        for (String modulePath : modulePathList) {
            String dexPath = context.getDir("xposed_plugin_dex", Context.MODE_PRIVATE).getAbsolutePath();
            if (!TextUtils.isEmpty(modulePath)) {
                Log.d(TAG, "Current truely loaded module path ----> " + modulePath);
                XposedModuleLoader.loadModule(modulePath, dexPath, null, context.getApplicationInfo(), originClassLoader);
            }
        }
    }

    private static String getPackageNameByPath(Context context, String apkPath) {
        return PackageNameCache.getInstance(context).getPackageNameByPath(apkPath);
    }

        // insert xposed modules saved in the lib path
    private static void insertXposedModulesFromLibPath(Context context, List<String> modulePathList) {
        String libPath = context.getApplicationInfo().nativeLibraryDir;
        XLog.d(TAG, "Current loaded module libPath ----> " + libPath);

        File libFileParent = new File(libPath);
        if (!libFileParent.exists()) {
            return;
        }

        File[] childFileList = libFileParent.listFiles();
        if (childFileList != null && childFileList.length > 0) {
            for (File libFile : childFileList) {
                String fileName = libFile.getName();
                if (fileName.startsWith(XPOSED_MODULE_FILE_NAME_PREFIX)) {
                    XLog.d(TAG, "add xposed modules from libPath, this lib path is --> " + libFile);
                    modulePathList.add(libFile.getAbsolutePath());
                }
            }
        }
    }

    private static void initSELinux(Context context) {
        XposedHelper.initSeLinux(context.getApplicationInfo().processName);
    }

    private static List<String> loadAllInstalledModule(Context context) {
        PackageManager pm = context.getPackageManager();
        List<String> modulePathList = new ArrayList<>();
//        modulePathList.add("mnt/sdcard/app-debug.apk");

        List<String> packageNameList = loadPackageNameListFromFile(true);
        List<Pair<String, String>> installedModuleList = new ArrayList<>();

        boolean configFileExist = configFileExist();

        List<PackageInfo> packageInfoList = pm.getInstalledPackages(PackageManager.GET_META_DATA);
        for (PackageInfo pkg : packageInfoList) {
            ApplicationInfo app = pkg.applicationInfo;
            if (!app.enabled)
                continue;
            if (app.metaData != null && app.metaData.containsKey("xposedmodule")) {
                String apkPath = pkg.applicationInfo.publicSourceDir;
                String apkName = context.getPackageManager().getApplicationLabel(pkg.applicationInfo).toString();
                if (TextUtils.isEmpty(apkPath)) {
                    apkPath = pkg.applicationInfo.sourceDir;
                }
                if (!TextUtils.isEmpty(apkPath) && (!configFileExist || packageNameList == null || packageNameList
                        .contains(app.packageName))) {
                    XLog.d(TAG, " query installed module path -> " + apkPath);
                    modulePathList.add(apkPath);
                }
                installedModuleList.add(Pair.create(pkg.applicationInfo.packageName, apkName));
            }
        }

        final List<Pair<String, String>> installedModuleListFinal = installedModuleList;

        new Thread(new Runnable() {
            @Override
            public void run() {
                List<String> savedPackageNameList = loadPackageNameListFromFile(false);
                if (savedPackageNameList == null) {
                    savedPackageNameList = new ArrayList<>();
                }
                List<Pair<String, String>> addPackageList = new ArrayList<>();
                for (Pair<String, String> packgagePair : installedModuleListFinal) {
                    if (!savedPackageNameList.contains(packgagePair.first)) {
                        XLog.d(TAG, " addPackageList packgagePair -> " + packgagePair);
                        addPackageList.add(packgagePair);
                    }
                }
                appendPackageNameToFile(addPackageList);
            }
        }).start();
        return modulePathList;
    }

    // 从sd卡中加载指定文件，以加载指定的xposed module
    private static List<String> loadPackageNameListFromFile(boolean loadActivedPackages) {
        File moduleFile = new File(DIR_BASE, XPOSED_MODULE_FILE_PATH);
        if (!moduleFile.exists()) {
            return null;
        }
        if (!moduleFile.getParentFile().exists()) {
            moduleFile.getParentFile().mkdirs();
        }
        List<String> modulePackageList = new ArrayList<>();

        FileInputStream fileInputStream = null;
        BufferedReader bufferedReader = null;
        try {
            fileInputStream = new FileInputStream(moduleFile);
            bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
            String modulePackageName;
            while ((modulePackageName = bufferedReader.readLine()) != null) {
                modulePackageName = modulePackageName.trim();
                if (modulePackageName.isEmpty() || (modulePackageName.startsWith("#") && loadActivedPackages)) {
                    continue;
                }

                if (modulePackageName.startsWith("#")) {
                    modulePackageName = modulePackageName.substring(1);
                }
                int index = modulePackageName.indexOf("#");
                if (index > 0) {
                    modulePackageName = modulePackageName.substring(0, index);
                }
                XLog.d(TAG, " load xposed_module.txt file result,  modulePackageName -> " + modulePackageName);
                modulePackageList.add(modulePackageName);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            closeStream(fileInputStream);
            closeStream(bufferedReader);
        }
        return modulePackageList;
    }

    private static void appendPackageNameToFile(List<Pair<String, String>> packageNameList) {

        if (isEmpty(packageNameList)) {
            return;
        }

        File moduleFile = new File(DIR_BASE, XPOSED_MODULE_FILE_PATH);

        if (!moduleFile.getParentFile().exists()) {
            moduleFile.getParentFile().mkdirs();
        }

        FileOutputStream outputStream = null;
        BufferedWriter writer = null;
        try {
            outputStream = new FileOutputStream(moduleFile, true);
            writer = new BufferedWriter(new OutputStreamWriter(outputStream));

            for (Pair<String, String> packageInfo : packageNameList) {
                String packageName = packageInfo.first;
                String appName = packageInfo.second;
                writer.write("\n\n" + packageName + "#" + appName);
            }
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeStream(outputStream);
            closeStream(writer);
        }
    }

    private static boolean configFileExist() {
        File moduleConfigFile = new File(DIR_BASE, XPOSED_MODULE_FILE_PATH);
        return moduleConfigFile.exists();
    }

    private static void closeStream(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean isEmpty(Collection collection) {
        if (collection == null || collection.size() == 0) {
            return true;
        }
        return false;
    }

    public static Context getAppContext() {
        return appContext;
    }
}
