package nz.ac.auckland.lablet.utility;

import android.content.Context;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Locale;
import nz.ac.auckland.lablet.script.Script;
import org.jetbrains.annotations.Contract;
import org.opencv.core.Point;

/**
 * A collection of utility methods for file access in Lablet.
 */
public final class FileHelper {

    private static final String TAG = "FileHelper";

    private FileHelper() {
        // should not be invoked
    }

    /**
     * Generates a {@link FileOutputStream} for writing to the file with the given filename in the
     * user Downloads directory.
     *
     * @param filename name of file linked to {@link FileOutputStream}
     * @return a {@link FileOutputStream} object for writing to the file
     */
    @Contract("null -> null")
    public static FileOutputStream downloadFileOutputStream(String filename) {
        if (filename == null || filename.equals(""))
            return null;

        // make sure external storage is accessible
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Log.e(TAG, "External storage is currently unavailable.");
            return null;
        }

        // get external download directory
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        Log.d(TAG, "External directory is: " + dir.toString());

        // make sure download directory exists
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.e(TAG, "External directory is not accessible and cannot be created.");
            return null;
        }
        Log.d(TAG, "Opening FileOutputStream to file in Downloads directory.");
        return getFileOutputStream(dir, filename);
    }

    /**
     * Calls the {@link #getFileWriter(File, String)} method, specifying the user Downloads directory.
     *
     * @param filename name of file linked to {@link FileWriter}
     * @return a {@link FileWriter} object for writing to the file
     */
    @Contract("null -> null")
    public static FileWriter downloadFileWriter(String filename) {
        if (filename == null || filename.equals(""))
            return null;

        // make sure external storage is accessible
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Log.e(TAG, "External storage is currently unavailable.");
            return null;
        }

        // get external download directory
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        Log.d(TAG, "External directory is: " + dir.toString());

        // make sure download directory exists
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.e(TAG, "External directory is not accessible and cannot be created.");
            return null;
        }
        Log.d(TAG, "Opening FileWriter to file in Downloads directory.");
        return getFileWriter(dir, filename);
    }

    /**
     * Generates a {@link FileOutputStream} for writing to the file with the given filename in the
     * Lablet experiments directory.
     *
     * @param script a reference to the {@link Script} object, so we can find the script directory
     * @param filename name of file linked to {@link FileOutputStream}
     * @return {@link FileOutputStream} object for writing to the file
     */
    @Contract("null, _ -> null; _, null -> null")
    public static FileOutputStream experimentFileOutputStream(Script script, String filename) {
        if (script == null || filename == null || filename.equals(""))
            return null;

        Log.d(TAG, "Opening FileOutputStream to file in Experiment directory: " + script.getUserDataDirectory().getAbsolutePath() + "/" + filename);
        return getFileOutputStream(script.getUserDataDirectory(), filename);
    }

    /**
     * Calls the {@link #getFileWriter(File, String)} method, specifying the directory for the experiment.
     *
     * @param script a reference to the {@link Script} object, so we can find the script directory
     * @param filename name of file linked to {@link FileWriter}
     * @return a {@link FileWriter} object for writing to the file
     */
    @Contract("null, null -> null")
    public static FileWriter experimentFileWriter(Script script, String filename) {
        if (script == null || filename == null || filename.equals(""))
            return null;

        Log.d(TAG, "Opening FileWriter to file in Experiment directory: " + script.getUserDataDirectory().getAbsolutePath() + "/" + filename);
        return getFileWriter(script.getUserDataDirectory(), filename);
    }

    /**
     * Obtain a {@link FileWriter} object.
     *
     * @param dir directory for the file
     * @param filename name of file
     * @return {@link FileWriter} object for writing to the file
     */
    @Nullable
    private static FileWriter getFileWriter(File dir, String filename) {
        FileWriter fileWriter;

        // open user data file for writing
        try {
            fileWriter = new FileWriter(new File(dir, filename));
        } catch (IOException e) {
            Log.e(TAG, "Error: ", e);
            Log.e(TAG, "Could not access file: " + filename);
            return null;
        }
        return fileWriter;
    }

    /**
     * Obtain a {@link FileOutputStream} object.
     *
     * @param dir directory for the file
     * @param filename name of file
     * @return {@link FileOutputStream} object for writing to the file
     */
    @Nullable
    private static FileOutputStream getFileOutputStream(File dir, String filename) {
        FileOutputStream fileOutputStream;

        // open user data file for writing
        try {
            fileOutputStream = new FileOutputStream(new File(dir, filename));
        } catch (IOException e) {
            Log.e(TAG, "Error: ", e);
            Log.e(TAG, "Could not access file: " + filename);
            return null;
        }
        return fileOutputStream;
    }

    /**
     * Writes a list of points to a CSV file.
     *
     * @param points points to put in the file
     * @param writer {@link FileWriter} to use when writing the file
     * @return false if writing fails
     */
    @Contract("_, null -> false")
    static public boolean writePlotDataToCSV(List<Point> points, Writer writer) {
        if (writer == null)
            return false;
        try {
            writer.write("x,y\n");
            for (Point point : points) {
                writer.write(String.format(Locale.UK, "%f,%f\n", point.x, point.y));
            }
            writer.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not write CSV file");
            return false;
        }
        Log.i(TAG, "CSV file written successfully");
        return true;
    }

    /**
     * Accepts {@link String} objects ending in '.lua'
     *
     * @param name file name to test
     * @return true if {@link String} ends in '.lua'
     */
    @Contract("null -> false")
    public static boolean isLuaFile(@Nullable String name) {
        return name != null && name.length() >= 5 && name.lastIndexOf(".lua") == name.length() - 4;
    }

    /**
     * The script user data is the directory that contains the stored script state, i.e., the
     * results.
     *
     * @param context the context
     * @return the script user data
     */
    @Nullable
    static public File getScriptUserDataDir(@Nullable Context context) {
        if (context == null) {
            return null;
        }
        File baseDir = context.getExternalFilesDir(null);
        File scriptDir = new File(baseDir, "script_user_data");
        if (!scriptDir.exists() && !scriptDir.mkdir()) {
            return null;
        }
        return scriptDir;
    }

    /**
     * The script directory is the directory the stores the script files, i.e., the lua files.
     *
     * @param context the context
     * @return the script directory File
     */
    @Nullable
    static public File getScriptDirectory(@Nullable Context context) {
        if (context == null) {
            return null;
        }
        File baseDir = context.getExternalFilesDir(null);
        File scriptDir = new File(baseDir, "scripts");
        if (!scriptDir.exists() && !scriptDir.mkdir()) {
            return null;
        }
        return scriptDir;
    }
}
