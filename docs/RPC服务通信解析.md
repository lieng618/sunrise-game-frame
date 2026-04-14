# RPC 服务通信详细解析

## 1. 目标

本文说明本项目自研 RPC 系统是如何完成以下工作的：

- 节点注册与发现
- 节点间建连
- RPC 方法注册
- RPC 调用发送
- RPC 回包与超时回调
- 广播 / 随机 / 定向调用
- game / external / global / http / gmback 之间的典型通信链路

## 2. RPC 的角色划分

整个 RPC 框架主要包含 4 类角色：

### 2.1 CenterServer

中心服不执行业务 RPC，只负责节点发现。

职责：

- 接收各节点 `report`
- 记录节点 `ip/port/serverId/nodeId`
- 广播新节点信息给旧节点
- 广播旧节点信息给新节点

### 2.2 RpcNode

每个具体服务进程都会启动一个 `RpcNode`，例如：

- external
- game
- global
- http
- gmback

`RpcNode` 内部包含：

- 一个 RPC Server
- 多个连接其他节点的 RPC Client
- 一个连接中心服的 ReportClient

### 2.3 RpcService

每个提供 RPC 能力的业务类都用：

- `@RpcService`
- `@RpcMethod`

例如：

- `GameRecvMessageService`
- `ExternalRecvMessageService`
- `ChatService`
- `MailService`
- `HttpRecvMessageService`

### 2.4 RpcFunction

调用侧统一使用：

- `RpcFunction.newInstance().call(...)`

它对上层屏蔽了：

- 节点选择
- 本地调用 / 远程调用
- 广播逻辑
- 回调注册
- 超时控制

## 3. 节点启动阶段：如何互相发现

## 3.1 每个进程启动 RpcNode

各进程启动时基本都遵循：

1. 读取配置
2. 创建 `RpcNodeManager.createRpcNode(serverId)`
3. `CallUtils.init(...)` 注册本进程提供的 RPC 服务
4. `rpcNode.start(new DbService())`

## 3.2 RpcNode.start(DbService) 做了什么

`RpcNode.start(DbService)` 的关键逻辑：

1. 查询 `rpc_server_system` 表
2. 确保当前 `serverId` 只会启动一个进程
3. 自动分配或复用端口
4. 启动本地 RPC Server
5. 调用 `connectMaster()` 连接中心服

也就是说：

- `rpc_server_system` 管理 RPC 节点端口
- `external_system` 管理对外服客户端接入端口

## 3.3 ReportClient 连接中心服

每个 `RpcNode` 会额外创建一个 `ReportClient` 去连中心服。

连接成功后：

- `ReportClientHandler.onConnectSuccess()`
- 调用 `NodeManager.reportFull(getNodeId())`

上报内容包括：

- 本节点 `serverId`
- 本节点 `ip`
- 本节点 `port`

## 3.4 CenterServer 收到上报后如何广播

中心服收到 `BaseMessage` 后交给：

- `CenterServerMessageManager`
- `NodeManager.updateNode(message)`

如果发现是新节点：

- 把“新节点信息”广播给所有旧节点
- 把“旧节点信息”广播给新节点

效果就是：

- 所有节点最终都能知道彼此的地址
- 所有节点都能主动去连对方 RPC Server

## 4. 节点建连：RPC 网络是怎么打通的

## 4.1 中心服广播的只是地址，不直接转发 RPC

中心服本身不承载业务 RPC，它只做“节点通讯录同步”。

收到其他节点地址后，各节点自己完成互连。

## 4.2 ReportClientMessageManager 触发 connectOther

节点收到中心服同步的节点信息后：

- `ReportClientMessageManager.pulseHandlerOne()`
- 调用 `RpcNode.connectOther(message)`

`message` 中带有：

- 对方 `serverId`
- 对方 `ip`
- 对方 `port`

## 4.3 connectOther 会创建一个到对方的 BaseClient

`RpcNode.connectOther` 会：

1. 判断当前是否已连过这个远端 serverId
2. 若没有，则创建一个 `BaseClient`
3. 给该 client 绑定 `RpcClientMessageManager`
4. 设置 handler 为 `ToOtherRpcNodeHandler`
5. 执行 `connect(ip, port)`

## 4.4 RPC 连接握手过程

底层握手沿用 `BaseClientHandler/BaseServerHandler` 的统一机制。

### 客户端首包

连接建立后，客户端先发：

CLIENT_CONNECT_<nodeId>

### 服务端回包

服务端收到后：

- 记录这条连接属于哪个 `connectNode`
- 回复：

```text
CLIENT_CONNECT_RESPONSE_SUCCESS<serverNodeId>
```

客户端拿到这个回包后：

- 保存远端 `serverNodeId`
- 标记连接完成

至此，RPC 双方建立了一条可用于传输 `Call` 的业务连接。

---

## 5. RPC 方法注册机制

## 5.1 CallEnum 是 RPC 方法编号表

项目里每个 RPC 方法最终都会对应一个 int 编号，定义在：

- `gen/src/main/java/org/sunrise/game/genRpc/gen/CallEnum.java`

命名规则：

```text
类名_方法名
```

例如：

- `GameRecvMessageService_recvMessage`
- `ExternalRecvMessageService_recvMessage`
- `ChatService_chat`
- `MailService_sendMail`

## 5.2 CallUtils.init 做了什么

`CallUtils.init(nodeId, classPaths, CallEnum.class)` 会：

1. 扫描指定包路径下所有类
2. 过滤 `@RpcService` 类
3. 实例化这些服务类（构造参数为 `nodeId`）
4. 把服务实例注册到 `ServiceManager`
5. 建立 `rpcId -> Method` 映射
6. 把“当前节点支持哪些 callId”写入 `RpcFunction.callIdNodes`
7. 启动所有服务生命周期 `ServiceManager.initAll()`

## 5.3 为什么能自动映射到方法

因为 `CallEnum` 字段名和方法缓存 key 一致：

```text
FriendService_getFriends
```

`CallUtils` 会把：

- `CallEnum.FriendService_getFriends` 对应的 int 值
- 映射到 `FriendService.getFriends(...)`

---

## 6. RPC 消息模型

## 6.1 Call 对象

RPC 最终传输的是 `Call`，它继承 `BaseMessage`。

关键字段：

- `nodeId`：发送方当前客户端节点 ID
- `toNodeId`：目标节点 ID
- `messageId`：唯一消息 ID
- `rpcId`：RPC 方法编号
- `type`：`Call` 或 `Update`
- `data`：参数数组，采用键值对形式
- `result`：调用结果码

## 6.2 参数为什么是键值对数组

调用时写法是：

```java
RpcFunction.newInstance().call(
    CallEnum.MailService_sendMail,
    "humanId", humanId,
    "templateId", templateId,
    "attachmentsJson", attachmentsJson,
    "senderName", senderName
);
```

底层会把它塞进：

```text
Object[] data = {"humanId", ..., "templateId", ..., ...}
```

服务端执行时实际按“位置参数”调用：

- 第 1 个参数取 `data[1]`
- 第 2 个参数取 `data[3]`
- ...

因此：

- 键名主要用于回包侧按名读取
- 真正调用依赖参数顺序必须和方法签名一致

这是本框架一个重要约束。

## 6.3 CallType

当前 RPC 消息类型有两种：

- `Call`：真实调用 / 回包
- `Update`：同步某节点支持的 callId 列表

---

## 7. 节点能力同步：为什么调用前知道谁能处理哪个 rpcId

## 7.1 本地先登记自己支持的 callId

`CallUtils.initCurRegisterCallIds()` 会把当前节点拥有的所有 `callId` 写入：

- `RpcFunction.callIdNodes`

即：

```text
callId -> [serverNodeId1, serverNodeId2, ...]
```

## 7.2 新连接建立时主动 update

当有新 RPC 客户端连到本节点 RPC Server 时：

- `RpcServerHandler.onRecvConnect()`
- 调用 `RpcFunction.newInstance().update(...)`

向对方发送：

- 当前节点所支持的全部 `callId`

## 7.3 对方收到 update 后更新路由表

- `RpcClientMessageManager.pulseHandlerOne()`
- 如果 `type == Update`
- 调用 `RpcFunction.onUpdate(call)`

从而更新：

```text
callId -> 可处理它的 serverNodeId 列表
```

这就是调用前的“服务发现路由表”。

---

## 8. 一次 RPC 调用的完整过程

下面以：

```java
RpcFunction.newInstance().call(CallEnum.ChatService_chat, ...)
```

为例。

## 8.1 调用方创建 RpcFunction

默认模式是：

- `SendRandom`

也可选：

- `SendAll`
- `SendDesignated`

## 8.2 根据 callId 找可用节点

`RpcFunction.call(id, params...)` 会先查：

- `RpcFunction.callIdNodes.get(id)`

如果没有节点注册这个方法，直接返回 false。

## 8.3 选择发送策略

### 1）SendRandom

规则：

- 如果当前节点自己就注册了此方法，优先本地处理
- 否则从远端节点随机选一个

### 2）SendAll

规则：

- 给所有注册了该 callId 的节点都发一份
- 多份消息共用同一个 `messageId`
- 监听回调时只注册一次

### 3）SendDesignated

规则：

- 按指定 `serverNodeId` 发给某一个目标节点
- external -> game、game -> external 这类定向转发大量使用此模式

## 8.4 调用如何进入发送队列

如果目标是本地：

- `BaseServerManager.recvFromClient(nodeId, call)`
- 直接塞进本地 RPC Server 的接收队列

如果目标是远端：

- `BaseClientManager.sendToServer(call)`
- 进入 RPC Client 的发送队列

## 8.5 发送到网络层

`BaseClientManager.sendMsgToServer(...)` 最终做：

1. `MessageUtils.toBytes(call)` -> MessagePack 序列化
2. 包装成 `SocketMessage(messageType=biz, data=...)`
3. 通过 Netty channel 发给远端 RPC Server

## 8.6 服务端接收并执行

远端 RPC Server 收到后：

- `RpcServerMessageManager.pulseHandlerOne()`
- 反序列化为 `Call`
- 调用 `CallUtils.handler(call)`

`CallUtils.handler` 做：

1. 根据 `rpcId` 找方法
2. 校验参数个数
3. 从 `ServiceManager` 拿到服务实例
4. 反射执行目标方法
5. 如有错误，返回错误码

## 8.7 服务方法返回结果

服务方法内部如果要回包，调用：

- `returns(...)`
- 本质是 `CallUtils.returns(...)`

返回时会构造新的 `Call`：

- `type=Call`
- `toNodeId=原调用方`
- `rpcId` 不变
- `messageId` 复用原调用的 `messageId`
- `result` 为执行结果码
- `data` 为返回参数

如果目标就是自己：

- 直接 `RpcManager.callResult(rep)`

如果目标是远端：

- 走 `BaseServerManager.sendToClient(rep)` 发回去

## 8.8 调用方回调触发

发起方如果之前注册了：

```java
rpcFunction.listenResult(callback, contexts...)
```

则：

- `RpcManager.registerCallback(messageId, callResult)`
- 回包回来后 `RpcManager.callResult(call)`
- 根据 `messageId` 取出回调并触发

---

## 9. RPC 超时机制

## 9.1 默认超时

默认 10 秒。

回调注册时：

- `RpcManager.checkTimeout.put(messageId, now + 10s)`

## 9.2 谁负责扫描超时

- `RpcClientMessageManager.pulseListenRpcTimeout()`

它会持续扫描：

- 哪些 `messageId` 已超过超时时间

超时后：

- 移除回调
- 构造 `RPC_TIMEOUT`
- 触发回调

## 9.3 自定义超时

可显式调用：

```java
rpcFunction.setTimeOut(millis)
```

---

## 10. ServiceManager：RPC 服务生命周期

每个 `@RpcService` 实例都交给 `ServiceManager` 托管。

## 10.1 initAll

启动时会：

1. 执行 `service.init()`
2. 异步从 `server_data` 表加载服务存档
3. 等待全部初始化完成
4. 注册 shutdown hook 做同步保存

## 10.2 pulse

RPC Server 主循环里会驱动：

- `pulsePerSec()`
- `pulsePer5Sec()`
- `pulsePerMin()`
- `pulse()`

这意味着很多服务端逻辑并不是被外部线程主动推，而是被 RPC 服务心跳驱动。

典型例子：

- `ExternalRecvMessageService.pulsePer5Sec()`：上报 external 地址到 http
- `GmBackRecvMessageService.pulsePerMin()`：广播封禁/禁言名单到 game
- `FriendService.pulse()`：清理过期申请

---

## 11. 典型通信场景解析

## 11.1 场景一：客户端消息从 external 转发到 game

调用链：

```text
Client -> ExternalServerHandler
       -> ClientConnection.msgQueue
       -> ExternalRecvMessageService.pulseHandlerConnectionMsg()
       -> RpcFunction.newInstance(connection.getGameNodeId())
       -> CallEnum.GameRecvMessageService_recvMessage
       -> GameRecvMessageService.recvMessage(...)
```

特点：

- 使用 `SendDesignated`
- 目标 game 节点来自 `ClientConnection.gameNodeId`
- 首次转发时 external 还不知道 gameNodeId，因此带空字符串，由 game 首次回包反向绑定

## 11.2 场景二：game 回包给客户端

调用链：

```text
HumanObject / Module
  -> ConnectObject.sendMsg(...)
  -> RpcFunction.newInstance(externalNodeId)
  -> CallEnum.ExternalRecvMessageService_recvMessage
  -> ExternalRecvMessageService.recvMessage(...)
  -> channel.writeAndFlush(...)
  -> Client
```

特点：

- 使用 `SendDesignated`
- 目标 external 节点来自 `ConnectObject.externalNodeId`
- 首次回包时会把当前 game 的 `rpcServerNodeId` 一起带给 external

## 11.3 场景三：game 调 global 获取聊天历史

调用链：

```text
game ChatMsgHandler
  -> RpcFunction.call(ChatService_history)
  -> Global ChatService.history()
  -> returns("info", protoData)
  -> callback
  -> human.sendMsg(S2C_History)
```

特点：

- global 返回的不是 Java 对象，而是已经编码好的 protobuf 二进制
- game 直接转发给客户端

## 11.4 场景四：好友申请跨服通知

调用链：

```text
game FriendModule.sendFriendRequest()
  -> global FriendService.sendFriendRequest()
  -> RpcFunction.SendAll.call(FriendRpcListenService_onNewFriendRequest)
  -> 所有 game 节点的 FriendRpcListenService.onNewFriendRequest()
  -> 若目标玩家在线于本节点，则推送好友申请更新
```

特点：

- 这里故意使用 `SendAll`
- 因为目标玩家可能在线于任意 game 节点
- 每个 game 节点各自判断是否持有该玩家

## 11.5 场景五：对外服地址同步到 http

调用链：

```text
ExternalRecvMessageService.pulsePer5Sec()
  -> RpcFunction.call(HttpRecvMessageService_recvMessage)
  -> HttpRecvMessageService.recvMessage(serverId, host, port)
  -> HttpServer.externalAddress 刷新
```

特点：

- external 周期性上报
- http 负责聚合并对客户端暴露查询接口

## 11.6 场景六：GM 指令广播到 game

调用链：

```text
GM Controller
  -> BaseController.sendMessageToAllGame()
  -> RpcFunction.SendAll.call(GameRecvGmBackService_recvMessage)
  -> 所有 game 节点收到 GM 指令
```

特点：

- 热更配置、封禁名单、禁言名单、踢人等都用这条链路
- 每个 game 节点根据本地在线玩家情况执行对应动作

---

## 12. RPC 框架的几个关键约束

## 12.1 一个进程只能有一个 RpcNode

代码中 `RpcNodeManager` 维护的是单例静态引用。

因此：

- 一个 JVM 进程只能创建一个 `RpcNode`

## 12.2 参数数量必须严格匹配

`CallUtils.handler` 会校验：

```text
call.getData().length / 2 == method.getParameterCount()
```

因此：

- `call()` 传的键值对数量必须和方法参数个数一致
- 且顺序要和目标方法参数顺序一致

## 12.3 键名主要是为了回调读取，不是强类型绑定

例如：

- 调用执行时按位置取值
- 回调读取时才使用 `getData("name")`

所以改参数名本身不会影响调用，但会影响回调解析代码。

## 12.4 广播不保证每个节点都有有效处理目标

`SendAll` 会给所有注册该 RPC 的节点都发消息。

典型用法：

- 通知所有 game 节点“谁在线由你们自己判断”

这不是精准路由，而是业务侧再过滤。

---

## 13. 总结

本项目 RPC 方案的核心思想是：

1. 用中心服做节点发现
2. 节点间直连，不经中心服中转业务数据
3. 用 `CallEnum + 反射` 做方法编号与方法体映射
4. 用 `RpcFunction` 封装随机 / 广播 / 定向三类调用
5. 用 `messageId` 做异步回调与超时处理
6. 用 `ServiceManager` 托管各 RPC 服务的生命周期与存档

它的优点是：

- 结构清晰
- 易于扩展服务
- 适合游戏多服协作
- 广播 / 定向调用都比较方便

需要注意的点是：

- 参数顺序强依赖
- 回调数据是弱类型 Object[]
- 一个进程仅一个 RpcNode
- 广播模式需要业务侧自行过滤目标玩家

