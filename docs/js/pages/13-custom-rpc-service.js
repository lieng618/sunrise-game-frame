registerPage('custom-rpc-service', '可扩展 RPC 服务', '新建 RPC 进程、接入中心服、编写 Service 与调用', () => `
<h1>可扩展 RPC 服务</h1>
<p class="page-desc">除中心服、对外服、游戏服、全局服、HTTP、GM 后台等内置进程外，框架支持按同一套模式新增任意 RPC 业务进程：创建 RpcNode、向中心服注册、与其他节点互连，并通过 @RpcService 暴露/调用远程方法。</p>

<h2>适用场景</h2>
<table>
<thead><tr><th>场景</th><th>建议</th></tr></thead>
<tbody>
<tr><td>仅需在现有 Game / Global 上增加跨服逻辑</td><td>在对应包下新增 @RpcService 即可，不必新进程（见 <a href="#/development">开发指南</a>）</td></tr>
<tr><td>想做一个独立的服务，比如第三方回调等</td><td>新建 RPC 进程</td></tr>
<tr><td>单进程本地调试</td><td>使用 RunAllOne，<code>master.id=0</code> 不连中心服（见 <a href="#/config">配置参考</a>）</td></tr>
</tbody>
</table>

<h2>服务 ID 规划</h2>
<p>每个 RPC 进程在 JVM 内只能有一个 <code>RpcNode</code>（<code>RpcNodeManager</code> 单例）。进程用整数 <strong>serverId</strong> 区分，并写入表 <code>rpc_server_system</code> 保证端口唯一。当前约定（可在 <a href="#/center-server">中心服</a> 文档中调整）：</p>
<table>
<thead><tr><th>serverId 区间</th><th>用途</th><th>示例</th></tr></thead>
<tbody>
<tr><td>1</td><td>中心服</td><td>CenterServer</td></tr>
<tr><td>2</td><td>GM 后台</td><td>GmBackServer</td></tr>
<tr><td>3 ~ 99</td><td>预留：HTTP 及同类扩展 RPC 服务</td><td>HttpServer 默认 3</td></tr>
<tr><td>100 ~ 199</td><td>对外服</td><td>External 默认 100</td></tr>
<tr><td>200 ~ 3999</td><td>游戏服</td><td>Game 默认 200</td></tr>
<tr><td>4000 ~ 4096</td><td>全局服</td><td>Global 默认 4000</td></tr>
</tbody>
</table>
<p>新增自定义服务时，请在 <strong>3~99</strong>（或你们重新划分的空闲段）选取未占用的 id，且全集群唯一。</p>

<h2>第一步：启动类（创建 RpcNode 并接入中心服）</h2>
<p>所有业务 RPC 进程启动模板一致，参考 <code>HttpServerStartUp</code>：</p>
<pre><code class="language-java">public class HttpServerStartUp {
    public static void main(String[] args) {
        // args[0]: 配置文件路径  args[1]: 本进程的 serverId（写入 rpc_server_system）
        if (args.length == 0) {
            args = new String[]{"./config/http-config.properties", "3"};
        }
        System.setProperty("programName", "HttpServer-" + args[1]);
        ConfigReader.loadConfig(args[0]);
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            return;
        }
        Utils.setLogLevel(properties.getProperty("log.level"));

        // 1. 创建 RPC 节点（每个 JVM 仅一个）
        var rpcNode = RpcNodeManager.createRpcNode(Integer.parseInt(args[1]));
        // 2. 扫描并注册本进程提供的 @RpcService（包路径 + CallEnum）
        CallUtils.init(rpcNode.getNodeId(),
            Collections.singletonList("org.sunrise.game.http.service"),
            CallEnum.class);
        // 3. 启动：写库分配端口 → 监听 → 连接中心服 → 等待互连
        rpcNode.start();

        Utils.startMemoryCheck();
    }
}</code></pre>

<h3>rpcNode.start() 做了什么</h3>
<ol class="step-list">
    <li>查询/插入 <code>rpc_server_system</code>：同一 serverId 已在运行则退出；否则复用或分配 RPC 监听端口（默认从 20000 递增）</li>
    <li>启动本机 <code>BaseServer</code>（RpcServer），对外提供 RPC 调用入口</li>
    <li>读取配置 <code>master.id / master.address / master.port</code>，创建 <code>ReportClient</code> 连接中心服；<code>report.address</code> 为上报给集群的 IP</li>
    <li>中心服将其他在线节点地址广播过来，本节点 <code>connectOther()</code> 建立 BaseClient 互连，握手后交换各自注册的 CallEnum 列表</li>
</ol>
<p>此后即可使用 <code>RpcFunction</code> 调用任意已注册方法（随机/广播/定向），详见 <a href="#/rpc">RPC 框架</a>。</p>

<h2>第二步：配置文件</h2>
<p>在 <code>config/</code> 下复制一份现有 RPC 进程配置（如 <code>http-config.properties</code>），按服务改名，例如 <code>my-service-config.properties</code>：</p>
<pre><code class="language-properties">jdbc.url=jdbc:mysql://127.0.0.1:3306/sunrise
jdbc.user=root
jdbc.password=123456
jdbc.maximumPoolSize=5
jdbc.minimumIdle=3
jdbc.connectionTimeout=30000
jdbc.idleTimeout=600000
jdbc.maxLifetime=1800000

# 连接中心服（合服调试可设 master.id=0，见配置参考）
master.id=1
master.address=127.0.0.1
master.port=8000
# 本节点上报给集群的 IP（Docker 下改为容器名或宿主机 IP）
report.address=127.0.0.1

# 本服务特有项（示例：HTTP 端口）
http.port=8090

log.level=DEBUG</code></pre>
<p>通用项说明见 <a href="#/config">配置参考</a>。启动时传入：<code>java -jar sunrise-xxx.jar config/my-service-config.properties &lt;serverId&gt;</code>。</p>

<h2>第三步：在 gen 模块声明 RPC 接口（Stub）</h2>
<p>框架用 <strong>接口 Stub + 实现类</strong> 分离：genRpc/service 下只声明方法名（无参），用于生成 <code>CallEnum</code>；真实参数写在 <code>game</code> 模块的实现类上。</p>

<h3>1. 新建 Stub 接口</h3>
<p>路径：<code>gen/src/main/java/org/sunrise/game/genRpc/service/MyNotifyService.java</code></p>
<pre><code class="language-java">package org.sunrise.game.genRpc.service;

public interface MyNotifyService {
    void ping();
    void pushNotice();
}</code></pre>
<p>规则：必须是 <code>public interface</code>；每个 RPC 方法写为 <code>void 方法名();</code>（无参数、无注解）。方法名需与实现类中的 <code>@RpcMethod</code> 方法名一致。</p>

<h3>2. 运行 GenRpcStartUp 生成 CallEnum</h3>
<p>在 IDE 中运行主类 <code>org.sunrise.game.genRpc.GenRpcStartUp</code>（工作目录为项目根目录）。生成物：</p>
<ul>
    <li><code>gen/src/main/java/org/sunrise/game/genRpc/gen/CallEnum.java</code></li>
    <li>常量名格式：<code>类名_方法名</code>，如 <code>MyNotifyService_ping = 31</code></li>
    <li>已有 id 保持不变，新方法自动递增；实现类若在 game 模块，会附带 <code>{@code 全限定类#方法(参数类型)}</code> 注释</li>
</ul>
<p>更多说明见 <a href="#/code-gen">代码生成 · RPC 枚举</a>。</p>

<h2>第四步：在 game 模块实现 Service</h2>
<p>实现类放在启动类 <code>CallUtils.init</code> 扫描的包下，例如 <code>game/src/main/java/org/sunrise/game/myservice/service/MyNotifyService.java</code>：</p>
<pre><code class="language-java">@RpcService
public class MyNotifyService extends BaseService {

    public MyNotifyService(String nodeId) {
        super(nodeId);
    }

    @RpcMethod
    public void ping(String fromNodeId) {
        LogCore.RpcUtils.info("ping from {}", fromNodeId);
        returns("ok", true);
    }

    @RpcMethod
    public void pushNotice(String title, String content) {
        // 业务逻辑，例如写入内存、推 HTTP 等
        returns("success", true);
    }

    @Override
    public void pulsePer5Sec() {
        super.pulsePer5Sec();
        // 可选：定时任务，由 ServiceManager 统一驱动
    }
}</code></pre>
<p>约定：</p>
<ul>
    <li>类标注 <code>@RpcService</code>，继承 <code>BaseService</code></li>
    <li>必须提供 <code>public XxxService(String nodeId)</code> 构造，供 <code>CallUtils.init</code> 实例化</li>
    <li>业务方法标注 <code>@RpcMethod</code>；需要回包时调用 <code>returns("key", value, ...)</code></li>
    <li>若需持久化到 <code>server_data</code> 表，重写 <code>load()</code> / <code>save()</code>（见 <a href="#/rpc">RPC 框架 · BaseService</a>）</li>
</ul>

<h3>以HttpRecvMessageService 举例</h3>
<p>HTTP 服务的 RPC 实现位于 <code>org.sunrise.game.http.service</code>，构造方法内启动 Javalin；对外服每 5 秒 RPC 上报地址，GM 通过 RPC 改开关/白名单：</p>
<pre><code class="language-java">@RpcService
public class HttpRecvMessageService extends BaseService {
    public HttpRecvMessageService(String nodeId) {
        super(nodeId);
        int port = Integer.parseInt(ConfigReader.getProp().getProperty("http.port", "8090"));
        httpServer = new HttpServer(port);
        httpServer.start();
    }

    @RpcMethod
    public void updateExternalRemoteData(int serverId, String host, int port) { /* 更新地址池 */ }

    @RpcMethod
    public void setExternalServerStatus(boolean open) { /* GM 关服 */ }
}</code></pre>
<p>对应 Stub：<code>genRpc/service/HttpRecvMessageService.java</code> 中四个无参方法声明。</p>

<h2>第五步：注册包路径与编译部署</h2>
<ol class="step-list">
    <li>启动类 <code>CallUtils.init</code> 的第二个参数改为你的 service 包，例如 <code>Collections.singletonList("org.sunrise.game.myservice.service")</code></li>
    <li>修改/新增 <code>game/pom.xml</code> 中 <code>maven-assembly-plugin</code> 条目（可复制 HttpServer 段），指定 <code>mainClass</code> 与 <code>finalName</code></li>
    <li><code>mvn clean package -pl game</code>，产物输出到 <code>start/jar/</code></li>
    <li>编写启动脚本，例如 <code>start/windows/single/my-service.bat</code>：<br/>
        <code>java -jar ../../jar/sunrise-my-service.jar ../../../config/my-service-config.properties 50</code></li>
    <li>先启动 CenterServer，再启动新服务；查看 <code>rpc_server_system</code> 是否插入对应 id/port</li>
</ol>

<h2>第六步：从其他节点调用</h2>
<p>调用方任意已接入集群的进程（Game、External、GmBack 等）均可使用 <code>RpcFunction</code> + <code>CallEnum</code>。</p>

<h3>随机调用（默认）</h3>
<p>在注册了该方法的节点中随机选一个；若本机也注册了该方法则优先本地执行。</p>
<pre><code class="language-java">RpcFunction.newInstance().call(
    CallEnum.MyNotifyService_ping,
    "fromNodeId", RpcNodeManager.getRpcServerNodeId()
);</code></pre>

<h3>带回调的调用</h3>
<pre><code class="language-java">RpcFunction rpc = RpcFunction.newInstance();
rpc.call(CallEnum.MyNotifyService_ping, "fromNodeId", "testNode");
rpc.listenResult(result -> {
    if (result.getResult() != ErrorType.SUCCESS) return;
    Boolean ok = (Boolean) result.getData("ok");
});
</code></pre>
<p>注意：<code>call()</code> 的键值对个数必须等于目标方法参数个数，<strong>顺序与方法签名一致</strong>；键名本身无意义。回调取返回值时，<code>getData("key")</code> 的 key 须与远端 <code>returns()</code> 一致。</p>


<h2>更多</h2>
<ul>
    <li><a href="#/rpc">RPC 框架</a> — 调用模式、消息结构、ServiceManager 生命周期</li>
    <li><a href="#/code-gen">代码生成</a> — GenRpcStartUp、CallEnum 生成规则</li>
    <li><a href="#/config">配置参考</a> — jdbc / master / report 等通用项</li>
</ul>
`);
