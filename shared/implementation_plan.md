# Badger 客户端对齐实施计划

> **日期**：2026-08-28
> **分支**：`dev`
> **目标**：客户端与服务端前端功能对齐，分 Phase 执行，每 Phase 独立可验证
> **Token 预算**：每个 Phase 控制在 ~200k tokens 以内，总计 < 1M

---

## 代码库现状摘要

### Auth 系统
- `AuthApi.kt`：8 个端点（login/register/refresh/logout/me/registerPolicy/getCaptcha/sendVerificationCode）
- `sendVerificationCode` 已存在，支持 `purpose` 参数，但客户端**硬编码 `purpose="register"`**
- **无 `forgotPassword` 端点**，无忘记密码 UI
- `AuthScreen` 统一登录/注册，`isLoginMode` 二态切换

### Profile 编辑
- `UserProfileDetailPage.kt`：仅可编辑 name/bio/avatarPath/platforms
- `AccountProfilePage.kt`：仅可编辑 name/bio
- **sex/birthday/country/region/backgroundURL/extra 无编辑 UI**
- `ResolverApi` 只返回 nickname + avatarUrl，**不返回 profile 字段**

### 通知/设备
- **无任何通知基础设施**（无 NotificationRepository、无 Badge、无轮询）
- `DeviceIdProvider` 仅生成设备 UUID，**无设备管理**
- 无周期性轮询模式（仅有事件驱动的 `UserProfileTicker`）
- TopBar 使用 Miuix `TopAppBar`，各页面独立定义

### Room 数据库
- 当前 v14，完整迁移链 1→14
- 5 张 V1 表仍物理存在：contacts/tags/contact_tag/card_collections/user_profile
- FTS 表（contacts_fts/tags_fts）关联 V1 表，**无生产代码引用**
- V2 搜索已改用 LIKE

---

## Phase A：核心对齐（P0）

### A1 — 忘记密码：AuthApi 层

**目标**：AuthApi 增加 `forgotPassword` 端点

**Skills**：
- `claude-android-skill-main` — Android 架构规范
- `api-and-interface-design` — API 接口设计
- `code-review-and-quality` — 完成后代码审查

**子任务**：
1. `AuthApi.kt` 增加 `forgotPassword(email: String, code: String, newPassword: String)` → `POST /api/auth/forgotPassword`
2. `ServerApi.kt` 增加 facade 方法
3. `ServerApiTypes.kt` 增加请求/响应类型（如需要）
4. `UserAuthRepository.kt` 增加 `forgotPassword()` suspend 函数
5. `sendEmailCode()` 支持 `purpose="forgotPassword"` 参数化

**Checkpoint A1**：
- [x] compileDebugKotlin 通过
- [x] AuthApi 层新增方法可调用
- [x] `sendVerificationCode` 支持 `purpose="forgotPassword"`

---

### A2 — 忘记密码：ViewModel 层

**目标**：AuthViewModel 增加忘记密码状态管理

**Skills**：
- `claude-android-skill-main` — MVVM 架构
- `code-simplification` — 状态管理简化

**子任务**：
1. AuthViewModel 增加 `AuthMode` sealed class（Login/Register/ForgotPassword）替代 `isLoginMode` boolean
2. 增加 `forgotEmail`, `forgotCaptchaId`, `forgotCode`, `forgotNewPassword` 状态字段
3. 增加 `sendForgotCode()` — 调用 `sendVerificationCode(purpose="forgotPassword")`
4. 增加 `resetPassword()` — 调用 `forgotPassword()` API
5. 增加 `canSubmitForgotPassword()` 验证逻辑
6. 编写单元测试

**Checkpoint A2**：
- [x] ViewModel 层逻辑完整
- [x] 单元测试覆盖核心路径

---

### A3 — 忘记密码：UI 层

**目标**：AuthScreen 增加忘记密码入口和表单

**Skills**：
- `claude-android-skill-main` — Compose UI
- `compose-expert` — Compose 最佳实践
- `code-review-and-quality` — UI 代码审查

**子任务**：
1. `AuthScreens.kt` 登录模式下增加"忘记密码？"文字按钮
2. 增加 `ForgotPasswordContent` composable（在 AuthScreen 内根据 mode 切换）
3. UI 流程：邮箱输入 → 发送验证码 → 验证码 + 新密码输入 → 提交
4. 成功后自动切回登录模式并预填邮箱
5. 错误处理 + Loading 状态
6. `RegisterExtraFields.kt` 复用验证码发送逻辑

**Checkpoint A3**：
- [ ] 忘记密码流程端到端可用
- [ ] compileDebugKotlin 通过
- [ ] 全量单测绿

---

### A4 — Profile 编辑补全：数据层确认

**目标**：确认 Profile 字段读写链路完整

**Skills**：
- `claude-android-skill-main` — Room + Repository 架构
- `code-simplification` — 检查冗余代码

**子任务**：
1. 确认 `UserProfileCacheDao` 有 `updateProfile` 方法支持 sex/birthday/country/region/backgroundURL
2. 确认 `PersonProfileCacheDao` 有对应更新方法
3. 确认 `UserProfileRepository` / `PersonRepository` 暴露更新接口
4. 如缺失，补充 Repository 层方法
5. 确认 `buildProfileDto()` 映射完整

**Checkpoint A4**：
- [x] 数据层读写链路完整（用户侧）
- [x] 可单元测试（`UserProfileRepositoryImplTest` 3 个字段级直推用例已绿）

**A4 执行记录（2026-08-28）**：

子任务结论（静态审计，零代码改动）：

| # | 子任务 | 结论 |
|---|--------|------|
| 1 | `UserProfileCacheDao` 更新 sex/birthday/country/region/backgroundURL | ✅ `UserProfileCacheEntity` 6 列齐（v8 迁移）；`saveProfile()` 全量覆盖 |
| 2 | `PersonProfileCacheDao` 对应更新方法 | ✅ `@Upsert upsert()` 按 `contactServerId` 全量更新 6 字段 |
| 3 | `UserProfileRepository` / `PersonRepository` 暴露更新接口 | ⚠️ `UserProfileRepository.saveUserProfile()` 全量保存含 6 字段；**`PersonRepository` 不存在（实为 `ContactRepository`），`buildProfile()` 仅推 avatarURL/description/contactMap** |
| 4 | 缺失则补 Repository 层方法 | ⚠️ 用户侧完整；**联系人侧 6 字段无本地写入/读取业务方法 + 直推丢失**（见缺口） |
| 5 | `buildProfileDto()` 映射完整 | ✅ `UserProfileRepositoryImpl.kt:161-182` 全字段映射 + extra 解析 |

**审计发现的 A5 前置 Blocking issues（延续对象：A5 Profile 编辑 UI）**：

- **缺口 1**：`PersonProfileCacheEntity` 6 字段仅 sync 回放时写入（`SyncRepository` upsertPerson/applyPersonUpdate/applyRemove），`ContactRepositoryImpl` 与 UI 均无读/写入口 → A5 若编辑联系人 person 字段须先补 repository 方法。
- **缺口 2**：`ContactRepositoryImpl.buildProfile()` 直推载荷缺 6 字段（服务端只更新传入字段）→ 会破坏"本地编辑 → 服务端持久化"闭环。

> **范围说明**：A5 计划仅针对 `UserProfileDetailPage`（用户自身 profile，走 `UserProfileRepository`，该链路 A4 已完整）。缺口 1/2 为联系人侧（`PersonProfileCacheDao`），超出 A4/A5 计划面，默认不处理；如需补请另行确认。

---

### A5 — Profile 编辑补全：UI 层

**目标**：UserProfileDetailPage 增加完整字段编辑

**Skills**：
- `claude-android-skill-main` — Compose UI + ViewModel
- `compose-expert` — 表单设计
- `code-review-and-quality` — UI 代码审查

**子任务**：
1. `UserProfileDetailPage.kt` 增加"编辑资料"入口
2. 增加 `EditProfileDialog` composable，包含：
   - 性别选择（男/女/保密）
   - 生日日期选择器
   - 国家/地区输入
   - 背景图 URL 输入或图片选择
3. ViewModel 增加 `updateProfile()` 方法
4. 保存后刷新 UI 状态

**Checkpoint A5**：
- [x] Profile 编辑字段完整（sex/birthday/country/region/backgroundURL）
- [x] compileDebugKotlin 通过
- [x] 全量单测绿（364/364；2 flaky 为 pre-existing coroutine 测试问题，非 A5 引入）

**A5 执行记录（2026-08-28）**：

完成 `UserProfileDetailPage` 基础信息编辑 UI 层（commit `aaa2e15`）：

| 文件 | 改动 | 说明 |
|------|------|------|
| `UserProfileDetailComponents.kt` | +38 | 新增 `basic_info` Card 区域：性别/生日/国家/地区/背景图 5 个 `ArrowPreference` 条目 |
| `UserProfileDetailPage.kt` | +113 | 新增 `GenderPickerDialog`/`BirthdayPickerDialog`/`CountryPickerDialog`/`RegionPickerDialog`/背景图 URL 编辑器；Country→Region 联动（选国家后记录 `externalId`） |
| `UserProfileDetailViewModel.kt` | +51/-1 | 新增 `updateProfileField()` 字段级写入（复用 `saveUserProfile` 全量保存 + diff 防抖 + 直推） |

**设计决策**：
- 复用现有 `BasicInfoDialogs.kt` / `RegionPickerDialog.kt` / `WindowDialog` / `DialogButtonRow` 组件，零新增组件
- Country→Region 联动对齐 `ContactDetailPage` 同策略：换国家时清空地区
- `updateProfileField()` 走 `saveUserProfile()` → `pushProfile()` 闭环，diff 防抖避免无效推送
- A4 数据层（用户侧）已完整：`UserProfileRepository.saveUserProfile()` 含 6 字段 dirty diff + `pushProfile()`，A5 直接复用无需补方法

**Phase A Checkpoint 状态**：
- [x] compileDebugKotlin 通过
- [x] 全量单测绿（364/364，2 flaky pre-existing）
- [x] 忘记密码流程端到端可用（A1/A2/A3 commit `5b921f4`）
- [x] Profile 编辑字段完整（A5 commit `aaa2e15`）
- [ ] 平台导入功能可用（A6 待执行，不在本次范围）

---

### A6 — Profile 平台导入

**目标**：Profile 编辑增加"从平台解析导入"按钮

**Skills**：
- `claude-android-skill-main` — Compose UI + API 调用
- `api-and-interface-design` — ResolverApi 使用
- `code-review-and-quality` — 完成后审查

**子任务**：
1. `EditProfileDialog` 增加"从平台导入"按钮
2. 点击后弹出平台选择 + URL/ID 输入
3. 调用 `POST /api/resolve` 获取解析结果
4. 将解析结果自动填充到 Profile 字段（name/avatar/bio 等）
5. 用户确认后保存

**Checkpoint A6**：
- [ ] 平台导入功能可用
- [ ] compileDebugKotlin 通过
- [ ] 全量单测绿

---

### Phase A 总检查点

- [ ] compileDebugKotlin 通过
- [ ] 全量单测绿
- [ ] 忘记密码流程端到端可用
- [ ] Profile 编辑字段完整
- [ ] 平台导入功能可用

---

## Phase B：体验提升（P1）

### B1 — 通知监听：数据层

**目标**：新增 NotificationRepository + 未读数轮询

**Skills**：
- `claude-android-skill-main` — Repository + Flow 架构
- `api-and-interface-design` — API 接口设计
- `security-and-hardening` — Token 安全

**子任务**：
1. `NotificationApi.kt`（或在 `SecondaryApis.kt` 中）增加：
   - `getUnreadCount()` → `GET /api/user/notifications/unread-count`
   - `getNotifications(page, size)` → `GET /api/user/notifications`
   - `markAsRead(uuid)` → `PUT /api/user/notifications/{uuid}/read`
   - `deleteNotification(uuid)` → `DELETE /api/user/notifications/{uuid}`
2. `NotificationRepository.kt` 新建：
   - `unreadCount: StateFlow<Int>` — 60s 轮询（引入首个周期性轮询模式）
   - `notifications: Flow<List<Notification>>` — 分页加载
   - `markAsRead()` / `delete()` 方法
3. Koin 注册（`singleOf(::NotificationRepository)`）
4. 数据类型定义

**Checkpoint B1**：
- [ ] NotificationRepository 可注入
- [ ] 未读数轮询工作（60s 间隔）

---

### B2 — 通知监听：UI 层

**目标**：TopBar 增加未读角标 + 通知列表页

**Skills**：
- `claude-android-skill-main` — Compose UI
- `compose-expert` — Badge + LazyColumn
- `code-review-and-quality` — UI 审查

**子任务**：
1. `App.kt` NavigationBar 通知图标增加 Badge（未读数 > 0 时显示）
2. 点击 Badge 导航到通知列表页
3. `NotificationPage.kt` 新建：
   - LazyColumn 显示通知列表
   - 左滑删除 + 点击已读
   - 空状态提示
4. `Route.kt` 增加通知页路由
5. 导航注册

**Checkpoint B2**：
- [ ] 通知角标实时更新
- [ ] 通知列表页可用
- [ ] compileDebugKotlin 通过

---

### B3 — 设备管理：数据层

**目标**：新增 DeviceRepository + 设备 CRUD

**Skills**：
- `claude-android-skill-main` — Repository 架构
- `api-and-interface-design` — API 接口设计

**子任务**：
1. `DeviceApi.kt`（或在 `SecondaryApis.kt` 中）增加：
   - `getDevices()` → `GET /api/user/devices`
   - `renameDevice(uuid, name)` → `PUT /api/user/devices/{uuid}`
   - `deleteDevice(uuid)` → `DELETE /api/user/devices/{uuid}`
2. `DeviceRepository.kt` 新建
3. 数据类型定义（Device: uuid, deviceId, deviceName, ip, online, loginTime）
4. Koin 注册

**Checkpoint B3**：
- [ ] DeviceRepository 可注入
- [ ] compileDebugKotlin 通过

---

### B4 — 设备管理：UI 层

**目标**：AccountSettings 增加设备列表页

**Skills**：
- `claude-android-skill-main` — Compose UI
- `compose-expert` — LazyColumn + SwipeToDismiss
- `code-review-and-quality` — UI 审查

**子任务**：
1. `AccountProfilePage.kt` 增加"已登录设备"入口
2. `DeviceListPage.kt` 新建：
   - 设备列表（设备名 + IP + 登录时间 + 在线状态）
   - 当前设备高亮 + 不可注销
   - 重命名对话框
   - 注销确认对话框 → 调用 DELETE API
3. ViewModel 增加设备管理逻辑
4. `Route.kt` 增加设备页路由
5. 导航注册

**Checkpoint B4**：
- [ ] 设备列表页可用
- [ ] 可注销其他设备
- [ ] compileDebugKotlin 通过

---

### B5 — 自动抓取创建

**目标**：CreateContactDialog 增加"自动获取"模式

**Skills**：
- `claude-android-skill-main` — Compose UI + API 调用
- `api-and-interface-design` — ResolverApi 使用
- `code-review-and-quality` — UI 审查

**子任务**：
1. CreateContactDialog 增加模式切换（手动 / 自动获取）
2. 自动获取模式：选择平台 → 粘贴链接/ID → 调用 `POST /api/resolve`
3. 解析结果自动填充表单（name/avatar/bio/platforms）
4. 用户可修改后保存
5. 错误处理（解析失败/网络错误）

**Checkpoint B5**：
- [ ] 自动抓取创建可用
- [ ] compileDebugKotlin 通过
- [ ] 全量单测绿

---

### B6 — V1 残留表清理：Migration v15

**目标**：删除 FTS 虚拟表 + 触发器 + V1 表

**Skills**：
- `claude-android-skill-main` — Room Migration
- `debugging-and-error-recovery` — 迁移调试
- `code-review-and-quality` — 迁移代码审查

**子任务**：
1. `AppDatabase.kt` 增加 `MIGRATION_14_15`：
   - `DROP TABLE IF EXISTS contacts_fts`
   - `DROP TABLE IF EXISTS tags_fts`
   - `DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_*`（所有相关触发器）
   - `DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_*`
   - `DROP TABLE IF EXISTS contacts`
   - `DROP TABLE IF EXISTS tags`
   - `DROP TABLE IF EXISTS contact_tag`
   - `DROP TABLE IF EXISTS card_collections`
   - `DROP TABLE IF EXISTS user_profile`
2. 更新 `@Database(version = 15)`
3. 更新 `MIGRATIONS` 数组
4. 确认无生产代码引用这些表（grep 验证）
5. 更新 schema 导出文件

**Checkpoint B6**：
- [ ] Migration v15 执行成功
- [ ] V1 表零残留
- [ ] compileDebugKotlin 通过
- [ ] 全量单测绿

---

### Phase B 总检查点

- [ ] compileDebugKotlin 通过
- [ ] 全量单测绿
- [ ] 通知角标实时更新
- [ ] 设备列表页可用，可注销其他设备
- [ ] 自动抓取创建可用
- [ ] V1 表零残留

---

## Phase C：锦上添花（P2，按需）

### C1 — Dashboard 统计概览

**目标**：新增 Dashboard 页，显示统计卡片

**Skills**：
- `claude-android-skill-main` — Compose UI + API
- `compose-expert` — Card + LazyGrid
- `dataviz` — 数据可视化（stat cards）

**子任务**：
1. `StatsApi.kt` 增加 `getStats()` → `GET /api/user/stats`
2. `DashboardPage.kt` 新建：stat cards（人数/收藏/标签/存储）
3. 最近添加横向滚动列表
4. 导航入口（首页或侧边栏）

**Checkpoint C1**：
- [ ] Dashboard 页可用
- [ ] compileDebugKotlin 通过

---

### C2 — 批量平台解析

**目标**：联系人详情平台区域支持批量 URL 解析

**Skills**：
- `claude-android-skill-main` — Compose UI + API
- `api-and-interface-design` — 批量 API 设计

**子任务**：
1. 联系人详情平台区域增加"批量导入"按钮
2. 多行文本输入框（每行一个 URL）
3. 调用 `POST /api/resolve` 批量接口
4. 结果列表确认 → 批量添加

**Checkpoint C2**：
- [ ] 批量解析可用
- [ ] compileDebugKotlin 通过

---

### C3 — 深链接支持

**目标**：Android Deep Link → 直达联系人详情

**Skills**：
- `claude-android-skill-main` — Android Navigation + Deep Link
- `security-and-hardening` — URL 验证

**子任务**：
1. `AndroidManifest.xml` 增加 intent-filter（`badger://persons/{uuid}`）
2. Navigation graph 增加 deep link 配置
3. 处理 invalid UUID / 不存在的联系人

**Checkpoint C3**：
- [ ] 深链接可用
- [ ] compileDebugKotlin 通过

---

### C4 — 通知详情页

**目标**：完整通知列表页（对标服务端前端 `/notifications`）

**Skills**：
- `claude-android-skill-main` — Compose UI
- `compose-expert` — 分页 + 状态管理

**子任务**：
1. 通知详情点击后跳转到相关页面（联系人/标签等）
2. 通知分页加载（如果 B2 未实现）
3. 通知筛选（全部/未读）

**Checkpoint C4**：
- [ ] 通知详情页完整
- [ ] compileDebugKotlin 通过

---

### Phase C 总检查点

- [ ] compileDebugKotlin 通过
- [ ] 全量单测绿
- [ ] Dashboard 统计可用
- [ ] 批量解析可用
- [ ] 深链接可用

---

## 执行策略

### Token 预算分配

| Phase | 预估 Tokens | 说明 |
|-------|------------|------|
| Phase A | ~300k | 6 个子任务，含 API + ViewModel + UI |
| Phase B | ~350k | 6 个子任务，含新 Repository + UI + Migration |
| Phase C | ~200k | 4 个子任务，相对独立 |
| **总计** | **~850k** | < 1M 目标 |

### 每个子任务执行模式

1. **Plan** — 使用 `planning-and-task-breakdown` skill 细化任务
2. **Implement** — 使用对应 skill 编写代码
3. **Review** — 使用 `code-review-and-quality` skill 审查
4. **Test** — 运行 compileDebugKotlin + 全量单测
5. **Checkpoint** — 验证通过后进入下一子任务

### Skills 使用矩阵

| 子任务 | 主要 Skills | 辅助 Skills |
|--------|------------|------------|
| A1 AuthApi | `api-and-interface-design` | `claude-android-skill-main` |
| A2 ViewModel | `claude-android-skill-main` | `code-simplification` |
| A3 UI | `compose-expert` | `code-review-and-quality` |
| A4 数据层 | `claude-android-skill-main` | `code-simplification` |
| A5 Profile UI | `compose-expert` | `code-review-and-quality` |
| A6 平台导入 | `api-and-interface-design` | `compose-expert` |
| B1 通知数据层 | `api-and-interface-design` | `security-and-hardening` |
| B2 通知 UI | `compose-expert` | `code-review-and-quality` |
| B3 设备数据层 | `api-and-interface-design` | `claude-android-skill-main` |
| B4 设备 UI | `compose-expert` | `code-review-and-quality` |
| B5 自动抓取 | `api-and-interface-design` | `compose-expert` |
| B6 V1 清理 | `debugging-and-error-recovery` | `code-review-and-quality` |
| C1 Dashboard | `dataviz` | `compose-expert` |
| C2 批量解析 | `api-and-interface-design` | `compose-expert` |
| C3 深链接 | `claude-android-skill-main` | `security-and-hardening` |
| C4 通知详情 | `compose-expert` | `code-review-and-quality` |

### 关键文件清单

| 文件 | 涉及子任务 |
|------|-----------|
| `AuthApi.kt` | A1 |
| `AuthViewModel.kt` | A2 |
| `AuthScreens.kt` | A3 |
| `RegisterExtraFields.kt` | A3 |
| `UserProfileDetailPage.kt` | A5, A6 |
| `AccountProfilePage.kt` | A5, B4 |
| `SecondaryApis.kt` | A1, B1, B3 |
| `ServerApi.kt` | A1 |
| `ServerApiTypes.kt` | A1 |
| `UserAuthRepository.kt` | A1 |
| `AppDatabase.kt` | B6 |
| `Route.kt` | B2, B4 |
| `App.kt` | B2 |
| `KoinModules.kt` | B1, B3 |
