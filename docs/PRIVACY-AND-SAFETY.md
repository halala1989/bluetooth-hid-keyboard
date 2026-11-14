# Privacy and Safety · 隐私与安全

[English](#english) | [简体中文](#简体中文)

## English

### Screen and desktop access

CareKey does not capture or read the computer display. It does not install a desktop driver, helper, or remote-control service. The computer receives ordinary keyboard events from a paired Bluetooth HID device or a supported USB HID bridge board.

### Local data

The Android application stores patient records, preferences, writing skills, drafts, and model configuration on the phone. Clinical records are intended to use application-level protected storage; device encryption and operating-system security remain essential.

### Model and network use

The Lite edition does not require an AI model for basic text delivery. In the Clinical edition, AI features are optional. When the user explicitly generates or edits content, the relevant prompt and clinical text may be sent to the configured provider, such as DeepSeek or MiMo. API keys are entered by the user and should never be committed to this repository.

### Clinical boundary

This project is not a medical device and does not provide diagnosis or treatment decisions. Generated text can be incomplete or incorrect. A qualified clinician must review every record before use in care. Institutions must approve the hardware, software, privacy controls, and workflow.

### Reporting and data handling

- Never include patient names, identifiers, clinical images, API keys, or access tokens in an issue, pull request, log, or screenshot.
- Use synthetic examples when reporting a problem.
- Treat a suspected data leak as a security incident and stop using the affected configuration until it is reviewed.

## 简体中文

### 屏幕与电脑访问

医键套件不捕获或读取电脑屏幕，不安装电脑驱动、辅助程序或远程控制服务。电脑接收的是已配对蓝牙 HID 设备或受支持 USB HID 桥接板发送的标准键盘事件。

### 本地数据

Android 应用把患者资料、偏好设置、书写 Skill、草稿和模型配置保存在手机本机。临床记录应使用应用级受保护存储；设备加密与操作系统安全仍然是必要基础。

### 模型与网络

Lite 版的基础文字输出不需要 AI 模型。临床版的 AI 功能是可选项。用户主动生成或修改文字时，相关提示词和临床文本可能发送到用户配置的 DeepSeek、MiMo 等服务商。API Key 由用户输入，绝不应提交到本仓库。

### 临床边界

本项目不是医疗器械，不提供诊断或治疗决策。模型生成的文字可能不完整或不正确。任何病历在进入临床使用前，都必须由有资质的医生逐项审阅。机构必须批准所用硬件、软件、隐私控制和工作流程。

### 问题报告与数据处理

- 不要在 Issue、Pull Request、日志或截图中包含患者姓名、身份标识、临床图片、API Key 或访问令牌。
- 报告问题时使用合成示例。
- 怀疑数据泄露时，应按安全事件处理，并在完成评估前停止使用相关配置。
