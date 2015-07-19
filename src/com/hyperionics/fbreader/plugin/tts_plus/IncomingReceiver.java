package com.hyperionics.fbreader.plugin.tts_plus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import com.hyperionics.TtsSetup.Lt;
import org.geometerplus.android.fbreader.api.ApiClientImplementation;
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
        String action = intent.getAction();
        if (action.equals(SpeakService.SVC_STARTED) || action.equals(SpeakService.API_CONNECTED)) {
            boolean startActivity = !InfoActivity.isShowing();
            Lt.d("GOT THE INTENT: " + action);
            if (SpeakActivity.wantFBReaderStarted) {
                SpeakActivity.wantFBReaderStarted = false;
                Lt.d("Trying to launch FBReader...");
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("com.fbreader");
                if (launchIntent == null)
                    launchIntent = context.getPackageManager().getLaunchIntentForPackage("org.geometerplus.zlibrary.ui.android");
                if (launchIntent != null) {
                    Lt.d("...calling startActivity()");
                    TtsApp.getContext().startActivity(launchIntent);
                }
            }
            if (SpeakService.myApi == null) {
                Lt.d("- myApi is null");
                if (SpeakService.getCurrentService() == null) {
                    Lt.d("- current service is null too.");
                    return;
                }
                SpeakService.getCurrentService().connectToApi(null);
                startActivity = false;
            } else {
                if (!SpeakService.myApi.isConnected()) {
                    Lt.d("- FBReader NOT connected");
//                    SpeakService.myApi = null;
//                    SpeakService.getCurrentService().connectToApi(null);
                    startActivity = false;
                } else {
                    Lt.d("- FBReader connected");
                }
            }
            if (startActivity)
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
                if (SpeakService.getCurrentService() != null && SpeakService.myApi != null) {
                    try {
                        TextPosition tp = SpeakService.myApi.getPageStart();
                        Lt.d("- tp = " + tp.ParagraphIndex + ", " + tp.ElementIndex);
                        if (tp.ParagraphIndex == 0 && tp.ElementIndex == 0 && count < 2) {
                            startSpeakActivityDelayed(count + 1);
                            return;
                        }
                    } catch (Exception e) {
                        Lt.d("startSpeakActivityDelayed(): ApiException " + e);
                        e.printStackTrace();
                        if (SpeakService.myApi != null) try {
                            SpeakService.myApi.disconnect();
                        } catch (Exception eIgnore) {}
                        SpeakService.myApi = null;
                        PackageManager pm = TtsApp.getContext().getPackageManager();
                        boolean hasPremium = pm.getLaunchIntentForPackage(InfoActivity.FBR_PACKAGE_PREMIUM) != null;
                        if (SpeakService.getCurrentService() == null)
                            return;
                        SpeakService.getCurrentService().connectToApi(hasPremium ?
                                ApiClientImplementation.FBREADER_PREMIUM_PREFIX : ApiClientImplementation.FBREADER_PREFIX);
                        startSpeakActivityDelayed(count + 1);
                        return;
                    }
                } else {
                    Lt.d("startSpeakActivityDelayed(): myApi is null");
                    if (SpeakService.getCurrentService() == null)
                        TtsApp.getContext().startService(new Intent(TtsApp.getContext(), SpeakService.class));
                    SpeakActivity.wantFBReaderStarted = false;
                }
                Intent in = new Intent(TtsApp.getContext(), SpeakActivity.class);
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                TtsApp.getContext().startActivity(in);
                //startSpeakActivityDelayed(count + 1);
            }
        }, count == 0 ? 100 : 1000);
    }
}
