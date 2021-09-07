package com.storm.wind.xpatch.task;

import com.android.apksigner.ApkSignerTool;
import com.storm.wind.xpatch.util.FileUtils;
import com.storm.wind.xpatch.util.ShellCmdUtil;
import java.io.File;
import java.util.ArrayList;

/**
 * Created by Wind
 */
public class BuildAndSignApkTask implements Runnable {

    private boolean keepUnsignedApkFile;

    private String signedApkPath;

    private String unzipApkFilePath;

    private String originalApkFilePath;

    public BuildAndSignApkTask(boolean keepUnsignedApkFile, String unzipApkFilePath, String signedApkPath, String originalApkFilePath) {
        this.keepUnsignedApkFile = keepUnsignedApkFile;
        this.unzipApkFilePath = unzipApkFilePath;
        this.signedApkPath = signedApkPath;
        this.originalApkFilePath = originalApkFilePath;
    }

    @Override
    public void run() {

        File unzipApkFile = new File(unzipApkFilePath);

        // 将文件压缩到当前apk文件的上一级目录上
        String unsignedApkPath = unzipApkFile.getParent() + File.separator + "unsigned.apk";
        FileUtils.compressToZip(unzipApkFilePath, unsignedApkPath);

        // 将签名文件复制从assets目录下复制出来
        String keyStoreFilePath = unzipApkFile.getParent() + File.separator + "keystore";

        File keyStoreFile = new File(keyStoreFilePath);
        // assets/keystore分隔符不能使用File.separator，否则在windows上抛出IOException !!!
        String keyStoreAssetPath;
        if (ShellCmdUtil.isAndroid()) {
            // BKS-V1 类型
            keyStoreAssetPath = "assets/android.keystore";
        } else {
            // BKS 类型
            keyStoreAssetPath = "assets/keystore";
        }

        FileUtils.copyFileFromJar(keyStoreAssetPath, keyStoreFilePath);

        String unsignedZipalignedApkPath = unzipApkFile.getParent() + File.separator + "unsigned_zipaligned.apk";
        try {
            zipalignApk(unsignedApkPath, unsignedZipalignedApkPath);
        } catch (Exception e) {
            e.printStackTrace();
        }

        String apkPath = unsignedZipalignedApkPath;
        if (!(new File(apkPath).exists())) {
            apkPath = unsignedApkPath;
            System.out.println(" zipalign apk failed, just sign not zipaligned apk !!!");
        }

        boolean signResult = signApk(apkPath, keyStoreFilePath, signedApkPath);

        File unsignedApkFile = new File(unsignedApkPath);
        File signedApkFile = new File(signedApkPath);
        // delete unsigned apk file
        if (!keepUnsignedApkFile && unsignedApkFile.exists() && signedApkFile.exists() && signResult) {
            unsignedApkFile.delete();
        }

        File unsign_zipaligned_file = new File(unsignedZipalignedApkPath);
        if (!keepUnsignedApkFile && unsign_zipaligned_file.exists() && signedApkFile.exists() && signResult) {
            unsign_zipaligned_file.delete();
        }

        File idsigFile = new File(signedApkPath + ".idsig");
        if (idsigFile.exists()) {
            idsigFile.delete();
        }

        // delete the keystore file
        if (keyStoreFile.exists()) {
            keyStoreFile.delete();
        }
    }

    private boolean signApk(String apkPath, String keyStorePath, String signedApkPath) {
        String apkParentPath = (new File(apkPath)).getParent();

        System.out.println(" apkParentPath  :" + apkParentPath);
        ShellCmdUtil.chmodNoException(apkParentPath, ShellCmdUtil.FileMode.MODE_755);
        if (signApkUsingAndroidApksigner(apkPath, keyStorePath, signedApkPath, "123456")) {
            return true;
        }
        if (ShellCmdUtil.isAndroid()) {
            System.out.println(" Sign apk failed, please sign it yourself.");
            return false;
        }
        try {
            long time = System.currentTimeMillis();
            File keystoreFile = new File(keyStorePath);
            if (keystoreFile.exists()) {
                StringBuilder signCmd;
                signCmd = new StringBuilder("jarsigner ");
                signCmd.append(" -keystore ")
                        .append(keyStorePath)
                        .append(" -storepass ")
                        .append("123456")
                        .append(" -signedjar ")
                        .append(" " + signedApkPath + " ")
                        .append(" " + apkPath + " ")
                        .append(" -digestalg SHA1 -sigalg SHA1withRSA ")
                        .append(" key0 ");
//                System.out.println("\n" + signCmd + "\n");
                String result = ShellCmdUtil.execCmd(signCmd.toString(), null);
                System.out.println(" sign apk time is :" + ((System.currentTimeMillis() - time) / 1000) +
                        "s\n\n" + "  result=" + result);
                return true;
            }
            System.out.println(" keystore not exist :" + keystoreFile.getAbsolutePath() +
                    " please sign the apk by hand. \n");
            return false;
        } catch (Throwable e) {
            System.out.println("use default jarsigner to sign apk failed, fail msg is :" +
                    e.toString());
            return false;
        }
    }

    // 使用Android build-tools里自带的apksigner工具进行签名
    private boolean signApkUsingAndroidApksigner(String apkPath, String keyStorePath, String signedApkPath, String keyStorePassword) {
        ArrayList<String> commandList = new ArrayList<>();

        commandList.add("sign");
        commandList.add("--ks");
        commandList.add(keyStorePath);
        commandList.add("--ks-key-alias");
        commandList.add("key0");
        commandList.add("--ks-pass");
        commandList.add("pass:" + keyStorePassword);
        commandList.add("--key-pass");
        commandList.add("pass:" + keyStorePassword);
        commandList.add("--out");
        commandList.add(signedApkPath);
        commandList.add("--v1-signing-enabled");
        commandList.add("true");
        commandList.add("--v2-signing-enabled");   // v2签名不兼容android 6
        commandList.add("true");
        commandList.add("--v3-signing-enabled");   // v3签名不兼容android 6
        commandList.add("true");
        commandList.add(apkPath);

        int size = commandList.size();
        String[] commandArray = new String[size];
        commandArray = commandList.toArray(commandArray);

        try {
            ApkSignerTool.main(commandArray);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void zipalignApk(String inputApkPath, String outputApkPath) {
        long time = System.currentTimeMillis();

        String os = System.getProperty("os.name");
        String zipalignAssetPath = "assets/zipalign";
        if (os.toLowerCase().startsWith("win")) {
            System.out.println(" The running os is " + os);
            zipalignAssetPath = "assets/win/zipalign.exe";
        }

        String zipalignPath = (new File(inputApkPath)).getParent() + File.separator + "zipalign";
        FileUtils.copyFileFromJar(zipalignAssetPath, zipalignPath);
        ShellCmdUtil.chmodNoException(zipalignPath, ShellCmdUtil.FileMode.MODE_755);
        StringBuilder signCmd = new StringBuilder(zipalignPath + " ");

        signCmd.append(" -f ")
                .append(" -p ")
                .append(" 4 ")
                .append(" " + inputApkPath + " ")
                .append(" " + outputApkPath + " ");
        System.out.println("\n" + signCmd + "\n");
        String result = null;
        try {
            result = ShellCmdUtil.execCmd(signCmd.toString(), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        File zipalignFile = new File(zipalignPath);
        if (zipalignFile.exists()) {
            zipalignFile.delete();
        }
        System.out.println(" zipalign apk time is :" + ((System.currentTimeMillis() - time)) +
                "s\n\n" + "  result=" + result);
    }
}
