package com.android.grafika.mix;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import com.android.grafika.effect.BaseEffect;
import com.android.grafika.effect.GLEffectUtil;
import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.Texture2dProgram;

import java.util.List;

public class OutputSurfaceWithFilter implements SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = "OutputSurface";
    private static final String FILTER_KEY="filter_name";
    private static final boolean VERBOSE = true;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private Object mFrameSyncObject = new Object();     // guards mFrameAvailable
    private boolean mFrameAvailable;

    private Context mContext;
    private int mWidth;
    private int mHeight;

//    private SceneFilter mSceneFilter;
    private boolean mIsAddWaterMark = false;


    public OutputSurfaceWithFilter(Context context, int width, int height) {
        mContext = context;
        mWidth = width;
        mHeight = height;
        setup();
    }

    static int mFacebeautyItem = 0; //美颜道具
    static int mEffectItem = 0; //贴纸道具
    static int mGestureItem = 0; //手势道具
    static int[] itemsArray = {mFacebeautyItem, mEffectItem, mGestureItem};
    private FullFrameRect mFullScreenFUDisplay;
    private FullFrameRect mFullScreenMovie;
    private int mTextureId;
    private final float[] mSTMatrix = new float[16];


    private void setup() {
        mFullScreenFUDisplay = new FullFrameRect(new Texture2dProgram( Texture2dProgram.ProgramType.TEXTURE_2D));
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
            mFullScreenFUDisplay=null;
        }
    }

    public void drawImage(int curPos) {
        mSurfaceTexture.updateTexImage();
        /*
         * 1.0   0.0  0.0  0.0
         * 0.0  -1.0  0.0  0.0
         * 0.0   0.0  1.0  0.0
         * 0.0   1.0  0.0  1.0
         */
        //
        mSurfaceTexture.getTransformMatrix(mSTMatrix);
//        printMatrix(mSTMatrix);
        //----------FaceU process begin--------/
//        boolean isOESTexture = true; //camera默认的是OES的
//        int flags = isOESTexture ? faceunity.FU_ADM_FLAG_EXTERNAL_OES_TEXTURE : 0;
//
//        int fuTex;
//
//        mSceneFilter.setTextureId(mTextureId);
//        mSceneFilter.draw();
//        fuTex = mSceneFilter.getOutputTexture();

//        mFullScreenMovie.drawFrame(fuTex, mSTMatrix);
        //----------FaceU process  End--------/
//        mFullScreenMovie.drawFrame(mTextureId, mSTMatrix);

//        mFullScreenFUDisplay.drawFrame(fuTex, mSTMatrix);
    }


    public Surface getSurface() {
        return mSurface;
    }

    public void awaitNewImage() {
        final int TIMEOUT_MS = 5000;
        synchronized (mFrameSyncObject) {
            while (!mFrameAvailable) {
                try {
                    mFrameSyncObject.wait(TIMEOUT_MS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
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