package nz.ac.auckland.lablet;

import static java.lang.Math.round;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceView;
import nz.ac.auckland.lablet.camera.decoder.SeekToFrameExtractor;

public class VideoRuler extends Activity implements
    GestureDetector.OnGestureListener,
    SeekToFrameExtractor.IListener {

    private static final String TAG = "VideoRuler";
    private static final int SEEK_MS_SLEEP = 100;
    private static final int SEEK_SCALE = 3000;

    private GestureDetector gestureDetector = null;
    @Nullable
    private SeekToFrameExtractor extractor = null;
    private int currPosition = 0;
    private long lastTime = 0;
    private SurfaceView frameView;
    private Uri uri;
    private boolean waitingForFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_ruler);

        frameView = (SurfaceView) findViewById(R.id.frameViewLayer);
        frameView.setZOrderMediaOverlay(true);

        uri = getIntent().getData();
        gestureDetector = new GestureDetector(this, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        extractor = new SeekToFrameExtractor();
        extractor.setListener(this);
        waitingForFrame = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        waitingForFrame = false;
        if (extractor != null) {
            extractor.setListener(null);
            extractor.release();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        lastTime = 0;
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        currPosition += round(distanceX);
        if (waitingForFrame) {
            return true;
        }
        long newTime = e2.getEventTime();
        if (newTime > lastTime + SEEK_MS_SLEEP) {
            lastTime = newTime;

            if (extractor == null) {
                Log.e(TAG, "null extractor");
                return true;
            }

            if (!extractor.init(getBaseContext(), uri, frameView.getHolder().getSurface())) {
                Log.e(TAG, "media extractor initialization failed");
                return true;
            }

            waitingForFrame = true;
            extractor.seekToFrame(currPosition * SEEK_SCALE);
        }
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return true;
    }

    @Override
    public void onFrameExtracted() {
        waitingForFrame = false;
    }
}
