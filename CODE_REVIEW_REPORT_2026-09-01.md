# Badger-Android 代码质量 / API 契约审查报告

日期：2026-09-01  
审查基线：`dev` + `refactor/dev-cleanup-2026-08-31`  
工作分支：`refactor/dev-cleanup-2026-08-31`（本轮未创建新分支）

> 本文为连续审查记录。本轮继续上一版 P1 correctness 工作后的架构收口，重点转向 UI maintainability：继续统一设计 Token、修复共享 UI 组件的真实交互缺陷，并推进大型 Compose Feature 的职责级拆分。

## 1. 总体结论

项目已经基本脱离历史 V1 compatibility / Service Locator 主导的结构。当前主链路为：

```text
Network API → Repository → V2 cache → ViewModel → Compose
```

此前已将 `CreateContactViewModel`、`UserProfileDetailViewModel`、`AccountSettingsViewModel`、`NotificationViewModel`、`DeviceViewModel`、`SettingsHomeViewModel`、`SyncStatusViewModel`、`SocialViewModel`、`ChangePasswordViewModel` 迁移到显式 constructor injection。

UI 方面，前几轮已经完成 Empty / Loading / Error / ListItem 的设计 Token 收口，并修复 `BadgerErrorStateCompact` 重试不可点击的问题。本轮继续处理 ContactDetail：把字段/列表展示和操作工具栏从页面协调器中移出，降低单页入口的职责密度，同时保持 ViewModel 状态流、Dialog 契约和导航行为不变。

本轮继续处理 Scanner 的控制层 UI：收敛可交互控件语义、复用设计 Token，并消除手动输入入口与确认按钮使用裸 `Box + clickable` 的可访问性/一致性问题；没有引入新的状态源，也没有改变 Scanner 的业务处理契约。

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

- `di/KoinComponentBy.kt`：仍有旧 ViewModel 通过静态 helper 获取依赖；继续从消费者侧逐步迁移，最终目标仍是删除 helper。
- `ui/components/EmptyStateView.kt`：仍有页面实际引用，因此保留为兼容 wrapper；新代码应直接使用 `BadgerEmptyState`。

Resolver 兼容层原则保持不变：authoritative 网络实现只有 canonical `/api/resolve`，compatibility alias / bridge 不恢复第二套 HTTP client。

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

`PendingPersonUpdateScheduler` 已接入 WorkManager：`NetworkType.CONNECTED`、指数退避、unique work `pending-person-updates`、`APPEND_OR_REPLACE`，App 启动和每次 enqueue 都会 kick。Worker replay 持久化 payload，失败写回 backoff 状态并返回 `Result.retry()`。

### 6.3 数据存储

`pending_person_updates` 使用同一个 Room SQLite connection，但不进入 Room Entity graph；包含 serverId、requestId、payload、时间、attempts、nextAttemptAt、lastError，并为 nextAttemptAt 建索引。

## 7. Repository failure-path

DELETE、MERGE、create-on-push 的既有 failure semantics 保持正确；update / updateBio / platform PUT 现在进入 durable outbox，不再只靠日志。

## 8. Dead-code sweep

本轮不继续做“看到文件就删”。实际消费者确认后处理。

保留：Room migrations、QAuxv importer、sync cursor/history、PlatformEntry JSON shape、SafeLog/API error types、ContactField/CustomField/ContactFieldValue、Operation History、LegacyTagFixup，以及仍被生产代码依赖的 `KoinComponentBy` / `EmptyStateView` 兼容层。

此前已完成：

- 9 个 ViewModel 的 Service Locator 依赖迁移到 constructor injection；
- `EmptyStateView` 与 `BadgerEmptyState` 的重复渲染实现合并；
- 删除迁移过程中不再需要的 ViewModel 静态 lookup；
- `SocialViewModel` 清掉不再使用的 `ShortLinkService` / `Job` import 噪音；
- 共享 UI 状态组件中的无效/重复 import 清理。

仍存在的主要 `KoinComponentBy` 消费者集中在 Auth、Card、Person、ContactDetail 等大型 / 历史迁移 ViewModel，需要按依赖分组继续迁移。

## 9. DI / 架构边界（本轮）

### 9.1 已迁移

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

Koin `viewModel { ... }` 在 composition root 负责解析 repository / use case / Context；默认 dispatcher 作为构造参数保留，便于 JVM 单测替换。

### 9.2 仍需迁移

优先级：

1. AuthViewModel
2. CardViewModel
3. PersonViewModel
4. ContactDetailViewModel
5. 其余仍实际调用 `KoinComponentBy.get()` 的小型 ViewModel

完成后再删除 `di/KoinComponentBy.kt`。

## 10. 大型 Compose Feature / UI maintainability

ContactDetail / Scanner 仍存在职责耦合；不以增加文件数量为目标，而以状态、展示和操作职责边界为目标。

当前共享 UI 组件已经继续收口：

```text
Empty / Loading / Error / ListItem
        ↓
BadgerSpacing design tokens
        ↓
统一间距与基础尺寸语义
```

同时，`BadgerErrorStateCompact` 在提供 `onRetry` 时已经改为真正可点击的 `TextButton`；`BadgerDialog` / `BadgerListItem` 等组件也继续清理无效 import 和散落尺寸。

`EmptyStateView` 继续作为旧 API 兼容层存在，新代码应直接使用 `BadgerEmptyState`。

### 10.1 ContactDetail：Fields / Actions 拆分已完成一阶段

本轮完成报告中上一阶段留下的 Fields / Actions 拆分：

- 新增 `ContactDetailFields.kt`：集中负责 `ContactDetailPageContent`、列表状态分支、Header、社交平台区、个人介绍区、标签区等页面展示职责；
- 新增 `ContactDetailActions.kt`：集中负责字段/平台 `FloatingToolbar` 的动作 UI 和可见性规则；
- `ContactDetailComponents.kt` 收缩为共享的 `ThinDivider`，不再同时承担整页展示和工具栏职责；
- `ContactDetailPage.kt` 的现有 state / ViewModel / Dialog orchestration 保持不变，因此没有引入新的路由或状态源；
- 字段列表在抽离后先计算 `additionalSystemFields`，避免在 LazyColumn DSL 内重复执行相同 filter；
- Toolbar 继续复用 `BadgerRadius` / `BadgerSpacing`，内部间距统一为已有设计 Token；
- 新增文件不直接访问 Repository / 网络，符合 UI 层架构红线；
- `ThinDivider` 继续保留原 0.5dp 视觉，不因 Token 化改变线宽。

本轮结构 diff：

```text
ContactDetailActions.kt      +84
ContactDetailFields.kt      +497
ContactDetailComponents.kt  -560 / +0
```

变更集中在 UI 结构层，没有改动 ViewModel API、Dialog 参数契约和导航栈。

需要明确：当前仍有大量 action orchestration 留在 `ContactDetailPage.kt`，因此这不是“ContactDetail 已完全解耦”，而是把 **Fields / Actions 的 UI 责任** 从入口文件中分离。下一步更适合继续处理 action handler 的分组、状态模型收敛和大型 ViewModel 的 constructor injection，而不是继续机械拆文件。

### 10.2 Scanner：控制层 UI 收口

本轮先处理 Scanner 入口中最明确、低风险的 UI maintainability 问题，没有继续机械拆 `ScannerPage.kt`：

- `ScannerComponents.kt` 的手动输入入口从裸 `Box + clickable` 改为统一的 `IconButton`，与返回/闪光灯/相册按钮保持一致的交互组件和语义；
- 多码模式的确认收集按钮补充明确的语义描述，并增加相机图标，避免原来只有空白白色圆形、无可访问性提示的情况；按钮仍保持原有 72dp 视觉尺寸和启用条件；
- Scanner 顶部、底部控制区的基础间距开始复用 `BadgerSpacing`，减少同一文件中的散落硬编码；
- 扫描中间的装饰图形保留为纯展示，不再声明为可交互控件；
- 保持 `ScannerPage` 的回调契约、Camera 生命周期、Dialog 状态与保存逻辑不变，因此本轮属于纯 UI 层收口；
- 同时清理了 ScannerComponents 中明显的格式噪音，并保持图片/OCR 辅助函数行为不变。

这里没有宣称 Scanner 已完成完整职责拆分；`ScannerPage.kt` 仍然是下一阶段重点，后续应继续把 Camera/Preview、拍照结果处理、Dialog 状态以及 Save/Merge action orchestration 分组，但优先避免把共享状态复制到多个 composable。

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
| UI maintainability | B+ | ContactDetail Fields / Actions 与 Scanner Controls 已继续职责化，但 ScannerPage / 大型 VM 仍较重 |
| Dead code 控制 | A- | 清理谨慎，不以“删文件”代替消费者分析 |
| 测试覆盖 | A- | Sync recovery / pagination guard / outbox generation 已覆盖；DI/UI 尚需补专项测试 |
| 综合 | A- | correctness 债务基本解决，剩余集中在架构迁移与 UI maintainability |

## 12. CI 状态

本轮最新代码 commit：

```text
fc07e1f3fee8ae3b10e5c2af0f67c765d959a4f6  refactor(ui): improve scanner controls semantics
```

该 commit 基于既有 `refactor/dev-cleanup-2026-08-31` 工作分支直接前进，本轮没有创建新分支。

GitHub 当前尚未返回该 commit 的 completed workflow run 或 status check；因此本报告**不宣称本轮已构建绿色，也不宣称构建失败**。

同时，本地环境无法直接通过 `github.com` DNS 获取仓库工作树，因此没有伪造本地 Gradle 构建结果。当前验证为 GitHub 文件级检查 + commit 写入后的分支状态检查。

## 13. 本轮变更记录

```text
UI / Structure
  → 延续 ContactDetail Fields / Actions 职责拆分
  → Scanner 控制层继续收口，不扩散状态源

UI / Scanner
  → 手动输入从裸 Box + clickable 改为 IconButton
  → 多码确认按钮增加图标与 accessibility semantics
  → 基础间距开始复用 BadgerSpacing
  → 保持 Camera / Dialog / Save / Merge 行为契约不变

UI / Maintainability
  → 清理 ScannerComponents 格式噪音与重复视觉写法
  → 不以增加文件数量为目标，先处理真实交互缺陷和明确职责边界

Architecture guard
  → 新 UI 代码不直接访问 Repository / 网络
  → 不新增分支，本轮继续使用既有 `refactor/dev-cleanup-2026-08-31`

Next
  → 继续收敛 Scanner Camera / Preview / Dialog / Save / Merge 边界
  → 继续迁移 Auth / Card / Person / ContactDetail 等大型 VM 的 constructor injection
  → 在可用 CI 环境补 UI / DI 专项测试
```

当前工作分支：`refactor/dev-cleanup-2026-08-31`

本轮未创建额外分支。
