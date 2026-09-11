package com.xiao.mtrliftcolornew.network;

import com.xiao.mtrliftcolornew.config.LiftColorManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 客户端与服务器之间的配色同步协议（Fabric 1.20.1 使用 Identifier + PacketByteBuf 的网络 API）。
 *
 * <p>两个通道：</p>
 * <ul>
 *   <li>{@link #C2S_UPDATE}：客户端 → 服务器，玩家保存某台电梯的配色。</li>
 *   <li>{@link #S2C_SYNC}：服务器 → 客户端，把某台电梯的配色发给（某个）玩家。</li>
 * </ul>
 *
 * <p>注册时机：服务端通道在客户端入口注册（覆盖单人游戏里的集成服务器）也在专用服务器
 * 入口注册（覆盖联机服务器）；客户端通道只在客户端入口注册。</p>
 */
public final class LiftColorNetwork {

    public static final Identifier C2S_UPDATE = new Identifier("mtrliftcolornew", "update_lift_color");
    public static final Identifier S2C_SYNC = new Identifier("mtrliftcolornew", "lift_color");

    private static final int MAX_STRING = 32767;

    private LiftColorNetwork() {
    }

    // ---------- 收发 ----------

    /** 客户端把某台电梯的配色发给服务器（服务器不带本模组时静默降级为仅本机生效）。 */
    public static void sendToServer(long liftId, String liftName, String hexId,
                                    String dimNamespace, String dimPath,
                                    int color, float scale, float offX, float offY) {
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
            ClientPlayNetworking.send(C2S_UPDATE, buf);
        } catch (RuntimeException ignored) {
            // 非联网场景等异常一律忽略
        }
    }

    /** 服务器把某台电梯的配色发给指定玩家。 */
    public static void sendToPlayer(ServerPlayerEntity player, long liftId, int color, float scale, float offX, float offY) {
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeLong(liftId);
        buf.writeInt(color);
        buf.writeFloat(scale);
        buf.writeFloat(offX);
        buf.writeFloat(offY);
        ServerPlayNetworking.send(player, S2C_SYNC, buf);
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
            server.execute(() -> LiftColorServerStore.applyUpdate(server, liftId, liftName, hexId, dimNamespace, dimPath, color, scale, offX, offY));
        });

        // 服务器启动：把已存在的配色扫进内存
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
            LiftColorManager.applyServerSync(liftId, color, scale, offX, offY);
        });
    }
}
