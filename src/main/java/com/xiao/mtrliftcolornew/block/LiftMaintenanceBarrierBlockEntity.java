package com.xiao.mtrliftcolornew.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * 围挡的方块实体：本身不带任何数据，只用来挂客户端渲染器
 * {@code LiftMaintenanceBarrierRenderer}，由它把「危险 DANGER / 电梯例行保养中 /
 * 禁止进入 / NO ENTRY」用 Minecraft 自带字体画到布面上。
 */
public class LiftMaintenanceBarrierBlockEntity extends BlockEntity {

    public LiftMaintenanceBarrierBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.LIFT_MAINTENANCE_BARRIER_BE, pos, state);
    }
}
