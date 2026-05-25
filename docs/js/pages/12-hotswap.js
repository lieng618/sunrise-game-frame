registerPage('hotswap', '代码热更', 'JVM Agent 类热更、GM 后台触发流程', () => `
<h1>代码热更</h1>
<p class="page-desc">通过 <code>hotswap</code> 模块在运行时替换已加载的 .class</p>

<h2>前置条件</h2>
<ol class="step-list">
    <li>编译 <code>hotswap</code> 模块，生成 <code>start/jar/hotswap-agent.jar</code></li>
    <li>Game 进程启动时必须挂载 Java Agent：<code>-javaagent:hotswap-agent.jar</code>（单进程脚本 <code>runallone.bat</code> 已内置，可参考使用）</li>
    <li>执行 <code>mvn package</code> 将新代码打入 JAR，在 gmback-ui「代码热更」页面填写 JAR 绝对路径触发热加载</li>
    <li>JAR 路径必须是 <strong>Game 进程所在机器</strong> 可访问的绝对路径</li>
</ol>

<h2>hotswap 模块结构</h2>
<table>
<thead><tr><th>类</th><th>说明</th></tr></thead>
<tbody>
<tr><td><code>Hotswap</code></td><td>Agent 入口 <code>premain</code>，持有 <code>Instrumentation</code>，通过 <code>redefineClasses</code> 替换字节码</td></tr>
<tr><td><code>HotswapScanner</code></td><td>记录 JAR 内每个 .class 的 CRC/大小，对比变更后仅热更差异类</td></tr>
</tbody>
</table>

<h2>GM 后台触发流程</h2>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-danger">gmback-ui<br/>/hotswap-jar</span>
        <span class="flow-arrow">→ POST /api/hotswap/jar →</span>
        <span class="flow-node flow-node-danger">HotswapController</span>
        <span class="flow-arrow">→ RPC SendAll / 指定节点 →</span>
        <span class="flow-node flow-node-secondary">GameRecvGmBackMessageService</span>
        <span class="flow-arrow">→</span>
        <span class="flow-node flow-node-primary">HotswapScanner、Hotswap</span>
    </div>
</div>

<h3>REST API</h3>
<table>
<thead><tr><th>接口</th><th>方法</th><th>请求体</th><th>说明</th></tr></thead>
<tbody>
<tr><td>/api/hotswap/jar</td><td>POST</td><td><code>{ "jarPath": "...", "nodeId": "可选" }</code></td><td>向 Game 节点发送代码热更指令；省略 nodeId 则广播全部 GameServer</td></tr>
</tbody>
</table>

<h3>RPC 消息格式</h3>
<pre><code class="language-java">// GmBack → Game
operation = "hotswapJar"
// 以windows平台举例
data = {"jarPath": "E:/sunrise-game-frame/start/jar/sunrise-game.jar"}

// Game 侧处理
HotswapScanner scanner = new HotswapScanner(jarPath);
scanner.reloadClasses();</code></pre>

<h2>推荐操作步骤</h2>
<ol class="step-list">
    <li>修改 game 模块业务代码</li>
    <li>在项目根目录执行 <code>mvn package -pl game -am</code>，生成新的 <code>start/jar/sunrise-game.jar</code></li>
    <li>登录 gmback-ui → 侧栏「代码热更」（<code>/hotswap-jar</code>）</li>
    <li>填写 JAR 绝对路径</li>
    <li>选择目标节点或「全部热更」</li>
    <li>在 Game 服日志中查看 <code>Hotswap jar [...] result</code> 输出</li>
</ol>

<h2>限制与注意</h2>
<ul>
    <li>仅支持已加载且可 redefine 的类；新增类、修改方法签名、字段结构变化等可能失败或行为异常</li>
    <li>未挂载 <code>-javaagent</code> 时 <code>Instrumentation</code> 为空，热更会失败</li>
    <li>多 Game 节点时，每个节点需能访问各自机器上的 JAR 路径（路径一致时需在各机同步 JAR 文件）</li>
</ul>
`);
