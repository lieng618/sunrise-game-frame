package org.sunrise.game.global.service.rank;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 排行榜条目
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SimpleRankEntry {
    private String humanId;
    private long score;
    /** 分数相同时，先达成者排在前面 */
    private long updateTime;
}
