package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.*;
import com.hyperionics.TtsSetup.*;

import java.util.Locale;

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
        requestWindowFeature(Window.FEATURE_NO_TITLE);
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
            public void onClick(View v) {
                SharedPreferences.Editor myEditor = SpeakService.getPrefs().edit();
                SpeakService.mySentences = new TtsSentenceExtractor.SentenceIndex[0];
                SpeakService.myHighlightSentences = ((CheckBox) v).isChecked();
                myEditor.putBoolean("hiSentences", SpeakService.myHighlightSentences);
                myEditor.commit();
            }
        });

        ((CheckBox)findViewById(R.id.plug_start)).setChecked(SpeakService.getPrefs().getBoolean("plugStart", false));
        setListener(R.id.plug_start, new View.OnClickListener() {
            public void onClick(View v) {
                SharedPreferences.Editor myEditor = SpeakService.getPrefs().edit();
                myEditor.putBoolean("plugStart", ((CheckBox) v).isChecked());
                myEditor.commit();
            }
        });

        ((CheckBox)findViewById(R.id.fbr_headset_start)).setChecked(SpeakService.getPrefs().getBoolean("fbrStart", false));
        setListener(R.id.fbr_headset_start, new View.OnClickListener() {
            public void onClick(View v) {
                SharedPreferences.Editor myEditor = SpeakService.getPrefs().edit();
                myEditor.putBoolean("fbrStart", ((CheckBox) v).isChecked());
                myEditor.commit();
            }
        });

        if (Build.VERSION.SDK_INT > 14) {
            int netSynth = 2;
            try {
                netSynth = SpeakService.getPrefs().getInt("netSynth", 2); // bit 0 - use net, bit 1 - wifi only
            } catch (ClassCastException e) {
                SharedPreferences.Editor ed = SpeakService.getPrefs().edit();
                ed.remove("netSynth");
                ed.commit();
            }
            boolean useNetSynth = (netSynth & 1) == 1;
            boolean onlyWiFi = (netSynth & 2) == 2;
            ((CheckBox)findViewById(R.id.net_synth)).setChecked(useNetSynth);
            final CheckBox wifiCb = (CheckBox)findViewById(R.id.net_synth_wifi);
            wifiCb.setEnabled(useNetSynth);
            wifiCb.setChecked(onlyWiFi);
            setListener(R.id.net_synth, new View.OnClickListener() {
                public void onClick(View v) {
                    int n = SpeakService.getPrefs().getInt("netSynth", 2); // bit 0 - use net, bit 1 - wifi only
                    if (((CheckBox) v).isChecked()) {
                        n |= 1;
                        wifiCb.setEnabled(true);
                    } else {
                        n &= ~1;
                        wifiCb.setEnabled(false);
                    }
                    SharedPreferences.Editor myEditor = SpeakService.getPrefs().edit();
                    myEditor.putInt("netSynth", n);
                    myEditor.commit();
                }
            });
            setListener(R.id.net_synth_wifi, new View.OnClickListener() {
                public void onClick(View v) {
                    int n = SpeakService.getPrefs().getInt("netSynth", 2); // bit 0 - use net, bit 1 - wifi only
                    if (((CheckBox) v).isChecked())
                        n |= 2;
                    else
                        n &= ~2;
                    SharedPreferences.Editor myEditor = SpeakService.getPrefs().edit();
                    myEditor.putInt("netSynth", n);
                    myEditor.commit();
                }
            });
        } else {
            findViewById(R.id.net_synth).setVisibility(View.GONE);
            findViewById(R.id.net_synth_wifi).setVisibility(View.GONE);
        }

        ((CheckBox)findViewById(R.id.word_opts)).setChecked(SpeakService.getPrefs().getBoolean("WORD_OPTS", false));
        setListener(R.id.word_opts, new View.OnClickListener() {
            public void onClick(View v) {
                SharedPreferences.Editor myEditor = SpeakService.getPrefs().edit();
                myEditor.putBoolean("WORD_OPTS", ((CheckBox) v).isChecked());
                myEditor.commit();
            }
        });

        final RadioButton rbDim = (RadioButton)findViewById(R.id.screenDimmed);
        final RadioButton rbBright = (RadioButton)findViewById(R.id.screenBright);
        final RadioGroup grp = (RadioGroup) findViewById(R.id.screen_level);
        int n = SpeakService.getPrefs().getInt("screenOn", 0);
        ((CheckBox)findViewById(R.id.screen_on)).setChecked(n > 0);
        if (n == 0) {
            grp.setVisibility(View.GONE);
        }
        setListener(R.id.screen_on, new View.OnClickListener() {
            public void onClick(View v) {
                Boolean enable = ((CheckBox) v).isChecked();
                grp.setVisibility(enable ? View.VISIBLE : View.GONE);
                if (enable)
                    grp.check(R.id.screenDimmed);
                SharedPreferences.Editor edt = SpeakService.getPrefs().edit();
                int scStat = enable ? 1  : 0;
                edt.putInt("screenOn", scStat);
                Lt.d("screenOn = " + scStat);
                edt.commit();
            }
        });
        grp.check(R.id.screenDimmed - 1 + n);
        grp.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int i) {
                SharedPreferences.Editor edt = SpeakService.getPrefs().edit();
                int n = i - R.id.screenDimmed + 1;
                edt.putInt("screenOn", n);
                Lt.d("screenOn = " + n);
                edt.commit();
            }
        });

        CheckBox avarSpeechBtn = ((CheckBox)findViewById(R.id.use_avar_speech));
        if (SpeakActivity.avarDefaultPath != null) {
            avarSpeechBtn.setChecked(SpeakService.getPrefs().getBoolean("AVAR_SPEECH", false));
            avarSpeechBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    SpeakService.getPrefs().edit().putBoolean("AVAR_SPEECH", isChecked).commit();
                    CldWrapper.initExtractorNative(SpeakService.getConfigPath(),
                            LangSupport.getIso3Lang(new Locale(SpeakService.getCurrentBookLanguage())), 0, null);
                }
            });
        } else {
            avarSpeechBtn.setEnabled(false);
        }

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
                    SharedPreferences.Editor editor = SpeakService.getPrefs().edit();
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
                SpeakActivity sa = SpeakActivity.getCurrent();
                if (Build.VERSION.SDK_INT >= 14) {
                    finish();
                    if (sa != null)
                        sa.selectTtsEngine();
                } else {
                    Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(intent);
                    finish();
                    if (sa != null) {
                        sa.doDestroy();
                        sa.finish();
                    }
                    TtsApp.ExitApp();
                }
            }
        });

        setListener(R.id.edit_speech_btn, new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(SettingsActivity.this, EditSpeech.class);
                String eng = LangSupport.getSelectedTtsEng(); // mySelectedEngine.name();
                if (eng != null)
                    intent.putExtra(VoiceSelector.SELECTED_ENGINE, eng);
                String voi = LangSupport.getPrefferedVoice(LangSupport.getIso3Lang(new Locale(SpeakService.getCurrentBookLanguage())));
                if (voi != null)
                    voi = voi.substring(0, voi.lastIndexOf('|'));
                else
                    voi = LangSupport.getIso3Lang(new Locale(SpeakService.getCurrentBookLanguage()));
                intent.putExtra(VoiceSelector.SELECTED_VOICE, voi);
                intent.putExtra(VoiceSelector.CONFIG_DIR, SpeakService.getConfigPath());
                startActivity(intent);
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
