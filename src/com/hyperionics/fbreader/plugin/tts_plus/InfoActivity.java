package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import com.hyperionics.TtsSetup.Lt;

/**
 * Created by greg on 1/8/14.
 */
public class InfoActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.info_panel);
        int state = getPackageManager().getComponentEnabledSetting(getComponentName());
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            CheckBox cb = (CheckBox) findViewById(R.id.remove_icon);
            cb.setChecked(true);
        }
    }

    public void onRemoveIcon(View view) {
        CheckBox cb = (CheckBox) view;
        PackageManager p = getPackageManager();
        if (cb.isChecked()) {
            p.setComponentEnabledSetting(getComponentName(), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            Lt.alert(this, R.string.remove_info);
        } else {
            p.setComponentEnabledSetting(getComponentName(), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public void atVoiceClick(View view) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("http://hyperionics.com/atVoice/index.asp"));
        startActivity(browserIntent);
    }

    public void fbReaderClick(View view) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("org.geometerplus.zlibrary.ui.android");
        if (launchIntent == null) {
            try {
                if (InstallInfo.installedFromAma()) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("amzn://apps/android?p=org.geometerplus.zlibrary.ui.android")));
                } else {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=org.geometerplus.zlibrary.ui.android")));
                }
            } catch (android.content.ActivityNotFoundException anfe) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://play.google.com/store/apps/details?id=org.geometerplus.zlibrary.ui.android")));
            }
        } else {
            startActivity(launchIntent);
            finish();
        }
    }
}
