# Badger 架构对齐重构 — Landing Checkpoints

> 依据 `tasks/plan.md` 生成。逐条勾选，完成一条打一条。任务跟踪以 TaskCreate 列表为准（#1–#27）。
> 每个 Task 标注了实施时应调用哪条 skill 验收（详见 plan §Skills 映射）。

## Phase 0 — 基线冻结（不写代码，任务 #1–#3）
> 主验收 skill：`planning-and-task-breakdown`

- [x] Task #1: 记录基线 commit + `compileDebugKotlin` + 全量单测通过数（历史 356/356）
- [x] Task #2: 对 `ContactMapper` 恒等 no-op / `SyncStatusRepository.snapshot` 队列语义 / `FieldRepositoryImpl` V1 DAO 使用加 `@Deprecated` KDoc

## Checkpoint 0
- [x] 基线 commit 已记录（`5f4dd45` @ `dev`）
- [x] 全量单测绿（356 tests，1 pre-existing flaky `AccountSettingsViewModelTest` 单类重跑即绿）
- [x] 无新增编译 warning（3 条 pre-existing KSP/Room warning 未变）

## Phase 1 — 语义冻结与文档修正（低风险，任务 #4–#8）
> 主验收 skill：`code-simplification` + `api-and-interface-design`

- [x] Task #4: 修正 `platformsJson` KDoc（`ContactCacheEntity` / `UserProfileCacheEntity`） → `api-and-interface-design`
- [x] Task #5: 补全 `ProfileDto` 字段映射文档（`ContactMapper` 顶部 KDoc） → `api-and-interface-design`
- [x] Task #6: 清理 `ContactMapper` 恒等 no-op（删 5 函数 + 调用点直传） → `code-simplification`
- [x] Task #7: 修正 `ScannerSaver` 注释（contact_platforms → contact_platforms_cache V2，可并行） → `code-simplification`

## Checkpoint 1
- [x] `compileDebugKotlin` 绿
- [x] 全量单测绿（356 tests，1 pre-existing flaky `CloudBackupViewModelTest` 单类重跑即绿）
- [x] `platformsJson` 语义文档与实现一致
- [x] `ContactMapper` 无恒等 no-op

## Phase 2 — Profile 字段完备化（中风险，任务 #9–#13）
> 主验收 skill：`android-data-layer` + `api-and-interface-design`

- [x] Task #9: Room v8 加列（`user_profile_cache` 加 sex/country/region/birthday/backgroundURL/extra，nullable） → `android-data-layer`
- [x] Task #10: `buildProfileDto` 全映射（`UserProfileRepositoryImpl` 新列 → `ProfileDto`） → `api-and-interface-design` + `claude-android-skill-main`
- [x] Task #11 (2.3a): 新建 `person_profile_cache` 子表（`contactServerId` 主键 + 外键到 `contacts_cache.serverId` + sex/country/region/birthday/backgroundURL/extra nullable） → `android-data-layer` + `diegosouzapw-awesome-omni-skill-architecture`
- [x] Task #29 (2.3b): sync 重放写入新列（`SyncRepository` 处理 Person ADD/UPDATE 时写入 `person_profile_cache`） → `android-data-layer` + `diegosouzapw-awesome-omni-skill-architecture`
- [x] Task #12: 持久化 `PersonDto.self`（`contacts_cache` 加 `self INTEGER`，可并行） → `api-and-interface-design`

## Checkpoint 2
- [x] `compileDebugKotlin` 绿
- [x] 全量单测绿
- [x] `buildProfileDto` 字段无丢失
- [x] `PersonDto.self` 持久化
- [x] `person_profile_cache` 表建立

## Phase 3 — V1 表退役 • 自定义字段值（高风险，expand/contract，任务 #14–#18）
> 主验收 skill：`deprecation-and-migration` + `android-data-layer`

- [x] Task #14: 新建 `contact_field_values_cache`（expand，Room v9/v10） → `deprecation-and-migration` + `android-data-layer`
- [x] Task #15: 双写（`FieldRepositoryImpl` 写同时写 V1 + 新表，读仍走 V1） → `deprecation-and-migration`
- [x] Task #16: 切读 + 回滚门控（feature flag 关→V1 读，开→新表读） → `deprecation-and-migration`
- [x] Task #28 (3.4a): flag 开启观察期（至少 1 个 commit 周期，监控无回归） → `deprecation-and-migration`
- [x] Task #17: 删 V1 表（contract，Room v11 删 `contact_field_values` / `custom_fields` / `contact_fields`，Task #28 观察期后） → `deprecation-and-migration` + `android-data-layer`

## Checkpoint 3
- [x] `compileDebugKotlin` 绿
- [x] 全量单测绿
- [x] V1 字段表已删
- [x] `FieldRepositoryImpl` 零 V1 DAO 引用

## Phase 4 — V1 表退役 • 平台表 + 队列表（任务 #19–#23）
> 主验收 skill：`deprecation-and-migration` + `claude-android-skill-main`

- [x] Task #19: 退役 `contact_platforms`（V1，Room v12） → `deprecation-and-migration`
- [x] Task #20: 退役 `scan_results`（可并行） → `deprecation-and-migration`
- [x] Task #21: 退役 `pending_uploads` + `SyncStatusRepository.snapshot()` 改读 `sync_cursor` + `isLocalOnly` 计数 → `claude-android-skill-main` + `android-data-layer`
- [x] Task #22: 清理 `ContactPlatform` V1 包装类（依赖 Task #19） → `code-simplification` + `diegosouzapw-awesome-omni-skill-architecture`

## Checkpoint 4
- [x] `compileDebugKotlin` 绿
- [x] 全量单测绿
- [x] V1 表（`contact_platforms` / `scan_results` / `pending_uploads` / 字段表）全部移除
- [x] `SyncStatusRepository` 无队列语义

## Phase 5 — 命名整理与文档收尾（低风险，任务 #24–#27）
> 主验收 skill：`code-review-and-quality` + `android-architecture`

- [ ] Task #24: `CLAUDE.md` 加 Person* 命名规则 → `code-review-and-quality`
- [ ] Task #25: 随 PR 顺手改名 `Contact*` → `Person*`（持续） → `code-review-and-quality`
- [ ] Task #26: `docs/architecture.md` 写「Badger 数据层架构」 → `android-architecture`

## Checkpoint 5（终态）
- [ ] `compileDebugKotlin` 绿
- [ ] 全量单测绿
- [ ] V1 表零残留
- [ ] `ContactMapper` 无恒等 no-op、无 V1 包装
- [ ] `CLAUDE.md` 有 Person* 命名规则
- [ ] `docs/architecture.md` 完成
