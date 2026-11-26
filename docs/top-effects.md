# 顶部内容渐隐与高斯渐进模糊

2026-11-26。适用于自定义 `ui/glass/Scaffold` 管理的页面主滚动视口。

## 绘制顺序与边界

1. `scrollTopFade()` 明确挂在页面 `LazyColumn` / `verticalScroll` 的视口上。透明度按物理屏幕 y 线性增长：屏幕顶端 0%，共享区域下缘 100%。固定兄弟组件、顶栏、索引和底部操作区不在这个遮罩里。
2. `GlassBackdropContent` 记录已经执行滚动内容渐隐的页面内容，不再对整个 Scaffold 内容槽额外做一次渐隐。
3. 页面只绘制一个顶端模糊覆盖层，采样上述渐隐后的内容，位于滚动内容之上、固定顶栏之下。MainTabsContent 外层明确设置 `progressiveTopBlur = false`，四个 Tab 各自管理效果。
4. 渐隐与模糊共用同一个 `TopEffectRegion`。上缘固定为物理屏幕 y=0，下缘由实际模糊区域测量得到。视口坐标通过 `localToScreen` 转换，遮罩不再单独增加状态栏 inset，也不会因视口内缩而重新从 0% 开始。
5. 当前尾段为 51dp：此前依次由 72dp 调到 60dp、56dp，本轮再缩短 5dp；在参考设备 density=2.625、固定栏高305px时，共用下缘约为屏幕 y=439px。保持 dp 适配，不把所有屏幕都硬编码为439物理像素。固定栏实际测量高度加尾段决定最终下缘，两种效果仍共用整数像素边界。

## 高斯实现

仅使用 Android `RenderEffect.createBlurEffect` 的高斯模糊。全分辨率采样，预留三倍最大半径的采样边界，不混用方框、Kawase、折射或液态玻璃算法。

用 8 档高斯半径及相邻档权重插值近似空间渐进半径：从下缘 0 到屏幕顶部的 `BadgerSize.topProgressiveBlurRadius`（20dp）。各档使用独立 RenderNode，避免一个节点在同帧反复更换 RenderEffect。各行混合权重总和固定为 255，不叠加两份完整内容，也不以固定高斯半径加透明度渐变冒充半径渐进。

Android API 31+ 硬件加速路径提供高斯效果。旧系统和软件画布不使用其他模糊算法替代。独立内容渐隐可在软件画布执行。

## 上轮验证（60dp 尾段）

- `ScrollTopFadeMaskTest` 使用人工生成的纯色像素，验证实际原生渐变着色器的透明度、中点、边界以及固定兄弟图层隔离；不采集设备或应用界面。
- `ProgressiveTopBlurGeometryTest` 验证共用屏幕区域、视口偏移、上下端点、高斯半径递进、采样边界和混合权重。
- `ScrollingPageChromeTest` 验证固定标题/操作仍可点击，以及 Pager 外层关闭效果、内层页面只拥有一组区域。
- 最终 `:app:assembleDebug :app:testDebugUnitTest` 返回 `BUILD SUCCESSFUL`；选定 19 类共 134 项，0 失败、0 错误、0 跳过。覆盖上述绘制检查，以及设置、联系人搜索/详情、添加平台、待办、组件和 Miuix 导航回归。未运行整个仓库测试集合。
- API 36 模拟器安装并启动 x86_64 新包，验证待办滚动、固定搜索按钮打开/关闭，以及“我的 → 设置 → UI 设置 → 返回”流程。当前进程无 AndroidRuntime 错误；这不代替人工目视或低端设备性能验收。
- 运行日志确认稳定布局时渐隐与高斯区域均为物理屏幕 `0..463px`，最大半径 52.5px（20dp）。日志证明配置与顺序，不单独证明最终画面观感。
- 全程没有使用设备截图、录屏或应用界面像素捕获；像素测试只处理人工生成的纯色样本。

### 本地 APK

三个 ABI 均已通过 `apksigner verify --verbose`（v2 签名），`aapt dump badging` 确认包名为 `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev，ABI 分别匹配。

文件位于 `app/build/outputs/apk/debug/`，后缀为 `20261126-0045.apk`：

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `FB8AB63FE32DE457C7F3355CDFAA2E41D2D06230D4CDE9F11238A7700C0A30BE` |
| armeabi-v7a | `E20C636DCBBAF97774985F234A60DBD34203031A6064D3583ACEE471C46BD065` |
| x86_64 | `5B36E98F196090352D55D836318A6B4FFC5C5EE4F071B6940394CB6D6DD96298` |

本机构建使用 Gradle Wrapper 8.14.3，工程 daemon JVM criteria 选 Oracle Java 21，离线本地 Maven init script 保持在工程外，不写入依赖配置。

## 上一轮微调与名片对比度（56dp 尾段）

- 顶部两种效果只通过同一个 `TopBarBlurTail` 从 60dp 缩至 56dp，下缘再上移 4dp；TopBar 高度、按钮位置及高斯参数不变。
- 联系人主列表只增加 8dp 的内部顶部留白，保持满屏滚动视口；固定 TopBar、索引、底栏及扫码按钮的位置不变，不添加影响列表索引的占位条目。
- 首页名片文字按实际文字区域取样：将屏幕采样点反算到 `ContentScale.Crop` 后的源图片，计入原有 25% 黑色遮罩，再比较黑/白文字对比度；不读取主题 `onPrimary`。正常取样每次 8×8 个点，缓存依赖图片、真实布局及底色。
- 没有图片时按卡片实际纯色底决定文字；图片不可用或采样失败时使用固定白色降级并记录 Tester 日志。硬件 Bitmap 仅复制临时软件副本用于计算并在 finally 释放，绝不回收显示中的源图。
- `SocialCardContrastTest` 使用合成图片检查明暗、裁切、暗罩、底色独立性和降级；`SocialCardInteractionTest` 从文字布局结果读取名字/签名的实际填充色，验证同一张图片在明暗主题下保持一致。没有截取真实 UI 像素。

二级页核查未发现普通非空主滚动视口漏接或重复 spring。修正两处回弹绘制边界：手动平台列表增加独立、静止的裁剪父 Box，只有其 LazyColumn 子节点做 spring 平移；联系人搜索同样将结果放入独立裁剪视口，固定搜索框留在外面。独立父容器明确固定区域，避免把这个边界与平移内容混在一起。字母索引留在列表裁剪容器外，不改变它的位置与交互。空导入态、空名片夹和引导平台空态保持原策略；弹窗内部滚动、横向 Pager 和主滚动内的局部选择列表不再额外叠 spring。

新增实际页面手势回归：平台列表滚到底持续上拉、短联系人搜索结果在极限继续上拉；验证存在真实移动、内容不侵入固定搜索区、固定控件仍可用，松手后回位。用独立父视口的实际边界计算断言，从固定屏幕坐标注入并在手指仍按住时取样，不把居中的字母索引区域当作列表边界。平台列表准备态使用 ScrollToIndex 定位末端，避免测试框架反复 ScrollBy 已经积累边缘偏移、把饱和回弹误当成静止状态。

设备对照已确认：联系人首个标题从 y=347 到 y=368，首条联系人从 y=711 到 y=732，即下移 21px（8dp，density=2.625）；TopBar 搜索/更多 y=205、字母索引 y=633、扫码按钮 y=1876、底栏标签 y=2185 均保持不变。渐隐/高斯稳定区域同时为屏幕 `0..452px`，对照上轮 `0..463px`，半径仍为 52.5px（20dp）。无设备截图或真实 UI 像素采集。

本轮最终 `:app:assembleDebug :app:testDebugUnitTest` 返回 BUILD SUCCESSFUL：26 类、193 项选定回归，0 失败、0 错误、0 跳过。包括取色 6 项、首页交互 6 项、联系人 14 项、添加平台 9 项，以及先前的触摸、导航、设置、顶部特效与弹簧状态回归；未运行整个仓库测试集。最终 x86_64 包已安装并启动，联系人坐标与共同下缘再次确认，当前应用进程没有 AndroidRuntime 错误。全程无截图；图片颜色由合成图与文字布局填充色断言验证，真实照片观感未做目视验收。

### 本轮 APK

位于 `app/build/outputs/apk/debug/`，文件后缀 `20261126-0236.apk`。三个 ABI 均通过 apksigner v2 验证；aapt 确认包名 `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev，以及对应 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `940464F5F659A4C6BC2A518222DFE5832666365BAE4EF9900B5AE78EA1B0CD00` |
| armeabi-v7a | `BF3C6B5B1A386CB4D403B5F1DF657292517D8B8724CE49B63FB27283C454D36A` |
| x86_64 | `310C3D2DA9995CFB781B0F1F4617E0C715A0190625EFEB3DF10FCAF2F04F76F2` |

补丁格式检查通过。未提交、未推送。
