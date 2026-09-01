# Badger-Android 代码审查后续报告

日期：2026-09-01  
审查分支：`refactor/dev-cleanup-2026-08-31`  
对应基线：`dev` 及本轮持续重构提交

> 本文件持续记录本轮代码审查、重构、UI 修复、运行时问题和真实构建验证状态。所有修改均继续落在现有工作分支，不创建新的工作分支。

## 1. 已完成：Scanner / Bitmap / DI

此前已完成 ScannerPage 恢复、OCR Job 取消、Bitmap ownership 收口、重复操作防护、ScannerViewModel constructor injection，以及对应测试。最新成功 Debug APK 基线曾为 `d39429f`。

## 2. 本轮 UI 回归（2026-09-01）

本轮针对 Compose/Miuix UI、状态机、导航、Insets、无障碍和用户交互顺序做了系统检查，并直接修复发现的确定性问题。

### 2.1 TagManager

**Bug：观察流失败后 `uiState` 永久终止。**  
原实现让 Repository 的异常穿透 `combine/stateIn`；连续重试失败后 StateFlow 完成，后续点击 Refresh 无法重新订阅数据。

**修复：**
- `tagsFlow` 改为 `Flow<Result<List<Tag>>>`；错误作为值发出，不再终止外层状态流。
- Refresh 现在可以重新创建 Repository observation。
- 批量删除反馈改成按成功/失败数量准确统计。
- 新增 `TagManagerSettingsViewModelTest` 覆盖“观察失败 → Refresh → 恢复成功”。

### 2.2 Social 平台选择

**Bug：快速连续切换平台会丢掉第二次选择。**  
原 `SelectPlatformUseCase` 的 2 秒时间防抖把整个平台选择调用标记为 `SKIPPED`，结果 UI 已显示 B，但默认平台仍可能持久化成 A。

**修复：**
- 移除会丢用户真实选择的时间防抖，保留 Mutex 保证短链更新串行。
- `SocialViewModel` 维护当前平台选择 Job，旧任务取消，过期任务不能覆盖最新 SUCCESS/ERROR 状态。
- 新增 `SelectPlatformUseCaseTest` 覆盖快速连续选择。

### 2.3 Social QR fallback / 编辑状态

**Bug：非手机号且没有 jumpLink 的平台全部生成“微信号：xxx”。**

**修复：**
- fallback 文案改为实际平台 displayName，例如 QQ/微博等不再误标为微信号。
- 平台编辑 Dialog 的确认/取消状态保持明确关闭，不依赖底层重组副作用。

另外，Social 页“更换背景图”目前仍会进入图片裁剪流程，但确认后明确提示“暂未支持自定义背景图”；该入口属于待决策的占位功能，后续应根据产品需求删除或真正接通 `backgroundURL`，当前暂不把它当成可用功能继续扩张。

### 2.4 ContactDetail / 主导航

**Bug：详情页的 refresh 回调会强制 `animateScrollToPage(1)`。**  
从“名片夹”等入口打开联系人详情后，保存联系人信息会把底层 Pager 强制切回“联系人” Tab。

**修复：**
- `ContactDetailPage` 的 refresh 回调现在只调用 `AppViewModel.refreshUserProfile()`。
- 不再为刷新数据改变用户当前导航位置。

### 2.5 通用 BottomSheet / 无障碍

**Bug：无按钮的 SelectionSheet 没有底部系统 Insets。**  
`BadgerBottomSheet` 关闭默认 Window Insets 后，原实现只给按钮行加 `navigationBarsPadding()`；`BadgerSelectionSheet(showButtons=false)` 的列表底部可能被手势/导航区域覆盖。

**修复：**
- `navigationBarsPadding()` 上移到整个 Sheet 内容列。
- `BadgerSelectionSheet` 为 RadioButton selection 增加 `selected` semantics，同时保留 `Role.RadioButton`。

## 3. 既有 UI 修复继续有效

- LiquidGlassNavBar 已具备边界保护、拖拽 clamp 和无障碍测试；本轮复审未发现新的确定性边界 Bug。
- `NavTransitions` 当前为 300ms，与既有性能整改目标一致。
- Scanner、CollectionCard、Dialog contract 等前轮修复继续保留。

## 4. 当前验证

当前分支最新代码已触发 `Build Debug APK` CI。最近一次可见运行 `33491744766` 对应 PR #1，运行时已进入 `Build Debug APK` 步骤，最终结论需以该运行完成后的结果为准。

截至目前尚未宣称安装到真实设备完成冷启动验证；GitHub Actions 能证明构建链路，但不能替代真实设备手势/视觉回归。

## 5. 后续剩余项

### P0

1. 等待并确认最新 CI 最终为 success；若失败，直接修。
2. 在真实设备或 Emulator 做冷启动、详情页保存、Tab 保持、SelectionSheet 底部点击、Social 快速切换等冒烟回归。

### P1

1. 迁移剩余 `KoinComponentBy` UI 消费者并最终删除兼容层。
2. 完成 Social 背景图占位入口的产品决策：删除入口或实现 `backgroundURL` 全链路。
3. 继续扫 `ContactDetailPage`、`NfcSettingsPage` 等大文件的职责边界，优先提取 UI-only helper，避免机械拆文件。

### P2

1. 全库 dead-code / 重复组件 sweep。
2. 检查更多 Compose semantics、长列表性能及低端 GPU fallback。

## 6. 后续顺序

```text
CI 最终验证
  → 设备/Emulator UI 冒烟
  → KoinComponentBy 剩余消费者迁移
  → Social 背景图占位决策
  → 大型 UI 文件职责收口
  → 全库 dead-code sweep
```
