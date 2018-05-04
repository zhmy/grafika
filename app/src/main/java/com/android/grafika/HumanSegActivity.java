package com.android.grafika;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import com.android.grafika.effect.BaseEffect;
import com.android.grafika.effect.EffectType;
import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.Texture2dProgram;
import com.android.grafika.mix.HumanSegOutputSurface;
import com.android.grafika.mix.InputSurface;
import com.android.grafika.mix.OutputSurface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class HumanSegActivity extends Activity {

    GLSurfaceView mGLSurfaceView;
    String video1, video2;
    SurfaceRender mRender;

    public class SegFrameItem {
        public long timestamp;
        public float alpha;
        public String imagePath;
        public int textureId;

        public SegFrameItem(long timestamp, float alpha, String imagePath) {
            this.timestamp = timestamp;
            this.alpha = alpha;
            this.imagePath = imagePath;
        }
    }

    List<SegFrameItem> mSegFrameItemList = new ArrayList<>();
    boolean isPositive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_funimate_effect);
        mGLSurfaceView = findViewById(R.id.surfaceView);
        mGLSurfaceView.setEGLContextClientVersion(2);

        SegFrameItem segFrameItem1 = new SegFrameItem(1000, 0.5f, "/sdcard/DCIM/nani/like.png");
        SegFrameItem segFrameItem2 = new SegFrameItem(2000, 1, "/sdcard/DCIM/nani/nani_water_mark.png");
        mSegFrameItemList.add(segFrameItem1);
        mSegFrameItemList.add(segFrameItem2);

        video1 = "/sdcard/DCIM/nani/zzz.mp4";
        video2 = "/sdcard/DCIM/nani/b50.mp4";

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
        //分身术合成
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Boolean doInBackground(Void... voids) {
                File outFile = new File("/sdcard/DCIM/zmy/humanSeg.mp4");
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
                Toast.makeText(HumanSegActivity.this,
                        "saved success in /sdcard/DCIM/zmy/humanSeg.mp4 ", Toast.LENGTH_LONG).show();
            }
        }.execute();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private boolean doMix(File outFile) {
        MediaExtractor videoExtractor1 = null;
        MediaCodec videoDecoder1 = null;
        MediaCodec videoEncoder1 = null;

        HumanSegOutputSurface outputSurface = null;
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
            outputSurface = new HumanSegOutputSurface(videoWidth, videoHeight, mSegFrameItemList, isPositive);
            videoDecoder1 = createVideoDecoder(inputVideoFormat, outputSurface.getSurface());


            ByteBuffer[] videoDecoderInputBuffers = null;
            ByteBuffer[] videoDecoderOutputBuffers = null;
            ByteBuffer[] videoEncoderOutputBuffers = null;
            MediaCodec.BufferInfo videoDecoderOutputBufferInfo = null;
            MediaCodec.BufferInfo videoEncoderOutputBufferInfo = null;

            videoDecoderInputBuffers = videoDecoder1.getInputBuffers();
            videoDecoderOutputBuffers = videoDecoder1.getOutputBuffers();
            videoEncoderOutputBuffers = videoEncoder1.getOutputBuffers();
            videoDecoderOutputBufferInfo = new MediaCodec.BufferInfo();
            videoEncoderOutputBufferInfo = new MediaCodec.BufferInfo();

            // We will get these from the decoders when notified of a format change.
            MediaFormat decoderOutputVideoFormat = null;
            // We will get these from the encoders when notified of a format change.
            MediaFormat encoderOutputVideoFormat = null;
            // We will determine these once we have the output format.
            int outputVideoTrack = -1;
            // Whether things are done on the video side.
            boolean videoExtractorDone = false;
            boolean videoDecoderDone = false;
            boolean videoEncoderDone = false;

            boolean addTrack = false;
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
                    break;
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
        FullFrameRect mFullScreen;
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

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {

            mFullScreen = new FullFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D));

            mFullScreen1 = new FullFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));

            mFullScreen2 = new FullFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D));

            mTextureId1 = mFullScreen1.createTextureObject();

            mTextureId2 = mFullScreen1.createTextureObject();

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
//                    mGLSurfaceView.requestRender();
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
                    mMediaPlayer2.setLooping(true);
                    mMediaPlayer2.start();
                    mMediaPlayer2.pause();
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
            effect.endPercentage = 40;
            effect.duration = 500;
            effect.effectType = EffectType.SOUL_OUT;
            effect.startTime = 2000;
            effect.endTime = 5000;
        }

        int[] frameBuffers = new int[1];
        int frameBuffer;
        int textureId3;

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);

//            int[] textures = new int[1];
//            GLES20.glGenTextures(1, textures, 0);
//            textureId3 = textures[0];
//            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId3);
//            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
//            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
//            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
//            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
//            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height,
//                    0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
//            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
//            GLES20.glGenFramebuffers(1, frameBuffers, 0);
//            frameBuffer = frameBuffers[0];

            for (SegFrameItem segFrameItem : mSegFrameItemList) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(segFrameItem.imagePath, options);
                int inSampleSize = 1;
                if (options.outWidth > 300 || options.outHeight > 300) {
                    int widthRatio = Math.round((float) options.outWidth / (float) 300);
                    int heightRatio = Math.round((float) options.outHeight / (float) 300);
                    inSampleSize = Math.min(widthRatio, heightRatio);
                }
                options.inSampleSize = inSampleSize;
                options.inJustDecodeBounds = false;
                Bitmap bitmap = BitmapFactory.decodeFile(segFrameItem.imagePath, options);
                if (bitmap != null) {
                    int textures2[] = new int[1];
                    GLES20.glGenTextures(1, textures2, 0);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures2[0]);
                    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                            GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                            GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                            GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                            GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
                    bitmap.recycle();
                    segFrameItem.textureId = textures2[0];
                }
            }
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            mSurfaceTexture1.updateTexImage();
            mSurfaceTexture1.getTransformMatrix(mSTMatrix1);

//            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);
//            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId3, 0);
            mFullScreen1.setAlpha(1);
            mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);
//            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
//            float[] m = new float[16];
//            Matrix.setIdentityM(m, 0);
//            GLES20.glViewport(0, 0, mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight());
//            mFullScreen.setAlpha(1);
//            mFullScreen.drawFrame(textureId3, m);

            int i = 0;
            if (isPositive) {
                for (SegFrameItem segFrameItem : mSegFrameItemList) {
                    if (mMediaPlayer1.getCurrentPosition() >= segFrameItem.timestamp) {
                        GLES20.glViewport(100 * i, 300 * i, 300, 300);
                        GLES20.glEnable(GLES20.GL_BLEND);
                        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                        float[] m2 = new float[16];
                        Matrix.setIdentityM(m2, 0);
                        mFullScreen.setAlpha(segFrameItem.alpha);
                        mFullScreen.drawFrame(segFrameItem.textureId, m2, true);
                        GLES20.glDisable(GLES20.GL_BLEND);
                        GLES20.glViewport(0, 0, mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight());
                    }
                    i++;
                }
            } else {
                List<SegFrameItem> removeList = new ArrayList<>();
                for (SegFrameItem segFrameItem : mSegFrameItemList) {
                    GLES20.glViewport(100 * i, 300 * i, 300, 300);
                    GLES20.glEnable(GLES20.GL_BLEND);
                    GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                    float[] m2 = new float[16];
                    Matrix.setIdentityM(m2, 0);
                    mFullScreen.setAlpha(segFrameItem.alpha);
                    mFullScreen.drawFrame(segFrameItem.textureId, m2, true);
                    GLES20.glDisable(GLES20.GL_BLEND);
                    GLES20.glViewport(0, 0, mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight());

                    if (mMediaPlayer1.getCurrentPosition() >= segFrameItem.timestamp) {
                        removeList.add(segFrameItem);
                    }
                    i++;
                }
                mSegFrameItemList.removeAll(removeList);
            }
        }
    }
}
