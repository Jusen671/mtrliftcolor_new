package com.xiao.mtrliftcolornew.client;

import com.xiao.mtrliftcolornew.config.LiftColorManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 「自动化」界面（测试功能）：图形化积木式地调整电梯箭头的方向与位置偏移。
 *
 * <p>外观与调色面板完全一致：同一块圆角半透明面板、同一套页签 / 行按钮配色，
 * 所有按钮都是自绘的圆角行，不使用原版灰色按钮。</p>
 *
 * <p>点「箭头」进入 {@link ArrowRuleScreen}：像图形化编程一样，把
 * 「箭头方向:向上 / 向下」和两根「箭头偏移」滑块拖进
 * {@code 上行时 ─ 箭头偏移X:0.12 ─ 箭头方向:向上} 这样的链里；
 * 上行 / 下行各拼一条，回到本页点「应用到电梯」即可按范围批量下发。</p>
 *
 * <p>配置以「每台电梯一份 JSON」落在存档里
 * （{@code mtrliftcolor/<维度>/lift_configs/<短码>/auto/<十六进制id>.json}），
 * 「导出」把全部自动化配置集中复制到 {@code mtrliftcolor/output/}，
 * 「导入」从该目录读回——都是让服务器执行，结果看游戏聊天栏。</p>
 */
public class AutomationScreen extends Screen {

    private static final int MAX_W = 340;
    private static final int MAX_H = 320;
    private static final int ROW_H = 20;
    private static final int ROW_GAP = 7;

    private static final String[] SCOPE_LABELS = {"作用范围: 仅当前维度的电梯", "作用范围: 本世界全部已存电梯"};

    private final Screen origin;

    /** 一行自绘按钮。 */
    private static final class Row {
        final int x;
        final int y;
        final int w;
        final int h;
        String label;
        final Runnable action;

        Row(int x, int y, int w, int h, String label, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.action = action;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private boolean onlyCurrentDimension = true;
    private String statusMessage = "";
    private int statusColor = PanelUi.SUBTLE_TEXT;

    private int panelLeft;
    private int panelTop;
    private int panelW;
    private int panelH;
    private int contentLeft;
    private int contentW;

    public AutomationScreen(Screen origin) {
        super(Text.literal("自动化"));
        this.origin = origin; // 1.20.1 的 Screen 没有 parent 机制，返回时手动切回调色面板
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** 关闭时回到调色面板（包括按 Esc）。 */
    @Override
    public void close() {
        if (this.client != null && this.origin != null) {
            this.client.setScreen(this.origin);
        }
    }

    @Override
    public void init() {
        this.rows.clear();
        this.panelW = Math.min(MAX_W, this.width - 24);
        this.panelH = Math.min(MAX_H, this.height - 24);
        this.panelLeft = (this.width - this.panelW) / 2;
        this.panelTop = (this.height - this.panelH) / 2;
        final int pad = 14;
        this.contentLeft = this.panelLeft + pad;
        this.contentW = this.panelW - pad * 2;

        int y = this.panelTop + pad + 40;

        this.addRow(this.contentLeft, y, this.contentW, ROW_H, "箭头 →",
                () -> MinecraftClient.getInstance().setScreen(new ArrowRuleScreen(this)));
        y += ROW_H + ROW_GAP;

        this.addRow(this.contentLeft, y, this.contentW, ROW_H, scopeLabel(), this::cycleScope);
        y += ROW_H + ROW_GAP;

        this.addRow(this.contentLeft, y, this.contentW, ROW_H, "应用到电梯", this::runAutomation);
        y += ROW_H + ROW_GAP + 3;

        final int halfW = (this.contentW - 6) / 2;
        this.addRow(this.contentLeft, y, halfW, ROW_H, "导出配置", this::requestExport);
        this.addRow(this.contentLeft + halfW + 6, y, halfW, ROW_H, "导入配置", this::requestImport);

        this.addRow(this.contentLeft, this.panelTop + this.panelH - pad - ROW_H, this.contentW, ROW_H,
                "返回", this::close);
    }

    private void addRow(int x, int y, int w, int h, String label, Runnable action) {
        this.rows.add(new Row(x, y, w, h, label, action));
    }

    private String scopeLabel() {
        return SCOPE_LABELS[this.onlyCurrentDimension ? 0 : 1];
    }

    // ---------- 操作 ----------

    /** 作用范围行就地换文案（位置与其他行保持不变）。 */
    private void cycleScope() {
        this.onlyCurrentDimension = !this.onlyCurrentDimension;
        for (final Row row : this.rows) {
            if (row.label.startsWith("作用范围")) {
                row.label = scopeLabel();
                break;
            }
        }
    }

    private void runAutomation() {
        final World world = this.client == null ? null : this.client.world;
        if (world == null) {
            setStatus("需要先进入一个世界", PanelUi.STATUS_FAIL);
            return;
        }
        if (AutoTemplate.UP.isEmpty() && AutoTemplate.DOWN.isEmpty()) {
            setStatus("链还是空的：先进「箭头」拼一条积木链", PanelUi.STATUS_FAIL);
            return;
        }
        final int n = LiftColorManager.applyTemplate(world, this.onlyCurrentDimension, AutoTemplate.UP, AutoTemplate.DOWN);
        if (n < 0) {
            setStatus("需要先进入一个世界", PanelUi.STATUS_FAIL);
            return;
        }
        if (n == 0) {
            setStatus("没有匹配的电梯记录（先给电梯保存一次配色）", PanelUi.STATUS_FAIL);
            return;
        }
        setStatus(String.format("已把模板下发到 %d 台电梯（%s）", n, AutoTemplate.summary()), PanelUi.STATUS_OK);
    }

    private void requestExport() {
        LiftColorManager.requestExport();
        setStatus("已发送导出请求，结果见游戏聊天栏", PanelUi.SUBTLE_TEXT);
    }

    private void requestImport() {
        LiftColorManager.requestImport();
        setStatus("已发送导入请求，结果见游戏聊天栏", PanelUi.SUBTLE_TEXT);
    }

    private void setStatus(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
    }

    // ---------- 绘制 ----------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        // 与调色面板同款的圆角面板：淡描边 + 半透明暗底
        PanelUi.fillRoundedRect(context, this.panelLeft, this.panelTop, this.panelW, this.panelH, 12, PanelUi.BORDER_COLOR);
        PanelUi.fillRoundedRect(context, this.panelLeft + 1, this.panelTop + 1, this.panelW - 2, this.panelH - 2, 11, PanelUi.BG_COLOR);

        context.drawCenteredTextWithShadow(this.textRenderer, "自动化", this.panelLeft + this.panelW / 2, this.panelTop + 12, PanelUi.TAB_ACTIVE_TEXT);
        context.drawCenteredTextWithShadow(this.textRenderer,
                "已加载电梯配置 " + LiftColorManager.recordCount() + " 台 · " + AutoTemplate.summary(),
                this.panelLeft + this.panelW / 2, this.panelTop + 23, PanelUi.SUBTLE_TEXT);

        for (final Row row : this.rows) {
            final boolean hover = PanelUi.hit(mouseX, mouseY, row.x, row.y, row.w, row.h);
            final boolean primary = "应用到电梯".equals(row.label) || "箭头 →".equals(row.label);
            PanelUi.drawRow(context, this.textRenderer, row.x, row.y, row.w, row.h, row.label,
                    hover ? PanelUi.TAB_ACTIVE_BG : (primary ? PanelUi.TAB_HOVER_BG : PanelUi.TAB_INACTIVE_BG),
                    PanelUi.TAB_ACTIVE_TEXT);
        }

        if (!this.statusMessage.isEmpty()) {
            String shown = this.statusMessage;
            final int maxX = this.panelW - 28;
            while (this.textRenderer.getWidth(shown) > maxX && shown.length() > 3) {
                shown = shown.substring(0, shown.length() - 3);
            }
            if (!shown.equals(this.statusMessage)) {
                shown = shown + "…";
            }
            context.drawCenteredTextWithShadow(this.textRenderer, shown, this.panelLeft + this.panelW / 2,
                    this.panelTop + this.panelH - 64, this.statusColor);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (final Row row : this.rows) {
                if (PanelUi.hit(mouseX, mouseY, row.x, row.y, row.w, row.h)) {
                    row.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
