package com.wind.xposed.entry;

import com.wind.xposed.entry.hooker.PackageSignatureHooker;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by Wind
 */
public class XposedHookLoadPackageInner implements IXposedHookLoadPackage {

    private static final String TAG = "XH_LoadPackageInner";

    protected static XposedHookLoadPackageInner newIntance() {
        return new XposedHookLoadPackageInner();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        new PackageSignatureHooker().handleLoadPackage(lpparam);
    }
}
