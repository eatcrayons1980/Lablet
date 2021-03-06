/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.views.plotview;

import android.graphics.*;
import android.os.Handler;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;


public class ThreadStrategyPainter extends BufferedStrategyPainter {
    final private ThreadRenderTask renderTask = new ThreadRenderTask();
    final private BitmapBuffer threadBitmap = new BitmapBuffer();
    private boolean invalidated = false;

    public class ThreadCookie {
        final public List<RenderPayload> payloads;
        final public Bitmap bitmap;
        final public RectF bitmapRealRange;
        final public boolean isCompleteRedraw;

        public ThreadCookie(List<RenderPayload> payloads, Bitmap bitmap, RectF bitmapRealRange,
                            boolean isCompleteRedraw) {
            this.payloads = payloads;
            this.bitmap = bitmap;
            this.bitmapRealRange = bitmapRealRange;
            this.isCompleteRedraw = isCompleteRedraw;
        }
    }

    class ThreadRenderTask {
        final private AtomicBoolean running = new AtomicBoolean();
        private Future thread;
        private ExecutorService threadPool = Executors.newCachedThreadPool();
        final private Handler uiHandler = new Handler();
        private ThreadStrategyPainter.ThreadCookie cookie;

        public ThreadRenderTask() {
            running.set(false);
        }

        Runnable renderRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap bitmap = cookie.bitmap;
                    Canvas bitmapCanvas = new Canvas(bitmap);
                    for (StrategyPainter.RenderPayload payload : cookie.payloads)
                        payload.getPainter().render(bitmapCanvas, payload);

                    publishBitmap(cookie);
                    cookie = null;
                } catch (Exception e) {
                    e.printStackTrace();
                    running.set(false);
                    cookie = null;
                }
            }
        };

        private void publishBitmap(final ThreadStrategyPainter.ThreadCookie cookie) {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    onMergeOffScreenRendering(cookie);

                    running.set(false);

                    onRenderingFinished();
                }
            });
        }

        public boolean start(ThreadStrategyPainter.ThreadCookie threadCookie) {
            if (running.get())
                return false;
            this.cookie = threadCookie;
            running.set(true);
            thread = threadPool.submit(renderRunnable);
            return true;
        }

        public void stop() {
            if (!running.get())
                return;
            running.set(false);
            try {
                thread.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }

        public boolean isRendering() {
            return running.get();
        }
    }

    @Override
    public void release() {
        super.release();

        threadBitmap.release();
    }

    @Override
    protected void onNewDirtyRegions(RectF newDirt) {
        super.onNewDirtyRegions(newDirt);

        if (invalidated)
            triggerOffScreenRendering(null);
        else
            triggerOffScreenRendering(newDirt);
    }

    @Override
    public void invalidate() {
        invalidated = true;
        triggerOffScreenRendering(null);
    }

    @Override
    public boolean hasThreads() {
        return true;
    }

    @Override
    protected void onDirectDraw() {

    }

    private boolean isRendering() {
        return renderTask.isRendering();
    }

    @Override
    public boolean hasFreeRenderingPipe() {
        return !renderTask.isRendering();
    }


    private void triggerOffScreenRendering(RectF newDirt) {
        if (containerView == null || getBufferBitmap() == null)
            return;
        if (isRendering())
            return;

        try {
            RectF viewRange = enlargeViewRangeToBufferRange(getContainerView().getRange());
            RectF range = newDirt;
            if (invalidated)
                range = viewRange;
            List<RenderPayload> dirt = collectAllRenderPayloads(true, range, viewRange);
            if (dirt.size() == 0) {
                if (invalidated)
                    // clear the buffer, this can happen if the data has just been cleared
                    startEditingBufferBitmap(true);
                return;
            }
            Bitmap renderBitmap = threadBitmap.getBuffer(getBufferBitmap());
            renderTask.start(new ThreadCookie(dirt, renderBitmap, new RectF(getBufferRealRect()), invalidated));

        } finally {
            clearDirtyRect();
            invalidated = false;
        }
    }

    protected void onMergeOffScreenRendering(ThreadCookie cookie) {
        if (containerView == null)
            return;

        Canvas canvas = startEditingBufferBitmap(cookie.isCompleteRedraw);

        RectF targetRect = containerView.toScreen(cookie.bitmapRealRange);
        canvas.drawBitmap(cookie.bitmap, null, targetRect, null);
    }

    protected void onRenderingFinished() {
        if (containerView == null)
            return;

        containerView.invalidate();

        onNewDirtyRegions(getDirtyRect());
    }
}
