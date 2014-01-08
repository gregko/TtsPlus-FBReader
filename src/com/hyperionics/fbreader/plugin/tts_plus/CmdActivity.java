package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.hyperionics.TtsSetup.Lt;

import java.io.*;
import java.nio.channels.FileChannel;

/**
 * Activity for executing remote commands only
 */
public class CmdActivity extends Activity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent != null && "com.hyperionics.fbreader.plugin.tts_plus.COPY_SPEECH_FILES".equals(intent.getAction())) {
            final String dstPath = intent.getStringExtra("DST_PATH");
            if (dstPath != null) {
                File dst = new File(dstPath);
                final File src = new File(SpeakService.getConfigPath(true));
                if (dst.isDirectory() && dst.canWrite() && src.isDirectory() && src.canRead()) {
                    String [] copied = src.list(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String fname) {
                            boolean bReturn = false;
                            if (fname.startsWith("replace-") && fname.endsWith(".txt")) try {
                                Lt.d("Copying: " + fname);
                                copyFile(new File(src.getAbsolutePath() + "/" + fname), new File(dstPath + "/" + fname));
                                bReturn = true;
                            } catch (IOException e) {}
                            return bReturn;
                        }
                    });
                    intent.putExtra("NUM_COPIED", copied.length);
                }
            }
            setResult(RESULT_OK, intent);
        }
        finish();
    }

    private static void copyFile(File source, File dest)
            throws IOException {
        FileChannel inputChannel = null;
        FileChannel outputChannel = null;
        try {
            inputChannel = new FileInputStream(source).getChannel();
            outputChannel = new FileOutputStream(dest).getChannel();
            outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
        } finally {
            inputChannel.close();
            outputChannel.close();
        }
    }
}