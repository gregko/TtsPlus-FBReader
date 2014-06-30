package com.hyperionics.fbreader.plugin.tts_plus;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.*;
import android.widget.*;
import com.hyperionics.TtsSetup.*;
import org.geometerplus.android.fbreader.api.ApiException;
import org.geometerplus.android.fbreader.api.TextPosition;

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
    private static boolean currentlyVisible = false;
    private static volatile PowerManager.WakeLock myWakeLock;
    static final int LANG_SEL_REQUEST = 101;
    static final int AVAR_DEF_PATH_REQUEST = 102;
    static final int GET_TTS_VOICES = 107;
    static boolean wantStarted = false;
    static SpeakActivity getCurrent() { return currentSpeakActivity; }
    static String avarDefaultPath = null;

    int myMaxVolume;
    private int savedBottomMargin = -1;
    private int hidePromo = 0;
    private final int promoMaxPress = 3;

    private void setListener(int id, View.OnClickListener listener) {
		findViewById(id).setOnClickListener(listener);
	}

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SpeakService.doStartup()) {
            currentSpeakActivity = null;
            return;
        }

        SpeakService.haveNewApi = 1;
        savedBottomMargin = -1;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.control_panel);
        currentSpeakActivity = this;
        hidePromo = SpeakService.getPrefs().getInt("hidePromo", 0);
        if (hidePromo < promoMaxPress) {
            PackageManager pm = getPackageManager();
            try {
                pm.getPackageInfo("com.hyperionics.avar", PackageManager.GET_META_DATA);
                hidePromo = promoMaxPress;
                SpeakService.getPrefs().edit().putInt("hidePromo", hidePromo).commit();
            } catch (PackageManager.NameNotFoundException e) {}
        }

        myMaxVolume = SpeakService.mAudioManager.getStreamMaxVolume(SpeakService.audioStream);

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
                SpeakService.stopTalking();
                if (Build.VERSION.SDK_INT >= 14) {
                    if (SpeakService.myTTS != null) {
                        TtsWrapper.shutdownTts(SpeakService.myTTS);
                        SpeakService.myTTS = null;
                        SpeakService.myInitializationStatus &= ~SpeakService.TTS_INITIALIZED;
                    }
                    VoiceSelector.resetSelector();
                    Intent intent = new Intent(currentSpeakActivity, VoiceSelector.class);
                    String lang = LangSupport.getIso3Lang(new Locale(SpeakService.getCurrentBookLanguage()));
                    intent.putExtra(VoiceSelector.INIT_LANG, lang);
                    intent.putExtra(VoiceSelector.CONFIG_DIR, SpeakService.getConfigPath());
                    startActivityForResult(intent, LANG_SEL_REQUEST);
                } else {
                    selectLanguage(true);
                }
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
                SharedPreferences.Editor myEditor = SpeakService.getPrefs().edit();
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
                ComponentName cn = new ComponentName("com.hyperionics.fbreader.plugin.tts_plus",
                        "com.hyperionics.fbreader.plugin.tts_plus.InfoActivity");
                getPackageManager().setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
                Intent intent = new Intent(SpeakActivity.this, InfoActivity.class);
                intent.putExtra("showAbout", true);
                startActivity(intent);
            }
        });
        setListener(R.id.button_setup, new View.OnClickListener() {
            public void onClick(View v) {
                View vs = findViewById(R.id.sliders);
                View v2 = findViewById(R.id.bigButtons);
                View v3 = findViewById(R.id.promo);
                View vw = findViewById(R.id.read_words);
                ImageButton vb = (ImageButton) findViewById(R.id.button_setup);
                SharedPreferences.Editor myEditor = SpeakService.getPrefs().edit();
                if (vs.isShown()) {
                    vb.setImageDrawable(getResources().getDrawable(R.drawable.setup_show));
                    vs.setVisibility(View.GONE);
                    v2.setVisibility(View.GONE);
                    v3.setVisibility(View.GONE);
                    vw.setVisibility(View.GONE);
                    adjustBottomMargin();
                    myEditor.putBoolean("HIDE_PREFS", true);
                } else {
                    vb.setImageDrawable(getResources().getDrawable(R.drawable.setup_hide));
                    vs.setVisibility(View.VISIBLE);
                    v2.setVisibility(View.VISIBLE);
                    boolean words = SpeakService.getPrefs().getBoolean("WORD_OPTS", false);
                    v3.setVisibility(words || hidePromo>= promoMaxPress ? View.GONE : View.VISIBLE);
                    vw.setVisibility(words ? View.VISIBLE : View.GONE);
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
        setListener(R.id.promo, new View.OnClickListener() {
            public void onClick(View v) {
                SpeakService.getPrefs().edit().putInt("hidePromo", ++hidePromo).commit();
                SpeakService.stopTalking();
                try {
                    if (InstallInfo.installedFromAma()) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("amzn://apps/android?p=com.hyperionics.avar")));
                    } else {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=com.hyperionics.avar")));
                    }
                } catch (android.content.ActivityNotFoundException anfe) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("http://play.google.com/store/apps/details?id=com.hyperionics.avar")));
                }
//                Uri uriUrl = Uri.parse("http://hyperionics.com/atVoice/index.asp");
//                Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
//                startActivity(launchBrowser);
            }
        });

        ((CheckBox)findViewById(R.id.single_words)).setChecked(SpeakService.getPrefs().getBoolean("SINGLE_WORDS", false));
        setListener(R.id.single_words, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences.Editor myEditor = SpeakService.getPrefs().edit();
                myEditor.putBoolean("SINGLE_WORDS", ((CheckBox) view).isChecked());
                myEditor.commit();
                SpeakService.stopTalking();
                SpeakService.switchReadMode();
            }
        });
        ((CheckBox)findViewById(R.id.pause_words)).setChecked(SpeakService.getPrefs().getBoolean("PAUSE_WORDS", false));
        setListener(R.id.pause_words, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences.Editor myEditor = SpeakService.getPrefs().edit();
                myEditor.putBoolean("PAUSE_WORDS", ((CheckBox) view).isChecked());
                myEditor.commit();
                SpeakService.wordPauses = SpeakService.getPrefs().getBoolean("WORD_OPTS", false) &&
                        SpeakService.getPrefs().getBoolean("SINGLE_WORDS", false) &&
                        SpeakService.getPrefs().getBoolean("PAUSE_WORDS", false);
            }
        });

        final SeekBar speedControl = (SeekBar)findViewById(R.id.speed_control);
		speedControl.setMax(300);
		speedControl.setProgress(SpeakService.getPrefs().getInt("rate", 100));
		speedControl.setEnabled(false);
		speedControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private SharedPreferences.Editor myEditor = SpeakService.getPrefs().edit();

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
        pitchControl.setProgress(SpeakService.getPrefs().getInt("pitch", 75));
        pitchControl.setEnabled(false);
        pitchControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private SharedPreferences.Editor myEditor = SpeakService.getPrefs().edit();

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
        int vol = SpeakService.mAudioManager.getStreamVolume(SpeakService.audioStream);
        volumeControl.setMax(myMaxVolume);
        volumeControl.setProgress(vol);
        volumeControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    SpeakService.mAudioManager.setStreamVolume(SpeakService.audioStream, progress, 0);
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
        if (SpeakService.getPrefs().getBoolean("HIDE_PREFS", false)) {
            ImageButton vb = (ImageButton) findViewById(R.id.button_setup);
            vb.setImageDrawable(getResources().getDrawable(R.drawable.setup_show));
            findViewById(R.id.sliders).setVisibility(View.GONE);
            findViewById(R.id.bigButtons).setVisibility(View.GONE);
            findViewById(R.id.promo).setVisibility(View.GONE);
        } else if (hidePromo>=promoMaxPress) {
            findViewById(R.id.promo).setVisibility(View.GONE);
        }
        findViewById(R.id.read_words).setVisibility(View.GONE);

        avarDefaultPath = SpeakService.getPrefs().getString("avarDefaultPath", null);
        try {
            Intent intent = new Intent();
            intent.setAction("com.hyperionics.avar.GET_DEFAULT_PATH");
            startActivityForResult(intent, AVAR_DEF_PATH_REQUEST);
        } catch (Exception e) {
            Lt.d("Exception: " + e);
            // is @Voice installed?
            if (avarDefaultPath == null) try {
                getPackageManager().getPackageInfo("com.hyperionics.avar", 0);
                // We have an old version of @Voice installed. Try at least the default location
                avarDefaultPath = Environment.getExternalStorageDirectory() + "/Hyperionics/atVoice";
            } catch (PackageManager.NameNotFoundException en) {}
            if (avarDefaultPath != null && !new File(avarDefaultPath + "/.config").isDirectory()) {
                SpeakService.getPrefs().edit().remove("avarDefaultPath").commit();
                avarDefaultPath = null;
            }
        }
        // call cacheContextNative() again, sometimes it does not work if called too early in the system restart process
        CldWrapper.initNativeLib(TtsApp.getContext());
        doStartTts();
        isActivated = true;
	}

    // implements TextToSpeech.OnInitListener
    public void onInit(int status) {
        if (SpeakService.myInitializationStatus != SpeakService.FULLY_INITIALIZED) {
            if (status == TextToSpeech.SUCCESS) {
                SpeakService.myInitializationStatus |= SpeakService.TTS_INITIALIZED;
            }
            if (SpeakService.myInitializationStatus == SpeakService.FULLY_INITIALIZED) {
                onInitializationCompleted();
            }
        }
    }

    static void adjustBottomMargin() {
        // Calculate the extra bottom margin needed for navigation buttons
        if (currentSpeakActivity != null) {
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
                Lt.df("ApiException " + e);
                e.printStackTrace();
                SpeakService.haveNewApi = 0;
            }
        }
    }

    static void restoreBottomMargin() {
        if (currentSpeakActivity != null && SpeakService.haveNewApi > 0 && currentSpeakActivity.savedBottomMargin > -1) {
            try {
                SpeakService.myApi.setBottomMargin(currentSpeakActivity.savedBottomMargin);
                SpeakService.myApi.setPageStart(SpeakService.myApi.getPageStart());
                currentSpeakActivity.savedBottomMargin = -1;
            } catch (Exception e) {
                ;
            }
        }
    }

    static void onInitializationCompleted() {
        onInitializationCompleted(0);
    }

    private static void onInitializationCompleted(final int count) {
        if (count < 9) {
            try {
                SpeakService.myBookHash = "BP:" + SpeakService.myApi.getBookHash();
            } catch (Exception ae) {
                // FBReader may not be fully initialized yet, try again after a while...
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (SpeakActivity.getCurrent() != null) {
                            SpeakActivity.getCurrent().runOnUiThread(new Runnable() {
                                public void run() {
                                    onInitializationCompleted(count + 1);
                                }
                            });
                        }
                    }
                }, 500);
                return;
            }
        }

        TtsApp.enableComponents(true);
        try {
            //SpeakService.myBookHash = "BP:" + SpeakService.myApi.getBookHash();
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
            SpeakService.startTalking();
        } catch (Exception e) {
//            if (SpeakService.myTTS == null)
//                ErrorReporter.getInstance().putCustomData("myTTS_null", "Yes");
//            if (SpeakService.myApi == null)
//                ErrorReporter.getInstance().putCustomData("myApi_null", "Yes");
            Lt.df("Exception in onInitializationCompleted(): " + e);
            e.printStackTrace();
    		if (SpeakService.myTTS != null) {
                TtsWrapper.shutdownTts(SpeakService.myTTS);
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
            if (currentSpeakActivity != null) {
                currentSpeakActivity.setActionsEnabled(false);
//                ErrorReporter.getInstance().handleException(e);
                currentSpeakActivity.finish();
            }
            else {
//                ErrorReporter.getInstance().putCustomData("currentSpeakActivity_null", "Yes");
//                ErrorReporter.getInstance().handleException(e);
                TtsApp.ExitApp();
            }
        }
    }

    void selectTtsEngine() {
        if (Build.VERSION.SDK_INT < 14)
            return;

        new LangSupport(this).setOnEngineSelected(new LangSupport.OnEngineSelected() {
            @Override
            public void engSelected(String engPackageName) {
                try {
                    if (SpeakService.myTTS != null) {
                        TtsWrapper.shutdownTts(SpeakService.myTTS);
                        SpeakService.myTTS = null;
                    }
                } catch (Exception e) {}
                doStartTts();
            }
        }).selectTtsEngine();
    }

    void doStartTts() {
        try {
            SpeakService.myInitializationStatus &= ~SpeakService.TTS_INITIALIZED;
            SpeakService.myTTS = TtsWrapper.createTts(SpeakService.getCurrentService(), this, LangSupport.getSelectedTtsEng());

        } catch (ActivityNotFoundException e) {
            currentSpeakActivity.showErrorMessage(R.string.no_tts_installed);
        }
    }

	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS ||
                resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL || // some engines fail here, yet work correctly...
                resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_MISSING_DATA) { // Google TTS fails on jellybean
                if (SpeakService.myTTS != null) {
                    TtsWrapper.shutdownTts(SpeakService.myTTS);
                    SpeakService.myTTS = null;
                }
                SpeakService.myTTS = TtsWrapper.createTts(this, this, null);
                // The line below gets voices for the "default action" speech engine...
                if (data != null)
                    myVoices = data.getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);
            } else {
                try {
                    Intent intent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    intent.setPackage(LangSupport.getSelectedTtsEng());
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    showErrorMessage(R.string.no_tts_installed);
                }
            }
        }
        else if (requestCode == 2) { // SettingsActivity returned
            SpeakService.setSleepTimer(resultCode);
        }
        else if (requestCode == GET_TTS_VOICES && data != null) {
            myVoices = data.getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);
            if (myVoices == null) {
                Lt.alert(this, R.string.no_voices);
                return;
            }
            selectLanguage(false);
        }
        else if (requestCode == LANG_SEL_REQUEST) {
            // On successful return the engine is already set as default in LangSupport
            String lang = data == null ? null : data.getStringExtra(VoiceSelector.SELECTED_VOICE);
            if (lang != null) {
                SpeakService.selectedLanguage = lang;
                SpeakService.savePosition();
            }
            try {
                if (SpeakService.myTTS != null) {
                    TtsWrapper.shutdownTts(SpeakService.myTTS);
                    SpeakService.myTTS = null;
                }
            } catch (Exception e) {}
            doStartTts();
        }
        else if (requestCode == AVAR_DEF_PATH_REQUEST && data != null) {
            avarDefaultPath = data.getStringExtra("DEFAULT_PATH");
            if (avarDefaultPath == null)
                SpeakService.getPrefs().edit().remove("avarDefaultPath").commit();
            else
                SpeakService.getPrefs().edit().putString("avarDefaultPath", avarDefaultPath).commit();
            Lt.d("Got avarDefaultPath = " + avarDefaultPath);
        }
        super.onActivityResult(requestCode, resultCode, data);
	}

	@TargetApi(Build.VERSION_CODES.FROYO)
    @Override protected void onResume() {
        super.onResume();
        currentSpeakActivity = this;
        currentlyVisible = true;
        if (!TtsApp.isNativeOk()) {
            AlertDialog.Builder bld = new AlertDialog.Builder(this);
            String message =
                    "Wrong version of TTS+ Plugin is installed on this device. " +
                            "Please uninstall it, then install from Google Play, Amazon App Store " +
                            "or Hyperionics web site.\n\n" +
                            "Technical information (please provide when contacting us):\n" +
                            "VersionCode = " + TtsApp.versionCode + "\n" +
                            "Build.CPU_ABI = " + Build.CPU_ABI + ", " + Build.CPU_ABI2 + "\n" +
                            "MODEL = " + android.os.Build.MODEL + "\n" +
                            "Android version = " + Build.VERSION.RELEASE;
            bld.setMessage(message);
            bld.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    currentSpeakActivity.finish();
                    System.exit(0);
                }
            });
            bld.create().show();
            return;
        }
        if (!SpeakService.getPrefs().getBoolean("HIDE_PREFS", false)) {
            View v3 = findViewById(R.id.promo);
            View vw = findViewById(R.id.read_words);
            boolean words = SpeakService.getPrefs().getBoolean("WORD_OPTS", false);
            if (v3 != null)
                v3.setVisibility(words || hidePromo>=promoMaxPress ? View.GONE : View.VISIBLE);
            if (vw != null)
                vw.setVisibility(words ? View.VISIBLE : View.GONE);
            SpeakService.wordPauses = SpeakService.getPrefs().getBoolean("WORD_OPTS", false) &&
                    SpeakService.getPrefs().getBoolean("SINGLE_WORDS", false) &&
                    SpeakService.getPrefs().getBoolean("PAUSE_WORDS", false);
            SpeakService.switchReadMode();
        }
        TtsApp.enableComponents(true);
        if (SpeakService.mAudioManager != null && SpeakService.componentName != null)
            SpeakService.mAudioManager.registerMediaButtonEventReceiver(SpeakService.componentName);
	}

	@Override protected void onPause() { // pre-HONEYCOMB be prepared to die after this exits
        super.onPause();
        currentlyVisible = false;
        if (isFinishing()) {
            Lt.d("SpeakActivity is finishing.");
            restoreBottomMargin();
            SpeakService.switchOff();
        } else if (!SpeakService.isTalking()) {
            TtsApp.enableComponents(false);
        }
	}

    @Override public void onConfigurationChanged (Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    static boolean isVisible() {
        return currentlyVisible;
    }

    void doDestroy() {
        restoreBottomMargin();
        if (isActivated) {
            isActivated = false;
            SpeakService.switchOff();
        }
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

    static void setVolumeProgress() {
        if (currentSpeakActivity != null && currentlyVisible) {
            final SeekBar volumeControl = (SeekBar)currentSpeakActivity.findViewById(R.id.volume_control);
            if (volumeControl != null) {
                int vol = SpeakService.mAudioManager.getStreamVolume(SpeakService.audioStream);
                volumeControl.setProgress(vol);
            }
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
        if (currentSpeakActivity != null) try {
            currentSpeakActivity.findViewById(R.id.button_play).setVisibility(active ? View.GONE : View.VISIBLE);
            currentSpeakActivity.findViewById(R.id.button_pause).setVisibility(active ? View.VISIBLE : View.GONE);
        } catch (Exception e) {}

		if (active) {
			if (myWakeLock == null && currentSpeakActivity != null) {
                int wakeLevel = PowerManager.PARTIAL_WAKE_LOCK;
                int n = SpeakService.getPrefs().getInt("screenOn", 0);
                if (n > 0 && currentSpeakActivity != null) {
                    currentSpeakActivity.runOnUiThread(new Runnable() {
                        public void run() {
                            if (currentSpeakActivity != null)
                                currentSpeakActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        }
                    });
                }
				myWakeLock = ((PowerManager)currentSpeakActivity.getSystemService(POWER_SERVICE))
						.newWakeLock(wakeLevel, "FBReader TTS+ plugin");
				myWakeLock.acquire();
			}
		} else {
			if (myWakeLock != null) {
				myWakeLock.release();
				myWakeLock = null;
			}
            if (currentSpeakActivity != null) {
                currentSpeakActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        if (currentSpeakActivity != null)
                            currentSpeakActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                });
            }
		}
	}

    public void selectLanguage(boolean tryCheckTtsData) {
        // Now this function is called only for SDK_INT < 14
        SpeakService.stopTalking();
        //myVoices = null; // test crash
        if (tryCheckTtsData) {
            Intent in = new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            String defTtsEng = Settings.Secure.getString(getContentResolver(), Settings.Secure.TTS_DEFAULT_SYNTH);
            if (defTtsEng != null) {
                in = in.setPackage(defTtsEng); // here setting package does have an effect.
            }
            try {
                currentSpeakActivity.startActivityForResult(in, GET_TTS_VOICES); // goes to onActivityResult()
            } catch (ActivityNotFoundException e) {
                showErrorMessage(R.string.no_tts_installed);
            }
            return;
        }
        int checkedItem = 0;
        String curBkLang = "";
        int bookLangKnown = 1;
        try {
            curBkLang = SpeakService.myApi.getBookLanguage();
        } catch (Exception e) {}
        if (curBkLang == null || curBkLang.equals("")) {
            curBkLang = "" + getText(R.string.unknown);
            bookLangKnown = 0;
        } else
            curBkLang = (new Locale(curBkLang)).getDisplayName();
        final CharSequence[] items = new CharSequence[myVoices.size()+1+bookLangKnown];
        if (bookLangKnown == 1)
            items[0] = getText(R.string.book_language) + " (" + curBkLang + ")";

        for (int i = 0; i < myVoices.size(); i++ ) {
            String s = myVoices.get(i);
            int n = s.indexOf("-");
            String lang;
            if (n > 0) {
                lang = s.substring(0, n);
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
            items[i+bookLangKnown] = LangSupport.localeFromString(s).getDisplayName(); // (new Locale(lang, country)).getDisplayName();
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
                if (curBkLang == null || curBkLang.equals("")) {
                    bookLangKnown = 0;
                }
                if (bookLangKnown == 1 && item == 0)
                    SpeakService.selectedLanguage = SpeakService.BOOK_LANG;
                else if (item < myVoices.size() + bookLangKnown)
                    SpeakService.selectedLanguage = myVoices.get(item - bookLangKnown);
                dialog.cancel();
                if (item == myVoices.size()+bookLangKnown) {
                    try {
                        Intent intent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                        intent.setPackage(LangSupport.getSelectedTtsEng());
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        showErrorMessage(R.string.no_tts_installed);
                    }
                    SpeakActivity.getCurrent().doDestroy();
                    TtsApp.ExitApp();
                }
            }
        });
        builder.create().show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) { // BUG in API 11 and above
        outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
        super.onSaveInstanceState(outState);
    }
}
