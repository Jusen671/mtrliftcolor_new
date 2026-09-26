package com.xiao.mtrliftcolornew.client;

import com.xiao.mtrliftcolornew.config.AutoChain;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 「箭头」图形化编辑器：像图形化编程一样，把积木块拖进中间的一条链。
 *
 * <p>界面分三块：</p>
 * <ul>
 *   <li>顶部页签：选择正在编辑「上行时」还是「下行时」的链；</li>
 *   <li>中间画布：一条竖直的积木链，起点是「上行时 / 下行时」触发块，
 *       块与块之间用一条长直线连接，例如
 *       {@code 上行时 ─ 箭头偏移X:0.12 ─ 箭头方向:向上}；</li>
 *   <li>底部积木盒：「箭头方向:向上」「箭头方向:向下」两块按钮积木，
 *       和「箭头偏移X」「箭头偏移Y」两根滑块积木。</li>
 * </ul>
 *
 * <p>操作方式：</p>
 * <ul>
 *   <li>按住积木 <b>不动约 0.25 秒</b> 再移动 —— 拿起积木拖动（长按即拖）；</li>
 *   <li>按住滑块积木后<b>直接左右移动</b> —— 调数值（按住即滑）；</li>
 *   <li>拖到中间画布即插入链中（链内同类型只保留一块，重复拖入等于挪位置）；</li>
 *   <li>把链中的积木拖回底部积木盒区域 = 从链上取下。</li>
 * </ul>
 */
public class ArrowRuleScreen extends Screen {

    private static final int MAX_W = 380;
    private static final int BG_BLOCK = 0x3AFFFFFF;
    private static final int BG_BLOCK_HOVER = 0x55FFFFFF;
    private static final int BG_SLOT = 0x28FFFFFF;
    private static final int BORDER_SLOT = 0x50FFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    /** 链里积木之间的连接线长度（“一条长一点的直线”）。 */
    private static final int LINK_LEN = 26;
    private static final int BLOCK_H = 20;
    /** 长按判定时间（纳秒）。 */
    private static final long LONG_PRESS_NS = 250_000_000L;

    /** 0 = 上行时，1 = 下行时。 */
    private int mode;

    private int panelLeft;
    private int panelTop;
    private int panelW;
    private int panelH;
    private int contentLeft;
    private int contentW;

    /** 画布区域（链的宿主）。 */
    private int canvasTop;
    private int canvasBottom;
    private int chainX;
    private int chainW;

    /** 积木盒区域。 */
    private int paletteTop;
    private int paletteBottom;

    /** 页签。 */
    private int tabY;
    private int tabW;

    /** 返回按钮。 */
    private int backY;

    // ---------- 拖动 / 滑动状态 ----------

    /** 当前被拿起的积木；null 表示没在拖。 */
    private AutoChain.Block dragBlock;
    /** 被拿起的积木来自链中的下标；-1 = 来自积木盒。 */
    private int dragFromChainIndex = -1;
    /** 按下点与积木左上角的偏移，让拖动不“跳手”。 */
    private double grabDX;
    private double grabDY;
    /** 拖动预览位置。 */
    private double dragX;
    private double dragY;

    /** 按下的积木（长按候选）；null = 没有。 */
    private AutoChain.Block pressBlock;
    private int pressFromChainIndex = -1;
    private long pressNanos;
    private double pressX;
    private double pressY;

    /** 正在滑动的链内滑块下标；-1 = 没有；0 = 积木盒的横向滑块；1 = 积木盒的纵向滑块。 */
    private int slideChainIndex = -1;
    private int slidePalette = -1;

    private final Screen origin;

    public ArrowRuleScreen(Screen origin) {
        super(Text.literal("箭头"));
        this.origin = origin;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** 关闭时回到自动化界面（包括按 Esc）。 */
    @Override
    public void close() {
        if (this.client != null && this.origin != null) {
            this.client.setScreen(this.origin);
        }
    }

    @Override
    public void init() {
        this.panelW = Math.min(MAX_W, this.width - 20);
        this.panelH = Math.min(440, this.height - 20);
        this.panelLeft = (this.width - this.panelW) / 2;
        this.panelTop = (this.height - this.panelH) / 2;
        final int pad = 14;
        this.contentLeft = this.panelLeft + pad;
        this.contentW = this.panelW - pad * 2;

        this.tabY = this.panelTop + 30;
        this.tabW = (this.contentW - 6) / 2;

        final int paletteH = BLOCK_H * 2 + 12; // 两行积木盒
        this.backY = this.panelTop + this.panelH - pad - BLOCK_H;
        this.paletteBottom = this.backY - 12;
        this.paletteTop = this.paletteBottom - paletteH;
        this.canvasTop = this.tabY + BLOCK_H + 12;
        this.canvasBottom = this.paletteTop - 10;

        this.chainX = this.contentLeft + 6;
        this.chainW = this.contentW - 12;

        // 重开界面时清掉残留的拖动状态
        this.dragBlock = null;
        this.pressBlock = null;
        this.slideChainIndex = -1;
        this.slidePalette = -1;
    }

    // ---------- 链布局 ----------

    private AutoChain chain() {
        return AutoTemplate.chain(this.mode);
    }

    /** 链中第 index 块积木的顶边 y（链首触发块下面按 LINK_LEN+BLOCK_H 排布）。 */
    private int blockTopY(int index) {
        return this.canvasTop + BLOCK_H + LINK_LEN + index * (LINK_LEN + BLOCK_H);
    }

    private boolean chainFitsOnScreen() {
        return blockTopY(chain().size() - 1) + BLOCK_H <= this.canvasBottom;
    }

    // ---------- 命中测试 ----------

    private int chainHit(double mx, double my) {
        if (mx < this.chainX || mx >= this.chainX + this.chainW) {
            return -1;
        }
        for (int i = chain().size() - 1; i >= 0; i--) {
            final int top = blockTopY(i);
            if (my >= top && my < top + BLOCK_H) {
                return i;
            }
        }
        return -1;
    }

    /** 积木盒布局：上行两块方向按钮；下行左右两根滑块。 */
    private int[] paletteSlotRect(int slot) {
        final int halfW = (this.contentW - 8) / 2;
        final int rowY = slot <= 1 ? this.paletteTop : this.paletteTop + BLOCK_H + 12;
        final int colX = (slot % 2 == 0) ? this.contentLeft : this.contentLeft + halfW + 8;
        return new int[]{colX, rowY, halfW, BLOCK_H};
    }

    private int paletteHit(double mx, double my) {
        for (int slot = 0; slot < 4; slot++) {
            final int[] r = paletteSlotRect(slot);
            if (PanelUi.hit(mx, my, r[0], r[1], r[2], r[3])) {
                return slot;
            }
        }
        return -1;
    }

    /** 积木盒槽位对应的类型。 */
    private static int slotType(int slot) {
        return switch (slot) {
            case 0 -> AutoChain.TYPE_DIR_UP;
            case 1 -> AutoChain.TYPE_DIR_DOWN;
            case 2 -> AutoChain.TYPE_OFF_X;
            default -> AutoChain.TYPE_OFF_Y;
        };
    }

    private float paletteValue(int type) {
        return type == AutoChain.TYPE_OFF_X ? AutoTemplate.paletteOffsetX : AutoTemplate.paletteOffsetY;
    }

    private void setPaletteValue(int type, float v) {
        if (type == AutoChain.TYPE_OFF_X) {
            AutoTemplate.paletteOffsetX = v;
        } else {
            AutoTemplate.paletteOffsetY = v;
        }
    }

    // ---------- 鼠标 ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // 返回
        if (PanelUi.hit(mouseX, mouseY, this.contentLeft, this.backY, this.contentW, BLOCK_H)) {
            this.close();
            return true;
        }
        // 页签
        if (mouseY >= this.tabY && mouseY < this.tabY + BLOCK_H) {
            if (mouseX >= this.contentLeft && mouseX < this.contentLeft + this.tabW) {
                this.mode = 0;
                return true;
            }
            if (mouseX >= this.contentLeft + this.tabW + 6 && mouseX < this.contentLeft + this.tabW * 2 + 6) {
                this.mode = 1;
                return true;
            }
        }
        if (this.dragBlock != null) {
            return true; // 正在拖，吞掉重复点击
        }
        // 链中积木：按下候选（方向块长按即拖；滑块块按住即滑）
        final int hitIdx = chainHit(mouseX, mouseY);
        if (hitIdx >= 0) {
            final AutoChain.Block b = chain().get(hitIdx);
            this.pressBlock = b;
            this.pressFromChainIndex = hitIdx;
            this.pressNanos = System.nanoTime();
            this.pressX = mouseX;
            this.pressY = mouseY;
            this.grabDX = mouseX - this.chainX;
            this.grabDY = mouseY - blockTopY(hitIdx);
            if (b.isSlider()) {
                this.slideChainIndex = hitIdx; // 按住滑块 = 滑
            }
            return true;
        }
        // 积木盒
        final int slot = paletteHit(mouseX, mouseY);
        if (slot >= 0) {
            final int type = slotType(slot);
            final int[] r = paletteSlotRect(slot);
            this.pressBlock = new AutoChain.Block(type, paletteValue(type));
            this.pressFromChainIndex = -1;
            this.pressNanos = System.nanoTime();
            this.pressX = mouseX;
            this.pressY = mouseY;
            this.grabDX = mouseX - r[0];
            this.grabDY = mouseY - r[1];
            if (type == AutoChain.TYPE_OFF_X) {
                this.slidePalette = 2;
            } else if (type == AutoChain.TYPE_OFF_Y) {
                this.slidePalette = 3;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.dragBlock != null) {
            this.dragX = mouseX - this.grabDX;
            this.dragY = mouseY - this.grabDY;
            return true;
        }
        if (this.pressBlock == null) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        final double dx = mouseX - this.pressX;
        final double dy = mouseY - this.pressY;
        final boolean longPressed = System.nanoTime() - this.pressNanos > LONG_PRESS_NS;

        if (!this.pressBlock.isSlider()) {
            // 方向块：点住移动就直接拖
            if (Math.abs(dx) + Math.abs(dy) > 3) {
                beginDrag(mouseX, mouseY);
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        // 滑块块：先判长按 / 竖直拿起，否则横向就是滑
        if (longPressed && Math.abs(dx) + Math.abs(dy) > 3) {
            beginDrag(mouseX, mouseY);
            return true;
        }
        if (Math.abs(dy) > 6 && Math.abs(dy) > Math.abs(dx)) {
            beginDrag(mouseX, mouseY);
            return true;
        }
        if (Math.abs(dx) > 1) {
            if (this.slideChainIndex >= 0) {
                setPaletteValue(this.pressBlock.type, slideValueFrom(this.pressBlock.value, dx, this.chainW));
                chain().insert(this.slideChainIndex, new AutoChain.Block(this.pressBlock.type, paletteValue(this.pressBlock.type)));
            } else if (this.slidePalette >= 0) {
                setPaletteValue(this.pressBlock.type, slideValueFrom(this.pressBlock.value, dx, paletteSlotRect(this.slidePalette)[2]));
                // 该类型已在链里（灰显）时，链中的数值一起更新
                final int ci = chain().indexOf(this.pressBlock.type);
                if (ci >= 0) {
                    chain().insert(ci, new AutoChain.Block(this.pressBlock.type, paletteValue(this.pressBlock.type)));
                }
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private static float slideValueFrom(float startValue, double dx, double slotWidth) {
        final float range = 0.6F; // -0.3 .. +0.3
        final float v = startValue + (float) (dx / Math.max(20, slotWidth - 44) * range);
        return PanelUi.clamp(v, -0.3F, 0.3F);
    }

    private void beginDrag(double mouseX, double mouseY) {
        if (this.pressBlock == null) {
            return;
        }
        // 方向块“点住即拖”；滑块块需要长按（上面已判）
        this.dragBlock = this.pressBlock;
        this.dragFromChainIndex = this.pressFromChainIndex;
        this.dragX = mouseX - this.grabDX;
        this.dragY = mouseY - this.grabDY;
        // 链里的块被拿起时先从链上摘下来（放下位置不合适会恢复）
        if (this.dragFromChainIndex >= 0) {
            chain().removeAt(this.dragFromChainIndex);
        }
        this.pressBlock = null;
        this.slideChainIndex = -1;
        this.slidePalette = -1;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragBlock != null) {
            dropDrag(mouseX, mouseY);
            return true;
        }
        this.pressBlock = null;
        this.slideChainIndex = -1;
        this.slidePalette = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void dropDrag(double mouseX, double mouseY) {
        final AutoChain.Block b = this.dragBlock;
        final int fromIndex = this.dragFromChainIndex;
        this.dragBlock = null;

        final boolean overCanvas = PanelUi.hit(mouseX, mouseY, this.contentLeft, this.canvasTop - BLOCK_H,
                this.contentW, this.canvasBottom - this.canvasTop + BLOCK_H);
        final boolean overPalette = mouseY >= this.paletteTop - 6;

        if (overCanvas && !overPalette) {
            // 插入链：按落点 y 找最近位置
            int index = chain().size();
            for (int i = 0; i < chain().size(); i++) {
                final int top = blockTopY(i);
                if (mouseY < top + BLOCK_H / 2.0) {
                    index = i;
                    break;
                }
            }
            chain().insert(index, new AutoChain.Block(b.type, b.value));
            return;
        }
        if (overPalette) {
            // 拖回积木盒 = 取下；滑块值留在盒里
            if (b.isSlider()) {
                setPaletteValue(b.type, b.value);
            }
            return;
        }
        // 松在别处：放回原位置
        if (fromIndex >= 0) {
            chain().insert(fromIndex, new AutoChain.Block(b.type, b.value));
        }
    }

    // ---------- 渲染 ----------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        PanelUi.fillRoundedRect(context, this.panelLeft, this.panelTop, this.panelW, this.panelH, 12, PanelUi.BORDER_COLOR);
        PanelUi.fillRoundedRect(context, this.panelLeft + 1, this.panelTop + 1, this.panelW - 2, this.panelH - 2, 11, PanelUi.BG_COLOR);

        context.drawCenteredTextWithShadow(this.textRenderer, "箭头", this.width / 2, this.panelTop + 12, PanelUi.TAB_ACTIVE_TEXT);
        context.drawCenteredTextWithShadow(this.textRenderer, "按住积木拖到中间组链，像图形化编程一样",
                this.width / 2, this.panelTop + 21, TEXT_DIM);

        // 页签：上行时 / 下行时
        drawTab(context, 0, "上行时", this.mode == 0);
        drawTab(context, 1, "下行时", this.mode == 1);

        renderChain(context, mouseX);
        renderPalette(context, mouseX, mouseY);

        // 拖动中的预览
        if (this.dragBlock != null) {
            drawBlock(context, (int) this.dragX, (int) this.dragY, this.chainW, this.dragBlock,
                    BG_BLOCK_HOVER, true);
        }

        // 返回
        PanelUi.drawRow(context, this.textRenderer, this.contentLeft, this.backY, this.contentW, BLOCK_H,
                "返回", hoverBg(mouseX, mouseY, this.contentLeft, this.backY, this.contentW, BLOCK_H), PanelUi.TAB_ACTIVE_TEXT);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawTab(DrawContext context, int index, String label, boolean active) {
        final int x = this.contentLeft + index * (this.tabW + 6);
        PanelUi.fillRoundedRect(context, x, this.tabY, this.tabW, BLOCK_H, 6, active ? PanelUi.TAB_ACTIVE_BG : PanelUi.TAB_INACTIVE_BG);
        if (active) {
            context.fill(x + 4, this.tabY + BLOCK_H - 2, x + this.tabW - 4, this.tabY + BLOCK_H - 1, 0xAAFFFFFF);
        }
        context.drawCenteredTextWithShadow(this.textRenderer, label, x + this.tabW / 2, this.tabY + (BLOCK_H - 8) / 2,
                active ? PanelUi.TAB_ACTIVE_TEXT : PanelUi.TAB_INACTIVE_TEXT);
    }

    private void renderChain(DrawContext context, int mouseX) {
        final AutoChain chain = chain();
        // 画布底：一块更暗的圆角背板 + 虚线似的边框，明确“这里能放积木”
        PanelUi.fillRoundedRect(context, this.contentLeft, this.canvasTop - BLOCK_H, this.contentW,
                this.canvasBottom - this.canvasTop + BLOCK_H, 8, 0x26000000);
        context.drawBorder(this.contentLeft, this.canvasTop - BLOCK_H, this.contentW,
                this.canvasBottom - this.canvasTop + BLOCK_H, 0x2AFFFFFF);

        // 触发块（链首）
        final int hatY = this.canvasTop;
        drawBlock(context, this.chainX, hatY, this.chainW, new AutoChain.Block(AutoChain.TYPE_DIR_UP, 0), BG_BLOCK, true,
                this.mode == 0 ? "上行时" : "下行时");

        final boolean fits = chainFitsOnScreen();
        for (int i = 0; i < chain.size(); i++) {
            final int top = blockTopY(i);
            // 连接直线（“一条长一点的直线”）
            context.fill(this.chainX + this.chainW / 2 - 1, top - LINK_LEN, this.chainX + this.chainW / 2 + 1, top, 0x66FFFFFF);
            drawBlock(context, this.chainX, top, this.chainW, chain.get(i),
                    chainHit(mouseX, top + BLOCK_H / 2.0) == i ? BG_BLOCK_HOVER : BG_BLOCK, fits);
        }
        if (chain.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "把下面的积木长按拖到这里",
                    this.panelLeft + this.panelW / 2, this.canvasTop + BLOCK_H + LINK_LEN + BLOCK_H / 2 - 4, TEXT_DIM);
        } else if (!fits) {
            context.drawCenteredTextWithShadow(this.textRenderer, "……链太长，画布放不下",
                    this.panelLeft + this.panelW / 2, this.canvasBottom - 10, TEXT_DIM);
        }
    }

    private void renderPalette(DrawContext context, int mouseX, int mouseY) {
        for (int slot = 0; slot < 4; slot++) {
            final int type = slotType(slot);
            final int[] r = paletteSlotRect(slot);
            // 该类型已在链里 → 画半透明“已在链上”的占位，避免误解为可重复添加
            final boolean inChain = chain().indexOf(type) >= 0;
            if (inChain) {
                drawSlot(context, r, ghostBlock(type), BG_SLOT, true);
            } else {
                drawSlot(context, r, new AutoChain.Block(type, paletteValue(type)),
                        hoverBg(mouseX, mouseY, r[0], r[1], r[2], r[3]), false);
            }
        }
        context.drawCenteredTextWithShadow(this.textRenderer, "积木盒 · 滑块：按住就滑 · 长按就拖",
                this.panelLeft + this.panelW / 2, this.paletteTop - 9, TEXT_DIM);
    }

    /** 已在链上的类型在积木盒里显示为灰块，避免误以为能重复添加。 */
    private AutoChain.Block ghostBlock(int type) {
        return new AutoChain.Block(type, paletteValue(type));
    }

    private int hoverBg(int mouseX, int mouseY, int x, int y, int w, int h) {
        return PanelUi.hit(mouseX, mouseY, x, y, w, h) ? BG_BLOCK_HOVER : BG_BLOCK;
    }

    private void drawSlot(DrawContext context, int[] r, AutoChain.Block b, int bg, boolean ghost) {
        final int color = ghost ? 0x50FFFFFF : BORDER_SLOT;
        PanelUi.fillRoundedRect(context, r[0], r[1], r[2], r[3], 6, bg);
        context.drawBorder(r[0], r[1], r[2], r[3], color);
        drawBlockContent(context, r[0], r[1], r[2], r[3], b, ghost);
    }

    /** 链上 / 拖动中的积木统一外观。 */
    private void drawBlock(DrawContext context, int x, int y, int w, AutoChain.Block b, int bg, boolean fits) {
        drawBlock(context, x, y, w, b, bg, fits, blockLabel(b));
    }

    private void drawBlock(DrawContext context, int x, int y, int w, AutoChain.Block b, int bg, boolean fits, String label) {
        PanelUi.fillRoundedRect(context, x, y, w, BLOCK_H, 6, bg);
        context.drawBorder(x, y, w, BLOCK_H, 0x50FFFFFF);
        drawBlockContent(context, x, y, w, BLOCK_H, b, !fits, label);
    }

    private void drawBlockContent(DrawContext context, int x, int y, int w, int h, AutoChain.Block b, boolean ghost) {
        drawBlockContent(context, x, y, w, h, b, ghost, blockLabel(b));
    }

    private void drawBlockContent(DrawContext context, int x, int y, int w, int h, AutoChain.Block b, boolean ghost, String label) {
        final int text = ghost ? 0x90FFFFFF : PanelUi.TAB_ACTIVE_TEXT;
        final int trackLeft = x + w - 66;
        if (b.isSlider()) {
            // 左半边放名称，右半截画轨道 + 圆头
            context.drawTextWithShadow(this.textRenderer, label, x + 8, y + (h - 8) / 2, text);
            final int trackY = y + h - 7;
            context.fill(trackLeft, trackY, x + w - 8, trackY + 1, ghost ? 0x50FFFFFF : 0x80FFFFFF);
            final double t = (b.value + 0.3) / 0.6;
            final int knobX = (int) (trackLeft + t * (x + w - 8 - trackLeft));
            PanelUi.fillRoundedRect(context, knobX - 3, trackY - 3, 7, 7, 3, ghost ? 0xB0FFFFFF : 0xFFFFFFFF);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, label, x + w / 2, y + (h - 8) / 2, text);
        }
    }

    private static String blockLabel(AutoChain.Block b) {
        return switch (b.type) {
            case AutoChain.TYPE_OFF_X -> "箭头偏移X:" + String.format("%.2f", b.value);
            case AutoChain.TYPE_OFF_Y -> "箭头偏移Y:" + String.format("%.2f", b.value);
            case AutoChain.TYPE_DIR_UP -> "箭头方向:向上";
            default -> "箭头方向:向下";
        };
    }
}
