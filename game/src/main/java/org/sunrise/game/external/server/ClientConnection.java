package org.sunrise.game.external.server;

import io.netty.channel.Channel;
import lombok.Data;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;

import java.util.LinkedList;

@Data
public class ClientConnection {
    private long id;// 连接id
    private Channel channel;
    private int msgCount = 0;// 累积消息数量
    private long msgCountStartTime = 0L;// 累积消息计数开始时间
    private static final long MSG_COUNT_RESET_INTERVAL = 60 * 1000L; // 消息计数重置间隔（毫秒）
    private final LinkedList<byte[]> msgQueue = new LinkedList<>(); //待发送消息队列
    private boolean firstSend; // 是否为首次发消息
    private String gameNodeId; //记录此玩家当前在哪个game服

    public ClientConnection(Channel channel) {
        this.channel = channel;
    }

    public boolean dataCheck(byte[] data) {
        // 检查消息大小
        if (data.length > Utils.MSG_BYTE_LEN_MAX) {
            LogCore.BaseServer.error("recv from client, msg too large, connectionId = {}, size = {}, max = {}",
                id, data.length, Utils.MSG_BYTE_LEN_MAX);
            return false;
        }

        long now = System.currentTimeMillis();
        
        // 如果计数器为0，初始化开始时间
        if (msgCount == 0) {
            msgCountStartTime = now;
        }
        
        // 增加计数
        msgCount++;
        
        // 检查是否超过限制
        if (msgCount > Utils.MSG_COUNT_MAX) {
            long elapsed = now - msgCountStartTime;
            if (elapsed < MSG_COUNT_RESET_INTERVAL) {
                // 在时间窗口内超过限制，拒绝消息并记录错误
                LogCore.BaseServer.error("recv from client, msg too many, connectionId = {}, count = {}, elapsed = {}ms",
                    id, msgCount, elapsed);
                // 重置计数器，拒绝当前消息
                resetCounter();
                return false;
            } else {
                // 超过时间窗口，重置计数器（正常情况）
                resetCounter();
            }
        }

        return true;
    }
    
    /**
     * 重置消息计数器
     */
    private void resetCounter() {
        msgCount = 0;
        msgCountStartTime = 0L;
    }
}
