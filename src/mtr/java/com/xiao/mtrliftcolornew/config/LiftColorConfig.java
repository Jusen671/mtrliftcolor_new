package com.xiao.mtrliftcolornew.config;

import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.serializer.WriterBase;

/**
 * 单台电梯的调色配置。
 *
 * <p>存储采用与 YTE 相同的手法：让配置类实现 MTR 的 {@link SerializedDataBase}，
 * 通过 {@link ReaderBase}/{@link WriterBase} 完成读写。这里选用 MTR 自带的
 * {@code JsonReader}/{@code JsonWriter}，因此磁盘上落的是可读的 JSON 文件，
 * 文件路径为「存档/mtrliftcolor/minecraft/&lt;维度&gt;/lift_configs/两字符小写短码/一长串小写十六进制.json」，
 * 由服务端 {@code LiftColorServerStore} 负责落盘，客户端只渲染。</p>
 *
 * <p>displayColor：轿厢内部屏幕（文字+箭头）颜色，ARGB 格式，默认与 MTR 原版一致为红色 0xFFFF0000。
 * arrowScale：箭头相对 MTR 默认尺寸（width/3）的缩放倍率。
 * arrowOffsetX/arrowOffsetY：箭头在屏幕上的横向/纵向偏移。
 * textScale/textOffsetX/textOffsetY：楼层文字（与箭头同一块屏幕）的大小倍率与横向/纵向偏移。</p>
 */
public class LiftColorConfig implements SerializedDataBase {

    private static final String KEY_DISPLAY_COLOR = "displayColor";
    private static final String KEY_ARROW_SCALE = "arrowScale";
    private static final String KEY_ARROW_OFFSET_X = "arrowOffsetX";
    private static final String KEY_ARROW_OFFSET_Y = "arrowOffsetY";
    private static final String KEY_ARROW_DIRECTION = "arrowDirection";
    private static final String KEY_TEXT_SCALE = "textScale";
    private static final String KEY_TEXT_OFFSET_X = "textOffsetX";
    private static final String KEY_TEXT_OFFSET_Y = "textOffsetY";

    /** 箭头方向：自动跟随电梯运行方向（默认）。 */
    public static final int DIRECTION_AUTO = 0;
    /** 箭头方向：固定朝上。 */
    public static final int DIRECTION_UP = 1;
    /** 箭头方向：固定朝下。 */
    public static final int DIRECTION_DOWN = 2;

    public int displayColor = 0xFFFF0000;
    public float arrowScale = 1.0F;
    public float arrowOffsetX = 0.0F;
    public float arrowOffsetY = 0.0F;
    /** 箭头方向：0=自动跟随、1=固定朝上、2=固定朝下。 */
    public int arrowDirection = DIRECTION_AUTO;
    /** 楼层文字大小倍率：1 = 原版大小（原版字号 = 18 / 屏幕宽度）。 */
    public float textScale = 1.0F;
    /** 楼层文字横向偏移，单位与箭头偏移一致。 */
    public float textOffsetX = 0.0F;
    /** 楼层文字纵向偏移，正数向下、负数向上。 */
    public float textOffsetY = 0.0F;
    public String liftId = "";

    // ---- 客户端缓存记录附带的元数据（用于自动化批量处理与导出/导入） ----
    /** 电梯的长十六进制 id（小写），与磁盘文件名一致。 */
    public String hexId = "";
    /** 电梯所在维度的命名空间，如 minecraft。 */
    public String dimNamespace = "minecraft";
    /** 电梯所在维度的路径，如 overworld / the_nether / the_end。 */
    public String dimPath = "overworld";

    public LiftColorConfig() {
    }

    public LiftColorConfig(int displayColor, float arrowScale, float arrowOffsetX, float arrowOffsetY, String liftId) {
        this.displayColor = displayColor;
        this.arrowScale = arrowScale;
        this.arrowOffsetX = arrowOffsetX;
        this.arrowOffsetY = arrowOffsetY;
        this.liftId = liftId;
    }

    @Override
    public void updateData(ReaderBase readerBase) {
        this.displayColor = readerBase.getInt(KEY_DISPLAY_COLOR, this.displayColor);
        this.arrowScale = clampScale((float) readerBase.getDouble(KEY_ARROW_SCALE, this.arrowScale));
        this.arrowOffsetX = clampOffset((float) readerBase.getDouble(KEY_ARROW_OFFSET_X, this.arrowOffsetX));
        this.arrowOffsetY = clampOffset((float) readerBase.getDouble(KEY_ARROW_OFFSET_Y, this.arrowOffsetY));
        this.arrowDirection = clampDirection(readerBase.getInt(KEY_ARROW_DIRECTION, this.arrowDirection));
        this.textScale = clampScale((float) readerBase.getDouble(KEY_TEXT_SCALE, this.textScale));
        this.textOffsetX = clampOffset((float) readerBase.getDouble(KEY_TEXT_OFFSET_X, this.textOffsetX));
        this.textOffsetY = clampOffset((float) readerBase.getDouble(KEY_TEXT_OFFSET_Y, this.textOffsetY));
    }

    @Override
    public void serializeData(WriterBase writerBase) {
        writerBase.writeInt(KEY_DISPLAY_COLOR, this.displayColor);
        writerBase.writeDouble(KEY_ARROW_SCALE, this.arrowScale);
        writerBase.writeDouble(KEY_ARROW_OFFSET_X, this.arrowOffsetX);
        writerBase.writeDouble(KEY_ARROW_OFFSET_Y, this.arrowOffsetY);
        writerBase.writeInt(KEY_ARROW_DIRECTION, clampDirection(this.arrowDirection));
        writerBase.writeDouble(KEY_TEXT_SCALE, this.textScale);
        writerBase.writeDouble(KEY_TEXT_OFFSET_X, this.textOffsetX);
        writerBase.writeDouble(KEY_TEXT_OFFSET_Y, this.textOffsetY);
    }

    /** 把另一份配置的显示参数复制到本份（颜色 + 箭头 + 楼层文字）。 */
    public void copyDisplayFrom(LiftColorConfig other) {
        this.displayColor = other.displayColor;
        this.arrowScale = other.arrowScale;
        this.arrowOffsetX = other.arrowOffsetX;
        this.arrowOffsetY = other.arrowOffsetY;
        this.arrowDirection = other.arrowDirection;
        this.textScale = other.textScale;
        this.textOffsetX = other.textOffsetX;
        this.textOffsetY = other.textOffsetY;
    }

    /** 箭头方向：把方向值夹到 0/1/2，防止旧文件或坏数据出现越界值。 */
    public static int clampDirection(int direction) {
        return direction < DIRECTION_AUTO ? DIRECTION_AUTO : Math.min(direction, DIRECTION_DOWN);
    }

    /** 大小倍率下限。 */
    public static final float MIN_SCALE = 0.05F;
    /** 大小倍率上限。 */
    public static final float MAX_SCALE = 8.0F;
    /** 偏移下限。 */
    public static final float MIN_OFFSET = -2.0F;
    /** 偏移上限。 */
    public static final float MAX_OFFSET = 2.0F;

    /** 大小倍率夹到 {@link #MIN_SCALE}..{@link #MAX_SCALE}，并过滤非法数。 */
    public static float clampScale(float scale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale)) {
            return 1.0F;
        }
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    /** 偏移夹到 {@link #MIN_OFFSET}..{@link #MAX_OFFSET}，并过滤非法数。 */
    public static float clampOffset(float offset) {
        if (Float.isNaN(offset) || Float.isInfinite(offset)) {
            return 0.0F;
        }
        return Math.max(MIN_OFFSET, Math.min(MAX_OFFSET, offset));
    }
}
