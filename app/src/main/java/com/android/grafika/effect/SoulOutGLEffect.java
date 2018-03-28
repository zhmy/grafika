package com.android.grafika.effect;

import android.content.Context;
import android.opengl.GLES20;

import com.android.grafika.GrafikaApplication;
import com.android.grafika.R;

import java.nio.FloatBuffer;

/**
 * Created by wanginbeijing on 2017/8/16.
 */

public class SoulOutGLEffect extends BaseGLEffect {
    private int mPercentageLocation;
    private float mPercentage;

    private volatile static ThreadLocal<SoulOutGLEffect> mInstanceLocal;

    public static SoulOutGLEffect getInstance() {
        if (mInstanceLocal == null) {
            synchronized (SoulOutGLEffect.class) {
                if (mInstanceLocal == null) {
                    mInstanceLocal = new ThreadLocal<SoulOutGLEffect>();
                }
            }
        }
        if (mInstanceLocal.get() == null) {
            mInstanceLocal.set(new SoulOutGLEffect(GrafikaApplication.getContext()));
        }
        return mInstanceLocal.get();
    }

    public static void reset() {
        if (mInstanceLocal != null) {
            if (mInstanceLocal.get() != null) {
                mInstanceLocal.set(null);
            }
        }
    }


    public SoulOutGLEffect(Context context) {
        super(readShaderFromRaw(context, R.raw.default_vertex), readShaderFromRaw(context, R.raw.soul_out_fragment));
    }

    @Override
    protected void init() {
        super.init();
        mPercentageLocation = GLES20.glGetUniformLocation(mProgram, "percentage");
    }


    public int draw(int textureId, FloatBuffer cubeBuffer, FloatBuffer textureBuffer, float[] texMatrix, float percentage) {
        mPercentage = percentage / 100;
        return super.draw(textureId, cubeBuffer, textureBuffer, texMatrix);
    }

    @Override
    protected void onDrawArraysPre() {
        GLES20.glUniform1f(mPercentageLocation, mPercentage);
    }
}
