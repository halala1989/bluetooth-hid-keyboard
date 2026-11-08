# 固件构建指南（BUILD）

本工程使用 **Pico SDK + BTstack + TinyUSB**，支持本地构建与 GitHub Actions 交叉编译。

## 1. 依赖

- Raspberry Pi Pico SDK（`v2.x`，建议 tag `2.1.0` 或更新）
- CMake ≥ 3.13
- ARM 交叉编译工具链：`arm-none-eabi-gcc`（推荐 13.x Rel1）
- （CI 中使用 `carlosperate/arm-none-eabi-gcc-action@v1` 提供 13.3.Rel1）

## 2. 目录结构

```
pico-w-firmware/
├── CMakeLists.txt
├── pico_sdk_import.cmake
├── btstack_config.h
├── pico_w_keyboard.gatt
├── src/
│   ├── main.c
│   ├── packet_parser.c / .h
│   ├── usb_hid.c / .h
│   └── tusb_config.h
├── docs/
└── .github/workflows/pico-build.yml
```

## 3. 本地构建

```bash
export PICO_SDK_PATH=/path/to/pico-sdk
mkdir -p build && cd build
cmake ..
make -j$(nproc)
```

产物（在 `build/` 下）：

| 文件 | 说明 |
|---|---|
| `pico_w_keyboard_firmware.uf2` | 拖入 Pico W 的 BOOTSEL 磁盘即可烧录 |
| `pico_w_keyboard_firmware.elf` | 调试用 ELF |
| `pico_w_keyboard_firmware.hex` | 兼容烧录格式 |

## 4. CI 交叉编译（GitHub Actions）

Workflow：`.github/workflows/pico-build.yml`

- 触发：push / PR 到 `main`；
- 步骤：checkout → 拉取 pico-sdk → 安装 ARM 工具链（gcc-action，13.3.Rel1）
  → `cmake` + `make` → 上传 `*.uf2 / *.elf / *.hex` 为构建产物；
- 最近一次成功 run：`32264952412`。

## 5. GATT 头文件生成

`CMakeLists.txt` 中：

```cmake
pico_btstack_gatt_build(
    TARGET pico_w_keyboard_firmware
    GATT_FILES pico_w_keyboard.gatt
)
```

编译期将 `pico_w_keyboard.gatt` 生成 `pico_w_keyboard.h`（profile 数据 + 回调声明），
`main.c` 通过 `#include "pico_w_keyboard.h"` 使用。

## 6. 常见配置开关（CMakeLists.txt）

| 项 | 值 | 作用 |
|---|---|---|
| `pico_cyw43_arch_none` | 开启 | CYW43 无线（BT 用），不走 lwIP |
| `pico_btstack_cyw43` | 开启 | BTstack over CYW43 |
| `pico_btstack_ble` | 开启 | BLE-only（裁剪 BR/EDR，减小体积） |
| `tinyusb_device` | 开启 | TinyUSB 设备模式（USB HID 键盘） |

## 7. 构建期常见错误与修复记录

| 错误 | 原因 | 修复 |
|---|---|---|
| `pico_enable_usb_reset_interface: 未知命令` | SDK 未初始化/版本不匹配 | 正确 `include(pico_sdk_import.cmake)` 并 `pico_sdk_init()` |
| `usb_hid.c: desc_hid_report 前向引用` | HID report descriptor 使用顺序错误 | 在引用前声明/定义 `desc_hid_report` |
| `SM_IO_CAPABILITY 未定义` | BTstack 枚举名拼写 | 改为 `SSP_IO_CAPABILITY_NO_INPUT_NO_OUTPUT` |
| `btstack_config.h: HCI_ACL_CHUNK_SIZE_ALIGNMENT 等未定义` | BLE-only 配置缺少必需宏 | 补齐 `HCI_ACL_CHUNK_SIZE_ALIGNMENT`、`HCI_OUTGOING_PRE_BUFFER_SIZE`、`NVM_NUM_DEVICE_DB_ENTRIES`、`NVM_NUM_LINK_KEYS` |
