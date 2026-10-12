package com.xiao.mtrliftcolornew.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

/**
 * 电梯维护围挡：三段黄色警示布面 + 深灰圆管框架，布面上的字由游戏自带字体实时绘制。
 *
 * <p>展开后比一格还宽（约 2.2 格），但方块本体仍然只占一格，所以碰撞与描边只取中间那层
 * 薄板，避免把模型两侧的空当也变成看不见的实心墙；薄板会跟着朝向一起转。</p>
 *
 * <p>这是隐藏的测试方块，不在创造模式物品栏里，只能用 {@code /give} 之类的指令获取。</p>
 */
public class LiftMaintenanceBarrierBlock extends Block implements BlockEntityProvider {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    /** 碰撞 / 描边：南北朝向时是中间一层薄板，东西朝向时转向另一轴。 */
    private static final VoxelShape SHAPE_NS = Block.createCuboidShape(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    private static final VoxelShape SHAPE_EW = Block.createCuboidShape(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    /** 左右两片布各相对中片折起的角度：45° 就是「90° 围挡」，22.5° 是「45° 围挡」。 */
    private final float foldDegrees;

    public LiftMaintenanceBarrierBlock(float foldDegrees, Settings settings) {
        super(settings);
        this.foldDegrees = foldDegrees;
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }

    public float foldDegrees() {
        return this.foldDegrees;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** 放下时让印着图案的那一面朝着玩家。 */
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    // 直接实现 BlockEntityProvider（而不是继承 BlockWithEntity）：后者会把渲染类型默认设成
    // INVISIBLE，还得再覆盖一个已弃用的 getRenderType；普通 Block 本来就是 MODEL 渲染。

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LiftMaintenanceBarrierBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapeOf(state);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapeOf(state);
    }

    private static VoxelShape shapeOf(BlockState state) {
        Direction facing = state.get(FACING);
        return facing.getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }
}
