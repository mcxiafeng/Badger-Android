# Badger-Android 代码质量 / API 契约审查报告

日期：2026-09-01  
审查基线：`dev` + 现有 `refactor/dev-cleanup-2026-08-31`  
工作分支：`refactor/dev-cleanup-2026-08-31`（本轮未创建新分支）

> 本文是上一轮审查报告的继续版。本轮根据“V1 API 从未实际使用，可直接删除”的产品事实，把 V1 HTTP compatibility 从“渐进迁移”改为“直接移除”；同时继续处理网络正确性、NFC I/O、死代码和 API 契约。

## 1. 总体结论

当前工程已经脱离“不可维护屎山”的阶段。网络层、V2 cache、sync、Repository 与 UI 已形成基本边界；本轮最大的变化是：**不再为从未上线/从未被用户使用的 V1 HTTP API 保留兼容 facade。**

本轮继续完成了 5 类问题：

1. **彻底删除 Resolver V1 compatibility**：`NetworkResolveResult`、`getResultInfo()` 及相关旧投影全部移除，`SetupStepPlatforms` 直接消费 canonical `IdentifyResponse`。
2. **删除 V1 迁移测试语义残留**：`ApiPathMigrationTest` 重命名为 `ApiPathContractTest`，测试现在只描述当前 `/api` 契约，不再暗示需要维护旧 API。
3. **Token refresh client 分叉已修复**：ServerApi 与 Koin 共用同一带 auth/refresh interceptor 的 OkHttp client。
4. **short.io Key ownership 已收口**：Android 不再把 server-owned API Key 存进本地 prefs，使用 `/api/user/settings` 的 `shortioApiKeySet` / `clearShortioApiKey`。
5. **NFC 初始化同步网络 I/O 已移除**：`ShortLinkService.getShortUrl()` 现在只读本地缓存，网络刷新由异步 effect/挂起方法负责。

另外，确认无消费者后已删除 `NfcSettingsViewModel` 及其 Koin binding。

## 2. 服务端 API 契约核对

服务端交接文档规定：除 AI 与 short.io 两个代理模块成功时返回裸 JSON，其余 `/api` 端点统一 `ApiResult`；Android 走 Bearer token；安装未完成时除 `/api/setup/*` 外统一 503；resolver、settings、shortlinks、admin shortlinks 等路径需保持文档要求的尾斜杠。fileciteturn0file0L11-L20 fileciteturn0file0L24-L40

Resolver 正式契约：单条 POST body `{ input, ... }`，批量 `{ items: [...] }`；批量最大 50；单条 `data` 直接是 ResolveResult，批量 `data.results`；字段使用 camelCase，如 `avatarUrl`、`description`、`jumpLink`、`contacts`。fileciteturn0file0L165-L193

用户设置契约：POST `/api/user/settings` 可更新 `shortioApiKey` 或通过 `clearShortioApiKey` 清除；GET 只返回 `shortioApiKeySet`，不返回 key 明文。fileciteturn0file0L154-L161

密钥纪律、sync 日志、AI 日志、批量 50 上限和 6 MiB 传输限制均属于应保持的行为约束。fileciteturn0file0L371-L382

### 当前确认一致的客户端覆盖

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

### 已纠正的旧结论

之前报告曾推断服务端存在 `/api/user/backups`。给定交接文档并没有定义这个 REST endpoint；文档只在 maxRequestSize 条目提到 backup envelope，不能据此推导 Android 应实现 BackupApi。fileciteturn0file0L379-L382

## 3. V1 API 直接删除结论

产品侧已经明确：**V1 相关 API 从未被真实用户使用，因此无需兼容、无需灰度迁移、无需保留 V1 HTTP facade。**

据此本轮执行以下删除策略：

- 删除 `ContactNetworkResolver` 中 V1/transitional `NetworkResolveResult`；
- 删除 `getResultInfo()` 及其兼容投影逻辑；
- `SetupStepPlatforms` 改为直接消费 `IdentifyResponse`；
- 删除旧 projection 测试；
- 将 `ApiPathMigrationTest` 重命名为 `ApiPathContractTest`，消除“仍需维护 V1 路径”的错误语义；
- 当前 network API 文件只保留 canonical `/api/*` 调用路径。

这比继续堆 `@Deprecated` facade 更符合项目现状：没有真实 V1 consumer，就不应让兼容代码长期占据架构边界。

需要注意：`Models.kt` 中仍有少量 V1 命名的数据 DTO，以及 Room migration / importer 等历史数据处理资产。这些**不是 V1 HTTP API**，不能因为名称带 V1 就直接删除；它们属于本地数据迁移/导入边界，后续可以单独做引用扫描。

## 4. 本轮实际修复

### P0：Resolver compatibility 完全删除

当前 `SetupStepPlatforms` 已直接：

```text
jumpLink / value
      ↓
ContactNetworkResolver.identify()
      ↓
IdentifyResponse
      ↓
UserProfileRepository
```

不再经过 `NetworkResolveResult` 或 `getResultInfo()`。

Resolver parser 统一读取服务端正式的 `description`，不再接受 `signature` 作为内部别名。

### P1：Token refresh client 分叉

原来存在两只 OkHttp client：ServerApi 使用基础 client，而 Koin 拿到的是带 auth/refresh interceptor 的另一只 client。这会使自动刷新 token 对 ServerApi 请求失效。

现已统一：

```text
baseClient
   ↓
client(+auth interceptor + refresh interceptor)
   ↓
ServerApi(client)
   ↓
ServerApiFactory
```

refresh 本身仍使用无 refresh interceptor 的底层 client，避免递归刷新。

### P1：short.io API Key ownership

旧版 Android 将 Key 存入本地 `ShortLinkPrefs`。现在本地不再持有 server-owned short.io credential：

- GET：读取 `shortioApiKeySet`；
- 设置：POST `/api/user/settings` + `shortioApiKey`；
- 清除：POST `/api/user/settings` + `clearShortioApiKey=true`；
- domains / links 只在服务器已配置时加载。

符合服务端的密钥纪律要求。fileciteturn0file0L154-L161 fileciteturn0file0L371-L382

### P1：NFC 初始化网络 I/O

`NfcSettingsPage` 初始化时不再通过 state initializer 同步访问网络。`ShortLinkService.getShortUrl()` 只读取本地短 URL cache，远端详情通过挂起函数在异步 effect 中刷新。

### P2：删除真实死 ViewModel

`NfcSettingsPage` 已经直接注入 `ServerApi`，`NfcSettingsViewModel` 没有任何实际消费者，因此已删除：

- `app/src/main/kotlin/top/mcxiafeng/badger/pages/settings/NfcSettingsViewModel.kt`
- 对应 Koin binding

### P2：清理 V1 迁移测试命名

旧 `ApiPathMigrationTest` 的测试内容实际上全部验证当前 `/api` endpoint。已改名为 `ApiPathContractTest`，避免项目继续保留“V1 → V2 迁移仍在进行”的假象。

## 5. 当前代码质量评价

### 优点

**网络层已经结构化。** `ApiCore` 集中 request construction、Bearer、URL join、HTTP 执行和 `ApiResult` 解包；Auth / Resolver / AI / short.io / domain API 已分域。

**Resolver contract tests 有实际价值。** 覆盖单条 canonical body、批量 50 分块、缺失 results、5xx、旧字段拒绝等边界。

**V2 cache 边界基本明确。** 旧数据 model 与新的 cache entity 已分开，当前数据库主链不再依赖退役 V1 Room 表。fileciteturn20file0L1-L3

**敏感配置边界更合理。** short.io Key 已从本地 source-of-truth 移到服务端。

### 仍值得继续处理

**1. Service Locator。** `GlobalContext.get()` / `KoinComponentBy.get()` 仍隐藏依赖。新代码应优先 constructor injection。

**2. V1 DTO。** 这里已经不是 V1 API 问题，而是历史 DTO 命名/数据边界问题。需要单独扫描实际生产引用，再判断是否能删。

**3. Repository failure semantics。** local cache、sync、pending operation 的失败补偿仍未完全统一。

**4. UI 大文件。** `ContactDetailPage`、`ContactDetailDialogs`、`ScannerDialogs` 等仍偏大，属于 P2 可维护性债务。

**5. ShortLinkService facade。** short-link service 仍通过 `GlobalContext` 取得 `ServerApiFactory`，是下一批适合 constructor injection 的目标。

## 6. 死代码 / 兼容代码状态

### 已明确删除

- Resolver `NetworkResolveResult`；
- `ContactNetworkResolver.getResultInfo()`；
- 旧 resolver projection tests；
- `NfcSettingsViewModel`；
- 无消费者的 Koin ViewModel binding；
- 本地 short.io API Key accessor / storage；
- `ApiPathMigrationTest` 的旧迁移语义（改名为 contract test）。

### 必须保留

- Room schema migrations；
- QAuxv importer；
- sync cursor / history；
- `PlatformEntry` shared JSON shape；
- SafeLog / HTTP error classification。

### 仍建议后续审查

- `ContactField` / `CustomField` / `ContactFieldValue` 等历史 DTO 的真实生产引用；
- Service Locator；
- Repository pending-operation 一致性；
- UI 巨型 Composable。

## 7. 最终结构目标

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
  migration / importer / one-shot data compatibility
```

这里的 `legacy/` 只表示历史数据迁移/导入，不表示保留 V1 HTTP API。

## 8. 下一阶段执行顺序

1. 清理 Resolver / ShortLinkService 的 `GlobalContext`，改为构造注入。
2. 对 `Models.kt` 的 V1 命名 DTO 做真实引用扫描；能删的直接删，不能删的移动到明确的 compatibility/data-import 边界。
3. 统一 Repository + pending operation 的离线失败语义。
4. 拆 ContactDetail / Scanner 大 Composable。
5. 最终进行一次全仓 dead-code / obsolete-comment sweep。

## 9. 测试与 CI 状态

当前分支 push CI 已启用，并会对 `refactor/dev-cleanup-2026-08-31` 自动执行 Build Debug APK。

截至本报告更新时，当前最新提交的 CI 结果尚未完成，因此这里不声称“当前 HEAD 构建通过”。此前已存在的 ApiCore / Resolver / HttpUtil / SafeLog 回归测试继续保留，`ApiPathMigrationTest` 已更名为 `ApiPathContractTest`。

当前分支相对 `dev`：**85 commits ahead / 0 behind**。

## 10. 综合评级

| 维度 | 评级 |
|---|---|
| API 契约一致性 | **A** |
| 网络层结构 | **B+** |
| 数据层 / Room | **B+** |
| DI / 架构边界 | **B** |
| UI 可维护性 | **B-** |
| 测试覆盖 | **B+** |
| 死代码控制 | **A** |
| 综合 | **B+** |

结论：项目现在已经可以把 V1 HTTP API 视为彻底结束，不需要再保留兼容壳。下一阶段应把精力从“迁移兼容”转向真正的结构优化：constructor injection、历史 DTO 引用收敛、pending-operation 一致性和 UI 拆分。
