package com.xiao.mtrliftcolornew.client;

import com.xiao.mtrliftcolornew.config.LiftColorConfig;
import com.xiao.mtrliftcolornew.config.LiftColorManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.mtr.core.data.Lift;

import java.util.ArrayList;
import java.util.List;

/**
 * MC 原版风格的电梯调色面板（圆角 + 半透明，顶部可切换的分区页签 + 「自动化」按钮）。
 *
 * <p>顶部三页签（点击切换）：</p>
 * <ul>
 *   <li><b>电梯染色</b>：hex 颜色输入、RGB 三个数值框、预设色块、颜色预览。</li>
 *   <li><b>电梯显示设置</b>：箭头与楼层文字的大小 / 偏移、三角钥匙检修门的开 / 关时长。</li>
 * </ul>
 *
 * <p><b>内容区可滚动</b>：手机上屏幕矮，显示设置页的八行输入框塞不进面板——以前靠压扁行高硬塞，
 * 结果标签叠在一起没法读。现在行高保持手指点得动的尺寸，放不下的部分从面板底部溢出，
 * 右侧出现一条竖向滚动条：鼠标滚轮、按住内容区上下拖（触屏 / 触控笔）、直接拖滚动条滑块都能翻页，
 * 手机、Windows 触控设备与鼠标设备通用；桌面大屏内容装得下时滚动条自动隐藏，一切照旧。</p>
 *
 * <p>所有数值都用 {@link NumberField}：手机上点开可以直接打字，也能按住左右拖动改值，
 * 框内两侧还有 − / + 微调。</p>
 *
 * <p>底部两个按钮（保存并应用 / 取消）不参与滚动，永远在面板底部可点。</p>
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
    /** 滚动条配色与尺寸。 */
    private static final int SB_W = 5;
    private static final int SB_TRACK = 0x30FFFFFF;
    private static final int SB_THUMB = 0x99FFFFFF;
    private static final int SB_THUMB_HOT = 0xD9FFFFFF;
    /** 滚动条命中区在两侧各放宽几像素，手指好按。 */
    private static final int SB_GRAB = 4;
    /** 滚轮一格走的像素。 */
    private static final int WHEEL_STEP = 18;

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
    /** 三角钥匙检修开门 / 关门时长（毫秒）。 */
    private float doorOpenMs;
    private float doorCloseMs;
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

    // 内容视口（页签之下、按钮之上；滚动只发生在这个矩形里）。
    private int contentTop;
    private int contentBottom;
    private int liftLineY;
    private int viewTop;
    private int viewBottom;
    private int viewH;

    // 底部固定按钮（不参与滚动，也不是子控件——这样滚动裁剪不影响它们）。
    private int btnY;
    private int btnW;
    private int btn2X;

    // 通用行尺寸。
    private int rowH;
    private int rowStep;
    private int labelW;
    private int fieldX;
    private int fieldW;

    // 染色页各行的基准 Y（滚动前的自然位置）。
    private int hexBaseY;
    private int rgbBaseY;
    private int presetLabelBaseY;
    private int presetBaseY;
    private int presetBlockW;
    private int presetBlockH;
    private int colorContentH;

    // 显示设置页的基准：8 行 + 3 个分组标题 + 1 行说明。
    private int displayRowBaseY;
    private int displayHintBaseY;
    private int displayContentH;
    private static final int HEADER_H = 11;
    private static final int GROUP_GAP = 8;

    // 滚动状态：每页各记各的，切页不串。
    private double scrollColor;
    private double scrollDisplay;
    /** 正在拖滚动条滑块 / 正在内容区拖页。 */
    private boolean thumbDragging;
    private boolean pageDragging;
    private double lastDragY;

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
    private NumberField doorOpenField;
    private NumberField doorCloseField;

    /** 随内容滚动的控件登记表：基准 Y + 所属页签。 */
    private record Scrolled(ClickableWidget widget, int baseY, int tab) {
    }

    private final List<Scrolled> scrolled = new ArrayList<>();

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
        this.doorOpenMs = cfg.doorOpenMs;
        this.doorCloseMs = cfg.doorCloseMs;
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
        this.scrolled.clear();
        computeLayout();

        // ---------- 染色页：hex 输入 ----------
        final int hexW = Math.max(70, Math.min(130, this.contentW - 56));
        this.hexField = new TextFieldWidget(this.textRenderer, this.contentLeft, this.hexBaseY, hexW, 18, Text.literal("颜色"));
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
        addScrolled(this.hexField, this.hexBaseY, 0);

        // ---------- 染色页：RGB 三个数值框（可打字、可拖动、可微调） ----------
        this.rField = channelField(this.rgbBaseY, 0);
        this.gField = channelField(this.rgbBaseY + this.rowStep, 1);
        this.bField = channelField(this.rgbBaseY + this.rowStep * 2, 2);
        addScrolled(this.rField, this.rgbBaseY, 0);
        addScrolled(this.gField, this.rgbBaseY + this.rowStep, 0);
        addScrolled(this.bField, this.rgbBaseY + this.rowStep * 2, 0);

        // ---------- 显示设置页：箭头 + 楼层文字 + 检修门时长 ----------
        this.arrowScaleField = displayField(displayRowY(0), LiftColorConfig.MIN_SCALE, LiftColorConfig.MAX_SCALE,
                0.05F, this.arrowScale, v -> this.arrowScale = v);
        this.arrowOffXField = displayField(displayRowY(1), LiftColorConfig.MIN_OFFSET, LiftColorConfig.MAX_OFFSET,
                0.05F, this.arrowOffsetX, v -> this.arrowOffsetX = v);
        this.arrowOffYField = displayField(displayRowY(2), LiftColorConfig.MIN_OFFSET, LiftColorConfig.MAX_OFFSET,
                0.05F, this.arrowOffsetY, v -> this.arrowOffsetY = v);
        this.textScaleField = displayField(displayRowY(3), LiftColorConfig.MIN_SCALE, LiftColorConfig.MAX_SCALE,
                0.05F, this.textScale, v -> this.textScale = v);
        this.textOffXField = displayField(displayRowY(4), LiftColorConfig.MIN_OFFSET, LiftColorConfig.MAX_OFFSET,
                0.05F, this.textOffsetX, v -> this.textOffsetX = v);
        this.textOffYField = displayField(displayRowY(5), LiftColorConfig.MIN_OFFSET, LiftColorConfig.MAX_OFFSET,
                0.05F, this.textOffsetY, v -> this.textOffsetY = v);
        this.doorOpenField = doorField(displayRowY(6), this.doorOpenMs, v -> this.doorOpenMs = v);
        this.doorCloseField = doorField(displayRowY(7), this.doorCloseMs, v -> this.doorCloseMs = v);
        addScrolled(this.arrowScaleField, displayRowY(0), 1);
        addScrolled(this.arrowOffXField, displayRowY(1), 1);
        addScrolled(this.arrowOffYField, displayRowY(2), 1);
        addScrolled(this.textScaleField, displayRowY(3), 1);
        addScrolled(this.textOffXField, displayRowY(4), 1);
        addScrolled(this.textOffYField, displayRowY(5), 1);
        addScrolled(this.doorOpenField, displayRowY(6), 1);
        addScrolled(this.doorCloseField, displayRowY(7), 1);

        applyScroll();
    }

    /** 登记一个随内容滚动的控件。 */
    private void addScrolled(ClickableWidget widget, int baseY, int tab) {
        this.scrolled.add(new Scrolled(widget, baseY, tab));
        this.addDrawableChild(widget);
    }

    /** 显示设置页第 i 行的基准 Y（三个分组之间留一段空隙）。 */
    private int displayRowY(int index) {
        return this.displayRowBaseY + this.rowStep * index
                + (index >= 3 ? GROUP_GAP : 0)
                + (index >= 6 ? GROUP_GAP : 0);
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

    /** 检修门时长框：毫秒整数，步进 100ms，区间跟 {@link LiftColorConfig} 的夹取边界一致。 */
    private NumberField doorField(int y, float value, java.util.function.Consumer<Float> setter) {
        return new NumberField(this.textRenderer, this.fieldX, y, this.fieldW, this.rowH,
                LiftColorConfig.MIN_DOOR_MS, LiftColorConfig.MAX_DOOR_MS, 100.0F, 0, value, setter);
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

        // 行高固定保持手指点得动（16~20），放不下的内容走滚动，不再压扁
        this.rowH = clamp(this.panelH / 22, 16, 20);
        this.rowStep = this.rowH + 3;
        this.labelW = Math.max(44, Math.min(62, this.contentW * 2 / 7));
        this.fieldX = this.contentLeft + this.labelW + 4;
        this.fieldW = Math.max(56, this.contentRight - this.fieldX - 8);

        // 顶部：两页签 + 自动化按钮（三段等宽）
        this.tabY = this.panelTop + this.pad;
        this.tabH = 20;
        this.tabGap = 6;
        this.tabW = (this.contentW - this.tabGap * 2) / 3;
        this.tab0X = this.contentLeft;
        this.tab1X = this.contentLeft + this.tabW + this.tabGap;
        this.autoBtnX = this.contentLeft + (this.tabW + this.tabGap) * 2;

        // 底部按钮行（固定，不滚动）
        final int btnH = 20;
        this.btnY = this.panelTop + this.panelH - this.pad - btnH;
        final int btnGap = 8;
        this.btnW = Math.max(60, (this.contentW - btnGap) / 2);
        this.btn2X = this.contentLeft + this.btnW + btnGap;

        // 视口：「电梯: 名字」行之下、底部按钮之上
        this.contentTop = this.tabY + this.tabH + 9;
        this.contentBottom = this.btnY - 12;
        this.liftLineY = this.contentTop;
        this.viewTop = this.contentTop + 16;
        this.viewBottom = this.contentBottom;
        this.viewH = Math.max(24, this.viewBottom - this.viewTop);

        // 染色页自然布局
        final int colorGap = 7;
        this.hexBaseY = this.viewTop;
        this.rgbBaseY = this.hexBaseY + 18 + colorGap;
        this.presetLabelBaseY = this.rgbBaseY + this.rowStep * 3 + colorGap;
        this.presetBaseY = this.presetLabelBaseY + 14 + colorGap;
        this.presetBlockW = Math.max(24, (this.contentW - 8) / 5);
        this.presetBlockH = 18;
        this.colorContentH = (this.presetBaseY + this.presetBlockH * 2 + 6) - this.viewTop;

        // 显示设置页自然布局：3 个分组标题 + 8 行 + 1 行说明
        this.displayRowBaseY = this.viewTop + HEADER_H + 4;
        this.displayHintBaseY = displayRowY(7) + this.rowH + 8;
        this.displayContentH = (this.displayHintBaseY + 12) - this.viewTop;

        // 窗口变小后把两页的滚动量夹回合法区间（init 时 applyScroll 会应用）
        clampScrolls();
    }

    /** 某个页签能滚的最大距离（0 = 装得下，不显示滚动条）。 */
    private int maxScroll(int tab) {
        return Math.max(0, (tab == 0 ? this.colorContentH : this.displayContentH) - this.viewH);
    }

    private double currentScroll() {
        return this.activeTab == 0 ? this.scrollColor : this.scrollDisplay;
    }

    private void clampScrolls() {
        this.scrollColor = clampD(this.scrollColor, 0, maxScroll(0));
        this.scrollDisplay = clampD(this.scrollDisplay, 0, maxScroll(1));
    }

    /** 滚一段（dy > 0 表示往下看）。只作用于当前页签。 */
    private void scrollBy(double dy) {
        if (dy == 0.0D) {
            return;
        }
        if (this.activeTab == 0) {
            this.scrollColor = clampD(this.scrollColor + dy, 0, maxScroll(0));
        } else {
            this.scrollDisplay = clampD(this.scrollDisplay + dy, 0, maxScroll(1));
        }
        applyScroll();
    }

    /** 把滚动量应用到所有登记过的控件：改 Y、隐藏滚出视口的（不能看见也不能被点到）。 */
    private void applyScroll() {
        final int scroll = (int) Math.round(currentScroll());
        for (final Scrolled entry : this.scrolled) {
            final boolean onTab = entry.tab() == this.activeTab;
            final int y = entry.baseY() - (onTab ? scroll : 0);
            entry.widget().setY(y);
            final boolean inside = y + this.rowH + 4 > this.viewTop && y - 4 < this.viewBottom;
            entry.widget().visible = onTab && inside;
            entry.widget().active = onTab;
            if (!onTab && entry.widget().isFocused()) {
                entry.widget().setFocused(false);
            }
        }
    }

    /** 视口矩形（含滚动条所在的右侧区域）。 */
    private boolean inViewport(double mouseX, double mouseY) {
        return mouseX >= this.panelLeft + 4 && mouseX <= this.panelLeft + this.panelW - 4
                && mouseY >= this.viewTop && mouseY <= this.viewBottom;
    }

    /** 滚动条轨道矩形。 */
    private int sbX() {
        return this.panelLeft + this.panelW - this.pad / 2 - SB_W;
    }

    /** 滑块几何：[返回 y, 返回高]。 */
    private int[] thumbRect() {
        final int max = maxScroll(this.activeTab);
        if (max <= 0) {
            return null;
        }
        final int contentH = this.activeTab == 0 ? this.colorContentH : this.displayContentH;
        final int thumbH = Math.max(18, (int) ((long) this.viewH * this.viewH / Math.max(1, contentH)));
        final int range = Math.max(1, this.viewH - thumbH);
        final int thumbY = this.viewTop + (int) (currentScroll() / max * range);
        return new int[]{thumbY, thumbH};
    }

    private boolean hitScrollbar(double mouseX, double mouseY) {
        return maxScroll(this.activeTab) > 0
                && mouseX >= this.sbX() - SB_GRAB && mouseX <= this.sbX() + SB_W + SB_GRAB
                && mouseY >= this.viewTop && mouseY <= this.viewBottom;
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
        cfg.doorOpenMs = LiftColorConfig.clampDoorMs(this.doorOpenMs);
        cfg.doorCloseMs = LiftColorConfig.clampDoorMs(this.doorCloseMs);
        // 带上原有的箭头方向，避免自动化设置的固定方向被面板保存覆盖掉
        cfg.arrowDirection = this.arrowDirection;
        LiftColorManager.setAndSave(this.lift, currentWorld(), cfg);
        this.close();
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

        // 电梯名（左上，较淡，固定不滚动）
        context.drawTextWithShadow(this.textRenderer, "电梯: " + (this.lift.getName() == null ? "?" : this.lift.getName()),
                this.contentLeft, this.liftLineY, SUBTLE_TEXT);

        // ---------- 滚动的内容区：先裁剪，再画文字与控件 ----------
        final int scroll = (int) Math.round(currentScroll());
        context.enableScissor(this.panelLeft + 4, this.viewTop, this.panelLeft + this.panelW - 4, this.viewBottom + 2);
        if (this.activeTab == 0) {
            renderColorTab(context, scroll);
        } else {
            renderDisplayTab(context, mouseX, mouseY, scroll);
        }
        super.render(context, mouseX, mouseY, delta);
        context.disableScissor();

        // 滚动条（不裁剪：贴面板内边）
        drawScrollbar(context, mouseX, mouseY);

        // 底部固定按钮
        drawBottomButton(context, this.contentLeft, "保存并应用", mouseX, mouseY);
        drawBottomButton(context, this.btn2X, "取消", mouseX, mouseY);
    }

    private void renderColorTab(DrawContext context, int scroll) {
        // 颜色预览（hex 输入框右侧）
        final int previewW = 46;
        final int previewX = this.contentRight - previewW;
        final int hexY = this.hexBaseY - scroll;
        context.fill(previewX, hexY, previewX + previewW, hexY + 18, 0xFF000000 | this.currentColor);
        context.drawBorder(previewX, hexY, previewW, 18, 0x66FFFFFF);

        // RGB 通道标签
        final int rgbY = this.rgbBaseY - scroll;
        context.drawTextWithShadow(this.textRenderer, "R", this.contentLeft, rgbY + (this.rowH - 8) / 2, SUBTLE_TEXT);
        context.drawTextWithShadow(this.textRenderer, "G", this.contentLeft, rgbY + this.rowStep + (this.rowH - 8) / 2, SUBTLE_TEXT);
        context.drawTextWithShadow(this.textRenderer, "B", this.contentLeft, rgbY + this.rowStep * 2 + (this.rowH - 8) / 2, SUBTLE_TEXT);

        // 预设颜色色块
        context.drawTextWithShadow(this.textRenderer, "预设颜色:", this.contentLeft, this.presetLabelBaseY - scroll, SUBTLE_TEXT);
        for (int i = 0; i < PRESETS.length; i++) {
            final int[] r = presetRect(i, scroll);
            context.fill(r[0], r[1], r[0] + this.presetBlockW, r[1] + this.presetBlockH, 0xFF000000 | PRESETS[i][0]);
            context.drawBorder(r[0], r[1], this.presetBlockW, this.presetBlockH, 0x5AFFFFFF);
        }
    }

    /** 显示设置页：箭头一组、楼层文字一组、检修门一组，标签写在每个数值框左边。 */
    private void renderDisplayTab(DrawContext context, int mouseX, int mouseY, int scroll) {
        context.drawTextWithShadow(this.textRenderer, "箭头", this.contentLeft, displayRowY(0) - HEADER_H - scroll, PanelUi.HEADER_TEXT);
        drawRowLabel(context, this.arrowScaleField, "大小");
        drawRowLabel(context, this.arrowOffXField, "偏移X");
        drawRowLabel(context, this.arrowOffYField, "偏移Y");

        final int textGroupY = this.textScaleField.getY() - HEADER_H;
        context.drawTextWithShadow(this.textRenderer, "楼层文字", this.contentLeft, textGroupY, PanelUi.HEADER_TEXT);
        drawRowLabel(context, this.textScaleField, "大小");
        drawRowLabel(context, this.textOffXField, "偏移X");
        drawRowLabel(context, this.textOffYField, "偏移Y");

        final int doorGroupY = this.doorOpenField.getY() - HEADER_H;
        context.drawTextWithShadow(this.textRenderer, "三角钥匙检修门", this.contentLeft, doorGroupY, PanelUi.HEADER_TEXT);
        drawRowLabel(context, this.doorOpenField, "开门ms");
        drawRowLabel(context, this.doorCloseField, "关门ms");

        context.drawTextWithShadow(this.textRenderer, "1.00 = 原版大小，0.00 = 不偏移；门：越大越慢",
                this.contentLeft, this.displayHintBaseY - scroll, SUBTLE_TEXT);
    }

    /**
     * 行标签：跟随输入框当前位置（框已经由 applyScroll 挪好，标签按框的 Y 写，永远对齐）。
     * 只在框可见时画，避免标签漂到视口外或被裁前重叠。
     */
    private void drawRowLabel(DrawContext context, NumberField field, String label) {
        if (!field.visible) {
            return;
        }
        context.drawTextWithShadow(this.textRenderer, label, this.contentLeft,
                field.getY() + (field.getHeight() - this.textRenderer.fontHeight) / 2,
                field.isFocused() ? TAB_ACTIVE_TEXT : SUBTLE_TEXT);
    }

    /** 右侧竖向滚动条：轨道 + 滑块；内容装得下时整条隐藏。 */
    private void drawScrollbar(DrawContext context, int mouseX, int mouseY) {
        final int[] thumb = thumbRect();
        if (thumb == null) {
            return;
        }
        final int x = sbX();
        context.fill(x, this.viewTop, x + SB_W, this.viewBottom, SB_TRACK);
        final boolean hot = this.thumbDragging
                || PanelUi.hit(mouseX, mouseY, x - SB_GRAB, this.viewTop, SB_W + SB_GRAB * 2, this.viewH);
        PanelUi.fillRoundedRect(context, x, thumb[0], SB_W, thumb[1], 2, hot ? SB_THUMB_HOT : SB_THUMB);
    }

    /** 底部固定按钮（手动绘制 + 手动命中，避免被滚动裁剪影响）。 */
    private void drawBottomButton(DrawContext context, int x, String label, int mouseX, int mouseY) {
        final boolean hover = PanelUi.hit(mouseX, mouseY, x, this.btnY, this.btnW, 20);
        PanelUi.fillRoundedRect(context, x, this.btnY, this.btnW, 20, 6,
                hover ? PanelUi.TAB_HOVER_BG : PanelUi.TAB_INACTIVE_BG);
        context.drawCenteredTextWithShadow(this.textRenderer, label, x + this.btnW / 2,
                this.btnY + (20 - this.textRenderer.fontHeight) / 2, TAB_ACTIVE_TEXT);
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
            // 滚动条：按在滑块上就拖滑块；按在轨道上先把滑块移过来再拖
            if (hitScrollbar(mouseX, mouseY)) {
                final int[] thumb = thumbRect();
                if (thumb != null && mouseY < thumb[0] || thumb != null && mouseY > thumb[0] + thumb[1]) {
                    jumpThumbTo(mouseY);
                }
                this.thumbDragging = true;
                this.lastDragY = mouseY;
                return true;
            }
            // 底部固定按钮
            if (PanelUi.hit(mouseX, mouseY, this.contentLeft, this.btnY, this.btnW, 20)) {
                saveAndClose();
                return true;
            }
            if (PanelUi.hit(mouseX, mouseY, this.btn2X, this.btnY, this.btnW, 20)) {
                this.close();
                return true;
            }
            // 内容区控件（先给输入框，它们吃掉点击就不算拖页）
            if (super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            // 染色页预设色块
            if (this.activeTab == 0) {
                final int scroll = (int) Math.round(currentScroll());
                for (int i = 0; i < PRESETS.length; i++) {
                    final int[] r = presetRect(i, scroll);
                    if (mouseX >= r[0] && mouseX < r[0] + this.presetBlockW && mouseY >= r[1] && mouseY < r[1] + this.presetBlockH) {
                        setColor(PRESETS[i][0]);
                        return true;
                    }
                }
            }
            // 视口空白处按下：开始拖页（触屏上下滑就翻页）
            if (maxScroll(this.activeTab) > 0 && inViewport(mouseX, mouseY)) {
                this.pageDragging = true;
                this.lastDragY = mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 点轨道：滑块中心跳到点的位置。 */
    private void jumpThumbTo(double mouseY) {
        final int max = maxScroll(this.activeTab);
        if (max <= 0) {
            return;
        }
        final int[] thumb = thumbRect();
        if (thumb == null) {
            return;
        }
        final int range = Math.max(1, this.viewH - thumb[1]);
        final double next = clampD(mouseY - thumb[1] / 2.0 - this.viewTop, 0, range) / range * max;
        setScroll(next);
    }

    private void setScroll(double value) {
        if (this.activeTab == 0) {
            this.scrollColor = clampD(value, 0, maxScroll(0));
        } else {
            this.scrollDisplay = clampD(value, 0, maxScroll(1));
        }
        applyScroll();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.thumbDragging) {
            final int max = maxScroll(this.activeTab);
            final int[] thumb = thumbRect();
            if (thumb != null && max > 0) {
                final int range = Math.max(1, this.viewH - thumb[1]);
                setScroll(currentScroll() + (mouseY - this.lastDragY) / range * max);
            }
            this.lastDragY = mouseY;
            return true;
        }
        if (this.pageDragging) {
            // 手指往下拖 = 看上面的内容：滚动量减小，跟手机 App 的惯性方向一致
            scrollBy(-(mouseY - this.lastDragY));
            this.lastDragY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.thumbDragging = false;
        this.pageDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (inViewport(mouseX, mouseY) && maxScroll(this.activeTab) > 0) {
            scrollBy(-amount * WHEEL_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private void switchTab(int tab) {
        if (this.activeTab == tab) {
            return;
        }
        this.activeTab = tab;
        this.thumbDragging = false;
        this.pageDragging = false;
        applyScroll();
    }

    private int[] presetRect(int index, int scroll) {
        final int blockGapX = 4;
        final int blockGapY = 6;
        final int x = this.contentLeft + (index % 5) * (this.presetBlockW + blockGapX);
        final int y = this.presetBaseY - scroll + (index / 5) * (this.presetBlockH + blockGapY);
        return new int[]{x, y};
    }

    /** 圆角矩形绘制已抽到 {@link PanelUi}，三个界面共用同一套外观。 */
    private static void fillRoundedRect(DrawContext context, int x, int y, int w, int h, int radius, int color) {
        PanelUi.fillRoundedRect(context, x, y, w, h, radius, color);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static double clampD(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}
