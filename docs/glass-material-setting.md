# 液态玻璃拆分：液态导航栏与液态界面

2026-11-28。液态玻璃体系按职责拆成两类，master 只保留导航栏形态：

| 类别 | 内容 | master 状态 |
| --- | --- | --- |
| **液态导航栏** | 悬浮玻璃胶囊底栏：`FloatingNavBar`、选中框指示器、指针光、`GlassSurface`/`GlassOptics`/`GlassOpticalParameters`（Bar 档）/`GlassGlow` | 保留 |
| **液态界面** | 玻璃按钮四件套、玻璃菜单/弹窗、`GlassActionGroup`、`GlassBackButton`、`SaveIconButton`、`GlassTopBar`、`GlassScaffold`、顶部渐隐与渐进模糊、弹簧回弹、玻璃材质开关 | 移至 `liquid-glass-interface` 分支，master 移除 |

拆分的分支基线是 master 上的检查点提交 `facb0fc`（含扫码页玻璃化与底栏指针光收尾）；液态界面的全部实现、测试与历史配方都在 `liquid-glass-interface` 分支继续演进。

## master 保留的设置与行为

- UI 设置只剩「悬浮导航栏」（`nav_bar_floating`，默认开）：开 = 液态导航栏（玻璃浮动胶囊底栏），关 = 经典 Miuix `NavigationBar`。
- 「玻璃材质」（`glass_material_enabled`）属于液态界面选项，随界面移出；master 的浮动底栏恒定玻璃态，`NavBarConfig` 只持久化悬浮开关。
- 导航栏保留：底栏水平拖动跟随（15%、左右各 5dp、150ms 回位）、选中框 239ms 单调位移 + 32ms 缓冲跟手、按压缩放 97%/整条 98%、选中 Tab 150ms 变色 + 106% 微放大、指针光（44dp 柔光、峰值 22% 白、裁切胶囊内）、`GlassOpticalParameters.Bar` 软透镜档。

## 界面层改为 Miuix 原生组件

删除项目自建、直接使用 Miuix 的组件：

| 删除的自建组件 | 替换为 |
| --- | --- |
| `ui/glass/GlassMenus.WindowDialog` | `top.yukonga.miuix.kmp.window.WindowDialog` |
| `ui/glass/GlassMenus.OverlayListPopup`、`AnimatedListPopup`、`MenuSurfaces` | `top.yukonga.miuix.kmp.overlay.OverlayListPopup`、`basic.ListPopupColumn` |
| `ui/glass/GlassMenus.FloatingToolbar` | `top.yukonga.miuix.kmp.basic.FloatingToolbar` |
| `ui/glass/GlassScaffold.Scaffold` | `top.yukonga.miuix.kmp.basic.Scaffold` |
| `ui/glass/GlassTopBar.TopAppBar/SmallTopAppBar/PageActionBar` | `top.yukonga.miuix.kmp.basic.SmallTopAppBar` |
| `ui/glass/GlassButtons`（Button/TextButton/IconButton/FAB）+ `ButtonTouchFeedback`/`GlassPressFeedback` 按钮部分 | Miuix 同名组件与其原生按压指示 |
| `ui/glass/GlassActionGroup`、`GlassBackButton`、`SaveIconButton`、`GlassActionText`、`buttonGlass`、`buttonGlassOrBackground` | Miuix `IconButton` / `Text` + 默认指示 |
| 经典底栏 `NavBarItem`（ui/FloatingNavBar.kt） | `top.yukonga.miuix.kmp.basic.NavigationBarItem` |
| `ui/components/BreadcrumbBar` | `top.yukonga.miuix.kmp.basic.BreadcrumbBar` |
| `ui/components/LinearProgressIndicator` | `top.yukonga.miuix.kmp.basic.LinearProgressIndicator` |
| `ui/glass/GlassDropdown`（零调用） | 删除；需要时用 Miuix `OverlayDropdownPreference` |

## master 保留的非 Miuix 组件（有存在理由）

- `FloatingNavBar` 及导航栏玻璃管线：Miuix 没有带 pager 拖动跟踪、指针光与胶囊折射的底栏。
- `PopupWindow` / `PopupMotion` / `RetainedPopupHost`：二维码、NFC 写入与图片裁剪弹层的 239ms 保留式退场（调用方卸载后仍播完退出动画），Miuix 没有对应公共 API。面板底色改为实心 `surfaceContainerHigh`，15% 黑色遮罩保留。
- `WorkspacePullRefresh`：完全委托 Miuix `PullToRefresh`，只统一文案、颜色与挂载策略。
- `Card` / `Switch` / `TextField` / `InputField` / `Snackbar` / `ColorPalette` / `ColorPicker`：内部仍由 Miuix 组件承担，仅做 G2 连续圆角与外观薄包装，属于设计系统而非重复实现。

## 行为变化（master）

- Miuix 顶栏会在其栏带内消费触摸：内容可在不透明顶栏下滑动，但落在栏带内的条目不可点击；页面列表的 `contentPadding` 必须包含 `innerPadding`（Miuix 官方示例模式）。
- 平台原生 overscroll（Miuix 拉伸/回弹）恢复；项目自绘弹簧回弹随液态界面移出。
- 顶部渐隐与渐进模糊不存在；页面顶部 3dp 内容留白（`BadgerSpacing.scrollContentTopGap`）保留。

## 历史

液态界面的完整配方（9% 白纱、rim、饱和度还原矩阵、239ms 弹层分段、顶部 8 档高斯、滚动驱动强度等）与对应测试保存在 `liquid-glass-interface` 分支的 `docs/glass-material-setting.md`、`docs/top-effects.md` 与 `ui/glass/` 源码中。
