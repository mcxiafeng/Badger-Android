# Badger-Android 代码审查后续报告

日期：2026-09-01  
审查分支：`refactor/dev-cleanup-2026-08-31`  
对应基线：`dev` 及本轮持续重构提交

> 本文件持续记录本轮代码审查、重构、UI 修复、运行时问题和真实构建验证状态。所有修改均继续落在现有工作分支，不创建新的工作分支。

## 1. 已完成：Scanner / Bitmap / DI

此前已完成 ScannerPage 恢复、OCR Job 取消、Bitmap ownership 收口、重复操作防护、ScannerViewModel constructor injection，以及对应测试。此前成功 Debug APK 基线曾为 `d39429f`。

## 2. 本轮 UI / 全局审计

本轮不再只做单点 UI 修复，而是先扫描 UI 页面、Route、通用组件、V1 历史设计和 legacy 架构边界。项目 `skills/README.md` 明确包含 15 组、50 个 `SKILL.md`；本轮重点阅读 Compose/UI、ViewModel、架构、Bug Hunter、代码简化、迁移、无障碍、Edge-to-Edge 和测试相关 skills。

### 2.1 TagManager

**Bug：观察流失败后 `uiState` 永久终止。**  
修复为错误作为值发出，Refresh 能重新建立 observation；批量删除反馈按成功/失败数量统计；补回归测试。

### 2.2 Social 平台 / QR

- 修复快速连续平台选择丢持久化。
- 旧平台请求取消，避免过期异步结果覆盖新状态。
- 无 jumpLink 的平台 QR fallback 改用实际平台名称。
- 编辑 Dialog 的确认/取消状态路径统一。

### 2.3 Social 无效 UI

原“更换背景图”入口会进入选图/裁剪流程，但最终只提示“不支持自定义背景图”，没有可完成的数据链路。

**本轮已删除：**
- 菜单入口。
- photo picker / crop state。
- 临时 Bitmap 处理。
- 相关无效引导文案。

`navigateToContacts` 暂时仍是 compatibility parameter，因为当前 App 主 Tab 调用链还没有一并迁移；将在 App 拆分时删除。

### 2.4 Settings 空 destination

`SettingsPage.UserSettings` 在 `Route.kt` 中存在，但 `SettingsSubPage` 分支仅为 `{}`，设置主页也没有真实入口。

**本轮已删除：**
- `SettingsPage.UserSettings` route。
- `SettingsSubPage` 空分支。

### 2.5 ContactDetail / Navigation

详情页 refresh 回调已改为只触发 `UserProfileTicker`，不再强制 Pager 跳到联系人 Tab。

### 2.6 BottomSheet / Accessibility

SelectionSheet 的系统导航栏 Insets 已移到整个内容容器，并补 selected / RadioButton semantics。

## 3. V1 / 历史视觉设计审计

确认历史有明确的 UI reset/design commits：

- `8cac87df`：重置 Social 界面。
- `303c45615`：重置 Auth 界面。
- `fb4be169`：重置 Setup 界面。
- `b31c7f105`：核心页面重设计、D4 组件采纳、Design Tokens。
- `36b267842`：重复代码提取、Compose 收尾、DI 模块拆分。

结论：V1 UI 设计不能简单视为“旧垃圾”。后续将恢复有价值的视觉层级、间距、交互和动画意图，但不会把 V1 数据访问和旧架构一起恢复。

## 4. Legacy / 架构混合审计

### 必须保留但隔离

- V1 Room entity / DAO：当前迁移兼容仍需要。
- V2 cache / queue / sync：当前主路径。
- 自定义 `AppNavigator`：仍为 active navigation system；本轮不因存在 Navigation 3 skill 就替换。

### 必须迁移后删除

- `KoinComponentBy` UI consumers。
- 旧 wrapper / compatibility helpers。

### 已确认的边界问题

- `App.kt` 当前仍存在 Composable 内 `scope.launch(Dispatchers.IO)` 并直接调用 `ContactNetworkResolver` / `UserProfileRepository` 写操作；后续 T7 必须移走。
- `ContactDetailPage` 仍存在 Composable 直接参与头像保存/网络图片加载等跨层逻辑；后续 T9 收口。

## 5. 大型 UI 文件

当前重点拆分对象：

- `App.kt` ~27 KB
- `AuthScreens.kt` ~41 KB
- `PersonPage.kt` ~43 KB
- `ContactDetailPage.kt` ~44.6 KB
- `UserProfileDetailPage.kt` ~37 KB
- `CardPage.kt` ~36 KB
- `CollectionDetailPage.kt` ~34.7 KB
- `SetupStepAccount.kt` ~25.8 KB
- `PhotoModeDialog.kt` ~30.7 KB
- `ScanModeDialog.kt` ~22 KB
- `LiquidGlassNavBar.kt` ~21 KB

目标是按 Route / Screen / State / Action / Dialog / Components 职责拆分，而不是单纯降低单文件行数。

## 6. 通用 UI 重复

`EmptyStateView.kt` 已确认只是 `BadgerEmptyState` 的 deprecated compatibility shim；最终零引用确认后删除。

`DialogComponents.kt` 的 `DialogButtonRow` 与 `BadgerDialog.kt` 目前形成基础组件组合，不直接删除，先统计消费者。

## 7. 当前计划文件

- `tasks/plan.md`：T1-T15 完整实施计划。
- `tasks/todo.md`：执行清单。
- `UI_REVIEW_PLAN_2026-09-01.md`：全局 UI/V1/legacy 审计。

Phase 2 当前状态：
- T4 UserSettings empty route ✅
- T5 Social background placeholder ✅
- T6 stale Social callback ⏳（等 T7 App split）

Phase 3 即将进入：
- T7 App root responsibility split

## 8. 当前验证

本轮每个代码修改均触发了新的 `Build Debug APK` workflow。最新相关 workflow 仍需以完成后的实际 conclusion 为准；本环境无法直接连接 GitHub clone 服务做本地构建，因此不虚构本地构建结果。

项目 `AGENTS.md` 禁止设备截图工具；运行时检查应使用 UI tree / adb / logcat（设备工具可用时）。

## 9. 后续顺序

```text
T1-T3 审计证据闭环
  → T7 App root 拆分 + 移出 UI IO/Repository 写操作
  → T8 Person
  → T9 ContactDetail / UserProfileDetail
  → T10 Card / CollectionDetail
  → T11 Social 视觉恢复 + 拆分
  → T12 Settings 收口
  → T13 KoinComponentBy UI consumers
  → T14 dead-code / duplicate sweep
  → T15 tests + Debug APK + final review
```
