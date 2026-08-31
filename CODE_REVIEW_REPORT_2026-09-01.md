# Badger-Android 代码质量 / API 契约审查报告

日期：2026-09-01  
审查基线：`dev` + 现有 `refactor/dev-cleanup-2026-08-31`  
当前工作分支：`refactor/dev-cleanup-2026-08-31`（没有创建新分支）  
当前分支相对 `dev`：69 commits ahead / 0 behind  

> 本报告是上一轮 `CODE_REVIEW_REPORT_2026-09-01.md` 的继续版。重点不是重复“已经修完”的问题，而是继续收口 API 契约、死代码、兼容边界和网络正确性。

## 1. 总体结论

当前工程已经不再属于典型的“不可维护屎山”。架构正在从 V1/V2 双时代代码向单一服务端 `/api` 契约收口，网络层也已经拆成多个领域 API 类。

本轮继续发现并处理了两个实际 correctness 问题，以及一个兼容边界问题：

1. **resolver 兼容回归**：上一提交移除了 `ContactNetworkResolver.getResultInfo*`，但 `SetupStepPlatforms.kt` 仍有生产调用，导致代码处于“接口已删、调用未迁”的半完成状态。
2. **token refresh 链路未真正作用于 `ServerApi`**：`NetworkModule` 原先创建 `ServerApi` 时传的是未安装 auth/refresh interceptor 的基础 OkHttpClient；refresh interceptor 只挂在返回给 Koin 的另一只 client 上，导致 `ServerApi` 请求可能无法自动 refresh。
3. **客户端内部旧命名**：`IdentifyResponse.signature` 与服务端正式 `ResolveResult.description` 不一致；已统一为 `description`，同时保留的兼容投影被显式标记为迁移边界。

当前剩余的大问题已经从“明显死代码/旧 endpoint”转移到：依赖注入边界、V1 DTO 生产读取冻结、Repository pending-operation 一致性、以及超大 Composable 的可维护性。

---

## 2. 服务端 API 契约核对

依据服务端交接文档（版本 2026-08-30）：除 `/api/proxy/ai/*` 与 `/api/proxy/shortio/*` 成功响应为裸 JSON 外，其余 `/api` 端点统一是 `ApiResult`；Android 使用 `Authorization: Bearer <token>`；未完成安装时 `/api/setup/*` 外统一 503；`/api/settings/`、`/api/shortlinks/`、`/api/admin/shortlinks/`、`/api/resolve/` 必须保留尾斜杠。fileciteturn0file0L11-L20 fileciteturn0file0L24-L40

服务端还规定 `/api/resolve/` 的单条请求是 `{input, ...}`、批量请求是 `{items:[...]}`，批量上限 50，ResolveResult 字段使用 camelCase，如 `avatarUrl`、`description`、`jumpLink`、`contacts`。fileciteturn0file0L165-L193

密钥纪律、sync 日志、AI 日志和 6 MiB 传输上限也属于必须保持的行为约束。fileciteturn0file0L371-L382

### 已确认一致

| 领域 | 客户端现状 | 结论 |
|---|---|---|
| Auth | `/api/auth/*` + Bearer | 一致 |
| Refresh | `/api/auth/refresh` + `data.token` | 一致；本轮补上真正的 client interceptor |
| Person | `/api/user/persons*` | 一致 |
| Collection | `/api/user/collections*` + members 子接口 | 一致 |
| Tag | `/api/user/tags*` + members 子接口 | 一致 |
| Device | `/api/user/devices*` | 一致 |
| Notification | `/api/user/notifications*` | 一致 |
| Sync | `/api/user/sync?since=&limit=` | 一致 |
| Settings | `/api/user/getSettings` + `/api/user/settings` | 一致 |
| Stats | `/api/user/stats` | 一致 |
| Upload | `/api/user/upload` multipart `file` | 一致；客户端 5 MiB 子限制低于服务端 6 MiB 总限制 |
| Resolver | `/api/resolve/`，单条/批量分流 | 一致 |
| AI | `/api/proxy/ai/tasks/*` 成功裸 JSON | 一致 |
| short.io | `/api/proxy/shortio/*` 成功裸 JSON | 一致 |
| 自建短链 | `/api/shortlinks/` + config | 一致 |

### 未发现客户端入口，但文档确实定义的服务端端点

交接文档中还定义了 setup、info、admin、platform management、system settings 等完整管理面。当前 Android 网络层的业务重点仍是用户端 API；这不等于这些接口“都是死代码”。不要为了追求覆盖率而凭空新增没有 UI/use case 的 admin client。

特别注意：**本次提供的 API 交接文档没有 `/api/user/backups` 端点定义**。上一版报告曾把 backups 当成服务端缺口；该结论没有文档依据，本版已删除。文档只在 maxRequestSize 条目里提到“backup envelope”与 AI OCR 共用 6 MiB 限制，不能据此推导出备份 REST API。fileciteturn0file0L379-L382

---

## 3. 本轮实际修复

### P0：resolver 删除后的生产调用回归

上一轮删除 `getResultInfo*` 时，`SetupStepPlatforms.kt` 仍调用 `ContactNetworkResolver.getResultInfo(...)`。这是典型的半迁移状态：测试已经删除旧入口，但生产调用还停留在旧 API。

本轮处理方式：

- 恢复一个**明确标记的迁移兼容边界**，内部实现只调用 canonical `identify()`。
- `IdentifyResponse.signature` → `description`，与服务端 `ResolveResult.description` 对齐。
- 兼容投影 `NetworkResolveResult` 与 `getResultInfo()` 均加 `@Deprecated`，避免新的调用继续扩散。
- canonical resolver 的 contract tests 继续保留，旧 projection 测试不再恢复。

这个做法优先保证当前分支可构建，同时把“剩余迁移”从隐式 legacy API 变成显式、可搜索、可删除的边界。

### P1：token refresh 实际未接入 `ServerApi`

原 `NetworkModule.provideOkHttpClient()`：

```text
baseClient -> 创建 ServerApi
          -> factory.install(api)
          -> 再创建带 tokenAuthInterceptor / tokenRefreshInterceptor 的 client
          -> 返回这个新 client
```

因此 `ServerApi` 内部实际使用的是 `baseClient`，并没有经过 refresh interceptor；而 Koin 拿到的 `OkHttpClient` 却带了 interceptor。这是隐藏的双 client 分叉。

本轮改成：

```text
baseClient
   ↓
带 token/refresh interceptor 的 client
   ↓
用同一个 client 构造 ServerApi
   ↓
factory.install(api)
```

同时 refresh URL 统一使用 `trimEnd('/')`，避免服务器地址带尾斜杠时产生 `//api/auth/refresh`。

### P2：resolver 内部模型命名收口

服务端正式字段是 `description`，客户端不再保留 `signature` 这个别名；canonical parser 直接读取 `description` / `avatarUrl` / `contacts`。

### CI

`.github/workflows/ci.yml` 已为当前清理分支配置 push 构建。最近提交已经自动创建 Build Debug APK workflow run，说明触发器本身正常工作。

---

## 4. 代码质量评价

### 4.1 优点

**网络领域拆分已经有效。** `ServerApi` 目前主要是 facade，Auth / Resolver / AI / short.io / domain API 已经分离；`ApiCore` 负责 URL、请求构造、Bearer、HTTP 执行和 `ApiResult` 解包。这比单一巨型 API 类明显更可维护。

**canonical API 契约测试是有效资产。** 当前 resolver 测试验证了：

- 单条请求使用 `/api/resolve/`；
- body 是 `{"input": ...}`；
- 单条 `data` 直接是 ResolveResult；
- 批量按 50 分块；
- malformed/missing `results` 不会越界；
- server 5xx 能退化为 null 结果。

**Room V2/cache 方向已经清晰。** `Models.kt` 中 V1 的字段定义明确标为 DTO，不再作为 Room 表；V2 cache entities 承担当前数据库职责。

**敏感信息处理意识较好。** API key / token 没有直接进入正常业务 DTO；日志已经大量使用 `SafeLog`，符合服务端交接文档的密钥纪律要求。fileciteturn0file0L376-L380

### 4.2 仍然偏复杂的地方

**Service Locator 仍是最大的架构债。** `GlobalContext.get()`、`KoinComponentBy.get()` 等模式会隐藏 ViewModel 的真实依赖。新代码应优先构造注入，老代码可以渐进迁移。

**V1 与 V2 DTO 共存是合理的，但必须冻结边界。** `ContactField`、`CustomField`、`ContactFieldValue` 等属于历史 DTO/兼容数据形态；它们不能重新进入业务主链，新的功能只能以 V2 cache / server DTO 为来源。

**UI 文件粒度仍偏大。** `ContactDetailPage`、`ContactDetailDialogs`、`ScannerDialogs` 等仍有较多状态与参数集中在单文件。相比 correctness，这属于 P2 可维护性问题，不建议一次性“大拆”。

**Repository 远端失败补偿还没有完全统一。** 现阶段已经出现 local/cache、sync、pending operation 多种策略共存。长期应该统一为“本地事务记录事实 + pending operation 记录副作用 + worker 重试”，而不是由每个 Repository 自己定义补偿语义。

---

## 5. 死代码 / 兼容代码复核

### 已明确可视为历史残留、且上一轮已经清掉

- 旧 resolver projection tests；
- 旧 resolver `/v1` 调用路径；
- 多处旧 FTS / V1 DAO 引用；
- 已被新的 Tag / Nfc / Settings 结构取代的旧页面入口；
- 无效 Experimental API opt-in / 多余 import / 旧 debug logging；
- 旧 Search/Filter helper 中已经不再由 Koin 注入的实现。

### 当前不应继续误删的“兼容资产”

- V1 → V2 migration 所需的数据模型；
- QAuxv importer；
- `PlatformEntry` 共享 JSON shape；
- `UserHistory` / sync cursor 相关模型；
- `SafeLog` / HTTP error classification；
- 当前 setup guide 仍依赖的 resolver compatibility projection。

关键判断原则：**是否仍有线上/迁移/导入入口消费，而不是文件名看起来“旧不旧”。**

---

## 6. 推荐的最终工程边界

```text
network/
  ApiCore
  AuthApi
  ResolverApi
  AiApi
  ShortLinkApi
  UserDomainApi

network/model/
  Auth*
  Resolve*
  User*

repository/
  ContactRepository
  CollectionRepository
  TagRepository
  UserProfileRepository

sync/
  SyncRepository
  PendingOperation*
  Worker / Executor

pages/
  feature-level UI

legacy/
  migration / importer / one-shot compatibility only
```

下一阶段最值钱的动作不是再做一次全仓格式化，而是：

1. 把 `SetupStepPlatforms` 迁移到直接消费 `IdentifyResponse`，删掉现在的 deprecated resolver projection。
2. 将 resolver 从 `GlobalContext` facade 迁移为构造注入 service/repository。
3. 把 V1 DTO 的生产读取点做静态清单并冻结。
4. 统一 Repository + pending operation 的失败模型。
5. 最后再拆 UI 大文件。

---

## 7. 测试与 CI 状态

当前分支的 CI push 触发已经验证会创建 workflow run；最近一次针对本轮修复的 Build Debug APK run 处于 `pending` 状态，因此**不能在本报告中声称“本轮最终构建已通过”**。

此前已经存在的 resolver / ApiCore / HttpUtil / SafeLog 回归测试仍保留。

特别是上一轮 `a0c84d8` 的 workflow 已实际进入 `cancelled`，所以旧报告中“queued / 未完成”的文字不能继续作为当前事实。

---

## 8. 本轮提交变更

本轮在同一分支继续提交，没有创建新分支：

- `ContactNetworkResolver.kt`
  - `signature` → `description`
  - 显式标记 `NetworkResolveResult` / `getResultInfo()` 为 deprecated migration boundary
  - compatibility implementation 统一走 canonical `identify()`
- `ContactNetworkResolverTest.kt`
  - 断言同步到 `description`
  - 保留 canonical single/batch contract coverage
- `NetworkModule.kt`
  - `ServerApi` 改用安装了 auth/refresh interceptor 的同一 client
  - refresh URL 规范化尾斜杠
- `CODE_REVIEW_REPORT_2026-09-01.md`
  - 按当前 HEAD 重写，删除过时 backups 结论
  - 更新 P0/P1、CI 状态和剩余工作

---

## 9. 最终评级

| 维度 | 评价 |
|---|---|
| API 契约一致性 | **A-** |
| 网络层结构 | **B+** |
| 数据层 / Room | **B+** |
| DI / 架构边界 | **B-** |
| UI 可维护性 | **B-** |
| 测试覆盖 | **B+** |
| 死代码控制 | **B+** |
| 综合 | **B+，已脱离不可维护阶段，进入结构性收口阶段** |

### 一句话结论

现在最需要避免的是“为了看起来干净而继续无差别删代码”。真正的下一轮价值在于**把最后一个 active resolver compatibility caller 迁走，然后删掉兼容层；随后冻结 V1 读取边界，并统一 pending-operation 语义**。这些做完后，项目结构才算真正进入稳定维护期。
