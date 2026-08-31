# Badger-Android 代码质量 / API 契约审查报告

日期：2026-09-01  
审查基线：`dev` + `refactor/dev-cleanup-2026-08-31`  
工作分支：`refactor/dev-cleanup-2026-08-31`（本轮未创建新分支）  

> 本文为连续审查记录。本轮继续完成上一版报告中 P1 Sync recovery / failure-path 的 correctness 工作，并同步更新测试、CI 与剩余技术债务。

## 1. 总体结论

项目已经脱离历史 V1 compatibility / Service Locator 主导的“屎山”状态。当前主要业务边界是 `Network Api → Repository → V2 cache → ViewModel → Compose`，剩余问题集中在直推写操作的离线失败恢复能力，以及少量大型 Compose feature 的职责耦合。

本轮完成后，Sync 层已经从“失败时停住 cursor”进一步提升为“失败不丢、游标不回退、不接受无进展分页、缺行自动回源、未知变更不静默吞掉”。

## 2. 已完成的历史清理

以下项目已在此前工作中完成，并在本轮保持：

- Resolver UI compatibility：删除 `NetworkResolveResult`、`getResultInfo()`、旧 `IdentifyResponse.signature/nickname` 以及调用残留。
- 核心 Service Locator：删除 `KoinComponentBy.kt`，`AppViewModel` 使用 constructor injection。
- NFC compatibility：删除 `NfcKoinInjectCompat.kt`、无消费者 `NfcSettingsViewModel.kt` 及对应 binding。
- 删除重复 `EmptyStateView.kt`。
- short.io API key source-of-truth 收口到服务端。
- Operation History 收口为只读历史记录；Pending / FAILED 语义统一。
- MERGE 404 不按 DELETE 的幂等成功处理。
- `ContactField` / `CustomField` / `ContactFieldValue` 经生产代码确认仍是业务 DTO，因此保留。
- create-on-push 首次失败后持久化 client UUID，后续重试复用同一 UUID，避免“服务端已创建但响应丢失”造成重复 Person。

## 3. API 契约核对

客户端当前主要使用 canonical `/api` surface：

- Auth：register / login / refresh / logout / me / policy / captcha / verification / password
- User：profile / person / collection / tag / device / notification
- Sync：`GET /api/user/sync?since=&limit=`
- Settings / Stats / Upload
- Resolver 单条与批量
- AI proxy
- short.io proxy / server shortlinks

Person API 已确认存在单实体：

```text
GET /api/user/persons/{uuid}
```

因此 Sync `UPDATE` 缺本地实体时可以使用服务端回源，而不需要静默跳过该 change。

## 4. 本轮 P1：Sync recovery 完成

### 4.1 UPDATE 缺行回源

旧行为：

```text
UPDATE Person
  ↓
本地没有 serverId
  ↓
整批失败
  ↓
cursor 停住
```

现在：

```text
UPDATE Person
  ↓
本地缺行
  ↓
GET /api/user/persons/{uuid}
  ↓
upsert 完整 Person / profile / platform rows
  ↓
继续应用当前 UPDATE field
  ↓
整批成功后推进 cursor
```

如果 GET 本身失败，不吞异常，仍保持当前 cursor，让下次同步继续重试。

### 4.2 禁止未知 change 静默消费

此前未知 `type`、未知 `objectName` 或未知 UPDATE `fieldName` 最终可能被当作“成功”，从而推进 cursor。

现在：

- 未知 `type` → 批次失败；
- 未知 `objectName` → 批次失败；
- 未知 Person / Collection / Tag field → 批次失败；
- `Device` / `UserSettings` 作为当前没有本地 projection 的明确对象允许记录后跳过。

目的：未来服务端增加新变更类型时，客户端宁可卡住并可恢复，也不能把数据永久吃掉。

### 4.3 游标单调性与分页安全

新增防护：

- `page.version < cursor` → 失败；
- 有 changes 但 `page.version == cursor` → 失败；
- `hasMore=true` 但分页没有实际前进 → 失败；
- 空 changes + `hasMore=true` → 失败；
- 空 changes 却返回新的 version → 拒绝跳跃；
- 达到 `MAX_PULL_ROUNDS` 且仍 `hasMore=true` → `Failed`，不再错误返回 `Done`。

### 4.4 Sync API 输入校验

`SyncApi.syncSince()` 现在拒绝：

- 负 `since`；
- `limit <= 0` 或超过单页最大值；
- 非对象 `data`；
- 缺失 `changes`；
- 原始 `changes` 数量与解析后数量不一致（防止 parser `mapNotNull` 静默丢行）；
- change version 不在 `since` 之后；
- 服务端 version 回退或无进展。

## 5. Sync 数据一致性说明

当前批次采用“先应用、后推进 cursor”的 replay-safe 设计，并不是数据库事务级整批 rollback。

因此一个批次中如果第 N 条失败，前 N-1 条可能已经落入 Room，但 cursor 不推进。下一次会从同一 cursor 重放；现有 ADD / UPDATE / REMOVE 均按 serverId / 本地 ID 设计为可重复应用。

这比“部分成功后直接推进 cursor”安全，但如果未来某类 change 出现不可幂等副作用，应该进一步把 `applyChanges()` 收敛到 Room transaction，而不是继续扩大非事务语义。

## 6. Repository failure-path 复核

### 已确认正确

DELETE：

```text
软删
 ↓
DELETE
 ├─ 2xx → hard delete
 ├─ 404 → 幂等成功 → hard delete
 └─ 其他失败 → 恢复软删
```

MERGE：

```text
merge API
 ├─ 成功 → 清理 merged 本地行
 └─ 失败 → 保留本地数据
```

create-on-push：

```text
失败时 serverId 保存 client UUID
 ↓
后续重试复用 UUID
 ↓
不会因为每次失败重新生成 UUID 而产生 clone
```

### 仍存在的明确技术债务

`updateContact()` / `updateContactBio()` / `pushPlatformUpdate()` 在服务端 PUT 失败时会保留本地最新状态并记录日志，但当前没有独立的持久化“待重试 PUT”队列。

因此：

```text
本地修改成功
 ↓
PUT 失败
 ↓
本地仍正确
 ↓
没有 outgoing pending update
```

该问题不能通过简单复用 `isLocalOnly` 解决：该字段已经被定义为“pending create UUID”，混用会让后续恢复误走 POST create-on-push，反而可能制造重复 Person。

因此本轮不做危险的字段复用式修补；正确方向是后续增加明确的 `pending update` 持久化状态/队列，再由 WorkManager 重试。

## 7. 测试新增 / 加强

`SyncRepositoryTest` 本轮补充：

- Person UPDATE 缺行 → 自动 GET 回源并继续应用；
- 未知 change type → Failed，不推进 cursor；
- version 回退 → Failed，不落库；
- `hasMore` 持续为 true → 达到上限后 Failed；
- 保留既有空批次、普通 ADD、多页、应用异常、网络异常、并发重入覆盖。

现有 create-on-push 测试继续覆盖：

- create 成功；
- 网络失败后的 UUID 持久化；
- 重试复用 UUID；
- 不生成第二个幂等键。

## 8. 第二轮 dead-code sweep 结果

本轮没有做“为了少几个文件而删代码”的危险清理。重点仍是生产消费者确认后再删除。

当前确认继续保留：

- Room migrations / schema history；
- QAuxv importer；
- sync cursor / history；
- `PlatformEntry` JSON shape；
- SafeLog / API error types；
- `ContactField` / `CustomField` / `ContactFieldValue`；
- Operation History；
- `LegacyTagFixup` 等历史数据修复逻辑。

历史 compatibility / duplicate UI 文件的删除已完成，不再重复制造兼容层。

## 9. 大型 Compose Feature

ContactDetail / Scanner 已完成第一轮拆分，但仍有职责耦合。

下一轮应继续按：

```text
Header / Fields / Platforms / Actions / Dialogs
```

做职责级拆分，而不是机械切文件。

这一项当前是 maintainability P2，不应阻塞 Sync / data correctness。

## 10. 代码质量评级

| 维度 | 当前评级 | 结论 |
|---|---:|---|
| API 契约一致性 | A | canonical `/api` 基本收口 |
| 网络层 | A- | `ApiCore` + 分域 API 清晰；本轮补强 Sync validation |
| Room / 数据层 | A- | V2 cache 稳定，sync replay-safe |
| Repository | B+ | 直推模型明确，但 PUT failure 尚无持久化 retry queue |
| DI / 架构边界 | A- | 核心 Service Locator 已清除 |
| Sync correctness | A- | 缺行回源、游标保护、未知变更 fail-safe 已补齐 |
| UI maintainability | B- | 大型 Compose feature 仍需职责级拆分 |
| Dead code 控制 | A | 历史 compatibility 已大幅收口，保留项均有依据 |
| 测试覆盖 | A- | 本轮新增 sync recovery / pagination guard |
| 综合 | A- | 当前主要风险已从历史兼容债务转为 outbound retry 能力 |

## 11. CI 状态

本轮修改会触发现有 PR CI：`Build Debug APK`。

截至本报告更新时，最新对应 commit 的 GitHub Actions run 已进入 `in_progress`，尚未得到最终 conclusion，因此本报告不宣称“构建已绿色”。

本地容器无法直接 clone GitHub（运行环境 DNS 无法解析 github.com），因此最终 Android Gradle 编译结果以仓库 CI 为准。

## 12. 本轮变更记录

```text
SyncRepository
  → 缺行 UPDATE 自动 GET 回源
  → unknown type/object/field fail-safe
  → version 单调 / page progress validation
  → MAX_PULL_ROUNDS 命中时返回 Failed

SyncApi
  → malformed page / parse drop / invalid cursor 拒绝

SyncRepositoryTest
  → recovery / unknown change / cursor regression / max rounds

CODE_REVIEW_REPORT_2026-09-01.md
  → 同步本轮实际完成项、剩余技术债务与 CI 状态
```

当前工作分支：

`refactor/dev-cleanup-2026-08-31`

本轮未创建额外分支。
