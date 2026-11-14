# CareKey Suite · 医键套件

**Clinical text input and inpatient documentation for Android, without screen capture.**
**面向 Android 的临床文字输入与住院文书套件，不读取屏幕内容。**

[English](#english) | [简体中文](#简体中文)

> Former repository name: `bluetooth-hid-keyboard`. Existing GitHub links redirect automatically.

> 原仓库名：`bluetooth-hid-keyboard`。旧 GitHub 链接会自动跳转到新仓库。

---

## English

CareKey Suite is an open-source, branch-based software suite for clinical text entry. It turns text typed on an Android phone into standard keyboard input on a connected computer through Bluetooth HID or a supported USB HID bridge board. The connected computer does not need a driver, helper application, or screen-monitoring service.

The suite contains two primary product editions and several engineering or legacy branches:

| Edition | Branch | Artificial intelligence | Intended use |
|---|---|---|---|
| **CareKey Lite** | [`lite-input`](https://github.com/halala1989/carekey-suite/tree/lite-input) | Not required | Direct text-to-keyboard input, common phrases, selectable output speed, and Chinese output modes |
| **CareKey Clinical** | [`medical-record-app-v4`](https://github.com/halala1989/carekey-suite/tree/medical-record-app-v4) | Optional DeepSeek / MiMo configuration | Patient management, clinical documentation, writing skills, and multi-terminal output |
| **Engineering branches** | [`multi-target-6`](https://github.com/halala1989/carekey-suite/tree/multi-target-6), [`esp32-s3-app`](https://github.com/halala1989/carekey-suite/tree/esp32-s3-app) | Varies | Firmware, terminal profiles, and hardware integration experiments |
| **Legacy branches** | `master`, `phone-keyboard`, `plain-bluetooth` | Varies | Historical Pico W and earlier Android product lines |

See [PROJECTS.md](PROJECTS.md) for the complete branch map and maintenance status.

`codex/lite-unified` is retained as the Lite engineering history; `lite-input` is the promoted long-term Lite branch.

### What the Clinical edition includes

- Patient list, patient creation, editing, and deletion
- Clinical record workspace with input and generated-output panels
- Global writing skills and document-specific prompts
- Optional DeepSeek and MiMo model configuration with connection testing
- Output speed and Alt+X, hexadecimal, decimal, or GBK character modes
- Phone Bluetooth HID and ESP32-S3 bridge output
- Unified connection status and configuration

### What the Lite edition includes

- Direct text output to the active desktop input field
- Bluetooth HID and optional bridge-board workflows
- Configurable typing speed and Chinese output modes
- No model account, API key, or network service is required for basic text delivery
- Optional model or chat tools retained from the engineering lineage are not required for this workflow

### Preview

| Patient list | Clinical documentation workspace |
|---|---|
| ![Patient list preview](https://raw.githubusercontent.com/halala1989/carekey-suite/medical-record-app-v4/design-prototype/main-screen-redesign-preview.png) | ![Clinical documentation preview](https://raw.githubusercontent.com/halala1989/carekey-suite/medical-record-app-v4/design-prototype/medical-record-workspace-preview.png) |

### Latest test build

- Clinical edition: [v4.0-test33 APK](https://github.com/halala1989/carekey-suite/releases/download/v4.0-test33/CareKey-Clinical-4.0-test33.apk)
- Release notes: [v4.0-test33](https://github.com/halala1989/carekey-suite/releases/tag/v4.0-test33)
- Build and setup instructions: [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md)

Build from source with Android Studio or:

```powershell
cd android
$env:JAVA_HOME='D:\jdk17\jdk-17.0.20+8'
.\gradlew.bat assembleDebug
```

### Privacy and safety

CareKey does not capture the desktop screen and does not require a desktop agent. Clinical records and API keys are stored by the Android application. When an AI feature is explicitly used, the selected clinical text is sent to the provider configured by the user.

This repository is a development project, not a certified medical device. Clinical use requires institutional authorization, privacy assessment, workflow validation, and clinician review. Never post patient-identifiable information in GitHub issues or pull requests.

Read [docs/PRIVACY-AND-SAFETY.md](docs/PRIVACY-AND-SAFETY.md) before handling real patient data.

---

## 简体中文

医键套件（CareKey Suite）是一个按分支组织的开源临床文字输入软件系列。它把 Android 手机上输入的文字，通过蓝牙 HID 或受支持的 USB HID 桥接板，转换为电脑上的标准键盘输入。目标电脑不需要安装驱动、辅助程序，也不会被读取屏幕内容。

项目包含两个主要产品版本，以及若干工程和历史分支：

| 版本 | 分支 | 大模型 | 用途 |
|---|---|---|---|
| **医键 Lite** | [`lite-input`](https://github.com/halala1989/carekey-suite/tree/lite-input) | 不需要 | 直接文字输出、常用语、速度调节和中文输出方式 |
| **医键临床版** | [`medical-record-app-v4`](https://github.com/halala1989/carekey-suite/tree/medical-record-app-v4) | 可选 DeepSeek / MiMo | 患者管理、临床文书、写作技能和多终端输出 |
| **工程分支** | [`multi-target-6`](https://github.com/halala1989/carekey-suite/tree/multi-target-6)、[`esp32-s3-app`](https://github.com/halala1989/carekey-suite/tree/esp32-s3-app) | 不定 | 固件、终端配置和硬件集成实验 |
| **历史分支** | `master`、`phone-keyboard`、`plain-bluetooth` | 不定 | Pico W 和早期 Android 产品线 |

完整分支职责与维护状态见 [PROJECTS.md](PROJECTS.md)。

`codex/lite-unified` 保留为 Lite 工程历史，`lite-input` 是正式接续的长期 Lite 分支。

### 临床版功能

- 患者列表、新建患者、编辑基本信息和删除患者
- 包含资料输入与病例输出区域的临床文书工作台
- 全局书写 Skill 和各文书专用提示词
- 可选 DeepSeek 与 MiMo 模型配置及连接测试
- 1–10 档输出速度，以及 Alt+X、十六进制、十进制、GBK 四种输出方式
- 手机蓝牙 HID 与 ESP32-S3 桥接板输出
- 统一的连接状态灯和连接配置

### Lite 版功能

- 把文字直接输出到电脑当前输入位置
- 支持手机蓝牙 HID，并可使用可选的桥接板
- 可调输出速度和中文输出方式
- 基础文字输出不要求模型账号、API Key 或网络服务
- 工程历史中保留的可选模型或对话工具，不属于 Lite 核心工作流，也不要求配置

### 最新测试版

- 临床版：[v4.0-test33 APK](https://github.com/halala1989/carekey-suite/releases/download/v4.0-test33/CareKey-Clinical-4.0-test33.apk)
- 更新说明：[v4.0-test33](https://github.com/halala1989/carekey-suite/releases/tag/v4.0-test33)
- 安装与构建：[docs/GETTING-STARTED.md](docs/GETTING-STARTED.md)

### 隐私与安全

本软件不捕获电脑屏幕，不要求安装电脑端代理。患者数据和 API Key 由 Android 应用保存在本机。用户主动使用 AI 功能时，所选临床文字会发送到用户配置的模型服务商。

本仓库是开发项目，不是经过认证的医疗器械。用于真实临床前，必须完成机构授权、隐私安全评估、流程验证和医生审核。请勿在 GitHub Issue 或 Pull Request 中提交任何可识别患者身份的信息。

处理真实患者数据前，请阅读 [docs/PRIVACY-AND-SAFETY.md](docs/PRIVACY-AND-SAFETY.md)。

## Documentation · 文档

| English | 简体中文 |
|---|---|
| [Getting started](docs/GETTING-STARTED.md) | [安装与构建](docs/GETTING-STARTED.md) |
| [Architecture](docs/ARCHITECTURE.md) | [架构说明](docs/ARCHITECTURE.md) |
| [Privacy and safety](docs/PRIVACY-AND-SAFETY.md) | [隐私与安全](docs/PRIVACY-AND-SAFETY.md) |
| [Contributing](docs/CONTRIBUTING.md) | [参与开发](docs/CONTRIBUTING.md) |
| [Release process](docs/RELEASES.md) | [发布流程](docs/RELEASES.md) |
| [Branch catalog](PROJECTS.md) | [分支目录](PROJECTS.md) |

## License · 许可证

[MIT](LICENSE)
