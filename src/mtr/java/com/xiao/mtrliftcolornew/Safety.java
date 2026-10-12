package com.xiao.mtrliftcolornew;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 注入代码的保险丝。
 *
 * <p>本模组有几段代码跑在 <b>MTR 自己的线程</b>上：{@code Lift.tick} 跑在电梯仿真线程
 * （服务器与客户端各一条），门方块的 {@code getDoorValue()} 跑在渲染线程。这些位置一旦
 * 抛出异常，死掉的是 MTR 的线程——表现就是「电梯全部瘫痪、轿厢屏幕不再刷新、整个模组像
 * 坏掉一样」，而且本模组自己的功能反而看起来是最后死的那个，特别难查。</p>
 *
 * <p>所以所有注入体都必须用 {@link #run} / {@link #call} 包住：出了任何异常（包括
 * {@code Error}，例如不兼容版本的 {@code AbstractMethodError} / {@code NoSuchFieldError}）
 * 就地吞掉、每个位置只往日志里写前几条，本模组的功能降级为「不生效」，
 * 但绝不会再拖垮 MTR 与染色这些核心功能。</p>
 */
public final class Safety {

    /** 每个位置已经记录过的异常条数（超过上限就不再刷日志）。 */
    private static final Map<String, AtomicInteger> SEEN = new ConcurrentHashMap<>();
    /** 单个位置最多写几条日志，之后只计数。 */
    private static final int LOG_LIMIT = 3;

    private Safety() {
    }

    /** 跑一段无返回值的受保护代码；异常只记录，不向外传播。 */
    public static void run(String where, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            report(where, t);
        }
    }

    /**
     * 跑一段有返回值的受保护代码；出错时返回 {@code fallbackOnFailure}。
     */
    public static <T> T call(String where, java.util.function.Supplier<T> body, T fallbackOnFailure) {
        try {
            return body.get();
        } catch (Throwable t) {
            report(where, t);
            return fallbackOnFailure;
        }
    }

    private static void report(String where, Throwable t) {
        final int count = SEEN.computeIfAbsent(where, k -> new AtomicInteger()).incrementAndGet();
        if (count <= LOG_LIMIT) {
            MTRLiftColorMod.LOGGER.warn("[MTRLiftColor] {} 处异常已忽略（第 {} 次，不会中断游戏）：{}",
                    where, count, t.toString());
            if (count == LOG_LIMIT) {
                MTRLiftColorMod.LOGGER.warn("[MTRLiftColor] {} 的后续同类异常不再写日志。请把 logs/latest.log 发给模组作者。", where);
            }
        }
    }
}
