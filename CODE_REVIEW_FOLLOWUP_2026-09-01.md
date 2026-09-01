# Badger-Android 代码审查后续报告

日期：2026-09-01  
审查分支：`refactor/dev-cleanup-2026-08-31`  
对应基线：`dev` 及本轮持续重构提交

> 本文件用于记录本轮代码审查、重构、UI 修复、运行时问题和真实构建验证的最新状态。所有修改均继续落在现有工作分支，不创建新的工作分支。

## UI 本轮重点（2026-09-01 续）

### Scanner Bitmap ownership（P0/P1）

**目标模型：**

```text
UI capturedImage  ──只由 UI lifecycle 管理──► releaseCapturedImage / DisposableEffect
        │
        │  process* 入口 createWorkBitmapCopy()
        ▼
独立 workBitmap ──worker 独占──► finally recycleSafely()
（不 recycle UI 持有的 capturedImage）
```

**已落地（`ScannerComponents.kt`）：**

1. `createWorkBitmapCopy()`：源已 recycle / 拷贝失败返回 null；
2. `processPhotoBitmap` / `processBitmapOcrOnly` **内部拷贝**，只释放副本，**不 recycle 输入**；
3. 与旧 `ScannerPage` 调用兼容：页面仍可把 `capturedImage` 传入，worker 自行解耦。

**可选强化（P2）：**

- dismiss/reset 时 cancel 进行中的 OCR Job；
- UI 在 launch 前同步拷贝（进一步缩小调度竞态窗口）。

### 仍待验证

1. 最新 `Build Debug APK` / `./gradlew clean assembleDebug`；
2. 冷启动与 Koin eager `NotificationRepository`；
3. UI regression tests（OCR dismiss/back、保存重复提交等）；
4. 最终 UI dead-code sweep。

### 事故说明

中间曾误推 `PLACEHOLDER` / `see-file` 占位内容到 `ScannerPage.kt` 与本文件；已恢复。后续推送须使用完整文件内容。

工作分支：`refactor/dev-cleanup-2026-08-31`
