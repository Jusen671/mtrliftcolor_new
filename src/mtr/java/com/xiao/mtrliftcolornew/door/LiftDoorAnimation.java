package com.xiao.mtrliftcolornew.door;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.mod.block.BlockPSDAPGDoorBase;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 客户端本地驱动的「被钥匙打开的那扇门」。
 *
 * <p>正常情况下服务器收到请求后会把电梯的开门冷却同步过来，MTR 的渲染会按
 * {@link LiftDoorService#doorValue(long, long)} 的慢速曲线把这扇门与轿门一起开合。
 * 但如果那一格找不到对应电梯（例如电梯刚放好还没同步到本机、或者服务器上没有本模组），
 * 玩家点了钥匙却毫无反应会很困惑，所以这里直接对被点的那扇门做同样的开合动画。</p>
 *
 * <p>每客户端 tick 写一次门开度；轿厢就在这一层时 MTR 每帧也会写同样的曲线（两边时长完全一致），
 * 两者不会互相拉扯。一轮结束后不再写入，门方块自己按原版逻辑收尾。</p>
 */
public final class LiftDoorAnimation {

    /** 门坐标 → 本轮开始时间（毫秒）。只在客户端线程访问。 */
    private static final Map<BlockPos, Long> ANIMATING = new HashMap<>();

    private LiftDoorAnimation() {
    }

    /** 开始播放这扇门（连同相邻的门方块）的开合动画。 */
    public static void start(BlockPos clickedPos, long startMillis) {
        final World world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }
        // 双扇 / 三扇 / 奇数宽的门方块实体不一定正好在被点那一格，把周围一格内的门都算上
        for (BlockPos pos : around(clickedPos)) {
            if (world.getBlockEntity(pos) instanceof BlockPSDAPGDoorBase.BlockEntityBase) {
                ANIMATING.put(pos.toImmutable(), startMillis);
            }
        }
    }

    public static void tick(MinecraftClient client) {
        if (ANIMATING.isEmpty()) {
            return;
        }
        final World world = client.world;
        if (world == null) {
            clear();
            return;
        }
        final long now = System.currentTimeMillis();
        final Iterator<Map.Entry<BlockPos, Long>> iterator = ANIMATING.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<BlockPos, Long> entry = iterator.next();
            final long elapsed = now - entry.getValue();
            if (elapsed >= LiftDoorService.KEY_CYCLE_MS) {
                iterator.remove(); // 一轮结束，之后的收尾交给门方块自己
                continue;
            }
            if (world.getBlockEntity(entry.getKey()) instanceof BlockPSDAPGDoorBase.BlockEntityBase door) {
                door.setDoorValue(LiftDoorService.doorValue(
                        LiftDoorService.KEY_CYCLE_MS, LiftDoorService.KEY_CYCLE_MS - elapsed));
            } else {
                iterator.remove(); // 门被拆掉了
            }
        }
    }

    public static void clear() {
        ANIMATING.clear();
    }

    private static BlockPos[] around(BlockPos pos) {
        return new BlockPos[]{
                pos, pos.up(), pos.down(),
                pos.north(), pos.south(), pos.east(), pos.west()
        };
    }
}
