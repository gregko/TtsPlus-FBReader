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

public class IncomingReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(SpeakService.SVC_STARTED)) {
            Lt.d("GOT THE SVC_STARTED INTENT");
            if (SpeakActivity.wantStarted) {
                SpeakActivity.wantStarted = false;
                Intent in = new Intent(TtsApp.getContext(), SpeakActivity.class);
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                TtsApp.getContext().startActivity(in);
            }
        }
        else if (intent.getAction().equals(SpeakService.TTSP_KILL)) {
            Lt.d("GOT THE TTSP_KILL INTENT");
            TtsApp.ExitApp();
        }
    }
}
