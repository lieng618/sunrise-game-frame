registerPage('hotswap', '代码热更完整指南', '全模块热更的构建、Agent 配置、GM 上传、节点结果、回滚与限制', () => `
<h1>代码热更完整指南</h1>
<p class="page-desc">
    本文对应当前<strong>全模块、全部署形态</strong>热更实现。单进程、多进程、服务组合部署和 Docker
    部署共用同一套补丁与执行流程。
</p>

<div class="callout callout-info">
    <p><strong>核心原则：</strong>每个 JVM 根据自己的 ClassLoader 和已加载类建立基线，同一个
    <code>sunrise-hotfix.jar</code> 发给所有节点。每个节点只重定义自己已经加载、允许修改且字节码发生变化的类。</p>
</div>

<h2>支持范围</h2>
<table>
<thead><tr><th>场景</th><th>支持情况</th><th>说明</th></tr></thead>
<tbody>
<tr><td>RunAllOne 单进程</td><td>支持</td><td>一次更新该 JVM 中已加载的 Game、External、Global、Http、GmBack 等业务类</td></tr>
<tr><td>多服务多进程</td><td>支持</td><td>同一个补丁发送到所有在线服务节点，各节点只处理自己的已加载类</td></tr>
<tr><td>Game + External 同进程</td><td>支持</td><td>不要求把进程拆开，热更范围由实际加载类决定</td></tr>
<tr><td>Windows、Linux PM2</td><td>支持</td><td>启动配置均已加入 <code>-javaagent</code></td></tr>
<tr><td>Docker Compose</td><td>支持</td><td>各容器共享 <code>hotfix_data</code> 卷，可读取同一补丁</td></tr>
<tr><td>CenterServer</td><td>支持</td><td>配置 <code>hotswap.center-dashboard-url</code> 后纳入 GM 批量热更</td></tr>
<tr><td>GM 前端、客户端工具</td><td>不支持 Java 热更</td><td>需要重新构建、部署或发布客户端版本</td></tr>
</tbody>
</table>

<h2>热更产物</h2>
<p>在项目根目录执行完整构建后，<code>start/jar/</code> 中会生成以下关键文件：</p>
<table>
<thead><tr><th>文件</th><th>用途</th><th>是否用于 GM 热更</th></tr></thead>
<tbody>
<tr>
    <td><code>hotswap-agent.jar</code></td>
    <td>最小 Java Agent 引导包，只包含 <code>AgentBootstrap</code>，负责取得 <code>Instrumentation</code></td>
    <td>否，启动时加载</td>
</tr>
<tr>
    <td><code>sunrise-lib.jar</code></td>
    <td>共享运行库，包含热更内核、扫描器、ClassLoader 注入和通用热更控制服务</td>
    <td>否，启动时加载</td>
</tr>
<tr>
    <td><code>sunrise-hotfix.jar</code></td>
    <td>全量业务补丁，包含 game、network、gen、center 的可热更 class</td>
    <td>是，GM 应选择此文件</td>
</tr>
<tr>
    <td><code>sunrise-game.jar</code></td>
    <td>多进程 Game 启动包</td>
    <td>否，启动时加载</td>
</tr>
<tr>
    <td><code>sunrise-runallone.jar</code></td>
    <td>单进程启动包，包含 Game、External、Global、Http、GmBack 等入口</td>
    <td>否，启动时加载</td>
</tr>
</tbody>
</table>

<div class="callout callout-warn">
    <p><strong>GM 热更统一选择 <code>sunrise-hotfix.jar</code>。</strong>
    它是包含全部可热更业务模块的全量补丁；<code>sunrise-game.jar</code> 只用于启动 Game 进程。</p>
</div>

<h2>首次启动准备</h2>
<p>首次部署时先构建并部署完整的运行包，然后启动全部服务。服务启动后即可通过 GM 执行后续的普通代码热更。</p>

<h3>1. 构建项目</h3>
<pre><code class="language-bash">mvn clean test
mvn clean package

cd gmback-ui
npm install
npm run build</code></pre>

<p>构建完成后确认以下文件存在且不是旧时间戳：</p>
<pre><code class="language-bash">start/jar/hotswap-agent.jar
start/jar/sunrise-lib.jar
start/jar/sunrise-hotfix.jar
start/jar/sunrise-game.jar
start/jar/sunrise-runallone.jar</code></pre>

<h3>2. 部署运行文件</h3>
<p>把构建产物部署到各服务节点：</p>
<ul>
    <li><code>hotswap-agent.jar</code></li>
    <li><code>sunrise-lib.jar</code></li>
    <li>对应的启动 JAR，例如 <code>sunrise-game.jar</code>、<code>sunrise-runallone.jar</code> 等</li>
    <li><code>sunrise-hotfix.jar</code></li>
    <li><code>gmback-ui/dist/</code></li>
</ul>
<p>部署或更新运行文件时需要停止对应进程；Windows 下 JVM 占用的 JAR 不能直接覆盖。</p>

<h3>3. 确认 Agent 启动参数</h3>
<p>每个需要热更的 JVM 都必须包含：</p>
<pre><code class="language-bash">-javaagent:/absolute/path/to/hotswap-agent.jar</code></pre>

<p>仓库内已为以下启动方式统一配置：</p>
<table>
<thead><tr><th>平台</th><th>已修改文件</th></tr></thead>
<tbody>
<tr><td>Windows</td><td><code>start/windows/single/*.bat</code></td></tr>
<tr><td>Linux PM2</td><td><code>start/linux/single/*.json</code></td></tr>
<tr><td>Docker</td><td><code>start/docker/docker-compose.yml</code></td></tr>
</tbody>
</table>

<p>如果使用自定义启动命令，必须自行添加上述参数。Agent 和共享库通常应放在同一目录：</p>
<pre><code class="language-bash">start/jar/
├── hotswap-agent.jar
├── sunrise-lib.jar
├── sunrise-game.jar
└── sunrise-runallone.jar</code></pre>

<p>如果 Agent 与 <code>sunrise-lib.jar</code> 不在同一目录，可在启动参数中指定：</p>
<pre><code class="language-bash">java \
  -Dsunrise.hotswap.runtime=/opt/sunrise/lib/sunrise-lib.jar \
  -javaagent:/opt/sunrise/agent/hotswap-agent.jar \
  -jar sunrise-game.jar \
  config/game-config.properties</code></pre>

<h3>4. 启动服务并确认基线</h3>
<p>正常启动后，日志中会出现类似输出：</p>
<pre><code>[hotswap] 热更基线已建立，来源 JAR 数=2，业务类数=..., 来源=[..., ...]</code></pre>
<p>该日志表示当前进程已经根据实际加载类建立旧代码快照。若出现“hotswap agent 未加载”，说明启动参数缺失或 Agent 文件路径错误。</p>

<h2>热更配置</h2>
<p>各服务配置文件中可以设置以下参数。未填写时使用默认值。</p>
<table>
<thead><tr><th>配置项</th><th>默认值</th><th>说明</th></tr></thead>
<tbody>
<tr>
    <td><code>hotswap.include.packages</code></td>
    <td><code>org.sunrise.game</code></td>
    <td>允许热更的包前缀，逗号分隔</td>
</tr>
<tr>
    <td><code>hotswap.exclude.classes</code></td>
    <td>热更内核、控制服务、CallEnum 等</td>
    <td>排除的类或包前缀，逗号分隔</td>
</tr>
<tr>
    <td><code>hotswap.upload-dir</code></td>
    <td><code>../../../hotfix/uploaded</code></td>
    <td>GM 上传补丁的保存目录；多机部署必须设置为所有节点可访问的共享目录</td>
</tr>
<tr>
    <td><code>hotswap.center-dashboard-url</code></td>
    <td>空</td>
    <td>Center Dashboard 地址，例如 <code>http://127.0.0.1:8088</code></td>
</tr>
</tbody>
</table>

<h3>推荐配置</h3>
<pre><code class="language-properties">hotswap.include.packages=org.sunrise.game
hotswap.upload-dir=/data/sunrise/hotfix/uploaded
hotswap.center-dashboard-url=http://127.0.0.1:8088</code></pre>

<h3>CenterServer 专属配置</h3>
<p>CenterServer 不是普通 RPC 业务节点，GM 通过它现有的 Dashboard HTTP 端口调用热更接口。因此配置分在两个文件中：</p>
<table>
<thead><tr><th>文件</th><th>配置项</th><th>作用</th></tr></thead>
<tbody>
<tr>
    <td><code>config/gmback-config.properties</code></td>
    <td><code>hotswap.center-dashboard-url=http://127.0.0.1:8088</code></td>
    <td>告诉 GMBack 如何访问 Center Dashboard</td>
</tr>
<tr>
    <td><code>config/center-config.properties</code></td>
    <td><code>dashboard.port=8088</code></td>
    <td>开启 Center 的热更 HTTP 接口</td>
</tr>
<tr>
    <td><code>config/center-config.properties</code></td>
    <td><code>hotswap.include.packages=org.sunrise.game</code></td>
    <td>指定 Center 进程允许热更的包范围</td>
</tr>
</tbody>
</table>

<p>如果 GMBack 和 Center 在同一台机器，可以使用 <code>127.0.0.1</code>；如果分机部署，必须填写 GMBack 进程能够访问的 Center IP 或域名：</p>
<pre><code class="language-properties"># config/gmback-config.properties
hotswap.center-dashboard-url=http://10.0.0.20:8088</code></pre>

<p>也可以通过环境变量配置，配置项本身保留如下形式：</p>
<pre><code class="language-properties">hotswap.center-dashboard-url=\${CENTER_DASHBOARD_URL:}</code></pre>
<pre><code class="language-bash">export CENTER_DASHBOARD_URL=http://10.0.0.20:8088</code></pre>

<p>本地多进程启动时，配置完上述两个文件后分别启动 GMBack 和 Center。两个启动脚本都已带 <code>-javaagent</code>：</p>
<pre><code class="language-bash">cd start\\windows\\single
.\\center.bat
.\\gmback.bat</code></pre>

<p>Docker 部署已经自动设置为容器内地址，无需手工填写：</p>
<pre><code class="language-yaml">CENTER_DASHBOARD_URL: http://center:8088</code></pre>

<div class="callout callout-info">
    <p><strong>执行范围：</strong>Center 不出现在普通 RPC 节点列表中。GM 点击“全部热更”或使用上传热更时，
    GMBack 会在更新所有 RPC 服务节点后再调用 Center Dashboard，并在结果表中追加一条
    <code>CenterServer</code> 结果。指定单个 Game/External 节点热更时，不会同时更新 Center。</p>
</div>

<p>确认 Center 热更基线已建立：</p>
<pre><code class="language-bash">[hotswap] 热更基线已建立，来源 JAR 数=2，业务类数=..., 来源=[..., ...]</code></pre>

<p>如果只需要单独验证 Center，可以直接调用它的 Dashboard 接口：</p>
<pre><code class="language-bash">curl -X POST http://127.0.0.1:8088/api/hotswap/reload \\
  -H "Content-Type: application/json" \\
  -d '{"operation":"reload","jarPath":"/data/sunrise/hotfix/uploaded/sunrise-hotfix.jar"}'</code></pre>

<p>热更内核默认排除以下内容：</p>
<ul>
    <li><code>org.sunrise.game.core.Hotswap</code></li>
    <li><code>org.sunrise.game.core.HotswapScanner</code></li>
    <li><code>org.sunrise.game.core.AgentBootstrap</code></li>
    <li><code>org.sunrise.game.rpc.hotswap</code></li>
    <li><code>org.sunrise.game.genRpc.gen.CallEnum</code></li>
</ul>
<p>这些类负责接收热更命令、维护 Instrumentation 或保存 RPC ID。运行中替换它们可能导致控制入口失效，应通过重启升级。</p>

<h2>日常热更完整步骤</h2>
<ol class="step-list">
    <li>
        <strong>修改代码。</strong>
        当前可靠范围是已加载类的方法体、局部变量和常量池调整。保持类名、字段、方法签名和继承关系不变。
    </li>
    <li>
        <strong>运行测试并构建全量补丁。</strong>
        服务继续运行时，不要执行会覆盖运行中 JAR 的完整 <code>clean package</code>。先只编译到 <code>target/classes</code>，再单独组装补丁：
        <pre><code class="language-bash"># game 及其依赖 network/gen
mvn -pl game -am -DskipTests compile

# 如果同时修改了 center
mvn -pl game,center -am -DskipTests compile

# 只更新 sunrise-hotfix.jar，不覆盖正在运行的启动 JAR
mvn -pl hotfix -DskipTests package</code></pre>
        最终补丁为 <code>start/jar/sunrise-hotfix.jar</code>。
    </li>
    <li>
        <strong>确认补丁内容。</strong>
        全量补丁应同时包含所有服务入口以及 network/gen/center 类。不要误用只有 game 模块的旧薄包。
    </li>
    <li>
        <strong>登录 GM 后台。</strong>
        进入“代码热更”页面。页面会列出全部在线服务节点。
    </li>
    <li>
        <strong>上传补丁并执行。</strong>
        点击“选择 JAR 并执行”，选择 <code>sunrise-hotfix.jar</code>。GM 会保存文件、逐个调用在线节点，并等待真实结果。
    </li>
    <li>
        <strong>核对每个节点结果。</strong>
        成功状态应重点检查 <code>SUCCESS</code>、<code>PARTIAL</code> 和 <code>FAILED</code>，
        不能只看 GM 是否接受了 HTTP 请求。
    </li>
    <li>
        <strong>进行业务验证。</strong>
        触发被修改的功能，确认新行为生效，同时观察错误日志、节点在线状态和异常指标。
    </li>
</ol>

<div class="callout callout-warn">
    <p><strong>多机部署：</strong>浏览器上传的文件默认只保存在 GM 服所在机器。其他机器的服务进程必须能访问同一路径；
    生产环境应把 <code>hotswap.upload-dir</code> 配置为 NFS、共享盘或统一的补丁分发目录。</p>
</div>

<div class="callout callout-warn">
    <p><strong>不要在服务运行时执行 <code>mvn clean package</code>。</strong>
    该命令会覆盖 <code>sunrise-lib.jar</code> 和启动 JAR。JVM 之后再次懒加载类时可能抛出
    <code>NoClassDefFoundError</code>，导致业务线程退出。完整打包必须先停止服务；在线热更只生成
    <code>sunrise-hotfix.jar</code>。</p>
</div>

<h2>GM 页面操作说明</h2>
<h3>上传并热更</h3>
<p>适合直接选择本机 JAR：</p>
<ol class="step-list">
    <li>点击“选择 JAR 并执行”</li>
    <li>选择 <code>sunrise-hotfix.jar</code></li>
    <li>等待所有节点返回结果</li>
    <li>在“最近一次热更结果”表中查看每个节点的状态</li>
</ol>

<h3>服务器路径热更</h3>
<p>如果已经通过共享盘或其他发布系统上传补丁，也可以在“JAR 包路径”中填写服务节点可见的绝对路径，然后点击“全部热更”或单节点“热更”。</p>

<h3>结果状态</h3>
<table>
<thead><tr><th>状态</th><th>含义</th><th>处理建议</th></tr></thead>
<tbody>
<tr><td><code>UNINITIALIZED</code></td><td>Center 状态接口尚无扫描器或基线</td><td>检查 Agent、启动日志与配置</td></tr>
<tr><td><code>READY</code></td><td>进程已加载 Agent 并建立热更基线，尚无热更结果</td><td>正常，可执行热更或查询状态</td></tr>
<tr><td><code>SUCCESS</code></td><td>本节点实际重定义了至少一个类，没有跳过或失败项</td><td>继续业务验证</td></tr>
<tr><td><code>PARTIAL</code></td><td>部分类已更新，但仍有未加载或不支持的类</td><td>确认未更新类是否属于当前节点功能</td></tr>
<tr><td><code>NO_CHANGE</code></td><td>补丁与当前已应用代码一致</td><td>检查是否上传了旧 JAR</td></tr>
<tr><td><code>NO_APPLICABLE</code></td><td>补丁有变化，但当前节点没有对应的已加载类</td><td>对未运行该模块的节点通常正常</td></tr>
<tr><td><code>FAILED</code></td><td>Agent、RPC、JAR、基线或类重定义失败</td><td>查看该节点 message、failedClasses 和服务日志</td></tr>
</tbody>
</table>

<h2>执行原理</h2>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-danger">GM 上传/路径</span>
        <span class="flow-arrow">→</span>
        <span class="flow-node flow-node-secondary">HotswapController</span>
        <span class="flow-arrow">→ 逐个节点 RPC →</span>
        <span class="flow-node flow-node-secondary">HotswapControlService</span>
        <span class="flow-arrow">→</span>
        <span class="flow-node flow-node-primary">HotswapScanner.reload</span>
    </div>
</div>

<ol class="step-list">
    <li>Agent 通过 <code>premain</code> 注入 <code>Instrumentation</code></li>
    <li>服务启动时扫描当前 JVM 已加载业务类的真实 <code>CodeSource</code></li>
    <li>从实际来源 JAR 记录旧版本 class 的 SHA-256、大小和来源路径</li>
    <li>热更时扫描全量补丁中的业务 class</li>
    <li>仅选择当前已经加载、ClassLoader 匹配且允许修改的类</li>
    <li>对比 SHA-256，收集发生变化的 class</li>
    <li>通过 <code>Instrumentation.redefineClasses</code> 批量替换字节码</li>
    <li>成功后更新该进程的当前基线，失败时不把新 JAR 记为已应用</li>
</ol>

<p>该流程不依赖 JVM 是通过 <code>-jar sunrise-game.jar</code> 还是 <code>-jar sunrise-runallone.jar</code> 启动，
而是按类实际来自 Game JAR、RunAllOne JAR 或共享库来识别旧代码。</p>

<h2>REST API</h2>
<h3>上传并热更</h3>
<pre><code>POST /api/hotswap/upload
Content-Type: multipart/form-data
Authorization: &lt;GM token&gt;

file=&lt;sunrise-hotfix.jar&gt;</code></pre>
<p>可选表单参数 <code>nodeId</code>；不传时对所有在线节点执行。</p>

<h3>按服务器路径热更</h3>
<pre><code class="language-json">POST /api/hotswap/jar
{
  "jarPath": "/data/sunrise/hotfix/uploaded/sunrise-hotfix.jar",
  "nodeId": "可选，省略则全部节点",
  "operation": "reload"
}</code></pre>

<h3>状态与回滚</h3>
<pre><code class="language-json">POST /api/hotswap/jar
{
  "operation": "status",
  "nodeId": "可选"
}</code></pre>

<pre><code class="language-json">POST /api/hotswap/jar
{
  "operation": "rollback",
  "nodeId": "可选"
}</code></pre>

<p>回滚使用该进程记录的上一份已成功应用补丁。回滚同样受“已加载、可修改、类结构兼容”限制。</p>

<h3>Center Dashboard</h3>
<pre><code>GET  /api/hotswap/status
POST /api/hotswap/reload</code></pre>
<p>Center 进程通过 <code>hotswap.center-dashboard-url</code> 接入 GM 批量热更。</p>

<h2>返回结果示例</h2>
<pre><code class="language-json">{
  "operation": "reload",
  "targetJar": "/app/hotfix/uploaded/123456-sunrise-hotfix.jar",
  "total": 3,
  "succeeded": 2,
  "failed": 1,
  "results": [
    {
      "nodeId": "game-node-1",
      "nodeType": "GameServer",
      "status": "SUCCESS",
      "targetVersion": "sha256-...",
      "requestedClasses": ["org.sunrise.game.game.logic.ItemSystem"],
      "redefinedClasses": ["org.sunrise.game.game.logic.ItemSystem"],
      "skippedNotLoadedClasses": [],
      "failedClasses": []
    },
    {
      "nodeId": "external-node-1",
      "nodeType": "ExternalServer",
      "status": "NO_APPLICABLE",
      "requestedClasses": ["org.sunrise.game.game.logic.ItemSystem"],
      "redefinedClasses": []
    },
    {
      "nodeId": "http-node-1",
      "nodeType": "HttpServer",
      "status": "FAILED",
      "message": "hotswap agent 未加载"
    }
  ]
}</code></pre>

<h2>Docker 热更</h2>
<p>Docker Compose 已配置 <code>hotfix_data</code> 共享卷：</p>
<ul>
    <li>GMBack 容器挂载为可写：<code>/app/hotfix</code></li>
    <li>其他服务容器挂载为只读：<code>/app/hotfix</code></li>
    <li>GMBack 环境变量使用 <code>HOTSWAP_UPLOAD_DIR=/app/hotfix/uploaded</code></li>
    <li>GMBack 环境变量使用 <code>CENTER_DASHBOARD_URL=http://center:8088</code></li>
</ul>
<p>因此从 GM 页面上传后，Game、External、Global、Http、GmBack、Center 容器都能读取同一个补丁路径。</p>

<pre><code class="language-bash">cd start/docker
docker compose up -d --build
docker compose logs -f gmback game center</code></pre>

<h2>失败排查</h2>
<h3>hotswap agent 未加载</h3>
<p>检查进程完整启动命令是否包含 <code>-javaagent</code>，并检查 Agent 文件是否存在、是否可读。Agent 路径不可使用仅在某些机器存在的网络盘。</p>

<h3>热更基线尚未初始化</h3>
<p>检查 <code>hotswap-agent.jar</code> 与 <code>sunrise-lib.jar</code> 是否来自同一次构建，并确认共享库已经部署到每个节点。版本不一致时重新部署这两个文件并重启进程。</p>

<h3>NO_APPLICABLE</h3>
<p>说明补丁中有代码变化，但当前节点没有加载对应类。对只运行 Game 的进程，更新了 GmBack 类时出现该状态是正常的。</p>

<h3>PARTIAL</h3>
<p>查看 <code>skippedNotLoadedClasses</code> 和 <code>skippedUnsupportedClasses</code>。未加载类可以等业务真正使用后再尝试；不可修改类需要检查是否被排除或属于受限类。</p>

<h3>FAILED / redefineClasses 异常</h3>
<p>常见原因是修改了字段、方法签名、父类、接口或枚举结构。JVM 不允许通过当前流程改变这些结构，需要版本化 JAR 和滚动重启。</p>

<h3>上传成功但其他节点找不到文件</h3>
<p>检查 <code>hotswap.upload-dir</code>。它必须是目标 JVM 能访问的同一绝对路径，而不是仅 GM 浏览器机器上的本地路径。</p>

<h3>Center 没有被更新</h3>
<p>检查 <code>hotswap.center-dashboard-url</code> 是否配置，Center Dashboard 端口是否开放，并确认 Center 启动时也加载了 Agent。</p>

<h2>上线检查清单</h2>
<ul>
    <li>所有服务启动参数都有 <code>-javaagent</code></li>
    <li>所有节点使用同一版本的 <code>hotswap-agent.jar</code> 和 <code>sunrise-lib.jar</code></li>
    <li>启动日志已出现热更基线建立信息</li>
    <li>GM 节点列表包含 Game、External、Global、Http、GmBack，配置后包含 Center</li>
    <li>补丁使用 <code>sunrise-hotfix.jar</code></li>
    <li>多机环境所有节点都能访问上传目录</li>
    <li>热更后每个节点返回的结果已逐项核对</li>
    <li>已验证业务功能、错误日志和在线状态</li>
    <li>保留上一个成功补丁，确认回滚路径可用</li>
</ul>

<h2>限制与安全要求</h2>
<div class="callout callout-warn">
    <p><strong>代码热更等同于在当前 JVM 中执行受信任代码。</strong>必须限制 GM 后台“代码热更”权限，
    校验补丁来源和 SHA-256，禁止上传不受信任 JAR。</p>
</div>
<ul>
    <li>不能新增或删除类；新增类需要使用新的 JAR 和重启</li>
    <li>不能新增/删除字段、方法，不能修改方法签名、父类、接口和枚举结构</li>
    <li>不保证静态状态、缓存和已创建实例自动重建；业务代码需要自行处理刷新</li>
    <li>RPC 方法签名变化时，即使类字节码被替换，RPC 注册表和调用 ID 也不会自动重建</li>
    <li>第三方依赖、Agent、热更控制服务不参与普通热更</li>
    <li>前端资源、数据库结构、配置表的部署方式不同，分别使用前端发布、SQL 迁移和配置重载</li>
</ul>

<h2>推荐发布策略</h2>
<ol class="step-list">
    <li>普通方法体修复：使用本文热更流程，所有节点返回结果后做业务验证。</li>
    <li>涉及类结构、RPC 签名、枚举、静态初始化或第三方库：构建新版本 JAR，执行滚动重启。</li>
    <li>集群升级顺序建议：External → Game → Global/Http/GmBack → Center；实际顺序仍应结合业务依赖调整。</li>
    <li>出现无法回滚的结构变更时，立即停止继续热更，使用上一版本运行包滚动重启。</li>
    <li>每次热更保留补丁、目标 SHA-256、各节点结果和业务验证记录。</li>
</ol>
`);
