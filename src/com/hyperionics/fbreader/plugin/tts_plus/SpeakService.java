package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import org.geometerplus.android.fbreader.api.ApiClientImplementation;
import org.geometerplus.android.fbreader.api.ApiException;
import org.geometerplus.android.fbreader.api.ApiListener;
import org.geometerplus.android.fbreader.api.TextPosition;

import java.util.HashMap;
import java.util.Locale;

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
    static boolean myReadSentences = true;
    static String selectedLanguage = BOOK_LANG; // either "book" or locale code like "eng-USA"
    static int myParagraphIndex = -1;
    static int myParagraphsNumber;

    private static final String UTTERANCE_ID = "FBReaderTTSPlugin";
    static String mySentences[] = new String[0];
    private static int myCurrentSentence = 0;

    static boolean myIsActive = false;
    static boolean myWasActive = false;

    static volatile int myInitializationStatus;
    static int API_INITIALIZED = 1;
    static int TTS_INITIALIZED = 2;
    static int FULLY_INITIALIZED = API_INITIALIZED | TTS_INITIALIZED;

    public void onUtteranceCompleted(String uttId) {
        regainBluetoothFocus();
        if (myIsActive && UTTERANCE_ID.equals(uttId)) {
            if (++myCurrentSentence >= mySentences.length) {
                ++myParagraphIndex;
                gotoNextParagraph();
                if (myParagraphIndex >= myParagraphsNumber) {
                    stopTalking();
                    return;
                }
            }
            speakString(mySentences[myCurrentSentence]);

        } else {
            SpeakActivity.setActive(false);
        }
    }

    static void setLanguage(String languageCode) {
        Locale locale = null;
        try {
            if (languageCode == null || languageCode.equals(BOOK_LANG)) {
                languageCode = myApi.getBookLanguage();
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
            gotoNextParagraph();
        }
        speakString(mySentences[myCurrentSentence]);
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
        mySentences = new String[0];
        if (myApi != null) {
            try {
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

    static void nextParagraph() {
        // Users sometime press media next button by mistake, instead of play/resume.
        // Therefore we go to the next paragraph only if we were actually speaking.
        boolean wasSpeaking = myTTS.isSpeaking();
        if (wasSpeaking) {
            stopTalking();
            if (myParagraphIndex < myParagraphsNumber) {
                ++myParagraphIndex;
                gotoNextParagraph();
                if (wasSpeaking)
                    startTalking();
            }
        }
        else
            startTalking();
    }

    static void prevParagraph() {
        // Same potential to press media previous button by mistake, instead of
        // play/resume.
        boolean wasSpeaking = myTTS.isSpeaking()
                || myParagraphIndex >= myParagraphsNumber;
        if (wasSpeaking) {
            stopTalking();
            gotoPreviousParagraph();
            if (wasSpeaking)
                startTalking();
        }
        else
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
        if (myTTS != null)
            myTTS.setPitch(pitch);
    }

    private static void highlightParagraph() throws ApiException {
        if (0 <= myParagraphIndex && myParagraphIndex < myParagraphsNumber) {
            myApi.highlightArea(
                    new TextPosition(myParagraphIndex, 0, 0),
                    new TextPosition(myParagraphIndex, Integer.MAX_VALUE, 0)
            );
        } else {
            myApi.clearHighlighting();
        }
    }

    static void gotoPreviousParagraph() {
        mySentences = new String[0];
        try {
            if (myParagraphIndex > myParagraphsNumber)
                myParagraphIndex = myParagraphsNumber;
            for (int i = myParagraphIndex - 1; i >= 0; --i) {
                if (myApi.getParagraphText(i).length() > 2) { // empty paragraph breaks previous function
                    myParagraphIndex = i;
                    break;
                }
            }
            if (myApi.getPageStart().ParagraphIndex >= myParagraphIndex) {
                myApi.setPageStart(new TextPosition(myParagraphIndex, 0, 0));
            }
            highlightParagraph();
            if (SpeakActivity.getCurrent() != null) {
                SpeakActivity.getCurrent().runOnUiThread(new Runnable() {
                    public void run() {
                        SpeakActivity.getCurrent().findViewById(R.id.button_next_paragraph).setEnabled(true);
                        SpeakActivity.getCurrent().findViewById(R.id.button_play).setEnabled(true);
                    }
                });
            }
        } catch (ApiException e) {
            e.printStackTrace();
        }
    }

    static String gotoNextParagraph() {
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
            if (!"".equals(text) && !myApi.isPageEndOfText()) {
                myApi.setPageStart(new TextPosition(myParagraphIndex, 0, 0));
            }
            highlightParagraph();
            if (myParagraphIndex >= myParagraphsNumber) {
                if (SpeakActivity.getCurrent() != null) {
                    SpeakActivity.getCurrent().runOnUiThread(new Runnable() {
                        public void run() {
                            SpeakActivity.getCurrent().findViewById(R.id.button_next_paragraph).setEnabled(false);
                            SpeakActivity.getCurrent().findViewById(R.id.button_play).setEnabled(false);
                        }
                    });
                }
            }
            if (myReadSentences) {
                mySentences = TtsSentenceExtractor.extract(text, myTTS.getLanguage());
            } else {
                mySentences = new String[1];
                mySentences[0] = text;
            }
            return text;
        } catch (ApiException e) {
            e.printStackTrace();
            return "";
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
            if (myInitializationStatus == FULLY_INITIALIZED) {
                SpeakActivity.onInitializationCompleted();
            }
        }
    }

    @Override
    public IBinder onBind(Intent arg0) { return null; }
    @Override
    public void onCreate() {
        currentService = this;
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        componentName = new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName());
        myApi = new ApiClientImplementation(this, this);
        if (myApi != null) {
            myApi.addListener(new ApiListener() {
                @Override
                public void onEvent(String eventType) {
                    if (SpeakActivity.isInitialized() && eventType.equals(EVENT_READ_MODE_OPENED)) {
                        SpeakActivity.restartActivity(SpeakApplication.getContext());
                    }
                }
            });
        }
        super.onCreate();
    }
    @Override
    public void onDestroy() {
        switchOff();
        if (myApi != null) {
            myApi.disconnect();
            myApi = null;
        }
        currentService = null;
        super.onDestroy();
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mAudioManager.registerMediaButtonEventReceiver(componentName);
        mAudioManager.requestAudioFocus(afChangeListener,
                // Use the music stream.
                AudioManager.STREAM_MUSIC,
                // Request permanent focus.
                AudioManager.AUDIOFOCUS_GAIN);

        myPreferences = getSharedPreferences("FBReaderTTS", MODE_PRIVATE);
        selectedLanguage = myPreferences.getString("lang", BOOK_LANG);
        myReadSentences = myPreferences.getBoolean("readSentences", true);
        if (myApi != null) {
            myApi.connect();
        }

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

}
