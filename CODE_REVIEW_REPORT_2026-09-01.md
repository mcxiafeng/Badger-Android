# Badger-Android 代码质量 / API 契约审查报告

日期：2026-09-01  
审查基线：`dev` + `refactor/dev-cleanup-2026-08-31`  
工作分支：`refactor/dev-cleanup-2026-08-31`（本轮未创建新分支）

> 本文为连续审查记录。本轮继续上一版 P1 correctness 工作，完成 Sync recovery 后，把 outbound PUT failure recovery 落成独立持久化 outbox，并完成 WorkManager/DI 接线与专项单测；同时修复并行清理过程中遗漏的编译兼容点，并核对了分支当前实际状态。

## 1. 总体结论

项目已经基本脱离历史 V1 compatibility / Service Locator 主导的结构。当前主链路为：

```text
Network API → Repository → V2 cache → ViewModel → Compose
```

本轮重点从“同步失败可恢复”继续推进到“本地修改成功但 PUT 失败也不能丢”。同时对上一轮过度清理导致的编译断点进行了收口：恢复仍被生产代码依赖的 Koin 静态 helper、EmptyState 组件，并把 Resolver 的旧调用点改成明确的 compatibility bridge，而不是恢复旧网络实现。

当前最大剩余问题已经转为大型 Compose feature 的职责耦合，以及 compatibility bridge / Service Locator 过渡层的后续迁移清理。

## 2. 历史清理状态与本轮纠偏

此前已经完成的清理继续保持：

- canonical `/api` surface 收口；
- short.io API key source-of-truth 收口到服务端；
- Operation History / pending / FAILED 语义分离；
- MERGE 失败不误当 DELETE 404；
- create-on-push 首次失败持久化 client UUID，后续复用同一 UUID；
- NFC 无消费者 ViewModel / compat 文件清理；
- V1 DTO / duplicate UI 的大部分删除。

本轮发现两项“删早了”的生产依赖并恢复：

- `di/KoinComponentBy.kt`：仍有多个旧 ViewModel 通过静态 helper 获取 repository；直接删除会产生编译错误。本轮恢复的是无业务逻辑的静态 Koin lookup helper，后续仍应继续做 constructor injection。
- `ui/components/EmptyStateView.kt`：仍有页面实际引用，恢复共享组件；不是重复死代码。

Resolver 没有恢复旧 HTTP client：

- `NetworkResolveResult` 变为 `typealias IdentifyResponse`；
- `IdentifyResponse` 提供 `nickname` / `type` 只读兼容属性；
- `ContactNetworkResolver` 提供 compatibility bridge，内部仍统一走 canonical `/api/resolve`；
- 旧 `getResultInfo()` 不再拥有独立网络实现。

因此当前原则是：**兼容调用可以短期存在，但网络实现只有一个 authoritative path。**

## 3. API 契约核对

客户端当前主要使用 canonical `/api` surface：Auth、User、Sync、Settings、Stats、Upload、Resolver、AI、short.io/server shortlinks。

Person API：

```text
GET /api/user/persons/{uuid}
PUT /api/user/persons/{uuid}
```

因此 Sync UPDATE 缺本地实体可以回源，而 outbound PUT failure 可以持久化等待重试。

## 4. P1：Sync recovery

### 4.1 UPDATE 缺行回源

```text
UPDATE Person → 本地缺行 → GET /api/user/persons/{uuid}
→ upsert 完整 Person/profile/platform → 应用当前 UPDATE → 推进 cursor
```

GET 回源失败不吞异常，cursor 保持不变。

### 4.2 未知 change fail-safe

未知 `type`、`objectName`、Person/Collection/Tag field，以及 parser 丢行均不会被静默消费；当前没有本地 projection 的 `Device` / `UserSettings` 是明确允许跳过的对象。

### 4.3 游标与分页安全

已增加 version 回退、无进展分页、空 changes + `hasMore`、伪造 version 跳跃及 `MAX_PULL_ROUNDS` 防护。

### 4.4 Sync API 输入校验

拒绝负 `since`、非法 `limit`、非对象 data、缺失 changes、parser 丢行、旧 version、回退 version 与无进展分页。

## 5. Sync 数据一致性

当前采用“先应用、后推进 cursor”的 replay-safe 设计，而不是数据库事务级整批 rollback。批次中途失败时 cursor 不推进，下次从同一 cursor 重放；未来若出现不可幂等副作用，应进一步收敛到 Room transaction。

## 6. P1：Outbound PUT failure recovery 已完成

此前存在明确缺口：

```text
本地修改成功 → PUT 失败 → 本地仍正确 → 没有 pending update
```

本轮增加独立的 `pending_person_updates` durable outbox，**不复用 `isLocalOnly`**。

### 6.1 Outbox

每次 PUT 先写 outbox，再执行网络请求。成功按 `(serverId, requestId)` 删除；失败记录 attempts / nextAttemptAt / error。新编辑会替换同一 `serverId` 的旧 pending payload，而旧请求成功返回时不会误删新 requestId。

### 6.2 Retry scheduler / Worker

`PendingPersonUpdateScheduler` 已接入 WorkManager：

- `NetworkType.CONNECTED` 约束；
- 指数退避，初始 10 秒；
- unique work 名称 `pending-person-updates`；
- 使用 `APPEND_OR_REPLACE`；
- App 启动、每次 enqueue 都会 kick；
- `PendingPersonUpdateWorker` 直接 replay 持久化 payload，成功按 requestId 收尾，失败写回 backoff 状态并返回 `Result.retry()`。

这样即使进程被杀，outbox 行仍存在，并由 WorkManager 在后续有网条件下恢复，而不是依赖进程生命周期。

### 6.3 数据存储

`pending_person_updates` 使用同一个 Room SQLite connection，但不进入 Room Entity graph；它是 integration outbox。包含 serverId、requestId、payload、时间、attempts、nextAttemptAt、lastError 等字段，并为 nextAttemptAt 建索引。

## 7. Repository failure-path

DELETE、MERGE、create-on-push 的既有 failure semantics 保持正确；update / updateBio / platform PUT 现在进入 durable outbox，不再只靠日志。

## 8. Dead-code sweep

本轮不继续做“看到文件就删”。实际消费者确认后处理。

保留：Room migrations、QAuxv importer、sync cursor/history、PlatformEntry JSON shape、SafeLog/API error types、ContactField/CustomField/ContactFieldValue、Operation History、LegacyTagFixup，以及仍被生产代码依赖的 EmptyStateView / KoinComponentBy 过渡层。

已收口：无消费者 NFC compatibility、重复 short-link helper、旧 Resolver 独立网络实现、已迁移 V1 API 调用路径。

下一阶段优先把剩余 `KoinComponentBy.get()` 迁移成 constructor injection，然后删除 helper。

## 9. 大型 Compose Feature

ContactDetail / Scanner 已完成第一轮拆分，但仍有职责耦合。下一轮按 `Header / Fields / Platforms / Actions / Dialogs` 做职责级拆分，而不是机械按文件大小切割。

这是 maintainability P2，不再阻塞 data correctness。

## 10. 代码质量评级

| 维度 | 当前评级 | 结论 |
|---|---:|---|
| API 契约一致性 | A | canonical `/api` 基本收口 |
| 网络层 | A- | 分域 API 清晰；refresh / resolver / sync / outbox 边界明确 |
| Room / 数据层 | A- | V2 cache 稳定；outbox 与 projection 分离 |
| Repository | A- | DELETE / MERGE / CREATE / UPDATE failure-path 均有策略 |
| DI / 架构边界 | B+ | 仍有 KoinComponentBy 过渡消费者 |
| Sync correctness | A- | 缺行回源、cursor guard、未知变更 fail-safe 已补齐 |
| Outbound recovery | A- | durable PUT outbox + WorkManager retry 已落地 |
| UI maintainability | B- | 大型 Compose feature 仍需职责级拆分 |
| Dead code 控制 | A- | 清理谨慎，本轮纠正误删并保留必要兼容层 |
| 测试覆盖 | A- | Sync recovery / pagination guard / outbox generation 已覆盖 |
| 综合 | A- | correctness 债务基本解决，剩余集中在架构迁移与 UI maintainability |

## 11. CI 状态

当前最新工作分支提交：`555cda857a2462020d097566363f8c332d498675`。

此前对应的 GitHub Actions `Build Debug APK` run `#292`（run id `33428883247`）在提交过程中曾处于 `pending`，但当前工具查询未返回该最新提交对应的 PR-triggered workflow run，因此本报告**不宣称构建已绿色或已失败**。最终 Android Gradle 编译结果以 GitHub Actions 实际 conclusion 为准。

## 12. 本轮变更记录

```text
SyncRepository / SyncApi
  → UPDATE 缺行回源
  → unknown change fail-safe
  → version / pagination validation
  → MAX_PULL_ROUNDS guard

PendingPersonUpdateStore
  → durable PUT outbox
  → request generation 防止旧请求误删新 payload
  → exponential backoff

PendingPersonUpdateScheduler / PendingPersonUpdateWorker
  → WorkManager network constraint
  → unique work + append-or-replace
  → crash/process death 后仍可恢复

ServerApi / NetworkModule / KoinModules
  → canonical Person PUT 接入 durable outbox
  → DI 参数链统一

ContactNetworkResolver
  → compatibility aliases / bridge
  → 不恢复旧网络实现

DI / UI
  → 恢复仍被生产代码使用的 KoinComponentBy / EmptyStateView
  → 删除确认无消费者的 helper

Tests
  → PendingPersonUpdateStore generation / stale success / backoff 覆盖
  → Sync recovery / cursor regression / max rounds 覆盖

CODE_REVIEW_REPORT_2026-09-01.md
  → 更新 P1 完成状态、WorkManager 接线、专项测试、当前分支提交与 CI 状态
```

当前工作分支：`refactor/dev-cleanup-2026-08-31`

本轮未创建额外分支。