# JavaScript provider 外部参考研究：New API、Magpie 与运行时边界

研究日期：**2026-11-22**。

研究范围：为 Badger「Android 联系人名片夹 + Go 后端」的平台资料获取改为 JavaScript provider 提供外部依据。仅研究参考系统、插件契约和运行时选择；**没有读取本地 Badger 业务实现，没有修改业务代码或运行参考项目**。下文对 Badger 的建议须结合本地分析验证，不表示当前项目已经具有这些能力。

证据方法：使用 Anysearch 搜索消歧，默认以 extract 读取官方页面；对被截断的长文档，使用分段 fetch，必要时以内存中的只读 HTTP 请求定位并读取源码片段。GitHub API 用于确认分支 commit、版本 tag 和文件入口。Goja、Node.js API 语义同时使用 Context7 resolve/query 核对，最终追到官方文档或源码。所有成功搜索均已提交 score；没有上传私有源码、安装插件、执行下载的脚本或克隆全仓库。

## 1. 最重要的结论

1. **New API 同时存在 Go 内置 adapter、声明式高级自定义渠道和真正的 JS 插件。** 本次 main 快照的插件不仅包含异步任务，还包含普通请求 provider 与请求处理扩展；「只有 Go adapter」或「JS 只支持视频任务」均不符合这份源码。[N1][N2][N3]
2. **New API 当前 JS 插件引擎是 `github.com/MoeForge/moejs v0.1.0`，不是 Goja。** 插件被编译为同步、无 import 的单文件模块；宿主执行网络请求、管理凭证、调用 hook 并校验结果。引擎默认执行预算 5 秒、并发槽 8；文档给出 JS 内存默认上限 512 MiB，可由配置调整。这些是参考系统的值，不应直接作为名片资料查询的默认值。[N4][N5][N6]
3. **这里的 Magpie 已消歧为 `yetone/magpie`，官方站点为 `usemagpie.ai`。** Go provider 层实际把请求转交给 plugin host；provider 插件运行在共享的 Bun 子进程，具有宿主权限。另有在 Go 主进程使用 moejs 执行的 gateway middleware，以及在 Bun host 执行的异步 gateway hooks。不能用其中一条路径的限制概括全部插件。[M1][M2][M3][M4][M5]
4. **Goja 可以中断 JS 执行，但不能据此承诺安全沙箱或每脚本硬内存隔离。** 官方源码明确中断不抢占原生 Go 函数，包括内置函数；维护者也说明没有找到实用的 Go 内内存限制方案。Node `vm` 官方明确禁止将其当作执行不受信代码的安全机制；其 timeout 对异步任务还有具体边界。[G1][G2][G3][J1]
5. **对受信 provider，优先借鉴「小接口、宿主管 I/O、统一结果、固定版本」；对任意用户脚本，先决定隔离部署，再选语言引擎。** 任意脚本不宜直接放入 Go API 进程，也不能仅靠 Node `vm`、worker 或普通 Bun 子进程实现安全隔离。独立 worker 还需要权限、网络、内存、CPU 和生命周期的宿主约束。这是基于已核验限制的设计建议，不是参考系统已完成多租户隔离的结论。

## 2. 研究快照与身份核验

| 对象 | 本次核验版本或 commit | 证据与限制 |
| --- | --- | --- |
| QuantumNous/new-api | `8542c9d6490b4330fe18160930f81ae7428bc777` | [main ref API](https://api.github.com/repos/QuantumNous/new-api/git/ref/heads/main) 返回；关键源码按此 SHA 读取。不是所有已部署 release 的能力声明 |
| QuantumNous/new-api-plugins | `b876ae773533ccb03eaa705afe7f37c3deef647c` | [main ref API](https://api.github.com/repos/QuantumNous/new-api-plugins/git/ref/heads/main)；README 证明官方单文件 JS 插件目录和完整性校验设计，未穷举插件 |
| QuantumNous/skills | `739cbae181dcedc381528503d941eee49445f7cd` | [main ref API](https://api.github.com/repos/QuantumNous/skills/git/ref/heads/main)；只读取仓库介绍，作为作者工具链的补充证据，未安装或执行 skill |
| yetone/magpie | `f0b58041df3c5031b42c788429288cf95b6c4e11` | [main ref API](https://api.github.com/repos/yetone/magpie/git/ref/heads/main)；关键 Go 源码按此 SHA 读取 |
| magpie-community/plugins | `3f2c56ddc770846494abaae1330f5d736147ec88` | [main ref API](https://api.github.com/repos/magpie-community/plugins/git/ref/heads/main)；核对 README 与一个 provider 实例，不做生态清单 |
| MoeForge/moejs | `v0.1.0`，tag 指向 `3035a4d642967b2eddeee8164ef3010ec85fc401` | [tag ref API](https://api.github.com/repos/MoeForge/moejs/git/ref/tags/v0.1.0)；两参考项目 go.mod 都使用 v0.1.0；安全策略、guide 按 tag 读取 |
| dop251/goja | `79d6ad37b9762327be0fc513ba4f8d47b62a2aad` | [master ref API](https://api.github.com/repos/dop251/goja/git/ref/heads/master)；README、runtime.go 按此 SHA 读取。Magpie 的 go.mod 另有 Goja 依赖，不等于 provider host 使用它 |
| Node.js | `v24.22.2` 文档与源码 tag | 使用固定版本文档检查 vm/worker 语义；不宣称它是当前最新版本 |

身份消歧依据：`yetone/magpie` README 直接链接 `usemagpie.ai` 和 `magpie-community/plugins`；官方插件文档描述的 Go gateway/Bun host 与 `internal/provider/plugins.go`、`internal/plugin/host.go` 对应。搜索还出现 `laincat/veneracat-core/magpieplugin` 等漫画来源包，未纳入本研究。`usemagpie.dev` 未建立一手身份对应，**不作为官方入口或结论依据**。Magpie README 也明确说明与 Apache Magpie 无关。[M1][M2][M3][M4]

## 3. New API：三类扩展并存

### 3.1 Go adapter 与声明式配置

`relay/relay_adaptor.go` 的 `GetAdaptor` 仍通过 `switch` 实例化 `openai.Adaptor`、`claude.Adaptor`、`gemini.Adaptor` 等 Go 实现，也包含 `advancedcustom.Adaptor` 与 `providerplugin.Adaptor`。因此其普通渠道实现并未全部变成 JS。[N1]

声明式渠道的 Base URL、模型映射、参数覆盖，解决的是既有协议内的配置和改写；插件 `meta.relay` 的路由字段也沿用高级自定义渠道的路径、认证模板与 converter 规则。JSON 配置出现于渠道管理，并不证明系统在执行 JS。反过来，JS 文件也可能只导出路由元数据：**provider 没有 hook 时，文档明确说请求阶段不执行 JS**。[N2][N7]

### 3.2 真正的 JS 插件契约

当前 `docs/plugin-api/v1.md` 虽仍名为 Task Plugin API v1，但已明确区分以下部分，可以组合，也可以只提供其中一种：[N2]

| 部分 | 判定方式与职责 | 对联系人资料获取的借鉴 |
| --- | --- | --- |
| task | 导出 `buildSubmitRequest`；提交、查询、轮询、结果呈现 | 借鉴请求描述与结果解析的分离；任务计费、视频协议和持久轮询无需先引入 |
| provider | `meta.relay` 非空；普通 API relay，可附加凭证和转换 hook | 借鉴声明支持范围、可选 hook、宿主统一发请求 |
| request processing | 导出 ingress/request/outbound/egress/reply/report 等 hook | 可借鉴阶段和失败语义；不必为单次资料查询复制整条 AI gateway 管线 |

任务部分的典型边界是：decoder 把输入转为规范 intent，`buildSubmitRequest` 返回请求描述，Go 宿主发请求，`parseSubmitResponse` 解析响应；查询与呈现另有对应 hook。上传文件以 request-scoped ref 进入插件，不直接交出临时路径和 reader。任务权限与归属仍由宿主检查。[N2]

provider 部分使用 type-61 渠道与 `meta.relay`；可选导出 `login`、`refresh`、`revoke`、`exchangeKey`、`signRequest`、`prepareRequest`、`credentialRejected` 和 converter hook。普通完整转换通常要求 `convertRequest`、`convertResponse`、`convertStreamEvent`；仅模型列表转换有专门例外。该设计服务模型 API，不是可以原样用于联系人字段的通用 ABI。[N2]

这不是文档中尚未接线的概念：`relay/channel/providerplugin/adaptor.go` 嵌入 Advanced Custom adapter，实际在 `DoRequest` 调用 `CallConvertRequest`、在准备阶段调用 `CallPrepareRequest`，通过 `CallSignRequest` 签名，并接入凭证刷新/静态 key token 交换；最终由 Go adapter 发送请求。[N3]

### 3.3 引擎、资源预算与网络权限

实际 `pkg/jsplugin/engine.go` 导入 moejs，编译时拒绝 `async`、`await`、`import`，并检查模块请求以拒绝 re-export 导入；创建 runtime 时设置 `DisableDynamicCode: true` 与内存预算，避免 eval/Function 绕过上传时检查。这里的「同步」是宿主插件契约选择，不能推导为 moejs 引擎不支持 async/await。[N4][R1]

引擎编译一次模块，按执行槽限制并发并复用 runtime；通过 watchdog 调用 Interrupt，中断后按路径丢弃不可复用实例，并在复用时清理调用数据。文档特别警告：**模块级变量随 runtime 跨请求保留，不能存请求数据**。因此 runtime 池不是天然的账号隔离，状态归属仍是宿主必须设计的事情。[N4][N2]

`utils.fetch` 是宿主提供的受控同步工具，并非完整的浏览器 fetch。实际源码限制每次 hook 最多 4 次请求、单次 15 秒、累计网络等待 30 秒、响应体最多 1 MiB；只允许 GET/HEAD，部分指定 hook 才允许 POST。等待网络时暂停 JS 执行计时并暂借执行槽，故「JS 5 秒」**不是整个查询 5 秒墙钟上限**。外层请求仍需独立截止时间。[N5]

文档把目标限制为渠道 Base URL host 与额外 `allowedHosts` 等，并区分携凭证内容请求、无凭证抓取与 SSRF 检查；`allowedHosts` 代表可信凭证接收方，不等于任意私网地址白名单。宿主函数需自行限制工作量，因为 JS 中断不能抢占运行中的 Go 函数。以上网络规则有官方契约依据，本次没有对完整传输链进行 SSRF 或 DNS rebinding 安全审计。[N2]

官方 `new-api-plugins` 仓库明确把安装插件视为 **administrator-level trust**：插件可见渠道凭证并构造上游请求。不能因单文件、禁 import、有 allowedHosts、执行超时，就将它描述成适合任意普通用户上传恶意脚本的环境。[N8]

### 3.4 发布与更新设计

官方插件仓库采用 `plugins/<kind>/<key>/<version>/plugin.js`，发布后的版本目录不可变；索引由插件编译后的 meta 生成，携带源码 sha256，安装时校验 hash 并重新验证 meta。README 还明确「index 是缓存，不是 trust anchor」。它解决发布一致性和内容完整性，**hash 本身不能证明作者可信**。[N8]

可借鉴的是：明确 apiVersion 与插件版本、发布产物固定、验证后激活、可定位当前版本、更新与运行中请求绑定同一代插件。官方文档描述路由 generation 原子发布；`relay_adaptor.go` 也实际保留请求 pinned plugin/generation。[N1][N2]

`QuantumNous/skills` 提供 `newapi-plugin-dev` 作者工具链，介绍 `plugin.js`、README/CHANGELOG、`new-api plugin lint` 与 fixtures。这里的 Bun/Node/Deno 要求属于 skill 工具，不是 New API 后端 JS 执行引擎的证据。[N9]

限制：官方插件仓库 README 目前仍以 task marketplace 为主要说明，本次没有穷举 provider 插件商品、核实每项能力最早进入哪个 release，或验证任意已部署版本兼容此 main。当前 API 版本仍为 1，但文档反复提示新增字段会被旧宿主拒绝，不能仅比较 `apiVersion: 1` 就认定兼容。[N2][N8]

## 4. Magpie：Go gateway 加 Bun provider host

### 4.1 真实请求路径

官方文档的 provider 模型是：插件声明配置和模型、负责登录，`auth.loader` 返回带认证逻辑的 fetch；另可提供 `provider.models`、`auth.usage`、`auth.refresh` 等 hook。Go gateway 负责协议转换、路由和账号策略。[M2]

源码中 `pluginBase = "plugin://"` 作为内部虚拟地址；`pluginProviderServing` 为账号安装 transport；`pluginFetch` 获取 loader options，决定实际 URL，再调用 `plugin.Fetch`，传递 provider、account、model、headers、body 与 session。因此 JS 插件确实参与上游请求，不是仅填写静态 JSON。[M3]

`internal/plugin/host.go` 的 `startOn` 实际构造 `bun ... run <host.js>`，用 stdin/stdout 传消息，初始化启用插件；`current *host` 复用当前 host，更新时旧 host 可继续完成已有请求。源码有窗口/队列/消息行长度限制、取消、进程树清理和内存增长后的回收逻辑。它们是运行管理能力，不能等同每插件 OS 权限隔离或硬内存配额。[M4]

本次另读取社区 `zen-free/index.mjs` 与其 `provider.mjs`：入口导出 server 函数，provider 返回 `config`、`auth.loader.fetch`、`provider.models`；文件实际 import `node:crypto`、`node:fs`、`node:os`、`node:path`，且使用网络 fetch。这直接表明该执行模式允许插件使用 Node 兼容的宿主能力。[M6]

### 4.2 必须区分三条 JS 路径

| 路径 | 已核实执行位置 | 权限、时限与适用范围 |
| --- | --- | --- |
| provider/OpenCode、pi 插件 | 共享 Bun plugin host 子进程 | 可做异步 I/O、导入模块、访问宿主能力；安装须信任代码。不同插件不是各自独立的 OS 沙箱 |
| gateway middleware | Go 主进程内 moejs | 各请求从池中取得 runtime，支持 onRequest/onAccount/onProvider/onEvent/onResponse；源码请求/整回复预算 250 ms、事件 50 ms、加载 1 秒 |
| async gateway hooks | Bun plugin host | 允许等待服务、导入 Node 内置模块；有调用预算和取消契约。官方文档明确说 Bun 插件具有宿主权限 |

依据分别是官方插件文档、`host.go`、`internal/middleware/middleware.go` 和 gateway 调用接线。[M2][M4][M5][M7]

middleware 源码创建 `moejs.NewRuntime(moejs.Options{})`，未在此处设置显式 MemoryLimit。**不能因为 moejs 引擎支持内存预算，就声称 Magpie 每个 middleware 已配置该预算。** Bun host 的 `heavyMB = 512` 是运行一段时间、检查并 GC 后触发 host 替换的阈值，不是进程或每插件 512 MiB 硬限制。[M4][M5]

文档与源码还有一处差异：社区 README 称 middleware 是无 import 单文件；本次 main 的 `compile` 实际调用 `jsmod.Compile(root, file, ...)`，注释允许同包旁边的文件模块，且没有 node_modules 解析。报告以所读运行代码为准；未全面审计该本地模块解析器的文件边界。[M5][M8]

middleware 失败后保留原输入的行为适合部分可选改写；**资料获取不能直接照搬为失败时返回未经验证的资料**。Badger 应明确超时、未找到、平台要求登录、解析失配等结果。这是业务域上的设计建议，不是对 Magpie 正确性的判断。

### 4.3 能借什么、不能套什么

能借：provider 身份与展示配置分开、能力声明、账号状态归宿主管理、统一调用与日志、插件更新不打断在途请求、宿主版本兼容策略。[M2][M3][M4]

不能直接套：AI 模型目录、token 额度、流式协议转换、OpenCode/AI SDK 的 provider ABI，以及以个人本机受信插件为前提的文件/账号权限模型。若 Badger 只需一次 HTTP 获取和字段解析，Bun/npm 整套 host 可能增加部署、更新、进程通信与依赖管理负担；只有实际需要 npm 包、复杂登录或浏览器能力时，这个方向才更有价值。此取舍未通过本地实现或性能实验验证。

## 5. 运行时核验：一个 Go 候选与 Node vm 的边界

### 5.1 Goja：受信、小型脚本的可用 Go 候选

本次只对 Goja 深入核对，**未并行展开 QuickJS 选型**。Goja 官方 README 说明它是纯 Go ECMAScript 5.1(+) 引擎，支持大量但非全部 ES6+ 功能，无 cgo；当前读取 commit 的最低 Go 版本是 1.26。runtime 一次只能由一个 goroutine 使用，timer/event loop 需要宿主另供，部分 Node 能力来自独立 `goja_nodejs` 项目。[G1]

官方 `runtime.go` 第 1579 行起对 `Interrupt` 的注释明确：仅在执行 JS 时生效，不能中断原生 Go 函数，包含全部内置函数；runtime 空闲时的 interrupt 会影响下次 Run。`ClearInterrupt` 还要求先保证运行结束、不可能有并发 Interrupt，再重新开放复用。这与 Context7 返回的语义一致。[G2]

因此正确的超时设计至少有两个边界：JS 执行由 Interrupt 管；宿主 HTTP、解析、签名等函数由各自 context、长度限制和可取消实现管。仅把 hook 放进 goroutine 并等待 context 超时，不会自动杀掉那个 goroutine，也不会终止已开始的宿主函数。这是由 Goja 中断边界导出的宿主设计要求。[G2]

内存方面，维护者在官方讨论 #646 中答复没有找到 Go 内实用的限制方案；所读公开 runtime API 不能提供本次可确认的每实例硬内存配额。不要以进程 Go GC 设置、定期统计或执行超时冒充单脚本堆上限。[G3]

适用建议：若 provider 为维护者审核发布、逻辑主要是同步请求构造/字段提取、能接受明确 JS 子集且无需 npm/浏览器环境，Goja 可作为先验证的纯 Go 候选。**这不是两个参考系统当前 provider 引擎的复刻**。需用真实 provider fixtures 验证语法、URL/编码、正则和错误行为；本次未运行兼容性或吞吐基准。

### 5.2 参考项目实际采用的 moejs：记录事实，不把它误写成 Goja

两个所读 go.mod 都使用 moejs v0.1.0；New API 已在创建 runtime 时显式设置 MemoryLimit，而 Magpie middleware 的所读创建路径没有设置。[N6][M9][N4][M5]

moejs v0.1.0 官方 guide 描述 Interrupt、内存预算和 runtime 池；安全策略把越权读取 Go 数据、跨 runtime 干扰、进程崩溃、严重超额分配或无法中断列为漏洞，并说明内存计数是近似值。安全策略同时标明项目仍处于 alpha。[R1][R2]

这些是作者的功能与安全契约，**不等于本次已证明它可安全承载公开多租户恶意脚本**，也不等于 JS 内存计数覆盖 HTTP 缓冲、全部 Go 堆与进程 RSS。当前只将其作为「与参考项目一致、具备明确预算 API 的候选线索」；没有做引擎安全审计、压力测试或与 Goja 的独立性能对比，不采用 README benchmark 作 Badger 性能承诺。

### 5.3 Node vm 与 worker

固定版本 Node.js v24.22.2 官方 vm 文档原文为：

> The node:vm module is not a security mechanism. Do not use it to run untrusted code.

`timeout` 终止本次执行并抛错，但 Promise/async 在默认模式下可在本次执行返回后继续运行。`microtaskMode: 'afterEvaluate'` 可把相应 context 自己的 microtask 纳入执行时限；外部 context 函数、共享全局队列，以及注入的 nextTick/setTimeout/setImmediate 等仍有文档指出的边界。因此它是执行控制，不是恶意代码隔离保证。[J1]

worker 提供另一种执行线程和 `terminate()`，但 `resourceLimits` 官方明确只影响 JS engine，不包括 ArrayBuffer 等外部数据；即使设置，进程仍可能因全局 OOM 中止。worker 能共享内存，不能因为比 vm 多一层线程就宣称跨租户安全边界。[J2]

若实际需求必须兼容 npm/完整 Node 生态，可以选择独立 Node/Bun worker 进程来获得进程级终止与故障分离；权限隔离和资源硬限制仍应由操作系统/容器等外部机制提供。**普通子进程仍可使用它继承到的环境、文件权限和网络，只有换进程还不够。** 这一部署建议不声称 Magpie 已实施这些多租户限制。

## 6. 对 Badger 可讨论的落地取舍

本节为研究推导；未检查本地业务、后端结构、账号系统或已有平台适配器，不给出现有类名和迁移工作量。

| 条件 | 建议方向 | 主要代价或前提 |
| --- | --- | --- |
| 仅维护者提交、审核和发布 provider | Go 宿主管网络与凭证，JS 负责构造请求/解析结果；先以 Goja 做可行性验证 | 需约定 JS 子集、控制输入输出大小和宿主函数；无每脚本硬内存隔离 |
| 希望贴近参考项目且看重 JS 内存预算 | 单独验证 moejs 固定版本，保持宿主接口可替换 | alpha 状态、API/语言兼容和资源边界须实测；不能因参考项目采用就免验证 |
| provider 必须依赖 npm、复杂异步认证或浏览器能力 | 比较 Magpie 式 Node/Bun worker host | 增加运行时分发、依赖锁定、通信、退出清理和权限控制；单共享 host 不隔离插件 |
| 开放任意用户提交脚本 | 隔离 worker + 外部 CPU/内存/墙钟/网络/文件权限约束 | 不与主 API 共进程，不把 vm/Interrupt 当安全方案；需隔离不同用户与凭证 |

建议最小契约保持在联系人资料域内，不照搬模型 API：

1. **静态元数据**：provider id、version、host API 兼容要求、支持的平台或输入类型、期望输出字段、所需网络与认证能力。避免把动态上游数据获取藏进元数据读取阶段。
2. **输入规范化**：宿主先规范化 URL/账号标识，校验用户提交内容；provider 只收到执行本次任务必需的输入。
3. **请求与解析边界**：单请求可用 buildRequest/parseResponse 的两段契约；有多步查询需求再显式增加受限宿主 HTTP 能力，别先引入任意网络 API。新 API 的 request descriptor 设计支持这一取舍。[N2][N3]
4. **统一输出**：仅返回可序列化的资料、原始来源 URL、获取时间和明确状态；字段长度、图片链接和 URL 由宿主再次验证。provider 不能直接写数据库、决定联系人归属或操作 Android UI。
5. **状态与凭证归宿主**：不把一个用户的 Cookie/token 放进共享模块全局变量。请求作用域、账号作用域和插件持久状态须显式区分；默认不跨请求保留私人数据。New API 对 module scope 的警告值得直接转成契约约束。[N2]
6. **可复现更新**：按不可变版本或源码 hash 安装、验证后激活、保留回退版本，在途调用绑定旧版本；日志记录 provider/version/阶段/耗时/状态，不记录 Cookie、token、私密响应正文。hash 只证明取到的内容与选定版本一致。[N8]

受信边界应写清楚：「只有管理员能上传」并不自动代表脚本已经受信；从社区 URL 安装但未经审核的脚本仍应按不受信处理。若脚本已获完整 Cookie，它在许可目标上能做什么也取决于该 Cookie 的权限，执行引擎无法替代凭证最小授权。

完成最小可行性验证时，优先选覆盖不同形态的少量 provider fixtures：普通 JSON、需要特定 header 的接口、短链/跳转、多步查询、字段缺失、超时、登录失效和解析结构变化。分别验证宿主请求取消、重定向策略、体积上限、跨账号状态和版本回退；这是一份后续验证建议，本次没有执行这些用例。

## 7. 未知、读取失败与结论适用范围

- **未读本地实现**：不能确认 Badger 哪些平台能在后端无登录获取、哪些依赖用户本机 Cookie、哪些必须使用浏览器，也不能据此承诺全部平台 JS 化可行。后端抓取与客户端可见数据的差异需要另一份本地/真实接口分析。
- **版本范围**：关键结论对应上表 SHA 或文档 tag；没有核实 provider 扩展首次发布版本、最新二进制或每个旧版本的兼容性。不能把 main 能力外推到用户正在运行的版本。
- **文档不同步**：New API 英文渠道页比中文当前入口与仓库 API 文档简略；中文页的 extract 出现字符编码乱码，未据乱码推断字段语义。Magpie 社区 README 与 main middleware 本地 import 能力存在上述差异。
- **长页截断**：New API v1.md、Magpie host.go、Node 文档等超过 extract 输出上限；已按需要分段读取或只读定位源码片段。没有把一次截断输出当作全文审计。Magpie `host.js` 未逐行审阅；本次实际执行路径由已读 `host.go`、provider 接线和实例插件确认。
- **读取失败**：最初尝试 New API `relay/relay.go` 未能提取；随后经 GitHub contents API 定位并读取实际 `relay/relay_adaptor.go`，结论不依赖失败路径。Context7 为 Goja 引用的 `_autodocs/api-reference/runtime.md` 在固定 commit 的 raw URL 未能提取，最终改读真实 `runtime.go` 第 1579–1596 行，直接核实 Interrupt/ClearInterrupt。
- **安全与性能未验证**：未跑任何参考项目、恶意脚本、隔离逃逸、OOM、吞吐基准或许可证兼容审查；Goja 内存结论依据公开 API 和维护者讨论，moejs 安全能力依据固定版本官方契约，不是本次独立安全证明。
- **范围主动收敛**：未穷举插件生态，未展开 QuickJS/cgo 工具链，也未建立 `usemagpie.dev` 官方身份。运行时与契约可先讨论，但最终选型应由真实 provider 需求、受信边界与部署环境决定。

## 8. 一手来源索引

以下关键源码 URL 固定到上述 SHA 或 tag；产品入口和网页文档可能随时间变化。

### New API

- [N1：Go adapter 与请求绑定源码][N1] — `relay/relay_adaptor.go`。
- [N2：完整插件 API v1 契约][N2] — 尤其 Request building、Host utilities、Provider part、Request processing part。
- [N3：provider adapter 实际接线][N3] — `relay/channel/providerplugin/adaptor.go`。
- [N4：JS 引擎、编译、watchdog 与池][N4] — `pkg/jsplugin/engine.go`。
- [N5：宿主 fetch 实现及预算][N5] — `pkg/jsplugin/fetch.go`。
- [N6：运行时依赖版本][N6] — `go.mod`。
- [N7：官方英文渠道管理][N7]；[中文入口](https://docs.newapi.ai/zh/docs/guide/feature-guide/admin/channel)。
- [N8：官方插件仓库](https://github.com/QuantumNous/new-api-plugins) — 本次已读仓库首页 README；固定快照引用见 [README][N8]。
- [N9：官方作者工具介绍](https://github.com/QuantumNous/skills) — 本次已读仓库首页 README；固定快照见 [README][N9]。

### Magpie

- [M1：官方仓库及 README](https://github.com/yetone/magpie) — 身份、官方站点和插件链接。
- [M2：官方插件文档][M2] — provider hooks、共享 Bun host、gateway hooks 与权限说明。
- [M3：Go provider 与 JS 插件桥接][M3] — `internal/provider/plugins.go`。
- [M4：Bun host 启动与生命周期][M4] — `internal/plugin/host.go`，重点 `startOn`、`stop`、`kill`、`heavyMB`。
- [M5：进程内 middleware 引擎][M5] — `internal/middleware/middleware.go`。
- [M6：已读 provider 实例][M6] — 入口 [index.mjs](https://github.com/magpie-community/plugins/blob/3f2c56ddc770846494abaae1330f5d736147ec88/packages/zen-free/index.mjs)。仅作为实际插件能力证据，不建议 Badger 复制该业务逻辑。
- [M7：gateway 的 middleware/hooks 调用][M7] — `internal/gateway/middleware.go`。
- [M8：社区插件 README](https://github.com/magpie-community/plugins) — 本次已读首页；固定快照见 [README][M8]。
- [M9：Magpie 运行时依赖][M9] — `go.mod`。

### 运行时

- [G1：Goja README][G1]；[官方仓库](https://github.com/dop251/goja)。
- [G2：Goja Interrupt/ClearInterrupt 官方源码][G2] — 第 1579–1596 行。
- [G3：Goja 内存限制维护者答复][G3] — 官方 discussion #646，2025-01-16。
- [R1：moejs v0.1.0 guide][R1]；[仓库介绍](https://github.com/MoeForge/moejs)。
- [R2：moejs v0.1.0 安全策略][R2]。
- [J1：Node.js v24.22.2 vm 官方源码文档][J1] — 开头安全声明、Timeout interactions with asynchronous tasks and Promises；[渲染文档](https://nodejs.org/download/release/v24.22.2/docs/api/vm.html)。
- [J2：Node.js v24.22.2 worker 官方源码文档][J2] — Worker options/resourceLimits、terminate；[渲染文档](https://nodejs.org/download/release/v24.22.2/docs/api/worker_threads.html)。

[N1]: https://github.com/QuantumNous/new-api/blob/8542c9d6490b4330fe18160930f81ae7428bc777/relay/relay_adaptor.go
[N2]: https://github.com/QuantumNous/new-api/blob/8542c9d6490b4330fe18160930f81ae7428bc777/docs/plugin-api/v1.md
[N3]: https://github.com/QuantumNous/new-api/blob/8542c9d6490b4330fe18160930f81ae7428bc777/relay/channel/providerplugin/adaptor.go
[N4]: https://github.com/QuantumNous/new-api/blob/8542c9d6490b4330fe18160930f81ae7428bc777/pkg/jsplugin/engine.go
[N5]: https://github.com/QuantumNous/new-api/blob/8542c9d6490b4330fe18160930f81ae7428bc777/pkg/jsplugin/fetch.go
[N6]: https://github.com/QuantumNous/new-api/blob/8542c9d6490b4330fe18160930f81ae7428bc777/go.mod
[N7]: https://docs.newapi.ai/en/docs/guide/feature-guide/admin/channel
[N8]: https://github.com/QuantumNous/new-api-plugins/blob/b876ae773533ccb03eaa705afe7f37c3deef647c/README.md
[N9]: https://github.com/QuantumNous/skills/blob/739cbae181dcedc381528503d941eee49445f7cd/README.md
[M1]: https://github.com/yetone/magpie
[M2]: https://usemagpie.ai/docs/plugins
[M3]: https://github.com/yetone/magpie/blob/f0b58041df3c5031b42c788429288cf95b6c4e11/internal/provider/plugins.go
[M4]: https://github.com/yetone/magpie/blob/f0b58041df3c5031b42c788429288cf95b6c4e11/internal/plugin/host.go
[M5]: https://github.com/yetone/magpie/blob/f0b58041df3c5031b42c788429288cf95b6c4e11/internal/middleware/middleware.go
[M6]: https://github.com/magpie-community/plugins/blob/3f2c56ddc770846494abaae1330f5d736147ec88/packages/zen-free/provider.mjs
[M7]: https://github.com/yetone/magpie/blob/f0b58041df3c5031b42c788429288cf95b6c4e11/internal/gateway/middleware.go
[M8]: https://github.com/magpie-community/plugins/blob/3f2c56ddc770846494abaae1330f5d736147ec88/README.md
[M9]: https://github.com/yetone/magpie/blob/f0b58041df3c5031b42c788429288cf95b6c4e11/go.mod
[G1]: https://github.com/dop251/goja/blob/79d6ad37b9762327be0fc513ba4f8d47b62a2aad/README.md
[G2]: https://github.com/dop251/goja/blob/79d6ad37b9762327be0fc513ba4f8d47b62a2aad/runtime.go#L1579-L1596
[G3]: https://github.com/dop251/goja/discussions/646
[R1]: https://github.com/MoeForge/moejs/blob/v0.1.0/docs/guide.md
[R2]: https://github.com/MoeForge/moejs/blob/v0.1.0/SECURITY.md
[J1]: https://github.com/nodejs/node/blob/v24.22.2/doc/api/vm.md
[J2]: https://github.com/nodejs/node/blob/v24.22.2/doc/api/worker_threads.md

## 附录：本地目录盘点（主线程只读观察，非外部结论）

以下内容由主线程直接观察后提供，本研究代理没有重新读取这些目录或本地业务实现。它们只补充工作区背景，不作为 New API、Magpie 或运行时能力的外部证据。

- `F:\Java\Badger` 顶层包括 `.claude`、`.design-kit-backups`、`AGENTS.md`、`ARCHITECTURE.md`、`Badger`、`Badger-Server`、`GLOSSARY.md`、`README.md`、`badger-ui-demo`、`docs`、`scripts`、`tasks`、`tests`、`worktrees`。
- `F:\Java\Badger\worktrees` 下包括 `_backup-main-plus-new-backend-20261103`、`_backup-v2-20261105`、`_dev-ref`、`main-plus-new-backend`。
- `F:\GolandProjects` 下包括 `BadgerServer`、`KeyRouter`、`SDKROOT`、`matrix-pylon`、`参考项目`。KeyRouter 的 ARCHITECTURE 是 AI 网关设计，未验证运行；matrix-pylon README 描述 Matrix/OneBot 桥；参考项目是 di/network/utils 等 2026-07 旧 Kotlin 代码，包含 WebDAV 和 adapter。目录或设计文档存在不代表可直接复用的已运行组件。
- 主线程核对的当前 Go README/protocol 以 Android 未提交代码为契约，路由同为 `/api/v1`；`docs/android-e2e.md` 的 2026-11-21 记录包含真实单模拟器双向同步、头像、登录退出验证。因此讨论 provider 扩展时，不应把 Go 后端当作尚未联调的新 `/api` 协议。此处转述主线程证据，本研究未复验。
- 所有本研究命令均在指定 worktree 工作目录执行，没有切换到原仓库；没有读取真实 `Data` 或 `.env` 密钥。
