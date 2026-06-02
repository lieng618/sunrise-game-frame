package org.sunrise.game.global.service.rank.board;

import org.sunrise.game.global.service.rank.SimpleRankEntry;
import org.sunrise.game.global.service.rank.RankType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理所有 rankType 对应的榜实例及持久化导入导出
 */
public class BoardManager {
    private final Map<Integer, RankBoard> boards = new HashMap<>();

    public void registerDefaultBoards() {
        for (RankType type : RankType.values()) {
            if (!type.isCustom()) {
                register(type, new SimpleRankBoard(type));
            }
        }
    }

    private void register(RankType type, RankBoard board) {
        if (board.getRankType() != type) {
            throw new IllegalArgumentException("board rank type mismatch");
        }
        boards.put(type.getId(), board);
    }

    public RankBoard getBoard(int rankTypeId) {
        return boards.get(rankTypeId);
    }

    public RankBoard requireBoard(int rankTypeId) {
        RankType type = RankType.require(rankTypeId);
        RankBoard board = boards.get(rankTypeId);
        if (board == null) {
            throw new IllegalStateException("rank board not registered: " + type);
        }
        return board;
    }

    public Map<Integer, List<SimpleRankEntry>> exportAll() {
        Map<Integer, List<SimpleRankEntry>> data = new HashMap<>();
        for (Map.Entry<Integer, RankBoard> entry : boards.entrySet()) {
            data.put(entry.getKey(), entry.getValue().exportEntries());
        }
        return data;
    }

    public void importAll(Map<Integer, List<SimpleRankEntry>> data) {
        if (data == null) {
            return;
        }
        for (Map.Entry<Integer, List<SimpleRankEntry>> entry : data.entrySet()) {
            RankBoard board = boards.get(entry.getKey());
            if (board != null) {
                board.importEntries(entry.getValue());
            }
        }
    }
}