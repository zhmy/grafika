package com.android.grafika.effect;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;


/**
 * Created by wanginbeijing on 2017/8/15.
 */

public class GLEffectUtil {
    public static BaseGLEffect getGLEffect(BaseEffect effect) {
        switch (effect.effectType) {
            case NO_MATCH:
                return null;
//            case HORIZONTAL_TRANSLATION:
//                return TranslationHorizontalGLEffect.getInstance();
//            case VERTICAL_TRANSLATION:
//                return TranslationVerticalGLEffect.getInstance();
//            case SCALE_BIG:
//                return ScaleBigGLEffect.getInstance();
//            case SCALE_SMALL:
//                return ScaleSmallGLEffect.getInstance();
            case SOUL_OUT:
                return SoulOutGLEffect.getInstance();
        }
        return null;
    }

    public static FloatBuffer DEFAULT_GL_CUBE_BUFFER;
    public static FloatBuffer DEFAULT_GL_TEXTURE_BUFFER;

    static {
        DEFAULT_GL_CUBE_BUFFER = ByteBuffer.allocateDirect(OpenGLUtils.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        DEFAULT_GL_CUBE_BUFFER.put(OpenGLUtils.CUBE).position(0);

        DEFAULT_GL_TEXTURE_BUFFER = ByteBuffer.allocateDirect(OpenGLUtils.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        DEFAULT_GL_TEXTURE_BUFFER.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
    }



    public static void applGLEffect(final int textureId, final FloatBuffer cubeBuffer,
                                    final FloatBuffer textureBuffer, float[] texMatrix, BaseEffect effect, float timePercentage) {
        if(effect == null){
            return;
        }
        switch (effect.effectType) {
            case HORIZONTAL_TRANSLATION:
            case VERTICAL_TRANSLATION:
                applyTranslationGLEffect(textureId, cubeBuffer, textureBuffer, texMatrix, effect, timePercentage);
                break;
            case SCALE_BIG:
            case SCALE_SMALL:
                applyScaleGLEffect(textureId, cubeBuffer, textureBuffer, texMatrix,  effect, timePercentage);
                break;
            case SOUL_OUT:
                applySoulOutGLEffect(textureId, cubeBuffer, textureBuffer, texMatrix,  effect, timePercentage);
                break;
            case WHITE_BLACK:
                applyWhiteBlackGLEffect(textureId, cubeBuffer, textureBuffer, texMatrix);
                break;
        }
    }

    private static void applyTranslationGLEffect(final int textureId, final FloatBuffer cubeBuffer,
                                                 final FloatBuffer textureBuffer, float[] texMatrix, BaseEffect effect, float timePercentage) {
        float translationPercentage = (float) ((effect.endPercentage - effect.startPercentage) * timePercentage + effect.startPercentage);
//        switch (effect.effectType) {
//            case HORIZONTAL_TRANSLATION:
//                TranslationHorizontalGLEffect.getInstance().draw(textureId, cubeBuffer, textureBuffer, texMatrix, translationPercentage);
//                break;
//            case VERTICAL_TRANSLATION:
//                TranslationVerticalGLEffect.getInstance().draw(textureId, cubeBuffer, textureBuffer, texMatrix, translationPercentage);
//                break;
//        }
    }

    private static void applyScaleGLEffect(final int textureId, final FloatBuffer cubeBuffer,
                                           final FloatBuffer textureBuffer, float[] texMatrix, BaseEffect effect, float timePercentage) {
        float scalePercentage = (float) ((effect.endPercentage - effect.startPercentage) * timePercentage + effect.startPercentage);
//        switch (effect.effectType) {
//            case SCALE_BIG:
//                ScaleBigGLEffect.getInstance().draw(textureId, cubeBuffer, textureBuffer, texMatrix, scalePercentage);
//                break;
//            case SCALE_SMALL:
//                ScaleSmallGLEffect.getInstance().draw(textureId, cubeBuffer, textureBuffer, texMatrix, scalePercentage);
//                break;
//        }
    }

    private static void applySoulOutGLEffect(final int textureId, final FloatBuffer cubeBuffer,
                                             final FloatBuffer textureBuffer, float[] texMatrix, BaseEffect effect, float timePercentage) {
        float percentage = (float) ((effect.endPercentage - effect.startPercentage) * timePercentage + effect.startPercentage);
        SoulOutGLEffect.getInstance().draw(textureId, cubeBuffer, textureBuffer, texMatrix, percentage);
    }


    private static void applyWhiteBlackGLEffect(final int textureId, final FloatBuffer cubeBuffer,
                                                final FloatBuffer textureBuffer, float[] texMatrix) {
//        WhiteBlackGLEffect.getInstance().draw(textureId, cubeBuffer, textureBuffer, texMatrix);
    }


    public static float calculateTimePercentage(BaseEffect effect, int musicTime) {
        float totalDuration = effect.endTime - effect.startTime;
        float timePercentage = (musicTime - effect.startTime) / totalDuration;
        return timePercentage;
    }
}
