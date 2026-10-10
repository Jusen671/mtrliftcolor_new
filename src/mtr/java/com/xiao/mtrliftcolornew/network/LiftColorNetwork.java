package com.xiao.mtrliftcolornew.network;

import com.xiao.mtrliftcolornew.config.LiftColorConfig;
import com.xiao.mtrliftcolornew.config.LiftColorManager;
import com.xiao.mtrliftcolornew.door.LiftDoorService;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 客户端与服务器之间的同步协议（Fabric 1.20.1 使用 Identifier + PacketByteBuf 的网络 API）。
 *
 * <p>通道一览：</p>
 * <ul>
 *   <li>{@link #C2S_UPDATE}：客户端 → 服务器，保存某台电梯的电梯显示设置（颜色、箭头、楼层文字）。</li>
 *   <li>{@link #S2C_SYNC}：服务器 → 客户端，把某台电梯的显示设置发给（某个）玩家。</li>
 *   <li>{@link #C2S_AUTO_UPDATE}：客户端 → 服务器，写入某台电梯的自动化积木链（上行/下行各一条）。</li>
 *   <li>{@link #S2C_AUTO_SYNC}：服务器 → 客户端，同步某台电梯的自动化积木链，
 *       渲染时按电梯当前运行方向取对应链覆盖箭头与楼层文字设置。</li>
 *   <li>{@link #C2S_AUTO_OP}：客户端 → 服务器，请求把全部配置导出到存档的
 *       {@code mtrliftcolor/output/}，或从该目录导入；结果以聊天消息回给请求者。</li>
 *   <li>{@link #C2S_DOOR_KEY}：客户端 → 服务器，用 MTR 自带的驾驶钥匙右击电梯门请求开门。
 *       开门动画不需要回程包：MTR 自己会把电梯的开门冷却同步给附近玩家，
 *       客户端渲染时读的就是那个冷却值（见 {@link LiftDoorService}）。</li>
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

    // ---------- 驾驶钥匙开门：发送 ----------

    /**
     * 客户端请求服务器用驾驶钥匙打开某台电梯的门。
     *
     * <p>服务器上没有本模组时 {@code canSend} 为 false，直接不发：本地那扇门仍由
     * {@code LiftDoorAnimation} 打开，只是电梯不会停。</p>
     */
    public static void sendDoorKeyToServer(long liftId) {
        try {
            if (!ClientPlayNetworking.canSend(C2S_DOOR_KEY)) {
                return;
            }
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeLong(liftId);
            ClientPlayNetworking.send(C2S_DOOR_KEY, buf);
        } catch (RuntimeException ignored) {
            // 忽略
        }
    }

    // ---------- 注册 ----------

    /** 注册服务端处理（专用服务器用；客户端入口也会调一次，覆盖单人游戏集成服务器）。 */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_UPDATE, (server, player, handler, buf, responseSender) -> {
            final long liftId = buf.readLong();
            final String liftName = buf.readString(MAX_STRING);
            final String hexId = buf.readString(MAX_STRING);
            final String dimNamespace = buf.readString(MAX_STRING);
            final String dimPath = buf.readString(MAX_STRING);
            final LiftColorConfig display = readDisplay(buf);
            server.execute(() -> LiftColorServerStore.applyUpdate(server, liftId, liftName, hexId, dimNamespace, dimPath, display));
        });

        // 自动化积木链：客户端发来某台电梯的上行/下行链，服务器落盘并广播
        ServerPlayNetworking.registerGlobalReceiver(C2S_AUTO_UPDATE, (server, player, handler, buf, responseSender) -> {
            final long liftId = buf.readLong();
            final String liftName = buf.readString(MAX_STRING);
            final String hexId = buf.readString(MAX_STRING);
            final String dimNamespace = buf.readString(MAX_STRING);
            final String dimPath = buf.readString(MAX_STRING);
            final String upJson = buf.readString(MAX_STRING);
            final String downJson = buf.readString(MAX_STRING);
            server.execute(() -> LiftColorServerStore.applyAutoUpdate(server, liftId, liftName, hexId,
                    dimNamespace, dimPath, upJson, downJson));
        });

        // 导出 / 导入：在服务器存档目录里进行，结果回聊天消息
        ServerPlayNetworking.registerGlobalReceiver(C2S_AUTO_OP, (server, player, handler, buf, responseSender) -> {
            final int op = buf.readInt();
            server.execute(() -> {
                if (op == AUTO_OP_EXPORT) {
                    LiftColorServerStore.exportAll(server, player);
                } else if (op == AUTO_OP_IMPORT) {
                    LiftColorServerStore.importAll(server, player);
                }
            });
        });

        // 驾驶钥匙开门：记下请求，等电梯仿真线程在下一次 Lift.tick 里执行（不能在这里直接改电梯状态）
        ServerPlayNetworking.registerGlobalReceiver(C2S_DOOR_KEY, (server, player, handler, buf, responseSender) -> {
            final long liftId = buf.readLong();
            LiftDoorService.requestOpen(liftId);
        });

        // 服务器启动：补建三个标准维度目录，并把已存在的配置扫进内存
        ServerLifecycleEvents.SERVER_STARTED.register(server -> LiftColorServerStore.loadFromWorld(server));

        // 玩家加入：把服务端已有的配置全部发给他
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            final ServerPlayerEntity player = handler.player;
            if (player != null) {
                LiftColorServerStore.syncTo(player);
            }
        }));
    }

    /** 注册客户端处理（只在客户端入口调用）。 */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(S2C_SYNC, (client, handler, buf, responseSender) -> {
            final long liftId = buf.readLong();
            final LiftColorConfig display = readDisplay(buf);
            final String liftName = buf.readString(MAX_STRING);
            final String hexId = buf.readString(MAX_STRING);
            final String dimNamespace = buf.readString(MAX_STRING);
            final String dimPath = buf.readString(MAX_STRING);
            LiftColorManager.applyServerSync(liftId, display, liftName, hexId, dimNamespace, dimPath);
        });

        // 服务器同步过来的自动化积木链
        ClientPlayNetworking.registerGlobalReceiver(S2C_AUTO_SYNC, (client, handler, buf, responseSender) -> {
            final long liftId = buf.readLong();
            final String upJson = buf.readString(MAX_STRING);
            final String downJson = buf.readString(MAX_STRING);
            LiftColorManager.applyAutoServerSync(liftId, upJson, downJson);
        });
    }
}
