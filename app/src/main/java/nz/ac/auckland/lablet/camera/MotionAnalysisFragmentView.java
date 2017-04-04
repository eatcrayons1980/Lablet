/*
 * Copyright 2013-2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.camera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Spinner;
import java.util.ArrayList;
import java.util.List;
import nz.ac.auckland.lablet.R;
import nz.ac.auckland.lablet.misc.Unit;
import nz.ac.auckland.lablet.misc.WeakListenable;
import nz.ac.auckland.lablet.views.graph.GraphView2D;
import nz.ac.auckland.lablet.views.graph.MarkerGraphAdapter;
import nz.ac.auckland.lablet.views.graph.MarkerTimeGraphAdapter;
import nz.ac.auckland.lablet.views.graph.SelectedMarkerPainter;
import nz.ac.auckland.lablet.views.graph.TimeMarkerGraphAxis;
import nz.ac.auckland.lablet.views.graph.XPositionMarkerGraphAxis;
import nz.ac.auckland.lablet.views.graph.XSpeedMarkerGraphAxis;
import nz.ac.auckland.lablet.views.graph.YPositionMarkerGraphAxis;
import nz.ac.auckland.lablet.views.graph.YSpeedMarkerGraphAxis;
import nz.ac.auckland.lablet.views.marker.MarkerDataModel;
import nz.ac.auckland.lablet.views.plotview.DrawConfig;
import nz.ac.auckland.lablet.views.table.ITableAdapter;
import nz.ac.auckland.lablet.views.table.MarkerDataTableAdapter;
import nz.ac.auckland.lablet.views.table.RunIdDataTableColumn;
import nz.ac.auckland.lablet.views.table.TableView;
import nz.ac.auckland.lablet.views.table.TimeDataTableColumn;
import nz.ac.auckland.lablet.views.table.XPositionDataTableColumn;
import nz.ac.auckland.lablet.views.table.YPositionDataTableColumn;
import nz.ac.auckland.lablet.vision.MotionTrackingStatusView;
import nz.ac.auckland.lablet.vision.ObjectTrackerAnalysis;
import nz.ac.auckland.lablet.vision.VideoPlayer;
import org.opencv.core.Rect;


class MotionAnalysisSideBar extends WeakListenable<MotionAnalysisSideBar.IListener> {
    public interface IListener {
        void onOpened();
        void onClosed();
    }

    final private FrameLayout sideBar;
    private boolean open = false;
    private AnimatorSet animator;
    final private View parent;
    final int animationDuration = 300;

    public MotionAnalysisSideBar(View parent, View content) {
        this.parent = parent;
        sideBar = (FrameLayout)parent.findViewById(R.id.sideBarFrame);

        sideBar.addView(content);
    }

    public float getWidth() {
        return sideBar.getWidth();
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        if (open)
            return;
        open = true;
        notifyOnOpened();

        if (animator != null)
            animator.cancel();

        animator = new AnimatorSet();
        int width = parent.getWidth();
        animator.play(ObjectAnimator.ofFloat(sideBar, View.X, width, width - sideBar.getWidth()));
        animator.setDuration(animationDuration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                sideBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                animator = null;
            }
        });
        animator.start();
    }

    public void close() {
        if (!open)
            return;
        open = false;
        notifyOnClosed();

        if (animator != null)
            animator.cancel();

        animator = new AnimatorSet();
        int width = parent.getWidth();
        animator.play(ObjectAnimator.ofFloat(sideBar, View.X, width - sideBar.getWidth(), width));
        animator.setDuration(animationDuration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animator = null;
                sideBar.setVisibility(View.INVISIBLE);
            }
        });
        animator.start();
    }

    private void notifyOnOpened() {
        for (IListener listener : getListeners())
            listener.onOpened();
    }

    private void notifyOnClosed() {
        for (IListener listener : getListeners())
            listener.onClosed();
    }
}

class MotionAnalysisFragmentView extends FrameLayout {
    final MotionAnalysis sensorAnalysis;
    private MarkerDataTableAdapter markerDataTableAdapter;
    final private FrameContainerView runContainerView;
    final private GraphView2D graphView;
    private SelectedMarkerPainter selectedMarkerPainter;
    final private Spinner graphSpinner;
    final private ViewGroup sideBarView;
    final private TableView tableView;
    final private MotionAnalysisSideBar sideBar;
    final private FrameDataSeekBar frameDataSeekBar;
    final private VideoPlayer videoPlayer;
    final private List<GraphSpinnerEntry> graphSpinnerEntryList = new ArrayList<>();
    final private MotionTrackingStatusView motionTrackingStatusView;
    final ObjectTrackerAnalysis.IListener trackingListener;
    private boolean releaseAdaptersWhenDrawerClosed = false;

    private class GraphSpinnerEntry {
        private String name;
        private MarkerTimeGraphAdapter markerGraphAdapter;
        private boolean fit = false;

        public GraphSpinnerEntry(String name, MarkerTimeGraphAdapter adapter) {
            this.name = name;
            this.markerGraphAdapter = adapter;
        }

        public GraphSpinnerEntry(String name, MarkerTimeGraphAdapter adapter, boolean fit) {
            this.name = name;
            this.markerGraphAdapter = adapter;
            this.fit = fit;
        }

        public void release() {
            markerGraphAdapter.release();
        }

        public String toString() {
            return name;
        }

        public MarkerTimeGraphAdapter getMarkerGraphAdapter() {
            return markerGraphAdapter;
        }

        public boolean getFit() {
            return fit;
        }
    }

    private SideBarState sideBarState = new ClosedSideBarState();

    private void setSideBarState(SideBarState state) {
        if (sideBarState != null)
            sideBarState.leaveState();
        sideBarState = state;
        if (sideBarState != null)
            sideBarState.enterState();
    }

    abstract class SideBarState {
        abstract void nextState();
        void enterState() {}
        void leaveState() {}
        abstract int getIcon();
    }

    class ClosedSideBarState extends SideBarState {
        @Override
        void nextState() {
            setSideBarState(new HalfOpenedSideBarState());
        }

        @Override
        int getIcon() {
            return R.drawable.ic_chart_line;
        }
    }

    class HalfOpenedSideBarState extends SideBarState {
        @Override
        void nextState() {
            setSideBarState(new OpenedSideBarState());
        }

        @Override
        void enterState() {
            sideBarView.setAlpha(0.4f);
            graphView.setClickable(false);

            tableView.setVisibility(GONE);
            ViewGroup.LayoutParams params = sideBarView.getLayoutParams();
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            sideBarView.setLayoutParams(params);
            sideBar.open();
        }

        @Override
        void leaveState() {
            graphView.setClickable(true);
            sideBarView.setAlpha(0.8f);

            ViewGroup.LayoutParams params = sideBarView.getLayoutParams();
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            sideBarView.setLayoutParams(params);
            tableView.setVisibility(VISIBLE);
        }

        @Override
        int getIcon() {
            return R.drawable.ic_chart_line_plus;
        }
    }

    class OpenedSideBarState extends SideBarState {
        @Override
        void nextState() {
            setSideBarState(new ClosedSideBarState());
        }

        @Override
        void leaveState() {
            sideBar.close();
        }

        @Override
        int getIcon() {
            return R.drawable.ic_chart_line_less;
        }
    }

    // keep hard reference to the listener!
    private MotionAnalysisSideBar.IListener sideBarListener = new MotionAnalysisSideBar.IListener() {
        @Override
        public void onOpened() {
            if (releaseAdaptersWhenDrawerClosed) {
                selectGraphAdapter(graphSpinner.getSelectedItemPosition());
                tableView.setAdapter(markerDataTableAdapter);
            }
        }

        @Override
        public void onClosed() {
            if (releaseAdaptersWhenDrawerClosed) {
                tableView.setAdapter((ITableAdapter) null);
                selectGraphAdapter(-1);
            }
        }
    };

    private class PlayVideoSeekBarAction implements FrameDataSeekBar.Action {
        @Override
        public int getIconResource() {
            return R.drawable.ic_media_play_big;
        }

        @Override
        public FrameDataSeekBar.Action perform() {
            videoPlayer.play();
            return new PauseVideoSeekBarAction();
        }

        @Override
        public void onActionUncalledRemoved() {

        }
    }

    private class PauseVideoSeekBarAction implements FrameDataSeekBar.Action {
        @Override
        public int getIconResource() {
            return R.drawable.ic_media_pause_big;
        }

        @Override
        public FrameDataSeekBar.Action perform() {
            onActionUncalledRemoved();
            return new PlayVideoSeekBarAction();
        }

        @Override
        public void onActionUncalledRemoved() {
            videoPlayer.stop();
        }
    }

    private class StopTrackingSeekBarAction implements FrameDataSeekBar.Action {
        @Override
        public int getIconResource() {
            return R.drawable.ic_media_stop_big;
        }

        @Override
        public FrameDataSeekBar.Action perform() {
            onActionUncalledRemoved();
            return new PlayVideoSeekBarAction();
        }

        @Override
        public void onActionUncalledRemoved() {
            sensorAnalysis.getObjectTrackerAnalysis().stopTracking();
        }
    }

    public MotionAnalysisFragmentView(Context context, final MotionAnalysis sensorAnalysis) {
        super(context);

        this.sensorAnalysis = sensorAnalysis;
        final LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View mainView = inflater.inflate(R.layout.motion_analysis, this, true);
        assert mainView != null;

        frameDataSeekBar = (FrameDataSeekBar)mainView.findViewById(R.id.frameDataSeekBar);
        frameDataSeekBar.setAction(new PlayVideoSeekBarAction());

        final VideoPlayer.IListener videoListener = new VideoPlayer.IListener() {
            @Override
            public void onFinished() {
                frameDataSeekBar.setAction(new PlayVideoSeekBarAction());
            }
        };

        videoPlayer = new VideoPlayer(sensorAnalysis.getFrameDataModel());
        videoPlayer.setListener(videoListener);

        sideBarView = (ViewGroup)inflater.inflate(R.layout.motion_analysis_data_side_bar, null, false);
        sideBar = new MotionAnalysisSideBar(mainView, sideBarView);

        runContainerView = (FrameContainerView)mainView.findViewById(R.id.frameContainerView);

        graphSpinner = (Spinner)sideBarView.findViewById(R.id.graphSpinner);

        // marker graph view
        graphView = (GraphView2D)sideBarView.findViewById(R.id.tagMarkerGraphView);
        assert graphView != null;

        tableView = (TableView)sideBarView.findViewById(R.id.tableView);
        assert tableView != null;
        tableView.setColumnWeights(1f, 1.8f, 1.4f, 1.4f);
        tableView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == 0)
                    return;
                // ignore header row
                int frameId = sensorAnalysis.getTagMarkers().getMarkerDataAt(i - 1).getId();
                sensorAnalysis.getFrameDataModel().setCurrentFrame(frameId);
            }
        });

        final CameraExperimentFrameView sensorAnalysisView = new CameraExperimentFrameView(context, sensorAnalysis);
        frameDataSeekBar.setTo(sensorAnalysis.getFrameDataModel(), sensorAnalysis.getTimeData());
        runContainerView.setTo(sensorAnalysisView, frameDataSeekBar, sensorAnalysis);

        final Unit xUnit = sensorAnalysis.getXUnit();
        final Unit yUnit = sensorAnalysis.getYUnit();
        final Unit tUnit = sensorAnalysis.getTUnit();

        // marker table view
        final ITimeData timeData = sensorAnalysis.getTimeData();
        markerDataTableAdapter = new MarkerDataTableAdapter(sensorAnalysis.getTagMarkers());
        markerDataTableAdapter.addColumn(new RunIdDataTableColumn("frame"));
        markerDataTableAdapter.addColumn(new TimeDataTableColumn(tUnit, timeData));
        markerDataTableAdapter.addColumn(new XPositionDataTableColumn(xUnit));
        markerDataTableAdapter.addColumn(new YPositionDataTableColumn(yUnit));

        MarkerDataModel markerDataModel = sensorAnalysis.getTagMarkers();
        ITimeData timeCalibration = sensorAnalysis.getTimeData();
        if (timeCalibration.getSize() > 400)
            releaseAdaptersWhenDrawerClosed = true;

        // graph spinner
        graphSpinnerEntryList.add(new GraphSpinnerEntry("Position Data", new MarkerTimeGraphAdapter(markerDataModel,
                timeCalibration, "Position Data",
                new XPositionMarkerGraphAxis(xUnit, sensorAnalysis.getXMinRangeGetter()),
                new YPositionMarkerGraphAxis(yUnit, sensorAnalysis.getYMinRangeGetter()))));
        graphSpinnerEntryList.add(new GraphSpinnerEntry("x-Velocity", new MarkerTimeGraphAdapter(markerDataModel,
                timeCalibration, "x-Velocity", new TimeMarkerGraphAxis(tUnit),
                new XSpeedMarkerGraphAxis(xUnit, tUnit)), true));
        graphSpinnerEntryList.add(new GraphSpinnerEntry("y-Velocity", new MarkerTimeGraphAdapter(markerDataModel,
                timeCalibration, "y-Velocity", new TimeMarkerGraphAxis(tUnit),
                new YSpeedMarkerGraphAxis(yUnit, tUnit)), true));
        graphSpinnerEntryList.add(new GraphSpinnerEntry("Time vs x-Position", new MarkerTimeGraphAdapter(markerDataModel,
                timeCalibration, "Time vs x-Position", new TimeMarkerGraphAxis(tUnit),
                new XPositionMarkerGraphAxis(xUnit, sensorAnalysis.getXMinRangeGetter()))));
        graphSpinnerEntryList.add(new GraphSpinnerEntry("Time vs y-Position", new MarkerTimeGraphAdapter(markerDataModel,
                timeCalibration, "Time vs y-Position", new TimeMarkerGraphAxis(tUnit),
                new YPositionMarkerGraphAxis(yUnit, sensorAnalysis.getYMinRangeGetter()))));

        graphSpinner.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item,
                graphSpinnerEntryList));
        graphSpinner.setSelection(0);
        graphSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                selectGraphAdapter(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                graphView.setAdapter(null);
            }
        });

        // setup the load/ unloading of the view data adapters
        sideBar.addListener(sideBarListener);

        if (!releaseAdaptersWhenDrawerClosed) {
            tableView.setAdapter(markerDataTableAdapter);
            selectGraphAdapter(graphSpinner.getSelectedItemPosition());
        }

        motionTrackingStatusView = (MotionTrackingStatusView)mainView.findViewById(R.id.trackingStatusView);
        motionTrackingStatusView.setObjectTrackerAnalysis(sensorAnalysis.getObjectTrackerAnalysis());

        trackingListener = new ObjectTrackerAnalysis.IListener() {
            @Override
            public void onTrackingStart() {
                frameDataSeekBar.setAction(new StopTrackingSeekBarAction());
            }

            @Override
            public void onTrackingFinished(SparseArray<Rect> results) {
                frameDataSeekBar.setAction(new PlayVideoSeekBarAction());
            }

            @Override
            public void onTrackingUpdate(int frameNumber, int totalNumberOfFrames) {
            }
        };

        sensorAnalysis.getObjectTrackerAnalysis().addListener(trackingListener);
    }

    /**
     *
     * @return an icon id for the new state
     */
    public void onToggleSidebar() {
        sideBarState.nextState();
    }

    public int getSideBarStatusIcon() {
        return sideBarState.getIcon();
    }

    private void selectGraphAdapter(int i) {
        if (i < 0) {
            graphView.setFitPainter(null);
            graphView.setAdapter(null);
            graphView.removePlotPainter(selectedMarkerPainter);
            return;
        }
        if (releaseAdaptersWhenDrawerClosed && !sideBar.isOpen())
            return;
        GraphSpinnerEntry entry = graphSpinnerEntryList.get(i);
        MarkerGraphAdapter adapter = entry.getMarkerGraphAdapter();
        graphView.setAdapter(adapter);
        DrawConfig selectedMarkerConfig = new DrawConfig(getContext());
        selectedMarkerConfig.getMarkerPaint().setColor(Color.RED);
        selectedMarkerPainter = new SelectedMarkerPainter(adapter, selectedMarkerConfig);
        graphView.addForegroundPainter(selectedMarkerPainter);
    }

    @Override
    protected void finalize() throws Throwable {
        release();
        super.finalize();
    }

    public void release() {
        markerDataTableAdapter.release();
        runContainerView.release();
        tableView.setAdapter((ITableAdapter)null);
        graphView.setAdapter(null);
        graphSpinner.setAdapter(null);
        for (GraphSpinnerEntry entry : graphSpinnerEntryList)
            entry.release();
        graphSpinnerEntryList.clear();
    }

    public void onPause() {
        runContainerView.onPause();
    }

    public void onResume() {
        runContainerView.onResume();
    }
}
