# Task List: Badger UI 重构

规格：[docs/ui-refactor-plan.md](../docs/ui-refactor-plan.md)
顺序与检查点：[tasks/ui-plan.md](./ui-plan.md)

状态：**时序重排**——U01–U04 可立即执行（作为 KMP 迁移前置清障）；U05–U24 在 KMP K4/K5 之后执行（详见 [docs/ui-refactor-plan.md](../docs/ui-refactor-plan.md) §8）。计划产出 2026-09-04。

> 通用验收（每个 UI 任务默认包含，不再逐条重复）：
> - [ ] Miuix 规范三自检：Card 点击不叠加 `combinedClickable`；WindowDialog 用外层 `if` 挂载（Pattern A）；不可点击信息行不用 ArrowPreference
> - [ ] 新增常量提取命名常量；无 `e.printStackTrace()`；新增逻辑有 `Log.d("当前类名Tester", ...)`
> - [ ] 验证：`./gradlew :app:compileDebugKotlin` 编译通过
> - [ ] UI 重构不改 VM 公开签名 / Repository / DB / 同步逻辑

---

## Phase U0 — 清障与基线

### Task U01: 删除 5 个死代码文件

**Description:** 删除探索确认无使用者的 5 个文件（~580 行）：`ui/components/BadgerListItem.kt`(158)、`ui/components/CollectionTheme.kt`(64)、`ui/components/LaunchActionHandler.kt`(184)、`ui/blur/InnerShadow.kt`(157)、`ui/blur/Vibrancy.kt`(16)。删除前各自 grep 一次全仓库 import 确认零引用。

**Acceptance criteria:**
- [x] 5 文件删除，全仓库无残留 import / 引用
- [x] 编译 + 全量单测绿

> **实施备注（2026-09-04 执行）：** 删除前逐符号 grep 发现**计划探索有误**——`CollectionTheme.kt` 的顶层函数（`textContentColorForBitmap`/`subTextColorFor`/`contentColorFor`）被 CollectionDetailHero/CollectionDetailList/CardComponents 使用，`LaunchActionHandler.kt` 的 `LaunchActionButtons` 被 FieldDetailDialog/PlatformDetailDialog 使用，**非死代码，已保留**。实际删除 3 个零引用文件：BadgerListItem(158 行，含 BadgerContactListItem/BadgerIconListItem)、blur/InnerShadow(157)、blur/Vibrancy(16)。编译 + 全量单测绿（486 绿 + Notification* 13 条已知基线红，见 memory known-test-failures）。

**Dependencies:** None. **Files:** 上述 5 个（全删）。**Scope:** XS

### Task U02: 空状态系统统一

**Description:** `EmptyStateView.kt` 并入 `BadgerEmptyState.kt`：保留 Simple/Compact 变体，新增带主题色「点击添加」CTA 的变体，落实 AGENTS.md 空态模板（"还没有XXX" + 主题色可点击链接）。迁移 Dashboard/Devices/Notification/ServerShortLink 4 个调用点。

**Acceptance criteria:**
- [x] `EmptyStateView.kt` 删除，4 个设置子页改用 `BadgerEmptyState`
- [x] 6 个空态页面（含 Card/Person）文案格式统一为模板句式，主题色可点击
- [x] 空列表页面居中提示、无空列表可滚动

> **实施备注（2026-09-04 执行）：** EmptyStateView 与 BadgerEmptyState 完整版本就同构，直接并入；CTA 加 `ButtonDefaults.textButtonColorsPrimary()` 落实主题色可点击。7 处调用（Dashboard 1 + Devices 2 + Notification 2 + ServerShortLink 2）全部迁移，未登录空态 title 统一「还没有XXX」句式（subtitle 保留「登录后同步」语义），空数据态「暂无/没有」→「还没有」。Card/Person 原本已是模板句式未动。

**Dependencies:** None. **Files:** `ui/components/BadgerEmptyState.kt`、`EmptyStateView.kt`、`dashboard/DashboardPage.kt`、`settings/devices/DeviceListPage.kt`、`settings/notification/NotificationPage.kt`、`settings/sync/ServerShortLinkPage.kt`。**Scope:** S

### Task U03: 图标体系选型 + MiuixIcons 残留清理（Q4 裁决：换血）

**Description:** 两步走：① **选型报告**——按标准「每图标独立 import（tree-shake 零全量膨胀）/ 统一线宽 / CMP 兼容（KMP 后 Android+iOS 共用）/ 许可友好」对比候选（Lucide / Phosphor / Tabler 的 Compose Multiplatform 移植，及 material-icons-extended 基线），输出选型结论落 `docs/kmp-dependency-matrix.md`；② **残留清理**——4 处 MiuixIcons 替换为等义 Material Icons（临时过渡，保持「统一 Material Icons」现状约束）：`App.kt:71`（4 个 Tab 图标）、`ContactFieldComponents.kt:151`、`NotificationPage.kt:380`、`SetupStepNavBarEffect.kt:66-69`。**全量替换 62 文件的图标 import 不在本任务执行**——并入 KMP K13（依赖切 CMP 坐标同批做，避免改两遍）。

**Acceptance criteria:**
- [x] 选型报告产出：候选矩阵 + 结论（图标库名 + 版本 + 集成方式），用户确认后生效
- [x] 全仓库 grep `MiuixIcons` 零命中（libdocs/ 除外），Tab 图标视觉等义、底部导航无回归
- [x] material-icons-extended 移除时机标注为 K13

> **实施备注（2026-09-04 执行）：** 选型结论 = **Lucide**（`com.composables:icons-lucide-cmp`，2025-12 活跃、MIT、iOS/Android/JVM/Wasm 全 target、2px 统一线宽），报告见 [docs/icon-selection.md](../docs/icon-selection.md)，**待用户确认后由 K13 执行**。4 处替换：App.kt 4 Tab 图标与 SetupStepNavBarEffect 预览图标 → `Icons.Filled.QrCodeScanner/Person/Folder/Settings`；ContactFieldComponents → 复用文件内既有 `Icons.Filled.ChevronRight`（原为死 import）；NotificationPage → `Icons.AutoMirrored.Filled.KeyboardArrowRight`。全仓库 `MiuixIcons` grep 零命中。

**Dependencies:** None（选型结论供 K13 消费）. **Files:** `App.kt`、`ContactFieldComponents.kt`、`NotificationPage.kt`、`SetupStepNavBarEffect.kt`、新建 `docs/icon-selection.md`。**Scope:** S

### Task U04: AGENTS.md 文档纠偏

**Description:** 修正 4 条与代码现状不符的记录：①「AnimatedContent tween(500) jank」→ 已改 300ms，现存问题改为「弹簧振荡 easing 待收敛（见 UI 重构 U09）」；②「设置 15 个 sub-page」→ 17 项（16 实现 + UserSettings 空占位）；③「material-icons-extended 只用 4 个图标」→ 实际 70 种/62 文件；④「手电筒不关 bug」→ 已在 ScannerCamera.kt DisposableEffect 修复，移出已知问题。

**Acceptance criteria:**
- [x] 4 条记录与代码一致，并在「重要参考文件」表登记 UI 重构三件套链接

> **实施备注（2026-09-04 执行，实际纠偏多于原 4 条）：** ① tween(500)→已改 300ms，jank 项改记「弹簧振荡 easing 待收敛（U09）」（导航节 + 性能节两处）；② 设置子页 15→17（16 实现 + UserSettings 空占位，Route.SettingsPage 实测）；③ 图标「4 个」→70 种/61 文件（U0 后实测）；④ 手电筒 bug 销账（ScannerCamera.kt onDispose 已修复）。**追加纠偏**：⑤ Room version 6→17 + Schema 演进表补全 1→17 全链（16 条迁移）；⑥ FTS4 红线改 LIKE 现状（FTS4 已于 v14→v15 退役，迁移规范 + 代码红线表两处）——此发现同时推翻 KMP 计划「FTS4 红线可保」前提，K02 spike 内容需相应调整；⑦ PendingUpload→Outbox 表层对齐（概述/技术栈/sync 树/队列契约/WorkManager 段/测试清单/DI 示例），同步章节全面刷新仍待独立任务；⑧ 参考文件表清理失效链接（Models.kt/Daos.kt）+ 登记 UI 重构与 KMP 文档五件套。

**Dependencies:** None. **Files:** `AGENTS.md`。**Scope:** XS

---

## Phase U1 — 设计系统层

### Task U05: BadgerDesignTokens v2

**Description:** 扩展 `ui/designsystem/BadgerDesignTokens.kt`：`BadgerSpacing`（4/8/12/16/24/32 六档，对应 mobile-app-design xs–xl 标度）、`BadgerRadius`（container 20 / card 16 / inner 12 / chip 8 层次化）、`BadgerElevation`（沿用现值）、**新增 `BadgerMotion`**（DURATION_FAST=200 / DURATION_BASE=300 / spring 规格：push 用 dampingRatio 0.9、stiffness 中档；expressive 交互如 NavBar 水滴保持现有阻尼参数）、`BadgerTypeScale`（title1–footnote2 语义别名，指向 `MiuixTheme.textStyles`）。本任务只定 token + KDoc 用法说明，不迁移页面。

**Acceptance criteria:**
- [ ] 五组 token 齐备，每个 token 有 KDoc 说明使用场景
- [ ] `NavTransitions.kt` 的 `DURATION_MS` 与 `BadgerMotion` 单一来源（一处引用另一处，不留双份常量）
- [ ] 编译绿

**Dependencies:** None. **Files:** `ui/designsystem/BadgerDesignTokens.kt`、`ui/navigation/NavTransitions.kt`。**Scope:** S

### Task U06: BadgerSemanticColors 扩展 + 深色 2.0 检查

**Description:** `BadgerSemanticColors.kt` 增加 success/warning/danger/info 四组语义色（明暗两套，来源对齐 Miuix colorScheme 派生或固定品牌色板），供同步状态、操作历史 StatusBadge、通知等使用。同时检查深色模式背景非纯黑（Miuix 默认色板确认，若为纯黑则在语义层给 tinted 替代，不动 Miuix 源码）。

**Acceptance criteria:**
- [ ] 四组语义色齐备 + `@Composable` 取色函数
- [ ] 深色模式背景色值记录在案（非 #000 即通过；纯黑则给结论与建议）
- [ ] 不改变现有任何页面外观（纯新增）

**Dependencies:** U05. **Files:** `ui/designsystem/BadgerSemanticColors.kt`。**Scope:** S

### Task U07: 滚动避让协议组件化（P6）

**Description:** 新建 `ui/components/FloatingBarScaffold.kt`：封装「读取 `LocalFloatingBarBottomPadding` + 计算 LazyColumn contentPadding」的统一实现，提供 `Modifier.badgerBottomBarPadding()` 或带 slots 的 Scaffold 变体。迁移 4 个主页的散落补偿：`PersonPage.kt:354/381/453-456`、`CardPage.kt:351/370/480-482`、`SettingsPage.kt:102-105`、`SocialPage.kt:311-342`。**`LocalFloatingBarBottomPadding` 值保持 84dp 不变。**

**Acceptance criteria:**
- [ ] 4 主页补偿逻辑各只有一处实现（组件内），页面侧无手算 padding
- [ ] 经典导航形态（padding=0）与浮动形态（84dp）下四页均能滚到底部最后一项完整可见
- [ ] 三档效果模式下抽查滚动（模糊不遮挡末项）

**Dependencies:** None. **Files:** 新建 `ui/components/FloatingBarScaffold.kt`、`PersonPage.kt`、`CardPage.kt`、`SettingsPage.kt`、`SocialPage.kt`。**Scope:** M

### Task U08: 通用组件下沉

**Description:** 把仅联系人详情在用的 `SectionCard`、`ToolbarAction`、`BasicInfoCard` 从 `pages/person/contact/detail/ContactFieldComponents.kt` 提升到 `ui/components/`（包迁移 + import 更新），KDoc 标注使用场景。本任务只移动不改视觉。

**Acceptance criteria:**
- [ ] 组件落位 `ui/components/SectionCard.kt`（或合并文件），原 import 处更新
- [ ] ContactDetail 页面渲染与迁移前逐像素等价（人工对比）

**Dependencies:** None. **Files:** `ContactFieldComponents.kt`、新建 `ui/components/SectionCard.kt`。**Scope:** S

---

## Phase U2 — 主框架

### Task U09: 动效系统收敛（P5）

**Description:** `NavTransitionEasing(0.8f, 0.95f)` 弹簧振荡曲线退役：push/pop 转场改为 `BadgerMotion` 定义的低弹 spring（dampingRatio≈0.9，无可见振荡，300ms 级收敛）；模态类（Scanner 进入）用 tween + 标准 FastOutSlowIn。保留 `NavTransitions` 的方向分发结构不变。

**Acceptance criteria:**
- [ ] 转场目测无振荡回弹，≤ 300ms 收敛，快滑快切不卡帧
- [ ] 全部动效常量出自 `BadgerMotion`，无散落 tween(500)/tween(300) 裸数字（CardComponents.kt:79、CollectionDetailHero.kt 的 tween(300) 一并换 token）
- [ ] 「效果模式 = 无」时 U11 前保持现状（本任务不接降级）

**Dependencies:** U05. **Files:** `ui/navigation/NavTransitions.kt`、`ui/navigation/NavTransitionEasing.kt`、`pages/card/CardComponents.kt`、`pages/card/CollectionDetailHero.kt`。**Scope:** M

### Task U10: 视觉特效系统重做（Q1 裁决；执行点 = KMP K14，本任务持有规格与验收）

**Description:** 用户裁决：现有模糊/液态玻璃实现是「拼凑抄袭、达不到期望形态」的产物，UI 重做时一并**推倒重做**而非修补。执行点定在 **KMP K14**（UI 已进 shared），**Skia-first 单套实现双端复用**——拒绝 AGSL 写一遍再移植 Skia 的两遍成本。分两步执行：

**第一步（规格先行）**：《特效视觉规格》**v0.9 已产出**（[docs/effect-visual-spec.md](../docs/effect-visual-spec.md)，2026-09-04）——参考基准用户已确认（iOS 26 Liquid Glass + iOS 磨砂），含双家族材质体系（BadgerMaterials 五档磨砂 / BadgerGlass 两档液态）、7 层玻璃构成、流动性规格、三档降级、性能预算与技术映射，**待用户签收**。
**第二步（实现）**：重写 `ui/LiquidGlassNavBar.kt`(541 行) 与 `ui/blur/` 全家（BlurHelper/SphereSurface/Lens/InteractiveHighlight/LiquidWobble/DampedDragAnimation），FloatingToolbar/FAB/对话框 scrim 的玻璃材质统一走新系统；shader 与材质参数全部 token 化进 `BadgerDesignTokens`，组件 API 面向「材质语义」而非「渲染路径」。

**Acceptance criteria:**
- [ ] 《特效视觉规格》（docs/effect-visual-spec.md）用户签收（v0.9 已产出，基准 = iOS 26 Liquid Glass + iOS 磨砂；待确认默认档位 opt-in 交互与参数微调）
- [ ] 规格第 9 节验收清单逐项通过（双端截图归档，对照 iOS 26 实机）
- [ ] 旧 blur/ 实现删除无遗留；导航栏默认形态按重做后效果定（Q1 原问题）

**Dependencies:** KMP K13（UI 进 shared）. **Files:** `ui/LiquidGlassNavBar.kt`、`ui/blur/` 全部（重写）、新建 `docs/effect-visual-spec.md`。**Scope:** XL——按「规格 commit → 实现 2–3 个 commit」拆分，每个 commit 双端可编译

### Task U11: 动效降级入口接通

**Description:** 「效果模式 = 无（NONE）」时路由转场与水滴动画直切/暂停（读取 `NavBarConfig.EffectMode`），落实「减弱动效」可访问性路径。UiSettingsPage 的效果模式说明文案同步补充「同时减少动画」。

**Acceptance criteria:**
- [ ] 效果模式切到无：转场直切（≤ 80ms 或无动画）、扫描线/水滴停摆
- [ ] 效果模式恢复后动画即恢复，无需重启
- [ ] UiSettings 文案更新

**Dependencies:** U05、U09. **Files:** `ui/navigation/NavTransitions.kt`、`App.kt`、`pages/settings/UiSettingsPage.kt`。**Scope:** S

---

## Phase U3 — 四大 Tab

### Task U12: SocialPage Expressive 门面重构（含 P7 QR 色差）

**Description:** 「我的名片」按门面气质升级：① `SocialProfileHeader` hero 化（头像大尺寸 + 姓名 title1 + 签名弱化层级 + 背景图缘渐隐）；② `QrCodeCard.kt:70-96` 浅色模式色差修复——QR 位图背景改为透明渲染 + 卡片容器统一取色（或对比锁定 foreground/background 对，深浅两态截图验证）；③ 平台 chips 与信息卡间距/圆角 token 化；④ NFC 写入弹窗对齐 DialogButtonRow。

**Acceptance criteria:**
- [ ] QR 卡深浅两模式下与容器背景无可见环形色差
- [ ] 头部信息层级：姓名 > 签名 > 短链状态，一眼可辨
- [ ] NFC 写入流程冒烟（弹窗进度 → 成功 → 3s 后关闭）

**Dependencies:** U05–U08. **Files:** `SocialPage.kt`、`SocialPageComponents.kt`、`QrCodeCard.kt`。**Scope:** L（拆 2 commit：色差修复先行）

### Task U13: PersonPage 安静列表 + 拆文件（P8）

**Description:** 805 行拆为 `PersonPage.kt`（骨架+状态）+ `PersonListSections.kt`（列表分组/索引条）+ 既有 Components 复用。列表降噪：行高统一 56–64dp、头像 44dp、二级信息 footnote1 弱化、分组字母头 sticky 视觉统一；`LetterIndexBar` 热区扩至 48dp 宽；搜索展开/收起动效接 `BadgerMotion`。

**Acceptance criteria:**
- [ ] 主文件 ≤ 500 行，无逻辑变化（Paging 分页、FTS 搜索、多选行为不变）
- [ ] 索引条拖动气泡仍居中跟手，热区 ≥ 48dp
- [ ] 多选模式进出 + BackHandler 正常，FloatingToolbar 避让正确

**Dependencies:** U05–U08. **Files:** `PersonPage.kt`、`PersonListComponents.kt`、新拆文件。**Scope:** L

### Task U14: CardPage + CollectionDetail 设计探索（P8，Q3 裁决：怎么好看怎么来）

**Description:** 726 行拆文件；网格形态按 Q3 裁决做**设计探索**：产出 2–3 套候选方案（A：2 等分卡+内部层次升级；B：不等宽 bento；C：其他如横滑大卡+网格混合），每套附静态预览，用户按视觉品质定稿后实现。无论哪种形态都落实：背景图上缘暗角渐隐保证标题对比度、计数弱化层、标题层级、空态用统一组件；`CollectionDetailHero` 头图与返回栏玻璃化对齐浮层原则（材质走 U10 重做后的新系统）。

**Acceptance criteria:**
- [ ] 候选方案对比材料产出并经用户定稿，实现与定稿一致
- [ ] 主文件 ≤ 500 行；带背景图卡片标题在任意图片上可读（暗角/渐隐遮罩）
- [ ] 创建/重命名/换背景/多选/导入冲突全对话框三路径 flag 重置正确；长按换背景、FAB/工具条切换正常

**Dependencies:** U05–U08、U10 特效系统（玻璃材质）. **Files:** `CardPage.kt`、`CardComponents.kt`、`CardDialogs.kt`、`CollectionDetailPage.kt`、`CollectionDetailHero.kt`、新建候选预览（临时文件可删）。**Scope:** L

### Task U15: SettingsPage 主页 + Dashboard bento 化（P12、Q5）

**Description:** 设置主页（292 行）：账号卡 bento 化（头像块 + 登录态 + 未读通知徽章的组合大卡），导航分组卡对齐 Spacing/Radius token，ArrowPreference 全量语义合规复查。Dashboard（263 行）：三等分 StatCard → bento（主指标大卡 2×1 + 次指标 1×1 + 最近联系人横滑带通栏），空态统一。

**Acceptance criteria:**
- [ ] 两页无三等宽卡模式；信息优先级（账号 > 同步 > 配置 > 关于）视觉成立
- [ ] 未登录态两页均给出引导（登录 CTA）
- [ ] 未读角标数字与 NotificationPage 一致

**Dependencies:** U05–U08. **Files:** `pages/settings/SettingsPage.kt`、`pages/dashboard/DashboardPage.kt`。**Scope:** M

---

## Phase U4 — 联系人详情页群（高风险区，每任务独立 commit）

### Task U16: ContactDetail 内容区统一

**Description:** `ContactDetailPage.kt`(610) + Components 迁移到 U08 下沉的 SectionCard：基本信息/字段分组/标签三卡视觉统一（圆角、内边距、标题行 style 一致）；`ContactTagsCard` 进度条换语义色；头像区与 AvatarPreviewDialog 对齐 token。

**Acceptance criteria:**
- [ ] 三类卡片标题/间距/圆角与设置页卡片一致（跨页对照）
- [ ] 字段增删改、平台编辑、标签选择全走查一遍无行为变化
- [ ] 撤销（snapshot）入口冒烟

**Dependencies:** U08、U15. **Files:** `ContactDetailPage.kt`、`ContactDetailComponents.kt`、`ContactFieldComponents.kt`、`TagChip.kt`。**Scope:** L

### Task U17: 对话框体系标准化（14 dialogs）

**Description:** `dialogs/` 子包 12 文件 + DialogHost 约 3,450 行做一致性扫描迁移（不改交互逻辑）：全部 WindowDialog 确认 Pattern A 外层挂载；按钮统一 `DialogButtonRow`（单个按钮 fillMaxWidth）；输入框错误提示样式统一；`SyncOptionsSheet` 保留唯一 BottomSheet 地位不动。

**Acceptance criteria:**
- [ ] 全部对话框逐个打开/取消/确认：视觉一致、三路径 flag 重置
- [ ] `if (show)` 模式 grep 抽检通过（无 `show = showXxx` 直传）
- [ ] RegionPickerDialog（512 行三级结构）滚动与选择正常

**Dependencies:** U16. **Files:** `pages/person/contact/detail/dialogs/` 全部、`ContactDetailDialogHost.kt`、`ContactDetailDialogs.kt`。**Scope:** L（迁移式，可拆 2–3 commit）

### Task U18: UserProfileDetailPage / CreateContactPage 收敛（P8）

**Description:** `UserProfileDetailPage.kt`(799) 拆 Page + Components + FloatingToolbar 三文件；`CreateContactPage.kt`(566) 的 MANUAL/AUTO 双模式表单视觉与详情页编辑卡对齐（输入框、解析预览行、模式 Tab 样式统一）。

**Acceptance criteria:**
- [ ] 两主文件 ≤ 500 行；我的名片编辑保存、create-then-edit 链路冒烟
- [ ] 头像裁剪 Dialog 在两页行为一致
- [ ] 全量单测绿

**Dependencies:** U16. **Files:** `UserProfileDetailPage.kt`、`UserProfileDetailComponents.kt`、`CreateContactPage.kt`。**Scope:** M

---

## Phase U5 — 外围页

### Task U19: Scanner 状态治理 + 动效 token 化（P9）

**Description:** `ScannerPage.kt:88-128` 15+ 状态变量按「相机预览 / 扫描结果 / 模式对话框」拆三子组件（无关状态隔离），每 Composable ≤ 10 个 mutableStateOf；扫描线 tween(2000/2500)、模式切换 tween(150) 接 `BadgerMotion`；ResultDialog/PhotoModeDialog/ScanModeDialog 按钮与间距对齐标准；相机资源 DisposableEffect 清理复核（torch 已修）。

**Acceptance criteria:**
- [ ] 单 Composable 状态变量 ≤ 10；扫描、连拍、拍照 OCR 全流程冒烟
- [ ] ML Kit 识别器仍为页面级复用；`Tasks.await()` 不出现在 analyzer 线程
- [ ] 权限拒绝/授予路径 UI 正常

**Dependencies:** U05、U09. **Files:** `ScannerPage.kt`、`ScannerUi.kt`、`ScannerComponents.kt`。**Scope:** L

### Task U20: Auth + SetupGuide token 对齐

**Description:** `AuthScreens.kt` 三态 + `SetupGuidePage` 6 步接 token：输入框、分段控件、验证码卡、步骤进度条统一规格；引导第 4 步（底栏特效预览卡）与 U03 后的图标/材质一致；SummaryCard 复核。

**Acceptance criteria:**
- [ ] 登录→注册→忘记密码三态切换动画连贯无跳变
- [ ] 引导 6 步走通（服务器→登录→资料→平台→外观→完成），滑动锁定正常
- [ ] 验证码/Captcha 卡片样式与全局一致

**Dependencies:** U05–U08. **Files:** `AuthScreens.kt`、`AuthChrome.kt`、`RegisterExtraFields.kt`、`SetupGuideComponents.kt`、`SetupStepNavBarEffect.kt`。**Scope:** M

### Task U21: 设置子页群统一过检（拆分后 17 个实现子页）

**Description:** 拆分后 17 个实现子页逐页按清单过检：PullToRefresh 页（Dashboard/Notification/DeviceList/ServerShortLink）手势一致；TabRowWithContour 页（OperationHistory/Notification/TagManager×2）样式一致；列表页间距/分节统一；`UserSettings` 空占位在 `SettingsSubPage.kt` 加注释说明（或删除该占位，顺手裁决）。

**Acceptance criteria:**
- [ ] 17 页逐页点开无样式 outlier（间距/圆角/字号/按钮）
- [ ] NfcSettings（纯 NFC）/ AiOcrSettings（AI OCR）/ ServerShortLinks（含高级项）三页职责边界清晰无重复；AI OCR API Key 输入仍经 SafeLog 脱敏路径
- [ ] 操作历史/通知/设备管理的左滑删除与下拉刷新手势一致

**Dependencies:** U15、U25. **Files:** `pages/settings/` 各子页（逐页小改）。**Scope:** L（迁移式，可拆 2–3 commit）

### Task U25: NfcSettings 拆分（Q2 裁决：拆子页）

**Description:** 拆解 `NfcSettingsPage.kt`(414 行) 的三主题混杂：① `Route.SettingsPage` 新增 `AiOcrSettings` data object，`SettingsSubPage.kt` 分发器接线，设置主页「配置」卡加入口；② AI OCR 配置（API 地址/API Key/模型选择等）独立为 `AiOcrSettingsPage.kt` + 专属 VM；③ 短链高级项（认证头/更新端点/请求体）移入既有 `ServerShortLinkPage.kt`；④ NfcSettings 只留 NFC 写入配置。**建议在 U21 之前执行。**

**Acceptance criteria:**
- [ ] Route 新增 1 项 + 分发器接线正确；NfcSettings 保留仅 NFC 内容，无死链
- [ ] AI OCR API Key 输入经 SafeLog 脱敏路径不变；涉及对话框三路径 flag 重置正确
- [ ] 设置主页「配置」分组入口与拆分后页面一一对应；编译 + 全量单测绿

**Dependencies:** U15. **Files:** `Route.kt`、`SettingsSubPage.kt`、`NfcSettingsPage.kt`、新建 `AiOcrSettingsPage.kt`(+VM)、`ServerShortLinkPage.kt`、`SettingsPage.kt`。**Scope:** M

---

## Phase U6 — 全局验收

### Task U22: 深色模式全页走查

**Description:** 四 Tab + 详情 + 扫码 + 16 子页 + 引导在 Dark/MonetDark 逐页走查：背景非纯黑确认、玻璃对比度、QR 深浅两态、平台品牌色在深色下的可读性、图片占位。

**Acceptance criteria:**
- [ ] 走查清单全部通过并记录（问题即改）
- [ ] 6 种色彩模式切换后无残留旧配色（Monet 动态色抽查）

**Dependencies:** U12–U21. **Scope:** M

### Task U23: 可访问性走查

**Description:** ①对比度：正文 ≥ 4.5:1、关键控件 ≥ 7:1（重点：玻璃上的文字、弱化二级文本、TagChip 文字）；②触控：全 App 可点元素 ≥ 48dp（重点 LetterIndexBar/chips/工具条）；③TalkBack 关键路径（扫码添加、联系人编辑、设置切换）可操作；④减弱动效路径 = U11 效果模式。

**Acceptance criteria:**
- [ ] 对比度抽测记录（至少 10 个高风险点）
- [ ] 热区不足处修复完毕
- [ ] TalkBack 三条关键路径走通

**Dependencies:** U11、U22. **Scope:** M

### Task U24: 性能基线

**Description:** 记录重构后基线：冷启动时间、联系人 500 条滚动帧率（macrobenchmark 不引入，用 `dumpsys gfxinfo` 即可）、三档效果模式在 GpuCompat 黑名单机型的降级正确性、启动内存。结论写入 AGENTS.md「已知性能问题」章节更新。

**Acceptance criteria:**
- [ ] 基线数据记录在案；无新增 jank（对比重构前体感/帧数据）
- [ ] GpuCompat 三档降级在限制设备模拟下正确
- [ ] AGENTS.md 性能章节刷新（销账已修复项）

**Dependencies:** U22、U23. **Scope:** M
