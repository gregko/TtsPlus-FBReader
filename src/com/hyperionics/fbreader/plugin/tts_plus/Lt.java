package com.hyperionics.fbreader.plugin.tts_plus;

import android.util.Log;

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

public class Lt {
    private static String myTag = "FBReaderTTS";
    private Lt() {}
    static void setTag(String tag) { myTag = tag; }
    public static void d(String msg) {
        // Uncomment line below to turn on debug output
        //Log.d(myTag, msg);
    }
    public static void df(String msg) {
        // Forced output, do not comment out - for exceptions etc.
        Log.d(myTag, msg);
    }
}
