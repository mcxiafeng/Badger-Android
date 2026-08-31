# Badger-Android 代码质量 / API 契约审查报告

日期：2026-09-01  
审查基线：`dev` + 现有 `refactor/dev-cleanup-2026-08-31`  
工作分支：`refactor/dev-cleanup-2026-08-31`（本轮未创建新分支）

> 本文是上一轮审查报告的继续版。重点是继续完成未完成项，并用 2026-08-30 服务端 API 交接文档重新核对 Android V2 客户端，而不是重复旧报告。

## 1. 总体结论

当前工程已经脱离“不可维护屎山”的阶段。网络层、V2 cache、sync、Repository 与 UI 已经形成基本边界；现在最大的技术债集中在 **legacy compatibility、Service Locator、UI 大 Composable、Repository 的远端失败补偿一致性**。

本轮继续发现并修复了 3 类真实问题：

1. **Resolver 迁移半完成回归**：上一轮删掉 `ContactNetworkResolver.getResultInfo*`，但 `SetupStepPlatforms.kt` 仍调用旧入口。若不补齐会形成生产代码/测试代码不一致。
2. **Token refresh 形同虚设**：`ServerApi` 原先使用不带 refresh interceptor 的基础 `OkHttpClient`，而 Koin 返回的是另一只带 interceptor 的 client，形成隐藏的“双 client”分叉。已改为 ServerApi 与 Koin 共用同一带认证/refresh interceptor 的 client。
3. **short.io Key 存储边界错误**：NFC 设置页原先把 API Key 当成本地 `SharedPreferences` source-of-truth；服务端契约明确要求 key 存服务端、客户端只读取 `shortioApiKeySet`。已改为 `/api/user/settings` 写入/清除，UI 不再依赖本地 Key 判断服务端能力。

同时将 resolver 内部旧字段名 `signature` 收口为服务端正式字段 `description`，并把剩余兼容层显式标记 `@Deprecated`，避免 legacy API 继续扩散。

## 2. 服务端 API 契约核对

服务端交接文档规定：除 AI 与 short.io 两个代理模块成功时返回裸 JSON，其余 `/api` 端点统一 `ApiResult`；Android 走 Bearer token；安装未完成时除 `/api/setup/*` 外统一 503；`/api/resolve/`、`/api/settings/`、`/api/shortlinks/`、`/api/admin/shortlinks/` 必须保留尾斜杠。fileciteturn0file0L11-L20 fileciteturn0file0L24-L40

Resolver 的正式契约是：单条 POST body `{ input, ... }`，批量 `{ items: [...] }`；批量最大 50；单条 `data` 直接是 ResolveResult，批量 `data.results`；字段为 camelCase，包含 `avatarUrl`、`description`、`jumpLink`、`contacts` 等。fileciteturn0file0L165-L193

用户设置契约允许 POST `/api/user/settings` 更新 `shortioApiKey` 或通过 `clearShortioApiKey` 清除；GET 只返回 `shortioApiKeySet`，不会返回 key 明文。fileciteturn0file0L154-L161

密钥、sync 日志、AI 日志、批量 50 上限、6 MiB 传输限制都是必须保持的行为约束。fileciteturn0file0L371-L382

### 已确认一致的客户端覆盖

- Auth：register/login/refresh/logout/me/registerPolicy/captcha/verification/forgot-password/change-password
- User：profile/person/collection/tag/device/notification
- Sync：`/api/user/sync?since=&limit=`
- Settings：`/api/user/getSettings` + `/api/user/settings`
- Stats：`/api/user/stats`
- Upload：`/api/user/upload` multipart
- Resolver：单条与批量 `/api/resolve/`
- AI：`/api/proxy/ai/tasks/tag_generate`、`contact_ocr`
- short.io：links/domains proxy
- 自建短链：`/api/shortlinks/`

### 需要避免的错误结论

上一版报告曾声称服务端定义 `/api/user/backups`。**给定的交接文档没有这个 REST endpoint 定义**；文档只在 maxRequestSize 中提到 backup envelope，因此不能据此推导出 Android 应实现 BackupApi。fileciteturn0file0L379-L382

## 3. 本轮实际修复

### P0：Resolver compatibility 回归

`SetupStepPlatforms.kt` 仍使用旧 `getResultInfo()`，而上一轮已经把它删除。本轮没有简单恢复一套旧实现，而是恢复为一个明确的迁移边界：

- canonical source 仍是 `identify()`；
- `NetworkResolveResult` 与 `getResultInfo()` 标记 `@Deprecated`；
- `IdentifyResponse.signature` 改为 `description`；
- canonical resolver tests 改为断言 `description`，同时继续确认旧 `signature/avatar_url/contact_map` 不会被错误接受。

这样能保持当前生产调用安全，同时把最后一个 legacy caller 变成可定位的债务。

### P1：Token refresh client 分叉

原结构是：

```text
baseClient -> ServerApi
          -> factory.install(api)
          -> baseClient.newBuilder(+refresh) -> Koin OkHttpClient
```

因此 ServerApi 自己发出的请求并没有经过 refresh interceptor。

现在改为：

```text
baseClient
   ↓
client(+auth interceptor + refresh interceptor)
   ↓
ServerApi(client)
   ↓
factory.install(api)
```

refresh 本身继续使用无 refresh interceptor 的底层 client，避免递归；refresh URL 同时做 `trimEnd('/')`。

### P1：short.io Key 位置错误

旧 NFC 设置页把 Key 存在 `ShortLinkPrefs`，然后由本地 key 是否为空决定是否拉取 domains。服务端交接文档明确要求 short.io key 存在服务端，并且用户端 GET 只返回 `shortioApiKeySet`。fileciteturn0file0L209-L217 fileciteturn0file0L154-L160

现在：

- 启动时通过 `ServerApi.getUserSettings()` 读取 `shortioApiKeySet`；
- 输入新 Key 后通过 `updateUserSettings(shortioApiKey=...)` 写服务器；
- 清除通过 `clearShortioApiKey=true`；
- domains / links 加载以 `shortioApiKeySet` 为准；
- 页面不再把本地 `ShortLinkPrefs.api_key` 当成 source-of-truth。

`ShortLinkService`/`ShortLinkPrefs` 中旧本地 Key accessor 暂未直接删除，因为无法仅靠不完整的代码搜索证明整个仓库已经没有其他消费者；它们应作为下一阶段显式 legacy 清理项。

## 4. 当前代码质量评价

### 优点

**网络层结构明显改善。** `ApiCore` 统一 request construction、Bearer、URL join、HTTP 执行和 `ApiResult` 解包；Auth/Resolver/AI/short.io/domain API 分域，已经比早期巨型 `ServerApi` 更容易测试。

**JSON 防御较成熟。** 通用 `stringOrNull`、时间、数值以及各 DTO parser 都避免对 null/object/array 进行不安全的 primitive 强转。

**V2 cache 边界清晰。** `Models.kt` 明确把部分 V1 entity 保留为 DTO，不再作为 Room 表；V2 cache entity 承担当前持久化职责。fileciteturn20file0L1-L3

**测试方向正确。** Resolver tests 覆盖 single/batch、50 分块、缺失 results、5xx、legacy 字段拒绝；ApiCore、HttpUtil、SafeLog 也有独立测试。

### 仍然值得重点处理

**1. Service Locator。** `GlobalContext.get()` / `KoinComponentBy.get()` 仍隐藏 ViewModel/Service 的依赖。新代码应该构造注入，旧代码渐进迁移。

**2. Legacy compatibility。** resolver compatibility 已显式化，但仍有 active caller。下一步应让 `SetupStepPlatforms` 直接消费 `IdentifyResponse`，然后删除 `NetworkResolveResult/getResultInfo()`。

**3. V1 DTO。** `ContactField` / `CustomField` / `ContactFieldValue` 等不能重新进入生产主链；建议建立“允许引用目录”或静态扫描规则，把 migration/import 与业务代码隔离。

**4. Repository failure semantics。** 当前 local cache、sync、pending operation 之间的失败补偿策略尚未完全统一，长期应采用“本地事务记录事实 + pending operation 记录副作用 + worker 重试”。

**5. UI 大文件。** `ContactDetailPage`、`ContactDetailDialogs`、`ScannerDialogs` 等参数和状态仍偏密集。它们属于 P2 可维护性问题，不应与 correctness 修复混为一谈。

## 5. 死代码 / 兼容代码处理结论

### 已确认可移除或上一轮已经移除

- 旧 resolver projection tests；
- 旧 `/v1` resolver 生产路径；
- 已被 V2 cache 替代的旧 DAO/FTS 引用；
- 无效 Experimental API opt-in；
- 多余 import / 旧 debug helper；
- 已退出 Koin useCaseModule 的旧 Search/Filter helper。

### 当前必须保留

- V1→V2 migrations；
- QAuxv importer；
- `PlatformEntry` shared JSON shape；
- UserHistory/sync cursor；
- SafeLog / HTTP error classification。

### 当前应继续清理但不能盲删

- `ShortLinkPrefs.api_key` 与 `ShortLinkService.getApiKey/saveApiKey`；
- `NetworkResolveResult/getResultInfo()` 兼容层；
- `ContactNetworkResolver` 的 GlobalContext Service Locator；
- V1 DTO 的生产引用点。

判断原则是“是否仍有迁移/导入/线上 caller”，不是单看文件是否旧。

## 6. 结构优化目标

建议最终边界保持为：

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

迁移顺序建议：Resolver caller → Service Locator → V1 read freeze → pending-operation 统一 → UI 大文件拆分 → 最终删除 short.io 本地 key accessor。

## 7. 一个尚未声称“已修复”的问题

当前 `NfcSettingsPage` 已经把 short.io Key source-of-truth 改到服务端，但页面仍存在历史代码路径：`ShortLinkService.getShortUrl(context)` 在初始化阶段可直接发网络请求，而该方法从 Compose state initializer 中被调用。

这属于**潜在 UI 线程网络 I/O**，应该在下一小步把 short URL 初始值改成 `null`，再放到 `LaunchedEffect + Dispatchers.IO` 中加载。由于该文件很大、当前 CI 尚未给出本轮最终构建结果，本轮没有在未验证的情况下继续做大幅度 UI 代码重写。

## 8. 测试 / CI 状态

当前清理分支的 push CI 已启用；最新代码提交自动触发 `Build Debug APK`。截至本报告编写时，针对最新变更的 GitHub Actions run 仍处于 `pending`，所以这里**不能宣称本轮最终 APK 构建已经通过**。citeturn69file0turn67file0

此前旧 HEAD `a0c84d8` 的 workflow 已被取消，不再作为本轮验证依据。

## 9. 综合评级

| 维度 | 评级 |
|---|---|
| API 契约一致性 | **A-** |
| 网络层结构 | **B+** |
| 数据层 / Room | **B+** |
| DI / 架构边界 | **B-** |
| UI 可维护性 | **B-** |
| 测试覆盖 | **B+** |
| 死代码控制 | **B+** |
| 综合 | **B+** |

结论：项目已经进入“结构性收口”阶段。下一轮最有价值的工作不是无差别删文件，而是删除最后的 active resolver compatibility caller、迁移 Service Locator、冻结 V1 生产读取边界、统一 pending operation 语义，并在此之后再做 UI 拆分和最终 dead-code sweep。
