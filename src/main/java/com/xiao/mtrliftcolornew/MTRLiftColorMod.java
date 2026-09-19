package com.xiao.mtrliftcolornew;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 主入口（客户端与服务器都加载）。
 *
 * <p>只有轻量的初始化日志；配色收发与存储逻辑分别由客户端入口 {@code MTRLiftColorClient}
 * 与服务器入口 {@code MTRLiftColorServer} 注册。</p>
 */
public final class MTRLiftColorMod implements ModInitializer {
    public static final String MOD_ID = "mtrliftcolornew";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[新·MTR电梯染色模组] 初始化完成。");
    }
}
