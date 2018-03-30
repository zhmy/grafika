package com.android.grafika.mix;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;

import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.Texture2dProgram;

public class OutputSurface implements SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = "OutputSurface";
    private static final boolean VERBOSE = true;
    private SurfaceTexture mSurfaceTexture;
    private SurfaceTexture mSurfaceTexture2;
    private Surface mSurface;
    private Surface mSurface2;
    private Object mFrameSyncObject = new Object();     // guards mFrameAvailable
    private Object mFrameSyncObject2 = new Object();     // guards mFrameAvailable
    private boolean mFrameAvailable;
    private boolean mFrameAvailable2;
    private int mVideoWidth, mVideoHeight;

    public OutputSurface(int videoWidth, int videoHeight) {
        mVideoWidth = videoWidth;
        mVideoHeight = videoHeight;
        setup();
    }

    private FullFrameRect mFullScreenFUDisplay;
    private FullFrameRect mFullScreenMovie;
    private FullFrameRect mFullScreenMovie2;
    private int mTextureId, mTextureId2;
    private final float[] mSTMatrix = new float[16];
    private final float[] mSTMatrix2 = new float[16];

    private void setup() {
        mFullScreenFUDisplay = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        Log.d(TAG, "onSurfaceCreated: ");
        mFullScreenMovie = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        mTextureId = mFullScreenMovie.createTextureObject();

        mFullScreenMovie2 = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        mTextureId2 = mFullScreenMovie2.createTextureObject();


        mSurfaceTexture = new SurfaceTexture(mTextureId);
        mSurfaceTexture2 = new SurfaceTexture(mTextureId2);
        mSurface = new Surface(mSurfaceTexture);
        mSurface2 = new Surface(mSurfaceTexture2);
        mSurfaceTexture.setOnFrameAvailableListener(this);
        mSurfaceTexture2.setOnFrameAvailableListener(this);
    }

    public void release() {
        mSurface.release();
        mSurface2.release();
        mSurface = null;
        mSurface2 = null;
        mSurfaceTexture = null;
        mSurfaceTexture2 = null;

        if (mFullScreenFUDisplay != null) {
            mFullScreenFUDisplay.release(false);
            mFullScreenFUDisplay = null;
        }
    }

    float alpha = 0.0f;

    public void drawImage(int curPos) {
        if (curPos >= 3000 && curPos < 6000) {
            mSurfaceTexture.getTransformMatrix(mSTMatrix);
            GLES20.glViewport(0, 0, mVideoWidth / 2, mVideoHeight / 2);
            mFullScreenFUDisplay.drawFrame(mTextureId, mSTMatrix);

            GLES20.glViewport(mVideoWidth / 2, mVideoHeight / 2, mVideoWidth / 2, mVideoHeight / 2);
            mFullScreenFUDisplay.drawFrame(mTextureId, mSTMatrix);
        } else {
            mSurfaceTexture.getTransformMatrix(mSTMatrix);
            mFullScreenFUDisplay.setAlpha(1);
            GLES20.glViewport(0, 0, mVideoWidth, mVideoHeight);
            mFullScreenFUDisplay.drawFrame(mTextureId, mSTMatrix);
        }

        if (curPos > 1000 && curPos < 3000) {
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            alpha += 0.03f;
            if (alpha > 1) {
                alpha = 1;
            }
            mSurfaceTexture2.getTransformMatrix(mSTMatrix2);
            mFullScreenFUDisplay.setAlpha(alpha);
            GLES20.glViewport(0, 0, mVideoWidth, mVideoHeight);
            mFullScreenFUDisplay.drawFrame(mTextureId2, mSTMatrix2);

            GLES20.glDisable(GLES20.GL_BLEND);
        } else if (curPos >= 3000 && curPos < 6000) {
            mSurfaceTexture2.getTransformMatrix(mSTMatrix2);
            GLES20.glViewport(mVideoWidth / 2, 0, mVideoWidth / 2, mVideoHeight / 2);
            mFullScreenFUDisplay.drawFrame(mTextureId2, mSTMatrix2);
            GLES20.glViewport(0, mVideoHeight / 2, mVideoWidth / 2, mVideoHeight / 2);
            mFullScreenFUDisplay.drawFrame(mTextureId2, mSTMatrix2);
        }
    }

    public Surface getSurface() {
        return mSurface;
    }

    public Surface getSurface2() {
        return mSurface2;
    }

    public void awaitNewImage() {
        final int TIMEOUT_MS = 5000;
        synchronized (mFrameSyncObject) {
            while (!mFrameAvailable) {
                try {
                    // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                    // stalling the test if it doesn't arrive.
                    mFrameSyncObject.wait(TIMEOUT_MS);
                } catch (InterruptedException e) {
                }
            }
            mFrameAvailable = false;
        }
        // Latch the data.
        checkGlError("before updateTexImage");
        mSurfaceTexture.updateTexImage();
        mSurfaceTexture2.updateTexImage();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        if (VERBOSE) Log.d(TAG, "new frame available");
        synchronized (mFrameSyncObject) {
            mFrameAvailable = true;
            mFrameSyncObject.notifyAll();
        }
    }

    public void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }

}