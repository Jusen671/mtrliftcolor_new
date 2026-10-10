package com.xiao.mtrliftcolornew.mixin;

import org.mtr.core.data.LiftFloor;
import org.mtr.core.data.LiftInstruction;
import org.mtr.core.generated.data.LiftSchema;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 电梯运动状态字段的读写口。
 *
 * <p>MTR 把 {@code railProgress} / {@code speed} / {@code stoppingCoolDown} 这些字段放在
 * 生成类 {@link LiftSchema} 里，{@code Lift} 只是继承。在挂在 {@code Lift} 上的 mixin 里
 * 直接 {@code @Shadow} 继承字段缺少目标，所以改为在字段的声明类上开 {@code @Accessor}，
 * 再由 {@link LiftDoorMixin} 转型调用。</p>
 */
@Mixin(value = LiftSchema.class, remap = false)
public interface LiftSchemaAccessor {

    @Accessor("railProgress")
    double mtrliftcolor$getRailProgress();

    @Accessor("railProgress")
    void mtrliftcolor$setRailProgress(double value);

    @Accessor("speed")
    double mtrliftcolor$getSpeed();

    @Accessor("speed")
    void mtrliftcolor$setSpeed(double value);

    @Accessor("stoppingCoolDown")
    long mtrliftcolor$getStoppingCoolDown();

    @Accessor("stoppingCoolDown")
    void mtrliftcolor$setStoppingCoolDown(long value);

    @Accessor("instructions")
    ObjectArrayList<LiftInstruction> mtrliftcolor$getInstructions();

    @Accessor("floors")
    ObjectArrayList<LiftFloor> mtrliftcolor$getFloors();
}
