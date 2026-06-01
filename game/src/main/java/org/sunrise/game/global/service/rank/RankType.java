package org.sunrise.game.global.service.rank;

import java.util.HashMap;
import java.util.Map;

/**
 * 排行榜类型，与 rank.proto 中 RANK_TYPE 枚举 id 保持一致。
 * <p>
 * custom=false 的榜由 {@link GlobalRankService} 自动创建；
 * custom=true 的榜需继承 {@link GlobalRankService.CustomRankBoard} 并在 init() 中注册。
 */
public enum RankType {
    FIGHT_POWER(1, 100, true, false),
    LEVEL(2, 100, true, false),
    SCORE(3, 100, true, false),
    ;

    private static final Map<Integer, RankType> BY_ID = new HashMap<>();

    static {
        for (RankType type : values()) {
            BY_ID.put(type.id, type);
        }
    }

    private final int id; // 排行榜类型 id
    private final int maxSize; // 最大上榜人数
    private final boolean descending; // 是否降序（分数越大越靠前）
    private final boolean custom; // 是否自定义

    RankType(int id, int maxSize, boolean descending, boolean custom) {
        this.id = id;
        this.maxSize = maxSize;
        this.descending = descending;
        this.custom = custom;
    }

    public int getId() {
        return id;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public boolean isDescending() {
        return descending;
    }

    public boolean isCustom() {
        return custom;
    }

    public static RankType of(int id) {
        return BY_ID.get(id);
    }

    public static RankType require(int id) {
        RankType type = of(id);
        if (type == null) {
            throw new IllegalArgumentException("unknown rank type: " + id);
        }
        return type;
    }
}
