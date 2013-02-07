package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.*;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.os.Handler;
import android.text.format.Time;
import org.geometerplus.android.fbreader.api.ApiClientImplementation;
import org.geometerplus.android.fbreader.api.ApiException;
import org.geometerplus.android.fbreader.api.ApiListener;
import org.geometerplus.android.fbreader.api.TextPosition;
//import org.acra.ErrorReporter;

import java.util.*;
import java.util.regex.Pattern;

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

    static private SpeakService currentService;
    static SpeakService getCurrentService() { return currentService; }

    static ApiClientImplementation myApi;
    static TextToSpeech myTTS;
    static AudioManager mAudioManager;
    static ComponentName componentName;
    static SharedPreferences myPreferences;

    static final String BOOK_LANG = "book";
    static boolean myHighlightSentences = true;
    static int myParaPause = 300;
    static String selectedLanguage = BOOK_LANG; // either "book" or locale code like "eng-USA"
    static int myParagraphIndex = -1;
    static int myParagraphsNumber;
    static float myCurrentPitch = 1f;
    static int haveNewApi = 1;
    static private boolean isServiceTalking = false;
    static boolean readingStarted = false;

    static private final String UTTERANCE_ID = "FBReaderTTS+Plugin";
    static TtsSentenceExtractor.SentenceIndex mySentences[] = new TtsSentenceExtractor.SentenceIndex[0];
    static private int myCurrentSentence = 0;

    static boolean myIsActive = false;
    static boolean myWasActive = false;
    static private HashMap<String, String> myCallbackMap;

    static volatile int myInitializationStatus = 0;
    static int API_INITIALIZED = 1;
    static int TTS_INITIALIZED = 2;
    static int FULLY_INITIALIZED = API_INITIALIZED | TTS_INITIALIZED;
    static final String SVC_STARTED = "com.hyperionics.fbreader.plugin.tts_plus.SVC_STARTED";
    static final String TTSP_KILL = "com.hyperionics.fbreader.plugin.tts_plus.TTSP_KILL";

    static void savePosition() {
        try {
            if (myCurrentSentence < mySentences.length) {
                String bookHash = "BP:" + myApi.getBookHash();
                SharedPreferences.Editor myEditor = myPreferences.edit();
                Time time = new Time();
                time.setToNow();
                String lang = "";
                lang = " l:" + selectedLanguage;
                myEditor.putString(bookHash, lang +
                        "p:" + myParagraphIndex + " s:" + myCurrentSentence + " e:" + mySentences[myCurrentSentence].i +
                        " d:" + time.format2445()
                );

                myEditor.commit();
            }
        } catch (ApiException e) {
            ;
        }
    }

    static void restorePosition() {
        try {
            String bookHash = "BP:" + myApi.getBookHash();
            String s = myPreferences.getString(bookHash, "");
            int il = s.indexOf("l:");
            int para = s.indexOf("p:");
            int sent = s.indexOf("s:");
            int idx = s.indexOf("e:");
            int dt = s.indexOf("d:");
            if (para > -1 && sent > -1 && idx > -1 && dt > -1) {
                if (il > -1) {
                    selectedLanguage = s.substring(il + 2, para);
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
            ;
        }
    }

    static void cleanupPositions() {
        // Cleanup - delete any hashes older than 6 months
        try {
            Map<String, ?> prefs = myPreferences.getAll();
            SharedPreferences.Editor myEditor = myPreferences.edit();
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
                    myTTS.playSilence(myParaPause, TextToSpeech.QUEUE_ADD, myCallbackMap);
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
                if (SpeakActivity.getCurrent() != null) {
                    SpeakActivity.getCurrent().selectLanguage(true);
                    return false;
                }
                else
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
        }

        locale = TtsSentenceExtractor.localeFromString(languageCode);
        if (myTTS == null)
            return false;
        boolean isAvailable = false;
        try {
            isAvailable = myTTS.isLanguageAvailable(locale) >= 0;
        } catch (IllegalArgumentException e) {}
        while (!isAvailable && languageCode.contains("-")) {
            languageCode = languageCode.substring(0, languageCode.lastIndexOf("-"));
            locale = TtsSentenceExtractor.localeFromString(languageCode);
            try {
                isAvailable = myTTS.isLanguageAvailable(locale) >= 0;
            } catch (IllegalArgumentException e) {}
        }

        if (!isAvailable) {
            String err = currentService.getText(R.string.no_data_for_language).toString()
                    .replace("%0", locale.getDisplayLanguage());
            AlertDialog.Builder builder = new AlertDialog.Builder(SpeakActivity.getCurrent());
            builder.setMessage(err)
                    .setCancelable(false)
                    .setPositiveButton(currentService.getText(R.string.yes), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            try {
                                Intent intent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
                                currentService.startActivity(intent);
                            } catch (ActivityNotFoundException e) {
                                SpeakActivity.showErrorMessage(R.string.no_tts_installed);
                            }
                            if (SpeakActivity.getCurrent() != null) // added this line post 1.5.5 rel. - line numbrs below same.
                                SpeakActivity.getCurrent().doDestroy();
                            TtsApp.ExitApp();
                        }
                    })
                    .setNegativeButton(currentService.getText(R.string.no), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
            return false;
        }
        myTTS.setLanguage(locale);
        return true;
    }

    static void startTalking() {
        SpeakActivity.setActive(true);
        setLanguage(SpeakService.selectedLanguage);
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
                speakString(mySentences[myCurrentSentence].s);
            }
        } else
            stopTalking();
    }

    static void stopTalking() {
        Lt.d("stopTalking()");
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
            mAudioManager.abandonAudioFocus(afChangeListener);
            regainBluetoothFocus();
        }
    }

    static void toggleTalking() {
        if (SpeakActivity.getCurrent() == null) {
            if (readingStarted) {
                if (currentService == null) {
                    TtsApp.getContext().startService(new Intent(TtsApp.getContext(), SpeakService.class));
                }
                Intent i = new Intent(SVC_STARTED);
                SpeakActivity.wantStarted = true;
                TtsApp.getContext().sendBroadcast(i);
            } else {
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
        stopTalking();
        currentService.mHandler.removeCallbacks(currentService.myTimerTask);
        mySentences = new TtsSentenceExtractor.SentenceIndex[0];
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
        if (!myPreferences.getBoolean("fbrStart", true)) {
            disconnect();
        }
        myInitializationStatus &= ~TTS_INITIALIZED;
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

    private static Pattern punctPat = Pattern.compile("[.?!'\"-,:;()\\[\\]{}\\*]");
    private static int speakString(String text) {
        int ret = TextToSpeech.SUCCESS;
        // Loquendo - Nuance TTS stops on long sentences. Tested on Russian edition of Frazen's "The Corrections"
        // "Поправки" - "Звонок звенил уже так давно, что Ламберты ..." - still speaks at 591 characters length,
        // but stops speaking at 592. Idea - break sentences at about 512 length at the next comma, hyphen, ( or ),
        // ellipses..., colon :, semicolon ;

        // stupid Google voice stops on empty or silent sentences...
        // int n = text.replaceAll("[.?!'\"-,:;()\\[\\]{}\\*]", "").trim().length();
        // faster with pre-compiled pattern
        if (punctPat.matcher(text).replaceAll("").trim().length() > 0) {
            ret = myTTS.speak(text, TextToSpeech.QUEUE_FLUSH, myCallbackMap);
            isServiceTalking = ret == TextToSpeech.SUCCESS;
        } else {
            currentService.onUtteranceCompleted(UTTERANCE_ID);
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
                    mySentences = TtsSentenceExtractor.build(wl, il, myTTS);
                }
            } catch (ApiException e) {
                stopTalking();
                SpeakActivity.showErrorMessage(R.string.api_error_2);
                e.printStackTrace();
            }
        }
    }

    static void regainBluetoothFocus() {
        if (mAudioManager != null) {
            //TtsApp.enableComponents(true); // takes a long time on some hardware?.
            mAudioManager.registerMediaButtonEventReceiver(componentName);
        }
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
        if (TtsApp.areComponentsEnabled() && !SpeakActivity.isVisible() && SpeakActivity.getCurrent() != null) {
            // bring SpeakActivity to top
            Lt.d("- areComponentsEnabled() is true, activity not visible");
            if (myTTS == null) {
                myInitializationStatus &= ~TTS_INITIALIZED;
            }
            Intent intent = new Intent(TtsApp.getContext(), SpeakActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
            currentService.startActivity(intent);
        }
        else {
            TtsApp.enableComponents(true);
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
    	if (currentService != null)
    		currentService.stopSelf();
    }
    
    static boolean doStartup() {
        if (currentService == null)
            return false;
        if (myPreferences == null)
            myPreferences = currentService.getSharedPreferences("FBReaderTTS", MODE_PRIVATE);
        selectedLanguage = BOOK_LANG; // myPreferences.getString("lang", BOOK_LANG);
        myHighlightSentences = myPreferences.getBoolean("hiSentences", true);
        myParaPause = myPreferences.getInt("paraPause", myParaPause);
        if (myCallbackMap == null) {
            myCallbackMap = new HashMap<String, String>();
            myCallbackMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID);
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
