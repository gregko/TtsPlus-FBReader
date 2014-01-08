package com.hyperionics.fbreader.plugin.tts_plus;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import com.hyperionics.TtsSetup.Lt;
import org.geometerplus.android.fbreader.api.ApiException;

/**
 * Created by Jacek Milewski
 * looksok.wordpress.com
 */

public class MyWidgetProvider extends AppWidgetProvider {

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        //Lt.d("MyWidgetProvider: onUpdate()");
        MyWidgetIntentReceiver.updateWidget(context);
	}

    @Override
    public void onReceive(Context context, Intent intent) {
        //Lt.d("MyWidgetProvider: onReceive()");
        super.onReceive(context, intent);
        MyWidgetIntentReceiver.updateWidget(context);
    }

    public static void updateWidgets() {
        //Lt.d("MyWidgetProvider: updateWidgets() called...");
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(TtsApp.getContext());
        ComponentName thisWidget = new ComponentName(TtsApp.getContext(), MyWidgetProvider.class);
        int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
        for (int widgetId : allWidgetIds) {
            RemoteViews remoteViews = MyWidgetIntentReceiver.setLayout(TtsApp.getContext());
            appWidgetManager.updateAppWidget(widgetId, remoteViews);
        }
    }
}
