package org.sunrise.game.game.modules;

import com.alibaba.fastjson2.TypeReference;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.annotation.HumanModule;
import org.sunrise.game.game.config.Enum.TaskType;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.item.TbItem;
import org.sunrise.game.game.logic.item.ItemData;
import org.sunrise.game.game.logic.task.TaskEventManager;
import org.sunrise.game.genProto.gen.ItemProto;
import org.sunrise.game.genProto.gen.TopicProto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@HumanModule
@Getter
@Setter
public class ItemModule extends BaseModule {
    // 物品顺序，每个索引就是一个背包格子
    private List<ItemData> itemsList = new ArrayList<>();
    // ItemID-拥有该ID的物品对象列表：某个物品id对应的所有格子
    private Map<Integer, List<ItemData>> itemsById = new HashMap<>();

    public ItemModule(String humanId) {
        super(humanId);
    }

    @Override
    public void init() {
        ArrayList<TbItem> dataList = Tables.ConfigItem.getDataList();
        for (TbItem item : dataList) {
            addItem(item.id, 1, false);
        }
    }

    @Override
    public void load() {
        getDbData("items", new TypeReference<List<ItemData>>() {
        }, value -> {
            if (value != null) {
                this.itemsList = value;
                rebuildItemMap();
            }
        });
    }

    /**
     * 根据 List 重建 Map 索引
     */
    private void rebuildItemMap() {
        this.itemsById.clear();
        for (ItemData item : this.itemsList) {
            this.itemsById.computeIfAbsent(item.getItemId(), k -> new ArrayList<>()).add(item);
        }
    }

    @Override
    public void save() {
        putDbData("items", itemsList);
    }

    @Override
    public void sendToClient() {
        sendItemList();
    }

    /**
     * 发送背包列表给客户端
     */
    public void sendItemList() {
        ItemProto.MS2C_ItemList.Builder builder = ItemProto.MS2C_ItemList.newBuilder();
        builder.setCapacity(Tables.ConfigParam.getItemBoxCapacity());

        for (int i = 0; i < itemsList.size(); i++) {
            ItemData item = itemsList.get(i);
            ItemProto.STItemInfo itemInfo = ItemProto.STItemInfo.newBuilder()
                    .setItemId(item.getItemId())
                    .setCount(item.getCount())
                    .setIndex(i)
                    .build();
            builder.addItems(itemInfo);
        }

        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_ITEM_VALUE,
                ItemProto.FROM_SERVER.S2C_ItemList_VALUE, builder);
    }

    /**
     * 通知客户端物品更新（支持多个位置变化）
     */
    public void notifyItemUpdate(Set<Integer> indexes) {
        if (indexes == null || indexes.isEmpty()) {
            return;
        }

        ItemProto.MS2C_ItemUpdate.Builder builder = ItemProto.MS2C_ItemUpdate.newBuilder();

        for (int index : indexes) {
            if (index >= 0 && index < itemsList.size()) {
                ItemData item = itemsList.get(index);
                ItemProto.STItemInfo itemInfo = ItemProto.STItemInfo.newBuilder()
                        .setItemId(item.getItemId())
                        .setCount(item.getCount())
                        .setIndex(index)
                        .build();
                builder.addItems(itemInfo);
            }
        }

        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_ITEM_VALUE,
                ItemProto.FROM_SERVER.S2C_ItemUpdate_VALUE, builder);
    }

    /**
     * 添加物品
     *
     * @param notifyItemUpdate 是否需要通知变动
     */
    public void addItem(int itemId, int count, boolean notifyItemUpdate) {
        // 1.尝试堆叠到现有格子
        Set<Integer> changeIndexes = new HashSet<>();
        List<ItemData> sameIdItems = itemsById.get(itemId);
        if (sameIdItems != null && !sameIdItems.isEmpty()) {
            for (ItemData item : sameIdItems) {
                int index = itemsList.indexOf(item);
                int added = item.addCount(count);
                count -= added;
                changeIndexes.add(index);
                if (count <= 0) break; // 全部放入堆叠
            }
        }

        // 2.处理剩余数量 (循环开新格子)
        while (count > 0) {
            // 检查背包是否已满
            if (itemsList.size() >= Tables.ConfigParam.getItemBoxCapacity()) {
                return;
            }

            // 计算当前这个新格子能放多少 (取 剩余量 和 堆叠上限 的较小值)
            int numToAdd = Math.min(count, Tables.ConfigParam.getItemMaxStack());

            // 创建新物品
            ItemData newItem = new ItemData(itemId, numToAdd);
            itemsList.add(newItem);
            itemsById.computeIfAbsent(itemId, k -> new ArrayList<>()).add(newItem);

            // 记录新添加的位置
            changeIndexes.add(itemsList.size() - 1);

            // 扣除已放入的数量
            count -= numToAdd;
        }

        // 通知所有变动的格子
        if (!changeIndexes.isEmpty() && notifyItemUpdate) {
            notifyItemUpdate(changeIndexes);
        }

    }

    /**
     * 根据id移除物品,需要先判断物品是否足够
     */
    public void removeByItemId(int itemId, int count) {
        List<ItemData> targetItems = itemsById.get(itemId);
        if (targetItems == null || targetItems.isEmpty()) {
            return;
        }

        boolean hasRemoved = false;
        Set<Integer> affectedIndexes = new HashSet<>();

        Iterator<ItemData> it = targetItems.iterator();
        while (it.hasNext() && count > 0) {
            ItemData item = it.next();
            int index = itemsList.indexOf(item);
            if (index < 0) continue;
            affectedIndexes.add(index);

            int currentHas = item.getCount();
            if (currentHas > count) {
                // 数量够扣，只减数，不移除
                item.removeCount(count);
                count = 0;
            } else {
                // 数量不够或刚好，扣光并移除
                count -= currentHas;
                hasRemoved = true;

                it.remove();
                itemsList.remove(item);
            }
        }

        if (targetItems.isEmpty()) {
            itemsById.remove(itemId);
        }

        // 如果有物品被删除，发送背包列表
        if (hasRemoved) {
            sendItemList();
        } else {
            // 只通知受影响的位置
            notifyItemUpdate(affectedIndexes);
        }
    }

    /**
     * 根据位置移除物品,只会删除此位置的物品
     */
    public void removeByIndex(int index, int count) {
        if (index < 0 || index >= itemsList.size()) return;

        ItemData item = itemsList.get(index);
        if (item.getCount() < count) {
            return;
        }
        Set<Integer> affectedIndexes = new HashSet<>();
        affectedIndexes.add(index);

        item.removeCount(count);

        if (item.getCount() == 0) {
            itemsList.remove(index);
            List<ItemData> cache = itemsById.get(item.getItemId());
            if (cache != null) {
                cache.remove(item);
                if (cache.isEmpty()) {
                    itemsById.remove(item.getItemId());
                }
            }
            // 如果有物品被删除，发送背包列表
            sendItemList();
        } else {
            // 只通知受影响的位置
            notifyItemUpdate(affectedIndexes);
        }
    }

    /**
     * 检查背包中是否有足够的指定物品
     */
    public boolean hasItem(int itemId, int requireCount) {
        if (requireCount <= 0) return true;

        List<ItemData> items = itemsById.get(itemId);
        if (items == null || items.isEmpty()) {
            return false;
        }

        long total = 0;
        for (ItemData item : items) {
            total += item.getCount();
            if (total >= requireCount) {
                return true;
            }
        }

        return total >= requireCount;
    }

    /**
     * 检查背包是否能容纳指定数量的物品。
     *
     * @return true 表示可以完全放入
     */
    public boolean canAddItem(int itemId, int count) {
        if (count <= 0) return true;

        int remaining = count;
        int maxStack = Tables.ConfigParam.getItemMaxStack();

        // 先扣现有堆叠的可堆叠空间
        List<ItemData> sameIdItems = itemsById.get(itemId);
        if (sameIdItems != null) {
            for (ItemData item : sameIdItems) {
                int space = maxStack - item.getCount();
                if (space > 0) {
                    remaining -= space;
                    if (remaining <= 0) return true;
                }
            }
        }

        // 需要开新格子
        int slotsNeeded = (int) Math.ceil((double) remaining / maxStack);
        int emptySlots = Tables.ConfigParam.getItemBoxCapacity() - itemsList.size();
        return slotsNeeded <= emptySlots;
    }

    /**
     * 整理背包,自动合并堆叠
     */
    public void sortItem() {
        if (itemsList.isEmpty()) return;

        // 先按itemId升序排序，ID小的排前面
        itemsList.sort((a, b) -> {
            int idCompare = Integer.compare(a.getItemId(), b.getItemId());
            if (idCompare != 0) {
                return idCompare; // itemId升序
            }
            // itemId相同，按count降序
            return Integer.compare(b.getCount(), a.getCount());
        });

        List<ItemData> mergedList = new ArrayList<>(itemsList.size());
        ItemData currentStack = null;

        for (ItemData item : itemsList) {
            if (currentStack != null && currentStack.getItemId() == item.getItemId()) {
                int added = currentStack.addCount(item.getCount());
                item.removeCount(added);
            }

            if (item.getCount() > 0) {
                mergedList.add(item);
                currentStack = item;
            }
        }

        this.itemsList = mergedList;
        rebuildItemMap();
        // 发送背包列表
        sendItemList();
    }

    /**
     * 使用物品（根据itemId）
     */
    public void useItem(int itemId, int count) {
        if (!hasItem(itemId, count)) {
            return;
        }

        // TODO: 实现物品使用逻辑（根据物品类型执行不同操作）
        // 这里可以扩展：消耗品、装备、材料等不同类型的使用逻辑

        // 使用后减少数量
        removeByItemId(itemId, count);

        // 触发任务事件：使用物品
        TaskEventManager.triggerEvent(getHuman(), TaskType.ITEM_USE, count, itemId);
    }

    /**
     * 出售物品（根据itemId）
     */
    public void sellItem(int itemId, int count) {
        if (!hasItem(itemId, count)) {
            return;
        }

        // TODO: 实现出售逻辑（获取金币等）
        // 这里需要根据物品配置表获取价格，然后给玩家增加金币

        // 移除物品
        removeByItemId(itemId, count);
    }
}
