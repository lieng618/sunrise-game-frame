package org.sunrise.game.game.logic.drop;

import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.drop.TbDrop;
import org.sunrise.game.game.config.drop.TbDropGroup;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 掉落两级随机计算器。
 *
 * <pre>
 *   怪物 dropId → TbDropGroup → 根据 type 对 dropIds 做随机：
 *     type = 1 (PICK_ONE)：等概率抽一个 id，再按该项 weight 判掉
 *     type = 2 (ROLL_ALL)：每个 id 独立按自己的 weight 判掉
 * </pre>
 */
public final class DropCalculator {
    public static final int TYPE_PICK_ONE = 1;
    public static final int TYPE_ROLL_ALL = 2;

    private DropCalculator() {}

    /**
     * 根据掉落组 ID 计算掉落结果。
     *
     * @param dropGroupId 掉落组 ID（TbDropGroup.id，由 TbMonster.dropId 引用）
     * @return 掉落结果列表（可能为空）
     */
    public static List<DropResult> roll(int dropGroupId) {
        if (dropGroupId <= 0) return Collections.emptyList();

        TbDropGroup group = Tables.ConfigDropGroup.get(dropGroupId);
        if (group == null || group.dropIds.isEmpty()) return Collections.emptyList();

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        List<DropResult> results = new ArrayList<>();

        switch (group.type) {
            case TYPE_PICK_ONE -> {
                int idx = rand.nextInt(group.dropIds.size());
                TbDrop drop = Tables.ConfigDrop.get(group.dropIds.get(idx));
                if (drop != null && rollHit(drop.weight, rand)) {
                    results.add(new DropResult(drop.itemId, randomCount(drop, rand)));
                }
            }
            case TYPE_ROLL_ALL -> {
                for (int dropId : group.dropIds) {
                    TbDrop drop = Tables.ConfigDrop.get(dropId);
                    if (drop != null && rollHit(drop.weight, rand)) {
                        results.add(new DropResult(drop.itemId, randomCount(drop, rand)));
                    }
                }
            }
        }
        return results;
    }

    private static boolean rollHit(int weight, ThreadLocalRandom rand) {
        if (weight >= 10000) return true;
        if (weight <= 0) return false;
        return rand.nextInt(1, 10001) <= weight;
    }

    private static int randomCount(TbDrop drop, ThreadLocalRandom rand) {
        int min = drop.countMin;
        int max = drop.countMax;
        if (min == max) return min;
        if (min > max) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        return rand.nextInt(min, max + 1);
    }

    /** 单次掉落结果 */
    public record DropResult(int itemId, int count) {}
}
