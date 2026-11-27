# 弹层进退场与回弹

2026-11-26。

- 模式、强调色及其他共享 WindowDialog、按钮展开列表、二维码的进入/退出统一使用固定 239ms tween。2026-11-27 用户反馈回弹观感差后，曲线改为单调贝塞尔，回弹取消；仍不额外叠加一段时长。
- PopupEnterEasing/PopupExitEasing 为单调三次贝塞尔（进场 easeOutQuint 快速到位再缓收，出场加速离开），精确从 0 到 1，全程限制在 0..1，不过冲、不回摆。二维码方向切换同样为 239ms，滑动与淡入淡出均单调。
- App 根部安装 RetainedPopupHost。调用方仍可沿用 Pattern A：外层 if 控制挂载、WindowDialog(show=true)。调用方关闭或卸载后，宿主保留最后注册的内容配置，完成退出后才移除窗口和清理捕获的 context/content。
- 业务关闭/选中回调仍立即执行；onDismissFinished 在动画完成后仅调用一次。退出中重开会取消旧完成回调，并从当前动画值继续。父弹层退出时，嵌套弹层并行退出。
- PopupWindow 区分原生 Popup 菜单与 Dialog 面板，保留联系人长按菜单的菜单语义；关闭平台默认窗口动画，避免与自定义过渡叠加。关闭中的窗口拦截触摸并隐藏操作语义；无 dismiss 回调时保留不可取消行为。列表收回不主动隐藏底层页面键盘。
- WindowDialog 保留手机底部/大屏居中策略、原有可见宽度、边距、IME/系统栏安全区域、标题和内容；列表继续复用 Miuix 的定位、测量与滚动 API，通过屏幕坐标在原页面和原生窗口之间转换锚点。
- 列表菜单从锚角缩放/淡入淡出，不使用裁去阴影的 reveal clip；窗口改为 24dp 轻微上浮淡入/回落（2026-11-27，替代整屏高度滑入），二维码保留按实际面板高度滑入/收回。二维码只旋转内部内容，原有贴屏侧直角、另一侧圆角及玻璃背景取样保持。
- 材质开关、圆角半径、菜单间距、阴影、按钮操作反馈、二维码内容与昵称来源保持。未修改 Miuix 库源码。
- 头像大图和扫码照片属于展示位图，不能在关闭标记清除时提前 recycle。关闭/替换只释放页面状态引用，让退出内容持有的最后展示引用在宿主释放后交由 GC 回收；临时处理位图的回收逻辑不变。

## 验证边界

受控时钟测试需同时推进 Compose 动画帧和原生 Dialog 的 Android UI 工作。PopupTestClock 只排空待处理的 UI/snapshot 通知，不任意增加动画时长；检查动画中途/末段仍保留窗口、239ms 完成后的提交帧才释放。

最终 `:app:assembleDebug :app:testDebugUnitTest` 返回 `BUILD SUCCESSFUL`：201 项相关回归、0 失败、0 跳过。包括 239ms/回弹曲线端点、外层 if 卸载、退出中重开、父子并行退出、原生 Popup 的返回与外部关闭、模式/强调色、二维码两个方向和两种材质的中途及238ms存活，以及退出完成后提交帧释放。未运行全仓库测试。

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*PopupTransitionTest" --tests "*AnimatedListPopupTransitionTest" --tests "*GlassMaterial*Test" --tests "*NavBarConfigTest" --tests "*UiSettingsPageTest" --tests "*MenuSurfaceGeometryTest" --tests "*PageScrollInsetsTest" --tests "*SettingsScrollEffectsTest" --tests "*ScrollTopFadeMaskTest" --tests "*ProgressiveTopBlurGeometryTest" --tests "*ScrollingPageChromeTest" --tests "*GlassToolbarInteractionTest" --tests "*GlassTouchFeedbackTest" --tests "*MyPageTest" --tests "*SocialCard*Test" --tests "*QrDisplay*Test" --tests "*Todo*Test" --tests "*SaveIconButtonTest" --tests "*ButtonTouchInteractionTest" --tests "*AddPlatformScreenTest" --tests "*ContactDetailInteractionTest" --tests "*BasicInfoCardInteractionTest" --tests "*AboutPageTest" --tests "*FloatingNav*Test"
```

API 36 模拟器完成二维码、深浅模式、强调色和排序菜单的打开/关闭检查；同轮日志记录 entering=true/false、durationMs=239、rebound=true、完成后 progress=0.0 再释放。最终0741包另确认排序菜单仍为原生 Popup，位置、选项和系统返回正常，没有更改排序或外观选择。动画参数与受控帧时钟验证为239ms；原生窗口建立、首次绘制和卸载提交受 vsync/设备负载影响，不把日志两行的墙钟差值当成动画参数或帧率证明。

最终 APK：`app/build/outputs/apk/debug/Badger-1.0.0-dev-3-<ABI>-20261126-0741.apk`。三个 ABI 均通过 apksigner 验证，aapt 确认应用 ID `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev 与对应 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `C65C7F80A52FAC1A6DB754E52A03E0E7C5C749004AA360C34407BD8197A43DFE` |
| armeabi-v7a | `BBF42FF0DAFBB3A11F9D4A2495DCABB044E8172E6437832E7397F84C99C3EC69` |
| x86_64 | `F38BFCBB14FCA98D7614CB41527AD5E994E9C6E93FBEC9D9CB1D43C23A152BE9` |

根宿主本身被销毁时，窗口随应用/页面环境销毁，无法继续退出动画；孤立预览或测试若要验证外层 if 移除后的保留，必须显式把 RetainedPopupHost 放在该 if 外。遵守禁止截图的约束，不捕获设备图像；元素树、状态和日志不能替代人工视觉验收。
