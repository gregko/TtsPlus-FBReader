package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

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
    int versionCode = 0;

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

    private void EnableComponents(boolean enabled) {
        int flag = (enabled ?
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        ComponentName component = new ComponentName(getPackageName(),
                MediaButtonIntentReceiver.class.getName());
        getPackageManager().setComponentEnabledSetting(component, flag,
                PackageManager.DONT_KILL_APP);
        component = new ComponentName(getPackageName(),
                BluetoothConnectReceiver.class.getName());
        getPackageManager().setComponentEnabledSetting(component, flag,
                PackageManager.DONT_KILL_APP);
    }

    static void exitApp() {
        if (SpeakActivity.getCurrent() != null)
            SpeakActivity.getCurrent().finish();
        SpeakService.mAudioManager.unregisterMediaButtonEventReceiver(SpeakService.componentName);
        SpeakService.mAudioManager.abandonAudioFocus(SpeakService.afChangeListener);
        myApplication.EnableComponents(false);
        SpeakService.stop();
        System.exit(0); // exit, so that next time FBReader is activated, we regain BT focus.
    }

    @Override
    public void onCreate() {
        myApplication = this;
        if (!isFbrPackageOnTop()) {
            Lt.d("No FBReader on top, exiting.");
            EnableComponents(false);
            System.exit(0);
        }
        try {
            EnableComponents(true);
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            Lt.d("version = " + versionName + " (" + versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
            ;
        }
        startService(new Intent(this, SpeakService.class));
    }

    static Context getContext() { return myApplication; }
}