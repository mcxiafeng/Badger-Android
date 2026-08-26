# Implementation Plan: 新 Java `/api` 契约迁移（换发动机）

## Overview

依据 `docs/api-handover.md`（新 Java Javalin 服务端）+ `docs/api-handover-migration-plan.md`（迁移方案，2026-08-26 拍板），把客户端从 Go `/v1` 契约（裸 JSON + If-Match 乐观锁 + op 队列同步）整体迁移到 Java `/api` 契约（ApiResult 壳 + uuid 幂等 + 服务端权威 sync）。

危险评级：🔴 High — 跨域全量迁移，换发动机不是打补丁。六大冲突：C1 响应外壳、C2 数据模型（乐观锁→uuid）、C3 同步范式（op 队列→服务端权威）、C4 鉴权（ApiResult + user + 验证码 + refresh）、C5 功能缺口（后端补 4 端点）、C6 解析器三层变。

## Architecture Decisions（已拍板）

- **同步模型**：写直推，读以 `GET /api/user/sync?since=` 为权威源；退役 op 队列 + 乐观锁 + 冲突解决 UI。
- **云端备份**：后端补 `/api/user/backups`，客户端基本不改。
- **token 刷新**：后端加 refresh 端点，客户端拦截器指向新端点。
- **注册/验证码**：完整支持（registerPolicy + getCaptcha + sendVerificationCode）。
- **Person 合并**：后端补 merge 端点，客户端保留合并 UX。
- **平台字段**：以服务端为主，`GET /api/resolve/platforms` 拉平台清单。
- **代理豁免**：AI/shortio 成功响应是裸 JSON，不走 ApiResult unwrap。

## 依赖图

```
后端 Phase 0（claude CLI 处理中）: refresh / backups / merge / 单查 / ResolveResult 字段表
        │
        ├── Phase 1 传输层（ApiCore unwrap + 路径 v1→api + 删乐观锁）  ← 地基，无依赖
        │       │
        │       └── Phase 2 鉴权域（AuthApi 重写 + 验证码 UI + refresh）  ← 可独立先行
        │               │
        │               └── Phase 3 数据域（PersonApi + SyncApi + 退役队列 + Room migration）  ← 核心最大
        │                       │
        │                       └── Phase 4 增值域（Resolver + 平台清单 + AI/shortio + 备份）
        │                               │
        │                               └── Phase 5 收尾（死代码 + 单测全量）
        └──（Phase 4 的 Resolver 字段重映射依赖 Phase 0 第 5 项字段表确认）
```

## 关键现状事实（2026-08-26 勘察）

- 传输层 `ApiCore.kt`：有 `buildRequestWithIfMatch` / `useNot2xxOrConflict` / `ConflictException`，需删，加 `unwrapApiResult`。
- `AuthApi.kt`：login/register/me 直接 `obj.get("token")` 取裸 JSON，全炸，需 ApiResult unwrap + user 字段扩展。
- `ContactApi.kt`：`/v1/contacts` + If-Match + `ContactResponse{id,serverId,version}` → `PersonApi`（uuid 幂等 + selfPerson 守卫 + Profile 嵌套）。`ContactPage` → `SyncPage`。
- `V2DomainApi.kt`：`patchMe` PATCH /api/auth/me → `PUT /api/user/profile`；tag/collection `id:Long`→`uuid:String` + colorHash + personMembers 子接口。
- `BackupApi.kt`：`/v1/backups` → `/api/user/backups`（Phase 0 后端）。
- `ContactRepositoryImpl.kt`：深度耦合乐观队列（`optimisticUpdate`/`commitDelete`/`commitMerge`/`ContactSnapshotter`/`PendingUploadScheduler`/`OperationHistoryDao`/`DeviceIdProvider`），Phase 3 核心重写点。
- `AppDatabase.kt`：version 6，需 6→7 migration（删 `serverVersion`、`serverId` 语义→uuid、新增 sync 游标）。`pending_uploads`/`operation_history` 降级为本地日志。
- `ContactNetworkResolver.kt`：`parseOne` 读旧 Go 字段（`platform`/`avatar_url`/`signature`/`contact_map`）→ 新 Java 字段（`name`/`status`/`contacts`/`extra`，camelCase，待 Phase 0 确认）。
- 测试基建：`LocalHttpServer` 进程内 ServerSocket 模拟服务端（无第三方依赖）。

## Task List

### Phase 1: 传输层（地基，机械全量）
- [ ] Task 2: ApiCore `unwrapApiResult()` + 路径 v1→api + 删乐观锁

### Checkpoint: 传输层
- [ ] `compileDebugKotlin` 通过
- [ ] 全量单测绿（含 LocalHttpServer 断言新路径）

### Phase 2: 鉴权域（可独立先行，切完即全链路可登录注册）
- [ ] Task 3: AuthApi 解析重写 + AuthResponse 扩展 + 注册验证码 UI + refresh

### Checkpoint: 鉴权域 → 向用户汇报决策点
- [ ] 登录/注册/me 全链路走 ApiResult + user 字段
- [ ] 手动决策点：验证码 UI 形态 / deviceId 登记时机

### Phase 3: 数据域（核心最大，依赖后端 Phase 0 + Phase 2）
- [ ] Task 4: PersonApi + SyncApi + 退役队列 + Tag/Collection 重写 + Room migration

### Checkpoint: 数据域 → 向用户汇报决策点
- [ ] 写直推 + sync 拉取落 Room
- [ ] 手动决策点：离线能力倒退接受度 / last-write-wins 提示文案
- [ ] 全量单测绿

### Phase 4: 增值域（依赖 Phase 0 字段表）
- [ ] Task 5: Resolver 重写 + 平台清单 + AI/shortio 路径 + 备份

### Checkpoint: 增值域
- [ ] Resolver 新契约 + platforms 接入
- [ ] AI/shortio 仅路径前缀变
- [ ] 全量单测绿

### Phase 5: 收尾
- [ ] Task 6: 死代码清理 + 单测全量适配

### Checkpoint: Complete
- [ ] 全部验收标准达成
- [ ] `compileDebugKotlin` + 全量单测绿
- [ ] 待用户回归真机

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Phase 3 队列退役后离线能力倒退 | 高（UX） | 迁移方案 §6.1：弃队列，离线编辑不再可靠上行；简化版待二期 |
| last-write-wins 静默覆盖 | 高（数据） | 设置页明示「多端同时编辑以最后保存者为准」 |
| ResolveResult 字段未确认 | 中（阻塞 Phase 4） | Phase 0 第 5 项让后端给出确定字段表（读 ResolveResult.java + 实测序列化） |
| Room migration 数据丢失 | 高（数据） | 6→7 migration 保守：只删 serverVersion 列 + 改语义，不重建表；禁止 fallbackToDestructive |
| 未提交工作树改动 | 低 | 已 commit 留档 afa0fa7 |

## Open Questions（随执行逐项向用户确认）
- Phase 2: 注册验证码 UI 形态（图形验证码 dev 明文渲染 vs 邮箱验证码输入框）。
- Phase 3: 离线编辑失败提示 + 重试 UX；多端提示文案位置。
- Phase 4: ResolveResult 字段表（等后端）。
