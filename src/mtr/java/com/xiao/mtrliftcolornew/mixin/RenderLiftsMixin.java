package com.xiao.mtrliftcolornew.mixin;

import com.xiao.mtrliftcolornew.config.AutoChain;
import com.xiao.mtrliftcolornew.config.AutoRule;
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
 *
 * <p>若该电梯配了自动化积木链（{@link AutoRule}）且电梯正在上 / 下行，
 * 则取对应方向的链覆盖箭头方向与偏移；链里没拼的项沿用手动配置。</p>
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

        final ObjectObjectImmutablePair<LiftDirection, ObjectObjectImmutablePair<String, String>> liftDetails =
                RenderLifts.getLiftDetails(world, lift, Init.positionToBlockPos(lift.getCurrentFloor().getPosition()));
        final LiftDirection liftDirection = liftDetails.left();

        // 自动化积木链：按当前运行方向取链求效果，覆盖方向与偏移
        float offX = config.arrowOffsetX;
        float offY = config.arrowOffsetY;
        boolean forceUp = false;
        boolean forceDown = false;
        final AutoRule rule = LiftColorManager.getAutoRule(lift.getId());
        final AutoChain chain = rule == null ? null : switch (liftDirection) {
            case UP -> rule.chainFor(true);
            case DOWN -> rule.chainFor(false);
            default -> null;
        };
        if (chain != null) {
            final AutoChain.Effect effect = chain.evaluate();
            if (effect.hasOffX) {
                offX = effect.offX;
            }
            if (effect.hasOffY) {
                offY = effect.offY;
            }
            if (effect.hasDirection) {
                forceUp = effect.dirUp;
                forceDown = !effect.dirUp;
            }
        } else {
            // 没有生效的链：沿用配色记录里的固定方向（旧版自动化 / 兼容数据）
            forceUp = config.arrowDirection == LiftColorConfig.DIRECTION_UP;
            forceDown = config.arrowDirection == LiftColorConfig.DIRECTION_DOWN;
        }

        // 给后面的 lambda 用的有效 final 副本
        final float drawOffX = offX;
        final float drawOffY = offY;

        // 箭头方向：自动=跟随运行方向；固定朝上/朝下=任何时候都画。
        final boolean showArrow = forceUp || forceDown || liftDirection != LiftDirection.NONE;
        final boolean arrowUp = forceUp || (!forceDown && liftDirection == LiftDirection.UP);

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

        if (showArrow) {
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
                                -width / 6.0F + drawOffX,
                                drawOffY,
                                arrowWidth,
                                arrowHeight,
                                0,
                                arrowUp ? 0 : 1,
                                1,
                                arrowUp ? 1 : 0,
                                Direction.UP,
                                color,
                                GraphicsHolder.getDefaultLight());
                        graphicsHolder.pop();
                    });
        }

        ci.cancel();
    }
}
