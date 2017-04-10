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
import static android.media.MediaFormat.KEY_FRAME_RATE;
import static android.media.MediaFormat.KEY_MIME;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Reads a video file and displays it at given time position on the surface. The surface must be fully initialized.
 */
public class SeekToFrameExtractor {

    private static final String TAG = "SeekToFrameExtractor";
    private static final String THREAD_GROUP_NAME = "frames";
    private static int threadCount = 0;
    private final Semaphore decoderPermit = new Semaphore(0);
    private final Semaphore queueAvailable = new Semaphore(1);
    private final AtomicReference<Long> queuedTime = new AtomicReference<>(null);
    private boolean initialized = false;
    @Nullable
    private MediaExtractor extractor = null;
    @Nullable
    private MediaCodec decoder = null;
    @Nullable
    private IListener listener = null;
    @NonNull
    private ThreadGroup group = new ThreadGroup(THREAD_GROUP_NAME);

    /**
     * Initialize the object with a file and surface.
     * <p>
     * This will configure the decoder and media extractor to render frames to the given
     * surface. At the end of this method, a permit will be released for the decoder semaphore.
     * This will allow other methods to lock the decoder and use it.
     * </p>
     *
     * @param mediaFile the file from which to extract frames
     * @param surface the surface to which frames are rendered
     * @throws RuntimeException if initialization fails
     */
    public void init(
        @NonNull File mediaFile,
        @NonNull Surface surface) throws RuntimeException {

        Log.d(TAG, "initializing");
        if (initialized) {
            Log.e(TAG, "already initialized");
            throw new RuntimeException();
        }

        // setup the media extractor
        extractor = new MediaExtractor();
        try {
            extractor.setDataSource(mediaFile.getPath());
        } catch (IOException e) {
            Log.e(TAG, "could not setup extractor");
            extractor.release();
            extractor = null;
            throw new RuntimeException();
        }

        processSurface(surface);
        decoderPermit.release();
        initialized = true;
        Log.d(TAG, "initialized");
    }

    /**
     * Initialize the object with a uri and surface.
     * <p>
     * This will configure the decoder and media extractor to render frames to the given
     * surface. At the end of this method, a permit will be released for the decoder semaphore.
     * This will allow other methods to lock the decoder and use it.
     * </p>
     *
     * @param context context which can access the uri
     * @param uri the uri from which to extract frames
     * @param surface the surface to which frames are rendered
     * @throws RuntimeException if initialization fails
     */
    public void init(
        @NonNull Context context,
        @NonNull Uri uri,
        @NonNull Surface surface) throws RuntimeException {

        Log.d(TAG, "initializing");
        if (initialized) {
            Log.e(TAG, "already initialized");
            throw new RuntimeException();
        }

        // setup the media extractor
        extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, uri, null);
        } catch (IOException e) {
            Log.e(TAG, "could not setup extractor");
            extractor.release();
            extractor = null;
            throw new RuntimeException();
        }

        processSurface(surface);
        decoderPermit.release();
        initialized = true;
        Log.d(TAG, "initialized");
    }

    /**
     * Called by {@link #init(File, Surface)} and {@link #init(Context, Uri, Surface)}.
     * <p>
     * Common code to both init() methods. Configures the decoder to use the surface.
     * </p>
     *
     * @param surface destination for rendered frames
     */
    private void processSurface(@NonNull Surface surface) {
        if (extractor == null) {
            Log.e(TAG, "null extractor");
            throw new RuntimeException();
        }

        // test the surface
        if (!surface.isValid()) {
            Log.e(TAG, "surface is invalid");
            extractor.release();
            extractor = null;
            throw new RuntimeException();
        }

        // find the media format
        MediaFormat format = null;
        String mime = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            format = extractor.getTrackFormat(i);
            mime = format.getString(KEY_MIME);
            if (mime.startsWith("video/")) {
                extractor.selectTrack(i);
                break;
            }
        }
        if (format == null) {
            Log.e(TAG, "could not get media format");
            extractor.release();
            extractor = null;
            throw new RuntimeException();
        }

        // configure the decoder
        try {
            if (SDK_INT < LOLLIPOP) {
                decoder = createDecoderByType(mime);
                decoder.configure(format, surface, null, 0);
            } else {
                decoder = configDecoder(format, surface);
            }
        } catch (IOException e) {
            Log.d(TAG, "could not configure decoder");
            if (decoder != null) {
                decoder.release();
                decoder = null;
            }
            extractor.release();
            extractor = null;
            throw new RuntimeException();
        }
    }

    /**
     * Configures the decoder with one of the device's codecs.
     * <p>
     * This method addresses a bug causing the application to crash when using
     * specific codecs. Specifically, the Samsung Galaxy Tab A(6) was crashing
     * when the OMX.Exynos.avc.dec codec was used by multiple views at once.
     * </p>
     * <p>
     * To address the bug, this method searches through all codecs and queries
     * them for capabilities matching the video format (instead of just using
     * the system returned codec provided by
     * {@link MediaCodec#createDecoderByType(String)} or
     * {@link MediaCodecList#findDecoderForFormat(MediaFormat)}).
     * </p>
     * <p>
     * To achieve the desired results, this method will attempt to configure
     * each matching decoder, discarding those that throw errors. Since most
     * devices will have more than one codec for common formats, this can be
     * trusted to work the majority of the time.
     * </p>
     *
     * @param format video format the codec must support
     * @return the configured decoder, or null if an error occurred
     */
    @RequiresApi(api = LOLLIPOP)
    @NonNull
    private MediaCodec configDecoder(
        @NonNull MediaFormat format,
        @NonNull Surface surface) throws IOException {

        final MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);

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
            if (SDK_INT == LOLLIPOP) {
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
            if (SDK_INT == LOLLIPOP) {
                format.setInteger(KEY_FRAME_RATE, saveFrameRate);
            }

            // can we configure it successfully
            if (formatSupported) {
                MediaCodec codec;
                // TODO: see if we can remove this
                if (info.getName().contains("xynos")) {
                    continue;
                }
                Log.i(TAG, "trying decoder: " + info.getName());
                try {
                    codec = MediaCodec.createByCodecName(info.getName());
                } catch (IOException e) {
                    Log.d(TAG, "could not create codec with for " + info.getName());
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
                codec.start();
                Log.d(TAG, "decoder success: " + info.getName());
                return codec;
            }
        } // end of for loop

        Log.e(TAG, "decoder configuration failed");
        throw new IOException();
    }

    /**
     * Sets a listener function which is called when a frame is rendered.
     *
     * @param listener the listening callback method
     */
    public void setListener(@Nullable IListener listener) {
        this.listener = listener;
    }

    /**
     * Frees the resources associated with the extractor.
     * <p>
     * This locks the decoder and releases the resources associated with the decoder and the
     * media extractor.
     * </p>
     */
    public void release() {
        if (initialized) {
            group.interrupt();
            try {
                if (!decoderPermit.tryAcquire(50, MILLISECONDS)) {
                    Log.e(TAG, "could not acquire decoder to release it - timed out");
                    return;
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "could not acquire decoder to release it");
                return;
            }
            initialized = false;
            if (decoder != null) {
                decoder.release();
                decoder = null;
            }
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
            Log.d(TAG, "released");
        }
    }

    /**
     * Starts a new thread to render the requested frame.
     *
     * @param positionMicroSeconds time position to render
     * @return true if thread started, false otherwise
     */
    public boolean seekToFrame(long positionMicroSeconds) {
        Log.d(TAG, "seeking to " + positionMicroSeconds);
        if (initialized) {
            String name = "Thread-" + threadCount++;
            SeekToThread seek = new SeekToThread(group, name, positionMicroSeconds);
            seek.start();
            return true;
        }
        Log.e(TAG, "seekToFrame called on uninitialized SeekToFrameExtractor");
        return false;
    }

    /**
     * Called when a frame has been extracted.
     */
    public interface IListener {

        void onFrameExtracted();
    }

    /**
     * Render a frame.
     * <p>
     * This class is responsible for rendering a frame using the decoder, extractor, and surface
     * of the {@link SeekToFrameExtractor} objects in which it is contained.
     * </p>
     * <p>
     * If a second instance of this thread is called with a new time, it will place this time into
     * a special LIFO queue containing only a single value (the last time seen). Threads which
     * queue a time should exit, while threads which lock the decoder and begin rendering frames
     * should continue to render frames until the queue is exhausted.
     * </p>
     */
    private class SeekToThread extends Thread {

        private static final String TAG = "SeekToThread";
        private static final int DEQUE_TIMEOUT = 1000;
        @NonNull
        private final BufferInfo bufferInfo = new BufferInfo();
        ByteBuffer[] inputBuffers;
        private Long position = 0L;

        SeekToThread(ThreadGroup group, String name, Long positionMicroSeconds) {
            super(group, name);
            position = positionMicroSeconds;
        }

        public void run() {
            // this time is definitely the newest time, so add it to the queue
            try {
                queueAvailable.acquire();
                queuedTime.set(position);
                queueAvailable.release();
            } catch (InterruptedException e) {
                Log.e(TAG, "could not queue time due to interruption");
                return;
            }

            // run times
            while (!isInterrupted()) {
                // can we get the decoder
                if (!decoderPermit.tryAcquire()) {
                    Log.e(TAG, "another thread will render this frame");
                    return;
                }

                // this is now the rendering thread -- remove the latest time from the queue
                try {
                    queueAvailable.acquire();
                } catch (InterruptedException e) {
                    Log.e(TAG, "could not get queued time due to interruption");
                    decoderPermit.release();
                    return;
                }

                position = queuedTime.getAndSet(null);
                Log.d(TAG, "rendering position " + position);
                queueAvailable.release();
                // end thread if no time in the queue
                if (position == null) {
                    decoderPermit.release();
                    return;
                }

                // double check that nothing we need is null
                if (extractor == null || decoder == null) {
                    Log.e(TAG, "null extractor or decoder received");
                    decoderPermit.release();
                    return;
                }

                // TODO: Update for API 21
                //noinspection deprecation
                inputBuffers = decoder.getInputBuffers();

                // coarse seek
                extractor.seekTo(position, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

                // fine manual seek
                boolean endOfStreamFlag = false;
                boolean positionReached = false;
                while (!positionReached) {
                    if (!endOfStreamFlag) {
                        endOfStreamFlag = advanceExtractor(decoder);
                    }

                    int outIndex = decoder.dequeueOutputBuffer(bufferInfo, DEQUE_TIMEOUT);
                    if (outIndex == INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.d(TAG, "buffer output format has changed");
                        // put time back in queue... unless something is already there
                        try {
                            queueAvailable.acquire();
                            Long newTime = queuedTime.get();
                            if (newTime == null) {
                                Log.d(TAG, "putting time back into the queue");
                                queuedTime.set(position);
                            } else {
                                Log.d(TAG, "newer time is in queue - aborting current time");
                            }
                            queueAvailable.release();
                        } catch (InterruptedException e) {
                            Log.e(TAG, "could not return time to queue due to interruption");
                        }
                        break;
                    }

                    if (outIndex == INFO_TRY_AGAIN_LATER) {
                        Log.d(TAG, "buffer dequeue must be tried again later");
                        try {
                            queueAvailable.acquire();
                        } catch (InterruptedException e) {
                            Log.e(TAG, "could not return time to queue due to interruption");
                        }
                        Long newTime = queuedTime.get();
                        if (newTime == null) {
                            Log.d(TAG, "putting time back into the queue");
                            queuedTime.set(position);
                        } else {
                            Log.d(TAG, "newer time is in queue - aborting current time");
                        }
                        queueAvailable.release();
                        break;
                    }

                    // Handle deprecated code on pre-API 21 systems
                    if (SDK_INT < LOLLIPOP) {
                        //noinspection deprecation
                        if (outIndex == android.media.MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            Log.d(TAG, "output buffers have changed");
                            try {
                                queueAvailable.acquire();
                                Long newTime = queuedTime.get();
                                if (newTime == null) {
                                    Log.d(TAG, "putting time back into the queue");
                                    queuedTime.set(position);
                                } else {
                                    Log.d(TAG, "newer time is in queue - aborting current time");
                                }
                                queueAvailable.release();
                            } catch (InterruptedException e) {
                                Log.e(TAG, "could not return time to queue due to interruption");
                            }
                            break;
                        }
                    }

                    boolean render = false;
                    if (bufferInfo.presentationTimeUs - position >= 0
                        || (bufferInfo.flags & BUFFER_FLAG_END_OF_STREAM) != 0) {
                        positionReached = true;
                        render = true;
                    }

                    try {
                        decoder.releaseOutputBuffer(outIndex, render);
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "could not release output buffer");
                        decoderPermit.release();
                        return;
                    }
                    if (render) {
                        decoder.flush();
                        if (listener != null) {
                            listener.onFrameExtracted();
                        }
                    }
                }
                decoderPermit.release();
            }
        }

        /**
         * Attempt to read more data from the class {@link MediaExtractor} object.
         * @return true if end-of-stream reached, false otherwise
         * @param codec reference to the class {@link MediaCodec}, which must be nonnull
         */
        private boolean advanceExtractor(@NonNull MediaCodec codec) {
            if (extractor == null || decoder == null) {
                Log.e(TAG, "null value received");
                return true;
            }

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
