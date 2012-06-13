
package com.hyperionics.fbreader.plugin.tts_plus;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BluetoothConnectReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String intentAction = intent.getAction();

        if (intentAction.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
            SpeakService.stopTalking();
        }
    }

}