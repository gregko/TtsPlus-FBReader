package com.hyperionics.fbreader.plugin.tts_plus;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.RemoteControlClient.MetadataEditor;
import android.os.Build;
import com.hyperionics.TtsSetup.Lt;

import java.util.List;

public class LockscreenManager {

	private RemoteControlClient _remoteControlClient;

	@TargetApi(14)
	private void setupLockscreenControls(Context context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			return;

		AudioManager audioManager = SpeakService.mAudioManager;
        if (audioManager == null) {
            setLockscreenStopped();
            _remoteControlClient = null;
            return;
        }
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.setComponent(new ComponentName(context, MediaButtonIntentReceiver.class));

        PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        // create and register the remote control client
        _remoteControlClient = new RemoteControlClient(mediaPendingIntent);
        audioManager.registerRemoteControlClient(_remoteControlClient);

		// android built-in lockscreen only supports play/pause/playpause/stop, previous, and next
		_remoteControlClient.setTransportControlFlags(
				RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
				| RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS
				| RemoteControlClient.FLAG_KEY_MEDIA_NEXT
                // | RemoteControlClient.FLAG_KEY_MEDIA_POSITION_UPDATE // API 18 and up, don't know how to show position...
               );

		try {
			final int METADATA_KEY_ARTWORK = 100;
			// Update the remote controls
			MetadataEditor metadataEditor = _remoteControlClient
					.editMetadata(true)
					//.putString(MediaMetadataRetriever.METADATA_KEY_AUTHOR, "author...")
					.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, SpeakService.myApi.getBookTitle())
					//.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, 20000)
                    ;
            try {
                Bitmap artwork = BitmapFactory.decodeResource(context.getResources(), R.drawable.open_book_read);
                metadataEditor.putBitmap(METADATA_KEY_ARTWORK, artwork);
            } catch (OutOfMemoryError ex) {
            } catch (Exception e) {}
			metadataEditor.apply();
		} catch (Exception e) {
			Lt.d("Updating lockscreen exception: " + e.toString());
		}

        _remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
	}

	@TargetApi(14)
	public void setLockscreenStopped() {
		if (_remoteControlClient != null) {
			_remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
            if (SpeakService.mAudioManager != null)
                SpeakService.mAudioManager.unregisterRemoteControlClient(_remoteControlClient);
            _remoteControlClient = null;
        }
	}

	@TargetApi(14)
	public void setLockscreenPaused() {
		if (_remoteControlClient == null)
			return;
		_remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
	}

	@TargetApi(14)
	public void setLockscreenPlaying() {
        if (SpeakService.mAudioManager != null && _remoteControlClient != null)
            SpeakService.mAudioManager.unregisterRemoteControlClient(_remoteControlClient);
        setupLockscreenControls(TtsApp.getContext());
	}

}
