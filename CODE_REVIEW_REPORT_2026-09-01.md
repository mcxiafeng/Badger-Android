# Badger-Android 代码质量 / API 契约审查报告

日期：2026-09-01  
审查基线：`dev` + `refactor/dev-cleanup-2026-08-31`  
工作分支：`refactor/dev-cleanup-2026-08-31`（本轮未创建新分支）

> 本文为连续审查记录。本轮继续上一版 P1 correctness 工作后的架构收口，重点转向 UI maintainability：继续统一设计 Token、修复共享 UI 组件的真实交互缺陷，并推进大型 Compose Feature 的职责级拆分。

## 1. 总体结论

项目已经基本脱离历史 V1 compatibility / Service Locator 主导的结构。当前主链路为：

```text
Network API → Repository → V2 cache → ViewModel → Compose
```

此前已将 `CreateContactViewModel`、`UserProfileDetailViewModel`、`AccountSettingsViewModel`、`NotificationViewModel`、`DeviceViewModel`、`SettingsHomeViewModel`、`SyncStatusViewModel`、`SocialViewModel`、`ChangePasswordViewModel` 迁移到显式 constructor injection。

UI 方面，前几轮已经完成 Empty / Loading / Error / ListItem 的设计 Token 收口，并修复 `BadgerErrorStateCompact` 重试不可点击的问题。本轮继续处理 ContactDetail：把字段/列表展示和操作工具栏从页面协调器中移出，降低单页入口的职责密度，同时保持 ViewModel 状态流、Dialog 契约和导航行为不变。

本轮继续处理 Scanner 的控制层 UI，并进一步修复拍照结果回调的线程/Bitmap 所有权问题：收敛可交互控件语义、复用设计 Token，同时确保 CameraX 后台线程不会直接修改 Compose 状态。

当前最大剩余问题仍然是大型 Compose feature 的职责耦合，以及部分核心大型 ViewModel 仍有 `KoinComponentBy` 过渡依赖。

## 2. 历史清理状态与本轮纠偏

此前已经完成的清理继续保持：

- canonical `/api` surface 收口；
- short.io API key source-of-truth 收口到服务端；
- Operation History / pending / FAILED 语义分离；
- MERGE 失败不误当 DELETE 404；
- create-on-push 首次失败持久化 client UUID，后续复用同一 UUID；
- NFC 无消费者 ViewModel / compat 文件清理；
- V1 DTO / duplicate UI 的大部分删除。

上一轮发现两项“删早了”的生产依赖并恢复：

- `di/KoinComponentBy.kt`：仍有旧 ViewModel 通过静态 helper 获取依赖；继续从消费者侧逐步迁移，最终目标仍是删除 helper。
- `ui/components/EmptyStateView.kt`：仍有页面实际引用，因此保留为兼容 wrapper；新代码应直接使用 `BadgerEmptyState`。

Resolver 兼容层原则保持不变：authoritative 网络实现只有 canonical `/api/resolve`，compatibility alias / bridge 不恢复第二套 HTTP client。

## 3. API 契约核对

客户端当前主要使用 canonical `/api` surface：Auth、User、Sync、Settings、Stats、Upload、Resolver、AI、short.io/server shortlinks。

Person API：

```text
GET /api/user/persons/{uuid}
PUT /api/user/persons/{uuid}
```

因此 Sync UPDATE 缺本地实体可以回源，而 outbound PUT failure 可以持久化等待重试。

## 4. P1：Sync recovery

### 4.1 UPDATE 缺行回源

```text
UPDATE Person → 本地缺行 → GET /api/user/persons/{uuid}
→ upsert 完整 Person/profile/platform → 应用当前 UPDATE → 推进 cursor
```

GET 回源失败不吞异常，cursor 保持不变。

### 4.2 未知 change fail-safe

未知 `type`、`objectName`、Person/Collection/Tag field，以及 parser 丢行均不会被静默消费；当前没有本地 projection 的 `Device` / `UserSettings` 是明确允许跳过的对象。

### 4.3 游标与分页安全

已增加 version 回退、无进展分页、空 changes + `hasMore`、伪造 version 跳跃及 `MAX_PULL_ROUNDS` 防护。

### 4.4 Sync API 输入校验

拒绝负 `since`、非法 `limit`、非对象 data、缺失 changes、parser 丢行、旧 version、回退 version 与无进展分页。

## 5. Sync 数据一致性

当前采用“先应用、后推进 cursor”的 replay-safe 设计，而不是数据库事务级整批 rollback。批次中途失败时 cursor 不推进，下次从同一 cursor 重放；未来若出现不可幂等副作用，应进一步收敛到 Room transaction。

## 6. P1：Outbound PUT failure recovery 已完成

此前存在明确缺口：

```text
本地修改成功 → PUT 失败 → 本地仍正确 → 没有 pending update
```

本轮增加独立的 `pending_person_updates` durable outbox，**不复用 `isLocalOnly`**。

### 6.1 Outbox

每次 PUT 先写 outbox，再执行网络请求。成功按 `(serverId, requestId)` 删除；失败记录 attempts / nextAttemptAt / error。新编辑会替换同一 `serverId` 的旧 pending payload，而旧请求成功返回时不会误删新 requestId。

### 6.2 Retry scheduler / Worker

`PendingPersonUpdateScheduler` 已接入 WorkManager：`NetworkType.CONNECTED`、指数退避、unique work `pending-person-updates`、`APPEND_OR_REPLACE`，App 启动和每次 enqueue 都会 kick。Worker replay 持久化 payload，失败写回 backoff 状态并返回 `Result.retry()`。

### 6.3 数据存储

`pending_person_updates` 使用同一个 Room SQLite connection，但不进入 Room Entity graph；包含 serverId、requestId、payload、时间、attempts、nextAttemptAt、lastError，并为 nextAttemptAt 建索引。

## 7. Repository failure-path

DELETE、MERGE、create-on-push 的既有 failure semantics 保持正确；update / updateBio / platform PUT 现在进入 durable outbox，不再只靠日志。

## 8. Dead-code sweep

本轮不继续做“看到文件就删”。实际消费者确认后处理。

保留：Room migrations、QAuxv importer、sync cursor/history、PlatformEntry JSON shape、SafeLog/API error types、ContactField/CustomField/ContactFieldValue、Operation History、LegacyTagFixup，以及仍被生产代码依赖的 `KoinComponentBy` / `EmptyStateView` 兼容层。

此前已完成：

- 9 个 ViewModel 的 Service Locator 依赖迁移到 constructor injection；
- `EmptyStateView` 与 `BadgerEmptyState` 的重复渲染实现合并；
- 删除迁移过程中不再需要的 ViewModel 静态 lookup；
- `SocialViewModel` 清掉不再使用的 `ShortLinkService` / `Job` import 噪音；
- 共享 UI 状态组件中的无效/重复 import 清理。

仍存在的主要 `KoinComponentBy` 消费者集中在 Auth、Card、Person、ContactDetail 等大型 / 历史迁移 ViewModel，需要按依赖分组继续迁移。

## 9. DI / 架构边界（本轮）

### 9.1 已迁移

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

Koin `viewModel { ... }` 在 composition root 负责解析 repository / use case / Context；默认 dispatcher 作为构造参数保留，便于 JVM 单测替换。

### 9.2 仍需迁移

优先级：

1. AuthViewModel
2. CardViewModel
3. PersonViewModel
4. ContactDetailViewModel
5. 其余仍实际调用 `KoinComponentBy.get()` 的小型 ViewModel

完成后再删除 `di/KoinComponentBy.kt`。

## 10. 大型 Compose Feature / UI maintainability

ContactDetail / Scanner 仍存在职责耦合；不以增加文件数量为目标，而以状态、展示和操作职责边界为目标。

当前共享 UI 组件已经继续收口：

```text
Empty / Loading / Error / ListItem
        ↓
BadgerSpacing design tokens
        ↓
统一间距与基础尺寸语义
```

同时，`BadgerErrorStateCompact` 在提供 `onRetry` 时已经改为真正可点击的 `TextButton`；`BadgerDialog` / `BadgerListItem` 等组件也继续清理无效 import 和散落尺寸。

`EmptyStateView` 继续作为旧 API 兼容层存在，新代码应直接使用 `BadgerEmptyState`。

### 10.1 ContactDetail：Fields / Actions 拆分已完成一阶段

本轮完成报告中上一阶段留下的 Fields / Actions 拆分：

- 新增 `ContactDetailFields.kt`：集中负责 `ContactDetailPageContent`、列表状态分支、Header、社交平台区、个人介绍区、标签区等页面展示职责；
- 新增 `ContactDetailActions.kt`：集中负责字段/平台 `FloatingToolbar` 的动作 UI 和可见性规则；
- `ContactDetailComponents.kt` 收缩为共享的 `ThinDivider`，不再同时承担整页展示和工具栏职责；
- `ContactDetailPage.kt` 的现有 state / ViewModel / Dialog orchestration 保持不变，因此没有引入新的路由或状态源；
- 字段列表在抽离后先计算 `additionalSystemFields`，避免在 LazyColumn DSL 内重复执行相同 filter；
- Toolbar 继续复用 `BadgerRadius` / `BadgerSpacing`，内部间距统一为已有设计 Token；
- 新增文件不直接访问 Repository / 网络，符合 UI 层架构红线；
- `ThinDivider` 继续保留原 0.5dp 视觉，不因 Token 化改变线宽。

本轮结构 diff：

```text
ContactDetailActions.kt      +84
ContactDetailFields.kt       +497
ContactDetailComponents.kt   -560 / +0
```

变更集中在 UI 结构层，没有改动 ViewModel API、Dialog 参数契约和导航栈。

需要明确：当前仍有大量 action orchestration 留在 `ContactDetailPage.kt`，因此这不是“ContactDetail 已完全解耦”，而是把 **Fields / Actions 的 UI 责任** 从入口文件中分离。下一步更适合继续处理 action handler 的分组、状态模型收敛和大型 ViewModel 的 constructor injection，而不是继续机械拆文件。

### 10.2 Scanner：控制层 UI + 拍照回调正确性

本轮先处理 Scanner 入口中最明确、低风险的 UI maintainability 问题，并补上一个会影响 UI 稳定性的线程/内存生命周期缺陷，没有继续机械拆 `ScannerPage.kt`：

- `ScannerComponents.kt` 的手动输入入口从裸 `Box + clickable` 改为统一的 `IconButton`，与返回/闪光灯/相册按钮保持一致的交互组件和语义；
- 多码模式的确认收集按钮补充明确的语义描述，并增加相机图标，避免原来只有空白白色圆形、无可访问性提示的情况；按钮仍保持原有 72dp 视觉尺寸和启用条件；
- Scanner 顶部、底部控制区的基础间距开始复用 `BadgerSpacing`，减少同一文件中的散落硬编码；
- 扫描中间的装饰图形保留为纯展示，不再声明为可交互控件；
- `ScannerCamera.kt` 的 `ImageCapture.OnImageSavedCallback` 原本运行在 `photoExecutor` 后台线程，却直接调用 `onImageCaptured`。这条回调最终会驱动 `ScannerPage` 的 Compose `mutableState`，存在 off-main state mutation 风险。本轮已将 Bitmap 投递切回 `Dispatchers.Main.immediate`，并通过 `Job.invokeOnCompletion` 在 UI 投递失败/取消时回收 Bitmap；正常投递后把所有权交给 UI state，不再由后台 finally 重复 recycle；
- 以上修复保持 `CameraPreview` 对外回调契约不变，没有增加新的 camera state，也没有改变 CameraX 生命周期；
- `ScannerCamera.kt` 的相机、Executor、TextRecognizer 离开页面仍统一在 `DisposableEffect` 中清理；
- 本轮复核确认：`ScannerComponents.kt` 的 `processPhotoBitmap` / `processBitmapOcrOnly` 已经在 `Dispatchers.Main` 上调用 `onResult`，因此不在 `ScannerPage` 外层重复套 Main dispatch；
- 弹出 `ResultDialog` 时，Scanner 右上角“手动输入”入口与闪光灯/相册一样禁用，避免模态结果层已经显示后仍能从背景控制区发起导航。

### 10.3 Scanner：CameraX → Compose 回调边界与临时文件清理（已完成）

上一阶段继续复核 Scanner 的 CameraX Analyzer 与 Compose 状态边界后，已处理后台线程回调与临时 `Bitmap` 生命周期问题：

- QR / 文本 Analyzer 的结果回调不再直接从 CameraX executor 线程修改页面状态，而是在进入 UI 层前切回主线程；
- 图片选择/拍照后的临时 `Bitmap` 所有权在处理链路结束后明确释放，避免重复 recycle 与泄漏；
- Analyzer / ImageCapture 的 executor 和 ML Kit detector 均在页面退出时清理；
- 维持既有 `CameraPreview` 回调 API，不引入第二套 camera state。

### 10.4 Scanner：共享 UI 组件继续收口（2026-09-01，本轮新增）

本轮继续处理 Scanner 的展示层重复代码，但没有进一步拆分 `ScannerPage.kt`：

- `ScanMarkerPickerDialog.kt` 中原本重复实现的「无」Tag、普通 Tag Chip 已抽成 `ScanMarkerChip`，统一处理选中态、前置颜色点、文字颜色及交互区域；
- 新建 Tag 的输入 / 颜色 / 操作按钮区域抽成 `CreateScanMarkerContent`，降低 Dialog 主体的嵌套深度；
- Tag 选择区域及新建标签区域开始统一使用 `BadgerRadius` / `BadgerSpacing`，与项目其他 Dialog UI 保持一致；
- Scanner 模型层的 `PlatformTag`、`DuplicateTag`、`ConflictTag` 已收口到统一的状态标签渲染方式，降低重复的圆角、padding、Text 样式定义；
- 本轮没有改变 Tag 选择、创建、关闭和回传逻辑，仅调整 UI 结构与设计 Token。

同时，本轮修复了 Scanner 多值字段合并的一个实际 correctness 问题：`phone_1`、`qq_1` 等重复字段 key 在进入合并流程后会统一剥离数字后缀，再映射到真实字段定义，避免 UI 已选择字段但保存阶段找不到对应 fieldId。

### 10.5 Scanner：ResultDialog / saving lifecycle（2026-09-01，本轮完成）

本轮把上一项明确留下的保存生命周期问题真正落地，没有再依赖临时脚本：

- `ScannerPage` 新增 `isSavingResult`，保存/附加开始后保持结果对话框状态，不再立即 `resetScannerState()`；
- 保存期间相机进入 paused 状态，Scanner 控制区保持禁用，Activity 返回键被拦截；
- `onConfirm` / `onAttachToExisting` 均增加父层互斥保护，第二次点击不会再启动新的保存协程；
- 增加可见的“正在保存”模态进度窗口，让用户知道本次扫描仍在提交，而不是误以为没有响应；
- 成功路径只在实际持久化流程完成后回到 Main dispatcher，再统一释放 Bitmap / 结果状态；
- `CancellationException` 单独透传，不会把协程取消误报成普通保存失败；
- 普通异常会解除 `isSavingResult` 锁并保留当前结果，显示“保存失败”提示；错误提示只能关闭，不自动重试，从而避免批量保存部分成功后重复写入；
- `onAttachToExisting` 复用同一生命周期与错误语义；
- 为这轮 UI 修改遗留的临时 patch/test workflow 已全部清理，正常构建入口恢复为 `.github/workflows/ci.yml`。

## 11. 本轮提交与验证状态

工作分支仍为 `refactor/dev-cleanup-2026-08-31`，没有创建新工作分支。

Scanner 保存生命周期代码已提交，随后清理了一次性 UI patch/test workflows。此前的失败运行属于这些临时 workflow 的脚本执行失败，不代表项目 `assembleDebug` 失败。

正常 `.github/workflows/ci.yml` 会对该分支执行 `./gradlew assembleDebug --stacktrace`。截至本报告本次更新时，最新正常 Debug 构建仍处于 pending / in-progress 验证阶段，因此**这里不提前宣称构建通过**；以 GitHub Actions 最终结果为准。

本轮当前可确认已经完成的 UI correctness / maintainability 项为 10.2～10.5：控制层交互语义、CameraX → Compose 回调边界、Bitmap 生命周期、共享 Tag UI 收口、ResultDialog 保存状态机以及临时 workflow 清理。

## 12. 下一步

按照优先级继续：

1. 读取最新 Debug CI 的最终结果；若有真实编译错误，优先修复后重新验证；
2. AuthViewModel → CardViewModel → PersonViewModel → ContactDetailViewModel 的 constructor injection 迁移；
3. 处理 remaining `KoinComponentBy` consumers，最终删除兼容 helper；
4. 继续按真实消费者做 dead-code sweep，而不是按文件名猜测删除；
5. 对 Scanner / ContactDetail 做针对性 UI 单元/仪器测试，再更新最终质量评级。
