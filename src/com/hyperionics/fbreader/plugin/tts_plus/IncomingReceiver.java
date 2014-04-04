package com.hyperionics.fbreader.plugin.tts_plus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.hyperionics.TtsSetup.Lt;
import org.geometerplus.android.fbreader.api.ApiException;
import org.geometerplus.android.fbreader.api.TextPosition;

import java.util.Timer;
import java.util.TimerTask;

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
            if (SpeakService.myApi == null) {
                Lt.d("- myApi is null");
            } else
                Lt.d(SpeakService.myApi.isConnected() ? "- FBReader connected" : "- FBReader NOT connected");
            if (SpeakActivity.wantStarted) {
                Lt.d("Trying to launch FBReader...");
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("org.geometerplus.zlibrary.ui.android");
                if (launchIntent != null) {
                    Lt.d("...calling startActivity()");
                    TtsApp.getContext().startActivity(launchIntent);
                    startSpeakActivityDelayed(0);
                }

            }
        }
        else if (intent.getAction().equals(SpeakService.TTSP_KILL)) {
            Lt.d("GOT THE TTSP_KILL INTENT");
            TtsApp.ExitApp();
        }
    }

    static void startSpeakActivityDelayed(final int count) {
        if (count > 20)
            return;
        Lt.d("startSpeakActivityDelayed() count = " + count);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (SpeakService.myApi != null) {
                    try {
                        TextPosition tp = SpeakService.myApi.getPageStart();
                        Lt.d("- tp = " + tp.ParagraphIndex + ", " + tp.ElementIndex);
                        if (tp.ParagraphIndex == 0 && tp.ElementIndex == 0 && count < 2) {
                            startSpeakActivityDelayed(count + 1);
                            return;
                        }
                    } catch (ApiException e) {
                        Lt.d("startSpeakActivityDelayed(): ApiException");
                        startSpeakActivityDelayed(count + 1);
                        return;
                    }
                } else {
                    Lt.d("startSpeakActivityDelayed(): myApi is null");
                    if (SpeakService.getCurrentService() == null)
                        TtsApp.getContext().startService(new Intent(TtsApp.getContext(), SpeakService.class));
                    startSpeakActivityDelayed(count + 1);
                    return;
                }
                SpeakActivity.wantStarted = false;
                Intent in = new Intent(TtsApp.getContext(), SpeakActivity.class);
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                TtsApp.getContext().startActivity(in);
            }
        }, 500);
    }
}
