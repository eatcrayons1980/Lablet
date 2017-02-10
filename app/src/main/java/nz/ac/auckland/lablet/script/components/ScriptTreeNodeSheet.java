/*
 * Copyright 2013-2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.script.components;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import nz.ac.auckland.lablet.camera.MotionAnalysis;
import nz.ac.auckland.lablet.script.*;
import nz.ac.auckland.lablet.views.graph.*;
import nz.ac.auckland.lablet.R;

import java.util.HashMap;
import java.util.Map;


/**
 * View holder for a view that only displays some text.
 */
class TextComponent extends ScriptComponentViewHolder {
    private String text = "";
    private int typeface = Typeface.NORMAL;

    public TextComponent(Script script, String text) {
        super(script);
        this.text = text;
        setState(ScriptTreeNode.SCRIPT_STATE_DONE);
    }

    public void setTypeface(int typeface) {
        this.typeface = typeface;
    }

    @Override
    public View createView(Context context, android.support.v4.app.Fragment parent) {
        TextView textView = new TextView(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            textView.setTextAppearance(android.R.style.TextAppearance_Medium);
        } else {
            textView.setTextAppearance(context, android.R.style.TextAppearance_Medium);
        }
        textView.setTypeface(null, typeface);
        textView.setText(text);
        return textView;
    }

    @Override
    public boolean initCheck() {
        return true;
    }
}


/**
 * View holder for a view that has a checkbox and a text view.
 */
class CheckBoxQuestion extends ScriptComponentViewHolder {
    private String text = "";
    public CheckBoxQuestion(Script script, String text) {
        super(script);
        this.text = text;
        setState(ScriptTreeNode.SCRIPT_STATE_ONGOING);
    }

    @Override
    public View createView(Context context, android.support.v4.app.Fragment parent) {
        CheckBox view = new CheckBox(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.setTextAppearance(android.R.style.TextAppearance_Medium);
            view.setBackgroundColor(context.getResources().getColor(R.color.sc_question_background_color, null));
        } else {
            view.setTextAppearance(context, android.R.style.TextAppearance_Medium);
            view.setBackgroundColor(context.getResources().getColor(R.color.sc_question_background_color));
        }
        view.setText(text);

        if (getState() == ScriptTreeNode.SCRIPT_STATE_DONE)
            view.setChecked(true);

        view.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if (checked)
                    setState(ScriptTreeNode.SCRIPT_STATE_DONE);
                else
                    setState(ScriptTreeNode.SCRIPT_STATE_ONGOING);
            }
        });
        return view;
    }

    @Override
    public boolean initCheck() {
        return true;
    }
}


/**
 * View holder for a view with just a question.
 */
class Question extends ScriptComponentViewHolder {
    private String text = "";
    private ScriptTreeNodeSheetBase component;

    public Question(Script script, String text, ScriptTreeNodeSheetBase component) {
        super(script);
        this.text = text;
        this.component = component;

        setState(ScriptTreeNode.SCRIPT_STATE_DONE);
    }

    @Override
    public View createView(Context context, android.support.v4.app.Fragment parent) {
        // we have to get a fresh counter here, if we cache it we will miss that it has been deleted in the sheet
        // component
        ScriptTreeNodeSheetBase.Counter counter = this.component.getCounter("QuestionCounter");

        TextView textView = new TextView(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            textView.setTextAppearance(android.R.style.TextAppearance_Medium);
            textView.setBackgroundColor(context.getResources().getColor(R.color.sc_question_background_color, null));
        } else {
            textView.setTextAppearance(context, android.R.style.TextAppearance_Medium);
            textView.setBackgroundColor(context.getResources().getColor(R.color.sc_question_background_color));
        }

        textView.setText("Q" + counter.increaseValue() + ": " + text);
        return textView;
    }

    @Override
    public boolean initCheck() {
        return true;
    }
}


/**
 * View holder for a view that has a question and a text input view.
 * <p>
 * The question is considered as answered as soon as the text input view contains some text. It is not checked if the
 * question is answered correctly.
 * </p>
 */
class TextQuestion extends ScriptComponentViewHolder {
    private String text = "";
    private Integer question_num = 0;
    private String answer = "";
    private boolean optional = false;
    private ScriptTreeNodeSheetBase component;

    public TextQuestion(Script script, String text, ScriptTreeNodeSheetBase component) {
        super(script);
        this.text = text;
        this.component = component;

        setState(ScriptTreeNode.SCRIPT_STATE_ONGOING);
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
        update();
    }

    @Override
    public View createView(Context context, Fragment parent) {
        ScriptTreeNodeSheetBase.Counter counter = this.component.getCounter("QuestionCounter");

        // Note: we have to do this programmatically because findViewById would find the wrong child
        // items if there is more than one text question.

        LinearLayout layout = new LinearLayout(context);
        layout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.setOrientation(LinearLayout.VERTICAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            layout.setBackgroundColor(context.getResources().getColor(R.color.sc_question_background_color, null));
        } else {
            layout.setBackgroundColor(context.getResources().getColor(R.color.sc_question_background_color));
        }

        TextView textView = new TextView(context);
        textView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            textView.setTextAppearance(android.R.style.TextAppearance_Medium);
        } else {
            textView.setTextAppearance(context, android.R.style.TextAppearance_Medium);
        }
        question_num = counter.increaseValue();
        textView.setText("Q" + question_num + ": " + text);

        EditText editText = new EditText(context);
        editText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        editText.setText(answer);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                answer = editable.toString();
                update();
            }
        });

        layout.addView(textView);
        layout.addView(editText);
        return layout;
    }

    @Override
    public boolean initCheck() {
        return true;
    }

    /**
     * Put the object data into a bundle.
     *
     * This information is saved to the user data for the app. To better identify the data, the
     * question text and number is also saved in this bundle, even though it is not needed in the
     * associated fromBundle method.
     *
     * If the answer is blank, the question text and number is not saved.
     *
     * @param bundle to store the component state in
     */
    public void toBundle(Bundle bundle) {
        if (!answer.equals("")) {
            bundle.putString("question", text);
            bundle.putInt("number", question_num);
        }
        bundle.putString("answer", answer);
        super.toBundle(bundle);
    }

    /**
     * Get the object data from a bundle.
     *
     * Note that even though the question, number, and answer are all stored in the bundle, the
     * answer is all that is needed.
     *
     * @param bundle that contains the component state
     * @return true if the state was restored
     */
    public boolean fromBundle(Bundle bundle) {
        answer = bundle.getString("answer", "");
        return super.fromBundle(bundle);
    }

    private void update() {
        if (optional)
            setState(ScriptTreeNode.SCRIPT_STATE_DONE);
        else if (!answer.equals(""))
            setState(ScriptTreeNode.SCRIPT_STATE_DONE);
        else
            setState(ScriptTreeNode.SCRIPT_STATE_ONGOING);
    }
}


/**
 * View holder for a graph view that shows some experiment analysis results.
 */
class MotionAnalysisGraphView extends ScriptComponentViewHolder {
    private ScriptTreeNodeSheet experimentSheet;
    private ScriptExperimentRef experiment;
    private MarkerTimeGraphAdapter adapter;
    private String xAxisContentId = "x-position";
    private String yAxisContentId = "y-position";
    private String title = "Position Data";
    private ScriptExperimentRef.IListener experimentListener;

    public MotionAnalysisGraphView(Script script, ScriptTreeNodeSheet experimentSheet, ScriptExperimentRef experiment) {
        super(script);
        this.experimentSheet = experimentSheet;
        this.experiment = experiment;
        setState(ScriptTreeNode.SCRIPT_STATE_DONE);
    }

    @Override
    public View createView(Context context, android.support.v4.app.Fragment parentFragment) {
        LayoutInflater layoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = layoutInflater.inflate(R.layout.script_component_graph_view, null);
        assert view != null;

        GraphView2D graphView2D = (GraphView2D)view.findViewById(R.id.graphView);
        assert graphView2D != null;

        graphView2D.setMaxWidth(500);

        MotionAnalysis sensorAnalysis = experiment.getMotionAnalysis(0);
        if (sensorAnalysis != null) {
            MarkerGraphAxis xAxis = createAxis(xAxisContentId);
            if (xAxis == null)
                xAxis = new XPositionMarkerGraphAxis(sensorAnalysis.getXUnit(), sensorAnalysis.getXMinRangeGetter());
            MarkerGraphAxis yAxis = createAxis(yAxisContentId);
            if (yAxis == null)
                yAxis = new XPositionMarkerGraphAxis(sensorAnalysis.getXUnit(), sensorAnalysis.getYMinRangeGetter());

            adapter = new MarkerTimeGraphAdapter(sensorAnalysis.getTagMarkers(), sensorAnalysis.getTimeData(),
                    title, xAxis, yAxis);
            graphView2D.setAdapter(adapter);
        }

        // install listener
        experimentListener = new ScriptExperimentRef.IListener() {
            @Override
            public void onExperimentAnalysisUpdated() {
                MotionAnalysis motionAnalysis = experiment.getMotionAnalysis(0);
                adapter.setTo(motionAnalysis.getTagMarkers(), motionAnalysis.getTimeData());
            }
        };
        experiment.addListener(experimentListener);

        return view;
    }

    @Override
    protected void finalize() {
        experiment.removeListener(experimentListener);
    }

    public boolean setXAxisContent(String axis) {
        if (!isValidAxis(axis))
            return false;
        xAxisContentId = axis;
        return true;
    }

    public boolean setYAxisContent(String axis) {
        if (!isValidAxis(axis))
            return false;
        yAxisContentId = axis;
        return true;
    }

    public void showXVsYPosition() {
        setTitle("Position Data");
        setXAxisContent("x-position");
        setYAxisContent("y-position");
    }

    public void showTimeVsXSpeed() {
        setTitle("X-Velocity vs. Time");
        setXAxisContent("time_v");
        setYAxisContent("x-velocity");
    }

    public void showTimeVsYSpeed() {
        setTitle("Y-Velocity vs. Time");
        setXAxisContent("time_v");
        setYAxisContent("y-velocity");
    }

    public void setTitle(String title) {
        this.title = title;
    }

    private MarkerGraphAxis createAxis(String id) {
        MotionAnalysis sensorAnalysis = experiment.getMotionAnalysis(0);

        if (id.equalsIgnoreCase("x-position"))
            return new XPositionMarkerGraphAxis(sensorAnalysis.getXUnit(), sensorAnalysis.getXMinRangeGetter());
        if (id.equalsIgnoreCase("y-position"))
            return new YPositionMarkerGraphAxis(sensorAnalysis.getYUnit(), sensorAnalysis.getYMinRangeGetter());
        if (id.equalsIgnoreCase("x-velocity"))
            return new XSpeedMarkerGraphAxis(sensorAnalysis.getXUnit(), sensorAnalysis.getTUnit());
        if (id.equalsIgnoreCase("y-velocity"))
            return new YSpeedMarkerGraphAxis(sensorAnalysis.getYUnit(), sensorAnalysis.getTUnit());
        if (id.equalsIgnoreCase("time"))
            return new TimeMarkerGraphAxis(sensorAnalysis.getTUnit());
        if (id.equalsIgnoreCase("time_v"))
            return new SpeedTimeMarkerGraphAxis(sensorAnalysis.getTUnit());
        return null;
    }

    private boolean isValidAxis(String id) {
        if (id.equalsIgnoreCase("x-position"))
            return true;
        if (id.equalsIgnoreCase("y-position"))
            return true;
        if (id.equalsIgnoreCase("x-velocity"))
            return true;
        if (id.equalsIgnoreCase("y-velocity"))
            return true;
        if (id.equalsIgnoreCase("time"))
            return true;
        if (id.equalsIgnoreCase("time_v"))
            return true;
        return false;
    }

    public MarkerTimeGraphAdapter getAdapter() {
        return adapter;
    }

    @Override
    public boolean initCheck() {
        if (experiment == null) {
            lastErrorMessage = "no experiment data";
            return false;
        }
        return true;
    }
}


/**
 * Base class for the {@link ScriptTreeNodeSheet} class.
 * <p>
 * All important logic is done here. ScriptComponentTreeSheet only has an interface to add different child components.
 * </p>
 */
class ScriptTreeNodeSheetBase extends ScriptTreeNodeFragmentHolder {
    /**
     * Page wide counter.
     * <p>
     * This counter is, for example, used to number the questions on a sheet.
     * </p>
     */
    public class Counter {
        private int counter = 0;

        public void setValue(int value) {
            counter = value;
        }

        public int increaseValue() {
            counter++;
            return counter;
        }
    }

    private SheetGroupLayout sheetGroupLayout = new SheetGroupLayout(LinearLayout.VERTICAL);
    private ScriptComponentContainer<ScriptComponentViewHolder> itemContainer
            = new ScriptComponentContainer<ScriptComponentViewHolder>();
    private Map<String, Counter> mapOfCounter = new HashMap<String, Counter>();

    public ScriptTreeNodeSheetBase(Script script) {
        super(script);

        itemContainer.setListener(new ScriptComponentContainer.IItemContainerListener() {
            @Override
            public void onAllItemStatusChanged(boolean allDone) {
                if (allDone)
                    setState(ScriptTreeNode.SCRIPT_STATE_DONE);
                else
                    setState(ScriptTreeNode.SCRIPT_STATE_ONGOING);
            }
        });
    }

    public SheetLayout getSheetLayout() {
        return sheetGroupLayout;
    }

    @Override
    public boolean initCheck() {
        return itemContainer.initCheck();
    }

    @Override
    public ScriptComponentGenericFragment createFragment() {
        ScriptComponentSheetFragment fragment = new ScriptComponentSheetFragment();
        fragment.setScriptComponent(this);
        return fragment;
    }

    @Override
    public void toBundle(Bundle bundle) {
        super.toBundle(bundle);

        itemContainer.toBundle(bundle);
    }

    @Override
    public boolean fromBundle(Bundle bundle) {
        if (!super.fromBundle(bundle))
            return false;

        return itemContainer.fromBundle(bundle);
    }

    public void setMainLayoutOrientation(String orientation) {
        if (orientation.equalsIgnoreCase("horizontal"))
            sheetGroupLayout.setOrientation(LinearLayout.HORIZONTAL);
        else
            sheetGroupLayout.setOrientation(LinearLayout.VERTICAL);
    }

    public SheetGroupLayout addHorizontalGroupLayout(SheetGroupLayout parent) {
        return addGroupLayout(LinearLayout.HORIZONTAL, parent);
    }

    public SheetGroupLayout addVerticalGroupLayout(SheetGroupLayout parent) {
        return addGroupLayout(LinearLayout.VERTICAL, parent);
    }

    protected SheetGroupLayout addGroupLayout(int orientation, SheetGroupLayout parent) {
        SheetGroupLayout layout = new SheetGroupLayout(orientation);
        if (parent == null)
            sheetGroupLayout.addLayout(layout);
        else
            parent.addLayout(layout);
        return layout;
    }

    protected SheetLayout addItemViewHolder(ScriptComponentViewHolder item,
                                            SheetGroupLayout parent) {
        itemContainer.addItem(item);
        SheetLayout layoutItem;
        if (parent == null)
            layoutItem = sheetGroupLayout.addView(item);
        else
            layoutItem = parent.addView(item);
        return layoutItem;
    }

    /**
     * The counter is valid for the lifetime of the fragment view. If not counter with the given name exist, a new
     * counter is created.
     * @param name counter name
     * @return a Counter
     */
    public Counter getCounter(String name) {
        if (!mapOfCounter.containsKey(name)) {
            Counter counter = new Counter();
            mapOfCounter.put(name, counter);
            return counter;
        }
        return mapOfCounter.get(name);
    }

    public void resetCounter() {
        mapOfCounter.clear();
    }
}


/**
 * Powerful script component that can holds a various kind of child components, e.g., questions or text.
 */
public class ScriptTreeNodeSheet extends ScriptTreeNodeSheetBase {

    public ScriptTreeNodeSheet(Script script) {
        super(script);
    }

    public ScriptComponentViewHolder addText(String text, SheetGroupLayout parent) {
        TextComponent textOnlyQuestion = new TextComponent(script, text);
        addItemViewHolder(textOnlyQuestion, parent);
        return textOnlyQuestion;
    }

    public ScriptComponentViewHolder addHeader(String text, SheetGroupLayout parent) {
        TextComponent component = new TextComponent(script, text);
        component.setTypeface(Typeface.BOLD);
        addItemViewHolder(component, parent);
        return component;
    }

    public ScriptComponentViewHolder addQuestion(String text, SheetGroupLayout parent) {
        Question component = new Question(script, text, this);
        addItemViewHolder(component, parent);
        return component;
    }

    public ScriptComponentViewHolder addTextQuestion(String text, SheetGroupLayout parent) {
        TextQuestion component = new TextQuestion(script, text, this);
        addItemViewHolder(component, parent);
        return component;
    }

    public ScriptComponentViewHolder addCheckQuestion(String text, SheetGroupLayout parent) {
        CheckBoxQuestion question = new CheckBoxQuestion(script, text);
        addItemViewHolder(question, parent);
        return question;
    }

    public AccelerometerExperiment addAccelerometerExperiment(SheetGroupLayout parent) {
        AccelerometerExperiment experiment = new AccelerometerExperiment(script);
        addItemViewHolder(experiment, parent);
        return experiment;
    }

    public CameraExperiment addCameraExperiment(SheetGroupLayout parent) {
        CameraExperiment cameraExperiment = new CameraExperiment(script);
        addItemViewHolder(cameraExperiment, parent);
        return cameraExperiment;
    }

    public MicrophoneExperiment addMicrophoneExperiment(SheetGroupLayout parent) {
        MicrophoneExperiment experiment = new MicrophoneExperiment(script);
        addItemViewHolder(experiment, parent);
        return experiment;
    }

    public PotentialEnergy1 addPotentialEnergy1Question(SheetGroupLayout parent) {
        PotentialEnergy1 question = new PotentialEnergy1(script);
        addItemViewHolder(question, parent);
        return question;
    }

    public MotionAnalysisGraphView addMotionAnalysisGraph(ScriptExperimentRef experiment, SheetGroupLayout parent) {
        MotionAnalysisGraphView item = new MotionAnalysisGraphView(script, this, experiment);
        addItemViewHolder(item, parent);
        return item;
    }

    public Export addExportButton(SheetGroupLayout parent) {
        Export item = new Export(script);
        addItemViewHolder(item, parent);
        return item;
    }
}

interface IActivityStarterViewParent {
    public void startActivityForResultFromView(ActivityStarterView view, Intent intent, int requestCode);

    /**
     * The parent has to provide a unique id that stays the same also if the activity has been reloaded. For example,
     * if views get added in a fix order this should be a normal counter.
     *
     * @return a unique id
     */
    public int getNewChildId();
}

/**
 * Base class for a view that can start a child activity and is able to receive the activity response.
 */
abstract class ActivityStarterView extends FrameLayout {
    protected IActivityStarterViewParent parent;

    public ActivityStarterView(Context context, IActivityStarterViewParent parent) {
        super(context);

        setId(parent.getNewChildId());

        this.parent = parent;
    }

    /**
     * Copies the behaviour of the Activity startActivityForResult method.
     *
     * @param intent the intent to start the activity
     * @param requestCode the request code for that activity
     */
    public void startActivityForResult(Intent intent, int requestCode) {
        parent.startActivityForResultFromView(this, intent, requestCode);
    }

    /**
     * Called when the started activity returns.
     *
     * @param requestCode the code specified in {@link #startActivityForResult(Intent, int)}.
     * @param resultCode the result code
     * @param data the Intent returned by the activity
     */
    abstract public void onActivityResult(int requestCode, int resultCode, Intent data);
}
