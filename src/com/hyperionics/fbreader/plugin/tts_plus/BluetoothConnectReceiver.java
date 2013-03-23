
package com.hyperionics.fbreader.plugin.tts_plus;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

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

public class BluetoothConnectReceiver extends BroadcastReceiver {
    private Handler mHandler = new Handler();

    @Override
    public void onReceive(Context context, Intent intent) {
        String intentAction = intent.getAction();

        if (intentAction.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
            SpeakService.stopTalking();
        }
        else if (SpeakService.myPreferences.getBoolean("plugStart", false) &&
                intentAction.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
            // make it start instead in about 4 seconds?
            mHandler.postDelayed(myTimerTask, 500); // customize this time in settings? Or test if bluetooth audio connected?
        }
    }

    private Runnable myTimerTask = new Runnable() {
        public void run() {
            if(SpeakService.mAudioManager.isBluetoothA2dpOn()) {
                SpeakService.toggleTalking();
            } else {
                mHandler.postDelayed(myTimerTask, 500); // customize this time in settings? Or test if bluetooth audio connected?
            }
        }
    };

}