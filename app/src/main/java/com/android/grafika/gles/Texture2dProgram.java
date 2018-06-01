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

package com.android.grafika.gles;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.text.TextUtils;
import android.util.Log;

import java.nio.FloatBuffer;

/**
 * GL program and supporting functions for textured 2D shapes.
 */
public class Texture2dProgram {
    private static final String TAG = GlUtil.TAG;
    private int mFilterInputTextureUniform2;
    private int mFilterInputTextureUniform;

    public enum ProgramType {
        TEXTURE_2D, TEXTURE_EXT, TEXTURE_EXT_BW, TEXTURE_EXT_FILT, TEXTURE_EXT_2,
        TEXTURE_EXT_SLIDE, TEXTURE_EXT_BLEND, TEXTURE_EXT_TEMPLATE, TEXTURE_2D_BLEND, TEXTURE_EXT_SLIDE2
    }

    // Simple vertex shader, used for all programs.
    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uTexMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * aPosition;\n" +
            "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
            "}\n";

    private static final String VERTEX_SHADER_BLEND =
            "uniform mat4 uMVPMatrix;\n" +
                    "uniform mat4 uTexMatrix;\n" +
                    "uniform mat4 uTexMatrix2;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "attribute vec4 aTextureCoord2;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "varying vec2 vTextureCoord2;\n" +
                    "void main() {\n" +
                    "    gl_Position = uMVPMatrix * aPosition;\n" +
                    "    vTextureCoord = vec2((uTexMatrix * aTextureCoord).x,(uTexMatrix * aTextureCoord).y);\n" +
                    "    vTextureCoord2 = (uTexMatrix2 * aTextureCoord2).xy;\n" +
                    "}\n";

    // Simple fragment shader for use with "normal" 2D textures.
    private static final String FRAGMENT_SHADER_2D =
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform sampler2D sTexture;\n" +
                    "uniform float alpha;\n" +
            "void main() {\n" +
                    "    vec4 color;\n" +
                    "    color = texture2D(sTexture, vTextureCoord);\n" +
                    "    gl_FragColor = color * alpha;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER_2D_BLEND =
            "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "varying vec2 vTextureCoord2;\n" +
                    "uniform sampler2D sTexture;\n" +
                    "uniform sampler2D sTexture2;\n" +
                    "uniform int maskMode;\n" +
                    "uniform vec4 maskColor;\n" +
                    "void main() {\n" +
                    "    vec4 textureColor =texture2D(sTexture, vTextureCoord);\n" +
                    "    vec4 textureColor2 =texture2D(sTexture2, vTextureCoord2);\n" +
                    "    if (textureColor2.r == 0.0 && textureColor2.g == 0.0 && textureColor2.b ==0.0){\n" +
                    "       textureColor2.a = 0.0;\n" +
                    "       gl_FragColor = textureColor2;\n" +
                    "       // discard;\n" +
                    "    } else {\n" +
                    "       if (maskMode == 1) {\n" +
                    "           gl_FragColor = maskColor;\n" +
                    "           //gl_FragColor = mix(vec4(maskColor.rgb,1.0),textureColor,maskColor.a);\n" +
                    "       } else {\n" +
                    "           gl_FragColor = textureColor;\n" +
                    "       }\n" +
                    "       //gl_FragColor = mix(maskColor,textureColor,0.5);\n" +
                    "    }\n" +
                    "}\n";

    // Simple fragment shader for use with external 2D textures (e.g. what we get from
    // SurfaceTexture).
    private static final String FRAGMENT_SHADER_EXT =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform float alpha;\n" +
            "void main() {\n" +
            "    vec4 color;\n" +
            "    color = texture2D(sTexture, vTextureCoord);\n" +
            "    gl_FragColor = color * alpha;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER_EXT2 =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "uniform float alpha;\n" +
                    "void main() {\n" +
                    "    vec4 color;\n" +
                    "    color = texture2D(sTexture, vTextureCoord);\n" +
                    "    if (color.r<0.5 && color.g>0.5 && color.b<0.5){\n" +
                    "    color.a = alpha;\n" +
                    "    }\n" +
                    "    gl_FragColor = color;\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER_EXT_SLIDE =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "uniform float alpha;\n" +
                    "void main() {\n" +
                    "    vec4 color;\n" +
                    "    if(alpha==0.0 || alpha == 1.0){\n" +
                    "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "    } else {\n" +
                    "    color = texture2D(sTexture, vTextureCoord);\n" +
                    "    gl_FragColor = color*0.1 +texture2D(sTexture,vec2(vTextureCoord.x+0.01,vTextureCoord.y))*0.2\n" +
                    "    +texture2D(sTexture,vec2(vTextureCoord.x+0.02,vTextureCoord.y))*0.3\n" +
                    "    +texture2D(sTexture,vec2(vTextureCoord.x+0.03,vTextureCoord.y))*0.4;\n" +
                    "    }\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER_EXT_SLIDE2 =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "varying vec2 vTextureCoord2;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "uniform samplerExternalOES sTexture2;\n" +
                    "uniform float distance;\n" +
                    "void main() {\n" +
                    "    vec2 vector;\n" +
                    "    vec4 color;\n" +
                    "    if(vTextureCoord.x < distance){\n" +
                    "       vector[0]=vTextureCoord.x+(1.0-distance);\n" +
                    "       vector[1]=vTextureCoord.y;\n" +
                    "       color = texture2D(sTexture, vector);\n" +
                    "       gl_FragColor = color * 0.2;\n" +
                    "       for (int i = 1; i<5;i++) {\n" +
                    "           gl_FragColor+=texture2D(sTexture,vec2(vector.x-0.02*float(i)*(1.0-distance),vector.y))*0.1;\n" +
                    "       }\n" +
                    "       for (int i = 1; i<5;i++) {\n" +
                    "           gl_FragColor+=texture2D(sTexture,vec2(vector.x+0.02*(1.0-distance)*float(i),vector.y))*0.1;\n" +
                    "       }\n" +
                    "    } else {\n" +
                    "       vector[0]=vTextureCoord2.x-distance;\n" +
                    "       vector[1]=vTextureCoord2.y;\n" +
                    "       color = texture2D(sTexture2, vector);\n" +
                    "       gl_FragColor = color * 0.2;\n" +
                    "       for (int i = 1; i<5;i++) {\n" +
                    "           gl_FragColor+=texture2D(sTexture2,vec2(vector.x-0.02*float(i)*(distance),vector.y))*0.1;\n" +
                    "       }\n" +
                    "       for (int i = 1; i<5;i++) {\n" +
                    "           gl_FragColor+=texture2D(sTexture2,vec2(vector.x+0.02*float(i)*(distance),vector.y))*0.1;\n" +
                    "       }\n" +
                    "    }\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER_EXT_BLEND =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "varying vec2 vTextureCoord2;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "uniform samplerExternalOES sTexture2;\n" +
                    "uniform float alpha;\n" +
                    "uniform float thresholdSensitivity;\n" +
                    "uniform float smoothing;\n" +
                    "uniform vec3 colorToReplace;\n" +
                    "const highp vec3 W = vec3(0.2125, 0.7154, 0.0721);"+
                    "void main() {\n" +
                    "    vec4 textureColor =texture2D(sTexture, vTextureCoord);\n" +
                    "    vec4 textureColor2 =texture2D(sTexture2, vTextureCoord2);\n" +
                    "    float maskY = 0.2989 * colorToReplace.r + 0.5866 * colorToReplace.g + 0.1145 * colorToReplace.b;\n" +
                    "    float maskCr = 0.7132 * (colorToReplace.r - maskY);\n" +
                    "    float maskCb = 0.5647 * (colorToReplace.b - maskY);\n" +
                    "    float Y = 0.2989 * textureColor.r + 0.5866 * textureColor.g + 0.1145 * textureColor.b;\n" +
                    "    float Cr = 0.7132 * (textureColor.r - Y);\n" +
                    "    float Cb = 0.5647 * (textureColor.b - Y);\n" +
                    "    float L = dot(textureColor.rgb, W);\n" +
                    "    float maskL = dot(colorToReplace.rgb, W);\n" +
                    "    float blendValue = 1.0 - smoothstep(thresholdSensitivity, thresholdSensitivity + smoothing, distance(vec3(Cr, Cb, L), vec3(maskCr, maskCb, maskL)));\n" +
                    "    gl_FragColor = mix(textureColor, textureColor2, blendValue);\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER_EXT_TEMPLATE =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "varying vec2 vTextureCoord2;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "uniform samplerExternalOES sTexture2;\n" +
                    "uniform float x;\n" +
                    "uniform float y;\n" +
                    "uniform float alpha;\n" +
                    "void main() {\n" +
                    "    vec4 textureColor =texture2D(sTexture, vTextureCoord);\n" +
                    "    if (vTextureCoord.x > x && vTextureCoord.x < x+0.3 && vTextureCoord.y > y && vTextureCoord.y < y+0.3){\n" +
                    "       gl_FragColor = texture2D(sTexture2, vec2(vTextureCoord2.x*0.001,vTextureCoord2.y*0.001));\n" +
                    "    } else {" +
                    "      gl_FragColor =textureColor; " +
                    "    }\n" +
                    "}\n";

    // Fragment shader that converts color to black & white with a simple transformation.
    private static final String FRAGMENT_SHADER_EXT_BW =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "    vec4 tc = texture2D(sTexture, vTextureCoord);\n" +
            "    float color = tc.r * 0.3 + tc.g * 0.59 + tc.b * 0.11;\n" +
            "    gl_FragColor = vec4(color, color, color, 1.0);\n" +
            "}\n";

    // Fragment shader with a convolution filter.  The upper-left half will be drawn normally,
    // the lower-right half will have the filter applied, and a thin red line will be drawn
    // at the border.
    //
    // This is not optimized for performance.  Some things that might make this faster:
    // - Remove the conditionals.  They're used to present a half & half view with a red
    //   stripe across the middle, but that's only useful for a demo.
    // - Unroll the loop.  Ideally the compiler does this for you when it's beneficial.
    // - Bake the filter kernel into the shader, instead of passing it through a uniform
    //   array.  That, combined with loop unrolling, should reduce memory accesses.
    public static final int KERNEL_SIZE = 9;
    private static final String FRAGMENT_SHADER_EXT_FILT =
            "#extension GL_OES_EGL_image_external : require\n" +
            "#define KERNEL_SIZE " + KERNEL_SIZE + "\n" +
            "precision highp float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform float uKernel[KERNEL_SIZE];\n" +
            "uniform vec2 uTexOffset[KERNEL_SIZE];\n" +
            "uniform float uColorAdjust;\n" +
            "void main() {\n" +
            "    int i = 0;\n" +
            "    vec4 sum = vec4(0.0);\n" +
            "    if (vTextureCoord.x < vTextureCoord.y - 0.005) {\n" +
            "        for (i = 0; i < KERNEL_SIZE; i++) {\n" +
            "            vec4 texc = texture2D(sTexture, vTextureCoord + uTexOffset[i]);\n" +
            "            sum += texc * uKernel[i];\n" +
            "        }\n" +
            "    sum += uColorAdjust;\n" +
            "    } else if (vTextureCoord.x > vTextureCoord.y + 0.005) {\n" +
            "        sum = texture2D(sTexture, vTextureCoord);\n" +
            "    } else {\n" +
            "        sum.r = 1.0;\n" +
            "    }\n" +
            "    gl_FragColor = sum;\n" +
            "}\n";

    private ProgramType mProgramType;

    // Handles to the GL program and various components of it.
    private int mProgramHandle;
    private int muMVPMatrixLoc;
    private int muTexMatrixLoc;
    private int muTexMatrixLoc2;
    private int muKernelLoc;
    private int muTexOffsetLoc;
    private int muColorAdjustLoc;
    private int maPositionLoc;
    private int maTextureCoordLoc;
    private int maTextureCoordLoc2;

    private int mTextureTarget;

    private float[] mKernel = new float[KERNEL_SIZE];
    private float[] mTexOffset;
    private float mColorAdjust;


    /**
     * Prepares the program in the current EGL context.
     */
    public Texture2dProgram(ProgramType programType) {
        mProgramType = programType;

        switch (programType) {
            case TEXTURE_2D:
                mTextureTarget = GLES20.GL_TEXTURE_2D;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D);
                break;
            case TEXTURE_EXT:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT);
                break;
            case TEXTURE_EXT_BW:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_BW);
                break;
            case TEXTURE_EXT_FILT:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_FILT);
                break;

            case TEXTURE_EXT_2:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT2);
                break;
            case TEXTURE_EXT_SLIDE:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_SLIDE);
                break;
            case TEXTURE_EXT_SLIDE2:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER_BLEND, FRAGMENT_SHADER_EXT_SLIDE2);
                break;
            case TEXTURE_EXT_BLEND:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER_BLEND, FRAGMENT_SHADER_EXT_BLEND);
                break;
            case TEXTURE_EXT_TEMPLATE:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER_BLEND, FRAGMENT_SHADER_EXT_TEMPLATE);
                break;
            case TEXTURE_2D_BLEND:
                mTextureTarget = GLES20.GL_TEXTURE_2D;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER_BLEND, FRAGMENT_SHADER_2D_BLEND);
                break;
            default:
                throw new RuntimeException("Unhandled type " + programType);
        }
        if (mProgramHandle == 0) {
            throw new RuntimeException("Unable to create program");
        }
        Log.d(TAG, "Created program " + mProgramHandle + " (" + programType + ")");

        // get locations of attributes and uniforms

        maPositionLoc = GLES20.glGetAttribLocation(mProgramHandle, "aPosition");
        GlUtil.checkLocation(maPositionLoc, "aPosition");
        mFilterInputTextureUniform = GLES20.glGetUniformLocation(mProgramHandle, "sTexture");
        maTextureCoordLoc = GLES20.glGetAttribLocation(mProgramHandle, "aTextureCoord");
        GlUtil.checkLocation(maTextureCoordLoc, "aTextureCoord");
        if (mProgramType == ProgramType.TEXTURE_EXT_BLEND || mProgramType == ProgramType.TEXTURE_EXT_TEMPLATE
                || mProgramType == ProgramType.TEXTURE_2D_BLEND || mProgramType == ProgramType.TEXTURE_EXT_SLIDE2) {
            mFilterInputTextureUniform2 = GLES20.glGetUniformLocation(mProgramHandle, "sTexture2");
            maTextureCoordLoc2 = GLES20.glGetAttribLocation(mProgramHandle, "aTextureCoord2");
            GlUtil.checkLocation(maTextureCoordLoc2, "aTextureCoord2");
        }
        muMVPMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uMVPMatrix");
        GlUtil.checkLocation(muMVPMatrixLoc, "uMVPMatrix");
        muTexMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uTexMatrix");
        GlUtil.checkLocation(muTexMatrixLoc, "uTexMatrix");
        if (mProgramType == ProgramType.TEXTURE_EXT_BLEND || mProgramType == ProgramType.TEXTURE_EXT_TEMPLATE
                || mProgramType == ProgramType.TEXTURE_2D_BLEND || mProgramType == ProgramType.TEXTURE_EXT_SLIDE2) {
            muTexMatrixLoc2 = GLES20.glGetUniformLocation(mProgramHandle, "uTexMatrix2");
            GlUtil.checkLocation(muTexMatrixLoc2, "uTexMatrix2");
        }
        muKernelLoc = GLES20.glGetUniformLocation(mProgramHandle, "uKernel");
        if (muKernelLoc < 0) {
            // no kernel in this one
            muKernelLoc = -1;
            muTexOffsetLoc = -1;
            muColorAdjustLoc = -1;
        } else {
            // has kernel, must also have tex offset and color adj
            muTexOffsetLoc = GLES20.glGetUniformLocation(mProgramHandle, "uTexOffset");
            GlUtil.checkLocation(muTexOffsetLoc, "uTexOffset");
            muColorAdjustLoc = GLES20.glGetUniformLocation(mProgramHandle, "uColorAdjust");
            GlUtil.checkLocation(muColorAdjustLoc, "uColorAdjust");

            // initialize default values
            setKernel(new float[] {0f, 0f, 0f,  0f, 1f, 0f,  0f, 0f, 0f}, 0f);
            setTexSize(256, 256);
        }
    }

    /**
     * Releases the program.
     * <p>
     * The appropriate EGL context must be current (i.e. the one that was used to create
     * the program).
     */
    public void release() {
        Log.d(TAG, "deleting program " + mProgramHandle);
        GLES20.glDeleteProgram(mProgramHandle);
        mProgramHandle = -1;
    }

    /**
     * Returns the program type.
     */
    public ProgramType getProgramType() {
        return mProgramType;
    }

    /**
     * Creates a texture object suitable for use with this program.
     * <p>
     * On exit, the texture will be bound.
     */
    public int createTextureObject() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GlUtil.checkGlError("glGenTextures");

        int texId = textures[0];
        GLES20.glBindTexture(mTextureTarget, texId);
        GlUtil.checkGlError("glBindTexture " + texId);

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GlUtil.checkGlError("glTexParameter");

        return texId;
    }

    public int createTexture2DObject() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GlUtil.checkGlError("glGenTextures");

        int texId = textures[0];
        GLES20.glBindTexture(mTextureTarget, texId);
        GlUtil.checkGlError("glBindTexture " + texId);

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GlUtil.checkGlError("glTexParameter");
        return texId;
    }

    /**
     * Configures the convolution filter values.
     *
     * @param values Normalized filter values; must be KERNEL_SIZE elements.
     */
    public void setKernel(float[] values, float colorAdj) {
        if (values.length != KERNEL_SIZE) {
            throw new IllegalArgumentException("Kernel size is " + values.length +
                    " vs. " + KERNEL_SIZE);
        }
        System.arraycopy(values, 0, mKernel, 0, KERNEL_SIZE);
        mColorAdjust = colorAdj;
        //Log.d(TAG, "filt kernel: " + Arrays.toString(mKernel) + ", adj=" + colorAdj);
    }

    /**
     * Sets the size of the texture.  This is used to find adjacent texels when filtering.
     */
    public void setTexSize(int width, int height) {
        float rw = 1.0f / width;
        float rh = 1.0f / height;

        // Don't need to create a new array here, but it's syntactically convenient.
        mTexOffset = new float[] {
            -rw, -rh,   0f, -rh,    rw, -rh,
            -rw, 0f,    0f, 0f,     rw, 0f,
            -rw, rh,    0f, rh,     rw, rh
        };
        //Log.d(TAG, "filt size: " + width + "x" + height + ": " + Arrays.toString(mTexOffset));
    }

    /**
     * Issues the draw call.  Does the full setup on every call.
     *
     * @param mvpMatrix The 4x4 projection matrix.
     * @param vertexBuffer Buffer with vertex position data.
     * @param firstVertex Index of first vertex to use in vertexBuffer.
     * @param vertexCount Number of vertices in vertexBuffer.
     * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
     * @param vertexStride Width, in bytes, of the position data for each vertex (often
     *        vertexCount * sizeof(float)).
     * @param texMatrix A 4x4 transformation matrix for texture coords.  (Primarily intended
     *        for use with SurfaceTexture.)
     * @param texBuffer Buffer with vertex texture data.
     * @param texStride Width, in bytes, of the texture data for each vertex.
     */
    public void draw(float[] mvpMatrix, FloatBuffer vertexBuffer, int firstVertex,
            int vertexCount, int coordsPerVertex, int vertexStride,
            float[] texMatrix, FloatBuffer texBuffer, int textureId, int texStride) {
        GlUtil.checkGlError("draw start");

        // Select the program.
        GLES20.glUseProgram(mProgramHandle);
        GlUtil.checkGlError("glUseProgram");

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(mTextureTarget, textureId);

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mvpMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, texMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionLoc, coordsPerVertex,
            GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);
        GlUtil.checkGlError("glVertexAttribPointer");

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureCoordLoc, 2,
                GLES20.GL_FLOAT, false, texStride, texBuffer);
            GlUtil.checkGlError("glVertexAttribPointer");

        // Populate the convolution kernel, if present.
        if (muKernelLoc >= 0) {
            GLES20.glUniform1fv(muKernelLoc, KERNEL_SIZE, mKernel, 0);
            GLES20.glUniform2fv(muTexOffsetLoc, KERNEL_SIZE, mTexOffset, 0);
            GLES20.glUniform1f(muColorAdjustLoc, mColorAdjust);
        }

        onDrawArraysPre();
        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount);
        GlUtil.checkGlError("glDrawArrays");

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc);
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc);
        GLES20.glBindTexture(mTextureTarget, 0);
        GLES20.glUseProgram(0);
    }

    public void draw(float[] mvpMatrix, FloatBuffer vertexBuffer, int firstVertex,
                     int vertexCount, int coordsPerVertex, int vertexStride,
                     float[] texMatrix, FloatBuffer texBuffer, int textureId, int texStride,
                     int textureId2, float[] texMatrix2, FloatBuffer texBuffer2) {
        GlUtil.checkGlError("draw start");
        // Select the program.
        GLES20.glUseProgram(mProgramHandle);
        GlUtil.checkGlError("glUseProgram");

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(mTextureTarget, textureId);
        GLES20.glUniform1i(mFilterInputTextureUniform, 0);

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mvpMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, texMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionLoc, coordsPerVertex,
                GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);
        GlUtil.checkGlError("glVertexAttribPointer");

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureCoordLoc, 2,
                GLES20.GL_FLOAT, false, texStride, texBuffer);
        GlUtil.checkGlError("glVertexAttribPointer");





        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(mTextureTarget, textureId2);
        GLES20.glUniform1i(mFilterInputTextureUniform2, 3);

        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(muTexMatrixLoc2, 1, false, texMatrix2, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");


        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc2);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureCoordLoc2, 2,
                GLES20.GL_FLOAT, false, texStride, texBuffer2);
        GlUtil.checkGlError("glVertexAttribPointer");

        // Populate the convolution kernel, if present.
        if (muKernelLoc >= 0) {
            GLES20.glUniform1fv(muKernelLoc, KERNEL_SIZE, mKernel, 0);
            GLES20.glUniform2fv(muTexOffsetLoc, KERNEL_SIZE, mTexOffset, 0);
            GLES20.glUniform1f(muColorAdjustLoc, mColorAdjust);
        }

        onDrawArraysPre();
        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount);
        GlUtil.checkGlError("glDrawArrays");

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc);
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc);
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc2);
        GLES20.glBindTexture(mTextureTarget, 0);
        GLES20.glUseProgram(0);
    }

    private float mAlpha = 1.0f;

    public void setAlpha(float alpha) {
        mAlpha = alpha;
    }

    private float mX = 0.0f;
    private float mY = 0.0f;
    private float mW = 0.0f;
    private float mH = 0.0f;
    public void setLocation(float x, float y, float w, float h) {
        mX = x;
        mY = y;
        mW = w;
        mH = h;
        Log.e("zmy", "x :" +x+" y :"+y+"w : "+w+" h :"+h);
    }

    private float mDistance;

    public void setDistance(float distance) {
        mDistance = distance;
    }

    private float[] mColorToReplace = new float[]{0.0f, 0.0f, 0.0f};

    private int mMaskMode;
    private float[] mMaskColor = new float[]{1.0f, 0.0f, 0.0f, 0.5f};

    public void setHumanSegMaskParams(int maskMode, String maskColor, float maskAlpha) {
        mMaskMode = maskMode;
        if (TextUtils.isEmpty(maskColor) || maskColor.length() < 6) {
            return;
        }
        try {
            String rStr = maskColor.substring(maskColor.length() - 6, maskColor.length() - 4);
            String gStr = maskColor.substring(maskColor.length() - 4, maskColor.length() - 2);
            String bStr = maskColor.substring(maskColor.length() - 2, maskColor.length());

            int r = hexToDecimal(rStr.toUpperCase());
            int g = hexToDecimal(gStr.toUpperCase());
            int b = hexToDecimal(bStr.toUpperCase());

            mMaskColor[0] = r * 1.0f / 255;
            mMaskColor[1] = g * 1.0f / 255;
            mMaskColor[2] = b * 1.0f / 255;
        } catch (Exception e) {
            mMaskColor[0] = 1;
            mMaskColor[1] = 0;
            mMaskColor[2] = 0;
        }
        mMaskColor[3] = maskAlpha;
    }

    public int hexToDecimal(String hex) {
        if (TextUtils.isEmpty(hex)) {
            return 0;
        }
        int decimalValue = 0;
        for (int i = 0; i < hex.length(); i++) {
            char hexChar = hex.charAt(i);
            decimalValue = decimalValue * 16 + hexCharToDecimal(hexChar);
        }
        return decimalValue;
    }

    public int hexCharToDecimal(char hexChar) {
        if (hexChar >= 'A' && hexChar <= 'F') {
            return 10 + hexChar - 'A';
        } else {
            return hexChar - '0';
        }
    }

    public void onDrawArraysPre() {
        GLES20.glUniform1f(GLES20.glGetUniformLocation(mProgramHandle, "alpha"), mAlpha);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(mProgramHandle, "distance"), mDistance);
        if (mProgramType == ProgramType.TEXTURE_EXT_BLEND || mProgramType == ProgramType.TEXTURE_2D_BLEND) {
            GLES20.glUniform1f(GLES20.glGetUniformLocation(mProgramHandle, "thresholdSensitivity"), 0.4f);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(mProgramHandle, "smoothing"), 0.1f);
            GLES20.glUniform3fv(GLES20.glGetUniformLocation(mProgramHandle, "colorToReplace"), 1, FloatBuffer.wrap(mColorToReplace));

        }

        if (mProgramType == ProgramType.TEXTURE_EXT_TEMPLATE) {
            GLES20.glUniform1f(GLES20.glGetUniformLocation(mProgramHandle, "x"), mX);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(mProgramHandle, "y"), mY);

        }

        if (mProgramType == ProgramType.TEXTURE_2D_BLEND) {
            GLES20.glUniform1i(GLES20.glGetUniformLocation(mProgramHandle, "maskMode"), mMaskMode);
            GLES20.glUniform4fv(GLES20.glGetUniformLocation(mProgramHandle, "maskColor"), 1, FloatBuffer.wrap(mMaskColor));
        }
    }
}
