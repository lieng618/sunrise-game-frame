package org.sunrise.game.game.logic.playerinfo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 玩家简略信息
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlayerInfo {
    private String humanId;      // 玩家ID
    private String name;          // 玩家名称
    private int level;            // 等级
    private String headIcon;      // 头像
    private int sex;              // 性别
    private int fightPower;       // 战斗力
}
