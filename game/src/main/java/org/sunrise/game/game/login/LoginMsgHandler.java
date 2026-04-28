package org.sunrise.game.game.login;

import com.alibaba.fastjson2.JSON;

import org.sunrise.game.db.entity.EntityAccount;
import org.sunrise.game.db.entity.EntityHumanInfo;
import org.sunrise.game.db.entity.EntityHumanList;
import org.sunrise.game.game.async.AsyncEventManager;
import org.sunrise.game.game.db.DbManager;
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
                if (connectObject != null) {
                    return;
                }
                LoginProto.MC2S_Login msg = (LoginProto.MC2S_Login) data;
                if (msg.getUid().isEmpty()) {
                    return;
                }
                connectObject = new ConnectObject(connectId, msg.getUid(), externalNodeId);
                HumanObjectManger.addConnectObject(connectId, connectObject);

                Long accountId = HumanObjectManger.uidAccounts.get(msg.getUid());
                if (accountId != null) {
                    HumanObjectManger.getConnectObject(connectId).onLoadAccount(accountId);
                } else {
                    // 加载账号
                    DbManager.getDbService().queryGetOneByParamsAsync(result -> {
                        // 将任务放入异步队列
                        AsyncEventManager.addAsyncEvent(() -> {
                            if (result != null) {
                                EntityAccount account = new EntityAccount(result);
                                ConnectObject connectContext = HumanObjectManger.getConnectObject(connectId);
                                connectContext.onLoadAccount(account.getId());
                                HumanObjectManger.uidAccounts.put(connectContext.getUid(), (long) account.getId());
                            } else {
                                DbManager.getDbService().executeAsyncWithGeneratedKey(r -> {
                                    AsyncEventManager.addAsyncEvent(() -> {
                                        ConnectObject connectContext = HumanObjectManger.getConnectObject(connectId);
                                        connectContext.onLoadAccount((long) r);
                                        HumanObjectManger.uidAccounts.put(connectContext.getUid(), (long) r);
                                    });
                                }, "insert into `account` (uid) values (?)", msg.getUid());
                            }
                        });
                    }, "select * from `account` where `uid` = ?", msg.getUid());
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
                if (msg.getPos() < 0 || msg.getPos() > 3) {
                    return;
                }
                var humanLists = HumanObjectManger.uidPlays.get(connectObject.getUid());
                if (humanLists == null) {
                    return;
                }

                EntityHumanList humanShowInfo = null;
                for (EntityHumanList entityInfo : humanLists) {
                    if (entityInfo.getPos() == msg.getPos() && entityInfo.getServer_id() == msg.getServerId()) {
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
                    boolean ban = HumanObjectManger.banHumanQueue.contains(humanShowInfo.getHuman_id());
                    // 根据玩家id 加载数据
                    // 重连无需加载
                    HumanObject humanObject = HumanObjectManger.getHumanObject(humanShowInfo.getHuman_id());
                    if (humanObject != null) {
                        // 清理旧的连接对象
                        humanObject.getConnectObject().kick("login elsewhere");
                        HumanObjectManger.removeConnectObject(humanObject.getConnectObject().getConnectId());
                        HumanObjectManger.humanIds.remove(humanObject.getConnectObject().getConnectId());
                        // 设置新的连接对象
                        humanObject.setConnectObject(connectObject);
                        HumanObjectManger.humanIds.put(connectId, humanShowInfo.getHuman_id());
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
                        loadHumanInfo(connectId, humanShowInfo.getServer_id(), humanShowInfo.getHuman_id());
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

    private static void loadHumanList(long connectId, boolean send) {
        ConnectObject connectObject = HumanObjectManger.getConnectObject(connectId);
        DbManager.getDbService().queryGetAllByParamsAsync(result -> {
            // 将任务放入异步队列
            AsyncEventManager.addAsyncEvent(() -> {
                ConnectObject contextObject = HumanObjectManger.getConnectObject(connectId);
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

                    HumanObjectManger.humanIds.put(connectId, entityHumanInfo.getHuman_id());
                    // 创建玩家 并解析数据
                    createHumanObject(connectId, serverId, entityHumanInfo.getHuman_id(), false);

                    HumanObject humanObject = HumanObjectManger.getHumanObject(entityHumanInfo.getHuman_id());
                    humanObject.getConnectObject().onSelectHuman();
                    humanObject.load(entityHumanInfo.getRole_data());
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