# 模态弹层、贡献者头像与点按衔接（2026-11-26）

## 弹层边界

按钮展开的列表菜单继续使用 `dropdown=true`，填充、阴影与遮罩策略保持。其他模态窗口（WindowDialog、二维码展示、NFC 写入窗口）由公共宿主绘制 15% 黑色遮罩，透明度跟随进退场进度并裁切过冲；窗口内旧 PopupDim 跳过，避免同一窗口重复叠加。

| 材质 | 主题 | 弹窗表面填充 |
| --- | --- | --- |
| 开 | 浅色 / 深色 | 9% 白色 |
| 关 | 浅色 / 深色 | CardDefaults.defaultColors().color，与普通卡片一致，100% 不透明且不启用光学 |

独立的 `POPUP_SEGMENT_DURATION_MS = 239` 分别用于进入、退出和二维码方向切换；不增加阶段数量。NFC 窗口接入公共宿主并保留居中、关闭/重试/去设置操作，以及 PREPARING、READY 的返回键保护。原生全屏图片裁剪属于编辑界面，保留图片内容与裁剪区域自身遮罩。

## 贡献者头像

贡献者使用 ArrowPreference 的 startAction 展示圆形头像，不改显示名、副标题和链接。用户提供的本地头像缩放为 256px 资源 `contributor_mars_avatar.png`，以 CircleShape 裁切并在离线时可用。其他贡献者通过 Coil 加载 GitHub 头像，加载中或失败时回退到 GitHub 图标。

## 快速点按衔接

源码中确认三处会打断快速点按的状态路径：抬手先向旧标签回位、同目标重复取消并启动位置动画、速度由独立帧协程延后估算。现在正常抬手给点击回调一次接管机会；未产生点击时仍回位。同目标复用已有动画时间线，前向改目标按当前速度衔接到有界曲线，位置和速度在同一动画回调更新；拖动在同一帧更新位移与速度。分页状态同步使用 SideEffect，位置/透明度/形变继续只在绘制阶段读取。

保持 239ms 位移时长、拖动缓冲、按压行为、材质开关与最终选中语义。新增受控帧时钟检查同目标连续性、抬手交接、未触发点击时回位，以及曲线初速度与边界。此处验证动画控制机制；发布版帧时间仍需单独性能测试，未以 debug 包帧率量化提升。

## 验证与交付

头像、快速点按与弹层的前轮扩展回归共 17 类 125 项通过，0 失败、0 错误、0 跳过。最新修订将关闭玻璃时的深浅色弹窗统一为卡片色、100% 不透明；针对受影响的弹窗、深浅模式、二维码、NFC 和分享流程重新验证，9 类共 65 项全部通过，构建返回 `BUILD SUCCESSFUL`。未运行全仓库测试。

本轮使用单次 Gradle、单 worker、进程内 Kotlin、1536m 最大堆和 Serial GC，测试 init 为 64m 初始堆 / 512m 最大堆。命令沿用本机 `local-maven.init.gradle` 与 `serial-tests.init.gradle`，执行 `:app:assembleDebug :app:testDebugUnitTest`，过滤 `*ModalPopupStyleTest`、`*MyPageTest`、`*QrDisplay*Test`、`*NfcWriteDialogTest`、`*PopupTransitionTest`、`*AnimatedListPopupTransitionTest`、`*PersonSearchInputTest`。

API 36 模拟器安装最终 x86_64 包。深色、无玻璃模式下，深浅模式弹窗日志为 `material=false; dark=true; fillAlpha=1.0; whiteFill=false; optics=false`，宿主遮罩 `blackAlpha=0.15`，转场 239ms。仅打开/关闭弹窗，不修改外观偏好或业务资料。之前也通过元素树确认圆形头像节点 105×105px，并在快速点按日志中记录目标复用及单一位移动画。未截图；未量测发布版帧率，未做真实 NFC 标签写入。

最终 APK：`app/build/outputs/apk/debug/Badger-1.0.0-dev-3-<ABI>-20261126-1512.apk`。三个 ABI 均通过 apksigner 验证，aapt 确认 `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev 和对应 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `2856E754AE834C4908A3396CDEC655FB76F7D98F020B8422D35CCE55AB40D9AB` |
| armeabi-v7a | `2A4A4661CDAA641DBF4FB1ED576FE878E440803AD5A549D2242BCDA1A1D972AE` |
| x86_64 | `43A20EC2998B1554CB63B45AAF4123717A8C01FCB32DDCE042059883B7BE58A1` |
