package com.wind.xpatch.proxy;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.wind.xposed.entry.SandHookInitialization;
import com.wind.xposed.entry.util.FileUtils;
import com.wind.xposed.entry.util.VMRuntime;
import com.wind.xposed.entry.util.XpatchUtils;

import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import static com.wind.xposed.entry.XposedModuleEntry.init;

/**
 * Created by Windysha
 */
public class XpatchProxyApplication extends Application {
    private static String original_application_name = null;

    private static Application sOriginalApplication = null;

    private static ClassLoader appClassLoader;

    private static Object activityThread;

    private static final String ORIGINAL_APPLICATION_NAME_ASSET_PATH = "xpatch_asset/original_application_name.ini";

    private static final String TAG = "XpatchProxyApplication";

    static {

        VMRuntime.setHiddenApiExemptions(new String[]{"L"});

        Context context = XpatchUtils.createAppContext();

        SandHookInitialization.init(context);

        original_application_name = FileUtils.readTextFromAssets(context, ORIGINAL_APPLICATION_NAME_ASSET_PATH);
        Log.d(TAG, " original_application_name = " + original_application_name);

        if (isApplicationProxied()) {
            doHook();
        }

        init(context);
    }

    public XpatchProxyApplication() {
        super();

        if (isApplicationProxied()) {
            createOriginalApplication();
        }
    }

    private static boolean isApplicationProxied() {
        if (original_application_name != null && !original_application_name.isEmpty()
                && !("android.app.Application").equals(original_application_name)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void attachBaseContext(Context base) {

        if (isApplicationProxied()) {
            // 将applicationInfo中保存的applcation class name还原为真实的application class name
            modifyApplicationInfo_className();
        }

        super.attachBaseContext(base);

        if (isApplicationProxied()) {
            attachOrignalBaseContext(base);
            setLoadedApkField(base);
        }

        // setApplicationLoadedApk(base);
    }

    private void attachOrignalBaseContext(Context base) {
        try {
            XposedHelpers.callMethod(sOriginalApplication, "attachBaseContext", base);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setLoadedApkField(Context base) {
        // mLoadedApk = ContextImpl.getImpl(context).mPackageInfo;
        try {
            Class contextImplClass = Class.forName("android.app.ContextImpl");
            Object contextImpl = XposedHelpers.callStaticMethod(contextImplClass, "getImpl", base);
            Object loadedApk = XposedHelpers.getObjectField(contextImpl, "mPackageInfo");
            XposedHelpers.setObjectField(sOriginalApplication, "mLoadedApk", loadedApk);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        // setLoadedApkField(sOriginalApplication);
        // XposedHelpers.setObjectField(sOriginalApplication, "mLoadedApk", XposedHelpers.getObjectField(this, "mLoadedApk"));
        super.onCreate();

        if (isApplicationProxied()) {
            // replaceApplication();
            replaceLoadedApk_Application();
            replaceActivityThread_Applicatio();

            sOriginalApplication.onCreate();
        }
    }

    private void replaceLoadedApk_Application() {
        try {
            // replace   LoadedApk.java makeApplication()      mActivityThread.mAllApplications.add(app);
            ArrayList<Application> list = (ArrayList<Application>) XposedHelpers.getObjectField(getActivityThread(), "mAllApplications");
            list.add(sOriginalApplication);

            Object mBoundApplication = XposedHelpers.getObjectField(getActivityThread(), "mBoundApplication"); // AppBindData
            Object loadedApkObj = XposedHelpers.getObjectField(mBoundApplication, "info"); // info

            // replace   LoadedApk.java makeApplication()      mApplication = app;
            XposedHelpers.setObjectField(loadedApkObj, "mApplication", sOriginalApplication);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void replaceActivityThread_Applicatio() {
        try {
            XposedHelpers.setObjectField(getActivityThread(), "mInitialApplication", sOriginalApplication);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Application createOriginalApplication() {
        if (sOriginalApplication == null) {
            try {
                sOriginalApplication = (Application) getAppClassLoader().loadClass(original_application_name).newInstance();
            } catch (InstantiationException | ClassNotFoundException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return sOriginalApplication;
    }

    private static ClassLoader getAppClassLoader() {
        if (appClassLoader != null) {
            return appClassLoader;
        }
        try {
            Object mBoundApplication = XposedHelpers.getObjectField(getActivityThread(), "mBoundApplication");
            Object loadedApkObj = XposedHelpers.getObjectField(mBoundApplication, "info");
            appClassLoader = (ClassLoader) XposedHelpers.callMethod(loadedApkObj, "getClassLoader");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return appClassLoader;
    }

    private static void doHook() {
        hookContextImpl_setOuterContext();

        hook_installContentProviders();

        hook_Activity_attach();
        hook_Service_attach();
    }

    private static void hookContextImpl_setOuterContext() {
        XposedHelpers.findAndHookMethod("android.app.ContextImpl", getAppClassLoader(), "setOuterContext", Context
                        .class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // if (param.args[0] == FakeApplication.this) {
                        //     param.args[0] = sOriginalApplication;
                        // }
                        replaceApplicationParam(param.args);

                        // XposedHelpers.setObjectField(param.thisObject, "mOuterContext", sOriginalApplication);
                    }
                });
    }

    private static void hook_installContentProviders() {
        //   ActivityThread.java   handleBindAplication()
        // if (!data.restrictedBackupMode) {
        //     if (!ArrayUtils.isEmpty(data.providers)) {
        //         installContentProviders(app, data.providers);
        //         ...
        //     }
        // }
        XposedBridge.hookAllMethods(XposedHelpers.findClass("android.app.ActivityThread", getAppClassLoader()),
                "installContentProviders",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        replaceApplicationParam(param.args);
                    }
                });
    }

    private static void hook_Activity_attach() {
        XposedBridge.hookAllMethods(XposedHelpers.findClass("android.app.Activity", getAppClassLoader()),
                "attach",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        replaceApplicationParam(param.args);
                    }
                });
    }

    private static void hook_Service_attach() {
        XposedBridge.hookAllMethods(XposedHelpers.findClass("android.app.Service", getAppClassLoader()),
                "attach",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        replaceApplicationParam(param.args);
                    }
                });
    }

    private static void replaceApplicationParam(Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        for (Object para : args) {
            if (para instanceof XpatchProxyApplication) {
                para = sOriginalApplication;
            }
        }
    }

    private void modifyApplicationInfo_className() {
        try {
            Object mBoundApplication = XposedHelpers.getObjectField(getActivityThread(), "mBoundApplication"); // AppBindData
            Object applicationInfoObj = XposedHelpers.getObjectField(mBoundApplication, "appInfo"); // info

            XposedHelpers.setObjectField(applicationInfoObj, "className", original_application_name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Object getActivityThread() {
        if (activityThread == null) {
            try {
                Class activityThreadClass = Class.forName("android.app.ActivityThread");
                activityThread = XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return activityThread;
    }
}
