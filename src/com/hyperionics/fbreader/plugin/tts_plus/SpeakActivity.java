
package com.hyperionics.fbreader.plugin.tts_plus;

import java.util.ArrayList;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
//import android.telephony.PhoneStateListener;
//import android.telephony.TelephonyManager;
import android.view.*;
import android.widget.*;

import org.geometerplus.android.fbreader.api.*;

public class SpeakActivity extends Activity implements TextToSpeech.OnInitListener {

    private static ArrayList<String> myVoices = new ArrayList<String>();
    private static SpeakActivity currentSpeakActivity;
    private static boolean isActivated = false;
    private static final String NO_RESTART_TALK = "NO_RESTART_TALK";
    private static boolean startTalkAtOnce = true;

    private static volatile PowerManager.WakeLock myWakeLock;
    private int myMaxVolume;
    static SpeakActivity getCurrent() { return currentSpeakActivity; }

    private void setListener(int id, View.OnClickListener listener) {
		findViewById(id).setOnClickListener(listener);
	}

    AlertDialog mySetup;

    static boolean isInitialized() { return isActivated; }

    @Override
	protected void onCreate(Bundle savedInstanceState) {

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        startTalkAtOnce = (getIntent().getIntExtra(NO_RESTART_TALK, 0) != 1);
        setContentView(R.layout.control_panel);
        currentSpeakActivity = this;
        startService(new Intent(this, SpeakService.class));

        myMaxVolume = SpeakService.mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        ((CheckBox)findViewById(R.id.read_sentences)).setChecked(SpeakService.myReadSentences);

		setListener(R.id.button_previous_paragraph, new View.OnClickListener() {
			public void onClick(View v) {
				SpeakService.stopTalking();
				SpeakService.gotoPreviousParagraph();
			}
		});
		setListener(R.id.button_next_paragraph, new View.OnClickListener() {
			public void onClick(View v) {
				SpeakService.stopTalking();
				if (SpeakService.myParagraphIndex < SpeakService.myParagraphsNumber) {
					++SpeakService.myParagraphIndex;
                    SpeakService.gotoNextParagraph();
				}
			}
		});
        setListener(R.id.button_close, new View.OnClickListener() {
            public void onClick(View v) {
                SpeakService.switchOff();
                currentSpeakActivity.finish();
            }
        });
        setListener(R.id.button_lang, new View.OnClickListener() {
            public void onClick(View v) {
                doSetup();
            }
        });
        setListener(R.id.button_reset, new View.OnClickListener() {
            public void onClick(View v) {
                boolean wasActive = SpeakService.myIsActive;
                SpeakService.stopTalking();
                SharedPreferences.Editor myEditor = SpeakService.myPreferences.edit();
                SeekBar speedControl = (SeekBar)findViewById(R.id.speed_control);
                SeekBar pitchControl = (SeekBar)findViewById(R.id.pitch_control);
                myEditor.putInt("rate", 100);
                myEditor.putInt("pitch", 75);
                myEditor.commit();
                speedControl.setProgress(100);
                pitchControl.setProgress(75);
                SpeakService.setSpeechRate(100);
                SpeakService.setPitch(1f);
                if (wasActive)
                    SpeakService.startTalking();
            }
        });
        setListener(R.id.button_tts_set, new View.OnClickListener() {
            public void onClick(View v) {
                SpeakService.stopTalking();
                Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                SpeakService.switchOff();
                finish();
            }
        });
        setListener(R.id.button_about, new View.OnClickListener() {
            public void onClick(View v) {
                SpeakService.stopTalking();
                AlertDialog.Builder builder = new AlertDialog.Builder(SpeakActivity.this);
                LayoutInflater inflater = LayoutInflater.from(SpeakActivity.this);
                View view = inflater.inflate(R.layout.about_panel, null);
                TextView tv = (TextView) view.findViewById(R.id.vtext);
                tv.setText(getString(R.string.version) + " " + SpeakApp.versionName);
//                tv = (TextView) view.findViewById(R.id.pgstart);
//                try {
//                    TextPosition tp = SpeakService.myApi.getPageStart();
//                    tv.setText("Page start: " + tp.ParagraphIndex + " (" + SpeakService.myApi.getElementsNumber(tp.ParagraphIndex) + ")");
//                } catch (ApiException e) {
//                }
                builder.setView(view);
                builder.setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
                AlertDialog alert = builder.create();
                alert.show();
            }
        });
        setListener(R.id.read_sentences, new View.OnClickListener() {
            private SharedPreferences.Editor myEditor = SpeakService.myPreferences.edit();
            public void onClick(View v) {
                SpeakService.stopTalking();
                SpeakService.mySentences = new String[0];
                SpeakService.myReadSentences = ((CheckBox) v).isChecked();
                myEditor.putBoolean("readSentences", SpeakService.myReadSentences);
                myEditor.commit();
            }
        });
        setListener(R.id.button_tts_set, new View.OnClickListener() {
            public void onClick(View v) {
                SpeakService.stopTalking();
                Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                SpeakService.switchOff();
                finish();
            }
        });
        setListener(R.id.button_setup, new View.OnClickListener() {
            public void onClick(View v) {
                View vs = findViewById(R.id.sliders);
                View v2 = findViewById(R.id.bigButtons);
                ImageButton vb = (ImageButton) findViewById(R.id.button_setup);
                SharedPreferences.Editor myEditor = SpeakService.myPreferences.edit();
                if (vs.isShown()) {
                    vb.setImageDrawable(getResources().getDrawable(R.drawable.setup_show));
                    vs.setVisibility(View.GONE);
                    v2.setVisibility(View.GONE);
                    myEditor.putBoolean("HIDE_PREFS", true);
                } else {
                    vb.setImageDrawable(getResources().getDrawable(R.drawable.setup_hide));
                    vs.setVisibility(View.VISIBLE);
                    v2.setVisibility(View.VISIBLE);
                    myEditor.putBoolean("HIDE_PREFS", false);
                }
                myEditor.commit();
            }
        });
        setListener(R.id.button_pause, new View.OnClickListener() {
            public void onClick(View v) {
                SpeakService.stopTalking();
            }
        });
		setListener(R.id.button_play, new View.OnClickListener() {
            public void onClick(View v) {
                SpeakService.startTalking();
            }
        });

        final SeekBar speedControl = (SeekBar)findViewById(R.id.speed_control);
		speedControl.setMax(200);
		speedControl.setProgress(SpeakService.myPreferences.getInt("rate", 100));
		speedControl.setEnabled(false);
		speedControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private SharedPreferences.Editor myEditor = SpeakService.myPreferences.edit();

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && SpeakService.myTTS != null) {
                    if (!SpeakService.myWasActive)
                        SpeakService.myWasActive = SpeakService.myIsActive;
                    SpeakService.stopTalking();
                    SpeakService.setSpeechRate(progress);
                    myEditor.putInt("rate", progress);
                }
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                myEditor.commit();
                if (SpeakService.myWasActive) {
                    SpeakService.myWasActive = false;
                    SpeakService.startTalking();
                }
            }
        });

        final SeekBar pitchControl = (SeekBar)findViewById(R.id.pitch_control);
        pitchControl.setMax(200);
        pitchControl.setProgress(SpeakService.myPreferences.getInt("pitch", 75));
        pitchControl.setEnabled(false);
        pitchControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private SharedPreferences.Editor myEditor = SpeakService.myPreferences.edit();

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && SpeakService.myTTS != null) {
                    if (!SpeakService.myWasActive)
                        SpeakService.myWasActive = SpeakService.myIsActive;
                    SpeakService.stopTalking();
                    SpeakService.myTTS.setPitch((progress + 25f) / 100f);
                    myEditor.putInt("pitch", progress);
                }
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                myEditor.commit();
                if (SpeakService.myWasActive) {
                    SpeakService.myWasActive = false;
                    SpeakService.startTalking();
                }
            }
        });

        final SeekBar volumeControl = (SeekBar)findViewById(R.id.volume_control);
        volumeControl.setMax(100);
        int vol = SpeakService.myPreferences.getInt("volume", 100);
        volumeControl.setProgress(vol);
        SpeakService.mAudioManager.setStreamVolume(SpeakService.mAudioManager.STREAM_MUSIC, myMaxVolume * vol / 100, 0);
        volumeControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private SharedPreferences.Editor myEditor = SpeakService.myPreferences.edit();

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    SpeakService.mAudioManager.setStreamVolume(SpeakService.mAudioManager.STREAM_MUSIC, myMaxVolume * progress / 100, 0);
                    myEditor.putInt("volume", progress);
                }
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                myEditor.commit();
            }
        });

        // The code below needs:
        // 	<uses-permission android:name="android.permission.READ_PHONE_STATE"/>
        // it does not seem to be necessary, the OnAudioFocusChangeListener() callback
        // handles breaking through of phone calls fine.
//        ((TelephonyManager)getSystemService(TELEPHONY_SERVICE)).listen(
//                new PhoneStateListener() {
//                    public void onCallStateChanged(int state, String incomingNumber) {
//                        if (state == TelephonyManager.CALL_STATE_RINGING) {
//                            SpeakService.myWasActive = SpeakService.myIsActive;
//                            SpeakService.stopTalking();
//                        }
//                    }
//                },
//                PhoneStateListener.LISTEN_CALL_STATE
//        );

        getWindow().setGravity(Gravity.BOTTOM);
		setActive(false);
		setActionsEnabled(false);
        if (SpeakService.myPreferences.getBoolean("HIDE_PREFS", false)) {
            ImageButton vb = (ImageButton) findViewById(R.id.button_setup);
            vb.setImageDrawable(getResources().getDrawable(R.drawable.setup_show));
            findViewById(R.id.sliders).setVisibility(View.GONE);
            findViewById(R.id.bigButtons).setVisibility(View.GONE);
        }

        doStartTts();
        isActivated = true;
	}

    // implements TextToSpeech.OnInitListener
    public void onInit(int status) {
        if (SpeakService.myInitializationStatus != SpeakService.FULLY_INITIALIZED) {
            if (status == TextToSpeech.SUCCESS)
                SpeakService.myInitializationStatus |= SpeakService.TTS_INITIALIZED;
            if (SpeakService.myInitializationStatus == SpeakService.FULLY_INITIALIZED) {
                onInitializationCompleted();
            }
        }
    }

    static void onInitializationCompleted() {
        SpeakService.myTTS.setOnUtteranceCompletedListener(SpeakService.currentService);
        SpeakService.setLanguage(SpeakService.selectedLanguage);

        if (currentSpeakActivity != null) {
            try {
                final SeekBar speedControl = (SeekBar)currentSpeakActivity.findViewById(R.id.speed_control);
                speedControl.setEnabled(true);
                SpeakService.setSpeechRate(speedControl.getProgress());

                final SeekBar pitchControl = (SeekBar)currentSpeakActivity.findViewById(R.id.pitch_control);
                pitchControl.setEnabled(true);
                SpeakService.myTTS.setPitch((pitchControl.getProgress()+25f)/100f);

                SpeakService.myParagraphIndex = SpeakService.myApi.getPageStart().ParagraphIndex;
                SpeakService.myParagraphsNumber = SpeakService.myApi.getParagraphsNumber();
                currentSpeakActivity.setActionsEnabled(true);
                if (startTalkAtOnce)
                    SpeakService.startTalking();
            } catch (ApiException e) {
                currentSpeakActivity.setActionsEnabled(false);
                SpeakActivity.showErrorMessage(R.string.initialization_error);
                e.printStackTrace();
            }
        }
    }

    void doStartTts() {
        try {
            Intent in = new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            String speakEng = Settings.Secure.getString(getContentResolver(), Settings.Secure.TTS_DEFAULT_SYNTH);
            if (speakEng != null) {
                in = in.setClassName(speakEng, speakEng + ".CheckVoiceData");
            }
            // WakeLock is needed, else we don't get to onActivityResult if the screen is dark...
            myWakeLock =
                    ((PowerManager)getSystemService(POWER_SERVICE))
                            .newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                                    "FBReader TTS+ plugin");
            myWakeLock.acquire();
            currentSpeakActivity.startActivityForResult(in, 1);
        } catch (ActivityNotFoundException e) {
            currentSpeakActivity.showErrorMessage(R.string.no_tts_installed);
        }
    }

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (myWakeLock != null) {
                myWakeLock.release();
                myWakeLock = null;
            }
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS ||
                resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL) { // some engines fail here, yet work correctly...
                SpeakService.myTTS = new TextToSpeech(this, this);
                // The line below gets voices for the "default action" speech engine...
                if (data != null)
                    myVoices = data.getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);
            } else {
                try {
                    startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
                } catch (ActivityNotFoundException e) {
                    showErrorMessage(R.string.no_tts_installed);
                }
            }
        }
	}

	@Override
	protected void onResume() {
        SpeakService.mAudioManager.registerMediaButtonEventReceiver(SpeakService.componentName);
        super.onResume();
	}

	@Override
	protected void onPause() {
        if (isFinishing()) {
            isActivated = false;
        } else {
            SpeakService.regainBluetoothFocus();
        }
		super.onPause();
	}

    @Override
    protected void onStop() {
        super.onStop();
    }

	@Override
	protected void onDestroy() {
        if (isFinishing()) {
            isActivated = false;
            SpeakService.switchOff();
        }
        currentSpeakActivity = null;
        super.onDestroy();
	}

	private void setActionsEnabled(final boolean enabled) {
        // again trouble if it's done through runOnUiThread()
        findViewById(R.id.button_previous_paragraph).setEnabled(enabled);
        findViewById(R.id.button_next_paragraph).setEnabled(enabled);
        findViewById(R.id.button_play).setEnabled(enabled);
        findViewById(R.id.button_setup).setEnabled(enabled);
	}

    static void startActivity(Context context) {
        if (currentSpeakActivity != null)
            currentSpeakActivity.finish();
        Intent i = new Intent();
        i.setClassName("com.hyperionics.fbreader.plugin.tts_plus", "com.hyperionics.fbreader.plugin.tts_plus.SpeakActivity");
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

    static void restartActivity(Context context) {
        boolean wasActive = SpeakService.myIsActive;
        if (currentSpeakActivity != null)
            currentSpeakActivity.finish();
        Intent i = new Intent();
        i.setClassName("com.hyperionics.fbreader.plugin.tts_plus", "com.hyperionics.fbreader.plugin.tts_plus.SpeakActivity");
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!wasActive)
            i.putExtra(NO_RESTART_TALK, 1);
        context.startActivity(i);
    }

    static void showErrorMessage(int textId) {
        if (currentSpeakActivity == null)
            return;
        final CharSequence text = currentSpeakActivity.getText(textId);
        currentSpeakActivity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(currentSpeakActivity, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    static void showErrorMessage(final CharSequence text) {
        if (currentSpeakActivity == null)
            return;
        currentSpeakActivity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(currentSpeakActivity, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    static synchronized void setActive(final boolean active) {
		SpeakService.myIsActive = active;

        // This must be done in the same thread for Bluetooth controls to work correctly
        if (currentSpeakActivity != null) {
            currentSpeakActivity.findViewById(R.id.button_play).setVisibility(active ? View.GONE : View.VISIBLE);
            currentSpeakActivity.findViewById(R.id.button_pause).setVisibility(active ? View.VISIBLE : View.GONE);
        }

		if (active) {
			if (myWakeLock == null && currentSpeakActivity != null) {
				myWakeLock =
					((PowerManager)currentSpeakActivity.getSystemService(POWER_SERVICE))
						.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FBReader TTS+ plugin");
				myWakeLock.acquire();
			}
		} else {
			if (myWakeLock != null) {
				myWakeLock.release();
				myWakeLock = null;
			}
		}
	}

    private void doSetup() {
        SpeakService.stopTalking();
        if (mySetup == null) {
            final CharSequence[] items = new CharSequence[myVoices.size()+1];
            int checkedItem = 0;
            String s = getText(R.string.book_language) + " (";
            try {
                items[0] = s + (new Locale(SpeakService.myApi.getBookLanguage())).getDisplayName() + ")";
            } catch (Exception e) {
                items[0] = s + getText(R.string.unknown) + ")";
            }
            for (int i = 0; i < myVoices.size(); i++ ) {
                s = myVoices.get(i);
                if (SpeakService.selectedLanguage.equals(s))
                    checkedItem = i+1;
                int n = s.indexOf("-");
                String lang, country = "";
                if (n > 0) {
                    lang = s.substring(0, n);
                    country = s.substring(n+1);
                }
                else {
                    lang = s;
                }
                items[i+1] = (new Locale(lang, country)).getDisplayName();

            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.choose_language);
            builder.setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
                    switch (item) {
                        case 0:
                            SpeakService.selectedLanguage = SpeakService.BOOK_LANG;
                            break;
                        default:
                            SpeakService.selectedLanguage = myVoices.get(item-1);
                            break;
                    }
                    dialog.cancel();
                    SpeakService.myPreferences.edit().putString("lang", SpeakService.selectedLanguage).commit();
                    SpeakService.setLanguage(SpeakService.selectedLanguage);
                    SpeakService.startTalking();
                }
            });
            mySetup = builder.create();
        }
        mySetup.show();
        return;
    }
}
