package org.sunrise.game.core.message;

import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.thread.DispatchThread;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 消息管理器基类
 * 需实现接收与发送逻辑
 */
@Getter
@Setter
public abstract class BaseMessageManager {
    private final String nodeId;
    private AtomicBoolean running = new AtomicBoolean(false);
    private DispatchThread dispatchThread;
    private ConcurrentLinkedQueue<Object> recvMsgQueue = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<Object> sendMsgQueue = new ConcurrentLinkedQueue<>();

    protected BaseMessageManager(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * 一次心跳处理接收到的所有数据
     */
    public void pulseHandler() {
        while (!recvMsgQueue.isEmpty()) {
            Object data = recvMsgQueue.poll();
            if (data == null) {
                continue;
            }
            pulseHandlerOne(data);
        }
    }

    /**
     * 一次心跳处理要发送到对端的所有数据
     */
    public void pulseSender() {
        while (!sendMsgQueue.isEmpty()) {
            Object data = sendMsgQueue.poll();
            if (data == null) {
                continue;
            }
            pulseSenderOne(data);
        }
    }

    /**
     * 单个数据的处理
     */
    protected abstract void pulseHandlerOne(Object data);

    /**
     * 单个数据的处理
     */
    protected abstract void pulseSenderOne(Object data);

    public void recvMsg(Object data) {
        if (data != null) {
            recvMsgQueue.add(data);
        }
    }

    public void sendMsg(Object data) {
        if (data != null) {
            sendMsgQueue.add(data);
        }
    }

    /**
     * 基类心跳接口
     */
    public void pulse() {
        try {
            pulseHandler();
            pulseSender();
        } catch (Exception e) {
            LogCore.BaseServer.error("DispatchThread pulse, error : ", e);
        }
    }

    public void run() {
        if (!running.get()) {
            running.set(true);
            String handlerName = this.getClass().getSimpleName();
            dispatchThread = new DispatchThread(this::pulse, handlerName);
            dispatchThread.setInterval(5);
            dispatchThread.start();
        } else {
            LogCore.ServerStartUp.warn("DispatchThread Start Failed, name = { {} }, reason = { {} })", this.getClass().getSimpleName(), "repeat run");
        }
    }

}
