package nz.ac.auckland.lablet.utility;

import static android.opengl.GLU.gluErrorString;

import android.opengl.GLES20;
import android.util.Log;
import nz.ac.auckland.lablet.BuildConfig;

/**
 * Commonly used methods for working with OpenGL code.
 */
public class OpenGLHelper {
    private static final String TAG = "OpenGLHelper";

    public static void checkGlError(String op) {
        int error;
        if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + gluErrorString(error));
            // it doesn't make sense to crash the app just because there is an error.
            // better to let the app crash when it can no longer carry on.
            // throw new RuntimeException(op + ": glError " + gluErrorString(error));
        }
    }
}
