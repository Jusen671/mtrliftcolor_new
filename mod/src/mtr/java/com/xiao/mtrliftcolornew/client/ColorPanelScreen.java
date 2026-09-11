package com.xiao.mtrliftcolornew.client;

import com.xiao.mtrliftcolornew.config.LiftColorConfig;
import com.xiao.mtrliftcolornew.config.LiftColorManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.mtr.core.data.Lift;

import java.util.function.Consumer;

/**
 * MC 原版风格的电梯调色面板（圆角 + 半透明，顶部两个可切换的分区页签）。
 *
 * <p>顶部两个页签（点击切换）：</p>
 * <ul>
 *   <li><b>电梯染色</b>：hex 颜色输入、RGB 三滑块、预设色块、颜色预览。</li>
 *   <li><b>电梯箭头配置</b>：箭头大小、横向偏移、纵向偏移三根滑块。</li>
 * </ul>
 *
 * <p>底部两个按钮：保存并应用 / 取消。</p>
 *
 * <p>界面按当前屏幕尺寸自适应，窄小屏幕（如手机）会自动缩小并压缩间距。</p>
 */
public class ColorPanelScreen extends Screen {

    /** 面板最大尺寸（像素），也就是普通屏幕下的大小。 */
    private static final int MAX_W = 340;
    private static final int MAX_H = 400;
    /** 面板与屏幕边缘之间至少留多少像素。 */
    private static final int SCREEN_MARGIN = 12;

    /** 面板底：更透明的暗色（alpha 0x60，约 38%）。 */
    private static final int BG_COLOR = 0x60000000;
    /** 面板外圈：一条很淡的白色描边，让面板轮廓更干净。 */
    private static final int BORDER_COLOR = 0x40FFFFFF;
    /** 顶部页签配色。 */
    private static final int TAB_ACTIVE_BG = 0x3AFFFFFF;
    private static final int TAB_INACTIVE_BG = 0x1EFFFFFF;
    private static final int TAB_ACTIVE_TEXT = 0xFFFFFFFF;
    private static final int TAB_INACTIVE_TEXT = 0xFFAAAAAA;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int SUBTLE_TEXT = 0xFFBBBBBB;

    private final Lift lift;
    private int currentColor;
    private float arrowScale;
    private float arrowOffsetX;
    private float arrowOffsetY;

    // 当前分区：0 = 电梯染色，1 = 电梯箭头配置。
    private int activeTab = 0;

    // 自适应布局：在 init() 里按屏幕计算好，render/mouseClicked 直接引用。
    private int panelLeft;
    private int panelTop;
    private int panelW;
    private int panelH;
    private int contentLeft;
    private int contentW;
    private int contentRight;
    private int pad;
    private int cornerRadius;

    // 顶部页签。
    private int tabY;
    private int tabH;
    private int tabW;
    private int tabGap;
    private int tab0X;
    private int tab1X;

    // 内容区。
    private int contentTop;
    private int contentBottom;
    private int liftLineY;
    private int btnY;

    // 染色页各行的 Y 坐标。
    private int hexY;
    private int rgbY;
    private int presetLabelY;
    private int presetY;
    private int presetBlockW;
    private int presetBlockH;

    // 箭头页第一行 Y 坐标。
    private int arrowY;

    private TextFieldWidget hexField;
    private IntSlider rSlider;
    private IntSlider gSlider;
    private IntSlider bSlider;
    private SliderWidget scaleSlider;
    private SliderWidget offXSlider;
    private SliderWidget offYSlider;

    private boolean updating;

    private static final int[][] PRESETS = {
            {0xFF0000}, {0xFF8C00}, {0xFFFF00}, {0x00FF00}, {0x00FFFF},
            {0x0000FF}, {0x8000FF}, {0xFFFFFF}, {0x000000}, {0xFF00FF}
    };

    public ColorPanelScreen(Lift lift) {
        super(Text.literal("电梯调色面板"));
        this.lift = lift;
        final LiftColorConfig cfg = LiftColorManager.get(lift, currentWorld());
        this.currentColor = cfg.displayColor & 0xFFFFFF; // 去掉 alpha
        this.arrowScale = cfg.arrowScale;
        this.arrowOffsetX = cfg.arrowOffsetX;
        this.arrowOffsetY = cfg.arrowOffsetY;
    }

    private static World currentWorld() {
        return MinecraftClient.getInstance().world;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void init() {
        computeLayout();

        // ---------- 染色页：hex 输入 ----------
        final int hexW = Math.max(70, Math.min(130, this.contentW - 56));
        this.hexField = new TextFieldWidget(this.textRenderer, this.contentLeft, this.hexY, hexW, 18, Text.literal("颜色"));
        this.hexField.setText(Integer.toHexString(this.currentColor & 0xFFFFFF));
        this.hexField.setMaxLength(6);
        this.hexField.setChangedListener(text -> {
            if (this.updating) {
                return;
            }
            try {
                int parsed = Integer.parseInt(text, 16);
                this.currentColor = parsed & 0xFFFFFF;
                this.updating = true;
                this.rSlider.setSliderValue((parsed >> 16 & 0xFF) / 255.0);
                this.gSlider.setSliderValue((parsed >> 8 & 0xFF) / 255.0);
                this.bSlider.setSliderValue((parsed & 0xFF) / 255.0);
                this.updating = false;
            } catch (NumberFormatException ignored) {
                // 无效的 hex 暂不处理
            }
        });
        this.addDrawableChild(this.hexField);

        // ---------- 染色页：RGB 滑块 ----------
        final int sliderLabelW = 16;
        final int sliderW = Math.max(40, this.contentW - sliderLabelW - 6);
        final int sliderX = this.contentLeft + sliderLabelW;
        final int sliderStep = 18;

        this.rSlider = new IntSlider(sliderX, this.rgbY, sliderW, 16, 0, 255, (this.currentColor >> 16 & 0xFF), v -> setColorChannel(0, v));
        this.gSlider = new IntSlider(sliderX, this.rgbY + sliderStep, sliderW, 16, 0, 255, (this.currentColor >> 8 & 0xFF), v -> setColorChannel(1, v));
        this.bSlider = new IntSlider(sliderX, this.rgbY + sliderStep * 2, sliderW, 16, 0, 255, (this.currentColor & 0xFF), v -> setColorChannel(2, v));
        this.addDrawableChild(this.rSlider);
        this.addDrawableChild(this.gSlider);
        this.addDrawableChild(this.bSlider);

        // ---------- 箭头页：箭头缩放与偏移 ----------
        final int arrowStep = 18;
        this.scaleSlider = new SliderWidget(this.contentLeft, this.arrowY, this.contentW, 16, Text.literal("箭头大小"), norm(0.5, 2.0, this.arrowScale)) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("箭头大小: " + String.format("%.2f", range(0.5, 2.0, this.value))));
            }

            @Override
            protected void applyValue() {
                ColorPanelScreen.this.arrowScale = range(0.5f, 2.0f, this.value);
            }
        };
        this.addDrawableChild(this.scaleSlider);

        this.offXSlider = new SliderWidget(this.contentLeft, this.arrowY + arrowStep, this.contentW, 16, Text.literal("横向偏移"), norm(-0.3, 0.3, this.arrowOffsetX)) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("横向偏移: " + String.format("%.2f", range(-0.3, 0.3, this.value))));
            }

            @Override
            protected void applyValue() {
                ColorPanelScreen.this.arrowOffsetX = range(-0.3f, 0.3f, this.value);
            }
        };
        this.addDrawableChild(this.offXSlider);

        this.offYSlider = new SliderWidget(this.contentLeft, this.arrowY + arrowStep * 2, this.contentW, 16, Text.literal("纵向偏移"), norm(-0.3, 0.3, this.arrowOffsetY)) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("纵向偏移: " + String.format("%.2f", range(-0.3, 0.3, this.value))));
            }

            @Override
            protected void applyValue() {
                ColorPanelScreen.this.arrowOffsetY = range(-0.3f, 0.3f, this.value);
            }
        };
        this.addDrawableChild(this.offYSlider);

        // ---------- 保存 / 取消 ----------
        final int btnGap = 8;
        final int btnW = Math.max(60, (this.contentW - btnGap) / 2);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("保存并应用"), b -> saveAndClose())
                .dimensions(this.contentLeft, this.btnY, btnW, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("取消"), b -> this.close())
                .dimensions(this.contentLeft + btnW + btnGap, this.btnY, btnW, 20).build());

        // 按当前分区显示/隐藏控件，并取消隐藏输入框的焦点。
        this.activeTab = 0;
        applyTabVisibility();
    }

    /** 依据当前屏幕尺寸计算面板、页签与内容的布局。 */
    private void computeLayout() {
        final int availW = this.width - SCREEN_MARGIN * 2;
        final int availH = this.height - SCREEN_MARGIN * 2;
        this.panelW = clamp(availW, 170, MAX_W);
        this.panelH = clamp(availH, 232, MAX_H);
        this.panelLeft = (this.width - this.panelW) / 2;
        this.panelTop = (this.height - this.panelH) / 2;

        this.pad = clamp(this.panelW / 26, 10, 16);
        this.contentLeft = this.panelLeft + this.pad;
        this.contentRight = this.panelLeft + this.panelW - this.pad;
        this.contentW = this.contentRight - this.contentLeft;
        this.cornerRadius = clamp(this.panelW / 16, 8, 14);

        // 顶部两页签
        this.tabY = this.panelTop + this.pad;
        this.tabH = 20;
        this.tabGap = 6;
        this.tabW = (this.contentW - this.tabGap) / 2;
        this.tab0X = this.contentLeft;
        this.tab1X = this.contentLeft + this.tabW + this.tabGap;

        // 底部按钮行
        final int btnH = 20;
        this.btnY = this.panelTop + this.panelH - this.pad - btnH;

        // 内容区（页签之下、按钮之上的区域）
        this.contentTop = this.tabY + this.tabH + 9;
        this.contentBottom = this.btnY - 12;
        this.liftLineY = this.contentTop;
        final int contentStart = this.contentTop + 16;

        // 染色页各段落：按可用高度压缩行间距，小屏也能容纳。
        final int colorFixed = 18 + 54 + 14 + 42; // hex、RGB、预设标签、两行预设色块
        final int colorGap = clamp((this.contentBottom - contentStart - colorFixed) / 3, 4, 10);
        this.hexY = contentStart;
        this.rgbY = this.hexY + 18 + colorGap;
        this.presetLabelY = this.rgbY + 54 + colorGap;
        this.presetY = this.presetLabelY + 14 + colorGap;

        // 预设色块：5 列 2 行，块间留 4/6 像素间距。
        final int blockGapX = 4;
        final int blockGapY = 6;
        this.presetBlockW = Math.max(24, (this.contentW - blockGapX * 4) / 5);
        this.presetBlockH = 18;

        // 箭头页第一行（内容足够放三根 18 高的滑块）。
        this.arrowY = contentStart;
    }

    /** 按 activeTab 显示/隐藏各分区控件，并取消隐藏输入框的焦点。 */
    private void applyTabVisibility() {
        final boolean colorTab = this.activeTab == 0;
        setWidgetVisible(this.hexField, colorTab);
        setWidgetVisible(this.rSlider, colorTab);
        setWidgetVisible(this.gSlider, colorTab);
        setWidgetVisible(this.bSlider, colorTab);
        setWidgetVisible(this.scaleSlider, !colorTab);
        setWidgetVisible(this.offXSlider, !colorTab);
        setWidgetVisible(this.offYSlider, !colorTab);

        if (!colorTab) {
            this.hexField.setFocused(false);
        }
    }

    private static void setWidgetVisible(ClickableWidget widget, boolean visible) {
        if (widget != null) {
            widget.visible = visible; // 隐藏的同时禁用点击，避免误触
            widget.active = visible;
        }
    }

    private void setColorChannel(int channel, int value) {
        int r = (this.currentColor >> 16 & 0xFF);
        int g = (this.currentColor >> 8 & 0xFF);
        int b = (this.currentColor & 0xFF);
        if (channel == 0) {
            r = value;
        } else if (channel == 1) {
            g = value;
        } else {
            b = value;
        }
        this.currentColor = (r << 16) | (g << 8) | b;
        this.updating = true;
        this.hexField.setText(Integer.toHexString(this.currentColor));
        this.updating = false;
    }

    private void setColor(int rgb) {
        this.currentColor = rgb & 0xFFFFFF;
        this.updating = true;
        this.rSlider.setSliderValue((rgb >> 16 & 0xFF) / 255.0);
        this.gSlider.setSliderValue((rgb >> 8 & 0xFF) / 255.0);
        this.bSlider.setSliderValue((rgb & 0xFF) / 255.0);
        this.hexField.setText(Integer.toHexString(rgb));
        this.updating = false;
    }

    private void saveAndClose() {
        final LiftColorConfig cfg = new LiftColorConfig(
                0xFF000000 | this.currentColor,
                this.arrowScale,
                this.arrowOffsetX,
                this.arrowOffsetY,
                this.lift.getName() == null ? "" : this.lift.getName());
        LiftColorManager.setAndSave(this.lift, currentWorld(), cfg);
        this.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        // 圆角半透明面板：先描边（外圈），再填底色（内圈），形成一圈淡描边。
        fillRoundedRect(context, this.panelLeft, this.panelTop, this.panelW, this.panelH, this.cornerRadius, BORDER_COLOR);
        fillRoundedRect(context, this.panelLeft + 1, this.panelTop + 1, this.panelW - 2, this.panelH - 2, this.cornerRadius - 1, BG_COLOR);

        // 顶部两个页签
        drawTab(context, 0, "电梯染色", this.activeTab == 0);
        drawTab(context, 1, "电梯箭头配置", this.activeTab == 1);

        // 电梯名（左上，较淡）
        context.drawTextWithShadow(this.textRenderer, "电梯: " + (this.lift.getName() == null ? "?" : this.lift.getName()), this.contentLeft, this.liftLineY, SUBTLE_TEXT);

        // 当前分区内容
        if (this.activeTab == 0) {
            renderColorTab(context);
        } else {
            renderArrowTab(context);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderColorTab(DrawContext context) {
        // 颜色预览（hex 输入框右侧）
        final int previewW = 46;
        final int previewX = this.contentRight - previewW;
        context.fill(previewX, this.hexY, previewX + previewW, this.hexY + 18, 0xFF000000 | this.currentColor);
        context.drawBorder(previewX, this.hexY, previewW, 18, 0x66FFFFFF);

        // RGB 滑块前的通道标签
        context.drawTextWithShadow(this.textRenderer, "R", this.contentLeft, this.rgbY + 4, SUBTLE_TEXT);
        context.drawTextWithShadow(this.textRenderer, "G", this.contentLeft, this.rgbY + 18 + 4, SUBTLE_TEXT);
        context.drawTextWithShadow(this.textRenderer, "B", this.contentLeft, this.rgbY + 18 * 2 + 4, SUBTLE_TEXT);

        // 预设颜色色块
        context.drawTextWithShadow(this.textRenderer, "预设颜色:", this.contentLeft, this.presetLabelY, SUBTLE_TEXT);
        for (int i = 0; i < PRESETS.length; i++) {
            int[] r = presetRect(i);
            context.fill(r[0], r[1], r[0] + this.presetBlockW, r[1] + this.presetBlockH, 0xFF000000 | PRESETS[i][0]);
            context.drawBorder(r[0], r[1], this.presetBlockW, this.presetBlockH, 0x5AFFFFFF);
        }
    }

    private void renderArrowTab(DrawContext context) {
        context.drawTextWithShadow(this.textRenderer, "箭头显示设置", this.contentLeft, this.contentTop, SUBTLE_TEXT);
    }

    /** 画一个顶部页签（圆角矩形 + 居中文字）。 */
    private void drawTab(DrawContext context, int index, String label, boolean active) {
        final int x = index == 0 ? this.tab0X : this.tab1X;
        fillRoundedRect(context, x, this.tabY, this.tabW, this.tabH, 6, active ? TAB_ACTIVE_BG : TAB_INACTIVE_BG);
        if (active) {
            // 激活页签底部的高亮条
            context.fill(x + 4, this.tabY + this.tabH - 2, x + this.tabW - 4, this.tabY + this.tabH - 1, 0xAAFFFFFF);
        }
        context.drawCenteredTextWithShadow(this.textRenderer, label, x + this.tabW / 2, this.tabY + (this.tabH - 8) / 2, active ? TAB_ACTIVE_TEXT : TAB_INACTIVE_TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 顶部页签
            if (mouseX >= this.tab0X && mouseX < this.tab0X + this.tabW && mouseY >= this.tabY && mouseY < this.tabY + this.tabH) {
                switchTab(0);
                return true;
            }
            if (mouseX >= this.tab1X && mouseX < this.tab1X + this.tabW && mouseY >= this.tabY && mouseY < this.tabY + this.tabH) {
                switchTab(1);
                return true;
            }
            // 染色页预设色块
            if (this.activeTab == 0) {
                for (int i = 0; i < PRESETS.length; i++) {
                    int[] r = presetRect(i);
                    if (mouseX >= r[0] && mouseX < r[0] + this.presetBlockW && mouseY >= r[1] && mouseY < r[1] + this.presetBlockH) {
                        setColor(PRESETS[i][0]);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void switchTab(int tab) {
        if (this.activeTab == tab) {
            return;
        }
        this.activeTab = tab;
        applyTabVisibility();
    }

    private int[] presetRect(int index) {
        final int blockGapX = 4;
        final int blockGapY = 6;
        int x = this.contentLeft + (index % 5) * (this.presetBlockW + blockGapX);
        int y = this.presetY + (index / 5) * (this.presetBlockH + blockGapY);
        return new int[]{x, y};
    }

    // ---------- 圆角矩形绘制（逐像素抗锯齿）----------

    /**
     * 画一个带圆角、边缘抗锯齿的矩形。
     *
     * @param color ARGB 颜色（带 alpha，可半透明）
     */
    private void fillRoundedRect(DrawContext context, int x, int y, int w, int h, int radius, int color) {
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
                final double coverage = clampd(0.5 - dist, 0.0, 1.0);
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

    private static double norm(double min, double max, double value) {
        return (value - min) / (max - min);
    }

    private static float range(float min, float max, double value) {
        return (float) (min + (max - min) * value);
    }

    private static double range(double min, double max, double value) {
        return min + (max - min) * value;
    }

    private static double clampd(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * RGB 整数滑块：把 0..1 映射到 min..max 的整数。
     */
    private static class IntSlider extends SliderWidget {
        private final int min;
        private final int max;
        private final Consumer<Integer> setter;

        IntSlider(int x, int y, int width, int height, int min, int max, int value, Consumer<Integer> setter) {
            super(x, y, width, height, Text.empty(), (value - min) / (double) (max - min));
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal("" + Math.round(range(min, max, this.value))));
        }

        @Override
        protected void applyValue() {
            this.setter.accept((int) Math.round(range(min, max, this.value)));
        }

        /** 供面板设置滑块位置（SliderWidget 的 value 字段是 protected）。 */
        public void setSliderValue(double normalized) {
            this.value = normalized;
            this.updateMessage();
        }
    }
}
