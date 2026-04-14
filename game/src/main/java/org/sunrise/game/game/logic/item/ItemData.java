package org.sunrise.game.game.logic.item;

import lombok.Getter;
import org.sunrise.game.game.config.Tables;

@Getter
public class ItemData {
    private final int itemId; //配置表id
    private int count; //拥有数量

    public ItemData(int itemId, int count) {
        this.itemId = itemId;
        this.count = count;
    }

    // 尝试增加数量，返回实际增加的数量
    public int addCount(int amount) {
        int space = Tables.ConfigParam.getItemMaxStack() - count;
        if (space <= 0) return 0;

        int toAdd = Math.min(space, amount);
        this.count += toAdd;
        return toAdd;
    }

    // 减少数量
    public void removeCount(int amount) {
        this.count -= amount;
    }
}
