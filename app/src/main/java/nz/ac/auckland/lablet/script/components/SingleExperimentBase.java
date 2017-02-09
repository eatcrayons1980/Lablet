/*
 * Copyright 2013-2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.script.components;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Keep;

import nz.ac.auckland.lablet.ExperimentActivity;
import nz.ac.auckland.lablet.experiment.ExperimentHelper;
import nz.ac.auckland.lablet.experiment.ISensorPlugin;
import nz.ac.auckland.lablet.misc.StorageLib;
import nz.ac.auckland.lablet.script.Script;
import nz.ac.auckland.lablet.script.ScriptComponentViewHolder;
import nz.ac.auckland.lablet.script.ScriptRunnerActivity;
import nz.ac.auckland.lablet.script.ScriptTreeNode;

import java.io.File;
import java.net.URI;


abstract class SingleExperimentBase extends ScriptComponentViewHolder {
    protected ScriptExperimentRef experiment = new ScriptExperimentRef();
    protected String descriptionText = "Please take an experiment:";

    public SingleExperimentBase(Script script) {
        super(script);
    }

    @Override
    public boolean initCheck() {
        return true;
    }

    public ScriptExperimentRef getExperiment() {
        return experiment;
    }

    public String getDescriptionText() {
        return descriptionText;
    }

    public void setDescriptionText(String descriptionText) {
        this.descriptionText = descriptionText;
    }

    public void toBundle(Bundle bundle) {
        super.toBundle(bundle);

        if (!experiment.getExperimentPath().equals("")) {
            // only save the relative path
            URI path = new File(experiment.getExperimentPath()).toURI();
            URI base = script.getUserDataDirectory().toURI();
            String relative = base.relativize(path).getPath();
            bundle.putString(ExperimentActivity.PATH, relative);
        }
    }

    public boolean fromBundle(Bundle bundle) {
        if (!super.fromBundle(bundle))
            return false;

        String relative = bundle.getString(ExperimentActivity.PATH, "");
        if (!relative.equals(""))
            experiment.setExperimentPath(new File(script.getUserDataDirectory(), relative).getPath());
        return true;
    }
}


/**
 * View to start a camera experiment activity.
 */
@Keep
abstract class ScriptComponentSingleExperimentBaseView<ExperimentSensorPlugin extends ISensorPlugin>
        extends ActivityStarterView {
    static final int PERFORM_EXPERIMENT = 0;

    final protected SingleExperimentBase experimentComponent;

    public ScriptComponentSingleExperimentBaseView(Context context, IActivityStarterViewParent parent,
                                                   SingleExperimentBase experimentComponent) {
        super(context, parent);

        this.experimentComponent = experimentComponent;
    }

    protected void startExperimentActivity(ExperimentSensorPlugin sensorPlugin) {
        Intent intent = new Intent(getContext(), ExperimentActivity.class);

        Bundle options = getExperimentOptions();

        String[] pluginName = new String[] {sensorPlugin.getSensorName()};
        ExperimentHelper.packStartExperimentIntent(intent, pluginName, options);
        intent.putExtras(options);

        startActivityForResult(intent, PERFORM_EXPERIMENT);
    }

    protected Bundle getExperimentOptions() {
        Bundle options = new Bundle();
        options.putBoolean("show_analyse_menu", false);
        options.putBoolean("sensors_editable", false);
        options.putInt("max_number_of_runs", 1);
        options.putString("experiment_base_directory", getScriptExperimentsDir().getPath());

        return options;
    }

    private File getScriptExperimentsDir() {
        ScriptComponentSheetFragment sheetFragment = (ScriptComponentSheetFragment)parent;
        ScriptRunnerActivity activity = (ScriptRunnerActivity)sheetFragment.getActivity();
        File scriptUserDataDir = activity.getScriptUserDataDir();

        File scriptExperimentDir = new File(scriptUserDataDir, "experiments");
        if (!scriptExperimentDir.exists())
            scriptExperimentDir.mkdirs();
        return scriptExperimentDir;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK)
            return;

        if (requestCode == PERFORM_EXPERIMENT) {
            if (data == null)
                return;
            if (data.hasExtra(ExperimentActivity.PATH)) {
                String oldExperiment = experimentComponent.getExperiment().getExperimentPath();
                if (!oldExperiment.equals(""))
                    StorageLib.recursiveDeleteFile(new File(oldExperiment));

                String experimentPath = data.getStringExtra(ExperimentActivity.PATH);
                ScriptExperimentRef experimentRef = experimentComponent.getExperiment();
                experimentRef.setExperimentPath(experimentPath);
                experimentComponent.setState(ScriptTreeNode.SCRIPT_STATE_DONE);

                onExperimentPerformed();
            }
        }
    }

    abstract protected void onExperimentPerformed();
}
