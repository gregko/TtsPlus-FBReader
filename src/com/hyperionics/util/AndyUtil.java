package com.hyperionics.util;

import android.app.Application;
import android.content.Context;

import java.io.File;
import java.lang.reflect.Method;
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
public class AndyUtil {

    private static Application myApp = getApp();

    private static ArrayList<File> myTempFiles = new ArrayList<File>();

    private AndyUtil() {}

    public static Application getApp() {
        if (myApp == null) {
            try {
                final Class<?> activityThreadClass =
                        Class.forName("android.app.ActivityThread");
                final Method method = activityThreadClass.getMethod("currentApplication");
                myApp = (Application) method.invoke(null, (Object[]) null);
            } catch (Exception e) {
                // handle exception
            }
        }
        // below gives me /mnt/sdcard/Android/data/com.hyperionics.pdfxTest/cache/tmpPdfx
        // File file = new File(app.getApplicationContext().getExternalCacheDir(), "tmpPdfx");
        return myApp;
    }

    public static Context getAppContext() {
        return getApp().getApplicationContext();
    }

    public static File getTempFile(String tmpName) {
        // getExternalCacheDir() returns something like /mnt/sdcard/Android/data/[package_name]/cache/
        File file = new File(getApp().getApplicationContext().getExternalCacheDir(), tmpName);
        myTempFiles.add(file);
        return file;
    }

    public static File getTempFile() {
        File file = null;
        try {
            file = File.createTempFile("temp", ".tmp", getApp().getApplicationContext().getExternalCacheDir());
            myTempFiles.add(file);
        } catch (Exception e) {}
        return file;
    }

    public static File getPermFile(String permName) {
        // getExternalFilesDir() returns something like /mnt/sdcard/Android/data/[package_name]/files/
        File file = new File(getApp().getApplicationContext().getExternalFilesDir(null), permName);
        return file;
    }

    public static void deleteTempFiles() {
        for (File f : myTempFiles)
            f.delete();
    }

    public static void reportProgress(String phase, int count, int total) {
        if (total > 0)
            Lt.d("Progress: " + phase + ": " + count + " of " + total);
        else
            Lt.d("Progress: " + phase + ": " + count);
    }

}
