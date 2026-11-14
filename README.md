# CareKey Clinical · 医键临床版

**Inpatient management and clinical documentation with optional DeepSeek / MiMo support.**
**住院患者管理与临床文书，可选接入 DeepSeek / MiMo 大模型。**

Part of [CareKey Suite · 医键套件](https://github.com/halala1989/carekey-suite).
本分支属于 [CareKey Suite · 医键套件](https://github.com/halala1989/carekey-suite)。

[English](#english) | [简体中文](#简体中文)

## English

`medical-record-app-v4` is the full Clinical edition of CareKey. It combines patient management, a clinical documentation workspace, writing skills, optional model generation, and direct output to a paired computer or supported HID bridge board.

### Features

- Patient list with create, edit, open, and delete workflows
- Clinical documentation workspace with input and generated-output panels
- Global writing skills and document-specific prompts
- Optional DeepSeek and MiMo configuration, model listing, and connection testing
- Unified phone Bluetooth HID and ESP32-S3 terminal configuration
- Output speed plus Alt+X, hexadecimal, decimal, and GBK character modes
- Pause, resume, stop, draft recovery, and output status controls
- Clinical record storage on the Android device

### Current test build

- Version: `4.0-test33`
- APK: [download](https://github.com/halala1989/carekey-suite/releases/download/v4.0-test33/CareKey-Clinical-4.0-test33.apk)
- SHA256: `FEB14A98987A43130E03C1E02B6AE84E72434B495A2D5E0FA5958EBED0C7A049`
- Release notes: [v4.0-test33](https://github.com/halala1989/carekey-suite/releases/tag/v4.0-test33)

### Build

```powershell
git clone https://github.com/halala1989/carekey-suite.git
cd carekey-suite
git switch medical-record-app-v4
cd android
$env:JAVA_HOME='D:\jdk17\jdk-17.0.20+8'
$env:ANDROID_HOME='D:\Android'
$env:ANDROID_SDK_ROOT='D:\Android'
.\gradlew.bat assembleDebug
```

### AI and privacy boundary

The application does not capture the desktop screen. Patient records and API keys are stored by the Android app. When an AI feature is used, selected prompts and clinical text may be sent to the provider configured by the user. Generated text must be reviewed by a qualified clinician before use.

This is a development project, not a certified medical device. Read the suite [privacy and safety guide](https://github.com/halala1989/carekey-suite/blob/main/docs/PRIVACY-AND-SAFETY.md) and [architecture guide](https://github.com/halala1989/carekey-suite/blob/main/docs/ARCHITECTURE.md).

## 简体中文

`medical-record-app-v4` 是医键套件的完整临床版，整合患者管理、临床文书工作台、书写 Skill、可选模型生成，以及向已配对电脑或受支持 HID 桥接板直接输出。

### 功能

- 患者列表，以及新建、编辑、打开和删除患者流程
- 包含资料输入与病例输出区域的临床文书工作台
- 全局书写 Skill 和各文书专用提示词
- 可选 DeepSeek、MiMo 配置、模型列表获取和连接测试
- 统一管理手机蓝牙 HID 与 ESP32-S3 终端
- 输出速度，以及 Alt+X、十六进制、十进制和 GBK 输出方式
- 暂停、继续、停止、草稿恢复和输出状态控制
- 临床病例资料保存在 Android 本机

### 当前测试版

- 版本：`4.0-test33`
- APK：[下载](https://github.com/halala1989/carekey-suite/releases/download/v4.0-test33/CareKey-Clinical-4.0-test33.apk)
- SHA256：`FEB14A98987A43130E03C1E02B6AE84E72434B495A2D5E0FA5958EBED0C7A049`
- 发布说明：[v4.0-test33](https://github.com/halala1989/carekey-suite/releases/tag/v4.0-test33)

### 构建

```powershell
git clone https://github.com/halala1989/carekey-suite.git
cd carekey-suite
git switch medical-record-app-v4
cd android
$env:JAVA_HOME='D:\jdk17\jdk-17.0.20+8'
$env:ANDROID_HOME='D:\Android'
$env:ANDROID_SDK_ROOT='D:\Android'
.\gradlew.bat assembleDebug
```

### AI 与隐私边界

应用不捕获电脑屏幕。患者资料和 API Key 由 Android 应用保存在本机。用户使用 AI 功能时，所选提示词和临床文字可能发送到用户配置的模型服务商。生成内容在临床使用前必须由有资质的医生逐项审核。

本项目是开发项目，不是经过认证的医疗器械。请阅读套件[隐私与安全说明](https://github.com/halala1989/carekey-suite/blob/main/docs/PRIVACY-AND-SAFETY.md)和[架构说明](https://github.com/halala1989/carekey-suite/blob/main/docs/ARCHITECTURE.md)。
