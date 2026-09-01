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
- `ScannerCamera` 的 `OnLayoutChangeListener` lambda 参数数量编译错误已修复。

## 3. 最新真实 CI / 构建结果

本阶段已经不再只有“SDK 安装成功”或“进入 Build 后被取消”的间接结果。

针对提交：

```text
ce49c98466062e8aa3dd53d49c3faf53dc471516
```

最新 `Build Debug APK` 已完整执行到：

```text
:app:kspDebugKotlin      SUCCESS
:app:compileDebugKotlin  FAILED
```

Android SDK、Gradle 初始化、资源处理、Manifest、KSP 等阶段均已实际通过。

当前真实编译 blocker 仅剩：

```text
SocialPage.kt:165:8 No value passed for parameter 'show'
SocialPage.kt:165:8 No value passed for parameter 'label'
```

因此当前不能宣称 `assembleDebug` 已通过，也不能把失败归因于 Android SDK 或 CI 环境。

### 3.1 当前编译异常的源码/解析矛盾

当前分支中 `ImageCropDialog.kt` 的源码签名为：

```kotlin
ImageCropDialog(
    imageUri: Uri,
    onConfirm: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
    cropConfig: CropConfig = CropConfig()
)
```

当前 `SocialPage` 调用也只传递上述契约所需参数：

```kotlin
ImageCropDialog(
    imageUri = cropSourceUri!!,
    onConfirm = onCropConfirm,
    onDismiss = { ... }
)
```

但 Kotlin 编译器却要求不存在于当前源码签名中的 `show` 与 `label` 参数。

这表明当前最需要继续验证的是 **编译解析/增量缓存与当前源码树之间的异常漂移**。下一步不应直接污染业务 API、随意增加无语义的 `show` / `label` 参数，而应优先执行一次干净构建：

```text
./gradlew clean assembleDebug --stacktrace
```

若 clean build 消失，则继续检查 CI Gradle cache 策略；若仍存在，则继续追踪实际参与编译的声明来源。

## 4. Scanner：仍待完成的 P0 Bitmap ownership

仍确认存在潜在生命周期竞态：

```text
UI capturedImage
      ↓
后台 OCR 直接访问同一 Bitmap
      ↓
页面 back / dismiss / reset
      ↓
releaseCapturedImage() recycle()
      ↓
后台任务可能继续访问已 recycle 的 Bitmap
```

不能通过“禁止返回”或任意延迟 recycle 掩盖。

最终修复必须明确：

- UI 展示 Bitmap 的所有权；
- 后台 OCR 工作 Bitmap 的所有权；
- 任务取消与 completion 后的释放责任；
- 页面离开时不破坏仍在执行任务的数据。

建议优先采用工作副本隔离或明确的任务资源持有模型，而不是让 UI 生命周期直接控制后台任务输入。

## 5. 当前剩余工作

### P0：恢复稳定构建

1. 对当前分支执行真实 clean build；
2. 定位 `ImageCropDialog(show, label)` 编译解析异常；
3. 取得一次完整：

```text
./gradlew clean assembleDebug --stacktrace
```

成功结论。

### P0/P1：Scanner Bitmap ownership

彻底消除 `Bitmap recycled` 生命周期竞态。

### P1：继续 UI 回归测试

继续补充：

- ContactDetail 写入完成后的刷新顺序；
- Scanner 保存期间重复提交保护；
- Scanner OCR 与 dismiss/back 生命周期；
- TagManager 搜索退出与 Dialog 状态机；
- TagManager 搜索全选与筛选切换 selection 语义。

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

本轮已经从架构清理推进到 UI 状态一致性和真实构建验证：

1. 核心 ViewModel 继续向 constructor injection 收口；
2. DI bridge 保持为明确的 Deprecated 过渡层；
3. Auth、平台 URL、Dialog 契约已修复；
4. LiquidGlassNavBar 边界与无障碍已强化并有测试；
5. TagManager Refresh、搜索全选、筛选 selection 污染和可见集合语义已修复；
6. Scanner Camera cleanup 和部分并发交互问题已修复；
7. Scanner Bitmap ownership 仍是重要未完成生命周期问题；
8. 最新 CI 已取得真实 Kotlin 编译结论，当前只剩 `SocialPage` / `ImageCropDialog` 的两个参数解析错误；
9. 在 clean build 通过前，不宣称项目已构建成功。

后续顺序：

```text
clean build
  → 定位 SocialPage / ImageCropDialog 解析异常
  → 取得 assembleDebug 成功
  → 收口 Scanner Bitmap ownership
  → 补关键 UI regression tests
  → 迁移并删除剩余 DI 兼容层
  → 最终 dead-code sweep
```
