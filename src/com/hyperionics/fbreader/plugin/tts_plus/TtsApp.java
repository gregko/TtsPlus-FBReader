package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Created with IntelliJ IDEA.
 * User: greg
 * Date: 5/22/12
 * Time: 5:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class TtsApp extends Application
{
    private static TtsApp myApplication;
    private static boolean componentsEnabled = false;
    static String versionName = "";
    static int versionCode = 0;
    static PackageManager myPackageManager;
    static String myPackageName;

    static void enableComponents(boolean enabled) {
        componentsEnabled = enabled;
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
        if (enabled) {
            SpeakService.mAudioManager.registerMediaButtonEventReceiver(SpeakService.componentName);
        }
        else {
            SpeakService.mAudioManager.unregisterMediaButtonEventReceiver(SpeakService.componentName);
            SpeakService.mAudioManager.abandonAudioFocus(SpeakService.afChangeListener);
        }
    }

    static boolean areComponentsEnabled() {
        return componentsEnabled;
    }
    
    static void ExitApp() {
    	enableComponents(false);
    	SpeakService.doStop();
    	System.exit(0);
    }

    @Override public void onCreate() {
        Lt.d("TtsApp created");
        myApplication = this;
        myPackageManager = getPackageManager();
        myPackageName = getPackageName();
        try {
            versionName = myPackageManager.getPackageInfo(myPackageName, 0).versionName;
            versionCode = myPackageManager.getPackageInfo(myPackageName, 0).versionCode;
            Lt.d("- version = " + versionName + " (" + versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
            ;
        }

        startService(new Intent(this, SpeakService.class));
    }

    static Context getContext() { return myApplication; }
}