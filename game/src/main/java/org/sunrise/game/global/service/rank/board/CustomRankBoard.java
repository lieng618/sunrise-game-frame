package org.sunrise.game.global.service.rank.board;

import org.sunrise.game.global.service.rank.GlobalRankService;
import org.sunrise.game.global.service.rank.RankType;

/**
 * 非单分数比大小的特殊榜继承此类，并在 {@link GlobalRankService#init()} 中注册
 */
public abstract class CustomRankBoard implements RankBoard {
    protected final RankType rankType;

    protected CustomRankBoard(RankType rankType) {
        if (!rankType.isCustom()) {
            throw new IllegalArgumentException("CustomRankBoard requires custom rank type: " + rankType);
        }
        this.rankType = rankType;
    }

    @Override
    public RankType getRankType() {
        return rankType;
    }
}