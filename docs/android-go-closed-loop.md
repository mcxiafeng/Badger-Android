# Android 与 Go 当前闭环验证（2026-11-22）

结论：**当前账号与私有同步闭环已实际跑通，本轮范围未发现阻断性产品缺陷。** 本轮没有修改 Android 或 Go 业务源码，没有 commit、push 或部署。原有待提交内容保持原样；新增验证记录与本轮临时驱动。

## 环境与制品

- 当前 `NewMain` 工作树，Android Debug / x86_64，独立干净 Android 13 / API 33 AVD `BadgerLoop26d1`，串号 `emulator-5580`。启动参数为 `-read-only -no-snapshot -no-window`，系统镜像来自已安装 SDK，没有复制原 AVD 用户资料。
- Go 使用本轮重新构建的 Go 1.26.11、`CGO_ENABLED=0` 二进制；独立合成数据目录、随机回环端口、两个合成账号。控制端与 App 均通过真实 HTTP 操作服务。
- App 通过真实登录表单认证。仅预置引导完成状态和测试连接地址，没有注入会话。
- 全程无截图。使用无障碍树、HTTP、只读合成数据库查询、图片摘要和脱敏日志核对，没有人工目视验收。
- 原有 `emulator-5554` 未被安装、清库、改配置或停止，结束时仍存在。

| 制品 | 路径 / 摘要 |
| --- | --- |
| 实际安装 APK | `app/build/outputs/apk/debug/Badger-1.0.0-dev-3-x86_64-20261122-0148.apk` |
| APK SHA-256 | `489239ffcd63bace1670a7111828df0e7041cfb8a5e14da0ae83f2b581f92ff7` |
| Go 二进制 | `F:/GolandProjects/BadgerServer/build/loop-20261122-a8f17c4d/badger.exe` |
| Go SHA-256 | `a71acf02bca08e3f0e5959bf52029ac0d32d5c0837e69e704b55e2f8f6099a38` |
| Android 本轮证据目录 | `build/full-loop-20261122-26d1db7f` |
| Go 本轮证据目录 | `F:/GolandProjects/BadgerServer/build/loop-20261122-a8f17c4d` |

Android 证据目录保留构建日志、16 份测试 XML、`android-tests.json`、`apk.json`、`events.jsonl`、`flow-result.json`、`device-app.log`、`log-checks.json`、`cleanup-result.json` 及本轮驱动。Go 目录保留各项 status JSON、日志及二进制。均为本地忽略目录，不代表已发布 CI 制品。

## 构建和测试

| 检查 | 本轮结果 | 范围 |
| --- | --- | --- |
| assembleDebug | 通过 | 三 ABI Debug APK；实际安装 x86_64 |
| Android 聚焦单测 | 16 套件、134 项，0 失败 / 0 错误 / 0 跳过 | 账号、迁移、认证、同步、导入、冲突、页面及直接相关 Repository |
| lintDebug | 通过 | 59 Warning、11 Hint，无未被基线过滤的 Error |
| UI 静态审计 | 0 ERROR、9 WARN | 显式扫描当前 worktree 的 app/src/main |
| 审计脚本自身测试 | 4 项通过 | test_ui_static_audit.py |
| Go build / vet | exit 0 | 本轮制品；vet 无诊断 |
| Go test | 71 顶层测试、151 个含子测试的通过事件，0 fail | 4 个测试包通过、1 个无测试包；后者不计测试用例跳过 |
| Go 真实进程 smoke | exit 0 | 登录、CAS/幂等、图片隔离、快照摘要、强制重启、退出、进程锁、一致备份、日志脱敏 |

Android 实际执行：

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest \
  --tests 'top.mcxiafeng.badger.data.account.*' \
  --tests 'top.mcxiafeng.badger.data.sync.*' \
  --tests 'top.mcxiafeng.badger.network.auth.*' \
  --tests 'top.mcxiafeng.badger.network.sync.*' \
  --tests 'top.mcxiafeng.badger.pages.account.*' \
  --tests 'top.mcxiafeng.badger.pages.auth.AuthViewModelTest' \
  --tests 'top.mcxiafeng.badger.pages.settings.SettingsAccountStateTest' \
  --tests 'top.mcxiafeng.badger.data.repository.ContactRepositoryImplTest' \
  --tests 'top.mcxiafeng.badger.data.repository.AddContactPlatformsTest' \
  :app:lintDebug \
  --init-script 'F:/Java/Badger/worktrees/main-plus-new-backend/build/account-test-diagnostics.init.gradle' \
  --console=plain
```

使用已有临时 C1 测试配置，避开此前记录的本机 C2 原生崩溃，没有修改生产 JVM 配置、跳过断言或改变产品检查。构建日志为 `android-build-tests.log`，记录 BUILD SUCCESSFUL，耗时 5 分 40 秒。

Go 使用指定启动器的 `-C F:/GolandProjects/BadgerServer`，实际执行 `build -mod=readonly`、`test -mod=readonly -count=1 -json ./...`、`vet -mod=readonly ./...`，以及 `python -I` 运行 `scripts/smoke.py --binary <本轮二进制>`。命令、cwd、时间和 exit code 保存在各项 `*.status.json`；未复用历史测试结果。

UI 审计使用 `python -I` 执行工作区脚本，显式传入当前 `app/src/main --json --fail-on-error`；脚本测试使用 `python -I -m unittest discover -s F:/Java/Badger/tests -p test_ui_static_audit.py -v`。9 个预警已查对应上下文：空状态文案及注释、既有平台资料刷新提示、私有同步的“已同步”状态。后者由实际拉取、上传、待写和冲突判断及本轮设备链路支持；其余不是本轮新增 UI，不扩大修改，也不据此宣称视觉通过。

## 实际 App 闭环

| 场景 | 观察结果 |
| --- | --- |
| 本地录入与进程重启 | 通过 UI 创建联系人及简介，重启后保留 |
| 登录 A | 真实表单登录成功，服务端新增会话；本地联系人未自动上传 |
| 导入预览 | 查看预览仍不上传；显示目标账号、来源与复制范围 |
| 确认导入 | 服务端出现一个联系人副本，同时完成本次所选自我资料及默认夹导入；本地原件保留 |
| 私有图片 | App 下载至 A 专属目录，文件 SHA-256 与 canonicalSha256 相同 |
| Android → Go | App 修改简介，服务端精确收到新值并推进版本 |
| Go → Android | 控制端更新简介和备注，强制停止并重启 App 后显示精确新值 |
| 离线修改 | 移除测试 ADB reverse 后修改仍保存在本机，云端保持原值 |
| 同字段竞争 | 控制端修改同一字段，恢复连接并重启后显示 1 项冲突；双方内容均保留 |
| 裁决 | 通过实际冲突页选择本机内容，服务端更新为该值；无关备注和头像引用保留，冲突清除并显示已同步 |
| 退出 A | 真实确认弹窗退出后回本地；服务端会话数 3→2；本地原件简介仍在 |
| 登录 B | 拒绝导入后，列表和快照仅有 B 的联系人；A 与本地联系人不可见；B 请求 A 图片为 404 |
| 退出 B | 服务端会话数再次 3→2 |
| 重新登录 A | 回到原账号空间；已导入联系人没有重复，裁决结果、备注和图片均保留 |
| 重复导入 | 未修改源资料默认跳过，空选择提交明确拒绝；显式选择合并原副本后新增 0 项，联系人数量及版本不变 |
| 最终退出 | 回到本地空间，对应 App 服务端会话已撤销 |

`events.jsonl` 保存 23 条通过的流程观察/断言记录，不能与单元测试数相加作为一个统一测试总数。两名账号在同一个独立 Android 实例中顺序验证；冲突的另一写入方为独立 HTTP 控制端，不称作两台 Android 设备联调。

## 运行中处理的驱动问题

- 原 AVD 复用启动被占用锁拒绝；保留失败日志，改用独立配置与 SDK 干净 userdata，未停止原设备。
- 初版驱动用组合键清空 Compose 文本框未生效，导致期望字符串校验失败。UI 与服务端实际值一致；改为按当前字段长度删除，并在保存前核对输入值，随后原双向修改场景通过。没有改同步逻辑来迁就驱动。
- 重复导入的初始脚本预期“全跳过也成功返回 0”，实际契约要求至少选择一项，UI 正确拒绝。核对 WorkspaceImport.validateTargets 后验证拒绝时零写入，再显式选合并原副本，确认返回 0 且版本不变。
- 审计报告初始 stdout 为本机编码，已转换为 UTF-8；没有通过重跑业务测试掩盖产品失败。

## 清理与限制

实际完成：停止自建 Go 进程和独立 AVD；核对实例身份、账号名及目录内容后删除合成数据库、明文 fixture 凭据文件和新 AVD 文件。脱敏日志保留，未发现本轮账号密码或 token 泄漏或 AndroidRuntime 崩溃。`cleanup-result.json` 记录清理结果，原有模拟器仍在。

未运行 / 未覆盖：真机、两个 Android 设备并发、HTTPS/反向代理部署、全量 Android 单测、全部历史数据库导入、NFC 硬件及人工视觉/读屏验收。Go 一致备份有进程 smoke 证据，完整恢复、新 dataEpoch 及会话撤销工具仍未实现，本轮不宣称灾备闭环已交付。JS provider 尚未接入，是独立后续工作。
