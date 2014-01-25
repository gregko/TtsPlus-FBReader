package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.*;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.os.Handler;
import android.text.format.Time;
import com.hyperionics.TtsSetup.CldWrapper;
import com.hyperionics.TtsSetup.LangSupport;
import com.hyperionics.TtsSetup.Lt;
import com.hyperionics.TtsSetup.VoiceSelector;
import org.geometerplus.android.fbreader.api.ApiClientImplementation;
import org.geometerplus.android.fbreader.api.ApiException;
import org.geometerplus.android.fbreader.api.ApiListener;
import org.geometerplus.android.fbreader.api.TextPosition;
//import org.acra.ErrorReporter;

import java.io.File;
import java.util.*;

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

public class SpeakService extends Service implements TextToSpeech.OnUtteranceCompletedListener,
        ApiClientImplementation.ConnectionListener {
    private Handler mHandler = new Handler();
    private static LockscreenManager _lockscreenManager = null;

    static private SpeakService currentService;
    static SpeakService getCurrentService() { return currentService; }

    static ApiClientImplementation myApi;
    static TextToSpeech myTTS;
    static AudioManager mAudioManager;
    static ComponentName componentName;
    private static SharedPreferences myPreferences;

    static final String BOOK_LANG = "book";
    static boolean myHighlightSentences = true;
    static int myParaPause = 300;
    static String selectedLanguage = BOOK_LANG; // either "book" or locale code like "eng-USA"
    static int myParagraphIndex = -1;
    static int myParagraphsNumber;
    static float myCurrentPitch = 1f;
    static int haveNewApi = 1;
    static private boolean isServiceTalking = false;
    static private boolean myHasNetworkTts = false;
    static boolean readingStarted = false;
    static boolean wordPauses = false;

    static private final String UTTERANCE_ID = "FBReaderTTS+Plugin";
    static TtsSentenceExtractor.SentenceIndex mySentences[] = new TtsSentenceExtractor.SentenceIndex[0];
    static private int myCurrentSentence = 0;
    static String myBookHash = null;

    static boolean myIsActive = false;
    static boolean myWasActive = false;
    static private HashMap<String, String> myParamMap;

    static volatile int myInitializationStatus = 0;
    static int API_INITIALIZED = 1;
    static int TTS_INITIALIZED = 2;
    static int FULLY_INITIALIZED = API_INITIALIZED | TTS_INITIALIZED;
    static final String SVC_STARTED = "com.hyperionics.fbreader.plugin.tts_plus.SVC_STARTED";
    static final String TTSP_KILL = "com.hyperionics.fbreader.plugin.tts_plus.TTSP_KILL";

    static String getConfigPath() {
        return getConfigPath(false);
    }

    static int getCurrentSentence() {
        return myCurrentSentence;
    }

    static int getSntLength() {
        return mySentences.length;
    }

    static String getConfigPath(boolean noAvar) {
        if (!noAvar && getPrefs().getBoolean("AVAR_SPEECH", false) && SpeakActivity.avarDefaultPath != null) {
            File cfgDir = new File(SpeakActivity.avarDefaultPath + "/.config");
            if (cfgDir.isDirectory())
                return cfgDir.getAbsolutePath();
        }
        File cfgDir = new File(TtsApp.getContext().getFilesDir().getPath() + "/.config");
        if (cfgDir.isDirectory())
            return cfgDir.getAbsolutePath();
        if (cfgDir.exists())
            cfgDir.delete(); // in case it was a regular file.
        cfgDir.mkdirs();
        return cfgDir.getAbsolutePath();
    }

    static void savePosition() {
        try {
            if (myCurrentSentence < mySentences.length) {
                if (myBookHash == null)
                    myBookHash = "BP:" + myApi.getBookHash();
                SharedPreferences.Editor myEditor = getPrefs().edit();
                Time time = new Time();
                time.setToNow();
                String lang = " l:" + selectedLanguage;
                String eng = LangSupport.getSelectedTtsEng();
                if (eng != null)
                    lang += "|" + eng;
                myEditor.putString(myBookHash, lang +
                        "p:" + myParagraphIndex + " s:" + myCurrentSentence + " e:" + mySentences[myCurrentSentence].i +
                        " d:" + time.format2445()
                );

                myEditor.commit();
            }
        } catch (ApiException e) {
            ;
        }
    }

    static boolean restorePosition() {
        try {
            if (myBookHash == null)
                myBookHash = "BP:" + myApi.getBookHash();
            String s = getPrefs().getString(myBookHash, "");
            int il = s.indexOf("l:");
            int para = s.indexOf("p:");
            int sent = s.indexOf("s:");
            int idx = s.indexOf("e:");
            int dt = s.indexOf("d:");
            if (para > -1 && sent > -1 && idx > -1 && dt > -1) {
                if (il > -1) {
                    selectedLanguage = s.substring(il + 2, para);
                    int n = selectedLanguage.lastIndexOf('|');
                    if (n > 0) {
                        String eng = selectedLanguage.substring(n+1);
                        selectedLanguage = selectedLanguage.substring(0, n);
                        if (eng != null && !eng.equals(LangSupport.getSelectedTtsEng())) {
                            // Speech engine change necessary
                            LangSupport.setSelectedTtsEng(eng);
                            if (myTTS != null) try {
                                myTTS.shutdown();
                            } catch (Exception e) {}
                            myTTS = null;
                            SpeakActivity.getCurrent().doStartTts();
                            return false;
                        }
                    }
                }
                para = Integer.parseInt(s.substring(para + 2, sent-1));
                sent = Integer.parseInt(s.substring(sent + 2, idx - 1));
                idx = Integer.parseInt(s.substring(idx + 2, dt - 1));
                TextPosition tp = new TextPosition(para, idx, 0);
                if (tp.compareTo(myApi.getPageStart()) >= 0 && tp.compareTo(myApi.getPageEnd()) < 0) {
                    myParagraphIndex = para;
                    processCurrentParagraph();
                    myCurrentSentence = sent;
                }
            }
        } catch (ApiException e) {
        }
        return true;
    }

    static void cleanupPositions() {
        // Cleanup - delete any hashes older than 6 months
        try {
            Map<String, ?> prefs = getPrefs().getAll();
            SharedPreferences.Editor myEditor = getPrefs().edit();
            for(Map.Entry<String,?> entry : prefs.entrySet())
            {
                if (entry.getKey().substring(0, 3).equals("BP:")) {
                    String s = entry.getValue().toString();
                    int i = s.indexOf("d:");
                    if (i > -1) {
                        Time time = new Time();
                        time.parse(s.substring(i+2));
                        Time now = new Time();
                        now.setToNow();
                        long days = (now.toMillis(false) - time.toMillis(false))/1000/3600/24;
                        if (days > 182)
                            myEditor.remove(entry.getKey());
                    }
                    else
                        myEditor.remove(entry.getKey());
                }
            }
            myEditor.commit();
        } catch (NullPointerException e) {
            ;
        }
    }

    public void onUtteranceCompleted(String uttId) {
        regainBluetoothFocus();
        isServiceTalking = false;
        if (myIsActive && UTTERANCE_ID.equals(uttId)) {
            if (++myCurrentSentence >= mySentences.length) {
                if (myParaPause > 0 && myCurrentSentence == mySentences.length) {
                    myTTS.playSilence(myParaPause, TextToSpeech.QUEUE_ADD, myParamMap);
                    return;
                }
                ++myParagraphIndex;
                processCurrentParagraph();
                if (myParagraphIndex >= myParagraphsNumber) {
                    stopTalking();
                    return;
                }
            }
            // Highlight the sentence here...
            if (haveNewApi > 0)
                highlightSentence();
            if (wordPauses && SpeakActivity.getCurrent() != null) {
                SpeakActivity.getCurrent().runOnUiThread(new Runnable() {
                    public void run() {
                        stopTalking();
                    }
                });
            } else
                speakString(mySentences[myCurrentSentence].s);

        } else {
            SpeakActivity.setActive(false);
        }
    }

    public static String getCurrentBookLanguage() {
        String languageCode = "";
        try {
            languageCode = selectedLanguage; // language previously selected by the user for this book
            if (languageCode == null || languageCode.equals(BOOK_LANG)) {
                languageCode = myApi.getBookLanguage();
            }
        } catch (Exception e) {}
        if (languageCode == null)
            languageCode = "";
        return languageCode;
    }

    static boolean setLanguage(String languageCode) {
        Locale locale;
        if (languageCode == null) {
            languageCode = getCurrentBookLanguage();
            if (languageCode == null || languageCode.equals("")) {
                languageCode = BOOK_LANG;
            }
        }
        if (languageCode.equals(BOOK_LANG)) { // the language of this book is unknown...
            try {
                languageCode = myApi.getBookLanguage();
            } catch (Exception caughtException) {
                languageCode = "";
            }
            if (languageCode == null || languageCode.equals(""))
                languageCode = Locale.getDefault().getLanguage();

            if (LangSupport.langInstalled(myTTS, languageCode) == null) {
                PowerManager powerManager = (PowerManager) TtsApp.getContext().getSystemService(POWER_SERVICE);
                KeyguardManager kgMgr = (KeyguardManager)  TtsApp.getContext().getSystemService(Context.KEYGUARD_SERVICE);
                if (!powerManager.isScreenOn() || kgMgr.inKeyguardRestrictedInputMode()) {
                    Lt.d("We can't auto-select language with screen off or in keyguard engaged...");
                    return false;
                } else {
                    Lt.d("Screen on and no keyguard...");
                }

                SpeakActivity sa = SpeakActivity.getCurrent();
                if (Build.VERSION.SDK_INT >= 14) {
                    if (SpeakService.myTTS != null) {
                        SpeakService.myTTS.shutdown();
                        SpeakService.myTTS = null;
                        SpeakService.myInitializationStatus &= ~SpeakService.TTS_INITIALIZED;
                    }
                    VoiceSelector.resetSelector();
                    Intent intent = new Intent(sa, VoiceSelector.class);
                    String lang = LangSupport.getIso3Lang(new Locale(SpeakService.getCurrentBookLanguage()));
                    intent.putExtra(VoiceSelector.INIT_LANG, lang);
                    intent.putExtra(VoiceSelector.CONFIG_DIR, SpeakService.getConfigPath());
                    sa.startActivityForResult(intent, SpeakActivity.LANG_SEL_REQUEST);
                    return false;
                } else {
                    sa.selectLanguage(true);
                    return false;
                }
            }
        }

        if (myTTS == null || currentService == null)
            return false;
        locale = LangSupport.langInstalled(myTTS, languageCode);
        if (locale == null)
            return false;
        myTTS.setLanguage(locale);
        return true;
    }

    static void startTalking() {
        if (myTTS == null)
            return;         // implement somehow creation of myTTS?...

        if (!setLanguage(SpeakService.selectedLanguage))
            return;
        SpeakActivity.setActive(true);
        String iso3lang = LangSupport.getIso3Lang(new Locale(SpeakService.getCurrentBookLanguage()));
        CldWrapper.initExtractorNative(getConfigPath(), iso3lang);
        wordPauses = getPrefs().getBoolean("WORD_OPTS", false) &&
                     myPreferences.getBoolean("SINGLE_WORDS", false) &&
                     myPreferences.getBoolean("PAUSE_WORDS", false);
        if (myCurrentSentence >= mySentences.length) {
            processCurrentParagraph();
        }
        if (myCurrentSentence < mySentences.length) {
            if (haveNewApi > 0)
                highlightSentence();
            if (myApi != null && myApi.isConnected()) {
                mAudioManager.requestAudioFocus(afChangeListener,
                        // Use the music stream.
                        AudioManager.STREAM_MUSIC,
                        // Request permanent focus.
                        AudioManager.AUDIOFOCUS_GAIN);
                myHasNetworkTts = false;
                if (Build.VERSION.SDK_INT > 14) try {
                    myParamMap.remove(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS);
                    Set<String> ss = myTTS.getFeatures(myTTS.getLanguage());
                    if (ss != null) {
                        for (String s : ss) {
                            if (s.equals(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS))
                                myHasNetworkTts = true;
                        }
                    }
                } catch (Exception e) {}

                if (_lockscreenManager == null)
                    _lockscreenManager = new LockscreenManager();
                _lockscreenManager.setLockscreenPlaying();

                if (myCurrentSentence < mySentences.length) // re-testing, still got index out of bounds exception...
                    speakString(mySentences[myCurrentSentence].s);
            }
        } else
            stopTalking();
    }

    static boolean isTalking() {
        return isServiceTalking;
    }

    static void stopTalking() {
        SpeakActivity.setActive(false);
        savePosition();
        if (isServiceTalking && myTTS != null) {
            isServiceTalking = false;
            try {
                int i;
                myTTS.stop();
                for (i = 0; i < 10 && SpeakActivity.getCurrent() != null && myTTS.isSpeaking(); i++) {
                    try {
                        synchronized (SpeakActivity.getCurrent()) {
                            SpeakActivity.getCurrent().wait(100);
                        }
                    } catch (InterruptedException e) {}
                }
            } catch (Exception e) {}
        }
        if (mAudioManager != null) {
            // mAudioManager.abandonAudioFocus(afChangeListener);
            regainBluetoothFocus();
        }
        if (_lockscreenManager != null)
            _lockscreenManager.setLockscreenPaused();
    }

    static void toggleTalking() {
        if (SpeakActivity.getCurrent() == null) {
            if (true || readingStarted) {
                if (currentService == null) {
                    TtsApp.getContext().startService(new Intent(TtsApp.getContext(), SpeakService.class));
                } else {
                    Intent i = new Intent(SVC_STARTED);
                    SpeakActivity.wantStarted = true;
                    TtsApp.getContext().sendBroadcast(i);
                }
            }
            else {
                TtsApp.enableComponents(false);
            }
            return;
        }

        if (myIsActive) {
            stopTalking();
        }
        else {
            startTalking();
        }
    }

    static void switchOff() {
        if (currentService == null)
            return;
        stopTalking();
        if (_lockscreenManager != null)
            _lockscreenManager.setLockscreenStopped();
        if (mAudioManager != null)
            mAudioManager.abandonAudioFocus(afChangeListener);
        try {
            currentService.mHandler.removeCallbacks(currentService.myTimerTask);
            mySentences = new TtsSentenceExtractor.SentenceIndex[0];
        } catch (Exception dontCare) {}
        if (myApi != null && myApi.isConnected()) {
            try {
                myApi.clearHighlighting();
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
        try {
            if (SpeakService.myTTS != null) {
                SpeakService.myTTS.shutdown();
                SpeakService.myTTS = null;
            }
        } catch (Exception e) {
        }
        cleanupPositions();
        if (!getPrefs().getBoolean("fbrStart", true)) {
            disconnect();
        }
        myInitializationStatus &= ~TTS_INITIALIZED;
        doStop();
    }

    static void nextToSpeak() {
        if (myTTS == null)
            return;
        boolean wasSpeaking = myTTS.isSpeaking();
        if (wasSpeaking)
            stopTalking();
        if (haveNewApi < 1) {
            if (myParagraphIndex < myParagraphsNumber) {
                ++myParagraphIndex;
                processCurrentParagraph();
                if (wasSpeaking)
                    startTalking();
            }
        }
        else {
            gotoNextSentence();
            if (wasSpeaking)
                startTalking();
        }
    }

    static void prevToSpeak() {
        if (myTTS == null)
            return;
        boolean wasSpeaking = myTTS.isSpeaking()
                || myParagraphIndex >= myParagraphsNumber;
        if (wasSpeaking)
            stopTalking();
        if (haveNewApi < 1) {
            gotoPreviousParagraph();
            highlightParagraph();
        } else
            gotoPreviousSentence();
        if (wasSpeaking)
            startTalking();
    }

    public static int connectionType() { // ret. 0 no connection, 1 mobile only, 2 wifi
        boolean haveConnection = false;
        ConnectivityManager cm = (ConnectivityManager) TtsApp.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] netInfo = cm.getAllNetworkInfo();
        for (NetworkInfo ni : netInfo) {
            if (ni.getTypeName().toLowerCase().startsWith("wifi"))
                if (ni.isConnected())
                    return 2;
            if (!haveConnection)
                haveConnection = ni.isConnected();
        }
        return haveConnection ? 1 : 0;
    }

    private static int speakString(String text) {
        int ret;
        // Stupid Google voice stops on empty or silent sentences, therefore
        // replaceForSpeechNative() will return empty string if there is only punctation and spaces.
        text = CldWrapper.replaceForSpeechNative(text);

        if (text.length() > 0) {
            myParamMap.remove(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS);
            if (myHasNetworkTts) {
                int n = 2;
                try {
                    n = getPrefs().getInt("netSynth", 2); // bit 0 - use net, bit 1 - wifi only
                } catch (ClassCastException e) {
                    SharedPreferences.Editor ed = getPrefs().edit();
                    ed.remove("netSynth");
                    ed.commit();
                }
                int conn = connectionType();
                boolean useNet = (n & 1) == 1;
                boolean wifiOnly = (n & 2) == 2;
                if (useNet && (conn == 2 || conn == 1 && !wifiOnly)) {
                    myParamMap.put(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "true");
                }
            }
            ret = myTTS.speak(text, TextToSpeech.QUEUE_ADD, myParamMap);
            isServiceTalking = ret == TextToSpeech.SUCCESS;
        } else {
            ret = myTTS.playSilence(20, TextToSpeech.QUEUE_ADD, myParamMap); // to call utteranceCompleted() on TTS thread...
        }
        return ret;
    }

    static void setSpeechRate(int progress) {
        if (myTTS != null) {
            myTTS.setSpeechRate((float)Math.pow(2.0, (progress - 100.0) / 75));
        }
    }

    static void setPitch(float pitch) {
        if (myTTS != null) {
            myCurrentPitch = pitch;
            myTTS.setPitch(pitch);
        }
    }

    private static void setPitchTemp(float pitch) {
        if (myTTS != null) {
            myTTS.setPitch(pitch);
        }
    }

    private static void highlightSentence() {
        try {
            int endEI = myCurrentSentence < mySentences.length-1 ?
                            mySentences[myCurrentSentence+1].i-1: Integer.MAX_VALUE;
            TextPosition stPos;
            if (myCurrentSentence <= 0) {
                myCurrentSentence = 0;
                stPos = new TextPosition(myParagraphIndex, 0, 0);
            } else
                stPos = new TextPosition(myParagraphIndex, mySentences[myCurrentSentence].i, 0);
            TextPosition edPos = new TextPosition(myParagraphIndex, endEI, 0);
            if (stPos.compareTo(myApi.getPageStart()) < 0 || edPos.compareTo(myApi.getPageEnd()) > 0)
                myApi.setPageStart(stPos);
            if (myHighlightSentences)
                myApi.highlightArea(stPos, edPos);
            else
                myApi.clearHighlighting();
        } catch (ApiException e) {
            switchOff();
            TtsApp.ExitApp();
        }
    }

    private static void highlightParagraph() {
        try {
            TextPosition stPos = new TextPosition(myParagraphIndex, 0, 0);
            TextPosition edPos = new TextPosition(myParagraphIndex, Integer.MAX_VALUE, 0);
            if (stPos.compareTo(myApi.getPageStart()) < 0 || edPos.compareTo(myApi.getPageEnd()) > 0)
                myApi.setPageStart(stPos);
            if (myHighlightSentences && 0 <= myParagraphIndex && myParagraphIndex < myParagraphsNumber) {
                myApi.highlightArea(
                        new TextPosition(myParagraphIndex, 0, 0),
                        new TextPosition(myParagraphIndex, Integer.MAX_VALUE, 0)
                );
            } else {
                myApi.clearHighlighting();
            }
        } catch (ApiException e) {
//            Lt.df(e.getCause().toString());
//            e.printStackTrace();
        }
    }

    static void gotoPreviousSentence() {
        try {
            myApi.clearHighlighting();
        } catch (ApiException e) {
            ;
        }
        if (myCurrentSentence > 0) {
            myCurrentSentence--;
            highlightSentence();
        }
        else if (myParagraphIndex > 0) {
            gotoPreviousParagraph();
            processCurrentParagraph();
            myCurrentSentence = mySentences.length - 1;
            highlightSentence();
        }
    }

    static void gotoNextSentence() {
        try {
            myApi.clearHighlighting();
        } catch (ApiException e) {
            ;
        }
        if (myCurrentSentence < mySentences.length -1) {
            myCurrentSentence++;
            highlightSentence();
        }
        else if (myParagraphIndex < myParagraphsNumber) {
            ++myParagraphIndex;
            processCurrentParagraph();
            myCurrentSentence = 0;
            highlightSentence();
        }
    }

    static void gotoPreviousParagraph() {
        mySentences = new TtsSentenceExtractor.SentenceIndex[0];
        try {
            if (myParagraphIndex > myParagraphsNumber)
                myParagraphIndex = myParagraphsNumber;
            for (int i = myParagraphIndex - 1; i >= 0; --i) {
                if (myApi.getParagraphText(i).length() > 2) { // empty paragraph breaks previous function
                    myParagraphIndex = i;
                    break;
                }
            }
            if (haveNewApi < 1)
                highlightParagraph();
            if (SpeakActivity.getCurrent() != null) {
                SpeakActivity.getCurrent().runOnUiThread(new Runnable() {
                    public void run() {
                        SpeakActivity.getCurrent().findViewById(R.id.button_next).setEnabled(true);
                        SpeakActivity.getCurrent().findViewById(R.id.button_play).setEnabled(true);
                    }
                });
            }
        } catch (ApiException e) {
            e.printStackTrace();
        }
    }

    static void switchReadMode() {
        if (myCurrentSentence < mySentences.length) {
            int sentIdx = mySentences[myCurrentSentence].i;
            processCurrentParagraph();
            for (int n = 0; n < mySentences.length; n++) {
                if (n == mySentences.length-1 || mySentences[n].i == sentIdx || mySentences[n+1].i > sentIdx) {
                    myCurrentSentence = n;
                    break;
                }
            }
            highlightSentence();
        }
    }

    static void processCurrentParagraph() {
        if (myTTS == null) {
            return;
        }
        if (haveNewApi < 1) { // Old API for FBReader 1.5.3 and lower
            try {
                String text = "";
                myCurrentSentence = 0;
                for (; myParagraphIndex < myParagraphsNumber; ++myParagraphIndex) {
                    final String s = myApi.getParagraphText(myParagraphIndex);
                    if (s.length() > 0) {
                        text = s;
                        break;
                    }
                }
                highlightParagraph();
                if (myParagraphIndex >= myParagraphsNumber) {
                    if (SpeakActivity.getCurrent() != null) {
                        SpeakActivity.getCurrent().runOnUiThread(new Runnable() {
                            public void run() {
                                SpeakActivity.getCurrent().findViewById(R.id.button_next).setEnabled(false);
                                SpeakActivity.getCurrent().findViewById(R.id.button_play).setEnabled(false);
                            }
                        });
                    }
                }
                mySentences = TtsSentenceExtractor.extract(text, myTTS.getLanguage());
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
        else {
            // The code below uses new APIs
            try {
                List<String> wl = null;
                ArrayList<Integer> il = null;
                myCurrentSentence = 0;
                for (; myParagraphIndex < myParagraphsNumber; ++myParagraphIndex) {
                    // final String s = myApi.getParagraphText(myParagraphIndex);
                    wl = myApi.getParagraphWords(myParagraphIndex);
                    if (wl != null && wl.size() > 0) {
                        il = myApi.getParagraphIndices(myParagraphIndex);
                        break;
                    }
                }
                if (wl == null || myParagraphIndex >= myParagraphsNumber) {
                    if (SpeakActivity.getCurrent() != null) {
                        SpeakActivity.getCurrent().runOnUiThread(new Runnable() {
                            public void run() {
                                SpeakActivity.getCurrent().findViewById(R.id.button_next).setEnabled(false);
                                SpeakActivity.getCurrent().findViewById(R.id.button_play).setEnabled(false);
                            }
                        });
                    }
                } else {
                    boolean wordsOnly = getPrefs().getBoolean("WORD_OPTS", false) &&
                            myPreferences.getBoolean("SINGLE_WORDS", false);
                    mySentences = TtsSentenceExtractor.build(wl, il, myTTS, wordsOnly);
                }
            } catch (ApiException e) {
                stopTalking();
                SpeakActivity.showErrorMessage(R.string.api_error_2);
                e.printStackTrace();
            }
        }
    }

    static void regainBluetoothFocus() {
        if (mAudioManager != null && componentName != null) {
            //TtsApp.enableComponents(true); // takes a long time on some hardware?.
            mAudioManager.registerMediaButtonEventReceiver(componentName);
        }
    }

    public static SharedPreferences getPrefs() {
        if (myPreferences == null) {
            LangSupport.setPrefsName("atVoice");
            if (currentService != null)
                myPreferences = currentService.getSharedPreferences("atVoice", MODE_PRIVATE);
            else
                myPreferences = TtsApp.getContext().getSharedPreferences("atVoice", MODE_PRIVATE);
        }
        return myPreferences;
    }

    // implements ApiClientImplementation.ConnectionListener
    public void onConnected() {
        if (myInitializationStatus != FULLY_INITIALIZED && myApi != null) {
            myInitializationStatus |= API_INITIALIZED;
//            try {
//                ErrorReporter.getInstance().putCustomData("FBReaderVer", myApi.getFBReaderVersion());
//            } catch (ApiException e) {
//                ;
//            }
            if (myInitializationStatus == FULLY_INITIALIZED) {
                SpeakActivity.onInitializationCompleted();
            }
        }
    }

    public static void reconnect() {
        Lt.d("reconnect()");
        readingStarted = true;
        if (TtsApp.areComponentsEnabled())
            TtsApp.enableComponents(true);
        if (!SpeakActivity.isVisible() && SpeakActivity.getCurrent() != null) {
            // bring SpeakActivity to top
            Lt.d("- areComponentsEnabled() is true, activity not visible");
            if (myTTS == null) {
                myInitializationStatus &= ~TTS_INITIALIZED;
            }
            Intent intent = new Intent(TtsApp.getContext(), SpeakActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
            TtsApp.getContext().startActivity(intent);
        }
    }

    public static void disconnect() {
        TtsApp.enableComponents(false);
        //currentService.sendBroadcast(new Intent(SpeakService.TTSP_KILL));
//        mAudioManager.unregisterMediaButtonEventReceiver(SpeakService.componentName);
//        mAudioManager.abandonAudioFocus(SpeakService.afChangeListener);
    }

    @Override public IBinder onBind(Intent arg0) { return null; }
    @Override public void onCreate() {
        Lt.d("SpeakService created.");
        currentService = this;
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        componentName = new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName());
        myPreferences = getSharedPreferences("FBReaderTTS", MODE_PRIVATE);
        if (myPreferences.getBoolean("fbrStart", true))
            TtsApp.enableComponents(true);
        super.onCreate();
    }
    @Override public void onDestroy() {
        switchOff();
        currentService = null;
        super.onDestroy();
    }

    static void doStop() {
    	if (currentService != null) {
            Lt.d("currentService.stopSelf()");
    		currentService.stopSelf();
            currentService = null;
        }
    }
    
    static boolean doStartup() {
        if (currentService == null)
            return false;
        if (myPreferences == null)
            myPreferences = currentService.getSharedPreferences("FBReaderTTS", MODE_PRIVATE);
        selectedLanguage = BOOK_LANG; // myPreferences.getString("lang", BOOK_LANG);
        myHighlightSentences = myPreferences.getBoolean("hiSentences", true);
        myParaPause = myPreferences.getInt("paraPause", myParaPause);
        if (myParamMap == null) {
            myParamMap = new HashMap<String, String>();
            myParamMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID);
        }
        if (myApi == null) {
            myInitializationStatus &= ~API_INITIALIZED;
            myApi = new ApiClientImplementation(currentService, currentService);
            myApi.connect();
        }
        myApi.addListener(new ApiListener() {
            @Override
            public void onEvent(String eventType) {
                Lt.d("onEvent: " + eventType);
                if (eventType.equals(EVENT_READ_MODE_OPENED))
                    readingStarted = true;
                else if (eventType.equals(EVENT_READ_MODE_CLOSED)) {
                    PowerManager powerManager = (PowerManager) TtsApp.getContext().getSystemService(POWER_SERVICE);
                    KeyguardManager kgMgr = (KeyguardManager)  TtsApp.getContext().getSystemService(Context.KEYGUARD_SERVICE);
                    if (powerManager.isScreenOn() && !kgMgr.inKeyguardRestrictedInputMode())
                        readingStarted = false;
                    else
                        Lt.d("  - no stop");
                }
                if (!readingStarted &&
                        (!myPreferences.getBoolean("fbrStart", true)) &&
                        SpeakActivity.getCurrent() == null) {
                    disconnect();
                }
            }
        });

        // Test code
//        Boolean debug = (currentService.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
//        if (debug) try {
//            List<String> optGroups = myApi.getOptionGroups();
//            Lt.d("Option groups:");
//            for (String s : optGroups) {
//                Lt.d(s);
//                List<String> opts = myApi.getOptionNames(s);
//                for (String ss: opts) {
//                    Lt.d(" - " + ss);
//                }
//            }
//            Lt.d("-------------------");
//        } catch (Exception e) {}

        if (myTTS != null) {
        	try {
        		if (myTTS.isSpeaking())
        			myTTS.stop();
        	} catch (Exception e) {
        		myTTS = null;
        	}
        }
        if (myTTS == null)
        	myInitializationStatus &= ~TTS_INITIALIZED;

        if (_lockscreenManager == null)
            _lockscreenManager = new LockscreenManager();

        return true;
    }

    static void setSleepTimer(int minutes) {
        if (currentService == null)
            return;
        currentService.mHandler.removeCallbacks(currentService.myTimerTask);
        if (minutes > 0)
            currentService.mHandler.postDelayed(currentService.myTimerTask, minutes*60000);
    }

    private Runnable myTimerTask = new Runnable() {
        public void run() {
            switchOff();
            SpeakActivity sa = SpeakActivity.getCurrent();
            if (sa != null) {
                sa.restoreBottomMargin();
                TtsApp.enableComponents(false);
                sa.doDestroy();
                sa.finish();
            }
        }
    };

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        currentService = this;
        if (myApi == null)
            doStartup();
        Lt.d("TTS+ Service started");
        Intent i = new Intent(SVC_STARTED);
        sendBroadcast(i);
        return START_STICKY;
    }

    // The listener below is needed to stop talking when Voice Dialer button is pressed,
    // and resume talking if cancelled or call finished.
    static AudioManager.OnAudioFocusChangeListener afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                if (!myWasActive)
                    myWasActive = myIsActive;
                stopTalking(); // Pause playback
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                if (myWasActive) {
                    myWasActive = false;
                    startTalking();// Resume playback
                }
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                myWasActive = myIsActive;
                stopTalking();
                mAudioManager.unregisterMediaButtonEventReceiver(componentName);
            }
        }
    };
}
