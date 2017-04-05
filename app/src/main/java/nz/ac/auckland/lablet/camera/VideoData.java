/*
 * Copyright 2013-2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.camera;

import android.graphics.PointF;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import nz.ac.auckland.lablet.experiment.AbstractSensorData;
import nz.ac.auckland.lablet.experiment.IExperimentSensor;


/**
 * Holds all important data from a camera experiment.
 */
public class VideoData extends AbstractSensorData {

    static final String DATA_TYPE = "Video";
    final static int LOW_FRAME_RATE = 10;
    private static final String TAG = "VideoData";
    private static final float DEFAULT_FRAME_RATE = 24.0f;
    private String videoFileName;
    // milli seconds
    private long videoDuration;
    private int videoWidth;
    private int videoHeight;
    private int videoFrameRate;
    private float recordingFrameRate;

    public VideoData(IExperimentSensor sourceSensor) {
        super(sourceSensor);
    }

    public VideoData() {
        super();
    }

    @Override
    public String getDataType() {
        return DATA_TYPE;
    }

    float getMaxRawX() {
        return 100.f;
    }

    float getMaxRawY() {
        float xToYRatio = (float)videoWidth / videoHeight;
        float xMax = getMaxRawX();
        return xMax / xToYRatio;
    }

    /**
     * Converts coordinates in marker space into coordinates in video space.
     *
     * @param markerPoint Point in marker space
     * @return Coordinates in video space
     */
    public PointF toVideoPoint(PointF markerPoint) {
        PointF videoPos = new PointF();
        int videoX = (int) (markerPoint.x / getMaxRawX() * getVideoWidth());
        float ySwappedDir = getMaxRawY() - markerPoint.y;
        int videoY = (int) (ySwappedDir / getMaxRawY() * getVideoHeight());
        videoPos.set(videoX, videoY);
        return videoPos;
    }

    /**
     * Converts coordinates in video space into coordinates in marker space.
     *
     * @param videoPoint Point in video space
     * @return Coordinates in marker space
     */
    public PointF toMarkerPoint(PointF videoPoint) {
        PointF markerPos = new PointF();
        float markerX = (videoPoint.x / (float)this.getVideoWidth()) * this.getMaxRawX();
        float markerY = this.getMaxRawY() - ((videoPoint.y / (float)this.getVideoHeight()) * this.getMaxRawY());
        markerPos.set(markerX, markerY);
        return markerPos;
    }

    @Override
    public boolean loadExperimentData(Bundle bundle, File storageDir) {
        if (!super.loadExperimentData(bundle, storageDir))
            return false;

        setVideoFileName(storageDir, bundle.getString("videoName"));

        recordingFrameRate = bundle.getFloat("recordingFrameRate");
        if (recordingFrameRate <= 0) {
            if (videoFrameRate > 0) {
                Log.i(TAG, "no recording frame rate found - using video frame rate");
                recordingFrameRate = videoFrameRate;
            } else {
                Log.i(TAG, "no recording frame rate found - using " + DEFAULT_FRAME_RATE);
                recordingFrameRate = DEFAULT_FRAME_RATE;
            }
        }
        return true;
    }

    @Override
    protected Bundle experimentDataToBundle() {
        Bundle bundle = super.experimentDataToBundle();

        bundle.putString("videoName", videoFileName);

        if (recordingFrameRate > 0)
            bundle.putFloat("recordingFrameRate", recordingFrameRate);

        return bundle;
    }

    /**
     * Set the file name of the taken video.
     *
     * @param storageDir directory where the video file is stored
     * @param fileName path of the taken video
     */
    void setVideoFileName(File storageDir, String fileName) {
        this.videoFileName = fileName;

        String videoFilePath = new File(storageDir, fileName).getPath();
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(videoFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);

            if (mime.startsWith("video/")) {
                extractor.selectTrack(i);

                videoDuration = format.getLong(MediaFormat.KEY_DURATION) / 1000;
                videoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                videoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
                if (format.containsKey(MediaFormat.KEY_FRAME_RATE))
                    videoFrameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                if (videoFrameRate == 0)
                    videoFrameRate = 30;
                break;
            }
        }
    }

    public String getVideoFileName() {
        return videoFileName;
    }

    /**
     * Gets the complete path of the video file.
     *
     * @return the path of the taken video
     */
    public File getVideoFile() {
        return new File(getStorageDir(), getVideoFileName());
    }

    /**
     * The complete duration of the recorded video.
     *
     * @return the duration of the recorded video
     */
    long getVideoDuration() {
        return videoDuration;
    }

    boolean isRecordedAtReducedFrameRate() {
        return recordingFrameRate < LOW_FRAME_RATE;
    }

    float getRecordingFrameRate() {
        return recordingFrameRate;
    }

    void setRecordingFrameRate(float recordingFrameRate) {
        this.recordingFrameRate = recordingFrameRate;
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    int getVideoFrameRate() {
        return videoFrameRate;
    }
}
