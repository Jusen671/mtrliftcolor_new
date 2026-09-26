package com.xiao.mtrliftcolornew.network;

import com.xiao.mtrliftcolornew.config.LiftColorConfig;
import com.xiao.mtrliftcolornew.config.LiftColorManager;
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
 * 客户端与服务器之间的配色同步协议（Fabric 1.20.1 使用 Identifier + PacketByteBuf 的网络 API）。
 *
 * <p>通道一览：</p>
 * <ul>
 *   <li>{@link #C2S_UPDATE}：客户端 → 服务器，玩家保存某台电梯的配色（含箭头方向与维度元数据）。</li>
 *   <li>{@link #S2C_SYNC}：服务器 → 客户端，把某台电梯的配色（含元数据）发给（某个）玩家。
 *       客户端把元数据一并记进缓存，供「自动化」界面批量调整与导出/导入配置使用。</li>
 *   <li>{@link #C2S_AUTO_UPDATE}：客户端 → 服务器，写入某台电梯的自动化积木链（上行/下行各一条）。</li>
 *   <li>{@link #S2C_AUTO_SYNC}：服务器 → 客户端，同步某台电梯的自动化积木链，
 *       渲染时按电梯当前运行方向取对应链覆盖箭头设置。</li>
 *   <li>{@link #C2S_AUTO_OP}：客户端 → 服务器，请求把全部自动化配置导出到存档的
 *       {@code mtrliftcolor/output/}，或从该目录导入；结果以聊天消息回给请求者。</li>
 * </ul>
 *
 * <p>注册时机：服务端通道在客户端入口注册（覆盖单人游戏里的集成服务器）也在专用服务器
 * 入口注册（覆盖联机服务器）；客户端通道只在客户端入口注册。</p>
 */
public final class LiftColorNetwork {

    public static final Identifier C2S_UPDATE = new Identifier("mtrliftcolornew", "update_lift_color");
    public static final Identifier S2C_SYNC = new Identifier("mtrliftcolornew", "lift_color");
    public static final Identifier C2S_AUTO_UPDATE = new Identifier("mtrliftcolornew", "auto_update");
    public static final Identifier S2C_AUTO_SYNC = new Identifier("mtrliftcolornew", "auto_sync");
    public static final Identifier C2S_AUTO_OP = new Identifier("mtrliftcolornew", "auto_op");

    /** 自动化操作：导出全部到 output 目录。 */
    public static final int AUTO_OP_EXPORT = 0;
    /** 自动化操作：从 output 目录导入。 */
    public static final int AUTO_OP_IMPORT = 1;

    private static final int MAX_STRING = 32767;

    private LiftColorNetwork() {
    }

    // ---------- 收发 ----------

    /** 客户端把某台电梯的配色发给服务器（服务器不带本模组时静默降级为仅本机生效）。 */
    public static void sendToServer(long liftId, String liftName, String hexId,
                                    String dimNamespace, String dimPath,
                                    int color, float scale, float offX, float offY, int direction) {
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
            buf.writeInt(color);
            buf.writeFloat(scale);
            buf.writeFloat(offX);
            buf.writeFloat(offY);
            buf.writeInt(LiftColorConfig.clampDirection(direction));
            ClientPlayNetworking.send(C2S_UPDATE, buf);
        } catch (RuntimeException ignored) {
            // 非联网场景等异常一律忽略
        }
    }

    /** 服务器把某台电梯的配色（含元数据）发给指定玩家。 */
    public static void sendToPlayer(ServerPlayerEntity player, long liftId,
                                    int color, float scale, float offX, float offY, int direction,
                                    String liftName, String hexId, String dimNamespace, String dimPath) {
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeLong(liftId);
        buf.writeInt(color);
        buf.writeFloat(scale);
        buf.writeFloat(offX);
        buf.writeFloat(offY);
        buf.writeInt(LiftColorConfig.clampDirection(direction));
        buf.writeString(liftName == null ? "" : liftName, MAX_STRING);
        buf.writeString(hexId == null ? "" : hexId, MAX_STRING);
        buf.writeString(dimNamespace == null ? "minecraft" : dimNamespace, MAX_STRING);
        buf.writeString(dimPath == null ? "overworld" : dimPath, MAX_STRING);
        ServerPlayNetworking.send(player, S2C_SYNC, buf);
    }

    // ---------- 自动化：发送 ----------

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

    /** 客户端请求服务器导出 / 导入自动化配置（文件在服务器存档目录里）。 */
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

    // ---------- 注册 ----------

    /** 注册服务端处理（专用服务器用；客户端入口也会调一次，覆盖单人游戏集成服务器）。 */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_UPDATE, (server, player, handler, buf, responseSender) -> {
            final long liftId = buf.readLong();
            final String liftName = buf.readString(MAX_STRING);
            final String hexId = buf.readString(MAX_STRING);
            final String dimNamespace = buf.readString(MAX_STRING);
            final String dimPath = buf.readString(MAX_STRING);
            final int color = buf.readInt();
            final float scale = buf.readFloat();
            final float offX = buf.readFloat();
            final float offY = buf.readFloat();
            final int direction = buf.readInt();
            server.execute(() -> LiftColorServerStore.applyUpdate(server, liftId, liftName, hexId, dimNamespace, dimPath,
                    color, scale, offX, offY, direction));
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

        // 服务器启动：补建三个标准维度目录，并把已存在的配色扫进内存
        ServerLifecycleEvents.SERVER_STARTED.register(server -> LiftColorServerStore.loadFromWorld(server));

        // 玩家加入：把服务端已有的配色全部发给他
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
            final int color = buf.readInt();
            final float scale = buf.readFloat();
            final float offX = buf.readFloat();
            final float offY = buf.readFloat();
            final int direction = buf.readInt();
            final String liftName = buf.readString(MAX_STRING);
            final String hexId = buf.readString(MAX_STRING);
            final String dimNamespace = buf.readString(MAX_STRING);
            final String dimPath = buf.readString(MAX_STRING);
            LiftColorManager.applyServerSync(liftId, color, scale, offX, offY, direction, liftName, hexId, dimNamespace, dimPath);
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
