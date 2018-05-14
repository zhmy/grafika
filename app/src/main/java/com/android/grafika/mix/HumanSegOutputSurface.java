package com.android.grafika.mix;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import com.android.grafika.HumanSegActivity;
import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.GlUtil;
import com.android.grafika.gles.Texture2dProgram;

import java.util.ArrayList;
import java.util.List;

public class HumanSegOutputSurface implements SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = "OutputSurface";
    private static final boolean VERBOSE = true;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private Object mFrameSyncObject = new Object();     // guards mFrameAvailable
    private boolean mFrameAvailable;
    private int mVideoWidth, mVideoHeight;
    private List<HumanSegActivity.SegFrameItem> mSegFrameItemList;
    private boolean isPositive;


    public HumanSegOutputSurface(int videoWidth, int videoHeight, List<HumanSegActivity.SegFrameItem> segFrameItemList, boolean isPositive) {
        mVideoWidth = videoWidth;
        mVideoHeight = videoHeight;
        mSegFrameItemList = segFrameItemList;
        this.isPositive = isPositive;
        setup();
    }

    private FullFrameRect mFullScreenFUDisplay;
    private FullFrameRect mFullScreenMovie;
    private int mTextureId;
    private final float[] mSTMatrix = new float[16];

    int[] frameBuffers = new int[1];
    int frameBuffer;
    int textureId3;

    private void setup() {
        mFullScreenFUDisplay = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D));
        mFullScreenMovie = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        Log.d(TAG, "onSurfaceCreated: ");
        mTextureId = mFullScreenMovie.createTextureObject();

        textureId3 = mFullScreenFUDisplay.createTexture2DObject();
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mVideoWidth, mVideoHeight,
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glGenFramebuffers(1, frameBuffers, 0);
        frameBuffer = frameBuffers[0];



        for (HumanSegActivity.SegFrameItem segFrameItem : mSegFrameItemList) {
            Bitmap bitmap = BitmapFactory.decodeFile(segFrameItem.imagePath);
            if (bitmap != null) {
                int textureId = mFullScreenFUDisplay.createTexture2DObject();
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
                bitmap.recycle();
                segFrameItem.textureId = textureId;
            }
        }

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
        mSurfaceTexture.getTransformMatrix(mSTMatrix);

        GLES20.glViewport(0, 0, mVideoWidth, mVideoHeight);
//        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);
//        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId3, 0);
        mFullScreenMovie.drawFrame(mTextureId, mSTMatrix);
//        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
//        mFullScreenFUDisplay.setAlpha(1);
//        mFullScreenFUDisplay.drawFrame(textureId3, GlUtil.IDENTITY_MATRIX);

        if (isPositive) {
            for (HumanSegActivity.SegFrameItem segFrameItem : mSegFrameItemList) {
                if (curPos >= segFrameItem.timestamp) {
                    GLES20.glEnable(GLES20.GL_BLEND);
                    GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                    mFullScreenFUDisplay.setAlpha(segFrameItem.alpha);
                    mFullScreenFUDisplay.drawFrame(segFrameItem.textureId, GlUtil.IDENTITY_MATRIX, true);
                    GLES20.glDisable(GLES20.GL_BLEND);
                }
            }
        } else {
            List<HumanSegActivity.SegFrameItem> removeList = new ArrayList<>();
            for (HumanSegActivity.SegFrameItem segFrameItem : mSegFrameItemList) {
                GLES20.glEnable(GLES20.GL_BLEND);
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                mFullScreenFUDisplay.setAlpha(segFrameItem.alpha);
                mFullScreenFUDisplay.drawFrame(segFrameItem.textureId, GlUtil.IDENTITY_MATRIX, true);
                GLES20.glDisable(GLES20.GL_BLEND);

                if (curPos >= segFrameItem.timestamp) {
                    removeList.add(segFrameItem);
                }
            }
            mSegFrameItemList.removeAll(removeList);
        }
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