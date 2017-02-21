package nz.ac.auckland.lablet;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Intent;

/**
 * Lablet application class. This subclassing was performed to assure privacy policy is displayed in
 * accordance with Google requirements.
 */
public class Lablet extends Application {
    public static boolean privacyPolicyApproved = false;
    private static AlertDialog privacyDialog;

    /**
     * On creation of the application, privacy policy must be approved. One time approval is not
     * acceptable due to the possibility of multiple users using one account in a lab setting.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        privacyDialog = null;
        privacyPolicyApproved = false;
    }

    /**
     * Check is privacy policy has been approved. Display policy dialog if necessary.
     *
     * @param activity context for dialog
     */
    public void ensurePrivacyPolicy(Activity activity) {
        if (privacyDialog != null && privacyDialog.isShowing())
            privacyDialog.cancel();
        if (!privacyPolicyApproved) {
            privacyDialog = new AlertDialog.Builder(activity)
                    .setCancelable(false)
                    .setTitle(activity.getString(R.string.privacy_title))
                    .setMessage(activity.getString(R.string.privacy_message))
                    .setPositiveButton(activity.getString(R.string.privacy_accept), (dialog, i) -> {
                        privacyPolicyApproved = true;
                        dialog.dismiss();
                    })
                    .setNegativeButton(activity.getString(R.string.privacy_reject), (dialog, i) -> {
                        dialog.cancel();
                        Intent goHome = new Intent(Intent.ACTION_MAIN);
                        goHome.addCategory(Intent.CATEGORY_HOME);
                        goHome.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(goHome);
                    })
                    .create();
            privacyDialog.show();
        }
    }
}
