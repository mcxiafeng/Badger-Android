# 独立玻璃材质开关

2026-11-26。UI 设置新增「玻璃材质」，默认开启，与「悬浮导航栏」分别持久化。

| 玻璃材质 | 悬浮导航栏 | 结果 |
| --- | --- | --- |
| 开 | 开 | 保留现有玻璃按钮、菜单与悬浮底栏 |
| 开 | 关 | 按钮和菜单仍为玻璃，仅底栏切回经典样式 |
| 关 | 开 | 悬浮组件（图标按钮、FAB、工具栏、展开菜单、悬浮底栏）使用不透明卡片色背景与主题色前景；内容按钮（页面 CTA、弹窗按钮）仍为不透明强调色。悬浮底栏与选中框保留位移/拖动跟随，取消缩放、回弹、光晕与描边，选中遮罩为 39% 强调色 |
| 关 | 关 | 同样的无材质控件与面板，底栏使用经典样式 |

- 模态弹层已采用新填充政策：材质开启时深浅均为 9% 白色；材质关闭时深浅色均与普通卡片同色、100% 不透明，且不启用光学。背景统一 15% 黑色遮罩，按钮展开菜单保持原有政策。详见 [模态弹层与点按衔接](modal-popup-and-tap-motion.md)。
- 新偏好为已有 `badger_settings` 中的 `glass_material_enabled`，缺省 true；原 `nav_bar_floating` 及首次引导逻辑保持独立。
- UI 通过 UiSettingsViewModel 收集两条 StateFlow 并调用独立 setter；新增 ViewModel 只持有 ApplicationContext。
- App 不再用悬浮开关控制整个 GlassStyle 的启用。`enabled`/`glassButtons` 管理既有控件形态与反馈，`materialEnabled` 决定材质绘制，`floatingNavigation` 决定底栏分支。
- 无材质分支在创建 GlassOptics、RuntimeShader 和采样坐标之前返回，使用相同 G2 bounds 绘制纯色，不采样 backdrop，不画光学 rim。悬浮有效控件（`floatingFill`）直接使用不透明卡片色（`surfaceContainer`）配主题色前景，无效悬浮控件保留 `surfaceVariant` 中性色；内容按钮（`buttonFill`）维持强调色 alpha=1 与黑白亮度前景。悬浮面板（底栏、展开菜单、悬浮工具栏）无材质时直接绘制不透明卡片色，不再做玻璃 tint 合成（`solidGlassFill` 已移除）。
- 模式、强调色及二维码等模态弹窗按上方填充规则处理；按钮展开列表、工具栏与底栏保留各自的纯色回退入口与交互，原有阴影、位置、尺寸及圆角保持。
- 普通按钮的缩放、按压光晕、内容透明度、5dp 水平跟随、松手/取消恢复、点击/长按均独立于材质开关。
- 悬浮导航开启且玻璃材质关闭时，底栏恢复与玻璃模式相同的选中框位移、拖动缓冲和底栏水平跟随，但不挂载光晕绘制，不执行缩放、速度形变、光学或到位回弹。G2 轮廓使用 `primary.copy(alpha = 0.39f)` 遮罩，无描边。选中图标和文字继续沿用原有强调色着色逻辑。再次开启材质时从当前标签建立动效状态，避免从旧位置补播。此规则替代此前完全静态与白色描边方案，其他按钮不变。
- 页面顶部渐进模糊与渐隐保持原有独立设置；其专用 backdrop 不因材质关闭而停止录制。

## 验证

配置测试覆盖缺省、旧偏好兼容、两项独立保存及重新初始化；UI 测试覆盖两个可用开关、行/原生开关点击、四种组合与 ViewModel 重建。材质测试覆盖强调色填充 alpha=1、透明弹窗底色的纯色回退、无材质按钮点击/取消与形变、浮动底栏点击/滑页/取消，以及无材质模式/强调色/二维码弹层的操作和方向切换。

`:app:assembleDebug` 完成三个 ABI APK；最终相关测试返回 `BUILD SUCCESSFUL`，177 项、0 失败、0 跳过。首轮的导航测试为普通 ComponentActivity，已显式注入真实 UiSettingsViewModel；灰度合成断言按一个 8-bit 色阶容差校验。打包后仅调整了测试，正式源码未变，最终主编译任务为 UP-TO-DATE。未运行全仓库测试。

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" :app:testDebugUnitTest --tests "*GlassMaterial*Test" --tests "*NavBarConfigTest" --tests "*UiSettingsPageTest" --tests "*MenuSurfaceGeometryTest" --tests "*PageScrollInsetsTest" --tests "*SettingsScrollEffectsTest" --tests "*ScrollTopFadeMaskTest" --tests "*ProgressiveTopBlurGeometryTest" --tests "*ScrollingPageChromeTest" --tests "*GlassToolbarInteractionTest" --tests "*GlassTouchFeedbackTest" --tests "*MyPageTest" --tests "*SocialCard*Test" --tests "*QrDisplay*Test" --tests "*Todo*Test" --tests "*SaveIconButtonTest" --tests "*ButtonTouchInteractionTest" --tests "*AddPlatformScreenTest" --tests "*ContactDetailInteractionTest" --tests "*BasicInfoCardInteractionTest" --tests "*AboutPageTest" --tests "*FloatingNav*Test"
```

API 36 模拟器已安装并启动 x86_64 最终包，经真实 UI 打开「我的 → 设置 → UI 设置」并切换两项。日志实际记录四种组合：material/floating 为 true/true、false/true、false/false、true/false；材质关闭时记录 runtimeShader=false、optics=false、backgroundBlur=false、fillAlpha=1.0。关闭悬浮后，元素树显示经典底栏的图标与标签位置，模式菜单仍可正常打开。检查结束恢复最初两项都开启，日志确认 true/true；未更改外观选项或业务内容。

遵守禁止设备截图的约束；这些状态、布局和日志检查不等同于人工视觉验收。

最终 APK：`app/build/outputs/apk/debug/Badger-1.0.0-dev-3-<ABI>-20261126-0603.apk`。三个 ABI 均通过 apksigner 验证，aapt 确认应用 ID 为 `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev 与相应 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `DFDDAE5E1263F6F3C8E153244AD0B119BE49B520ADBC4F0091C668259252F19E` |
| armeabi-v7a | `3236015556D1602F109DF2C4BFD2BFC0B9A259E2F1F316F463D46E4679962618` |
| x86_64 | `5324C71C35344DA4F9E7BC328E4F2DB4A4BA6DF9C67CF09FB541C06992C1BAF9` |

## 白色描边历史回归（2026-11-26，已由静态强调色方案替代）

选中框在现有绘制层上增加白色 Stroke，与普通填充共用 `indicator.bounds`；动画值仅在绘制期读取，没有新增动画状态或改变导航手势。

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*FloatingNav*Test" --tests "*GlassMaterialNavigationTest" --tests "*NavBarConfigTest"
```

构建返回 `BUILD SUCCESSFUL`；6 个测试类共 45 项，0 失败、0 错误、0 跳过。覆盖底栏导航、点击/拖动/取消、光影进退场、速度形变及独立设置，未运行全仓库测试。

API 36 模拟器安装本轮 x86_64 包，读取到初始 floating=true/material=false。按住当前标签并横向拖到「我的」后，日志确认 `white outline=true`、`widthDp=1.0`、`followsOpticalProgress=true`，共用进度到 1；松手到位后以既有 100ms 过渡回到 0。开启玻璃材质后日志确认 `white outline=false`，主标签点击正常。检查后恢复初始开关组合。使用元素树与日志，未截图，描边最终观感仍需人工目视验收。

本轮 APK：`app/build/outputs/apk/debug/Badger-1.0.0-dev-3-<ABI>-20261126-1209.apk`。三个 ABI 均通过 apksigner 验证；aapt 确认 `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev 与对应 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `432D9824260FA48EC85E5896936416D9A740FAC5A741488317662650673B1EBA` |
| armeabi-v7a | `2A314058A717FEB5B0D996A8F8AE9A488E7C44B660D6E6C6829E5D2F6CB230A2` |
| x86_64 | `708C1DF9704AA23AFF5D7591541F1BBE26D81437A189ED139C060760AF9BFA73` |

## 无材质静态底栏历史方案（2026-11-26，位移规则已修订）

底栏在 material=false 时不挂载按压/跟手观察器和光晕绘制，不调用选中框位置、光学及形变动画。点击使用 `requestScrollToPage`，底栏拖动结束直接选择目标，取消保留当前页；遮罩与选中内容着色使用同一个当前页的静态轮廓。绘制代码独立为 `FloatingNavIndicatorDrawing.kt`，保留玻璃模式的光学参数和着色管线。普通按钮源码未改。

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*FloatingNav*Test" --tests "*GlassMaterialNavigationTest" --tests "*NavBarConfigTest" --tests "*ButtonTouchInteractionTest" --tests "*GlassTouchFeedbackTest"
```

三个 ABI APK 构建成功。首轮 57 项中两项新增选中状态断言失败：暂停时钟仅推进一帧时，requestScrollToPage 完成测量后，选中语义尚未重组。测试按测量帧和后续语义提交帧分别同步，未放宽断言或修改正式行为。重跑同一组 `:app:testDebugUnitTest` 返回 `BUILD SUCCESSFUL`；8 类共 57 项，0 失败、0 错误、0 跳过，正式主编译为 UP-TO-DATE。未运行全仓库测试。

API 36 模拟器安装本轮 x86_64 包，按住当前标签与横向拖到「我的」均正常；日志明确 `animations=false; halo=false; outline=false; solidAccentAlpha=0.39`，此时没有启动 FloatingNavIndicator 的按压/光学/运动任务。开启材质后记录 animations=true，按压从当前第 3 页开始，光影进度到 1，释放后回到 0；检查后恢复 floating=true/material=false。使用元素树与日志，无设备截图；最终遮罩与图标颜色观感待人工目视验收。

APK：`app/build/outputs/apk/debug/Badger-1.0.0-dev-3-<ABI>-20261126-1306.apk`。三个 ABI 均通过 apksigner 验证，aapt 确认 `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev 和各 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `3056E6B2636F2DC14C2AEDF66FFFED3236E531AF3D7895D544908D1AC9C83815` |
| armeabi-v7a | `DAEF6E5026FA4623CCB03FA1C5492C70DDE4F3028094F330C5CB8E7F0A46724D` |
| x86_64 | `546EA40EFEE4EABF534ADFF3E19BDF4D88BD8A35BF2F5F51027B7F5DEFA62F9D` |

## 扁平模式悬浮组件改卡片色（2026-11-26）

- 悬浮组件（图标按钮、返回按钮、FAB、GlassActionGroup 工具栏、FloatingToolbar、SaveIconButton、按钮展开菜单、悬浮底栏）在材质关闭时直接绘制不透明 `surfaceContainer`（当前亮/暗卡片色），前景改用主题色。无效悬浮控件（如未就绪的保存按钮）保留 `surfaceVariant` 中性色配 `disabledOnSurface` 前景。
- `GlassStyle.floatingFill` 承载该分支；`buttonGlass(floating=…)` 与 `glassButtonForeground(floating=…)` 默认 `true`（悬浮路径），`Button`/`TextButton`（页面 CTA、弹窗按钮）显式 `floating=false`，仍走 `buttonFill` 强调色填充与黑白亮度前景。无材质面板（底栏、展开菜单、悬浮工具栏）不再做玻璃 tint 合成，`solidGlassFill` 已删除。
- 悬浮底栏选中内容仍为主题色，选中框仍为 39% 强调色遮罩；页面顶部渐进模糊与渐隐不受影响。
- 验证：`:app:compileDebugKotlin`、`:app:assembleDebug` 通过。`GlassMaterialTest` 以 `floatingFill` 断言替换 `solidGlassFill` 断言（材质开=强调色 50% tint，材质关=卡片色/surfaceVariant，亮色中性 tint 走 8-bit sRGB 量化做对称比较）；连同导航、触摸反馈、菜单几何、弹层、账号页、联系人页、顶部特效与二维码共 26 类相关回归，首轮 150 项中除新断言口径修正外全部通过，`PersonSearchInputTest`/`PersonPagingScrollTest` 在并行满载下偶发 5s 等待超时，单独重跑全部通过（与已知 flaky 家族一致）。未做真机目视验收。

## 扁平模式弹窗委托 Miuix 原生（2026-11-27）

- 材质关闭时悬浮组件的弹窗不再走自绘保留窗口：`GlassStyle.flatMode`（`enabled && !materialEnabled`）下，`ui/glass` 的 `WindowDialog` 与 `OverlayListPopup` 直接委托 Miuix 原生 `top.yukonga.miuix.kmp.window.WindowDialog` / `top.yukonga.miuix.kmp.overlay.OverlayListPopup`，面板表面、圆角与进出场动画全部由 Miuix 承担。遮罩与底色细节按下方两轮修订执行（初版菜单无遮罩，第二轮起恢复 Miuix 自身遮罩并统一提升容器底色）。材质开启时两条路径的行为完全不变。
- 原生弹窗面板背景取 `colorScheme.surfaceContainer`，因此 `GlassPopupHost` 在扁平模式下不再把 `surfaceContainer` 覆写为透明（否则原生面板会隐形）；材质开启时保留原透明覆写。
- 应用自身的 239ms 退场交接保留：扁平 `WindowDialog` 仍包在 `RetainedPopup` 中，把 `motion.visible` 作为原生 `show`，外层 `if` 卸载后由原生播完自己的退出动画；`onDismissFinished` 只交给原生，避免重复回调。
- 关闭语义不变：全部 65 处 `WindowDialog` 调用点都显式传入 `onDismissRequest`，没有依赖 `null` 阻断关闭的调用点。二维码弹层与 NFC 写入窗口直接走 `RetainedPopup` + `PopupWindow`，不在本次范围；`FloatingToolbar`、`SaveIconButton` 等非弹窗悬浮组件也不变。
- 验证：`:app:compileDebugKotlin` 通过；`:app:testDebugUnitTest` 覆盖 `ui.glass.*`、`pages.settings.*`、`pages.social.*`、`pages.person.PersonSearchInputTest`，189 项 0 失败、0 错误、0 跳过。新增 `FlatModeNativePopupTest` 覆盖扁平菜单面板保留卡片色（材质开启时仍清空宿主表面）、扁平弹窗经原生 `MiuixWindowDialog` 呈现、以及调用方卸载后的退场交接；`MyPageTest.assertFlatModalSurface` 的断言口径由「自绘 Solid material 面板」改为「原生 `MiuixWindowDialog` 委托日志」。未做真机目视验收。

## 弹窗透明根因、菜单锚点间距与按钮审计（2026-11-27）

- **弹窗透明根因**：玻璃模式下模态面板底色取 `colorScheme.background`（浅色 = 纯白），而页面 `surface` 为 #F7F7F7，几乎同色；其上仅叠加 9% 白纱，弹窗主体与页面无法区分，浅/深色下均呈透明观感（深色两者同为 #242424）。修复：面板底色统一由 `modalPanelBase` 决定——材质开 = 提升容器 `surfaceContainerHigh`（浅色 #E8E8E8，与页面拉开层级；深色与卡片同色，观感不变），材质关 = 卡片色 alpha=1（原状）。`modalPopupSurface` 因此移除调用方 underlay 参数，WindowDialog、二维码、NFC 三个调用点同步精简；`ModalPopupStyleTest` 新增 `modalPanelBase` 纯函数断言。
- **菜单与按钮间隙**：全部 `OverlayListPopup` 调用方传的是 0 边距 `ContextMenuPositionProvider`；玻璃路径经 `MenuPopupPositionProvider` 已强制 8dp，扁平原生分支新增 `FlatMenuPositionProvider = dropdownPositionProvider(verticalMargin = menuAnchorGap)` 对齐同一 8dp 间距。弹窗外边距维持 Miuix `DialogDefaults.outsideMargin`。
- **按钮底色/图标审计结论**：IconButton、FloatingActionButton、SaveIconButton、GlassActionGroup、FloatingToolbar（经 ToolbarAction）、GlassActionText 在材质关闭时底色均走 `floatingFill` = 当前亮/暗 `surfaceContainer`，图标经 `glassButtonForeground(floating=true)` 强制主题色；悬浮底栏选中内容主题色、未选中中性色（按底栏规范）；图片裁剪页 `MaterialIconButton` 为唯一豁免（黑底媒体层上的白色图标，非卡片色底）。
- **原生路径补全（第二轮）**：首轮只改了自绘面板的底色；材质关闭时委托 Miuix 原生的弹窗与菜单仍取库默认底色（弹窗 = `colorScheme.background` 窗口色、菜单 = `surfaceContainer` 卡片色），透明观感在原生路径上依旧存在。现统一修复：原生 `WindowDialog` 的包装器默认 `backgroundColor = Color.Unspecified`，未显式指定时解析为 `surfaceContainerHigh`（显式传入者保持不变）；`GlassPopupHost` 在扁平模式下把 `surfaceContainer` 覆写为 `surfaceContainerHigh`，原生菜单面板与选项底色一并提升。原生菜单同时恢复 Miuix 自身遮罩（`windowDimming`，浅色 30%、深色 60% 黑）——深色下无遮罩时 #242424 面板与页面完全同色不可见；玻璃路径的菜单维持无遮罩。
- **真正的元凶（第三轮，AppearanceSettings）**：`深浅模式`/`强调色` 的选项弹窗是全库唯一显式传 `backgroundColor` 的 `WindowDialog` 调用点，且写的是 `if (glassEnabled) Color.Transparent else DialogDefaults.backgroundColor()`——应用内 `GlassStyle.enabled` 恒为真，所以两种材质下都一直传 **透明色**。玻璃模式下这是"纯玻璃纱面板"的有意写法（观感即用户投诉的透明）；扁平原生路径下则变成原生弹窗的显式透明底，任何默认值修复都拦不住。已删除该参数，让包装器统一解析提升容器色；同时全库扫描确认无其他把透明色硬传给弹窗/面板背景的调用点（扫码镂空、TabRow 透明底、NFC 卡片叠层均为有意设计）。
- **取值依据**：Anysearch 已实际调用（5 组查询，含小米官方深色模式 PDF——为二进制无法解析，外部检索无可用具体取值）；最终取值以 Miuix 0.9.5-rc01 一手来源为准：`surfaceContainerHigh`（浅 #E8E8E8 / 深 #242424）、`dropdownPositionProvider(verticalMargin = 8.dp)`、`windowDimming`（浅色黑 30% / 深色黑 60%）。
