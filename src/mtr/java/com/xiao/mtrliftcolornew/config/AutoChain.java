package com.xiao.mtrliftcolornew.config;

import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动化「积木链」：图形化编程式的一条规则，由若干块拼成。
 *
 * <p>块类型：</p>
 * <ul>
 *   <li>{@link #TYPE_OFF_X} / {@link #TYPE_OFF_Y}：箭头横向 / 纵向偏移（带数值）；</li>
 *   <li>{@link #TYPE_DIR_UP} / {@link #TYPE_DIR_DOWN}：箭头方向向上 / 向下。</li>
 * </ul>
 *
 * <p>同类型只允许出现一次（重复拖入按移动处理）。链为空表示不做任何覆盖，
 * 渲染时回落到电梯自己的手动配置。</p>
 *
 * <p>序列化格式为 JSON 数组：{@code [{"t":0,"v":0.12},{"t":2}]}，
 * 随自动化配置文件落盘、经网络在客户端与服务端之间同步。</p>
 */
public final class AutoChain {

    public static final int TYPE_OFF_X = 0;
    public static final int TYPE_OFF_Y = 1;
    public static final int TYPE_DIR_UP = 2;
    public static final int TYPE_DIR_DOWN = 3;

    /** 链中的一块积木。 */
    public static final class Block {
        public final int type;
        public final float value;

        public Block(int type, float value) {
            this.type = type;
            this.value = value;
        }

        public boolean isSlider() {
            return type == TYPE_OFF_X || type == TYPE_OFF_Y;
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
            return new Block(t, v);
        }
    }

    /** 链的求值结果。 */
    public static final class Effect {
        public boolean hasOffX;
        public boolean hasOffY;
        public boolean hasDirection;
        public float offX;
        public float offY;
        /** true = 箭头向上（TYPE_DIR_UP）。 */
        public boolean dirUp;
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
        final int existing = indexOf(block.type);
        if (existing >= 0) {
            blocks.remove(existing);
            if (existing < index) {
                index--;
            }
        }
        blocks.add(Math.max(0, Math.min(index, blocks.size())), block);
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
                if (block.type >= TYPE_OFF_X && block.type <= TYPE_DIR_DOWN && chain.indexOf(block.type) < 0) {
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
                case TYPE_DIR_UP:
                    effect.hasDirection = true;
                    effect.dirUp = true;
                    break;
                case TYPE_DIR_DOWN:
                    effect.hasDirection = true;
                    effect.dirUp = false;
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
}
