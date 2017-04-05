package nz.ac.auckland.lablet;

import static java.lang.Math.round;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceView;
import nz.ac.auckland.lablet.camera.decoder.SeekToFrameExtractor;

public class VideoRuler extends Activity implements GestureDetector.OnGestureListener {

    private static final String TAG = "VideoRuler";
    public static final int SEEK_MS_SLEEP = 100;
    public static final int SEEK_SCALE = 3000;

    private GestureDetector gestureDetector = null;
    private SeekToFrameExtractor extractor = null;
    private int currPosition = 0;
    private long lastTime = 0;
    private SurfaceView frameView;
    private Uri uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_ruler);

        frameView = (SurfaceView) findViewById(R.id.frameViewLayer);
        frameView.setZOrderMediaOverlay(true);

        uri = getIntent().getData();
        gestureDetector = new GestureDetector(this, this);

        extractor = new SeekToFrameExtractor();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (extractor == null || !extractor.init(this, uri, frameView.getHolder().getSurface())) {
            Log.e(TAG, "surface could not be attached to extractor");
            extractor = null;
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
        long newTime = e2.getEventTime();
        if (newTime > lastTime + SEEK_MS_SLEEP) {
            lastTime = newTime;
            if (extractor == null) {
                extractor = new SeekToFrameExtractor();
                if (!extractor.init(getBaseContext(), uri, frameView.getHolder().getSurface())) {
                    Log.e(TAG, "surface could not be attached to extractor");
                    extractor = null;
                }
            } else {
                extractor.seekToFrame(currPosition * SEEK_SCALE);
            }
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
}
