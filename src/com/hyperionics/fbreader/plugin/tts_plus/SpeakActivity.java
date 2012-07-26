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
import android.view.*;
import android.widget.*;
import org.geometerplus.android.fbreader.api.ApiException;

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

public class SpeakActivity extends Activity implements TextToSpeech.OnInitListener {

    private static ArrayList<String> myVoices = new ArrayList<String>();
    private static SpeakActivity currentSpeakActivity;
    private static boolean isActivated = false;
    private static final String NO_RESTART_TALK = "NO_RESTART_TALK";
    private static boolean startTalkAtOnce = true;
    private static boolean currentlyVisible = false;
    private static volatile PowerManager.WakeLock myWakeLock;
    static boolean wantStarted = false;
    static SpeakActivity getCurrent() { return currentSpeakActivity; }

    private int myMaxVolume;
    private int savedBottomMargin = -1;

    private void setListener(int id, View.OnClickListener listener) {
		findViewById(id).setOnClickListener(listener);
	}

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Lt.d("SpeakActivity onCreate()");
        if (!SpeakService.doStartup()) {
            currentSpeakActivity = null;
            if (SpeakService.myApi != null) {
                SpeakService.myApi.disconnect();
                SpeakService.myApi = null;
            }
            // this is asynchronous
            wantStarted = true;
            startService(new Intent(TtsApp.getContext(), SpeakService.class));
            finish();
            return;
        }
        SpeakService.haveNewApi = 1;
        savedBottomMargin = -1;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        startTalkAtOnce = (getIntent().getIntExtra(NO_RESTART_TALK, 0) != 1);
        setContentView(R.layout.control_panel);
        currentSpeakActivity = this;

        myMaxVolume = SpeakService.mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

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
                selectLanguage();
                //SpeakService.myPreferences.edit().putString("lang", SpeakService.selectedLanguage).commit();
            }
        });
        setListener(R.id.button_more, new View.OnClickListener() {
            public void onClick(View v) {
                SpeakService.stopTalking();
                Intent in = new Intent(TtsApp.getContext(), SettingsActivity.class);
                startActivityForResult(in, 2);
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

    static void adjustBottomMargin() {
        // Calculate the extra bottom margin needed for navigation buttons
        try {
            if (currentSpeakActivity.savedBottomMargin < 0)
                currentSpeakActivity.savedBottomMargin = SpeakService.myApi.getBottomMargin();
            Lt.d("savedBottomMargin = " + currentSpeakActivity.savedBottomMargin);
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
    }

    static void restoreBottomMargin() {
        if (currentSpeakActivity != null && SpeakService.haveNewApi > 0 && currentSpeakActivity.savedBottomMargin > -1) {
            try {
                SpeakService.myApi.setBottomMargin(currentSpeakActivity.savedBottomMargin);
                SpeakService.myApi.setPageStart(SpeakService.myApi.getPageStart());
                currentSpeakActivity.savedBottomMargin = -1;
            } catch (ApiException e) {
                ;
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
            SpeakService.myTTS.setPitch((pitchControl.getProgress() + 25f) / 100f);
            SpeakService.myTTS.setOnUtteranceCompletedListener(SpeakService.getCurrentService());
            adjustBottomMargin();
            SpeakService.restorePosition();
            currentSpeakActivity.setActionsEnabled(true);
            if (SpeakService.setLanguage(null) && startTalkAtOnce)
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
        // This was used for testing crash reports.
//        final SeekBar speedControl = (SeekBar)findViewById(777);
//        speedControl.setMax(200);

        try {
            SpeakService.myInitializationStatus &= ~SpeakService.TTS_INITIALIZED;
            Intent in = new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            String speakEng = Settings.Secure.getString(getContentResolver(), Settings.Secure.TTS_DEFAULT_SYNTH);
            if (speakEng != null) {
                in = in.setPackage(speakEng);
                //in = in.setClassName(speakEng, speakEng + ".CheckVoiceData");
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
        else if (requestCode == 2) { // SettingsActivity returned
            SpeakService.setSleepTimer(resultCode);
        }
	}

	@Override protected void onResume() {
        super.onResume();
        currentSpeakActivity = this;
        currentlyVisible = true;
        adjustBottomMargin();
        SpeakService.mAudioManager.registerMediaButtonEventReceiver(SpeakService.componentName);
	}

	@Override protected void onPause() { // pre-HONEYCOMB be prepared to die after this exits
        super.onPause();
        currentlyVisible = false;
        if (isFinishing()) {
            SpeakService.switchOff();
            restoreBottomMargin();
            TtsApp.enableComponents(false);
            sendBroadcast(new Intent(SpeakService.TTSP_KILL));
        }
	}

    @Override protected void onStart() {
        super.onStart();
    }

    @Override protected void onStop() { // HONEYCOMB and up: be prepared to die after this exits
        restoreBottomMargin();
        super.onStop();
    }

    static boolean isVisible() {
        return currentlyVisible;
    }

    void doDestroy() {
        restoreBottomMargin();
        if (!isActivated)
            return;
        isActivated = false;
        SpeakService.switchOff();
        currentSpeakActivity = null;
    }

	@Override protected void onDestroy() {
        restoreBottomMargin();
        if (isFinishing())
            doDestroy();
        currentSpeakActivity = null;
        super.onDestroy();
        unbindDrawables(findViewById(R.id.RootView));
        System.gc();
    }

    private void unbindDrawables(View view) {
        if (view == null)
            return;
        if (view.getBackground() != null) {
            view.getBackground().setCallback(null);
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                unbindDrawables(((ViewGroup) view).getChildAt(i));
            }
            ((ViewGroup) view).removeAllViews();
        }
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

    public void selectLanguage() {
        SpeakService.stopTalking();
        AlertDialog mySetup;

        int checkedItem = 0;
        String curBkLang = "";
        int bookLangKnown = 1;
        try {
            curBkLang = SpeakService.myApi.getBookLanguage();
        } catch (Exception e) {}
        if (curBkLang.equals("")) {
            curBkLang += getText(R.string.unknown);
            bookLangKnown = 0;
        } else
            curBkLang = (new Locale(curBkLang)).getDisplayName();
        final CharSequence[] items = new CharSequence[myVoices.size()+1+bookLangKnown];
        if (bookLangKnown == 1)
            items[0] = getText(R.string.book_language) + " (" + curBkLang + ")";

        for (int i = 0; i < myVoices.size(); i++ ) {
            String s = myVoices.get(i);
            int n = s.indexOf("-");
            String lang, country = "";
            if (n > 0) {
                lang = s.substring(0, n);
                country = s.substring(n+1);
            }
            else {
                lang = s;
            }
            String currentSystemLang = (n == 2 ? Locale.getDefault().getLanguage() : Locale.getDefault().getISO3Language());
            if (bookLangKnown == 1) {
                if (SpeakService.selectedLanguage.equals(s))
                    checkedItem = i+1;
            } else if (currentSystemLang.equals(lang)) {
                checkedItem = i;
            }
            items[i+bookLangKnown] = (new Locale(lang, country)).getDisplayName();
        }
        items[myVoices.size()+bookLangKnown] = getString(R.string.add_language);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.choose_language);
        builder.setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                String curBkLang = "";
                int bookLangKnown = 1;
                try {
                    curBkLang = SpeakService.myApi.getBookLanguage();
                } catch (Exception e) {}
                if (curBkLang.equals("")) {
                    bookLangKnown = 0;
                }
                if (bookLangKnown == 1 && item == 0)
                    SpeakService.selectedLanguage = SpeakService.BOOK_LANG;
                else if (item < myVoices.size() + bookLangKnown)
                    SpeakService.selectedLanguage = myVoices.get(item - bookLangKnown);
                dialog.cancel();
                if (item == myVoices.size()+bookLangKnown) {
                    try {
                        startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
                    } catch (ActivityNotFoundException e) {
                        showErrorMessage(R.string.no_tts_installed);
                    }
                    SpeakActivity.getCurrent().doDestroy();
                    TtsApp.ExitApp();
                }
            }
        });
        mySetup = builder.create();
        mySetup.show();
        return;
    }
}
