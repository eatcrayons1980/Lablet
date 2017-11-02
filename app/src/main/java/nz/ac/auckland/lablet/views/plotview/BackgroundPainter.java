/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.views.plotview;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import java.util.List;
import nz.ac.auckland.lablet.views.plotview.axes.LabelPartitioner;
import nz.ac.auckland.lablet.views.plotview.axes.XAxisView;
import nz.ac.auckland.lablet.views.plotview.axes.YAxisView;


public class BackgroundPainter extends AbstractPlotPainter {

    private final int DEFAULT_BACKGROUND_COLOR = Color.DKGRAY;
    private final int DEFAULT_MAIN_GRID_COLOR = Color.LTGRAY;
    final private Rect bounds = new Rect();
    final private XAxisView xAxisView;
    final private YAxisView yAxisView;
    private Paint backgroundPaint = new Paint();
    private Paint mainGridPaint = new Paint();
    private boolean showXGrid = false;
    private boolean showYGrid = false;

    private PlotPainterContainerView containerView;

    BackgroundPainter(XAxisView xAxisView, YAxisView yAxisView) {
        this.xAxisView = xAxisView;
        this.yAxisView = yAxisView;

        backgroundPaint.setColor(DEFAULT_BACKGROUND_COLOR);
        backgroundPaint.setStyle(Paint.Style.FILL);

        mainGridPaint.setColor(DEFAULT_MAIN_GRID_COLOR);
        mainGridPaint.setStrokeWidth(1f);
    }

    public Paint getBackgroundPaint() {
        return backgroundPaint;
    }

    public void setBackgroundPaint(Paint backgroundPaint) {
        this.backgroundPaint = backgroundPaint;
    }

    public boolean showXGrid() {
        return showXGrid;
    }

    public void setShowXGrid(boolean showXGrid) {
        this.showXGrid = showXGrid;
    }

    public boolean showYGrid() {
        return showYGrid;
    }

    public void setShowYGrid(boolean showYGrid) {
        this.showYGrid = showYGrid;
    }

    public void setShowGrid(boolean showGrid) {
        this.showXGrid = showGrid;
        this.showYGrid = showGrid;
    }

    @Override
    public void setContainer(PlotPainterContainerView view) {
        containerView = view;
    }

    @Override
    public void onSizeChanged(int width, int height, int oldw, int oldh) {
        bounds.set(0, 0, width, height);
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (backgroundPaint != null)
            canvas.drawRect(bounds, backgroundPaint);

        if (showXGrid) {
            List<LabelPartitioner.LabelEntry> labels = xAxisView.getLabels();
            for (LabelPartitioner.LabelEntry label : labels) {
                float position = label.relativePosition * bounds.width();
                canvas.drawLine(position, 0f, position, bounds.height(), mainGridPaint);
            }
        }

        if (showYGrid) {
            List<LabelPartitioner.LabelEntry> labels = yAxisView.getLabels();
            for (LabelPartitioner.LabelEntry label : labels) {
                float position =(1.f - label.relativePosition) * bounds.height();
                canvas.drawLine(0f, position, bounds.width(), position, mainGridPaint);
            }
        }

    }
}
