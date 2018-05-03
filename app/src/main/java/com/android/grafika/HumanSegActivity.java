package com.android.grafika;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;
import android.view.Surface;
import android.view.View;

import com.android.grafika.effect.BaseEffect;
import com.android.grafika.effect.EffectType;
import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.Texture2dProgram;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class HumanSegActivity extends Activity {

    GLSurfaceView mGLSurfaceView;
    String video1, video2;
    SurfaceRender mRender;

    class SegFrameItem {
        long timestamp;
        float alpha;
        String imagePath;
        int textureId;

        public SegFrameItem(long timestamp, float alpha, String imagePath) {
            this.timestamp = timestamp;
            this.alpha = alpha;
            this.imagePath = imagePath;
//            BitmapFactory.Options options = new BitmapFactory.Options();
//            options.inJustDecodeBounds = true;
//            BitmapFactory.decodeFile(imagePath, options);
//            int inSampleSize = 1;
//            if (options.outWidth > 300 || options.outHeight > 300) {
//                int widthRatio = Math.round((float) options.outWidth / (float) 300);
//                int heightRatio = Math.round((float) options.outHeight / (float) 300);
//                inSampleSize = Math.min(widthRatio, heightRatio);
//            }
//            options.inSampleSize = inSampleSize;
//            options.inJustDecodeBounds = false;
////            Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
//            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.like);
//            if (bitmap != null) {
//                int textures[] = new int[1];
//                GLES20.glGenTextures(1, textures, 0);
//                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
//                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
//                        GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
//                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
//                        GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
//                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
//                        GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
//                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
//                        GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
//                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
////                bitmap.recycle();
//                textureId = textures[0];
//            }
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

        SegFrameItem segFrameItem1 = new SegFrameItem(1000, 1, "/sdcard/DCIM/nani/like.png");
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

    public void onMix(View view) {
        //分身术合成

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

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            textureId3 = textures[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId3);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height,
                    0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glGenFramebuffers(1, frameBuffers, 0);
            frameBuffer = frameBuffers[0];

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

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId3, 0);
            mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            float[] m = new float[16];
            Matrix.setIdentityM(m, 0);
            GLES20.glViewport(0, 0, mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight());
            mFullScreen.drawFrame(textureId3, m);

            int i = 0;
            if (isPositive) {
                for (SegFrameItem segFrameItem : mSegFrameItemList) {
                    if (mMediaPlayer1.getCurrentPosition() >= segFrameItem.timestamp) {
                        GLES20.glViewport(100 * i, 300 * i, 300, 300);
                        GLES20.glEnable(GLES20.GL_BLEND);
                        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                        float[] m2 = new float[16];
                        Matrix.setIdentityM(m2, 0);
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
