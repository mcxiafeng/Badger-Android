# Implementation Plan: 本地权威写路径 + 通用 Outbox + 双向 SyncEngine

规格（根因、目标架构、finding 全表、开放问题裁决）：[docs/refactor-plan.md](../docs/refactor-plan.md)

任务清单（可抓取、带验收）：[tasks/todo.md](./todo.md)

> 本文件只回答「按什么顺序做、每步多小、在哪停」。不改生产代码。
>
> **进度（2026-09-03）**：Phase 0–4 已完成并验证：
> `2cc4599 fix(data): Phase 0 止血` / `418f19d refactor(sync): Phase 1 Identity 分层` / `a96f1f3 refactor(sync): Phase 2 通用 Outbox` / `a9104b5 refactor(sync): Phase 3 CreateOnPush + 双向 SyncEngine` / `556ac1a refactor(phase4): UI 契约 + 网络装甲 + 设置正确性`。
> **Phase 5（T30–T55 结构拆分 + 死代码）下一步。**

## Overview

把「直推 + 失败兜底 + 单向 pull」换成 **本地先提交 + 通用 Outbox（字段级 merge）+ SyncEngine（先 push 再 pull）**。Person / Tag / Collection 走同一套幂等与重放。UI 投影永远不能覆盖 identity。保持单 gradle module。

先做 Phase 0 止血（正在发生的数据损坏），再分层重构。每一阶段独立可编译、可回滚；Phase 之间不交叉改文件（Koin 接线除外）。

## Architecture Decisions

- **写路径本地权威，pull 仍以服务端为冲突权威。** 用户永远先看到自己的编辑；同字段以后到的 sync change 覆盖本地。
- **投影 → 实体必须 rebase identity**（`serverId` / `personMembers` / `isLocalOnly` / `createTime`）。不能靠「记得 copy」。
- **Outbox 按 `(entityKind, localId, op)` 合并 PATCH**，禁止 `serverId PRIMARY KEY + CONFLICT_REPLACE`。
- **Tag/Collection POST 带客户端 uuid（选项 C）**：忽略则用返回 uuid 覆盖；400 则去掉 uuid 再 POST 一次。服务端真正幂等另开 ticket。
- **Field 值不上云。** 平台变更继续走 Person `profile` 全量 PUT。
- **v16 不搬 `pending_person_updates`。** 该表从未发版；迁移 = CREATE outbox + DROP。
- **Phase 0 单独一个 commit**，不和架构重构混。后续每阶段 1–3 个 commit。改完代码不自动 commit，问用户。
- **单测扩现有文件**，不新建测试基建。命令：
  - 编译：`./gradlew :app:compileDebugKotlin`
  - 子集：`./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.<包>.<类>"`
  - 全量非 UI：`./gradlew :app:testDebugUnitTest`（已知 `NotificationApiTest` 2 条红，排除干扰）

## Task List

任务详情、验收、文件列表在 `tasks/todo.md`。这里只保留顺序和依赖。

### Phase 0 — 止血（1 个 commit）

- [x] T01 F1 `upsertTag` 捕获 rowId
- [x] T02 F2 查名查询补 `isDeleted = 0`
- [x] T03 F3 `updateCollection` rebase identity
- [x] T04 F4 outbox enqueue 字段级 merge
- [x] T05 F5 `code` 类型保护
- [x] T06a `executeImport` 动作表改 index/rowId
- [x] T06b `ImportConflictDialog` 状态与 LazyColumn key
- [x] T06c CardPage / CollectionDetailPage 进度与 checkbox 接线

### Checkpoint 0

- [x] `compileDebugKotlin` 绿
- [x] T01–T06 对应单测绿
- [x] 问用户是否 commit Phase 0；**不进入 Phase 1 直到合入**（已问，用户选拆两个 commit；Phase 0 = `2cc4599`）


### Phase 1 — Identity 分层

- [x] T07 `RemoteIdentity` / `EntityKind`
- [x] T08 `IdentityRebase` 接入 Collection/Tag 写路径
- [x] T09 `hardDeleteContact` 删头像文件

### Checkpoint 1

- [x] 投影 round-trip 后 `serverId` 不变的单测绿
- [x] 问用户是否 commit（Phase 1 = `418f19d`）

### Phase 2 — 通用 Outbox

- [x] T10 Room v16 `outbox` 表 + 迁移（CREATE + DROP，不搬数据）
- [x] T11 `OutboxStore` 字段级 merge + 原子 attempts
- [x] T12a Outbox Worker + Scheduler
- [x] T12b Person PUT 改为只入队
- [x] T13 删除 `PendingPersonUpdateStore` 旧链路

### Checkpoint 2

- [x] 离线改名再改 bio，重放 payload 保留 name（`OutboxStoreTest.patchMerge_partialPayload_keepsQueuedName` + `OutboxWorkerTest.replay_mergedPartialPuts_keepsQueuedName` 端到端验证 HTTP body）
- [x] `recordFailure` 并发 attempts 不丢（`OutboxStoreTest.recordFailure_incrementsAtomicallyUnderContention`，12 线程）
- [x] 问用户是否 commit（`a96f1f3`，单 commit）

**Phase 2 实施备注（2026-09-02）**：
- 规格验收「离线建 Tag 再改名，重连后服务端有该 Tag（R2 闭环）」**移交 Phase 3**：Phase 2 按任务清单只接 PATCH/MEMBER/DELETE 入队，CREATE 仍直推（T14 CreateOnPush + T16c 启动扫描承接 R2/R4），非静默裁剪。
- `SyncWorkerFactory.kt` 已在早前清理中移除（WorkManager 走默认 factory），T12a 的「Koin / SyncWorkerFactory 指向新类」按现状落为「Worker 在 doWork 时从 Koin GlobalContext 拉依赖」，与现存 `PendingPersonUpdateWorker` 同模式。
- 部分唯一索引以 `mergeKey` 合成列等价实现（规格 §3.1 已补实现注记）。
- **开放问题（待登记）**：poison 行无出口——永久 4xx（如契约错）与未知结局同路径无限退避重试（640s 上限空转）。规格只禁止对超时/断连记 FAILED_PERMANENT，未定义永久失败出口；不阻塞 Phase 3，建议随 T16a 三结局细化一并裁决。

### Phase 3 — CreateOnPush + 双向 Sync

- [x] T14 统一 `CreateOnPush`（Person/Tag/Collection，选项 C）
- [x] T15 QAuxv `insertOne` 改走 `insertContact`
- [x] T16a PushLoop
- [x] T16b PullLoop 搬家 + mutex
- [x] T16c 启动扫描 isLocalOnly 补 CREATE
- [x] T17 `retryAll` 改 `syncOnce`；搬走并删除 `SyncRepository`

### Checkpoint 3

- [x] 离线建联系人/名片夹，不编辑，点「立即同步」，能推上去（`SyncEngineTest.syncOnce_backfillsLocalOnlyRows_andPushesThemUp` 端到端验证三种实体全推上去）
- [x] 服务端 ADD 带成员的新 Tag，本地 cross-ref `tagId ≠ 0`（F1 由 T01 在 Phase 0 固化，SyncEngine.upsertTag 原样保留 rowId 回填）
- [x] 问用户是否 commit Phase 3（已提交 `a9104b5`）

**Phase 3 实施备注（2026-09-03）**：
- `SyncEngine.kt` 新建 ~730 行，替代原 `SyncRepository.kt` ~477 行（净增 ~250 行含 CreateOnPush + PushLoop + backfill）。
- `ContactCacheEntity.id` 原缺 `autoGenerate = true`（Tag/Collection 均有），id=0 字面插入 + REPLACE 会吞行；补 `autoGenerate` 后 Room v17 迁移重建 contacts_cache 表（数据不丢）。
- `OutboxStore.backfillAfterCreate` 的 MEMBER payload 回填需要 SQL `LIKE` 带 `%` 通配符，否则 exact match 找不到嵌套在 payload 里的 uuid。
- `OutboxStore.getReady` 新增 `includeBackoff` 参数：手动「立即同步」传 true（无视退避窗口），WorkManager 触发传 false（尊重退避）。
- `SyncEngine.pushLocked` 在 CREATE 成功后 `break` + 重取批次：CREATE 兑现会回填同实体其它行的 remoteId / MEMBER payload 的 personUuid，内存批次还是旧值。
- **开放问题（待登记）**：poison 行无出口（永久 4xx 如契约错）无限退避重试（640s 上限空转）。不阻塞 Phase 4，建议随 T18 网络装甲一并裁决。

### Phase 4 — UI 契约 + 网络装甲

- [ ] T18 C1+C9 网络失败不登出
- [ ] T19 C2/C4/C5 API 边界装甲
- [ ] T20 C3 时间戳 + C6 OCR 体积
- [ ] T21 C7 `commitMerge` 不销毁 localOnly
- [ ] T22 C8 DeviceId 并发
- [ ] T23 C10/C20/D4 详情页写完再 reload
- [ ] T24a C11 PersonPage 全选 remember key
- [ ] T24b C12/D5 RegionPicker reset + 日志
- [ ] T24c C13 Dashboard 最近联系人本地 id
- [ ] T25 C14/C15 Auth 协程与邮箱绑定
- [ ] T26 C16 导入 JSON 空列表
- [ ] T27 S1/S3/S4 扫码 Mat / 后缀 key / 事务
- [ ] T28 S2 NFC 未开启不得 READY
- [ ] T29 S6 ImportProfile 用平台 key
- [ ] T37 U1 扫码确认等保存完成再关对话框
- [ ] T38 U2 扫码写路径收进 ViewModel，页面不再拿 Repository
- [ ] T39 U3/U4/U5 EXIF 回收、OCR 未配置提示、相册回调线程
- [ ] T40 U6/U7 头像 Bitmap 回收 + 删平台刷新
- [ ] T41 U8 导航 500ms→300ms
- [ ] T42 U9 名片 QR 浅色底对齐 surface
- [ ] T43 U10 扫描器/名片夹 WindowDialog Pattern A
- [ ] T44a ARCH-A AppViewModel 不再公开 Repository
- [ ] T44b ARCH-B 详情/创建/名片 VM 收口
- [ ] T44c ARCH-C 设置/引导/AccountProfile 收口
- [ ] T45 U11/U15 拍照处理中禁止点外部关闭（Photo + Scan）
- [ ] T46 U13 NFC「当前指向」改 BasicComponent
- [ ] T47 U12 AvatarPreviewDialog Pattern A
- [ ] T48 U14 空名片夹详情 / 空社交页禁止 LazyColumn 空滚
- [ ] T49 SET7 改密 in-flight 闸
- [ ] T50 SET10 引导 bootstrap 与资料写入竞态
- [ ] T51 SET11 日志页禁止 IO 线程写 State
- [ ] T52 SET12/13 标签管理 Refresh 与批量删除反馈
- [ ] T53 SET15/16 API Key 不要按键落盘；密码不要 rememberSaveable

### Checkpoint 4

- [x] 本阶段涉及的单测绿；`compileDebugKotlin` 绿
- [x] 问用户是否 commit（已提交 `556ac1a`，单 commit）

### Phase 5 — 结构拆分 + 死代码（纯搬运）

- [ ] T30 `AppDatabase` 迁移 SQL 外置
- [ ] T31 `PersonPage` 拆字母索引/对话框
- [ ] T31b `CollectionDetailPage` 拆分（728 行红线，§3.9 S-C）
- [ ] T31c `TagManagerSettingsPage` 拆分（744 行红线，§3.9 S-C）
- [ ] T32 `AuthScreens` 按 Login/Register/Forgot 拆
- [ ] T33 `ContactDetailPage` 按 section 拆
- [ ] T34 D1–D3/D6 死字段与重复组件
- [ ] T34b D7–D10/D13–D18 未引用符号与未使用 UI 变体（含 A3 四个零引用文件）
- [ ] T35 S5 扫码合并只留一条路径（含 D12 ParseQrCodeUseCase）
- [ ] T54 A1 ContactWriter 深模块（吸收 S4 事务）
- [ ] T55 `App.kt` 长函数拆解（§3.9 S-C）
- [ ] T36 更新 `docs/architecture.md`（现状与目标）

### Checkpoint 5

- [ ] 拆分前后行为不变；非 UI 单测全绿（排除已知 `NotificationApiTest`）
- [ ] T54 的 ContactWriterTest 绿
- [ ] 问用户是否 commit

## Parallelization

| 可并行 | 必须串行 |
|---|---|
| T01–T05（Phase 0 内无互相依赖；T06a→b→c 按契约依赖串行） | Phase 0 合入 → Phase 1 |
| T18–T22 与 T23–T26 与 T37–T43（不同子系统） | T10 → T11 → T12 → T13；T37 先于或并入 T38 |
| T30–T33 文件拆分（不同文件） | T14 → T16 → T17（SyncEngine 吃 CreateOnPush） |

同一工作树不要并行改 `AppDatabase` / `KoinModules` / `ServerApi`。

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Tag/Collection POST 因未知字段 400 | Phase 3 创建失败 | CreateOnPush 对 400 去 uuid 重试一次 |
| Phase 2 直推与 Outbox 双路径 | 重复 PUT | T12 提交瞬间切：Repository 不再直调写 API |
| 拆 >800 行文件误改行为 | 回归 | T30–T33 纯搬运，禁止改逻辑；每拆一个编译一次 |
| `NotificationApiTest` 2 条遗留红 | 干扰全量信号 | 全量跑时排除；不在本方案修 |
| F6/F7 只改 UI key | 同名联系人仍共享动作 | T06 必须改 `executeImport` 签名，禁止只改 LazyColumn |

## Open Questions

规格里的 3 个开放问题已裁决（C / Field 不上云 / 旁路表无生产数据）。仍需用户开口的只有：

1. **是否现在开始 T01？** 本轮只出任务，不动刀。
2. **Phase 3 是否坚持等服务端 A？** 默认 C。若改 A，T14/T16 暂停，其余阶段仍可做。

## Assumptions

1. 规格以 `docs/refactor-plan.md` 为准，本文件不重复根因长文。
2. 不拆 gradle 多 module、不改 UI 视觉、不引入新网络栈。
3. 每个任务完成后跑该任务 Verification，不默认跑全量测试。
4. 不自动 git commit。
