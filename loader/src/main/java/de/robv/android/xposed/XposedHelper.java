package de.robv.android.xposed;

import android.util.Log;

import java.lang.reflect.Member;

public class XposedHelper {

    private static final String TAG = "XposedHelper";

    public static void initSeLinux(String processName) {
        // SELinuxHelper.initOnce();
        // SELinuxHelper.initForProcess(processName);
    }

    public static boolean isIXposedMod(Class<?> moduleClass) {
        // Log.d(TAG, "module's classLoader : " + moduleClass.getClassLoader() + ", super: " + moduleClass.getSuperclass());
        // Log.d(TAG, "IXposedMod's classLoader : " + IXposedMod.class.getClassLoader());
        return IXposedMod.class.isAssignableFrom(moduleClass);
    }


    public static XC_MethodHook.Unhook newUnHook(XC_MethodHook methodHook, Member member) {
        return methodHook.new Unhook(member);
    }

    public static void callInitZygote(String modulePath, Object moduleInstance) throws Throwable {
        IXposedHookZygoteInit.StartupParam param = new IXposedHookZygoteInit.StartupParam();
        param.modulePath = modulePath;
        param.startsSystemServer = false;
        ((IXposedHookZygoteInit) moduleInstance).initZygote(param);
    }

    public static void beforeHookedMethod(XC_MethodHook methodHook, XC_MethodHook.MethodHookParam param) throws Throwable{
        methodHook.beforeHookedMethod(param);
    }

    public static void afterHookedMethod(XC_MethodHook methodHook, XC_MethodHook.MethodHookParam param) throws Throwable{
        methodHook.afterHookedMethod(param);
    }
}
