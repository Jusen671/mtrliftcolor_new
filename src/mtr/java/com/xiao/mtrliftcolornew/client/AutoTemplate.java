package com.xiao.mtrliftcolornew.client;

import com.xiao.mtrliftcolornew.config.AutoChain;

/**
 * 自动化积木编辑器的模板（本次游戏会话内共享）。
 *
 * <p>上行链、下行链各一条；「应用到电梯」时按作用范围把两条链复制下发给每台电梯，
 * 每台电梯得到自己的一份 auto/&lt;hex&gt;.json。</p>
 */
public final class AutoTemplate {

    /** 上行时生效的模板链。 */
    public static final AutoChain UP = new AutoChain();
    /** 下行时生效的模板链。 */
    public static final AutoChain DOWN = new AutoChain();

    /**
     * 积木盒里每种数值积木的当前数值：拖进链里时带上的初始值，
     * 本次会话内记住上一次的调节结果，省得每次都从头调。
     */
    private static final float[] PALETTE_VALUES = new float[AutoChain.TYPE_MAX + 1];

    static {
        for (final int type : AutoChain.TYPES) {
            PALETTE_VALUES[type] = AutoChain.defaultValue(type);
        }
    }

    private AutoTemplate() {
    }

    /** 当前编辑模式对应的链（0=上行时，1=下行时）。 */
    public static AutoChain chain(int mode) {
        return mode == 0 ? UP : DOWN;
    }

    /** 积木盒里某类型积木的当前数值。 */
    public static float paletteValue(int type) {
        return type >= 0 && type < PALETTE_VALUES.length ? PALETTE_VALUES[type] : AutoChain.defaultValue(type);
    }

    /** 记住积木盒里某类型积木的当前数值（拖动或输入后调用）。 */
    public static void setPaletteValue(int type, float value) {
        if (type >= 0 && type < PALETTE_VALUES.length) {
            PALETTE_VALUES[type] = AutoChain.clampValue(type, value);
        }
    }

    /** 模板摘要，例如 "上行 2 块 · 下行 1 块"。 */
    public static String summary() {
        return "上行 " + UP.size() + " 块 · 下行 " + DOWN.size() + " 块";
    }
}
