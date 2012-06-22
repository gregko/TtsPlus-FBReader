package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.provider.MediaStore;

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

public class TtsApp extends Application
{
    private static TtsApp myApplication;
    private static boolean componentsEnabled = false;
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
            headsetPlugReceiver = new HeadsetPlugReceiver();
            myApplication.registerReceiver(headsetPlugReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
            myApplication.registerReceiver(headsetPlugReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
        }
        else if (headsetPlugReceiver != null) {
            myApplication.unregisterReceiver(headsetPlugReceiver);
            headsetPlugReceiver = null;
        }

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
        startService(new Intent(this, SpeakService.class));
        try {
            versionName = myPackageManager.getPackageInfo(myPackageName, 0).versionName;
            versionCode = myPackageManager.getPackageInfo(myPackageName, 0).versionCode;
            Lt.d("- version = " + versionName + " (" + versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
        }
    }

    static Context getContext() { return myApplication; }
}