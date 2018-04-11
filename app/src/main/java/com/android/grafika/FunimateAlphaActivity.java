package com.android.grafika;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Toast;

import com.android.grafika.effect.BaseEffect;
import com.android.grafika.effect.EffectType;
import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.Texture2dProgram;
import com.android.grafika.mix.InputSurface;
import com.android.grafika.mix.OutputSurface;
import com.android.grafika.mix.OutputSurfaceWithFilter;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class FunimateAlphaActivity extends Activity {

    GLSurfaceView mGLSurfaceView;
    String video1, video2;
    SurfaceRender mRender;
    SeekBar mSeekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_funimate_alpha);
        mGLSurfaceView = findViewById(R.id.surfaceView);
        mGLSurfaceView.setEGLContextClientVersion(2);
        mSeekBar = findViewById(R.id.seek);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mRender != null) {
                    mRender.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (mRender != null) {
                    mRender.pause();
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mRender != null) {
                    mRender.start();
                }
            }
        });

        video1 = "/sdcard/DCIM/nani/zzz.mp4";
        video2 = "/sdcard/DCIM/nani/yyy.mp4";

        mRender = new SurfaceRender();
        mGLSurfaceView.setRenderer(mRender);
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }


    @Override
    protected void onPause() {
        super.onPause();
        mGLSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                // Tell the renderer that it's about to be paused so it can clean up.
                mRender.notifyPausing();
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void onMix(View view) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Boolean doInBackground(Void... voids) {
                File outFile = new File("/sdcard/DCIM/zmy/funimate_alpha.mp4");
                if (outFile.exists()) {
                    outFile.delete();
                }
                //音频使用第一个视频的音频。然后根据特效合成一个视频
                if (doMix(outFile)) return false;
                return true;
            }

            @Override
            protected void onPostExecute(Boolean aBoolean) {
                super.onPostExecute(aBoolean);
                Toast.makeText(FunimateAlphaActivity.this,
                        "saved success in /sdcard/DCIM/zmy/funimate_alpha.mp4 ", Toast.LENGTH_LONG).show();
            }
        }.execute();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private boolean doMix(File outFile) {
        MediaExtractor videoExtractor1 = null;
        MediaCodec videoDecoder1 = null;
        MediaCodec videoEncoder1 = null;

        MediaExtractor videoExtractor2 = null;
        MediaCodec videoDecoder2 = null;

        OutputSurface outputSurface = null;
        InputSurface inputSurface = null;

        try {
            MediaMuxer mediaMuxer = new MediaMuxer(outFile.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            MediaCodecInfo videoCodecInfo = selectCodec(OUTPUT_VIDEO_MIME_TYPE);
            if (videoCodecInfo == null) {
                return true;
            }
            //init video 1 encoder and decoder start
            videoExtractor1 = new MediaExtractor();
            videoExtractor1.setDataSource(video1);

            int videoInputTrack = getAndSelectVideoTrackIndex(videoExtractor1);
            MediaFormat inputVideoFormat = videoExtractor1.getTrackFormat(videoInputTrack);


            int videoWidth = inputVideoFormat.getInteger(MediaFormat.KEY_WIDTH);
            int videoHeight = inputVideoFormat.getInteger(MediaFormat.KEY_HEIGHT);
            int fps1 = inputVideoFormat.getInteger(MediaFormat.KEY_FRAME_RATE);


            //init outputVideoFormat
            MediaFormat outputVideoFormat = MediaFormat.createVideoFormat(OUTPUT_VIDEO_MIME_TYPE, videoWidth, videoHeight);
            outputVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            setMediaFormatProperty(inputVideoFormat, outputVideoFormat,
                    MediaFormat.KEY_BIT_RATE, 16 * 1024 * 1024);
            setMediaFormatProperty(inputVideoFormat, outputVideoFormat, MediaFormat.KEY_FRAME_RATE, 20);
            setMediaFormatProperty(inputVideoFormat, outputVideoFormat, MediaFormat.KEY_I_FRAME_INTERVAL, 5);

            AtomicReference<Surface> inputSurfaceReference = new AtomicReference<Surface>();
            videoEncoder1 = createVideoEncoder(videoCodecInfo, outputVideoFormat, inputSurfaceReference);
            inputSurface = new InputSurface(inputSurfaceReference.get());
            inputSurface.makeCurrent();
            //init video 1 encoder and decoder end

            // Create a MediaCodec for the decoder, based on the extractor's format.
            outputSurface = new OutputSurface(videoWidth, videoHeight);
            videoDecoder1 = createVideoDecoder(inputVideoFormat, outputSurface.getSurface());

            //init video 2 encoder and decoder start
            videoExtractor2 = new MediaExtractor();
            videoExtractor2.setDataSource(video2);

            int videoInputTrack2 = getAndSelectVideoTrackIndex(videoExtractor2);
            MediaFormat inputVideoFormat2 = videoExtractor2.getTrackFormat(videoInputTrack2);
            videoDecoder2 = createVideoDecoder(inputVideoFormat2, outputSurface.getSurface2());
            int fps2 = inputVideoFormat2.getInteger(MediaFormat.KEY_FRAME_RATE);
            Log.e("zmy", "fps1 : " + fps1 + " fps2 : " + fps2);

            ByteBuffer[] videoDecoderInputBuffers = null;
            ByteBuffer[] videoDecoderInputBuffers2 = null;
            ByteBuffer[] videoDecoderOutputBuffers = null;
            ByteBuffer[] videoDecoderOutputBuffers2 = null;
            ByteBuffer[] videoEncoderOutputBuffers = null;
            MediaCodec.BufferInfo videoDecoderOutputBufferInfo = null;
            MediaCodec.BufferInfo videoDecoderOutputBufferInfo2 = null;
            MediaCodec.BufferInfo videoEncoderOutputBufferInfo = null;

            videoDecoderInputBuffers = videoDecoder1.getInputBuffers();
            videoDecoderInputBuffers2 = videoDecoder2.getInputBuffers();
            videoDecoderOutputBuffers = videoDecoder1.getOutputBuffers();
            videoDecoderOutputBuffers2 = videoDecoder2.getOutputBuffers();
            videoEncoderOutputBuffers = videoEncoder1.getOutputBuffers();
            videoDecoderOutputBufferInfo = new MediaCodec.BufferInfo();
            videoDecoderOutputBufferInfo2 = new MediaCodec.BufferInfo();
            videoEncoderOutputBufferInfo = new MediaCodec.BufferInfo();

            // We will get these from the decoders when notified of a format change.
            MediaFormat decoderOutputVideoFormat = null;
            // We will get these from the encoders when notified of a format change.
            MediaFormat encoderOutputVideoFormat = null;
            // We will determine these once we have the output format.
            int outputVideoTrack = -1;
            // Whether things are done on the video side.
            boolean videoExtractorDone = false;
            boolean videoExtractorDone2 = false;
            boolean videoDecoderDone = false;
            boolean videoDecoderDone2 = false;
            boolean videoEncoderDone = false;

            boolean addTrack = false;
            int i = 0;
            while (!videoEncoderDone) {
                // Extract video from file and feed to decoder.
                // Do not extract video if we have determined the output format but we are not yet
                // ready to mux the frames.
                while (!videoExtractorDone) {
                    int decoderInputBufferIndex = videoDecoder1.dequeueInputBuffer(10000);
                    if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break;
                    }
                    ByteBuffer decoderInputBuffer = videoDecoderInputBuffers[decoderInputBufferIndex];
                    int size = videoExtractor1.readSampleData(decoderInputBuffer, 0);
                    long presentationTime = videoExtractor1.getSampleTime();
                    if (size >= 0) {
                        videoDecoder1.queueInputBuffer(decoderInputBufferIndex, 0, size,
                                presentationTime, videoExtractor1.getSampleFlags());
                    }
                    videoExtractorDone = !videoExtractor1.advance();
                    if (videoExtractorDone) {
                        videoDecoder1.queueInputBuffer(decoderInputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                    // We extracted a frame, let's try something else next.
                    break;
                }




//                 Poll output frames from the video decoder and feed the encoder.
                while (!videoDecoderDone) {
                    int decoderOutputBufferIndex =
                            videoDecoder1.dequeueOutputBuffer(
                                    videoDecoderOutputBufferInfo, 10000);
                    if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break;
                    }
                    if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        videoDecoderOutputBuffers = videoDecoder1.getOutputBuffers();
                        break;
                    }
                    if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        decoderOutputVideoFormat = videoDecoder1.getOutputFormat();
                        break;
                    }

                    ByteBuffer decoderOutputBuffer =
                            videoDecoderOutputBuffers[decoderOutputBufferIndex];
                    if ((videoDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                            != 0) {
                        videoDecoder1.releaseOutputBuffer(decoderOutputBufferIndex, false);
                        break;
                    }
                    boolean render = videoDecoderOutputBufferInfo.size != 0;
                    videoDecoder1.releaseOutputBuffer(decoderOutputBufferIndex, render);
                    if (render) {
                        outputSurface.awaitNewImage();
                        // Edit the frame and send it to the encoder.
                        outputSurface.drawImage((int) (videoDecoderOutputBufferInfo.presentationTimeUs / 1000));

                        inputSurface.setPresentationTime(
                                videoDecoderOutputBufferInfo.presentationTimeUs * 1000);
                        inputSurface.swapBuffers();
                    }
                    if ((videoDecoderOutputBufferInfo.flags
                            & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        videoDecoderDone = true;
                        videoEncoder1.signalEndOfInputStream();
                    }
                    // We extracted a pending frame, let's try something else next.
                    i++;
                    break;
                }

                if (i % (fps1 / fps2) != 0) {
                    while (!videoExtractorDone2) {
                        int decoderInputBufferIndex = videoDecoder2.dequeueInputBuffer(10000);
                        if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            break;
                        }
                        ByteBuffer decoderInputBuffer = videoDecoderInputBuffers2[decoderInputBufferIndex];
                        int size = videoExtractor2.readSampleData(decoderInputBuffer, 0);
                        long presentationTime = videoExtractor2.getSampleTime();
                        if (size >= 0) {
                            videoDecoder2.queueInputBuffer(decoderInputBufferIndex, 0, size,
                                    presentationTime, videoExtractor2.getSampleFlags());
                        }
                        videoExtractorDone2 = !videoExtractor2.advance();
                        if (videoExtractorDone2) {
                            videoDecoder2.queueInputBuffer(decoderInputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        }
                        // We extracted a frame, let's try something else next.
                        break;
                    }

                    while (!videoDecoderDone2) {
                        int decoderOutputBufferIndex =
                                videoDecoder2.dequeueOutputBuffer(
                                        videoDecoderOutputBufferInfo2, 10000);
                        if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            break;
                        }
                        if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            videoDecoderOutputBuffers2 = videoDecoder2.getOutputBuffers();
                            break;
                        }
                        if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            decoderOutputVideoFormat = videoDecoder2.getOutputFormat();
                            break;
                        }

                        ByteBuffer decoderOutputBuffer =
                                videoDecoderOutputBuffers2[decoderOutputBufferIndex];
                        if ((videoDecoderOutputBufferInfo2.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                                != 0) {
                            videoDecoder2.releaseOutputBuffer(decoderOutputBufferIndex, false);
                            break;
                        }
                        boolean render = videoDecoderOutputBufferInfo2.size != 0;
                        videoDecoder2.releaseOutputBuffer(decoderOutputBufferIndex, render);
                        if ((videoDecoderOutputBufferInfo2.flags
                                & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            videoDecoderDone2 = true;
                        }
                        // We extracted a pending frame, let's try something else next.
                        break;
                    }
                }

                // Poll frames from the video encoder and send them to the muxer.
                while (!videoEncoderDone) {
                    int encoderOutputBufferIndex = videoEncoder1.dequeueOutputBuffer(
                            videoEncoderOutputBufferInfo, 10000);
                    if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break;
                    }
                    if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        videoEncoderOutputBuffers = videoEncoder1.getOutputBuffers();
                        break;
                    }
                    if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (outputVideoTrack >= 0) {
                            return true;
                        }
                        encoderOutputVideoFormat = videoEncoder1.getOutputFormat();
                        break;
                    }
                    ByteBuffer encoderOutputBuffer =
                            videoEncoderOutputBuffers[encoderOutputBufferIndex];
                    if ((videoEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                            != 0) {
                        // Simply ignore codec config buffers.
                        videoEncoder1.releaseOutputBuffer(encoderOutputBufferIndex, false);
                        break;
                    }
                    if (videoEncoderOutputBufferInfo.size != 0) {
                        mediaMuxer.writeSampleData(outputVideoTrack, encoderOutputBuffer,
                                videoEncoderOutputBufferInfo);
                    }
                    if ((videoEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            != 0) {
                        videoEncoderDone = true;
                    }
                    videoEncoder1.releaseOutputBuffer(encoderOutputBufferIndex, false);
                    // We enqueued an encoded frame, let's try something else next.
                    break;
                }
                if (encoderOutputVideoFormat != null && !addTrack) {
                    outputVideoTrack = mediaMuxer.addTrack(encoderOutputVideoFormat);
                    mediaMuxer.start();
                    addTrack = true;
                }
            }
            mediaMuxer.stop();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (videoExtractor1 != null) {
                    videoExtractor1.release();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                if (videoDecoder1 != null) {
                    videoDecoder1.stop();
                    videoDecoder1.release();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                if (outputSurface != null) {
                    outputSurface.release();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                if (videoEncoder1 != null) {
                    videoEncoder1.stop();
                    videoEncoder1.release();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                if (inputSurface != null) {
                    inputSurface.release();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                if (videoExtractor2 != null) {
                    videoExtractor2.release();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                if (videoDecoder2 != null) {
                    videoDecoder2.stop();
                    videoDecoder2.release();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private static final String OUTPUT_VIDEO_MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private int getAndSelectVideoTrackIndex(MediaExtractor extractor) {
        for (int index = 0; index < extractor.getTrackCount(); ++index) {
            if (extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                extractor.selectTrack(index);
                return index;
            }
        }
        return -1;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private static void setMediaFormatProperty(MediaFormat inputFormat, MediaFormat outFormat, final String key, final int defaultVaule) {
        int value = defaultVaule;
        if (inputFormat != null && inputFormat.containsKey(key) && inputFormat.getInteger(key) > 0) {
            value = inputFormat.getInteger(key);
        }
        if (outFormat != null) {
            outFormat.setInteger(key, value);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private MediaCodec createVideoEncoder(MediaCodecInfo codecInfo, MediaFormat format, AtomicReference<Surface> surfaceReference) throws IOException {
        MediaCodec encoder = MediaCodec.createByCodecName(codecInfo.getName());
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // Must be called before start() is.
        surfaceReference.set(encoder.createInputSurface());
        encoder.start();
        return encoder;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private MediaCodec createVideoDecoder(MediaFormat inputFormat, Surface surface) throws IOException {
        MediaCodec decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
        decoder.configure(inputFormat, surface, null, 0);
        decoder.start();
        return decoder;
    }

    class SurfaceRender implements GLSurfaceView.Renderer {
        FullFrameRect mFullScreen1;
        FullFrameRect mFullScreen2;
        int mTextureId1, mTextureId2;
        SurfaceTexture mSurfaceTexture1, mSurfaceTexture2;
        MediaPlayer mMediaPlayer1, mMediaPlayer2;
        Surface mSurface1, mSurface2;
        private final float[] mSTMatrix1 = new float[16];
        private final float[] mSTMatrix2 = new float[16];
        BaseEffect effect;

        public void notifyPausing() {
            if (mMediaPlayer1 != null) {
                mMediaPlayer1.pause();
            }
            if (mMediaPlayer2 != null) {
                mMediaPlayer2.pause();
            }
        }

        public void seekTo(int progress) {
            if (mMediaPlayer1 != null) {
                int seekPosition = (int) ((progress * 1.0f / 100) * mMediaPlayer1.getDuration());
                mMediaPlayer1.seekTo(seekPosition);
            }
            pause();
        }

        public void pause() {
            if (mMediaPlayer1 != null) {
                mMediaPlayer1.pause();
            }
            if (mMediaPlayer2 != null) {
                mMediaPlayer2.pause();
            }
        }

        public void start() {
            if (mMediaPlayer1 != null) {
                mMediaPlayer1.start();
            }
            if (mMediaPlayer2 != null) {
                mMediaPlayer2.start();
            }
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {

            mFullScreen1 = new FullFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));

            mFullScreen2 = new FullFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));

            mTextureId1 = mFullScreen1.createTextureObject();

            mTextureId2 = mFullScreen2.createTextureObject();

            // Create a SurfaceTexture, with an external texture, in this EGL context.  We don't
            // have a Looper in this thread -- GLSurfaceView doesn't create one -- so the frame
            // available messages will arrive on the main thread.
            mSurfaceTexture1 = new SurfaceTexture(mTextureId1);
            mSurfaceTexture1.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    mGLSurfaceView.requestRender();
                }
            });
            mSurfaceTexture2 = new SurfaceTexture(mTextureId2);
            mSurfaceTexture2.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    mGLSurfaceView.requestRender();
                }
            });

            mSurface1 = new Surface(mSurfaceTexture1);
            mSurface2 = new Surface(mSurfaceTexture2);

            mMediaPlayer1 = new MediaPlayer();
            mMediaPlayer1.setSurface(mSurface1);
            mMediaPlayer1.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mMediaPlayer1.start();
                }
            });
            try {
                mMediaPlayer1.setDataSource(video1);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mMediaPlayer1.prepareAsync();

            mMediaPlayer2 = new MediaPlayer();
            mMediaPlayer2.setSurface(mSurface2);
            mMediaPlayer2.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mMediaPlayer2.start();
                }
            });
            try {
                mMediaPlayer2.setDataSource(video2);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mMediaPlayer2.prepareAsync();

            effect = new BaseEffect();
            effect.startPercentage = 0;
            effect.endPercentage = 80;
            effect.duration = 500;
            effect.effectType = EffectType.SOUL_OUT;
            effect.startTime = 2000;
            effect.endTime = 5000;
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }

        float alpha = 0.0f;
        boolean stateAdd;

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            if (mMediaPlayer1.getCurrentPosition() <= 1000) {
                mSurfaceTexture1.updateTexImage();
                mSurfaceTexture1.getTransformMatrix(mSTMatrix1);
                mFullScreen1.setAlpha(1.0f);
                mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);
            } else if (mMediaPlayer1.getCurrentPosition() > 1000 && mMediaPlayer1.getCurrentPosition() < 3000) {
                mSurfaceTexture1.updateTexImage();
                mSurfaceTexture1.getTransformMatrix(mSTMatrix1);
                mFullScreen1.setAlpha(1.0f);
                mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);

                if (stateAdd) {
                    alpha += 0.2f;
                } else {
                    alpha -= 0.2f;
                }
                if (alpha > 1) {
                    stateAdd = false;
                    alpha = 1;
                }
                if (alpha < 0) {
                    stateAdd = true;
                    alpha = 0;
                }

                mSurfaceTexture2.updateTexImage();
                mSurfaceTexture2.getTransformMatrix(mSTMatrix2);
                mFullScreen1.setAlpha(alpha);
                mFullScreen1.drawFrame(mTextureId2, mSTMatrix2);
            } else if (mMediaPlayer1.getCurrentPosition() >= 3000 && mMediaPlayer1.getCurrentPosition() < 4000) {
                mSurfaceTexture1.updateTexImage();
                mSurfaceTexture1.getTransformMatrix(mSTMatrix1);
                mFullScreen1.setAlpha(1.0f);
                mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);

                alpha = 0.0f;
                stateAdd = true;

            } else if (mMediaPlayer1.getCurrentPosition() >= 4000 && mMediaPlayer1.getCurrentPosition() < 6000) {
                mSurfaceTexture1.updateTexImage();
                mSurfaceTexture1.getTransformMatrix(mSTMatrix1);
                mFullScreen1.setAlpha(1.0f);
                mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);

                alpha += 0.03f;
                if (alpha > 1) {
                    alpha = 1;
                }

                mSurfaceTexture2.updateTexImage();
                mSurfaceTexture2.getTransformMatrix(mSTMatrix2);
                mFullScreen1.setAlpha(alpha);
                mFullScreen1.drawFrame(mTextureId2, mSTMatrix2);
            } else if (mMediaPlayer1.getCurrentPosition() >= 6000 && mMediaPlayer1.getCurrentPosition() < 8000) {
                mSurfaceTexture1.updateTexImage();
                mSurfaceTexture1.getTransformMatrix(mSTMatrix1);
                mFullScreen1.setAlpha(1.0f);
                mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);

                mSurfaceTexture2.updateTexImage();
                mSurfaceTexture2.getTransformMatrix(mSTMatrix2);
                mFullScreen1.setAlpha(1.0f);
                mFullScreen1.drawFrame(mTextureId2, mSTMatrix2);

                alpha = 0.0f;
                stateAdd = true;
            } else if (mMediaPlayer1.getCurrentPosition() >= 8000 && mMediaPlayer1.getCurrentPosition() < 10000) {
                mSurfaceTexture2.updateTexImage();
                mSurfaceTexture2.getTransformMatrix(mSTMatrix2);
                mFullScreen1.setAlpha(1.0f);
                mFullScreen1.drawFrame(mTextureId2, mSTMatrix2);

                alpha += 0.03f;
                if (alpha > 1) {
                    alpha = 1;
                }

                mSurfaceTexture1.updateTexImage();
                mSurfaceTexture1.getTransformMatrix(mSTMatrix1);
                mFullScreen1.setAlpha(alpha);
                mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);
            } else if (mMediaPlayer1.getCurrentPosition() >= 10000 && mMediaPlayer1.getCurrentPosition() < 11000) {
                mSurfaceTexture1.updateTexImage();
                mSurfaceTexture1.getTransformMatrix(mSTMatrix1);
                mFullScreen1.setAlpha(1.0f);
                mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);
            } else {
                mSurfaceTexture1.updateTexImage();
                mSurfaceTexture1.getTransformMatrix(mSTMatrix1);
                mFullScreen1.setAlpha(1.0f);
                mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);

                mSurfaceTexture2.updateTexImage();
                mSurfaceTexture2.getTransformMatrix(mSTMatrix2);
                mFullScreen1.setAlpha(1.0f);
                mFullScreen1.drawFrame(mTextureId2, mSTMatrix2);
            }

            GLES20.glDisable(GLES20.GL_BLEND);
        }
    }
}
