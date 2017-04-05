/*
 * Copyright 2013-2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.camera.decoder;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED;
import static android.media.MediaCodec.INFO_TRY_AGAIN_LATER;
import static android.media.MediaCodec.createDecoderByType;
import static android.media.MediaCodecList.REGULAR_CODECS;
import static android.media.MediaFormat.KEY_FRAME_RATE;
import static android.media.MediaFormat.KEY_MIME;
import static android.os.Build.VERSION_CODES.LOLLIPOP;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;


/**
 * Reads a video file and displays it at given time position on the surface. The surface must be fully initialized.
 */
public class SeekToFrameExtractor {

    private static final String TAG = "SeekToFrameExtractor";
    private static final boolean IS_LOLLIPOP = VERSION.SDK_INT == LOLLIPOP;

    /**
     * Called when a frame has been extracted.
     */
    public interface IListener {
        void onFrameExtracted();
    }

    private SeekToThread seekToThread;
    @NonNull
    private final Semaphore threadReadySemaphore = new Semaphore(0);
    @Nullable
    private IListener listener = null;

    public SeekToFrameExtractor() {
        seekToThread = new SeekToThread();
    }

    public void init(@NonNull File mediaFile, Surface surface) throws IOException {
        seekToThread.attachFile(mediaFile, surface);
        seekToThread.start();
        try {
            threadReadySemaphore.acquire();
        } catch (InterruptedException e) {
            Log.e(TAG, "could not acquire semaphore");
        }
    }

    public boolean init(@NonNull Context context, @NonNull Uri uri, @NonNull Surface surface) {
        try {
            seekToThread.attachContentUri(context, uri, surface);
        } catch (IOException e) {
            Log.e(TAG, "init failed");
            seekToThread.quit();
            release();
            return false;
        }
        seekToThread.start();
        try {
            threadReadySemaphore.acquire();
        } catch (InterruptedException e) {
            Log.e(TAG, "could not acquire semaphore");
            return false;
        }
        return true;
    }

    public void setListener(@Nullable IListener listener) {
        this.listener = listener;
    }

    public void release() {
        seekToThread.quit();
    }

    public boolean seekToFrame(long positionMicroSeconds) {
        Handler seekHandler = seekToThread.getHandler();
        seekHandler.removeMessages(SeekToThread.SEEK_MESSAGE);
        Message message = new Message();
        message.what = SeekToThread.SEEK_MESSAGE;
        Bundle bundle = new Bundle();
        bundle.putLong("position", positionMicroSeconds);
        message.setData(bundle);
        return seekHandler.sendMessage(message);
    }

    private static class SeekHandler extends Handler {
        final SeekToThread thread;

        SeekHandler(SeekToThread thread) {
            this.thread = thread;
        }

        @Override
        public void handleMessage(@NonNull Message message) {
            if (message.what != SeekToThread.SEEK_MESSAGE)
                return;
            Bundle data = message.peekData();
            assert data != null;

            long positionMicroSeconds = data.getLong("position");
            thread.performSeekTo(positionMicroSeconds);
        }
    }

    private class SeekToThread extends Thread {

        private static final String TAG = "SeekToThread";
        private static final int DEQUE_TIMEOUT = 1000;
        static final int SEEK_MESSAGE = 1;

        @NonNull
        private final MediaExtractor extractor = new MediaExtractor();
        @NonNull
        private final BufferInfo bufferInfo = new BufferInfo();

        private MediaCodec decoder = null;

        ByteBuffer[] inputBuffers;
        Handler seekHandler;

        private void attachFile(
            @NonNull File mediaFile,
            @NonNull Surface surface) throws IOException {
            try {
                extractor.setDataSource(mediaFile.getPath());
                startDecoder(surface);
            } catch (IOException e) {
                Log.e(TAG, "could not attach file to surface");
                throw e;
            }
        }

        private void attachContentUri(
            @NonNull Context context,
            @NonNull Uri uri,
            @NonNull Surface surface) throws IOException {
            try {
                extractor.setDataSource(context, uri, null);
                startDecoder(surface);
            } catch (IOException e) {
                Log.e(TAG, "could not attach content uri to surface");
                throw e;
            }
        }

        private void startDecoder(@NonNull Surface surface) throws IOException {
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(KEY_MIME);

                if (mime.startsWith("video/")) {
                    extractor.selectTrack(i);
                    // newer Android devices have had issues with the codec selection process
                    if (VERSION.SDK_INT < LOLLIPOP) {
                        try {
                            decoder = createDecoderByType(mime);
                        } catch (IOException e) {
                            Log.e(TAG, "could not configure decoder");
                            throw e;
                        }
                        decoder.configure(format, surface, null, 0);
                        decoder.start();
                        return;
                    } else {
                        decoder = configDecoder(format, surface);
                        decoder.start();
                        return;
                    }
                }
            }
        }

        /**
         * Configures the decoder with one of the device's codecs.
         * <p>
         *     This method addresses a bug causing the application to crash when using
         *     specific codecs. Specifically, the Samsung Galaxy Tab A(6) was crashing
         *     when the OMX.Exynos.avc.dec codec was used by multiple views at once.
         * </p>
         * <p>
         *     To address the bug, this method searches through all codecs and queries
         *     them for capabilities matching the video format (instead of just using
         *     the system returned codec provided by
         *     {@link MediaCodec#createDecoderByType(String)} or
         *     {@link MediaCodecList#findDecoderForFormat(MediaFormat)}).
         * </p>
         * <p>
         *     To achieve the desired results, this method will attempt to configure
         *     each matching decoder, discarding those that throw errors. Since most
         *     devices will have more than one codec for common formats, this can be
         *     trusted to work the majority of the time.
         * </p>
         * @param format video format the codec must support
         * @param surface the surface to attach to the codec
         * @return the configured decoder, or null if an error occurred
         */
        @RequiresApi(api = LOLLIPOP)
        @NonNull
        private MediaCodec configDecoder(
            @NonNull MediaFormat format,
            @NonNull Surface surface) throws IOException {

            final MediaCodecList list = new MediaCodecList(REGULAR_CODECS);

            for (MediaCodecInfo info : list.getCodecInfos()) {
                CodecCapabilities capabilities;
                boolean formatSupported;
                // does codec support this mime type
                String mime = format.getString(KEY_MIME);
                try {
                    capabilities = info.getCapabilitiesForType(mime);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }

                // KEY_FRAME_RATE must be removed for Lollipop API
                Integer saveFrameRate = 0;
                if (IS_LOLLIPOP) {
                    saveFrameRate = format.getInteger(KEY_FRAME_RATE);
                    format.setString(KEY_FRAME_RATE, null);
                }

                // does codec support his video format
                try {
                    formatSupported = capabilities.isFormatSupported(format);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }

                // KEY_FRAME_RATE is restored
                if (IS_LOLLIPOP) {
                    format.setInteger(KEY_FRAME_RATE, saveFrameRate);
                }

                // can we configure it successfully
                if (formatSupported) {
                    MediaCodec codec;
                    if (info.getName().contains("xynos")) continue;;
                    Log.i(TAG, "trying decoder: " + info.getName());
                    try {
                        codec = MediaCodec.createByCodecName(info.getName());
                    } catch (IOException e) {
                        continue;
                    }
                    try {
                        codec.configure(format, surface, null, 0);
                    } catch (IllegalArgumentException ignored) {
                        Log.w(TAG, "decoder failed: " + info.getName());
                        codec.release();
                        continue;
                    } catch (IllegalStateException ignored) {
                        Log.w(TAG, "decoder failed: " + info.getName());
                        codec.release();
                        continue;
                    }
                    Log.d(TAG, "decoder success: " + info.getName());
                    return codec;
                }
            } // end of for loop

            Log.e(TAG, "decoder configuration failed");
            throw new IOException();
        }

        // thread safe
        public Handler getHandler() {
            return seekHandler;
        }

        // thread safe
        void quit() {
            if (seekHandler != null) {
                seekHandler.getLooper().quit();
            }
            try {
                seekToThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
            extractor.release();
        }

        public void run() {
            Looper.prepare();
            seekHandler = new SeekHandler(this);
            threadReadySemaphore.release();
            Looper.loop();
        }

        private void performSeekTo(long seekTarget) {

            if (decoder != null) {
                // TODO: Update for API 21
                //noinspection deprecation
                inputBuffers = decoder.getInputBuffers();
            }

            // coarse seek
            extractor.seekTo(seekTarget, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

            boolean endOfStreamFlag = false;
            // fine manual seek
            boolean positionReached = false;
            while (!positionReached) {
                if (!endOfStreamFlag) {
                    endOfStreamFlag = advanceExtractor(decoder);
                }

                int outIndex = decoder.dequeueOutputBuffer(bufferInfo, DEQUE_TIMEOUT);
                if (outIndex == INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "buffer output format has changed");
                    continue;
                }

                if (outIndex == INFO_TRY_AGAIN_LATER) {
                    Log.d(TAG, "buffer dequeue must be tried again later");
                    continue;
                }

                // Handle deprecated code on pre-API 21 systems
                if (android.os.Build.VERSION.SDK_INT < LOLLIPOP) {
                    //noinspection deprecation
                    if (outIndex == android.media.MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        continue;
                    }
                }

                boolean render = false;
                if (bufferInfo.presentationTimeUs - seekTarget >= 0
                    || (bufferInfo.flags & BUFFER_FLAG_END_OF_STREAM) != 0) {
                    positionReached = true;
                    render = true;
                }

                decoder.releaseOutputBuffer(outIndex, render);
                if (render) {
                    decoder.flush();
                    if (listener != null) {
                        listener.onFrameExtracted();
                    }
                }
            }
        }

        /**
         * Attempt to read more data from the class {@link MediaExtractor} object.
         * @return true if end-of-stream reached, false otherwise
         * @param codec reference to the class {@link MediaCodec}, which must be nonnull
         */
        private boolean advanceExtractor(@NonNull MediaCodec codec) {
            int inIndex = codec.dequeueInputBuffer(DEQUE_TIMEOUT);
            if (inIndex >= 0) {
                ByteBuffer buffer = inputBuffers[inIndex];

                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0, BUFFER_FLAG_END_OF_STREAM);
                    return true;
                } else {
                    codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                    extractor.advance();
                }
            }
            return false;
        }
    }
}
