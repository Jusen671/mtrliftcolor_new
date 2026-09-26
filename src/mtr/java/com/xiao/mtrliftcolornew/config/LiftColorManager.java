package com.xiao.mtrliftcolornew.config;

import com.xiao.mtrliftcolornew.network.LiftColorNetwork;
import net.minecraft.world.World;
import org.mtr.core.data.Lift;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 客户端侧的电梯配色与自动化缓存。
 *
 * <p>数据不再由客户端直接写盘：保存时把配色 / 自动化积木链发给服务器（服务器负责落盘 + 广播），
 * 渲染时读取缓存。这样单人游戏、局域网、专用服务器都走同一条链路。</p>
 *
 * <p>当服务器不带本模组时，{@link #setAndSave} 仍会把配色记到缓存里，恰好让当前会话生效，
 * 只是不会持久化、也不会同步给其他人。</p>
 *
 * <p>每条配色缓存都附带电梯元数据（长 id、名字、十六进制 id、所在维度），因此
 * 「自动化」界面可以跨维度批量下发积木链模板。</p>
 */
public final class LiftColorManager {

    private static final Object2ObjectOpenHashMap<Long, LiftColorConfig> CACHE = new Object2ObjectOpenHashMap<>();
    /** 按电梯 id 缓存的自动化积木链规则（上行 / 下行各一条）。 */
    private static final Object2ObjectOpenHashMap<Long, AutoRule> AUTO_RULES = new Object2ObjectOpenHashMap<>();

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
        config.hexId = hexIdOf(lift);
        if (world != null) {
            config.dimNamespace = world.getRegistryKey().getValue().getNamespace();
            config.dimPath = world.getRegistryKey().getValue().getPath();
        }
        CACHE.put(id, config);
        sendToServer(id, config);
    }

    /** 服务器同步过来的某台电梯配色（含元数据）。 */
    public static void applyServerSync(long liftId, int displayColor, float arrowScale, float arrowOffsetX,
                                       float arrowOffsetY, int arrowDirection,
                                       String liftName, String hexId, String dimNamespace, String dimPath) {
        final LiftColorConfig config = new LiftColorConfig(displayColor, arrowScale, arrowOffsetX, arrowOffsetY,
                liftName == null ? "" : liftName);
        config.arrowDirection = LiftColorConfig.clampDirection(arrowDirection);
        config.hexId = hexId == null ? "" : hexId;
        config.dimNamespace = dimNamespace == null || dimNamespace.isEmpty() ? "minecraft" : dimNamespace;
        config.dimPath = dimPath == null || dimPath.isEmpty() ? "overworld" : dimPath;
        CACHE.put(liftId, config);
    }

    /** 缓存快照（用于导出与批量自动化）。 */
    public static List<Map.Entry<Long, LiftColorConfig>> entries() {
        return new ArrayList<>(CACHE.entrySet());
    }

    /** 当前缓存里有多少条电梯配置记录。 */
    public static int recordCount() {
        return CACHE.size();
    }

    // ---------- 自动化积木链 ----------

    /** 服务器同步过来的某台电梯自动化积木链（坏数据按空链处理）。 */
    public static void applyAutoServerSync(long liftId, String upJson, String downJson) {
        final AutoRule rule = new AutoRule(AutoChain.fromJson(upJson), AutoChain.fromJson(downJson));
        if (rule.isEmpty()) {
            AUTO_RULES.remove(liftId);
        } else {
            AUTO_RULES.put(liftId, rule);
        }
    }

    /** 某台电梯的自动化规则；没有则返回 null。渲染用。 */
    public static AutoRule getAutoRule(long liftId) {
        return AUTO_RULES.get(liftId);
    }

    /**
     * 把图形化界面积好的模板链批量下发到电梯（测试功能）。
     *
     * @param onlyCurrentDimension true = 只处理当前维度的记录；false = 本世界全部已保存记录
     * @return 实际下发的电梯数量；world 为 null 时返回 -1
     */
    public static int applyTemplate(World world, boolean onlyCurrentDimension, AutoChain up, AutoChain down) {
        if (world == null) {
            return -1;
        }
        final String ns = world.getRegistryKey().getValue().getNamespace();
        final String path = world.getRegistryKey().getValue().getPath();
        final String upJson = up.toJson();
        final String downJson = down.toJson();
        int count = 0;
        for (final Map.Entry<Long, LiftColorConfig> entry : entries()) {
            final LiftColorConfig config = entry.getValue();
            if (onlyCurrentDimension && !(ns.equals(config.dimNamespace) && path.equals(config.dimPath))) {
                continue;
            }
            // 本地立即生效（服务器会再广播确认回来）
            applyAutoServerSync(entry.getKey(), upJson, downJson);
            LiftColorNetwork.sendAutoUpdateToServer(entry.getKey(), config.liftId, config.hexId,
                    config.dimNamespace, config.dimPath, upJson, downJson);
            count++;
        }
        return count;
    }

    /** 请求服务器把全部自动化配置导出到存档的 mtrliftcolor/output/ 目录。 */
    public static void requestExport() {
        LiftColorNetwork.sendAutoOpToServer(LiftColorNetwork.AUTO_OP_EXPORT);
    }

    /** 请求服务器从存档的 mtrliftcolor/output/ 目录导入自动化配置。 */
    public static void requestImport() {
        LiftColorNetwork.sendAutoOpToServer(LiftColorNetwork.AUTO_OP_IMPORT);
    }

    // ---------- 内部 ----------

    /** 按记录的元数据把一条配色发给服务器。 */
    private static void sendToServer(long liftId, LiftColorConfig config) {
        LiftColorNetwork.sendToServer(
                liftId,
                config.liftId,
                config.hexId,
                config.dimNamespace,
                config.dimPath,
                config.displayColor,
                config.arrowScale,
                config.arrowOffsetX,
                config.arrowOffsetY,
                config.arrowDirection);
    }

    /** 清空缓存（切换世界时由客户端调用）。 */
    public static void clearCache() {
        CACHE.clear();
        AUTO_RULES.clear();
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
