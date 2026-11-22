# 有界加载验证记录（2026-11-22）

本轮保持本地独立使用与按账号同步；实现范围见 [bounded-loading.md](bounded-loading.md)。未提交、推送或部署，禁止截图的约束已遵守。

## 交付门禁

最后一轮聚焦回归、Debug / Release 构建及 lint 全部通过，日志为 `build/bounded-delivery-verification.log`，记录 `BUILD SUCCESSFUL in 11m 44s`。本轮最终 19 个套件、103 项测试，0 失败、0 错误、0 跳过；汇总及套件明细保存在 `build/bounded-delivery-tests.json`。Debug / Release 均按三个 ABI 生成，完整路径、体积和 SHA-256 保存在 `build/bounded-delivery-apks.json`。

`lintDebug` 遵循项目既有 baseline 通过：59 Warning、6 Hint；4 个 Error 与 71 个 Warning 被既有 baseline 过滤，本轮未放宽 baseline。UI 静态审计为 0 Error、9 Warning，已人工复核空状态文案、既有平台提示和实际同步状态；脚本自身 4 项测试通过。两个仓库 `git diff --check` 通过。以上是聚焦验证，未运行全部历史 Android 测试，也不等于人工视觉验收。

服务端本轮 `go test -mod=readonly -count=1 ./...`、`go vet -mod=readonly ./...`、`go build -mod=readonly ./...` 与差异格式检查均通过，记录位于 `F:\GolandProjects\BadgerServer\docs\verification.md` 的“私有资产缩略图扩展”节。测试只使用临时实例，未访问真实 Data、密钥或用户图片。

## 缺陷机制与回归依据

- 联系人页面不再收集完整联系人 List 或全量标签映射。分页每页 30、初载 60、缓存候选上限 180，启用占位与远跳。`maxSize` 是 Paging 的尽力限制，暂态预取与初载可能超过窗口，不宣称严格字节上限。
- 1,000 / 10,000 条 Room 数据的近页和远页均只读取 60 个结果；实际前后滚动后驻留为 180。10,000 条样本的首建派生索引耗时约 565 ms、额外 SQLite 分配 6,831,104 B；这些是宿主 Robolectric 样本，索引存在存储与写入成本。
- 原全量图片下载已从同步 pull 移除。云端资料恢复用例确认业务引用存在且图片 GET 为 0；缓存淘汰不清空资料字段。
- 原图和缩略图内容不同的用例覆盖表示隔离、摘要、错误变体、非图片、429、旧服务端回退；文件 Source、第三个读取等待、重复关闭、异常路径与账号切换覆盖资源和作用域边界。
- 冷缩略图请求在客户端单路执行，与服务端单路生成配合；两路文件 Source 许可保持至解码结束。该用例使用可控调度，验证两个独立缓存键不会同时请求冷生成。
- 包装 Context 的首次 Coil 加载器入口、固定 32 MiB / 100 MiB 配置和组件回调转发分别有回归；真实私有 Fetcher、文件 Source、PNG 解码和缓存管线已通过聚焦用例。
- 名片夹取消移除背景后，本地原件和远端引用均保留；配置重建不通过 onDispose 物理删除草稿。更换后取消与完整配置重建流程尚未逐项进行设备验证。
- 宽幅头像原实现的 4096×256 输入仍输出长边 4096，回归断言为不超过 256；改用长边缩放后，同一用例通过。显示中的 Bitmap 不手动回收。
- Android 原生 FTS4 basic 语法把显式 AND 当普通 token，已改用隐式空格交集。搜索框移出 session key，逐字跨防抖输入、焦点和中间选区有 Compose 回归。
- 删除前方 50 项时，联系人 201 应从位置 200 移到 150，并保留 13px。日志确认排名正确，但初次 scrollToItem 在布局过程中强制重入测量并抛异常。两次定位现均排到下一次 measure，等待真实行及 key / 位置 / 偏移确认；三项滚动专项通过，原断言保留。

## 测试是否能捕获校验缺失

临时只移除原图摘要条件，`PrivateAssetTest.corruptResponseNeverEntersCache` 按预期失败（`expected to be true`）；执行器的 finally 已逐字节恢复源码。`build/bounded-checksum-mutation-result.json` 记录 `caughtMutation=true`、`restored=true`，没有使用弱化校验的代码构建交付 APK。

服务端也临时反转过 variant 边界条件，对应兼容测试按预期失败，恢复后已跑完整 Go 回归。

## 独立 Android 容量验证

设备：独立 emulator-5582，Android 36 x86_64、2 vCPU、1080×2400、420 dpi、WHPX + SwiftShader；请求 1536 MiB，模拟器实际提升为 2048 MiB。没有操作原 emulator-5554。宿主同时运行构建，软件 GPU 和资源竞争影响帧耗时。

先执行了中间 0424 APK 的 1,000 / 10,000 档。发现输入重建与 FTS basic 问题后，保留失败证据；最终候选 0702 以独立结果目录完整复测 10,000 档。不同 APK 的结果不作为优化前后对照。

最终容量候选 SHA-256：`ddd47400ff4ff647bf29016a41c1ecaabe7c5c6a94fc45e90f533211b448f7fc`。锁定副本在 `build/capacity-20261122-ee1cbf5f/candidate-0702-locked.apk`。此后业务代码仅修正上述 ScrollPolicy 的测量调度；静态滚动、搜索与缓存预算的测量范围没有包含数据库删除动作。

| 10,000 档检查 | 实际结果 |
| --- | --- |
| 连续滚动 | 60 手势，41.39 秒，474 个有效帧 |
| 帧耗时 p50 / p95 / p99 | 137.70 / 209.52 / 268.96 ms |
| 全流程采样 PSS 峰值 | 205.83 MiB |
| 滚动期间采样 PSS 峰值 | 197.60 MiB |
| RSS 内核高水位 | 347.18 MiB |
| 最大加载联系人 | 180 |
| 工作负载结束后数据目录分配空间 | 230.96 MiB |
| 从注入数据后到工作负载结束的增长 | 40 KiB |
| 六类搜索 | 宽泛姓名、精确姓名、邮箱片段、平台、标签、无结果，各 3/3，共 18/18 |
| 普通连续输入 | 18 次完整查询值核对通过，未使用逐字符规避 |
| 实际图片入口 | 四次启动均记录 33554432 / 104857600 B 配置和账号命名空间拦截 |

数据包含 20,000 字段值、10,000 平台行、20 标签、10,000 标签关系和 10,000 个独立头像路径（100 种合成内容）。原生 SQL 验证邮箱字段命中 1、备注旁路命中 0、平台命中 1、标签关联 500；Room v6 identity 保留、完整性 ok。App fatal 为 0。

帧时间戳中的无效未来值按原始 uptime 核查并剔除，保留原始 dump 与剔除数量。第一次 final 尝试在标签业务断言已通过后，多余的 UI 导出超时；原尝试保留。驱动删除这次冗余导出并保存同一断言 XML 后，从干净 fixture 重新完整跑三轮；最终 111 次 UI 导出全部成功，没有拼接中断轮次。

证据：`build/capacity-20261122-ee1cbf5f/capacity-summary-final.json`、`final-postcheck.json`、`protocol.json` 及 `results-10000-final/`。UIAutomator 搜索观察包含导出开销，其数值是观察上界，不代表 SQL 耗时；本环境也不能证明实体手机达到流畅度目标。

## 同步容量与边界

Robolectric、同进程 HTTP fixture、C1 测试 JVM 中，0→1,000 与 1,000→10,000 的文字恢复均通过，图片 GET 为 0。事务内 ID 索引优化前的宿主样本为约 7.0 / 132.1 秒，优化后的最终完整回归样本为约 8.0 / 69.3 秒；保留原始 stdout，不据单次采样宣称设备加速倍数。

这些结果覆盖客户端 Room / 编解码 / 映射恢复逻辑，不是 Go 网络吞吐或手机同步帧耗时。同步仍使用完整文字快照及既有容量限制，尚未改为增量同步。

## 包体与未覆盖项

最终 Release：arm64-v8a 39.30 MiB、armeabi-v7a 27.77 MiB、x86_64 44.29 MiB，文件名时间标记为 `20261122-0732`。arm64 制品为 `app/build/outputs/apk/release/Badger-1.0.0-3-arm64-v8a-20261122-0732.apk`，SHA-256 为 `19aa326f2b45f9c241cb25056624ff81d9352a745270ce0525c37a18eb0a89ca`。arm64 的 OpenCV 22.42 MiB、OCR 10.55 MiB 仍在包内；本轮没有实现 OCR 可选下载或拆出识别组件。三 ABI Debug 分别约 64.05 / 52.52 / 69.04 MiB（arm64 / armv7 / x86_64）。完整制品清单见 `build/bounded-delivery-apks.json`。

最终 Debug / x86_64 SHA-256 为 `1fab6f30bc542c6f47a49337f5206243ae3ccf5fe7a0bd9dba02170eee47e1ac`；它与容量测试的 0702 候选不同。该差异对应滚动恢复测量调度修正，容量报告仍明确绑定其实际测试 APK，没有把旧 APK 测量冒称为最终 APK 实测。

未覆盖：真实手机性能与人工视觉 / 读屏验收、设备上的详情返回逐项回归、实际 App 对新 Go 缩略图的网络 E2E、网络缓存压满的设备实测、Go 新增吞吐 / race、全部历史 Android 单测、图片完整离线导出及灾备恢复。公开名片、JS provider、OCR 可选交付属于后续独立能力。

自建设备清理已完成：5582 AVD 及归属进程正常退出，端口释放；删除仅本轮创建的 AVD、合成 DB / 模板、头像及失败 helper 等共 2,445,380,550 B 逻辑文件。321 份报告、脚本和原始统计的哈希保持不变，三份 APK 保留；原 emulator-5554 仍在线，transport ID 保持 3。没有操作其设备 UI、数据或 SDK 共享镜像。结果在 `build/capacity-20261122-ee1cbf5f/cleanup-result.json`。