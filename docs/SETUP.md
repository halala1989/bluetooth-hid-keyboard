# 安装与设置

## 1. 硬件准备

- Raspberry Pi Pico W
- 一根可传输数据的 Micro-USB 线
- Android 8.0+ 且支持 BLE 的手机
- Windows 10/11 电脑

## 2. 刷写 Pico W 固件

### 使用预编译 UF2

1. 按住 `BOOTSEL` 并把 Pico W 插入电脑。
2. 资源管理器中出现 `RPI-RP2`。
3. 将 `firmware/pico_ble_hid_keyboard.uf2` 复制到该盘。
4. 复制完成后设备会自动重启。

### 从源码编译

安装：

- arm-none-eabi-gcc
- CMake
- Ninja
- Pico SDK
- MinGW-w64 或其他主机 C/C++ 编译器（用于 pioasm）

PowerShell 示例：

```powershell
$env:PICO_SDK_PATH = "D:\pico\pico-sdk"
$env:Path = "D:\pico\winlibs\mingw64\bin;" + $env:Path

cmake -S .\pico_firmware -B D:\pico_build\pico_hid -G Ninja `
  -DPICO_BOARD=pico_w -DPICO_PLATFORM=rp2040
cmake --build D:\pico_build\pico_hid -j 8
```

建议把构建目录放在不含中文和空格的路径中。

## 3. Windows 中文输入设置

普通 USB HID 键盘不能直接发送 Unicode。本项目使用 Windows 自带的 Unicode 十六进制输入：

```powershell
reg add "HKCU\Control Panel\Input Method" /v EnableHexNumpad /t REG_SZ /d 1 /f
```

执行后注销或重启。若不设置，英文、数字和按键命令仍可使用，但汉字输入不可靠。

建议将 Windows 键盘布局切换为“英语（美国）”。

## 4. Android 客户端

1. 用 Android Studio 打开 `android/`。
2. 等待 Gradle 同步。
3. 连接手机并允许“USB 调试”。
4. 点击 Run。

也可以在配置好 Android SDK 后执行：

```powershell
cd android
.\gradlew assembleDebug
```

APK 在：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

首次扫描/连接时，允许蓝牙和位置权限。Android 12+ 需要“附近设备”权限。

## 5. 使用

1. Pico W 插入目标电脑。
2. 打开 Android App。
3. 点“扫描设备”，连接 `Pico HID Keyboard`。
4. 把光标放在电脑上需要输入的位置。
5. 在手机文本框输入并发送。

## 故障排查

### 电脑不识别键盘

- 检查 USB 线是否支持数据。
- 换 USB 口。
- 在 Windows 设备管理器中确认是否出现“HID 键盘”。

### 扫描不到设备

- 确认 Pico W LED 在慢闪。
- 确认手机蓝牙和位置权限已开启。
- 靠近设备后重试。
- 某些手机需要开启定位才能扫描 BLE。

### 连接后无法输入

- 确认电脑上光标位于可输入区域。
- 确认 Pico W 已通过 USB 连接电脑，而不是只连接充电器。
- 查看 App 日志中是否收到 `OK` 或错误信息。

### 汉字不能输入

- 确认已执行 `EnableHexNumpad` 并重启/注销。
- 尝试在记事本中测试。
- 部分应用/远程桌面可能拦截 Alt 组合键，属于目标应用限制。

### 字母大小写错误

- 输入过程中不要按 CapsLock。
- 固件会根据主机回报的 CapsLock LED 状态自动修正字母。
