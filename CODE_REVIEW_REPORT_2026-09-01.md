# Badger-Android 代码质量 / API 契约审查报告

日期：2026-09-01  
审查基线：`dev` + 现有 `refactor/dev-cleanup-2026-08-31`  
工作分支：`refactor/dev-cleanup-2026-08-31`（本轮未创建新分支）  
当前 HEAD：`a898af19087f9bf9e97ce112c9f66f402b36bd19`  
当前分支相对 `dev`：**79 commits ahead / 0 behind**

> 本文是上一轮审查报告的继续版。重点是继续完成未完成项，并用 2026-08-30 服务端 API 交接文档核对 Android V2 客户端。

## 1. 总体结论

当前工程已经脱离“不可维护屎山”的阶段。网络层、V2 cache、sync、Repository 与 UI 已形成基本边界；剩余技术债主要集中在 **legacy compatibility、Service Locator、UI 大 Composable、Repository 远端失败补偿一致性**。

本轮继续处理了 4 类问题：

1. **Resolver 半迁移回归**：显式保留并收窄最后一个兼容边界，canonical 数据模型统一使用 `description`。
2. **Token refresh client 分叉**：ServerApi 与 Koin 现在使用同一个安装了 auth/refresh interceptor 的 OkHttp client。
3. **short.io Key ownership 错误**：移除 Android 本地 API Key accessor / storage，页面只依赖服务器 `shortioApiKeySet`。
4. **NFC 初始化同步网络 I/O**：`ShortLinkService.getShortUrl()` 现在只读本地短 URL 缓存，不再在 Compose state initializer 中发同步网络请求。

另外，已经确认 `NfcSettingsViewModel` 没有任何当前消费者，页面已直接注入 `ServerApi`，因此该 ViewModel 及其 Koin binding 已删除。

## 2. 服务端 API 契约核对

服务端交接文档规定：除 AI 与 short.io 两个代理模块成功时返回裸 JSON，其余 `/api` 端点统一 `ApiResult`；Android 走 Bearer token；安装未完成时除 `/api/setup/*` 外统一 503；resolver、settings、shortlinks、admin shortlinks 等路径需保持文档要求的尾斜杠。fileciteturn0file0L11-L20 fileciteturn0file0L24-L40

Resolver 的正式契约为：单条 POST body `{ input, ... }`，批量 `{ items: [...] }`；批量最大 50；单条 `data` 直接是 ResolveResult，批量 `data.results`；字段采用 camelCase，例如 `avatarUrl`、`description`、`jumpLink`、`contacts`。fileciteturn0file0L165-L193

用户设置契约允许通过 `/api/user/settings` 更新 `shortioApiKey` 或 `clearShortioApiKey`；GET 只返回 `shortioApiKeySet`，不会回传明文 key。fileciteturn0file0L154-L161

密钥纪律、sync 日志、AI 日志、批量 50 上限、6 MiB 传输限制都属于应保持的行为约束。fileciteturn0file0L371-L382

### 已确认一致的客户端覆盖

- Auth：register / login / refresh / logout / me / registerPolicy / captcha / verification / forgot-password / change-password
- User：profile / person / collection / tag / device / notification
- Sync：`/api/user/sync?since=&limit=`
- Settings：`/api/user/getSettings` + `/api/user/settings`
- Stats：`/api/user/stats`
- Upload：`/api/user/upload` multipart
- Resolver：单条与批量 `/api/resolve/`
- AI：`/api/proxy/ai/tasks/tag_generate`、`contact_ocr`
- short.io：links / domains proxy
- 自建短链：`/api/shortlinks/`

### 需要避免的错误结论

上一版报告曾声称服务端定义 `/api/user/backups`。**给定交接文档没有定义这个 REST endpoint**；文档只在 maxRequestSize 中提到 backup envelope，不能据此推导 Android 应实现 BackupApi。fileciteturn0file0L379-L382

## 3. 本轮实际修复

### P0：Resolver compatibility 收口

上一轮删除 `getResultInfo*` 后，`SetupStepPlatforms.kt` 仍有旧调用，因此出现生产调用与测试不一致。本轮先把兼容边界重新明确化：

- `IdentifyResponse` 为 canonical model；
- `signature` 已删除，正式字段为 `description`；
- compatibility projection 只能由 `identify()` 生成；
- compatibility projection 与 `getResultInfo()` 均标记 `@Deprecated`；
- canonical contract tests 保持 single/batch 覆盖。

**剩余债务**：`SetupStepPlatforms` 仍需要最后一次直接改用 `IdentifyResponse`，随后才能删除 compatibility projection 本身。当前没有在不完整的大文件内容基础上盲改这一块。

### P1：Token refresh client 分叉

原先存在：

```text
baseClient -> ServerApi
          -> baseClient.newBuilder(+auth/+refresh) -> Koin OkHttpClient
```

即 ServerApi 使用的 client 并不带 refresh interceptor。

现已统一为：

```text
baseClient
   ↓
client(+auth interceptor + refresh interceptor)
   ↓
ServerApi(client)
   ↓
factory.install(api)
```

refresh 本身继续使用底层 client，避免 refresh recursion；服务器地址拼接同时做尾斜杠规范化。

### P1：short.io API Key ownership

旧实现把 API Key 放进 `ShortLinkPrefs`，并由本地 key 是否为空决定 short.io 是否“已配置”。这与服务端交接文档冲突。

现在：

- `ShortLinkPrefs` 不再保存 `api_key`；
- `ShortLinkService.getApiKey/saveApiKey` 已删除；
- NFC 页面通过 `ServerApi.getUserSettings()` 读取 `shortioApiKeySet`；
- 输入新 Key 通过 `updateUserSettings(shortioApiKey=...)` 写服务器；
- 清除通过 `clearShortioApiKey=true`；
- domains / links 只由服务器配置状态决定。

这样避免了客户端长期保留 server-owned credential。

### P1：NFC 页面同步网络 I/O

之前 `NfcSettingsPage` 的 Compose state initializer 会调用 `ShortLinkService.getShortUrl(context)`，而该方法会同步访问 `/api/proxy/shortio/links`。这存在 UI thread I/O 风险。

现在 `getShortUrl()` 是纯本地 accessor，只读取 `ShortLinkPrefs.short_url`；远端详情刷新仍由 suspend 方法负责，并在页面的异步 effect 中执行。

创建/选择/更新短链接时同步更新本地短 URL cache，因此 UI 首次渲染仍能快速显示上次选择。

### P2：删除真实死 ViewModel

当前 `NfcSettingsPage` 已经直接 `koinInject<ServerApi>()`，不再使用 `NfcSettingsViewModel`。该 ViewModel 只剩一个 Repository holder，没有独立状态，也没有其他 caller。

已删除：

- `app/src/main/kotlin/top/mcxiafeng/badger/pages/settings/NfcSettingsViewModel.kt`
- 对应 Koin `viewModel` binding

这是本轮最明确、风险最低的死代码删除之一。

## 4. 当前代码质量评价

### 优点

**网络层已经明显结构化。** `ApiCore` 负责 URL、Bearer、HTTP 执行、ApiResult shell；Auth / Resolver / AI / short.io / domain API 分域。`ServerApi` 基本退回 facade 职责。

**Resolver contract tests 有实际价值。** 当前测试覆盖单条 canonical body、批量 50 分块、结果缺失、5xx、旧字段拒绝等边界。

**V2 cache 边界明确。** `Models.kt` 中部分 V1 model 已明确标记为 DTO，不再映射 Room 表；V2 cache entity 是当前主要持久化模型。fileciteturn20file0L1-L3

**敏感配置边界更合理。** short.io key 现在只在服务端持有，客户端 GET 只消费 `shortioApiKeySet`，符合服务端交接文档的密钥纪律。fileciteturn0file0L154-L161 fileciteturn0file0L371-L382

### 仍值得重点处理

**1. Service Locator。** `GlobalContext.get()` / `KoinComponentBy.get()` 仍隐藏部分依赖。新代码应构造注入，旧代码渐进迁移。

**2. Resolver compatibility projection。** 仍有一个 active caller，需要直接迁移后删除，而不是长期保留 deprecated facade。

**3. V1 DTO 生产引用冻结。** migration/import 可以保留，但业务主链应该只使用 V2 cache / server DTO。

**4. Repository failure semantics。** local cache、sync、pending operation 的失败补偿仍未完全统一，长期应收口到 pending-operation + worker。

**5. UI 大文件。** `ContactDetailPage`、`ContactDetailDialogs`、`ScannerDialogs` 参数/状态仍偏密集，属于 P2 可维护性问题。

## 5. 死代码 / 兼容代码状态

### 已移除或已确认退出生产主链

- 旧 resolver projection tests；
- 旧 `/v1` resolver 生产路径；
- 多处旧 FTS / V1 DAO 引用；
- 无效 Experimental API opt-in；
- 多余 import / debug helper；
- `NfcSettingsViewModel` 及其 Koin binding；
- 本地 short.io API Key accessor / storage。

### 必须继续保留

- V1→V2 migrations；
- QAuxv importer；
- `PlatformEntry` shared JSON shape；
- UserHistory / sync cursor；
- SafeLog / HTTP error classification。

### 当前仍是迁移债务

- `NetworkResolveResult/getResultInfo()`；
- `ContactNetworkResolver` 的 Service Locator；
- V1 DTO 的非 migration/import 引用点；
- `ShortLinkService` 本身的 GlobalContext facade。

## 6. 最终结构目标

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
  migration / importer / one-shot compatibility
```

## 7. 下一阶段执行顺序

1. 直接迁移 `SetupStepPlatforms` 到 canonical `IdentifyResponse`，删除 resolver compatibility projection。
2. 给 resolver/short-link service 做构造注入，逐步消灭 `GlobalContext.get()`。
3. 建立 V1 DTO 生产引用清单，并限制到 migration/import。
4. 统一 Repository + pending operation 的离线失败语义。
5. 拆 ContactDetail / Scanner 大 Composable。
6. 对短链接配置做最终 dead-code sweep，确认没有旧本地 Key accessor 的引用后删除其全部兼容痕迹。

## 8. 测试与 CI 状态

当前分支的 CI 已配置对清理分支 push 构建。此前提交已经成功触发 workflow；但针对**当前 HEAD `a898af1...`** 的最终 Build Debug APK 结果在本报告生成时尚未返回，因此不能声称当前 HEAD 已构建通过。

当前可确认的是：

- `NfcSettingsViewModel` 已完全从代码和 Koin binding 删除；
- `ShortLinkPrefs` 已没有本地 API Key 字段；
- `ShortLinkService.getShortUrl()` 不再执行网络请求；
- 分支相对 `dev` 为 79 commits ahead / 0 behind。

## 9. 综合评级

| 维度 | 评级 |
|---|---|
| API 契约一致性 | **A-** |
| 网络层结构 | **B+** |
| 数据层 / Room | **B+** |
| DI / 架构边界 | **B** |
| UI 可维护性 | **B-** |
| 测试覆盖 | **B+** |
| 死代码控制 | **A-** |
| 综合 | **B+** |

结论：项目已经进入“结构性收口”阶段。现在最值钱的工作不是继续大面积格式化，而是把最后一个 active resolver compatibility caller 去掉、继续收敛 Service Locator、冻结 V1 生产读取边界，并统一 pending operation 语义。
