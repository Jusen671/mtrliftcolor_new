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
 * arrowOffsetX/arrowOffsetY：箭头在屏幕上的横向/纵向偏移。</p>
 */
public class LiftColorConfig implements SerializedDataBase {

    private static final String KEY_DISPLAY_COLOR = "displayColor";
    private static final String KEY_ARROW_SCALE = "arrowScale";
    private static final String KEY_ARROW_OFFSET_X = "arrowOffsetX";
    private static final String KEY_ARROW_OFFSET_Y = "arrowOffsetY";
    private static final String KEY_ARROW_DIRECTION = "arrowDirection";

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
        this.arrowScale = (float) readerBase.getDouble(KEY_ARROW_SCALE, this.arrowScale);
        this.arrowOffsetX = (float) readerBase.getDouble(KEY_ARROW_OFFSET_X, this.arrowOffsetX);
        this.arrowOffsetY = (float) readerBase.getDouble(KEY_ARROW_OFFSET_Y, this.arrowOffsetY);
        this.arrowDirection = clampDirection(readerBase.getInt(KEY_ARROW_DIRECTION, this.arrowDirection));
    }

    @Override
    public void serializeData(WriterBase writerBase) {
        writerBase.writeInt(KEY_DISPLAY_COLOR, this.displayColor);
        writerBase.writeDouble(KEY_ARROW_SCALE, this.arrowScale);
        writerBase.writeDouble(KEY_ARROW_OFFSET_X, this.arrowOffsetX);
        writerBase.writeDouble(KEY_ARROW_OFFSET_Y, this.arrowOffsetY);
        writerBase.writeInt(KEY_ARROW_DIRECTION, clampDirection(this.arrowDirection));
    }

    /** 把方向值夹到 0/1/2，防止旧文件或坏数据出现越界值。 */
    public static int clampDirection(int direction) {
        return direction < DIRECTION_AUTO ? DIRECTION_AUTO : Math.min(direction, DIRECTION_DOWN);
    }
}
