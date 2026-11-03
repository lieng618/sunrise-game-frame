registerPage('database', '数据库', 'MySQL 表结构、数据存储方式、DbService API', () => `
<h1>数据库</h1>
<p class="page-desc">Sunrise Game Frame 使用 MySQL 作为数据持久化存储，共 6 张核心表；初始化脚本会删除并重建 sunrise 数据库</p>

<h2>数据库信息</h2>
<table>
<thead><tr><th>项</th><th>值</th></tr></thead>
<tbody>
<tr><td>数据库名</td><td>sunrise</td></tr>
<tr><td>连接池</td><td>HikariCP（默认最大连接数 5）</td></tr>
<tr><td>字符集</td><td>utf8mb4</td></tr>
<tr><td>初始化脚本</td><td>gen/.../genDb/sql/sunrise.sql；Windows/Linux 各有一个包装脚本</td></tr>
</tbody>
</table>

<h2>表结构</h2>
<h3>external_system（对外服地址管理）</h3>
<p>管理所有 ExternalServer 的注册与启动状态：启动时用于保证同一 serverId 唯一，端口由进程探测后通过 RPC / GM 实时上报。</p>
<table>
<thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
<tbody>
<tr><td>id</td><td>INT AUTO_INCREMENT</td><td>主键</td></tr>
<tr><td>create_time</td><td>DATETIME</td><td>创建时间</td></tr>
<tr><td>ip</td><td>VARCHAR(255)</td><td>对外服 IP（启动时写入，便于排查）</td></tr>
<tr><td>status</td><td>INT</td><td>状态（0=关闭，1=开启）</td></tr>
<tr><td>update_time</td><td>DATETIME</td><td>租约心跳：运行中每 5 秒刷新</td></tr>
</tbody>
</table>
<p>status=1 时以 <code>update_time</code> 作为租约：外部服每 5 秒续租，超过 30 秒未续租说明上次进程异常退出（崩溃、强杀、断电），新实例会直接接管，不会因为残留的 status=1 永久起不来。</p>

<h3>rpc_server_system（RPC 节点地址管理）</h3>
<p>管理所有 RPC 节点的地址信息，RpcNode 启动时查询此表确保 serverId 唯一并获取端口分配；同样以 <code>update_time</code> 作为租约（每 5 秒续租，30 秒过期后允许新实例抢占）。</p>
<table>
<thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
<tbody>
<tr><td>id</td><td>INT AUTO_INCREMENT</td><td>主键</td></tr>
<tr><td>create_time</td><td>DATETIME</td><td>创建时间</td></tr>
<tr><td>update_time</td><td>DATETIME</td><td>租约续期时间</td></tr>
<tr><td>ip</td><td>VARCHAR(255)</td><td>RPC 节点 IP</td></tr>
<tr><td>port</td><td>INT</td><td>RPC 节点端口</td></tr>
<tr><td>status</td><td>INT</td><td>状态（0=关闭，1=开启）</td></tr>
</tbody>
</table>

<h3>account（玩家账号表）</h3>
<p>玩家账号信息，一个 uid 对应一个 account。</p>
<table>
<thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
<tbody>
<tr><td>id</td><td>INT AUTO_INCREMENT</td><td>主键（accountId）</td></tr>
<tr><td>create_time / update_time</td><td>DATETIME</td><td>创建与更新时间</td></tr>
<tr><td>uid</td><td>VARCHAR(255)</td><td>用户唯一标识（邮箱注册时由 Http 服务生成；开发模式可由客户端直接传入）</td></tr>
</tbody>
</table>

<h3>human_list（玩家角色列表）</h3>
<p>每个账号可以有多个角色，按 pos 区分。</p>
<table>
<thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
<tbody>
<tr><td>id</td><td>INT AUTO_INCREMENT</td><td>主键</td></tr>
<tr><td>create_time / update_time</td><td>DATETIME</td><td>创建与更新时间</td></tr>
<tr><td>uid</td><td>VARCHAR(255)</td><td>用户 UID</td></tr>
<tr><td>human_id</td><td>VARCHAR(255)</td><td>角色 ID（雪花算法生成）</td></tr>
<tr><td>server_id</td><td>INT</td><td>服务器 ID</td></tr>
<tr><td>pos</td><td>INT</td><td>角色位置（0, 1, 2...）</td></tr>
<tr><td>name</td><td>VARCHAR(255)</td><td>角色名</td></tr>
<tr><td>level</td><td>INT</td><td>等级</td></tr>
</tbody>
</table>

<h3>human_info（玩家信息存档表）</h3>
<p>玩家完整存档数据，role_data 字段存储所有模块的 JSON 数据。</p>
<table>
<thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
<tbody>
<tr><td>id</td><td>INT AUTO_INCREMENT</td><td>主键</td></tr>
<tr><td>create_time / update_time</td><td>DATETIME</td><td>创建与更新时间</td></tr>
<tr><td>human_id</td><td>VARCHAR(255) UNIQUE</td><td>角色 ID</td></tr>
<tr><td>role_data</td><td>MEDIUMBLOB</td><td>角色存档数据（JSON 格式，模块名→JSON字符串）</td></tr>
</tbody>
</table>

<h3>server_data（服务信息存档表）</h3>
<p>RPC 服务持久化数据，BaseService.save() 将 dataMap 写入此表。</p>
<table>
<thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
<tbody>
<tr><td>id</td><td>INT AUTO_INCREMENT</td><td>主键</td></tr>
<tr><td>create_time / update_time</td><td>DATETIME</td><td>创建与更新时间</td></tr>
<tr><td>server_id</td><td>INT</td><td>服务 ID</td></tr>
<tr><td>name</td><td>VARCHAR(255)</td><td>服务或 GameSystem 名称</td></tr>
<tr><td>data</td><td>MEDIUMBLOB</td><td>服务存档数据（JSON 格式）</td></tr>
</tbody>
</table>
<div class="callout callout-warn">
    <p><strong>当前 DDL 的唯一约束：</strong>只有 <code>human_info.human_id</code> 声明了 UNIQUE。<code>account.uid</code>、<code>human_list(uid, server_id, pos)</code>、<code>server_data(server_id, name)</code> 都没有唯一约束；并发创建或重复部署时可能产生重复业务记录，应用层的单行查询与 Map 缓存不能替代数据库约束。</p>
</div>

<h2>数据存储方式</h2>
<h3>玩家数据</h3>
<p>模块内先通过 <code>putDbData(key, value)</code> 把各字段写入自己的 <code>dataMap</code>。玩家存档时 <code>HumanObject.save()</code> 收集为“模块简单类名 → dataMap”，再整体序列化到 <code>human_info.role_data</code>：</p>
<pre><code class="language-json">{
  "DataModule": {
    "name": "\"test\"",
    "level": "1",
    "exp": "0",
    "headIcon": "\"1\"",
    "fightPower": "0",
    "sex": "1",
    "ld": "1793632000000",
    "lw": "1793632000000"
  },
  "ItemModule": {
    "items": "[{\"itemId\":1,\"count\":99}]"
  },
  "PlayerUnitModule": {
    "baseValues": "{\"1\":100}",
    "id": "10001",
    "mapPostX": "0.0",
    "mapPostY": "0.0",
    "mapPostZ": "0.0",
    "Orientation": "0.0"
  },
  "CdkModule": {
    "usedCodes": "[\"VIP666\"]"
  }
}</code></pre>
<p>上例只展示代表字段；实际模块和字段以各 Module 的 <code>save()</code> 为准。每个内部值仍是 JSON 文本字符串，读取时由 <code>getDbData()</code> 配合 <code>TypeReference</code> 还原。</p>

<h3>服务数据</h3>
<p>RPC 服务通过 <code>BaseService</code> 的 <code>dataMap</code> 存到 <code>server_data.data</code> 字段，格式类似。ServiceManager.initAll() 时异步加载，save() 时异步写入。</p>

<h2>DbService API</h2>
<pre><code class="language-java">DbService db = DbManager.getDbService();

// 同步查询（参数是完整 SQL）
Map&lt;String, Object&gt; one = db.queryGetOneByParams(
    "select * from \`account\` where \`id\` = ?", accountId);
List&lt;Map&lt;String, Object&gt;&gt; list = db.queryGetAllByParams(
    "select * from \`human_list\` where \`uid\` = ?", uid);
List&lt;String&gt; ids = db.queryAllSingleColumn(
    "select \`human_id\` from \`human_list\`");

// 异步查询：回调运行在 DbService 线程池
db.queryGetAllByParamsAsync(results -&gt; {
    AsyncEventManager.addAsyncEvent(() -&gt; {
        // 游戏业务状态修改应回到业务线程
    });
}, "select * from \`human_list\` where \`uid\` = ?", uid);

// 同步写入
db.execute("update \`account\` set \`update_time\` = now() where \`uid\` = ?", uid);
Object id = db.executeWithGeneratedKey(
    "insert into \`account\` (\`uid\`) values (?)", uid);

// 异步写入
db.executeAsync("update \`human_info\` set \`role_data\` = ? where \`human_id\` = ?",
    data, humanId);
db.executeAsyncWithGeneratedKey(id -&gt; { /* 自增 ID 回调 */ },
    "insert into \`human_list\` (\`uid\`, \`human_id\`, \`server_id\`, \`pos\`) values (?, ?, ?, ?)",
    uid, humanId, serverId, pos);</code></pre>
`);

registerPage('config', '配置参考', '各服务配置项说明', () => `
<h1>配置参考</h1>
<p class="page-desc">服务以命令行第一个参数读取 properties 文件。仓库提交的是模板，使用 reset-config 脚本生成本地实际配置。</p>

<h2>数据库与可观测配置</h2>
<p>中心服与所有 RPC 业务进程都使用以下 JDBC、日志和内部认证配置：</p>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>jdbc.url</td><td>MySQL 连接地址</td><td>jdbc:mysql://127.0.0.1:3306/sunrise</td></tr>
<tr><td>jdbc.user</td><td>数据库用户</td><td>root</td></tr>
<tr><td>jdbc.password</td><td>数据库密码（模板默认仅供本地开发，生产必须覆盖）</td><td>123456</td></tr>
<tr><td>jdbc.pool.maximum-size</td><td>连接池最大连接数</td><td>5</td></tr>
<tr><td>jdbc.pool.minimum-idle</td><td>连接池最小空闲连接数</td><td>3</td></tr>
<tr><td>jdbc.pool.connection-timeout</td><td>获取连接超时（毫秒）</td><td>30000</td></tr>
<tr><td>jdbc.pool.idle-timeout</td><td>空闲连接存活时间（毫秒）</td><td>600000</td></tr>
<tr><td>jdbc.pool.max-lifetime</td><td>连接最大生命周期（毫秒）</td><td>1800000</td></tr>
<tr><td>master.id</td><td>中心服 ID（0=不连接中心服，单进程模式）</td><td>1</td></tr>
<tr><td>master.address</td><td>中心服地址</td><td>127.0.0.1</td></tr>
<tr><td>master.port</td><td>中心服端口</td><td>8000</td></tr>
<tr><td>report.address</td><td>本机上报 IP</td><td>127.0.0.1</td></tr>
<tr><td>log.level</td><td>日志级别</td><td>DEBUG</td></tr>
<tr><td>rpc.auth-token</td><td>内部 RPC 握手共享密钥；所有 RPC 节点与中心服必须相同</td><td>随机长字符串</td></tr>
</tbody>
</table>

<h2>RPC 节点配置</h2>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>rpc.node.type</td><td>节点类型，用于连接策略与拓扑</td><td>game</td></tr>
<tr><td>rpc.node.server-id</td><td>集群内唯一节点 ID，写入 rpc_server_system</td><td>200</td></tr>
<tr><td>rpc.scan.packages</td><td>必填，逗号分隔的 @RpcService 扫描包；运行时自动加入热更控制包</td><td>org.sunrise.game.game.service</td></tr>
<tr><td>rpc.call-enum-class</td><td>生成 RPC ID 的 CallEnum 全类名</td><td>org.sunrise.game.genRpc.gen.CallEnum</td></tr>
<tr><td>hotswap.include.packages</td><td>允许热更的类包前缀</td><td>org.sunrise.game</td></tr>
</tbody>
</table>

<h2>各服务特有配置</h2>
<h3>center-config.properties</h3>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>dashboard.port</td><td>RPC 拓扑 Dashboard 端口；0 表示不启动</td><td>8088</td></tr>
<tr><td>rpc.connect.&lt;type&gt;</td><td>该类型节点允许主动连接的目标类型列表（逗号分隔）；不配任何 rpc.connect.* 则全量互连</td><td>rpc.connect.game=external,global,gmback</td></tr>
</tbody>
</table>

<h3>external-config.properties</h3>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>external.address</td><td>对外暴露的 IP（客户端连接地址）</td><td>127.0.0.1</td></tr>
<tr><td>external.listen.tcp</td><td>tcp协议是否启动</td><td>true</td></tr>
<tr><td>external.listen.ws</td><td>ws协议是否启动</td><td>true</td></tr>
<tr><td>external.listen.kcp</td><td>kcp协议是否启动</td><td>true</td></tr>
<tr><td>external.listen.port</td><td>固定 TCP 起始端口；WS=p+1，KCP=p+2；留空自动探测</td><td>10000</td></tr>
<tr><td>external.listen.base-port</td><td>自动探测的起始端口</td><td>10000</td></tr>
<tr><td>external.listen.port-range</td><td>最多扫描的端口数，必须 ≥3</td><td>100</td></tr>
<tr><td>external.rate-limit.refill-per-minute</td><td>单连接令牌桶的长期补充速度</td><td>1200</td></tr>
<tr><td>external.rate-limit.burst</td><td>单连接允许的最大突发消息数</td><td>120</td></tr>
<tr><td>external.rate-limit.abuse-window-seconds</td><td>违规拒绝计数窗口</td><td>5</td></tr>
<tr><td>external.rate-limit.abuse-threshold</td><td>窗口内达到该拒绝数后断开连接</td><td>600</td></tr>
<tr><td>external.rate-limit.log-interval-ms</td><td>单连接限流告警最小间隔</td><td>1000</td></tr>
</tbody>
</table>

<h3>game-config.properties</h3>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>config.path</td><td>Luban 配置 JSON 目录；相对路径相对配置文件目录</td><td>../tables/json</td></tr>
<tr><td>config.nav.path</td><td>地图导航 JSON 目录</td><td>../tables/mapjson</td></tr>
<tr><td>login.queue.max-per-second</td><td>登录排队：每秒允许放行的最大登录数（含直通与出队），超出则入队等待</td><td>100</td></tr>
<tr><td>player.auth.enabled</td><td>游戏服是否强制校验 C2S_Login Token</td><td>false</td></tr>
<tr><td>player.jwt.secret</td><td>玩家 JWT 密钥（须与 http-config 一致）</td><td>sunrise-player-jwt-secret-change-me-in-production-32b</td></tr>
<tr><td>player.jwt.expiration</td><td>玩家 JWT 过期时间（毫秒）</td><td>86400000</td></tr>
</tbody>
</table>

<h3>http-config.properties</h3>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>http.port</td><td>HTTP 服务端口</td><td>8090</td></tr>
<tr><td>player.auth.enabled</td><td>地址类接口是否强制 Token 鉴权</td><td>false</td></tr>
<tr><td>player.jwt.secret</td><td>玩家 JWT 密钥</td><td>sunrise-player-jwt-secret-change-me-in-production-32b</td></tr>
<tr><td>player.jwt.expiration</td><td>玩家 JWT 过期时间（毫秒）</td><td>86400000</td></tr>
<tr><td>mail.smtp.username</td><td>SMTP 发件邮箱</td><td>your@qq.com</td></tr>
<tr><td>mail.smtp.password</td><td>SMTP 授权码</td><td>xxxx</td></tr>
</tbody>
</table>

<h3>gmback-config.properties</h3>
<p>仅配置 <strong>GmBackServer API</strong> 进程；Web 页面在 <code>gmback-ui/</code> 单独构建部署，不读取本文件。</p>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>admin.port</td><td>GM 后台 API 端口（非前端页面端口）</td><td>8010</td></tr>
<tr><td>admin.user</td><td>登录用户名</td><td>admin</td></tr>
<tr><td>admin.password</td><td>登录密码</td><td>sunrise</td></tr>
<tr><td>admin.jwt.expiration</td><td>GM JWT 过期时间（毫秒）</td><td>864000000</td></tr>
<tr><td>hotswap.upload-dir</td><td>GM 上传热更 JAR 的保存目录</td><td>/data/sunrise/hotfix/uploaded</td></tr>
<tr><td>hotswap.center-dashboard-url</td><td>Center Dashboard HTTP 地址；配置后纳入批量热更</td><td>http://127.0.0.1:8088</td></tr>
</tbody>
</table>

<h3>client-config.properties</h3>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>http.address</td><td>HTTP 服务地址</td><td>127.0.0.1</td></tr>
<tr><td>http.port</td><td>HTTP 服务端口</td><td>8090</td></tr>
<tr><td>client.socket</td><td>连接协议（tcp/websocket/kcp）</td><td>tcp</td></tr>
</tbody>
</table>

<h3>runallone-config.properties</h3>
<p>单进程合服模式（External + Game + Http + GmBack 等同进程），<code>master.id=0</code> 表示不连接中心服。除下列项外，也包含上文「通用配置项」</p>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>master.id</td><td>中心服 ID，合服固定为 0（不连 Center）</td><td>0</td></tr>
<tr><td>rpc.node.type</td><td>单进程节点类型；当前模板为 runallone</td><td>runallone</td></tr>
<tr><td>rpc.node.server-id</td><td>单进程 RPC 节点 ID，仍写入 rpc_server_system</td><td>100</td></tr>
<tr><td>rpc.scan.packages</td><td>必须覆盖所有内置服务包，模板使用完整前缀</td><td>org.sunrise.game</td></tr>
<tr><td>rpc.call-enum-class</td><td>CallEnum 全类名</td><td>org.sunrise.game.genRpc.gen.CallEnum</td></tr>
<tr><td>external.address</td><td>对外暴露 IP（客户端连接、Http 注册 External 地址）</td><td>127.0.0.1</td></tr>
<tr><td>external.listen.* / external.rate-limit.*</td><td>对外服协议开关、端口块与单连接限流，含义同 external-config</td><td>见模板</td></tr>
<tr><td>config.path</td><td>Luban 配置 JSON 目录；相对路径相对配置文件目录</td><td>../tables/json</td></tr>
<tr><td>config.nav.path</td><td>地图导航 JSON 目录</td><td>../tables/mapjson</td></tr>
<tr><td>http.port</td><td>合服内嵌 Http 服务端口</td><td>8090</td></tr>
<tr><td>admin.port</td><td>合服内嵌 GM API 端口</td><td>8010</td></tr>
<tr><td>admin.user</td><td>GM 登录用户名（gmback-ui 登录）</td><td>admin</td></tr>
<tr><td>admin.password</td><td>GM 登录密码</td><td>sunrise</td></tr>
<tr><td>admin.jwt.expiration</td><td>GM JWT 过期时间（毫秒）</td><td>864000000</td></tr>
<tr><td>login.queue.max-per-second</td><td>登录排队每秒放行上限（与 game-config 含义相同）</td><td>100</td></tr>
<tr><td>player.auth.enabled</td><td>玩家 Token 鉴权开关（Http + Game 须一致）</td><td>false</td></tr>
<tr><td>player.jwt.secret</td><td>玩家 JWT 密钥</td><td>sunrise-player-jwt-secret-change-me-in-production-32b</td></tr>
<tr><td>player.jwt.expiration</td><td>玩家 JWT 过期时间（毫秒）</td><td>86400000</td></tr>
<tr><td>mail.smtp.username</td><td>SMTP 发件邮箱</td><td>your@qq.com</td></tr>
<tr><td>mail.smtp.password</td><td>SMTP 授权码</td><td>xxxx</td></tr>
<tr><td>hotswap.*</td><td>热更包扫描、上传目录等配置</td><td>见模板</td></tr>
</tbody>
</table>

<h2>玩家鉴权配置说明</h2>
<ul>
    <li><code>player.auth.enabled=false</code>（默认）：压测机器人、消息发送工具等可直接用任意 uid 登录；HTTP 地址接口也可通过 query <code>uid</code> 访问</li>
    <li><code>player.auth.enabled=true</code>：须先邮箱登录获取 Token，HTTP 请求带 <code>Authorization</code> Header，游戏登录发 <code>C2S_Login(token)</code></li>
</ul>

<h2>占位符解析顺序</h2>
<p>配置文件中的 <code>\${ENV}</code> / <code>\${ENV:default}</code> 按“环境变量 → JVM <code>-D</code> 系统属性 → 默认值”解析。无默认值且找不到时启动失败。该规则由 <code>ConfigPlaceholderResolver</code> 实现。</p>

<h2>Docker 配置差异</h2>
<table>
<thead><tr><th>配置项</th><th>本地值</th><th>Docker 值</th><th>说明</th></tr></thead>
<tbody>
<tr><td>jdbc.url</td><td>127.0.0.1:3306</td><td>mysql:3306</td><td>Docker 容器名</td></tr>
<tr><td>master.address</td><td>127.0.0.1</td><td>center</td><td>Center 容器名</td></tr>
<tr><td>report.address</td><td>127.0.0.1</td><td>各自容器名</td><td>如 game、external</td></tr>
<tr><td>external.address</td><td>127.0.0.1</td><td>宿主机 IP</td><td>客户端连接地址</td></tr>
<tr><td>config.path</td><td>本地路径</td><td>/app/tables/json</td><td>容器内路径</td></tr>
</tbody>
</table>
`);
