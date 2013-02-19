package com.hyperionics.fbreader.plugin.tts_plus;

import android.app.Activity;
import com.amazon.inapp.purchasing.BasePurchasingObserver;
import com.amazon.inapp.purchasing.PurchasingManager;

/**
 * Purchasing Observer will be called on by the Purchasing Manager asynchronously.
 * Since the methods on the UI thread of the application, all fulfillment logic is done via an AsyncTask. This way, any
 * intensive processes will not hang the UI thread and cause the application to become
 * unresponsive.
 */
public class AmaObserver extends BasePurchasingObserver {

    private final Activity baseActivity;
    private static boolean amaInstalled = false;

    public AmaObserver(Activity activity) {
        super(activity);
        this.baseActivity = activity;
        PurchasingManager.registerObserver(this);
    }

    /**
     * Invoked once the observer is registered with the Puchasing Manager If the boolean is false, the application is
     * receiving responses from the SDK Tester. If the boolean is true, the application is live in production.
     *
     * @param isSandboxMode
     *            Boolean value that shows if the app is live or not.
     */
    @Override
    public void onSdkAvailable(final boolean isSandboxMode) {
        Lt.df("Amazon onSdkAvailable recieved: isSandboxMode -" + isSandboxMode);
        amaInstalled = !isSandboxMode;
        // PurchasingManager.initiateGetUserIdRequest(); etc. - when we need to use more of this stuff.
    }

    public static boolean installedFromAma() {
        return amaInstalled;
    }

}
