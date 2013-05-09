package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import com.hyperionics.util.Lt;
//import org.acra.*;
//import org.acra.annotation.*;


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

//@ReportsCrashes(formKey="dHkxejl0NVFKWndMdE43UlN5Q1VIVHc6MQ", // see: http://code.google.com/p/acra/
//        mode = ReportingInteractionMode.NOTIFICATION,
//        resToastText = R.string.crash_toast_text, // optional, displayed as soon as the crash occurs, before collecting data which can take a few seconds
//        resNotifTickerText = R.string.crash_notif_ticker_text,
//        resNotifTitle = R.string.crash_notif_title,
//        resNotifText = R.string.crash_notif_text,
//        resNotifIcon = android.R.drawable.stat_notify_error, // optional. default is a warning sign
//        resDialogText = R.string.crash_dialog_text,
//        resDialogIcon = android.R.drawable.ic_dialog_info, //optional. default is a warning sign
//        resDialogTitle = R.string.crash_dialog_title, // optional. default is your application name
//        resDialogCommentPrompt = R.string.crash_dialog_comment_prompt, // optional. when defined, adds a user text field input with this text resource as a label
//        resDialogOkToast = R.string.crash_dialog_ok_toast // optional. displays a Toast message when the user accepts to send a report.
//)
public class TtsApp extends Application
{
    private static TtsApp myApplication;
    private static boolean componentsEnabled = false;
    private static boolean myIsDebug = true;
    private static HeadsetPlugReceiver headsetPlugReceiver = null;

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
            if (headsetPlugReceiver == null) {
                headsetPlugReceiver = new HeadsetPlugReceiver();
                myApplication.registerReceiver(headsetPlugReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
                myApplication.registerReceiver(headsetPlugReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
            }
        }
        else if (headsetPlugReceiver != null) {
            myApplication.unregisterReceiver(headsetPlugReceiver);
            headsetPlugReceiver = null;
        }

        if (SpeakService.mAudioManager != null && SpeakService.componentName != null) {
            if (enabled) {
                SpeakService.mAudioManager.registerMediaButtonEventReceiver(SpeakService.componentName);
            }
            else {
                SpeakService.mAudioManager.unregisterMediaButtonEventReceiver(SpeakService.componentName);
                SpeakService.mAudioManager.abandonAudioFocus(SpeakService.afChangeListener);
            }
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

    public static boolean isDebug() {
        return myIsDebug;
    }

    public TtsApp() {
        (new GlobalExceptionHandler()).init(this);
    }

    @Override public void onCreate() {
        // The following line triggers the initialization of ACRA
        //ACRA.init(this);
        InstallInfo.init(this);
        myApplication = this;
        myIsDebug = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        myPackageManager = getPackageManager();
        myPackageName = getPackageName();
        Lt.d("TtsApp created");
        startService(new Intent(this, SpeakService.class));
        try {
            versionName = myPackageManager.getPackageInfo(myPackageName, 0).versionName;
            versionCode = myPackageManager.getPackageInfo(myPackageName, 0).versionCode;
            Lt.d("- version = " + versionName + " (" + versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
        }
        super.onCreate();
    }

    static Context getContext() { return myApplication; }
}