package com.xiao.mtrliftcolornew.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import org.mtr.libraries.com.google.gson.Gson;
import org.mtr.libraries.com.google.gson.GsonBuilder;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 服务端权威的电梯配色存储。
 *
 * <p>与客户端一样采用「每台电梯一份 JSON」的分梯架构，落在服务器存档目录下：</p>
 * <pre>
 *   &lt;服务器世界目录&gt;/mtrliftcolor/&lt;维度命名空间&gt;/&lt;维度路径&gt;/lift_configs/&lt;电梯名或短码&gt;/&lt;两字符短码&gt;.json
 * </pre>
 *
 * <p>文件名为由电梯 id 生成的<b>两字符短码</b>（如 {@code E5}、{@code 32}），而不是一长串十六进制，
 * 便于在存档目录里一眼认出是哪台电梯。</p>
 *
 * <p>服务端持有唯一的内存缓存，写入磁盘后广播给所有在线玩家；玩家加入时把已有配置
 * 全部发给他，从而保证不同玩家看到一致的颜色。</p>
 *
 * <p>为了让读档时能还原出电梯的长 id，JSON 里额外写入了 liftId / liftName / hexId 三个字段；
 * 旧版本客户端只写配色的字段，因此读取时若缺 liftId 会跳过该文件（不会把颜色弄丢）。</p>
 */
public final class LiftColorServerStore {

    private static final Map<Long, StoredConfig> CONFIGS = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LiftColorServerStore() {
    }

    /** 客户端发来的某台电梯新配色：写入磁盘并广播。 */
    public static void applyUpdate(MinecraftServer server, long liftId, String liftName, String hexId,
                                   String dimNamespace, String dimPath,
                                   int color, float scale, float offX, float offY) {
        final StoredConfig config = new StoredConfig(color, scale, offX, offY, liftName, hexId, dimNamespace, dimPath);
        CONFIGS.put(liftId, config);
        writeToWorld(server, liftId, config);
        for (final ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            LiftColorNetwork.sendToPlayer(player, liftId, color, scale, offX, offY);
        }
    }

    /** 玩家加入时，把服务端现阶段所有已保存的配色发给他。 */
    public static void syncTo(ServerPlayerEntity player) {
        for (final Map.Entry<Long, StoredConfig> entry : CONFIGS.entrySet()) {
            final StoredConfig config = entry.getValue();
            LiftColorNetwork.sendToPlayer(player, entry.getKey(), config.color, config.scale, config.offX, config.offY);
        }
    }

    /** 服务器启动时，把世界目录下已存在的配色扫进内存缓存。 */
    public static void loadFromWorld(MinecraftServer server) {
        CONFIGS.clear();
        final Path base = server.getSavePath(WorldSavePath.ROOT).resolve("mtrliftcolor");
        if (!Files.isDirectory(base)) {
            return;
        }
        try (Stream<Path> files = Files.walk(base)) {
            files
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                    .forEach(path -> loadOne(path));
        } catch (IOException ignored) {
            // 扫描失败不影响游戏，等玩家改色时再写
        }
    }

    private static void loadOne(Path file) {
        try {
            final JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (!json.has("liftId")) {
                return; // 旧格式文件没有长 id，无法可靠还原，跳过
            }
            final long liftId = json.get("liftId").getAsLong();
            final int color = json.has("displayColor") ? json.get("displayColor").getAsInt() : 0xFFFF0000;
            final float scale = json.has("arrowScale") ? (float) json.get("arrowScale").getAsDouble() : 1.0F;
            final float offX = json.has("arrowOffsetX") ? (float) json.get("arrowOffsetX").getAsDouble() : 0.0F;
            final float offY = json.has("arrowOffsetY") ? (float) json.get("arrowOffsetY").getAsDouble() : 0.0F;
            final String liftName = json.has("liftName") ? json.get("liftName").getAsString() : "";
            final String hexId = json.has("hexId") ? json.get("hexId").getAsString() : file.getFileName().toString().replace(".json", "");

            // 从目录结构里还原维度：.../mtrliftcolor/<dimNs>/<dimPath>/lift_configs/<文件夹>/<文件名>.json
            final Path folder = file.getParent();
            final Path liftConfigs = folder == null ? null : folder.getParent();
            final Path dimPathDir = liftConfigs == null ? null : liftConfigs.getParent();
            final Path dimNsDir = dimPathDir == null ? null : dimPathDir.getParent();
            final String dimNamespace = dimNsDir == null ? "minecraft" : dimNsDir.getFileName().toString();
            final String dimPath = dimPathDir == null ? "overworld" : dimPathDir.getFileName().toString();

            CONFIGS.put(liftId, new StoredConfig(color, scale, offX, offY, liftName, hexId, dimNamespace, dimPath));
        } catch (IOException | RuntimeException ignored) {
            // 单个文件损坏只跳过该电梯
        }
    }

    private static void writeToWorld(MinecraftServer server, long liftId, StoredConfig config) {
        // 文件夹用电梯名（便于辨认）；没有名字时退化为短码。文件名用两字符短码。
        final String folderName = (config.liftName == null || config.liftName.isEmpty())
                ? shortCode(liftId)
                : config.liftName;
        final Path folder = server.getSavePath(WorldSavePath.ROOT)
                .resolve("mtrliftcolor")
                .resolve(config.dimNamespace)
                .resolve(config.dimPath)
                .resolve("lift_configs")
                .resolve(folderName);
        try {
            Files.createDirectories(folder);
            final JsonObject json = new JsonObject();
            json.addProperty("displayColor", config.color);
            json.addProperty("arrowScale", config.scale);
            json.addProperty("arrowOffsetX", config.offX);
            json.addProperty("arrowOffsetY", config.offY);
            json.addProperty("liftId", liftId);
            json.addProperty("liftName", config.liftName == null ? "" : config.liftName);
            json.addProperty("hexId", config.hexId == null ? "" : config.hexId);
            Files.writeString(folder.resolve(shortCode(liftId) + ".json"), GSON.toJson(json));
        } catch (IOException | RuntimeException ignored) {
            // 写入失败只影响本次保存
        }
    }

    /** 用电梯 id 生成一个两字符短码（如 E5、32），用作配置文件名，避免一长串十六进制。 */
    private static String shortCode(long liftId) {
        final long value = Math.floorMod(liftId, 36L * 36L);
        final String encoded = Long.toString(value, 36).toUpperCase();
        return encoded.length() < 2 ? "0" + encoded : encoded;
    }

    private static final class StoredConfig {
        private final int color;
        private final float scale;
        private final float offX;
        private final float offY;
        private final String liftName;
        private final String hexId;
        private final String dimNamespace;
        private final String dimPath;

        private StoredConfig(int color, float scale, float offX, float offY,
                             String liftName, String hexId, String dimNamespace, String dimPath) {
            this.color = color;
            this.scale = scale;
            this.offX = offX;
            this.offY = offY;
            this.liftName = liftName == null ? "" : liftName;
            this.hexId = hexId == null ? "" : hexId;
            this.dimNamespace = dimNamespace == null ? "minecraft" : dimNamespace;
            this.dimPath = dimPath == null ? "overworld" : dimPath;
        }
    }
}
