# Badger-Android UI / V1 / Legacy Cleanup — Global Audit Plan

**工作分支：** `refactor/dev-cleanup-2026-08-31`（继续使用，不创建新分支）  
**当前审计基线：** `57ae1af13b2d8fc2bafdf2ec5f421c707ab34e95`

## 本轮结论

本次先做全局探索，再进行职责拆分。目标不是把所有文件“拆小”，而是清除混合时代 UI、无效入口和旧架构耦合，同时恢复有价值的 V1 视觉设计。

项目 `skills/README.md` 明确包含 50 个 `SKILL.md`、15 组 skill；本轮已重点阅读并采用：`using-agent-skills`、`planning-and-task-breakdown`、`compose-expert`、`compose-ui`、`android-viewmodel`、`android-architecture`、`code-simplification`、`deprecation-and-migration`、`code-review-and-quality`、`bug-hunter`、`android-testing`、`android-accessibility`、`edge-to-edge`、`android-coroutines`、Navigation 相关 skills。fileciteturn209file0L2-L2

## 已完成的全局结构扫描

### UI 入口域

当前 app UI 按功能分为：

- `auth/`
- `person/` + `person/contact/`
- `card/`
- `scanner/`
- `settings/`
- `setupguide/`
- `social/`
- `dashboard/`
- 通用 `ui/`

主导航仍由 `HorizontalPager` + 自定义 `AppNavigator` 管理；`Route` 明确包含 MainTabs/Login/Register/Scanner/ContactDetail/CollectionDetail/CreateContact/SettingsSubPage。`SettingsPage` 仍包含通知、设备、Dashboard、改密、短链等真实功能。fileciteturn227file0L2-L2

### 明显大型文件

| 文件 | 当前规模/状态 | 判断 |
|---|---|---|
| `App.kt` | ~27 KB，启动、认证、DeepLink、Pager、路由、Blur/Lifecycle 混合 | **必须拆** |
| `PersonPage.kt` | ~43 KB | **必须拆** |
| `ContactDetailPage.kt` | ~44.6 KB，20+ UI flags | **必须拆** |
| `UserProfileDetailPage.kt` | ~37 KB | **必须拆** |
| `CardPage.kt` | ~36.1 KB | **必须拆** |
| `CollectionDetailPage.kt` | ~34.7 KB | **必须拆** |
| `AuthScreens.kt` | ~41 KB | **必须拆** |
| `SetupStepAccount.kt` | ~25.8 KB | **必须拆/重整** |
| `PhotoModeDialog.kt` | ~30.7 KB | **重点检查是否应为 Dialog + components** |
| `ScanModeDialog.kt` | ~22 KB | **重点检查职责** |
| `LiquidGlassNavBar.kt` | ~21 KB | **按视觉/动画/interaction 职责审视** |

Person/contact 目录目前已有大量细分 Dialog/Components，但页面自身和 ViewModel 仍然承担过多状态与流程协调，因此下一阶段按职责继续收口，而不是再堆文件。fileciteturn151file0L2-L2

## V1 / 历史设计扫描结果

`dev` 历史存在明确的 UI 设计重置提交，不能简单视为垃圾代码：

- `8cac87df`：`重置Social界面`
- `303c45615`：`重置Auth界面`
- `fb4be169`：`重置Setup界面`
- `b31c7f105`：`feat(d3): 核心页面重设计 — D4 组件采纳 + 设计 Token`
- `36b267842`：`P2 重复代码提取 + Compose 收尾 + DI 模块拆分`

其中 Social 历史设计明确包含 TopAppBar 操作、个人资料卡、平台 chips、平台信息卡、QR 卡；Person/Card/Settings/Social 也曾统一过 `BadgerSpacing`、空状态和确认 Dialog。说明“V1 设计”本身是需要被保留/重新打磨的设计资产，而不是要连同 V1 架构一起回滚。fileciteturn226file0L2-L2

## Legacy / 架构混合扫描

### 必须保留但要隔离

- V1 Room entities / DAOs：`AGENTS.md` 明确仍承担兼容职责，不能因为“V2 已存在”就直接删除。
- V2 cache / queue / sync：是当前主数据路径，UI 拆分不能回退到 V1 数据直接访问。
- 自定义 `AppNavigator`：当前仍是实际导航系统；Navigation 3 skill 只作为架构参考，不在本轮无理由替换。

### 必须迁移后删除

- `KoinComponentBy` 的 UI 消费者：迁移到 constructor injection / Route-level ViewModel 后再删除兼容层。
- 旧 UI wrapper / pass-through helper：只有在完成消费者确认后删除。

### 明确违反当前 UI 边界、需要在拆分时处理

`App.kt` 当前仍存在 `scope.launch(Dispatchers.IO)` 后直接调用 `userProfileRepository.updatePlatformField(...)` 的 UI 层写操作；这违反项目 `AGENTS.md` 的 UI → ViewModel → UseCase/Repository 边界，必须随着 App 拆分移到合适的 ViewModel/use case。`ContactDetailPage` 的头像保存也仍在 Composable 中直接执行 `Methods.saveBitmapAsAvatar(...)`，应一并收口。fileciteturn95file0L2-L2

## 无效 / 死 UI 扫描结果

### 已确认高可信删除候选

1. **`SettingsPage.UserSettings`**：`SettingsSubPage` 对它的分支是完全空的 `{}`，没有实际界面。该 route 仍留在 `Route.kt` 中，属于无效 destination，应在确认无外部/反射引用后删除 route + 分支。fileciteturn197file0L2-L2 fileciteturn227file0L2-L2
2. **Social “更换背景图”**：当前行为走图片选择/裁剪后只提示“不支持自定义背景图”，属于不可完成的假入口；没有完整能力链就应该删除，而不是继续维护状态和 UI。
3. **`SocialRoute.navigateToContacts`**：当前代码已经标为 `UNUSED_PARAMETER`，需要完成引用闭环后删除，而不是继续保留兼容参数。

### 明确不是死 UI

- `Dashboard`：虽然不是一级 Tab，但 Settings 有真实入口，属于有效页面。fileciteturn228file0L2-L2
- Notifications / Devices / ServerShortLinks / SyncStatus 等：存在 Settings 路由与具体页面，不按“旧代码”处理。fileciteturn197file0L2-L2

## Skills 对本轮重构的约束

- `planning-and-task-breakdown`：先只读探索、建立依赖图，再执行；任务保持可验证，L/XL 再拆成更小任务。fileciteturn210file0L2-L2 fileciteturn213file0L2-L2
- `compose-ui` / Compose patterns：优先 Route → Screen，状态向上提升，Screen 尽量无状态；使用稳定参数和 `collectAsStateWithLifecycle()`。fileciteturn162file0L2-L2 fileciteturn189file0L2-L2
- `android-viewmodel`：StateFlow 持久状态、SharedFlow 一次性事件，UI 不直接承担业务逻辑。fileciteturn195file0L2-L2
- `code-simplification`：简化的目标是减少理解成本，不是单纯减少行数；必须先理解再改。fileciteturn160file0L2-L2
- `deprecation-and-migration`：迁移消费者 → 验证零使用 → 再删除旧系统。fileciteturn165file0L2-L2
- `code-review-and-quality`：重构必须检查 correctness/readability/architecture/security/performance，并优先消除结构性复杂度。fileciteturn154file0L2-L2
- `android-accessibility`：48dp touch target、正确 contentDescription、selection state semantics、heading 等都要在拆分中保留。fileciteturn163file0L2-L2
- `edge-to-edge`：使用 Scaffold/Insets 单一来源，避免状态栏/导航栏/IME 重复 padding。fileciteturn156file0L2-L2
- `android-testing`：行为验证优先，必要时增加 Compose/视觉回归；本项目另有禁止截图工具的 AGENTS 约束。fileciteturn172file0L2-L2
- `bug-hunter`：大型扫描优先按领域进行，而不是把整个项目扁平化一次读完；确认 Bug 后再修。fileciteturn173file0L2-L2

## 实施顺序

```text
T1 UI 可达性闭环
   ↓
T2 V1 → 当前视觉差异矩阵
   ↓
T3 Legacy consumer graph
   ↓
T4 删除 UserSettings 空 route
T5 删除 Social 假背景图入口
T6 删除确认无引用的 stale callbacks
   ↓
T7 App root 拆分 + 移走 UI business/IO
   ↓
T8 Person 拆分
T9 ContactDetail / UserProfileDetail 拆分
T10 Card / CollectionDetail 拆分
T11 Social 视觉恢复 + 拆分
T12 Settings 收口
   ↓
T13 KoinComponentBy UI consumers 迁移
T14 全库 dead-code / duplicate sweep
   ↓
T15 测试 + Debug APK + 最终 code review
```

## 当前阶段

**正在进行：全局审计收口。**  
结构扫描、skills 阅读和初步 V1/legacy/dead-UI 分类已经完成；下一步先闭环 T1~T3 的引用证据，然后再开始删除无效 UI 与职责拆分。

## 交付标准

- 不创建新分支。
- 不因“V2”直接删除 V1 数据兼容层。
- 不为了文件数量机械拆分。
- 不保留用户无法完成的假交互。
- 不把业务/IO 留在 Composable。
- 每个结构性删除都有引用证据。
- 每个大拆分都有 focused verification。
- 最终必须更新 `CODE_REVIEW_FOLLOWUP_2026-09-01.md` 并以实际 CI/build 结果为准。
