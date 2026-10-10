package com.xiao.mtrliftcolornew.door;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.core.data.Lift;
import org.mtr.core.data.LiftFloor;
import org.mtr.core.data.Position;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockLiftDoor;
import org.mtr.mod.block.BlockLiftDoorOdd;
import org.mtr.mod.client.MinecraftClientData;

/**
 * 客户端：从「被右击的电梯门方块」反查它属于哪台电梯。
 *
 * <p>先按 MTR 自己的精确匹配（{@code Lift.getFloorIndex}）扫描这一格与上下左右相邻格——
 * 楼层位置通常就是门旁边那个呼梯面板的位置，所以邻居格里几乎一定能命中，结果和 MTR 判断
 * 完全一致。精确匹配找不到时再退回到距离判断：楼层位置与门方块的水平距离不超过 3 格、
 * 垂直不超过 1 格，取最近的一台电梯。</p>
 *
 * <p>查不到就返回 {@code -1}，此时右击不会产生任何效果（不会误停别的电梯）。</p>
 */
public final class LiftDoorLookup {

    /** 楼层位置与门方块的最大水平距离（曼哈顿） */
    private static final int MAX_HORIZONTAL = 3;
    /** 楼层位置与门方块的最大垂直距离 */
    private static final int MAX_VERTICAL = 1;

    private LiftDoorLookup() {
    }

    /** 这一格（含上下）是不是电梯门方块。 */
    public static boolean isLiftDoor(World world, BlockPos pos) {
        return isLiftDoorBlock(world.getBlockState(pos).getBlock())
                || isLiftDoorBlock(world.getBlockState(pos.up()).getBlock())
                || isLiftDoorBlock(world.getBlockState(pos.down()).getBlock());
    }

    private static boolean isLiftDoorBlock(Block block) {
        // 只认电梯门；车站的 PSD / APG 屏蔽门不是这两个类
        return block instanceof BlockLiftDoor || block instanceof BlockLiftDoorOdd;
    }

    /**
     * 找出这扇电梯门属于哪台电梯。
     *
     * @return 电梯 id；找不到返回 -1
     */
    public static long findLiftId(BlockPos doorPos) {
        final MinecraftClientData data = MinecraftClientData.getInstance();
        if (data == null || doorPos == null) {
            return -1;
        }
        // 1) 精确匹配：这一格或紧邻的一格正好是某个楼层位置
        for (BlockPos candidate : neighbours(doorPos)) {
            final Position position = Init.blockPosToPosition(new org.mtr.mapping.holder.BlockPos(candidate));
            for (Lift lift : data.lifts) {
                if (lift.getFloorIndex(position) >= 0) {
                    return lift.getId();
                }
            }
        }
        // 2) 距离兜底：取离门最近的那台电梯
        long bestId = -1;
        long bestScore = Long.MAX_VALUE;
        for (Lift lift : data.lifts) {
            final long score = doorScore(lift, doorPos);
            if (score >= 0 && score < bestScore) {
                bestScore = score;
                bestId = lift.getId();
            }
        }
        return bestId;
    }

    /** 门与这台电梯各楼层的最小距离分；不符合范围要求返回 -1。 */
    private static long doorScore(Lift lift, BlockPos doorPos) {
        final long[] best = {-1};
        lift.iterateFloors(floor -> {
            final Position position = floor.getPosition();
            final long vertical = Math.abs(position.getY() - doorPos.getY());
            if (vertical > MAX_VERTICAL) {
                return;
            }
            final long horizontal = Math.abs(position.getX() - doorPos.getX())
                    + Math.abs(position.getZ() - doorPos.getZ());
            if (horizontal > MAX_HORIZONTAL) {
                return;
            }
            final long score = horizontal * 4 + vertical;
            if (best[0] < 0 || score < best[0]) {
                best[0] = score;
            }
        });
        return best[0];
    }

    /** 这一格 + 上下 + 四个水平邻居（门与呼梯面板通常只差一格）。 */
    private static BlockPos[] neighbours(BlockPos pos) {
        return new BlockPos[]{
                pos, pos.up(), pos.down(),
                pos.north(), pos.south(), pos.east(), pos.west()
        };
    }

    /** 供提示文字用：这扇门的方块状态名，调试时打印。 */
    public static String describeBlock(BlockState state) {
        return state.getBlock().toString();
    }
}
