package com.hyperionics.fbreader.plugin.tts_plus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

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

public class MediaButtonIntentReceiver extends BroadcastReceiver {

    public MediaButtonIntentReceiver() {
        super();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String intentAction = intent.getAction();

        if (!Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
            return;
        }
        KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (event == null) {
            return;
        }
        int keycode = event.getKeyCode();

        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            switch (keycode) {
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    SpeakService.stopTalking();
                    break;
                case KeyEvent.KEYCODE_HEADSETHOOK:
                    if (SpeakService.myPreferences.getBoolean("wiredKey", false)) {
                        Lt.d("Bluetooth media button: " + keycode);
                        SpeakService.toggleTalking();
                    }
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case 127: // KeyEvent.KEYCODE_MEDIA_PAUSE: - not available under Gingerbread API
                case 126: // KeyEvent.KEYCODE_MEDIA_PLAY:
                    // Note: works much better without distinguishing between KEYCODE_MEDIA_PAUSE and
                    // KEYCODE_MEDIA_PAUSE on ICS!
                    Lt.d("Bluetooth media button: " + keycode);
                    SpeakService.toggleTalking();
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    SpeakService.nextToSpeak();
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    SpeakService.prevToSpeak();
                    break;
                default:
                    break;
            }
        }
    }
}
