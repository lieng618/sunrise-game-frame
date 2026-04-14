package org.sunrise.game.game.logic.friend;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 好友申请数据
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestData {
    private String applicantHumanId;  // 申请人ID
    private long requestTime;        // 申请时间（时间戳）
}
