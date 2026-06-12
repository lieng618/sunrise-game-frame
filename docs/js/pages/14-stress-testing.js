registerPage('stress-testing', '压测统计', '分阶段登录耗时、发包 TPS、端到端诊断', () => `
<h1>压测工具与统计</h1>
<p class="page-desc">压测客户端（<code>sunrise-stress.jar</code>），支持批量建连登录的分阶段耗时统计，以及以<strong>服务器回包</strong>为准的发包 TPS 压测。</p>

<div class="callout callout-tip">
    <p><strong>与压测机器人的区别</strong>：<a href="#/client-tools">压测机器人</a>（<code>bot.bat</code>）侧重批量在线、定时 Ping、向所有机器人广播自定义包；本工具侧重<strong>登录各阶段耗时</strong>与<strong>可重复的发包 TPS 基准</strong>。二者共用 <code>client-config.properties</code> 与同一套登录流程。</p>
</div>

<h2>启动</h2>
<pre><code class="language-bash"># 先启动服务器（推荐本地单进程调试）
start\\windows\\single\\runallone.bat

# 压测工具（Windows）
start\\windows\\stress.bat

# 或在 IDE 中运行主类
# stress.main.StressStartUp</code></pre>
<p>产物：<code>start/jar/sunrise-stress.jar</code>，配置同 <code>config/client-config.properties</code>（协议类型 <code>client.socket</code>：tcp / websocket / kcp）。</p>

<h2>界面功能</h2>
<table>
<thead><tr><th>区域</th><th>说明</th></tr></thead>
<tbody>
<tr><td>客户端控制</td><td>批量添加/移除压测客户端（uid 前缀 <code>stress</code>），停止全部</td></tr>
<tr><td>实时统计</td><td>总数、已取地址、登录成功、登录失败</td></tr>
<tr><td>发包压测</td><td>选择 Ping 或业务包（获取背包），填写发包总数，一键开始</td></tr>
<tr><td>日志区</td><td>阶段耗时、发包进度、TPS 结果、客户端/服务端诊断提示</td></tr>
</tbody>
</table>

<h2>分阶段登录统计</h2>
<p>每批调用「添加客户端」时，<code>StressManager</code> 按两阶段汇总并在日志输出：</p>
<ol class="step-list">
    <li><strong>阶段 1 — 获取对外服地址</strong>：并发 HTTP 请求 <code>/external_address</code>，全部完成后输出 <code>成功数/本批人数</code> 与总耗时（ms）。</li>
    <li><strong>阶段 2 — 连接并登录</strong>：对阶段 1 成功的客户端连接对外服，自动走完 认证 → C2S_Login → 角色列表 → 选角；以收到选角完成为「登录成功」。本批全部选角结束后输出 <code>成功数/预期人数</code> 与从「开始连接」到「全部选角完成」的总耗时（ms）。</li>
</ol>
<p>登录成功后工具会每 10 秒发送 <code>C2S_ClientPing</code> 保活，避免 60 秒无心跳被踢。</p>

<h2>发包压测流程</h2>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-primary">已登录客户端</span>
        <span class="flow-arrow">→</span>
        <span class="flow-node flow-node-secondary">按人数均分发包总数</span>
        <span class="flow-arrow">→</span>
        <span class="flow-node flow-node-secondary">每连接 EventLoop 发送<br/>（在途上限 64）</span>
        <span class="flow-arrow">→</span>
        <span class="flow-node flow-node-warning">对外服 → RPC → 游戏服</span>
        <span class="flow-arrow">→</span>
        <span class="flow-node flow-node-primary">收齐回包统计 TPS</span>
    </div>
</div>

<p><code>external.rate-limit.per-minute</code>（对外服配置）设置单连接每分钟从客户端接收的最大消息数；压测时需按目标 TPS 调大，避免对外服限流拦截。</p>

<h3>发包模式</h3>
<table>
<thead><tr><th>模式</th><th>上行</th><th>计 TPS 的回包</th></tr></thead>
<tbody>
<tr><td>Ping 包</td><td><code>C2S_ClientPing</code></td><td><code>S2C_ClientPing</code></td></tr>
<tr><td>业务包</td><td><code>C2S_GetItemList</code></td><td><code>S2C_ItemList</code></td></tr>
</tbody>
</table>

<h3>客户端优化（高频发包）</h3>
<ul>
    <li>预序列化 <code>MBasePacketData</code>，循环内复用 <code>SocketMessage</code> 写入，减少 GC。</li>
    <li>每连接<strong>在途未回包上限 64</strong>（<code>MAX_INFLIGHT_PER_CLIENT</code>），通道不可写时延迟重试，避免打满写缓冲。</li>
    <li>收包<strong>快速路径</strong>（<code>tryFastRouteStressResponse</code>）：仅扫描 Protobuf 的 topic/packetId，跳过 Router 与 packet_data 解析。</li>
</ul>

<h2>统计指标说明</h2>
<p>压测结束时日志字段见下表；具体数值见文末「本机测试记录」。</p>
<table>
<thead><tr><th>指标</th><th>含义</th><th>计算方式</th></tr></thead>
<tbody>
<tr><td>发送耗时</td><td>从首包发出到本批全部发完</td><td><code>sendEndTime - sendStartTime</code></td></tr>
<tr><td>总耗时（全程）</td><td>从压测发起到收齐最后一个回包</td><td><code>lastResponseTime - sendStartTime</code></td></tr>
<tr><td>回包窗口</td><td>首包回包到末包回包</td><td><code>lastResponseTime - firstResponseTime</code></td></tr>
<tr><td>TPS（全程）</td><td>含发送阶段的平均吞吐</td><td><code>已收到回包数 × 1000 / 总耗时</code></td></tr>
<tr><td>TPS（仅回包窗口）</td><td>服务器处理+回传阶段的吞吐</td><td><code>已收到回包数 × 1000 / 回包窗口</code></td></tr>
<tr><td>在途未回包</td><td>已发送尚未收到对应回包的数量</td><td>各连接 <code>inflight</code> 之和；停滞时会打诊断日志</td></tr>
</tbody>
</table>
<p>进度日志每 5 秒输出一次；发送完毕 30 秒仍未收齐会提示等待；连续 10 秒 <code>received</code> 不增长或超时（默认 30 分钟）会输出<strong>客户端诊断</strong>并建议对照服务端日志。</p>

<h2>本机测试记录</h2>
<p> Windows 10，Intel i7-8700，6 核 12 线程 @ 3.20GHz，内存 约 64 GB。</p>
<p>启动 RunAllOne 单进程、客户端连接方式为 TCP。启动服务器后不再关闭，一次运行内连续压测各档位（压测多次求平均）。</p>
<p>以下测试为客户端进行了完整的登录流程，登录成功后再进行发包。由对外服务接收到消息，转发到游戏服处理。</p>
<p>Ping包和业务包数据相差较多，原因是业务包需要在LogicUtils.handler()中通过反射调用到具体的消息处理函数，以当前业务为例，会调用到ItemMsgHandler中，并且需要构建物品信息发送，新号初始设置了10个物品。</p> 

<h3>Ping 包（<code>C2S_ClientPing</code> / <code>S2C_ClientPing</code>）</h3>
<table>
<thead>
<tr>
    <th>在线人数</th>
    <th>发包总数</th>
    <th>发送耗时(ms)</th>
    <th>总耗时(ms)</th>
    <th>回包窗口(ms)</th>
    <th>TPS(全程)</th>
    <th>TPS(回包窗口)</th>
</tr>
</thead>
<tbody>
<tr><td>100</td><td>10000</td><td>36</td><td>49</td><td>36</td><td>204081.63</td><td>277777.78</td></tr>
<tr><td>100</td><td>50000</td><td>219</td><td>230</td><td>218</td><td>217391.30</td><td>229357.80</td></tr>
<tr><td>100</td><td>100000</td><td>410</td><td>423</td><td>407</td><td>236406.62</td><td>245700.25</td></tr>
<tr><td>300</td><td>100000</td><td>345</td><td>354</td><td>325</td><td>282485.88</td><td>307692.31</td></tr>
<tr><td>500</td><td>300000</td><td>996</td><td>1010</td><td>970</td><td>297029.70</td><td>309278.35</td></tr>
<tr><td>1000</td><td>500000</td><td>1668</td><td>1706</td><td>1625</td><td>293083.24</td><td>307692.31</td></tr>
<tr><td>2000</td><td>1000000</td><td>3352</td><td>3385</td><td>3291</td><td>295420.97</td><td>303859.01</td></tr>
<tr><td>2000</td><td>1500000</td><td>4852</td><td>4883</td><td>4733</td><td>307188.20</td><td>316923.73</td></tr>
<tr><td>2000</td><td>2000000</td><td>6498</td><td>6512</td><td>6380</td><td>307125.31</td><td>313479.62</td></tr>
</tbody>
</table>

<h3>业务包 — 获取背包（<code>C2S_GetItemList</code> / <code>S2C_ItemList</code>）</h3>
<table>
<thead>
<tr>
    <th>在线人数</th>
    <th>发包总数</th>
    <th>发送耗时(ms)</th>
    <th>总耗时(ms)</th>
    <th>回包窗口(ms)</th>
    <th>TPS(全程)</th>
    <th>TPS(回包窗口)</th>
</tr>
</thead>
<tbody>
<tr><td>100</td><td>10000</td><td>101</td><td>120</td><td>71</td><td>83333.33</td><td>140845.07</td></tr>
<tr><td>100</td><td>50000</td><td>591</td><td>603</td><td>547</td><td>82918.74</td><td>91407.68</td></tr>
<tr><td>100</td><td>100000</td><td>1205</td><td>1216</td><td>1164</td><td>82236.84</td><td>85910.65</td></tr>
<tr><td>300</td><td>100000</td><td>1164</td><td>1175</td><td>1025</td><td>85106.38</td><td>97560.98</td></tr>
<tr><td>500</td><td>300000</td><td>3356</td><td>3375</td><td>3115</td><td>88888.89</td><td>96308.19</td></tr>
<tr><td>1000</td><td>500000</td><td>5404</td><td>5685</td><td>5385</td><td>87950.75</td><td>92850.51</td></tr>
<tr><td>2000</td><td>1000000</td><td>10878</td><td>11259</td><td>10221</td><td>88817.83</td><td>97837.78</td></tr>
<tr><td>2000</td><td>1500000</td><td>16573</td><td>17036</td><td>16197</td><td>88048.84</td><td>92609.74</td></tr>
<tr><td>2000</td><td>2000000</td><td>22703</td><td>23131</td><td>22275</td><td>86464.05</td><td>89786.76</td></tr>
</tbody>
</table>



`);
