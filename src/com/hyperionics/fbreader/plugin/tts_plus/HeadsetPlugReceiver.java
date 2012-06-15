
package com.hyperionics.fbreader.plugin.tts_plus;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

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
            else if (headsetState == 1 && !SpeakService.myIsActive)
                SpeakService.startTalking();
        }
        else if (intentAction.equals("android.media.VOLUME_CHANGED_ACTION")) {
            SpeakActivity.SetVolumeProgress();
        }
    }
}