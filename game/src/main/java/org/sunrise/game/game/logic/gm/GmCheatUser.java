package org.sunrise.game.game.logic.gm;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 金手指用户授权记录。
 * uid 和 humanId 二选一，uid 授权到账号，humanId 授权到单个角色。
 */
@Data
public class GmCheatUser {
    private String uid;
    private String humanId;
    private List<String> abilities = new ArrayList<>();
    private String remark;
    private Long addTime;
    private Long updateTime;
}
