package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

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

public class SettingsActivity extends Activity {

    public class MyOnSleepSelectedListener implements AdapterView.OnItemSelectedListener {

        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            int[] minutes = getResources().getIntArray(R.array.sleep_minutes);
            setResult(minutes[pos]);
        }

        public void onNothingSelected(AdapterView parent) {
        }
    }

    private void setListener(int id, View.OnClickListener listener) {
        findViewById(id).setOnClickListener(listener);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setup_panel);

        Spinner spinner = (Spinner) findViewById(R.id.sleepSpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.sleep_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(0);
        spinner.setOnItemSelectedListener(new MyOnSleepSelectedListener());

        ((CheckBox)findViewById(R.id.highlight_sentences)).setChecked(SpeakService.myHighlightSentences);
        setListener(R.id.highlight_sentences, new View.OnClickListener() {
            private SharedPreferences.Editor myEditor = SpeakService.myPreferences.edit();
            public void onClick(View v) {
                SpeakService.mySentences = new TtsSentenceExtractor.SentenceIndex[0];
                SpeakService.myHighlightSentences = ((CheckBox) v).isChecked();
                myEditor.putBoolean("hiSentences", SpeakService.myHighlightSentences);
                myEditor.commit();
            }
        });

        ((CheckBox)findViewById(R.id.plug_start)).setChecked(SpeakService.myPreferences.getBoolean("plugStart", false));
        setListener(R.id.plug_start, new View.OnClickListener() {
            public void onClick(View v) {
                SharedPreferences.Editor myEditor = SpeakService.myPreferences.edit();
                myEditor.putBoolean("plugStart", ((CheckBox) v).isChecked());
                myEditor.commit();
            }
        });

        ((CheckBox)findViewById(R.id.wired_key)).setChecked(SpeakService.myPreferences.getBoolean("wiredKey", false));
        setListener(R.id.wired_key, new View.OnClickListener() {
            public void onClick(View v) {
                SharedPreferences.Editor myEditor = SpeakService.myPreferences.edit();
                myEditor.putBoolean("wiredKey", ((CheckBox) v).isChecked());
                myEditor.commit();
            }
        });

        final EditText paraPauseEdit = (EditText)findViewById(R.id.paraPause);
        paraPauseEdit.setText(Integer.toString(SpeakService.myParaPause));
        paraPauseEdit.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                int pp;
                try {
                    pp = Integer.parseInt(s.toString());
                } catch (NumberFormatException e) {
                    pp = 0;
                }
                SpeakService.myParaPause = pp;
                if (pp < 0)
                    pp = 0;
                else if (pp > 5000)
                    pp = 5000;
                if (pp != SpeakService.myParaPause) {
                    paraPauseEdit.setText(Integer.toString(pp));
                }
                else {
                    SharedPreferences.Editor editor = SpeakService.myPreferences.edit();
                    editor.putInt("paraPause", pp);
                    editor.commit();
                }
                SpeakService.myParaPause = pp;
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        setListener(R.id.button_tts_set, new View.OnClickListener() {
            public void onClick(View v) {
                SpeakActivity.getCurrent().doDestroy();
                Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                TtsApp.ExitApp();
            }
        });

        setListener(R.id.button_back, new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        getWindow().setGravity(Gravity.BOTTOM);
        SpeakService.setSleepTimer(0); // removes sleep timer
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        System.gc();
    }

}
