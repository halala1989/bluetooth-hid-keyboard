# CareKey Lite · 医键 Lite

**Direct phone-to-computer text input through Bluetooth HID. No AI model is required.**
**无需大模型，通过蓝牙 HID 把手机文字直接输入电脑。**

Part of [CareKey Suite · 医键套件](https://github.com/halala1989/carekey-suite).
本分支属于 [CareKey Suite · 医键套件](https://github.com/halala1989/carekey-suite)。

[English](#english) | [简体中文](#简体中文)

## English

`lite-input` is the long-term Lite edition of CareKey. It is based on the pre-LLM Android lineage and intentionally keeps the workflow small: type text on the phone and send it to the active input field on a paired computer.

### Features

- Standalone Android Bluetooth HID keyboard
- No desktop driver, companion service, screen capture, or remote-control agent
- Text input, common phrases, output speed, and Chinese character modes
- Alt+X, hexadecimal, decimal, and GBK output options in the original Lite lineage
- Core text output does not require model configuration, API key, or network service

### Use

1. Install the app on an Android 9 or later device that supports Bluetooth HID.
2. Enable the keyboard mode and allow the phone to become discoverable.
3. Pair the phone with the target Windows computer.
4. Focus the intended text field on the computer and send text from the phone.

### Build

```powershell
git clone https://github.com/halala1989/carekey-suite.git
cd carekey-suite
git switch lite-input
cd android
$env:JAVA_HOME='D:\jdk17\jdk-17.0.20+8'
$env:ANDROID_HOME='D:\Android'
$env:ANDROID_SDK_ROOT='D:\Android'
.\gradlew.bat assembleDebug
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

### Boundary

The Lite edition does not diagnose, summarize, or generate medical records. It only delivers text and key events selected by the user. The target computer application remains responsible for accepting and displaying that text.

The branch may retain optional model or chat tools inherited from the engineering lineage. They are separate from the Lite text-delivery workflow and do not require configuration for normal use.

See the suite [getting-started guide](https://github.com/halala1989/carekey-suite/blob/main/docs/GETTING-STARTED.md) and [privacy guide](https://github.com/halala1989/carekey-suite/blob/main/docs/PRIVACY-AND-SAFETY.md).

## 简体中文

`lite-input` 是医键套件的长期 Lite 版本。它基于加入大模型之前的 Android 产品线，刻意保持较小的工作流：在手机上输入文字，然后发送到已配对电脑的当前输入框。

### 功能

- 独立的 Android 蓝牙 HID 键盘
- 不需要电脑驱动、配套服务、屏幕捕获或远程控制代理
- 文字输入、常用语、输出速度和中文输出方式
- Lite 原始产品线支持 Alt+X、十六进制、十进制和 GBK
- 基础文字输出不需要模型配置、API Key 或网络服务

### 使用

1. 在支持蓝牙 HID、Android 9 或更高版本的手机上安装应用。
2. 开启键盘模式，并允许手机对附近设备可见。
3. 把手机与目标 Windows 电脑配对。
4. 在电脑上点击目标输入框，然后从手机发送文字。

### 构建

```powershell
git clone https://github.com/halala1989/carekey-suite.git
cd carekey-suite
git switch lite-input
cd android
$env:JAVA_HOME='D:\jdk17\jdk-17.0.20+8'
$env:ANDROID_HOME='D:\Android'
$env:ANDROID_SDK_ROOT='D:\Android'
.\gradlew.bat assembleDebug
```

APK 输出位置：`android/app/build/outputs/apk/debug/app-debug.apk`。

### 产品边界

Lite 版不进行诊断、总结或病历生成，只输出用户主动选择的文字和按键事件。文字是否被接受和显示，由目标电脑上的软件决定。

本分支可能保留工程历史中的可选模型或对话工具。这些工具与 Lite 文字输出流程相互独立，正常使用不要求配置模型账号或 API Key。

套件说明见[快速开始](https://github.com/halala1989/carekey-suite/blob/main/docs/GETTING-STARTED.md)与[隐私安全](https://github.com/halala1989/carekey-suite/blob/main/docs/PRIVACY-AND-SAFETY.md)。
