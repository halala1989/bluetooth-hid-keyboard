# BLE HID 虚拟键盘 - 详细设置指南

## 目录

1. [硬件准备](#1-硬件准备)
2. [Pico W 固件安装](#2-pico-w-固件安装)
3. [Android 应用编译](#3-android-应用编译)
4. [首次使用](#4-首次使用)
5. [故障排除](#5-故障排除)

---

## 1. 硬件准备

### 所需材料

- **Raspberry Pi Pico W**（带 CYW43438 蓝牙芯片）
- **Micro-USB 数据线**（支持数据传输，非充电线）
- **Windows 10/11 电脑**
- **Android 8.0+ 手机**（支持 BLE 4.0）

### 检查 Pico W

确认你的 Pico W 是**标准版本**：
- 丝印应显示 "Raspberry Pi Pico W"
- 带有 CYW43438 蓝牙芯片（金属屏蔽罩）
- **不是** ESP8285 版本的克隆板

---

## 2. Pico W 固件安装

### 步骤 1：安装 MicroPython

1. 下载 MicroPython 固件：
   - 访问 https://micropython.org/download/RPI_PICO_W/
   - 下载最新的 `.uf2` 文件（例如 `RPPI_PICO_W-2024xxxx.uf2`）

2. 进入 BOOTSEL 模式：
   - **按住** Pico W 上的 BOOTSEL 按钮
   - **同时** 插入 USB 线连接到电脑
   - 保持按住直到电脑识别到 RPI-RP2 驱动器

3. 刷新固件：
   - 将下载的 `.uf2` 文件**拖拽**到 RPI-RP2 驱动器
   - **等待**驱动器自动弹出/消失（**不要提前拔线！**）
   - 如果驱动器没有自动弹出，说明文件复制被中断，需要重新操作

4. 验证安装：
   - 打开设备管理器，应该看到新的 COM 端口（COMx）
   - 使用串口工具（如 PuTTY）连接，波特率 115200
   - 按回车，应该看到 `>>>` 提示符
   - 输入 `import sys; print(sys.version)` 验证 MicroPython 版本

### 步骤 2：复制固件文件

使用 Thonny IDE 或其他串口工具将以下文件复制到 Pico W：

```
pico/
├── boot.py
├── main.py
└── lib/
    ├── ble_server.py
    ├── hid_keyboard.py
    └── protocol.py
```

**使用 Thonny 的方法：**
1. 安装 Thonny IDE：https://thonny.org/
2. 连接 Pico W，选择正确的端口
3. 打开 `boot.py`，选择"另存为" → "Raspberry Pi Pico"
4. 重复上述步骤复制所有文件

**使用命令行的方法：**
```bash
# 使用 ampy 工具
pip install adafruit-ampy

# 复制文件
ampy --port COMx put pico/boot.py boot.py
ampy --port COMx put pico/main.py main.py
ampy --port COMx mkdir lib
ampy --port COMx put pico/lib/ble_server.py lib/ble_server.py
ampy --port COMx put pico/lib/hid_keyboard.py lib/hid_keyboard.py
ampy --port COMx put pico/lib/protocol.py lib/protocol.py
```

### 步骤 3：重启 Pico W

1. 拔掉 USB 线
2. 重新插入 USB 线
3. LED 应该闪烁 3 次，表示固件启动成功
4. 在串口终端中应该看到：
   ```
   === BLE HID Keyboard ===
   Initializing...
   USB HID keyboard ready
   Protocol parser ready
   BLE server ready
   BLE advertising...
   === Ready ===
   Waiting for BLE connection...
   ```

---

## 3. Android 应用编译

### 方法 1：使用 Android Studio

1. 安装 Android Studio：
   - 访问 https://developer.android.com/studio
   - 下载并安装最新版本

2. 打开项目：
   - 启动 Android Studio
   - 选择 "Open an Existing Project"
   - 导航到 `android/` 目录并打开

3. 配置 SDK：
   - 等待 Gradle 同步完成
   - 如果提示 SDK 版本问题，按照提示安装所需 SDK

4. 编译 APK：
   - 点击菜单 "Build" → "Build Bundle(s) / APK(s)" → "Build APK(s)"
   - 等待编译完成
   - APK 文件位于 `android/app/build/outputs/apk/debug/app-debug.apk`

5. 安装到手机：
   - 将 APK 文件传输到 Android 手机
   - 在手机上打开 APK 文件进行安装
   - 如果提示"未知来源"，需要在设置中允许安装

### 方法 2：使用命令行

```bash
cd android/
./gradlew assembleDebug
```

APK 位于：`app/build/outputs/apk/debug/app-debug.apk`

---

## 4. 首次使用

### 步骤 1：连接 Pico W

1. 使用 Micro-USB 线将 Pico W 连接到电脑
2. 电脑会自动识别为 USB HID 键盘设备
3. **不需要**安装任何驱动程序

### 步骤 2：连接 Android 手机

1. 打开 Android 手机的蓝牙设置
2. 确保蓝牙已开启
3. 打开 "HID BLE 键盘" 应用
4. 点击 "扫描设备" 按钮
5. 在弹出的对话框中选择 "Pico HID Keyboard"
6. 等待连接成功，状态应显示"已连接"

### 步骤 3：输入文本

1. 在电脑上打开任意文本编辑器（记事本、Word 等）
2. 在手机应用的文本框中输入文字
3. 点击 "发送" 按钮
4. 文字将自动输入到电脑上的活动窗口中

### 使用光标控制

- 使用方向键按钮移动光标
- 使用 Home/End 按钮快速跳到行首/行尾
- 使用组合键按钮进行复制/粘贴等操作

---

## 5. 故障排除

### Pico W 相关问题

**问题：LED 不亮**
- 检查 USB 线是否支持数据传输
- 尝试更换 USB 端口
- 重新刷写固件

**问题：无法看到 COM 端口**
- 确认已正确安装 MicroPython 固件
- 检查设备管理器中是否有未知设备
- 尝试不同的 USB 线

**问题：串口终端无响应**
- 按回车键激活终端
- 检查波特率是否为 115200
- 尝试按 Ctrl+C 中断当前程序

### BLE 连接问题

**问题：手机扫描不到设备**
- 确认 Pico W 已启动并正在广播
- 检查手机蓝牙是否开启
- 确认手机支持 BLE 4.0+
- 尝试重启 Pico W 和手机蓝牙

**问题：连接后很快断开**
- 检查 Pico W 和手机的距离（应在 10 米以内）
- 避免障碍物干扰
- 检查是否有其他 BLE 设备干扰

**问题：发送命令无响应**
- 确认连接状态显示"已连接"
- 查看命令日志是否有错误信息
- 尝试断开并重新连接

### USB HID 问题

**问题：电脑无法识别为键盘**
- 确认使用的是标准 Pico W
- 检查 MicroPython 固件版本
- 尝试重新刷写固件

**问题：中文字符无法输入**
- 确认目标电脑支持 Alt+Numpad Unicode 输入
- 检查 NumLock 是否开启
- 尝试使用英文输入测试

**问题：输入速度慢**
- BLE 传输有延迟，这是正常现象
- 长文本会分批发送
- 可以通过优化 BLE 参数提高速度

---

## 高级配置

### 修改 BLE 广播名称

编辑 `pico/lib/ble_server.py`，修改以下行：

```python
self.ble.config(name="Your Custom Name")
```

### 修改服务 UUID

如果需要使用自定义 UUID，需要同时修改：
1. `pico/lib/ble_server.py` 中的 UUID
2. `android/app/src/main/java/com/hidble/keyboard/BleManager.kt` 中的 UUID

### 调整输入延迟

编辑 `pico/lib/hid_keyboard.py`，修改 `time.sleep_ms()` 的值：
- 增加延迟可以提高稳定性
- 减少延迟可以提高输入速度

---

## 技术支持

如果遇到问题，请检查：

1. 是否使用了正确的 Pico W 型号
2. MicroPython 固件是否正确安装
3. Android 手机是否支持 BLE
4. BLE 权限是否已授予应用

更多信息请参考项目 README.md 和 PROTOCOL.md。
