package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.os.Handler;
import android.text.format.Time;
import org.geometerplus.android.fbreader.api.ApiClientImplementation;
import org.geometerplus.android.fbreader.api.ApiException;
import org.geometerplus.android.fbreader.api.ApiListener;
import org.geometerplus.android.fbreader.api.TextPosition;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: greg
 * Date: 5/23/12
 * Time: 12:45 PM
 * To change this template use File | Settings | File Templates.
 */
public class SpeakService extends Service implements TextToSpeech.OnUtteranceCompletedListener, ApiClientImplementation.ConnectionListener {
    static SpeakService currentService;

    static ApiClientImplementation myApi;
    static TextToSpeech myTTS;
    static AudioManager mAudioManager;
    static ComponentName componentName;
    static SharedPreferences myPreferences;

    static final String BOOK_LANG = "book";
    static boolean myHighlightSentences = true;
    static String selectedLanguage = BOOK_LANG; // either "book" or locale code like "eng-USA"
    static int myParagraphIndex = -1;
    static int myParagraphsNumber;
    static float myCurrentPitch = 1f;
    static int haveNewApi = 1;

    private static final String UTTERANCE_ID = "FBReaderTTSPlugin";
    static TtsSentenceExtractor.SentenceIndex mySentences[] = new TtsSentenceExtractor.SentenceIndex[0];
    private static int myCurrentSentence = 0;

    static boolean myIsActive = false;
    static boolean myWasActive = false;

    static volatile int myInitializationStatus;
    static int API_INITIALIZED = 1;
    static int TTS_INITIALIZED = 2;
    static int FULLY_INITIALIZED = API_INITIALIZED | TTS_INITIALIZED;

    static void savePosition() {
        try {
            String bookHash = "BP:" + myApi.getBookHash();
            SharedPreferences.Editor myEditor = myPreferences.edit();
            Time time = new Time();
            time.setToNow();
            myEditor.putString(bookHash,
                    "p:" + myParagraphIndex + " s:" + myCurrentSentence + " e:" + mySentences[myCurrentSentence].i +
                            " d:" + time.format2445());
            myEditor.commit();
        } catch (ApiException e) {
            ;
        }
    }

    static void restorePosition() {
        try {
            String bookHash = "BP:" + myApi.getBookHash();
            String s = myPreferences.getString(bookHash, "");
            int para = s.indexOf("p:");
            int sent = s.indexOf("s:");
            int idx = s.indexOf("e:");
            int dt = s.indexOf("d:");
            if (para > -1 && sent > -1 && idx > -1 && dt > -1) {
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
        if (myIsActive && UTTERANCE_ID.equals(uttId)) {
            if (++myCurrentSentence >= mySentences.length) {
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

    static void setLanguage(String languageCode) {
        Locale locale = null;
        try {
            if (languageCode == null || languageCode.equals(BOOK_LANG)) {
                languageCode = myApi.getBookLanguage();
                if (languageCode == null)
                    languageCode = Locale.getDefault().getLanguage();
            }
            int n = languageCode.indexOf("-");
            if (n > 0) {
                String lang, country;
                lang = languageCode.substring(0, n);
                country = languageCode.substring(n+1);
                locale = new Locale(lang, country);
            }
            else {
                locale = new Locale(languageCode);
            }
        } catch (Exception e) {
        }
        if (locale == null || myTTS.isLanguageAvailable(locale) < 0) {
            final Locale originalLocale = locale;
            locale = Locale.getDefault();
            if (myTTS.isLanguageAvailable(locale) < 0) {
                locale = Locale.ENGLISH;
            }
            String err = currentService.getText(R.string.no_data_for_language).toString()
                    .replace("%0", originalLocale != null
                            ? originalLocale.getDisplayLanguage() : languageCode)
                    .replace("%1", locale.getDisplayLanguage());

            SpeakActivity.showErrorMessage(err);
        }
        myTTS.setLanguage(locale);
    }

    static void startTalking() {
        SpeakActivity.setActive(true);
        if (myCurrentSentence >= mySentences.length) {
            processCurrentParagraph();
        }
        if (myCurrentSentence < mySentences.length) {
            if (haveNewApi > 0)
                highlightSentence();
            speakString(mySentences[myCurrentSentence].s);
        } else
            stopTalking();
    }

    static void stopTalking() {
        SpeakActivity.setActive(false);
        if (myTTS != null && myTTS.isSpeaking()) {
            myTTS.stop();
            while (SpeakActivity.getCurrent() != null && myTTS.isSpeaking()) {
                try {
                    synchronized (SpeakActivity.getCurrent()) {
                        SpeakActivity.getCurrent().wait(100);
                    }
                } catch (InterruptedException e) {
                    ;
                }
            }
            savePosition();
        }
        regainBluetoothFocus();
    }

    static void toggleTalking() {
        if (SpeakActivity.getCurrent() == null)
            return;

        if (myIsActive) {
            stopTalking();
        }
        else {
            startTalking();
        }
    }

    static void switchOff() {
        stopTalking();
        mySentences = new TtsSentenceExtractor.SentenceIndex[0];
        if (myApi != null) {
            try {
                cleanupPositions();
                myApi.clearHighlighting();
            } catch (ApiException e) {
                e.printStackTrace();
            }
            // do not disconnect and destroy, we need it to get events.
//            myApi.disconnect();
//            myApi = null;
        }
        if (SpeakService.myTTS != null) {
            SpeakService.myTTS.shutdown();
            SpeakService.myTTS = null;
        }
        myInitializationStatus &= ~TTS_INITIALIZED;
    }

    static void nextToSpeak() {
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

    private static int speakString(String text) {
        HashMap<String, String> callbackMap = new HashMap<String, String>();
        callbackMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID);
        int ret = myTTS.speak(text, TextToSpeech.QUEUE_FLUSH, callbackMap);
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
            if (myCurrentSentence == 0)
                stPos = new TextPosition(myParagraphIndex, 0, 0);
            else
                stPos = new TextPosition(myParagraphIndex, mySentences[myCurrentSentence].i, 0);
            TextPosition edPos = new TextPosition(myParagraphIndex, endEI, 0);
            if (stPos.compareTo(myApi.getPageStart()) < 0 || edPos.compareTo(myApi.getPageEnd()) > 0)
                myApi.setPageStart(stPos);
            if (myHighlightSentences)
                myApi.highlightArea(stPos, edPos);
            else
                myApi.clearHighlighting();
        } catch (ApiException e) {
            Lt.df(e.getCause().toString());
            e.printStackTrace();
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
            Lt.df(e.getCause().toString());
            e.printStackTrace();
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
                    if (wl.size() > 0) {
                        il = myApi.getParagraphIndices(myParagraphIndex);
                        break;
                    }
                }
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

                mySentences = TtsSentenceExtractor.build(wl, il, myTTS.getLanguage());
            } catch (ApiException e) {
                stopTalking();
                SpeakActivity.showErrorMessage(R.string.api_error_2);
                e.printStackTrace();
            }
        }
    }

    static void regainBluetoothFocus() {
        if (myTTS != null)
            mAudioManager.registerMediaButtonEventReceiver(componentName);
    }

    static void stop() {
        mAudioManager.unregisterMediaButtonEventReceiver(componentName);
        mAudioManager.abandonAudioFocus(afChangeListener);
        currentService.stopSelf();
    }

    // implements ApiClientImplementation.ConnectionListener
    public void onConnected() {
        if (myInitializationStatus != FULLY_INITIALIZED) {
            myInitializationStatus |= API_INITIALIZED;
            try {
                String version = myApi.getFBReaderVersion();
                Lt.d("FBReader version: " + version);
                String bookHash = myApi.getBookHash();
                Lt.d("book hash = " + bookHash + "(" + myApi.getBookTitle() + ")");
            } catch (ApiException e) {
                ;
            }
            if (myInitializationStatus == FULLY_INITIALIZED) {
                SpeakActivity.onInitializationCompleted();
            }
        }
    }

    @Override public IBinder onBind(Intent arg0) { return null; }
    @Override public void onCreate() {
        Lt.d("SpeakService created.");
        currentService = this;
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        componentName = new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName());
        super.onCreate();
    }
    @Override public void onDestroy() {
        switchOff();
        currentService = null;
        super.onDestroy();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        mAudioManager.registerMediaButtonEventReceiver(componentName);
        mAudioManager.requestAudioFocus(afChangeListener,
                // Use the music stream.
                AudioManager.STREAM_MUSIC,
                // Request permanent focus.
                AudioManager.AUDIOFOCUS_GAIN);

        myPreferences = getSharedPreferences("FBReaderTTS", MODE_PRIVATE);
        selectedLanguage = myPreferences.getString("lang", BOOK_LANG);
        myHighlightSentences = myPreferences.getBoolean("hiSentences", true);
        myApi = new ApiClientImplementation(this, this);
        myApi.addListener(new ApiListener() {
            @Override
            public void onEvent(String eventType) {
                mHandler.removeCallbacks(mTimerTask);
                boolean isInitialized = SpeakActivity.isInitialized();
                boolean isTop = SpeakApp.isFbrPackageOnTop();
                Lt.d("onEvent-" + eventType + "; isInit=" + isInitialized + "; FbrTop=" + isTop);
                if (eventType.equals(EVENT_READ_MODE_OPENED)) {
                    SpeakApp.EnableComponents(true);
                    if (isInitialized)
                        SpeakActivity.restartActivity(SpeakApp.getContext());
                }
                else if (!isInitialized && eventType.equals(EVENT_READ_MODE_CLOSED))
                {
                    if (!isTop) {
                        Lt.d("  - Disabling components from onEvent");
                        SpeakApp.EnableComponents(false);
                        //SpeakApp.exitApp();
                    } else {
                        // Need to check if FBReader is still on top or exited only after some time...
                        mHandler.postDelayed(mTimerTask, 1000);
                    }
                }
            }
        });
        myApi.connect();

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
                mAudioManager.abandonAudioFocus(afChangeListener);
            }
        }
    };

    // A timer called on "stopReading" event sent from FBReader and when TTS+ is not active.
    // After some time passes, we need to check if the main reader is still on top - if not,
    // we need to stop the service and exit, to let other apps take over Bluetooth controls.
    private Handler mHandler = new Handler();
    private Runnable mTimerTask = new Runnable() {
        public void run() {
            mHandler.removeCallbacks(mTimerTask);
            if (!SpeakApp.isFbrPackageOnTop()) {
                if (!SpeakActivity.isInitialized()) {
                    Lt.d("  - Disabling components from mTimerTask");
                    SpeakApp.EnableComponents(false);
                    //SpeakApp.exitApp();
                }
            } else if (!SpeakActivity.isInitialized()) {
                // Re-post again, we could be in a modal dialog of FBReader. Callbacks will be removed
                // upon next event posted to us from FBR, or we'll exit if user presses Home while in
                // Settings or other modal dialog.
                mHandler.postDelayed(mTimerTask, 1000);
            }
        }
    };
}
