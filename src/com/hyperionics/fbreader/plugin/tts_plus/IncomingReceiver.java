package com.hyperionics.fbreader.plugin.tts_plus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.hyperionics.TtsSetup.Lt;
import org.geometerplus.android.fbreader.api.ApiClientImplementation;
import org.geometerplus.android.fbreader.api.ApiException;
import org.geometerplus.android.fbreader.api.TextPosition;

import java.util.Timer;
import java.util.TimerTask;

/**
 *  Copyright (C) 2012 Hyperionics Technology LLC <http://www.hyperionics.com>
 *
 */

public class IncomingReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(SpeakService.SVC_STARTED)) {
            Lt.d("GOT THE SVC_STARTED INTENT");
            if (SpeakService.myApi == null) {
                Lt.d("- myApi is null");
            } else {
                if (!SpeakService.myApi.isConnected()) {
                    Lt.d("- FBReader NOT connected");
                    SpeakService.myApi = null;
                    SpeakService.getCurrentService().connectToApi(null);
                } else {
                    Lt.d("- FBReader connected");
                }
            }
            if (SpeakActivity.wantStarted) {
                Lt.d("Trying to launch FBReader...");
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("com.fbreader");
                if (launchIntent == null)
                    launchIntent = context.getPackageManager().getLaunchIntentForPackage("org.geometerplus.zlibrary.ui.android");
                if (launchIntent != null) {
                    Lt.d("...calling startActivity()");
                    TtsApp.getContext().startActivity(launchIntent);
                }
            }
            startSpeakActivityDelayed(0);
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
                        Lt.d("startSpeakActivityDelayed(): ApiException " + e);
                        SpeakService.myApi.disconnect();
                        SpeakService.myApi = null;
                        SpeakService.getCurrentService().connectToApi(ApiClientImplementation.FBREADER_PREMIUM_PREFIX);
                        startSpeakActivityDelayed(count + 1);
                        return;
                    }
                } else {
                    Lt.d("startSpeakActivityDelayed(): myApi is null");
                    if (SpeakService.getCurrentService() == null)
                        TtsApp.getContext().startService(new Intent(TtsApp.getContext(), SpeakService.class));
                    SpeakActivity.wantStarted = false;
                }
                Intent in = new Intent(TtsApp.getContext(), SpeakActivity.class);
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                TtsApp.getContext().startActivity(in);
                //startSpeakActivityDelayed(count + 1);
            }
        }, 500);
    }
}
