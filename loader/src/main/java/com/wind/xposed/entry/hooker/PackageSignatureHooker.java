package com.wind.xposed.entry.hooker;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

// import com.lody.whale.WhaleRuntime;
import com.wind.xposed.entry.XposedModuleEntry;
import com.wind.xposed.entry.util.FileUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class PackageSignatureHooker implements IXposedHookLoadPackage {

    private final static String SIGNATURE_INFO_ASSET_PATH = "xpatch_asset/original_signature_info.ini";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        Context context = XposedModuleEntry.getAppContext();
        final String originalSignature = getOriginalSignatureFromAsset(context);
        android.util.Log.d("PackageSignatureHooker", "Get the original signature --> " + originalSignature);
        if (originalSignature == null || originalSignature.isEmpty()) {
            return;
        }

        // hookSignatureByXposed(lpparam, originalSignature);  //不稳定  暂时不使用
        hookSignatureByProxy(lpparam, originalSignature, context);

    }

    private void hookSignatureByXposed(XC_LoadPackage.LoadPackageParam lpparam, final String originalSignature) {
        final String currentPackageName = lpparam.packageName;

        XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", lpparam.classLoader,
                "getPackageInfo", String.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            int flag = (int) param.args[1];
                            PackageInfo packageInfo = (PackageInfo) param.getResult();
                            android.util.Log.d("PackageSignatureHooker", "Get flag " + flag + " packageInfo =" +
                                    packageInfo);

                            if (PackageManager.GET_SIGNATURES == flag) {
                                if (param.args[0] != null && param.args[0] instanceof String) {
                                    String packageName = (String) param.args[0];
                                    if (!packageName.equals(currentPackageName)) {
                                        return;
                                    }
                                }

                                //  先获取这个方法返回的结果
                                if (packageInfo.signatures != null && packageInfo.signatures.length > 0) {
                                    android.util.Log.d("PackageSignatureHooker", "ackageInfo.signatures " + packageInfo.signatures
                                            + " packageInfo =" +
                                            packageInfo);
                                    // 替换结果里的签名信息
                                    packageInfo.signatures[0] = new Signature(originalSignature);
                                }
                            } else if (Build.VERSION.SDK_INT >= 28 && PackageManager.GET_SIGNING_CERTIFICATES == flag) {
                                if (param.args[0] != null && param.args[0] instanceof String) {
                                    String packageName = (String) param.args[0];
                                    if (!packageName.equals(currentPackageName)) {
                                        return;
                                    }
                                }

                                if (packageInfo.signingInfo != null) {
                                    Signature[] signaturesArray = packageInfo.signingInfo.getApkContentsSigners();
                                    if (signaturesArray != null && signaturesArray.length > 0) {
                                        signaturesArray[0] = new Signature(originalSignature);
                                    }
                                }
                            }
                            // 更改最终的返回结果
                            param.setResult(packageInfo);
                        } catch (Throwable e) {
                            e.printStackTrace();
                            android.util.Log.e("PackageSignatureHooker", "Get the original signature  " +
                                    " failed !!!!!!!! ", e);
                        }
                    }
                });
    }

    private void hookSignatureByProxy(XC_LoadPackage.LoadPackageParam lpparam, String originalSignature, Context context) {
        try {

            // Just make sure whale so is loaded, the the hidden policy is disabled.
            // WhaleRuntime.reserved2();

            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            // 获取全局的ActivityThread对象的实现
            Object activityThreadObj = currentActivityThreadMethod.invoke(null);

            // 获取ActivityThread里的getPackageManager方法获取原始的sPackageManager对象
            Method getPackageManagerMethod = activityThreadClass.getDeclaredMethod("getPackageManager");
            getPackageManagerMethod.setAccessible(true);
            Object packageManagerObj = getPackageManagerMethod.invoke(activityThreadObj);

            // 准备好代理对象, 用来替换原始的对象
            Class<?> iPackageManagerInterface = Class.forName("android.content.pm.IPackageManager");
            Object proxy = Proxy.newProxyInstance(
                    iPackageManagerInterface.getClassLoader(),
                    new Class<?>[]{iPackageManagerInterface},
                    new MyInvocationHandler(packageManagerObj, lpparam.packageName, originalSignature));
            // 1. 替换掉ActivityThread里面的 sPackageManager 字段
            Field sPackageManagerField = activityThreadClass.getDeclaredField("sPackageManager");
            sPackageManagerField.setAccessible(true);
            sPackageManagerField.set(activityThreadObj, proxy);
            // 2. 替换 ApplicationPackageManager里面的 mPM对象
            PackageManager pm = context.getPackageManager();
            Field mPmField = pm.getClass().getDeclaredField("mPM");
            mPmField.setAccessible(true);
            mPmField.set(pm, proxy);
        } catch (Exception e) {
            android.util.Log.e("PackageSignatureHooker", " hookSignatureByProxy failed !!", e);
        }
    }

    static class MyInvocationHandler implements InvocationHandler {

        private Object pmBase;
        private String currentPackageName;
        private String originalSignature;

        public MyInvocationHandler(Object base, String currentPackageName, String originalSignature) {
            pmBase = base;
            this.currentPackageName = currentPackageName;
            this.originalSignature = originalSignature;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("getPackageInfo".equals(method.getName())) {
                if (args[0] != null && args[0] instanceof String) {
                    String packageName = (String) args[0];
                    if (!packageName.equals(currentPackageName)) {
                        return method.invoke(pmBase, args);
                    }
                }

                Integer flag = (Integer) args[1];
                if (PackageManager.GET_SIGNATURES == flag) {
                    PackageInfo packageInfo = (PackageInfo) method.invoke(pmBase, args);

                    //  先获取这个方法返回的结果
                    if (packageInfo.signatures != null && packageInfo.signatures.length > 0) {
                        // 替换结果里的签名信息
                        packageInfo.signatures[0] = new Signature(originalSignature);
                    }
                    return packageInfo;
                } else if (Build.VERSION.SDK_INT >= 28 && PackageManager.GET_SIGNING_CERTIFICATES == flag) {
                    PackageInfo packageInfo = (PackageInfo) method.invoke(pmBase, args);
                    if (packageInfo.signingInfo != null) {
                        Signature[] signaturesArray = packageInfo.signingInfo.getApkContentsSigners();
                        if (signaturesArray != null && signaturesArray.length > 0) {
                            signaturesArray[0] = new Signature(originalSignature);
                        }
                    }
                    return packageInfo;
                }
            }
            return method.invoke(pmBase, args);
        }
    }

    private String getOriginalSignatureFromAsset(Context context) {
        return FileUtils.readTextFromAssets(context, SIGNATURE_INFO_ASSET_PATH);
    }
}
