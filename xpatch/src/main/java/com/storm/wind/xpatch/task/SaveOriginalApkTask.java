package com.storm.wind.xpatch.task;

import com.storm.wind.xpatch.util.FileUtils;

import java.io.File;

/**
 * Created by Windysha
 * This is used to copy original apk into the apk asset directory, so another xposed module can use it to by pass apk check.
 */
public class SaveOriginalApkTask implements Runnable {

    private final String apkPath;
    private String dstApkFilePath;
    private String dstXposedModulePath;

    private static final String ORIGINAL_APK_ASSET_PATH = "assets/xpatch_asset/original_apk/base.apk";
    public static final String XPOSED_MODULE_ASSET_PATH = "assets/xpatch_asset/original_apk/xposedmodule.apk";

    public SaveOriginalApkTask(String apkPath, String unzipApkFilePath) {
        this.apkPath = apkPath;
        this.dstApkFilePath = (unzipApkFilePath + ORIGINAL_APK_ASSET_PATH).replace("/", File.separator);
        this.dstXposedModulePath = (unzipApkFilePath + XPOSED_MODULE_ASSET_PATH).replace("/", File.separator);
    }

    @Override
    public void run() {
        ensureDstFileCreated();
        FileUtils.copyFile(apkPath, dstApkFilePath);

        String moduleAssetPath = "assets/xposedmodule/hook_apk_path_module.apk";
        FileUtils.copyFileFromJar(moduleAssetPath, dstXposedModulePath);
    }

    private void ensureDstFileCreated() {
        File dstParentFile = new File(dstApkFilePath);
        if (!dstParentFile.getParentFile().exists()) {
            dstParentFile.getParentFile().mkdirs();
        }
    }
}