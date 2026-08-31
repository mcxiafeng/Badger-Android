# Badger-Android 代码质量 / API 契约审查报告

日期：2026-09-01  
审查基线：`dev` + `refactor/dev-cleanup-2026-08-31`  
工作分支：`refactor/dev-cleanup-2026-08-31`（本轮未创建新分支）  
报告更新目的：同步本轮 correctness 修复、死代码清理、测试覆盖与 CI 状态。

> 本文为连续审查记录。项目已经完成主要 V1 HTTP compatibility、核心 Service Locator、NFC compatibility shim、重复 UI 组件等历史架构清理。本轮继续处理 create-on-push 幂等性、Repository failure-path、Resolver 迁移残留及文档一致性。

## 1. 当前总体结论

项目已经脱离“不可维护屎山”的阶段。网络层、V2 cache、sync、Repository 与 UI 已形成基本边界；当前剩余问题主要集中在同步恢复策略和部分大型 Compose feature 的职责耦合，而不是历史兼容层。

本轮确认完成：

1. 删除 Resolver UI compatibility：`NetworkResolveResult`、`getResultInfo()`、`IdentifyResponse.signature` / `nickname` 等兼容投影及调用方残留。
2. 清除核心 Service Locator：`KoinComponentBy.kt` 已删除，`AppViewModel` 改为 constructor injection。
3. 删除 `NfcKoinInjectCompat.kt`、无消费者的 `NfcSettingsViewModel.kt` 及对应 Koin binding。
4. short.io credential ownership 保持服务端化。
5. Operation History 收口为只读日志，并统一 `Pending` / `FAILED` 状态语义。
6. 删除与 `BadgerEmptyState` 重复的 `EmptyStateView.kt`。
7. 确认 `ContactField` / `CustomField` / `ContactFieldValue` 仍是生产业务 DTO，不删除。
8. 修正 MERGE 404 语义及对应测试。
9. 修复 create-on-push 幂等键生命周期：网络失败后持久化并复用同一 client UUID。
10. 新增 `ContactRepositoryCreateIdempotencyTest.kt`，覆盖创建成功、失败持久化及重试复用。
11. 清除 App 中旧 Resolver compatibility 调用，统一 canonical `ContactNetworkResolver` / `IdentifyResponse`。

## 2. 服务端 API 契约核对

当前客户端主要使用 canonical `/api` surface，不再保留旧 V1 REST facade。

已核对主要域：

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

Resolver 当前链路：

```text
jumpLink / value
      ↓
ContactNetworkResolver.identify()
      ↓
IdentifyResponse
      ↓
Repository / UI
```

当前代码已无 `NetworkResolveResult`、`getResultInfo()` 等旧 UI compatibility projection。

## 3. V1 API 与历史 DTO 边界

产品侧明确 V1 HTTP API 不需要真实用户兼容，因此旧 HTTP facade 删除是正确方向。

历史 DTO 需要与历史 HTTP API 区分。`Models.kt` 中的：

- `ContactField`
- `CustomField`
- `ContactFieldValue`

仍被 `FieldRepositoryImpl` 作为 Repository 业务类型使用，并通过 `ContactMapper` 与 cache entity 转换，因此继续保留。

策略：

```text
历史 HTTP compatibility
  → 删除

历史数据迁移 / importer
  → 保留

仍属于本地业务层的数据 DTO
  → 保留
```

## 4. 本轮完成项

### P0：create-on-push 幂等键生命周期修复

原问题：创建请求超时但服务端已成功创建时，客户端认为失败并在下一次重试重新生成 UUID，存在重复 Person 的窗口。

现在使用：

```text
首次创建
  ↓
生成 client UUID
  ↓
POST /api/user/persons
  ├─ 成功 → 保存服务端 UUID + isLocalOnly=false
  └─ 失败 → 保存原 client UUID + isLocalOnly=true
                    ↓
             后续重试复用 UUID
                    ↓
             服务端幂等返回原记录
```

本地 `ContactCacheEntity.serverId` 在 `isLocalOnly=true` 时承载 pending-create idempotency key。现有 `serverId` 已有唯一索引，因此无需新增 Room schema 字段。

### P1：create-on-push failure-path 测试

新增 `ContactRepositoryCreateIdempotencyTest.kt`，覆盖：

- create 成功后的状态落盘；
- create 网络失败后的 UUID 持久化；
- 后续 update / platform push 对 pending UUID 的复用；
- create 成功后 `isLocalOnly=false`；
- 重试不重新生成第二个幂等键。

### P1：AppViewModel Service Locator 残留修复

此前报告认为 `KoinComponentBy.kt` 已删除，但 `AppViewModel` 仍引用它，这是实际编译级残留。

当前 `AppViewModel` 改为显式构造依赖，Koin binding 同步调整；核心业务路径不再通过 `KoinComponentBy.get()` / `GlobalContext.get()` 获取业务依赖。

### P1：Resolver migration residue 修复

`App.kt` 扫描导入流程此前仍调用已退役的 Resolver 旧接口。本轮已统一使用注入的 `ContactNetworkResolver`，不再依赖旧 `getResultInfo()` / compatibility projection。

### P1：Resolver compatibility API 最终收口

`ContactNetworkResolver.kt` 已移除：

- `NetworkResolveResult` typealias
- `IdentifyResponse.nickname`
- `IdentifyResponse.signature`
- `getResultInfo()` compatibility extension

## 5. 当前 Repository / 同步一致性评估

### 联系人直推模型

```text
UI/Repository 本地修改
      ↓
必要时直推 ServerApi
      ↓
成功 → 本地同步 serverId / 状态
失败 → 本地保留，可继续恢复
```

DELETE：

```text
软删
  ↓
DELETE /api/user/persons/{uuid}
  ├─ 2xx → hard delete
  ├─ 404 → 幂等成功 → hard delete
  └─ 其他失败 → 恢复软删
```

MERGE：

```text
POST /api/user/persons/{target}/merge
  ├─ 成功 → 删除 merged 本地行
  └─ 失败 → 保留本地 merged 行
```

DELETE 与 MERGE 的 404 语义保持明确分离：DELETE 可按幂等删除处理；MERGE 404 不在缺少明确服务端契约时直接清理本地 merged 数据。

### create-on-push 当前语义

`insertContact()` 和 `ensureServerUuid()` 现在保持同一 pending UUID 生命周期。即使第一次 POST 已成功但响应丢失，下一次恢复仍以相同 UUID 重放，从而避免重复联系人。

对于历史数据中 `isLocalOnly=true && serverId=null` 的旧行，第一次恢复会生成 UUID；一旦请求失败，该 UUID 会立即持久化，后续重试保持稳定。

## 6. 同步引擎评估

`SyncRepository` 使用 `GET /api/user/sync?since=` 增量重放：

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

现有防御：

- change 应用失败时不推进游标；
- 下轮从旧 cursor 重放；
- `Mutex` 防并发 pull；
- `AtomicBoolean` 防启动期重复进入；
- `MAX_PULL_ROUNDS` 防异常 `hasMore` 无限循环。

当前待优化点：UPDATE 找不到本地实体会让整批失败并阻塞 cursor。这是偏保守且不丢数据的策略，但恢复能力仍可加强。

后续建议：明确服务端实体 GET / 权限 / 删除语义后，为“缺行 UPDATE”增加实体重拉恢复路径；不要简单 catch 后静默跳过。

## 7. 大型 Compose Feature

ContactDetail / Scanner 已完成第一轮职责拆分：

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

目前不建议机械按文件行数继续拆。下一阶段应按 Header / Fields / Platforms / Actions 等责任边界拆分。

UI 可维护性仍评为 B-，但已不是当前最高优先级 correctness 风险。

## 8. 死代码 / 兼容代码状态

### 已删除

- `ResolverUiCompat.kt`
- `NetworkResolveResult`
- `ContactNetworkResolver.getResultInfo()`
- `IdentifyResponse.signature`
- `IdentifyResponse.nickname`
- `KoinComponentBy.kt`
- `NfcKoinInjectCompat.kt`
- `NfcSettingsViewModel.kt`
- 对应无消费者 Koin binding
- 本地 short.io API Key source-of-truth
- V1 migration test 的旧语义
- `EmptyStateView.kt`
- App 中旧 Resolver compatibility 调用残留

### 明确保留

- Room schema migrations
- QAuxv importer
- sync cursor / history
- `PlatformEntry` shared JSON shape
- SafeLog / HTTP error classification
- `ContactField`
- `CustomField`
- `ContactFieldValue`
- Operation History 历史数据模型
- `LegacyTagFixup` 等历史数据修复逻辑

### 不再列为 dead code

`ContactField` / `CustomField` / `ContactFieldValue` 有生产引用；`OperationTypes` 仍服务于历史日志展示，因此不删除。

## 9. 代码质量评级

| 维度 | 当前评级 | 说明 |
|---|---|---|
| API 契约一致性 | **A** | canonical `/api` surface 基本收口，Resolver compatibility 已清除 |
| 网络层结构 | **A-** | `ApiCore` + 分域 API，边界清晰 |
| 数据层 / Room | **A-** | V2 cache 稳定，create-on-push 幂等键生命周期已修复 |
| DI / 架构边界 | **A-** | 核心 Service Locator 已清除，AppViewModel 也改为 constructor injection |
| UI 可维护性 | **B-** | 大型 feature 仍偏重，后续按责任边界拆分 |
| 测试覆盖 | **B+** | 已补 create-on-push failure-path，仍需深化 sync recovery |
| 死代码控制 | **A** | compatibility 与重复组件持续收口，保留项均有生产用途 |
| 综合 | **A-** | 历史架构债务已基本处理，剩余重点为 sync recovery 与 UI maintainability |

## 10. 推荐后续顺序

### P1：Sync 缺行恢复

```text
UPDATE 找不到本地行
        ↓
按 objectId 尝试重拉实体
        ↓
成功 → 应用 change / 推进 cursor
失败 → 进入明确可恢复错误状态
```

实现前需要确认服务端单实体 GET、权限和删除重建语义。

### P1：Repository failure-path 深化

继续增加：

- update 失败后的本地状态一致性；
- DELETE 多次重试；
- MERGE target / merged 部分缺失；
- create-on-push 响应丢失模拟；
- 同一 pending UUID 多次重试不产生副本；
- sync 重复拉取相同 page 的幂等性。

### P2：obsolete / dead-code sweep

继续搜索：

- `V1`
- `legacy`
- `compat`
- 旧 `/v1/` path 字符串
- 无消费者 helper / ViewModel / UseCase
- 重复 extension / utility

仍遵循“先确认生产消费者，再删除”的原则。

### P2：大型 Compose feature 职责级拆分

优先 ContactDetail 与 Scanner，按责任边界拆，而不是按行数拆。

## 11. CI 状态

工作分支 push / pull request CI 会运行 `Build Debug APK`。

本轮最新可观测状态：

- Java 17 setup：通过
- Android SDK setup：通过
- SDK licenses：通过
- SDK components：通过
- Gradle setup：通过
- Debug APK：当时正在执行 `./gradlew assembleDebug --stacktrace`

因此在未观察到最终 conclusion 前，不宣称 CI 已绿色。

此前中间提交的 CI 被新提交触发的 concurrency 自动取消属于正常行为，不代表构建失败。

## 12. 本轮变更记录

```text
create-on-push
  → 持久化 client UUID / 幂等重放

AppViewModel
  → Service Locator → constructor injection

Resolver
  → compatibility API → canonical IdentifyResponse

App
  → 旧 Resolver 调用 → 注入 Resolver

Tests
  → 增加 create-on-push idempotency / failure-path coverage

Report
  → 同步当前完成项、风险与 CI 状态
```

当前工作分支仍为：

`refactor/dev-cleanup-2026-08-31`

本轮未创建额外分支。
