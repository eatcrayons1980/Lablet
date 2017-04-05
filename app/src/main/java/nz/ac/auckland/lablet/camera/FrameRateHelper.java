/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.camera;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;


/**
 * Helper class for various common frame rate related methods.
 */
class FrameRateHelper {

    private static final String TAG = "FrameRateHelper";

    static List<Float> getPossibleAnalysisFrameRates(int maxFrameRate) {
        // sanitize the frame rate a bit
        if (maxFrameRate >= 27)
            maxFrameRate = 30;

        List<Float> frameRateList = new ArrayList<>();
        frameRateList.add(1f);
        for (int i = 2; i < maxFrameRate; i++) {
            if (maxFrameRate % i == 0)
                frameRateList.add((float)i);
        }
        frameRateList.add((float)maxFrameRate);
        return frameRateList;
    }

    static List<Float> getPossibleLowAnalysisFrameRates(float maxFrameRate) {
        if (maxFrameRate <= 0 || maxFrameRate > VideoData.LOW_FRAME_RATE)
            throw new RuntimeException("bad frame rate");

        List<Float> frameRateList = new ArrayList<>();
        frameRateList.add(10f);
        frameRateList.add(6f);
        frameRateList.add(5f);
        frameRateList.add(3f);
        frameRateList.add(2.5f);
        frameRateList.add(2f);
        frameRateList.add(1.5f);
        frameRateList.add(1f);
        frameRateList.add(0.5f);
        frameRateList.add(0.4f);
        frameRateList.add(0.3f);
        frameRateList.add(0.2f);
        frameRateList.add(0.1f); // every 10 second
        frameRateList.add(0.06666666f); // every 15 seconds
        frameRateList.add(0.05f); // every 20 seconds
        frameRateList.add(0.016666666f); // every minute
        frameRateList.add(0.00333333f); // every 5 minutes

        try {
            while (maxFrameRate < frameRateList.get(0)) {
                frameRateList.remove(0);
            }
        } catch (IndexOutOfBoundsException ignored) {
            Log.w(TAG, "very low frame rate detected");
        }
        if (frameRateList.size() == 0)
            frameRateList.add(maxFrameRate);

        return frameRateList;
    }

    /**
     * Returns the best possible analysis frame rate given a desired target frame rate.
     *
     * @param maxFrameRate the max possible frame rate
     * @param targetFrameRate the desired frame rate
     * @return a analysis frame rate that is closed to the target frame rate
     */
    static float getBestPossibleAnalysisFrameRate(int maxFrameRate, int targetFrameRate) {
        List<Float> possibleAnalysisFrameRates = getPossibleAnalysisFrameRates(maxFrameRate);

        float bestMatchingFrameRate = maxFrameRate;
        for (Float frameRate : possibleAnalysisFrameRates) {
            if (Math.abs(bestMatchingFrameRate - targetFrameRate) > Math.abs(frameRate - targetFrameRate))
                bestMatchingFrameRate = frameRate;
        }

        return bestMatchingFrameRate;
    }
}
