# Badger-Android 代码质量 / API 契约审查报告

日期：2026-09-01  
审查基线：`dev` + 现有 `refactor/dev-cleanup-2026-08-31`  
工作分支：`refactor/dev-cleanup-2026-08-31`（本轮未创建新分支）  
当前审查 HEAD：`e7bd4d7ee871e95872b5c91bdcc7b956bd4588c5`

> 本文为连续审查记录。相较上一版，本轮已经把报告中标记为“下一阶段”的 Service Locator 与 Resolver UI compatibility 继续清除，并同步修正相关文档状态。当前剩余重点转向历史 DTO、Repository/pending-operation 一致性以及大型 Compose 文件拆分。

## 1. 当前总体结论

项目已经脱离“不可维护屎山”的阶段。网络层、V2 cache、sync、Repository 与 UI 已形成基本边界；V1 HTTP compatibility 不再作为架构负担保留。

截至当前 HEAD，本轮已完成：

1. **删除 Resolver UI compatibility**：`NetworkResolveResult`、`getResultInfo()`、`IdentifyResponse.signature` 兼容投影及对应 shim 已删除。
2. **核心 Service Locator 清理完成**：Resolver、ShortLinkService、SetupGuideViewModel、ServerShortLinkViewModel 改为 constructor injection；`KoinComponentBy` 已删除。
3. **NFC DI compatibility shim 删除**：`NfcKoinInjectCompat.kt` 已删除，页面使用正式 Koin 注入入口。
4. **删除无消费者的 NfcSettingsViewModel** 及对应 Koin binding。
5. **ShortLinkService 网络 I/O 与职责边界继续收口**：网络刷新走挂起/IO 路径，避免初始化阶段主线程网络访问。
6. **short.io credential ownership 保持服务端化**：客户端只消费 `shortioApiKeySet` / clear/set API，不把 server-owned key 作为本地 source-of-truth。

当前没有再引入 V1 HTTP facade。历史数据 DTO 仍保留，但不再与“V1 API compatibility”混为一谈。

## 2. 服务端 API 契约核对

服务端交接文档规定：除 AI 与 short.io 两个代理模块成功时返回裸 JSON，其余 `/api` 端点统一 `ApiResult`；Android 使用 Bearer token；安装未完成时除 `/api/setup/*` 外统一 503；resolver、settings、shortlinks、admin shortlinks 等路径保持文档要求的尾斜杠。fileciteturn0file0L11-L20 fileciteturn0file0L24-L40

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

此前报告曾推断服务端存在 `/api/user/backups`。给定交接文档并没有定义这个 REST endpoint；文档只在 maxRequestSize 条目提到 backup envelope，不能据此推导 Android 应实现 BackupApi。fileciteturn0file0L379-L382

## 3. V1 API 与历史 DTO 的边界

产品侧已经明确：**V1 HTTP API 从未被真实用户使用，因此无需兼容、无需灰度迁移、无需保留 V1 HTTP facade。**

因此 Resolver 的兼容 facade 已从代码中彻底删除。当前链路为：

```text
jumpLink / value
      ↓
ContactNetworkResolver.identify()
      ↓
IdentifyResponse
      ↓
UserProfileRepository / UI
```

不再经过 `NetworkResolveResult`、`getResultInfo()` 或 `signature` alias。

需要单独说明：`Models.kt` 里仍存在 `ContactField` / `CustomField` / `ContactFieldValue` 等历史 DTO。它们当前属于本地数据字段定义/Repository/mapper 边界，不等于 V1 HTTP API，因此本轮没有依据名称直接删除。下一步将按实际引用继续收敛。

## 4. 本轮新增完成项

### P0：删除 Resolver UI compatibility layer

删除：

- `app/src/main/kotlin/top/mcxiafeng/badger/network/ResolverUiCompat.kt`
- `NetworkResolveResult`
- `ContactNetworkResolver.getResultInfo()`
- `IdentifyResponse.signature` 兼容扩展
- 旧 resolver projection 语义

当前 resolver 只暴露 canonical `/api/resolve/` contract。

### P1：Service Locator 核心路径清理

此前 `GlobalContext.get()` / `KoinComponentBy.get()` 隐藏依赖；当前已把核心路径改为构造注入：

```text
Koin
  ↓
constructor injection
  ↓
ViewModel / Service / Resolver
```

已删除：

- `app/src/main/kotlin/top/mcxiafeng/badger/di/KoinComponentBy.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/settings/NfcKoinInjectCompat.kt`

对全仓可检索 `GlobalContext` 的检查未发现残留结果；因此本报告不再把 Service Locator 列为“待处理”，只保留普通 DI 结构优化作为后续维护项。

### P1：NFC 页面 DI 与 I/O 收口

`NfcSettingsPage` 已从临时 `GlobalContext` shim 转为正式注入方式；`NfcSettingsViewModel` 无实际消费者，继续保持删除状态。

ShortLinkService 的远端刷新保持在挂起/IO 路径，不在 UI 初始化 state 中直接触发阻塞网络请求。

### P1：Token refresh client 分叉

ServerApi 与 Koin 使用同一带 auth/refresh interceptor 的 client；refresh 自身保留无 refresh interceptor 的底层 client，避免递归刷新。

### P1：short.io API Key ownership

旧版本地 Key source-of-truth 已取消。客户端通过设置 API 管理服务端 credential 状态，GET 只拿 `shortioApiKeySet`，清除通过 `clearShortioApiKey=true`。

## 5. 当前代码质量评价

### 网络层：A-

`ApiCore` 集中 request construction、Bearer、URL join、HTTP 执行和 `ApiResult` 解包；Auth / Resolver / AI / short.io / domain API 已分域。Resolver compatibility 已完全从 HTTP/UI 边界移除。

### DI / 架构边界：A-

核心 Service Locator 已清除，构造注入成为主路径。剩余问题主要是少量历史代码的职责边界，而不是隐藏式依赖获取。

### 数据层 / Room：B+

V2 cache 与旧 Room schema 已基本分离；Room migration / importer 等历史资产应继续保留。需要进一步关注 Repository 与 pending operation 的最终一致性。

### UI 可维护性：B-

`ContactDetailPage`、`ContactDetailDialogs`、`ScannerDialogs`、`PersonPage`、`CardPage` 等仍然偏大。它们不是立即性的 correctness bug，但会显著提高后续修改成本。

### 测试：B+

ApiCore / Resolver / HttpUtil / SafeLog 等回归测试保留；Resolver contract test 覆盖当前 canonical body、批量限制和错误边界。最终评分仍受大型 feature UI 与 repository failure-path 集成测试不足影响。

## 6. 死代码 / 兼容代码状态

### 已删除

- `ResolverUiCompat.kt`
- `NetworkResolveResult`
- `ContactNetworkResolver.getResultInfo()`
- `IdentifyResponse.signature` 兼容扩展
- `KoinComponentBy.kt`
- `NfcKoinInjectCompat.kt`
- `NfcSettingsViewModel.kt`
- 对应无消费者 Koin binding
- 本地 short.io API Key accessor / storage
- V1 migration test 的旧语义（测试命名/内容已收敛为当前 contract）

### 必须保留

- Room schema migrations
- QAuxv importer
- sync cursor / history
- `PlatformEntry` shared JSON shape
- SafeLog / HTTP error classification
- 历史 DTO（在未完成实际引用扫描前，不做无依据删除）

### 当前明确不属于死代码

- `ContactField`
- `CustomField`
- `ContactFieldValue`

它们仍可能服务 FieldRepository / mapper / 本地字段业务，因此本轮只更新文档语义，不做删除。

## 7. 剩余工作清单

### P1：历史 DTO 真实引用扫描

目标：确认 `ContactField` / `CustomField` / `ContactFieldValue` 的所有 production consumer。

处理规则：

```text
无生产引用
  → 删除

仅 migration / importer 引用
  → 迁移到明确 legacy/data-import 边界

仍属于业务 DTO
  → 保留，但去掉误导性的 V1 HTTP 命名
```

### P1：Repository / PendingOperation 一致性

重点审查：

- 本地写入与远端成功之间的状态转换
- HTTP 失败后的入队语义
- retry / revert / stuck-op 处理
- DELETE / MERGE 等不可逆操作的幂等性
- Worker 与 UI `CommitResult` 语义是否统一

### P2：大型 Compose 拆分

建议按照 feature + responsibility 拆：

```text
ContactDetail
  ├─ Header
  ├─ ContactFields
  ├─ Platforms
  ├─ Actions
  └─ Dialogs

Scanner
  ├─ Preview
  ├─ Result
  ├─ Import
  └─ Dialogs
```

不建议机械按行数切文件。

### P2：最终 dead-code / obsolete-comment sweep

重点检查：

- `V1` / `legacy` / `compat` 注释是否还准确
- 已删除类型名称是否存在文档残留
- 无消费者 ViewModel / UseCase / helper
- 重复 extension / utility
- 旧 API path 字符串

## 8. 推荐最终结构

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

## 9. 当前 CI 状态

当前分支 push CI 会自动执行 `Build Debug APK`。

最新已知 HEAD：`e7bd4d7ee871e95872b5c91bdcc7b956bd4588c5`。该提交的修改内容包含删除 `ResolverUiCompat.kt`，GitHub Actions 已为该 HEAD 触发新的 Build Debug APK 流程。fileciteturn79file0

截至当前记录时，最新 workflow 尚未提供最终 build conclusion，因此**不能声称当前 HEAD 已构建通过**。后续以 Actions 的最终结果为准。

## 10. 综合评级

| 维度 | 当前评级 |
|---|---|
| API 契约一致性 | **A** |
| 网络层结构 | **A-** |
| 数据层 / Room | **B+** |
| DI / 架构边界 | **A-** |
| UI 可维护性 | **B-** |
| 测试覆盖 | **B+** |
| 死代码控制 | **A-** |
| 综合 | **B+ / A- 边缘** |

### 结论

V1 HTTP API 已经可以视为结束，不应继续添加兼容壳。核心 Service Locator 也已经清除。当前真正值得投入的工作已经从“迁移兼容”转向：

1. 历史 DTO 的真实引用收敛；
2. Repository / PendingOperation 的一致性与失败补偿；
3. ContactDetail / Scanner 等大型 Compose feature 拆分；
4. 最终一次全仓 obsolete/dead-code 扫描。

后续每完成一个阶段，应同步更新本文件，避免出现“代码已经修了、报告仍写旧状态”的情况。
