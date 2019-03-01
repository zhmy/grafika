/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.grafika;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Trace;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import com.android.grafika.gles.EglCore;
import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.Texture2dProgram;
import com.android.grafika.gles.WindowSurface;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * Exercises some less-commonly-used aspects of SurfaceView.  In particular:
 * <ul>
 * <li> We have three overlapping SurfaceViews.
 * <li> One is at the default depth, one is at "media overlay" depth, and one is on top of the UI.
 * <li> One is marked "secure".
 * </ul>
 * <p>
 * To watch this in systrace, use
 * <code>systrace.py --app=com.android.grafika gfx view sched dalvik</code>
 * (most interesting while bouncing).
 */
public class MultiSurfaceActivity extends Activity implements SurfaceHolder.Callback {
    private static final String TAG = MainActivity.TAG;

    // Number of steps in each direction.  There's actually N+1 positions because we
    // don't re-draw the same position after a rebound.
    private static final int BOUNCE_STEPS = 30;

    private SurfaceView mSurfaceView1;
    private SurfaceView mSurfaceView2;
    private SurfaceView mSurfaceView3;
    private volatile boolean mBouncing;
    private Thread mBounceThread;
    private CircularEncoder mCircEncoder;
    private MainHandler mHandler;
    private WindowSurface mEncoderSurface;
    private EglCore mEglCore;
    private boolean mFileSaveInProgress;

    FullFrameRect mFullFrameBlit;
    private int mTextureId;
    SurfaceTexture mSurfaceTexture;
    Surface mSurface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_surface_test);

        mHandler = new MainHandler(this);

        // #1 is at the bottom; mark it as secure just for fun.  By default, this will use
        // the RGB565 color format.
        mSurfaceView1 = (SurfaceView) findViewById(R.id.multiSurfaceView1);
        mSurfaceView1.getHolder().addCallback(this);
        mSurfaceView1.setSecure(true);

        // #2 is above it, in the "media overlay"; must be translucent or we will totally
        // obscure #1 and it will be ignored by the compositor.  The addition of the alpha
        // plane should switch us to RGBA8888.
        mSurfaceView2 = (SurfaceView) findViewById(R.id.multiSurfaceView2);
        mSurfaceView2.getHolder().addCallback(this);
        mSurfaceView2.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        mSurfaceView2.setZOrderMediaOverlay(true);

        // #3 is above everything, including the UI.  Also translucent.
        mSurfaceView3 = (SurfaceView) findViewById(R.id.multiSurfaceView3);
        mSurfaceView3.getHolder().addCallback(this);
        mSurfaceView3.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        mSurfaceView3.setZOrderOnTop(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBounceThread != null) {
            stopBouncing();
        }
        if (mCircEncoder != null) {
            mCircEncoder.shutdown();
            mCircEncoder = null;
        }

        if (mFullFrameBlit != null) {
            mFullFrameBlit.release(false);
            mFullFrameBlit = null;
        }

        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }

        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }

        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
    }

    /**
     * onClick handler for "bounce" button.
     */
    public void clickBounce(@SuppressWarnings("unused") View unused) {
        Log.d(TAG, "clickBounce bouncing=" + mBouncing);
        if (mBounceThread != null) {
            File file = new File("/sdcard/DCIM/zmy", "continuous-capture.mp4");
            if (file.exists()) {
                file.delete();
            }
            if (mFileSaveInProgress) {
                Log.w(TAG, "HEY: file save is already in progress");
                return;
            }

            // The button is disabled in onCreate(), and not enabled until the encoder and output
            // surface is ready, so it shouldn't be possible to get here with a null mCircEncoder.
            mFileSaveInProgress = true;
            mCircEncoder.saveVideo(file);

            stopBouncing();
        } else {
            mFileSaveInProgress = false;

            startBouncing();
        }
    }

    private void startBouncing() {
        final Surface surface = mSurfaceView2.getHolder().getSurface();
        if (surface == null || !surface.isValid()) {
            Log.w(TAG, "mSurfaceView2 is not ready");
            return;
        }
        mBounceThread = new Thread() {
            @Override
            public void run() {
                while (true) {
                    long startWhen = System.nanoTime();
                    for (int i = 0; i < BOUNCE_STEPS; i++) {
                        if (!mBouncing) return;
                        drawBouncingCircle(surface, i);
                        drawBouncingCircle(mSurface, i);
                        drawFrame();
                    }
                    for (int i = BOUNCE_STEPS; i > 0; i--) {
                        if (!mBouncing) return;
                        drawBouncingCircle(surface, i);
                        drawBouncingCircle(mSurface, i);
                        drawFrame();
                    }
                    long duration = System.nanoTime() - startWhen;
                    double framesPerSec = 1000000000.0 / (duration / (BOUNCE_STEPS * 2.0));
                    Log.d(TAG, "Bouncing at " + framesPerSec + " fps");
                }
            }
        };
        mBouncing = true;
        mBounceThread.setName("Bouncer");
        mBounceThread.start();
    }

    /**
     * Signals the bounce-thread to stop, and waits for it to do so.
     */
    private void stopBouncing() {
        Log.d(TAG, "Stopping bounce thread");
        mBouncing = false;      // tell thread to stop
        try {
            mBounceThread.join();
        } catch (InterruptedException ignored) {
        }
        mBounceThread = null;
    }

    /**
     * Returns an ordinal value for the SurfaceHolder, or -1 for an invalid surface.
     */
    private int getSurfaceId(SurfaceHolder holder) {
        if (holder.equals(mSurfaceView1.getHolder())) {
            return 1;
        } else if (holder.equals(mSurfaceView2.getHolder())) {
            return 2;
        } else if (holder.equals(mSurfaceView3.getHolder())) {
            return 3;
        } else {
            return -1;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        int id = getSurfaceId(holder);
        if (id < 0) {
            Log.w(TAG, "surfaceCreated UNKNOWN holder=" + holder);
        } else {
            Log.d(TAG, "surfaceCreated #" + id + " holder=" + holder);

        }

        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        try {
            mCircEncoder = new CircularEncoder(VIDEO_WIDTH, VIDEO_HEIGHT, 6000000,
                    30, 7, mHandler);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        mEncoderSurface = new WindowSurface(mEglCore, mCircEncoder.getInputSurface(), true);
        mEncoderSurface.makeCurrent();

        mFullFrameBlit = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        mTextureId = mFullFrameBlit.createTextureObject();
        mSurfaceTexture = new SurfaceTexture(mTextureId);
        mSurfaceTexture.setDefaultBufferSize(VIDEO_WIDTH, VIDEO_HEIGHT);
        mSurface = new Surface(mSurfaceTexture);
    }

    /**
     * SurfaceHolder.Callback method
     * <p>
     * Draws when the surface changes.  Since nothing else is touching the surface, and
     * we're not animating, we just draw here and ignore it.
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height +
                " holder=" + holder);

        int id = getSurfaceId(holder);
        boolean portrait = height > width;
        Surface surface = holder.getSurface();

        switch (id) {
            case 1:
                // default layer: circle on left / top
                if (portrait) {
                    drawCircleSurface(surface, width / 2, height / 4, width / 4);
                } else {
                    drawCircleSurface(surface, width / 4, height / 2, height / 4);
                }
                break;
            case 2:
                // media overlay layer: circle on right / bottom
                if (portrait) {
                    drawCircleSurface(surface, width / 2, height * 3 / 4, width / 4);
                } else {
                    drawCircleSurface(surface, width * 3 / 4, height / 2, height / 4);
                }
                break;
            case 3:
                // top layer: alpha stripes
                if (portrait) {
                    int halfLine = width / 16 + 1;
                    drawRectSurface(surface, width / 2 - halfLine, 0, halfLine * 2, height);
                } else {
                    int halfLine = height / 16 + 1;
                    drawRectSurface(surface, 0, height / 2 - halfLine, width, halfLine * 2);
                }
                break;
            default:
                throw new RuntimeException("wha?");
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // ignore
        Log.d(TAG, "Surface destroyed holder=" + holder);
    }

    private static final int VIDEO_WIDTH = 720;  // dimensions for 720p video
    private static final int VIDEO_HEIGHT = 1280;
    private static final int DESIRED_PREVIEW_FPS = 15;

    /**
     * Clears the surface, then draws some alpha-blended rectangles with GL.
     * <p>
     * Creates a temporary EGL context just for the duration of the call.
     */
    private void drawRectSurface(Surface surface, int left, int top, int width, int height) {
        EglCore eglCore = new EglCore();
        WindowSurface win = new WindowSurface(eglCore, surface, false);
        win.makeCurrent();
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        for (int i = 0; i < 4; i++) {
            int x, y, w, h;
            if (width < height) {
                // vertical
                w = width / 4;
                h = height;
                x = left + w * i;
                y = top;
            } else {
                // horizontal
                w = width;
                h = height / 4;
                x = left;
                y = top + h * i;
            }
            GLES20.glScissor(x, y, w, h);
            switch (i) {
                case 0:     // 50% blue at 25% alpha, pre-multiplied
                    GLES20.glClearColor(0.0f, 0.0f, 0.125f, 0.25f);
                    break;
                case 1:     // 100% blue at 25% alpha, pre-multiplied
                    GLES20.glClearColor(0.0f, 0.0f, 0.25f, 0.25f);
                    break;
                case 2:     // 200% blue at 25% alpha, pre-multiplied (should get clipped)
                    GLES20.glClearColor(0.0f, 0.0f, 0.5f, 0.25f);
                    break;
                case 3:     // 100% white at 25% alpha, pre-multiplied
                    GLES20.glClearColor(0.25f, 0.25f, 0.25f, 0.25f);
                    break;
            }
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        win.swapBuffers();
        win.release();
        eglCore.release();
    }

    int mFrameNum;

    private void drawFrame() {
        if (!mFileSaveInProgress) {
            mEncoderSurface.makeCurrent();

            mSurfaceTexture.updateTexImage();
            float[] mtx = new float[16];
            mSurfaceTexture.getTransformMatrix(mtx);
            GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glViewport(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);

            mFullFrameBlit.drawFrame(mTextureId, mtx);

            drawExtra(mFrameNum, VIDEO_WIDTH, VIDEO_HEIGHT);

            mCircEncoder.frameAvailableSoon();
            mEncoderSurface.setPresentationTime(System.nanoTime());
            mEncoderSurface.swapBuffers();
            mFrameNum++;
//            Log.e("zmy", "mFrameNum : " + mFrameNum);
        }
    }

    private static void drawExtra(int frameNum, int width, int height) {
        // We "draw" with the scissor rect and clear calls.  Note this uses window coordinates.
        int val = frameNum % 3;
        switch (val) {
            case 0:
                GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
                break;
            case 1:
                GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
                break;
            case 2:
                GLES20.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
                break;
        }

        int xpos = (int) (width * ((frameNum % 100) / 100.0f));
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(xpos, 0, width / 32, height / 32);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    private void fileSaveComplete(int status) {
        mFileSaveInProgress = false;
    }

    /**
     * Clears the surface, then draws a filled circle with a shadow.
     * <p>
     * The Canvas drawing we're doing may not be fully implemented for hardware-accelerated
     * renderers (shadow layers only supported for text).  However, Surface#lockCanvas()
     * currently only returns an unaccelerated Canvas, so it all comes out looking fine.
     */
    private void drawCircleSurface(Surface surface, int x, int y, int radius) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setShadowLayer(radius / 4 + 1, 0, 0, Color.RED);

        Canvas canvas = surface.lockCanvas(null);
        try {
//            Log.e("zmy", "---w : " + canvas.getWidth() + " h : " + canvas.getHeight());
            Log.v(TAG, "drawCircleSurface: isHwAcc=" + canvas.isHardwareAccelerated());
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            canvas.drawCircle(x, y, radius, paint);
        } finally {
            surface.unlockCanvasAndPost(canvas);
        }
    }

    /**
     * Clears the surface, then draws a filled circle with a shadow.
     * <p>
     * Similar to drawCircleSurface(), but the position changes based on the value of "i".
     */
    private void drawBouncingCircle(Surface surface, int i) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextSize(34);
        paint.setStyle(Paint.Style.FILL);

        Canvas canvas = surface.lockCanvas(null);
        try {
            Trace.beginSection("drawBouncingCircle");
            Trace.beginSection("drawColor");
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            Trace.endSection(); // drawColor

            int width = canvas.getWidth();
            int height = canvas.getHeight();
//            Log.e("zmy", "w : " + width + " h : " + height);
            int radius, x, y;
            if (width < height) {
                // portrait
                radius = width / 4;
                x = width / 4 + ((width / 2 * i) / BOUNCE_STEPS);
                y = height * 3 / 4;
            } else {
                // landscape
                radius = height / 4;
                x = width * 3 / 4;
                y = height / 4 + ((height / 2 * i) / BOUNCE_STEPS);
            }

            paint.setShadowLayer(radius / 4 + 1, 0, 0, Color.RED);

            canvas.drawCircle(x, y, radius, paint);
            canvas.drawText("zzhaha哈哈😁 : " + i, width / 2, height / 2, paint);
            Trace.endSection(); // drawBouncingCircle
        } finally {
            surface.unlockCanvasAndPost(canvas);
        }
    }

    private static class MainHandler extends Handler implements CircularEncoder.Callback {
        public static final int MSG_BLINK_TEXT = 0;
        public static final int MSG_FRAME_AVAILABLE = 1;
        public static final int MSG_FILE_SAVE_COMPLETE = 2;
        public static final int MSG_BUFFER_STATUS = 3;

        private WeakReference<MultiSurfaceActivity> mWeakActivity;

        public MainHandler(MultiSurfaceActivity activity) {
            mWeakActivity = new WeakReference<MultiSurfaceActivity>(activity);
        }

        // CircularEncoder.Callback, called on encoder thread
        @Override
        public void fileSaveComplete(int status) {
            sendMessage(obtainMessage(MSG_FILE_SAVE_COMPLETE, status, 0, null));
        }

        // CircularEncoder.Callback, called on encoder thread
        @Override
        public void bufferStatus(long totalTimeMsec) {
            sendMessage(obtainMessage(MSG_BUFFER_STATUS,
                    (int) (totalTimeMsec >> 32), (int) totalTimeMsec));
        }

        @Override
        public void handleMessage(Message msg) {
            MultiSurfaceActivity activity = mWeakActivity.get();
            if (activity == null) {
                Log.d(TAG, "Got message for dead activity");
                return;
            }

            switch (msg.what) {
                case MSG_BLINK_TEXT: {
                    TextView tv = (TextView) activity.findViewById(R.id.recording_text);

                    // Attempting to make it blink by using setEnabled() doesn't work --
                    // it just changes the color.  We want to change the visibility.
                    int visibility = tv.getVisibility();
                    if (visibility == View.VISIBLE) {
                        visibility = View.INVISIBLE;
                    } else {
                        visibility = View.VISIBLE;
                    }
                    tv.setVisibility(visibility);

                    int delay = (visibility == View.VISIBLE) ? 1000 : 200;
                    sendEmptyMessageDelayed(MSG_BLINK_TEXT, delay);
                    break;
                }
                case MSG_FRAME_AVAILABLE: {
//                    activity.drawFrame();
                    break;
                }
                case MSG_FILE_SAVE_COMPLETE: {
                    activity.fileSaveComplete(msg.arg1);
                    break;
                }
                case MSG_BUFFER_STATUS: {
                    long duration = (((long) msg.arg1) << 32) |
                            (((long) msg.arg2) & 0xffffffffL);
                    break;
                }
                default:
                    throw new RuntimeException("Unknown message " + msg.what);
            }
        }
    }
}
