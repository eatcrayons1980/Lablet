/*
 * Copyright 2013-2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import nz.ac.auckland.lablet.experiment.*;

import java.io.*;


/**
 * Abstract base class for activities that analyze an experiment.
 */
abstract public class ExperimentAnalysisBaseActivity extends FragmentActivity {
    @Nullable
    protected ExperimentAnalysis experimentAnalysis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ensureExperimentDataLoaded();
    }

    @Nullable
    public ExperimentAnalysis getExperimentAnalysis() {
        return experimentAnalysis;
    }

    public boolean ensureExperimentDataLoaded() {
        return experimentAnalysis != null || loadExperiment();
    }

    private boolean loadExperiment() {
        Intent intent = getIntent();

        String experimentPath = intent.getStringExtra(ExperimentActivity.PATH);
        int runId = intent.getIntExtra(ExperimentAnalysis.AnalysisRef.RUN_ID_KEY, 0);
        String analysisId = intent.getStringExtra(ExperimentAnalysis.AnalysisRef.ANALYSIS_UID_KEY);

        ExperimentData experimentData = ExperimentHelper.loadExperimentData(experimentPath);

        if (experimentData == null) {
            experimentAnalysis = null;
            return false;
        }

        if (!experimentData.getLoadError().equals("")) {
            showErrorAndFinish(experimentData.getLoadError());
            return false;
        }
        experimentAnalysis = new ExperimentAnalysis();
        experimentAnalysis.setExperimentData(experimentData);

        if (experimentAnalysis.getNumberOfRuns() == 0
                || experimentAnalysis.getAnalysisRunAt(0).analysisList.size() == 0) {
            showErrorAndFinish("No experiment found.");
            return false;
        }

        experimentAnalysis.setCurrentAnalysisRun(runId);
        experimentAnalysis.setCurrentAnalysis(analysisId);
        return true;
    }

    protected void showErrorAndFinish(String error) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(error);
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

    static public File getDefaultExperimentBaseDir(Context context) {
        File baseDir = context.getExternalFilesDir(null);
        return new File(baseDir, "experiments");
    }
}
