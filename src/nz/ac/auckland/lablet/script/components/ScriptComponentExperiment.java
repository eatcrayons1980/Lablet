/*
 * Copyright 2013-2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.script.components;


import android.content.Context;
import nz.ac.auckland.lablet.experiment.ExperimentData;
import nz.ac.auckland.lablet.experiment.ExperimentLoader;
import nz.ac.auckland.lablet.experiment.SensorAnalysis;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Reference to an experiment conducted in the script.
 * Also caches the experiment analysis and notifies listeners if the experiment has been updated.
 */
public class ScriptComponentExperiment {
    public interface IScriptComponentExperimentListener {
        public void onExperimentAnalysisUpdated();
    }

    private List<WeakReference<IScriptComponentExperimentListener>> listeners
            = new ArrayList<WeakReference<IScriptComponentExperimentListener>>();
    private String experimentPath = "";
    private ExperimentData experimentData;

    public String getExperimentPath() {
        return experimentPath;
    }
    public void setExperimentPath(String path) {
        experimentPath = path;
    }

    public SensorAnalysis getExperimentAnalysis(Context context) {
        if (experimentData == null)
            experimentData = loadSensorAnalysis(context);

        ExperimentData.SensorEntry entry = new ExperimentData.SensorEntry();

        return ExperimentLoader.getSensorAnalysis(experimentData.getRuns().get(0).sensors.get(0));
    }

    public void addListener(IScriptComponentExperimentListener listener) {
        listeners.add(new WeakReference<IScriptComponentExperimentListener>(listener));
    }

    public boolean removeListener(IScriptComponentExperimentListener listener) {
        return listeners.remove(listener);
    }

    public void reloadExperimentAnalysis(Context context) {
        experimentData = loadSensorAnalysis(context);
        for (ListIterator<WeakReference<IScriptComponentExperimentListener>> it = listeners.listIterator();
             it.hasNext();) {
            IScriptComponentExperimentListener listener = it.next().get();
            if (listener != null)
                listener.onExperimentAnalysisUpdated();
            else
                it.remove();
        }
    }

    private ExperimentData loadSensorAnalysis(Context context) {
        ExperimentData experimentData = new ExperimentData();
        if (!experimentData.load(context, getExperimentPath()))
            return null;
        return experimentData;
    }
}
