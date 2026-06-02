package org.sunrise.game.global.service.rank.board;

import org.sunrise.game.global.service.rank.SimpleRankEntry;
import org.sunrise.game.global.service.rank.RankType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 常规单分数榜：TreeSet 维护有序 TopN，entryMap 支持 O(1) 按 humanId 更新/删除
 */
public class SimpleRankBoard implements RankBoard {
    private final RankType rankType;
    private final Map<String, SimpleRankEntry> entryMap = new HashMap<>();
    /** first=第一名，last=最后一名 */
    private final TreeSet<SimpleRankEntry> sortedEntries;

    SimpleRankBoard(RankType rankType) {
        if (rankType.isCustom()) {
            throw new IllegalArgumentException("SimpleRankBoard does not support custom rank type: " + rankType);
        }
        this.rankType = rankType;
        this.sortedEntries = new TreeSet<>(createEntryComparator(rankType.isDescending()));
    }

    @Override
    public RankType getRankType() {
        return rankType;
    }

    @Override
    public void update(String humanId, long score) {
        remove(humanId);
        SimpleRankEntry entry = new SimpleRankEntry(humanId, score, System.currentTimeMillis());
        entryMap.put(humanId, entry);
        sortedEntries.add(entry);
        trimToMaxSize();
    }

    @Override
    public void remove(String humanId) {
        SimpleRankEntry old = entryMap.remove(humanId);
        if (old != null) {
            sortedEntries.remove(old);
        }
    }

    @Override
    public List<SimpleRankEntry> getTop(int limit) {
        List<SimpleRankEntry> result = new ArrayList<>(Math.min(limit, sortedEntries.size()));
        int count = 0;
        for (SimpleRankEntry entry : sortedEntries) {
            result.add(entry);
            if (++count >= limit) {
                break;
            }
        }
        return result;
    }

    @Override
    public int getRank(String humanId) {
        if (!entryMap.containsKey(humanId)) {
            return 0;
        }
        int rank = 1;
        for (SimpleRankEntry entry : sortedEntries) {
            if (entry.getHumanId().equals(humanId)) {
                return rank;
            }
            rank++;
        }
        return 0;
    }

    @Override
    public SimpleRankEntry getEntry(String humanId) {
        return entryMap.get(humanId);
    }

    @Override
    public List<SimpleRankEntry> exportEntries() {
        return new ArrayList<>(sortedEntries);
    }

    @Override
    public void importEntries(List<SimpleRankEntry> entries) {
        entryMap.clear();
        sortedEntries.clear();
        if (entries == null) {
            return;
        }
        for (SimpleRankEntry entry : entries) {
            if (entry == null || entry.getHumanId() == null || entry.getHumanId().isEmpty()) {
                continue;
            }
            entryMap.put(entry.getHumanId(), entry);
            sortedEntries.add(entry);
        }
        trimToMaxSize();
    }

    /**
     * 超出 RankType.maxSize 时淘汰榜末
     */
    private void trimToMaxSize() {
        while (sortedEntries.size() > rankType.getMaxSize()) {
            SimpleRankEntry last = sortedEntries.last();
            sortedEntries.remove(last);
            entryMap.remove(last.getHumanId());
        }
    }

    /** 排序规则：分数 → 达成时间 → humanId（保证 TreeSet 元素唯一） */
    private Comparator<SimpleRankEntry> createEntryComparator(boolean descending) {
        return (a, b) -> {
            int cmp = Long.compare(a.getScore(), b.getScore());
            if (descending) {
                cmp = -cmp;
            }
            if (cmp != 0) {
                return cmp;
            }
            cmp = Long.compare(a.getUpdateTime(), b.getUpdateTime());
            if (cmp != 0) {
                return cmp;
            }
            return a.getHumanId().compareTo(b.getHumanId());
        };
    }
}