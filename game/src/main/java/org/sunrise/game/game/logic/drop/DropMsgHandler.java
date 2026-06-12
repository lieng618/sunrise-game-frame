package org.sunrise.game.game.logic.drop;

import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.logic.map.GameMap;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.logic.system.MapSystem;
import org.sunrise.game.game.logic.unit.DropItemUnit;
import org.sunrise.game.game.logic.unit.PlayerUnit;
import org.sunrise.game.game.modules.ItemModule;
import org.sunrise.game.game.modules.PlayerUnitModule;
import org.sunrise.game.genProto.gen.DropProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.log.LogCore;

/**
 * 掉落物拾取消息处理。
 * <p>
 * 客户端发送 C2S_PickupDrop → 验证 → canAddItem → addItem → 移除掉落物。
 * 成功/失败均返回 MS2C_DropPickupResult；失败额外发送 chat tips 提示原因。
 */
@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_DROP_VALUE)
public class DropMsgHandler {

    private static final float PICKUP_RANGE = 5.0f;

    @MsgHandlerMethod(packetId = DropProto.FROM_CLIENT.C2S_PickupDrop_VALUE)
    public static void pickupDrop(HumanObject humanObject, DropProto.MC2S_PickupDrop data) {
        if (data == null || data.getUnitId().isEmpty()) {
            sendResult(humanObject, "", false);
            return;
        }

        String unitId = data.getUnitId();
        PlayerUnitModule unitModule = humanObject.getModule(PlayerUnitModule.class);
        int mapId = unitModule.getMapId();
        if (mapId == 0) {
            sendResult(humanObject, unitId, false);
            return;
        }

        GameMap gameMap = GameSystemUtils.getSystem(MapSystem.class).getMap(mapId);
        if (gameMap == null) {
            sendResult(humanObject, unitId, false);
            return;
        }

        DropItemUnit dropItem = gameMap.getDropItem(unitId);
        if (dropItem == null) {
            humanObject.sendTips("掉落物已被拾取");
            sendResult(humanObject, unitId, false);
            return;
        }

        // 拾取保护
        if (!dropItem.canPickup(humanObject.getHumanId())) {
            humanObject.sendTips("掉落物保护期内，请稍后再试");
            sendResult(humanObject, unitId, false);
            return;
        }

        // 过期
        if (dropItem.isExpired()) {
            gameMap.leaveUnit(unitId);
            humanObject.sendTips("掉落物已消失");
            sendResult(humanObject, unitId, false);
            return;
        }

        // 距离
        PlayerUnit playerUnit = gameMap.getPlayer(humanObject.getHumanId());
        if (playerUnit != null) {
            float dx = playerUnit.getPosition().getX() - dropItem.getPosition().getX();
            float dy = playerUnit.getPosition().getY() - dropItem.getPosition().getY();
            float dz = playerUnit.getPosition().getZ() - dropItem.getPosition().getZ();
            if (dx * dx + dy * dy + dz * dz > PICKUP_RANGE * PICKUP_RANGE) {
                humanObject.sendTips("距离太远，无法拾取");
                sendResult(humanObject, unitId, false);
                return;
            }
        }

        // 背包容量检查
        ItemModule itemModule = humanObject.getModule(ItemModule.class);
        if (!itemModule.canAddItem(dropItem.getItemId(), dropItem.getCount())) {
            humanObject.sendTips("背包已满");
            sendResult(humanObject, unitId, false);
            return;
        }

        // 移除掉落物
        gameMap.leaveUnit(unitId);

        // 添加物品
        itemModule.addItem(dropItem.getItemId(), dropItem.getCount(), true);

        // 成功
        sendResult(humanObject, unitId, true);

        LogCore.GameServer.debug("Drop picked up: unitId={}, itemId={}, count={}, humanId={}",
                unitId, dropItem.getItemId(), dropItem.getCount(), humanObject.getHumanId());
    }

    private static void sendResult(HumanObject humanObject, String unitId, boolean success) {
        var builder = DropProto.MS2C_DropPickupResult.newBuilder()
                .setUnitId(unitId)
                .setSuccess(success);
        humanObject.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_DROP_VALUE,
                DropProto.FROM_SERVER.S2C_DropPickupResult_VALUE, builder);
    }
}
