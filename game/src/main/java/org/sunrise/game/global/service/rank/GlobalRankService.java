package org.sunrise.game.global.service.rank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.game.logic.playerinfo.PlayerInfo;
import org.sunrise.game.genProto.gen.RankProto;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.global.service.rank.board.BoardManager;
import org.sunrise.game.global.service.rank.board.CustomRankBoard;
import org.sunrise.game.global.service.rank.board.RankBoard;
import org.sunrise.game.global.service.rank.board.SimpleRankBoard;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.function.Call;
import org.sunrise.game.rpc.function.CallContext;
import org.sunrise.game.rpc.function.ErrorType;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.rpc.service.BaseService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 全局通用排行榜服务
 * <p>
 * 职责划分：
 * <ul>
 *   <li>Game 服通过 RPC {@link #updateRank} 上报分数</li>
 *   <li>查询接口异步拉取 {@link org.sunrise.game.global.service.playerinfo.GlobalPlayerInfoService} 补充名字/头像等展示信息</li>
 *   <li>常规榜（{@link RankType#isCustom()} = false）使用内部 {@link SimpleRankBoard}，按单一 long 分数排序</li>
 *   <li>特殊榜继承 {@link CustomRankBoard}，在 {@link #init()} 中手动注册</li>
 * </ul>
 */
@RpcService
public class GlobalRankService extends BaseService {
    /**
     * 客户端未传 pageSize 时的默认值
     */
    private static final int DEFAULT_PAGE_SIZE = 20;
    /**
     * 单页最大条数，防止一次拉取过多
     */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 各 rankType → 榜单实例
     */
    private final BoardManager boardManager = new BoardManager();

    public GlobalRankService(String nodeId) {
        super(nodeId);
    }

    @Override
    public void init() {
        // 自动为 RankType 中 custom=false 的类型创建 SimpleRankBoard
        boardManager.registerDefaultBoards();
        // 特殊榜单示例：boardManager.register(RankType.XXX, new MyCustomRankBoard(RankType.XXX));
    }

    @Override
    public void load() {
        // key: rankType id，value: 该榜全部上榜条目
        getDbData("boards", new TypeReference<Map<Integer, List<SimpleRankEntry>>>() {
        }, boardManager::importAll);
    }

    @Override
    public void save() {
        putDbData("boards", boardManager.exportAll());
    }

    /**
     * 更新玩家分数。分数不变也会刷新 updateTime（同分时按先达成者优先）。
     */
    @RpcMethod
    public void updateRank(int rankType, String humanId, long score) {
        RankType type = RankType.of(rankType);
        if (type == null) {
            LogCore.GlobalServer.warn("updateRank ignored, unknown rankType = {}", rankType);
            return;
        }
        RankBoard board = boardManager.getBoard(rankType);
        if (board == null) {
            LogCore.GlobalServer.warn("updateRank ignored, board not registered, rankType = {}", rankType);
            return;
        }
        board.update(humanId, score);
    }

    /**
     * 将玩家移出指定榜（封号、数据清理等场景）
     */
    @RpcMethod
    public void removeFromRank(int rankType, String humanId) {
        RankBoard board = boardManager.getBoard(rankType);
        if (board != null) {
            board.remove(humanId);
        }
    }

    /**
     * 分页查询排行榜。
     * <p>
     * 返回 {@code protoData}（{@link RankProto.MS2C_GetRankList} 序列化字节），
     * 由 Game 服直接转发给客户端。
     * <p>
     * 因需 RPC 拉取玩家展示信息，回调中通过 {@code returns(fromCall, ...)} 响应，
     */
    @RpcMethod
    public void getRankList(int rankType, int page, int pageSize) {
        Call fromCall = CallContext.getLastCall();
        try {
            RankBoard board = boardManager.requireBoard(rankType);
            int safePage = Math.max(page, 1);
            int safePageSize = normalizePageSize(pageSize);

            List<SimpleRankEntry> allEntries = board.getTop(board.getRankType().getMaxSize());
            int totalCount = allEntries.size();
            int fromIndex = (safePage - 1) * safePageSize;
            final List<SimpleRankEntry> pageEntries;
            if (fromIndex < totalCount) {
                int toIndex = Math.min(fromIndex + safePageSize, totalCount);
                pageEntries = new ArrayList<>(allEntries.subList(fromIndex, toIndex));
            } else {
                pageEntries = List.of();
            }

            final int startRank = fromIndex + 1;
            List<String> humanIds = pageEntries.stream()
                    .map(SimpleRankEntry::getHumanId)
                    .distinct()
                    .collect(Collectors.toList());
            fetchPlayerInfos(humanIds, playerInfos -> {
                RankProto.MS2C_GetRankList response = buildRankListPage(
                        rankType, pageEntries, totalCount, startRank, playerInfos);
                returns(fromCall, "protoData", response.toByteArray());
            });
        } catch (IllegalArgumentException | IllegalStateException e) {
            LogCore.GlobalServer.warn("getRankList failed, rankType = {}, reason = {}", rankType, e.getMessage());
            returns(fromCall, "protoData",
                    RankProto.MS2C_GetRankList.newBuilder().setRankType(rankType).build().toByteArray());
        }
    }

    /**
     * 查询指定玩家的名次与分数。
     * 未上榜时 rank=0，不含 my_entry。
     */
    @RpcMethod
    public void getMyRank(int rankType, String humanId) {
        Call fromCall = CallContext.getLastCall();
        try {
            RankBoard board = boardManager.requireBoard(rankType);
            int rank = board.getRank(humanId);
            SimpleRankEntry entry = board.getEntry(humanId);
            if (entry == null) {
                RankProto.MS2C_GetMyRank response = buildMyRank(rankType, rank, null, Map.of());
                returns(fromCall, "protoData", response.toByteArray());
                return;
            }
            fetchPlayerInfos(List.of(humanId), playerInfos -> {
                RankProto.MS2C_GetMyRank response = buildMyRank(rankType, rank, entry, playerInfos);
                returns(fromCall, "protoData", response.toByteArray());
            });
        } catch (IllegalArgumentException | IllegalStateException e) {
            LogCore.GlobalServer.warn("getMyRank failed, rankType = {}, humanId = {}, reason = {}", rankType, humanId, e.getMessage());
            returns(fromCall, "protoData",
                    RankProto.MS2C_GetMyRank.newBuilder().setRankType(rankType).build().toByteArray());
        }
    }

    /**
     * 通过 RPC 批量查询玩家展示信息，与 GlobalPlayerInfoService 解耦
     */
    private void fetchPlayerInfos(List<String> humanIds, Consumer<Map<String, PlayerInfo>> callback) {
        if (humanIds.isEmpty()) {
            callback.accept(Map.of());
            return;
        }
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalPlayerInfoService_getPlayerInfos, "humanIds", humanIds);
        rpcFunction.listenResult(rpcResult -> {
            Map<String, PlayerInfo> playerInfos = new HashMap<>();
            if (rpcResult.getResult() == ErrorType.SUCCESS) {
                String playerInfosJson = (String) rpcResult.getData("playerInfosJson");
                if (playerInfosJson != null && !playerInfosJson.isEmpty()) {
                    playerInfos = JSON.parseObject(playerInfosJson, new TypeReference<Map<String, PlayerInfo>>() {
                    });
                }
            }
            callback.accept(playerInfos);
        });
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    // ==================== Protobuf 组装 ====================

    private static RankProto.MS2C_GetRankList buildRankListPage(int rankType, List<SimpleRankEntry> entries, int totalCount,
                                                                int startRank, Map<String, PlayerInfo> playerInfos) {
        RankProto.MS2C_GetRankList.Builder builder = RankProto.MS2C_GetRankList.newBuilder()
                .setRankType(rankType)
                .setTotalCount(totalCount);
        int rank = startRank;
        for (SimpleRankEntry entry : entries) {
            builder.addEntries(buildRankEntry(rank++, entry, playerInfos));
        }
        return builder.build();
    }

    private static RankProto.MS2C_GetMyRank buildMyRank(int rankType, int rank, SimpleRankEntry entry,
                                                        Map<String, PlayerInfo> playerInfos) {
        RankProto.MS2C_GetMyRank.Builder builder = RankProto.MS2C_GetMyRank.newBuilder()
                .setRankType(rankType)
                .setRank(rank);
        if (entry != null) {
            builder.setMyEntry(buildRankEntry(rank, entry, playerInfos));
        }
        return builder.build();
    }

    private static RankProto.STRankEntry buildRankEntry(int rank, SimpleRankEntry entry, Map<String, PlayerInfo> playerInfos) {
        RankProto.STRankEntry.Builder builder = RankProto.STRankEntry.newBuilder()
                .setRank(rank)
                .setScore(entry.getScore());
        PlayerInfo playerInfo = playerInfos.get(entry.getHumanId());
        if (playerInfo != null) {
            builder.setPlayer(toRankPlayerInfo(playerInfo));
        } else {
            // 玩家信息尚未同步到 Global，仅返回 humanId
            builder.setPlayer(RankProto.STRankPlayerInfo.newBuilder().setHumanId(entry.getHumanId()).build());
        }
        return builder.build();
    }

    private static RankProto.STRankPlayerInfo toRankPlayerInfo(PlayerInfo info) {
        RankProto.STRankPlayerInfo.Builder builder = RankProto.STRankPlayerInfo.newBuilder()
                .setHumanId(info.getHumanId())
                .setName(info.getName() != null ? info.getName() : "")
                .setLevel(info.getLevel())
                .setSex(info.getSex())
                .setFightPower(info.getFightPower());
        if (info.getHeadIcon() != null) {
            builder.setHeadIcon(info.getHeadIcon());
        }
        return builder.build();
    }
}
