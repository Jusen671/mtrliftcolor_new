package com.xiao.mtrliftcolornew.config;

/**
 * 单台电梯的自动化规则：上行链 + 下行链，渲染时按电梯当前运行方向取对应链覆盖箭头设置。
 *
 * <p>由服务端存盘（{@code lift_configs/xx/auto/<hex>.json}）并同步给所有客户端；
 * 客户端 {@code LiftColorManager} 按电梯 id 缓存。</p>
 */
public final class AutoRule {

    public static final AutoRule EMPTY = new AutoRule(new AutoChain(), new AutoChain());

    /** 上行时生效的积木链。 */
    public final AutoChain up;
    /** 下行时生效的积木链。 */
    public final AutoChain down;

    public AutoRule(AutoChain up, AutoChain down) {
        this.up = up == null ? new AutoChain() : up;
        this.down = down == null ? new AutoChain() : down;
    }

    public boolean isEmpty() {
        return up.isEmpty() && down.isEmpty();
    }

    /** 取电梯朝指定方向运行时应生效的链；静止返回 null。 */
    public AutoChain chainFor(boolean goingUp) {
        final AutoChain chain = goingUp ? up : down;
        return chain.isEmpty() ? null : chain;
    }
}
