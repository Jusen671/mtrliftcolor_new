package com.xiao.mtrliftcolornew.network;

import com.xiao.mtrliftcolornew.config.AutoChain;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import org.mtr.libraries.com.google.gson.Gson;
import org.mtr.libraries.com.google.gson.GsonBuilder;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 服务端权威的电梯配色存储。
 *
 * <p>与客户端一样采用「每台电梯一份 JSON」的分梯架构，落在服务器存档目录下。
 * 结构跟随原版 MTR 按<b>维度</b>分目录，落在「mtrliftcolor/minecraft/&lt;维度&gt;/lift_configs/…」：</p>
 * <pre>
 *   &lt;服务器世界目录&gt;/mtrliftcolor/minecraft/overworld/lift_configs/&lt;两字符小写短码&gt;/&lt;一长串小写十六进制&gt;.json
 *                                             /&lt;两字符小写短码&gt;/auto/&lt;同样的十六进制&gt;.json   ← 自动化积木链
 *                                             /the_end/lift_configs/&lt;两字符小写短码&gt;/&lt;一长串小写十六进制&gt;.json
 *                                             /the_nether/lift_configs/&lt;两字符小写短码&gt;/&lt;一长串小写十六进制&gt;.json
 * </pre>
 *
 * <p>文件夹用电梯 id 生成的<b>两字符小写短码</b>（如 {@code e5}、{@code 32}、{@code 8c}），
 * 文件名用电梯的<b>一长串小写十六进制 id</b>（{@code Lift#getHexId()}，如 {@code 0000000000000e5a}.json）。
 * 自动化的积木链配置写在同一短码文件夹下的 {@code auto/} 子目录里，文件名与配色文件一致。</p>
 *
 * <p>「导出 / 导入」把散落在各维度、各短码文件夹里的 auto 配置集中复制到
 * {@code mtrliftcolor/output/}（按同样的十六进制文件名），用户不必逐层翻文件夹。</p>
 *
 * <p>服务端持有唯一的内存缓存，写入磁盘后广播给所有在线玩家；玩家加入时把已有配置
 * 全部发给他，从而保证不同玩家看到一致的颜色。</p>
 *
 * <p>为了让读档时能还原出电梯的长 id，JSON 里额外写入了 liftId / liftName / hexId 三个字段；
 * 旧版本客户端只写配色的字段，因此读取时若缺 liftId 会跳过该文件（不会把颜色弄丢）。</p>
 */
public final class LiftColorServerStore {

    private static final Map<Long, StoredConfig> CONFIGS = new HashMap<>();
    private static final Map<Long, StoredAuto> AUTOS = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** 与 MTR 原版一致，读档时保证存在的三个标准维度目录。 */
    private static final String[] STANDARD_DIMENSIONS = {"overworld", "the_nether", "the_end"};
    /** 自动化配置子目录名 / 导出目录名。 */
    private static final String AUTO_FOLDER = "auto";
    private static final String OUTPUT_FOLDER = "output";

    private LiftColorServerStore() {
    }

    /** 客户端发来的某台电梯新配色：写入磁盘并广播。 */
    public static void applyUpdate(MinecraftServer server, long liftId, String liftName, String hexId,
                                   String dimNamespace, String dimPath,
                                   int color, float scale, float offX, float offY, int direction) {
        final StoredConfig config = new StoredConfig(color, scale, offX, offY, direction, liftName, hexId, dimNamespace, dimPath);
        CONFIGS.put(liftId, config);
        writeToWorld(server, liftId, config);
        for (final ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendTo(player, liftId, config);
        }
    }

    /** 玩家加入时，把服务端现阶段所有已保存的配色与自动化配置发给他。 */
    public static void syncTo(ServerPlayerEntity player) {
        for (final Map.Entry<Long, StoredConfig> entry : CONFIGS.entrySet()) {
            sendTo(player, entry.getKey(), entry.getValue());
        }
        for (final Map.Entry<Long, StoredAuto> entry : AUTOS.entrySet()) {
            LiftColorNetwork.sendAutoToPlayer(player, entry.getKey(), entry.getValue().upJson, entry.getValue().downJson);
        }
    }

    // ---------- 自动化积木链 ----------

    /** 客户端发来的某台电梯自动化积木链：规范格式、写入磁盘并广播。 */
    public static void applyAutoUpdate(MinecraftServer server, long liftId, String liftName, String hexId,
                                       String dimNamespace, String dimPath, String upJson, String downJson) {
        // 借 AutoChain 解析一遍再序列化，保证落盘 / 广播的一定是合法数组
        final String normUp = AutoChain.fromJson(upJson).toJson();
        final String normDown = AutoChain.fromJson(downJson).toJson();
        final StoredAuto auto = new StoredAuto(normUp, normDown, liftName, hexId, dimNamespace, dimPath);
        AUTOS.put(liftId, auto);
        writeAutoToWorld(server, liftId, auto);
        LiftColorNetwork.broadcastAuto(server, liftId, normUp, normDown);
    }

    /** 导出：把全部自动化配置按同样的十六进制文件名集中复制到 mtrliftcolor/output/。 */
    public static void exportAll(MinecraftServer server, ServerPlayerEntity player) {
        final Path output = server.getSavePath(WorldSavePath.ROOT).resolve("mtrliftcolor").resolve(OUTPUT_FOLDER);
        int count = 0;
        String error = null;
        try {
            Files.createDirectories(output);
            for (final Map.Entry<Long, StoredAuto> entry : AUTOS.entrySet()) {
                final StoredAuto auto = entry.getValue();
                final String fileName = (auto.hexId == null || auto.hexId.isEmpty())
                        ? shortCode(entry.getKey())
                        : auto.hexId.toLowerCase(Locale.ROOT);
                Files.writeString(output.resolve(fileName + ".json"), GSON.toJson(buildAutoDoc(entry.getKey(), auto)));
                count++;
            }
        } catch (IOException | RuntimeException e) {
            error = e.getClass().getSimpleName();
        }
        reply(player, error == null
                ? Text.literal("[MTRLiftColor] 已导出 " + count + " 个自动化配置到 mtrliftcolor/" + OUTPUT_FOLDER + "/")
                : Text.literal("[MTRLiftColor] 导出失败: " + error));
    }

    /** 导入：读取 mtrliftcolor/output/ 里所有 JSON，逐条写回对应维度的 auto/ 目录并广播。 */
    public static void importAll(MinecraftServer server, ServerPlayerEntity player) {
        final Path output = server.getSavePath(WorldSavePath.ROOT).resolve("mtrliftcolor").resolve(OUTPUT_FOLDER);
        if (!Files.isDirectory(output)) {
            reply(player, Text.literal("[MTRLiftColor] 没有找到导出目录 mtrliftcolor/" + OUTPUT_FOLDER + "/"));
            return;
        }
        int count = 0;
        try (Stream<Path> files = Files.list(output)) {
            final List<Path> jsonFiles = files
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .collect(java.util.stream.Collectors.toList());
            for (final Path file : jsonFiles) {
                final JsonObject json;
                try {
                    json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                } catch (IOException | RuntimeException e) {
                    continue; // 坏文件跳过
                }
                final StoredAuto auto = readAutoDoc(json);
                if (auto == null) {
                    continue;
                }
                final long liftId = json.get("liftId").getAsLong();
                final String normUp = AutoChain.fromJson(auto.upJson).toJson();
                final String normDown = AutoChain.fromJson(auto.downJson).toJson();
                final StoredAuto stored = new StoredAuto(normUp, normDown, auto.liftName, auto.hexId, auto.dimNamespace, auto.dimPath);
                AUTOS.put(liftId, stored);
                writeAutoToWorld(server, liftId, stored);
                LiftColorNetwork.broadcastAuto(server, liftId, normUp, normDown);
                count++;
            }
        } catch (IOException | RuntimeException e) {
            reply(player, Text.literal("[MTRLiftColor] 导入失败: " + e.getClass().getSimpleName()));
            return;
        }
        reply(player, count == 0
                ? Text.literal("[MTRLiftColor] mtrliftcolor/" + OUTPUT_FOLDER + "/ 里没有可导入的配置")
                : Text.literal("[MTRLiftColor] 已从 mtrliftcolor/" + OUTPUT_FOLDER + "/ 导入 " + count + " 个自动化配置"));
    }

    private static void reply(ServerPlayerEntity player, Text message) {
        if (player != null) {
            player.sendMessage(message, false);
        }
    }

    /** 自动化配置文件的 JSON 结构（磁盘与导出目录共用同一格式）。 */
    private static JsonObject buildAutoDoc(long liftId, StoredAuto auto) {
        final JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        json.addProperty("liftId", Long.toString(liftId));
        json.addProperty("liftName", auto.liftName == null ? "" : auto.liftName);
        json.addProperty("hexId", auto.hexId == null ? "" : auto.hexId);
        json.addProperty("dimNamespace", auto.dimNamespace);
        json.addProperty("dimPath", auto.dimPath);
        json.add("up", JsonParser.parseString(auto.upJson));
        json.add("down", JsonParser.parseString(auto.downJson));
        return json;
    }

    /** 从 JSON 文档还原自动化记录；缺关键字段返回 null。 */
    private static StoredAuto readAutoDoc(JsonObject json) {
        try {
            if (!json.has("liftId")) {
                return null;
            }
            json.get("liftId").getAsLong();
            final String up = json.has("up") ? json.getAsJsonArray("up").toString() : "[]";
            final String down = json.has("down") ? json.getAsJsonArray("down").toString() : "[]";
            final String liftName = json.has("liftName") ? json.get("liftName").getAsString() : "";
            final String hexId = json.has("hexId") ? json.get("hexId").getAsString() : "";
            final String ns = json.has("dimNamespace") ? json.get("dimNamespace").getAsString() : "minecraft";
            final String path = json.has("dimPath") ? json.get("dimPath").getAsString() : "overworld";
            return new StoredAuto(up, down, liftName, hexId, ns, path);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 读档时判断某个 JSON 是不是自动化链文件：位于 …/lift_configs/&lt;短码&gt;/auto/ 之下。 */
    private static boolean isAutoFile(Path file) {
        final Path parent = file.getParent();
        if (parent == null || !"auto".equals(parent.getFileName().toString())) {
            return false;
        }
        final Path configs = parent.getParent();
        return configs != null && "lift_configs".equals(configs.getFileName().toString());
    }

    /** 把一条存储记录按新协议发给某个玩家。 */
    private static void sendTo(ServerPlayerEntity player, long liftId, StoredConfig config) {
        LiftColorNetwork.sendToPlayer(player, liftId, config.color, config.scale, config.offX, config.offY,
                config.direction, config.liftName, config.hexId, config.dimNamespace, config.dimPath);
    }

    /**
     * 服务器启动（加载存档）时：
     * <ol>
     *   <li>先把 overworld / the_nether / the_end 三个维度目录连同 lift_configs 子目录建出来
     *       ——与 MTR 原版一致，即便末地、下界还没放过电梯也会生成这两个文件夹；</li>
     *   <li>再把世界目录下已存在的配色扫进内存缓存。</li>
     * </ol>
     */
    public static void loadFromWorld(MinecraftServer server) {
        CONFIGS.clear();
        AUTOS.clear();
        final Path base = server.getSavePath(WorldSavePath.ROOT).resolve("mtrliftcolor");
        ensureDimensionFolders(base);
        if (!Files.isDirectory(base)) {
            return;
        }
        final Path output = base.resolve(OUTPUT_FOLDER);
        try (Stream<Path> files = Files.walk(base)) {
            files
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    // output/ 是集中导出的镜像目录，读档时不重复加载
                    .filter(path -> !isUnder(path, output))
                    .forEach(path -> {
                        if (isAutoFile(path)) {
                            loadAuto(path);
                        } else {
                            loadOne(path);
                        }
                    });
        } catch (IOException ignored) {
            // 扫描失败不影响游戏，等玩家改色时再写
        }
    }

    /** 文件是否位于指定目录之下。 */
    private static boolean isUnder(Path file, Path dir) {
        final Path parent = file.getParent();
        return parent != null && parent.equals(dir);
    }

    /** 读入一个 auto/&lt;hex&gt;.json 文件。 */
    private static void loadAuto(Path file) {
        try {
            final JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            final StoredAuto auto = readAutoDoc(json);
            if (auto == null) {
                return;
            }
            // 文件里没写维度时，从目录结构还原：.../mtrliftcolor/<ns>/<path>/lift_configs/<sc>/auto/<hex>.json
            String ns = auto.dimNamespace;
            String path = auto.dimPath;
            final Path scDir = file.getParent() == null ? null : file.getParent().getParent();
            final Path liftConfigs = scDir == null ? null : scDir.getParent();
            final Path dimPathDir = liftConfigs == null ? null : liftConfigs.getParent();
            final Path dimNsDir = dimPathDir == null ? null : dimPathDir.getParent();
            if (dimNsDir != null && dimPathDir != null && "lift_configs".equals(liftConfigs.getFileName().toString())) {
                ns = dimNsDir.getFileName().toString();
                path = dimPathDir.getFileName().toString();
            }
            AUTOS.put(json.get("liftId").getAsLong(),
                    new StoredAuto(AutoChain.fromJson(auto.upJson).toJson(), AutoChain.fromJson(auto.downJson).toJson(),
                            auto.liftName, auto.hexId, ns, path));
        } catch (IOException | RuntimeException ignored) {
            // 单个文件损坏只跳过该条自动化
        }
    }

    /** 补建三个标准维度的配置目录（存在的不动，创建失败也不影响游戏）。 */
    private static void ensureDimensionFolders(Path base) {
        for (final String dim : STANDARD_DIMENSIONS) {
            try {
                Files.createDirectories(base.resolve("minecraft").resolve(dim).resolve("lift_configs"));
            } catch (IOException | RuntimeException ignored) {
                // 只影响本次补建目录
            }
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
            final int direction = com.xiao.mtrliftcolornew.config.LiftColorConfig.clampDirection(
                    json.has("arrowDirection") ? json.get("arrowDirection").getAsInt() : 0);
            final String liftName = json.has("liftName") ? json.get("liftName").getAsString() : "";
            final String hexId = json.has("hexId") ? json.get("hexId").getAsString() : file.getFileName().toString().replace(".json", "");

            // 从目录结构里还原维度：.../mtrliftcolor/<dimNs>/<dimPath>/lift_configs/<文件夹>/<文件名>.json
            final Path folder = file.getParent();
            final Path liftConfigs = folder == null ? null : folder.getParent();
            final Path dimPathDir = liftConfigs == null ? null : liftConfigs.getParent();
            final Path dimNsDir = dimPathDir == null ? null : dimPathDir.getParent();
            final String dimNamespace = dimNsDir == null ? "minecraft" : dimNsDir.getFileName().toString();
            final String dimPath = dimPathDir == null ? "overworld" : dimPathDir.getFileName().toString();

            CONFIGS.put(liftId, new StoredConfig(color, scale, offX, offY, direction, liftName, hexId, dimNamespace, dimPath));
        } catch (IOException | RuntimeException ignored) {
            // 单个文件损坏只跳过该电梯
        }
    }

    private static void writeToWorld(MinecraftServer server, long liftId, StoredConfig config) {
        final String folderName = shortCode(liftId);
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
            json.addProperty("arrowDirection", config.direction);
            json.addProperty("liftId", liftId);
            json.addProperty("liftName", config.liftName == null ? "" : config.liftName);
            // 文件名用电梯的一长串小写十六进制 id；没有 hexId 时退化为两字符短码。
            final String fileName = (config.hexId == null || config.hexId.isEmpty())
                    ? folderName
                    : config.hexId.toLowerCase(Locale.ROOT);
            json.addProperty("hexId", fileName);
            Files.writeString(folder.resolve(fileName + ".json"), GSON.toJson(json));
        } catch (IOException | RuntimeException ignored) {
            // 写入失败只影响本次保存
        }
    }

    /** 把某台电梯的自动化配置写到 lift_configs/&lt;短码&gt;/auto/&lt;hex&gt;.json。 */
    private static void writeAutoToWorld(MinecraftServer server, long liftId, StoredAuto auto) {
        final String folderName = shortCode(liftId);
        final String fileName = (auto.hexId == null || auto.hexId.isEmpty()) ? folderName : auto.hexId.toLowerCase(Locale.ROOT);
        final Path folder = server.getSavePath(WorldSavePath.ROOT)
                .resolve("mtrliftcolor")
                .resolve(auto.dimNamespace)
                .resolve(auto.dimPath)
                .resolve("lift_configs")
                .resolve(folderName)
                .resolve(AUTO_FOLDER);
        try {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve(fileName + ".json"), GSON.toJson(buildAutoDoc(liftId, auto)));
        } catch (IOException | RuntimeException ignored) {
            // 写入失败只影响本次保存
        }
    }

    /** 用电梯 id 生成一个两字符小写短码（如 e5、32、8c），用作配置的文件夹名。 */
    private static String shortCode(long liftId) {
        final long value = Math.floorMod(liftId, 36L * 36L);
        final String encoded = Long.toString(value, 36).toLowerCase(Locale.ROOT);
        return encoded.length() < 2 ? "0" + encoded : encoded;
    }

    /** 服务端内存里的一条自动化记录：上行 / 下行两条积木链（JSON 数组字符串）加元数据。 */
    private static final class StoredAuto {
        private final String upJson;
        private final String downJson;
        private final String liftName;
        private final String hexId;
        private final String dimNamespace;
        private final String dimPath;

        private StoredAuto(String upJson, String downJson, String liftName, String hexId,
                           String dimNamespace, String dimPath) {
            this.upJson = upJson == null ? "[]" : upJson;
            this.downJson = downJson == null ? "[]" : downJson;
            this.liftName = liftName == null ? "" : liftName;
            this.hexId = hexId == null ? "" : hexId;
            this.dimNamespace = dimNamespace == null || dimNamespace.isEmpty() ? "minecraft" : dimNamespace;
            this.dimPath = dimPath == null || dimPath.isEmpty() ? "overworld" : dimPath;
        }
    }

    private static final class StoredConfig {
        private final int color;
        private final float scale;
        private final float offX;
        private final float offY;
        private final int direction;
        private final String liftName;
        private final String hexId;
        private final String dimNamespace;
        private final String dimPath;

        private StoredConfig(int color, float scale, float offX, float offY, int direction,
                             String liftName, String hexId, String dimNamespace, String dimPath) {
            this.color = color;
            this.scale = scale;
            this.offX = offX;
            this.offY = offY;
            this.direction = direction;
            this.liftName = liftName == null ? "" : liftName;
            this.hexId = hexId == null ? "" : hexId;
            this.dimNamespace = dimNamespace == null ? "minecraft" : dimNamespace;
            this.dimPath = dimPath == null ? "overworld" : dimPath;
        }
    }
}
