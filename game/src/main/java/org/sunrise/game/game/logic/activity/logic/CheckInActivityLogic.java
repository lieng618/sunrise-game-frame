package org.sunrise.game.game.logic.activity.logic;

import com.alibaba.fastjson2.TypeReference;
import com.google.protobuf.ByteString;
import org.sunrise.game.game.logic.ProtoParserUtils;
import org.sunrise.game.genProto.gen.ActivityProto;

import java.util.HashSet;
import java.util.Set;

public class CheckInActivityLogic extends BaseActivityLogic {

    private Set<Integer> days = new HashSet<>();

    public CheckInActivityLogic(int activityId, String humanId) {
        super(activityId, humanId);
    }

    @Override
    public void load() {
        getDbData("days", new TypeReference<Set<Integer>>() {
        }, value -> {
            if (value != null) {
                this.days = value;
            }
        });
    }

    @Override
    public void save() {
        putDbData("days", days);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onEnd() {
        super.onEnd();
    }

    @Override
    public void onAction(int actionId, ByteString data) {
        if (actionId == 2) {
            ActivityProto.MC2S_CheckInAction checkInAction = ProtoParserUtils.parseProto(data, ActivityProto.MC2S_CheckInAction.parser());
            if (checkInAction == null) {
                return;
            }
            days.add(checkInAction.getId());
        }
        ActivityProto.MS2C_CheckInInfo.Builder builder = ActivityProto.MS2C_CheckInInfo.newBuilder();
        builder.addAllIds(days);
        sendToClient(actionId, builder);
    }

}
