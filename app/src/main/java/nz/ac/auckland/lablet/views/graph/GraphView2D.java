/*
 * Copyright 2013-2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.views.graph;

import static nz.ac.auckland.lablet.utility.FileHelper.experimentFileOutputStream;
import static nz.ac.auckland.lablet.utility.FileHelper.experimentFileWriter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import nz.ac.auckland.lablet.ExperimentAnalysisActivity;
import nz.ac.auckland.lablet.misc.Unit;
import nz.ac.auckland.lablet.script.ScriptRunnerActivity;
import nz.ac.auckland.lablet.utility.FileHelper;
import nz.ac.auckland.lablet.views.ZoomDialog;
import nz.ac.auckland.lablet.views.plotview.AbstractXYDataAdapter;
import nz.ac.auckland.lablet.views.plotview.BufferedDirectStrategyPainter;
import nz.ac.auckland.lablet.views.plotview.CloneablePlotDataAdapter;
import nz.ac.auckland.lablet.views.plotview.LinearFitPainter;
import nz.ac.auckland.lablet.views.plotview.PlotView;
import nz.ac.auckland.lablet.views.plotview.Region1D;
import nz.ac.auckland.lablet.views.plotview.StrategyPainter;
import nz.ac.auckland.lablet.views.plotview.XYConcurrentPainter;
import nz.ac.auckland.lablet.views.plotview.XYDataAdapter;
import org.opencv.core.Point;


/**
 * Abstract base class for graph adapters.
 */
abstract class AbstractGraphAdapter extends AbstractXYDataAdapter {
    interface IGraphDataAxis {
        int size();
        Number getValue(int index);
        String getTitle();
        Unit getUnit();
        Number getMinRange();
    }

    private IGraphDataAxis xAxis;
    private IGraphDataAxis yAxis;

    @Override
    public int getSize() {
        return Math.min(getXAxis().size(), getYAxis().size());
    }

    abstract public void setTitle(String title);
    abstract public String getTitle();

    IGraphDataAxis getXAxis() {
        return xAxis;
    }

    IGraphDataAxis getYAxis() {
        return yAxis;
    }

    void setXAxis(IGraphDataAxis axis) {
        xAxis = axis;
    }

    void setYAxis(IGraphDataAxis axis) {
        yAxis = axis;
    }

    @Override
    public Number getX(int i) {
        return getXAxis().getValue(i);
    }

    @Override
    public Number getY(int i) {
        return getYAxis().getValue(i);
    }

    @Override
    public CloneablePlotDataAdapter clone(Region1D region) {
        int start = region.getMin();
        if (start > 0)
            start--;
        XYDataAdapter xyDataAdapter = new XYDataAdapter(start);
        for (int i = start; i < region.getMax() + 1; i++) {
            xyDataAdapter.addData(getX(i), getY(i));
        }
        return xyDataAdapter;
    }
}


public class GraphView2D extends PlotView {
    private static final String TAG = "GraphView2D";
    private AbstractGraphAdapter adapter;

    private StrategyPainter painter;
    private LinearFitPainter fitPainter;

    // max layout sizes in dp
    private int maxWidth = -1;
    private int maxHeight = -1;

    // in dp
    public int getMaxWidth() {
        return maxWidth;
    }
    public void setMaxWidth(float maxWidth) {
        final float scale = getResources().getDisplayMetrics().density;
        this.maxWidth = (int)(scale * maxWidth);
    }
    public int getMaxHeight() {
        return maxHeight;
    }
    public void setMaxHeight(int maxHeight) {
        final float scale = getResources().getDisplayMetrics().density;
        this.maxHeight = (int)(scale * maxHeight);
    }

    public GraphView2D(Context context, String title, boolean zoomOnClick) {
        super(context);

        getTitleView().setTitle(title);

        if (zoomOnClick)
            setZoomOnClick(true);
    }

    public GraphView2D(Context context, AttributeSet attrs) {
        super(context, attrs);

        setZoomOnClick(true);
    }

    public void setZoomOnClick(boolean zoomable) {
        if (zoomable) {

            setOnClickListener(view -> {
                final GraphView2D zoomGraphView = new GraphView2D(getContext(), adapter.getTitle(), false);
                zoomGraphView.setAdapter(adapter);

                Rect startBounds = new Rect();
                Rect finalBounds = new Rect();

                getGlobalVisibleRect(startBounds);
                ViewGroup parent = ((ViewGroup)getRootView());
                parent.getDrawingRect(finalBounds);

                if (fitPainter != null)
                    zoomGraphView.addPlotPainter(fitPainter);

                ZoomDialog dialog = new ZoomDialog(getContext(), zoomGraphView, startBounds, finalBounds);
                dialog.setOnDismissListener(dialogInterface -> {
                    saveSelector();
                    zoomGraphView.release();
                });
                dialog.show();
            });

        } else {
            setOnClickListener(null);
        }
    }

    /**
     * Calculates the correct path in which to save plot data and then saves it.
     */
    private void saveSelector() {
        File dir;
        String pngFilename;
        String csvFilename;

        if (getContext() instanceof ScriptRunnerActivity) {
            ScriptRunnerActivity activity = (ScriptRunnerActivity) getContext();
            int sheet = activity.getCurrentPagerItem();
            String title = getTitleView().getTitle();
            dir = activity.getScriptUserDataDir();
            pngFilename = "Sheet" + sheet + "_" + title + ".png";
            csvFilename = "Sheet" + sheet + "_" + title + ".csv";
        } else if (getContext() instanceof ExperimentAnalysisActivity) {
            ExperimentAnalysisActivity activity = (ExperimentAnalysisActivity) getContext();
            dir = activity.getExperimentAnalysis().getExperimentData().getStorageDir();
            pngFilename = getTitleView().getTitle() + ".png";
            csvFilename = getTitleView().getTitle() + ".csv";
        } else {
            Toast.makeText(getContext(), "Graph data NOT saved", Toast.LENGTH_LONG).show();
            return;
        }
        saveToExperiment(dir, pngFilename, csvFilename);
    }

    /**
     * Saves plot data into png and csv formats.
     * @param dir directory in which to save the files
     * @param pngFilename filename for png file
     * @param csvFilename filename for csv file
     */
    private void saveToExperiment(
        final File dir,
        final String pngFilename,
        final String csvFilename) {

        final FileOutputStream pngFile = experimentFileOutputStream(dir, pngFilename);
        final FileWriter csvFile = experimentFileWriter(dir, csvFilename);

        int width = getWidth();
        int height = getHeight();
        int measureWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int measureHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);

        if (pngFile != null) {
            // draw graph to a bitmap
            setAdapter(adapter);
            measure(measureWidth, measureHeight);
            layout(0, 0, getMeasuredWidth(), getMeasuredHeight());
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            draw(canvas);

            // compress and save bitmap
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngFile);
            try {
                pngFile.close();
            } catch (IOException e) {
                Log.e(TAG, "Graph PNG file could not be closed for some reason.");
            }
        }

        if (csvFile != null) {
            // save data points to CSV file
            List<Point> dataPoints = new ArrayList<>();
            for (int i = 0; i < adapter.getSize(); i++) {
                dataPoints.add(new Point(adapter.getX(i).floatValue(), adapter.getY(i).floatValue()));
            }
            FileHelper.writePlotDataToCSV(dataPoints, csvFile);
        }
        Toast.makeText(getContext(), "Graph data saved", Toast.LENGTH_LONG).show();
    }

    public void release() {
        setFitPainter(null);
        setAdapter(null);
    }

    public void setFitPainter(LinearFitPainter fitPainter) {
        if (this.fitPainter != null) {
            this.fitPainter.setDataAdapter(null);
            removePlotPainter(this.fitPainter);
        }

        this.fitPainter = fitPainter;

        if (this.fitPainter != null)
            addPlotPainter(this.fitPainter);
    }

    public void setAdapter(AbstractGraphAdapter adapter) {
        if (this.adapter != null)
            removePlotPainter(painter);
        painter = null;
        this.adapter = adapter;
        if (adapter == null)
            return;

        getTitleView().setTitle(adapter.getTitle());
        getXAxisView().setTitle(this.adapter.getXAxis().getTitle());
        getXAxisView().setUnit(this.adapter.getXAxis().getUnit());
        getYAxisView().setTitle(this.adapter.getYAxis().getTitle());
        getYAxisView().setUnit(this.adapter.getYAxis().getUnit());

        setMinXRange(this.adapter.getXAxis().getMinRange().floatValue());
        setMinYRange(this.adapter.getYAxis().getMinRange().floatValue());

        getBackgroundPainter().setShowXGrid(true);
        getBackgroundPainter().setShowYGrid(true);

        /*if (adapter.size() < 100)
            painter = new BufferedStrategyPainter();
        else
            painter = new ThreadStrategyPainter();*/
        painter = new BufferedDirectStrategyPainter();

        XYConcurrentPainter xyConcurrentPainter = new XYConcurrentPainter(adapter, getContext());
        painter.addChild(xyConcurrentPainter);
        addPlotPainter(painter);

        setAutoRange(AUTO_RANGE_ZOOM, AUTO_RANGE_ZOOM);

        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Adjust width as necessary
        int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        if(maxWidth > 0 && maxWidth < measuredWidth) {
            int measureMode = MeasureSpec.getMode(widthMeasureSpec);
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, measureMode);
        }
        // Adjust height as necessary
        int measuredHeight = MeasureSpec.getSize(heightMeasureSpec);
        if(maxHeight > 0 && maxHeight < measuredHeight) {
            int measureMode = MeasureSpec.getMode(heightMeasureSpec);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, measureMode);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}


