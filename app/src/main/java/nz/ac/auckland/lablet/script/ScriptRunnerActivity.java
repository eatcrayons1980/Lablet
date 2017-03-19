/*
 * Copyright 2013-2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.script;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.MediaColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.ViewGroup;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import nz.ac.auckland.lablet.R;
import nz.ac.auckland.lablet.misc.PersistentBundle;
import nz.ac.auckland.lablet.misc.StorageLib;
import nz.ac.auckland.lablet.script.components.ScriptComponentFragmentFactory;
import nz.ac.auckland.lablet.script.components.ScriptComponentGenericFragment;
import nz.ac.auckland.lablet.utility.FileHelper;


/**
 * Activity that host one running script.
 */
public class ScriptRunnerActivity extends FragmentActivity implements IScriptListener {

    private static final String TAG = "ScriptRunnerActivity";
    private static final String SCRIPT_USER_DATA_FILENAME = "script_user_data.xml";
    private static final String SCRIPT_USER_DATA_DIR = "script_user_data_dir";
    private static final String SCRIPT_STUDENT_ANSWERS_FILENAME = "user_answers.txt";

    @Nullable
    public Script script = null;
    @Nullable
    private ViewPager pager = null;
    @Nullable
    private ScriptFragmentPagerAdapter pagerAdapter = null;
    private List<ScriptTreeNode> activeChain = new ArrayList<>();
    @Nullable
    private File scriptFile = null;
    private String lastErrorMessage = "";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int lastSelectedFragment;

        // load the script
        if (savedInstanceState != null) {
            final String userDataDir = savedInstanceState.getString(SCRIPT_USER_DATA_DIR);
            if (userDataDir == null) {
                showErrorAndFinish(
                    "Can't start script from saved instance state (user data directory is null)");
                return;
            }
            File scriptUserDataDir = new File(userDataDir);
            lastSelectedFragment = loadScriptStateFromFile(scriptUserDataDir);
            if (lastSelectedFragment < 0) {
                showErrorAndFinish("Can't continue script:", lastErrorMessage);
                return;
            }
        } else {
            // An ACTION_VIEW intent means a script has been shared with Lablet from another app
            final String action = getIntent().getAction();
            if (action != null && action.equals(Intent.ACTION_VIEW)) {
                setIntent(prepareExternalIntent(getIntent()));
            }
            lastSelectedFragment = createFromIntent();
            if (lastSelectedFragment < 0) {
                showErrorAndFinish("Can't start script", lastErrorMessage);
                return;
            }
        }

        // gui
        setContentView(R.layout.experiment_analyser);
        // Instantiate a ViewPager and a PagerAdapter.
        pager = (ViewPager) findViewById(R.id.pager);
        pagerAdapter = new ScriptFragmentPagerAdapter(getSupportFragmentManager(), activeChain);
        if (pager != null) {
            pager.setAdapter(pagerAdapter);
        }

        pagerAdapter.setComponents(activeChain);
        pager.setCurrentItem(lastSelectedFragment);

        if (script == null) {
            Log.e(TAG, "Cannot start script. Script object is null");
        } else {
            script.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        saveScriptStateToFile();
    }

    @Override
    protected void onSaveInstanceState(@Nullable Bundle outState) {
        super.onSaveInstanceState(outState);

        if (outState != null) {
            outState.putString(SCRIPT_USER_DATA_DIR,
                script == null ? "" : script.getUserDataDirectory().getPath());
        }
    }

    /**
     * Saves the state of the current script to the current script data dir.
     *
     * @return false if an error occurred.
     */
    public boolean saveScriptStateToFile() {
        if (script == null) {
            return false;
        }
        File scriptUserDataDir = script.getUserDataDirectory();
        if (scriptFile == null || scriptUserDataDir == null) {
            return false;
        }

        Bundle bundle = new Bundle();
        bundle.putString("script_name", scriptFile.getName());
        bundle.putInt("current_fragment", getCurrentPagerItem());
        if (!script.saveScriptState(bundle)) {
            return false;
        }

        // save script file
        File scriptFileTarget = new File(scriptUserDataDir, scriptFile.getName());
        if (!scriptFileTarget.exists()) {
            try {
                StorageLib.copyFile(scriptFile, scriptFileTarget);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        File projectFile = new File(scriptUserDataDir, SCRIPT_USER_DATA_FILENAME);
        FileWriter fileWriter;
        try {
            fileWriter = new FileWriter(projectFile);

            PersistentBundle persistentBundle = new PersistentBundle();
            persistentBundle.flattenBundle(bundle, fileWriter);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return saveUserAnswersToExperimentFile(bundle);
    }

    /**
     * @return integer value associated with current sheet
     */
    public int getCurrentPagerItem() {
        return pager != null ? pager.getCurrentItem() : -1;
    }

    /**
     * Extracts the user answers from a bundle and saves them to a file.
     *
     * This function is simply implemented to provide users with a more readable version of their
     * answers than what is provided in the XML data saved as the persistentBundle for the
     * application.
     *
     * This file is saved into the internal directory containing Lablet data for the experiment. It
     * may or may not be accessible to the user.
     *
     * @param bundle the top-level (script-level) bundle of data
     * @return true if everything is written successfully; false otherwise
     */
    private boolean saveUserAnswersToExperimentFile(@Nullable Bundle bundle) {
        if (bundle == null) {
            return false;
        }

        // get fileWriter object
        FileWriter fileWriter = FileHelper
            .experimentFileWriter(script, SCRIPT_STUDENT_ANSWERS_FILENAME);
        return fileWriter != null && saveUserAnswersToFile(bundle, fileWriter);
    }

    /**
     * Extracts the user answers from a bundle and saves them to a file.
     *
     * @param bundle the top-level (script-level) bundle of data
     * @param fileWriter the FileWriter object in which to write user answers
     * @return true if everything is written successfully; false otherwise
     */
    private boolean saveUserAnswersToFile(@Nullable Bundle bundle,
        @Nullable FileWriter fileWriter) {
        if (bundle == null || fileWriter == null) {
            return false;
        }

        try {
            // write timestamp and script name to file
            fileWriter.write((new Date()).toString() + "\n");
            fileWriter.write(bundle.getString("script_name", "unknown_script") + "\n\n");

            int i = 0;
            Bundle sheet;
            Bundle child;

            // for all sheets in bundle ("0", "1", "2", ...)
            while ((sheet = bundle.getBundle(Integer.toString(i++))) != null) {
                int j = 0;
                int k = 0;
                boolean sheet_label_printed = false;

                // for all child objects ("child0", "child1", "child2", ...)
                while ((child = sheet.getBundle("child" + Integer.toString(j++))) != null) {
                    String question;
                    String answer;

                    // if child has a "question" and "answer" field
                    if ((question = child.getString("question")) != null
                        && (answer = child.getString("answer")) != null) {

                        // print sheet title if not printed yet
                        if (!sheet_label_printed) {
                            fileWriter.write("Sheet" + Integer.toString(i - 1) + "\n--------\n");
                            sheet_label_printed = true;
                        }

                        // print question
                        fileWriter.write(String.format(Locale.US, "Q%d: %s\n", ++k, question));

                        // print answer
                        fileWriter.write(String.format(Locale.US, "%s\n\n", answer));
                    }
                }
            }
        } catch (IOException e) {
            // ERROR
            Log.e(TAG, "Error: ", e);
            Log.e(TAG, "Could not write user answers to file: " + fileWriter.toString());
            try {
                fileWriter.close();
            } catch (IOException e1) {
                Log.w(TAG, "Error: ", e1);
                Log.w(TAG, "Could not close user answer file: " + fileWriter.toString());
            }
            return false;
        }
        try {
            // flush and close file with user answers
            fileWriter.close();
        } catch (IOException e) {
            Log.w(TAG, "Error: ", e);
            Log.w(TAG, "Could not close user answer file: " + fileWriter.toString());
        }

        // SUCCESS
        Log.i(TAG, "User answer saved successfully to: " + fileWriter.toString());
        return true;
    }

    private void showErrorAndFinish(String error) {
        showErrorAndFinish(error, null);
    }

    /**
     * Load a script that has been stored in the given directory.
     *
     * @param scriptUserDataDir directory where the user data is stored
     * @return the index of the last selected fragment or -1 if an error occurred
     */
    private int loadScriptStateFromFile(File scriptUserDataDir) {
        File userDataFile = new File(scriptUserDataDir, SCRIPT_USER_DATA_FILENAME);

        Bundle bundle;
        InputStream inStream;
        try {
            inStream = new FileInputStream(userDataFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            lastErrorMessage = "can't open script file \"" + userDataFile.getPath() + "\"";
            return -1;
        }

        PersistentBundle persistentBundle = new PersistentBundle();
        try {
            bundle = persistentBundle.unflattenBundle(inStream);
        } catch (Exception e) {
            e.printStackTrace();
            lastErrorMessage = "can't read bundle from \"" + userDataFile.getPath() + "\"";
            return -1;
        }

        String scriptName = bundle.getString("script_name");
        if (scriptName == null) {
            lastErrorMessage = "bundle contains no script_name";
            return -1;
        }

        scriptFile = new File(scriptUserDataDir, scriptName);
        if (!loadScript(scriptFile, scriptUserDataDir)) {
            return -1;
        }

        if (script == null) {
            Log.e(TAG, "Script object is null");
            return -1;
        }

        if (!script.loadScriptState(bundle)) {
            lastErrorMessage = script.getLastError();
            script = null;
            return -1;
        }

        activeChain = script.getActiveChain();

        return bundle.getInt("current_fragment", 0);
    }

    private void showErrorAndFinish(String error, @Nullable String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(error);
        if (message != null) {
            builder.setMessage(message);
        }
        builder.setNeutralButton("Ok", null);
        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(dialogInterface -> finish());
        dialog.show();
    }

    /**
     * Transforms an intent with a script from an external application into a
     * new intent that will launch the shared script.
     * <p>
     * This method is used to start scripts which are in an unknown location. The
     * data is copied into a new script in the Lablet scripts directory, and an
     * intent to start the new script is returned.
     * </p>
     *
     * @param intent the intent received from the system
     * @return the new intent to be started
     */
    @Nullable
    private Intent prepareExternalIntent(@NonNull Intent intent) {
        Uri uri = intent.getData();
        File scriptCopy = copyScriptToScriptDirectory(uri);
        if (scriptCopy != null) {
            final File directory = FileHelper.getScriptUserDataDir(this);
            final String name = Script.generateScriptUid(scriptCopy.getName());
            File scriptUserDataDir = new File(directory, name);
            Intent newIntent = new Intent(this, ScriptRunnerActivity.class);
            newIntent.putExtra("script_path", scriptCopy.getPath());
            newIntent.putExtra("script_user_data_dir", scriptUserDataDir.getPath());
            return newIntent;
        }
        return null;
    }

    private int createFromIntent() {
        Intent intent = getIntent();
        final String scriptPath = intent.getStringExtra("script_path");
        final String userDataDir = intent.getStringExtra("script_user_data_dir");

        if (userDataDir == null) {
            lastErrorMessage = "user data directory is missing";
            return -1;
        }
        File scriptUserDataDir = new File(userDataDir);
        if (!scriptUserDataDir.exists()) {
            if (!scriptUserDataDir.mkdir()) {
                lastErrorMessage = "can't create user data directory";
                return -1;
            }
        }

        if (scriptPath != null) {
            // start new script
            scriptFile = new File(scriptPath);
            if (loadScript(scriptFile, scriptUserDataDir)) {
                if (script == null) {
                    Log.e(TAG, "Script object is null");
                } else {
                    activeChain = script.getActiveChain();
                }
                return 0;
            }
            StorageLib.recursiveDeleteFile(scriptUserDataDir);
            return -1;
        } else {
            return loadScriptStateFromFile(scriptUserDataDir);
        }
    }

    private boolean loadScript(File scriptFile, File scriptUserDataDir) {
        ScriptComponentFragmentFactory factory = new ScriptComponentFragmentFactory();
        LuaScriptLoader loader = new LuaScriptLoader(factory);
        script = loader.load(scriptFile);
        if (script == null) {
            lastErrorMessage = loader.getLastError();
            return false;
        }

        script.setUserDataDirectory(scriptUserDataDir);
        script.setListener(this);
        return true;
    }

    /**
     * Takes a Uri (probably a 'content:' Uri) and copies it into the Lablet directory.
     * <p>
     * This is mostly used to support launching scripts directly into Lablet, without
     * needing to copy the script into the correct folder manually.
     * </p>
     * <p>
     * Please note that this will overwrite any file with the same name in the scripts
     * directory.
     * </p>
     *
     * @param uri the Uri to copy
     * @return the copy of the file
     */
    @Nullable
    private File copyScriptToScriptDirectory(@Nullable Uri uri) {
        if (uri == null) {
            return null;
        }

        // compute the file name
        String fileName = computeFileName(uri);

        // copy script into the Lablet scripts directory
        File scriptCopy = new File(FileHelper.getScriptDirectory(this), fileName);
        try {
            byte[] buf = new byte[4096];
            int length;
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                throw new IOException("could not open input stream");
            }
            FileOutputStream outputStream = new FileOutputStream(scriptCopy);
            while ((length = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, length);
            }
            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            return null;
        }

        return scriptCopy;
    }

    @Nullable
    private String computeFileName(@NonNull Uri uri) {

        // 'content' scheme will include the file name in the meta data
        if (uri.getScheme().equals("content")) {

            // open the Uri meta data so we can read the file name
            final String[] nameProjection = {MediaColumns.DISPLAY_NAME};
            Cursor uriMetaData = getContentResolver().query(uri, nameProjection, null, null, null);
            if (uriMetaData == null) {
                return null;
            }

            // move to first row of uri meta data and read display name, return if this fails
            final int displayName = 0;
            try {
                return uriMetaData.moveToFirst() ? uriMetaData.getString(displayName) : null;
            } finally {
                uriMetaData.close();
            }
        }

        // 'file' scheme will have the file name stored in the path
        if (uri.getScheme().equals("file")) {
            return uri.getLastPathSegment();
        }

        // unsupported scheme
        return null;
    }

    @Nullable
    public ScriptTreeNode getScriptComponentTreeAt(int index) {
        if (index < 0 || index >= activeChain.size()) {
            return null;
        }
        return activeChain.get(index);
    }

    public int getScriptComponentIndex(ScriptTreeNode component) {
        return activeChain.indexOf(component);
    }

    @Nullable
    public File getScriptUserDataDir() {
        return script != null ? script.getUserDataDirectory() : null;
    }

    @Override
    public void onComponentStateChanged(ScriptTreeNode current, int state) {
        if (pagerAdapter == null) {
            return;
        }

        ScriptTreeNode lastSelectedComponent = null;
        if (activeChain.size() > 0) {
            lastSelectedComponent = activeChain.get(getCurrentPagerItem());
        }
        if (script == null) {
            Log.e(TAG, "Script object is null");
        } else {
            activeChain = script.getActiveChain();
        }
        pagerAdapter.setComponents(activeChain);

        int index = activeChain.indexOf(lastSelectedComponent);
        if (index < 0) {
            index = activeChain.size() - 1;
        }
        if (index >= 0 && pager != null) {
            pager.setCurrentItem(index);
        }
    }

    public void setNextComponent(ScriptTreeNode next) {
        int index = activeChain.indexOf(next);
        if (index > 0 && pager != null) {
            pager.setCurrentItem(index);
        }
    }

    private class ScriptFragmentPagerAdapter extends FragmentStatePagerAdapter {

        @NonNull
        private final Map<ScriptTreeNode, ScriptComponentGenericFragment> fragmentMap = new HashMap<>();

        private List<ScriptTreeNode> components;

        ScriptFragmentPagerAdapter(android.support.v4.app.FragmentManager fragmentManager,
            List<ScriptTreeNode> components) {
            super(fragmentManager);
            this.components = components;
        }

        public void setComponents(List<ScriptTreeNode> components) {
            this.components = components;
            notifyDataSetChanged();
        }

        @Override
        public android.support.v4.app.Fragment getItem(int position) {
            ScriptTreeNodeFragmentHolder fragmentCreator
                = (ScriptTreeNodeFragmentHolder) components.get(position);
            ScriptComponentGenericFragment fragment = fragmentCreator.createFragment();
            fragmentMap.put(components.get(position), fragment);
            return fragment;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, @NonNull Object object) {
            super.destroyItem(container, position, object);
            fragmentMap.remove(findComponentFor((Fragment) object));
        }

        private ScriptTreeNode findComponentFor(Fragment fragment) {
            for (Map.Entry<ScriptTreeNode, ScriptComponentGenericFragment> entry : fragmentMap
                .entrySet()) {
                if (entry.getValue() == fragment) {
                    return entry.getKey();
                }
            }
            return null;
        }

        @Override
        public int getCount() {
            return components.size();
        }

        // disable this code since it causes some invalidate problems, e.g., when starting a sub activity and going

        // going back some pages are not invalidated completely!
        /*@Override
        public int getItemPosition(Object object) {
            Fragment fragment = (Fragment)object;
            ScriptComponentTree component = findComponentFor(fragment);
            if (component == null)
                return POSITION_NONE;

            int index = components.indexOf(component);
            assert index >= 0;

            return index;
        }*/
    }
}
