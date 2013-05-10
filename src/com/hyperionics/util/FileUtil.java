package com.hyperionics.util;

import java.io.*;
import java.util.ArrayList;

/**
 *  Copyright (C) 2012 Hyperionics Technology LLC <http://www.hyperionics.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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
