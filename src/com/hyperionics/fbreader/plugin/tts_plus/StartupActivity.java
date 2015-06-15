package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.hyperionics.TtsSetup.Lt;

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

// This is a dummy activity, it never actually starts. It's invoked only from FBReader menu,
// posts intent to start our main SpeakActivity.
public class StartupActivity extends Activity {
    static Intent originalIntent = null;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        originalIntent = getIntent();
        if (SpeakActivity.getCurrent() == null) {
            if (SpeakService.myApi != null) {
                try {
                    SpeakService.myApi.disconnect();
                } catch (Exception e) {
                    Lt.df("StartupActivity exception: " + e);
                    e.printStackTrace();
                }
                SpeakService.myApi = null;
            }
            // this is asynchronous
            SpeakActivity.wantStarted = false;
            startService(new Intent(TtsApp.getContext(), SpeakService.class));
        }
        finish();
    }
}