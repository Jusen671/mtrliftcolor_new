package com.xiao.mtrliftcolornew.config;

import com.xiao.mtrliftcolornew.network.LiftColorNetwork;
import net.minecraft.world.World;
import org.mtr.core.data.Lift;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/**
 * 客户端侧的电梯配色缓存。
 *
 * <p>数据不再由客户端直接写盘：保存时把配色发给服务器（服务器负责落盘 + 广播），
 * 渲染时读取缓存的配色。这样单人游戏、局域网、专用服务器都走同一条链路。</p>
 *
 * <p>当服务器不带本模组时，{@link #setAndSave} 仍会把配色记到缓存里，恰好让当前会话生效，
 * 只是不会持久化、也不会同步给其他人。</p>
 */
public final class LiftColorManager {

    private static final Object2ObjectOpenHashMap<Long, LiftColorConfig> CACHE = new Object2ObjectOpenHashMap<>();

    private LiftColorManager() {
    }

    /** 取某台电梯的配色（带缓存；没有则返回默认值）。 */
    public static LiftColorConfig get(Lift lift, World world) {
        final LiftColorConfig cached = CACHE.get(lift.getId());
        return cached == null ? new LiftColorConfig() : cached;
    }

    /** 保存某台电梯的配色：更新本地缓存并把新配色发给服务器。 */
    public static void setAndSave(Lift lift, World world, LiftColorConfig config) {
        if (config == null) {
            return;
        }
        final long id = lift.getId();
        final String liftName = lift.getName() == null ? "" : lift.getName();
        config.liftId = liftName;
        CACHE.put(id, config);

        final String dimNamespace = world.getRegistryKey().getValue().getNamespace();
        final String dimPath = world.getRegistryKey().getValue().getPath();
        LiftColorNetwork.sendToServer(
                id,
                liftName,
                hexIdOf(lift),
                dimNamespace,
                dimPath,
                config.displayColor,
                config.arrowScale,
                config.arrowOffsetX,
                config.arrowOffsetY);
    }

    /** 服务器同步过来的某台电梯配色。 */
    public static void applyServerSync(long liftId, int displayColor, float arrowScale, float arrowOffsetX, float arrowOffsetY) {
        CACHE.put(liftId, new LiftColorConfig(displayColor, arrowScale, arrowOffsetX, arrowOffsetY, ""));
    }

    /** 清空缓存（切换世界时由客户端调用）。 */
    public static void clearCache() {
        CACHE.clear();
    }

    /** 电梯的长十六进制 id，统一用小写（磁盘文件名与目录名都按小写存）。 */
    private static String hexIdOf(Lift lift) {
        String hex = lift.getHexId();
        if (hex == null || hex.isEmpty()) {
            hex = Long.toHexString(lift.getId());
        }
        return hex.toLowerCase(java.util.Locale.ROOT);
    }
}
