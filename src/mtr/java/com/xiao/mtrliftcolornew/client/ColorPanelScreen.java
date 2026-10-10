package com.xiao.mtrliftcolornew.client;

import com.xiao.mtrliftcolornew.config.LiftColorConfig;
import com.xiao.mtrliftcolornew.config.LiftColorManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.mtr.core.data.Lift;

/**
 * MC 原版风格的电梯调色面板（圆角 + 半透明，顶部两个可切换的分区页签 + 「自动化」按钮）。
 *
 * <p>顶部两页签（点击切换）、一个自动化按钮：</p>
 * <ul>
 *   <li><b>电梯染色</b>：hex 颜色输入、RGB 三个数值框、预设色块、颜色预览。</li>
 *   <li><b>电梯显示设置</b>：箭头与楼层文字的大小 / 偏移。两者本来就是轿厢里同一块屏幕上的东西，
 *       所以放在一起调。</li>
 * </ul>
 *
 * <p>所有数值都用 {@link NumberField}：手机上点开可以直接打字，也能按住左右拖动改值，
 * 框内两侧还有 − / + 微调，取代原来的滑块。</p>
 *
 * <p>底部两个按钮：保存并应用 / 取消。界面按当前屏幕尺寸自适应，窄小屏幕会自动缩小并压缩间距。</p>
 */
public class ColorPanelScreen extends Screen {

    /** 面板最大尺寸（像素），也就是普通屏幕下的大小。 */
    private static final int MAX_W = 340;
    private static final int MAX_H = 420;
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
    private static final int SUBTLE_TEXT = 0xFFBBBBBB;

    private static final int[][] PRESETS = {
            {0xFF0000}, {0xFF8C00}, {0xFFFF00}, {0x00FF00}, {0x00FFFF},
            {0x0000FF}, {0x8000FF}, {0xFFFFFF}, {0x000000}, {0xFF00FF}
    };

    private final Lift lift;
    private int currentColor;
    private float arrowScale;
    private float arrowOffsetX;
    private float arrowOffsetY;
    private float textScale;
    private float textOffsetX;
    private float textOffsetY;
    /** 保存时原样带回自动化设置过的箭头方向，避免面板保存把方向重置回"自动"。 */
    private int arrowDirection = LiftColorConfig.DIRECTION_AUTO;

    // 当前分区：0 = 电梯染色，1 = 电梯显示设置。
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
    private int autoBtnX;

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

    // 显示设置页第一行 Y 坐标与行高。
    private int displayY;
    private int rowH;
    private int rowStep;
    private int labelW;
    private int fieldX;
    private int fieldW;

    private TextFieldWidget hexField;
    private NumberField rField;
    private NumberField gField;
    private NumberField bField;
    private NumberField arrowScaleField;
    private NumberField arrowOffXField;
    private NumberField arrowOffYField;
    private NumberField textScaleField;
    private NumberField textOffXField;
    private NumberField textOffYField;

    private boolean updating;

    public ColorPanelScreen(Lift lift) {
        super(Text.literal("电梯调色面板"));
        this.lift = lift;
        final LiftColorConfig cfg = LiftColorManager.get(lift, currentWorld());
        this.currentColor = cfg.displayColor & 0xFFFFFF; // 去掉 alpha
        this.arrowScale = cfg.arrowScale;
        this.arrowOffsetX = cfg.arrowOffsetX;
        this.arrowOffsetY = cfg.arrowOffsetY;
        this.textScale = cfg.textScale;
        this.textOffsetX = cfg.textOffsetX;
        this.textOffsetY = cfg.textOffsetY;
        this.arrowDirection = cfg.arrowDirection;
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
        super.init();  // 清掉上一次的孩子列表（窗口缩放会重新 init）
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
                final int parsed = Integer.parseInt(text, 16) & 0xFFFFFF;
                this.currentColor = parsed;
                setChannelValues(parsed);
            } catch (NumberFormatException ignored) {
                // 无效的 hex 暂不处理
            }
        });
        this.addDrawableChild(this.hexField);

        // ---------- 染色页：RGB 三个数值框（可打字、可拖动、可微调） ----------
        this.rField = channelField(this.rgbY, 0);
        this.gField = channelField(this.rgbY + this.rowStep, 1);
        this.bField = channelField(this.rgbY + this.rowStep * 2, 2);
        this.addDrawableChild(this.rField);
        this.addDrawableChild(this.gField);
        this.addDrawableChild(this.bField);

        // ---------- 显示设置页：箭头 + 楼层文字 ----------
        this.arrowScaleField = displayField(this.displayY, LiftColorConfig.MIN_SCALE, LiftColorConfig.MAX_SCALE,
                0.05F, this.arrowScale, v -> this.arrowScale = v);
        this.arrowOffXField = displayField(this.displayY + this.rowStep, LiftColorConfig.MIN_OFFSET, LiftColorConfig.MAX_OFFSET,
                0.05F, this.arrowOffsetX, v -> this.arrowOffsetX = v);
        this.arrowOffYField = displayField(this.displayY + this.rowStep * 2, LiftColorConfig.MIN_OFFSET, LiftColorConfig.MAX_OFFSET,
                0.05F, this.arrowOffsetY, v -> this.arrowOffsetY = v);
        this.textScaleField = displayField(this.displayY + this.rowStep * 3, LiftColorConfig.MIN_SCALE, LiftColorConfig.MAX_SCALE,
                0.05F, this.textScale, v -> this.textScale = v);
        this.textOffXField = displayField(this.displayY + this.rowStep * 4, LiftColorConfig.MIN_OFFSET, LiftColorConfig.MAX_OFFSET,
                0.05F, this.textOffsetX, v -> this.textOffsetX = v);
        this.textOffYField = displayField(this.displayY + this.rowStep * 5, LiftColorConfig.MIN_OFFSET, LiftColorConfig.MAX_OFFSET,
                0.05F, this.textOffsetY, v -> this.textOffsetY = v);
        this.addDrawableChild(this.arrowScaleField);
        this.addDrawableChild(this.arrowOffXField);
        this.addDrawableChild(this.arrowOffYField);
        this.addDrawableChild(this.textScaleField);
        this.addDrawableChild(this.textOffXField);
        this.addDrawableChild(this.textOffYField);

        // ---------- 保存 / 取消 ----------
        final int btnGap = 8;
        final int btnW = Math.max(60, (this.contentW - btnGap) / 2);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("保存并应用"), b -> saveAndClose())
                .dimensions(this.contentLeft, this.btnY, btnW, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("取消"), b -> this.close())
                .dimensions(this.contentLeft + btnW + btnGap, this.btnY, btnW, 20).build());

        this.activeTab = 0;
        applyTabVisibility();
    }

    /** 一个 RGB 通道输入框（0..255 整数）。 */
    private NumberField channelField(int y, int channel) {
        final int value = switch (channel) {
            case 0 -> (this.currentColor >> 16) & 0xFF;
            case 1 -> (this.currentColor >> 8) & 0xFF;
            default -> this.currentColor & 0xFF;
        };
        return new NumberField(this.textRenderer, this.fieldX, y, this.fieldW, this.rowH,
                0, 255, 1, 0, value, v -> setColorChannel(channel, (int) (float) v));
    }

    /** 显示设置页的一个数值框。 */
    private NumberField displayField(int y, float min, float max, float step, float value, java.util.function.Consumer<Float> setter) {
        return new NumberField(this.textRenderer, this.fieldX, y, this.fieldW, this.rowH,
                min, max, step, 2, value, setter);
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

        // 行高 / 行距：小屏压缩，但输入框不低于 16 高，保证手指点得动
        this.rowH = clamp(this.panelH / 22, 16, 20);
        this.rowStep = this.rowH + 3;
        this.labelW = Math.max(44, Math.min(62, this.contentW * 2 / 7));
        this.fieldX = this.contentLeft + this.labelW + 4;
        this.fieldW = Math.max(56, this.contentRight - this.fieldX);

        // 顶部：两页签 + 自动化按钮（三段等宽）
        this.tabY = this.panelTop + this.pad;
        this.tabH = 20;
        this.tabGap = 6;
        this.tabW = (this.contentW - this.tabGap * 2) / 3;
        this.tab0X = this.contentLeft;
        this.tab1X = this.contentLeft + this.tabW + this.tabGap;
        this.autoBtnX = this.contentLeft + (this.tabW + this.tabGap) * 2;

        // 底部按钮行
        final int btnH = 20;
        this.btnY = this.panelTop + this.panelH - this.pad - btnH;

        // 内容区（页签之下、按钮之上的区域）
        this.contentTop = this.tabY + this.tabH + 9;
        this.contentBottom = this.btnY - 12;
        this.liftLineY = this.contentTop;
        final int contentStart = this.contentTop + 16;

        // 染色页各段落：按可用高度压缩行间距，小屏也能容纳。
        final int colorFixed = 18 + this.rowStep * 3 + 14 + this.presetRowsHeight();
        final int colorGap = clamp((this.contentBottom - contentStart - colorFixed) / 3, 4, 10);
        this.hexY = contentStart;
        this.rgbY = this.hexY + 18 + colorGap;
        this.presetLabelY = this.rgbY + this.rowStep * 3 + colorGap;
        this.presetY = this.presetLabelY + 14 + colorGap;

        // 预设色块：5 列 2 行，块间留 4/6 像素间距。
        final int blockGapX = 4;
        final int blockGapY = 6;
        this.presetBlockW = Math.max(24, (this.contentW - blockGapX * 4) / 5);
        this.presetBlockH = 18;

        // 显示设置页：两个分组标题 + 六行，按可用高度均分
        final int displayFixed = 2 * 11 + this.rowStep * 6;
        final int displayGap = clamp((this.contentBottom - contentStart - displayFixed) / 7, 0, 8);
        this.displayY = contentStart + 11 + displayGap;
    }

    private int presetRowsHeight() {
        return 18 * 2 + 6;
    }

    /** 按 activeTab 显示/隐藏各分区控件，并取消隐藏输入框的焦点。 */
    private void applyTabVisibility() {
        final boolean colorTab = this.activeTab == 0;
        setWidgetVisible(this.hexField, colorTab);
        setWidgetVisible(this.rField, colorTab);
        setWidgetVisible(this.gField, colorTab);
        setWidgetVisible(this.bField, colorTab);
        setWidgetVisible(this.arrowScaleField, !colorTab);
        setWidgetVisible(this.arrowOffXField, !colorTab);
        setWidgetVisible(this.arrowOffYField, !colorTab);
        setWidgetVisible(this.textScaleField, !colorTab);
        setWidgetVisible(this.textOffXField, !colorTab);
        setWidgetVisible(this.textOffYField, !colorTab);

        if (!colorTab) {
            this.hexField.setFocused(false);
        } else {
            this.arrowScaleField.setFocused(false);
        }
    }

    private static void setWidgetVisible(ClickableWidget widget, boolean visible) {
        if (widget != null) {
            widget.visible = visible; // 隐藏的同时禁用点击，避免误触
            widget.active = visible;
        }
    }

    private void setColorChannel(int channel, int value) {
        int r = (this.currentColor >> 16) & 0xFF;
        int g = (this.currentColor >> 8) & 0xFF;
        int b = this.currentColor & 0xFF;
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

    /** 外部（hex 输入、点预设）改颜色时同步三个数值框。 */
    private void setChannelValues(int rgb) {
        this.updating = true;
        this.rField.setValue((rgb >> 16) & 0xFF);
        this.gField.setValue((rgb >> 8) & 0xFF);
        this.bField.setValue(rgb & 0xFF);
        this.updating = false;
    }

    private void setColor(int rgb) {
        this.currentColor = rgb & 0xFFFFFF;
        setChannelValues(rgb);
        this.updating = true;
        this.hexField.setText(Integer.toHexString(rgb));
        this.updating = false;
    }

    private void saveAndClose() {
        final LiftColorConfig cfg = new LiftColorConfig(
                0xFF000000 | this.currentColor,
                LiftColorConfig.clampScale(this.arrowScale),
                LiftColorConfig.clampOffset(this.arrowOffsetX),
                LiftColorConfig.clampOffset(this.arrowOffsetY),
                this.lift.getName() == null ? "" : this.lift.getName());
        cfg.textScale = LiftColorConfig.clampScale(this.textScale);
        cfg.textOffsetX = LiftColorConfig.clampOffset(this.textOffsetX);
        cfg.textOffsetY = LiftColorConfig.clampOffset(this.textOffsetY);
        // 带上原有的箭头方向，避免自动化设置的固定方向被面板保存覆盖掉
        cfg.arrowDirection = this.arrowDirection;
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
        drawTab(context, 1, "电梯显示设置", this.activeTab == 1);
        // 顶部「自动化」入口：与前两页签同款圆角样式（普通字色，不做金色强调）
        final boolean autoHover = PanelUi.hit(mouseX, mouseY, this.autoBtnX, this.tabY, this.tabW, this.tabH);
        PanelUi.fillRoundedRect(context, this.autoBtnX, this.tabY, this.tabW, this.tabH, 6,
                autoHover ? PanelUi.TAB_HOVER_BG : PanelUi.TAB_INACTIVE_BG);
        context.drawCenteredTextWithShadow(this.textRenderer, "自动化",
                this.autoBtnX + this.tabW / 2, this.tabY + (this.tabH - 8) / 2, PanelUi.TAB_ACTIVE_TEXT);

        // 电梯名（左上，较淡）
        context.drawTextWithShadow(this.textRenderer, "电梯: " + (this.lift.getName() == null ? "?" : this.lift.getName()),
                this.contentLeft, this.liftLineY, SUBTLE_TEXT);

        if (this.activeTab == 0) {
            renderColorTab(context);
        } else {
            renderDisplayTab(context, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderColorTab(DrawContext context) {
        // 颜色预览（hex 输入框右侧）
        final int previewW = 46;
        final int previewX = this.contentRight - previewW;
        context.fill(previewX, this.hexY, previewX + previewW, this.hexY + 18, 0xFF000000 | this.currentColor);
        context.drawBorder(previewX, this.hexY, previewW, 18, 0x66FFFFFF);

        // RGB 通道标签
        context.drawTextWithShadow(this.textRenderer, "R", this.contentLeft, this.rgbY + (this.rowH - 8) / 2, SUBTLE_TEXT);
        context.drawTextWithShadow(this.textRenderer, "G", this.contentLeft, this.rgbY + this.rowStep + (this.rowH - 8) / 2, SUBTLE_TEXT);
        context.drawTextWithShadow(this.textRenderer, "B", this.contentLeft, this.rgbY + this.rowStep * 2 + (this.rowH - 8) / 2, SUBTLE_TEXT);

        // 预设颜色色块
        context.drawTextWithShadow(this.textRenderer, "预设颜色:", this.contentLeft, this.presetLabelY, SUBTLE_TEXT);
        for (int i = 0; i < PRESETS.length; i++) {
            final int[] r = presetRect(i);
            context.fill(r[0], r[1], r[0] + this.presetBlockW, r[1] + this.presetBlockH, 0xFF000000 | PRESETS[i][0]);
            context.drawBorder(r[0], r[1], this.presetBlockW, this.presetBlockH, 0x5AFFFFFF);
        }
    }

    /** 显示设置页：箭头一组、楼层文字一组，标签写在每个数值框左边。 */
    private void renderDisplayTab(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWithShadow(this.textRenderer, "箭头", this.contentLeft, this.displayY - 11, PanelUi.HEADER_TEXT);
        drawRowLabel(context, this.arrowScaleField, "大小", mouseX, mouseY);
        drawRowLabel(context, this.arrowOffXField, "偏移X", mouseX, mouseY);
        drawRowLabel(context, this.arrowOffYField, "偏移Y", mouseX, mouseY);

        final int textGroupY = this.textScaleField.getY() - 11;
        context.drawTextWithShadow(this.textRenderer, "楼层文字", this.contentLeft, textGroupY, PanelUi.HEADER_TEXT);
        drawRowLabel(context, this.textScaleField, "大小", mouseX, mouseY);
        drawRowLabel(context, this.textOffXField, "偏移X", mouseX, mouseY);
        drawRowLabel(context, this.textOffYField, "偏移Y", mouseX, mouseY);

        context.drawTextWithShadow(this.textRenderer, "1.00 = 原版大小，0.00 = 不偏移", this.contentLeft,
                this.textOffYField.getY() + this.rowH + 4, SUBTLE_TEXT);
    }

    private void drawRowLabel(DrawContext context, NumberField field, String label, int mouseX, int mouseY) {
        final boolean active = field.isFocused() || field.isMouseOver(mouseX, mouseY);
        context.drawTextWithShadow(this.textRenderer, label, this.contentLeft,
                field.getY() + (field.getHeight() - this.textRenderer.fontHeight) / 2,
                active ? TAB_ACTIVE_TEXT : SUBTLE_TEXT);
    }

    /** 画一个顶部页签（圆角矩形 + 居中文字）。 */
    private void drawTab(DrawContext context, int index, String label, boolean active) {
        final int x = index == 0 ? this.tab0X : this.tab1X;
        fillRoundedRect(context, x, this.tabY, this.tabW, this.tabH, 6, active ? TAB_ACTIVE_BG : TAB_INACTIVE_BG);
        if (active) {
            // 激活页签底部的高亮条
            context.fill(x + 4, this.tabY + this.tabH - 2, x + this.tabW - 4, this.tabY + this.tabH - 1, 0xAAFFFFFF);
        }
        final int textW = this.textRenderer.getWidth(label);
        // 页签文字比页签宽时（小屏 + 长名字）缩小字号，避免被裁掉
        if (textW > this.tabW - 4) {
            final float shrink = (this.tabW - 4f) / textW;
            context.getMatrices().push();
            context.getMatrices().translate(x + this.tabW / 2f, this.tabY + this.tabH / 2f, 0);
            context.getMatrices().scale(shrink, shrink, 1);
            context.drawTextWithShadow(this.textRenderer, label, -textW / 2, -this.textRenderer.fontHeight / 2,
                    active ? TAB_ACTIVE_TEXT : TAB_INACTIVE_TEXT);
            context.getMatrices().pop();
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, label, x + this.tabW / 2,
                    this.tabY + (this.tabH - 8) / 2, active ? TAB_ACTIVE_TEXT : TAB_INACTIVE_TEXT);
        }
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
            // 「自动化」入口
            if (PanelUi.hit(mouseX, mouseY, this.autoBtnX, this.tabY, this.tabW, this.tabH)) {
                MinecraftClient.getInstance().setScreen(new AutomationScreen(this));
                return true;
            }
            // 染色页预设色块
            if (this.activeTab == 0) {
                for (int i = 0; i < PRESETS.length; i++) {
                    final int[] r = presetRect(i);
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
        final int x = this.contentLeft + (index % 5) * (this.presetBlockW + blockGapX);
        final int y = this.presetY + (index / 5) * (this.presetBlockH + blockGapY);
        return new int[]{x, y};
    }

    /** 圆角矩形绘制已抽到 {@link PanelUi}，三个界面共用同一套外观。 */
    private static void fillRoundedRect(DrawContext context, int x, int y, int w, int h, int radius, int color) {
        PanelUi.fillRoundedRect(context, x, y, w, h, radius, color);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
