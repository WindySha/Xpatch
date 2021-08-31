package com.storm.wind.xposed;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;

import com.wind.xposed.entry.XposedModuleEntry;

import java.lang.reflect.Method;

//import de.robv.android.xposed.XC_MethodHook;
//import de.robv.android.xposed.XposedBridge;
//import de.robv.android.xposed.XposedHelper;
//import de.robv.android.xposed.XposedHelpers;

public class XposedTestApplication extends Application {

    static {
        // 加载系统中所有已安装的Xposed Modules
        XposedModuleEntry.init();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        test();
//        hookOnCreate();
    }

//    private void hookOnCreate() {
//        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
//            @Override
//            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                super.beforeHookedMethod(param);
//                Log.e("xiawanli", " beforeHookedMethod  onCreate");
//            }
//
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                super.afterHookedMethod(param);
//                Log.e("xiawanli", " beforeHookedMethod  onCreate");
//            }
//        });
//        XposedHelpers.findAndHookMethod(Test.class, "add", new XC_MethodHook() {
//            @Override
//            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                super.beforeHookedMethod(param);
//                Log.e("xiawanli", " beforeHookedMethod Test add");
//            }
//
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                super.afterHookedMethod(param);
//                Log.e("xiawanli", " afterHookedMethod Test add");
//            }
//        });
//    }

    private void test() {
        try {
            Class activityThreadClass = Class.forName("android.app.ActivityThread");
            Log.e("XposedApplication", " activityThreadClass --> " + activityThreadClass);
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);

            Log.e("XposedApplication", " currentActivityThreadMethod --> " + currentActivityThreadMethod);

            Object activityThreadObj = currentActivityThreadMethod.invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("XposedApplication", " exception --> ", e);
        }

        try {
            Class activityThreadClass = Class.forName("android.view.ScrollCaptureTargetResolver");
            Log.e("XposedApplication", " ScrollCaptureTargetResolver --> " + activityThreadClass);
            Method nullOrEmpty = activityThreadClass.getDeclaredMethod("nullOrEmpty", Rect.class);
            nullOrEmpty.setAccessible(true);

            Object nullOrEmpty_result = nullOrEmpty.invoke(null, new Rect());
            Log.e("XposedApplication", " nullOrEmpty --> " + nullOrEmpty + " nullOrEmpty_result = " + nullOrEmpty_result);

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("XposedApplication", " exception --> ", e);
        }
    }
}
