# 液态玻璃拆分：液态导航栏与液态界面

2026-11-28。液态玻璃体系按职责拆成两类，master 只保留导航栏形态：

| 类别 | 内容 | master 状态 |
| --- | --- | --- |
| **液态导航栏** | 悬浮玻璃胶囊底栏：`FloatingNavBar`、选中框指示器、指针光、`GlassSurface`/`GlassOptics`/`GlassOpticalParameters`（Bar 档）/`GlassGlow` | 保留 |
| **液态界面** | 玻璃按钮四件套、玻璃菜单/弹窗、`GlassBackButton`、`SaveIconButton`、玻璃版 Scaffold chrome、弹簧回弹、玻璃材质开关 | 移至 `liquid-glass-interface` 分支，master 移除（`GlassActionGroup` 动作组胶囊后经用户要求恢复，见下） |
| **自定义 TopBar + 顶部效果** | `GlassTopBar`（透明固定顶栏/`PageActionBar`）、`GlassScaffold` 裁剪版、`ScrollTopFade` 渐隐、`TopEdgeScroll` 滚动强度 | 2026-11-28 从分支回到 master；顶区渐进高斯模糊同日按用户要求移除（历史实现见分支） |

拆分的分支基线是 master 上的检查点提交 `facb0fc`（含扫码页玻璃化与底栏指针光收尾）；液态界面的全部实现、测试与历史配方都在 `liquid-glass-interface` 分支继续演进。2026-11-28 起自定义 TopBar 与顶部渐隐回到 master，顶区渐进模糊按用户要求移除（见 [top-effects](top-effects.md)）。

## master 保留的设置与行为

- UI 设置包含「悬浮导航栏」（`nav_bar_floating`，默认开）与「液态导航栏」（`nav_liquid_enabled`，默认开）两项：**悬浮决定形态**（开 = 液态导航栏所在的浮动胶囊底栏，关 = 经典 Miuix `NavigationBar`），**液态决定材质**（悬浮底栏的玻璃/扁平）。先有悬浮再有液态——液态开关只在悬浮开启时出现；关闭悬浮时液态选择保留，重新开启后恢复。
- 旧「玻璃材质」（`glass_material_enabled`）是液态界面选项，已随界面移出 master；`NavBarConfig` 现在持久化悬浮与液态两个独立 key。
- 导航栏保留：底栏水平拖动跟随（15%、左右各 5dp、150ms 回位）、选中框 239ms 单调位移 + 32ms 缓冲跟手、按压缩放 97%/整条 98%、选中 Tab 150ms 变色 + 106% 微放大、指针光（44dp 柔光、峰值 22% 白、裁切胶囊内）、`GlassOpticalParameters.Bar` 软透镜档；液态关闭时底栏为不透明卡片色 + 39% 强调色选中遮罩（位置跟踪与拖动缓冲保留）。

## 界面层改为 Miuix 原生组件

删除项目自建、直接使用 Miuix 的组件：

| 删除的自建组件 | 替换为 |
| --- | --- |
| `ui/glass/GlassMenus.WindowDialog` | `top.yukonga.miuix.kmp.window.WindowDialog` |
| `ui/glass/GlassMenus.OverlayListPopup`、`AnimatedListPopup`、`MenuSurfaces` | `top.yukonga.miuix.kmp.overlay.OverlayListPopup`、`basic.ListPopupColumn` |
| `ui/glass/GlassMenus.FloatingToolbar` | `top.yukonga.miuix.kmp.basic.FloatingToolbar` |
| `ui/glass/GlassButtons`（Button/TextButton/IconButton/FAB）+ `ButtonTouchFeedback`/`GlassPressFeedback` 按钮部分 | Miuix 同名组件与其原生按压指示 |
| `ui/glass/GlassBackButton`、`SaveIconButton`、`GlassActionText`、`buttonGlass`、`buttonGlassOrBackground` | Miuix `IconButton` / `Text` + 默认指示 |
| ~~`ui/glass/GlassActionGroup`~~ | 2026-11-28 应用户要求恢复：右上角动作组共享胶囊，跟随「液态导航栏」材质开关（玻璃胶囊/卡片色胶囊） |
| 经典底栏 `NavBarItem`（ui/FloatingNavBar.kt） | `top.yukonga.miuix.kmp.basic.NavigationBarItem` |
| `ui/components/BreadcrumbBar` | `top.yukonga.miuix.kmp.basic.BreadcrumbBar` |
| `ui/components/LinearProgressIndicator` | `top.yukonga.miuix.kmp.basic.LinearProgressIndicator` |
| `ui/components/Card`（MiuixCard + G2 裁剪） | `top.yukonga.miuix.kmp.basic.Card` |
| `ui/components/Switch` + `SwitchPreference` | `top.yukonga.miuix.kmp.basic.Switch`、`preference.SwitchPreference` |
| `ui/components/TextField`（G2 焦点描边） | `top.yukonga.miuix.kmp.basic.TextField` |
| `ui/components/InputField`（G2 背景胶囊） | `top.yukonga.miuix.kmp.basic.InputField` |
| `ui/components/Snackbar`（G2 表面 + 自绘 Host） | `top.yukonga.miuix.kmp.basic.SnackbarHost` / `Snackbar` |
| `ui/components/ColorPalette` / `ColorPicker`（G2 视口裁剪） | `top.yukonga.miuix.kmp.basic.ColorPalette` / `ColorPicker` |
| `ui/glass/GlassDropdown`（零调用） | 删除；需要时用 Miuix `OverlayDropdownPreference` |

## master 保留的非 Miuix 组件（有存在理由）

- `GlassTopBar`（TopAppBar/SmallTopAppBar/PageActionBar）、裁剪版 `GlassScaffold`、`ScrollTopFade`/`ProgressiveTopBlur`/`TopEdgeScroll`：用户指定保留的自定义顶栏风格与轻量顶部效果。
- `FloatingNavBar` 及导航栏玻璃管线：Miuix 没有带 pager 拖动跟踪、指针光与胶囊折射的底栏。
- `GlassActionGroup`（顶栏右上角动作组胶囊）：跟随「液态导航栏」材质开关的玻璃/卡片色胶囊，子按钮为 Miuix `IconButton`（48dp 触控目标），前景黑/白对比自动计算。
- `PopupWindow` / `PopupMotion` / `RetainedPopupHost`：二维码、NFC 写入与图片裁剪弹层的 239ms 保留式退场（调用方卸载后仍播完退出动画），Miuix 没有对应公共 API。面板底色改为实心 `surfaceContainerHigh`，15% 黑色遮罩保留。
- `WorkspacePullRefresh`：完全委托 Miuix `PullToRefresh`，只统一文案、颜色与挂载策略（刷新中不卸载，防指示器卡死）。
- `DialogButtonRow`（弹窗按钮行）、`EmptyStateView`、`SkeletonBox`、`ContactAvatar`、`AlphabetIndexBar` 等：Miuix 没有对应组件的业务/设计件。

## 行为变化（master）

- 顶栏恢复为自定义透明覆盖式 `GlassTopBar`（不消费栏带触摸）；页面骨架为裁剪版 `GlassScaffold.Scaffold`，接线渐隐/滚动强度（渐进模糊已移除）。
- 平台原生 overscroll（Miuix 拉伸/回弹）保留；项目自绘弹簧回弹仍留在分支。
- 页面顶部 3dp 内容留白（`BadgerSpacing.scrollContentTopGap`）保留。

## 历史

液态界面的完整配方（9% 白纱、rim、饱和度还原矩阵、239ms 弹层分段、顶部 8 档高斯、滚动驱动强度等）与对应测试保存在 `liquid-glass-interface` 分支的 `docs/glass-material-setting.md`、`docs/top-effects.md` 与 `ui/glass/` 源码中。
