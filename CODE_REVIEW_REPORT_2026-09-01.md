# Badger-Android 代码质量 / API 契约审查报告

日期：2026-09-01  
审查基线：`dev` + 现有 `refactor/dev-cleanup-2026-08-31`  
工作分支：`refactor/dev-cleanup-2026-08-31`（本轮未创建新分支）  
本轮记录 HEAD：`7cf111a5f7e775f7f7b6c110258edb978e7f0526`  
相对 `dev`：ahead 120、behind 0

> 本文为连续审查记录。当前阶段已经完成 V1 HTTP compatibility、核心 Service Locator、NFC compatibility shim 等主要历史架构清理；本轮继续处理报告遗留的 dead code、状态语义不一致、重复 UI 组件以及历史 DTO 实际引用确认。

## 1. 当前总体结论

项目已经脱离“不可维护屎山”的阶段。网络层、V2 cache、sync、Repository 与 UI 已形成基本边界；V1 HTTP compatibility 不再作为当前架构的一部分。

截至本轮 HEAD，已完成：

1. 删除 Resolver UI compatibility：`NetworkResolveResult`、`getResultInfo()`、`IdentifyResponse.signature` 兼容投影以及对应 shim。
2. 清除核心 Service Locator：Resolver、ShortLinkService、SetupGuideViewModel、ServerShortLinkViewModel 等核心路径改为 constructor injection；`KoinComponentBy.kt` 已真正删除。
3. 删除 `NfcKoinInjectCompat.kt` 与无消费者 `NfcSettingsViewModel.kt` 及对应 Koin binding。
4. short.io credential ownership 保持服务端化，客户端不再把 server-owned API key 作为本地 source-of-truth。
5. Operation History 改为只读日志，移除已退役队列操作相关副作用。
6. 修正 Operation History `Pending` 状态分类，使 Repository 与 UI 一致包含 `FAILED`。
7. 删除与 `BadgerEmptyState` 重复的 `EmptyStateView.kt`。
8. 确认 `ContactField` / `CustomField` / `ContactFieldValue` 仍属于真实 production business DTO，不属于可以直接删除的死代码。
9. 修正 MERGE 404 测试语义：当前不将 404 视为可安全本地硬删的幂等成功。

## 2. 服务端 API 契约核对

客户端当前主要使用 canonical `/api` surface，不再保留旧 V1 REST facade。

当前已核对的主要域包括：

- Auth：register / login / refresh / logout / me / policy / captcha / verification / forgot-password / change-password
- User：profile / person / collection / tag / device / notification
- Sync：`GET /api/user/sync?since=&limit=`
- Settings：`/api/user/getSettings`、`/api/user/settings`
- Stats：`/api/user/stats`
- Upload：`/api/user/upload`
- Resolver：`/api/resolve/` 单条与批量
- AI：`/api/proxy/ai/tasks/tag_generate`、`contact_ocr`
- short.io：links / domains proxy
- 自建短链：`/api/shortlinks/`

Resolver 当前链路已收敛为：

```text
jumpLink / value
      ↓
ContactNetworkResolver.identify()
      ↓
IdentifyResponse
      ↓
Repository / UI
```

不存在额外的 V1 compatibility projection。

## 3. V1 API 与历史 DTO 边界

产品侧已经明确 V1 HTTP API 不需要真实用户兼容，因此不继续保留旧 HTTP facade。

需要特别区分：历史 DTO 并不等于历史 HTTP API。当前 `Models.kt` 中的：

- `ContactField`
- `CustomField`
- `ContactFieldValue`

仍被 `FieldRepositoryImpl` 直接作为 Repository 对外业务类型使用，并通过 `ContactMapper` 与 cache entity 转换。因此这些类型本轮确认应保留，而不是仅因名称带有历史语义就删除。

当前策略：

```text
历史 HTTP compatibility
  → 删除

历史数据迁移 / importer
  → 保留

仍属于本地业务层的数据 DTO
  → 保留并明确语义
```

## 4. 本轮新增完成项

### P0：Service Locator 残留彻底清理

此前报告已经标记 `KoinComponentBy.kt` 应删除，但当时工作分支实际仍存在。本轮确认并删除：

`app/src/main/kotlin/top/mcxiafeng/badger/di/KoinComponentBy.kt`

当前核心路径使用构造注入；不再以 `GlobalContext.get()` 作为业务依赖获取方案。

### P1：Operation History 状态语义统一

此前 Repository 的 pending filter 已包含：

- `CONFLICT`
- `FAILED`
- `FAILED_PERMANENT`

但 `OperationHistoryOpFormatter.isPendingStatus()` 漏掉了 `FAILED`，造成数据层和 UI 分类不一致。

现在两侧统一为同一语义，避免“设置页明明有失败记录，状态分类却不认为它属于待处理”的问题。

### P1：Operation History 退役队列语义收口

历史页当前为只读日志，不再提供撤销、重发、冲突处理等已退役队列副作用。

`OperationHistoryViewModel` 通过构造函数注入 `OperationHistoryRepository`；Repository 只负责历史记录与本地联系人名称 join。

### P1：MERGE 404 语义修正

DELETE 与 MERGE 的 404 语义不能混用：

- DELETE：服务端已不存在通常可以安全视为幂等成功。
- MERGE：404 可能意味着 target 或 merged person 不存在，不能在没有明确服务端幂等契约的情况下直接删除本地 merged 行。

因此生产代码保持 `SentFailed`，测试同步到该行为。

### P2：重复 Empty State 组件清理

`EmptyStateView.kt` 与 `BadgerEmptyState.kt` 存在明显重复。后者已经覆盖完整版、Simple、Compact 三种使用方式，因此删除前者，避免维护两套视觉与行为实现。

### P1：历史 DTO 实际引用扫描

已完成第一轮 production consumer 确认：`FieldRepositoryImpl` 直接依赖上述三个 DTO，不能删除。

这项从“待确认”变为：**已完成、保留为业务 DTO**。

## 5. 当前 Repository / 同步一致性评估

### 联系人直推模型

`ContactRepositoryImpl` 当前写入语义为：

```text
UI/Repository 本地修改
      ↓
必要时直推 ServerApi
      ↓
成功 → 本地同步 serverId / 状态
失败 → 本地保留，可继续恢复
```

DELETE 当前采用：

```text
软删
  ↓
DELETE /api/user/persons/{uuid}
  ├─ 2xx → hard delete
  ├─ 404 → 幂等成功 → hard delete
  └─ 其他失败 → 恢复软删
```

MERGE 当前采用：

```text
POST /api/user/persons/{target}/merge
  ├─ 成功 → 删除 merged 本地行
  └─ 失败 → 保留本地 merged 行
```

这比旧的“所有修改都进入 PendingOperation/Worker”更加直接，但同时也意味着客户端需要持续保证本地失败恢复和下一次编辑/同步的可达性。

### 仍然存在的真实风险：create-on-push 幂等键生命周期

`insertContact()` 创建联系人时会生成 client UUID 并提交服务端；若请求异常，客户端会落 `isLocalOnly=true`。

当前风险在于：client UUID 没有随本地草稿持久化。极端情况下可能出现：

```text
客户端 POST 已到达服务端
        ↓
服务端实际创建成功
        ↓
客户端因为网络超时认为失败
        ↓
只保留 isLocalOnly 本地行
        ↓
以后再次 create-on-push 使用新的 UUID
```

从理论上存在重复联系人的窗口。

这项属于后续真正值得投入的 correctness fix，建议后续将“创建幂等键”与本地 pending-create 状态一起持久化，而不是简单重新生成 UUID。

## 6. 同步引擎评估

`SyncRepository` 已采用 `GET /api/user/sync?since=` 的增量重放方式：

```text
cursor
  ↓
syncSince(cursor)
  ↓
applyChanges(changes)
  ↓
整批成功
  ↓
推进 cursor
```

关键防御已经存在：

- 任一 change 应用失败时不推进游标；
- 下一轮从旧 cursor 重放；
- `Mutex` 防止并发 pull；
- `AtomicBoolean` 防止启动期重复进入；
- `MAX_PULL_ROUNDS` 防止异常 hasMore 导致无限循环。

需要注意：增量 UPDATE 对本地不存在实体目前倾向于抛错并阻止游标推进。这是偏保守策略，优点是不静默丢数据，缺点是单条异常 change 可能阻塞后续同步。后续可以考虑“缺行时触发实体重拉”的恢复策略，但不应在没有服务端语义保证的情况下静默跳过。

## 7. 大型 Compose Feature

当前 ContactDetail / Scanner 已经做过第一轮职责拆分，例如：

```text
ContactDetail
  ├─ Components
  ├─ Dialogs
  ├─ Utils
  └─ ViewModel

Scanner
  ├─ Camera
  ├─ Components / Ui
  ├─ Dialogs
  ├─ MergeLogic
  ├─ Saver
  └─ ViewModel
```

因此目前不建议为了“文件更小”继续机械拆分。后续真正需要拆的是**责任边界**，例如 Header / Fields / Platforms / Actions，而不是简单按 300 行、500 行切文件。

当前 UI 可维护性仍评为 B-，主要原因是部分页面仍然较大、Composable 之间耦合度较高，但这已经不是本阶段最紧急的问题。

## 8. 死代码 / 兼容代码状态

### 已删除

- `ResolverUiCompat.kt`
- `NetworkResolveResult`
- `ContactNetworkResolver.getResultInfo()`
- `IdentifyResponse.signature` 兼容投影
- `KoinComponentBy.kt`
- `NfcKoinInjectCompat.kt`
- `NfcSettingsViewModel.kt`
- 对应无消费者 Koin binding
- 本地 short.io API Key source-of-truth
- V1 migration test 的旧语义
- `EmptyStateView.kt`

### 明确保留

- Room schema migrations
- QAuxv importer
- sync cursor / history
- `PlatformEntry` shared JSON shape
- SafeLog / HTTP error classification
- `ContactField`
- `CustomField`
- `ContactFieldValue`
- Operation History 的历史数据模型（用于只读日志展示）

### 当前不再把这些列为 dead code

`ContactField` / `CustomField` / `ContactFieldValue` 已确认存在生产引用；`OperationTypes` 虽然描述的是历史操作类型，但仍用于历史日志展示，因此也不应误删。

## 9. 代码质量评级

| 维度 | 当前评级 | 说明 |
|---|---|---|
| API 契约一致性 | **A** | canonical `/api` surface 已基本收口 |
| 网络层结构 | **A-** | `ApiCore` + 分域 API，边界清晰 |
| 数据层 / Room | **B+** | V2 cache 已稳定，但 create-on-push 仍有幂等键生命周期问题 |
| DI / 架构边界 | **A-** | Service Locator 核心残留已清除 |
| UI 可维护性 | **B-** | 大型 feature 仍偏重，但已完成第一轮职责拆分 |
| 测试覆盖 | **B+** | 网络、Repository、Sync、UI VM 均有较多回归测试 |
| 死代码控制 | **A** | 本轮进一步清除兼容层与重复组件 |
| 综合 | **A-** | 已进入收尾优化阶段，主要剩 correctness / maintainability 工作 |

## 10. 推荐后续顺序

### P1：修复 create-on-push 幂等键生命周期

建议将 pending-create 的 client UUID 持久化，使网络超时后的重试仍然使用同一个 UUID，彻底消除“服务端已创建、客户端误判失败后再次创建”的重复窗口。

### P1：Repository failure-path 深化测试

重点增加：

- create-on-push 网络超时
- update 前后本地状态一致性
- DELETE 失败恢复
- MERGE 目标/merged 部分缺失
- sync 缺行恢复
- 多次重复拉取的幂等性

### P2：继续做 obsolete / dead-code sweep

下一轮重点搜索：

- `V1`
- `legacy`
- `compat`
- 旧 `/v1/` path 字符串
- 无消费者 helper / ViewModel / UseCase
- 重复 extension / utility

但只有确认无生产消费者后才删除，不再做基于名称的机械清理。

### P2：大型 Compose feature 的职责级拆分

优先 ContactDetail 与 Scanner，按照 UI responsibility 拆，而不是按照行数拆。

## 11. 当前 CI 状态

工作分支 push CI 会自动运行 `Build Debug APK`。

在本轮代码提交完成后，最新代码 HEAD 为：

`7cf111a5f7e775f7f7b6c110258edb978e7f0526`

该 HEAD 已触发 GitHub Actions；在本报告更新时，Actions 尚未提供最终 build conclusion，因此**不能声称最新 HEAD 已构建通过**。后续以对应 workflow 的最终结果为准。

## 12. 最终结论

当前项目已经完成从“V1 兼容迁移 + 老架构清理”向“稳定性与维护性优化”的阶段切换。

已经不建议继续做大面积架构翻新。当前最有价值的工作顺序是：

1. 修复 create-on-push 的 client UUID 持久化与重试幂等性；
2. 深化 Repository / Sync failure-path 测试；
3. 做一次严格的 obsolete / dead-code 全仓扫描；
4. 最后再做 ContactDetail / Scanner 的职责级 UI 拆分。

后续修改应持续同步更新本报告，保证文档状态与实际代码 HEAD 一致。
