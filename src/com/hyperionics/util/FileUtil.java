package com.hyperionics.util;

import java.io.*;
import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: greg
 * Date: 2/4/13
 * Time: 11:08 AM
 * To change this template use File | Settings | File Templates.
 */
public class FileUtil {
    private FileUtil() {}

    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1)
        {
            out.write(buffer, 0, read);
        }
        out.flush();
    }

    public static boolean copyFile(File fin, File fout) {
        FileInputStream in;
        FileOutputStream out;
        try {
            in = new FileInputStream(fin);
            out = new FileOutputStream(fout);
            FileUtil.copyFile(in, out);
            in.close();
            out.flush();
            out.close();
        } catch(IOException e) {
            return false;
        }
        return true;
    }

    public static ArrayList<File> listFilesRecursive(File dir) { // returns files only, including files in sub-dirs
        ArrayList<File> result = new ArrayList<File>();
        if (!dir.isDirectory())
            return result;
        for (File f : dir.listFiles()) {
            if (f.isDirectory())
                result.addAll(listFilesRecursive(f));
            else
                result.add(f);
        }
        return result;
    }

    public static boolean moveContents(File srcDir, File dstDir) {
        if (!srcDir.isDirectory() || !(srcDir.canRead() && srcDir.canWrite()))
            return false;
        if (!dstDir.exists())
            dstDir.mkdirs();
        if (!dstDir.isDirectory())
            return false;
        ArrayList<File> files = listFilesRecursive(srcDir);
        String srcPath = srcDir.getAbsolutePath();
        String dstPath = dstDir.getAbsolutePath();
        for (File f : files) {
            File parentDir = new File(f.getParent().replace(srcPath, dstPath));
            parentDir.mkdirs();
            if (!parentDir.exists())
                return false;
            String newName = parentDir.getAbsolutePath() + "/" + f.getName();
            if (!f.renameTo(new File(newName))) {
                File newFile = new File(newName);
                if (!copyFile(f, newFile))
                    return false;
            }
        }

        return true;
    }

    public static boolean deleteFolder(File dir) { // deletes all contents and sub-folders
        for (File f : dir.listFiles()) {
            if (f.isDirectory())
                deleteFolder(f);
            else
                f.delete();
        }
        return dir.delete();
    }
}
