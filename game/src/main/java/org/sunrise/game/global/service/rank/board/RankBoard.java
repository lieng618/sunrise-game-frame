package org.sunrise.game.global.service.rank.board;

import org.sunrise.game.global.service.rank.SimpleRankEntry;
import org.sunrise.game.global.service.rank.RankType;

import java.util.List;

/**
 * 单个榜的读写接口
 */
public interface RankBoard {
    RankType getRankType();

    void update(String humanId, long score);

    void remove(String humanId);

    List<SimpleRankEntry> getTop(int limit);

    int getRank(String humanId);

    SimpleRankEntry getEntry(String humanId);

    List<SimpleRankEntry> exportEntries();

    void importEntries(List<SimpleRankEntry> entries);
}
