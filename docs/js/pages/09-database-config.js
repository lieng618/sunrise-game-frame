registerPage('database', '数据库', 'MySQL 表结构、数据存储方式、DbService API', () => `
<h1>数据库</h1>
<p class="page-desc">Sunrise Game Frame 使用 MySQL 作为数据持久化存储，共 6 张核心表，字符集 utf8mb4</p>

<h2>数据库信息</h2>
<table>
<thead><tr><th>项</th><th>值</th></tr></thead>
<tbody>
<tr><td>数据库名</td><td>sunrise</td></tr>
<tr><td>连接池</td><td>HikariCP（默认最大连接数 5）</td></tr>
<tr><td>字符集</td><td>utf8mb4</td></tr>
<tr><td>初始化脚本</td><td>start/windows/create_sql_table.bat 或 start/linux/create_sql_table.sh</td></tr>
</tbody>
</table>

<h2>表结构</h2>
<h3>external_system（对外服地址管理）</h3>
<p>管理所有 ExternalServer 的地址信息，ExternalServer 启动时查询此表获取端口分配。</p>
<table>
<thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
<tbody>
<tr><td>id</td><td>INT AUTO_INCREMENT</td><td>主键</td></tr>
<tr><td>ip</td><td>VARCHAR(50)</td><td>对外服 IP</td></tr>
<tr><td>port</td><td>INT</td><td>对外服端口（TCP 端口，WS=port+1，KCP=port+2）</td></tr>
<tr><td>status</td><td>TINYINT</td><td>状态（0=关闭，1=开启）</td></tr>
</tbody>
</table>

<h3>rpc_server_system（RPC 节点地址管理）</h3>
<p>管理所有 RPC 节点的地址信息，RpcNode 启动时查询此表确保 serverId 唯一并获取端口分配。</p>
<table>
<thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
<tbody>
<tr><td>id</td><td>INT AUTO_INCREMENT</td><td>主键</td></tr>
<tr><td>ip</td><td>VARCHAR(50)</td><td>RPC 节点 IP</td></tr>
<tr><td>port</td><td>INT</td><td>RPC 节点端口</td></tr>
<tr><td>status</td><td>TINYINT</td><td>状态（0=关闭，1=开启）</td></tr>
</tbody>
</table>

<h3>account（玩家账号表）</h3>
<p>玩家账号信息，一个 uid 对应一个 account。</p>
<table>
<thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
<tbody>
<tr><td>id</td><td>INT AUTO_INCREMENT</td><td>主键（accountId）</td></tr>
<tr><td>uid</td><td>VARCHAR(50)</td><td>用户唯一标识（邮箱注册时由http服务生成；开发模式可由客户端直接传入）</td></tr>
</tbody>
</table>

<h3>human_list（玩家角色列表）</h3>
<p>每个账号可以有多个角色，按 pos 区分。</p>
<table>
<thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
<tbody>
<tr><td>id</td><td>INT AUTO_INCREMENT</td><td>主键</td></tr>
<tr><td>uid</td><td>VARCHAR(50)</td><td>用户 UID</td></tr>
<tr><td>human_id</td><td>VARCHAR(50)</td><td>角色 ID（雪花算法生成）</td></tr>
<tr><td>server_id</td><td>INT</td><td>服务器 ID</td></tr>
<tr><td>pos</td><td>INT</td><td>角色位置（0, 1, 2...）</td></tr>
<tr><td>name</td><td>VARCHAR(50)</td><td>角色名</td></tr>
<tr><td>level</td><td>INT</td><td>等级</td></tr>
</tbody>
</table>

<h3>human_info（玩家信息存档表）</h3>
<p>玩家完整存档数据，role_data 字段存储所有模块的 JSON 数据。</p>
<table>
<thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
<tbody>
<tr><td>id</td><td>INT AUTO_INCREMENT</td><td>主键</td></tr>
<tr><td>human_id</td><td>VARCHAR(50) UNIQUE</td><td>角色 ID</td></tr>
<tr><td>role_data</td><td>MEDIUMBLOB</td><td>角色存档数据（JSON 格式，模块名→JSON字符串）</td></tr>
</tbody>
</table>

<h3>server_data（服务信息存档表）</h3>
<p>RPC 服务持久化数据，BaseService.save() 将 dataMap 写入此表。</p>
<table>
<thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
<tbody>
<tr><td>id</td><td>INT AUTO_INCREMENT</td><td>主键</td></tr>
<tr><td>server_id</td><td>INT</td><td>服务 ID</td></tr>
<tr><td>name</td><td>VARCHAR(50)</td><td>服务名称（@RpcService 类名）</td></tr>
<tr><td>data</td><td>MEDIUMBLOB</td><td>服务存档数据（JSON 格式）</td></tr>
</tbody>
</table>

<h2>数据存储方式</h2>
<h3>玩家数据</h3>
<p>每个模块通过 <code>putDbData(key, value)</code> / <code>getDbData(key, typeRef, callback)</code> 把数据存到 <code>human_info.role_data</code> 字段：</p>
<pre><code class="language-json">{
    "DataModule": "{\\"name\\":\\"test\\",\\"level\\":1,\\"exp\\":0,\\"headIcon\\":0,\\"fightPower\\":0,\\"sex\\":0}",
    "ItemModule": "{\\"items\\":[{\\"itemId\\":1,\\"count\\":99}],\\"capacity\\":100}",
    "PlayerUnitModule": "{\\"mapId\\":1001,\\"x\\":0,\\"y\\":0,\\"attributes\\":{}}",
    "TaskModule": "{\\"tasks\\":[{\\"taskId\\":1,\\"status\\":2,\\"progress\\":0}]}",
    "FriendModule": "{\\"friends\\":[],\\"requests\\":[]}",
    "MailModule": "{\\"mails\\":[]}",
    "ActivityModule": "{\\"activities\\":[]}",
    "MinerModule": "{\\"minerData\\":{}}",
    "CdkModule": "{\\"usedCodes\\":[\\"VIP666\\",\\"ABCD1234EFGH\\"]}"
}</code></pre>

<h3>服务数据</h3>
<p>RPC 服务通过 <code>BaseService</code> 的 <code>dataMap</code> 存到 <code>server_data.data</code> 字段，格式类似。ServiceManager.initAll() 时异步加载，save() 时异步写入。</p>

<h2>DbService API</h2>
<pre><code class="language-java">DbService dbService = DbManager.getDbService();

// ===== 同步查询 =====
List&lt;Map&lt;String, Object&gt;&gt; all = dbService.queryAll("account");
Map&lt;String, Object&gt; one = dbService.queryById("account", accountId, "id");
List&lt;Map&lt;String, Object&gt;&gt; list = dbService.queryGetAllByParams("human_list", "uid", uid);
Map&lt;String, Object&gt; single = dbService.queryGetOneByParams("human_list", "human_id", humanId);
List&lt;Object&gt; column = dbService.queryAllSingleColumn("human_list", "human_id", String.class);

// ===== 异步查询（回调在主线程执行）=====
dbService.queryGetAllByParamsAsync("human_list", "uid", uid, results -> {
    // 处理结果
});

// ===== 同步执行 =====
dbService.execute("INSERT INTO account (uid) VALUES (?)", uid);
int generatedId = dbService.executeWithGeneratedKey("INSERT INTO human_list ...", params);

// ===== 异步执行 =====
dbService.executeAsync("UPDATE human_info SET role_data = ? WHERE human_id = ?", data, humanId);
dbService.executeAsync(callback, "DELETE FROM human_list WHERE human_id = ?", humanId);
dbService.executeAsyncWithGeneratedKey("INSERT INTO ...", params, id -> {
    // 获取自增 ID
});</code></pre>
`);

registerPage('config', '配置参考', '各服务配置项说明', () => `
<h1>配置参考</h1>
<p class="page-desc">所有服务都从 config/ 目录读取 .properties 配置文件，通过 ConfigReader 加载</p>

<h2>通用配置项</h2>
<p>以下配置项在所有服务的配置文件中都有：</p>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>jdbc.url</td><td>MySQL 连接地址</td><td>jdbc:mysql://127.0.0.1:3306/sunrise?useUnicode=true&characterEncoding=utf8mb4</td></tr>
<tr><td>jdbc.user</td><td>数据库用户</td><td>root</td></tr>
<tr><td>jdbc.password</td><td>数据库密码</td><td>666666</td></tr>
<tr><td>jdbc.pool.maximum-size</td><td>连接池最大连接数</td><td>5</td></tr>
<tr><td>jdbc.pool.minimum-idle</td><td>连接池最小空闲连接数</td><td>3</td></tr>
<tr><td>jdbc.pool.connection-timeout</td><td>获取连接超时（毫秒）</td><td>30000</td></tr>
<tr><td>jdbc.pool.idle-timeout</td><td>空闲连接存活时间（毫秒）</td><td>600000</td></tr>
<tr><td>jdbc.pool.max-lifetime</td><td>连接最大生命周期（毫秒）</td><td>1800000</td></tr>
<tr><td>master.id</td><td>中心服 ID（0=不连接中心服，单进程模式）</td><td>1</td></tr>
<tr><td>master.address</td><td>中心服地址</td><td>127.0.0.1</td></tr>
<tr><td>master.port</td><td>中心服端口</td><td>8000</td></tr>
<tr><td>report.address</td><td>本机上报 IP</td><td>127.0.0.1</td></tr>
<tr><td>rpc.node.type</td><td>RPC 节点类型，用于连接策略与拓扑展示（external/game/global/http/gmback 等）</td><td>game</td></tr>
<tr><td>rpc.node.server-id</td><td>RPC 节点 ID，写入 rpc_server_system，集群内唯一</td><td>200</td></tr>
<tr><td>log.level</td><td>日志级别</td><td>DEBUG</td></tr>
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
<tr><td>external.rate-limit.per-minute</td><td>单客户端每分钟最大接收消息数</td><td>1000</td></tr>
</tbody>
</table>

<h3>game-config.properties</h3>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>config.path</td><td>Luban 配置 JSON 路径</td><td>E:/sunrise-game-frame/tables/json</td></tr>
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
<tr><td>admin.jwt.expiration</td><td>JWT 过期时间（毫秒）</td><td>86400000</td></tr>
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
<tr><td>external.address</td><td>对外暴露 IP（客户端连接、Http 注册 External 地址）</td><td>127.0.0.1</td></tr>
<tr><td>config.path</td><td>Luban 配置 JSON 路径</td><td>E:/sunrise-game-frame/tables/json</td></tr>
<tr><td>http.port</td><td>合服内嵌 Http 服务端口</td><td>8090</td></tr>
<tr><td>admin.port</td><td>合服内嵌 GM API 端口</td><td>8010</td></tr>
<tr><td>admin.user</td><td>GM 登录用户名（gmback-ui 登录）</td><td>admin</td></tr>
<tr><td>admin.password</td><td>GM 登录密码</td><td>sunrise</td></tr>
<tr><td>admin.jwt.expiration</td><td>JWT 过期时间（毫秒）</td><td>864000000</td></tr>
<tr><td>login.queue.max-per-second</td><td>登录排队每秒放行上限（与 game-config 含义相同）</td><td>100</td></tr>
<tr><td>player.auth.enabled</td><td>玩家 Token 鉴权开关（Http + Game 须一致）</td><td>false</td></tr>
<tr><td>player.jwt.secret</td><td>玩家 JWT 密钥</td><td>sunrise-player-jwt-secret-change-me-in-production-32b</td></tr>
<tr><td>player.jwt.expiration</td><td>玩家 JWT 过期时间（毫秒）</td><td>86400000</td></tr>
<tr><td>mail.smtp.username</td><td>SMTP 发件邮箱</td><td>your@qq.com</td></tr>
<tr><td>mail.smtp.password</td><td>SMTP 授权码</td><td>xxxx</td></tr>
</tbody>
</table>

<h2>玩家鉴权配置说明</h2>
<ul>
    <li><code>player.auth.enabled=false</code>（默认）：压测机器人、消息发送工具等可直接用任意 uid 登录；HTTP 地址接口也可通过 query <code>uid</code> 访问</li>
    <li><code>player.auth.enabled=true</code>：须先邮箱登录获取 Token，HTTP 请求带 <code>Authorization</code> Header，游戏登录发 <code>C2S_Login(token)</code></li>
</ul>

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
