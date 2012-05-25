
package com.hyperionics.fbreader.plugin.tts_plus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

// See: http://stackoverflow.com/questions/6468463/start-activity-inside-onreceive-broadcastreceiver
// on how to start activity from a receiver...
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
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case 127: // KeyEvent.KEYCODE_MEDIA_PAUSE: - not available under Gingerbread API
                case 126: // KeyEvent.KEYCODE_MEDIA_PLAY:
                    Lt.d("Bluetooth media button: " + keycode);
                    if (!SpeakActivity.isInitialized() && SpeakApp.isFBReaderOnTop()) {
                        SpeakActivity.startActivity(context);
                    } else {
                        SpeakService.toggleTalking();
                    }
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    SpeakService.nextParagraph();
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    SpeakService.prevParagraph();
                    break;
                default:
                    break;
            }
        }
        abortBroadcast();
    }
}
