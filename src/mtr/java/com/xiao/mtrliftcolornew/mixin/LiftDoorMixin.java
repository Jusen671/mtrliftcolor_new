package com.xiao.mtrliftcolornew.mixin;

import com.xiao.mtrliftcolornew.door.LiftDoorService;
import org.mtr.core.data.Lift;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 接管 MTR 电梯的运动与轿门开合，用来实现「驾驶钥匙开门」与「按真实速度开关门」。
 *
 * <p>挂在 {@code Lift} 上（不是客户端类），因为电梯的运动由 MTR 私有仿真线程驱动，
 * 只有在那个线程里改这些字段才是安全的：{@code tick} 的 HEAD 注入运行在仿真线程
 * （服务器）与渲染线程（客户端镜像）上，正好是唯二能安全写入的时机。</p>
 *
 * <p>注入内容：</p>
 * <ol>
 *   <li>{@code tick} HEAD：取出「驾驶钥匙开门」请求 → 电梯在运行就立即急停
 *       （清空指令、速度归零、平层到最近楼层），本来就停着就不动它；
 *       然后把冷却设成 {@link LiftDoorService#KEY_CYCLE_MS} 让门打开；</li>
 *   <li>同一注入里做「本轮开合总时长」的学习：冷却值突然变大就说明开始了一轮新的开合，
 *       MTR 原版写入的到站冷却（5700）会被换成 {@link LiftDoorService#ARRIVAL_CYCLE_MS}，
 *       于是正常到站的门也按真实速度开合；</li>
 *   <li>{@code getDoorValue} HEAD：只要本轮是本模组起头的，就用
 *       {@link LiftDoorService#doorValue(long, long)} 的慢速曲线代替 MTR 的曲线。
 *       这个函数只被客户端渲染读取（{@code RenderLifts} 拿它去写轿门方块的开度），
 *       所以覆盖它就能同时改变层门与轿厢门的速度。</li>
 * </ol>
 *
 * <p>运动状态字段在父类 {@code LiftSchema} 上，通过 {@link LiftSchemaAccessor} 读写。</p>
 */
@Mixin(value = Lift.class, remap = false)
public abstract class LiftDoorMixin {

    @Shadow @Final private boolean isClientside;
    @Shadow private boolean needsUpdate;

    /** 本模组学到的「本轮开合总时长」；0 表示交回 MTR 原版曲线。 */
    @Unique
    private long mtrliftcolor$cycleTotal;
    /** 上一拍拍到的冷却值，用来识别「新的一轮开合开始」。 */
    @Unique
    private long mtrliftcolor$lastCoolDown;

    /** MTR 内部方法：某个楼层在轨道上的累计里程（方块单位）。 */
    @Invoker("getProgress")
    protected abstract long mtrliftcolor$getProgress(int index);

    /** 取父类字段读写口。 */
    @Unique
    private LiftSchemaAccessor mtrliftcolor$schema() {
        return (LiftSchemaAccessor) (Object) this;
    }

    @Inject(method = "tick(J)V", at = @At("HEAD"))
    private void mtrliftcolor$onTickHead(long elapsed, CallbackInfo ci) {
        if (!this.isClientside) {
            this.mtrliftcolor$handleDoorKey();
        }
        this.mtrliftcolor$learnCycle();
    }

    /**
     * 驾驶钥匙请求：在仿真线程里把这台电梯停下来并开门。
     *
     * <p>「停下来」= 清掉所有指令 + 速度归零 + 平层到最近的楼层。平层是必要的：
     * MTR 只在轿厢与层门对齐时才会把开度写给层门方块，轿厢停在两层之间时门是打不开的。</p>
     */
    @Unique
    private void mtrliftcolor$handleDoorKey() {
        final LiftSchemaAccessor schema = this.mtrliftcolor$schema();
        final long id = ((Lift) (Object) this).getId();
        if (!LiftDoorService.consumeRequest(id)) {
            return;
        }
        final boolean moving = Math.abs(schema.mtrliftcolor$getSpeed()) > 1.0E-9
                || !schema.mtrliftcolor$getInstructions().isEmpty();
        schema.mtrliftcolor$getInstructions().clear();
        schema.mtrliftcolor$setSpeed(0);
        if (moving) {
            schema.mtrliftcolor$setRailProgress(this.mtrliftcolor$nearestFloorProgress());
        }
        schema.mtrliftcolor$setStoppingCoolDown(LiftDoorService.KEY_CYCLE_MS);
        this.needsUpdate = true;
    }

    /** 离当前轨道里程最近的楼层里程。 */
    @Unique
    private double mtrliftcolor$nearestFloorProgress() {
        final LiftSchemaAccessor schema = this.mtrliftcolor$schema();
        final double current = schema.mtrliftcolor$getRailProgress();
        double best = current;
        double bestDistance = Double.MAX_VALUE;
        final int floorCount = schema.mtrliftcolor$getFloors().size();
        for (int i = 0; i < floorCount; i++) {
            final double progress = this.mtrliftcolor$getProgress(i);
            final double distance = Math.abs(progress - current);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = progress;
            }
        }
        return best;
    }

    /**
     * 学习本轮开合的总时长，并把 MTR 到站时写入的短周期换成本模组的慢速周期。
     *
     * <p>判据是「冷却值比上一拍变大」：只有新的一轮开始才会变大。客户端镜像不做替换，
     * 只跟随服务器同步过来的值，避免两端算出不同的时长。</p>
     */
    @Unique
    private void mtrliftcolor$learnCycle() {
        final LiftSchemaAccessor schema = this.mtrliftcolor$schema();
        final long coolDown = schema.mtrliftcolor$getStoppingCoolDown();
        if (coolDown > this.mtrliftcolor$lastCoolDown) {
            if (!this.isClientside && coolDown <= LiftDoorService.MTR_COOLDOWN_MS) {
                schema.mtrliftcolor$setStoppingCoolDown(LiftDoorService.ARRIVAL_CYCLE_MS);
                this.needsUpdate = true;
            }
            this.mtrliftcolor$cycleTotal = schema.mtrliftcolor$getStoppingCoolDown();
        } else if (coolDown <= 0) {
            this.mtrliftcolor$cycleTotal = 0;
        }
        this.mtrliftcolor$lastCoolDown = schema.mtrliftcolor$getStoppingCoolDown();
    }

    @Inject(method = "getDoorValue()F", at = @At("HEAD"), cancellable = true)
    private void mtrliftcolor$getDoorValue(CallbackInfoReturnable<Float> cir) {
        final long cycleTotal = this.mtrliftcolor$cycleTotal;
        if (cycleTotal <= LiftDoorService.MTR_COOLDOWN_MS) {
            return; // 这一轮不是本模组起的头，用 MTR 原曲线
        }
        cir.setReturnValue(LiftDoorService.doorValue(cycleTotal,
                this.mtrliftcolor$schema().mtrliftcolor$getStoppingCoolDown()));
    }
}
