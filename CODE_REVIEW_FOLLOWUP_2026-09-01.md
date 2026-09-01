# Badger-Android 代码审查后续报告

日期：2026-09-01  
审查分支：`refactor/dev-cleanup-2026-08-31`  
对应基线：`dev` 及本轮持续重构提交

> 本文件持续记录本轮代码审查、重构、UI 修复、运行时问题和真实构建验证状态。所有修改均继续落在现有工作分支，不创建新的工作分支。

## 1. 已完成：Scanner / Bitmap / DI

此前已完成 ScannerPage 恢复、OCR Job 取消、Bitmap ownership 收口、重复操作防护、ScannerViewModel constructor injection，以及对应测试。此前成功 Debug APK 基线曾为 `d39429f`。

## 2. 本轮 UI / 全局审计

本轮继续沿既有 UI 审计计划推进，并在现有支线直接修改。重点覆盖 App 根、主 Tab、导航、通用 UI、Person、Social、ContactDetail、Settings 与视觉效果边界。

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

`SettingsPage.UserSettings` 曾在 `Route.kt` 中存在，但 `SettingsSubPage` 分支仅为 `{}`，没有实际界面。

**已删除：**`SettingsPage.UserSettings` route 与 `SettingsSubPage` 空分支。

### 2.5 ContactDetail / Navigation

详情页 refresh 回调已改为只触发 `UserProfileTicker`，不再强制 Pager 跳到联系人 Tab。

另外修复了自定义 `AppNavigator` 的重复导航行为：当目标 route 与当前 route 相同时，现在直接忽略，不再向返回栈重复 push 相同 destination，并新增 `AppNavigatorTest` 回归覆盖。

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

`AppMainTabs.kt` 后续审查还发现缺失 blur extension imports，已补齐并清掉无用 Compose state imports。

App 加载态统一回 Miuix 基础组件，避免 root 页面和应用其余 UI 混用 Material3 loading 组件。

同时 `AppViewModel` 不再向 UI 暴露 Repository，扫描导入使用 intent-like API + UseCase。

因此原本位于 App Composable 内的 `ContactNetworkResolver.identify()` 与 `UserProfileRepository.updatePlatformField()` 已移出 UI 层。

## 4. T6 / stale callback — 已完成

`SocialRoute.navigateToContacts` 已从 Route/Screen API 中删除，`AppMainTabs` 不再传递无效回调。

## 5. 本轮新增 UI 质量修复

### 5.1 UI 设置持久化稳定性

发现 `ThemeConfig`、`NavBarConfig` 对枚举设置使用 `ordinal` 持久化。枚举后续如果插入/调整顺序，会让老用户的主题、导航栏效果或模糊强度被映射到错误选项。

**已修复：**
- 新写入使用 `enum.name`。
- 读取同时兼容旧版本 `Int` ordinal 数据与新 `String` name 数据。
- blur radius 读取/写入增加 `0..64dp` 防御性范围约束。

涉及：`ThemeConfig.kt`、`NavBarConfig.kt`。

### 5.2 通用输入 Dialog

发现 `BadgerInputDialog` 只用 `isNotBlank()` 的视觉意图，但确认按钮校验原先没有排除只包含空白字符的输入。

**已修复：**确认按钮使用 `value.trim().isNotEmpty()` 判定；保留原始输入值传递，不强制修改用户输入。

### 5.3 通用 UI 死代码 / 重复代码

**已删除：**`EmptyStateView.kt` 这一只负责转发到 `BadgerEmptyState` 的 deprecated compatibility shim。  
同时移除 `BadgerPlatformColors` 中重复的 `douyin` key。

### 5.4 Person 页面审计发现

`PersonPage` 当前仍是 UI 最大热点之一。已确认存在一个不必要且具有重组风险的 `Ref<String?>` 字母状态：列表项组合顺序可能让 `lastShownLetter` 与实际首项脱节，理论上会造成首个字母标题偶发缺失。

正确方向是改成纯派生规则：`index == 0 || currentLetter != previousLetter`，彻底删除可变 `Ref` 与组合期间写状态。

该修复计划并入 T8 的 focused component split 中，避免在当前大文件里继续堆补丁式状态。

## 6. T8 Person — 进行中

本轮确认 PersonViewModel 的 `_contactsLoadedFromDb` 只写不读，属于 Paging/StateFlow 迁移遗留状态；已删除。

Room `getAllContacts()` Flow 继续作为联系人列表唯一事实源，未改变现有数据行为。

当前 `PersonPage` 已完成状态/导航方向审计，但 toolbar/search/selection/list/Dialog 主体拆分仍未完成。

## 7. T9 / T13 当前边界

- `ContactDetailPage` 仍存在头像 Bitmap 加载、网络下载和部分文件/图片处理协调，下一阶段继续迁移到 ViewModel / storage / use-case 边界。
- `AvatarStorage` 已建立并接入 `ContactDetailViewModel`；头像保存链路已有 ViewModel 入口，但 Screen 仍有部分旧路径待迁移。
- `ContactDetailViewModel` 与 `UserProfileDetailPage` 仍存在 `ContactNetworkResolver` legacy companion 使用，需要继续迁移到实例注入后才能删除 compatibility 入口。

## 8. 其他大型 UI

Person / Card / CollectionDetail / Auth / Setup / ContactDetail / UserProfileDetail 等大型文件仍待职责拆分；后续按 Route → Screen → focused UI module 的边界推进。

## 9. 当前任务状态

- T4 UserSettings empty route ✅
- T5 Social background placeholder ✅
- T6 stale Social callback ✅
- T7 App root responsibility split ✅
- T8 Person page ⏳（dead-state 清理 + 导航回归测试 + UI 状态审计完成；主体拆分仍在进行）
- T9 ContactDetail / UserProfileDetail ⏳
- T10 Card / CollectionDetail ⏳
- T11 Social polish + split ⏳
- T12 Settings consolidation ⏳
- T13 KoinComponentBy consumers ⏳
- T14 dead-code / duplicate sweep ⏳（已完成第一轮高可信项）
- T15 final verification ⏳

## 10. 验证状态

当前环境无法通过本地 `git clone` 稳定获取仓库，因此不虚构本地构建结果。

本轮最新提交目前没有可用的 GitHub Actions workflow run 记录，因此暂时没有可信的“本轮 Debug APK 构建成功”证据；状态仅能按代码审查与静态修改结果记录。

只有最终 workflow conclusion 为 `success` 才标记 build passed。

## 11. 本轮提交

当前工作分支仍为 `refactor/dev-cleanup-2026-08-31`，未创建新分支。

本轮主要提交：

- `2c036b8c4cb9d6879a0e28329b1efb7a6af19dd3` — `fix(ui): remove duplicate platform color mapping`
- `6e3030788e6147d5b12b76fefa0477d943c2d57a` — `fix(ui): make persisted theme mode stable across enum changes`
- `03204e80140c859dbe18c410efab207cd2c64c15` — `refactor(ui): remove unused EmptyStateView compatibility shim`
- `76a8ca3b6b8032e3a216e8006bab767ab428f476` — `fix(ui): stabilize persisted navigation effect settings`
- `40c16b7ca0878cc7a882eceb303400a3b1b95cfc` — `docs(tasks): record first UI cleanup pass findings`
- `6a6b4e4a30ca18d56a1f7b92b1bc1cf88fe4bb1c` — `fix(ui): reject whitespace-only dialog input`

## 12. 后续顺序

```text
T8 Person focused component split + letter-header fix
  → T9 ContactDetail / UserProfileDetail 跨层收口
  → T10 Card / CollectionDetail
  → T11 Social visual polish / decomposition
  → T12 Settings
  → T13 KoinComponentBy migration
  → T14 dead-code / duplicate sweep
  → T15 JVM/Compose/Debug APK/final review
```
