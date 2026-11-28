# 顶部内容渐隐与高斯渐进模糊

> 2026-11-28（master）：自定义 TopBar 与顶部渐隐（`ScrollTopFade`）+ 滚动驱动强度（`TopEdgeScroll`）从 `liquid-glass-interface` 分支回到 master；**同日顶区渐进高斯模糊按用户要求移除**（master 无页面顶部模糊，历史实现见分支）。与分支差异：`SpringOverscroll` 未恢复（master 用平台/Miuix 原生 overscroll 回弹）；`GlassScaffold.Scaffold` 为裁剪版（`topEffects` 参数只接线渐隐区域与滚动强度，不录制背景、无玻璃 chrome）。

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

2026-11-27 材质补充：**每档高斯后串一级色彩还原 ColorFilter**（`createColorFilterEffect(ColorMatrixColorFilter(glassBlurColorMatrix))`：饱和度还原 浅色 1.7 / 深色 1.55 + R/G/B 通道提亮 1.05/1.02/1.06，两个 Liquid Glass Skill 的金色配方收敛值）——高斯平均会去饱和并压暗，不还原的磨砂在彩色内容上读作一片奶灰。第 0 档（原始透传）保持不还原，否则区域下缘会在原始内容上叠加饱和拷贝。**第 0 档（sigma=0）绝不能创建 RenderEffect**：零 sigma 的原生创建在真机上抛 `IllegalArgumentException: nativePtr is null`（issuetracker 241546169，2026-11-27 实机崩溃回归），必须像首版一样对第 0 档短路。

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

## 下拉刷新与顶栏避让（2026-11-26，2026-11-27 更新）

- 下拉刷新统一走 `ui/components/WorkspacePullRefresh`，封装 Miuix 0.9.3 自带的 `PullToRefresh`（MIUI 风格刷新头：圆环、文案、弹簧回弹与触感反馈）。此前使用的 M3 1.3.1 `pullToRefresh` 依赖重组观察到 `isRefreshing` 的跳变才收起指示器：同步在一帧内 true→false 的丢失脉冲、或回调未置起 refreshing 的空刷新，都会把指示器卡在阈值位置（用户反馈「拉下来后有概率收不回去」）。Miuix 状态机按级别同步 hoisted 标志，这些情况都会立即播放收尾动画。
- `WorkspacePullRefresh` 的 `topInset`（顶栏高度）经组件的 `contentPadding` 下移刷新头，容器保持满屏接收手势；此前联系人页指示器顶到屏幕 y=0（状态栏之后、顶部模糊层之下）的问题一并消除。
- 「用户与同步」页移除外层 `Box.padding(inset)` 的整体避让，顶栏高度改为进入 LazyColumn `contentPadding` 与刷新头避让；本地导入空态保留顶栏高度的顶部留白，居中空态直接满屏居中。
- `enabled` 只在当前没有刷新进行时才整体卸载组件；刷新进行中（例如下拉触发同步后账号任务 busy 把 enabled 翻成 false）保持挂载，让收尾动画完整播放，刷新提示不会中途消失。该规则修复了下拉触发同步后组件立即被拆卸、「正在同步资料」永不出现的回归。
- 验证：`WorkspacePullRefreshTest` 以「首行归位 + 回调真实触发过」断言亚帧脉冲与空回调两种卡死场景的收回；`AccountPageTest` 覆盖下拉触发同步、刷新中重复下拉不再触发、完成后收回。`:app:assembleDebug` 与相关回归通过；未做真机目视验收。

## 滚动驱动强度（2026-11-27 顶栏动效重做，同日合成修正）

依据 Apple HIG「Adopting Liquid Glass」的 scroll edge effect：滚动视图在内容滚到栏下方时加深遮蔽以保证控件可读性。本轮把顶部渐隐与高斯的强度改为「基线 + 滚动加深」：

- 所有页面共享统一基础磨砂（`TOP_EDGE_BASE_STRENGTH` = 0.7），静止与不可滚动页面保持在基线，保证页面之间、以及与玻璃底栏的观感恒定统一；内容滚入顶区后随滚动量（0..`topEdgeScrollTravel`，56dp）smoothstep 从基线加深到满强度。
- `GlassScaffold` 在内容 Box 上挂一个 `TopEdgeScrollState.connection`（`NestedScrollConnection.onPostScroll`），位于所有滚动容器之上；只累计被真实消费的 `consumed.y`，因此空列表拖拽、下拉刷新回拉、未被消费的手势都不会虚增强度。横向滚动 `consumed.y` 为 0，不受影响。
- **合成规则（首版教训）**：渐隐强度必须通过**重烘渐变端点**生效——只有顶端点透明度随强度抬升（α=(1−s)·255），底端点保持全白；**禁止用 `paint.alpha` 均匀缩放整张遮罩**——那会同时压低底端点，造成区域下缘行半隐（看不清）与顶区露出底色灰洞（灰蒙蒙/染色感）。高斯层的加法混合量按强度线性缩放（`GaussianBlurLevel` 遮罩 α 乘强度），配对后的任意中间强度都无空洞：内容透明度 1−s·strength(y) 恰好等于模糊层加法量。
- 强度是绘制期读取的 `mutableFloatStateOf`，滚动帧只重绘不重组；着色器缓存以 region+screenY+strength 为键，静止时不重建。
- 纯函数（`progressiveTopBlurStrength`、`gaussianLevelAlpha`、几何类）未改，`ScrollTopFadeMaskTest` / `ProgressiveTopBlurGeometryTest` 保持原断言（`paint` 的 strength 默认 1 时与旧渐变完全一致）。
- 同轮配套：顶栏标题/副标题模式切换改 `AnimatedContent` 150ms 淡入淡出 + 10% 上滑（`GlassTopBar.PinnedTitle`）；底栏选中 Tab 改 150ms 变色 + 图标 106% 微放大，删除 `indicatorContentTint` 裁剪染色层（见 `docs/ui-spec.md` 动效规范第 7 条与底栏补充条目）。
- 已知边界：仅靠进程重建恢复的 `rememberSaveable` 滚动位（无滚动事件派发）首帧停留在基线，下一次滚动即校正；`PersonScrollAnchor` 的恢复经 `scrollToItem` 派发事件，不受影响。未做真机目视验收，日志标签 `TopEdgeScrollTester`（挂载/满强度/回基线）。
