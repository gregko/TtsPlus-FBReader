package com.hyperionics.fbreader.plugin.tts_plus;

import android.util.Log;

/**
 * Created with IntelliJ IDEA.
 * User: greg
 * Date: 5/25/12
 * Time: 9:04 AM
 * To change this template use File | Settings | File Templates.
 */
public class Lt {
    private static String myTag = "FBReaderTTS";
    private Lt() {}
    static void setTag(String tag) { myTag = tag; }
    public static void d(String msg) {
        // Uncomment line below to turn on debug output
        Log.d(myTag, msg);
    }
    public static void df(String msg) {
        // Forced output, do not comment out - for exceptions etc.
        Log.d(myTag, msg);
    }
}
