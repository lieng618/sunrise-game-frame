package org.sunrise.game.game.logic.data;

import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.modules.DataModule;
import org.sunrise.game.genProto.gen.HumanProto;
import org.sunrise.game.genProto.gen.TopicProto;

@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_HUMAN_VALUE)
public class HumanMsgHandler {
    
    /**
     * 修改名字
     */
    @MsgHandlerMethod(packetId = HumanProto.FROM_CLIENT.C2S_ChangeName_VALUE)
    public static void changeName(HumanObject humanObject, HumanProto.MC2S_ChangeName data) {
        DataModule dataModule = humanObject.getModule(DataModule.class);
        dataModule.changeName(data.getName());
    }
    
    /**
     * 修改头像
     */
    @MsgHandlerMethod(packetId = HumanProto.FROM_CLIENT.C2S_ChangeHeadIcon_VALUE)
    public static void changeHeadIcon(HumanObject humanObject, HumanProto.MC2S_ChangeHeadIcon data) {
        DataModule dataModule = humanObject.getModule(DataModule.class);
        dataModule.changeHeadIcon(data.getHeadIconId());
    }
    
    /**
     * 修改性别
     */
    @MsgHandlerMethod(packetId = HumanProto.FROM_CLIENT.C2S_ChangeSex_VALUE)
    public static void changeSex(HumanObject humanObject, HumanProto.MC2S_ChangeSex data) {
        DataModule dataModule = humanObject.getModule(DataModule.class);
        dataModule.changeSex(data.getSex());
    }
}