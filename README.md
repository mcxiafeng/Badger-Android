<div align="center">

# Badger

### 一本不只是名片的电子名片册

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(8.0)-3DDC84?style=flat-square)](https://www.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-37-3DDC84?style=flat-square)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-GPL--v3-blue?style=flat-square)](./LICENSE)

把联系人、社交身份、NFC 活链，统统装进你的口袋。

[功能](#-功能) · [支持平台](#-支持平台) · [系统要求](#-系统要求) · [架构](#-技术架构) · [构建](#-本地构建) · [计划](#-计划)

</div>

---

## ✨ 功能

<table>
<tr>
<td width="50%" valign="top">

### 📷 扫码添加
扫一扫别人的社交二维码，自动识别平台和账号，一键保存。也可以拍下实体名片，AI 帮你提取关键信息。

保存的不只是联系方式，还有这个人的"样子"——头像、昵称、平台身份，都一并记录。

</td>
<td width="50%" valign="top">

### 🪪 我的名片
把你所有的社交账号汇聚到一张数字名片上，随时切换展示。今天想突出 GitHub，明天换成微信，一键搞定。

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 📇 名片夹
像真正的名片册一样，把联系人分到不同的名片夹里归类。每个名片夹可以有自己的样式，也可以共用一种。支持按需导出，数据真正归你所有。

</td>
<td width="50%" valign="top">

### 📡 NFC 活链
把一个可变的链接写入 NFC 卡片，别人手机碰一下就能打开。**重点是：跳转目标可以在 APP 内随时切换，不用重新刷卡。** 一张卡，今天指向你的微信，明天换成 GitHub。

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 🔐 去中心化
数据存你手机里，AI、云同步、短链接等能力全部对接外部第三方服务，APP 本身不持有你的数据。

</td>
<td width="50%" valign="top">

### ☁️ WebDAV 备份
支持 WebDAV 协议备份与恢复，跨设备迁移、换机无忧。数据去向由你自己掌控。

</td>
</tr>
</table>

---

## 🌐 支持平台

图例：`✅ 可用` &nbsp;&nbsp; `⚠️ 未测试` &nbsp;&nbsp; `❌ 不可用`

| 平台 | 状态 | 备注 |
|:---:|:---:|:---|
| QQ | ✅ | |
| B 站 | ✅ | |
| GitHub | ✅ | |
| X (Twitter) | ✅ | |
| Telegram | ✅ | |
| 个人网站 | ✅ | |
| 微博 | ⚠️ | 仅可识别，平台政策限制无法同步 |
| 抖音 | ⚠️ | 仅可识别，平台政策限制无法同步 |
| Facebook | ⚠️ | 仅可识别，平台政策限制无法同步 |
| 微信 | ❌ | 平台政策限制，仅可识别 |
| 小红书 | ❌ | 平台政策限制，仅可识别 |

---

## 📸 截图

> 🚧 即将补上...

---

## 📱 系统要求

| 项目 | 要求 |
|:---|:---|
| 最低系统 | Android 8.0 (API 26) |
| 目标系统 | Android 16 (API 37) |
| 架构支持 | `arm64-v8a` · `armeabi-v7a` · `x86_64` |

### 权限说明

| 权限 | 用途 | 是否必需 |
|:---|:---|:---:|
| 📷 相机 | 扫描二维码 / 拍摄实体名片 | 必需 |
| 📡 NFC | 写入 NFC 标签 | 可选 |
| 🌐 网络 | 云端同步 / AI 识别 | 可选 |
| 💾 存储 | WebDAV 备份 / 导入导出 | 可选 |

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────┐
│              UI Layer (Compose)              │
│   Material 3 · Miuix · Haze Blur · Paging 3 │
└──────────────────┬──────────────────────────┘
                   │ StateFlow / ViewModel
┌──────────────────▼──────────────────────────┐
│            Domain / ViewModel                │
│      Hilt DI · Coroutines · Flow API         │
└──────────────────┬──────────────────────────┘
                   │ Repository
┌──────────────────▼──────────────────────────┐
│              Data Layer                      │
│   Room (SQLite) · DataStore · WebDAV Client  │
└─────────────────────────────────────────────┘
```

### 技术栈

- **语言**：Kotlin 2.x · JVM 17
- **UI**：Jetpack Compose · Material 3 · [Miuix](https://github.com/compose-miuix/miuix) · Haze Blur
- **架构**：MVVM · Hilt · Coroutines · Flow
- **数据库**：Room (KSP)
- **相机**：CameraX · ML Kit (中文识别)
- **二维码**：ZXing · 微信 OpenCV
- **网络**：OkHttp · Coil
- **导航**：Navigation Compose

---

## 🛠️ 本地构建

```bash
# 克隆仓库
git clone https://github.com/yourname/badger.git
cd badger

# Debug 包
./gradlew :app:assembleDebug

# Beta 包
./gradlew :app:assembleBeta

# Release 包
./gradlew :app:assembleRelease
```

构建产物会按 ABI 拆分：

```
app/build/outputs/apk/beta/beta/
├── Badger-1.0.0-3-beta-arm64-v8a-20260611-1530.apk
├── Badger-1.0.0-3-beta-armeabi-v7a-20260611-1530.apk
└── Badger-1.0.0-3-beta-x86_64-20260611-1530.apk
```

### 环境要求

- Android Studio Ladybug | 2024.2.1 或更高
- JDK 17
- Android SDK 37
- Gradle 8.x（Wrapper 已包含）

---

## 📋 计划

- [ ] 🌍 i18n 多语言支持
- [ ] 🏷️ 联系人标签分类
- [ ] 💧 整体 UI 液态模糊
- [ ] ♿ 无障碍（TalkBack / 动态字号 / 触控目标尺寸）

---

## 💬 联系我们

加入我们，一起讨论功能、反馈问题、催更催更 🦡

| 平台 | 链接 |
|:---|:---|
| QQ 群 | `1106424576` |
| Telegram 群 | `https://t.me/+TCvPsqPXQltjOWM1` |
| Matrix 房间 | `https://matrix.to/#/#Open-Badger-APP:matrix.org` |

---

## 🤝 贡献

欢迎提 Issue 和 PR！如果你：
- 发现了 Bug
- 有新平台解析需求（提供样本链接最佳）
- 想贡献翻译 / 文档
- 想要新功能

都可以直接开 Issue 讨论。

---

## 📄 许可

本项目基于 [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.html) 开源。

你可以自由地使用、修改和分发本项目，但所有衍生作品**必须同样以 GPL-3.0 协议开源**。完整条款见根目录 `LICENSE` 文件。

---

<div align="center">

**[⬆ 回到顶部](#-badger)**

Made with ❤️ & ☕

</div>
