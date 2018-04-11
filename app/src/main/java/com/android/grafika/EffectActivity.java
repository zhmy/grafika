package com.android.grafika;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import com.android.grafika.effect.BaseEffect;
import com.android.grafika.effect.EffectType;
import com.android.grafika.effect.GLEffectUtil;
import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.Texture2dProgram;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class EffectActivity extends Activity {

    GLSurfaceView mGLSurfaceView;
    String video1, video2;
    SurfaceRender mRender;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_funimate_effect);
        mGLSurfaceView = findViewById(R.id.surfaceView);
        mGLSurfaceView.setEGLContextClientVersion(2);
//        mGLSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
//        mGLSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);

        video1 = "/sdcard/DCIM/nani/zzz.mp4";
        video2 = "/sdcard/DCIM/nani/zmy111.mp4";

        mRender = new SurfaceRender();
        mGLSurfaceView.setRenderer(mRender);
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }


    @Override
    protected void onPause() {
        super.onPause();
        mGLSurfaceView.queueEvent(new Runnable() {
            @Override public void run() {
                // Tell the renderer that it's about to be paused so it can clean up.
                mRender.notifyPausing();
            }
        });
    }

    public void onMix(View view) {
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
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT_2));

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

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
//            if (effect.startTime <= mMediaPlayer1.getCurrentPosition() && effect.endTime > mMediaPlayer1.getCurrentPosition()) {
//                float timePercentage = ((mMediaPlayer1.getCurrentPosition() - effect.startTime) % effect.duration) / (1.0f * (effect.duration));
//                Log.e("zmy", "timePercentage : "+timePercentage);
//
//                int texture2DId = 0;
//                GLEffectUtil.applGLEffect(mTextureId1, GLEffectUtil.DEFAULT_GL_CUBE_BUFFER, GLEffectUtil.DEFAULT_GL_TEXTURE_BUFFER, mSTMatrix1, effect, timePercentage);
//            } else {
//
//            }

            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

            mSurfaceTexture1.updateTexImage();
            mSurfaceTexture1.getTransformMatrix(mSTMatrix1);
            GLES20.glViewport(0, 0, mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight());
            mFullScreen1.setAlpha(1);
            mFullScreen1.drawFrame(mTextureId1, mSTMatrix1);

            mSurfaceTexture2.updateTexImage();
            mSurfaceTexture2.getTransformMatrix(mSTMatrix2);
//            GLES20.glViewport(0, 0, mGLSurfaceView.getWidth()/2, mGLSurfaceView.getHeight()/2);
            mFullScreen1.setAlpha(0);
            mFullScreen1.drawFrame(mTextureId2, mSTMatrix2);

            GLES20.glDisable(GLES20.GL_BLEND);
        }
    }
}
