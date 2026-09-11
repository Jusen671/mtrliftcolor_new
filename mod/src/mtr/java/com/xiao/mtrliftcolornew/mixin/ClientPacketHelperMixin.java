package com.xiao.mtrliftcolornew.mixin;

import com.xiao.mtrliftcolornew.client.MTRLiftColorClient;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mod.packet.ClientPacketHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 MTR 打开电梯重置编辑器的入口（openLiftCustomizationScreen）。
 * 玩家右键重置器时，MTR 会通过 packet 调到这个方法来打开编辑器；
 * 我们改用在屏幕上弹出"上半圆=MTR编辑器 / 下半圆=调色面板"的圆形选择器。
 * 选择器上半圆调用原逻辑（绕过本 Mixin）即可回到 MTR 编辑器。
 */
@Mixin(value = ClientPacketHelper.class, remap = false)
public abstract class ClientPacketHelperMixin {

    @Inject(method = "openLiftCustomizationScreen(Lorg/mtr/mapping/holder/BlockPos;)V", at = @At("HEAD"), cancellable = true)
    private static void mtrliftcolor$openLiftCustomizationScreen(BlockPos blockPos, CallbackInfo ci) {
        if (MTRLiftColorClient.isBypassingMtr()) {
            return; // 放行 MTR 原逻辑（选择器上半圆触发时）
        }
        ci.cancel();
        MTRLiftColorClient.openRadialChooser(blockPos);
    }
}
