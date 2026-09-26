package com.xiao.mtrliftcolornew.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * 界面共享绘制：圆角矩形（逐像素抗锯齿）与统一配色，
 * 让调色面板、自动化界面、积木编辑器看起来是同一套 UI。
 */
public final class PanelUi {

    /** 面板底：半透明暗色。 */
    public static final int BG_COLOR = 0x60000000;
    /** 面板外圈淡描边。 */
    public static final int BORDER_COLOR = 0x40FFFFFF;
    /** 页签 / 行按钮配色。 */
    public static final int TAB_ACTIVE_BG = 0x3AFFFFFF;
    public static final int TAB_HOVER_BG = 0x2CFFFFFF;
    public static final int TAB_INACTIVE_BG = 0x1EFFFFFF;
    public static final int TAB_ACTIVE_TEXT = 0xFFFFFFFF;
    public static final int TAB_INACTIVE_TEXT = 0xFFAAAAAA;
    public static final int SUBTLE_TEXT = 0xFFBBBBBB;
    public static final int STATUS_OK = 0xFF90EE90;
    public static final int STATUS_FAIL = 0xFFFF9090;

    private PanelUi() {
    }

    /** 画一个带圆角、边缘抗锯齿的矩形；color 为 ARGB（可半透明）。 */
    public static void fillRoundedRect(DrawContext context, int x, int y, int w, int h, int radius, int color) {
        final int alpha = (color >>> 24) & 0xFF;
        if (alpha == 0 || w <= 0 || h <= 0) {
            return;
        }
        final int rr = (color >>> 16) & 0xFF;
        final int gg = (color >>> 8) & 0xFF;
        final int bb = color & 0xFF;
        final float rad = Math.min(radius, Math.min(w, h) / 2f);
        if (rad < 1.0f) {
            context.fill(x, y, x + w, y + h, color);
            return;
        }
        final double cx = x + w / 2.0;
        final double cy = y + h / 2.0;
        final double halfW = w / 2.0;
        final double halfH = h / 2.0;

        for (int py = y; py < y + h; py++) {
            final double dyAbs = Math.abs(py + 0.5 - cy);
            final double dy = Math.max(dyAbs - (halfH - rad), 0.0);
            int runStart = -1;
            int runAlpha = 0;
            for (int px = x; px < x + w; px++) {
                final double dxAbs = Math.abs(px + 0.5 - cx);
                final double dx = Math.max(dxAbs - (halfW - rad), 0.0);
                final double dist = Math.sqrt(dx * dx + dy * dy) - rad;
                final double coverage = clamp(0.5 - dist, 0.0, 1.0);
                if (coverage <= 0.0) {
                    if (runStart >= 0) {
                        context.fill(runStart, py, px, py + 1, (runAlpha << 24) | (rr << 16) | (gg << 8) | bb);
                        runStart = -1;
                    }
                    continue;
                }
                final int a = Math.max(1, (int) Math.round(alpha * coverage));
                if (runStart < 0) {
                    runStart = px;
                    runAlpha = a;
                } else if (a != runAlpha) {
                    context.fill(runStart, py, px, py + 1, (runAlpha << 24) | (rr << 16) | (gg << 8) | bb);
                    runStart = px;
                    runAlpha = a;
                }
            }
            if (runStart >= 0) {
                context.fill(runStart, py, x + w, py + 1, (runAlpha << 24) | (rr << 16) | (gg << 8) | bb);
            }
        }
    }

    /** 画一条圆角“行按钮”：圆角矩形 + 居中文字（悬停时由调用方换背景色）。 */
    public static void drawRow(DrawContext context, TextRenderer textRenderer, int x, int y, int w, int h, String label, int bg, int textColor) {
        fillRoundedRect(context, x, y, w, h, Math.min(8, h / 2), bg);
        context.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + (h - 8) / 2, textColor);
    }

    /** 命中检测：点是否落在矩形内。 */
    public static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }
}
