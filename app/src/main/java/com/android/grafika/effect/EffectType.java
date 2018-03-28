package com.android.grafika.effect;


/**
 * Created by wanginbeijing on 2017/8/7.
 * 魔法类型
 */

public enum EffectType {

    NO_MATCH("no_match"),

    /** 无 */
    NO("no"),
    /** 左右平移 */
    HORIZONTAL_TRANSLATION("horizontal_translation"),
    /** 上下平移 */
    VERTICAL_TRANSLATION("vertical_translation"),
    /** 放大 */
    SCALE_BIG("scale_big"),
    /** 缩小 */
    SCALE_SMALL("scale_small"),
    /** 旋转 */
    ROTATE("rotate"),
    /** 灵魂出窍 */
    SOUL_OUT("soul_out"),
    /** 黑白魔法 */
    WHITE_BLACK("white_black"),

    //时间特效

    /** 反复 */
    TIME_REPEAT("time_repeat"),
    /** 倒放 */
    TIME_REVERSE("time_reverse"),

    PARTICLE_HEART("particle_heart"),
    PARTICLE_FLAME("particle_flame"),
    PARTICLE_PINKSTAR("particle_pinkstar"),
    PARTICLE_MAGICSTICK("particle_magicstick"),
    PARTICLE_FLASH("particle_flash"),
    PARTICLE_BOMB("particle_bomb"),
    PARTICLE_SAKULA("particle_sakula"),
    PARTICLE_SNOWFLAKE("particle_snowflake"),
    PARTICLE_SNOW("particle_snow"),
    PARTICLE_BANANA("particle_banana");


    private String type;

    EffectType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public static EffectType findType(String type) {
        for (EffectType m : EffectType.values()) {
            if (m.getType().equals(type)) {
                return m;
            }
        }
        return NO_MATCH;
    }

}
