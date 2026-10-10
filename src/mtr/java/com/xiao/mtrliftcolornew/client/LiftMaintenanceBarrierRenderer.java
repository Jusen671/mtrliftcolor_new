package com.xiao.mtrliftcolornew.client;

import java.util.List;

import com.xiao.mtrliftcolornew.block.LiftMaintenanceBarrierBlock;
import com.xiao.mtrliftcolornew.block.LiftMaintenanceBarrierBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/**
 * 把围挡布面上的字用 Minecraft 自带字体「现场画」上去。
 *
 * <p>为什么不用贴图：字烤进贴图又糊又改不了字体，还只能是一张死图；走 MC 自己的文字渲染
 * 管线之后，字是清楚的，而且会跟着玩家用的字体资源包走（包括中文）。贴图里只剩下黄布、
 * 黑色包边和那个红圈禁止符号。</p>
 *
 * <p>坐标全部按方块模型的「模型单位」（1 格 = 16 单位）来写，和 tools/barrier.py 里那套
 * 几何一一对应：中片 x 3~13、左片 -7~3、右片 13~23，折页立柱在 x=3 / x=13，布面正面在
 * z=7。折叠的旋转角度、轴心、方向都跟方块模型 JSON 里的元素 rotation 完全一致。</p>
 */
public class LiftMaintenanceBarrierRenderer implements BlockEntityRenderer<LiftMaintenanceBarrierBlockEntity> {

    /** 折页立柱中心（模型单位）。 */
    private static final float FOLD_L = 3.0F;
    private static final float FOLD_R = 13.0F;
    /** 折页 / 布面所在的 z（模型单位）。 */
    private static final float PIVOT_Z = 7.5F;
    /** 布面正面所在的 z：贴图上按 z=7 画，字稍微往外挪一丝，免得和布面打架。 */
    private static final float TEXT_Z = 6.98F;

    /** 三片布各自的正中 x（模型单位）。 */
    private static final float CENTER_X = 8.0F;
    private static final float LEFT_X = -2.0F;
    private static final float RIGHT_X = 18.0F;

    private static final int RED = 0xFFE0261C;
    private static final int DARK = 0xFF232326;

    /**
     * 布面上的四行字。
     *
     * <p>{@code scale} 是「一个字体像素等于多少模型单位」，{@code topY} 是这一行文字框顶端的
     * y（模型单位）。数字和 tools/barrier.py 里禁止符号的上下留白对齐，改的时候两边要一起改。</p>
     */
    private static final List<Line> LINES = List.of(
            new Line(Text.literal("危险 DANGER"), RED, 0.135F, 13.40F),
            new Line(Text.literal("电梯例行保养中"), DARK, 0.100F, 11.95F),
            new Line(Text.literal("禁止进入").setStyle(Style.EMPTY.withBold(true)), DARK, 0.135F, 5.15F),
            new Line(Text.literal("NO ENTRY"), DARK, 0.100F, 3.75F));

    private final TextRenderer font;

    public LiftMaintenanceBarrierRenderer(BlockEntityRendererFactory.Context context) {
        this.font = context.getTextRenderer();
    }

    @Override
    public void render(LiftMaintenanceBarrierBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = entity.getCachedState();
        if (!(state.getBlock() instanceof LiftMaintenanceBarrierBlock block)) {
            return;
        }
        Direction facing = state.get(LiftMaintenanceBarrierBlock.FACING);
        float fold = block.foldDegrees();

        matrices.push();
        // 先按朝向转，转法和 blockstate 里的 "y" 完全一致（绕方块中心）。
        matrices.translate(0.5F, 0.5F, 0.5F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-modelY(facing)));
        matrices.translate(-0.5F, -0.5F, -0.5F);

        drawPanel(matrices, vertexConsumers, light, CENTER_X, 0.0F, 0.0F);
        drawPanel(matrices, vertexConsumers, light, LEFT_X, fold, FOLD_L);
        drawPanel(matrices, vertexConsumers, light, RIGHT_X, -fold, FOLD_R);

        matrices.pop();
    }

    /** 画一片布上的四行字；angle/pivotX 是这片布相对中片的折角与折页位置。 */
    private void drawPanel(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                           float centerX, float angle, float pivotX) {
        matrices.push();
        if (angle != 0.0F) {
            matrices.translate(pivotX / 16.0F, 0.0F, PIVOT_Z / 16.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
            matrices.translate(-pivotX / 16.0F, 0.0F, -PIVOT_Z / 16.0F);
        }
        for (Line line : LINES) {
            matrices.push();
            matrices.translate(centerX / 16.0F, line.topY() / 16.0F, TEXT_Z / 16.0F);
            // 文字 quad 默认朝 +Z，布面正面朝北（-Z），所以转 180°；顺带把横轴翻过来，
            // 从北面看过去才是从左往右读。Y 取负是把字体的「向下」变成世界的「向上」。
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
            matrices.scale(line.scale() / 16.0F, -line.scale() / 16.0F, line.scale() / 16.0F);
            float x = -this.font.getWidth(line.text()) / 2.0F;
            this.font.draw(line.text(), x, 0.0F, line.color(), false,
                    matrices.peek().getPositionMatrix(), vertexConsumers,
                    TextRenderer.TextLayerType.NORMAL, 0, light);
            matrices.pop();
        }
        matrices.pop();
    }

    /** facing=north 对应 blockstate 的 y=0，east 是 90，以此类推。 */
    private static float modelY(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    /** 模型比一格宽，别让包围盒剔除了它。 */
    @Override
    public boolean rendersOutsideBoundingBox(LiftMaintenanceBarrierBlockEntity entity) {
        return true;
    }

    private record Line(Text text, int color, float scale, float topY) {
    }
}
