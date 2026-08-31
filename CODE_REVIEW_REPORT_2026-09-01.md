# Badger-Android 代码质量 / API 契约审查报告

日期：2026-09-01  
审查基线：`dev` + `refactor/dev-cleanup-2026-08-31`  
工作分支：`refactor/dev-cleanup-2026-08-31`（本轮未创建新分支）

> 本文为连续审查记录。本轮继续上一版 P1 correctness 工作后的架构收口，重点转向 UI maintainability：继续统一设计 Token、修复共享 UI 组件的真实交互缺陷，并记录大型 Compose Feature 的后续职责级拆分计划。

## 1. 总体结论

项目已经基本脱离历史 V1 compatibility / Service Locator 主导的结构。当前主链路为：

```text
Network API → Repository → V2 cache → ViewModel → Compose
```

本轮进一步减少了 ViewModel 对进程级 Koin 容器的直接依赖。`CreateContactViewModel`、`UserProfileDetailViewModel`、`AccountSettingsViewModel`、`NotificationViewModel`、`DeviceViewModel`、`SettingsHomeViewModel`、`SyncStatusViewModel`、`SocialViewModel`、`ChangePasswordViewModel` 已改为显式 constructor injection，Koin 仅负责在 composition root 组装它们。

UI 方面没有继续进行机械式“大文件拆分”，而是先处理共享组件中可以确认的行为问题和设计一致性问题：`BadgerErrorStateCompact` 的“重试”此前只是普通文本、实际上不可点击；同时 Empty / Loading / Error / ListItem 的间距和尺寸继续向 `BadgerSpacing` 收口。

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
- `SocialViewModel` 一并清掉了不再使用的 `ShortLinkService` / `Job` 等 import 噪音；
- 共享 UI 状态组件中的无效/重复 import 继续清理。

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

当前共享 UI 组件已经继续收口：

```text
Empty / Loading / Error / ListItem
        ↓
BadgerSpacing design tokens
        ↓
统一间距与基础尺寸语义
```

同时修复了一个实际 UI 行为缺陷：`BadgerErrorStateCompact` 原本在提供 `onRetry` 回调的情况下，只渲染普通 `Text`，用户无法点击重试。现在改为真正的 `TextButton`，只有 `retryLabel != null && onRetry != null` 时显示可交互操作。

`BadgerDialog` 也清除了不再使用的 `ButtonDefaults` import；`BadgerListItem` 的箭头与图标尺寸使用已有设计 Token，减少散落的硬编码尺寸。

`EmptyStateView` 继续保留旧 API，避免一次迁移打断已有页面；新代码应直接使用 `BadgerEmptyState`。

下一阶段大型 Compose feature 仍按 `Header / Fields / Platforms / Actions / Dialogs` 做职责级拆分，而不是按文件大小切割。优先目标为 ContactDetail，其次 Scanner。

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
| UI maintainability | B | 共享状态组件已进一步统一并修复真实交互缺陷，大型 Compose feature 仍需职责级拆分 |
| Dead code 控制 | A- | 清理谨慎，不以“删文件”代替消费者分析 |
| 测试覆盖 | A- | Sync recovery / pagination guard / outbox generation 已覆盖；DI/UI 尚需补专项测试 |
| 综合 | A- | correctness 债务基本解决，剩余集中在架构迁移与 UI maintainability |

## 12. CI 状态

本轮 UI 收口相关提交：

```text
38679bc2630ce5a2e4e577be5e952a20ebb6ff40  refactor(ui): use design spacing tokens for empty states
dc84952b60b21fd0911404f89c2e5971b000cb32  fix(ui): clean loading state imports after token migration
97717ec3ffc1327cba2667d9e87cf9ea66d58d80  refactor(ui): align list item dimensions with design tokens
a3cdc34a5a9f79135ea4c83470451a69ce5fc104  fix(ui): make compact error retry action clickable
bb0db178b1a6a214fab567e8e12239f9a99e608d  refactor(ui): remove unused dialog import
3346f4a021d757afa78640b02e12efd61e43939f  docs(review): record UI cleanup and retry fix
```

通过 GitHub connector 查询，`3346f4a021d757afa78640b02e12efd61e43939f6` 对应的 workflow runs 当前为空，因此本报告**不宣称当前 tip 已构建绿色，也不宣称已失败**。此前历史 CI 结论保持原记录不变。

## 13. 本轮变更记录

```text
UI / Design System
  → BadgerEmptyState 使用 BadgerSpacing 统一卡片/内容间距与图标尺寸
  → BadgerLoadingState 使用 BadgerSpacing 统一常规/紧凑加载布局
  → BadgerListItem 使用已有设计 Token 统一箭头/图标尺寸
  → BadgerErrorState 使用设计 Token 统一布局

UI / Correctness
  → 修复 BadgerErrorStateCompact 的“重试”不可点击问题
  → retryLabel + onRetry 存在时现在渲染真正的 TextButton

UI / Dead-code cleanup
  → 清理 BadgerErrorState 未使用的 Refresh import
  → 清理 BadgerDialog 未使用的 ButtonDefaults import

架构 / DI
  → 继续保留现有 9 个 constructor-injected ViewModel
  → 暂不删除 KoinComponentBy，等待大型 VM 分组迁移

Review
  → 继续避免机械式大文件拆分
  → 下一阶段优先 ContactDetail / Scanner 的 Header / Fields / Platforms / Actions / Dialogs 职责拆分

CI
  → 当前 tip 尚无 completed workflow conclusion
```

当前工作分支：`refactor/dev-cleanup-2026-08-31`

本轮未创建额外分支。
