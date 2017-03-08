/*
 * Copyright 2013-2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.PopupMenu;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nz.ac.auckland.lablet.misc.NaturalOrderComparator;
import nz.ac.auckland.lablet.misc.StorageLib;
import nz.ac.auckland.lablet.script.LuaScriptLoader;
import nz.ac.auckland.lablet.script.Script;
import nz.ac.auckland.lablet.script.ScriptMetaData;
import nz.ac.auckland.lablet.script.ScriptRunnerActivity;
import nz.ac.auckland.lablet.utility.FileHelper;
import nz.ac.auckland.lablet.views.CheckBoxAdapter;
import nz.ac.auckland.lablet.views.CheckBoxListEntry;
import nz.ac.auckland.lablet.views.ExportDirDialog;
import nz.ac.auckland.lablet.views.InfoBarBackgroundDrawable;
import nz.ac.auckland.lablet.views.InfoSideBar;
import org.jetbrains.annotations.Contract;


/**
 * Helper class to manage Lab Activities (scripts).
 */
class ScriptDirs {

    @NonNull
    final static private String SCRIPTS_COPIED_KEY = "scripts_copied_v1";
    @NonNull
    final static private String PREFERENCES_NAME = "lablet_preferences";

    /**
     * The script directory is the directory the stores the script files, i.e., the lua files.
     *
     * @param context the context
     * @return the script directory File
     */
    @Nullable
    static private File getScriptDirectory(@Nullable Context context) {
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

    @Contract("null -> null; !null -> !null")
    @Nullable
    static private File getResourceScriptDir(@Nullable Context context) {
        if (context == null) {
            return null;
        }
        return new File(getScriptDirectory(context), "demo");
    }

    @Contract("null -> null; !null -> !null")
    @Nullable
    static File getRemoteScriptDir(@Nullable Context context) {
        if (context == null) {
            return null;
        }
        return new File(getScriptDirectory(context), "remotes");
    }

    /**
     * Copies the default Lab Activities from the app resources.
     *
     * @param activity the current activity
     * @param forceCopy overwrite existing Lab Activities
     */
    static void copyResourceScripts(@Nullable Activity activity, boolean forceCopy) {
        if (activity == null) {
            return;
        }
        SharedPreferences settings = activity.getSharedPreferences(PREFERENCES_NAME, 0);
        if (!forceCopy && settings.getBoolean(SCRIPTS_COPIED_KEY, false)) {
            return;
        }

        File scriptDir = getResourceScriptDir(activity);
        if (!scriptDir.exists()) {
            if (!scriptDir.mkdir()) {
                return;
            }
        }
        try {
            String[] files = activity.getAssets().list("");
            for (String file : files) {
                if (!FileHelper.isLuaFile(file)) {
                    continue;
                }
                InputStream inputStream = activity.getAssets().open(file);
                File scriptOutFile = new File(scriptDir, file);
                if (!forceCopy && scriptOutFile.exists()) {
                    continue;
                }

                OutputStream outputStream = new BufferedOutputStream(
                    new FileOutputStream(scriptOutFile, false));
                byte[] buffer = new byte[16384];
                while (true) {
                    int n = inputStream.read(buffer);
                    if (n <= -1) {
                        break;
                    }
                    outputStream.write(buffer, 0, n);
                }

                inputStream.close();
                outputStream.flush();
                outputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        settings.edit().putBoolean(SCRIPTS_COPIED_KEY, true).apply();
    }


    /**
     * Read available Lab Activities.
     *
     * @param scriptList found Lab Activities are places here
     * @param context current context
     */
    static void readScriptList(@NonNull List<ScriptMetaData> scriptList, Context context) {
        File[] scriptDirs = {
            getScriptDirectory(context),
            getResourceScriptDir(context),
            getRemoteScriptDir(context)
        };

        for (File scriptDir : scriptDirs) {
            if (scriptDir.isDirectory()) {
                readScriptsFromDir(scriptDir, scriptList);
            }
        }
    }

    /**
     * Read available script from a certain directory.
     *
     * @param scriptDir the directory that should be searched
     * @param scripts found Lab Activities are places here
     */
    static void readScriptsFromDir(@NonNull File scriptDir, @NonNull List<ScriptMetaData> scripts) {
        File[] children = scriptDir.listFiles();
        for (File child : children != null ? children : new File[0]) {
            if (FileHelper.isLuaFile(child.getName())) {
                ScriptMetaData metaData = LuaScriptLoader.getScriptMetaData(child);
                if (metaData != null) {
                    scripts.add(metaData);
                }
            }
        }
    }
}

/**
 * Main or home activity to manage scripts (lab activities).
 *
 * The user is able to start a new script and resume or delete existing scripts.
 */
public class ScriptHomeActivity extends Activity {

    final static public String REMOTE_TYPE = "remote";
    final static private int START_SCRIPT = 1;

    @NonNull
    private List<ScriptMetaData> scriptList = new ArrayList<>();
    @Nullable
    private ArrayAdapter<ScriptMetaData> scriptListAdaptor = null;
    @Nullable
    private ArrayList<CheckBoxListEntry> existingScriptList = null;
    private CheckBoxListEntry.OnCheckBoxListEntryListener checkBoxListEntryListener;
    @Nullable
    private CheckBoxAdapter existingScriptListAdaptor = null;
    @Nullable
    private MenuItem deleteItem = null;
    @Nullable
    private MenuItem exportItem = null;
    @Nullable
    private AlertDialog deleteScriptDataAlertBox = null;
    @Nullable
    private AlertDialog infoAlertBox = null;
    @Nullable
    private CheckBox selectAllCheckBox = null;

    /**
     * The script user data is the directory that contains the stored script state, i.e., the
     * results.
     *
     * @param context the context
     * @return the script user data
     */
    static private File getScriptUserDataDir(@NonNull Context context) {
        File baseDir = context.getExternalFilesDir(null);
        File scriptDir = new File(baseDir, "script_user_data");
        if (!scriptDir.exists() && !scriptDir.mkdir()) {
            return null;
        }
        return scriptDir;
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        menu.clear();
        getMenuInflater().inflate(R.menu.script_activity_actions, menu);

        // script options
        MenuItem scriptOptions = menu.findItem(R.id.action_script_options);
        if (BuildConfig.DEBUG && scriptOptions == null) {
            throw new RuntimeException("scriptOptions is null");
        }
        scriptOptions.setOnMenuItemClickListener(menuItem -> {
            showScriptMenu();
            return true;
        });

        // to stand alone experiment screen
        MenuItem standAlone = menu.findItem(R.id.action_stand_alone);
        if (BuildConfig.DEBUG && standAlone == null) {
            throw new RuntimeException("standAlone is null");
        }
        standAlone.setOnMenuItemClickListener(menuItem -> {
            startStandAloneExperimentActivity();
            return true;
        });

        // info item
        MenuItem infoItem = menu.findItem(R.id.action_info);
        if (BuildConfig.DEBUG && infoItem == null) {
            throw new RuntimeException("infoItem is null");
        }
        String versionString = InfoHelper.getVersionString(this);
        infoItem.setTitle(versionString);
        infoAlertBox = InfoHelper.createAlertInfoBox(this);
        infoItem.setOnMenuItemClickListener(menuItem -> {
            if (infoAlertBox == null) {
                return false;
            }
            infoAlertBox.show();
            return true;
        });

        // delete item
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton("No", (dialogInterface, i) -> {

        });
        builder.setTitle("Really delete the selected script data?");
        builder.setPositiveButton("Yes", (dialogInterface, i) -> deleteSelectedExistingScript());

        deleteScriptDataAlertBox = builder.create();

        deleteItem = menu.findItem(R.id.action_delete);
        assert deleteItem != null;
        deleteItem.setVisible(false);
        deleteItem.setOnMenuItemClickListener(menuItem -> {
            if (!isAtLeastOneExistingScriptSelected() || deleteScriptDataAlertBox == null) {
                return false;
            }
            deleteScriptDataAlertBox.show();
            return true;
        });

        exportItem = menu.findItem(R.id.action_mail);
        assert exportItem != null;
        exportItem.setVisible(false);
        exportItem.setOnMenuItemClickListener(menuItem -> {
            if (!isAtLeastOneExistingScriptSelected()) {
                return false;
            }
            exportSelection();
            return true;
        });

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean atLeastOneSelected = isAtLeastOneExistingScriptSelected();
        if (deleteItem != null) {
            deleteItem.setVisible(atLeastOneSelected);
        }
        if (exportItem != null) {
            exportItem.setVisible(atLeastOneSelected);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    private void exportSelection() {
        List<File> exportList = new ArrayList<>();

        File scriptBaseDir = getScriptUserDataDir(this);
        if (existingScriptList != null) {
            for (CheckBoxListEntry entry : existingScriptList) {
                if (!entry.getSelected()) {
                    continue;
                }
                File experimentDir = new File(scriptBaseDir, entry.getName());
                exportList.add(experimentDir);
            }
        }

        File[] fileArray = new File[exportList.size()];
        for (int i = 0; i < exportList.size(); i++) {
            fileArray[i] = exportList.get(i);
        }

        ExportDirDialog dirDialog = new ExportDirDialog(this, fileArray);
        dirDialog.show();
    }

    private void startStandAloneExperimentActivity() {
        Intent intent = new Intent(this, ExperimentHomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.script_home);

        int grey = 70;
        int listBackgroundColor = Color.rgb(grey, grey, grey);

        // info side bar
        InfoSideBar infoSideBar = (InfoSideBar) findViewById(R.id.infoSideBar);
        assert infoSideBar != null;
        infoSideBar.setIcon(R.drawable.ic_console);
        infoSideBar.setInfoText("Lab Activities");
        infoSideBar.setBackground(new InfoBarBackgroundDrawable(Color.argb(255, 22, 115, 155)));

        // experiment list
        scriptList = new ArrayList<>();
        scriptListAdaptor = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
            scriptList);
        ListView scriptListView = (ListView) findViewById(R.id.scriptList);
        scriptListView.setBackgroundColor(listBackgroundColor);
        scriptListView.setAdapter(scriptListAdaptor);
        scriptListView.setOnItemClickListener((adapterView, view, i, l) -> {
            ScriptMetaData metaData = scriptList.get(i);
            startScript(metaData);
        });

        // existing experiment list
        selectAllCheckBox = (CheckBox) findViewById(R.id.checkBoxSelectAll);
        selectAllCheckBox.setOnCheckedChangeListener((compoundButton, b) -> {
            if (existingScriptList != null) {
                for (CheckBoxListEntry entry : existingScriptList) {
                    entry.setSelected(b);
                }
            }
            if (existingScriptListAdaptor != null) {
                existingScriptListAdaptor.notifyDataSetChanged();
            }
        });

        ListView existingScriptListView = (ListView) findViewById(R.id.existingScriptListView);
        existingScriptListView.setBackgroundColor(listBackgroundColor);
        existingScriptList = new ArrayList<>();
        existingScriptListAdaptor = new CheckBoxAdapter(this, R.layout.check_box_list_item,
            existingScriptList);
        existingScriptListView.setAdapter(existingScriptListAdaptor);

        existingScriptListView.setOnItemClickListener((adapterView, view, i, l) -> {
            String id = existingScriptList.get(i).getName();
            loadPreviousScript(id);
        });

        checkBoxListEntryListener = entry -> updateSelectedMenuItem();

        ScriptDirs.copyResourceScripts(this, false);
    }

    private void showScriptMenu() {
        final View parent = findViewById(R.id.action_script_options);
        final ScriptHomeActivity that = this;

        PopupMenu popup = new PopupMenu(this, parent);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.script_popup, popup.getMenu());
        popup.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case R.id.scriptManager:
                    Intent intent = new Intent(that, ScriptManagerActivity.class);
                    startActivity(intent);
                    return true;
                default:
                    return false;
            }

        });
        popup.show();
    }

    private void updateSelectedMenuItem() {
        invalidateOptionsMenu();
    }

    @Override
    public void onResume() {
        super.onResume();
        ((Lablet) getApplication()).ensurePrivacyPolicy(this);

        if (selectAllCheckBox != null) {
            selectAllCheckBox.setChecked(false);
        }
        invalidateOptionsMenu();

        updateScriptList();
        updateExistingScriptList();
    }

    private boolean isAtLeastOneExistingScriptSelected() {
        boolean itemSelected = false;
        if (existingScriptList != null) {
            for (CheckBoxListEntry entry : existingScriptList) {
                if (entry.getSelected()) {
                    itemSelected = true;
                    break;
                }
            }
        }
        return itemSelected;
    }

    private void deleteSelectedExistingScript() {
        File scriptDir = getScriptUserDataDir(this);
        if (existingScriptList != null) {
            for (CheckBoxListEntry entry : existingScriptList) {
                if (!entry.getSelected()) {
                    continue;
                }
                File file = new File(scriptDir, entry.getName());
                StorageLib.recursiveDeleteFile(file);
            }
        }
        if (selectAllCheckBox != null) {
            selectAllCheckBox.setChecked(false);
        }
        if (deleteItem != null) {
            deleteItem.setVisible(false);
        }
        updateExistingScriptList();
    }

    private void startScript(@NonNull ScriptMetaData metaData) {
        String scriptId = metaData.getScriptFileName();
        File scriptUserDataDir = new File(getScriptUserDataDir(this),
            Script.generateScriptUid(scriptId));
        Intent intent = new Intent(this, ScriptRunnerActivity.class);
        intent.putExtra("script_path", metaData.file.getPath());
        intent.putExtra("script_user_data_dir", scriptUserDataDir.getPath());
        startActivityForResult(intent, START_SCRIPT);
    }

    private void loadPreviousScript(@NonNull String scriptDir) {
        File scriptUserDataDir = new File(getScriptUserDataDir(this), scriptDir);

        Intent intent = new Intent(this, ScriptRunnerActivity.class);
        intent.putExtra("script_user_data_dir", scriptUserDataDir.getPath());
        startActivityForResult(intent, START_SCRIPT);
    }

    private void updateScriptList() {
        scriptList.clear();
        ScriptDirs.readScriptList(scriptList, this);

        Collections.sort(scriptList,
            (metaData, metaData2) -> metaData.getTitle().compareTo(metaData2.getTitle()));

        if (scriptListAdaptor != null) {
            scriptListAdaptor.notifyDataSetChanged();
        }
    }

    private void updateExistingScriptList() {
        if (existingScriptList == null) {
            existingScriptList = new ArrayList<>();
        } else {
            existingScriptList.clear();
        }
        File scriptDir = getScriptUserDataDir(this);
        if (scriptDir != null && scriptDir.isDirectory() && scriptDir.listFiles() != null) {
            List<String> children = new ArrayList<>();
            for (File file : scriptDir.listFiles()) {
                children.add(file.getName());
            }
            Collections.sort(children, Collections.reverseOrder(new NaturalOrderComparator()));
            //noinspection Convert2streamapi
            for (String child : children) {
                existingScriptList.add(new CheckBoxListEntry(child, checkBoxListEntryListener));
            }
        }

        if (existingScriptListAdaptor != null) {
            existingScriptListAdaptor.notifyDataSetChanged();
        }
    }
}
