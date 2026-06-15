package org.sunrise.game.game.init;

import org.sunrise.game.game.logic.ConfigUtils;
import org.sunrise.game.game.logic.LogicUtils;
import org.sunrise.game.game.logic.ProtoParserUtils;
import org.sunrise.game.game.logic.map.MapNavUtils;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.modules.ModuleUtils;

import java.util.List;

/**
 * GameServer / RunAllOne 共用的游戏逻辑层初始化。
 * <p>
 * 从各 StartUp 的 main 中抽离，避免 GameServerStartUp 与 RunAllOneServerStartUp 重复维护同一套扫描包与加载顺序。
 * 须在 {@link org.sunrise.game.rpc.node.RpcNodeManager#runMain} 的 {@code beforeListen} 回调中调用，
 * 即 RPC 节点已创建、尚未 {@code start()} 监听之前。
 */
public final class GameServerRuntime {
    /** {@link org.sunrise.game.game.annotation.MsgHandlerClass} 扫描根包 */
    private static final List<String> LOGIC_PACKAGES = List.of("org.sunrise.game.game.logic");
    /** {@link org.sunrise.game.game.annotation.HumanModule} 扫描根包 */
    private static final List<String> MODULE_PACKAGES = List.of("org.sunrise.game.game.modules");
    /** {@link org.sunrise.game.game.annotation.GameSystem} 扫描根包 */
    private static final List<String> SYSTEM_PACKAGES = List.of("org.sunrise.game.game.logic.system");

    private GameServerRuntime() {
    }

    /**
     * 按固定顺序完成游戏侧 classpath 扫描与资源加载；顺序不可随意调整（如 LogicUtils 依赖 ProtoParserUtils）。
     */
    public static void init() {
        // Luban 配置表
        ConfigUtils.load();
        // 地图寻路 JSON
        MapNavUtils.load();
        // Protobuf 消息类索引
        ProtoParserUtils.init();
        // 客户端协议处理器
        LogicUtils.init(LOGIC_PACKAGES);
        // 玩家数据模块
        ModuleUtils.init(MODULE_PACKAGES);
        // 服务器级游戏系统（活动、矿场等）
        GameSystemUtils.init(SYSTEM_PACKAGES);
    }
}
