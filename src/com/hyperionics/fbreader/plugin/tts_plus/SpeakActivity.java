package com.hyperionics.fbreader.plugin.tts_plus;

import java.util.ArrayList;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import org.geometerplus.android.fbreader.api.ApiException;

public class SpeakActivity extends Activity implements TextToSpeech.OnInitListener {

    private static ArrayList<String> myVoices = new ArrayList<String>();
    private static SpeakActivity currentSpeakActivity;
    private static boolean isActivated = false;
    private static final String NO_RESTART_TALK = "NO_RESTART_TALK";
    private static boolean startTalkAtOnce = true;
    private static boolean currentlyVisible = false;

    private static volatile PowerManager.WakeLock myWakeLock;
    private int myMaxVolume;
    private int savedBottomMargin = -1;
    static SpeakActivity getCurrent() { return currentSpeakActivity; }
    AlertDialog mySetup;

    private void setListener(int id, View.OnClickListener listener) {
		findViewById(id).setOnClickListener(listener);
	}

    @Override protected void onCreate(Bundle savedInstanceState) {
        SpeakService.haveNewApi = 1;
        SpeakService.doStartup();
        savedBottomMargin = -1;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        startTalkAtOnce = (getIntent().getIntExtra(NO_RESTART_TALK, 0) != 1);
        setContentView(R.layout.control_panel);
        currentSpeakActivity = this;

        myMaxVolume = SpeakService.mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        ((CheckBox)findViewById(R.id.highlight_sentences)).setChecked(SpeakService.myHighlightSentences);

		setListener(R.id.button_previous, new View.OnClickListener() {
			public void onClick(View v) {
                SpeakService.prevToSpeak();
			}
		});
		setListener(R.id.button_next, new View.OnClickListener() {
			public void onClick(View v) {
                SpeakService.nextToSpeak();
			}
		});
        setListener(R.id.button_close, new View.OnClickListener() {
            public void onClick(View v) {
                SpeakService.stopTalking();
                doDestroy();
                finish();
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
                doDestroy();
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
                tv.setText(getString(R.string.version) + " " + TtsApp.versionName);
                builder.setView(view);
                builder.setPositiveButton(R.string.rate, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=com.hyperionics.fbreader.plugin.tts_plus"));
                        startActivity(browserIntent);                    }
                });
                builder.setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
                AlertDialog alert = builder.create();
                alert.show();
            }
        });
        setListener(R.id.highlight_sentences, new View.OnClickListener() {
            private SharedPreferences.Editor myEditor = SpeakService.myPreferences.edit();
            public void onClick(View v) {
                SpeakService.stopTalking();
                SpeakService.mySentences = new TtsSentenceExtractor.SentenceIndex[0];
                SpeakService.myHighlightSentences = ((CheckBox) v).isChecked();
                myEditor.putBoolean("hiSentences", SpeakService.myHighlightSentences);
                myEditor.commit();
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

        final EditText paraPauseEdit = (EditText)findViewById(R.id.paraPause);
        paraPauseEdit.setText(Integer.toString(SpeakService.myParaPause));
        paraPauseEdit.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                int pp;
                try {
                    pp = Integer.parseInt(s.toString());
                } catch (NumberFormatException e) {
                    pp = 0;
                }
                SpeakService.myParaPause = pp;
                if (pp < 0)
                    pp = 0;
                else if (pp > 5000)
                    pp = 5000;
                if (pp != SpeakService.myParaPause) {
                    paraPauseEdit.setText(Integer.toString(pp));
                }
                else {
                    SharedPreferences.Editor editor = SpeakService.myPreferences.edit();
                    editor.putInt("paraPause", pp);
                    editor.commit();
                }
                SpeakService.myParaPause = pp;
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
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
        int vol = SpeakService.mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        volumeControl.setMax(myMaxVolume);
        volumeControl.setProgress(vol);
        volumeControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    SpeakService.mAudioManager.setStreamVolume(SpeakService.mAudioManager.STREAM_MUSIC, progress, 0);
                }
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        getWindow().setGravity(Gravity.BOTTOM);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.dimAmount = 0f;
        getWindow().setAttributes(lp);
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
        TtsApp.enableComponents(true);
        try {
            SpeakService.myParagraphIndex = SpeakService.myApi.getPageStart().ParagraphIndex;
            SpeakService.myParagraphsNumber = SpeakService.myApi.getParagraphsNumber();

            final SeekBar speedControl = (SeekBar)currentSpeakActivity.findViewById(R.id.speed_control);
            speedControl.setEnabled(true);
            SpeakService.setSpeechRate(speedControl.getProgress());

            final SeekBar pitchControl = (SeekBar)currentSpeakActivity.findViewById(R.id.pitch_control);
            pitchControl.setEnabled(true);
            SpeakService.setLanguage(SpeakService.selectedLanguage);
            SpeakService.myTTS.setPitch((pitchControl.getProgress() + 25f) / 100f);
            SpeakService.myTTS.setOnUtteranceCompletedListener(SpeakService.getCurrentService());

            // Calculate the extra bottom margin needed for navigation buttons
            try {
                currentSpeakActivity.savedBottomMargin = SpeakService.myApi.getBottomMargin();
                Rect rectf = new Rect();
                View v = currentSpeakActivity.findViewById(R.id.nav_buttons);
                v.getLocalVisibleRect(rectf);
                int d = rectf.bottom;
                d += d/5 + currentSpeakActivity.savedBottomMargin;
                if (currentSpeakActivity.savedBottomMargin < d) {
                    SpeakService.myApi.setBottomMargin(d);
                    SpeakService.myApi.setPageStart(SpeakService.myApi.getPageStart());
                }
            } catch (ApiException e) {
                SpeakService.haveNewApi = 0;
            }
            SpeakService.restorePosition();
            currentSpeakActivity.setActionsEnabled(true);
            if (startTalkAtOnce)
                SpeakService.startTalking();
        } catch (ApiException e) {
            Lt.df("Init error: " + (SpeakService.myApi == null ? "myApi=null" :
                (SpeakService.myApi.isConnected() ? "myApi connected" : "myApi NOT connected"))
            		);
        
	        Lt.df("- myTTS is " + (SpeakService.myTTS == null ? "null" : "NOT null"));
	        currentSpeakActivity.setActionsEnabled(false);
    		if (SpeakService.myTTS != null) {
		        try {
	       			SpeakService.myTTS.shutdown();
	        	} catch (Exception e2) {
	        	}
    		}
    		if (SpeakService.myApi != null) {
    			try {
    				SpeakService.myApi.disconnect();
    			} catch (Exception e3) {
    			}
    		}
        	SpeakService.myApi = null;
        	SpeakService.myTTS = null;
        	SpeakService.myInitializationStatus = 0;
        	currentSpeakActivity.finish();
        }
    }

    void doStartTts() {
        try {
            SpeakService.myInitializationStatus &= ~SpeakService.TTS_INITIALIZED;
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
            currentSpeakActivity.startActivityForResult(in, 1); // goes to onActivityResult() below
        } catch (ActivityNotFoundException e) {
            currentSpeakActivity.showErrorMessage(R.string.no_tts_installed);
        }
    }

	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (myWakeLock != null) {
                myWakeLock.release();
                myWakeLock = null;
            }
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS ||
                resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL) { // some engines fail here, yet work correctly...
                if (SpeakService.myTTS != null) {
                    try {
                        SpeakService.myTTS.shutdown();
                    } catch (Exception e) {
                    }
                    SpeakService.myTTS = null;
                }
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

	@Override protected void onResume() {
        currentSpeakActivity = this;
        SpeakService.mAudioManager.registerMediaButtonEventReceiver(SpeakService.componentName);
        super.onResume();
	}

	@Override protected void onPause() {
        if (isFinishing()) {
            TtsApp.enableComponents(false);
        }
		super.onPause();
	}

    @Override protected void onStart() {
        currentlyVisible = true;
        super.onStart();
    }

    @Override protected void onStop() {
        currentlyVisible = false;
        super.onStop();
    }

    static boolean isVisible() {
        return currentlyVisible;
    }

    private void doDestroy() {
        if (!isActivated)
            return;
        isActivated = false;
        if (savedBottomMargin > -1) {
            try {
                SpeakService.myApi.setBottomMargin(savedBottomMargin);
                SpeakService.myApi.setPageStart(SpeakService.myApi.getPageStart());
            } catch (ApiException e) {
                ;
            }
        }
        SpeakService.switchOff();
        currentSpeakActivity = null;
    }

	@Override protected void onDestroy() {
        if (isFinishing())
            doDestroy();
        currentSpeakActivity = null;
        super.onDestroy();
	}

	private void setActionsEnabled(final boolean enabled) {
        // again trouble if it's done through runOnUiThread()
        findViewById(R.id.button_previous).setEnabled(enabled);
        findViewById(R.id.button_next).setEnabled(enabled);
        findViewById(R.id.button_play).setEnabled(enabled);
        findViewById(R.id.button_setup).setEnabled(enabled);
	}

    static void SetVolumeProgress() {
        if (currentSpeakActivity != null && currentlyVisible) {
            final SeekBar volumeControl = (SeekBar)currentSpeakActivity.findViewById(R.id.volume_control);
            int vol = SpeakService.mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            volumeControl.setProgress(vol);
        }
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
