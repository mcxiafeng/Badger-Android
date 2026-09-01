# Badger-Android 代码审查后续报告

日期：2026-09-01  
审查分支：`refactor/dev-cleanup-2026-08-31`  
对应基线：`dev` 及本轮持续重构提交

> 本文件用于记录本轮代码审查、重构、UI 修复、运行时问题和真实构建验证的最新状态。所有修改均继续落在现有工作分支，不创建新的工作分支。

## 1. 本轮目标

本轮持续处理：

1. 清理历史 Service Locator / `KoinComponentBy`；
2. 收口大型 UI Feature 的依赖、状态和交互边界；
3. 修复真实 UI、生命周期、DI 和状态问题；
4. 补充针对性回归测试；
5. 取得一次真实、完整的 Debug 构建结论；
6. 对安装到真机/模拟器后的实际启动崩溃继续做运行时回归。

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
- `processOcrAndAi` 不再保留未使用的 OCR 回调参数，进一步收紧处理链路。

当前仍有一个更高优先级的 ownership 问题待最终收口：`ScannerPage` 在 Composable reset/back 时仍可能直接 recycle 正在被后台 OCR 使用的 `capturedImage`。最终方案应让 UI Bitmap 生命周期与后台工作 Bitmap 所有权彻底解耦，推荐在进入后台任务前建立独立工作副本，或建立明确的资源 lease / Job 所有权模型。

### 2.6 Social / Collection Card UI

本次继续推进 UI 后发现并修复：

- `SocialPage` 调用 `BadgerInputDialog` 时缺少必需的 `show`、`label` 参数，同时 `onConfirm` 错误地使用了无参 lambda；现已按真实组件契约修正，并使用传入的最新输入值提交平台更新；
- `CollectionCard` 的背景 Bitmap 原先没有在 Composable 离开 composition 时主动释放，现在增加明确 native Bitmap cleanup；
- `CollectionCard` 的 cleanup 改为通过 `rememberUpdatedState` 获取最新 Bitmap，避免 `DisposableEffect(Unit)` 捕获初始 `null` 导致最终释放失效；
- `CollectionCard` 的背景文字颜色计算会读取 Bitmap 像素，已用稳定 key 缓存，避免 selection/recomposition 时反复计算；
- 同时移除了 `val collection = item` 这一无意义别名。

### 2.7 Network compatibility bridge / JVM signature 修复

最新真实构建继续暴露了由迁移兼容层引入的 JVM 签名冲突：

- `ContactNetworkResolver` 的 companion `@JvmStatic identify(String)` / `identifyBatch(List<String>)` 与注入后的实例方法生成相同 JVM signature；
- `ShortLinkService` 的 companion `@JvmStatic isConfigured(Context)` 与实例 `isConfigured(Context)` 生成相同 JVM signature。

处理方式：

- 移除这些兼容方法上的 `@JvmStatic`，保留 Kotlin companion 调用语义；
- 继续让新代码走 constructor injection；
- 不额外创建新的工作分支。

### 2.8 ServerApi / Koin 启动顺序修复

真机/模拟器启动回归发现新的 P0 崩溃：

```text
Unable to create application BadgerApplication
  → NotificationRepository eager singleton
    → ServerApi
      → ServerApiFactory.get()
        → IllegalStateException: ServerApi not yet installed
```

根因是旧网络模块形成了错误的初始化顺序：Koin 试图先创建 `ServerApi`，但 `ServerApi` 的 provider 又通过 `ServerApiFactory.get()` 读取一个尚未安装的实例；而 `ServerApiFactory.install()` 原本只会在 `provideOkHttpClient()` 内部创建完 `ServerApi` 后才执行。这实际上构成了一个启动时的“先安装才能创建、先创建才能安装”死结。

现在已改为：

```text
PendingPersonUpdateScheduler
        ↓
   OkHttpClient
        ↓
     ServerApi
        ↓
ServerApiFactory.install(api)
        ↓
NotificationRepository
```

具体调整：

- `NetworkModule.provideOkHttpClient()` 只负责创建 OkHttpClient，不再偷偷创建或安装 ServerApi；
- 新增 `NetworkModule.provideServerApi()`，由 Koin 明确构造唯一 `ServerApi`；
- `ServerApi` 构造成功后再执行 `ServerApiFactory.install()`；
- `NotificationRepository(createdAtStart = true)` 现在可以安全获取 `ServerApi`，不会再命中 `ServerApiFactory.get()` 的“未安装”异常；
- 同时移除了 OkHttp provider 中已经没有使用的 `PendingPersonUpdateStore` / `PendingPersonUpdateScheduler` 参数，避免形成虚假的 DI 依赖。

对应提交：

```text
4aef8b17  fix(di): construct ServerApi before installing factory
3d122482  fix(di): align OkHttp provider with ServerApi startup order
4cadaca8  cleanup(network): remove unused OkHttp provider dependencies
```

## 3. 最新真实构建结果

此前真实 CI 已完整执行到：

```text
:app:kspDebugKotlin      SUCCESS
:app:compileDebugKotlin  FAILED
```

先后暴露的实际编译 blocker：

```text
SocialPage.kt:165:8 No value passed for parameter 'show'
SocialPage.kt:165:8 No value passed for parameter 'label'
```

以及：

```text
ContactNetworkResolver.kt
Platform declaration clash: identify(String)
Platform declaration clash: identifyBatch(List<String>)

ShortLinkService.kt
Platform declaration clash: isConfigured(Context)
```

上述编译问题均已修复。

随后真机/模拟器安装运行时发现：

```text
ServerApi not yet installed; NetworkModule must initialize first
```

该问题属于运行时 DI 初始化顺序，而不是 Kotlin 编译错误，当前已经修复。

截至本文件更新时，最新分支提交为：

```text
4cadaca8b34d35c392103c5f2cbcecbc6b25d56c
```

并且该提交之后还有 Koin module 的签名同步提交：

```text
3d1224823a2d54d7898c6f440dbac6fb63064c31
```

GitHub Actions 已针对最新分支持续触发 `Build Debug APK`；当前仍需以最新 run 的最终结果确认 Debug APK 构建成功，不能仅凭代码修改宣称通过。

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

### P0：稳定启动与构建验证

1. 确认最新 `Build Debug APK` 完整通过；
2. 安装最新 Debug APK 做冷启动验证；
3. 验证 `NotificationRepository` eager 初始化不再触发 Koin `InstanceCreationException`；
4. 最终取得一次完整：

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
- CollectionCard Bitmap 生命周期和 selection 重组行为；
- Koin application startup / eager singleton dependency graph。

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

本轮已经从架构清理推进到 UI 状态一致性、资源生命周期、JVM 编译兼容和真实运行时 DI 验证：

1. 核心 ViewModel 继续向 constructor injection 收口；
2. DI bridge 保持为明确的 Deprecated 过渡层；
3. Auth、平台 URL、Dialog 契约已修复；
4. LiquidGlassNavBar 边界与无障碍已强化并有测试；
5. TagManager Refresh、搜索全选、筛选 selection 污染和可见集合语义已修复；
6. Scanner Camera cleanup 和部分并发交互问题已修复；
7. Scanner OCR 后台资源释放和取消语义已强化，但 `ScannerPage` 的 UI/worker Bitmap 所有权仍需最后一刀；
8. SocialPage 的输入 Dialog 契约已恢复；
9. CollectionCard 增加 native Bitmap 释放、最新引用 cleanup 和高成本文字颜色缓存；
10. ContactNetworkResolver / ShortLinkService 的 JVM signature clash 已修复；
11. `ServerApi` 与 `ServerApiFactory` 的 Koin 初始化死结已修复，避免 `BadgerApplication.onCreate()` 因 eager `NotificationRepository` 创建失败而直接崩溃；
12. 最新 CI / 真机回归仍需最终确认，因此当前不宣称项目整体已经完成验证。

后续顺序：

```text
确认最新 CI
  → 安装 Debug APK 冷启动
  → 验证 Koin eager singleton 启动链
  → clean assembleDebug 成功
  → 收口 Scanner Bitmap ownership
  → 补关键 UI regression tests
  → 迁移并删除剩余 DI 兼容层
  → 最终 dead-code sweep
```
