package com.xiao.mtrliftcolornew;

import com.xiao.mtrliftcolornew.network.LiftColorNetwork;
import net.fabricmc.api.DedicatedServerModInitializer;

/**
 * 专用服务器入口。
 *
 * <p>只注册服务端的配色接收与广播；客户端渲染与 GUI 不在此加载。</p>
 */
public final class MTRLiftColorServer implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        LiftColorNetwork.registerServer();
    }
}
