# Getting Started · 快速开始

[English](#english) | [简体中文](#简体中文)

## English

### 1. Choose the edition

- Choose [`lite-input`](https://github.com/halala1989/carekey-suite/tree/lite-input) for direct text-to-keyboard output without model features.
- Choose [`medical-record-app-v4`](https://github.com/halala1989/carekey-suite/tree/medical-record-app-v4) for patient management and clinical documentation.

### 2. Requirements

- Android 9 or later on a phone that supports Bluetooth HID
- A Windows computer with Bluetooth
- Optional: a supported ESP32-S3 bridge board for USB HID output
- JDK 17 and Android SDK 34 when building from source

### 3. Build the Android app

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

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

For the Lite edition, switch to `lite-input` before building.

### 4. Connect the target computer

1. Install and open the app.
2. Tap the connection status indicator or open connection configuration.
3. Pair the phone with the target computer, or select a supported bridge board.
4. Confirm that the status indicator turns green.
5. Put the cursor in the intended desktop text field and start output from the app.

The computer does not need a driver or companion program. If the phone is used as the HID keyboard, the operating system may request a pairing code confirmation.

### 5. Chinese output

Choose the output mode that matches the desktop application:

- **Alt+X** works with many RichEdit applications such as Notepad and Word.
- **Hexadecimal** may require the Windows `EnableHexNumpad` setting and NumLock.
- **Decimal** works in some legacy RichEdit editors.
- **GBK** is primarily for Chinese Windows environments.

Start with a slower output speed if characters are lost, then increase it gradually.

### 6. Troubleshooting

- Red status light: verify Bluetooth permission, pairing, and the selected terminal.
- Text appears in the wrong place: focus the desktop input field before starting output.
- Chinese characters are garbled: try another character-output mode.
- Characters are missing: reduce output speed and keep the target application responsive.

## 简体中文

### 1. 选择版本

- 只需要直接文字输入、不使用模型：选择 [`lite-input`](https://github.com/halala1989/carekey-suite/tree/lite-input)。
- 需要患者管理和临床文书：选择 [`medical-record-app-v4`](https://github.com/halala1989/carekey-suite/tree/medical-record-app-v4)。

### 2. 环境要求

- Android 9 或更高版本，并且手机支持蓝牙 HID
- 带蓝牙的 Windows 电脑
- 可选：受支持的 ESP32-S3 桥接板，用于 USB HID 输出
- 从源码构建时需要 JDK 17 和 Android SDK 34

### 3. 构建 Android 应用

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

APK 输出位置：`android/app/build/outputs/apk/debug/app-debug.apk`。

构建 Lite 版时，先切换到 `lite-input` 分支。

### 4. 连接目标电脑

1. 安装并打开应用。
2. 点击连接状态灯或进入连接配置。
3. 把手机与目标电脑配对，或者选择受支持的桥接板。
4. 等待连接状态灯变为绿色。
5. 把电脑光标放到目标输入框，然后在手机端开始输出。

目标电脑不需要安装驱动或配套程序。手机作为 HID 键盘使用时，系统可能要求确认配对码。

### 5. 中文输出

根据电脑软件选择输出方式：

- **Alt+X**：适用于记事本、Word 等许多 RichEdit 软件。
- **十六进制**：可能需要 Windows 的 `EnableHexNumpad` 注册表设置，并开启 NumLock。
- **十进制**：适用于部分旧式 RichEdit 编辑器。
- **GBK**：主要用于中文版 Windows 环境。

如果出现丢字，先降低输出速度，再逐步提高。

### 6. 常见问题

- 状态灯为红色：检查蓝牙权限、配对状态和当前选择的终端。
- 文字输入到错误位置：开始输出前先点击目标电脑的输入框。
- 中文乱码：尝试其他中文输出方式。
- 出现丢字：降低输出速度，并确认目标软件没有卡顿。
