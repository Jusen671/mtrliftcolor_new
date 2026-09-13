// Portions derived from MTR (MIT License), original copyright MTR developers       
package com.xiao.mtrliftcolornew.mixin;

import com.xiao.mtrliftcolornew.config.LiftColorConfig;
import com.xiao.mtrliftcolornew.config.LiftColorManager;
import org.mtr.core.data.Lift;
import org.mtr.core.data.LiftDirection;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.Init;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.render.MainRenderer;
import org.mtr.mod.render.QueuedRenderLayer;
import org.mtr.mod.render.RenderLifts;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修改 MTR RenderLifts.renderLiftDisplay：
 * 用分梯配置里的颜色替代默认红色，并按配置调整箭头大小与偏移。
 * 采用"取消原逻辑、按配置重绘"的方式，完全掌控颜色和箭头几何。
 */
@Mixin(value = RenderLifts.class, remap = false)
public abstract class RenderLiftsMixin {

    @Inject(method = "renderLiftDisplay(Lorg/mtr/mod/render/StoredMatrixTransformations;Lorg/mtr/mapping/holder/World;Lorg/mtr/core/data/Lift;FF)V", at = @At("HEAD"), cancellable = true)
    private static void mtrliftcolor$renderLiftDisplay(
            StoredMatrixTransformations storedMatrixTransformations,
            org.mtr.mapping.holder.World world,
            Lift lift,
            float width,
            float height,
            CallbackInfo ci) {

        final LiftColorConfig config = LiftColorManager.get(lift, world.data);
        final int color = config.displayColor;
        final float scale = config.arrowScale;
        final float offX = config.arrowOffsetX;
        final float offY = config.arrowOffsetY;

        final ObjectObjectImmutablePair<LiftDirection, ObjectObjectImmutablePair<String, String>> liftDetails =
                RenderLifts.getLiftDetails(world, lift, Init.positionToBlockPos(lift.getCurrentFloor().getPosition()));
        final LiftDirection liftDirection = liftDetails.left();

        MainRenderer.scheduleRender(QueuedRenderLayer.TEXT, (graphicsHolder, offset) -> {
            storedMatrixTransformations.transform(graphicsHolder, offset);
            IDrawing.drawStringWithFont(
                    graphicsHolder,
                    liftDetails.right().left(),
                    IGui.HorizontalAlignment.CENTER,
                    IGui.VerticalAlignment.BOTTOM,
                    0, height, width, -1, 18 / width,
                    color,
                    false,
                    GraphicsHolder.getDefaultLight(),
                    null);
            graphicsHolder.pop();
        });

        if (liftDirection != LiftDirection.NONE) {
            final float arrowWidth = width / 3.0F * scale;
            final float arrowHeight = width / 3.0F * scale;
            MainRenderer.scheduleRender(
                    new Identifier(Init.MOD_ID, "textures/block/sign/lift_arrow.png"),
                    false,
                    QueuedRenderLayer.LIGHT_TRANSLUCENT,
                    (graphicsHolder, offset) -> {
                        storedMatrixTransformations.transform(graphicsHolder, offset);
                        IDrawing.drawTexture(
                                graphicsHolder,
                                -width / 6.0F + offX,
                                offY,
                                arrowWidth,
                                arrowHeight,
                                0,
                                liftDirection == LiftDirection.UP ? 0 : 1,
                                1,
                                liftDirection == LiftDirection.UP ? 1 : 0,
                                Direction.UP,
                                color,
                                GraphicsHolder.getDefaultLight());
                        graphicsHolder.pop();
                    });
        }

        ci.cancel();
    }
}
