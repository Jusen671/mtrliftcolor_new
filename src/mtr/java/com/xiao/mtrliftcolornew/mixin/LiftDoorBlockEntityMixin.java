package com.xiao.mtrliftcolornew.mixin;

import com.xiao.mtrliftcolornew.Safety;
import com.xiao.mtrliftcolornew.door.LiftMaintenanceRegistry;
import org.mtr.mapping.holder.BlockEntityAbstractMapping;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mod.block.BlockPSDAPGDoorBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让被三角钥匙打开的那扇层门按本模组的慢速曲线开合。
 *
 * <p>关键：<b>只覆写读取</b>，<b>不</b>去写门方块实体的 {@code doorValue} 字段。
 * MTR 有两条会写这个字段的路径：{@code RenderVehicleHelper.canOpenDoors} 在轿厢靠层时把轿门开度
 * 写进门口的层门；{@code BlockEntityBase.tick} 在没人写的时候让门自己回落。以前本模组再用一个
 * 客户端 tick 去写第三条曲线，同一帧里三方互相覆盖，层门就来回抽搐。改成覆写读取之后，
 * 谁写进去都不影响渲染看到的值。</p>
 *
 * <p>开度的唯一来源是 {@link LiftMaintenanceRegistry}（拿本方块实体的坐标去查），
 * 所以这里不需要在门方块实体上塞任何状态：区块卸载重载、玩家重进服都不会把状态搞丢。</p>
 *
 * <p>返回的开度上限是 0.75 —— MTR 的门模型把 0.75 当作全开（{@code RenderPSDAPGDoor} 除以 0.75，
 * {@code RenderLifts} 先取 {@code min(轿门开度, 0.75)}），跟着它走两边才对得上。</p>
 */
@Mixin(value = BlockPSDAPGDoorBase.BlockEntityBase.class, remap = false)
public abstract class LiftDoorBlockEntityMixin {

    @Inject(method = "getDoorValue()D", at = @At("HEAD"), cancellable = true)
    private void mtrliftcolor$maintenanceDoorValue(CallbackInfoReturnable<Double> cir) {
        Safety.run("层门开度覆写", () -> {
            final long key = mtrliftcolor$posKey();
            if (key == Long.MIN_VALUE) {
                return; // 还没世界 / 坐标拿不到，别乱改门
            }
            final Double value = LiftMaintenanceRegistry.valueAt(key);
            if (value == null) {
                return; // 这扇门不在检修状态，交回 MTR 自己
            }
            cir.setReturnValue(value);
        });
    }

    /** 本方块实体的坐标 → 状态表用的 long 键。拿不到时返回一个不可能命中的键。 */
    @Unique
    private long mtrliftcolor$posKey() {
        try {
            final BlockPos pos = ((BlockEntityAbstractMapping) (Object) this).getPos2();
            return pos == null ? Long.MIN_VALUE : pos.asLong();
        } catch (RuntimeException | LinkageError e) {
            return Long.MIN_VALUE;
        }
    }
}
