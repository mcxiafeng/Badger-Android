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

### 2.3 Social 无效 UI / dead API

原“更换背景图”入口会进入选图/裁剪流程，但最终只提示“不支持自定义背景图”，没有可完成的数据链路。

**已删除：**菜单入口、photo picker/crop state、临时 Bitmap 处理及相关引导文案。

另外删除 `SocialRoute.navigateToContacts` stale compatibility callback；主 Tab 不再传递无消费者参数。

进一步确认 Social UI 已不再使用旧 `cardImagePath` 链路后，删除 `SocialViewModel.updateCardImage()` no-op 兼容函数，避免保留假 API。

### 2.4 Settings 空 destination

`SettingsPage.UserSettings` 曾在 `Route.kt` 中存在，但 `SettingsSubPage` 分支仅为 `{}`，没有实际界面。

**已删除：**`SettingsPage.UserSettings` route 与 `SettingsSubPage` 空分支。

### 2.5 ContactDetail / Navigation

详情页 refresh 回调已改为只触发 `UserProfileTicker`，不再强制 Pager 跳到联系人 Tab。

另外修复了自定义 `AppNavigator` 的重复导航行为：当目标 route 与当前 route 相同时，现在直接忽略，不再向返回栈重复 push 相同 destination，并新增 `AppNavigatorTest` 回归覆盖。

`ContactDetailViewModel` 已继续沿 constructor injection 注入 `ContactNetworkResolver`，减少 legacy 静态 resolver 依赖。

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

## 4. 本轮新增架构边界修复

### 4.1 UI 不再直接持有 Platform Repository

`PlatformListPage` 原先在 Composable 中直接通过 `koinViewModel<PlatformListViewModel>().repository` 暴露 `UserProfileRepository`，并直接收集 Repository Flow / 调用删除。

**已修复：**
- Repository 留在 `PlatformListViewModel`。
- UI 仅消费 ViewModel 暴露状态。
- 删除动作统一通过 ViewModel 执行。
- Koin ViewModel 绑定同步调整。

### 4.2 KoinComponentBy UI consumer 迁移

以下 UI consumer 已迁移为 Compose `koinInject()` 或构造注入：

- `AddContactFieldDialog`
- `AddPlatformDialog`
- `ImportFromPlatformDialog`
- `SetupStepAccount`
- `CreateContactPage`
- `PlatformListPage`

`KoinComponentBy` 当前仍作为 legacy compatibility bridge 保留，不能在所有消费者清零前删除。

### 4.3 联系方式平台网格去重

`AddContactFieldDialog` 原先会直接拼接 `SYSTEM_FIELDS + addableDefs + customFields`，服务端清单与本地系统字段重叠时可能出现两个相同平台入口。

**已修复：**本地系统字段优先，服务端清单仅补充不存在的 `fieldKey`，避免重复入口。

### 4.4 Scanner Dialog visibility

`ScanMarkerPickerDialog` 已正确将外部 `show` 参数传递给 `WindowDialog`，不再内部硬编码 `show = true`。

## 5. Person / ContactDetail / Card 当前状态

### 5.1 Person

`PersonPage` 原先存在不必要的 `Ref<String?>` 字母状态，并在组合阶段主动写入；当搜索态/列表快照变化时，这个跨项可变引用会让首个字母标题依赖历史组合状态。

**已修复：**
- 删除 `lastShownLetter` / `Ref`。
- 字母标题完全由当前列表快照派生：`index == 0 || currentLetter != previousLetter`。
- 删除 `PersonScreen` 已无消费者的 `onAddContact` 兼容参数。
- 字母索引拖动时缓存每项高度，避免重复做相同除法。

T8 的首字母标题正确性问题已完成，后续仍需继续做 Person 主体组件拆分与 focused verification。

### 5.2 ContactDetail / UserProfileDetail

`ContactDetailViewModel` 已经拥有 `AvatarStorage` 与 `saveAndApplyAvatar()`，且 network resolver 已继续沿 constructor injection 收口。

但 `UserProfileDetailPage` / ContactDetail UI 中仍存在头像文件保存与部分大参数 Composable 结构问题，需要进一步拆分和边界清理。

暂不标记 T9 完成。

### 5.3 Card / CollectionDetail

已确认下一步优先清理 Route 中没有消费者的兼容参数，再拆出 import/export、selection、dialog 和 list/grid presentation。

暂不标记 T10 完成。

## 6. Settings / 其他 UI 质量修复

### 6.1 UI 设置持久化稳定性

发现 `ThemeConfig`、`NavBarConfig` 对枚举设置使用 `ordinal` 持久化。枚举后续如果插入/调整顺序，会让老用户的主题、导航栏效果或模糊强度被映射到错误选项。

**已修复：**
- 新写入使用 `enum.name`。
- 读取同时兼容旧版本 `Int` ordinal 数据与新 `String` name 数据。
- blur radius 读取/写入增加 `0..64dp` 范围约束并提取命名常量。

### 6.2 UI 设置状态同步

发现 `UiSettingsPage` 对“悬浮导航栏”使用一次性的 `SharedPreferences` 读取并保存到 `remember`，当其他页面修改 `NavBarConfig.floatingFlow` 后，本页可能继续展示旧值。

**已修复：**直接观察 `NavBarConfig.floatingFlow`，设置项 UI 与全局状态保持同步；点击回调只负责写入配置，不再维护第二份本地状态。

### 6.3 通用输入 Dialog

发现 `BadgerInputDialog` 确认状态应排除只包含空白字符的输入。

**已修复：**确认按钮使用 `value.trim().isNotEmpty()` 判定；保留原始输入值传递，不强制修改用户输入。

### 6.4 导航与视觉效果

- `NavTransitions` 的普通 push/pop 动画从 500ms 降至 300ms，减少长动画造成的拖沓感。
- `GpuCompat` 遇到没有有效 GL renderer/context 时不再错误判定为支持高级模糊，而是保守关闭。
- GPU 黑名单改为预编译 `Regex`，避免检测路径重复构造正则。
- `AppVisualEffects` 标记为 `@Immutable`，明确其作为 Compose UI 状态快照的稳定性。

### 6.5 其他高可信 dead-code / duplicate

已清理 `EmptyStateView` compatibility shim、`BadgerPlatformColors` 重复平台键、若干死 import / 空 `init{}` / 调试日志与已失效的背景图链路。

## 7. 当前任务状态

- T4 UserSettings empty route ✅
- T5 Social background placeholder ✅
- T6 stale Social callback ✅
- T7 App root responsibility split ✅
- T8 Person page ✅（首字母标题状态 bug 已修复；主体拆分继续推进）
- T9 ContactDetail / UserProfileDetail ⏳（部分 DI / avatar 边界已收口）
- T10 Card / CollectionDetail ⏳
- T11 Social polish + split ⏳（部分平台/QR/死 API 已完成）
- T12 Settings consolidation ✅（持久化兼容、状态同步、blur radius 约束均已完成）
- T13 KoinComponentBy consumers ⏳（多个 UI consumer 已迁移，compat bridge 仍保留）
- T14 dead-code / duplicate sweep ⏳（首轮及本轮多项高可信问题已完成）
- T15 final verification ⏳

## 8. 验证状态

最新代码提交已触发 `Build Debug APK` push workflow；截至本次记录时 workflow 已进入 `in_progress`，最终是否 `success` 尚未确认，因此不虚构 Debug APK 或全量单测已通过。

本地环境无法直接访问 GitHub，无法用本机 Gradle clone 做第二套构建验证；最终以 GitHub Actions 的 `conclusion == success` 为准。

## 9. 当前分支约束

工作分支继续固定为 `refactor/dev-cleanup-2026-08-31`，不创建新的工作分支。

不因“V2”直接删除 V1 数据兼容层；不保留用户无法完成的假交互；Composable 不直接承担 Repository 写操作或文件/网络业务协调；每个删除先确认引用闭环；大型拆分完成后进行 focused verification。

## 10. 后续顺序

```text
T9 ContactDetail / UserProfileDetail 跨层收口
  → T10 Card / CollectionDetail
  → T11 Social visual polish / decomposition
  → T13 KoinComponentBy migration
  → T14 dead-code / duplicate sweep
  → T15 JVM/Compose/Debug APK/final review
```
