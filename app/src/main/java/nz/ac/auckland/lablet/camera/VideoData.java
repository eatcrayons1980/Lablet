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
import nz.ac.auckland.lablet.experiment.AbstractSensorData;
import nz.ac.auckland.lablet.experiment.IExperimentSensor;

import java.io.File;
import java.io.IOException;


/**
 * Holds all important data from a camera experiment.
 */
public class VideoData extends AbstractSensorData {
    private String videoFileName;

    // milli seconds
    private long videoDuration;
    private int videoWidth;
    private int videoHeight;
    private int videoFrameRate;

    private float recordingFrameRate;

    static final public String DATA_TYPE = "Video";

    public VideoData(IExperimentSensor sourceSensor) {
        super(sourceSensor);
    }

    @Override
    public String getDataType() {
        return DATA_TYPE;
    }

    public VideoData() {
        super();
    }

    public float getMaxRawX() {
        return 100.f;
    }

    public float getMaxRawY() {
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
        int videoX = (int)(markerPoint.x / this.getMaxRawX() * this.getVideoWidth());
        float ySwappedDir = this.getMaxRawY() - markerPoint.y;
        int height = this.getVideoHeight();
        int videoY = (int)(ySwappedDir / this.getMaxRawY() * this.getVideoHeight());
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

        recordingFrameRate = bundle.getFloat("recordingFrameRate", -1);
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
    public void setVideoFileName(File storageDir, String fileName) {
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
    public long getVideoDuration() {
        return videoDuration;
    }

    final static int LOW_FRAME_RATE = 10;

    public boolean isRecordedAtReducedFrameRate() {
        return 0 < recordingFrameRate && recordingFrameRate < LOW_FRAME_RATE;
    }

    public void setRecordingFrameRate(float recordingFrameRate) {
        this.recordingFrameRate = recordingFrameRate;
    }

    public float getRecordingFrameRate() {
        return recordingFrameRate;
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    public int getVideoFrameRate() {
        return videoFrameRate;
    }
}
