# 顶部内容渐隐与高斯渐进模糊（已移出 master）

2026-11-28。顶部渐隐（`ScrollTopFade`）、8 档渐进高斯（`ProgressiveTopBlur`）、滚动驱动强度（`TopEdgeScroll`）与共享顶区区域（`TopEffectRegion`）属于**液态界面**，已随液态界面整体移至 `liquid-glass-interface` 分支，master 不再包含这些组件，也不再有页面级顶部模糊/渐隐。

master 当前状态：

- 页面骨架使用 Miuix `Scaffold`，顶栏使用 Miuix `SmallTopAppBar`；顶部 3dp 内容留白（`BadgerSpacing.scrollContentTopGap`）保留。
- `GlassScaffold.Scaffold` 的 `progressiveTopBlur` 参数、`Modifier.scrollTopFade()`、`Modifier.springOverscroll()`、`Modifier.progressiveTopBlur()` 均已删除；滚动回弹使用平台/Miuix 原生 overscroll。
- 液态导航栏自身的玻璃管线（`GlassSurface`/`GlassOptics`/`GlassOpticalParameters`/`GlassGlow`）与底栏指针光保留，与顶部特效无关。

历史实现说明（绘制顺序、8 档高斯、滚动驱动强度配方、验证记录）见 `liquid-glass-interface` 分支的 `docs/top-effects.md`。
