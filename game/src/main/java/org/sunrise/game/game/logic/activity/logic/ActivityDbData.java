package org.sunrise.game.game.logic.activity.logic;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 每个活动要存储的数据
 */
@Data
public class ActivityDbData {
    private int status; //状态：0-未开启，1-进行中，2-已结束
    private int activityId; //活动id
    private Map<String, String> dataMap = new HashMap<>(); //每个活动要存储的数据
}
