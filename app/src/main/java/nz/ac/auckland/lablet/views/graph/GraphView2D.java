/*
 * Copyright 2013-2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.views.graph;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import nz.ac.auckland.lablet.misc.Unit;
import nz.ac.auckland.lablet.views.ZoomDialog;
import nz.ac.auckland.lablet.views.plotview.*;


/**
 * Abstract base class for graph adapters.
 */
abstract class AbstractGraphAdapter extends AbstractXYDataAdapter {
    public interface IGraphDataAxis {
        int size();
        Number getValue(int index);
        String getTitle();
        Unit getUnit();
        Number getMinRange();
    }

    protected IGraphDataAxis xAxis;
    protected IGraphDataAxis yAxis;

    @Override
    public int getSize() {
        return Math.min(getXAxis().size(), getYAxis().size());
    }

    abstract public void setTitle(String title);
    abstract public String getTitle();

    public IGraphDataAxis getXAxis() {
        return xAxis;
    }

    public IGraphDataAxis getYAxis() {
        return yAxis;
    }

    public void setXAxis(IGraphDataAxis axis) {
        xAxis = axis;
    }

    public void setYAxis(IGraphDataAxis axis) {
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

            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    //zoom();

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
                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialogInterface) {
                            zoomGraphView.release();
                        }
                    });
                    dialog.show();
                }
            });

        } else {
            setOnClickListener(null);
        }
    }

    public void release() {
        setFitPainter(null);
        setAdapter(null);
    }

    /**
     * Sets linear fit data for this graph view.
     *
     * This method takes a preexisting linear fit painter and
     * sets it in the graph view. It is important to note that
     * this does not set linear fit data for the existing data
     * in the view. Instead, the linear fit painter must be
     * created separately, with its own data, and need not reflect
     * the linear fit of the other data in the view.
     *
     * @param fitPainter    the painter to be added as the linear fit data
     * @see                 LinearFitPainter
     */
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


