/*
 * Copyright 2013-2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.script;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.ViewGroup;
import nz.ac.auckland.lablet.misc.PersistentBundle;
import nz.ac.auckland.lablet.misc.StorageLib;
import nz.ac.auckland.lablet.script.components.ScriptComponentFragmentFactory;
import nz.ac.auckland.lablet.script.components.ScriptComponentGenericFragment;
import nz.ac.auckland.lablet.R;

import java.io.*;
import java.util.*;


/**
 * Activity that host one running script.
 */
public class ScriptRunnerActivity extends FragmentActivity implements IScriptListener {
    private Script script = null;
    private ViewPager pager = null;
    private ScriptFragmentPagerAdapter pagerAdapter = null;
    private List<ScriptTreeNode> activeChain = new ArrayList<>();

    private File scriptFile = null;
    private String lastErrorMessage = "";

    final String SCRIPT_USER_DATA_FILENAME = "script_user_data.xml";
    final String SCRIPT_USER_DATA_DIR = "script_user_data_dir";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int lastSelectedFragment;

        // load the script
        if (savedInstanceState != null) {
            final String userDataDir = savedInstanceState.getString(SCRIPT_USER_DATA_DIR);
            if (userDataDir == null) {
                showErrorAndFinish("Can't start script from saved instance state (user data directory is null)");
                return;
            }
            File scriptUserDataDir = new File(userDataDir);
            lastSelectedFragment = loadScriptStateFromFile(scriptUserDataDir);
            if (lastSelectedFragment < 0) {
                showErrorAndFinish("Can't continue script:", lastErrorMessage);
                return;
            }
        } else {
            lastSelectedFragment = createFormIntent();
            if (lastSelectedFragment < 0) {
                showErrorAndFinish("Can't start script", lastErrorMessage);
                return;
            }
        }

        // gui
        setContentView(R.layout.experiment_analyser);
        // Instantiate a ViewPager and a PagerAdapter.
        pager = (ViewPager)findViewById(R.id.pager);
        pagerAdapter = new ScriptFragmentPagerAdapter(getSupportFragmentManager(), activeChain);
        pager.setAdapter(pagerAdapter);

        pagerAdapter.setComponents(activeChain);
        pager.setCurrentItem(lastSelectedFragment);

        script.start();
    }

    @Override
    protected void onSaveInstanceState (Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(SCRIPT_USER_DATA_DIR, script.getUserDataDirectory().getPath());
    }

    public ScriptTreeNode getScriptComponentTreeAt(int index) {
        if (index < 0 || index >= activeChain.size())
            return null;
        return activeChain.get(index);
    }

    public int getScriptComponentIndex(ScriptTreeNode component) {
        return activeChain.indexOf(component);
    }

    public File getScriptUserDataDir() {
        return script.getUserDataDirectory();
    }

    private int createFormIntent() {
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
            if (!loadScript(scriptFile, scriptUserDataDir)) {
                StorageLib.recursiveDeleteFile(scriptUserDataDir);
                return -1;
            }
            activeChain = script.getActiveChain();
            return 0;
        } else
            return loadScriptStateFromFile(scriptUserDataDir);
    }

    @Override
    protected void onPause() {
        super.onPause();

        saveScriptStateToFile();
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
        if (!loadScript(scriptFile, scriptUserDataDir))
            return -1;

        if (!script.loadScriptState(bundle)) {
            lastErrorMessage = script.getLastError();
            script = null;
            return -1;
        }

        activeChain = script.getActiveChain();

        int lastSelectedFragment = bundle.getInt("current_fragment", 0);
        return lastSelectedFragment;
    }

    /**
     * Saves the state of the current script to the current script data dir.
     * @return false if an error occurred.
     */
    public boolean saveScriptStateToFile() {
        if (script == null)
            return false;
        File scriptUserDataDir = script.getUserDataDirectory();
        if (scriptFile == null || scriptUserDataDir == null)
            return false;

        Bundle bundle = new Bundle();
        bundle.putString("script_name", scriptFile.getName());
        bundle.putInt("current_fragment", pager.getCurrentItem());
        if (!script.saveScriptState(bundle))
            return false;

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
        return true;
    }

    protected void showErrorAndFinish(String error) {
        showErrorAndFinish(error, null);
    }

    protected void showErrorAndFinish(String error, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(error);
        if (message != null)
            builder.setMessage(message);
        builder.setNeutralButton("Ok", null);
        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                finish();
            }
        });
        dialog.show();
    }

    @Override
    public void onComponentStateChanged(ScriptTreeNode current, int state) {
        if (pagerAdapter == null)
            return;

        ScriptTreeNode lastSelectedComponent = null;
        if (activeChain.size() > 0)
            lastSelectedComponent = activeChain.get(pager.getCurrentItem());
        activeChain = script.getActiveChain();
        pagerAdapter.setComponents(activeChain);

        int index = activeChain.indexOf(lastSelectedComponent);
        if (index < 0)
            index = activeChain.size() - 1;
        if (index >= 0)
            pager.setCurrentItem(index);
    }

    public void setNextComponent(ScriptTreeNode next) {
        int index = activeChain.indexOf(next);
        if (index >0)
            pager.setCurrentItem(index);
    }

    private class ScriptFragmentPagerAdapter extends FragmentStatePagerAdapter {
        private List<ScriptTreeNode> components;
        private Map<ScriptTreeNode, ScriptComponentGenericFragment> fragmentMap = new HashMap<>();

        public ScriptFragmentPagerAdapter(android.support.v4.app.FragmentManager fragmentManager,
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
                    = (ScriptTreeNodeFragmentHolder)components.get(position);
            ScriptComponentGenericFragment fragment = fragmentCreator.createFragment();
            fragmentMap.put(components.get(position), fragment);
            return fragment;
        }

        @Override
        public int getCount() {
            return components.size();
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            super.destroyItem(container, position, object);
            fragmentMap.remove(findComponentFor((Fragment)object));
        }

        private ScriptTreeNode findComponentFor(Fragment fragment) {
            for (Map.Entry<ScriptTreeNode, ScriptComponentGenericFragment> entry : fragmentMap.entrySet()) {
                if (entry.getValue() == fragment)
                    return entry.getKey();
            }
            return null;
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
