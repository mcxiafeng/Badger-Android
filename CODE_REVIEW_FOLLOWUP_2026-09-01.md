# Badger-Android 代码审查后续报告

日期：2026-09-01  
审查分支：`refactor/dev-cleanup-2026-08-31`  
对应基线：`dev` 及本轮持续重构提交

> 本文件用于记录本轮代码审查、重构、UI 修复、运行时问题和真实构建验证的最新状态。所有修改均继续落在现有工作分支，不创建新的工作分支。

## 1. 本轮立即处理（2026-09-01 续 2）

最新 `Build Debug APK` 在 `195fa8d` 失败，根因不是文档提交本身，而是更早的占位事故仍未完全收口：

```text
app/src/main/kotlin/.../scanner/ScannerPage.kt  = "see-file"（8 bytes）
```

`ScannerComponents.kt` 与本 follow-up 已在 `d9bf168` / `195fa8d` 恢复，但 `ScannerPage.kt` 仍是占位文件，因此 `:app:compileDebugKotlin` 无法通过。

本轮已：

1. 恢复完整 `ScannerPage`，并与 worker 所有权模型对齐：三处启动路径在 `launch` 前同步 `createWorkBitmapCopy()`；
2. dismiss / back / dispose 时 cancel 进行中的 OCR `Job`，避免回调把已关闭的结果 Dialog 再弹回来；
3. 保存中 / 识别中禁止重复拍照与相册选择；
4. `ScannerViewModel` 从 `KoinComponentBy` 迁到 constructor injection；
5. 增加 `ScannerWorkBitmapCopyTest` 覆盖拷贝独立性与已 recycle 源图。

## 2. Scanner Bitmap ownership

```text
UI capturedImage  ──只由 UI lifecycle 管理──► releaseCapturedImage / DisposableEffect
        │
        │  launch 前在同一线程 createWorkBitmapCopy()
        ▼
独立 workBitmap ──所有权交给 processPhotoBitmap / processBitmapOcrOnly──► finally recycleSafely()
```

- worker **不再 recycle** UI 持有的 `capturedImage`；
- 拷贝失败时立即结束 loading 并提示，不启动 worker；
- `resetScannerState()` 与 `onDispose` 会 cancel OCR Job；worker 的 `CancellationException` 继续上抛，`finally` 仍释放副本。

## 3. 已完成的架构与 UI 修复（累计）

1. 核心 ViewModel 继续向 constructor injection 收口（本轮新增 `ScannerViewModel`）；
2. DI bridge 保持为明确的 `@Deprecated` 过渡层；
3. Auth、平台 URL、Dialog 契约已修复；
4. LiquidGlassNavBar 边界与无障碍已强化并有测试；
5. TagManager Refresh、搜索全选、筛选 selection 污染和可见集合语义已修复；
6. Scanner Camera cleanup、OCR 取消语义、Bitmap 所有权、dismiss 生命周期已收口；
7. SocialPage 输入 Dialog 契约已恢复；
8. CollectionCard native Bitmap 释放与文字颜色缓存；
9. ContactNetworkResolver / ShortLinkService 的 JVM signature clash 已修复；
10. `ServerApi` 与 `ServerApiFactory` 的 Koin 初始化死结已修复。

此前一次完整通过的 Debug 构建是 `1455c9a`（占位事故之前）。本轮修复后需以最新 `Build Debug APK` 结论为准。

## 4. 当前剩余工作

### P0：确认本轮修复后的 CI

1. 最新 `Build Debug APK` 完整通过；
2. 安装 Debug APK 冷启动，确认 `NotificationRepository` eager 初始化不再崩溃。

### P1：继续 UI 回归测试

- ContactDetail 写入完成后的刷新顺序；
- TagManager 搜索退出与 Dialog 状态机；
- Social 编辑平台字段 Dialog 的确认/取消状态；
- Koin application startup / eager singleton dependency graph。

### P1：剩余 `KoinComponentBy` 消费者

仍走过渡层的主要入口：

- `DashboardViewModel`
- `PlatformListPage` / `PlatformListViewModel`
- `AuthScreens`（`ServerUrlHolder`）
- `AddPlatformDialog` / `AddContactFieldDialog` / `ImportFromPlatformDialog` / `CreateContactPage`
- `RegionPickerDialog`
- `SetupStepAccount`
- `ShortLinkService` / `ContactNetworkResolver` companion 兼容桥

迁完后再删除 `KoinComponentBy`。

### P2：大型 UI 文件职责边界

行为稳定后再按 Screen / Presentation / Action / ViewModel 拆分，不机械拆文件。

## 5. 后续顺序

```text
确认最新 CI
  → 安装 Debug APK 冷启动
  → 继续迁移剩余 KoinComponentBy 消费者
  → 补 ContactDetail / TagManager / Social 回归测试
  → 删除 DI 兼容层
  → 最终 dead-code sweep
```
