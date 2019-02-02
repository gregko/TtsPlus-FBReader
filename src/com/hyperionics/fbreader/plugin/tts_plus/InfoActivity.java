package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import com.hyperionics.ttssetup.Lt;
import org.geometerplus.android.fbreader.api.ApiClientImplementation;
import org.geometerplus.android.fbreader.api.PluginApi;

/**
 * Created by greg on 1/8/14.
 */
public class InfoActivity extends Activity {

    static final String FBR_PACKAGE = "org.geometerplus.zlibrary.ui.android";
    static final String FBR_PACKAGE_PREMIUM = "com.fbreader";
    private PackageManager myPm;
    private boolean _fbrInstalled = false;
    private static boolean _isInfoShowing = false;
    ComponentName myCn;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StartupActivity.originalIntent = getIntent();
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
            SpeakActivity.wantFBReaderStarted = false;
            startService(new Intent(TtsApp.getContext(), SpeakService.class));
        }
        myPm = getPackageManager();
        myCn = getComponentName();
        _fbrInstalled = myPm.getLaunchIntentForPackage(FBR_PACKAGE_PREMIUM) != null;
        if (!_fbrInstalled)
            _fbrInstalled = myPm.getLaunchIntentForPackage(FBR_PACKAGE) != null;
        boolean autoStartSpeech = getSharedPreferences("atVoice", MODE_PRIVATE).getBoolean("speakFromIcon", false);
        boolean showAbout = getIntent().getBooleanExtra("showAbout", false);

        if (_fbrInstalled && autoStartSpeech && !showAbout) {
            fbReaderClick(null); // will finish()
            return;
        }

        setContentView(R.layout.info_panel);
        TextView tv = (TextView) findViewById(R.id.vtext);
        String s = tv.getText().toString() + " " + TtsApp.versionName;
        tv.setText(s);

        Button fbrButton = (Button) findViewById(R.id.start_fbr);
        RadioGroup rg = (RadioGroup) findViewById(R.id.startup_opts);
        if (showAbout) {
            fbrButton.setVisibility(View.GONE);
            findViewById(R.id.long_info).setVisibility(View.GONE);
        }
        else if (_fbrInstalled) {
            fbrButton.setText(R.string.start_fbr);
            findViewById(R.id.long_info).setVisibility(View.GONE);
        }
        else {
            fbrButton.setText(R.string.install_fbr);
            rg.setVisibility(View.GONE);
            findViewById(R.id.icons_info).setVisibility(View.GONE);
        }

        boolean hideLauncherIcon = getSharedPreferences("atVoice", MODE_PRIVATE).getBoolean("hideLauncherIcon", false) ||
            myPm.getComponentEnabledSetting(myCn) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (hideLauncherIcon) {
            rg.check(R.id.remove_icon);
        }
        else if (autoStartSpeech) {
            rg.check(R.id.start_speech);
        }
        else {
            rg.check(R.id.show_icon);
        }
    }

    public void onIconOpts(View view) {
        RadioButton rb = (RadioButton) view;
        SharedPreferences.Editor edt = getSharedPreferences("atVoice", MODE_PRIVATE).edit();
        if (rb.getId() == R.id.remove_icon) {
            myPm.setComponentEnabledSetting(myCn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            edt.putBoolean("speakFromIcon", false);
            edt.putBoolean("hideLauncherIcon", true);
            Lt.alert(this, R.string.remove_info);
        } else if (rb.getId() == R.id.show_icon) {
            myPm.setComponentEnabledSetting(myCn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            edt.putBoolean("speakFromIcon", false);
            edt.remove("hideLauncherIcon");
        } else if (rb.getId() == R.id.start_speech) {
            myPm.setComponentEnabledSetting(myCn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            edt.putBoolean("speakFromIcon", true);
            edt.remove("hideLauncherIcon");
        }
        edt.commit();
    }

    @Override
    public void onResume() {
        _isInfoShowing = true;
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getSharedPreferences("atVoice", MODE_PRIVATE).getBoolean("hideLauncherIcon", false))
            myPm.setComponentEnabledSetting(myCn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        _isInfoShowing = false;
    }

    public static boolean isShowing() { return _isInfoShowing; }

    public void atVoiceClick(View view) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("http://hyperionics.com/atVoice/index.asp"));
        startActivity(browserIntent);
    }

    public void fbReaderClick(View viewNotUsed) {
        _isInfoShowing = false;
        boolean launchPremium = true;
        Intent launchIntent = myPm.getLaunchIntentForPackage(FBR_PACKAGE_PREMIUM);
        if (launchIntent == null) {
            launchIntent = myPm.getLaunchIntentForPackage(FBR_PACKAGE);
            launchPremium = false;
        }
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
            // New method:
            // The 2 lines below will be enough to start FBReader and plugin once Nikolay fixes
            // FBReader issue (handle PLUGIN intent not only in onNewIntent(), but also in onCreate()
            // in FBReader.java
//            launchIntent.setAction("android.fbreader.action.PLUGIN");
//            launchIntent.setData(Uri.parse("http://hyperionics.com/plugin/tts_plus/speak"));
//            startActivity(launchIntent);

            // Old method:
            StartupActivity.originalIntent.setAction((launchPremium ?
                    ApiClientImplementation.FBREADER_PREMIUM_PREFIX : ApiClientImplementation.FBREADER_PREFIX) +
                    PluginApi.ACTION_RUN_POSTFIX);
            startActivity(launchIntent);
            if (SpeakService.getCurrentService() == null)
                TtsApp.getContext().startService(new Intent(TtsApp.getContext(), SpeakService.class));
            //IncomingReceiver.startSpeakActivityDelayed(0); // startService() will start the activity when all ready
        }
        finish();
    }

    public void onRate(View view) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=com.hyperionics.fbreader.plugin.tts_plus"));
        startActivity(browserIntent);
        finish();
    }

    public void onReturn(View view) {
        finish();
    }
}
