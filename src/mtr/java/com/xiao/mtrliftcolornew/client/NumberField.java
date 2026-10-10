package com.xiao.mtrliftcolornew.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * 可拖动的数值输入框：手机上能直接点它打字，鼠标 / 手指按住左右拖也能改值，框内左右两侧还有 − / + 微调。
 *
 * <p>用来替代面板与积木编辑器里的所有滑块：滑块在小屏上要精确点住拖动条很不好操作，
 * 输入框 + 拖动 + 微调三合一，键盘输入时会自动夹到 {@code min..max} 区间。</p>
 */
public class NumberField extends TextFieldWidget {

    /** 框内左右各留出的微调按钮宽度。 */
    private static final int BUTTON_W = 13;
    /** 超过这个像素位移才算「拖动」，避免只是想点住打字时把数值带跑。 */
    private static final int DRAG_THRESHOLD = 3;

    private final TextRenderer renderer;
    private final int decimals;
    private final Consumer<Float> onChange;

    private float min;
    private float max;
    private float step;
    private float pixelsPerUnit;
    private float value;
    private boolean internal;
    private boolean dragging;
    private double dragAnchorX;

    /**
     * @param decimals 小数位显示（0 = 整数，如 RGB 通道）
     * @param step     − / + 每次调整的幅度
     */
    public NumberField(TextRenderer textRenderer, int x, int y, int width, int height,
                       float min, float max, float step, int decimals, float value, Consumer<Float> onChange) {
        super(textRenderer, x, y, width, height, Text.empty());
        this.renderer = textRenderer;
        this.decimals = decimals;
        this.onChange = onChange;
        this.configure(min, max, step, value);
        this.setMaxLength(12);
        this.setChangedListener(text -> {
            if (this.internal) {
                return;
            }
            final Float parsed = parse(text);
            if (parsed != null) {
                this.setValue(parsed, false);
            }
        });
    }

    /**
     * 换一个取值区间并写入初始值（编辑器里选中别的积木时复用同一个输入框）。
     * 拖动灵敏度随之改变：横向拖满整个框宽正好走完整个区间。
     */
    public NumberField configure(float min, float max, float step, float value) {
        this.min = min;
        this.max = max;
        this.step = step;
        this.pixelsPerUnit = Math.max(1, this.getWidth()) / Math.max(1.0E-4F, max - min);
        this.setValue(value, true);
        return this;
    }

    public float getValue() {
        return this.value;
    }

    /** 外部（例如切换页签、恢复默认、拖动积木同步）改值。 */
    public void setValue(float value) {
        this.setValue(value, true);
    }

    private void setValue(float raw, boolean rewriteText) {
        final float clamped = clamp(raw);
        final boolean changed = clamped != this.value;
        this.value = clamped;
        if (rewriteText) {
            final String text = format(clamped);
            if (!text.equals(this.getText())) {
                this.internal = true;
                this.setText(text);
                this.internal = false;
            }
        }
        if (changed && this.onChange != null) {
            this.onChange.accept(clamped);
        }
    }

    private float clamp(float raw) {
        if (Float.isNaN(raw) || Float.isInfinite(raw)) {
            return this.value;
        }
        return Math.max(this.min, Math.min(this.max, raw));
    }

    private String format(float value) {
        return String.format(Locale.ROOT, decimals <= 0 ? "%.0f" : "%." + decimals + "f", value);
    }

    /** 允许 "1"、"1."、"-0.2"、" 1.5 " 这些输入中间态；解析不出来就当作没输入。 */
    private static Float parse(String text) {
        if (text == null) {
            return null;
        }
        final String trimmed = text.trim().replace('+', ' ');
        if (trimmed.isEmpty() || "-".equals(trimmed) || ".".equals(trimmed) || "-.".equals(trimmed)) {
            return null;
        }
        try {
            return Float.parseFloat(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isOverButton(mouseX, mouseY)) {
            this.dragging = false;
            this.setValue(this.value + (mouseX < this.getX() + BUTTON_W ? -this.step : this.step), true);
            return true;
        }
        this.dragging = false;
        this.dragAnchorX = mouseX;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && !this.isOverButton(mouseX, mouseY)) {
            if (!this.dragging) {
                if (Math.abs(mouseX - this.dragAnchorX) < DRAG_THRESHOLD) {
                    return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
                }
                this.dragging = true;
            }
            this.setValue(this.value + (float) (deltaX / this.pixelsPerUnit), true);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 焦点离开时把 "1." 这类中间态补成规范写法。 */
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            final Float parsed = parse(this.getText());
            this.setValue(parsed == null ? this.value : parsed, true);
        }
    }

    private boolean isOverButton(double mouseX, double mouseY) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        final double local = mouseX - this.getX();
        return local < BUTTON_W || local > this.getWidth() - BUTTON_W;
    }

    @Override
    public void renderButton(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderButton(context, mouseX, mouseY, delta);
        final TextRenderer tr = this.renderer;
        final int textY = this.getY() + (this.getHeight() - tr.fontHeight) / 2;
        final boolean hovering = this.isMouseOver(mouseX, mouseY);
        final boolean overLeft = hovering && mouseX - this.getX() < BUTTON_W;
        final boolean overRight = hovering && mouseX - this.getX() > this.getWidth() - BUTTON_W;
        drawGlyph(context, tr, "-", this.getX() + (BUTTON_W - tr.getWidth("-")) / 2, textY,
                overLeft ? 0xFFFFFFFF : 0xFFBFBFBF);
        drawGlyph(context, tr, "+", this.getX() + this.getWidth() - BUTTON_W / 2 - tr.getWidth("+") / 2, textY,
                overRight ? 0xFFFFFFFF : 0xFFBFBFBF);
    }

    private static void drawGlyph(net.minecraft.client.gui.DrawContext context, TextRenderer tr,
                                  String glyph, int x, int y, int color) {
        context.drawTextWithShadow(tr, glyph, x, y, color);
    }
}
