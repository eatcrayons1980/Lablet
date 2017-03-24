/*
 * Copyright 2013-2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.camera.decoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import org.jetbrains.annotations.Contract;


/**
 * Reads a video file and displays it at given time position on the surface. The surface must be fully initialized.
 */
public class SeekToFrameExtractor {

    private static final boolean IS_LOLLIPOP = VERSION.SDK_INT == VERSION_CODES.LOLLIPOP;

    public interface IListener {
        /**
         * Is called from the extractor thread.
         */
        void onFrameExtracted();
    }

    private SeekToThread seekToThread;
    private final Semaphore threadReadySemaphore = new Semaphore(0);
    private IListener listener = null;

    public SeekToFrameExtractor(File mediaFile, Surface surface) throws IOException {
        seekToThread = new SeekToThread(mediaFile, surface);
        seekToThread.start();
        // wait till thread is up and running
        try {
            threadReadySemaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void setListener(IListener listener) {
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

    static class SeekHandler extends Handler {
        final SeekToThread thread;

        public SeekHandler(SeekToThread thread) {
            this.thread = thread;
        }

        @Override
        public void handleMessage(Message message)
        {
            if (message.what != SeekToThread.SEEK_MESSAGE)
                return;
            Bundle data = message.peekData();
            assert data != null;

            long positionMicroSeconds = data.getLong("position");
            thread.performSeekTo(positionMicroSeconds);
        }
    }

    class SeekToThread extends Thread {
        private static final String TAG = "SeekToThread";
        final static int SEEK_MESSAGE = 1;

        private MediaExtractor extractor;
        private MediaCodec decoder;
        private MediaCodec.BufferInfo bufferInfo;
        ByteBuffer[] inputBuffers;

        Handler seekHandler;

        public SeekToThread(File mediaFile, Surface surface) throws IOException {
            extractor = new MediaExtractor();
            extractor.setDataSource(mediaFile.getPath());

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);

                if (mime.startsWith("video/")) {
                    extractor.selectTrack(i);

                    // newer Android devices have had issues with the codec selection process
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        decoder = configDecoder(format, surface);
                    } else {
                        decoder = MediaCodec.createDecoderByType(mime);
                        decoder.configure(format, surface, null, 0);
                    }

                    break;
                }
            }
            if (decoder == null)
                throw new IOException();

            decoder.start();

            bufferInfo = new MediaCodec.BufferInfo();
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
        @Contract("null, _ -> null; !null, null -> null")
        @RequiresApi(api = VERSION_CODES.LOLLIPOP)
        @Nullable
        private MediaCodec configDecoder(@Nullable MediaFormat format, @Nullable Surface surface) {

            if (format == null || surface == null) {
                return null;
            }

            MediaCodec codec;
            String mime = format.getString(MediaFormat.KEY_MIME);
            MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            MediaCodecInfo[] infos = list.getCodecInfos();

            for (MediaCodecInfo info : infos) {

                CodecCapabilities capabilities;
                boolean formatSupported;

                // does codec support this mime type
                try {
                    capabilities = info.getCapabilitiesForType(mime);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }

                // KEY_FRAME_RATE must be removed for Lollipop API
                Integer saveFrameRate = 0;
                if (IS_LOLLIPOP) {
                    saveFrameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                    format.setString(MediaFormat.KEY_FRAME_RATE, null);
                }

                // does codec support his video format
                try {
                    formatSupported = capabilities.isFormatSupported(format);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }

                // KEY_FRAME_RATE is restored
                if (IS_LOLLIPOP) {
                    format.setInteger(MediaFormat.KEY_FRAME_RATE, saveFrameRate);
                }

                // can we configure it successfully
                if (formatSupported) {
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

            Log.e(TAG, "no decoder successfully configured.");
            return null;
        }

        // thread safe
        public Handler getHandler() {
            return seekHandler;
        }

        // thread safe
        public void quit() {
            seekHandler.getLooper().quit();
            try {
                seekToThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            decoder.stop();
            decoder.release();
            extractor.release();
        }

        public void run() {
            Looper.prepare();
            seekHandler = new SeekHandler(this);
            threadReadySemaphore.release();
            Looper.loop();
        }

        private void performSeekTo(long seekTarget) {
            final int DEQUE_TIMEOUT = 1000;

            inputBuffers = decoder.getInputBuffers();

            // coarse seek
            extractor.seekTo(seekTarget, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

            boolean endOfStream = false;
            // fine manual seek
            boolean positionReached = false;
            while (!positionReached) {
                if (!endOfStream) {
                    int inIndex = decoder.dequeueInputBuffer(DEQUE_TIMEOUT);
                    if (inIndex >= 0) {
                        ByteBuffer buffer = inputBuffers[inIndex];

                        int sampleSize = extractor.readSampleData(buffer, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            endOfStream = true;
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outIndex = decoder.dequeueOutputBuffer(bufferInfo, DEQUE_TIMEOUT);
                switch (outIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        break;
                    default:
                        boolean render = false;
                        if (bufferInfo.presentationTimeUs - seekTarget >= 0
                                || (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            positionReached = true;
                            render = true;
                        }

                        decoder.releaseOutputBuffer(outIndex, render);
                        if (render) {
                            decoder.flush();
                            if (listener != null)
                                listener.onFrameExtracted();
                        }
                        break;
                }
            }
        }
    }
}
