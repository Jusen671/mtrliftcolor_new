package com.xiao.mtrliftcolornew.door;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 检修开门状态的唯一权威表：哪一格门方块现在被三角钥匙打开了、什么时候开始动、动多快、
 * 属于这台电梯的第几层。
 *
 * <p>状态<b>不</b>存在门方块实体自己身上，而是集中放在这张表里，键是方块坐标。这样做有三个好处：</p>
 * <ol>
 *   <li><b>区块卸载重载不怕</b>：方块实体会被销毁重建，状态挂在它身上就会随区块一起消失；
 *       放在这张表里，玩家走开再走回来，门还是开着的。</li>
 *   <li><b>只有一个写入者</b>：门方块实体的 {@code getDoorValue()} 被本模组覆写成「问这张表」，
 *       MTR 自己怎么写它的 {@code doorValue} 字段都影响不到渲染，从根上消掉了层门抽搐。</li>
 *   <li><b>能整体快照</b>：玩家重新进服时把表里所有还开着的门一次性发给他，
 *       不会出现「别人看着门开着，我这边是关的」。</li>
 * </ol>
 *
 * <p>服务器与客户端各有一份：服务器那份是权威的（冻结电梯、给新玩家补状态都看它），
 * 由 {@code LiftColorNetwork} 在收到客户端请求时写入并广播；客户端那份由广播喂，
 * 只用来算渲染开度与本地冻结镜像。</p>
 *
 * <p>一轮检修开门的语义是<b>保持</b>：开到位就一直停在全开，直到有人再用三角钥匙关它。
 * 这跟 MTR 到站自动开关门是两回事，所以这张表不参与「到站」那一套。</p>
 */
public final class LiftMaintenanceRegistry {

    /** 一格门方块的一条检修记录。 */
    public static final class Entry {
        /** 这格门方块的坐标键（一扇门有 2~4 格，各自一条记录）。 */
        public final long key;
        /** 这扇门属于哪台电梯。 */
        public final long liftId;
        /** 这扇门是电梯的第几层（-1 = 没认出来，只影响轿厢门跟不跟着开）。 */
        public final int floorIndex;
        /** true = 目标是打开（开到位后保持）；false = 正在关门。 */
        public final boolean open;
        /** 本轮动画时长（毫秒）。 */
        public final long durationMs;
        /** 本轮开始时刻（{@code System.currentTimeMillis}）。 */
        public final long startMillis;
        /** 本轮开始时的开度，用来从当前位置平滑接上，不会跳一下。 */
        public final double fromValue;

        Entry(long key, long liftId, int floorIndex, boolean open, long durationMs,
              long startMillis, double fromValue) {
            this.key = key;
            this.liftId = liftId;
            this.floorIndex = floorIndex;
            this.open = open;
            this.durationMs = Math.max(1L, durationMs);
            this.startMillis = startMillis;
            this.fromValue = fromValue;
        }

        /** 这条记录是否已经走完：关门动画结束才算走完；开到位的那类永远不算（要一直保持着）。 */
        public boolean isFinished(long now) {
            return !open && now - startMillis >= durationMs;
        }
    }

    /** 方块坐标 long → 记录。渲染线程读、主线程写。 */
    private static final Map<Long, Entry> ENTRIES = new ConcurrentHashMap<>();
    /** 电梯 id → 它当前占用的门方块坐标（判断这台电梯还被不被检修联锁锁着）。 */
    private static final Map<Long, Set<Long>> BY_LIFT = new ConcurrentHashMap<>();
    /**
     * 写侧串行锁。
     *
     * <p>读侧（渲染每帧问开度、仿真每拍问有没有被锁）走 {@code ConcurrentHashMap} 的无锁读；
     * 只有「切换 / 清理」这一小段需要串行。原因是删一格时要顺手判断这台电梯的门是不是全删完了、
     * 全删完就把 {@code BY_LIFT} 里那一整桶移掉：两个写入者同时走到这里，或者一边删桶一边
     * {@code computeIfAbsent} 拿旧桶往里加，就会留下「{@code ENTRIES} 里有、索引里没有」的孤儿格，
     * {@link #isLocked(long)} 从此再也看不见它。写操作本身就是玩家点一下钥匙的量级，加锁没有代价。</p>
     */
    private static final Object WRITE_LOCK = new Object();

    private LiftMaintenanceRegistry() {
    }

    /**
     * 应用一次检修开关门。
     *
     * @param keys       这一组门方块的坐标（服务端解析后随包下发，保证所有玩家看到同一组）
     * @param floorIndex 这组门属于第几层；-1 表示没认出来
     * @param open       true = 开门并保持；false = 关门
     * @param durationMs 本轮动画时长
     */
    public static void apply(long liftId, int floorIndex, Collection<Long> keys, boolean open, long durationMs) {
        apply(liftId, floorIndex, keys, open, durationMs, 0L);
    }

    /**
     * 指定开始时刻地写入记录；{@code startMillis <= 0} 表示「就从现在开始」。
     */
    public static void apply(long liftId, int floorIndex, Collection<Long> keys, boolean open,
                             long durationMs, long startMillis) {
        synchronized (WRITE_LOCK) {
            final long now = System.currentTimeMillis();
            final long actualStart = startMillis > 0L ? startMillis : now;
            for (final Long key : keys) {
                final Entry old = ENTRIES.get(key);
                // 回声守卫：同方向、上一轮还在播的时候保留原来的开始时刻，
                // 免得「本地先开 → 服务端广播回来又开一次」把动画从头重播，看着就是抖一下。
                if (old != null && old.open == open && !old.isFinished(now)) {
                    continue;
                }
                final double from = currentOpen(old, now);
                ENTRIES.put(key, new Entry(key, liftId, floorIndex, open, durationMs, actualStart, from));
                BY_LIFT.computeIfAbsent(liftId, id -> ConcurrentHashMap.newKeySet()).add(key);
            }
            sweepLocked(now);
        }
    }

    /** 此刻这格门的开度（0 = 关严，{@link LiftDoorService#DOOR_FULL_OPEN} = 全开）。 */
    public static double currentOpen(Entry entry, long now) {
        if (entry == null) {
            return 0.0D;
        }
        final long elapsed = now - entry.startMillis;
        if (entry.isFinished(now)) {
            return 0.0D;
        }
        return LiftDoorService.maintenanceValue(entry.open, elapsed, entry.durationMs, entry.fromValue);
    }

    /** 渲染查询：这一格门现在该开多大；不在检修状态返回 {@code null}（交回 MTR 自己算）。 */
    public static Double valueAt(long blockPosKey) {
        final Entry entry = ENTRIES.get(blockPosKey);
        if (entry == null) {
            return null;
        }
        final long now = System.currentTimeMillis();
        if (entry.isFinished(now)) {
            remove(blockPosKey, entry.liftId);
            return null;
        }
        return currentOpen(entry, now);
    }

    /**
     * 这台电梯现在是不是还在检修联锁里：有任何一扇门开着、或者还有一扇门正在关门的路上都算。
     *
     * <p>关门动画没播完就解冻的话，轿厢会在门还在合的过程中就开走——现实里安全回路是
     * 门完全关好才复位的，这里照这个来。</p>
     */
    public static boolean isLocked(long liftId) {
        final Set<Long> keys = BY_LIFT.get(liftId);
        if (keys == null || keys.isEmpty()) {
            return false;
        }
        final long now = System.currentTimeMillis();
        boolean locked = false;
        for (final Long key : keys) {
            final Entry entry = ENTRIES.get(key);
            if (entry == null) {
                continue;
            }
            if (entry.isFinished(now)) {
                remove(key, liftId);
            } else {
                locked = true;
            }
        }
        return locked;
    }

    /**
     * 轿厢门该开多大：只有「车正好平层在这台电梯某个<b>开着</b>的检修门前面」时才回值，
     * 否则返回 {@code null} 交给 MTR 自己判断。
     *
     * <p>这个前提很重要：车停在别处、检修门开在另一层时，如果也把开度推给轿厢门，
     * MTR 会把这个值写进车门口那几扇层门，就会出现「没人开它、它自己开了」的怪事。</p>
     *
     * @param isLevel 判断某个楼层索引现在是不是正好平层（由调用方用 MTR 自己的轨道进度算）
     */
    public static Double carDoorValue(long liftId, java.util.function.IntPredicate isLevel) {
        final Set<Long> keys = BY_LIFT.get(liftId);
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        final long now = System.currentTimeMillis();
        Double best = null;
        for (final Long key : keys) {
            final Entry entry = ENTRIES.get(key);
            if (entry == null) {
                continue;
            }
            if (entry.isFinished(now)) {
                remove(key, liftId);
                continue;
            }
            if (!entry.open || entry.floorIndex < 0 || !isLevel.test(entry.floorIndex)) {
                continue;
            }
            final double value = currentOpen(entry, now);
            if (best == null || value > best) {
                best = value;
            }
        }
        return best;
    }

    /** 某个电梯的某一层现在是不是已经开着（决定三角钥匙这次是开还是关）。 */
    public static boolean isDoorOpen(long liftId, Collection<Long> keys) {
        final long now = System.currentTimeMillis();
        for (final Long key : keys) {
            final Entry entry = ENTRIES.get(key);
            if (entry != null && entry.liftId == liftId && entry.open && !entry.isFinished(now)) {
                return true;
            }
        }
        return false;
    }

    /** 表里所有还没关严的记录；玩家加入时按这个补状态。 */
    public static List<Entry> snapshot() {
        final long now = System.currentTimeMillis();
        final List<Entry> out = new ArrayList<>();
        for (final Entry entry : ENTRIES.values()) {
            if (!entry.isFinished(now)) {
                out.add(entry);
            }
        }
        return out;
    }

    /**
     * 服务器驳回：把这些格的检修状态直接撤掉，门交回 MTR。
     *
     * <p>只撤属于这台电梯的记录——万一同坐标已经有别的（合法的）记录，不动它。</p>
     */
    public static void reject(long liftId, Collection<Long> keys) {
        synchronized (WRITE_LOCK) {
            for (final Long key : keys) {
                final Entry entry = ENTRIES.get(key);
                if (entry != null && entry.liftId == liftId) {
                    removeLocked(key, liftId);
                }
            }
        }
    }

    /** 删掉一格的记录；这台电梯没门了就把索引也清掉。 */
    public static void remove(long blockPosKey, long liftId) {
        synchronized (WRITE_LOCK) {
            removeLocked(blockPosKey, liftId);
        }
    }

    /** 真正的删除；调用方必须已持有 {@link #WRITE_LOCK}。 */
    private static void removeLocked(long blockPosKey, long liftId) {
        ENTRIES.remove(blockPosKey);
        final Set<Long> keys = BY_LIFT.get(liftId);
        if (keys != null) {
            keys.remove(blockPosKey);
            if (keys.isEmpty()) {
                BY_LIFT.remove(liftId);
            }
        }
    }

    /**
     * 清掉已经关严的记录；调用方必须已持有 {@link #WRITE_LOCK}。
     *
     * <p>先复制一份再删：{@code isLocked} / {@code carDoorValue} 在遍历索引的过程中就会顺手删格，
     * 直接边遍历边删容易漏格。</p>
     */
    private static void sweepLocked(long now) {
        for (final Map.Entry<Long, Entry> e : new ArrayList<>(ENTRIES.entrySet())) {
            if (e.getValue().isFinished(now)) {
                removeLocked(e.getKey(), e.getValue().liftId);
            }
        }
    }

    /** 换世界 / 断线 / 关服时整表清空。 */
    public static void clear() {
        synchronized (WRITE_LOCK) {
            ENTRIES.clear();
            BY_LIFT.clear();
        }
    }

}
