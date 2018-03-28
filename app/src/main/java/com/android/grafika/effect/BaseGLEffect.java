package com.android.grafika.effect;

/**
 * Created by v_yanligang on 2017/5/18.
 */

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.LinkedList;

public class BaseGLEffect {

    public static final String VERTEX_SHADERCODE =
            "attribute vec4 position;\n" +
                    "attribute vec4 inputTextureCoordinate;\n" +
                    " \n" +
                    "varying vec2 textureCoordinate;\n" +
                    " \n" +
                    "void main()\n" +
                    "{\n" +
                    "    gl_Position = position;\n" +
                    "    textureCoordinate = inputTextureCoordinate.xy;\n" +
                    "}";

    public static final String FRAGMENT_SHADERCODE =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;" +
                    "varying vec2 textureCoordinate;\n" +
                    "uniform samplerExternalOES s_texture;\n" +
                    "void main() {" +
                    "  gl_FragColor = texture2D( s_texture, textureCoordinate );\n" +
                    "}";

    /**
     * 着色器程序
     */
    public int mProgram;
    public int mGLAttribPosition;
    public int mGLAttribTextureCoordinate;
    private int muTexMatrixLoc;
    private int muMVPMatrixLoc;
    public String mVertexShader;
    public String mFragmentShader;
    private LinkedList<Runnable> mRunOnDraw = new LinkedList<Runnable>();

    public static final float[] IDENTITY_MATRIX;

    static {
        IDENTITY_MATRIX = new float[16];
        Matrix.setIdentityM(IDENTITY_MATRIX, 0);
    }


    public BaseGLEffect() {
        this(VERTEX_SHADERCODE, FRAGMENT_SHADERCODE);
    }

    public BaseGLEffect(String vertexShaderCode, String fragmentShaderCode) {
        this.mVertexShader = vertexShaderCode;
        this.mFragmentShader = fragmentShaderCode;
        init();
    }

    protected void init() {
//        BdLog.e("BaseGLEffect", "vertexshader" + mVertexShader + "fragmentshader" + mFragmentShader);
        mProgram = OpenGLUtils.loadProgram(mVertexShader, mFragmentShader);
        // 获取着色器程序中，指定为attribute类型变量的id
        mGLAttribPosition = GLES20.glGetAttribLocation(mProgram, "position");
        // 获取着色器程序中，指定为uniform类型变量的id。
        mGLAttribTextureCoordinate = GLES20.glGetAttribLocation(mProgram, "inputTextureCoordinate");
        muMVPMatrixLoc = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        muTexMatrixLoc = GLES20.glGetUniformLocation(mProgram, "textureTransform");
        onInitialized();
    }

    protected void onInitialized() {
    }

    public static void checkGlError(String op) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            String msg = op + ": glError 0x" + Integer.toHexString(error);
//            BdLog.e("glError", msg);
        }
    }

    public int draw(final int textureId, final FloatBuffer cubeBuffer,
                    final FloatBuffer textureBuffer, float[] texMatrix) {
        checkGlError("1");
        GLES20.glUseProgram(mProgram);
        checkGlError("2");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        checkGlError("3");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        checkGlError("4");
        runPendingOnDrawTasks();
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, IDENTITY_MATRIX, 0);
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, texMatrix, 0);
        cubeBuffer.position(0);
        // 顶点位置数据传入着色器
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, cubeBuffer);
        checkGlError("5");
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        checkGlError("6");
        textureBuffer.position(0);
        // 顶点坐标传递到顶点着色器
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
                textureBuffer);
        checkGlError("7");
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        checkGlError("8");

        onDrawArraysPre();
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("9");
        onDrawArraysAfter();
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        checkGlError("10");
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        checkGlError("11");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        checkGlError("12");
        GLES20.glUseProgram(0);
        checkGlError("13");
        return OpenGLUtils.ON_DRAWN;
    }

    protected void onDrawArraysPre() {
    }

    protected void onDrawArraysAfter() {
//        ByteBuffer buf = ByteBuffer.allocateDirect(1080 * 1854 * 4);
//        buf.order(ByteOrder.LITTLE_ENDIAN);
//        GLES20.glReadPixels(0, 0, 1080, 1854, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
//        Bitmap bmp = Bitmap.createBitmap(1080, 1854, Bitmap.Config.ARGB_8888);
//        bmp.copyPixelsFromBuffer(buf);

    }


    protected void runPendingOnDrawTasks() {
        while (!mRunOnDraw.isEmpty()) {
            mRunOnDraw.removeFirst().run();
        }
    }

    protected static String readShaderFromRaw(Context context, int resourceId) {
        InputStream inputStream = context.getResources().openRawResource(resourceId);
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String nextLine;
        StringBuilder sb = new StringBuilder();
        try {
            while ((nextLine = bufferedReader.readLine()) != null) {
                sb.append(nextLine);
                sb.append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

}
