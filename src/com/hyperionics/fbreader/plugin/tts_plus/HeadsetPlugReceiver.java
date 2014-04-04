
package com.hyperionics.fbreader.plugin.tts_plus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

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

public class HeadsetPlugReceiver extends BroadcastReceiver {
    private boolean myIgnore1st;

    HeadsetPlugReceiver() {
        myIgnore1st = true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String intentAction = intent.getAction();

        if (intentAction.equals(Intent.ACTION_HEADSET_PLUG))
        {
            if (myIgnore1st) {
                myIgnore1st = false;
                return;
            }
            int headsetState = intent.getIntExtra("state", 0);      //get the headset state property
            if (headsetState == 0 && SpeakService.myIsActive)
                SpeakService.stopTalking();
            else if (headsetState == 1 && !SpeakService.myIsActive &&
                     SpeakService.getPrefs().getBoolean("plugStart", false) &&
                    SpeakService.getPrefs().getBoolean("fbrStart", false))
                SpeakService.toggleTalking();
        }
        else if (intentAction.equals("android.media.VOLUME_CHANGED_ACTION")) {
            SpeakActivity.setVolumeProgress();
        }
    }
}