# Badger-Android 代码质量 / API 契约审查报告

日期：2026-09-01  
审查基线：`dev` + `refactor/dev-cleanup-2026-08-31`  
工作分支：`refactor/dev-cleanup-2026-08-31`（本轮未创建新分支）

> 连续审查记录。当前阶段从 P1 correctness 收口转向 UI maintainability：统一设计 Token、修复真实交互/生命周期缺陷、降低大型 Compose Feature 的职责耦合，并继续清理 Service Locator 过渡层。

## 1. 总体结论

当前主链路已基本稳定为：

```text
Network API → Repository → V2 cache → ViewModel → Compose
```

此前已完成的核心正确性工作继续保持：canonical `/api` surface、短链接 source-of-truth、Operation History / pending / FAILED 语义分离、DELETE/MERGE failure semantics、create-on-push client UUID 持久化，以及 Sync recovery / cursor / pagination / input validation 的 fail-safe 处理。

UI 当前主要剩余问题不是单纯“文件太大”，而是部分大型页面同时承担 state orchestration、Repository/Flow 访问、展示树和 action handler。处理策略以职责边界为目标，不机械拆文件。

## 2. DI 与历史兼容层

已完成 constructor injection 的 ViewModel：

```text
CreateContactViewModel
UserProfileDetailViewModel
AccountSettingsViewModel
NotificationViewModel
DeviceViewModel
SettingsHomeViewModel
SyncStatusViewModel
SocialViewModel
ChangePasswordViewModel
```

仍存在的主要 `KoinComponentBy` 消费者集中在 Auth、Card、Person、ContactDetail 等大型 / 历史迁移 ViewModel，后续继续按依赖分组迁移，最终删除 `di/KoinComponentBy.kt`。

`ui/components/EmptyStateView.kt` 仍被实际生产代码引用，因此保留为 compatibility wrapper；新代码优先直接使用 `BadgerEmptyState`。

## 3. Dead-code sweep

继续采用“先确认生产消费者，再删除”的原则，不按文件名猜测。

明确保留：Room migrations、QAuxv importer、sync cursor/history、PlatformEntry JSON shape、SafeLog/API error types、ContactField/CustomField/ContactFieldValue、Operation History、LegacyTagFixup，以及当前仍被依赖的兼容层。

已完成多处重复 UI / 无效 import / 过渡代码清理；Scanner / ContactDetail 等大型页面继续以真实调用链为依据收口。

## 4. 大型 Compose Feature / UI maintainability

### 4.1 ContactDetail：Fields / Actions 拆分

已完成第一阶段职责拆分：

- `ContactDetailFields.kt` 负责页面展示、Header、平台区、个人介绍和标签区；
- `ContactDetailActions.kt` 负责字段/平台 `FloatingToolbar` 的动作 UI 与可见性规则；
- `ContactDetailComponents.kt` 收缩为共享 `ThinDivider`；
- `ContactDetailPage.kt` 保留页面 state、Dialog orchestration 和导航契约；
- `additionalSystemFields` 在进入 LazyColumn 前计算，避免重复 filter；
- Toolbar 使用 `BadgerRadius` / `BadgerSpacing`；
- 新增 Fields / Actions 文件不访问 Repository / 网络。

仍需处理：`ContactDetailPage.kt` 中 `collectionRepository.getContactCollectionIds()` 的直接 Flow 订阅，以及其他逐步下沉的 action orchestration。下一步应优先把这些状态观测变成 ViewModel `StateFlow`，而不是继续机械拆文件。

### 4.2 Scanner

已完成：

- 手动输入入口改为明确的 `IconButton` 交互语义；
- 多码确认按钮增加图标及 accessibility 描述；
- 顶部/底部控制区复用 `BadgerSpacing`；
- CameraX ImageCapture / Analyzer 回调统一在 UI 边界切回主线程；
- Bitmap 所有权明确，取消/投递失败时回收，正常交给 UI state 后不重复 recycle；
- CameraX executor、ImageCapture、ML Kit detector 在离开页面时统一清理；
- Scanner ResultDialog 保存状态引入 `isSavingResult`，保存期间禁止重复提交、暂停相机控制并拦截返回键；
- 保存成功仅在持久化完成后清理结果；取消异常单独透传；普通异常保留结果并解锁 UI；
- `ScanMarkerPickerDialog` 的重复 Chip 渲染抽成 `ScanMarkerChip`；创建 Tag 区抽成 `CreateScanMarkerContent`；Scanner 标签 UI 使用统一圆角/间距 Token；
- 修复 `phone_1`、`qq_1` 等重复字段 key 在合并保存阶段未正确映射的问题。

### 4.3 Shared UI

已完成：

- `BadgerErrorStateCompact` 的重试按钮真实可点击；
- `BadgerListItem` 没有尾部内容时不再创建无意义的 endActions 槽；
- `BadgerListItem` / `BadgerContactListItem` / `BadgerIconListItem` 增加可选点击语义；
- `BadgerSelectionSheet` 使用 `Role.RadioButton` 和明确选择描述；
- 共享组件引入 `BadgerSize`，逐步替代裸 dp；
- ContactDetail Header / Bio / Tag 区进一步补齐 Button accessibility 语义；
- `BadgerSize` 本轮新增：`iconXs`、`avatarXl`、`controlMd`、`bioMinHeight`。

### 4.4 PersonPage

已修复搜索全选状态缓存 bug：原 `allFilteredIds` 只以结果数量作为 `remember` key，当结果数量不变但联系人 ID 变化时会复用旧集合。现在以实际结果内容作为依赖，连续搜索时不会漏选新联系人或保留已经离开结果集的联系人。

## 5. 本轮 UI 新增变更

### 5.1 ContactDetail 几何 Token

`BadgerDesignTokens.kt` 新增：

```text
BadgerSize.iconXs       = 16dp
BadgerSize.avatarXl     = 80dp
BadgerSize.controlMd    = 36dp
BadgerSize.bioMinHeight = 96dp
```

这样联系人详情头像、编辑图标、AI 标签按钮和个人介绍区域不再各自散落定义组件几何尺寸。

### 5.2 ContactDetail accessibility

`ContactDetailFields.kt` 本轮完成：

- 头像点击区域增加 `Role.Button` 和“查看并更换头像”的语义；图片本身不重复朗读；
- 姓名编辑区域增加 `Role.Button` 与当前姓名描述；
- 个人介绍区域改为整个内容区域可点击，不再只有“点击添加”几个字可点击；同时根据空/非空状态提供“添加/编辑个人介绍”语义；
- 已有标签区域增加编辑语义；
- AI 标签按钮尺寸改用 `BadgerSize.controlMd`，图标改用 `BadgerSize.iconSm`。

这些调整不改变业务回调、导航、数据库写入或 Dialog 契约。

## 6. P1 correctness 历史状态

### Sync recovery

UPDATE 缺本地实体会 GET `/api/user/persons/{uuid}` 回源，GET 失败不推进 cursor；未知 change / parser 丢行 fail-safe；version 回退、无进展分页、空 changes + `hasMore`、伪造跳跃和 `MAX_PULL_ROUNDS` 均有保护。

### Outbound PUT failure recovery

已增加独立 `pending_person_updates` durable outbox，包含 requestId、attempts、nextAttemptAt、lastError，并接入 WorkManager 网络约束、指数退避、unique work `pending-person-updates`。update / updateBio / platform PUT 失败不再只记录日志。

## 7. 验证状态

工作分支当前 HEAD：`12d1e3c5d56c2bc72c2e1aca8b4d72963377491d`。

GitHub Actions 已对该 HEAD 触发 `Build Debug APK`（run `33437876692`，push 事件）。在最新观测时，job 已完成 checkout/JDK17，仍处于 Android SDK setup / build 前阶段，因此当前**不能宣称 Debug 构建通过**。

仓库正常 CI 工作流为 `.github/workflows/ci.yml`，执行 `./gradlew assembleDebug --stacktrace`。

本地容器无法直接 clone GitHub 仓库（运行环境 DNS 无法解析 github.com），因此验证以仓库 Actions 为准，不伪造本地构建结果。

## 8. 当前优先级

1. 等待当前 Debug CI 得到最终结论；若失败，只修真实编译/测试问题；
2. 将 `ContactDetailPage` 的 `getContactCollectionIds(contactId)` Flow 访问下沉到 `ContactDetailViewModel`，UI 只观察 StateFlow；
3. 继续 AuthViewModel → CardViewModel → PersonViewModel → ContactDetailViewModel 的 constructor injection；
4. 收口 remaining `KoinComponentBy` consumers，最终删除 helper；
5. 增加 ContactDetail / Scanner 针对性的 Compose/UI regression tests；
6. 继续以真实消费者进行 dead-code sweep，并在每轮完成后更新本报告。

## 9. 本轮提交

- `eee5b003` — `refactor(ui): extend shared geometry tokens`
- `12d1e3c5` — `refactor(ui): improve contact detail accessibility`
- 本报告已同步更新到上述最新工作分支。

此前完成的 Scanner、PersonPage、ContactDetail Fields/Actions、Shared UI、P1 Sync/outbox 等记录均属于同一连续工作分支，不创建新的工作分支。
