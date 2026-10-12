package com.xiao.mtrliftcolornew.network;

import com.xiao.mtrliftcolornew.Safety;
import com.xiao.mtrliftcolornew.config.LiftColorConfig;
import com.xiao.mtrliftcolornew.config.LiftColorManager;
import com.xiao.mtrliftcolornew.door.LiftDoorMaintenanceHandler;
import com.xiao.mtrliftcolornew.door.LiftDoorService;
import com.xiao.mtrliftcolornew.door.LiftMaintenanceRegistry;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 客户端与服务器之间的同步协议（Fabric 1.20.1 使用 Identifier + PacketByteBuf 的网络 API）。
 *
 * <p>通道一览：</p>
 * <ul>
 *   <li>{@link #C2S_UPDATE}：客户端 → 服务器，保存某台电梯的电梯显示设置（颜色、箭头、楼层文字、检修门时长）。</li>
 *   <li>{@link #S2C_SYNC}：服务器 → 客户端，把某台电梯的显示设置发给（某个）玩家。</li>
 *   <li>{@link #C2S_AUTO_UPDATE}：客户端 → 服务器，写入某台电梯的自动化积木链（上行/下行各一条）。</li>
 *   <li>{@link #S2C_AUTO_SYNC}：服务器 → 客户端，同步某台电梯的自动化积木链，
 *       渲染时按电梯当前运行方向取对应链覆盖箭头与楼层文字设置。</li>
 *   <li>{@link #C2S_AUTO_OP}：客户端 → 服务器，请求把全部配置导出到存档的
 *       {@code mtrliftcolor/output/}，或从该目录导入；结果以聊天消息回给请求者。</li>
 *   <li>{@link #C2S_DOOR_KEY}：客户端 → 服务器，用 MTR 自带的驾驶钥匙（三角钥匙）右击电梯层门
 *       请求检修开门 / 关门。服务器验完再经 {@link #S2C_DOOR_STATE} 广播，
 *       这样所有玩家看到的是同一段动画（同一批门格、同一个时长）。</li>
 *   <li>{@link #S2C_DOOR_STATE}：服务器 → 客户端，检修门状态。有两种载荷：
 *       {@link #DOOR_TOGGLE}（一次切换，带整组门格坐标与时长）与
 *       {@link #DOOR_SNAPSHOT}（玩家中途进服时补齐当前还开着的门，直接落到开到位的状态）。</li>
 * </ul>
 *
 * <p>注册时机：服务端通道在客户端入口注册（覆盖单人游戏集成服务器）也在专用服务器
 * 入口注册（覆盖联机服务器）；客户端通道只在客户端入口注册。</p>
 */
public final class LiftColorNetwork {

    public static final Identifier C2S_UPDATE = new Identifier("mtrliftcolornew", "update_lift_color");
    public static final Identifier S2C_SYNC = new Identifier("mtrliftcolornew", "lift_color");
    public static final Identifier C2S_AUTO_UPDATE = new Identifier("mtrliftcolornew", "auto_update");
    public static final Identifier S2C_AUTO_SYNC = new Identifier("mtrliftcolornew", "auto_sync");
    public static final Identifier C2S_AUTO_OP = new Identifier("mtrliftcolornew", "auto_op");
    public static final Identifier C2S_DOOR_KEY = new Identifier("mtrliftcolornew", "lift_door_key");
    /** 服务器 → 客户端：把一次检修开关门广播给所有玩家，让所有人看到同一段动画。 */
    public static final Identifier S2C_DOOR_STATE = new Identifier("mtrliftcolornew", "lift_door_state");

    /** 包类型：一次切换（开或关）。 */
    public static final int DOOR_TOGGLE = 0;
    /** 包类型：给刚进服的玩家补发当前所有还开着的检修门。 */
    public static final int DOOR_SNAPSHOT = 1;
    /** 包类型：服务器驳回客户端的请求，客户端把对应的本地开启动画撤掉（门不会卡在假开状态）。 */
    public static final int DOOR_REJECT = 2;

    /** 自动化操作：导出全部到 output 目录。 */
    public static final int AUTO_OP_EXPORT = 0;
    /** 自动化操作：从 output 目录导入。 */
    public static final int AUTO_OP_IMPORT = 1;

    private static final int MAX_STRING = 32767;

    private LiftColorNetwork() {
    }

    // ---------- 显示设置：收发 ----------

    /** 显示设置上线的字段顺序，读写两侧共用，避免错位。 */
    private static void writeDisplay(PacketByteBuf buf, LiftColorConfig cfg) {
        final LiftColorConfig source = cfg == null ? new LiftColorConfig() : cfg;
        buf.writeInt(source.displayColor);
        buf.writeFloat(source.arrowScale);
        buf.writeFloat(source.arrowOffsetX);
        buf.writeFloat(source.arrowOffsetY);
        buf.writeInt(LiftColorConfig.clampDirection(source.arrowDirection));
        buf.writeFloat(source.textScale);
        buf.writeFloat(source.textOffsetX);
        buf.writeFloat(source.textOffsetY);
        buf.writeInt(LiftColorConfig.clampDoorMs(source.doorOpenMs));
        buf.writeInt(LiftColorConfig.clampDoorMs(source.doorCloseMs));
    }

    /** 与 {@link #writeDisplay} 一一对应的读取，读出来立刻夹紧。 */
    private static LiftColorConfig readDisplay(PacketByteBuf buf) {
        final LiftColorConfig cfg = new LiftColorConfig();
        cfg.displayColor = buf.readInt();
        cfg.arrowScale = LiftColorConfig.clampScale(buf.readFloat());
        cfg.arrowOffsetX = LiftColorConfig.clampOffset(buf.readFloat());
        cfg.arrowOffsetY = LiftColorConfig.clampOffset(buf.readFloat());
        cfg.arrowDirection = LiftColorConfig.clampDirection(buf.readInt());
        cfg.textScale = LiftColorConfig.clampScale(buf.readFloat());
        cfg.textOffsetX = LiftColorConfig.clampOffset(buf.readFloat());
        cfg.textOffsetY = LiftColorConfig.clampOffset(buf.readFloat());
        cfg.doorOpenMs = LiftColorConfig.clampDoorMs(buf.readInt());
        cfg.doorCloseMs = LiftColorConfig.clampDoorMs(buf.readInt());
        return cfg;
    }

    /** 客户端把某台电梯的显示设置发给服务器（服务器不带本模组时静默降级为仅本机生效）。 */
    public static void sendToServer(long liftId, String liftName, String hexId,
                                    String dimNamespace, String dimPath, LiftColorConfig display) {
        try {
            if (!ClientPlayNetworking.canSend(C2S_UPDATE)) {
                return; // 当前没连上，或服务器没有这个模组
            }
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeLong(liftId);
            buf.writeString(liftName == null ? "" : liftName, MAX_STRING);
            buf.writeString(hexId == null ? "" : hexId, MAX_STRING);
            buf.writeString(dimNamespace == null ? "minecraft" : dimNamespace, MAX_STRING);
            buf.writeString(dimPath == null ? "overworld" : dimPath, MAX_STRING);
            writeDisplay(buf, display);
            ClientPlayNetworking.send(C2S_UPDATE, buf);
        } catch (RuntimeException ignored) {
            // 非联网场景等异常一律忽略
        }
    }

    /** 服务器把某台电梯的显示设置（含元数据）发给指定玩家。 */
    public static void sendToPlayer(ServerPlayerEntity player, long liftId, LiftColorConfig display,
                                    String liftName, String hexId, String dimNamespace, String dimPath) {
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeLong(liftId);
        writeDisplay(buf, display);
        buf.writeString(liftName == null ? "" : liftName, MAX_STRING);
        buf.writeString(hexId == null ? "" : hexId, MAX_STRING);
        buf.writeString(dimNamespace == null ? "minecraft" : dimNamespace, MAX_STRING);
        buf.writeString(dimPath == null ? "overworld" : dimPath, MAX_STRING);
        ServerPlayNetworking.send(player, S2C_SYNC, buf);
    }

    // ---------- 自动化：收发 ----------

    /** 客户端把某台电梯的自动化积木链发给服务器（服务器落盘并广播）。 */
    public static void sendAutoUpdateToServer(long liftId, String liftName, String hexId,
                                              String dimNamespace, String dimPath,
                                              String upJson, String downJson) {
        try {
            if (!ClientPlayNetworking.canSend(C2S_AUTO_UPDATE)) {
                return; // 当前没连上，或服务器没有这个模组
            }
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeLong(liftId);
            buf.writeString(liftName == null ? "" : liftName, MAX_STRING);
            buf.writeString(hexId == null ? "" : hexId, MAX_STRING);
            buf.writeString(dimNamespace == null ? "minecraft" : dimNamespace, MAX_STRING);
            buf.writeString(dimPath == null ? "overworld" : dimPath, MAX_STRING);
            buf.writeString(upJson == null ? "[]" : upJson, MAX_STRING);
            buf.writeString(downJson == null ? "[]" : downJson, MAX_STRING);
            ClientPlayNetworking.send(C2S_AUTO_UPDATE, buf);
        } catch (RuntimeException ignored) {
            // 非联网场景等异常一律忽略
        }
    }

    /** 客户端请求服务器导出 / 导入配置（文件在服务器存档目录里）。 */
    public static void sendAutoOpToServer(int op) {
        try {
            if (!ClientPlayNetworking.canSend(C2S_AUTO_OP)) {
                return;
            }
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeInt(op);
            ClientPlayNetworking.send(C2S_AUTO_OP, buf);
        } catch (RuntimeException ignored) {
            // 忽略
        }
    }

    /** 服务器把某台电梯的自动化积木链广播给所有玩家。 */
    public static void broadcastAuto(MinecraftServer server, long liftId, String upJson, String downJson) {
        for (final ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendAutoToPlayer(player, liftId, upJson, downJson);
        }
    }

    /** 服务器把某台电梯的自动化积木链发给指定玩家（加入时同步用）。 */
    public static void sendAutoToPlayer(ServerPlayerEntity player, long liftId, String upJson, String downJson) {
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeLong(liftId);
        buf.writeString(upJson == null ? "[]" : upJson, MAX_STRING);
        buf.writeString(downJson == null ? "[]" : downJson, MAX_STRING);
        ServerPlayNetworking.send(player, S2C_AUTO_SYNC, buf);
    }

    // ---------- 驾驶钥匙检修开门 ----------

    /**
     * 客户端请求服务器切换某扇电梯层门的检修开关。
     *
     * <p>返回 {@code true} 表示服务器装了本模组、状态会以 {@link #S2C_DOOR_STATE} 广播回来，
     * 客户端不用先斩后奏；返回 {@code false} 表示对端没有本模组（或没连上），
     * 调用方应当只在本地把门打开（没有联锁、别人也看不到），至少让手的这一下有反馈。</p>
     *
     * <p>整组门坐标一并发给服务器：服务器驳回请求时原样带回，客户端据此撤销本地动画，
     * 不会出现「门卡在假开状态、电梯被永久钉住」。</p>
     *
     * @param floorIndex 这扇门是电梯的第几层（-1 = 认不出来，只影响轿厢门跟不跟着开）
     * @param clicked    被点的那一格门方块坐标（服务器由它重算整组门）
     */
    public static boolean sendDoorToggleToServer(long liftId, int floorIndex, BlockPos clicked,
                                                 boolean open, long durationMs, Collection<Long> keys) {
        try {
            if (!ClientPlayNetworking.canSend(C2S_DOOR_KEY)) {
                return false;
            }
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeByte(DOOR_TOGGLE);
            buf.writeLong(liftId);
            buf.writeVarInt(floorIndex);
            buf.writeLong(clicked.asLong());
            buf.writeBoolean(open);
            buf.writeVarLong(LiftDoorService.clampMaintenanceMs(durationMs));
            buf.writeVarInt(keys.size());
            for (final Long key : keys) {
                buf.writeLong(key);
            }
            ClientPlayNetworking.send(C2S_DOOR_KEY, buf);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** 服务器把一次检修开关门广播给所有玩家（含发起者，客户端自己带去重）。 */
    public static void broadcastDoorState(MinecraftServer server, long liftId, int floorIndex,
                                          Collection<Long> keys, boolean open, long durationMs) {
        for (final ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendDoorStateToPlayer(player, liftId, floorIndex, keys, open, durationMs);
        }
    }

    /** 一条检修门状态：电梯 id + 楼层 + 整组门坐标 + 开关 + 时长。 */
    public static void sendDoorStateToPlayer(ServerPlayerEntity player, long liftId, int floorIndex,
                                             Collection<Long> keys, boolean open, long durationMs) {
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(DOOR_TOGGLE);
        buf.writeLong(liftId);
        buf.writeVarInt(floorIndex);
        buf.writeVarInt(keys.size());
        for (final Long key : keys) {
            buf.writeLong(key);
        }
        buf.writeBoolean(open);
        buf.writeVarLong(LiftDoorService.clampMaintenanceMs(durationMs));
        ServerPlayNetworking.send(player, S2C_DOOR_STATE, buf);
    }

    /** 服务器驳回某玩家的一次检修请求：把他说不上账的那几格本地状态撤掉。 */
    public static void sendDoorReject(ServerPlayerEntity player, long liftId, Collection<Long> keys) {
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(DOOR_REJECT);
        buf.writeLong(liftId);
        buf.writeVarInt(keys.size());
        for (final Long key : keys) {
            buf.writeLong(key);
        }
        ServerPlayNetworking.send(player, S2C_DOOR_STATE, buf);
    }

    /** 玩家中途进服时，把当前所有还开着的检修门一次性补给他（直接就是开好的样子）。 */
    public static void sendDoorSnapshot(ServerPlayerEntity player) {
        final List<LiftMaintenanceRegistry.Entry> entries = LiftMaintenanceRegistry.snapshot();
        if (entries.isEmpty()) {
            return;
        }
        final long now = System.currentTimeMillis();
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(DOOR_SNAPSHOT);
        buf.writeVarInt(entries.size());
        for (final LiftMaintenanceRegistry.Entry entry : entries) {
            buf.writeLong(entry.liftId);
            buf.writeVarInt(entry.floorIndex);
            buf.writeLong(entry.key);
            buf.writeBoolean(entry.open);
            buf.writeVarLong(entry.durationMs);
            // 已经走了多久：开到位的那些传一个大于时长的值，客户端一落地就是全开，不重播动画
            buf.writeVarLong(Math.max(0L, now - entry.startMillis));
        }
        ServerPlayNetworking.send(player, S2C_DOOR_STATE, buf);
    }

    // ---------- 注册 ----------

    /** 注册服务端处理（专用服务器用；客户端入口也会调一次，覆盖单人游戏集成服务器）。 */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_UPDATE, (server, player, handler, buf, responseSender) ->
                Safety.run("显示设置收包", () -> {
                    final long liftId = buf.readLong();
                    final String liftName = buf.readString(MAX_STRING);
                    final String hexId = buf.readString(MAX_STRING);
                    final String dimNamespace = buf.readString(MAX_STRING);
                    final String dimPath = buf.readString(MAX_STRING);
                    final LiftColorConfig display = readDisplay(buf);
                    server.execute(() -> Safety.run("显示设置落盘", () -> LiftColorServerStore.applyUpdate(
                            server, liftId, liftName, hexId, dimNamespace, dimPath, display)));
                }));

        // 自动化积木链：客户端发来某台电梯的上行/下行链，服务器落盘并广播
        ServerPlayNetworking.registerGlobalReceiver(C2S_AUTO_UPDATE, (server, player, handler, buf, responseSender) ->
                Safety.run("积木链收包", () -> {
                    final long liftId = buf.readLong();
                    final String liftName = buf.readString(MAX_STRING);
                    final String hexId = buf.readString(MAX_STRING);
                    final String dimNamespace = buf.readString(MAX_STRING);
                    final String dimPath = buf.readString(MAX_STRING);
                    final String upJson = buf.readString(MAX_STRING);
                    final String downJson = buf.readString(MAX_STRING);
                    server.execute(() -> Safety.run("积木链落盘", () -> LiftColorServerStore.applyAutoUpdate(
                            server, liftId, liftName, hexId, dimNamespace, dimPath, upJson, downJson)));
                }));

        // 导出 / 导入：在服务器存档目录里进行，结果回聊天消息
        ServerPlayNetworking.registerGlobalReceiver(C2S_AUTO_OP, (server, player, handler, buf, responseSender) ->
                Safety.run("导出导入收包", () -> {
                    final int op = buf.readInt();
                    server.execute(() -> Safety.run("导出导入执行", () -> {
                        if (op == AUTO_OP_EXPORT) {
                            LiftColorServerStore.exportAll(server, player);
                        } else if (op == AUTO_OP_IMPORT) {
                            LiftColorServerStore.importAll(server, player);
                        }
                    }));
                }));

        // 三角钥匙检修开门：验包、落状态、广播给所有玩家（电梯的冻结由仿真线程自己看状态表做）
        ServerPlayNetworking.registerGlobalReceiver(C2S_DOOR_KEY, (server, player, handler, buf, responseSender) -> {
            Safety.run("检修门收包", () -> {
                final int type = buf.readByte();
                final long liftId = buf.readLong();
                if (type != DOOR_TOGGLE) {
                    return; // 这个方向只受理切换，快照是下行包
                }
                final int floorIndex = buf.readVarInt();
                final long clickedKey = buf.readLong();
                final boolean open = buf.readBoolean();
                final long durationMs = buf.readVarLong();
                final int count = buf.readableBytes() >= 1 ? Math.min(buf.readVarInt(), 64) : 0;
                final List<Long> clientKeys = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    clientKeys.add(buf.readLong());
                }
                server.execute(() -> LiftDoorMaintenanceHandler.toggle(server, player, liftId, floorIndex,
                        BlockPos.fromLong(clickedKey), open, durationMs, clientKeys));
            });
        });

        // 服务器启动 / 关服：配置读盘；检修状态一律清空（存档里没有电梯冻结，重启后必须回到没锁的状态）
        // 读盘整段包保险丝：存档目录里哪怕有一个坏文件，也绝不能拖到开服失败。
        ServerLifecycleEvents.SERVER_STARTED.register(server -> Safety.run("开服读配置", () -> {
            LiftMaintenanceRegistry.clear();
            LiftColorServerStore.loadFromWorld(server);
        }));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> LiftMaintenanceRegistry.clear());

        // 玩家加入：把服务端已有的配置全部发给他，再补一份当前开着的检修门
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() ->
                Safety.run("进服同步配置", () -> {
            final ServerPlayerEntity player = handler.player;
            if (player != null) {
                LiftColorServerStore.syncTo(player);
                sendDoorSnapshot(player);
            }
        })));
    }

    /** 注册客户端处理（只在客户端入口调用）。 */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(S2C_SYNC, (client, handler, buf, responseSender) ->
                Safety.run("显示设置同步包", () -> {
                    final long liftId = buf.readLong();
                    final LiftColorConfig display = readDisplay(buf);
                    final String liftName = buf.readString(MAX_STRING);
                    final String hexId = buf.readString(MAX_STRING);
                    final String dimNamespace = buf.readString(MAX_STRING);
                    final String dimPath = buf.readString(MAX_STRING);
                    LiftColorManager.applyServerSync(liftId, display, liftName, hexId, dimNamespace, dimPath);
                }));

        // 服务器同步过来的自动化积木链
        ClientPlayNetworking.registerGlobalReceiver(S2C_AUTO_SYNC, (client, handler, buf, responseSender) -> {
            final long liftId = buf.readLong();
            final String upJson = buf.readString(MAX_STRING);
            final String downJson = buf.readString(MAX_STRING);
            LiftColorManager.applyAutoServerSync(liftId, upJson, downJson);
        });

        // 检修开关门：别人（或自己那次的服务端回执）播的同一段动画
        ClientPlayNetworking.registerGlobalReceiver(S2C_DOOR_STATE, (client, handler, buf, responseSender) -> {
            final int type = buf.readByte();
            if (type == DOOR_TOGGLE) {
                final long liftId = buf.readLong();
                final int floorIndex = buf.readVarInt();
                final int count = buf.readVarInt();
                final List<Long> keys = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    keys.add(buf.readLong());
                }
                final boolean open = buf.readBoolean();
                final long durationMs = buf.readVarLong();
                LiftMaintenanceRegistry.apply(liftId, floorIndex, keys, open, durationMs);
                return;
            }
            if (type == DOOR_SNAPSHOT) {
                // 中途进服：这些门直接就是开好的样子，不重播开门动画
                final int count = buf.readVarInt();
                for (int i = 0; i < count; i++) {
                    final long liftId = buf.readLong();
                    final int floorIndex = buf.readVarInt();
                    final long key = buf.readLong();
                    final boolean open = buf.readBoolean();
                    final long durationMs = buf.readVarLong();
                    final long elapsedMs = buf.readVarLong();
                    LiftMaintenanceRegistry.apply(liftId, floorIndex, List.of(key), open, durationMs,
                            System.currentTimeMillis() - elapsedMs);
                }
                return;
            }
            if (type == DOOR_REJECT) {
                // 服务器驳回：撤销本地这批门的检修状态，绝不让门卡在假开、电梯被永久钉住
                final long liftId = buf.readLong();
                final int count = buf.readVarInt();
                final List<Long> keys = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    keys.add(buf.readLong());
                }
                LiftMaintenanceRegistry.reject(liftId, keys);
            }
        });
    }
}
