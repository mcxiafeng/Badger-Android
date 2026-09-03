# Task List: 本地权威写路径重构

规格：[docs/refactor-plan.md](../docs/refactor-plan.md)
顺序与检查点：[tasks/plan.md](./plan.md)

状态：**Phase 0（T01–T06c）、Phase 1（T07–T09）、Phase 2（T10–T13 + Checkpoint 2）、Phase 3（T14–T17 + Checkpoint 3）已完成并验证**——
`2cc4599 fix(data): Phase 0 止血` / `418f19d refactor(sync): Phase 1 Identity 分层` / `a96f1f3 refactor(sync): Phase 2 通用 Outbox`。
下一步从 **Phase 4（T18 网络装甲）** 开始。

> Phase 2 实施备注：
> - `SyncWorkerFactory.kt` 已不存在（WorkManager 默认 factory），OutboxWorker 在 doWork 时从 Koin GlobalContext 拉依赖。
> - 规格验收「离线建 Tag 再改名 → 服务端有该 Tag（R2 闭环）」移交 Phase 3（T14/T16c），Phase 2 只接 PATCH/MEMBER/DELETE。
> - 部分唯一索引以 `outbox.mergeKey` 合成列等价实现（规格 §3.1 已补注记）；poison 行（永久 4xx）无出口已登记为开放问题。

构建：`./gradlew :app:compileDebugKotlin`
单测：`./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.<包>.<类>"`

---

## Phase 0 — 止血（1 个 commit）

### Task T01: upsertTag 捕获 insert 返回的 rowId

**Description:** `SyncRepository.upsertTag` 新标签走 `insertTag` 时丢弃返回值，`rebuildTagRefs` 用 `entity.id = 0` 写 cross-ref。捕获 rowId 后 `copy(id = rowId)` 再重建关联。

**Acceptance criteria:**
- [x] 新标签路径：`insertTag` 返回值写入 entity 再 `rebuildTagRefs`
- [x] 已存在标签路径行为不变
- [x] 单测：mock `insertTag` 返回 `42L`，`insertCrossRefs` 的 `tagId == 42`，绝不为 `0`

**Verification:**
- [x] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.sync.SyncRepositoryTest"`
- [x] `./gradlew :app:compileDebugKotlin`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/SyncRepository.kt`
- `app/src/test/kotlin/top/mcxiafeng/badger/sync/SyncRepositoryTest.kt`

**Estimated scope:** S

---

### Task T02: 查名查询补 isDeleted = 0

**Description:** `getContactsByName` / `searchContactsByName` 缺软删过滤，`checkDuplicate` 会把软删同名当重复。与同文件其他查询对齐，补 `AND isDeleted = 0`。

**Acceptance criteria:**
- [x] 两条 SQL 都带 `isDeleted = 0`
- [x] 单测：软删同名不进入 `checkDuplicate` 的 `existingContact`（新建轻量 `ContactCacheDaoTest` 跑真实 Room 内存库）

**Verification:**
- [x] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.data.repository.ContactRepositoryImplTest"`
- [x] `./gradlew :app:compileDebugKotlin`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/data/cache/dao/ContactCacheDao.kt`
- `app/src/test/kotlin/top/mcxiafeng/badger/data/repository/ContactRepositoryImplTest.kt`

**Estimated scope:** S

---

### Task T03: updateCollection 入口 rebase identity

**Description:** UI 经 `CardCollectionWithCount.toCacheEntity()` 得到的实体没有 `serverId`/`personMembers`。`updateCollection` 全行 `@Update` 会抹掉身份字段。写前重读 existing，强制保留 `serverId` / `personMembers` / `isLocalOnly` / `createTime`。`deleteCollection` 若走投影实体，同样 rebase 后再清封面/DELETE。

**Acceptance criteria:**
- [x] 传入 `serverId=null` 的投影实体时，DAO 收到的行仍带 existing 的 identity
- [x] 名称等业务字段仍按入参更新
- [x] 单测：`updateCollection_projectionRoundTrip_keepsServerId`（另补 `deleteCollection_projection_keepsServerIdAndPushesDelete`）

**Verification:**
- [x] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.data.repository.CollectionRepositoryImplTest"`
- [x] `./gradlew :app:compileDebugKotlin`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/data/repository/CollectionRepositoryImpl.kt`
- `app/src/test/kotlin/top/mcxiafeng/badger/data/repository/CollectionRepositoryImplTest.kt`

**Estimated scope:** S

---

### Task T04: PendingPersonUpdate enqueue 字段级 merge

**Description:** `serverId PRIMARY KEY + CONFLICT_REPLACE` 让 `name=null` 的半载 PUT 覆盖待重放的改名。enqueue 时若已有行：新 payload 非 null 覆盖对应字段；新 payload 为 null 则保留旧值。`requestId` 仍换新代。

**Acceptance criteria:**
- [x] `enqueue(name="新")` 再 `enqueue(name=null, profile=P)` 后 `getReady().name == "新"` 且 profile 为 P
- [x] 两次都带非 null name 时保留最新 name
- [x] 现有「只保留最新 generation」测试仍绿

**Verification:**
- [x] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.data.queue.PendingPersonUpdateStoreTest"`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/data/queue/PendingPersonUpdateStore.kt`
- `app/src/test/kotlin/top/mcxiafeng/badger/data/queue/PendingPersonUpdateStoreTest.kt`

**Estimated scope:** S

---

### Task T05: ApiResult code 类型保护

**Description:** `unwrapApiResult` 对非数值 `code` 调 `asInt` 会抛 `NumberFormatException`/`UnsupportedOperationException`，调用方只 catch `ApiException`/`IOException` 时崩溃。预检 `isJsonPrimitive && isNumber`，否则抛 `ApiException`。

**Acceptance criteria:**
- [x] `{"code":"SUCCESS"}` 抛 `ApiException`，不抛 `NumberFormatException`
- [x] `code` 为 object/array 同样抛 `ApiException`
- [x] 数值 `code=200` 路径不变

**Verification:**
- [x] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.network.ApiCoreUnwrapTest"`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/network/ApiCore.kt`
- `app/src/test/kotlin/top/mcxiafeng/badger/network/ApiCoreUnwrapTest.kt`

**Estimated scope:** S

---

### Task T06a: executeImport 动作表改 index/rowId

**Description:** 数据层契约先改：`executeImport` 不再用 name 当地图键。分析时给每个 collection/contact 冲突分配稳定 rowId 或下标，动作表按这个取值。两个同名联系人必须能独立 MERGE/SKIP。

**Acceptance criteria:**
- [x] `executeImport` 签名不再是 `Map<String, Action>` 以 name 取值（改为 rowId 键；`ImportConflict`/`ContactConflict` 分析时分配稳定 rowId）
- [x] 单测：两个 name="张伟" 分别 MERGE / SKIP，只合并一个（`CollectionExporterTest`，另补同名名片夹 SKIP 与 rowId 唯一性两条）

**Verification:**
- [x] 扩展 `data` 包 JVM 测
- [x] `./gradlew :app:compileDebugKotlin`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/data/CollectionExporter.kt`
- 对应测试

**Estimated scope:** S

---

### Task T06b: ImportConflictDialog 按 rowId 管理状态

**Description:** LazyColumn key、checkbox map 全部改用 T06a 的稳定 id。调用方暂时传空 map 也能编译的过渡期尽量短，最好与 T06c 同一 commit。

**Acceptance criteria:**
- [x] `key` 不再是 `contactExport.name`（改为 `ContactConflict.rowId`）
- [x] 同名两行 checkbox 互不影响

**Verification:**
- [x] `./gradlew :app:compileDebugKotlin`

**Dependencies:** T06a

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/card/ImportConflictDialog.kt`

**Estimated scope:** S

---

### Task T06c: CardPage / CollectionDetailPage 进度与接线

**Description:** 名片夹冲突进度不用 name-keyed map 的 size。checkbox 初始值、`CardViewModel.executeImport` 参数与 T06a 对齐。

**Acceptance criteria:**
- [x] 两个同名名片夹冲突对话框能结束（进度改「第一个未作答冲突」按 rowId，不再用 name-keyed map.size）
- [x] Card 与 CollectionDetail 都改完

**Verification:**
- [x] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：导入含两个同名联系人、两个同名名片夹的 JSON（待真机验证）

**Dependencies:** T06a, T06b

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/card/CardPage.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/card/CollectionDetailPage.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/card/CardViewModel.kt`

**Estimated scope:** S

---

### Checkpoint 0

- [x] `./gradlew :app:compileDebugKotlin` 绿
- [x] T01–T06c 单测绿
- [x] 问用户是否把 Phase 0 打成一个 commit（已问，用户选拆两个 commit → `2cc4599`）
- [x] **未合入前不要开始 Phase 1**（已合入后才进入 Phase 1）

---

## Phase 1 — Identity 分层

### Task T07: 引入 RemoteIdentity / EntityKind

**Description:** 把 `serverId` 的两义（Synced vs PendingCreate）写成密封类型，供写路径与后续 Outbox 使用。本任务只加类型与 entity 扩展，不改行为。

**Acceptance criteria:**
- [x] `RemoteIdentity.Synced` / `PendingCreate` / `Unidentified`
- [x] `EntityKind.PERSON` / `TAG` / `COLLECTION`
- [x] Contact / Tag / Collection cache entity 能映射到 `RemoteIdentity`
- [x] 新写入禁止产出 `Unidentified`（仅迁移存量）（KDoc 已标注约束）

**Verification:**
- [x] `./gradlew :app:compileDebugKotlin`（另补 `IdentityTest` 4 条 JVM 映射回归）

**Dependencies:** Checkpoint 0

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/Identity.kt`（新建：RemoteIdentity + EntityKind + rebase 同文件，§2.2 收敛）

**Estimated scope:** S

---

### Task T08: IdentityRebase 接入 Collection/Tag 写路径

**Description:** 投影→实体的唯一合法路径。`updateCollection` 用正式 `IdentityRebase` 替换 T03 的内联 copy；Tag 写路径同样 rebase。`toCacheEntity()` 加 KDoc：禁止直接用于写路径。

**Acceptance criteria:**
- [x] Collection 更新强制 rebase（updateCollection / deleteCollection 均走 `rebaseCollection`）
- [x] Tag 更新强制 rebase（renameTag / setTagColor 走 `rebaseTag`，renameTag 由定向 UPDATE 升级为全行 rebase 写）
- [x] 单测：投影 round-trip 后 `serverId`/`personMembers`/`isLocalOnly` 不变（Collection + Tag 两侧都有）

**Verification:**
- [x] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.data.repository.CollectionRepositoryImplTest"`
- [x] `./gradlew :app:compileDebugKotlin`

**Dependencies:** T07

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/Identity.kt`（rebase 并入 T07 的同文件，§2.2 收敛）
- `app/src/main/kotlin/top/mcxiafeng/badger/data/repository/CollectionRepositoryImpl.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/data/repository/TagRepositoryImpl.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/data/Models.kt`（KDoc）
- `app/src/main/kotlin/top/mcxiafeng/badger/data/repository/ContactMapper.kt`（KDoc）

**Estimated scope:** M

---

### Task T09: 删除联系人时回收头像文件

**Description:** `hardDeleteContact`、批量删除、sync REMOVE 只删 DB 行，本地 `contact_*_avatar.webp` 泄漏。对齐换头像路径，调用 `Methods.deleteAvatarFile`。

**Acceptance criteria:**
- [x] 三条删除路径都删文件（hardDeleteContact / 批量 deleteByIds / sync REMOVE）
- [x] 无 avatarPath 时不崩（含 `avatarPath=null` 用例）
- [x] 不 recycle 仍在展示的 Bitmap（只删文件）

**Verification:**
- [x] `./gradlew :app:compileDebugKotlin`
- [x] 有现成仓库测则扩展；否则代码审查 + 日志（扩展 CommitDeleteTest 3 条 + ContactRepositoryImplTest 1 条 + SyncRepositoryTest 1 条）

**Dependencies:** T07（可与 T08 并行，不要同时改 ContactRepositoryImpl 的无关逻辑）

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/data/repository/ContactRepositoryImpl.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/SyncRepository.kt`

**Estimated scope:** S

---

### Checkpoint 1

- [x] 编译绿；T08 单测绿
- [x] 问用户是否 commit Phase 1（`418f19d`）

---

## Phase 2 — 通用 Outbox

### Task T10: Room v16 outbox 表

**Description:** 按规格建 `outbox` 表与索引。`MIGRATION_15_16`：CREATE + DROP `pending_person_updates`，**不 INSERT 搬数据**。导出 schema JSON。禁止 destructive fallback。

**Acceptance criteria:**
- [x] `@Database version = 16`，entities 含 Outbox
- [x] schemas 有 `16.json`
- [x] 迁移不读旁路表行
- [x] Koin `databaseModule` 注册 DAO

**Verification:**
- [x] `./gradlew :app:compileDebugKotlin`（触发 schema 导出）
- [x] 确认 `app/schemas/top.mcxiafeng.badger.data.AppDatabase/16.json` 存在（DDL 与迁移逐字一致）

**Dependencies:** Checkpoint 1

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/data/queue/OutboxEntity.kt`（新建）
- `app/src/main/kotlin/top/mcxiafeng/badger/data/queue/OutboxDao.kt`（新建）
- `app/src/main/kotlin/top/mcxiafeng/badger/data/AppDatabase.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/di/KoinModules.kt`

**Estimated scope:** M

---

### Task T11: OutboxStore 字段级 merge + 原子 attempts

**Description:** 实现规格 §3.1 + §3.8 契约：同 `(kind, localId, PATCH)` 字段级 merge；CREATE 幂等忽略；DELETE 取消未发 CREATE/PATCH；MEMBER_* 不合并、FIFO。`recordFailure` 必须单条 SQL `attempts = attempts + 1`。

**Acceptance criteria:**
- [x] 半载 PATCH 不覆盖已有非 null 字段（F4 升级版）
- [x] 并发 `recordFailure` attempts 不丢
- [x] CREATE 已在队再入队被忽略
- [x] 认领靠 `UNIQUE(entityKind, localId, op)` 索引一次完成，禁止 SELECT-then-INSERT（INSERT-first，mergeKey 唯一索引等价实现）
- [x] CREATE payload 变更仍忽略（差量走 PATCH），决策写进 `OutboxStore` KDoc
- [x] 行在成功前不删；KDoc 标注保留期盖过最长重试链
- [x] enqueue 返回类型化结果（新增 / 并入已有），不是裸 Boolean

**Verification:**
- [x] 新建 `OutboxStoreTest`（仿 `PendingPersonUpdateStoreTest`，11 条全绿）
- [x] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.data.queue.OutboxStoreTest"`

**Dependencies:** T10

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/OutboxStore.kt`（新建：op 枚举 + store 同文件，§2.2 收敛）
- `app/src/test/kotlin/top/mcxiafeng/badger/data/queue/OutboxStoreTest.kt`

**Estimated scope:** M

---

### Task T12a: Outbox Worker + Scheduler

**Description:** 新 Worker 按 `EntityKind` 消费 Outbox；Scheduler 去抖 kick。Koin / `SyncWorkerFactory` 指向新类。本任务还不改 `ServerApi.updatePerson`。

**Acceptance criteria:**
- [x] Worker 能重放 Person PATCH（用现有测试夹具：真实 Room outbox + LocalHttpServer）
- [x] Kick 去抖仍在（ExistingWorkPolicy.APPEND_OR_REPLACE）

**Verification:**
- [x] `./gradlew :app:compileDebugKotlin`
- [x] `OutboxWorkerTest` 3 条绿（重放 body 含合并后 name+bio / 按 kind 分发 / 失败保留 PENDING 记 attempts）

**Dependencies:** T11

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/OutboxWorker.kt`（新建）
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/OutboxScheduler.kt`（新建）
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/SyncWorkerFactory.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/di/KoinModules.kt`

**Estimated scope:** S

---

### Task T12b: Person PUT 改为只入队

**Description:** `ServerApi.updatePerson` 只 enqueue + kick。去掉「先入队再同步 PUT 再 deleteIfRequest」。Tag/Collection PATCH/MEMBER/DELETE 接上分发骨架（Create 仍走 T14）。**A6 注记**：终态 seam 应命名在 Repository 层（「commit」），`ServerApi.updatePerson` 变 enqueue 是过渡形态，Phase 3 后随 A2 一并评估收缩，本任务不做大迁移。

**Acceptance criteria:**
- [x] 应用内无第二套 Person PUT 直发（除 Worker）
- [x] 旧双路径测试改写或删除（Tag/Collection 仓库测试改挂新签名）

**Verification:**
- [x] `./gradlew :app:compileDebugKotlin`

**Dependencies:** T12a

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/network/ServerApi.kt`

**Estimated scope:** S

---

### Task T13: 删除旧 PendingPersonUpdate 链路

**Description:** 新 Worker 接管后删除 `PendingPersonUpdateStore` / 旧 Worker / 旧 Scheduler 及测试改挂 Outbox。禁止留兼容 shim。

**Acceptance criteria:**
- [x] 主代码无 `PendingPersonUpdateStore` 引用（仅历史注释提及）
- [x] 旧测试删除或改写（PendingPersonUpdateStoreTest 删除，OutboxStoreTest 承接）

**Verification:**
- [x] `./gradlew :app:compileDebugKotlin`
- [x] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.data.queue.*"`

**Dependencies:** T12b

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/data/queue/PendingPersonUpdateStore.kt`（删）
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/PendingPersonUpdateWorker.kt`（删）
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/PendingPersonUpdateScheduler.kt`（删）
- 对应 test

**Estimated scope:** S

---

### Checkpoint 2

- [x] 离线改 name 再改 bio，重放 payload.name 仍是新值（Store 级 + Worker 端到端 HTTP body 双覆盖）
- [x] attempts 并发单测绿（12 线程并发 recordFailure）
- [x] 问用户是否 commit Phase 2（已提交 `a96f1f3`，单 commit）

---

## Phase 3 — CreateOnPush + 双向 Sync

### Task T14: 统一 CreateOnPush（选项 C）

**Description:** Person/Tag/Collection 创建走同一函数：复用已有 clientUuid，禁止重新生成；POST 带 uuid；忽略则用返回 uuid 覆盖；400 则去 uuid 再 POST 一次并打 error 日志。失败保留 `PendingCreate` 并记 attempts。

**Acceptance criteria:**
- [x] 三种实体离线创建都会入队 CREATE（`SyncEngineTest.syncOnce_backfillsLocalOnlyRows_andPushesThemUp` 三种实体全推上去）
- [x] 400 降级只发生一次（`SyncEngineTest.createOnPush_tag400_downgradesOnceWithoutUuid`）
- [x] 已 Synced 的实体不 POST（`SyncEngineTest.createOnPush_syncedEntity_skipsPost`）
- [x] clientUuid 复用有测：同实体两次调用 CREATE 用同一 uuid（`SyncEngineTest.createOnPush_failureThenRetry_reusesSameClientUuid`）
- [x] 超时/断连 = **未知结局**：保留 PendingCreate 走重试，禁止记 FAILED_PERMANENT（`SyncEngineTest.pushOnce_createFails_patchBlockedWithoutAttemptsPenalty`）
- [x] 结果复用/扩展 `CommitResult`，不新造结果类型
- [x] KDoc 标注：Tag/Collection CREATE 在服务端兑现 uuid（ticket A）前 unsafe-to-retry，靠 pull 收敛兜底

**Verification:**
- [x] 针对 CreateOnPush 的 JVM 测（`SyncEngineTest` 9 条全绿）
- [x] `./gradlew :app:compileDebugKotlin`

**Dependencies:** Checkpoint 2, T07

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/SyncEngine.kt`（CreateOnPush 是文件内函数，不单独成文件，§2.2 收敛）
- `app/src/main/kotlin/top/mcxiafeng/badger/data/repository/ContactRepositoryImpl.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/data/repository/TagRepositoryImpl.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/data/repository/CollectionRepositoryImpl.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/network/V2DomainApi.kt`（请求体加可选 uuid）

**Estimated scope:** M

---

### Task T15: QAuxv insertOne 走 insertContact

**Description:** QAuxv 导入直接 `dao.insertContact`，无 clientUuid、无 CREATE。改为 `ContactRepository.insertContact`，进入统一 create-on-push。

**Acceptance criteria:**
- [x] `insertOne` 不再直写 DAO（改走 `insertContact` + `pushPlatformUpdate`）
- [x] 导入的好友带 PendingCreate identity

**Verification:**
- [x] 现有 QAuxv 解析测仍绿；`ContactRepositoryImplTest` QAuxv 系列适配验收值
- [x] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.data.QAuxvFriendImporterTest"`

**Dependencies:** T14

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/data/repository/ContactRepositoryImpl.kt`

**Estimated scope:** S

---

### Task T16a: PushLoop

**Description:** 按 CREATE → PATCH → MEMBER → DELETE 消费 Outbox。本任务不搬 pull、不改 retryAll。

**Acceptance criteria:**
- [x] 离线建联系人后 `pushOnce` 会 POST（`SyncEngineTest.createOnPush_failureThenRetry_reusesSameClientUuid`）
- [x] mutex 防止并发 push（`SyncEngine` syncMutex + AtomicBoolean started）
- [x] 单条 op 三种结局：成功出队 / 失败 `recordFailure` / **未知（超时断连）**保留 Pending 不加 attempts 惩罚以外的状态跳变

**Verification:**
- [x] 新 `SyncEngineTest` 覆盖 push 顺序（`pushOnce_replaysCreateBeforePatch_andBackfillsPatchRemoteId` / `pushOnce_createFails_patchBlockedWithoutAttemptsPenalty` / `pushOnce_memberPayloadPersonUuid_backfilledAfterPersonCreate`）
- [x] `./gradlew :app:compileDebugKotlin`

**Dependencies:** T14

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/SyncEngine.kt`（push 逻辑进同文件，§2.2 收敛）

**Estimated scope:** S

---

### Task T16b: PullLoop 搬家 + syncOnce 顺序

**Description:** 现有 `doPull` 搬到 `SyncPullLoop`。`syncOnce` = pushOnce 然后 pullOnce。整批 pull 成功才推进游标。`upsertTag` 继续用 T01 的 rowId。

**Acceptance criteria:**
- [x] 顺序固定 push → pull（`SyncEngine.syncOnce` = backfill → pushLocked → doPull）
- [x] 现有 SyncRepositoryTest 游标用例改挂 PullLoop 仍绿（`SyncPullLoopTest` 12 条全绿）

**Verification:**
- [x] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.sync.*"`

**Dependencies:** T16a

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/SyncEngine.kt`（doPull 搬进同文件，§2.2 收敛）
- `app/src/test/kotlin/top/mcxiafeng/badger/sync/SyncPullLoopTest.kt`（原 SyncRepositoryTest 改挂）

**Estimated scope:** S

---

### Task T16c: 启动扫描 isLocalOnly 补 CREATE

**Description:** 启动或 syncOnce 前扫描 `isLocalOnly=true` 的 Person/Tag/Collection，补建 CREATE op。打日志。一次性回填，不是迁移 SQL。

**Acceptance criteria:**
- [x] 存量 local-only 行在 syncOnce 时入队 CREATE（`SyncEngineTest.syncOnce_backfillsLocalOnlyRows_andPushesThemUp`）
- [x] 已有 CREATE 的不重复入队（`IgnoredDuplicateCreate` 幂等 + 回填幂等再次 syncOnce 不重复）

**Verification:**
- [x] JVM 测一条存量行（同上端到端测试覆盖 Person/Tag/Collection 三种实体）
- [x] `./gradlew :app:compileDebugKotlin`

**Dependencies:** T16a

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/SyncEngine.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/data/cache/dao/ContactCacheDao.kt`（`getLocalOnlyContactsOnce` 已有）
- `app/src/main/kotlin/top/mcxiafeng/badger/data/cache/dao/TagCacheDao.kt`（新增 `getNeverSyncedTagsOnce`）
- `app/src/main/kotlin/top/mcxiafeng/badger/data/cache/dao/CardCollectionCacheDao.kt`（新增 `getNeverSyncedCollectionsOnce`）

**Estimated scope:** S

---

### Task T17: retryAll 改 syncOnce；删除 SyncRepository

**Description:** 设置页「立即同步」改为 `syncOnce`。逻辑搬完后删除 `SyncRepository`，测试改挂 SyncEngine/PullLoop。

**Acceptance criteria:**
- [x] `SyncStatusRepository.retryAll` 不再只 pull（改为 `syncEngine.syncOnce()`）
- [x] 主代码无 `class SyncRepository`（已删除，477 行）

**Verification:**
- [x] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.sync.*"`（SyncPullLoopTest 12 + SyncEngineTest 9 + OutboxWorkerTest 3 = 24 条全绿）
- [x] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.data.repository.SyncStatusRepositoryImplTest"`（6 条全绿）

**Dependencies:** T16b

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/data/repository/SyncStatusRepositoryImpl.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/SyncRepository.kt`（删）
- `app/src/main/kotlin/top/mcxiafeng/badger/di/KoinModules.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/BadgerApplication.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/setupguide/SetupGuideViewModel.kt`

**Estimated scope:** S

---

### Checkpoint 3

- [x] 离线建联系人/名片夹 → 立即同步 → 能推上去（`SyncEngineTest.syncOnce_backfillsLocalOnlyRows_andPushesThemUp` 端到端验证三种实体全推上去）
- [x] 服务端 ADD 带成员的新 Tag，本地 `tagId ≠ 0`（F1 由 T01 在 Phase 0 固化，SyncEngine.upsertTag 原样保留 rowId 回填）
- [x] 问用户是否 commit Phase 3（已提交 `a9104b5`）

---

## Phase 4 — UI 契约 + 网络装甲

同一阶段内 T18–T22、T23–T26、T27–T29 可拆三个 commit。不要和 Phase 5 搬运混。

### Task T18: 网络失败不登出（C1 + C9）

**Description:** token 刷新遇到 ConnectException/SocketTimeout/UnknownHost 不得 `clearAuth`。`bootstrap` 仅对真 401 清 token。

**Acceptance criteria:**
- [ ] 瞬时断网不删磁盘 refresh_token
- [ ] 真 401 仍登出

**Verification:**
- [ ] 扩展 `NetworkModuleTest` / `AuthViewModelTest` 或 UserAuth 测
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** Checkpoint 3（若用户允许，可在 Phase 3 之前做：本任务不依赖 Outbox）

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/NetworkModule.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/data/repository/UserAuthRepository.kt`

**Estimated scope:** S

---

### Task T19: API 边界装甲（C2 / C4 / C5）

**Description:** 全 network/ 解析路径按 §3.8 边界校验扫一遍：服务端 JSON 是不可信输入，形状违规一律 `ApiException`，不许 NPE/ISE/NumberFormatException 逃逸。已知点：`me()` 日志的 `asString`（C2）、`baseUrl` 无 scheme 的 OkHttp IAE（C4）、AI/shortio 2xx 空体 `body!!`（C5）；其余 `*Api` 逐个核对（Gson `asX` 系列与 `!!` 是重点）。

**Acceptance criteria:**
- [ ] name 非 primitive 不抛 UnsupportedOperationException
- [ ] `192.168.1.5:8080` 抛可理解的 ApiException
- [ ] 2xx 空体/非对象走错误路径并打日志
- [ ] network/ 全量 grep `asJsonObject|asJsonArray|asString|!!` 的命中点逐个有防护或有「不可达」注释

**Verification:**
- [ ] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.network.*"`

**Dependencies:** None（可与 T18 并行）

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/network/AuthApi.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/network/ApiCore.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/network/SecondaryApis.kt`

**Estimated scope:** S

---

### Task T20: 时间戳数量级 + OCR 体积（C3 / C6）

**Description:** `parseServerDateMillis` 的 double 分支加与 long 分支相同的数量级判断。OCR `bitmapToBase64` 先限制最长边并限制 JPEG 体积（对齐 uploadImage 5MB 量级，抽命名常量）。

**Acceptance criteria:**
- [ ] `1725270000000.0` 不再 ×1000
- [ ] 秒级 double 仍 ×1000
- [ ] OCR 不再对全分辨率位图无上限编码

**Verification:**
- [ ] PersonApi 若有测则扩；OCR 用小 Bitmap 单测或审查
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/network/PersonApi.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/ocr/AiOcrService.kt`

**Estimated scope:** S

---

### Task T21: commitMerge 不销毁 localOnly 唯一副本（C7）

**Description:** 被合并行若 `isLocalOnly`：先把字段/平台拷到 target，再硬删；HTTP `mergePersons` 不得带 clientUuid。target 已是 localOnly 的现有守卫保留。

**Acceptance criteria:**
- [ ] localOnly 被合并行的字段出现在 target 上
- [ ] `mergePersons` 参数不含其 serverId/clientUuid
- [ ] 全是 localOnly 被合并时不发 HTTP 仍拷数据

**Verification:**
- [ ] 扩展 `ContactRepositoryImplTest` 或 Commit 相关测
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/data/repository/ContactRepositoryImpl.kt`

**Estimated scope:** S

---

### Task T22: DeviceId 首次初始化线程安全（C8）

**Description:** `cachedId` 加 `@Volatile`，生成+写 prefs 用 synchronized 或 double-check。

**Acceptance criteria:**
- [ ] 双线程首次 `deviceId()` 返回同一 uuid
- [ ] 与落盘 prefs 一致

**Verification:**
- [ ] 单测双线程；`./gradlew :app:compileDebugKotlin`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/sync/DeviceIdProvider.kt`

**Estimated scope:** XS

---

### Task T23: 详情页写完再 reload（C10 / C20 / D4）

**Description:** 平台与字段的增改删改为 suspend，完成后再 `reloadContact`。删除 VM 外层空 catch `CancellationException`（保留内层 rethrow）。

**Acceptance criteria:**
- [ ] `addOrUpdatePlatform` / `updateFieldValue` / `deleteFieldValue` 对页面是可等待的
- [ ] 不再 fire-and-forget + 立即 reload
- [ ] CancellationException 向上传

**Verification:**
- [ ] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.pages.person.contact.ContactDetailViewModelBatchTest"`
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/person/contact/ContactDetailViewModel.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/person/contact/ContactDetailPage.kt`

**Estimated scope:** M

---

### Task T24a: PersonPage 全选 remember key（C11）

**Description:** `allFilteredIds` 的 remember key 改为 id 列表或内容 hash，不能只用 size。

**Acceptance criteria:**
- [ ] 同 size 不同 id 的搜索结果全选用新 id

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：搜索切换同数量人名后全选

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/person/PersonPage.kt`

**Estimated scope:** XS

---

### Task T24b: RegionPicker reset + 日志（C12 / D5）

**Description:** 对话框打开前 `reset()`。空 catch 补 `Log.e`。

**Acceptance criteria:**
- [ ] 换国家再打开看到新省份
- [ ] invalidate 失败有日志

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/person/contact/RegionPickerDialog.kt`

**Estimated scope:** XS

---

### Task T24c: Dashboard 最近联系人用本地 id（C13）

**Description:** `stats.recentPersons` 用本地 id 匹配，禁止 `id=0` 覆盖可点击列表。

**Acceptance criteria:**
- [ ] 刷新后最近联系人卡片 `id > 0` 可跳转

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：登录后点最近添加

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/dashboard/DashboardViewModel.kt`

**Estimated scope:** XS

---

### Task T25: Auth 取消在途协程与邮箱绑定（C14 / C15）

**Description:** `reset`/`switchToX` 取消发码、验证码、登录、注册 Job。发码期间冻结邮箱，或把 `emailCaptchaId` 绑到快照邮箱，register 不得用新邮箱配旧 captcha。

**Acceptance criteria:**
- [ ] 切回登录后再切注册，不出现过期「验证码已发送」
- [ ] 发码中改邮箱，register 不会用错绑

**Verification:**
- [ ] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.pages.auth.AuthViewModelTest"`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/auth/AuthViewModel.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/auth/AuthScreens.kt`（若需 disabled）

**Estimated scope:** S

---

### Task T26: 导入 JSON 空列表不 NPE（C16）

**Description:** `analyzeImportConflicts` 在 map 之前把 `collections`/`contacts`/`fields` 的 Gson null 当成空列表或直接抛 `IllegalArgumentException`，与 preview 的宽容策略对齐到「分析失败可提示」。

**Acceptance criteria:**
- [ ] `analyzeImportConflicts("{}")` 不 NPE
- [ ] 非法结构有明确异常信息

**Verification:**
- [ ] 与 T06 同一测试类加用例
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** T06a（若 Phase 0 已改签名，在新签名上补）

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/data/CollectionExporter.kt`

**Estimated scope:** XS

---

### Task T27: 扫码 Mat 泄漏、后缀 key、保存事务（S1 / S3 / S4）

**Description:** `detectQrCodesWithBounds` 在 catch/finally 释放 OpenCV `Mat`。`buildMergeEntries`/`mergeFieldsToContact` 查 fieldId 前 `stripFieldKeySuffix`。`SaveScannedContactUseCase` 的 insert→字段→名片夹放入同一 Room 事务（或 repository 事务 API）。

**Acceptance criteria:**
- [ ] 检测抛错后 Mat 被 release
- [ ] `qq_1` 能合并进 `qq` 字段
- [ ] 保存中途失败不留下无字段孤儿（事务回滚）

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 有 scanner/domain 测则扩；S1 以代码审查为主

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/QrCodeUtils.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScannerSaver.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/domain/SaveScannedContactUseCase.kt`

**Estimated scope:** M

---

### Task T28: NFC 未开启不得进入 READY（S2）

**Description:** `enableReaderMode` 在 adapter 关闭时 return，但 `startWriting` 已设 `_pendingUri`，UI 停在等待贴卡。失败必须清 pending 并让 VM 进 ERROR（提示去系统设置开 NFC）。

**Acceptance criteria:**
- [ ] NFC 关闭时不会保持 `isWriting == true`
- [ ] UI 收到明确失败，不是无限 READY

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：关系统 NFC 再点写入

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/social/NfcHelper.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/social/SocialViewModel.kt`

**Estimated scope:** S

---

### Task T29: ImportProfile 用平台 key（S6）

**Description:** `updatePlatformField(fieldKey = displayName)` 会把 map 键写成「QQ」。改为 `toFieldValues()` 的平台 key（`qq`/`wechat`）。displayName 只进 `PlatformEntry.displayName`。

**Acceptance criteria:**
- [ ] 导入后 platforms map 的 key 是 `qq` 不是「QQ」
- [ ] 空白 value 仍跳过 phone/email

**Verification:**
- [ ] 给 UseCase 加 JVM 测（mock repository）
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/domain/ImportProfileFieldsUseCase.kt`

**Estimated scope:** XS

---

### Task T37: 扫码确认等保存完成再关对话框（U1）

**Description:** `ScannerPage.onConfirm` 先 `scope.launch(IO) { 保存 }` 再立刻 `resetScannerState()`。对话框消失、若用户马上返回，`rememberCoroutineScope` 取消，insert→字段→名片夹会半截中断。改为 await 保存（成功/失败）后再 reset；失败 Toast + 日志，不丢对话框里的选择。

**Acceptance criteria:**
- [ ] reset 发生在保存 suspend 返回之后
- [ ] 离开扫码页不会取消一次已经开始的保存（用 `viewModelScope` 或应用级 scope，不用 Composable scope 扛写库）
- [ ] 失败有 Toast，不静默

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：确认保存后立刻按返回，联系人字段仍在

**Dependencies:** 最好与 T38 同一 commit（写路径进 VM 后天然用 `viewModelScope`）

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScannerPage.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScannerViewModel.kt`

**Estimated scope:** S

---

### Task T38: 扫码写路径收进 ViewModel（U2）

**Description:** 页面把 `contactRepository` 等 public 字段拿去 `Dispatchers.IO` 写库。VM 的 `saveContact` / `mergeWithExisting` / `setImageResult` / `dismiss*` / `uiState` **页面零调用**（D11），真正保存走 helper。保存、合并、附加、打标改走 VM suspend API；Repository 改 `private`；删掉没人调的旧 API，不要留 shim。合并语义保持 `mergeFieldsToContact` 的 KEEP/REPLACE/APPEND（不要误换成「只补空」的 UseCase——那是 T35）。详情/设置同样的红线走 **T44a–c**，不要塞进本任务。

**Acceptance criteria:**
- [ ] `ScannerViewModel` 不再 public 暴露 Repository
- [ ] `ScannerPage` / `ResultDialog` 不直接调 DAO/Repository 写方法（只传用户选择）
- [ ] 现有冲突合并行为不变

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：新建扫码联系人、合并到已有、附加到已有

**Dependencies:** T37 可合并；T35 仍负责删死 UseCase，本任务不要先切到 MergeContactUseCase

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScannerViewModel.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScannerPage.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScannerDialogs.kt`

**Estimated scope:** M

---

### Task T39: EXIF 回收、OCR 未配置提示、相册回调（U3 / U4 / U5）

**Description:** flip/transpose 与旋转路径一样，新图 !== 旧图则 recycle 入参。确认按钮在 OCR 开启但未配置、且没有累积码时提示去设置，禁止静默 no-op。相册/`onImageCaptured` 在主线程调 `processPhotoBitmap`/`processBitmapOcrOnly`，不要外层再套一层 IO launch。

**Acceptance criteria:**
- [ ] EXIF 四向翻转回收原 Bitmap
- [ ] 未配置 OCR 时有可见提示
- [ ] 相册选图结果仍在主线程更新状态

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：未配 AI 点确认；相册选一张镜像自拍

**Dependencies:** None（可与 T37 并行，少交叉 ScannerPage 的 onCaptureClick）

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/QrImagePreprocessor.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScannerPage.kt`

**Estimated scope:** S

---

### Task T40: 头像 Bitmap 回收与删平台刷新（U6 / U7）

**Description:** `ContactDetailPage` 页头 `avatarBitmap`、`CreateContactPage`/`ImportFromPlatformDialog` 的 `previewBitmap`：赋值前回收旧引用（仍作为当前帧显示的不要在同一帧 recycle）。删平台并回退头像后 `avatarVersion++`。页面离开用 `DisposableEffect` 收尾。

**Acceptance criteria:**
- [ ] 换头像/换 URL 不会只丢引用不 recycle
- [ ] 删当前头像来源平台后面立刻显示回退图或空
- [ ] 不 recycle 正在 `asImageBitmap()` 展示中的同一实例（先换引用再 recycle 旧的）

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：详情页反复同步头像；删掉当前头像平台

**Dependencies:** T23 若同改 ContactDetailPage，先合 T23 或同一人改，避免冲突

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/person/contact/ContactDetailPage.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/person/contact/CreateContactPage.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/person/contact/ImportFromPlatformDialog.kt`

**Estimated scope:** S

---

### Task T41: 导航转场 500ms 改为 300ms（U8）

**Description:** `NavTransitions.DURATION_MS` 从 500 改为 300。Easing 可保留。这是 AGENTS.md 已记录的 jank，不是视觉重做。

**Acceptance criteria:**
- [ ] push/pop 使用 300ms
- [ ] reset/none 现有 300/200/0 不误改

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：进联系人详情再返回，无明显过冲拖尾

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/ui/navigation/NavTransitions.kt`

**Estimated scope:** XS

---

### Task T42: 名片 QR 浅色背景对齐卡片（U9）

**Description:** 浅色模式 `qrBackgroundColor = WHITE` 与 Miuix `surface` 不一致，出现环形边。改为与当前 `MiuixTheme.colorScheme.surface` 同源的 ARGB（记住 theme 变化）。深色 `#FF242424` 若仍与 surface 不一致同样对齐。

**Acceptance criteria:**
- [ ] 浅色模式码与卡片之间无白环
- [ ] 深色模式无灰阶错层
- [ ] 码仍可扫（对比度足够）

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：浅色/深色打开「我的名片」看码，再用另一台设备扫一次

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/social/QrCodeCard.kt`

**Estimated scope:** XS

---

### Task T43: 扫描器与名片夹 WindowDialog Pattern A（U10）

**Description:** 规范：外层 `if (showXxx) { WindowDialog(show = true) }`。优先收口扫描器：`MergeConflictDialog`、`ScanMarkerPickerDialog`、`ScannerSubDialogs`；名片夹 `CreateCollectionDialog`/`EditCollectionDialog`/`ContactSelectDialog` 若调用方已 `if` 包一层则在函数内保持 `show=true` 并在 KDoc 写明「调用方必须 if 挂载」，禁止再套 `WindowDialog(show = showXxx)`。

**Acceptance criteria:**
- [ ] 扫描器对话框首次打开有内容，无首帧空白
- [ ] 不出现双重 `show` 参数导致的闪烁
- [ ] 三条 dismiss 路径仍关 flag（onDismiss / 取消 / 确认）

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：扫码合并冲突、标记 Tag、新建名片夹，各开一次看首帧

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/MergeConflictDialog.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScanMarkerPickerDialog.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScannerSubDialogs.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/card/CardDialogs.kt`

**Estimated scope:** S

---

### Checkpoint 4

- [ ] 本阶段单测绿；`compileDebugKotlin` 绿
### Task T44a: AppViewModel 不再公开 Repository（ARCH-A）

**Description:** `AppViewModel` 三个 Repository 改 private。`App.kt` 不得再 `appViewModel.contactRepository`。需要的能力做成 VM 方法。

**Acceptance criteria:**
- [ ] `AppViewModel` 无 public Repository
- [ ] `App.kt` 不直接调 Repository

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** None（可与 T38 并行）

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/AppViewModel.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/App.kt`

**Estimated scope:** S

---

### Task T44b: 详情/创建/名片 VM 收口（ARCH-B）

**Description:** `ContactDetailViewModel` / `CreateContactViewModel` / `UserProfileDetailViewModel` 的 Repository 改 private。页面里 `launch(IO)` 写库改为等 VM suspend（与 T23 衔接：T23 管 reload 竞态，本任务管可见性）。

**Acceptance criteria:**
- [ ] 三个 VM 无 public Repository
- [ ] UserProfileDetailPage 不再 `viewModel.userProfileRepository`

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.pages.person.contact.*"`

**Dependencies:** T23 建议先合或同一人改 ContactDetail

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/person/contact/ContactDetailViewModel.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/person/contact/CreateContactViewModel.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/person/contact/UserProfileDetailViewModel.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/person/contact/UserProfileDetailPage.kt`

**Estimated scope:** M

---

### Task T44c: 设置/引导/AccountProfile 收口（ARCH-C）

**Description:** `PlatformListViewModel.repository`、`NfcSettingsViewModel.userProfileRepository`、`SetupGuideViewModel.userProfileRepository` 改 private。`AccountProfilePage` 禁止 Composable `launch(IO)` 调 `saveUserProfile`，走已有 `AccountSettingsViewModel` / `UserProfileDetailViewModel` 方法。

**Acceptance criteria:**
- [ ] 上述 VM 无 public Repository
- [ ] AccountProfile 保存不在 Composable IO 协程里直写仓库

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/settings/PlatformListPage.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/settings/NfcSettingsViewModel.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/settings/AccountProfilePage.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/setupguide/SetupGuideViewModel.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/setupguide/SetupStepPlatforms.kt` / `SetupStepProfile.kt`（接线）

**Estimated scope:** M（接线文件刚好 5 个，超了就把两个 SetupStep 放到下一刀）

---

### Task T45: 拍照处理中禁止点外部关闭（U11 / U15）

**Description:** `PhotoModeDialog` 与 `ScanModeDialog` 处理中拦了 BackHandler，但 `onDismissRequest` 仍是 `onDismiss`。与 `ResultDialog` 对齐：处理中 dismiss 置空，不要依赖父级传入空 lambda。

**Acceptance criteria:**
- [ ] `isProcessingPhoto` 时点外部不关、不丢图
- [ ] 两个对话框处理结束后可关

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：拍照识别中点对话框外部

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/PhotoModeDialog.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScanModeDialog.kt`

**Estimated scope:** XS

---

### Task T46: NFC「当前指向」改 BasicComponent（U13）

**Description:** 只读状态行用了 `ArrowPreference` 且无 onClick，箭头暗示可点。改 `BasicComponent`。

**Acceptance criteria:**
- [ ] 「当前指向」无箭头、不可点
- [ ] summary 文案不变

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/settings/NfcSettingsPage.kt`

**Estimated scope:** XS

---

### Task T47: AvatarPreviewDialog 改为 Pattern A（U12）

**Description:** `WindowDialog(show = show && displayBitmap != null)` 改为外层 `if (show && displayBitmap != null) { WindowDialog(show = true) }`。调用方 `ContactDetailPage` 同步用 `if` 挂载。

**Acceptance criteria:**
- [ ] 首次打开预览有内容，无首帧空白
- [ ] dismiss 三条路径仍关 flag 并 recycle previewBitmap

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：详情页点头像打开预览

**Dependencies:** T40 若同改头像生命周期，先合或同一人改

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/person/contact/ContactDetailAvatar.kt`

**Estimated scope:** XS

---

### Task T48: 空列表不要 LazyColumn 空滚（U14）

**Description:** `CollectionDetailPage` 无联系人、`SocialPage` 无平台时仍用 LazyColumn 放 hero/引导卡，空态可 overscroll。改成非滚动 Column：固定头 + 居中「还没有XXX」。

**Acceptance criteria:**
- [ ] 空联系人名片夹详情不能把空列表滑来滑去
- [ ] 空平台「我的名片」同样
- [ ] 有数据时仍用 LazyColumn

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：空名片夹详情、未加平台的社交页

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/card/CollectionDetailPage.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/social/SocialPage.kt`

**Estimated scope:** S

---

### Task T49: 改密禁止并发提交（SET7）

**Description:** `changePassword` 无 loading 闸。按钮 disabled 了，IME Done 仍会再发。后返回的失败会盖掉先返回的成功。入口先 `if (loading) return`，KeyboardActions 同样守卫。

**Acceptance criteria:**
- [ ] loading 时第二次 `changePassword` 是 no-op
- [ ] 成功不会被后到的失败覆盖

**Verification:**
- [ ] 扩展 `ChangePassword` 相关测若存在，否则编译 + 手动连点/IME Done
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/settings/ChangePasswordViewModel.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/settings/ChangePasswordPage.kt`

**Estimated scope:** XS

---

### Task T50: 引导 bootstrap 与资料写入不要互相覆盖（SET10）

**Description:** 登录后 `bootstrapPostLogin` 后台 pull/mergeProfile，同时 Step2 已可 `saveUserProfile`。用户改的昵称/头像可能被 merge 盖掉。merge 不得覆盖用户刚写的本地字段，或 Step2 保存走 VM mutex 与 bootstrap 串行。

**Acceptance criteria:**
- [ ] 登录后立刻改昵称再等同步结束，本地昵称仍是用户输入
- [ ] 仍不阻塞 onNext 翻页（可后台，但写入要有顺序）

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：注册/登录后马上改昵称

**Dependencies:** T44c（资料写入应收进 VM 后再做竞态）

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/setupguide/SetupGuideViewModel.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/setupguide/SetupStepProfile.kt`

**Estimated scope:** S

---

### Task T51: 日志页禁止在 IO 线程写 Compose State（SET11）

**Description:** `withContext(IO) { logText = collectLogcat() }` 跨线程写 snapshot state。先在 IO 收集字符串，回 Main 再赋值。滚动也不得在 IO 调 `scrollTo`。

**Acceptance criteria:**
- [ ] Compose State 只在主线程赋值
- [ ] 打开日志页不抛线程异常

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：打开软件日志

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/settings/LogViewerPage.kt`

**Estimated scope:** XS

---

### Task T52: 标签管理 Refresh 与批量删除反馈（SET12 / SET13）

**Description:** `Refresh -> Unit`，错误态点重试无效。批量删除 forEach catch 后仍提示「已删除 N 个」。Refresh 应重新订阅/再拉 tags；批量删除按成功条数提示，失败要 Log.e + 可见错误。

**Acceptance criteria:**
- [ ] Error 态点重试会再加载
- [ ] 部分失败不显示全部成功

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：标签管理（能造失败更好）

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/settings/TagManagerSettingsViewModel.kt`

**Estimated scope:** S

---

### Task T53: API Key 不要按键落盘；密码不要 saveable（SET15 / SET16）

**Description:** NFC 设置里 API Key 每个字符 `saveApiKey`。改密页三个密码 `rememberSaveable`。Key 改为失焦/确认时写盘（仍是现有明文 SP，本任务不引入加密）。密码改 `remember`，离开页丢弃。

**Acceptance criteria:**
- [ ] 输入一半 API Key 离开未确认，磁盘不是半截 key（或至少不是每个字符都写）
- [ ] 旋转屏幕后改密页密码框为空

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：NFC 输入 key；改密页旋转

**Dependencies:** None

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/settings/NfcSettingsPage.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/settings/ChangePasswordPage.kt`

**Estimated scope:** S

---

### Checkpoint 4

- [x] 本阶段单测绿；`compileDebugKotlin` 绿
- [x] 问用户是否 commit（已提交 `556ac1a`，单 commit）

**Phase 4 实施备注（2026-09-03）**：
- 31 个文件，520 insertions / 704 deletions（含注释精简）。
- **code-review 发现并修复**：SecondaryApis.tag_generate 吞异常改 Log.e；AuthViewModel.switchToRegister 未清 emailCaptchaBoundTo；SaveScannedContactUseCase 伪事务（repo 内 withContext(IO) 打破 withTransaction 边界）撤回。
- **code-simplification**：ContactDetailViewModel 新增 deleteFieldAndReload / updateFieldAndReload / addOrUpdatePlatformAndReload 便捷方法，页面不再自行 scope.launch 编排 suspend+reload。
- **security-and-hardening 扫描**：无 body!!、无 e.printStackTrace()、无吞异常、无 token 泄漏。
- **未覆盖的任务（移交 Phase 5 或单独 ticket）**：T37（扫码保存 await）、T40（头像 Bitmap 回收）、T43（扫描器/名片夹 Dialog Pattern A）、T44c（设置/引导 VM 收口）、T47（AvatarPreviewDialog Pattern A）、T48（空列表禁止 LazyColumn 空滚）、T50（引导 bootstrap 竞态）。这些任务需要更大范围的文件改动，与 Phase 5 结构拆分合并执行更合适。

---

## Phase 5 — 结构拆分 + 死代码（纯搬运）

禁止改行为。每拆一个文件编译一次。

### Task T30: AppDatabase 迁移 SQL 外置

**Description:** 把 Migration 对象抽到 `data/migrations/`（单文件或按版本）。`AppDatabase` 只保留 `@Database`、DAO、builder。

**Acceptance criteria:**
- [ ] `AppDatabase.kt` 降到约 200 行量级
- [ ] 迁移链与 version 不变

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** Checkpoint 4

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/data/AppDatabase.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/data/migrations/Migrations.kt`（新建）

**Estimated scope:** M（行多但是搬运）

---

### Task T31: PersonPage 拆字母索引与对话框

**Description:** 字母索引独立 Composable；dialog 移出主文件。不改列表/多选/搜索逻辑。

**Acceptance criteria:**
- [ ] `PersonPage.kt` 明显低于 800 行
- [ ] 行为不变

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** T24a 已合入（避免拆的时候还在改 remember key）

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/person/PersonPage.kt`
- 同包新建 Components/Dialogs 文件

**Estimated scope:** M

---

### Task T32: AuthScreens 按 Login/Register/Forgot 拆

**Description:** 按 screen 拆文件，共享 FieldLabel 收到 components（可与 T34 D6 一起抽）。

**Acceptance criteria:**
- [ ] 无单文件近 1000 行的 Auth UI
- [ ] 登录/注册/忘记密码行为不变

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.pages.auth.AuthViewModelTest"`

**Dependencies:** T25 已合入

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/auth/AuthScreens.kt`

**Estimated scope:** M

---

### Task T33: ContactDetailPage 按 section 拆

**Description:** 头部/字段/平台区拆可组合子函数或文件。不改 T23 的写路径语义。

**Acceptance criteria:**
- [ ] 主文件低于 800 行
- [ ] 行为不变

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** T23 已合入

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/person/contact/ContactDetailPage.kt`

**Estimated scope:** M

---

### Task T34: 死字段、过时注释、重复组件（D1–D3 / D6）

**Description:** 删 FieldMigrationConfig 过时注释；删无人读的 `lastSyncedAt`、只写不读的 `updatedAt`（确认无 UI 读取）。FieldLabel/ChoiceChip/PreviewRow 抽到 `ui/components`。

**Acceptance criteria:**
- [ ] 全库无 `lastSyncedAt` 读取方后再删
- [ ] 三组重复组件只留一份
- [ ] 不误删仍在用的 API

**Verification:**
- [ ] grep 无残留误用
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** Phase 2 已用新 Outbox（D3 若仍被 cursor 写入则随 T16 一起删）

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/data/AppDatabase.kt` 或 migrations 注释
- `app/src/main/kotlin/top/mcxiafeng/badger/data/repository/SyncStatusRepository.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/data/cache/entity/SyncCursorEntity.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/ui/components/`

**Estimated scope:** M

---

### Task T34b: 未引用符号与未使用 UI 变体（D7–D10 / D13–D18）

**Description:** 全库标识符对引用后确认零调用：`MergeConflictDialog` 整文件、`AiOcrPrivacyDialog`、`mergePlatformEntries`、`filteredFieldValues`（若 T27 未删）、`LinkUpdateResult.SKIPPED`、`isSetupGuideCompleted`（只写不读）、`importFromJson`/`previewImport`、`longOrNull`、`ModeItem`、未使用的 BadgerEmptyState/Error/Loading/ListItem/Elevation/PlatformColors/TagColors/`rememberCombinedBackdrop`，以及 **A3 评审确认的整文件零外部引用**：`BadgerBottomSheet.kt`、`BadgerErrorState.kt`、`BadgerLoadingState.kt`、`BadgerSectionCard.kt`（SectionCard 的 2 处引用均在同文件内）。每删一组 grep 一次。不删 `AppAndroidPreview`（Studio Preview）。`ParseQrCodeUseCase` 走 T35，ScannerViewModel 死 API 走 T38。

**Acceptance criteria:**
- [ ] 上列符号主代码+测试均为 0 引用后再删
- [ ] 四个零引用组件文件整文件删除
- [ ] 正在用的 `BadgerEmptyStateSimple/Compact` 保留
- [ ] `compileDebugKotlin` 绿

**Verification:**
- [ ] grep 每个符号
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** T38 / T27 已合更好（避免和扫码保存改动打架）；可在其后单独 commit

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/MergeConflictDialog.kt`（删）
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScannerSubDialogs.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScannerSaver.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/ui/components/BadgerBottomSheet.kt`（删）
- `app/src/main/kotlin/top/mcxiafeng/badger/ui/components/BadgerErrorState.kt`（删）
- `app/src/main/kotlin/top/mcxiafeng/badger/ui/components/BadgerLoadingState.kt`（删）
- `app/src/main/kotlin/top/mcxiafeng/badger/ui/components/BadgerSectionCard.kt`（删）
- `app/src/main/kotlin/top/mcxiafeng/badger/data/CollectionExporter.kt`

**Estimated scope:** M

---

### Task T35: 扫码合并只留一条路径（S5）

**Description:** `ScannerPage` 走 `mergeFieldsToContact`（用户冲突选择），`ScannerViewModel` 走 `MergeContactUseCase`（只补空）。确认调用链后只留一条：要么 UseCase 接管 KEEP/REPLACE/APPEND，要么 VM 不再调 UseCase。`ParseQrCodeUseCase`（D12）仅被从未从页面调用的 `onQrCodeDetected` 使用，确认后从 Koin 删除或接到唯一解析器。

**Acceptance criteria:**
- [ ] 合并只走一条实现
- [ ] 冲突选择语义不丢
- [ ] 死 UseCase 从 Koin 删除

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：扫码合并冲突选 KEEP/REPLACE

**Dependencies:** T27、T38（写路径进 VM 后再删死 UseCase，避免两套合并同时改）。**T35 与 T54 合并执行**：确认保留哪条合并路径后，落进 ContactWriter，本任务不再单独产生代码。

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScannerPage.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScannerViewModel.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/domain/MergeContactUseCase.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/di/KoinModules.kt`

**Estimated scope:** M

---

### Task T54: ContactWriter 深模块（A1）

**Description:** 「保存/合并/附加联系人」目前散在 4 处编排：`ScannerSaver.saveScannedContact` / `mergeFieldsToContact` / `attachToExistingContact` + `SaveScannedContactUseCase` / `MergeContactUseCase`，每处手动协调 Contact+Field+Collection(+Tag) 仓库。收拢为单一 **ContactWriter** 模块：insert / merge（KEEP/REPLACE/APPEND）/ attach 三入口，内部用 Room `withTransaction` 包住（吸收 S4），字段 key 统一 `stripFieldKeySuffix`（吸收 S3），同步提交走 SyncEngine（吸收 C18/QAuxv 路径的基础）。两个 UseCase 删除；ScannerViewModel 只调 ContactWriter。

**Acceptance criteria:**
- [ ] 保存/合并/附加只有一条实现路径（ScannerSaver 编排函数删除）
- [ ] 写入在单事务内：中途失败不留下无字段的孤儿联系人
- [ ] `qq_1` 后缀 key 在 Writer 内 strip 后写入
- [ ] SaveScannedContactUseCase / MergeContactUseCase 从 Koin 删除
- [ ] 合并的 KEEP/REPLACE/APPEND 语义不变
- [ ] 三入口返回统一 `CommitResult`（或其扩展），不新造结果类型

**Verification:**
- [ ] 新增 `ContactWriterTest`（in-memory Room）：三条入口各一例 + 中途失败回滚一例
- [ ] `./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.<Writer 所在包>.ContactWriterTest"`
- [ ] 手动：扫码新建、扫码合并冲突、附加到已有联系人

**Dependencies:** T38（写路径先进 VM）；T35 并入本任务执行（不再单独留）；T27 的 S3/S4 若尚未修，随本任务一并闭环

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/data/repository/ContactWriter.kt`（新建）
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScannerSaver.kt`（编排函数删除）
- `app/src/main/kotlin/top/mcxiafeng/badger/domain/SaveScannedContactUseCase.kt`（删）
- `app/src/main/kotlin/top/mcxiafeng/badger/domain/MergeContactUseCase.kt`（删）
- `app/src/main/kotlin/top/mcxiafeng/badger/di/KoinModules.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/scanner/ScannerViewModel.kt`

**Estimated scope:** M

---

### Task T31b: CollectionDetailPage 拆分（§3.9 S-C）

**Description:** 728 行超过 AGENTS.md 700 红线，主 Composable 单函数 ~572 行。按 section 拆：hero 头部、联系人列表、多选工具栏、对话框宿主。纯搬运。

**Acceptance criteria:**
- [ ] 主文件低于 700 行
- [ ] 行为不变（多选/导入/成员管理）

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** T06c、T48 已合入（这俩都在改这个文件）

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/card/CollectionDetailPage.kt`
- 同包新建 Components/Dialogs 文件

**Estimated scope:** M

---

### Task T31c: TagManagerSettingsPage 拆分（§3.9 S-C）

**Description:** 744 行超红线；`TagManagerSettingsPage` ~272 行、`TagManagerSuccessBody` ~155 行、`TagManagerListRow` ~91 行。按列表/成功态/行组件拆文件。纯搬运。

**Acceptance criteria:**
- [ ] 单文件低于 700 行
- [ ] 标签管理行为不变

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`

**Dependencies:** T52 已合入（同改 VM）

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/pages/settings/TagManagerSettingsPage.kt`
- 同包新建组件文件

**Estimated scope:** M

---

### Task T55: App.kt 长函数拆解（§3.9 S-C）

**Description:** 文件 549 行未超红线，但三个函数超 50 行红线：`safeNavigateBack` ~182 行、`MainTabsContent` ~132 行、`resolveDeepLink` ~97 行。抽成同文件或相邻文件的子函数，纯搬运。

**Acceptance criteria:**
- [ ] 无超 50 行的函数
- [ ] 导航/DeepLink/Tab 行为不变

**Verification:**
- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] 手动：四个 Tab 切换、二级页返回、deep link 直达

**Dependencies:** Checkpoint 4 之后均可

**Files likely touched:**
- `app/src/main/kotlin/top/mcxiafeng/badger/App.kt`
- `app/src/main/kotlin/top/mcxiafeng/badger/AppMainTabs.kt`（若 MainTabsContent 回迁）

**Estimated scope:** S

---

### Task T36: 更新 architecture.md 与计划状态

**Description:** `docs/architecture.md` 仍写「Phase 0–5 已完成 / Room v14 / BackupApi」。改成与仓库一致：v16 Outbox、BackupApi 已删、写路径本地权威。`docs/refactor-plan.md` 状态改为「实施中/已完成」对应阶段。

**Acceptance criteria:**
- [ ] 文档不再声称 BackupApi 存在
- [ ] Room 版本与迁移链和代码一致
- [ ] 下一轮 Agent 不会被过时抬头误导

**Verification:**
- [ ] 人工读一遍与 `AppDatabase` version 对照

**Dependencies:** T10（至少 version 已升）；建议整个方案完成后做终稿，阶段中可先改抬头「进行中」

**Files likely touched:**
- `docs/architecture.md`
- `docs/refactor-plan.md`

**Estimated scope:** S

---

### Checkpoint 5

- [ ] `./gradlew :app:compileDebugKotlin` 绿
- [ ] `./gradlew :app:testDebugUnitTest`（排除已知 `NotificationApiTest` 红）
- [ ] 问用户是否 commit Phase 5
- [ ] 全部完成后问用户是否打 Dev/Beta changelog（AGENTS.md 版本发布约定）
