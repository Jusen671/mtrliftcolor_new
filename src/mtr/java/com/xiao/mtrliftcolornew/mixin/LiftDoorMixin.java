package com.xiao.mtrliftcolornew.mixin;

import com.xiao.mtrliftcolornew.Safety;
import com.xiao.mtrliftcolornew.door.LiftDoorService;
import com.xiao.mtrliftcolornew.door.LiftMaintenanceRegistry;
import org.mtr.core.data.Lift;
import org.mtr.core.data.LiftInstruction;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 接管 MTR 电梯的运动与轿门开合，用来实现「三角钥匙检修开门」与「按真实速度开关门」。
 *
 * <p>挂在 {@code Lift} 上（不是客户端类），因为电梯的运动由 MTR 私有仿真线程驱动，
 * {@code tick} 的注入点正好运行在那个线程（服务器）与渲染线程（客户端镜像）上，
 * 是唯二能安全写这些字段的地方。</p>
 *
 * <p><b>这里是全模组最危险的代码：仿真线程一旦被这里的异常打死，所有电梯立刻瘫痪，
 * 轿厢屏幕不再刷新，整个模组看起来「所有功能都不能用」。</b>所以每一段注入体都必须
 * 经过 {@link Safety} 包一层：异常就地吞掉并写日志，最多让检修功能不生效，绝不连累 MTR。</p>
 *
 * <p>检修联锁怎么把电梯停住：以前是「把 MTR 的到站冷却设成一个很长的值」，
 * 借 MTR 「冷却大于 0 就不走车」这条规则来停车。副作用很多——冷却值会被别的模组
 * （YTE）读到、也被本模组的曲线读到，门开着开着电梯又自己开走。现在改成直接
 * <b>冻结</b>：只要这台电梯还有检修门开着（或正在关门），{@code speed} 归零、
 * {@code railProgress} 钉在刚进检修时的那个值上。车停在哪就是哪，绝不会跳层。</p>
 *
 * <p>轿厢里已经按下的楼层<b>不动</b>（不清空指令队列），所以检修门关上之后电梯会照常把
 * 之前按的楼层跑完。为了做到这点，进检修时把指令队列<b>借出来存着</b>、退出时原样塞回去：
 * 直接留在队列里会被 MTR 自己「到站」那条分支吃掉（车正好压在某层时下一拍就算到达、出队、
 * 开一轮 5700 的到站冷却），那样按下的层就白按了。</p>
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
    /** 当前是否在检修联锁里。 */
    @Unique
    private boolean mtrliftcolor$locked;
    /** 进检修那一刻的轨道进度，联锁期间一直把车钉在这个位置。 */
    @Unique
    private double mtrliftcolor$frozenRailProgress;
    /** 服务端借出来的指令队列（轿厢里按下的楼层），退出检修时原样还回去。 */
    @Unique
    private ObjectArrayList<LiftInstruction> mtrliftcolor$borrowed;

    /** MTR 用轨道进度判断车到没到某一层，这里借用它的私有算法。 */
    @org.spongepowered.asm.mixin.gen.Invoker("getProgress")
    public abstract long mtrliftcolor$progressOf(int floorIndex);

    /** 取父类字段读写口。 */
    @Unique
    private LiftSchemaAccessor mtrliftcolor$schema() {
        return (LiftSchemaAccessor) (Object) this;
    }

    @Inject(method = "tick(J)V", at = @At("HEAD"))
    private void mtrliftcolor$onTickHead(long elapsed, CallbackInfo ci) {
        Safety.run("Lift.tick 头部注入", () -> {
            final long id = ((Lift) (Object) this).getId();
            if (LiftMaintenanceRegistry.isLocked(id)) {
                mtrliftcolor$freeze();
                return; // 联锁期间不学到站曲线，免得跟检修曲线抢
            }
            if (this.mtrliftcolor$locked) {
                mtrliftcolor$release();
            }
            mtrliftcolor$learnCycle();
        });
    }

    /**
     * 一拍仿真跑完后再钉一次：万一有别的模组（YTE 就是）在这一拍里改了进度或速度，
     * 这里照样把它按回检修位置，车绝不会飘。
     *
     * <p>客户端镜像不钉：它的进度归服务器同步说了算，钉了反而会跟同步值打架、看起来一跳一跳。</p>
     */
    @Inject(method = "tick(J)V", at = @At("RETURN"))
    private void mtrliftcolor$onTickReturn(long elapsed, CallbackInfo ci) {
        Safety.run("Lift.tick 尾部注入", () -> {
            if (this.mtrliftcolor$locked && !this.isClientside) {
                final LiftSchemaAccessor schema = mtrliftcolor$schema();
                schema.mtrliftcolor$setSpeed(0);
                schema.mtrliftcolor$setRailProgress(this.mtrliftcolor$frozenRailProgress);
            }
        });
    }

    /**
     * 检修联锁中：速度归零，服务端另外把进度钉在进检修的那一刻。
     *
     * <p>不取消 MTR 自己那一拍：还是要把完整的 {@code tick} 走完，否则 {@code needsUpdate}
     * 没人消费，「车已经停稳」就同步不出去。速度为 0、指令队列又已借走，MTR 自己算出来就是原地不动。</p>
     *
     * <p>客户端镜像只归零速度、<b>不</b>钉进度：它的 {@code railProgress} 由服务器同步决定，
     * 本地再钉一个自己算出来的值就会和同步值互相覆盖。速度为 0 之后 MTR 的积分项就走不动了，
     * 显示上同样是稳稳停住。</p>
     */
    @Unique
    private void mtrliftcolor$freeze() {
        final LiftSchemaAccessor schema = mtrliftcolor$schema();
        if (this.mtrliftcolor$locked) {
            schema.mtrliftcolor$setSpeed(0);
            if (!this.isClientside) {
                schema.mtrliftcolor$setRailProgress(this.mtrliftcolor$frozenRailProgress);
            }
            return;
        }
        this.mtrliftcolor$locked = true;
        if (!this.isClientside) {
            this.mtrliftcolor$frozenRailProgress = schema.mtrliftcolor$getRailProgress();
            mtrliftcolor$borrowInstructions();
            // 只在刚进联锁时同步一次：之后进度与速度都是常量，没必要每拍都发一遍
            this.needsUpdate = true;
        }
        schema.mtrliftcolor$setSpeed(0);
    }

    /**
     * 退出检修：还回指令，让电梯自己接着跑。
     *
     * <p>本轮曲线相关的两个值<b>不</b>重置：联锁期间 MTR 那一拍照走、冷却照常递减，只是本模组不
     * 「学到新一轮」而已。所以解锁后接着用解锁前学到的轮长换算，正在收尾的到站开门不会被截断。</p>
     */
    @Unique
    private void mtrliftcolor$release() {
        this.mtrliftcolor$locked = false;
        if (!this.isClientside) {
            mtrliftcolor$returnInstructions();
        }
        this.needsUpdate = true;
    }

    /** 把指令队列借出来存着（只在服务端调用）。 */
    @Unique
    private void mtrliftcolor$borrowInstructions() {
        Safety.run("借出轿厢指令", () -> {
            final ObjectArrayList<LiftInstruction> live = mtrliftcolor$schema().mtrliftcolor$getInstructions();
            if (live.isEmpty()) {
                this.mtrliftcolor$borrowed = null;
                return;
            }
            this.mtrliftcolor$borrowed = new ObjectArrayList<>(live);
            live.clear();
        });
    }

    /** 把借走的指令按原顺序放回队列头部（只在服务端调用）。 */
    @Unique
    private void mtrliftcolor$returnInstructions() {
        Safety.run("归还轿厢指令", () -> {
            final ObjectArrayList<LiftInstruction> borrowed = this.mtrliftcolor$borrowed;
            if (borrowed == null || borrowed.isEmpty()) {
                this.mtrliftcolor$borrowed = null;
                return;
            }
            final ObjectArrayList<LiftInstruction> live = mtrliftcolor$schema().mtrliftcolor$getInstructions();
            for (int i = borrowed.size() - 1; i >= 0; i--) {
                live.add(0, borrowed.get(i)); // 倒着往前插，整体顺序保持原样
            }
            this.mtrliftcolor$borrowed = null;
        });
    }

    /**
     * 学习本轮开合的总时长，并把 MTR 到站时写入的短周期换成本模组的慢速周期。
     *
     * <p>判据是「冷却值比上一拍变大」：只有新的一轮开始才会变大。客户端镜像不做替换，
     * 只跟随服务器同步过来的值，避免两端算出不同的时长。只有恰好等于 MTR 原版的
     * {@link LiftDoorService#MTR_COOLDOWN_MS} 才替换——别的模组自己设的冷却值不去动它。</p>
     */
    @Unique
    private void mtrliftcolor$learnCycle() {
        final LiftSchemaAccessor schema = mtrliftcolor$schema();
        final long coolDown = schema.mtrliftcolor$getStoppingCoolDown();
        if (coolDown > this.mtrliftcolor$lastCoolDown) {
            if (!this.isClientside && coolDown == LiftDoorService.MTR_COOLDOWN_MS) {
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
        final Double maintenance = Safety.call("轿门开度查询", this::mtrliftcolor$maintenanceCarValue, null);
        if (maintenance != null) {
            cir.setReturnValue(maintenance.floatValue());
            return;
        }
        final long cycleTotal = this.mtrliftcolor$cycleTotal;
        if (cycleTotal <= LiftDoorService.MTR_COOLDOWN_MS) {
            return; // 这一轮不是本模组起的头，用 MTR 原曲线
        }
        // 换算曲线本身是纯计算，不会抛；读冷却值走 MTR 字段，包一层保险
        Safety.run("轿门曲线换算", () ->
                cir.setReturnValue(LiftDoorService.doorValue(cycleTotal,
                        mtrliftcolor$schema().mtrliftcolor$getStoppingCoolDown())));
    }

    /**
     * 检修期间要让 MTR 认为「正在停站开门」，否则 {@code RenderLifts} 根本不去读轿门开度，
     * 轿厢门就会在层门已经打开的情况下关着。判据与 {@link #mtrliftcolor$maintenanceCarValue()} 一致。
     */
    @Inject(method = "hasCoolDown()Z", at = @At("HEAD"), cancellable = true)
    private void mtrliftcolor$hasCoolDown(CallbackInfoReturnable<Boolean> cir) {
        if (Safety.call("轿门停站判定", this::mtrliftcolor$maintenanceCarValue, null) != null) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 轿厢门此刻该开多大：只有车正好平层在某一扇<b>开着</b>的检修门前面时才回值，
     * 否则返回 {@code null} 交给 MTR 自己判断。
     *
     * <p>这个前提很重要：车停在别处、检修门开在另一层时如果也把开度推给轿厢门，
     * MTR 会把这个值写进车门口那几扇层门，就会出现「没人开它、它自己开了」的怪事。</p>
     */
    @Unique
    private Double mtrliftcolor$maintenanceCarValue() {
        if (!this.mtrliftcolor$locked) {
            return null;
        }
        final long id = ((Lift) (Object) this).getId();
        final double rail = mtrliftcolor$schema().mtrliftcolor$getRailProgress();
        return LiftMaintenanceRegistry.carDoorValue(id, floorIndex -> {
            try {
                return Math.abs(rail - mtrliftcolor$progressOf(floorIndex)) < 0.01D;
            } catch (RuntimeException | LinkageError e) {
                return false;
            }
        });
    }
}
