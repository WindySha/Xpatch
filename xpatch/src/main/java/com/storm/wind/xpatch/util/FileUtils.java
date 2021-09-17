package com.storm.wind.xpatch.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by Wind
 */
public class FileUtils {

    static final int BUFFER = 8192;

    // from : http://androidxref.com/9.0.0_r3/xref/frameworks/base/tools/aapt/Package.cpp#30
    static final String[] kNoCompressExt = {
            ".jpg", ".jpeg", ".png", ".gif",
            ".wav", ".mp2", ".mp3", ".ogg", ".aac",
            ".mpg", ".mpeg", ".mid", ".midi", ".smf", ".jet",
            ".rtttl", ".imy", ".xmf", ".mp4", ".m4a",
            ".m4v", ".3gp", ".3gpp", ".3g2", ".3gpp2",
            ".amr", ".awb", ".wma", ".wmv",
            ".tflite", ".lite"
    };
    private static final String APPEND_PREFIX_FORMAT = "#$&(";
    private static final String APPEND_SUBFIX_FORMAT = ")&$#";

    private static final HashSet<String> ResFileNameSet = new HashSet<>();
    private static final String RES_PATH_CONST = "res" + File.separator;


    /**
     * 解压文件
     *
     * @param zipPath 要解压的目标文件
     * @param descDir 指定解压目录
     * @return 解压结果：成功，失败
     */
    @SuppressWarnings("rawtypes")
    public static boolean decompressZip(String zipPath, String descDir) {
        ResFileNameSet.clear();
        File zipFile = new File(zipPath);
        boolean flag = false;
        if (!descDir.endsWith(File.separator)) {
            descDir = descDir + File.separator;
        }
        File pathFile = new File(descDir);
        if (!pathFile.exists()) {
            pathFile.mkdirs();
        }

        ZipFile zip = null;
        try {
            try {
                // api level 24 才有此方法
                zip = new ZipFile(zipFile, Charset.forName("gbk"));//防止中文目录，乱码
            } catch (NoSuchMethodError e) {
                // api < 24
                zip = new ZipFile(zipFile);
            }
            for (Enumeration entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                String zipEntryName = entry.getName();
                InputStream in = zip.getInputStream(entry);

                //指定解压后的文件夹+当前zip文件的名称
                String outPath = (descDir + zipEntryName).replace("/", File.separator);
                //判断路径是否存在,不存在则创建文件路径
                File file = new File(outPath.substring(0, outPath.lastIndexOf(File.separator)));

                if (!file.exists()) {
                    file.mkdirs();
                }
                //判断文件全路径是否为文件夹,如果是上面已经上传,不需要解压
                if (new File(outPath).isDirectory()) {
                    continue;
                }

                // 处理res/aaa.xml资源名称忽略大小写出现重复的问题, 比如 youtube app, 漫画人app等等
                if (zipEntryName.startsWith(RES_PATH_CONST) && zipEntryName.split(File.separator).length == 2) {
                    String fileName = zipEntryName.split(File.separator)[1];
                    fileName = getNotContainedFileName(fileName, ResFileNameSet);
                    ResFileNameSet.add(fileName.toLowerCase());

                    zipEntryName = RES_PATH_CONST + fileName;
                    outPath = (descDir + zipEntryName).replace("/", File.separator);
                }

                //保存文件路径信息（可利用md5.zip名称的唯一性，来判断是否已经解压）
//                System.err.println("当前zip解压之后的路径为：" + outPath);
                OutputStream out = new FileOutputStream(outPath);
                byte[] buf1 = new byte[2048];
                int len;
                while ((len = in.read(buf1)) > 0) {
                    out.write(buf1, 0, len);
                }
                close(in);
                close(out);
            }
            flag = true;
            close(zip);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return flag;
    }

    private static String getNotContainedFileName(String fileName, HashSet<String> set) {
        StringBuilder name = new StringBuilder(fileName);
        if (set.contains(name.toString().toLowerCase())) {
            for (int i = 1; i < 100; i++) {
                name.insert(0, APPEND_SUBFIX_FORMAT);
                name.insert(0, i);
                name.insert(0, APPEND_PREFIX_FORMAT);
                if (!set.contains(name.toString().toLowerCase())) {
                    return name.toString();
                }
            }
        } else {
            return name.toString();
        }
        return name.toString();
    }

    private static String removeFileNamePrefix(String fileName) {
        String lastPrefix = APPEND_PREFIX_FORMAT + "1" + APPEND_SUBFIX_FORMAT;
        int index = fileName.lastIndexOf(lastPrefix);
        if (index >= 0) {
            return fileName.substring(index + lastPrefix.length());
        } else {
            return fileName;
        }
    }

    private static InputStream getInputStreamFromFile(String filePath) {
        return FileUtils.class.getClassLoader().getResourceAsStream(filePath);
    }

    // copy an asset file into a path
    public static void copyFileFromJar(String inJarPath, String distPath) {

//        System.out.println("start copyFile  inJarPath =" + inJarPath + "  distPath = " + distPath);
        InputStream inputStream = getInputStreamFromFile(inJarPath);

        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        try {
            in = new BufferedInputStream(inputStream);
            out = new BufferedOutputStream(new FileOutputStream(distPath));

            int len = -1;
            byte[] b = new byte[1024];
            while ((len = in.read(b)) != -1) {
                out.write(b, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(out);
            close(in);
        }
    }

    public static void copyFile(String sourcePath, String targetPath) {
        copyFile(new File(sourcePath), new File(targetPath));
    }

    public static void copyFile(File source, File target) {

        FileInputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            inputStream = new FileInputStream(source);
            outputStream = new FileOutputStream(target);
            FileChannel iChannel = inputStream.getChannel();
            FileChannel oChannel = outputStream.getChannel();

            ByteBuffer buffer = ByteBuffer.allocate(1024);
            while (true) {
                buffer.clear();
                int r = iChannel.read(buffer);
                if (r == -1) {
                    break;
                }
                buffer.limit(buffer.position());
                buffer.position(0);
                oChannel.write(buffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(inputStream);
            close(outputStream);
        }
    }

    public static void deleteDir(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null && files.length > 0) {
                for (File f : files) {
                    deleteDir(f);
                }
            }
        }
        file.delete();
    }

    public static void compressToZip(String srcPath, String dstPath) {
        File srcFile = new File(srcPath);
        File dstFile = new File(dstPath);
        if (!srcFile.exists()) {
            System.out.println(srcPath + " does not exist ！");
            return;
        }

        FileOutputStream out = null;
        ZipOutputStream zipOut = null;
        try {
            out = new FileOutputStream(dstFile);
            CheckedOutputStream cos = new CheckedOutputStream(out, new CRC32());
            zipOut = new ZipOutputStream(cos);
            String baseDir = "";
            compress(srcFile, zipOut, baseDir, true);
        } catch (IOException e) {
            System.out.println(" compress exception = " + e.getMessage());
        } finally {
            try {
                if (zipOut != null) {
                    zipOut.closeEntry();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            close(zipOut);
            close(out);
        }
    }

    private static void compress(File file, ZipOutputStream zipOut, String baseDir, boolean isRootDir) throws IOException {
        if (file.isDirectory()) {
            compressDirectory(file, zipOut, baseDir, isRootDir);
        } else {
            compressFile(file, zipOut, baseDir);
        }
    }

    /**
     * 压缩一个目录
     */
    private static void compressDirectory(File dir, ZipOutputStream zipOut, String baseDir, boolean isRootDir) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            String compressBaseDir = "";
            if (!isRootDir) {
                compressBaseDir = baseDir + dir.getName() + "/";
            }
            compress(files[i], zipOut, compressBaseDir, false);
        }
    }

    /**
     * 压缩一个文件
     */
    private static void compressFile(File file, ZipOutputStream zipOut, String baseDir) throws IOException {
        if (!file.exists()) {
            return;
        }

        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            String fileName = file.getName();
            if (baseDir.equals(RES_PATH_CONST)) {   // 处理res/目录下，文件名称忽略大小写出现重复的问题
                fileName = removeFileNamePrefix(fileName);
            }
            ZipEntry entry = new ZipEntry(baseDir + fileName);
            boolean isNoCompressFileFormat = false;
            int index = fileName.lastIndexOf(".");
            if (index >= 0) {
                String suffix = fileName.substring(index);
                for (String s : kNoCompressExt) {
                    if (s.equalsIgnoreCase(suffix)) {
                        isNoCompressFileFormat = true;
                        break;
                    }
                }
            }
            if (fileName.equals("resources.arsc") || isNoCompressFileFormat) {
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(file.length());
                long crc = calFileCRC32(file);
                entry.setCrc(crc);
            }
            zipOut.putNextEntry(entry);
            int count;
            byte data[] = new byte[BUFFER];
            while ((count = bis.read(data, 0, BUFFER)) != -1) {
                zipOut.write(data, 0, count);
            }

        } finally {
            if (null != bis) {
                bis.close();
            }
        }
    }

    public static long calFileCRC32(File file) throws IOException {
        FileInputStream fi = new FileInputStream(file);
        CheckedInputStream checksum = new CheckedInputStream(fi, new CRC32());
        while (checksum.read() != -1) {
        }
        long temp = checksum.getChecksum().getValue();
        fi.close();
        checksum.close();
        return temp;
    }

    private static ZipEntry getZipEntryFromZipFile(String zipPath, String fileName) {
        ZipInputStream zin = null;
        try {
            zin = new ZipInputStream(new FileInputStream(zipPath));

            ZipEntry entry = zin.getNextEntry();
            while (entry != null) {
                String name = entry.getName();
                if (name.equals(fileName)) {
                    return entry;
                }
                entry = zin.getNextEntry();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (zin != null) {
                try {
                    zin.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static void writeFile(String filePath, String content) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }
        if (content == null || content.isEmpty()) {
            return;
        }

        File dstFile = new File(filePath);

        if (!dstFile.getParentFile().exists()) {
            dstFile.getParentFile().mkdirs();
        }

        FileOutputStream outputStream = null;
        BufferedWriter writer = null;
        try {
            outputStream = new FileOutputStream(dstFile);
            writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            writer.write(content);
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close(outputStream);
            close(writer);
        }
    }

    private static void close(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException io) {
            io.printStackTrace();
        }
    }
}
