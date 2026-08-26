# Badger 客户端迁移 Landing Checkpoints

> 依据 `tasks/plan.md` 生成。逐条勾选，完成一条打一条。Phase 0（后端）由 claude CLI 在 Badger-Server 处理中。

## Phase 0 — 后端补齐（claude CLI 进行中，任务 #1）
- [ ] `POST /api/auth/refresh`（返回 `data:{token}`）
- [ ] 备份 CRUD `/api/user/backups`（GET/POST + GET/DELETE/{id}，ApiResult 壳，≤6MiB 上限）
- [ ] `POST /api/user/persons/{uuid}/merge`（target+merged_ids，级联清 personMembers + sync）
- [ ] `GET /api/user/persons/{uuid}` 单查（详情页依赖）
- [ ] ResolveResult 线上序列化字段表确认（camelCase? contacts 数组/map? signature? 批量结构?）
- [ ] 服务端 `compileJava` / 测试通过

## Phase 1 — 传输层（任务 #2）
- [x] `ApiCore.unwrapApiResult()`：2xx→data；code!=200 或非2xx→ApiException；代理豁免
- [x] 全部路径 `v1/*`→`api/*`（机械替换；尾斜杠清单 `/api/settings/` `/api/shortlinks/` `/api/admin/shortlinks/` `/api/resolve/` 当前客户端无使用点，Phase 4 接入 resolver/platforms 时处理）
- [ ] 删 `buildRequestWithIfMatch` / `useNot2xxOrConflict` / `ConflictException` / `ConflictResponse` / `ContactPage` → **已裁决：随 Phase 3 一起删**（被 ContactRepositoryImpl/PendingUploadExecutor/ContactSyncBootstrapper/ContactSnapshotter/OperationHistoryPage 引用，属 Phase 3 退役面）
- [x] `compileDebugKotlin` 通过
- [x] 全量单测：337/338 绿；唯一失败 `AuthViewModelTest.onPassword` = **基线 pre-existing**（HEAD 已存在，与 Phase 1 改动无关，见 Checkpoint C1）

## Checkpoint C1: 传输层
- [x] 上述全部勾选（乐观锁删除项推迟 Phase 3，已裁决）
- [x] 向用户汇报
- [x] 基线失败 `AuthViewModelTest.onPassword` 已裁决：推迟到 Phase 2 一并修（实现禁空格是有意设计，测试断言是旧契约残留）

## Phase 2 — 鉴权域（任务 #3）
- [x] `AuthApi`：login/me/register 解析重写（ApiResult 壳 + `user{uuid,name,displayName,email,isAdmin,profile,lastLogin,createTime}`）
- [x] `AuthResponse` 扩展 user 字段
- [x] 注册表单：`passwordAgain` + 必填邮箱 + 验证码 UI（registerPolicy→getCaptcha→sendVerificationCode）
- [x] 登录可选传 `deviceId` / `deviceName`
- [x] refresh 拦截器指向新端点；失败→强制重登
- [x] `AuthPrefs` token/user 结构适配
- [x] `compileDebugKotlin` 通过 + 相关单测绿

## Checkpoint C2: 鉴权域 → 决策点（已确认）
- [x] 验证码 UI 形态 / deviceId 登记时机已确认（完整策略驱动 / 登录时传）
- [x] 向用户汇报后可进入 Phase 3

## Phase 3 — 数据域（任务 #4，核心最大）
- [x] `PersonApi`（原 ContactApi）：create/list/update/delete + uuid 幂等重放 + selfPerson 400 守卫 + Profile 嵌套映射
- [x] `ContactMapper` ↔ Person 重写
- [x] 新建 `SyncApi`：`GET /api/user/sync?since=` 增量重放落 Room（单批 500 + hasMore 分页）
- [x] 退役：`PendingUploadExecutor` / `PendingUploadScheduler` / `ContactSnapshotter` / `OperationTypes`(P5/P6) / 撤销重放 / `scheduleRevertIfStuck` / `recoverFromDirect`
- [x] `OperationHistoryPage` 降级为只读本地日志（删撤销/重发/冲突解决按钮）
- [x] `ContactRepositoryImpl` 直推直删；`commitMerge` 走后端 merge 端点
- [x] Tag/Collection 重写：Long→uuid、colorHash、personMembers、成员子接口
- [x] Room migration 6→7：删 `serverVersion`、`serverId` 语义→uuid、sync 游标表
- [x] `compileDebugKotlin` 通过 + 全量单测绿

## Checkpoint C3: 数据域 → 决策点（问用户）
- [x] 离线能力倒退接受度 / last-write-wins 提示文案已确认（接受倒退进入 Phase 4；文案暂不加）
- [x] 向用户汇报后可进入 Phase 4

## Phase 4 — 增值域（任务 #5，依赖 Phase 0 字段表）
- [ ] `ResolverApi`：`/api/resolve/`（尾斜杠）+ `{items:[...]}` + ApiResult 壳 + 字段重映射（parseOne）
- [ ] `GET /api/resolve/platforms` 接入 `SetupStepPlatforms` / 平台清单
- [ ] `AiApi` / `ShortLinkApi` 纯路径前缀替换（v1→api）
- [ ] `BackupApi` 指向新 `/api/user/backups`
- [ ] `compileDebugKotlin` 通过 + 相关单测绿

## Checkpoint C4: 增值域
- [ ] 上述全部勾选
- [ ] 向用户汇报

## Phase 5 — 收尾（任务 #6）
- [ ] 清死代码：`LinkResolver`(stub) / `PlatformIdExtractor` / `kindCanSync`/`SYNCABLE_KINDS` / 死 `/v1` 注释
- [ ] 单测全量适配（ContactNetworkResolverTest 等）+ 新增 Person/Sync 映射测试
- [ ] `compileDebugKotlin` + 全量单测绿
- [ ] 向用户汇报迁移完成，待真机回归
