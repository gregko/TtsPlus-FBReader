package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import com.hyperionics.TtsSetup.Lt;

/**
 * Created by Jacek Milewski
 * looksok.wordpress.com
 */

public class MyWidgetIntentReceiver extends BroadcastReceiver {
    static final String INTENT_PREVIOUS = "com.hyperionics.fbreader.plugin.tts_plus.PREVIOUS";
    static final String INTENT_PLAY = "com.hyperionics.fbreader.plugin.tts_plus.PLAY";
    static final String INTENT_PAUSE = "com.hyperionics.fbreader.plugin.tts_plus.PAUSE";
    static final String INTENT_NEXT = "com.hyperionics.fbreader.plugin.tts_plus.NEXT";

	@Override
	public void onReceive(Context context, Intent intent) {
//        Lt.d("Widget onReceive() " + intent.getAction());
//        if (SpeakService.myApi == null)
//            Lt.d("- myApi is null");
//        else
//            Lt.d(SpeakService.myApi.isConnected() ? "- FBReader connected" : "- FBReader NOT connected");

        if (INTENT_PREVIOUS.equals(intent.getAction())) {
            SpeakService.prevToSpeak();
		} else if (INTENT_PLAY.equals(intent.getAction())) {
            SpeakService.toggleTalking();
        } else if (INTENT_PAUSE.equals(intent.getAction())) {
            SpeakService.toggleTalking();
        } else if (INTENT_NEXT.equals(intent.getAction())) {
            SpeakService.nextToSpeak();
        }
        updateWidget(context);
	}

	static void updateWidget(Context context) {
		RemoteViews remoteViews = setLayout(context);

		//REMEMBER TO ALWAYS REFRESH YOUR BUTTON CLICK LISTENERS!!!
        remoteViews.setOnClickPendingIntent(R.id.button_previous, buildButtonPendingIntent(context, INTENT_PREVIOUS));
        remoteViews.setOnClickPendingIntent(R.id.button_play, buildButtonPendingIntent(context, INTENT_PLAY));
        remoteViews.setOnClickPendingIntent(R.id.button_pause, buildButtonPendingIntent(context, INTENT_PAUSE));
        remoteViews.setOnClickPendingIntent(R.id.button_next, buildButtonPendingIntent(context, INTENT_NEXT));

		pushWidgetUpdate(context.getApplicationContext(), remoteViews);
	}

    static RemoteViews setLayout(Context context) {
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        if (SpeakService.isTalking()) {
            remoteViews.setViewVisibility(R.id.button_play, View.GONE);
            remoteViews.setViewVisibility(R.id.button_pause, View.VISIBLE);
        } else {
            remoteViews.setViewVisibility(R.id.button_play, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.button_pause, View.GONE);
        }
        try {
            String title = SpeakService.myApi.getBookTitle();
            remoteViews.setTextViewText(R.id.book_info, title);
            int progress = 0;
            if (SpeakService.myParagraphsNumber > 0) {
                progress = 100*SpeakService.myParagraphIndex/SpeakService.myParagraphsNumber;
                remoteViews.setViewVisibility(R.id.button_next,
                        progress >= 100 && SpeakService.getCurrentSentence() >= SpeakService.getSntLength()-1 ?
                                View.INVISIBLE : View.VISIBLE);
                remoteViews.setViewVisibility(
                        R.id.button_previous, progress == 0 && SpeakService.getCurrentSentence() == 0 ?
                            View.INVISIBLE : View.VISIBLE);
            }
            remoteViews.setProgressBar(R.id.book_progress, 100, progress, false);

        } catch (Exception e) {
            remoteViews.setTextViewText(R.id.book_info, "(" + TtsApp.getContext().getString(R.string.unknown) + ")");
        }
        return remoteViews;
    }

    private static PendingIntent buildButtonPendingIntent(Context context, String action) {
        Intent intent = new Intent();
        intent.setAction(action);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void pushWidgetUpdate(Context context, RemoteViews remoteViews) {
        ComponentName myWidget = new ComponentName(context, MyWidgetProvider.class);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        manager.updateAppWidget(myWidget, remoteViews);
    }
}
