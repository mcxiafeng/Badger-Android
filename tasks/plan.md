# Implementation Plan: 架构对齐重构（API 迁移后）

## Overview

依据 `docs/architecture-refactor-plan.md`（2026-08-27 拍板），在 API 迁移 Phase 1–5 已完成的基础上，清理残留的「V1 Contact 语言」与「V2 Person/Profile 语言」并存问题，让服务端 `/api` 响应结构成为数据层唯一契约。

**范围**：把 V1 表（`contact_fields` / `custom_fields` / `contact_field_values` / `scan_results` / `contact_platforms` / `pending_uploads`）按 expand/contract 退役；补全 `ProfileDto` 字段持久化；清理 `ContactMapper` 恒等 no-op；统一命名向 Person* 靠拢。

**不在范围**：API 迁移 Phase 1–5（已完成，见 `docs/api-handover-migration-plan.md`）；平台清单接 UI（`/api/resolve/platforms` 服务端清单接入「添加平台」网格，`PlatformManifestRepository` + `StateFlow` 缓存 + `merge` 纯函数，已完成）；Compose UX 重写（仅仓库边界适配）；DI 迁移（§14.2 Hilt→Koin 已完成）。

## Skills 映射（plan §0.3）

> 每个 Task 标注实施时应调用哪条 skill 的 checklist 验收。Phase 级别标注该 Phase 主验收 skill。

| Skill | 用途 | 适用 Task |
|---|---|---|
| `planning-and-task-breakdown` | 任务拆解、验收准则、checkpoint | 全局（本计划生成时已调用） |
| `api-and-interface-design` | 服务端契约作为数据层标准；DTO 字段命名 / 错误语义 / 边界校验 | #5、#10、#11、#12 |
| `deprecation-and-migration` | V1 表与 V1 DAO 的 expand/contract 退役；strangler 清理 | Phase 3 主 skill（#14–#17）、Phase 4 主 skill（#19–#22） |
| `claude-android-skill-main` | NowInAndroid 架构最佳实践（offline-first / unidirectional / Flow） | #10、#11、#21 |
| `diegosouzapw-awesome-omni-skill-architecture` | MVVM + Clean Architecture + Repository + DI 分层 | #10、#11、#22 |
| `android-architecture` | 三层分层 + Koin 模块化边界 | #11、#22、#26 |
| `android-data-layer` | Repository 模式 + Room + Retrofit + offline-first 同步 | #9、#11、#14–#17、#21 |
| `code-simplification` | 清理 `ContactMapper` 恒等 no-op / 重复转换 / 死字段 | #6、#22 |
| `code-review-and-quality` | 五轴评审（correctness / readability / architecture / security / performance） | 每 Phase checkpoint 验收 |

### Phase → Skill 主责

| Phase | 主验收 skill | 说明 |
|---|---|---|
| Phase 0 基线冻结 | `planning-and-task-breakdown` | 基线记录 + @Deprecated 标记 |
| Phase 1 语义冻结与文档修正 | `code-simplification` + `api-and-interface-design` | 文档修正 + 恒等 no-op 清理 |
| Phase 2 Profile 字段完备化 | `android-data-layer` + `api-and-interface-design` | Room 加列 + DTO 全映射 + sync 写入 |
| Phase 3 V1 表退役 — 自定义字段值 | `deprecation-and-migration` + `android-data-layer` | expand/contract 四步走 |
| Phase 4 V1 表退役 — 平台表+队列表 | `deprecation-and-migration` + `claude-android-skill-main` | 退役 + 队列语义清理 |
| Phase 5 命名整理与文档收尾 | `code-review-and-quality` + `android-architecture` | 命名规则 + 架构文档 |

## Architecture Decisions（已拍板）

| 决策 | 结论 | 理由 |
|---|---|---|
| D1: `platformsJson` 形状 | 保留 `Map<String,PlatformEntry>`，修正 KDoc | UI 层大量消费 `PlatformEntry`；改形状成本 > 收益 |
| D2: `ContactCacheEntity.id` | 保留本地 Long 主键，uuid 作 `serverId` 唯一索引 | 本地 `id` 是 Room PagingSource / 外键的锚点 |
| D3: `self` 持久化 | 加 `self INTEGER` nullable 列 | 避免离线误删 selfPerson |
| D4: `color` vs `colorHash` | 暂保留双写 | 本地 `color: Long` 是 UI 渲染直接消费 |
| D5: V1 表退役顺序 | `contact_platforms` → `scan_results` → `contact_field_values` → `custom_fields` → `contact_fields` → `pending_uploads` | 按依赖度从低到高 |
| D6: 命名整理范围 | 仅新代码强制 Person* + 随 PR 顺手改 | 全量改名 ROI 低 |
| D7: Profile 新字段 | 新建 `person_profile_cache` 子表（不膨胀 `contacts_cache`） | 避免主表继续膨胀 |

## 依赖图

```
Checkpoint 0（基线冻结）
        │
        ├── Phase 1 语义冻结与文档修正（低风险，可先行）
        │       │
        │       └── Phase 2 Profile 字段完备化（中风险）
        │               │
        │               └── Phase 3 V1 表退役 — 自定义字段值（高风险，expand/contract）
        │                       │
        │                       └── Phase 4 V1 表退役 — 平台表+队列表
        │                               │
        │                               └── Phase 5 命名整理与文档收尾
        └──（Phase 1 的 Task 1.4 ScannerSaver 注释无依赖，可并行）
```

## 关键现状事实（2026-08-27 勘察）

- **网络层已全 V2**：`PersonDto` / `ProfileDto` / `TagDto` / `CollectionDto` / `AuthResponse` / `BackupSummary` / `SyncPage` 是数据层主键。
- **持久层仍混 V1**：`ContactCacheEntity` / `UserProfileCacheEntity` / `TagCacheEntity` 仍在 V1 语言上运作。
- **ContactMapper** 是当前「双语词典」，但含 5 个恒等 no-op（`toContact()` / `toTag()` / `toUserProfile()` / `toCardCollection()` / `toCrossRef()`）。
- **V1 表仍注册在 Room v7**：`contact_fields` / `custom_fields` / `contact_field_values` / `scan_results` / `contact_platforms` + `pending_uploads` / `operation_history`。
- **9 个冲突点**：C1 `platformsJson` 语义漂移、C2 `UserProfileCacheEntity` 字段残缺、C3 `PersonDto.self` 未持久化、C4 `FieldRepositoryImpl` 仍用 V1 DAO、C5 `SyncStatusRepositoryImpl` 仍数 `pending_uploads`、C6 `TagCacheEntity` 双写 color、C7 `ContactMapper` 恒等 no-op、C8 UI 边界 Contact vs Person 命名混用、C9 V1 表仍注册在 Room。

## Task List

### Phase 0: 基线冻结（不写代码）
> 主验收 skill：`planning-and-task-breakdown`

- [x] Task #1: 记录基线 commit + `compileDebugKotlin` + 全量单测通过数（历史 356/356）
- [x] Task #2: 对 `ContactMapper` 恒等 no-op / `SyncStatusRepository.snapshot` 队列语义 / `FieldRepositoryImpl` V1 DAO 使用加 `@Deprecated` KDoc

### Checkpoint 0
- [x] 基线 commit 已记录（`5f4dd45` @ `dev`）
- [x] 全量单测绿（356 tests，1 pre-existing flaky `AccountSettingsViewModelTest` 单类重跑即绿）
- [x] 无新增编译 warning（3 条 pre-existing KSP/Room warning 未变）

### Phase 1: 语义冻结与文档修正（低风险）
> 主验收 skill：`code-simplification` + `api-and-interface-design`

- [x] Task #4: 修正 `platformsJson` KDoc（`ContactCacheEntity` / `UserProfileCacheEntity`） → `api-and-interface-design`
- [x] Task #5: 补全 `ProfileDto` 字段映射文档（`ContactMapper` 顶部 KDoc） → `api-and-interface-design`
- [x] Task #6: 清理 `ContactMapper` 恒等 no-op（删 5 函数 + 调用点直传） → `code-simplification`
- [x] Task #7: 修正 `ScannerSaver` 注释（contact_platforms → contact_platforms_cache V2，可并行） → `code-simplification`

### Checkpoint 1
- [x] `compileDebugKotlin` 绿
- [x] 全量单测绿（356 tests，1 pre-existing flaky `CloudBackupViewModelTest` 单类重跑即绿）
- [x] `platformsJson` 语义文档与实现一致
- [x] `ContactMapper` 无恒等 no-op

### Phase 2: Profile 字段完备化（中风险）
> 主验收 skill：`android-data-layer` + `api-and-interface-design`

- [ ] Task #9: Room v8 加列（`user_profile_cache` 加 sex/country/region/birthday/backgroundURL/extra，nullable） → `android-data-layer`
- [ ] Task #10: `buildProfileDto` 全映射（`UserProfileRepositoryImpl` 新列 → `ProfileDto`） → `api-and-interface-design` + `claude-android-skill-main`
- [ ] Task #11 (2.3a): 新建 `person_profile_cache` 子表（uuid 主键 + 外键到 `contacts_cache.serverId` + sex/country/region/birthday/backgroundURL/extra nullable） → `android-data-layer` + `diegosouzapw-awesome-omni-skill-architecture`
- [ ] Task #29 (2.3b): sync 重放写入新列（`SyncRepository` 处理 Person UPDATE 时写入 `person_profile_cache`） → `android-data-layer` + `diegosouzapw-awesome-omni-skill-architecture`
- [ ] Task #12: 持久化 `PersonDto.self`（`contacts_cache` 加 `self INTEGER`，可并行） → `api-and-interface-design`

### Checkpoint 2
- [ ] `compileDebugKotlin` 绿
- [ ] 全量单测绿
- [ ] `buildProfileDto` 字段无丢失
- [ ] `PersonDto.self` 持久化
- [ ] `person_profile_cache` 表建立

### Phase 3: V1 表退役 — 自定义字段值（高风险，expand/contract）
> 主验收 skill：`deprecation-and-migration` + `android-data-layer`

- [ ] Task #14: 新建 `contact_field_values_cache`（expand，Room v9/v10） → `deprecation-and-migration` + `android-data-layer`
- [ ] Task #15: 双写（`FieldRepositoryImpl` 写同时写 V1 + 新表，读仍走 V1） → `deprecation-and-migration`
- [ ] Task #16: 切读 + 回滚门控（feature flag 关→V1 读，开→新表读） → `deprecation-and-migration`
- [ ] Task #28 (3.4a): flag 开启观察期（至少 1 个 commit 周期，监控无回归） → `deprecation-and-migration`
- [ ] Task #17: 删 V1 表（contract，Room v11 删 `contact_field_values` / `custom_fields` / `contact_fields`，Task #28 观察期后） → `deprecation-and-migration` + `android-data-layer`

### Checkpoint 3
- [ ] `compileDebugKotlin` 绿
- [ ] 全量单测绿
- [ ] V1 字段表已删
- [ ] `FieldRepositoryImpl` 零 V1 DAO 引用

### Phase 4: V1 表退役 — 平台表 + 队列表
> 主验收 skill：`deprecation-and-migration` + `claude-android-skill-main`

- [ ] Task #19: 退役 `contact_platforms`（V1，Room v12） → `deprecation-and-migration`
- [ ] Task #20: 退役 `scan_results`（可并行） → `deprecation-and-migration`
- [ ] Task #21: 退役 `pending_uploads` + `SyncStatusRepository.snapshot()` 改读 `sync_cursor` + `isLocalOnly` 计数 → `claude-android-skill-main` + `android-data-layer`
- [ ] Task #22: 清理 `ContactPlatform` V1 包装类（依赖 Task #19） → `code-simplification` + `diegosouzapw-awesome-omni-skill-architecture`

### Checkpoint 4
- [ ] `compileDebugKotlin` 绿
- [ ] 全量单测绿
- [ ] V1 表（`contact_platforms` / `scan_results` / `pending_uploads` / 字段表）全部移除
- [ ] `SyncStatusRepository` 无队列语义

### Phase 5: 命名整理与文档收尾（低风险）
> 主验收 skill：`code-review-and-quality` + `android-architecture`

- [ ] Task #24: `CLAUDE.md` 加 Person* 命名规则 → `code-review-and-quality`
- [ ] Task #25: 随 PR 顺手改名 `Contact*` → `Person*`（持续） → `code-review-and-quality`
- [ ] Task #26: `docs/architecture.md` 写「Badger 数据层架构」 → `android-architecture`

### Checkpoint 5（终态）
- [ ] `compileDebugKotlin` 绿
- [ ] 全量单测绿
- [ ] V1 表零残留
- [ ] `ContactMapper` 无恒等 no-op、无 V1 包装
- [ ] `CLAUDE.md` 有 Person* 命名规则
- [ ] `docs/architecture.md` 完成

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Room 迁移在用户设备上崩溃 | 高 | 每次 migration 写 `MigrationTest`；expand/contract 分步部署；保留 down 路径 |
| `platformsJson` 语义修正引发 UI 解析失败 | 中 | 仅改 KDoc，不改运行时；grep 确认无代码依赖旧 KDoc |
| Profile 新列导致 `buildProfileDto` 行为变化 | 中 | 新列 nullable；旧代码不读新列；单测覆盖 |
| V1 表退役后历史数据不可读 | 低 | 退役前双写 + 观察期；`pending_uploads` 历史数据已无业务含义 |
| 命名整理引发大规模 merge conflict | 低 | 仅新代码强制 + 随 PR 顺手改；不做一次性全量 rename |
| `self` 列加列后旧 sync 数据缺失 | 低 | nullable；离线时 UI 降级为「允许删除」，服务端返回 400 即可 |

## Open Questions（需人工拍板）

- Q1: `person_profile_cache` 子表方案 vs 直接给 `contacts_cache` 加列？→ 本计划倾向子表（已按子板落地任务）
- Q2: `SyncStatusSnapshot` 在 `pending_uploads` 退役后是否保留 DTO？还是直接删掉「同步状态」页？
- Q3: `color` Long 是否在本计划内彻底退役（只保留 `colorHash`）？→ 本计划暂保留双写
- Q4: Phase 3 的 feature flag 用什么机制？Koin 配置项 / BuildConfig / 服务端远程配置？
- Q5: 命名整理是否接受「随 PR 渐进」而不做一次性 rename？→ 本计划选渐进

## 验证策略

| 阶段 | 验证项 | 命令/方式 |
|---|---|---|
| 每 Task | 编译 | `./gradlew :app:compileDebugKotlin` |
| 每 Task | 全量单测 | `./gradlew :app:testDebugUnitTest --no-daemon` |
| 每 Task | 静态分析 | `code-review-and-quality` 五轴自检 |
| Checkpoint | 回归基线 | 对比 Task #1 的 commit，确认无回退 |
| Phase 2+ | Room 迁移 | `MigrationTest` 覆盖每次 schema 变更 |
| Phase 3+ | 行为守恒 | 双写期对比 V1 / V2 表行数与内容 |

### 历史坑（来自 MEMORY.md）

- **Robolectric native OOM**：偶发，重跑即恢复；不阻塞 merge。
- **CloudBackupViewModelTest flaky**：全量跑偶发绿，单类重跑即绿。
- **gradle daemon OOM**：需 `taskkill` 清残留。
- **全量单测须 `--no-daemon`**：否则 SSL 级联失败。
- **KSP 增量**：已在 `gradle.properties:7-9` 启用（`ksp.incremental=true` + `intermodule=true` + `useClasspathSnapshot=true`）。

## 与既有计划的关系

| 文档 | 关系 |
|---|---|
| `docs/api-handover-migration-plan.md` | 已完成（Phase 1–5）；本计划是其**后续**清理，不重叠 |
| `MEMORY.md` 中 Phase 1–5 记录 | 本计划完成后需追加「架构对齐 Phase 0–5 完成」条目 |
| `§14.2 Hilt→Koin` 备忘 | 已完成；本计划不涉及 DI 迁移 |
| `§15 技术债` | 部分条目已清；本计划清理的是 §15 之外的架构债 |

## 验收总准则（Definition of Done）

1. `compileDebugKotlin` 通过。
2. 全量单测通过（历史基线 356/356，本计划新增单测覆盖新增/改动路径）。
3. V1 表（`contact_fields` / `custom_fields` / `contact_field_values` / `scan_results` / `contact_platforms` / `pending_uploads`）从 `AppDatabase` entities 移除。
4. `ContactMapper` 无恒等 no-op、无 V1 包装类转换。
5. `buildProfileDto` 字段无丢失。
6. `PersonDto.self` 持久化。
7. `SyncStatusRepository` 无队列语义。
8. `CLAUDE.md` 有 Person* 命名规则。
9. `docs/architecture.md` 完成。
