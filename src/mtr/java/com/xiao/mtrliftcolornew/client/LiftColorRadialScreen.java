package com.xiao.mtrliftcolornew.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.mtr.core.data.Lift;
import org.mtr.mapping.holder.BlockPos;

/**
 * 圆形冲突选择页：玩家右键 MTR 重置器时弹出。
 * 上半圆 → 打开 MTR 电梯重置编辑器；下半圆 → 打开我们的电梯调色面板。
 *
 * <p>这一版按参考图改成「大范围、更透明」的圆盘：</p>
 * <ol>
 *   <li><b>大范围圆盘</b>：半径随屏幕尺寸放大（最大约屏高的 1/3），更像截图里的整块圆盘。</li>
 *   <li><b>更透明柔和</b>：圆盘本体、两半与描边都压低了 alpha，背景透出，仿若磨砂玻璃。
 *       边缘做抗锯齿，加一圈淡描边。</li>
 *   <li><b>中心小圆</b>：盘心加一个小圆环作为视觉锚点（贴近参考图中央的圆形元素）。</li>
 * </ol>
 */
public class LiftColorRadialScreen extends Screen {

    private static final int MAX_RADIUS = 190;
    private static final int MIN_RADIUS = 56;

    /** 圆盘外圈：很淡的一圈白描边，让圆盘轮廓更干净。 */
    private static final int COLOR_RING = 0x40FFFFFF;
    /** 圆盘本体：很透明的亮色，磨砂玻璃质感。 */
    private static final int COLOR_DISC = 0x1AFFFFFF;
    /** 上半圆（MTR 编辑器）：很柔和的一层蓝。 */
    private static final int COLOR_MTR = 0x24337AAC;
    /** 下半圆（调色面板）：很柔和的一层橙。 */
    private static final int COLOR_COLOR = 0x24CC7733;
    /** 悬停高亮：盖一层很淡的白色。 */
    private static final int COLOR_HOVER = 0x2EFFFFFF;
    /** 分隔线。 */
    private static final int COLOR_DIVIDER = 0x30FFFFFF;
    /** 中心小圆：很透明的暗底 + 一圈淡描边。 */
    private static final int CENTER_FILL = 0x66000000;
    private static final int CENTER_RIM = 0x66FFFFFF;

    private final BlockPos blockPos;
    private int centerX;
    private int centerY;
    private int radius;

    public LiftColorRadialScreen(BlockPos blockPos) {
        super(Text.literal("MTR电梯调色选择"));
        this.blockPos = blockPos;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    /** 根据当前屏幕尺寸决定圆半径：比旧版更大，最贴近参考图的整块圆盘。 */
    private int radius() {
        final double base = Math.min(this.width, this.height) * 0.32;
        final int r = (int) Math.min(MAX_RADIUS, base);
        return Math.max(r, MIN_RADIUS);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        this.centerX = this.width / 2;
        this.centerY = this.height / 2;
        this.radius = this.radius();

        // 标题提示
        final int headerY = Math.max(4, this.centerY - this.radius - 26);
        context.drawCenteredTextWithShadow(this.textRenderer, "选择要打开的界面", this.centerX, headerY, 0xFFE8E8E8);

        // 外圈淡描边（比圆盘大一点，形成柔和光边）
        fillCircleSmooth(context, this.centerX, this.centerY, this.radius + 2, COLOR_RING, 1.0f, true, true);

        // 圆盘本体（很透明）
        fillCircleSmooth(context, this.centerX, this.centerY, this.radius, COLOR_DISC, 1.0f, true, true);

        // 两个半圆
        fillCircleSmooth(context, this.centerX, this.centerY, this.radius, COLOR_MTR, 1.0f, true, false);
        fillCircleSmooth(context, this.centerX, this.centerY, this.radius, COLOR_COLOR, 1.0f, false, true);

        // 悬停高亮
        final boolean bottomHover = isInsideCircle(mouseX, mouseY) && mouseY >= this.centerY;
        if (bottomHover) {
            fillCircleSmooth(context, this.centerX, this.centerY, this.radius, COLOR_HOVER, 1.0f, false, true);
        } else if (isInsideCircle(mouseX, mouseY)) {
            fillCircleSmooth(context, this.centerX, this.centerY, this.radius, COLOR_HOVER, 1.0f, true, false);
        }

        // 分隔线
        context.fill(this.centerX - this.radius, this.centerY, this.centerX + this.radius, this.centerY + 1, COLOR_DIVIDER);

        // 盘心小圆（视觉锚点）：一圈淡描边 + 暗色盘心。
        final int smallRadius = Math.max(8, (int) (this.radius * 0.18f));
        fillCircleSmooth(context, this.centerX, this.centerY, smallRadius, CENTER_RIM, 1.0f, true, true);
        fillCircleSmooth(context, this.centerX, this.centerY, smallRadius - 1.5f, CENTER_FILL, 1.0f, true, true);

        // 上半圆标签
        context.drawCenteredTextWithShadow(this.textRenderer, "MTR", this.centerX, this.centerY - (int) (this.radius * 0.42f), 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "电梯重置编辑器", this.centerX, this.centerY - (int) (this.radius * 0.26f), 0xFFDDDDDD);

        // 下半圆标签
        context.drawCenteredTextWithShadow(this.textRenderer, "MTRliftcolor", this.centerX, this.centerY + (int) (this.radius * 0.26f), 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "电梯调色面板", this.centerX, this.centerY + (int) (this.radius * 0.42f), 0xFFDDDDDD);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        final int r = this.radius();
        final int cx = this.width / 2;
        final int cy = this.height / 2;
        final double dx = mouseX - cx;
        final double dy = mouseY - cy;
        if (dx * dx + dy * dy <= (double) (r * r)) {
            final Lift lift = MTRLiftColorClient.findLift(this.blockPos);
            if (dy < 0) {
                // 上半圆：打开 MTR 编辑器
                MTRLiftColorClient.openMtrEditor(this.blockPos);
            } else {
                // 下半圆：电梯调色面板
                if (lift != null) {
                    MTRLiftColorClient.openColorPanel(lift);
                } else {
                    // 暂时没匹配到电梯：不静默关屏，让玩家重试或按 Esc 退出
                    return true;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isInsideCircle(double mouseX, double mouseY) {
        final double dx = mouseX - this.centerX;
        final double dy = mouseY - this.centerY;
        return dx * dx + dy * dy <= (double) (this.radius * this.radius);
    }

    // ---------- 平滑圆形绘制 ----------

    /**
     * 画一个（或半个）圆，边缘做抗锯齿。
     *
     * @param top    是否画上半圆
     * @param bottom 是否画下半圆
     */
    private void fillCircleSmooth(DrawContext context, int cx, int cy, float r, int color, float alphaMult, boolean top, boolean bottom) {
        final int baseA = clamp((int) (((color >>> 24) & 0xFF) * alphaMult), 0, 255);
        if (baseA <= 0) {
            return;
        }
        final int rr = (color >>> 16) & 0xFF;
        final int gg = (color >>> 8) & 0xFF;
        final int bb = color & 0xFF;

        final int yMin = top ? (int) Math.ceil(cy - r) : (int) Math.ceil(cy);
        final int yMax = bottom ? (int) Math.floor(cy + r) : (int) Math.floor(cy);

        for (int y = yMin; y <= yMax; y++) {
            final double dy = y + 0.5 - cy;
            final double halfSpan = Math.sqrt(Math.max(0, r * r - dy * dy));
            final int xStart = (int) Math.ceil(cx - halfSpan);
            final int xEnd = (int) Math.floor(cx + halfSpan);

            int runStart = -1;
            int runAlpha = 0;
            for (int x = xStart; x <= xEnd; x++) {
                final double dx = x + 0.5 - cx;
                final double dist = Math.sqrt(dx * dx + dy * dy);
                final double coverage = clampd(r + 0.5 - dist, 0.0, 1.0);
                if (coverage <= 0.0) {
                    if (runStart >= 0) {
                        context.fill(runStart, y, x, y + 1, (runAlpha << 24) | (rr << 16) | (gg << 8) | bb);
                        runStart = -1;
                    }
                    continue;
                }
                final int alpha = Math.max(1, (int) Math.round(baseA * coverage));
                if (runStart < 0) {
                    runStart = x;
                    runAlpha = alpha;
                } else if (alpha != runAlpha) {
                    context.fill(runStart, y, x, y + 1, (runAlpha << 24) | (rr << 16) | (gg << 8) | bb);
                    runStart = x;
                    runAlpha = alpha;
                }
            }
            if (runStart >= 0) {
                context.fill(runStart, y, xEnd + 1, y + 1, (runAlpha << 24) | (rr << 16) | (gg << 8) | bb);
            }
        }
    }

    private static double clampd(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
