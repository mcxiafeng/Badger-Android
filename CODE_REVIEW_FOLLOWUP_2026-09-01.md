# Badger-Android 代码审查后续报告

日期：2026-09-01  
审查分支：`refactor/dev-cleanup-2026-08-31`  
对应基线：`dev` 及本轮持续重构提交

> 本文件用于记录本轮代码审查、重构、UI 修复和真实构建验证的最新状态。所有修改均继续落在现有工作分支，不创建新的工作分支。

## 1. 本轮目标

本轮持续处理：

1. 清理历史 Service Locator / `KoinComponentBy`；
2. 收口大型 UI Feature 的依赖、状态和交互边界；
3. 修复真实 UI、生命周期和状态问题；
4. 补充针对性回归测试；
5. 取得一次真实、完整的 Debug 构建结论。

工作分支：

```text
refactor/dev-cleanup-2026-08-31
```

## 2. 已完成的架构与 UI 修复

### 2.1 ViewModel constructor injection

以下核心 ViewModel 已继续从历史 `KoinComponentBy` 全局取依赖迁移为 constructor injection：

- `AuthViewModel`
- `CardViewModel`
- `PersonViewModel`
- `ContactDetailViewModel`
- `TagManagerSettingsViewModel`

当前目标结构：

```text
ViewModel
  ← constructor dependencies
  ← Koin module
```

历史 DI bridge 仍保留为明确标记的 `@Deprecated` 过渡层；剩余消费者迁移完成后应删除，不应将其视为最终架构。

### 2.2 Auth / URL / Dialog 行为

已完成：

- Auth 异常、取消和成功路径的 loading 状态恢复；
- 完整 `http/https` URL 直接使用，用户名/UID 再按 `linkTemplate` 构造；
- `DialogButtonRow` 保持历史调用契约，未提供按钮文本时不强行改变旧 Dialog 行为。

### 2.3 LiquidGlassNavBar

已处理：

- 空 Tab 列表；
- selected index 负数或越界；
- drag stop 再次 clamp；
- tabs / icons 数量不一致；
- `Role.Tab`、selected 和 content description；
- 避免图标与外层 Tab 重复朗读。

已新增 `LiquidGlassNavBarTest` 覆盖空列表、负 index、越界 index 和单 Tab。

### 2.4 TagManager UI 状态

已修复：

- 错误态 `Refresh` 原本为空操作，现在真正重新建立 `observeAllTags()` 订阅；
- 搜索状态下“全选”不会再选择搜索结果之外的隐藏标签；
- 切换“全部 / 手动 / AI”筛选时清理旧 selection，避免批量操作隐藏标签；
- “筛选 + 排序 + 搜索”的最终可见集合统一收口到 `TagManagerUiState.Success.searchVisibleTags(query)`；
- 页面渲染和批量选择共用同一可见集合语义；
- 已增加 `TagManagerUiStateTest` 固定相关筛选、搜索和排序语义。

### 2.5 Scanner UI

已修复：

- `CameraPreview` cleanup 不再只捕获初始 `camera`，通过 `rememberUpdatedState(camera)` 使用最新 Camera；
- Scanner 标记创建增加创建中锁，避免快速连续点击导致重复异步 `upsertTag`；
- `ScannerCamera` 的 `OnLayoutChangeListener` lambda 参数数量编译错误已修复；
- OCR 工作函数不再吞掉 `CancellationException`，避免页面销毁后把取消错误当普通识别失败继续回调；
- `processOcrAndAi` 对临时蒙版 Bitmap 使用 `try/finally` 回收；
- ML Kit `TextRecognizer` 使用 `try/finally` 确保 `close()`；
- `processPhotoBitmap` / `processBitmapOcrOnly` 对其工作 Bitmap 在完成或取消路径统一 `recycleSafely()`；
- 移除 `processOcrAndAi` 从未使用的 OCR 回调参数，进一步收紧处理链路。

当前仍有一个更高优先级的 ownership 问题待最终收口：`ScannerPage` 在 Composable reset/back 时仍可能直接 recycle 正在被后台 OCR 使用的 `capturedImage`。最终方案应让 UI Bitmap 生命周期与后台工作 Bitmap 所有权彻底解耦，推荐在进入后台任务前建立独立工作副本，或建立明确的资源 lease / Job 所有权模型。

### 2.6 Social / Collection Card UI

本次继续推进 UI 后发现并修复：

- `SocialPage` 调用 `BadgerInputDialog` 时缺少必需的 `show`、`label` 参数，同时 `onConfirm` 错误地使用了无参 lambda；现已按真实组件契约修正，并使用传入的最新输入值提交平台更新；
- `CollectionCard` 的背景 Bitmap 原先没有在 Composable 离开 composition 时主动释放，现在增加明确 native Bitmap cleanup；
- `CollectionCard` 的 cleanup 改为通过 `rememberUpdatedState` 获取最新 Bitmap，避免 `DisposableEffect(Unit)` 捕获初始 `null` 导致最终释放失效；
- `CollectionCard` 的背景文字颜色计算会读取 Bitmap 像素，已用稳定 key 缓存，避免 selection/recomposition 时反复计算；
- 同时移除了 `val collection = item` 这一无意义别名。

## 3. 最新真实 CI / 构建结果

此前针对提交：

```text
ce49c98466062e8aa3dd53d49c3faf53dc471516
```

真实 CI 已完整执行到：

```text
:app:kspDebugKotlin      SUCCESS
:app:compileDebugKotlin  FAILED
```

实际错误为：

```text
SocialPage.kt:165:8 No value passed for parameter 'show'
SocialPage.kt:165:8 No value passed for parameter 'label'
```

该错误已在后续提交中修正。修复后新的 CI 已被 GitHub Actions 接收并进入队列/执行阶段，当前以最新 run 的最终结果为准。

截至本次更新，不能把 `assembleDebug` 标记为最终通过；需要等待包含最近 Scanner / Card / Social 修改的最新 run 完成后再下结论。

此前报告中将该错误表述成 `ImageCropDialog` 自身契约异常是不准确的；实际调用点是 `SocialPage` 中的 `BadgerInputDialog`。

## 4. Scanner Bitmap ownership：当前状态

已经先完成后台处理侧的资源纪律：

```text
OCR task
  ├─ CancellationException 正常向上传播
  ├─ masked Bitmap finally recycle
  ├─ ML Kit recognizer finally close
  └─ work Bitmap finally recycle
```

但页面侧当前仍存在：

```text
capturedImage
      ↓
后台 OCR 直接读取同一实例
      ↓
back / dismiss / reset
      ↓
UI 侧 releaseCapturedImage() 直接 recycle()
      ↓
后台仍可能访问已 recycle Bitmap
```

因此“后台函数 finally 回收”不是最终解决方案，只解决了 worker 自身的释放纪律；下一步必须修改 `ScannerPage` 的 ownership 边界，不能继续让 UI reset 直接回收后台任务输入。

## 5. 当前剩余工作

### P0：恢复稳定构建

1. 等待当前分支最新 CI 完整完成；
2. 如仍有编译错误，以最新日志为准继续修复；
3. 最终取得一次完整：

```text
./gradlew clean assembleDebug --stacktrace
```

成功结论。

### P0/P1：Scanner Bitmap ownership

彻底消除 `Bitmap recycled` 生命周期竞态；推荐：

1. UI `capturedImage` 只由 UI 生命周期管理；
2. 后台 OCR 接收独立工作副本；
3. worker 自己在 completion/cancel/failure 路径释放副本；
4. 新任务开始时不得 recycle 旧任务仍持有的输入。

### P1：继续 UI 回归测试

继续补充：

- ContactDetail 写入完成后的刷新顺序；
- Scanner 保存期间重复提交保护；
- Scanner OCR 与 dismiss/back 生命周期；
- TagManager 搜索退出与 Dialog 状态机；
- TagManager 搜索全选与筛选切换 selection 语义；
- Social 编辑平台字段 Dialog 的确认/取消状态；
- CollectionCard Bitmap 生命周期和 selection 重组行为。

### P1：最终 UI dead-code sweep

继续检查：

- 未使用 Compose state；
- 无效 remember key；
- 不可达分支；
- 已无消费者的过渡 wrapper；
- 无效 import；
- 重复 helper。

### P2：大型 UI 文件职责边界

在行为稳定后按：

```text
Screen orchestration
Presentation components
Action components
ViewModel state/mutations
```

做必要拆分，不进行机械拆文件。

## 6. 当前结论

本轮已经从架构清理推进到 UI 状态一致性、资源生命周期和真实构建验证：

1. 核心 ViewModel 继续向 constructor injection 收口；
2. DI bridge 保持为明确的 Deprecated 过渡层；
3. Auth、平台 URL、Dialog 契约已修复；
4. LiquidGlassNavBar 边界与无障碍已强化并有测试；
5. TagManager Refresh、搜索全选、筛选 selection 污染和可见集合语义已修复；
6. Scanner Camera cleanup 和部分并发交互问题已修复；
7. Scanner OCR 后台资源释放和取消语义已强化，但 `ScannerPage` 的 UI/worker Bitmap 所有权仍需最后一刀；
8. SocialPage 的输入 Dialog 契约已恢复，消除了此前真实 CI 的 Kotlin 编译 blocker；
9. CollectionCard 增加 native Bitmap 释放、最新引用 cleanup 和高成本文字颜色缓存；
10. 最新 CI 尚未最终完成，因此当前不宣称项目整体构建成功。

后续顺序：

```text
确认最新 CI
  → 修复新增编译/回归问题
  → clean assembleDebug 成功
  → 收口 Scanner Bitmap ownership
  → 补关键 UI regression tests
  → 迁移并删除剩余 DI 兼容层
  → 最终 dead-code sweep
```