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
- [x] 全量单测绿（372/372，2026-08-29）

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

**A5 补完记录（2026-08-29）**：

对照计划子任务，UI 层在 `aaa2e15` 已齐；本次补的是独立可验证的 ViewModel 单测 + 可注入 `ioDispatcher`（测试调度器），不改业务语义。

| 计划子项 | 状态 | 说明 |
|----------|------|------|
| 1. 「编辑资料」入口 | ✅ 已完成 | 点击名字/简介打开 EditNameDialog；基本信息 5 个 ArrowPreference |
| 2. `EditProfileDialog` 含性别/生日/国家/地区/背景图 | ✅ 已完成（形态差异） | 未做单一大 Dialog，拆成独立 picker（Gender/Birthday/Country/Region + 背景图 URL 编辑器），与 ContactDetail 同模式 |
| 2. 背景图「图片选择」 | ⚠️ 有意不做 | `backgroundURL` 是远端 URL 字段，本地选图需上传端点，当前无此 API；计划写的是「URL 输入**或**图片选择」，走 URL 输入 |
| 3. ViewModel `updateProfile()` | ✅ 已完成 | 实现为 `updateProfileField(fieldKey, value)` 字段级写入 |
| 4. 保存后刷新 UI | ✅ 已完成 | `onDone(fresh)` + `appViewModel.refreshUserProfile()` |
| 单测缺口 | ✅ 本次补完 | `UserProfileDetailViewModelTest`：sex/birthday/country/region/backgroundURL 映射、空串→null、未知 key 不落库、其它字段不被抹掉 |

**Phase A Checkpoint 状态**：
- [x] compileDebugKotlin 通过
- [x] 全量单测绿（372/372）
- [x] 忘记密码流程端到端可用（A1/A2/A3 commit `5b921f4`）
- [x] Profile 编辑字段完整（A5 commit `aaa2e15` + 2026-08-29 单测补完）
- [x] 平台导入功能可用（A6，见下）

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
- [x] 平台导入功能可用
- [x] compileDebugKotlin 通过
- [x] 全量单测绿（372/372）

**A6 执行记录（2026-08-29）**：

| 文件 | 改动 | 说明 |
|------|------|------|
| `ImportFromPlatformDialog.kt` | 新建 | 平台网格 → URL/ID 输入 → `ContactNetworkResolver.identify`（`POST /api/resolve/`）→ 预览 name/bio/avatar → 确认 |
| `UserProfileDetailComponents.kt` | +入口 | 基本信息 Card 增加「从平台导入」ArrowPreference |
| `UserProfileDetailPage.kt` | +接线 | 打开 Dialog；确认后走 `viewModel.importFromPlatform()` |
| `UserProfileDetailViewModel.kt` | +方法 | `importFromPlatform()` + 纯函数 `mergeImportedProfile()`（仅覆盖非空字段，过滤 `name=="未知"`） |
| `UserProfileDetailViewModelTest.kt` | +8 用例 | A5 字段级写入 5 条 + A6 merge/import 3 条 |

**设计决策**：
- 计划写的 `EditProfileDialog` 不存在（A5 已拆成独立 picker），导入入口挂在基本信息 Card，不另造大 Dialog
- 复用 `PlatformGridSelector` + `PlatformManifestRepository`（服务端清单，离线兜底本地）
- 解析走既有 `ContactNetworkResolver.identify`，不新开 Resolver 客户端
- 头像先落盘再回传路径；下载失败只写 name/bio，不阻断、有日志
- 合并语义：只覆盖解析到的非空字段，不抹掉用户已填的 sex/birthday/country/region/backgroundURL

---

### Phase A 总检查点

- [x] compileDebugKotlin 通过
- [x] 全量单测绿（372/372）
- [x] 忘记密码流程端到端可用
- [x] Profile 编辑字段完整
- [x] 平台导入功能可用

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
- [x] NotificationRepository 可注入
- [x] 未读数轮询工作（60s 间隔）

**B1 执行记录（2026-08-29）**：

| 文件 | 改动 | 说明 |
|------|------|------|
| `NotificationApi.kt` | 新建 | GET unread-count / GET list / PUT read / DELETE；uuid 路径穿越校验；delete 404 幂等 |
| `ServerApiTypes.kt` | +`UserNotification` | camelCase 行；createTime 兼容 ISO 字符串与 epoch millis |
| `ServerApi.kt` | +facade | `getUnreadNotificationCount` / `listNotifications` / `markNotificationRead` / `deleteNotification` |
| `NotificationRepository.kt` | 新建 | `unreadCount` 60s 轮询（仅 SignedIn+有 token）；登出清零；失败保留上次未读 |
| `KoinModules.kt` | +1 | `single(createdAtStart=true)`，B1 不依赖 B2 UI 也能跑轮询 |
| `NotificationApiTest` / `NotificationRepositoryTest` / `ApiPathMigrationTest` | +单测 | 路径 + 解析 + 轮询虚时 + 登出停轮询 |

**设计决策**：
- 服务端 `GET /api/user/notifications` **无 page/size**（一次全量，未读在前），计划写的分页改为全量 `StateFlow` 快照
- 类型不叫 `Notification`，避免与 `android.app.Notification` 撞名
- 轮询对齐服务端前端 `auth-shared.js`：已登录才打、60s、失败不把 badge 清零
- uuid 拼进路径前拒绝 `/` `?` `#`（Token/路径安全）

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
- [x] 通知角标实时更新（NavigationBar 设置 Tab + Settings TopBar + 设置卡入口，同源 `unreadCount`）
- [x] 通知列表页可用（点击已读 / 左滑删除 / 空状态 / 未登录引导）
- [x] compileDebugKotlin 通过
- [x] 相关单测绿（NotificationViewModelTest / NotificationPageFormatTest / SettingsHomeViewModelTest / B1 仓库测）

**B2 执行记录（2026-08-29）**：

| 文件 | 改动 | 说明 |
|------|------|------|
| `NotificationPage.kt` | 补齐 | LazyColumn + 左滑删除（失败回弹）+ 点击已读 + 空/未登录 EmptyState + 下拉刷新 |
| `NotificationViewModel.kt` | 接线 | combine 仓库 Flow；refresh/markAsRead/delete 失败写 error，不静默清空列表 |
| `Route.kt` | +`SettingsPage.Notifications` | 走既有 `SettingsSubPage` 栈，不新开顶层 Route |
| `SettingsSubPage.kt` | +分发 | `NotificationPage(onBack, onNavigateToLogin)` |
| `SettingsPage.kt` | +入口 | TopBar 铃铛 BadgedBox + 设置卡「通知」ArrowPreference |
| `SettingsHomeViewModel.kt` | +`unreadCount` | combine `NotificationRepository.unreadCount` |
| `App.kt` / `AppViewModel.kt` | +角标 | MainTabs NavigationBar / FloatingNavBar 设置 Tab 显示未读 |
| `LiquidGlassNavBar.kt` | +`badge` | `NavBarItem` / `FloatingNavBar` 可选 Miuix Badge |
| `UnreadBadge.kt` | 新建 | `formatUnreadBadge`：0 隐藏、>99 → `99+` |
| `KoinModules.kt` | +VM | `viewModel { NotificationViewModel() }` |
| `NotificationViewModelTest` / `NotificationPageFormatTest` | +单测 | refresh/已读/删除失败不丢列表 + 时间/角标纯函数 |

**设计决策**：
- 计划写「App.kt NavigationBar 通知图标」——客户端底栏是 4 Tab（名片/联系人/名片夹/**设置**），没有独立通知 Tab。角标挂在**设置 Tab** + Settings TopBar 铃铛，点击铃铛/设置卡进列表（与「点击 Badge 导航」等价）
- 左滑 `confirmValueChange` 恒 false：等仓库删行后再离开 composition，API 失败行回弹（有 snackbar，不吞根因）
- 未登录空态引导去登录，不假装有列表

**与计划差异**：
- 未做独立顶层 `Route.Notifications`（复用 `SettingsSubPage`，与 CloudBackup 同模式）
- 底栏无独立通知图标，角标落在设置 Tab（产品结构约束，非漏做）

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
- [x] DeviceRepository 可注入
- [x] compileDebugKotlin 通过
- [x] 全量单测绿（含 DeviceApiTest 8 条 + DeviceRepositoryTest 5 条 + ApiPathMigrationTest 3 条）

**B3 执行记录（2026-08-29）**：

| 文件 | 改动 | 说明 |
|------|------|------|
| `ServerApiTypes.kt` | +`UserDevice` | camelCase 行；parse 缺 uuid → null；loginTime 兼容 ISO 字符串与 epoch millis |
| `DeviceApi.kt` | 新建 | GET list / PUT rename / DELETE；uuid 路径穿越校验；delete 404 幂等 |
| `ServerApi.kt` | +facade +field | `listDevices` / `renameDevice` / `deleteDevice`；`private val devices = DeviceApi(core)` |
| `DeviceRepository.kt` | 新建 | `devices` StateFlow 全量快照；refresh 按需拉（无轮询）；rename/delete 乐观更新；登出清空 |
| `KoinModules.kt` | +1 | `single { DeviceRepository(...) }`；无需 createdAtStart（无轮询） |
| `DeviceApiTest` / `DeviceRepositoryTest` / `ApiPathMigrationTest` | +单测 | 路径 + 解析 + uuid 穿越 + 404 幂等 + 乐观更新 + 登出清空 |

**设计决策**：
- 服务端 `GET /api/user/devices` 一次返回全量，无分页 → `StateFlow` 快照，不造 page/size
- 类型不叫 `Device`，避免与 `android.hardware.Device` 撞名（与 `UserNotification` 同策略）
- 设备列表无需轮询（用户主动进设备页时才拉），与 NotificationRepository 不同，无定时 Job
- rename/delete 走乐观更新（UI 立即反映），API 失败则下次 refresh 矫正（有 snackbar，不吞根因）
- uuid 拼进路径前拒绝 `/` `?` `#`（与 NotificationApi 同策略，Token/路径安全）
- delete 404 幂等（设备已删视为成功），403 不吞（当前设备不可自删，抛给 UI）
- 登出清空设备列表（避免设备信息在 SignedOut 后残留 UI）

**与计划差异**：
- 计划写 `SecondaryApis.kt` 中增加 → 实际独立为 `DeviceApi.kt`（与 NotificationApi 同模式，§15 #19 拆分架构）

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
- [x] 设备列表页可用
- [x] 可注销其他设备
- [x] compileDebugKotlin 通过
- [x] 全量单测绿（含 DeviceViewModelTest 9 条）

**B4 执行记录（2026-08-29）**：

| 文件 | 改动 | 说明 |
|------|------|------|
| `DeviceViewModel.kt` | 新建 | refresh / renameDevice / deleteDevice / currentDeviceId / 失败写 error 不清列表 |
| `DeviceListPage.kt` | 新建 | 左滑注销（确认弹窗）/ 点击重命名 / 当前设备高亮禁自删 / 下拉刷新 / 未登录空态 |
| `Route.kt` | +`SettingsPage.Devices` | 设备页路由 |
| `SettingsSubPage.kt` | +分发 + 修调用 | `DeviceListPage(onBack, onNavigateToLogin)`；`AccountProfilePage` 传 `onNavigateToSubPage` |
| `AccountProfilePage.kt` | +入口 | 操作卡增加「已登录设备」ArrowPreference（修改密码下方、退出登录上方） |
| `KoinModules.kt` | +VM | `viewModel { DeviceViewModel() }` |
| `DeviceViewModelTest.kt` | +9 用例 | refresh 成功/失败 + rename 成功/blank 防御 + delete 失败/blank 防御 + currentDeviceId + authState + clearError |

**设计决策**：
- 复用 `NotificationPage` 同模式：SwipeToDismiss + PullToRefresh + EmptyStateView + SnackbarHost + WindowDialog
- 当前设备通过 `DeviceIdProvider.deviceId()` 与 `UserDevice.deviceId` 比对识别，UI 高亮 + 不可左滑注销
- 注销弹二次确认（WindowDialog），重命名弹编辑对话框
- 左滑 `confirmValueChange` 恒 false：等仓库删行后再离开 composition，API 失败行回弹（有 snackbar）
- `formatDeviceLoginTime` 纯函数复用 `formatNotificationTime` 同策略（ISO 字符串 / epoch millis 兼容）
- ViewModel 通过 `KoinComponentBy.get()` 获取依赖，与 `NotificationViewModel` 同模式

**与计划差异**：
- 计划写「AccountSettings 增加设备列表页入口」→ 实际入口在 `AccountProfilePage`（个人信息页）操作卡，与「修改密码」「退出登录」同级，更符合用户认知
- 计划写「注销确认对话框」→ 实际用 `WindowDialog` + `DialogButtonRow`，与项目已有 Dialog 模式一致

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

- [x] compileDebugKotlin 通过
- [x] 全量单测绿
- [x] 通知角标实时更新
- [x] 设备列表页可用，可注销其他设备
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
