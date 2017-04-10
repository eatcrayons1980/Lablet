package nz.ac.auckland.lablet;

import static java.lang.Math.round;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import nz.ac.auckland.lablet.camera.decoder.SeekToFrameExtractor;

public class VideoRuler extends Activity implements
    GestureDetector.OnGestureListener,
    SurfaceHolder.Callback,
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
        frameView.getHolder().addCallback(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        waitingForFrame = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
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
        Log.d(TAG, "scroll position currently " + currPosition);
        long newTime = e2.getEventTime();
        if (newTime > lastTime + SEEK_MS_SLEEP) {
            lastTime = newTime;

            if (extractor == null) {
                Log.e(TAG, "null extractor");
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

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surface created for SeekToFrame");
        extractor = new SeekToFrameExtractor();
        extractor.init(getBaseContext(), uri, frameView.getHolder().getSurface());
        extractor.setListener(this);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surface changed for SeekToFrame");
        if (extractor != null) {
            extractor.release();
            waitingForFrame = false;
        }
        extractor = new SeekToFrameExtractor();
        extractor.init(getBaseContext(), uri, frameView.getHolder().getSurface());
        extractor.setListener(this);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surface destroyed for SeekToFrame");
        if (extractor != null) {
            extractor.release();
            waitingForFrame = false;
        }
    }
}
