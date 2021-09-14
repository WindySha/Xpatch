package com.wind.xposed.entry.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;

public class FileUtils {

    //读写权限
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public static boolean isFilePermissionGranted(Context context) {
        int pid = android.os.Process.myPid();
        int uid = Process.myUid();
        return context.checkPermission(PERMISSIONS_STORAGE[0], pid, uid) == PackageManager.PERMISSION_GRANTED &&
                context.checkPermission(PERMISSIONS_STORAGE[1], pid, uid) == PackageManager.PERMISSION_GRANTED;
    }

    public static String readTextFromAssets(Context context, String assetsFileName) {
        if (context == null) {
            return null;
        }
        try {
            InputStream is = context.getAssets().open(assetsFileName);
            return readTextFromInputStream(is);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String readTextFromInputStream(InputStream is) {
        InputStreamReader reader = null;
        BufferedReader bufferedReader = null;
        try {
            reader = new InputStreamReader(is, "UTF-8");
            bufferedReader = new BufferedReader(reader);
            StringBuilder builder = new StringBuilder();
            String str;
            while ((str = bufferedReader.readLine()) != null) {
                builder.append(str);
            }
            return builder.toString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeSafely(reader);
            closeSafely(bufferedReader);
        }
        return null;
    }

    private static void closeSafely(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
