package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: greg
 * Date: 5/22/12
 * Time: 5:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class SpeakApp extends Application
{
    private static SpeakApp myApplication;
    static String versionName = "";
    static int versionCode = 0;
    static PackageManager myPackageManager;
    static String myPackageName;

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

    static boolean isFbrPackageOnTop() {
        ActivityManager am = (ActivityManager) myApplication.getSystemService(ACTIVITY_SERVICE);
        // get the info from the currently running task
        List< ActivityManager.RunningTaskInfo > taskInfo = am.getRunningTasks(1);
        String cn = taskInfo.get(0).topActivity.getPackageName();
        return cn.equals("org.geometerplus.zlibrary.ui.android");
    }

    static void EnableComponents(boolean enabled) {
        int flag = (enabled ?
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        ComponentName component = new ComponentName(myPackageName,
                MediaButtonIntentReceiver.class.getName());
        myPackageManager.setComponentEnabledSetting(component, flag,
                PackageManager.DONT_KILL_APP);
        component = new ComponentName(myPackageName,
                BluetoothConnectReceiver.class.getName());
        myPackageManager.setComponentEnabledSetting(component, flag,
                PackageManager.DONT_KILL_APP);
        if (SpeakService.mAudioManager != null)
        {
            if (enabled) {
                SpeakService.mAudioManager.registerMediaButtonEventReceiver(SpeakService.componentName);
                SpeakService.mAudioManager.requestAudioFocus(SpeakService.afChangeListener,
                        // Use the music stream.
                        AudioManager.STREAM_MUSIC,
                        // Request permanent focus.
                        AudioManager.AUDIOFOCUS_GAIN);
            }
            else {
                SpeakService.mAudioManager.unregisterMediaButtonEventReceiver(SpeakService.componentName);
                SpeakService.mAudioManager.abandonAudioFocus(SpeakService.afChangeListener);
            }
        }
    }

    @Override
    public void onCreate() {
        myApplication = this;
        myPackageManager = getPackageManager();
        myPackageName = getPackageName();
        if (!isFbrPackageOnTop()) {
            Lt.d("No FBReader on top, disabling components.");
            EnableComponents(false);
        }
        try {
            EnableComponents(true);
            versionName = myPackageManager.getPackageInfo(myPackageName, 0).versionName;
            versionCode = myPackageManager.getPackageInfo(myPackageName, 0).versionCode;
            Lt.d("version = " + versionName + " (" + versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
            ;
        }
        startService(new Intent(this, SpeakService.class));
    }

    static Context getContext() { return myApplication; }
}