# Architecture · 架构说明

[English](#english) | [简体中文](#简体中文)

## English

### Repository model

The suite uses one repository with branch-level product isolation. `main` contains the public landing page and suite documentation. Product branches contain their own Android source, firmware integrations, tests, and release history.

### Input pipeline

```text
Android text input
        |
        v
Characters and key events
        |
        +----> Phone Bluetooth HID ----> Target computer
        |
        +----> BLE bridge protocol ----> ESP32-S3 or supported board ----> USB HID ----> Target computer
```

The target computer sees a standard keyboard. CareKey does not capture the display, inject input through a desktop agent, or require accessibility APIs.

### Android layers

- **UI layer**: patient list, clinical workspace, settings, connection configuration, and lite text entry.
- **Domain layer**: text chunking, character conversion, writing skills, document generation, and output scheduling.
- **Transport layer**: Bluetooth HID, Bluetooth Low Energy bridge protocols, and USB HID firmware paths.
- **Storage layer**: application preferences, encrypted clinical records, model configuration, and recoverable drafts.

### Clinical edition

The clinical branch adds a patient repository, document templates, writing-skill management, optional model calls, and a terminal abstraction. Clinical text must remain reviewable by the clinician before it is sent to the target computer.

### Extension points

New output terminals should implement the shared connection and typing contracts so that the UI can keep one status indicator and one output settings screen. New document types should remain separate from transport code and should declare their prompt and skill requirements explicitly.

## 简体中文

### 仓库模型

套件采用“单仓库、分支级产品隔离”的结构。`main` 保存公开入口和套件说明；各产品分支保存自己的 Android 源码、固件集成、测试与发布历史。

### 输入链路

```text
Android 文字输入
        |
        v
字符与按键事件
        |
        +----> 手机蓝牙 HID ----> 目标电脑
        |
        +----> BLE 桥接协议 ----> ESP32-S3 或受支持开发板 ----> USB HID ----> 目标电脑
```

目标电脑只看到一个标准键盘。医键套件不捕获屏幕内容，不通过桌面代理注入输入，也不依赖桌面辅助功能接口。

### Android 分层

- **界面层**：患者列表、临床工作台、设置、连接配置和 Lite 文字输入。
- **业务层**：文字分段、字符转换、书写 Skill、文书生成和输出调度。
- **传输层**：蓝牙 HID、BLE 桥接协议和 USB HID 固件路径。
- **存储层**：应用偏好、加密临床记录、模型配置和可恢复草稿。

### 临床版

临床分支增加患者资料库、文书模板、书写 Skill、可选模型调用和终端抽象。任何临床文字在发送到目标电脑前，都应由医生检查确认。

### 扩展点

新增输出终端应实现统一的连接与打字接口，使界面继续使用同一个状态灯和输出设置页面。新增文书类型应与传输代码分离，并明确声明所需提示词和 Skill。
