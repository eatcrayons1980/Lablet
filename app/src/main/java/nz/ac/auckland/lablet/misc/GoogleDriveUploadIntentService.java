package nz.ac.auckland.lablet.misc;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import net.openid.appauth.AuthState;

import org.json.JSONException;

import java.io.File;

/**
 * An {@link IntentService} subclass for uploading files to Google Drive on a separate
 * handler thread.
 * <p>
 * To use this service successfully, it must first be sent a serialized AuthState object,
 * which is used to handle API access.
 */
public class GoogleDriveUploadIntentService extends IntentService {
    private static final String TAG = "GoogleDriveUpload";

    private static final String ACTION_DRIVE_UPLOAD = "nz.ac.auckland.lablet.misc.action.DRIVE_UPLOAD";
    private static final String ACTION_AUTHORIZE = "nz.ac.auckland.lablet.misc.action.AUTHORIZE";

    private static final String EXTRA_AUTHORIZATION_STATE = "nz.ac.auckland.lablet.misc.extra.STATE";

    private static AuthState authState = new AuthState();

    public GoogleDriveUploadIntentService() {
        super("GoogleDriveUploadIntentService");
    }

    /**
     * Sets the Google OAuth2 state to allow API calls. This must be performed before
     * ACTION_DRIVE_UPLOAD can be used.
     */
    public static void startActionAuthState(Context context, String authState) {
        Intent intent = new Intent(context, GoogleDriveUploadIntentService.class);
        intent.setAction(ACTION_AUTHORIZE);
        intent.putExtra(EXTRA_AUTHORIZATION_STATE, authState);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action 'Drive Upload' with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startActionDriveUpload(Context context, Uri directory) {
        Intent intent = new Intent(context, GoogleDriveUploadIntentService.class);
        intent.setAction(ACTION_DRIVE_UPLOAD);
        intent.setData(directory);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_AUTHORIZE.equals(action)) {
                final String authStateJson = intent.getStringExtra(EXTRA_AUTHORIZATION_STATE);
                handleActionAuthorize(authStateJson);
            }
            if (authState.isAuthorized() && ACTION_DRIVE_UPLOAD.equals(action)) {
                final Uri directory = intent.getData();
                handleActionDriveUpload(directory);
            }
        }
    }

    private void handleActionAuthorize(String authStateJson) {
        try {
            authState = AuthState.jsonDeserialize(authStateJson);
        } catch (JSONException e) {
            Log.d(TAG, "JSON is malformed.");
            e.printStackTrace();
        }
        if (!authState.isAuthorized()) {
            Log.d(TAG, "AuthState received is unauthorized.");
        }
    }

    /**
     * Handle ACTION_DRIVE_UPLOAD in the provided background thread with the provided
     * parameters.
     */
    private void handleActionDriveUpload(Uri directory) {
        assert authState.isAuthorized();
        Log.d(TAG, "Not yet implemented. Cannot upload " + directory);
    }
}
