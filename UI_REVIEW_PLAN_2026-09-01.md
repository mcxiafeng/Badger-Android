# Badger-Android UI / V1 / Legacy Cleanup — Global Audit Plan

**工作分支：** `refactor/dev-cleanup-2026-08-31`（继续使用，不创建新分支）

## 当前进度

本轮全局探索、无效入口清理和 App root 职责拆分已经完成。当前进入大型 UI 页面边界收口和第二轮架构清理阶段；不为了减少文件数量进行机械拆分。

### 已完成

- T1 UI 可达性闭环
- T2 V1 → 当前视觉差异矩阵
- T3 Legacy consumer graph
- T4 删除 `SettingsPage.UserSettings` 空 route
- T5 删除 Social “更换背景图”假入口
- T6 删除 `SocialRoute.navigateToContacts` stale callback
- T7 App root 拆分，并移走 App Composable 中的 Repository / 网络写操作
- T14 第一轮高可信 dead-code / duplicate sweep
- Person 批量删除失败回滚：失败联系人恢复且保持删除前列表顺序
- Person 批量删除回归测试
- ContactDetailViewModel 注入 `ContactNetworkResolver`
- 多个 UI Dialog 从 `KoinComponentBy` 迁移到 `koinInject()`
- PlatformListPage Repository 边界收口到 ViewModel
- AddContactFieldDialog 系统字段/服务端清单去重
- ScanMarkerPickerDialog 正确透传 `show` 到 `WindowDialog`
- Social 删除无效 `updateCardImage` 兼容 API

### 当前进行中

- T8 Person：字母标题纯派生修复 + focused component split
- T9 ContactDetail / UserProfileDetail：头像保存/下载责任彻底迁出 UI + stale `!!` 收口
- T10 Card / CollectionDetail：Route 死参数清理 + import/export、selection、dialogs、presentation 拆分
- T11 Social polish / decomposition
- T12 Settings consolidation
- T13 `KoinComponentBy` UI consumers migration
- T14 全库第二轮 dead-code / duplicate sweep
- T15 最终测试、Debug APK、code review

## 本轮已确认的重点

### Person

`PersonViewModel` 已保持 Room Flow 为联系人列表事实源；批量删除失败不会再让 UI 因乐观移除而永久少数据。

仍待处理：

- `PersonPage` 的 `Ref<String?>` 字母状态改为 `index == 0 || currentLetter != previousLetter` 的纯派生逻辑。
- 将 toolbar/search/selection/list/import dialogs 拆成 focused composable，同时保持 `LazyListState`、多选状态、QAuxv 状态和返回键优先级。

### ContactDetail / UserProfileDetail

`ContactDetailViewModel` 已改为构造注入网络解析器，详情页不再依赖 resolver 静态入口。

仍待处理：

- `ContactDetailPage` 中裁剪头像、远程头像下载、头像文件删除/回退仍有 UI 侧文件/网络协调。
- `UserProfileDetailPage` 中 `selectedPlatform!!` 需要改为安全快照路径。

### Card / CollectionDetail

当前优先级是：

- 清理 `CardRoute` 中没有真正下传给 `CardScreen` 的兼容参数。
- 将导入/导出与选择态 UI 进一步拆分，避免一个 Screen 承担过多状态。

### Social / Settings

- Social 已移除无效 `updateCardImage` API；平台选择和 NFC 状态仍继续保留。
- PlatformListPage 的 Repository 读取已移到 ViewModel。
- SetupStepAccount / ImportFromPlatform / CreateContact / AddPlatform 等部分 UI consumer 已迁离 `KoinComponentBy`。
- `KoinComponentBy` 目前仍作为兼容桥存在，只有在确认零消费者后才删除。

## 验证约束

- 不创建新分支。
- 不因“V2”直接删除 V1 数据兼容层。
- 不保留用户无法完成的假交互。
- Composable 不直接承担 Repository 写操作或文件/网络业务协调。
- 每个删除必须先确认引用闭环。
- 每个大型拆分完成后进行 focused verification。
- T15 只能以真实 CI/build 结果标记通过。
- 连续提交会取消旧的 Build Debug APK run，因此需要在最终代码稳定后等待单个 run 完成再判断。

## 实施顺序

```text
T8 Person focused split + letter-header fix
  ↓
T9 ContactDetail / UserProfileDetail boundary cleanup
  ↓
T10 Card / CollectionDetail decomposition
  ↓
T11 Social visual polish / decomposition
  ↓
T12 Settings consolidation
  ↓
T13 KoinComponentBy consumer migration
  ↓
T14 second dead-code / duplicate sweep
  ↓
T15 JVM/Compose/Debug APK/final review
```