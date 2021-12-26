package com.wind.xposed.entry.util;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class NativeLibraryHelperCompat {
    private static final String TAG = "NativeLibraryHelper";

    public static int copyNativeBinaries(File apkFile, File sharedLibraryDir) {
        Log.i(TAG, " copyNativeBinaries  !!! apkFile = " + apkFile.getAbsolutePath() + " sharedLibraryDir = " + sharedLibraryDir.getAbsolutePath());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return copyNativeBinariesAfterL(apkFile, sharedLibraryDir);
        } else {
            return copyNativeBinariesBeforeL(apkFile, sharedLibraryDir);
        }
    }

    private static int copyNativeBinariesBeforeL(File apkFile, File sharedLibraryDir) {
        try {
            String className = "com.android.internal.content.NativeLibraryHelper";
            Object result = ReflectUtils.callMethod(className,
                    null, "copyNativeBinariesIfNeededLI",
                    apkFile, sharedLibraryDir);
            if (result != null) {
                return (int) result;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return -1;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static int copyNativeBinariesAfterL(File apkFile, File sharedLibraryDir) {
        try {
            String handleClassName = "com.android.internal.content.NativeLibraryHelper$Handle";
            Object handle = ReflectUtils.callMethod(handleClassName,
                    null, "create", apkFile);
            if (handle == null) {
                return -1;
            }

            String abi = null;
            Set<String> abiSet = getSupportAbiList(apkFile.getAbsolutePath());
            if (abiSet.isEmpty()) {
                return 0;
            }
            boolean is64Bit = is64bit();
            String className = "com.android.internal.content.NativeLibraryHelper";
            if (is64Bit && contain64bitAbi(abiSet)) {
                if (Build.SUPPORTED_64_BIT_ABIS.length > 0) {
                    int abiIndex = (int) ReflectUtils.callMethod(className,
                            null,
                            "findSupportedAbi",
                            handle, Build.SUPPORTED_64_BIT_ABIS);
                    if (abiIndex >= 0) {
                        abi = Build.SUPPORTED_64_BIT_ABIS[abiIndex];
                    }
                }
            } else {
                if (Build.SUPPORTED_32_BIT_ABIS.length > 0) {
                    int abiIndex = (int) ReflectUtils.callMethod(className,
                            null,
                            "findSupportedAbi",
                            handle, Build.SUPPORTED_32_BIT_ABIS);
                    if (abiIndex >= 0) {
                        abi = Build.SUPPORTED_32_BIT_ABIS[abiIndex];
                    }
                }
            }
            Log.i(TAG, " is64Bit=" + is64Bit + " abi = " + abi + " abiSet = " + abiSet + " sharedLibraryDir =" + sharedLibraryDir);
            if (abi == null) {
                Log.e(TAG, "Not match any abi." + apkFile.getAbsolutePath());
                return -1;
            }
            int result = (int) ReflectUtils.callMethod(className,
                    null,
                    "copyNativeBinaries",
                    handle, sharedLibraryDir, abi);
            Log.i(TAG, "copyNativeBinaries result = " + result + " apkFile path = " + apkFile.getAbsolutePath());
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static boolean is64bitAbi(String abi) {
        return "arm64-v8a".equals(abi)
                || "x86_64".equals(abi)
                || "mips64".equals(abi);
    }

    public static boolean is32bitAbi(String abi) {
        return "armeabi".equals(abi)
                || "armeabi-v7a".equals(abi)
                || "mips".equals(abi)
                || "x86".equals(abi);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static boolean contain64bitAbi(Set<String> supportedABIs) {
        for (String supportedAbi : supportedABIs) {
            if (is64bitAbi(supportedAbi)) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> getSupportAbiList(String apk) {
        try {
            ZipFile apkFile = new ZipFile(apk);
            Enumeration<? extends ZipEntry> entries = apkFile.entries();
            Set<String> supportedABIs = new HashSet<String>();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.contains("../")) {
                    continue;
                }
                if (name.startsWith("lib/") && !entry.isDirectory() && name.endsWith(".so")) {
                    String supportedAbi = name.substring(name.indexOf("/") + 1, name.lastIndexOf("/"));
                    supportedABIs.add(supportedAbi);
                }
            }
            return supportedABIs;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Collections.emptySet();
    }

    public static boolean is64bit() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Process.is64Bit();
        }
        Object runtime = ReflectUtils.callMethod("dalvik.system.VMRuntime", null, "getRuntime");
        Object is64Bit = ReflectUtils.callMethod("dalvik.system.VMRuntime", runtime, "is64Bit");
        if (is64Bit == null) return true;
        return (boolean) is64Bit;
    }
}
