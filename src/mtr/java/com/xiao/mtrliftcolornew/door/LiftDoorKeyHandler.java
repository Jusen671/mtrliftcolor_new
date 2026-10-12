package com.xiao.mtrliftcolornew.door;

import com.xiao.mtrliftcolornew.Safety;
import com.xiao.mtrliftcolornew.config.LiftColorConfig;
import com.xiao.mtrliftcolornew.config.LiftColorManager;
import com.xiao.mtrliftcolornew.network.LiftColorNetwork;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.mod.item.ItemDriverKey;

import java.util.List;

/**
 * 客户端：处理「手持 MTR 车辆驾驶钥匙（三角钥匙）右击电梯层门」。
 *
 * <p>本模组<b>不新增任何物品</b>：直接复用 MTR 自带的驾驶钥匙，基础 / 高级 / 创造三种都算，
 * 也不看钥匙自己的 {@code canOpenDoors} 标记——现实里的三角钥匙本来就能开层门。
 * 潜行右击被放行，方便照常对车辆使用钥匙。</p>
 *
 * <p>一次右击 = <b>切换</b>：这扇门关着就缓缓打开、并且<b>一直保持</b>；已经开着就缓缓关上。
 * 门开着的时候电梯被检修联锁钉在原地（见 {@code LiftDoorMixin}），层门关严之后
 * 之前在轿厢里按下的楼层会接着跑完。</p>
 *
 * <p>状态以服务器为权威：把请求（含本地解析的整组门坐标）发给服务器，
 * 服务器验过再广播回来，所有玩家看到同一段动画。<b>服务器驳回时客户端会收到撤销包</b>，
 * 不会出现门卡在假开状态、电梯被永久钉住的情况。对端没有本模组时才退化为纯本地开门。</p>
 */
@Environment(EnvType.CLIENT)
public final class LiftDoorKeyHandler {

    /**
     * 钥匙右击的受理距离。手机触屏准星容易偏、站着操作也够不到门的正中，
     * 这里放宽到 12 格；服务器还会按它自己的 10 格二次把关，不会真的允许隔半张图开门。
     */
    private static final double MAX_REACH = 12.0D;

    private LiftDoorKeyHandler() {
    }

    /** 在客户端入口注册。 */
    public static void registerClient() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) ->
                Safety.call("钥匙右击处理", () -> onUseBlock(player, world, hand, hit), ActionResult.PASS));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LiftMaintenanceRegistry.clear());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> LiftMaintenanceRegistry.clear());
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (!world.isClient || player.isSneaking()) {
            return ActionResult.PASS; // 服务器端不处理；潜行时把右击还给原逻辑
        }
        final ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof ItemDriverKey)) {
            return ActionResult.PASS;
        }
        // 触屏准星容易偏半格：点到门框、呼梯面板也按点到了这扇门算
        final BlockPos clicked = LiftDoorGroup.normalizeDoor(world, hit.getBlockPos());
        if (clicked == null) {
            return ActionResult.PASS;
        }
        if (player.squaredDistanceTo(clicked.getX() + 0.5, clicked.getY() + 0.5, clicked.getZ() + 0.5)
                > MAX_REACH * MAX_REACH) {
            return ActionResult.PASS;
        }
        final long liftId = LiftDoorLookup.findLiftId(clicked);
        if (liftId < 0L) {
            return ActionResult.PASS; // 认不出是哪台电梯就别动手，免得误停别的电梯
        }

        final List<Long> keys = LiftDoorGroup.toKeys(LiftDoorGroup.resolve(world, clicked));
        final int floorIndex = LiftDoorLookup.floorIndexOf(liftId, clicked);
        final boolean open = !LiftMaintenanceRegistry.isDoorOpen(liftId, keys);
        final long durationMs = maintenanceDuration(liftId, open);

        player.swingHand(hand);
        // 状态以服务器为准：单人 / 服务器装了本模组时等它广播回来（同一刻内就到，手感无差别），
        // 服务器不在或没装本模组才退化为纯本地开门（电梯不会停，但门一定有反馈）。
        // 不再「本地先斩后奏」：以前服务器驳回时本地那颗乐观打开的门会永远卡着、电梯被永久钉住。
        if (!LiftColorNetwork.sendDoorToggleToServer(liftId, floorIndex, clicked, open, durationMs, keys)) {
            LiftMaintenanceRegistry.apply(liftId, floorIndex, keys, open, durationMs);
        }
        return ActionResult.SUCCESS; // 消费这次右击，不再触发方块与物品自身的其他逻辑
    }

    /**
     * 本轮动画时长：优先用这台电梯自己存过的设置，没设置过就用默认值。
     *
     * <p>时长在切换那一刻定死并随包广播，所以所有人看到的开合速度一致；
     * 这里不做随机，随机出来的「快一点慢一点」在玩家眼里只是不稳定。</p>
     */
    private static long maintenanceDuration(long liftId, boolean open) {
        final LiftColorConfig config = LiftColorManager.getById(liftId);
        return LiftDoorService.clampMaintenanceMs(open ? config.doorOpenMs : config.doorCloseMs);
    }
}
