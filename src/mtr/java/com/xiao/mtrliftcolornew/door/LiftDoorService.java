package com.xiao.mtrliftcolornew.door;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 驾驶钥匙（MTR 自带的车辆驾驶钥匙，俗称三角钥匙）开门服务。
 *
 * <p>本模组<b>不新增任何物品</b>：玩家手持 MTR 的驾驶钥匙右击电梯门即可开门。</p>
 *
 * <p>链路：客户端右击 → {@code LiftColorNetwork.C2S_DOOR_KEY} 把电梯 id 发给服务器
 * → 服务器把请求记进 {@link #PENDING} → 电梯仿真线程在 {@code Lift.tick} 开头取出请求：
 * 电梯在运行就立刻急停（清指令、速度归零、平层到最近楼层），本来停着就什么都不做
 * → 把 {@code stoppingCoolDown} 设成 {@link #KEY_CYCLE_MS} 并置 needsUpdate
 * → MTR 自己把这轮冷却同步给所有客户端 → 客户端渲染时调用被本模组接管的
 * {@code Lift.getDoorValue()}，按 {@link #doorValue(long, long)} 的慢速曲线开合。</p>
 *
 * <p>之所以只靠 {@code stoppingCoolDown} 这一个字段：MTR 的轿门开合完全由客户端渲染驱动
 * （{@code RenderLifts} 每帧读 {@code lift.getDoorValue()} 与 {@code lift.hasCoolDown()}，
 * 再把开度写进轿门方块的 BlockEntity），改这一个源头就能同时影响轿厢门与层门，
 * 不需要自己去找门在哪。</p>
 */
public final class LiftDoorService {

    /**
     * MTR 原版到站时写入的冷却值（字节码里内联的 5700）。
     * 跳转检测时用它做阈值：小于这个量级的新周期都当作「MTR 自己的到站开门」，
     * 会被换成 {@link #ARRIVAL_CYCLE_MS}；更大的值是本模组起的头，原样保留。
     */
    public static final long MTR_COOLDOWN_MS = 5700L;
    /** 到站开门的一轮时长（比原版慢，见下面三段拆分）。 */
    public static final long ARRIVAL_CYCLE_MS = 7600L;
    /** 驾驶钥匙开门的一轮时长：门开到位后多保持一会儿，方便检修与进出。 */
    public static final long KEY_CYCLE_MS = 11600L;
    /** 开门耗时：约 2.6 秒，接近真实电梯门（MTR 原版只有 1.6 秒）。 */
    public static final long OPEN_MS = 2600L;
    /** 关门耗时。 */
    public static final long CLOSE_MS = 2600L;
    /** 完全关到位之后、轿厢重新启动前的停顿。 */
    public static final long TAIL_MS = 400L;
    /** 请求有效期：超过这个时间还没被电梯取走就作废（电梯不在这个世界等）。 */
    private static final long REQUEST_TTL_MS = 2000L;

    /** 待处理开门请求：电梯 id → 过期时间毫秒。网络线程写、仿真线程读。 */
    private static final Map<Long, Long> PENDING = new ConcurrentHashMap<>();

    private LiftDoorService() {
    }

    /** 记下一次「用驾驶钥匙开门」的请求，等仿真线程处理。 */
    public static void requestOpen(long liftId) {
        PENDING.put(liftId, System.currentTimeMillis() + REQUEST_TTL_MS);
    }

    /**
     * 仿真线程取走某个电梯的请求。
     *
     * @return true 表示这台电梯确实有一个还没过期的开门请求
     */
    public static boolean consumeRequest(long liftId) {
        final Long deadline = PENDING.remove(liftId);
        if (deadline == null) {
            return false;
        }
        if (PENDING.size() > 64) {
            sweepExpired();
        }
        return deadline >= System.currentTimeMillis();
    }

    /** 清掉过期请求，避免玩家对着不存在的电梯狂点导致表里堆积。 */
    public static void sweepExpired() {
        final long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    /** 当前挂起的请求数，调试用。 */
    public static int pendingCount() {
        return PENDING.size();
    }

    /** 世界切换时清空所有未处理请求。 */
    public static void clear() {
        PENDING.clear();
    }

    /**
     * 慢速开合曲线：把一轮的总时长与剩余冷却换算成 0..1 的门开度。
     *
     * <p>三段时长固定为 {@link #OPEN_MS} / {@link #CLOSE_MS} / {@link #TAIL_MS}，
     * 剩下的都算「保持全开」。因此总时长变长（驾驶钥匙）只是门开着等多一会儿，
     * 开与关的速度始终按真实电梯的节奏。轮时长不够放下三段时按比例压缩，绝不会算出负数。</p>
     *
     * @param cycleTotal 本轮总时长（由冷却值的跳变自动学到）
     * @param coolDown   剩余冷却毫秒
     * @return 门开度 0..1
     */
    public static float doorValue(long cycleTotal, long coolDown) {
        if (cycleTotal <= 0 || coolDown <= 0) {
            return 0.0F;
        }
        final long tail = Math.min(TAIL_MS, cycleTotal / 10);
        final long room = cycleTotal - tail;
        final long open = Math.min(OPEN_MS, (long) (room * 0.45));
        final long close = Math.min(CLOSE_MS, (long) (room * 0.45));
        final long hold = Math.max(0, room - open - close);
        final long elapsed = cycleTotal - coolDown;
        if (elapsed < open) {
            return smooth(elapsed / (float) open);
        }
        if (elapsed < open + hold) {
            return 1.0F;
        }
        if (elapsed < open + hold + close) {
            return 1.0F - smooth((elapsed - open - hold) / (float) close);
        }
        return 0.0F;
    }

    /** 真实电梯门是「起步慢—中间快—到位再减速」，这里用 smoothstep 近似。 */
    private static float smooth(float t) {
        final float clamped = t < 0 ? 0 : Math.min(t, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
