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
 * 修改 MTR RenderLifts.renderLiftDisplay：轿厢屏幕上「楼层文字 + 箭头」都由本模组按分梯配置重绘。
 *
 * <p>颜色替代默认红色；箭头大小 / 偏移、楼层文字大小 / 偏移都来自配置，
 * 两者在同一块屏幕上一起调。采用「取消原逻辑、按配置重绘」的方式，完全掌控显示几何。</p>
 *
 * <p>若该电梯配了自动化积木链（{@link AutoRule}）且电梯正在上 / 下行，
 * 则取对应方向的链覆盖箭头与楼层文字的设置；链里没拼的项沿用手动配置。</p>
 */
@Mixin(value = RenderLifts.class, remap = false)
public abstract class RenderLiftsMixin {

    /** MTR 原版楼层文字的字号（18 / 屏幕宽度），本模组的大小倍率在此基础上乘算。 */
    private static final float VANILLA_TEXT_BASE = 18.0F;
    /** MTR 原版箭头边长（屏幕宽度的 1/3），大小倍率同样按此乘算。 */
    private static final float ARROW_BASE_DIVISOR = 3.0F;

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

        // 楼层文字：手动配置为底
        float textScale = config.textScale;
        float textOffX = config.textOffsetX;
        float textOffY = config.textOffsetY;
        // 箭头：手动配置为底
        float arrowScale = config.arrowScale;
        float arrowOffX = config.arrowOffsetX;
        float arrowOffY = config.arrowOffsetY;

        final ObjectObjectImmutablePair<LiftDirection, ObjectObjectImmutablePair<String, String>> liftDetails =
                RenderLifts.getLiftDetails(world, lift, Init.positionToBlockPos(lift.getCurrentFloor().getPosition()));
        final LiftDirection liftDirection = liftDetails.left();

        // 自动化积木链：按当前运行方向取链求效果，覆盖箭头与楼层文字
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
            if (effect.hasArrowScale) {
                arrowScale = effect.arrowScale;
            }
            if (effect.hasOffX) {
                arrowOffX = effect.offX;
            }
            if (effect.hasOffY) {
                arrowOffY = effect.offY;
            }
            if (effect.hasTextScale) {
                textScale = effect.textScale;
            }
            if (effect.hasTextOffX) {
                textOffX = effect.textOffX;
            }
            if (effect.hasTextOffY) {
                textOffY = effect.textOffY;
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

        // 箭头方向：自动=跟随运行方向；固定朝上/朝下=任何时候都画。
        final boolean showArrow = forceUp || forceDown || liftDirection != LiftDirection.NONE;
        final boolean arrowUp = forceUp || (!forceDown && liftDirection == LiftDirection.UP);

        // 给后面的 lambda 用的有效 final 副本
        final float drawTextScale = textScale;
        final float drawTextOffX = textOffX;
        final float drawTextOffY = textOffY;
        final float drawArrowScale = arrowScale;
        final float drawArrowOffX = arrowOffX;
        final float drawArrowOffY = arrowOffY;

        // 楼层文字：与箭头一样属于轿厢屏幕，字号 = 原版 18/width 再乘大小倍率，位置按偏移平移
        MainRenderer.scheduleRender(QueuedRenderLayer.TEXT, (graphicsHolder, offset) -> {
            storedMatrixTransformations.transform(graphicsHolder, offset);
            IDrawing.drawStringWithFont(
                    graphicsHolder,
                    liftDetails.right().left(),
                    IGui.HorizontalAlignment.CENTER,
                    IGui.VerticalAlignment.BOTTOM,
                    drawTextOffX, height + drawTextOffY, width, -1,
                    VANILLA_TEXT_BASE / width * drawTextScale,
                    color,
                    false,
                    GraphicsHolder.getDefaultLight(),
                    null);
            graphicsHolder.pop();
        });

        if (showArrow) {
            final float arrowWidth = width / ARROW_BASE_DIVISOR * drawArrowScale;
            final float arrowHeight = width / ARROW_BASE_DIVISOR * drawArrowScale;
            MainRenderer.scheduleRender(
                    new Identifier(Init.MOD_ID, "textures/block/sign/lift_arrow.png"),
                    false,
                    QueuedRenderLayer.LIGHT_TRANSLUCENT,
                    (graphicsHolder, offset) -> {
                        storedMatrixTransformations.transform(graphicsHolder, offset);
                        IDrawing.drawTexture(
                                graphicsHolder,
                                -width / 6.0F + drawArrowOffX,
                                drawArrowOffY,
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
