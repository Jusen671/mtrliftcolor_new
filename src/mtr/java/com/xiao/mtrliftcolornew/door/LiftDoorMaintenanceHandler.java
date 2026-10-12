package com.xiao.mtrliftcolornew.door;

import com.xiao.mtrliftcolornew.Safety;
import com.xiao.mtrliftcolornew.network.LiftColorNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务器端：受理一次三角钥匙检修开关门。
 *
 * <p>这里只做「验包 + 落状态 + 广播」，<b>不直接碰电梯</b>：电梯的运动由 MTR 私有仿真线程驱动，
 * 从网络线程去改它的速度 / 进度是不安全的。状态落进 {@link LiftMaintenanceRegistry} 之后，
 * 仿真线程在下一次 {@code Lift.tick} 里看到自己这台电梯被联锁住了，再就地停稳
 * （见 {@code LiftDoorMixin}）。</p>
 *
 * <p>验三件事，任何一条不过就<b>回一条驳回包</b>让客户端撤销它本地记的那几格门
 * ——绝不能默默丢弃：客户端已经把这一下点下去了，不撤销就会出现门卡在假开、电梯被永久钉住。</p>
 * <ol>
 *   <li>区块已加载 —— 否则要么过期要么是伪造的；</li>
 *   <li>点到的那一格（或紧邻）确实是电梯层门 —— 触屏准星容易偏半格，判定与客户端同款放宽；</li>
 *   <li>玩家离得够近 —— 不能站在地面远程锁别人的电梯。</li>
 * </ol>
 */
public final class LiftDoorMaintenanceHandler {

    /** 受理距离（格）。留一点余量，允许在门口往后站半步操作。 */
    private static final double MAX_DISTANCE = 10.0D;

    private LiftDoorMaintenanceHandler() {
    }

    /**
     * 服务端处理一次切换。
     *
     * @param floorIndex 客户端认出来的楼层索引；-1 表示它也没认出来（只影响轿厢门跟不跟着开）
     * @param clientKeys 客户端已经记下的整组门坐标；任何一步驳回时原样回传，客户端据此撤销本地状态
     */
    public static void toggle(MinecraftServer server, ServerPlayerEntity player, long liftId,
                              int floorIndex, BlockPos clicked, boolean open, long durationMs,
                              List<Long> clientKeys) {
        Safety.run("检修门请求受理", () -> {
            if (server == null || player == null || liftId < 0L) {
                return;
            }
            final World world = player.getEntityWorld();
            if (world == null || !world.isChunkLoaded(clicked)) {
                LiftColorNetwork.sendDoorReject(player, liftId, clientKeys);
                return;
            }
            // 与客户端同款的宽松判定：触屏准星偏半格点到的门框 / 面板也算点对了门
            final BlockPos door = LiftDoorGroup.normalizeDoor(world, clicked);
            if (door == null) {
                LiftColorNetwork.sendDoorReject(player, liftId, clientKeys);
                return;
            }
            if (player.squaredDistanceTo(door.getX() + 0.5, door.getY() + 0.5, door.getZ() + 0.5)
                    > MAX_DISTANCE * MAX_DISTANCE) {
                LiftColorNetwork.sendDoorReject(player, liftId, clientKeys);
                return;
            }
            final long safeDuration = LiftDoorService.clampMaintenanceMs(durationMs);
            // 由服务器重算整组门坐标并随包下发，保证所有玩家改的是同一批方块
            final List<Long> keys = LiftDoorGroup.toKeys(LiftDoorGroup.resolve(world, door));
            LiftMaintenanceRegistry.apply(liftId, floorIndex, keys, open, safeDuration);
            LiftColorNetwork.broadcastDoorState(server, liftId, floorIndex, keys, open, safeDuration);
            // 服务器算出的门格集合若与客户端本地的不一致，客户端那些多余记录也要撤销，
            // 否则会留下几格永远开着的假门（同一批 key 时 reject 是空操作）。
            final List<Long> stale = new ArrayList<>(clientKeys);
            stale.removeAll(keys);
            if (!stale.isEmpty()) {
                LiftColorNetwork.sendDoorReject(player, liftId, stale);
            }
        });
    }

    /** 世界卸载 / 关服时清空服务端状态。 */
    public static void clear() {
        LiftMaintenanceRegistry.clear();
    }
}
