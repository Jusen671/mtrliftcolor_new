package com.xiao.mtrliftcolornew.client;

import com.xiao.mtrliftcolornew.config.AutoChain;
import com.xiao.mtrliftcolornew.config.LiftColorConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 自动化「电梯显示设置」图形化编辑器：像图形化编程一样，把积木块拖进中间的一条链。
 *
 * <p>箭头和楼层文字是轿厢里同一块屏幕上的东西，所以两类积木放在同一个编辑器里：
 * 白底积木改箭头，青底积木改楼层文字。</p>
 *
 * <p>界面分四块：</p>
 * <ul>
 *   <li>顶部页签：选择正在编辑「上行时」还是「下行时」的链；</li>
 *   <li>中间画布：一条竖直的积木链，起点是「上行时 / 下行时」触发块，
 *       例如 {@code 上行时 ─ 箭头大小:1.20 ─ 文字偏移Y:0.05}；画布占据大部分空间，方便拖放；</li>
 *   <li>选中行：点一下链里（或积木盒里）的积木，这里出现它的数值输入框 ——
 *       可以打字，可以按住框左右拖，也可以用框内的 − / + 微调；旁边是「拆下这块」；</li>
 *   <li>底部积木盒：8 块积木两排。</li>
 * </ul>
 *
 * <p>操作方式：</p>
 * <ul>
 *   <li>点一下积木盒里的积木 = 直接加入链尾（手机上最顺手的方式）；</li>
 *   <li>按住积木移动 = 拿起积木拖到画布上想插的位置（长按也能拿起来）；</li>
 *   <li>按住链里的数值积木左右拖 = 直接改数值，上下拖 = 拿起来挪位置；</li>
 *   <li>把链里的积木拖回底部积木盒区域，或选中后按「拆下这块」= 从链上取下。</li>
 * </ul>
 */
public class ArrowRuleScreen extends Screen {

    private static final int MAX_W = 400;
    private static final int BG_BLOCK = 0x3AFFFFFF;
    /** 楼层文字类积木用偏浅的青色底，和箭头类区分开。 */
    private static final int BG_BLOCK_TEXT = 0x3AA0E0C0;
    private static final int BG_BLOCK_HOVER = 0x55FFFFFF;
    private static final int BG_SLOT = 0x28FFFFFF;
    private static final int BORDER_SLOT = 0x50FFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int SELECT_BORDER = 0xFFE0E0E0;
    /** 链里积木之间的连接线长度。 */
    private static final int LINK_LEN = 20;
    private static final int BLOCK_H = 18;
    /** 积木盒：4 列 2 行。 */
    private static final int PALETTE_COLS = 4;
    private static final int PALETTE_SLOTS = AutoChain.TYPES.length;
    /** 输入框与按钮的宽度。 */
    private static final int REMOVE_W = 62;

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

    /** 选中行与积木盒区域。 */
    private int editorY;
    private int editorFieldX;
    private int editorFieldW;
    private int paletteTop;
    private int paletteBottom;

    /** 页签 / 返回。 */
    private int tabY;
    private int tabW;
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

    /** 按下的积木（长按 / 点击候选）；null = 没有。 */
    private AutoChain.Block pressBlock;
    private int pressFromChainIndex = -1;
    /** 按下的是链里的数值块（左右拖 = 改值）。 */
    private boolean pressIsChainNumber;
    private double pressX;
    private double pressY;

    /** 选中的积木类型（同类型在一条链里最多一块，所以类型就是选中标记）；-1 = 没选。 */
    private int selectedType = -1;

    private NumberField valueField;

    private final Screen origin;

    public ArrowRuleScreen(Screen origin) {
        super(Text.literal("电梯显示设置"));
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
        super.init();  // 清掉上一次的孩子列表（窗口缩放会重新 init）
        this.panelW = Math.min(MAX_W, this.width - 20);
        this.panelH = Math.min(460, this.height - 16);
        this.panelLeft = (this.width - this.panelW) / 2;
        this.panelTop = (this.height - this.panelH) / 2;
        final int pad = 12;
        this.contentLeft = this.panelLeft + pad;
        this.contentW = this.panelW - pad * 2;

        this.tabY = this.panelTop + 28;
        this.tabW = (this.contentW - 6) / 2;

        this.backY = this.panelTop + this.panelH - pad - BLOCK_H;
        this.paletteBottom = this.backY - 6;
        final int paletteH = BLOCK_H * 2 + 4; // 两排积木
        this.paletteTop = this.paletteBottom - paletteH;
        this.editorY = this.paletteTop - BLOCK_H - 12;
        this.canvasTop = this.tabY + BLOCK_H + 10;
        this.canvasBottom = this.editorY - 8;

        this.chainX = this.contentLeft + 6;
        this.chainW = this.contentW - 12;

        final int labelW = Math.min(72, this.contentW / 5 + 12);
        this.editorFieldX = this.contentLeft + labelW;
        this.editorFieldW = Math.max(60, this.contentRight() - this.editorFieldX - REMOVE_W - 6);

        // 重开界面时清掉残留的拖动状态
        this.dragBlock = null;
        this.pressBlock = null;

        this.valueField = new NumberField(this.textRenderer, this.editorFieldX, this.editorY,
                this.editorFieldW, BLOCK_H, LiftColorConfig.MIN_OFFSET, LiftColorConfig.MAX_OFFSET, 0.01F, 2, 0F,
                this::applySelectedValue);
        this.valueField.visible = false;
        this.addDrawableChild(this.valueField);
        syncValueField();
    }

    private int contentRight() {
        return this.contentLeft + this.contentW;
    }

    /** 数值框 − / + 每次调整的幅度。 */
    private static float stepFor(int type) {
        return AutoChain.isScaleType(type) ? 0.05F : 0.01F;
    }

    /** 把选中行的输入框切成当前选中积木的区间与数值。 */
    private void syncValueField() {
        if (this.valueField == null) {
            return;
        }
        final int type = this.selectedType;
        if (type < 0 || !AutoChain.isNumeric(type)) {
            this.valueField.visible = false;
            this.valueField.active = false;
            return;
        }
        final float[] range = AutoChain.rangeOf(type);
        final int inChain = chain().indexOf(type);
        final float value = inChain >= 0 ? chain().get(inChain).value : AutoTemplate.paletteValue(type);
        this.valueField.visible = true;
        this.valueField.active = true;
        this.valueField.configure(range[0], range[1], stepFor(type), value);
    }

    /** 输入框改动后写回：链里有这块就改链，没有就改积木盒的默认值。 */
    private void applySelectedValue(float value) {
        final int type = this.selectedType;
        if (type < 0) {
            return;
        }
        AutoTemplate.setPaletteValue(type, value);
        final int index = chain().indexOf(type);
        if (index >= 0) {
            chain().set(index, new AutoChain.Block(type, value));
        }
    }

    // ---------- 链 ----------

    private AutoChain chain() {
        return AutoTemplate.chain(this.mode);
    }

    /** 链中第 index 块积木的顶边 y。 */
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

    /** 积木盒第 slot 格的矩形（4 列 2 行，格间留 6 像素）。 */
    private int[] paletteSlotRect(int slot) {
        final int gap = 6;
        final int slotW = (this.contentW - gap * (PALETTE_COLS - 1)) / PALETTE_COLS;
        final int row = slot / PALETTE_COLS;
        final int col = slot % PALETTE_COLS;
        final int x = this.contentLeft + col * (slotW + gap);
        final int y = this.paletteTop + row * (BLOCK_H + 4);
        return new int[]{x, y, slotW, BLOCK_H};
    }

    private int paletteHit(double mx, double my) {
        for (int slot = 0; slot < PALETTE_SLOTS; slot++) {
            final int[] r = paletteSlotRect(slot);
            if (PanelUi.hit(mx, my, r[0], r[1], r[2], r[3])) {
                return slot;
            }
        }
        return -1;
    }

    /** 积木盒槽位对应的积木类型。 */
    private static int slotType(int slot) {
        return AutoChain.TYPES[slot];
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
        // 「拆下这块」
        final int[] remove = removeButtonRect();
        if (remove != null && PanelUi.hit(mouseX, mouseY, remove[0], remove[1], remove[2], remove[3])) {
            removeSelected();
            return true;
        }
        // 页签
        if (mouseY >= this.tabY && mouseY < this.tabY + BLOCK_H) {
            if (mouseX >= this.contentLeft && mouseX < this.contentLeft + this.tabW) {
                setMode(0);
                return true;
            }
            if (mouseX >= this.contentLeft + this.tabW + 6 && mouseX < this.contentLeft + this.tabW * 2 + 6) {
                setMode(1);
                return true;
            }
        }
        if (this.dragBlock != null) {
            return true; // 正在拖，吞掉重复点击
        }
        // 链中积木：数值块按住左右拖 = 改值，点一下 = 选中；上下拖 = 拿起来
        final int hitIdx = chainHit(mouseX, mouseY);
        if (hitIdx >= 0) {
            final AutoChain.Block b = chain().get(hitIdx);
            select(b.type);
            this.pressBlock = b;
            this.pressFromChainIndex = hitIdx;
            this.pressX = mouseX;
            this.pressY = mouseY;
            this.pressIsChainNumber = AutoChain.isNumeric(b.type);
            this.grabDX = mouseX - this.chainX;
            this.grabDY = mouseY - blockTopY(hitIdx);
            return true;
        }
        // 积木盒
        final int slot = paletteHit(mouseX, mouseY);
        if (slot >= 0) {
            final int type = slotType(slot);
            final int[] r = paletteSlotRect(slot);
            select(type);
            this.pressBlock = new AutoChain.Block(type, AutoTemplate.paletteValue(type));
            this.pressFromChainIndex = -1;
            this.pressX = mouseX;
            this.pressY = mouseY;
            this.pressIsChainNumber = false; // 盒里的积木只用来拿，改数值在选中行里
            this.grabDX = mouseX - r[0];
            this.grabDY = mouseY - r[1];
            return true;
        }
        // 点到输入框以外的空白处 = 取消选中
        if (this.valueField != null && this.valueField.isMouseOver(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        select(-1);
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

        if (!this.pressIsChainNumber) {
            // 方向块 / 积木盒里的块：点住移动就直接拖
            if (Math.abs(dx) + Math.abs(dy) > 3) {
                beginDrag(mouseX, mouseY);
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        // 链里的数值块：上下拖 = 拿起来挪位置，左右拖 = 直接改值
        if (Math.abs(dy) > 6 && Math.abs(dy) > Math.abs(dx)) {
            beginDrag(mouseX, mouseY);
            return true;
        }
        if (Math.abs(dx) > 2) {
            slideValue(this.pressBlock, deltaX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    /** 按住数值块左右拖改值：横向拖满整条链宽正好走完该类型的取值区间。 */
    private void slideValue(AutoChain.Block block, double deltaX) {
        final float[] range = AutoChain.rangeOf(block.type);
        final float unit = (range[1] - range[0]) / Math.max(20, this.chainW);
        final float next = AutoChain.clampValue(block.type, block.value + (float) deltaX * unit);
        // 拖动过程中换掉 pressBlock 里的当前值，避免每帧都从按下时的值重来
        this.pressBlock = new AutoChain.Block(block.type, next);
        final int index = chain().indexOf(block.type);
        if (index >= 0) {
            chain().set(index, this.pressBlock);
        }
        AutoTemplate.setPaletteValue(block.type, next);
        if (this.selectedType == block.type && this.valueField != null) {
            this.valueField.setValue(next);
        }
    }

    private void beginDrag(double mouseX, double mouseY) {
        if (this.pressBlock == null) {
            return;
        }
        this.dragBlock = this.pressBlock;
        this.dragFromChainIndex = this.pressFromChainIndex;
        this.dragX = mouseX - this.grabDX;
        this.dragY = mouseY - this.grabDY;
        // 链里的块被拿起时先从链上摘下来（放下位置不合适会恢复）
        if (this.dragFromChainIndex >= 0) {
            chain().removeAt(this.dragFromChainIndex);
        }
        this.pressBlock = null;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragBlock != null) {
            dropDrag(mouseX, mouseY);
            return true;
        }
        // 点住又松开、既没拖也没滑 = 一次「点击」：积木盒里的积木直接加进链
        if (this.pressBlock != null && this.pressFromChainIndex < 0 && !movedMuch(mouseX, mouseY)) {
            tapAddPaletteBlock(this.pressBlock);
        }
        this.pressBlock = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean movedMuch(double mouseX, double mouseY) {
        return Math.abs(mouseX - this.pressX) + Math.abs(mouseY - this.pressY) > 4;
    }

    /** 点积木盒 = 加到链尾（已在链里就只选中，不重复添加）。 */
    private void tapAddPaletteBlock(AutoChain.Block block) {
        if (chain().indexOf(block.type) < 0) {
            chain().insert(chain().size(), new AutoChain.Block(block.type, AutoTemplate.paletteValue(block.type)));
        }
        select(block.type);
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
            select(b.type);
            return;
        }
        if (overPalette) {
            // 拖回积木盒 = 取下；数值留在盒里当下次的初始值
            AutoTemplate.setPaletteValue(b.type, b.value);
            if (this.selectedType == b.type) {
                syncValueField();
            }
            return;
        }
        // 松在别处：放回原位置
        if (fromIndex >= 0) {
            chain().insert(fromIndex, new AutoChain.Block(b.type, b.value));
        }
    }

    /** 选中某个类型的积木：只切换输入框的区间与可见性，不重建控件。 */
    private void select(int type) {
        if (this.selectedType == type && (type < 0 || !AutoChain.isNumeric(type))) {
            return;
        }
        this.selectedType = type;
        syncValueField();
    }

    private void setMode(int newMode) {
        if (this.mode == newMode) {
            return;
        }
        this.mode = newMode;
        this.dragBlock = null;
        this.pressBlock = null;
        this.selectedType = -1;
        syncValueField();
    }

    /** 选中行的「拆下这块」按钮位置；当前选中不在链里时返回 null（不显示）。 */
    private int[] removeButtonRect() {
        if (this.selectedType < 0 || chain().indexOf(this.selectedType) < 0) {
            return null;
        }
        return new int[]{this.contentRight() - REMOVE_W, this.editorY, REMOVE_W, BLOCK_H};
    }

    private void removeSelected() {
        final int index = chain().indexOf(this.selectedType);
        if (index >= 0) {
            chain().removeAt(index);
        }
        this.selectedType = -1;
        syncValueField();
    }

    // ---------- 渲染 ----------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        PanelUi.fillRoundedRect(context, this.panelLeft, this.panelTop, this.panelW, this.panelH, 12, PanelUi.BORDER_COLOR);
        PanelUi.fillRoundedRect(context, this.panelLeft + 1, this.panelTop + 1, this.panelW - 2, this.panelH - 2, 11, PanelUi.BG_COLOR);

        context.drawCenteredTextWithShadow(this.textRenderer, "电梯显示设置", this.width / 2, this.panelTop + 6, PanelUi.TAB_ACTIVE_TEXT);
        context.drawCenteredTextWithShadow(this.textRenderer, "箭头与楼层文字 · 拖积木组链，点积木改数值",
                this.width / 2, this.panelTop + 16, TEXT_DIM);

        // 页签：上行时 / 下行时
        drawTab(context, 0, "上行时", this.mode == 0, mouseX, mouseY);
        drawTab(context, 1, "下行时", this.mode == 1, mouseX, mouseY);

        renderChain(context, mouseX, mouseY);
        renderEditor(context, mouseX, mouseY);
        renderPalette(context, mouseX, mouseY);

        // 拖动中的预览
        if (this.dragBlock != null) {
            drawBlock(context, (int) this.dragX, (int) this.dragY, this.chainW, this.dragBlock,
                    BG_BLOCK_HOVER, true, this.dragBlock.text(), false);
        }

        // 返回
        PanelUi.drawRow(context, this.textRenderer, this.contentLeft, this.backY, this.contentW, BLOCK_H,
                "返回", hoverBg(mouseX, mouseY, this.contentLeft, this.backY, this.contentW, BLOCK_H), PanelUi.TAB_ACTIVE_TEXT);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawTab(DrawContext context, int index, String label, boolean active, int mouseX, int mouseY) {
        final int x = this.contentLeft + index * (this.tabW + 6);
        final boolean hover = PanelUi.hit(mouseX, mouseY, x, this.tabY, this.tabW, BLOCK_H);
        PanelUi.fillRoundedRect(context, x, this.tabY, this.tabW, BLOCK_H, 6,
                active ? PanelUi.TAB_ACTIVE_BG : (hover ? PanelUi.TAB_HOVER_BG : PanelUi.TAB_INACTIVE_BG));
        if (active) {
            context.fill(x + 4, this.tabY + BLOCK_H - 2, x + this.tabW - 4, this.tabY + BLOCK_H - 1, 0xAAFFFFFF);
        }
        context.drawCenteredTextWithShadow(this.textRenderer, label, x + this.tabW / 2,
                this.tabY + (BLOCK_H - this.textRenderer.fontHeight) / 2,
                active ? PanelUi.TAB_ACTIVE_TEXT : PanelUi.TAB_INACTIVE_TEXT);
    }

    private void renderChain(DrawContext context, int mouseX, int mouseY) {
        final AutoChain chain = chain();
        // 画布底：一块更暗的圆角背板 + 边框，明确“这里能放积木”，并且留足拖放空间
        PanelUi.fillRoundedRect(context, this.contentLeft, this.canvasTop - 4, this.contentW,
                this.canvasBottom - this.canvasTop + 8, 8, 0x33000000);
        context.drawBorder(this.contentLeft, this.canvasTop - 4, this.contentW,
                this.canvasBottom - this.canvasTop + 8, 0x3AFFFFFF);

        // 触发块（链首）
        drawBlock(context, this.chainX, this.canvasTop, this.chainW,
                new AutoChain.Block(AutoChain.TYPE_DIR_UP, 0), BG_BLOCK, true,
                this.mode == 0 ? "上行时" : "下行时", false);

        final boolean fits = chainFitsOnScreen();
        for (int i = 0; i < chain.size(); i++) {
            final AutoChain.Block b = chain.get(i);
            final int top = blockTopY(i);
            // 连接直线
            context.fill(this.chainX + this.chainW / 2 - 1, top - LINK_LEN, this.chainX + this.chainW / 2 + 1, top, 0x66FFFFFF);
            drawBlock(context, this.chainX, top, this.chainW, b,
                    chainHit(mouseX, mouseY) == i ? BG_BLOCK_HOVER : blockBg(b.type),
                    fits, b.text(), this.selectedType == b.type);
        }
        if (chain.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "点下面的积木直接加入，或按住拖到合适的位置",
                    this.panelLeft + this.panelW / 2, this.canvasTop + BLOCK_H + LINK_LEN + BLOCK_H / 2 - 4, TEXT_DIM);
        } else if (!fits) {
            context.drawCenteredTextWithShadow(this.textRenderer, "……链太长，画布放不下",
                    this.panelLeft + this.panelW / 2, this.canvasBottom - 10, TEXT_DIM);
        }
    }

    /** 选中行：左边写选中的积木名，中间是数值输入框，右边是「拆下这块」。 */
    private void renderEditor(DrawContext context, int mouseX, int mouseY) {
        if (this.selectedType < 0) {
            context.drawCenteredTextWithShadow(this.textRenderer, "选中一块积木后可以在这里改数值",
                    this.panelLeft + this.panelW / 2, this.editorY + (BLOCK_H - this.textRenderer.fontHeight) / 2, TEXT_DIM);
            return;
        }
        context.drawTextWithShadow(this.textRenderer, AutoChain.label(this.selectedType), this.contentLeft,
                this.editorY + (BLOCK_H - this.textRenderer.fontHeight) / 2, PanelUi.TAB_ACTIVE_TEXT);
        if (!AutoChain.isNumeric(this.selectedType)) {
            // 方向块没有数值，提示一下用法
            context.drawTextWithShadow(this.textRenderer, "方向块没有数值，拖它换位置",
                    this.editorFieldX, this.editorY + (BLOCK_H - this.textRenderer.fontHeight) / 2, TEXT_DIM);
        }
        final int[] rect = removeButtonRect();
        if (rect != null) {
            PanelUi.drawRow(context, this.textRenderer, rect[0], rect[1], rect[2], rect[3],
                    "拆下这块", hoverBg(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3]), PanelUi.TAB_ACTIVE_TEXT);
        }
    }

    private void renderPalette(DrawContext context, int mouseX, int mouseY) {
        context.drawCenteredTextWithShadow(this.textRenderer, "积木盒 · 青底是楼层文字，白底是箭头",
                this.panelLeft + this.panelW / 2, this.paletteTop - 9, TEXT_DIM);
        for (int slot = 0; slot < PALETTE_SLOTS; slot++) {
            final int type = slotType(slot);
            final int[] r = paletteSlotRect(slot);
            final AutoChain.Block b = new AutoChain.Block(type, AutoTemplate.paletteValue(type));
            final boolean inChain = chain().indexOf(type) >= 0;
            if (inChain) {
                // 已在链上的类型画成灰块，避免误以为能重复添加
                PanelUi.fillRoundedRect(context, r[0], r[1], r[2], r[3], 5, BG_SLOT);
                context.drawBorder(r[0], r[1], r[2], r[3], BORDER_SLOT);
                drawBlockContent(context, r[0], r[1], r[2], r[3], b, true, b.text());
            } else {
                final boolean hover = PanelUi.hit(mouseX, mouseY, r[0], r[1], r[2], r[3]);
                PanelUi.fillRoundedRect(context, r[0], r[1], r[2], r[3], 5, hover ? BG_BLOCK_HOVER : blockBg(type));
                context.drawBorder(r[0], r[1], r[2], r[3], BORDER_SLOT);
                drawBlockContent(context, r[0], r[1], r[2], r[3], b, false, b.text());
            }
            if (this.selectedType == type) {
                context.drawBorder(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, SELECT_BORDER);
            }
        }
    }

    /** 同一链里同类型只会有一块，所以按类型上色就能分成箭头 / 楼层文字两组。 */
    private static int blockBg(int type) {
        return "楼层文字".equals(AutoChain.group(type)) ? BG_BLOCK_TEXT : BG_BLOCK;
    }

    private int hoverBg(int mouseX, int mouseY, int x, int y, int w, int h) {
        return PanelUi.hit(mouseX, mouseY, x, y, w, h) ? BG_BLOCK_HOVER : BG_BLOCK;
    }

    private void drawBlock(DrawContext context, int x, int y, int w, AutoChain.Block b, int bg, boolean fits,
                           String label, boolean selected) {
        PanelUi.fillRoundedRect(context, x, y, w, BLOCK_H, 5, bg);
        context.drawBorder(x, y, w, BLOCK_H, selected ? SELECT_BORDER : 0x50FFFFFF);
        drawBlockContent(context, x, y, w, BLOCK_H, b, !fits, label);
    }

    /** 积木上的文字：数值块名称靠左、数值靠右（原来的小滑块轨道已换成选中行的输入框）。 */
    private void drawBlockContent(DrawContext context, int x, int y, int w, int h, AutoChain.Block b,
                                  boolean ghost, String label) {
        final int text = ghost ? 0x90FFFFFF : PanelUi.TAB_ACTIVE_TEXT;
        final int textY = y + (h - this.textRenderer.fontHeight) / 2;
        if (AutoChain.isNumeric(b.type)) {
            context.drawTextWithShadow(this.textRenderer, label, x + 6, textY, text);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, label, x + w / 2, textY, text);
        }
    }
}
