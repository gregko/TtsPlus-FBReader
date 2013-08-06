
package com.hyperionics.fbreader.plugin.tts_plus;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
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
    private int retryCount = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        String intentAction = intent.getAction();

        if (SpeakService.isTalking() && intentAction.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
            SpeakService.stopTalking();
        }
        else if (intentAction.equals(BluetoothDevice.ACTION_ACL_CONNECTED) &&
                !SpeakService.isTalking() &&
                SpeakService.getPrefs().getBoolean("plugStart", false)) {
            retryCount = 0;
            mHandler.postDelayed(myTimerTask, 500);
        }
    }

    private Runnable myTimerTask = new Runnable() {
        @TargetApi(Build.VERSION_CODES.FROYO)
        public void run() {
            AudioManager am = SpeakService.mAudioManager;
            if(am != null &&
                    (am.isBluetoothA2dpOn() || am.isBluetoothScoAvailableOffCall() && am.isBluetoothScoOn())) {
                SpeakService.toggleTalking();
            } else if (++retryCount < 10) {
                mHandler.postDelayed(myTimerTask, 500);
            } else {
                SpeakService.toggleTalking();
            }
        }
    };

}