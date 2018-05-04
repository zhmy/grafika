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

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId3 = textures[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId3);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mVideoWidth, mVideoHeight,
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glGenFramebuffers(1, frameBuffers, 0);
        frameBuffer = frameBuffers[0];

        mSurfaceTexture = new SurfaceTexture(mTextureId);
        mSurface = new Surface(mSurfaceTexture);
        mSurfaceTexture.setOnFrameAvailableListener(this);

        for (HumanSegActivity.SegFrameItem segFrameItem : mSegFrameItemList) {
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

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId3, 0);
        mFullScreenMovie.drawFrame(mTextureId, mSTMatrix);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);
        GLES20.glViewport(0, 0, mVideoWidth, mVideoHeight);
        mFullScreenFUDisplay.setAlpha(1);
        mFullScreenFUDisplay.drawFrame(textureId3, m);

        int i = 0;
        if (isPositive) {
            for (HumanSegActivity.SegFrameItem segFrameItem : mSegFrameItemList) {
                if (curPos >= segFrameItem.timestamp) {
                    GLES20.glViewport(100 * i, 300 * i, 300, 300);
                    GLES20.glEnable(GLES20.GL_BLEND);
                    GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                    float[] m2 = new float[16];
                    Matrix.setIdentityM(m2, 0);
                    mFullScreenFUDisplay.setAlpha(segFrameItem.alpha);
                    mFullScreenFUDisplay.drawFrame(segFrameItem.textureId, m2, true);
                    GLES20.glDisable(GLES20.GL_BLEND);
                    GLES20.glViewport(0, 0, mVideoWidth, mVideoHeight);
                }
                i++;
            }
        } else {
            List<HumanSegActivity.SegFrameItem> removeList = new ArrayList<>();
            for (HumanSegActivity.SegFrameItem segFrameItem : mSegFrameItemList) {
                GLES20.glViewport(100 * i, 300 * i, 300, 300);
                GLES20.glEnable(GLES20.GL_BLEND);
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                float[] m2 = new float[16];
                Matrix.setIdentityM(m2, 0);
                mFullScreenFUDisplay.setAlpha(segFrameItem.alpha);
                mFullScreenFUDisplay.drawFrame(segFrameItem.textureId, m2, true);
                GLES20.glDisable(GLES20.GL_BLEND);
                GLES20.glViewport(0, 0, mVideoWidth, mVideoHeight);

                if (curPos >= segFrameItem.timestamp) {
                    removeList.add(segFrameItem);
                }
                i++;
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