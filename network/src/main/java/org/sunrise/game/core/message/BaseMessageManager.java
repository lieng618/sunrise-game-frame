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
    /**
     * 入站队列
     **/
    private ConcurrentLinkedQueue<Object> recvMsgQueue = new ConcurrentLinkedQueue<>();
    /**
     * 出站队列
     **/
    private ConcurrentLinkedQueue<Object> sendMsgQueue = new ConcurrentLinkedQueue<>();

    protected BaseMessageManager(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * 一次心跳处理接收到的所有数据
     *
     * @return 本次实际 poll 并处理的条数
     */
    public int pulseHandler() {
        int processed = 0;
        while (!recvMsgQueue.isEmpty()) {
            Object data = recvMsgQueue.poll();
            if (data == null) {
                continue;
            }
            processed++;
            pulseHandlerOne(data);
        }
        return processed;
    }

    /**
     * 一次心跳处理要发送到对端的所有数据
     *
     * @return 本次实际发送条数
     */
    public int pulseSender() {
        int sent = 0;
        while (!sendMsgQueue.isEmpty()) {
            Object data = sendMsgQueue.poll();
            if (data == null) {
                continue;
            }
            sent++;
            pulseSenderOne(data);
        }
        return sent;
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

    /**
     * 停机时排空入站队列（阻塞当前线程），直到队列为空或超时。
     *
     * @param timeoutMs 最大等待毫秒数
     * @return 本次排空处理的消息条数
     */
    public int drainRecvQueue(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int processed = 0;
        while (System.currentTimeMillis() < deadline) {
            int batch = pulseHandler();
            processed += batch;
            if (batch == 0 && recvMsgQueue.isEmpty()) {
                break;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LogCore.BaseServer.info("drainRecvQueue done: processed={}, remaining={}, nodeId={}",
                processed, recvMsgQueue.size(), nodeId);
        return processed;
    }

    /**
     * 停机时排空出站队列（阻塞当前线程），直到队列为空或超时。
     *
     * @param timeoutMs 最大等待毫秒数
     * @return 本次排空发送的消息条数
     */
    public int drainSendQueue(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int sent = 0;
        while (System.currentTimeMillis() < deadline) {
            int batch = pulseSender();
            sent += batch;
            if (batch == 0 && sendMsgQueue.isEmpty()) {
                break;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LogCore.BaseServer.info("drainSendQueue done: sent={}, remaining={}, nodeId={}",
                sent, sendMsgQueue.size(), nodeId);
        return sent;
    }

}
