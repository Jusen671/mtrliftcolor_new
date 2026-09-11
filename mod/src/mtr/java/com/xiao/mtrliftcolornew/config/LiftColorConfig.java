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
 * 文件路径为「存档/mtrliftcolor/维度命名空间/维度路径/lift_configs/电梯名或短码/两字符短码.json」。</p>
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

    public int displayColor = 0xFFFF0000;
    public float arrowScale = 1.0F;
    public float arrowOffsetX = 0.0F;
    public float arrowOffsetY = 0.0F;
    public String liftId = "";

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
    }

    @Override
    public void serializeData(WriterBase writerBase) {
        writerBase.writeInt(KEY_DISPLAY_COLOR, this.displayColor);
        writerBase.writeDouble(KEY_ARROW_SCALE, this.arrowScale);
        writerBase.writeDouble(KEY_ARROW_OFFSET_X, this.arrowOffsetX);
        writerBase.writeDouble(KEY_ARROW_OFFSET_Y, this.arrowOffsetY);
    }
}
