package org.sunrise.game.rpc.center;

import org.sunrise.game.core.server.BaseServer;
import org.sunrise.game.core.server.BaseServerManager;
import org.sunrise.game.rpc.policy.RpcConnectPolicy;
import org.sunrise.game.utils.IdGenerator;
import org.sunrise.game.utils.Utils;

public class CenterServer {
    private final String ip;
    private final int port;
    private final BaseServer server;

    public CenterServer(int id, String ip, int port) {
        this.ip = ip;
        this.port = port;
        this.server = new BaseServer(this.getClass().getSimpleName() + id) {
            @Override
            public void onStart() {
                super.onStart();
            }

            @Override
            public void onStop() {
                if (isStartSuccess()) {
                    super.onStop();
                }
            }
        };
        this.server.setMessageManager(new CenterServerMessageManager(server.getNodeId()));
        BaseServerManager.register(server);
        IdGenerator.init(id);
        RpcConnectPolicy.init();
    }

    public String getNodeId() {
        return server.getNodeId();
    }

    public void start() {
        server.startListen(Utils.getListenIpAddress(), port);
    }

}
