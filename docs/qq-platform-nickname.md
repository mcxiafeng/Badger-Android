# QQ 平台昵称与失败回退（2026-11-26）

## 原因

设备脱敏日志确认现有 QQ 资料接口请求发生 SocketTimeoutException，随后出现 `QQ profile lookup returned no response body`。QqAdapter 原来在此时生成「QQ用户＋号码」，LoadPlatformProfileUseCase 又把它视为有效资料，首页因此显示并缓存该字符串。

首页资料加载本身不写联系人或全局姓名；联系人同步入口此前可能把同一生成名称保存为平台昵称和全局姓名。

## 修改

- QqAdapter 只返回实际获取到的昵称；查询失败时昵称为空，QQ 账号及兜底头像仍保留。
- 平台资料没有有效昵称和签名时返回空结果，不再记作加载成功。仅获取签名而没有昵称时，可在后续请求重试。
- 统一昵称验证供首页、二维码、资料加载和联系人同步复用。只拒绝当前 QQ 账号对应的完整生成名称，支持别名与仅链接账号；正常的「QQ用户昵称」不会被前缀规则误删。
- 首页保留「有效的已保存平台昵称 → 有效的获取昵称 → 账号值 → 平台标签」顺序，不借用其他平台的名字。二维码保留原有全局姓名回退，同时过滤已保存或获取的生成名称。
- 联系人同步分别验证远端和已保存昵称，避免历史生成名称再次覆盖联系人姓名；头像仍独立同步。首页加载继续只更新当前账号的展示缓存，不自动修改已保存的姓名。

现有服务超时时不能凭空恢复真实 QQ 昵称。未增加替代昵称服务；本轮网络测试使用合成账号与模拟响应，不拿真实账号向外部服务做诊断查询。

## 验证

执行低内存串行构建（单次 Gradle、1536m、Serial GC、Kotlin 进程内编译、本地 Maven 与 serial-tests init），目标为 `:app:assembleDebug :app:testDebugUnitTest`，过滤 `*QqProfileLookupTest`、`*SocialViewModelTest`、`*QrDisplayIdentityTest`、`*SocialCard*Test`、`*ContactDetailViewModelTest`、`*ContactDetailInteractionTest`、`*ParseLocalContentTest`。

覆盖有效昵称、查询失败、仅签名、历史生成昵称、保留已保存昵称、重新获取成功、账号隔离和首页不写姓名。8 个实际执行测试类共 105 项，0 失败、0 错误、0 跳过。首次构建已输出 BUILD SUCCESSFUL 并生成完整报告，但命令在退出清理阶段触及后台时限而被停止；同组增量复验正常返回 BUILD SUCCESSFUL（59s、退出码 0），主编译和测试任务均为 UP-TO-DATE。未运行全仓库测试。

API 36 模拟器安装本轮 x86_64 包，应用已有的资料请求本次成功获取 QQ 昵称。元素树确认首页不再显示生成标签，脱敏日志为 `hasNickname=true; generatedName=false`，页面选择来源为 `saved=false; fetched=true`。未修改已保存的姓名，没有记录真实账号或昵称。接口成功与否仍取决于网络和现有服务，本轮失败与成功路径都已验证；未使用设备截图。

最终 APK：`app/build/outputs/apk/debug/Badger-1.0.0-dev-3-<ABI>-20261126-1602.apk`。三个 ABI 均通过 apksigner 验证，aapt 确认包名 `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev 和对应 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `7080F96C680EA1F7F982288ED36121C8EB9197EFCDCE185DAC9F6C78EDF49F52` |
| armeabi-v7a | `4CDEBD4D0B42472476E7CDC3013AB951FE8C3BD639B540E3A66FEBA06DEBE11E` |
| x86_64 | `833BC4D6F3A49D601924321F5DC3A57BCDB0F4335682459922E9E99F57450A1F` |
