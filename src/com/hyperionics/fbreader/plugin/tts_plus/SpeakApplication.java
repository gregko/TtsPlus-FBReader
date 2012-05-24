package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: greg
 * Date: 5/22/12
 * Time: 5:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class SpeakApplication extends Application
{
    private static SpeakApplication myApplication;

    static boolean isFBReaderOnTop() {
        // the code below needs:  <uses-permission android:name="android.permission.GET_TASKS"/>
        // Gets:
        // taskInfo.get(0).topActivity.getClassName(): org.geometerplus.android.fbreader.FBReader
        // taskInfo.get(0).topActivity.getPackageName(): org.geometerplus.zlibrary.ui.android

        ActivityManager am = (ActivityManager) myApplication.getSystemService(ACTIVITY_SERVICE);
        // get the info from the currently running task
        List< ActivityManager.RunningTaskInfo > taskInfo = am.getRunningTasks(1);
        String cn = taskInfo.get(0).topActivity.getClassName();
        return cn.equals("org.geometerplus.android.fbreader.FBReader");
    }

    static void exitApp() {
        SpeakService.stop();
        System.exit(0); // exit, so that next time FBReader is activated, we regain BT focus.
    }

    @Override
    public void onCreate() {
        myApplication = this;
        startService(new Intent(this, SpeakService.class));
    }

    static Context getContext() { return myApplication; }
}