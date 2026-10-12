package com.xiao.mtrliftcolornew.door;

/**
 * 电梯门的时间与曲线。
 *
 * <p>这里只有两类东西，都是纯函数，不持有任何状态：</p>
 * <ol>
 *   <li><b>到站开门</b>：把 MTR 原版的 1.6 秒换成接近真实电梯的慢速曲线。状态（本轮总时长）
 *       记在电梯自己身上，见 {@code LiftDoorMixin}。</li>
 *   <li><b>三角钥匙检修开门</b>：从当前开度平滑走到全开并保持，或走回全关。
 *       状态在 {@link LiftMaintenanceRegistry}。</li>
 * </ol>
 *
 * <p>开度统一用 0 .. {@link #DOOR_FULL_OPEN} 这个量纲。MTR 的门模型把 0.75 当成「全开」
 * （{@code RenderPSDAPGDoor} 里除以 0.75，{@code RenderLifts} 里也先取 min(轿门开度, 0.75)），
 * 跟着它走，检修开门和 MTR 自己开门才对得上、不会一个开到 100% 另一个只到 75%。</p>
 */
public final class LiftDoorService {

    /**
     * MTR 原版到站时写入的冷却值（字节码里内联的 5700）。
     *
     * <p>只有冷却值<b>恰好</b>等于它，才认定是「MTR 自己的到站开门」并换成
     * {@link #ARRIVAL_CYCLE_MS}；更大或更小的值都原样保留——其它模组（例如 YTE）
     * 自己设的冷却周期不去动，避免把别人的平层 / 开门逻辑一起改掉。</p>
     */
    public static final long MTR_COOLDOWN_MS = 5700L;
    /** 到站开门的一轮时长（比原版慢，见下面三段拆分）。 */
    public static final long ARRIVAL_CYCLE_MS = 7600L;
    /** 到站开门时的开门耗时。 */
    public static final long OPEN_MS = 2600L;
    /** 到站开门时的关门耗时。 */
    public static final long CLOSE_MS = 2600L;
    /** 完全关到位之后、轿厢重新启动前的停顿。 */
    public static final long TAIL_MS = 400L;

    /** MTR 层门 / 轿门模型里「全开」对应的开度。 */
    public static final double DOOR_FULL_OPEN = 0.75D;

    /** 检修开关门时长下限（毫秒）：太快就像瞬移，不像电梯。 */
    public static final long MAINTENANCE_MIN_MS = 600L;
    /** 检修开关门时长上限（毫秒）。 */
    public static final long MAINTENANCE_MAX_MS = 15000L;
    /** 检修开关门时长的默认值：约 2.6 秒，跟到站那一档保持一致。 */
    public static final long MAINTENANCE_DEFAULT_MS = 2600L;

    private LiftDoorService() {
    }

    /** 把任意来源（界面输入、网络包、存档文件）的时长夹到安全区间；非法值退回默认。 */
    public static long clampMaintenanceMs(long ms) {
        if (ms < MAINTENANCE_MIN_MS || ms > MAINTENANCE_MAX_MS) {
            return MAINTENANCE_DEFAULT_MS;
        }
        return ms;
    }

    /**
     * 检修开合曲线：从 {@code fromValue} 平滑走到「全开」或「全关」。
     *
     * <p>开门那一头走完就停在全开不动（一直保持，直到有人再用钥匙关它）；
     * 关门那一头走完就是 0，调用方会把这条记录删掉、把门交回 MTR。</p>
     *
     * @param open       true = 正在开（之后保持全开）；false = 正在关
     * @param elapsed    本轮已经走的毫秒数
     * @param durationMs 本轮动画时长
     * @param fromValue  本轮开始时的开度，保证来回切换时不会跳一下
     * @return 开度，量纲 0 .. {@link #DOOR_FULL_OPEN}
     */
    public static double maintenanceValue(boolean open, long elapsed, long durationMs, double fromValue) {
        final double start = clampDoor(fromValue);
        final long safeDuration = Math.max(1L, durationMs);
        final double eased = smooth(elapsed <= 0 ? 0.0D : Math.min(1.0D, elapsed / (double) safeDuration));
        if (open) {
            return clampDoor(start + (DOOR_FULL_OPEN - start) * eased);
        }
        return clampDoor(start * (1.0D - eased));
    }

    /** 开度夹到 0 .. 全开。 */
    private static double clampDoor(double value) {
        return value < 0 ? 0.0D : Math.min(DOOR_FULL_OPEN, value);
    }

    /**
     * 到站开门的慢速曲线：把一轮的总时长与剩余冷却换算成 0..1 的门开度。
     *
     * <p>三段时长固定为 {@link #OPEN_MS} / {@link #CLOSE_MS} / {@link #TAIL_MS}，
     * 剩下的都算「保持全开」。轮时长不够放下三段时按比例压缩，绝不会算出负数。</p>
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
    private static double smooth(double t) {
        final double clamped = t < 0 ? 0 : Math.min(t, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    /** 同上，float 版（到站曲线用）。 */
    private static float smooth(float t) {
        final float clamped = t < 0 ? 0 : Math.min(t, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
