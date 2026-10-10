package com.xiao.mtrliftcolornew.door;

import com.xiao.mtrliftcolornew.network.LiftColorNetwork;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.mod.item.ItemDriverKey;

/**
 * 客户端：处理「手持 MTR 驾驶钥匙右击电梯门」。
 *
 * <p>本模组不新增物品，直接复用 MTR 的车辆驾驶钥匙（{@link ItemDriverKey}）：
 * 基础、高级、创造三种驾驶钥匙都能用，也不看钥匙自己的 canOpenDoors 标记
 * ——现实里的三角钥匙本来就能开层门。</p>
 *
 * <p>只在客户端受理，然后走自定义通道通知服务器；服务器不装本模组时右击不会有任何效果。
 * 潜行右击被放行，方便玩家照常使用钥匙对车辆的操作。</p>
 */
@Environment(net.fabricmc.api.EnvType.CLIENT)
public final class LiftDoorKeyHandler {

    private LiftDoorKeyHandler() {
    }

    /** 在客户端入口注册。 */
    public static void registerClient() {
        UseBlockCallback.EVENT.register(LiftDoorKeyHandler::onUseBlock);
        ClientTickEvents.END_CLIENT_TICK.register(LiftDoorKeyHandler::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LiftDoorService.clear();
            LiftDoorAnimation.clear();
        });
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (!world.isClient || player.isSneaking()) {
            return ActionResult.PASS; // 服务器端不处理，潜行时把右击还给原逻辑
        }
        final ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof ItemDriverKey)) {
            return ActionResult.PASS;
        }
        final BlockPos pos = hit.getBlockPos();
        if (!LiftDoorLookup.isLiftDoor(world, pos)) {
            return ActionResult.PASS;
        }
        // 手上这把钥匙就是来开门的：先让门前这一层本地开起来（保证一定有反馈），
        // 再让服务器把电梯停下 / 保持停靠，两侧的门最终都按慢速曲线开合。
        LiftDoorAnimation.start(pos, System.currentTimeMillis());
        player.swingHand(hand);
        final long liftId = LiftDoorLookup.findLiftId(pos);
        if (liftId >= 0) {
            LiftColorNetwork.sendDoorKeyToServer(liftId);
        }
        return ActionResult.SUCCESS; // 消费这次右击，不再触发方块与物品自身的其他逻辑
    }

    private static void onClientTick(MinecraftClient client) {
        LiftDoorAnimation.tick(client);
    }
}
