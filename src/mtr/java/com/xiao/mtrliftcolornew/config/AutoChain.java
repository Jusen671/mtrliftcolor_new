package com.xiao.mtrliftcolornew.config;

import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 自动化「积木链」：图形化编程式的一条规则，由若干块拼成。
 *
 * <p>箭头与楼层文字都属于轿厢里同一块屏幕，因此两类积木放在同一条链里编辑。
 * 类型编号保持向后兼容（0~3 是旧版本就在用的编号，新增的类型只能往后排）：</p>
 * <ul>
 *   <li>{@link #TYPE_OFF_X} / {@link #TYPE_OFF_Y}：箭头横向 / 纵向偏移（带数值）；</li>
 *   <li>{@link #TYPE_DIR_UP} / {@link #TYPE_DIR_DOWN}：箭头方向向上 / 向下；</li>
 *   <li>{@link #TYPE_TEXT_SCALE} / {@link #TYPE_TEXT_OFF_X} / {@link #TYPE_TEXT_OFF_Y}：
 *       楼层文字大小与横向 / 纵向偏移（带数值）；</li>
 *   <li>{@link #TYPE_ARROW_SCALE}：箭头大小（带数值）。</li>
 * </ul>
 *
 * <p>同类型只允许出现一次（重复拖入按移动处理）。链为空表示不做任何覆盖，
 * 渲染时回落到电梯自己的手动配置。</p>
 *
 * <p>序列化格式为 JSON 数组：{@code [{"t":0,"v":0.12},{"t":2}]}，
 * 随自动化配置文件落盘、经网络在客户端与服务端之间同步。</p>
 */
public final class AutoChain {

    /** 箭头横向偏移（数值）。 */
    public static final int TYPE_OFF_X = 0;
    /** 箭头纵向偏移（数值）。 */
    public static final int TYPE_OFF_Y = 1;
    /** 箭头方向向上（无数值）。 */
    public static final int TYPE_DIR_UP = 2;
    /** 箭头方向向下（无数值）。 */
    public static final int TYPE_DIR_DOWN = 3;
    /** 楼层文字大小（数值）。 */
    public static final int TYPE_TEXT_SCALE = 4;
    /** 楼层文字横向偏移（数值）。 */
    public static final int TYPE_TEXT_OFF_X = 5;
    /** 楼层文字纵向偏移（数值）。 */
    public static final int TYPE_TEXT_OFF_Y = 6;
    /** 箭头大小（数值）。 */
    public static final int TYPE_ARROW_SCALE = 7;

    /** 最小类型编号。 */
    public static final int TYPE_MIN = TYPE_OFF_X;
    /** 最大类型编号。 */
    public static final int TYPE_MAX = TYPE_ARROW_SCALE;
    /** 调色面板「显示设置」页里积木的展示顺序。 */
    public static final int[] TYPES = {TYPE_DIR_UP, TYPE_DIR_DOWN, TYPE_ARROW_SCALE, TYPE_OFF_X, TYPE_OFF_Y,
            TYPE_TEXT_SCALE, TYPE_TEXT_OFF_X, TYPE_TEXT_OFF_Y};

    /** 链中的一块积木。 */
    public static final class Block {
        public final int type;
        public final float value;

        public Block(int type, float value) {
            this.type = type;
            this.value = value;
        }

        /** 带数值输入的块（输入框，可打字也可拖动改值）。 */
        public boolean isSlider() {
            return isNumeric(type);
        }

        /** 中文标签，界面与调试输出共用。 */
        public String label() {
            return AutoChain.label(type);
        }

        /** 数值显示文本，例如 {@code 箭头大小:1.20}。 */
        public String text() {
            return AutoChain.text(type, value);
        }

        /** 当前值的合法区间，供输入框夹值。 */
        public float[] range() {
            return rangeOf(type);
        }

        JsonObject toJson() {
            final JsonObject obj = new JsonObject();
            obj.addProperty("t", type);
            if (isSlider()) {
                obj.addProperty("v", value);
            }
            return obj;
        }

        static Block fromJson(JsonObject obj) {
            final int t = obj.has("t") ? obj.get("t").getAsInt() : -1;
            final float v = obj.has("v") ? (float) obj.get("v").getAsDouble() : 0F;
            return new Block(t, clampValue(t, v));
        }
    }

    /** 链的求值结果。 */
    public static final class Effect {
        public boolean hasOffX;
        public boolean hasOffY;
        public boolean hasDirection;
        public boolean hasArrowScale;
        public boolean hasTextScale;
        public boolean hasTextOffX;
        public boolean hasTextOffY;
        public float offX;
        public float offY;
        public float arrowScale = 1.0F;
        /** true = 箭头向上（TYPE_DIR_UP）。 */
        public boolean dirUp;
        public float textScale = 1.0F;
        public float textOffX;
        public float textOffY;
    }

    private final List<Block> blocks = new ArrayList<>();

    public int size() {
        return blocks.size();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public Block get(int index) {
        return blocks.get(index);
    }

    /** 某类型积木在链中的下标，没有则 -1。 */
    public int indexOf(int type) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).type == type) {
                return i;
            }
        }
        return -1;
    }

    /** 在指定位置插入；同类型已存在时先移除（等效于把旧的那块挪到新位置）。 */
    public void insert(int index, Block block) {
        if (!isValidType(block.type)) {
            return;
        }
        final int existing = indexOf(block.type);
        if (existing >= 0) {
            blocks.remove(existing);
            if (existing < index) {
                index--;
            }
        }
        blocks.add(Math.max(0, Math.min(index, blocks.size())), new Block(block.type, clampValue(block.type, block.value)));
    }

    /** 原地替换某一块（输入框改数值时用，避免链顺序被打乱）。 */
    public void set(int index, Block block) {
        if (index >= 0 && index < blocks.size() && isValidType(block.type)) {
            blocks.set(index, new Block(block.type, clampValue(block.type, block.value)));
        }
    }

    public void removeAt(int index) {
        if (index >= 0 && index < blocks.size()) {
            blocks.remove(index);
        }
    }

    public void clear() {
        blocks.clear();
    }

    /** 序列化成网络 / 磁盘用的 JSON 数组字符串。 */
    public String toJson() {
        final JsonArray array = new JsonArray();
        for (final Block block : blocks) {
            array.add(block.toJson());
        }
        return new org.mtr.libraries.com.google.gson.Gson().toJson(array);
    }

    /** 从 JSON 数组字符串解析；坏数据按空链处理，绝不抛异常。 */
    public static AutoChain fromJson(String json) {
        final AutoChain chain = new AutoChain();
        if (json == null || json.isEmpty()) {
            return chain;
        }
        try {
            final JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) {
                return chain;
            }
            for (final JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                final Block block = Block.fromJson(element.getAsJsonObject());
                if (isValidType(block.type) && chain.indexOf(block.type) < 0) {
                    chain.blocks.add(block);
                }
            }
        } catch (RuntimeException ignored) {
            // 坏链按空处理
        }
        return chain;
    }

    /** 按链中积木求最终效果（空链返回的 Effect 所有标记均为 false）。 */
    public Effect evaluate() {
        final Effect effect = new Effect();
        for (final Block block : blocks) {
            switch (block.type) {
                case TYPE_OFF_X:
                    effect.hasOffX = true;
                    effect.offX = block.value;
                    break;
                case TYPE_OFF_Y:
                    effect.hasOffY = true;
                    effect.offY = block.value;
                    break;
                case TYPE_ARROW_SCALE:
                    effect.hasArrowScale = true;
                    effect.arrowScale = block.value;
                    break;
                case TYPE_DIR_UP:
                    effect.hasDirection = true;
                    effect.dirUp = true;
                    break;
                case TYPE_DIR_DOWN:
                    effect.hasDirection = true;
                    effect.dirUp = false;
                    break;
                case TYPE_TEXT_SCALE:
                    effect.hasTextScale = true;
                    effect.textScale = block.value;
                    break;
                case TYPE_TEXT_OFF_X:
                    effect.hasTextOffX = true;
                    effect.textOffX = block.value;
                    break;
                case TYPE_TEXT_OFF_Y:
                    effect.hasTextOffY = true;
                    effect.textOffY = block.value;
                    break;
                default:
                    break;
            }
        }
        return effect;
    }

    /** 复制一条链（模板下发到各电梯时使用）。 */
    public AutoChain copy() {
        return fromJson(toJson());
    }

    // ---------- 类型元数据 ----------

    /** 是否是已知类型。 */
    public static boolean isValidType(int type) {
        return type >= TYPE_MIN && type <= TYPE_MAX;
    }

    /** 带数值输入的类型（其余是纯开关块）。 */
    public static boolean isNumeric(int type) {
        return type != TYPE_DIR_UP && type != TYPE_DIR_DOWN;
    }

    /** 数值类型所属的区间：大小倍率类 or 偏移类。 */
    public static boolean isScaleType(int type) {
        return type == TYPE_ARROW_SCALE || type == TYPE_TEXT_SCALE;
    }

    /** 某类型的合法取值区间 {min, max}。 */
    public static float[] rangeOf(int type) {
        return isScaleType(type)
                ? new float[]{LiftColorConfig.MIN_SCALE, LiftColorConfig.MAX_SCALE}
                : new float[]{LiftColorConfig.MIN_OFFSET, LiftColorConfig.MAX_OFFSET};
    }

    /** 把数值夹到该类型的合法区间。 */
    public static float clampValue(int type, float value) {
        if (!isNumeric(type)) {
            return 0F;
        }
        return isScaleType(type) ? LiftColorConfig.clampScale(value) : LiftColorConfig.clampOffset(value);
    }

    /** 某类型积木的默认数值（新拖入时使用）。 */
    public static float defaultValue(int type) {
        return isScaleType(type) ? 1.0F : 0.0F;
    }

    /** 中文标签。 */
    public static String label(int type) {
        switch (type) {
            case TYPE_OFF_X:
                return "箭头偏移X";
            case TYPE_OFF_Y:
                return "箭头偏移Y";
            case TYPE_DIR_UP:
                return "箭头方向:向上";
            case TYPE_DIR_DOWN:
                return "箭头方向:向下";
            case TYPE_TEXT_SCALE:
                return "文字大小";
            case TYPE_TEXT_OFF_X:
                return "文字偏移X";
            case TYPE_TEXT_OFF_Y:
                return "文字偏移Y";
            case TYPE_ARROW_SCALE:
                return "箭头大小";
            default:
                return "未知积木";
        }
    }

    /** 界面显示文本：数值类型带上格式化后的数值。 */
    public static String text(int type, float value) {
        if (!isNumeric(type)) {
            return label(type);
        }
        return label(type) + ":" + format(type, value);
    }

    /** 数值文本：倍率类保留两位小数，偏移类保留两位小数并去掉多余的 0。 */
    public static String format(int type, float value) {
        return String.format(Locale.ROOT, isScaleType(type) ? "%.2f" : "%.2f", value);
    }

    /** 该类型属于「箭头」还是「楼层文字」，用于界面分组显示。 */
    public static String group(int type) {
        return type == TYPE_TEXT_SCALE || type == TYPE_TEXT_OFF_X || type == TYPE_TEXT_OFF_Y ? "楼层文字" : "箭头";
    }
}
