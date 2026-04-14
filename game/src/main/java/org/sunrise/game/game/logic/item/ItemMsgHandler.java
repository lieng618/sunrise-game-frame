package org.sunrise.game.game.logic.item;

import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.modules.ItemModule;
import org.sunrise.game.genProto.gen.ItemProto;
import org.sunrise.game.genProto.gen.TopicProto;

@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_ITEM_VALUE)
public class ItemMsgHandler {
    
    /**
     * 获取背包列表
     */
    @MsgHandlerMethod(packetId = ItemProto.FROM_CLIENT.C2S_GetItemList_VALUE)
    public static void getItemList(HumanObject humanObject) {
        ItemModule module = humanObject.getModule(ItemModule.class);
        module.sendItemList();
    }
    
    /**
     * 使用物品
     */
    @MsgHandlerMethod(packetId = ItemProto.FROM_CLIENT.C2S_UseItem_VALUE)
    public static void useItem(HumanObject humanObject, ItemProto.MC2S_UseItem data) {
        ItemModule module = humanObject.getModule(ItemModule.class);
        int itemId = data.getItemId();
        int count = data.getCount() > 0 ? data.getCount() : 1;
        
        module.useItem(itemId, count);
    }
    
    /**
     * 删除物品（根据位置）
     */
    @MsgHandlerMethod(packetId = ItemProto.FROM_CLIENT.C2S_RemoveItem_VALUE)
    public static void removeItem(HumanObject humanObject, ItemProto.MC2S_RemoveItem data) {
        ItemModule module = humanObject.getModule(ItemModule.class);
        int index = data.getIndex();
        int count = data.getCount() > 0 ? data.getCount() : 1;
        
        module.removeByIndex(index, count);
    }
    
    /**
     * 删除物品（根据物品ID）
     */
    @MsgHandlerMethod(packetId = ItemProto.FROM_CLIENT.C2S_RemoveItemById_VALUE)
    public static void removeItemById(HumanObject humanObject, ItemProto.MC2S_RemoveItemById data) {
        ItemModule module = humanObject.getModule(ItemModule.class);
        int itemId = data.getItemId();
        int count = data.getCount() > 0 ? data.getCount() : 1;
        
        module.removeByItemId(itemId, count);
    }
    
    /**
     * 整理背包（空消息）
     */
    @MsgHandlerMethod(packetId = ItemProto.FROM_CLIENT.C2S_SortItem_VALUE)
    public static void sortItem(HumanObject humanObject) {
        ItemModule module = humanObject.getModule(ItemModule.class);
        module.sortItem();
    }
    
    /**
     * 出售物品
     */
    @MsgHandlerMethod(packetId = ItemProto.FROM_CLIENT.C2S_SellItem_VALUE)
    public static void sellItem(HumanObject humanObject, ItemProto.MC2S_SellItem data) {
        ItemModule module = humanObject.getModule(ItemModule.class);
        int itemId = data.getItemId();
        int count = data.getCount() > 0 ? data.getCount() : 1;
        
        module.sellItem(itemId, count);
    }
}
