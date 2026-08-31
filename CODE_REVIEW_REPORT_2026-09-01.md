# Badger-Android 代码质量 / API 契约审查报告

日期：2026-09-01  
审查基线：`dev` + `refactor/dev-cleanup-2026-08-31`  
工作分支：`refactor/dev-cleanup-2026-08-31`（本轮未创建新分支）

> 本文为连续审查记录。本轮继续上一版 P1 correctness 工作后的架构收口，重点处理 UI / DI：将多个仍依赖 `KoinComponentBy` 的 ViewModel 迁移到 constructor injection，并把重复的 EmptyState UI 收敛到单一实现，同时保持现有兼容调用点可用。

## 1. 总体结论

项目已经基本脱离历史 V1 compatibility / Service Locator 主导的结构。当前主链路为：

```text
Network API → Repository → V2 cache → ViewModel → Compose
```

本轮进一步减少了 ViewModel 对进程级 Koin 容器的直接依赖。`CreateContactViewModel`、`UserProfileDetailViewModel`、`AccountSettingsViewModel`、`NotificationViewModel`、`DeviceViewModel`、`SettingsHomeViewModel`、`SyncStatusViewModel`、`SocialViewModel`、`ChangePasswordViewModel` 已改为显式 constructor injection，Koin 仅负责在 composition root 组装它们。

UI 方面没有继续进行机械式“大文件拆分”，而是先消除一个确认存在的重复实现：`EmptyStateView` 现在只是 `BadgerEmptyState` 的兼容 wrapper，实际渲染只有一个 source of truth。

当前最大剩余问题仍然是大型 Compose feature 的职责耦合，以及部分核心大型 ViewModel 仍有 `KoinComponentBy` 过渡依赖。

## 2. 历史清理状态与本轮纠偏

此前已经完成的清理继续保持：

- canonical `/api` surface 收口；
- short.io API key source-of-truth 收口到服务端；
- Operation History / pending / FAILED 语义分离；
- MERGE 失败不误当 DELETE 404；
- create-on-push 首次失败持久化 client UUID，后续复用同一 UUID；
- NFC 无消费者 ViewModel / compat 文件清理；
- V1 DTO / duplicate UI 的大部分删除。

上一轮发现两项“删早了”的生产依赖并恢复：

- `di/KoinComponentBy.kt`：仍有旧 ViewModel 通过静态 helper 获取依赖；本轮不是继续删除，而是从消费者侧逐步迁移，最终目标仍是删除 helper。
- `ui/components/EmptyStateView.kt`：仍有页面实际引用；本轮保留文件，但去掉重复渲染逻辑，改为兼容 wrapper。

Resolver 兼容层原则保持不变：

- authoritative 网络实现只有 canonical `/api/resolve`；
- compatibility alias / bridge 只负责旧调用面，不恢复第二套 HTTP client。

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

保留：Room migrations、QAuxv importer、sync cursor/history、PlatformEntry JSON shape、SafeLog/API error types、ContactField/CustomField/ContactFieldValue、Operation History、LegacyTagFixup，以及仍被生产代码依赖的 `KoinComponentBy` / `EmptyStateView` 兼容层。

本轮已收口：

- 9 个 ViewModel 的 Service Locator 依赖迁移到 constructor injection；
- `EmptyStateView` 与 `BadgerEmptyState` 的重复渲染实现合并；
- 删除了这些迁移过程中不再需要的 ViewModel 静态 lookup；
- `SocialViewModel` 一并清掉了不再使用的 `ShortLinkService` / `Job` 等 import 噪音。

仍存在的主要 `KoinComponentBy` 消费者集中在大型 / 历史迁移 ViewModel，尤其是 Auth、Card、Person、ContactDetail；它们因为文件体量和依赖数量较大，需要下一轮按依赖分组迁移，避免用一次性重写引入行为回归。

## 9. DI / 架构边界（本轮）

### 9.1 已迁移

以下 ViewModel 已明确接收业务依赖，VM 本身不再直接读取 `GlobalContext`：

```text
CreateContactViewModel
UserProfileDetailViewModel
AccountSettingsViewModel
NotificationViewModel
DeviceViewModel
SettingsHomeViewModel
SyncStatusViewModel
SocialViewModel
ChangePasswordViewModel
```

Koin `viewModel { ... }` 现在负责在 composition root 解析 repository / use case / Context；默认 dispatcher 仍作为构造参数保留，方便 JVM 单测替换。

### 9.2 仍需迁移

剩余迁移优先级：

1. AuthViewModel
2. CardViewModel
3. PersonViewModel
4. ContactDetailViewModel
5. 其余仍实际调用 `KoinComponentBy.get()` 的小型 ViewModel

完成后再删除 `di/KoinComponentBy.kt`，而不是提前删除兼容层。

## 10. 大型 Compose Feature / UI maintainability

ContactDetail / Scanner 仍存在职责耦合；本轮没有为了“拆文件数量”而机械切分。

本轮先完成一个低风险、高确定性的 UI 收口：

```text
EmptyStateView
      ↓ compatibility wrapper
BadgerEmptyState
      ↓
唯一实际渲染实现
```

`EmptyStateView` 继续保留旧 API，避免一次迁移打断已有页面；新代码应直接使用 `BadgerEmptyState`。

下一轮大型 Compose feature 仍按 `Header / Fields / Platforms / Actions / Dialogs` 做职责级拆分，而不是按文件大小切割。

## 11. 代码质量评级

| 维度 | 当前评级 | 结论 |
|---|---:|---|
| API 契约一致性 | A | canonical `/api` 基本收口 |
| 网络层 | A- | 分域 API 清晰；refresh / resolver / sync / outbox 边界明确 |
| Room / 数据层 | A- | V2 cache 稳定；outbox 与 projection 分离 |
| Repository | A- | DELETE / MERGE / CREATE / UPDATE failure-path 均有策略 |
| DI / 架构边界 | A- | 新迁移的一批 VM 已无 Service Locator，但大型 VM 仍有遗留消费者 |
| Sync correctness | A- | 缺行回源、cursor guard、未知变更 fail-safe 已补齐 |
| Outbound recovery | A- | durable PUT outbox + WorkManager retry 已落地 |
| UI maintainability | B- | 重复空状态已收口，大型 Compose feature 仍需职责级拆分 |
| Dead code 控制 | A- | 清理谨慎，不以“删文件”代替消费者分析 |
| 测试覆盖 | A- | Sync recovery / pagination guard / outbox generation 已覆盖；DI/UI 尚需补专项测试 |
| 综合 | A- | correctness 债务基本解决，剩余集中在架构迁移与 UI maintainability |

## 12. CI 状态

当前工作分支最新提交为本轮 DI 收口提交 `f9f3eec827abc072b3280fb72a32b68ffe1ed34d`。

工作流 `.github/workflows/ci.yml` 明确配置了 `refactor/dev-cleanup-2026-08-31` 的 push 构建；当前分支最新 commit 的 GitHub commit status 仍为 `pending`，尚无 completed check。可见的 PR-triggered `Build Debug APK` run `#322`（run id `33429695364`）仍处于 `pending`，但其 `head_sha` 是此前的 `305fc64c26aea34f27a96b75716892e3d5516951`，不是当前分支 tip，因此本报告**不宣称当前 tip 已构建绿色，也不宣称已失败**。

## 13. 本轮变更记录

```text
DI / ViewModel
  → CreateContactViewModel constructor injection
  → UserProfileDetailViewModel constructor injection
  → AccountSettingsViewModel constructor injection
  → NotificationViewModel constructor injection
  → DeviceViewModel constructor injection
  → SettingsHomeViewModel constructor injection
  → SyncStatusViewModel constructor injection
  → SocialViewModel constructor injection
  → ChangePasswordViewModel constructor injection

KoinModules
  → 对上述 VM 显式组装 repository / use case / Context
  → 保留 dispatcher 默认值，便于测试

UI
  → EmptyStateView 改为 BadgerEmptyState compatibility wrapper
  → 消除两套相同 UI rendering implementation

Review
  → 对 CreateContactViewModel 做 PR 差异复核，确认原有 create-on-resolve 业务行为未因 DI 迁移丢失
  → 保留 KoinComponentBy，等待剩余大型 VM 迁移完再删除

CI
  → 已核对工作流分支过滤与当前 tip status
  → 当前 tip 尚无 completed build conclusion
```

当前工作分支：`refactor/dev-cleanup-2026-08-31`

本轮未创建额外分支。
