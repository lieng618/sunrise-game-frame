package org.sunrise.game.game.logic.combat;

import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.config.Enum.AttributeType;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.logic.map.GameMap;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.logic.system.MapSystem;
import org.sunrise.game.game.logic.unit.GameUnit;
import org.sunrise.game.game.logic.unit.MonsterUnit;
import org.sunrise.game.game.logic.unit.PlayerUnit;
import org.sunrise.game.game.logic.unit.UnitType;
import org.sunrise.game.game.modules.PlayerUnitModule;
import org.sunrise.game.genProto.gen.BattleProto;
import org.sunrise.game.genProto.gen.TopicProto;

@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_BATTLE_VALUE)
public class BattleMsgHandler {

    /**
     * 玩家攻击目标单位（当前仅支持攻击怪物）。
     */
    @MsgHandlerMethod(packetId = BattleProto.FROM_CLIENT.C2S_Attack_VALUE)
    public static void attack(HumanObject humanObject, BattleProto.MC2S_Attack data) {
        if (data == null || data.getDefenderUnitId().isEmpty()) {
            return;
        }

        String defenderUnitId = data.getDefenderUnitId();
        PlayerUnitModule unitModule = humanObject.getModule(PlayerUnitModule.class);
        int mapId = unitModule.getMapId();
        if (mapId == 0) {
            return;
        }

        MapSystem mapSystem = GameSystemUtils.getSystem(MapSystem.class);
        GameMap gameMap = mapSystem.getMap(mapId);
        if (gameMap == null) {
            return;
        }

        PlayerUnit attacker = gameMap.getPlayer(humanObject.getHumanId());
        if (attacker == null) {
            return;
        }

        GameUnit defender = gameMap.getUnit(defenderUnitId);
        if (defender == null || defender.getUnitType() != UnitType.MONSTER) {
            return;
        }

        MonsterUnit monster = (MonsterUnit) defender;
        if (!monster.isAlive()) {
            return;
        }

        if (!CombatUtils.isInAttackRange(attacker, defender)) {
            humanObject.sendTips("距离太远，无法攻击");
            return;
        }

        gameMap.broadcastAttack(attacker.getUnitId(), defenderUnitId);

        long damage = CombatUtils.calculateDamage(attacker, defender);
        var changed = CombatUtils.applyDamage(defender, damage);
        if (!changed.isEmpty()) {
            gameMap.broadcastUnitAttributeUpdate(defenderUnitId, changed);
        }
        gameMap.broadcastDamage(attacker.getUnitId(), defenderUnitId, damage);

        if (monster.getAttributeContainer().getFinalValue(AttributeType.HP) <= 0) {
            monster.die(humanObject.getHumanId());
        }
    }
}
