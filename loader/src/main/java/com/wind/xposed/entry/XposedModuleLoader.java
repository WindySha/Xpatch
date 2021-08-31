package com.wind.xposed.entry;

import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.wind.xposed.entry.util.XLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import dalvik.system.DexClassLoader;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelper;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedModuleLoader {

    private static final String TAG = "XposedModuleLoader";

    public static boolean loadModule(final String moduleApkPath, String moduleOdexDir, String moduleLibPath,
                                 final ApplicationInfo currentApplicationInfo, ClassLoader appClassLoader) {

        XLog.i(TAG, "Loading modules from " + moduleApkPath);

        if (!new File(moduleApkPath).exists()) {
            Log.e(TAG, moduleApkPath + " does not exist");
            return false;
        }

        ClassLoader mcl = new DexClassLoader(moduleApkPath, moduleOdexDir, moduleLibPath, appClassLoader);
        InputStream is = mcl.getResourceAsStream("assets/xposed_init");
        if (is == null) {
            Log.i(TAG, "assets/xposed_init not found in the APK");
            return false;
        }

        BufferedReader moduleClassesReader = new BufferedReader(new InputStreamReader(is));
        try {
            String moduleClassName;
            while ((moduleClassName = moduleClassesReader.readLine()) != null) {
                moduleClassName = moduleClassName.trim();
                if (moduleClassName.isEmpty() || moduleClassName.startsWith("#"))
                    continue;

                try {
                    XLog.i(TAG, "  Loading class " + moduleClassName);
                    Class<?> moduleClass = mcl.loadClass(moduleClassName);

                    if (!XposedHelper.isIXposedMod(moduleClass)) {
                        Log.i(TAG, "    This class doesn't implement any sub-interface of IXposedMod, skipping it");
                        continue;
                    } else if (IXposedHookInitPackageResources.class.isAssignableFrom(moduleClass)) {
                        Log.i(TAG, "    This class requires resource-related hooks (which are disabled), skipping it.");
                        continue;
                    }

                    final Object moduleInstance = moduleClass.newInstance();
                    if (moduleInstance instanceof IXposedHookZygoteInit) {
                        XposedHelper.callInitZygote(moduleApkPath, moduleInstance);
                    }

                    if (moduleInstance instanceof IXposedHookLoadPackage) {
                        // hookLoadPackage(new IXposedHookLoadPackage.Wrapper((IXposedHookLoadPackage) moduleInstance));
                        IXposedHookLoadPackage.Wrapper wrapper = new IXposedHookLoadPackage.Wrapper((IXposedHookLoadPackage) moduleInstance);
                        XposedBridge.CopyOnWriteSortedSet<XC_LoadPackage> xc_loadPackageCopyOnWriteSortedSet = new XposedBridge.CopyOnWriteSortedSet<>();
                        xc_loadPackageCopyOnWriteSortedSet.add(wrapper);
                        XC_LoadPackage.LoadPackageParam lpparam = new XC_LoadPackage.LoadPackageParam(xc_loadPackageCopyOnWriteSortedSet);
                        lpparam.packageName = currentApplicationInfo.packageName;
                        lpparam.processName = getCurrentProcessName(currentApplicationInfo);;
                        lpparam.classLoader = appClassLoader;
                        lpparam.appInfo = currentApplicationInfo;
                        lpparam.isFirstApplication = true;
                        XC_LoadPackage.callAll(lpparam);
                    }

                    if (moduleInstance instanceof IXposedHookInitPackageResources) {
                        // hookInitPackageResources(new IXposedHookInitPackageResources.Wrapper((IXposedHookInitPackageResources) moduleInstance));
                        // TODO: Support Resource hook
                    }

                } catch (Throwable t) {
                    Log.e(TAG, " error ", t);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, " error ", e);
        } finally {
            try {
                is.close();
            } catch (IOException ignored) {
            }
        }
        return true;
    }

    public static void startInnerHook(ApplicationInfo applicationInfo, ClassLoader originClassLoader) {
        IXposedHookLoadPackage.Wrapper wrapper = new IXposedHookLoadPackage.Wrapper(XposedHookLoadPackageInner.newIntance());

        XposedBridge.CopyOnWriteSortedSet<XC_LoadPackage> xc_loadPackageCopyOnWriteSortedSet = new XposedBridge.CopyOnWriteSortedSet<>();
        xc_loadPackageCopyOnWriteSortedSet.add(wrapper);

        XC_LoadPackage.LoadPackageParam lpparam = new XC_LoadPackage.LoadPackageParam(xc_loadPackageCopyOnWriteSortedSet);

        lpparam.packageName = applicationInfo.packageName;
        lpparam.processName = getCurrentProcessName(applicationInfo);
        lpparam.classLoader = originClassLoader;
        lpparam.appInfo = applicationInfo;
        lpparam.isFirstApplication = true;

        XC_LoadPackage.callAll(lpparam);
    }

    private static String currentProcessName = null;

    @SuppressLint("DiscouragedPrivateApi")
    private static String getCurrentProcessName(ApplicationInfo applicationInfo) {
        if (currentProcessName != null) return currentProcessName;

        currentProcessName = applicationInfo.packageName;
        try {
            Class activityThread_clazz = Class.forName("android.app.ActivityThread");
            Method method = activityThread_clazz.getDeclaredMethod("currentProcessName");
            method.setAccessible(true);
            currentProcessName = (String) method.invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return currentProcessName;
    }
}
