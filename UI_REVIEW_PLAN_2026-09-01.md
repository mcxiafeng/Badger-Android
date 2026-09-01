# Badger-Android UI / V1 / Legacy Cleanup — Global Audit Plan

**工作分支：** `refactor/dev-cleanup-2026-08-31`（继续使用，不创建新分支）

## 当前进度

本轮全局探索、无效入口清理和 App root 职责拆分已经完成。后续工作继续按大型 UI 文件的职责边界推进，不为了减少文件数量进行机械拆分。

### 已完成

- T1 UI 可达性闭环
- T2 V1 → 当前视觉差异矩阵
- T3 Legacy consumer graph
- T4 删除 `SettingsPage.UserSettings` 空 route
- T5 删除 Social “更换背景图”假入口
- T6 删除 `SocialRoute.navigateToContacts` stale callback
- T7 App root 拆分，并移走 App Composable 中的 Repository / 网络写操作
- T14 第一轮高可信 dead-code / duplicate sweep

### 当前进行中

- T8 Person
- T9 ContactDetail / UserProfileDetail
- T10 Card / CollectionDetail
- T11 Social polish / decomposition
- T12 Settings consolidation
- T13 `KoinComponentBy` UI consumers migration
- T14 全库第二轮 dead-code / duplicate sweep
- T15 最终测试、Debug APK、code review

## 已确认的当前重点

### Person

`PersonPage` 已完成数据源和导航方向审计。联系人列表继续以 Room Flow 为唯一事实源。

已确认需要继续处理的 UI 问题：

- 字母标题逻辑应完全由 `index == 0 || currentLetter != previousLetter` 派生，避免 `Ref<String?>` 在 LazyColumn 组合期间保存上一次组合顺序状态。
- toolbar/search/selection/list/import dialogs 仍集中在同一 Screen，需要按 focused UI component 拆分。
- 拆分时保持 `LazyListState`、多选状态、QAuxv 导入状态和返回键优先级不变。

### ContactDetail / UserProfileDetail

`ContactDetailViewModel` 已经拥有 `AvatarStorage` 与 `saveAndApplyAvatar()`，下一步需要把 Screen 中遗留的 Bitmap 文件保存协调彻底迁移到 ViewModel API，并继续减少 legacy `ContactNetworkResolver` companion 依赖。

### Card / CollectionDetail

优先清理 Route 中没有消费者的兼容参数，然后拆出 import/export、selection、dialog 和 list/grid presentation。

### 验证约束

- 不创建新分支。
- 不因“V2”直接删除 V1 数据兼容层。
- 不保留用户无法完成的假交互。
- Composable 不直接承担 Repository 写操作或文件/网络业务协调。
- 每个删除必须先确认引用闭环。
- 每个大型拆分完成后进行 focused verification。
- T15 只能以真实 CI/build 结果标记通过。

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
T13 KoinComponentBy UI consumer migration
  ↓
T14 second dead-code / duplicate sweep
  ↓
T15 JVM/Compose/Debug APK/final review
```
