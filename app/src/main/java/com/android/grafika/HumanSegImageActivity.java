package com.android.grafika;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.ImageView;

import com.android.grafika.gles.EglCore;
import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.OffscreenSurface;
import com.android.grafika.gles.Texture2dProgram;
import com.android.grafika.gles.WindowSurface;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class HumanSegImageActivity extends Activity {
    GLSurfaceView mGLSurfaceView;
    int image1Res;
    int image2Res;
    int image3Res;
    int image4Res;
    SurfaceRender mRender;
    ImageView mImageView1, mImageView2;
    private EglCore mEglCore;
    private OffscreenSurface mOffscreenSurface;
    FullFrameRect mFullScreen, mFullScreen1;
    int mTextureId1, mTextureId2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_human_seg_image);
        image1Res = R.drawable.input;
        image2Res = R.drawable.result_scale;
        image3Res = R.drawable.result_final;
        image4Res = R.drawable.human_seg;
        mRender = new SurfaceRender();
        mImageView1 = findViewById(R.id.image1);
        mImageView2 = findViewById(R.id.image2);
        mImageView1.setImageResource(image1Res);

        mGLSurfaceView = findViewById(R.id.surfaceView);
        mGLSurfaceView.setEGLContextClientVersion(2);
        mRender = new SurfaceRender();
        mGLSurfaceView.setEGLConfigChooser(8,8,8,8,16,0);
        mGLSurfaceView.setRenderer(mRender);
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);



        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        mOffscreenSurface = new OffscreenSurface(mEglCore, 800, 500);
        mOffscreenSurface.makeCurrent();

        mFullScreen = new FullFrameRect(
                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D_BLEND));

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), image1Res);
        int inSampleSize = 1;
        options.inSampleSize = inSampleSize;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), image1Res);
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
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap, 0);
            bitmap.recycle();
            mTextureId1 = textures2[0];
        }

        BitmapFactory.Options options2 = new BitmapFactory.Options();
        options2.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), image4Res);
        int inSampleSize2 = 1;
        options2.inSampleSize = inSampleSize2;
        options2.inJustDecodeBounds = false;
        Bitmap bitmap2 = BitmapFactory.decodeResource(getResources(), image4Res);
        if (bitmap2 != null) {
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
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap2, 0);
            bitmap2.recycle();
            mTextureId2 = textures2[0];
        }


//        mRender.setOnGetImageBitmapListener(new OnGetImageBitmapListener() {
//            @Override
//            public void getBitmap(final Bitmap bitmap) {
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        mImageView2.setImageBitmap(bitmap);
//                    }
//                });
//            }
//        });


    }

    private void drawFrame() {
        if (mEglCore == null) {
            return;
        }
        GLES20.glViewport(0, 0, 800, 500);
        float[] m2 = new float[16];
        Matrix.setIdentityM(m2, 0);
        mFullScreen.drawFrame(mTextureId1, m2, mTextureId2, m2, true);

        capture();
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
    }

    public void onMix(View view) {
//        //分身术合成
        drawFrame();
//        mGLSurfaceView.requestRender();
//
//        mGLSurfaceView.queueEvent(new Runnable() {
//            @Override
//            public void run() {
//                // Tell the renderer that it's about to be paused so it can clean up.
//                mRender.capture();
//            }
//        });
    }

    public void capture() {
        int width = mGLSurfaceView.getWidth();
        int height = mGLSurfaceView.getHeight();
//            sendImage(width, height);

        final int[] pixelMirroredArray = new int[width * height * 4];
        final IntBuffer pixelBuffer = IntBuffer.allocate(width * height * 4);
        long start = System.currentTimeMillis();
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer);
        int[] pixelArray = pixelBuffer.array();

        // Convert upside down mirror-reversed image to right-side up normal image.
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                pixelMirroredArray[(height - i - 1) * width + j] = pixelArray[i * width + j];
            }
        }
        long end = System.currentTimeMillis();
        Log.e("zmy", "glReadPixels: " + (end - start));

        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(IntBuffer.wrap(pixelMirroredArray));


        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mImageView2.setImageBitmap(bitmap);
            }
        });

        File file = new File("/sdcard/DCIM/zmy/humanSeg.png");
        if (file.exists()) {
            file.delete();
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                out.flush();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public interface OnGetImageBitmapListener {
        void getBitmap(Bitmap bitmap);
    }

    class SurfaceRender implements GLSurfaceView.Renderer {
        FullFrameRect mFullScreen;
        FullFrameRect mFullScreen1;
        int mTextureId1, mTextureId2;
        OnGetImageBitmapListener mOnGetImageBitmapListener;

        public void setOnGetImageBitmapListener(OnGetImageBitmapListener listener) {
            mOnGetImageBitmapListener = listener;
        }

        private void sendImage(int width, int height) {
            ByteBuffer rgbaBuf = ByteBuffer.allocateDirect(width * height * 4);
            rgbaBuf.position(0);
            long start = System.currentTimeMillis();
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
                    rgbaBuf);
            long end = System.currentTimeMillis();
            Log.e("zmy", "glReadPixels: " + (end - start));

            saveRgb2Bitmap(rgbaBuf, "/sdcard/DCIM/zmy/human_segg.png", width, height);
        }

        private void saveRgb2Bitmap(Buffer buf, String filename, int width, int height) {
            Log.e("zmy", "Creating " + filename);
            BufferedOutputStream bos = null;
            try {
                File file = new File(filename);
                file.mkdirs();
                if (file.exists()) {
                    file.delete();
                }
                bos = new BufferedOutputStream(new FileOutputStream(filename));
                Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bmp.copyPixelsFromBuffer(buf);
                bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
                if (mOnGetImageBitmapListener != null) {
                    mOnGetImageBitmapListener.getBitmap(bmp);
                }
//                bmp.recycle();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (bos != null) {
                    try {
                        bos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        public void capture() {
            int width = mGLSurfaceView.getWidth();
            int height = mGLSurfaceView.getHeight();
//            sendImage(width, height);

            final int[] pixelMirroredArray = new int[width * height * 4];
            final IntBuffer pixelBuffer = IntBuffer.allocate(width * height * 4);
            long start = System.currentTimeMillis();
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer);
            int[] pixelArray = pixelBuffer.array();

            // Convert upside down mirror-reversed image to right-side up normal image.
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    pixelMirroredArray[(height - i - 1) * width + j] = pixelArray[i * width + j];
                }
            }
            long end = System.currentTimeMillis();
            Log.e("zmy", "glReadPixels: " + (end - start));

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(IntBuffer.wrap(pixelMirroredArray));

            if (mOnGetImageBitmapListener != null) {
                mOnGetImageBitmapListener.getBitmap(bitmap);
            }

            File file = new File("/sdcard/DCIM/zmy/humanSeg.png");
            if (file.exists()) {
                file.delete();
            }
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                try {
                    out.flush();
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            mFullScreen = new FullFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D_BLEND));

            mFullScreen1 = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D));
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(getResources(), image1Res);
            int inSampleSize = 1;
            options.inSampleSize = inSampleSize;
            options.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), image1Res);
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
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap, 0);
                bitmap.recycle();
                mTextureId1 = textures2[0];
            }

            BitmapFactory.Options options2 = new BitmapFactory.Options();
            options2.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(getResources(), image4Res);
            int inSampleSize2 = 1;
            options2.inSampleSize = inSampleSize2;
            options2.inJustDecodeBounds = false;
            Bitmap bitmap2 = BitmapFactory.decodeResource(getResources(), image4Res);
            if (bitmap2 != null) {
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
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap2, 0);
                bitmap2.recycle();
                mTextureId2 = textures2[0];
            }
        }

        @Override
        public void onDrawFrame(GL10 gl) {
//            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
//            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            float[] m2 = new float[16];
            Matrix.setIdentityM(m2, 0);
//            mFullScreen1.drawFrame(mTextureId1, m2, true);
            mFullScreen.drawFrame(mTextureId1, m2, mTextureId2, m2, true);
        }
    }
}
