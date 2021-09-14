package com.wind.xposed.entry.util;

import android.annotation.SuppressLint;
import android.os.Build;

import java.lang.reflect.Method;

@SuppressLint("DiscouragedPrivateApi")
public class VMRuntime {
    public static void setHiddenApiExemptions(final String[] signaturePrefixes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                final Method method_getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod",
                        String.class, Class[].class);
                final Method method_forName = Class.class.getDeclaredMethod("forName", String.class);
                final Class<?> class_VMRuntime = (Class<?>) method_forName.invoke(null, "dalvik.system.VMRuntime");
                final Method method_getRuntime = (Method) method_getDeclaredMethod.invoke(class_VMRuntime,
                        "getRuntime", null);
                final Object object_VMRuntime = method_getRuntime.invoke(null);

                Method setHiddenApiExemptions = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    final Class<?> class_Unsafe = Class.forName("sun.misc.Unsafe");
                    final Method method_getUnsafe = class_Unsafe.getDeclaredMethod("getUnsafe");
                    final Object object_Unsafe = method_getUnsafe.invoke(null);

                    final Method method_getLong = class_Unsafe.getDeclaredMethod("getLong", long.class);
                    final Method method_putLong = class_Unsafe.getDeclaredMethod("putLong", long.class, long.class);

                    final Method method_getInt = class_Unsafe.getDeclaredMethod("getInt", long.class);
                    // final Method method_putInt = class_Unsafe.getDeclaredMethod("putInt", long.class, int.class);

                    final Method method_addressOf = class_VMRuntime.getDeclaredMethod("addressOf", Object.class);
                    final Method method_newNonMovableArray = class_VMRuntime.getDeclaredMethod("newNonMovableArray", Class.class, int.class);

                    final Method[] declaredMethods = class_VMRuntime.getDeclaredMethods();
                    final int length = declaredMethods.length;
                    final Method[] array = (Method[]) method_newNonMovableArray.invoke(object_VMRuntime,
                            Method.class, length);
                    System.arraycopy(declaredMethods, 0, array, 0, length);

                    // http://aosp.opersys.com/xref/android-11.0.0_r3/xref/art/runtime/mirror/executable.h
                    // uint64_t Executable::art_method_
                    final int offset_art_method_ = 24;

                    final long address = (long) method_addressOf.invoke(object_VMRuntime, (Object) array);
                    long min = Long.MAX_VALUE, min_second = Long.MAX_VALUE, max = Long.MIN_VALUE;
                    for (int k = 0; k < length; ++k) {
                        final long address_Method = (int) method_getInt.invoke(object_Unsafe, address + k * Integer.BYTES);
                        final long address_art_method = (long) method_getLong.invoke(object_Unsafe,
                                address_Method + offset_art_method_);
                        if (min >= address_art_method) {
                            min = address_art_method;
                        } else if (min_second >= address_art_method) {
                            min_second = address_art_method;
                        }
                        if (max <= address_art_method) {
                            max = address_art_method;
                        }
                    }

                    final long size_art_method = min_second - min;
                    if (size_art_method > 0 && size_art_method < 100) {
                        for (min += size_art_method; min < max; min += size_art_method) {
                            final long address_Method = (int) method_getInt.invoke(object_Unsafe, address);
                            method_putLong.invoke(object_Unsafe,
                                    address_Method + offset_art_method_, min);
                            final String name = array[0].getName();
                            if ("setHiddenApiExemptions".equals(name)) {
                                setHiddenApiExemptions = array[0];
                                break;
                            }
                        }
                    }
                } else {
                    setHiddenApiExemptions = (Method) method_getDeclaredMethod.invoke(class_VMRuntime,
                            "setHiddenApiExemptions", new Class[]{String[].class});
                }

                if (setHiddenApiExemptions != null) {
                    setHiddenApiExemptions.invoke(object_VMRuntime, (Object) signaturePrefixes);
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }
}
