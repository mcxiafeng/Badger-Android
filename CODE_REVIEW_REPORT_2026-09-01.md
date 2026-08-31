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

### 4.1.1 ContactDetail：collection state 边界收口

已完成 Repository/Flow 访问收口：

- `ContactDetailViewModel` 新增 `contactCollectionIds: StateFlow<Set<Long>>`；
- collection Flow 生命周期由 ViewModel 管理；
- `ContactDetailPage` 只观察 StateFlow；
- 页面不再用 `remember + mutableStateOf(list.toSet())` 对 Repository Flow 做二次缓存；
- 现有 CollectionPickerDialog 契约保持不变；
- 同时修复 `onPlatformSync` 日志读取时机错误。

### 4.1.2 ContactDetail：mutation / refresh 时序收口（本轮）

修复了详情页一个真实的 UI/data race：部分写操作使用 `viewModelScope.launch` 后，页面又立即执行 `onRefreshData`，造成上级联系人列表可能先于数据库写入完成而读取旧数据。

本轮采用“可等待 mutation”而不是 `delay` / polling：

- `ContactDetailViewModel` 增加 `reloadContactAwait()`；
- 增加 `removePlatformAwait()`、`addOrUpdatePlatformAwait()`、`updateContactAwait()`；
- 增加 `updateNameAwait()`、`deleteFieldValueAwait()`、`updateFieldValueAwait()`、`updateCollectionsAwait()`；
- 原有非 suspend API 保留，避免影响历史调用方；
- `ContactDetailPage` 的平台删除、平台新增/编辑、字段删除/编辑、联系人改名、名片夹更新、批量平台导入、平台同步全部改为等待真实持久化完成后再 `reloadContactAwait()`；
- 外层 `onRefreshData` 现在发生在本地状态重新读取完成之后；
- 平台删除的头像 fallback 也改为顺序执行，避免“平台已删但头像仍指向被删平台”的短暂错误状态；
- 批量导入不再在后台操作尚未完成时提前刷新父页面。

这项修复直接解决了“详情页看起来操作成功，但返回列表时旧数据闪现 / 刷新失败”的 race window。页面层继续只负责编排 UI，实际写入由 ViewModel awaitable API 承担。

## 4.2 Scanner

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

## 4.3 Shared UI

已完成：

- `BadgerErrorStateCompact` 的重试按钮真实可点击；
- `BadgerListItem` 没有尾部内容时不再创建无意义的 endActions 槽；
- `BadgerListItem` / `BadgerContactListItem` / `BadgerIconListItem` 增加可选点击语义；
- `BadgerSelectionSheet` 使用 `Role.RadioButton` 和明确选择描述；
- 共享组件引入 `BadgerSize`，逐步替代裸 dp；
- ContactDetail Header / Bio / Tag 区进一步补齐 Button accessibility 语义；
- `BadgerSize` 新增 `iconXs`、`avatarXl`、`controlMd`、`bioMinHeight`。

### 4.3.1 Shared Dialog correctness

已修复共享 `BadgerDialog` / `DialogButtonRow` 的可选按钮语义：

- `negativeText = null` 时真正不渲染取消按钮；
- `positiveText = null` 时真正不渲染确认按钮；
- 两个按钮均为 `null` 时不渲染按钮行，也不额外占用底部间距；
- 单按钮场景自动使用整行宽度；
- 按钮间距改为 `BadgerSpacing.md`，去掉裸 `20.dp`；
- 未发现其它生产代码直接调用 `DialogButtonRow`。

同时修复 `BadgerInputDialog` 的 `placeholder` 契约：参数原本已暴露但未传入 `TextField`，现在会真正显示调用方提供的 placeholder。

## 4.4 PersonPage

已修复搜索全选状态缓存 bug：原 `allFilteredIds` 只以结果数量作为 `remember` key，当结果数量不变但联系人 ID 变化时会复用旧集合。现在以实际结果内容作为依赖，连续搜索时不会漏选新联系人或保留已经离开结果集的联系人。

## 5. 本轮 UI 新增变更

### 5.1 ContactDetail mutation contract

新增 awaitable ViewModel mutation API，使 Compose 可以在数据真正落库后再刷新外层页面。旧的 fire-and-forget API 保留以避免一次性破坏其它调用方。

### 5.2 ContactDetail refresh ordering

本轮所有已定位的详情页“写入后立即刷新”路径均改成：

```text
UI action
  → ViewModel suspend mutation
  → DB commit
  → reloadContactAwait()
  → onRefreshData()
```

不再：

```text
UI action
  → launch mutation
  → onRefreshData()
  → mutation later completes
```

### 5.3 Shared Input Dialog

`BadgerInputDialog.placeholder` 现在真正传给 Miuix `TextField`，避免 API 表面存在但 UI 行为无效。

### 5.4 Shared ListItem accessibility default

继续收口共享列表项的无障碍默认行为：`BadgerListItem` 在调用方提供 `onClick` 但没有显式传入 `role` 时，自动声明 `Role.Button`；显式传入 `role` 的现有场景保持原语义不变。

这样 `BadgerContactListItem` / `BadgerIconListItem` 通过统一基类自然继承默认语义，避免每个调用点重复写 `role = Role.Button`，也降低后续新增列表项时遗漏 accessibility 语义的概率。

### 5.5 TagManagerSettingsPage 交互状态修复

继续处理设置页中的真实 UI 状态问题：

- 系统返回键退出标签搜索时，现在同步清空 `query`，重新打开搜索不会把上一次输入带回来；
- 删除标签流程进入“合并目标选择”时，立即关闭原删除选择 Dialog，只保留 source tag 的独立状态，避免两个 Dialog 同时进入 Composition 并产生叠层/返回键状态冲突；
- 合并完成后只清理合并状态，不再重复操作已关闭的删除 Dialog。

这些变更均为页面状态机级修复，没有改变 TagManagerViewModel 的 CRUD 契约。

## 6. P1 correctness 历史状态

### Sync recovery

UPDATE 缺本地实体会 GET `/api/user/persons/{uuid}` 回源，GET 失败不推进 cursor；未知 change / parser 丢行 fail-safe；version 回退、无进展分页、空 changes + `hasMore`、伪造跳跃和 `MAX_PULL_ROUNDS` 均有保护。

### Outbound PUT failure recovery

已增加独立 `pending_person_updates` durable outbox，包含 requestId、attempts、nextAttemptAt、lastError，并接入 WorkManager 网络约束、指数退避、unique work `pending-person-updates`。update / updateBio / platform PUT 失败不再只记录日志。

## 7. 验证状态

当前工作分支最新提交：`4dc850babc6eee17205a7909553ccc43c82db4ca`。

针对该提交 GitHub Actions 已创建 `Build Debug APK` workflow run `33441450429`，当前状态为 `in_progress`，尚未产生最终结论。因此目前不能把本轮改动宣称为 CI 已通过。

仓库正常 CI 工作流为 `.github/workflows/ci.yml`，执行：

```text
./gradlew assembleDebug --stacktrace
```

本地容器无法直接 clone GitHub 仓库（运行环境 DNS 无法解析 github.com），因此本轮继续以 GitHub Actions 为主要构建验证来源，不伪造本地构建结果。

## 8. 当前优先级

1. 获取 `4dc850babc6eee17205a7909553ccc43c82db4ca` 对应 Debug CI 最终结果；若失败，只修真实编译/测试问题；
2. 继续 AuthViewModel → CardViewModel → PersonViewModel → ContactDetailViewModel 的 constructor injection；
3. 收口 remaining `KoinComponentBy` consumers，最终删除 helper；
4. 对 ContactDetail / Scanner 增加针对性的 Compose/UI regression tests；
5. 继续按真实消费者进行 dead-code sweep；
6. 再评估 ContactDetail action orchestration 是否需要职责级重构。

## 9. 本轮提交

- `eee5b003` — `refactor(ui): extend shared geometry tokens`
- `12d1e3c5` — `refactor(ui): improve contact detail accessibility`
- `34c14552` — `refactor(ui): expose contact collection state from viewmodel`
- `b3a86d6f` — `fix(ui): preserve contact detail orchestration while using stateflow`
- `19c0210b` — `fix(ui): honor optional dialog buttons`
- `2d07b8e0` — `fix(ui): render optional dialog buttons correctly`
- `aff2c102` — `fix(ui): honor input dialog placeholder`
- `6948e3d9` — `refactor(ui): add awaitable contact detail mutations`
- `4d928769` — `fix(ui): synchronize detail page refresh ordering`
- `cc7deff2` — `refactor(ui): restore nested scroll import`
- `43dac303` — `refactor(ui): default clickable list item role`
- `4dc850ba` — `fix(ui): reset tag search on back and prevent dialog overlap`

所有修改均继续落在既有 `refactor/dev-cleanup-2026-08-31`，未创建新的工作分支。
