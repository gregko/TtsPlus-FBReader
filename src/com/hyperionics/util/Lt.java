package com.hyperionics.util;

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
import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;

public class Lt {
    private static String myTag = "FBReaderTTS";
    private static boolean myResult = false;
    private Lt() {}
    static void setTag(String tag) { myTag = tag; }
    public static void d(String msg) {
        // Uncomment line below to turn on debug output
        Log.d(myTag, msg == null ? "(null)" : msg);
    }
    public static void df(String msg) {
        // Forced output, do not comment out - for exceptions etc.
        Log.d(myTag, msg == null ? "(null)" : msg);
    }
    public static void e(String msg) {
        Log.e(myTag, msg == null ? "(null)" : msg);
    }

    static void alert(Activity activity, String message) {
        AlertDialog.Builder bld = new AlertDialog.Builder(activity);
        bld.setMessage(message);
        bld.setNeutralButton("OK", null);
        bld.create().show();
    }

    static void alert(Activity activity, int resId) {
        alert(activity, activity.getResources().getString(resId));
    }
}