# BLE HID 虚拟键盘

通过 Raspberry Pi Pico W 实现的 USB HID 虚拟键盘，使用蓝牙低功耗（BLE）从安卓手机接收输入，在目标电脑上模拟键盘操作。

## 系统架构

```
[安卓手机 App] --BLE--> [Pico W] --USB HID--> [Windows 电脑]
```

## 功能特性

- **USB HID 键盘**：即插即用，无需在目标电脑安装任何软件
- **BLE 无线连接**：通过蓝牙低功耗与安卓手机连接
- **中文输入支持**：使用 Alt+Numpad Unicode 方法输入任意中文字符
- **完整键盘功能**：支持所有标准按键、组合键、光标控制
- **安全可靠**：不监测屏幕、不安装软件、纯硬件方案

## 项目结构

```
├── pico/                    # Pico W MicroPython 固件
│   ├── boot.py             # 启动配置
│   ├── main.py             # 主程序
│   └── lib/
│       ├── ble_server.py   # BLE GATT 服务器
│       ├── hid_keyboard.py # USB HID 键盘驱动
│       └── protocol.py     # 通信协议解析
├── android/                 # Android 客户端应用
│   └── app/
│       └── src/main/
│           ├── java/com/hidble/keyboard/
│           │   ├── MainActivity.kt    # 主界面
│           │   ├── BleManager.kt      # BLE 管理器
│           │   └── HidProtocol.kt     # 协议封装
│           └── res/
│               ├── layout/            # 布局文件
│               └── values/            # 资源文件
└── docs/
    ├── PROTOCOL.md         # BLE 通信协议
    └── SETUP.md            # 详细设置指南
```

## 快速开始

### 1. 准备 Pico W

1. 下载 MicroPython 固件：https://micropython.org/download/RPI_PICO_W/
2. 按住 BOOTSEL 按钮，插入 USB，将 `.uf2` 文件拖入 RPI-RP2 驱动器
3. 等待驱动器自动弹出（不要提前拔线！）
4. 将 `pico/` 目录下的所有文件复制到 Pico W

### 2. 安装 Android 应用

1. 使用 Android Studio 打开 `android/` 目录
2. 编译并安装到安卓手机
3. 或直接使用预编译的 APK（如有提供）

### 3. 使用方法

1. 将 Pico W 通过 USB 连接到电脑
2. 在安卓手机上打开 HID BLE Keyboard 应用
3. 点击"扫描设备"，找到并连接 Pico W
4. 在文本框中输入文字，按发送键
5. 文字将自动输入到电脑上当前活动的窗口中

## BLE 通信协议

详见 [docs/PROTOCOL.md](docs/PROTOCOL.md)

## 硬件要求

- Raspberry Pi Pico W（带 CYW43438 蓝牙芯片）
- Micro-USB 数据线
- 运行 Android 8.0+ 的手机（支持 BLE）

## 注意事项

- 中文字符使用 Alt+Numpad Unicode 方法输入，需要目标电脑支持
- 首次连接可能需要几秒钟配对
- BLE 传输距离约 10 米，取决于环境干扰
- 长文本会分包发送，避免 BLE MTU 限制

## 许可证

MIT License
