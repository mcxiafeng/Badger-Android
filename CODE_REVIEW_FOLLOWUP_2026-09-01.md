# Badger-Android 代码审查后续报告

日期：2026-09-01  
审查分支：`refactor/dev-cleanup-2026-08-31`  
对应基线：`dev` 及本轮持续重构提交

> 本文件持续记录本轮代码审查、重构、UI 修复、运行时问题和真实构建验证状态。所有修改均继续落在现有工作分支，不创建新的工作分支。

## 1. 已完成：Scanner / Bitmap / DI

此前已完成 ScannerPage 恢复、OCR Job 取消、Bitmap ownership 收口、重复操作防护、ScannerViewModel constructor injection，以及对应测试。此前成功 Debug APK 基线曾为 `d39429f`。

## 2. 本轮 UI / 全局审计

本轮继续沿既有 UI 审计计划推进，并在现有支线直接修改。重点覆盖 App 根、主 Tab、路由、Social、ContactDetail、Settings 与通用视觉效果边界。

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

**已删除：**菜单入口、photo picker/crop state、临时 Bitmap 处理及相关引导文案。

本轮进一步删除 `SocialRoute.navigateToContacts` 的 stale compatibility callback；主 Tab 不再传递无消费者参数。

### 2.4 Settings 空 destination

`SettingsPage.UserSettings` 在 `Route.kt` 中存在，但 `SettingsSubPage` 分支仅为 `{}`，设置主页也没有真实入口。

**已删除：**`SettingsPage.UserSettings` route 与 `SettingsSubPage` 空分支。

### 2.5 ContactDetail / Navigation

详情页 refresh 回调已改为只触发 `UserProfileTicker`，不再强制 Pager 跳到联系人 Tab。

### 2.6 BottomSheet / Accessibility

SelectionSheet 的系统导航栏 Insets 已移到整个内容容器，并补 selected / RadioButton semantics。

## 3. T7 App root responsibility split — 已完成

原 `App.kt` 同时承担启动/onboarding、认证 bootstrap、DeepLink、Pager、主 Tab、二级路由、blur/lifecycle、动画与扫描导入后的网络/Repository 写操作，文件约 27KB。

本轮拆为：

- `App.kt`：只做 application composition root。
- `AppMainTabs.kt`：4 个一级 Tab、Pager、底部导航、FloatingNavBar。
- `AppRouteHost.kt`：二级 route dispatch 与统一返回处理。
- `AppDeepLinkEffect.kt`：冷启动/热启动 DeepLink 消费。
- `AppVisualEffects.kt`：Blur/Haze/GPU/Lifecycle/scroll visual state。
- `ImportProfileFieldsUseCase.kt`：扫描导入个人平台字段的解析 + profile repository 写入。

同时 `AppViewModel` 不再向 UI 暴露 Repository，改由 `findContactIdByServerId()`、`importProfileFields()` 等 intent-like API 协调；Koin 已注册新的 UseCase。

因此原本位于 Composable 内的 `ContactNetworkResolver.identify()` 与 `UserProfileRepository.updatePlatformField()` 已移出 UI 层。

## 4. T6 / stale callback — 已完成

`SocialRoute.navigateToContacts` 已从 Route/Screen API 中删除，`AppMainTabs` 不再传递无效回调。

## 5. 仍待处理的结构性问题

- `ContactDetailPage` 仍有头像 Bitmap 文件落盘逻辑，下一阶段 T9 需要迁移到专门的 storage/use-case 边界。
- `ContactDetailViewModel` / `UserProfileDetailPage` 仍有 `ContactNetworkResolver` legacy companion 入口，归入 T13 compatibility cleanup。
- `AppNavigator.navigate()` 目前允许重复 push 相同 route，需在导航行为测试覆盖后决定是否增加去重策略。
- Person/Card/CollectionDetail/Auth/Setup 等大型 UI 文件仍待职责拆分。

## 6. 当前任务状态

- T4 UserSettings empty route ✅
- T5 Social background placeholder ✅
- T6 stale Social callback ✅
- T7 App root responsibility split ✅
- T8 Person page ⏳
- T9 ContactDetail / UserProfileDetail ⏳
- T10 Card / CollectionDetail ⏳
- T11 Social polish + split ⏳
- T12 Settings consolidation ⏳
- T13 KoinComponentBy consumers ⏳
- T14 dead-code / duplicate sweep ⏳
- T15 final verification ⏳

## 7. 验证状态

此前环境无法直接通过 `git clone` 连接 GitHub，因此不虚构本地构建结果。GitHub Actions 已存在 `Build Debug APK` workflow；本轮代码修改产生的相关 workflow 需要以 GitHub 最终 conclusion 为准。

在当前支线的一个代码提交 `34721e79ada65f5228e49d85ab8e079dcabac4ba` 上，`Build Debug APK` workflow #753 已启动并处于 `in_progress`，当时已完成 checkout / Java，正在准备 Android SDK；该结果不能替代后续新增提交的最终验证。

## 8. 后续顺序

```text
T8 Person
  → T9 ContactDetail / UserProfileDetail
  → T10 Card / CollectionDetail
  → T11 Social visual polish / decomposition
  → T12 Settings
  → T13 KoinComponentBy migration
  → T14 dead-code / duplicate sweep
  → T15 JVM/Compose/Debug APK/final review
```
