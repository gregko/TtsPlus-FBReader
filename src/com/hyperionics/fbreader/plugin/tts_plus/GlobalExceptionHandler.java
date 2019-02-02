package com.hyperionics.fbreader.plugin.tts_plus;

import android.content.Context;
import com.hyperionics.ttssetup.Lt;

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

public class GlobalExceptionHandler  implements Thread.UncaughtExceptionHandler {
    // A reference to the system's previous default UncaughtExceptionHandler
    // kept in order to execute the default exception handling after sending
    // the report.
    private Thread.UncaughtExceptionHandler mDefaultExceptionHandler;

    public void uncaughtException(Thread thread, Throwable ex) {
        Lt.df("GLOBAL Exception :" + ex.toString() + " and Message:" + ex.getMessage());
        ex.printStackTrace();
        try {
            SpeakActivity.restoreBottomMargin();
            if (SpeakService.myApi != null)
                SpeakService.myApi.clearHighlighting();
        } catch (Exception e) {}
        mDefaultExceptionHandler.uncaughtException(thread, ex);
    }

    public void init(Context context) {
        // If mDefaultExceptionHandler is not null, initialization is already done.
        // Don't do it twice to avoid losing the original handler.
        if (mDefaultExceptionHandler == null) {
            mDefaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(this);
        }
    }

}
