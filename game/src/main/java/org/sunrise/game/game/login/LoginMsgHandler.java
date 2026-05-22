package org.sunrise.game.game.login;

import com.alibaba.fastjson2.JSON;

import org.sunrise.game.db.DbManager;
import org.sunrise.game.db.entity.EntityAccount;
import org.sunrise.game.db.entity.EntityHumanInfo;
import org.sunrise.game.db.entity.EntityHumanList;
import org.sunrise.game.game.async.AsyncEventManager;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.logic.system.LoginQueueSystem;
import org.sunrise.game.game.human.ConnectObject;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManger;
import org.sunrise.game.genProto.gen.LoginProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.IdGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LoginMsgHandler {
    public static void handlerLogin(long connectId, int packetId, Object data, String externalNodeId) {
        ConnectObject connectObject = HumanObjectManger.getConnectObject(connectId);
        switch (packetId) {
            case LoginProto.FROM_CLIENT.C2S_Login_VALUE: {
                LoginProto.MC2S_Login msg = (LoginProto.MC2S_Login) data;
                if (msg.getUid().isEmpty()) {
                    return;
                }
                if (connectObject != null) {
                    return;
                }

                LoginQueueSystem loginQueue = GameSystemUtils.getSystem(LoginQueueSystem.class);
                if (loginQueue == null) {
                    return;
                }
                // 绑定连接id对应的对外服节点
                loginQueue.saveExternalNodeIdIfPresent(connectId, externalNodeId);

                // 直接登录或者进入排队队列
                if (loginQueue.tryEnterOrQueue(connectId, msg.getUid())) {
                    loginQueue.sendQueueInfo(connectId);
                } else {
                    processLogin(connectId, msg.getUid());
                }

                break;
            }
            case LoginProto.FROM_CLIENT.C2S_HumanList_VALUE: {
                if (connectObject == null) {
                    LogCore.GameServer.error("recv msg, connectionId = {}, packetType = {}, packetId = {}, humanObject not found", connectId, TopicProto.TOPIC.TOPIC_TYPE_LOGIN, packetId);
                    return;
                }
                var humanLists = HumanObjectManger.uidPlays.get(connectObject.getUid());
                if (humanLists != null) {
                    // 已经加载过数据
                    HumanObjectManger.getConnectObject(connectId).onLoadHumanList(humanLists);
                } else {
                    // 加载此账号下的所有角色
                    loadHumanList(connectId, true);
                }
                break;
            }

            case LoginProto.FROM_CLIENT.C2S_SelectHuman_VALUE: {
                if (connectObject == null) {
                    LogCore.GameServer.error("recv msg, connectionId = {}, packetType = {}, packetId = {}, humanObject not found", connectId, TopicProto.TOPIC.TOPIC_TYPE_LOGIN, packetId);
                    return;
                }
                LoginProto.MC2S_SelectHuman msg = (LoginProto.MC2S_SelectHuman) data;
                // 检测服务器id和位置是否有效
                if (msg.getServerId() <= 0) {
                    return;
                }
                // 假设开放0 1 2三个位置
                if (msg.getPos() < 0 || msg.getPos() > 2) {
                    return;
                }
                var humanLists = HumanObjectManger.uidPlays.get(connectObject.getUid());
                if (humanLists == null) {
                    return;
                }

                EntityHumanList humanShowInfo = null;
                for (EntityHumanList entityInfo : humanLists) {
                    if (entityInfo.getPos() == msg.getPos() && entityInfo.getServerId() == msg.getServerId()) {
                        humanShowInfo = entityInfo;
                        break;
                    }
                }
                // 没有找到此服务器下的角色 新建
                if (humanShowInfo == null) {
                    // 生成玩家id
                    String humanId = String.valueOf(IdGenerator.getId());
                    // 新增列表信息
                    DbManager.getDbService().executeAsyncWithGeneratedKey((r) -> {
                        AsyncEventManager.addAsyncEvent(() -> {
                            // 新增后，重新查询
                            loadHumanList(connectId, false);
                        });
                    }, "insert into `human_list` (uid, human_id, server_id, pos) values (?,?,?,?)", connectObject.getUid(), humanId, msg.getServerId(), msg.getPos());

                    HumanObjectManger.humanIds.put(connectId, humanId);
                    // 创建玩家
                    createHumanObject(connectId, msg.getServerId(), humanId, true);
                    HumanObject humanObject = HumanObjectManger.getHumanObject(humanId);
                    // 选择角色回包
                    connectObject.onSelectHuman();
                    humanObject.sendHumanData();
                    // 新增角色 插入db
                    DbManager.getDbService().executeAsync("insert into `human_info` (human_id, role_data) values (?,?)", humanId, JSON.toJSONBytes(humanObject.save()));
                } else {
                    // 检测是否被封禁
                    boolean ban = HumanObjectManger.banHumanQueue.contains(humanShowInfo.getHumanId());
                    // 根据玩家id 加载数据
                    // 重连无需加载
                    HumanObject humanObject = HumanObjectManger.getHumanObject(humanShowInfo.getHumanId());
                    if (humanObject != null) {
                        // 清理旧的连接对象
                        humanObject.getConnectObject().kick("login elsewhere");
                        HumanObjectManger.removeConnectObject(humanObject.getConnectObject().getConnectId());
                        HumanObjectManger.humanIds.remove(humanObject.getConnectObject().getConnectId());
                        // 设置新的连接对象
                        humanObject.setConnectObject(connectObject);
                        HumanObjectManger.humanIds.put(connectId, humanShowInfo.getHumanId());
                        // 选择角色回包
                        connectObject.onSelectHuman();
                        humanObject.sendHumanData();
                    } else {
                        if (ban) {
                            connectObject.kick("ban");
                            HumanObjectManger.uidAccounts.remove(connectObject.getUid());
                            HumanObjectManger.uidPlays.remove(connectObject.getUid());
                            HumanObjectManger.removeConnectObject(connectId);
                            return;
                        }
                        loadHumanInfo(connectId, humanShowInfo.getServerId(), humanShowInfo.getHumanId());
                    }
                }
                break;
            }

            case LoginProto.FROM_CLIENT.C2S_ClientPing_VALUE: {
                String humanId = HumanObjectManger.humanIds.get(connectId);
                if (humanId == null) {
                    return;
                }
                HumanObject humanObject = HumanObjectManger.getHumanObject(humanId);
                if (humanObject == null) {
                    return;
                }
                humanObject.setLastPingTime(System.currentTimeMillis());
                humanObject.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_LOGIN_VALUE, LoginProto.FROM_SERVER.S2C_ClientPing_VALUE);
                break;
            }
        }
    }

    public static void processLogin(long connectId, String uid) {
        LoginQueueSystem loginQueue = GameSystemUtils.getSystem(LoginQueueSystem.class);
        if (loginQueue == null) {
            return;
        }

        // 从排队队列移除
        loginQueue.removeFromQueue(connectId);
        // 未正确绑定对外服节点
        String externalNodeId = loginQueue.getExternalNodeId(connectId);
        if (externalNodeId == null || externalNodeId.isEmpty()) {
            LogCore.GameServer.error("processLogin failed, externalNodeId empty, connectId = {}, uid = {}", connectId, uid);
            return;
        }
        // 创建连接对象
        ConnectObject connectObject = new ConnectObject(connectId, uid, externalNodeId);
        HumanObjectManger.addConnectObject(connectId, connectObject);

        // 删除缓存的绑定信息
        loginQueue.releaseConnect(connectId);

        Long accountId = HumanObjectManger.uidAccounts.get(uid);
        if (accountId != null) {
            HumanObjectManger.getConnectObject(connectId).onLoadAccount(accountId);
        } else {
            loadAccount(connectId, uid);
        }
    }

    private static void loadAccount(long connectId, String uid) {
        DbManager.getDbService().queryGetOneByParamsAsync(result -> {
            AsyncEventManager.addAsyncEvent(() -> {
                ConnectObject connectContext = HumanObjectManger.getConnectObject(connectId);
                if (connectContext == null) {
                    return;
                }
                if (result != null) {
                    EntityAccount account = new EntityAccount(result);
                    connectContext.onLoadAccount(account.getId());
                    HumanObjectManger.uidAccounts.put(connectContext.getUid(), (long) account.getId());
                } else {
                    DbManager.getDbService().executeAsyncWithGeneratedKey(r -> {
                        AsyncEventManager.addAsyncEvent(() -> {
                            ConnectObject ctx = HumanObjectManger.getConnectObject(connectId);
                            if (ctx == null) {
                                return;
                            }
                            ctx.onLoadAccount((long) r);
                            HumanObjectManger.uidAccounts.put(ctx.getUid(), (long) r);
                        });
                    }, "insert into `account` (uid) values (?)", uid);
                }
            });
        }, "select * from `account` where `uid` = ?", uid);
    }

    private static void loadHumanList(long connectId, boolean send) {
        ConnectObject connectObject = HumanObjectManger.getConnectObject(connectId);
        DbManager.getDbService().queryGetAllByParamsAsync(result -> {
            // 将任务放入异步队列
            AsyncEventManager.addAsyncEvent(() -> {
                ConnectObject contextObject = HumanObjectManger.getConnectObject(connectId);
                if (contextObject == null) {
                    return;
                }
                List<EntityHumanList> humanLists = new ArrayList<>();
                if (result != null) {
                    for (Map<String, Object> objectMap : result) {
                        humanLists.add(new EntityHumanList(objectMap));
                    }
                }
                if (send) {
                    contextObject.onLoadHumanList(humanLists);
                }
                HumanObjectManger.uidPlays.put(contextObject.getUid(), humanLists);
            });
        }, "select * from `human_list` where `uid` = ?", connectObject.getUid());
    }

    private static void loadHumanInfo(long connectId, int serverId, String humanId) {
        DbManager.getDbService().queryGetOneByParamsAsync(result -> {
            AsyncEventManager.addAsyncEvent(() -> {
                if (result != null) {
                    EntityHumanInfo entityHumanInfo = new EntityHumanInfo(result);

                    HumanObjectManger.humanIds.put(connectId, entityHumanInfo.getHumanId());
                    // 创建玩家 并解析数据
                    createHumanObject(connectId, serverId, entityHumanInfo.getHumanId(), false);

                    HumanObject humanObject = HumanObjectManger.getHumanObject(entityHumanInfo.getHumanId());
                    humanObject.getConnectObject().onSelectHuman();
                    humanObject.load(entityHumanInfo.getRoleData());
                    humanObject.sendHumanData();
                } else {
                    // human_list和human_info表数据不匹配
                    LogCore.GameServer.error("loadHumanInfo failed, connectId = {}, serverId = {}, humanId = {}", connectId, serverId, humanId);
                }
            });
        }, "select * from `human_info` where `human_id` = ?", humanId);
    }


    private static void createHumanObject(long connectId, int serverId, String humanId, boolean newHumanObj) {
        HumanObjectManger.addHumanObject(humanId, new HumanObject(humanId, serverId, HumanObjectManger.getConnectObject(connectId), newHumanObj));
    }
}