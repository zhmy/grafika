package com.android.grafika;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.SeekBar;

import com.android.grafika.effect.BaseEffect;
import com.android.grafika.effect.EffectType;
import com.android.grafika.gles.Drawable2d;
import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.Sprite2d;
import com.android.grafika.gles.Texture2dProgram;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class FunimateSlideActivity extends Activity {

    GLSurfaceView mGLSurfaceView;
    String video1, video2;
    SurfaceRender mRender;
    SeekBar mSeekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_funimate_slide);
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

    public void onMix(View view) {
    }

    class SurfaceRender implements GLSurfaceView.Renderer {
        FullFrameRect mFullScreen1;
        FullFrameRect mFullScreen2;
        FullFrameRect mFullScreen3;
        FullFrameRect mFullScreenBlur;
        FullFrameRect mFullScreen4;
        int mTextureId1, mTextureId2;
        SurfaceTexture mSurfaceTexture1, mSurfaceTexture2;
        MediaPlayer mMediaPlayer1, mMediaPlayer2;
        Surface mSurface1, mSurface2;
        private final float[] mSTMatrix1 = new float[16];
        private final float[] mSTMatrix2 = new float[16];
        BaseEffect effect;
        private int mVideoWidth, mVideoHeight, mVideo2Width, mVideo2Height;
        private float[] mScratchMatrix = new float[16];

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
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT_SLIDE));

            mFullScreen2 = new FullFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));

            mFullScreen3 = new FullFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT_SLIDE2));

            mFullScreen4 = new FullFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D));

            mFullScreenBlur = new FullFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT_BLURX));

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
                    mVideoWidth = mp.getVideoWidth();
                    mVideoHeight = mp.getVideoHeight();
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
                    mVideo2Width = mp.getVideoWidth();
                    mVideo2Height = mp.getVideoHeight();
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

        private int mTexture2DId;
        private int[] mFramebuffers = new int[1];
        private int mFrameBuffer;

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            mTexture2DId = textures[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture2DId);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height,
                    0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glGenFramebuffers(1, mFramebuffers, 0);
            mFrameBuffer = mFramebuffers[0];
        }

        int i = 0;
        boolean stateAdd;
        float endTime;
        float startTime;

        @Override
        public void onDrawFrame(GL10 gl) {
            if (mMediaPlayer1.getCurrentPosition() <= 500) {
                mSurfaceTexture1.updateTexImage();
                mSurfaceTexture1.getTransformMatrix(mSTMatrix1);
                mFullScreen1.setAlpha(1);
                mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);
            } else if (mMediaPlayer1.getCurrentPosition() > 500 && mMediaPlayer1.getCurrentPosition() < 2000) {
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                mSurfaceTexture1.updateTexImage();
                mSurfaceTexture1.getTransformMatrix(mSTMatrix1);
//                GLES20.glViewport(-i, 0, mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight());
//                if (i < mGLSurfaceView.getWidth()) {
//                    mFullScreen1.setAlpha(0.5f);
//                } else {
//                    mFullScreen1.setAlpha(1);
//                }
//                mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);

                mSurfaceTexture2.updateTexImage();
                mSurfaceTexture2.getTransformMatrix(mSTMatrix2);


                Matrix.multiplyMM(mScratchMatrix, 0, mSTMatrix2, 0, getModelViewMatrix(), 0);
//                GLES20.glViewport(mGLSurfaceView.getWidth()-i, 0, mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight());
//
//                mFullScreen1.drawFrame(mTextureId2, mSTMatrix2);
//
//                i = i + 50;
//                if (i > mGLSurfaceView.getWidth()) {
//                    i = mGLSurfaceView.getWidth();
//                }
                if (endTime == 0) {
                    endTime = mMediaPlayer1.getCurrentPosition() + 1000;
                }
                if (startTime == 0) {
                    startTime = mMediaPlayer1.getCurrentPosition();
                }

                float timePercentage = 1 - (mMediaPlayer1.getCurrentPosition() - startTime) / (1.0f * 1000);
//                float translationPercentage = timePercentage > 1 ? (float) Math.pow(timePercentage, 4) : 0;
                float translationPercentage = timePercentage;
                translationPercentage = translationPercentage > 0 ? (float) Math.pow(translationPercentage, 4) : 0;
//                translationPercentage = translationPercentage > 1 ? 1 : translationPercentage;
//                Log.e("zmy", "p : "+translationPercentage);

                mFullScreen3.setDistance(translationPercentage);//1-translationPercentage
                mFullScreen3.drawFrame(mTextureId1, mSTMatrix1, mTextureId2, mScratchMatrix);


            } else if (mMediaPlayer1.getCurrentPosition() >= 2000 && mMediaPlayer1.getCurrentPosition() < 3000) {
                mSurfaceTexture2.updateTexImage();
                mSurfaceTexture2.getTransformMatrix(mSTMatrix2);
                mFullScreen1.setAlpha(1);
                mFullScreen1.drawFrame(mTextureId2, mSTMatrix2);

                i = 0;
                endTime = 0;
                startTime = 0;
            } else if (mMediaPlayer1.getCurrentPosition() >= 3000 && mMediaPlayer1.getCurrentPosition() < 4000) {
//                if (i < mGLSurfaceView.getWidth()) {
//                    mFullScreen1.setAlpha(0.5f);
//                } else {
//                    mFullScreen1.setAlpha(1);
//                }

                mSurfaceTexture1.updateTexImage();
                mSurfaceTexture1.getTransformMatrix(mSTMatrix1);
//                GLES20.glViewport( i - mGLSurfaceView.getWidth(), 0, mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight());
//                mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);

                mSurfaceTexture2.updateTexImage();
                mSurfaceTexture2.getTransformMatrix(mSTMatrix2);
//                GLES20.glViewport(i, 0, mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight());
//                mFullScreen1.drawFrame(mTextureId2, mSTMatrix2);
//
//                i = i + 50;
//                if (i > mGLSurfaceView.getWidth()) {
//                    i = mGLSurfaceView.getWidth();
//                }

                if (endTime == 0) {
                    endTime = mMediaPlayer1.getCurrentPosition() + 1000;
                }
                if (startTime == 0) {
                    startTime = mMediaPlayer1.getCurrentPosition();
                }
                float timePercentage = (mMediaPlayer1.getCurrentPosition() - startTime) / (1.0f * 1000);
                float translationPercentage = timePercentage > 0 ? (float) (Math.cos((timePercentage + 1) * Math.PI) / 2.0f) + 0.5f : 0;
//                translationPercentage = translationPercentage > 1 ? 1 : translationPercentage;
                //                Log.e("zmy", "p : "+translationPercentage);
                mFullScreen3.setDistance(translationPercentage);//1-translationPercentage
                mFullScreen3.drawFrame(mTextureId1, mSTMatrix1, mTextureId2, mSTMatrix2);

            } else if (mMediaPlayer1.getCurrentPosition() >= 4000 && mMediaPlayer1.getCurrentPosition() < 5000) {
                mSurfaceTexture1.updateTexImage();
                mSurfaceTexture1.getTransformMatrix(mSTMatrix1);
                GLES20.glViewport(0, 0, mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight());
                mFullScreen1.setAlpha(1);
                mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);

                i = 0;
                stateAdd = true;
                endTime = 0;
                startTime = 0;
            } else if (mMediaPlayer1.getCurrentPosition() >= 5000 && mMediaPlayer1.getCurrentPosition() < 10000) {
                mSurfaceTexture1.updateTexImage();
                mSurfaceTexture1.getTransformMatrix(mSTMatrix1);

                mSurfaceTexture2.updateTexImage();
                mSurfaceTexture2.getTransformMatrix(mSTMatrix2);

                if (endTime == 0) {
                    endTime = mMediaPlayer1.getCurrentPosition() + 5000;
                }
                if (startTime == 0) {
                    startTime = mMediaPlayer1.getCurrentPosition();
                }

                float timePercentage = ((mMediaPlayer1.getCurrentPosition() - startTime) % 150) / (1.0f * 150);
                int x = (int) ((mMediaPlayer1.getCurrentPosition() - startTime) / 150 % 2);
                float translationPercentage = Math.abs(x - timePercentage);

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer);
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mTexture2DId, 0);
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

                mFullScreen3.setDistance(translationPercentage);//1-translationPercentage
                mFullScreen3.drawFrame(mTextureId1, mSTMatrix1, mTextureId2, mSTMatrix2);

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

                Matrix.setIdentityM(mSTMatrix1, 0);
                mFullScreenBlur.setDistance(translationPercentage);
                mFullScreenBlur.drawFrame(mTexture2DId, mSTMatrix1);


//                GLES20.glViewport(-i, 0, mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight());
//                mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);
//
//                GLES20.glViewport(mGLSurfaceView.getWidth()-i, 0, mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight());
//                mFullScreen1.drawFrame(mTextureId2, mSTMatrix2);
//
//                if (stateAdd) {
//                    i += 120;
//                    if (i < mGLSurfaceView.getWidth()) {
//                        mFullScreen1.setAlpha(0.5f);
//                    } else {
//                        mFullScreen1.setAlpha(1);
//                    }
//                } else {
//                    i -= 120;
//
//                    if (i < mGLSurfaceView.getWidth()) {
//                        mFullScreen1.setAlpha(0.5f);
//                    } else {
//                        mFullScreen1.setAlpha(1);
//                    }
//                }
//                if (i > mGLSurfaceView.getWidth()) {
//                    stateAdd = false;
//                    i = mGLSurfaceView.getWidth();
//                }
//                if (i < 0) {
//                    stateAdd = true;
//                    i = 0;
//                }
            } else {
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

                mFullScreen1.setAlpha(1);
                mSurfaceTexture1.updateTexImage();
                mSurfaceTexture1.getTransformMatrix(mSTMatrix1);

                int destHeight = (int) (mGLSurfaceView.getHeight() * 1.0f / mGLSurfaceView.getWidth() * mGLSurfaceView.getWidth() / 2);
                GLES20.glViewport(0, (mGLSurfaceView.getHeight() - destHeight) / 2, mGLSurfaceView.getWidth() / 2, destHeight);
                mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);

                mSurfaceTexture2.updateTexImage();
                mSurfaceTexture2.getTransformMatrix(mSTMatrix2);

                GLES20.glViewport(mGLSurfaceView.getWidth() / 2, (mGLSurfaceView.getHeight() - destHeight) / 2, mGLSurfaceView.getWidth() / 2, destHeight);
                mFullScreen1.drawFrame(mTextureId2, mSTMatrix2);
            }
        }

        private float[] mModelViewMatrix = new float[16];

        public float[] getModelViewMatrix() {
            recomputeMatrix();
            return mModelViewMatrix;
        }

        private void recomputeMatrix() {
            float[] modelView = mModelViewMatrix;
            int width = mGLSurfaceView.getWidth();
            int height = mGLSurfaceView.getHeight();
            int smallDim = Math.min(width, height);
            // Max scale is a bit larger than the screen, so we can show over-size.
            float scaled = smallDim;
            float cameraAspect = (float) mVideoWidth / mVideoHeight;
            float newWidth = Math.round(scaled * cameraAspect);
            float newHeight = Math.round(scaled);
//            float newWidth = mVideoWidth * 1.0f / mVideo2Width;
//            float newHeight = mVideoHeight * 1.0f / mVideo2Height;
            Log.e("zmy", "newWidth : " + newWidth + " newHeight : " + newHeight);
            Matrix.setIdentityM(modelView, 0);
//            Matrix.translateM(modelView, 0, mGLSurfaceView.getWidth()/2, mGLSurfaceView.getHeight()/2, 0.0f);
//            Matrix.scaleM(modelView, 0, newWidth, newHeight, 1.0f);
            float sh = (mVideo2Height * mVideoWidth) * 1f / (mVideo2Width * mVideoHeight);
            float sw = (mVideo2Width * mVideoHeight) * 1f / (mVideo2Height * mVideoWidth);
            Log.e("zmy", "sh : " + sh+" sw : "+sw);
//            Matrix.scaleM(modelView, 0, 1.0f, sh, 1.0f);
        }
    }
}
