package org.sunrise.game.game.logic.attribute;

import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.modules.AttributeModule;
import org.sunrise.game.genProto.gen.AttributeProto;
import org.sunrise.game.genProto.gen.TopicProto;

/**
 * 属性消息处理器
 * 处理客户端发送的属性相关请求
 * 协议类型：TOPIC_TYPE_ATTRIBUTE
 */
@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_ATTRIBUTE_VALUE)
public class AttributeMsgHandler {

    /**
     * 处理客户端请求获取属性列表
     * 返回玩家当前所有属性数据
     */
    @MsgHandlerMethod(packetId = AttributeProto.FROM_CLIENT.C2S_GetAttributeList_VALUE)
    public static void getAttributeList(HumanObject humanObject) {
        AttributeModule attributeModule = humanObject.getModule(AttributeModule.class);
        attributeModule.sendToClient();
    }
}
