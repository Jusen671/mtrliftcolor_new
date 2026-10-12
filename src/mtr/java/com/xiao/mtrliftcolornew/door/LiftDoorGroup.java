package com.xiao.mtrliftcolornew.door;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.mtr.mod.block.BlockLiftDoor;
import org.mtr.mod.block.BlockLiftDoorOdd;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 把「被三角钥匙点中的那一格」还原成整组门方块。
 *
 * <p>MTR 的电梯门是<b>两格高</b>的一列，一整扇门又由<b>左右两列</b>（宽门是三列）拼成，
 * 渲染时读的开度是「本列 + 相邻列」两者的最大值。所以要开一扇门，必须把这一整组方块都记进
 * {@link LiftMaintenanceRegistry}，只标记被点的那一格会出现「一半开一半关」。</p>
 *
 * <p>找相邻列用 MTR 自己的 {@code IBlock.getSideDirection(state)}：它按 FACING 与 SIDE
 * 两个方块状态算出「这一列的搭档在哪边」，和 MTR 渲染层门时用的是同一套判定，
 * 所以不用猜门朝哪边、也不用管是偶数宽还是奇数宽的门。</p>
 *
 * <p>服务器与客户端各自解析会得到同样的结果（同一世界、同一坐标），但为了让所有玩家完全一致，
 * 实际下发的是<b>服务器算好的坐标列表</b>，客户端只是照着记。</p>
 */
public final class LiftDoorGroup {

    /** 一整组门最多几列（宽电梯也就 2~3 列，留点余量，同时防止死循环）。 */
    private static final int MAX_COLUMNS = 6;

    private LiftDoorGroup() {
    }

    /** 这格是不是电梯层门方块。车站的屏蔽门（PSD/APG）不算，它们不属于电梯。 */
    public static boolean isLiftDoor(World world, BlockPos pos) {
        return isLiftDoorBlock(world.getBlockState(pos).getBlock());
    }

    /**
     * 手机触屏准星容易偏半格：点到的不是门本身、但紧邻（上下左右）有一格电梯层门时，
     * 把那一格当作「被点到的门」返回；周围也没有门才返回 {@code null}。
     */
    public static BlockPos normalizeDoor(World world, BlockPos clicked) {
        if (isLiftDoor(world, clicked)) {
            return clicked;
        }
        for (final BlockPos candidate : new BlockPos[]{
                clicked.down(), clicked.up(),
                clicked.north(), clicked.south(), clicked.east(), clicked.west()}) {
            if (isLiftDoor(world, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isLiftDoorBlock(Block block) {
        return block instanceof BlockLiftDoor || block instanceof BlockLiftDoorOdd;
    }

    /**
     * 解析整组门方块。
     *
     * @return 这一组门的全部坐标（每列的下格与上格）；点到的不是电梯门时退化为只返回这一格
     */
    public static List<BlockPos> resolve(World world, BlockPos clicked) {
        if (!isLiftDoor(world, clicked)) {
            return List.of(clicked);
        }
        // 先落到本列的下格：点在上半格时，下面那格也是门
        BlockPos bottom = clicked;
        if (isLiftDoor(world, clicked.down())) {
            bottom = clicked.down();
        }
        final Set<BlockPos> columns = new LinkedHashSet<>();
        columns.add(bottom);

        final Direction side = sideDirection(world, bottom);
        if (side != null) {
            collectRun(world, columns, bottom, side);
            collectRun(world, columns, bottom, side.getOpposite());
        }

        final List<BlockPos> out = new ArrayList<>(columns.size() * 2);
        for (final BlockPos column : columns) {
            out.add(column);
            out.add(column.up());
        }
        return out;
    }

    /** 沿着搭档方向一路收集连续的门列（两扇门的电梯会拿到 2 列，奇数宽可能 3 列）。 */
    private static void collectRun(World world, Set<BlockPos> columns, BlockPos from, Direction direction) {
        BlockPos pos = from.offset(direction);
        while (columns.size() < MAX_COLUMNS) {
            if (!isLiftDoor(world, pos) && isLiftDoor(world, pos.down())) {
                pos = pos.down(); // 邻居只在下半格有门（点的时候站在上半格高度）
            }
            if (!isLiftDoor(world, pos)) {
                break;
            }
            // 统一落到该列的下格，避免出现同一列的两个高度被当成两列
            if (isLiftDoor(world, pos.down())) {
                pos = pos.down();
            }
            if (!columns.add(pos)) {
                break;
            }
            pos = pos.offset(direction);
        }
    }

    /** 取 MTR 算出来的「搭档列在哪一边」；拿不到返回 null（只处理本列）。 */
    private static Direction sideDirection(World world, BlockPos pos) {
        final BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockLiftDoor) && !(state.getBlock() instanceof BlockLiftDoorOdd)) {
            return null;
        }
        try {
            return org.mtr.mod.block.IBlock.getSideDirection(
                    new org.mtr.mapping.holder.BlockState(state)).data;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** 方块坐标列表 → long 键列表（协议与状态表都用 long）。 */
    public static List<Long> toKeys(List<BlockPos> positions) {
        final List<Long> keys = new ArrayList<>(positions.size());
        for (final BlockPos pos : positions) {
            keys.add(pos.asLong());
        }
        return keys;
    }
}
