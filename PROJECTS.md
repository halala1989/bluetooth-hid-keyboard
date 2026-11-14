# Product Branches · 产品分支

[English](#english) | [简体中文](#简体中文)

## English

Branches are used as product boundaries. They share the repository and project identity, but each branch can build and release independently. The `main` branch is the documentation-only landing page and should remain the default branch.

| Branch | Product role | AI dependency | Status |
|---|---|---|---|
| `main` | Suite overview, product catalog, privacy, architecture, and contribution guide | None | Current repository entry point |
| `medical-record-app-v4` | **CareKey Clinical**: inpatient management, clinical documentation, skills, model configuration, and multi-terminal output | Optional DeepSeek / MiMo | Active development; current test 33 |
| `lite-input` | **CareKey Lite**: direct text-to-HID input; optional model UI may remain from the engineering lineage | None for core use | Long-term lite edition; basic text delivery works without a model |
| `codex/lite-unified` | Unified Lite engineering lineage with send, pause, stop, and clear controls | Optional; not required for input | Preserved as the source history for `lite-input` |
| `multi-target-6` | Multi-target terminal work, including ESP32-S3 build and flashing profiles | Mixed | Engineering branch |
| `esp32-s3-app` | Earlier ESP32-S3 terminal architecture and roadmap work | Mixed | Engineering archive |
| `plain-bluetooth` | Android Bluetooth HID development lineage with later optional model work | Optional | Historical development branch |
| `phone-keyboard` | Earlier inpatient application and v4 test lineage | Optional | Historical development branch |
| `master` | Original Pico W HID keyboard firmware line (v10/v11) | None | Legacy firmware |

### Promotion rules

1. Keep product-specific source and releases inside their product branch.
2. Update suite-level documentation on `main` when a product name, branch role, or safety boundary changes.
3. Do not merge unrelated product branches into one another.
4. Preserve old branches unless a documented migration and archive copy exist.

## 简体中文

本仓库使用分支作为产品边界。所有分支共享同一个仓库和项目身份，但可以独立构建和发布。`main` 分支是纯文档入口，应保持为默认分支。

| 分支 | 产品职责 | 大模型依赖 | 状态 |
|---|---|---|---|
| `main` | 套件总览、产品目录、隐私、架构和贡献说明 | 无 | 当前仓库入口 |
| `medical-record-app-v4` | **医键临床版**：住院管理、临床文书、Skill、模型配置和多终端输出 | 可选 DeepSeek / MiMo | 活跃开发；当前 Test 33 |
| `lite-input` | **医键 Lite**：直接文字 HID 输入；工程线中可能保留可选模型界面 | 核心功能无 | 长期 Lite 版；基础文字输出不需要模型 |
| `codex/lite-unified` | 统一 Lite 工程线，包含发送、暂停、停止和清空控制 | 可选；文字输入不要求配置 | 作为 `lite-input` 的来源历史保留 |
| `multi-target-6` | 多目标终端、ESP32-S3 构建和烧录配置 | 混合 | 工程分支 |
| `esp32-s3-app` | 早期 ESP32-S3 终端架构与路线图 | 混合 | 工程存档 |
| `plain-bluetooth` | Android 蓝牙 HID 开发线，后期加入过可选模型功能 | 可选 | 历史开发分支 |
| `phone-keyboard` | 早期住院应用和 v4 测试产品线 | 可选 | 历史开发分支 |
| `master` | 最早的 Pico W HID 键盘固件（v10/v11） | 无 | 历史固件 |

### 维护规则

1. 产品代码和发布产物保留在对应产品分支。
2. 产品名称、分支职责或安全边界改变时，同步更新 `main` 上的套件说明。
3. 不把不相关的产品分支相互合并。
4. 除非已有迁移和存档，否则保留历史分支。
