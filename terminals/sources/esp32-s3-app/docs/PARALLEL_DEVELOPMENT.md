# CONTInue · 三版本并行开发方案（蓝牙直连 / ESP32 / Pico）

> 目标（用户 2026-09-12 明确）：**三个版本并行开发；任何通用改动一次修改，三端同时生效。**
> 本文档说明如何做到这一点，以及从当前"多分支复制"状态迁移过来的步骤。

---

## 1. 最终要维护的三个目标

| 目标 | App 包名 | App 名称 | 数据链路 |
|---|---|---|---|
| `bt`（普通蓝牙） | `com.hidble.phonekeyboard` | 手机蓝牙键盘 | 手机自己当 BLE HID 键盘 → 电脑 |
| `esp32` | `com.hidble.esp32keyboard` | ESP32 蓝牙键盘 | 手机 --BLE--> ESP32 板子 --USB HID--> 电脑 |
| `pico` | `com.hidble.picokeyboard` | Pico 蓝牙键盘 | 手机 --BLE--> Pico 板子 --BLE HID--> 电脑 |

三个 App 可同时安装（包名不同）。

---

## 2. 核心原则：一份 Android 代码 + 三个 Gradle flavor

### 2.1 目录/构建结构

```
android/app/
  src/main/                 ← 全部共享代码（改一次，三个版本同时生效）
    java/.../MainActivity.kt, LlmClient.kt, LlmLiterature.kt, 气泡界面…
    java/.../transport/     ← 只有"输出通道"抽象
        OutputTransport.kt      （接口：sendText / key / speed / unicodeMode …）
        PhoneHidTransport.kt    （bt 用：现有 HidDeviceManager + TypingEngine）
        BoardTransport.kt       （esp32/pico 共用：现有 BoardBleManager，协议 1234/1235/1236）
  build.gradle              ← product flavors 定义包名/应用名/默认参数
```

`build.gradle` 关键配置（示意）：

```gradle
android {
    flavorDimensions "target"
    productFlavors {
        bt {
            dimension "target"
            applicationId "com.hidble.phonekeyboard"
            resValue "string", "app_name", "手机蓝牙键盘"
            buildConfigField "String", "TARGET_MODE", '"bt"'
        }
        esp32 {
            dimension "target"
            applicationId "com.hidble.esp32keyboard"
            resValue "string", "app_name", "ESP32 蓝牙键盘"
            buildConfigField "String", "TARGET_MODE", '"board"'
            buildConfigField "String", "BOARD_NAME_HINT", '"ESP32"'
        }
        pico {
            dimension "target"
            applicationId "com.hidble.picokeyboard"
            resValue "string", "app_name", "Pico 蓝牙键盘"
            buildConfigField "String", "TARGET_MODE", '"board"'
            buildConfigField "String", "BOARD_NAME_HINT", '"Pico"'
        }
    }
}
buildFeatures { buildConfig true }
```

- `TARGET_MODE == "bt"`：显示"手机模拟蓝牙键盘"卡片，输出走 `PhoneHidTransport`
- `TARGET_MODE == "board"`：显示"外接键盘板"卡片，输出走 `BoardTransport`
- 扫描时用 `BOARD_NAME_HINT`（ESP32 版找含 "ESP32" 的板子；Pico 版找含 "Pico" 的板子）

### 2.2 共享范围（这些以后只改一份）

- 大模型对话（`LlmClient` / 流式 / 停止输出 / 后台保活）
- 提示词预设、历史对话、文献检索、设置页
- 聊天气泡界面、长文本按行拆分/字节切块
- 版本规则：**每个 flavor 各自 versionCode + APK 文件名带版本号**
  （例：`Esp32BluetoothKeyboard-v28.apk`、`PicoBluetoothKeyboard-v1.apk`、`PhoneBluetoothKeyboard-v22.apk`）

### 2.3 只分叉两小块

1. **包名/应用名/默认设备名**：Gradle flavor 配置
2. **输出通道**：`OutputTransport` 的两个实现
   - `PhoneHidTransport`：现 `HidDeviceManager` + `TypingEngine`（bt）
   - `BoardTransport`：现 `BoardBleManager`（esp32 与 pico **完全共用**，二者协议一致）

---

## 3. 固件端共享方案

ESP32 与 Pico 是两套不同的芯片/框架，无法共享全部 C 代码，但可以共享核心逻辑：

```
shared_firmware/
  protocol.h        ← 命令/协议常量（TEXT/KEY/MOD/UNI/UMOD/SPEED，UUID 1234/1235/1236）
  typing_engine.c/h ← 打字逻辑（ASCII→HID、Alt+X 中文、速度档缩放）
  gbk_table.c/h     ← GBK 码表（约 540KB，两平台共用）
```

- ESP32 侧：`esp32_bridge_firmware/`（NimBLE 收数据 + TinyUSB 输出 USB HID）
- Pico 侧：`pico_firmware/`（BTstack，Pico 自己作为 BLE HID 键盘）
- 两边都 `#include "../shared_firmware/..."`，改协议/打字逻辑只改一处

---

## 4. 从当前状态迁移的步骤（建议顺序）

1. **建一个统一分支**：`multi-target`（从 `esp32-s3-app` 拉出，因为它最新、功能最全）
2. **合并 bt 的界面代码**：把 `phone-keyboard` 分支里"手机模拟蓝牙键盘 + 已配对设备"那张卡片
   合并回来，用 `TARGET_MODE` 控制显示
3. **抽 `OutputTransport` 接口**，把现有两条发送路径分别包成两个实现
4. **加 Gradle flavors**（第 2.1 节），确保 `assembleBtDebug` / `assembleEsp32Debug` / `assemblePicoDebug` 都能出包
5. **抽 `shared_firmware/`**：把 `pico_firmware/usb_hid.c` 的打字逻辑与 `gbk_table.c` 提取成共享模块，
   ESP32 桥接固件改为引用
6. 三个 APK + 两个固件都编译通过后，把 `multi-target` 设为新主线；旧分支保留归档

> 迁移完成前，`esp32-s3-app` / `phone-keyboard` / `plain-bluetooth` 保持现状可用。

---

## 5. 今后改动的规矩（避免再分叉）

- **通用功能**（对话、提示词、文献、界面…）→ 只改 `src/main`，一次生效三端
- **新增差异** → 只加在 flavor 配置 / `OutputTransport` 实现 / 各自固件目录里
- **协议改动**（命令格式、UUID）→ 先改 `shared_firmware/protocol.h`，App 侧同步改 `BoardTransport`
- **每次发布**：三个 flavor 都要编译；APK 文件名带各自版本号；更新本文件与 `docs/CONTINUE_ESP32.md`

---

## 6. 相关文档

- `docs/CONTINUE_ESP32.md`：ESP32 桥接固件 + App 详细交接（硬件坑、构建烧录、排查）
- `docs/CONTINUE_DEV.md`：手机自己当蓝牙键盘方案（旧主线）
- `docs/BRANCHES.md`：分支/包名/APK 命名约定
- `esp32_bridge_firmware/README.md`、`esp32_usb_hid_test/README.md`、`pico_firmware/`（Pico 固件）