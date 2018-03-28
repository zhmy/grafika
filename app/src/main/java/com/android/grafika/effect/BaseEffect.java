package com.android.grafika.effect;

import org.json.JSONObject;

import java.io.Serializable;

/**
 * Created by wanginbeijing on 2017/8/7.
 */

public class BaseEffect implements Cloneable,Serializable {
    public int baseType;
    public EffectType effectType;
    public int startTime;
    public int endTime;
    public int duration;
    public double startPercentage;
    public double endPercentage;

    public static final int TYPE_MAGIC = 1;
    public static final int TYPE_TIME = 2;
    public static final int TYPE_PARTICLE = 3;

    public static <T extends BaseEffect> T praseJson(JSONObject object, Class<T> clazz) {
        try {
            T effect = clazz.newInstance();
            effect.effectType = EffectType.findType(object.optString("type"));
            effect.duration = (int) (object.optDouble("duration") * 1000);
            return effect;
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return null;
    }


}
