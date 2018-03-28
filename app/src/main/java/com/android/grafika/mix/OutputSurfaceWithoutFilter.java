package com.android.grafika.mix;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;

import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.Texture2dProgram;

public class OutputSurfaceWithoutFilter implements SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = "OutputSurface";
    private static final boolean VERBOSE = true;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private Object mFrameSyncObject = new Object();     // guards mFrameAvailable
    private boolean mFrameAvailable;

    public OutputSurfaceWithoutFilter() {
        setup();
    }

    private FullFrameRect mFullScreenFUDisplay;
    private FullFrameRect mFullScreenMovie;
    private int mTextureId;
    private final float[] mSTMatrix = new float[16];

    private void setup() {
        mFullScreenFUDisplay = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        Log.d(TAG, "onSurfaceCreated: ");
        mFullScreenMovie = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        mTextureId = mFullScreenMovie.createTextureObject();
        mSurfaceTexture = new SurfaceTexture(mTextureId);
        mSurface = new Surface(mSurfaceTexture);
        mSurfaceTexture.setOnFrameAvailableListener(this);
    }

    public void release() {
        mSurface.release();
        mSurface = null;
        mSurfaceTexture = null;

        if (mFullScreenFUDisplay != null) {
            mFullScreenFUDisplay.release(false);
            mFullScreenFUDisplay = null;
        }
    }

    public void drawImage(int curPos) {
        mSurfaceTexture.updateTexImage();
        mSurfaceTexture.getTransformMatrix(mSTMatrix);
        mFullScreenFUDisplay.drawFrame(mTextureId, mSTMatrix);
    }


    public Surface getSurface() {
        return mSurface;
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