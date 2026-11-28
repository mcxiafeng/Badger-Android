# Badger 全局 UI 规范

> 适用范围：Badger 客户端全部 Compose 页面，包括当前 `app/src/main` 与后续接入的共享 UI。任何新页面 / 新组件动手前先读本文。
> 当前工程适配：Miuix 锁版本为 0.9.5-rc01，现有客户端使用 Hilt；`settings.gradle.kts` 当前仅包含 `:app`。下文共享目录、token 和封装是设计目标/候选，使用前核对实际源码；本次安装不迁移架构或增加图标依赖。
> 视觉目标：Miuix（文档记载 0.9.5，**实际 API 以本仓库 Gradle 锁版本为准**）+ 单一 Lucide 图标语义 + 经审查可复用的 Badger 组件；旧项目遗产仅保留成熟行为，不自动当作视觉标杆。
> 演进记录：由 2026-09 名片夹页开发笔记升级而来，趋势调研结论见文末。

---


## 0. 强制适用范围、来源与工作流程（新增）

- **正式 UI 禁止模仿设计说明书**：用户看到的是当前资料/任务/操作/真实风险，而不是“先存草稿、预览后确认、此数据仅供演示”这种方案解说。技术 JSON、generation、编码字节数、原始同步版本默认归诊断页。规范位于 [UI_QUALITY_GATES.md](../../docs/ai/UI_QUALITY_GATES.md)。
- **不允许自动造模拟功能**：相机、相册、扫码、手动同步、恢复、云端等以真实项目能力为准。功能未交付则不产出虚假可点入口。
- **图标/文字有边界**：顶栏 `返回/搜索/更多/关闭` 优先项目统一 Lucide IconButton（有 contentDescription）；`保存/确认覆盖/公开发布/删除账号` 保留具体文字。底部四 Tab 不因“图标优先”去掉识别所需的标签。
- **旧代码只能验证“能运行”和行为，不自动验证“好看”**：复用已验证组件与业务逻辑，灰色胶囊按钮墙、重复状态文字、长文案必须重新设计。
- **实现严格遵守设备禁止截图**：不使用任何设备截图/截屏工具。改用编译、Compose semantics/UI tests、UI 元素树/非截图 Android 调试以及人工目视验收（如另行许可）。没有目视就不能自称视觉已验证。
- **一屏一主任务、一组一主操作**：信息结构先于 Kotlin 实现；只有真实风险和复杂授权才保留长确认说明；正常页不默认展示全局同步/诊断文字。
- **绝不因为 UI 改动而改业务契约**：产品 `D` 建议≠已实现；草稿保留、账号作用域、冲突条件写、私有字段分享白名单沿用权威现有实现。
- **组件选择必查**：[MIUIX_COMPONENT_CATALOG.md](../../docs/ai/MIUIX_COMPONENT_CATALOG.md)，参考应用证据在 [REFERENCE_APPS_RESEARCH.md](../../docs/ai/REFERENCE_APPS_RESEARCH.md)，UI 推理步骤见 [BADGER_UI_AGENT_WORKFLOW.md](../../docs/ai/BADGER_UI_AGENT_WORKFLOW.md)，已知冲突见 [DESIGN_CONFLICTS.md](../../docs/ai/DESIGN_CONFLICTS.md)。

### 0.1 从用户一句话到页面布局的最小决策

```text
用户目标 → 实体/数据边界 → 页面/弹层/页内的导航深度 → 首屏核心信息
→ 主要动作与次级入口 → 当前项目已验证 wrapper → MIUIX 锁版本组件
→ 加载/空/失败/冲突/离线/权限/草稿状态 → 文案去噪 → 非截图质量验证
```

**不允许**用户说“做一个名片夹界面”，AI 就机械生成“标题 + 副标题 + 五个灰色 Button + Text 列表”。

### 0.2 Badger 真实组件检索顺序

1. `ui/components` 的已有 `Alert` / `BadgerDialog`、`StatusPanel`、`ContactAvatar`、`BadgerEmptyState`、`SectionCard`、`ToolbarAction` 等（**仅文档候选，调用前检查实际类与签名**）。
2. 现有四 Tab Scaffold、FloatingBarScaffold、主题/间距 token、搜索/导航/大屏双栏封装。
3. MIUIX 官方同语义组件：Preferences、Card、TopAppBar、Nav、Overlay/Window 弹层。
4. Compose Foundation/系统 Picker；必要时的兼容实现（例：官方 MIUIX Scaffold 的 snackbarHost slot 需要项目另行提供 Host）。
5. 只有明确缺失时新增可复用组件；写出为何不是已有组件能完成。

### 0.3 明确的视觉拒绝条件

下述任一项出现，至少重新设计该页面的信息层级：无意义的“点击这里…”；普通返回写成胶囊文字按钮；技术协议/JSON 在首屏；三个以上同权重且无关联的操作按钮；真实列表被重复同步提示挤出首屏；没有数据却硬塞演示账号；危险确认只留一个不明确图标；键盘/底栏遮挡核心操作；图标缺读屏语义。完整门禁见 `UI_QUALITY_GATES.md`。

## 一、UI 目录结构与职责边界

```
ui/
├── components/        # 跨页面通用组件（本文第四节的全部成员）
│   ├── Alert.kt            # 通用弹窗（确认 / 输入 / 自定义内容，一个组件全包）
│   ├── StatusPanel.kt      # 全屏 Loading / Error 态
│   ├── ContactAvatar.kt    # 头像（图片优先，首字回落）
│   ├── EntranceReveal.kt   # 卡片进场（延迟淡入上移，一次性）
│   ├── SkeletonBox.kt      # 骨架呼吸块（形状由调用方 modifier 决定）
│   └── GhostCardStack.kt   # 空态装饰（三张错位斜叠幽灵卡）
└── designsystem/      # 设计令牌与语义色
    ├── BadgerDesignTokens.kt   # BadgerSpacing / BadgerRadius / BadgerMotion / BadgerAlpha / BadgerSize
    └── BadgerDesignColors.kt   # success / warning 色系（随深浅色切换）
page/<域>/            # 页面编排 + 页面域组件（如 CollectionCard）
                      #   判据：依赖领域实体的留页面，纯通用 RecyclerView 式的进 ui/
```

**组件上提判据**：跨页面且**业务中立、接口稳定、复用收益成立**的组件上提 `ui/components`。强领域绑定的 `CollectionCard`/`ContactDetail` 等留在对应域，不为达成“复用次数”强造全局封装。跨域互相 import 需先审查依赖边界。

## 二、设计令牌（零裸数字）

| 令牌 | 内容 | 备注 |
|---|---|---|
| `BadgerSpacing` | xs=4 / sm=8 / md=12 / lg=16 / xl=24 / cardPadding=16 / cardGap=12 | 卡片纵向间距 ≥12dp、横向 ≥8dp 是底线 |
| `BadgerRadius` | surface=20.86 / menu≈46.86 / chip=8 / inner=12 / card=16 / large=24 | menu 为弹出菜单/选项框半径，123px @2.625 密度 |
| `BadgerMotion` | DURATION_FAST=150 / BASE=300 / SLOW=500 | 所有 tween / Crossfade 时长从这里取 |
| `BadgerAlpha` | AVATAR_TEXT_ALPHA / COVER_GRADIENT_END_ALPHA | 语义化透明度 |
| `BadgerSize` | coverHeight=148 / coverAvatarSize=56 / coverIconSize=64 | 封面类尺寸预留 |

新增视觉常量一律先看令牌有没有，没有就**加进令牌文件**再引用，不允许页面里写裸数字。

## 三、主题与语义色

- 颜色只从 `MiuixTheme.colorScheme` 取，深浅色自动成立。常用对照：`surface`=页面底、`surfaceContainer`=卡片底、`onSurfaceVariantSummary`=次级文字（Miuix 没有 M3 的 `onSurfaceVariant`）。
- `MiuixTheme.colorScheme.*` 只能在 `@Composable` 作用域内调用。
- 成功 / 警告色走 `BadgerDesignTheme.colors`（success / warning 双色系，随 ThemeController 深浅色切换）。
- **陷阱**：`Color.Transparent` 的 RGB 是 (0,0,0)，`copy(alpha)` 会得到半透明黑。需要半透明主题色时用 `colorScheme.xxx.copy(alpha=…)`，不要拿 Transparent 调。
- 文字层级用 `MiuixTheme.textStyles`（title1~4 / subtitle / body1~2 / footnote1~2），不手写 fontSize+fontWeight。

## 四、通用组件使用规范

### Alert（弹窗，唯一入口）
- 纯确认：`Alert(show, title, summary, confirmText, onConfirm, onDismiss)`；
- 带输入：传 `content = { TextField… }` + `positiveEnabled = input.isNotBlank()`（名称为空确认置灰，**不用 toast 报错**）；
- 禁止手写 WindowDialog 壳 / 按钮 Row（Pattern A `if (show)` 外层挂载已由 Alert 内部约定覆盖，调用方持 flag、三条路径复位：onConfirm / onDismiss / 点外部）；
- 弹窗按钮最多两个（取消+确认）。

### StatusPanel（全屏态）
- `StatusPanel.onLoading()` / `onError(message) { retry }`，页面 `when (uiState)` 直接调。
- 页面级 Loading 若想更精致，用第五节骨架屏替代，Error 仍走 StatusPanel。

### EntranceReveal（进场）
- 包住网格 / 列表 item：`EntranceReveal(delayMillis = colIndex * 20L, modifier) { … }`；
- 一次性动画，动机 = 建立阅读顺序；**禁止**常驻循环动画（呼吸类除外，见 SkeletonBox）。

### SkeletonBox（骨架）
- 按最终布局形状拼装（形状由 modifier 给：`.weight(1f).height(200.dp).clip(...)`）；
- 加载态优先骨架屏，转圈（StatusPanel.onLoading）留给无法预知布局的场景。

### ContactAvatar / GhostCardStack
- 头像：图片优先、首字回落（回落色 `primary.copy(alpha=0.28f)` 是标准回落语言）；
- 幽灵卡：空态装饰专用，配合空状态模板文案。

## 五、状态设计（列表页四态标准）

| 状态 | 方案 |
|---|---|
| Loading | 骨架屏（形状匹配最终布局） |
| Error | `StatusPanel.onError` + 点击重试 |
| Empty | 具体空态（如“还没有圈友”）+ 唯一真实操作（如“添加圈友”），装饰仅在确有价值时使用；**禁止空列表可滚动及说明书式“点击添加”** |
| Content | 列表/网格 + PullToRefresh |

## 六、动效规范

1. **每个动效说得出动机**（反馈 / 层级 / 叙事），说不出来就不做；
2. 时长一律 `BadgerMotion`，弹簧一律 `Spring.DampingRatioNoBouncy`（过冲回摆 = 果冻感）；
3. 动画值只在**绘制期**读（`graphicsLayer { … }`），组合期读动画值会让重组/特效管线每帧重跑；
4. 列表进场用 `EntranceReveal` 错峰（每列 20ms 步进），item 复用 remember 保证滚回来不重播；
5. 同类内容切换（换封面 / 换图）用 `Crossfade(tween(BadgerMotion.DURATION_BASE))`；
6. **状态变色一律过渡**：选中/就绪等语义色切换用 `animateColorAsState(tween(BadgerMotion.DURATION_FAST))`，不允许颜色突变（2026-11-27 动效重做）。
7. **顶栏标题模式切换过渡**：顶栏标题/副标题文案变化（多选计数、手动排序等）用 `AnimatedContent` 150ms 淡入淡出 + 10% 上滑，单段单调曲线（2026-11-27 顶栏动效重做）。

## 七、图片与封面规范

- 文字压图必须有**功能性遮罩**（上下双段渐变 + 平涂层，参考 CollectionCard 的三层遮罩参数）；
- **渐变只允许出现在图片遮罩里**——给纯色 UI 元素上装饰性渐变 = AI 味，否决；
- 图片加载失败必须有回落 UI（参照 ContactAvatar 的 isError 语义），失败 URL 打日志必须 `SafeLog.url` 脱敏；
- 敏感值日志一律 SafeLog（url / token / authHeader / phone / email / apiKey）。

## 八、布局与交互红线

- Tab 页内**禁止自绘同轴横滑手势**（与 HorizontalPager 抢轴，历史三次翻车）；
- 可拖拽 UI 的隐形热区只 claim 必要轴向（字母索引用 `detectVerticalDragGestures`），`onDragCancel` 与 `onDragEnd` 同样复位状态；
- Miuix Card 自带 `onClick`/`onLongPress`，禁止在 modifier 上叠 `combinedClickable`；
- Miuix 弹出层（OverlayListPopup 等）必须配 **Miuix Scaffold**，material3 Scaffold 下永不出现；
- 触控目标 ≥48dp；按钮一律使用 Miuix 原生 `Button` / `TextButton` / `IconButton` / `FloatingActionButton`（含其自身按压指示），不再保留自绘玻璃按钮；FAB 保持 Miuix 默认 60dp 圆形 + 24dp 图标；
- 液态玻璃已拆分为两类：**液态导航栏**（本分支唯一保留的玻璃形态，见下）与**液态界面**（玻璃按钮、玻璃菜单/弹窗、玻璃顶栏形态、弹簧回弹）。液态界面及其选项已移至 `liquid-glass-interface` 分支，本分支不再包含；自定义 TopBar 与顶部渐隐已回到本分支（顶区渐进模糊 2026-11-28 按用户要求移除）。设置页保留「悬浮导航栏」（形态）与「液态导航栏」（材质，仅在悬浮开启时出现）两项，先有悬浮再有液态；
- 液态导航栏：「悬浮导航栏」开 = 浮动胶囊底栏，关 = 经典 Miuix `NavigationBar`；「液态导航栏」开 = 玻璃材质（折射、rim、指针光），关 = 不透明卡片色底栏 + 39% 强调色选中遮罩（位置跟踪与拖动缓冲保留）。底栏只在水平拖动后跟随触点，水平偏移按拖动量 15% 计算、限制左右各 5dp、松手/取消 150ms 回位，只改绘制变换，观察手势不得消费点击；
- 底栏选中框：位移 239ms 单调曲线 + 32ms 缓冲跟手；按压缩至 97%（底栏整条 98%）；无速度拉宽/压扁、无到位惯性回弹、无触点光晕。快速点按由同一位移动画处理：正常抬手允许点击接管，不先回旧标签；同目标保留时间线，前向改目标衔接当前速度；
- 底栏选中 Tab 变色与微放大：选中 Tab 图标/文字以 150ms `animateColorAsState` 过渡到主题色，未选中回到中性色；色彩目标取选中框位置的就近取整 Tab；选中图标同段放大至 106%（`SELECTED_ICON_SCALE`）；经典底栏使用 Miuix `NavigationBarItem`；
- 底栏指针光（液态导航栏标志行为）：玻璃态激活期间（100ms 光学包络）在胶囊下绘制 44dp 柔光（峰值 22% 白），跟随选中框中心，始终裁切在胶囊内、不做整面扫光；长胶囊透镜用 `GlassOpticalParameters.Bar`（低中段弯折 + 色散 6，防宽条彩虹条纹），配方为高斯后串色彩还原矩阵（饱和度 浅 1.7/深 1.55 + R/G/B 提亮 1.05/1.02/1.06），详见 [glass-material-setting](glass-material-setting.md)；
- 页面骨架使用自定义 `GlassScaffold.Scaffold`（Miuix Scaffold 槽位之上接线顶部效果与背景录制），顶栏使用自定义 `GlassTopBar`（透明覆盖、固定标题、标题/副标题 150ms 交叉滑动、`PageActionBar` 标题与动作同行）；顶栏不消费栏带触摸；
- 顶部效果：滚动视口 `Modifier.scrollTopFade()` 渐隐，强度为「70% 基线 + 滚动加深」（`TopEdgeScroll`，只统计真实消费位移）；顶区渐进高斯模糊已按用户要求移除（2026-11-28，历史实现见 `liquid-glass-interface` 分支）；相机页与 MainTabs 宿主 `topEffects = false`（各 Tab 自管一处区域），详见 [top-effects](top-effects.md)；
- 弹窗、菜单统一使用 Miuix 原生 `WindowDialog` / `OverlayListPopup` / `FloatingToolbar`，面板表面、圆角、遮罩与进出场动画由 Miuix 承担；二维码展示与 NFC 写入弹层保留项目 239ms 保留式退场（`RetainedPopup`/`PopupWindow`）与 15% 黑色遮罩，面板底色为实心提升容器色（`surfaceContainerHigh`）；
- 滚动内容顶部统一增加 3dp 留白（`BadgerSpacing.scrollContentTopGap`），留白位于滚动内容内且每个视口只增加一次；空态不因此变为可滚动。设置内容在此基础上额外下移 5dp；
- 首页名片可见表面宽高比固定为 5:3，横向外边距放在比例约束之外；
- 单 Composable ≤10 个 `mutableStateOf`，超了拆子组件；无关状态隔离到独立 Composable；
- 单文件 ≤500 行：页面编排放 `Page.kt`，页面域组件放 `XxxComponents.kt`；
- 图标统一 Lucide（`com.composables.icons.lucide`），不用 MiuixIcons；
- 顶栏要随滚动收起用 `MiuixScrollBehavior(rememberTopAppBarState())` + `nestedScroll`。

## 九、2026 趋势备忘（调研结论）

来源：[uxpilot 2026 移动趋势](https://uxpilot.ai/blogs/mobile-app-design-trends) · [优设 2026 七大趋势](https://www.uisdc.com/2026-ui-ux-ai-trends) · [NN/g 骨架屏](https://www.nngroup.com/articles/skeleton-screens/)

- **已采纳**：骨架屏（替代转圈）、有动机的微交互、数据驱动界面（DB Flow 实时刷 UI）、深色优先、克制的空态视觉构图、适当折叠顶栏；
- **明确不追**：液态界面（玻璃按钮/菜单/弹窗/玻璃顶栏形态；项目特效管线有跳帧前科，已拆分到 `liquid-glass-interface` 分支）、AI 生成式 UI / 空间计算（无关）、动态排版/粒子动效（成本收益不匹配、与 Miuix 气质冲突）。唯一保留的玻璃形态是**液态导航栏**（浮动胶囊底栏）；顶部渐隐为轻量遮罩实现，不属于玻璃形态。

## 十、教训记录

1. **装饰性渐变被否决**（2026-09 名片夹页）：无封面卡上主题色渐变被用户点名"爱用渐变"。渐变只服务可读性。
2. **弹窗重复造轮子**（2026-09 名片夹页）：手写 WindowDialog 壳+按钮行+校验，被指出不复用现成 Dialog。规则：**写新 UI 前先查 `ui/components` + 旧项目遗产目录，确认没有轮子再造**。
3. **不加第五个文件**：页面功能收敛在既定文件集内，通用件上提 `ui/`，不为一次性的东西开新文件。
4. **先读令牌再设计**：`BadgerSize.cover*` 系列早就为封面卡预留——设计前先扫一遍 designsystem。


---

## 十一、页面 UI 评审的最小约定（新增）

- **先画内容区，不先画按钮**：列出用户第一个屏幕看到的“数据、当前对象、真正需要解决的问题”，再决定 CTA。UI 文案必须与数据库/状态相符。
- **颜色限于语义**：背景、内容、选中、警告、危险。不要默认用亮色胶囊强调每一项，更不把色彩做成信息唯一通道。
- **卡片按组使用**：同一个人物或名片夹一张完整行/卡，不把每个字段单独套 Card；设置分组可采用 Card + Preference，但长 Lazy 表需要评估逐行虚拟化。
- **弹层并非目的地**：复杂编辑、字段差异核对、恢复/注销任务使用页面；Dialog 只做短确认，BottomSheet 处理轻量附属任务，设置选择优先相应 Preference。
- **真实错误与撤销**：保存仅本机时叫“已存本机”，远端确认后叫“已同步”；删除可撤销则给撤销路径，不可撤销明确影响。
- **正式文案禁止**：无事实来源的“您可以在这里…”“点击以继续”“此数据为演示，请以实际为准”“模拟扫描成功”；灰色占位数据、技术 JSON 不能替代模型绑定。
- **验收证据**：写清做了哪些代码级/可访问性/编译测试；未对真实界面做人工目视时不能声称审美已通过。不得调用设备截图工具。
