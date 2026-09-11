package com.xiao.mtrliftcolornew.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import org.mtr.core.data.Lift;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mod.Init;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.ClientPacketHelper;

import com.xiao.mtrliftcolornew.config.LiftColorManager;
import com.xiao.mtrliftcolornew.network.LiftColorNetwork;

/**
 * 客户端入口。保存"是否正在放行 MTR 原逻辑"的开关，并提供打开圆形选择器、
 * 打开 MTR 原编辑器、打开调色面板的入口。
 *
 * <p>同时注册配色同步的网络通道：单人游戏里集成服务器需要服务端通道，所以客户端
 * 入口把两端的通道都注册上；专用服务器由 {@code MTRLiftColorServer} 单独注册。</p>
 */
public final class MTRLiftColorClient implements ClientModInitializer {

    private static boolean bypassMtr = false;

    /** 点 MTR 选项但电梯还没生成完（MTR 的生成是异步的）时，稍等几拍再重试打开编辑器。 */
    private static BlockPos pendingMtrEditorPos = null;
    private static int pendingMtrEditorTicks = 0;
    private static final int PENDING_TICKS = 60; // 最多等约 3 秒

    @Override
    public void onInitializeClient() {
        // 配置按世界存储：离开服务器时清空缓存，避免把上一个世界的配色带到新世界来。
        // 注意用 DISCONNECT 而不是 JOIN——服务器会在玩家加入时同步配色，若在 JOIN 里清缓存
        // 可能把刚同步过来的配色又清掉。
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LiftColorManager.clearCache());

        // 服务端通道：单人游戏（集成服务器）也需要
        LiftColorNetwork.registerServer();
        // 客户端通道：接收服务器广播的配色
        LiftColorNetwork.registerClient();

        // 处理"点 MTR 选项、电梯还没生成"的重试
        ClientTickEvents.END_CLIENT_TICK.register(client -> tickPendingMtrEditor());
    }

    /** 供 Mixin 判断：是否放行 MTR 的 openLiftCustomizationScreen 原逻辑。 */
    public static boolean isBypassingMtr() {
        return bypassMtr;
    }

    /** 用右键指向的方块找到对应电梯（与 MTR 自身逻辑一致）。 */
    public static Lift findLift(BlockPos blockPos) {
        if (blockPos == null) {
            return null;
        }
        final org.mtr.core.data.Position position = Init.blockPosToPosition(blockPos);
        final MinecraftClientData data = MinecraftClientData.getInstance();
        if (data == null) {
            return null;
        }
        // 与 MTR 的 openLiftCustomizationScreen 一致：遍历客户端已知的全部电梯
        for (Lift lift : data.lifts) {
            if (lift.getFloorIndex(position) >= 0) {
                return lift;
            }
        }
        return null;
    }

    /** 打开圆形冲突页选择器。 */
    public static void openRadialChooser(BlockPos blockPos) {
        MinecraftClient.getInstance().setScreen(new LiftColorRadialScreen(blockPos));
    }

    /**
     * 打开 MTR 原版的电梯编辑器（上半圆）。
     *
     * <p>MTR 生成电梯是异步的：重置器右键后，服务端先发"生成电梯"的消息、再发打开编辑器的
     * 包。如果编辑器打开时电梯还没同步到本机，这里会等几拍再试，保证"点 MTR 就自动放置电梯
     * 并打开原版编辑器"不被中断。</p>
     */
    public static void openMtrEditor(BlockPos blockPos) {
        final Lift lift = findLift(blockPos);
        if (lift != null) {
            openMtrEditorNow(blockPos);
            return;
        }
        // 电梯还没生成完（MTR 的生成是异步的）：先关掉圆形选择器，等电梯出现后自动打开编辑器
        MinecraftClient.getInstance().setScreen(null);
        pendingMtrEditorPos = blockPos;
        pendingMtrEditorTicks = PENDING_TICKS;
    }

    private static void openMtrEditorNow(BlockPos blockPos) {
        bypassMtr = true;
        try {
            ClientPacketHelper.openLiftCustomizationScreen(blockPos);
        } finally {
            bypassMtr = false;
        }
    }

    /** 每客户端 tick 调用：等待电梯出现后重试打开编辑器。 */
    private static void tickPendingMtrEditor() {
        if (pendingMtrEditorPos == null) {
            return;
        }
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.isPaused()) {
            return; // 不在世界 / 暂停中就不重试，等到恢复
        }
        if (client.currentScreen != null) {
            pendingMtrEditorPos = null; // 玩家打开了别的界面，不再强制弹出，尊重玩家
            return;
        }
        final BlockPos pos = pendingMtrEditorPos;
        if (findLift(pos) != null) {
            pendingMtrEditorPos = null;
            openMtrEditorNow(pos);
        } else if (--pendingMtrEditorTicks <= 0) {
            pendingMtrEditorPos = null; // 超时放弃，不再打扰玩家
        }
    }

    /** 打开我们的调色面板（下半圆）。 */
    public static void openColorPanel(Lift lift) {
        MinecraftClient.getInstance().setScreen(new ColorPanelScreen(lift));
    }
}
