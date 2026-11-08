# 快速参考

## 架构

```text
Android App --BLE--> Pico W --USB HID Keyboard--> Windows
```

## 刷固件

1. 按住 BOOTSEL 插入 Pico W。
2. 复制 `firmware/pico_ble_hid_keyboard.uf2` 到 `RPI-RP2`。
3. Windows 识别为 USB HID 键盘。

## Windows 中文输入

默认 **Alt+X 模式**（输入十六进制码后按 Alt+X 转换），Win11 记事本/写字板/Word 等 RichEdit 应用可直接使用，**无需注册表、无需 NumLock**。

浏览器等应用需切换“十六进制”模式（需一次性开启注册表并注销/重启）：

```powershell
reg add "HKCU\Control Panel\Input Method" /v EnableHexNumpad /t REG_SZ /d 1 /f
```

- `UMOD:3` Alt+X（默认，记事本/Word 等 RichEdit）
- `UMOD:1` 十六进制（浏览器等；需注册表+NumLock；Win11 记事本无效）
- `UMOD:0` 十进制（RichEdit；无需注册表）
- `UMOD:2` GBK（仅中文版 Windows）

## 编译

```powershell
$env:PICO_SDK_PATH = "D:\pico\pico-sdk"
$env:Path = "D:\pico\winlibs\mingw64\bin;" + $env:Path
cmake -S .\pico_firmware -B D:\pico_build\pico_hid -G Ninja -DPICO_BOARD=pico_w -DPICO_PLATFORM=rp2040
cmake --build D:\pico_build\pico_hid -j 8
```

## BLE UUID

- Service: `00001234-0000-1000-8000-00805f9b34fb`
- Write: `00001235-0000-1000-8000-00805f9b34fb`
- Notify: `00001236-0000-1000-8000-00805f9b34fb`

## 命令

| 功能 | 示例 |
|---|---|
| 文本 | `TEXT:你好 world123` |
| 按键 | `KEY:ENTER` |
| 组合键 | `MOD:CTRL+C` |
| Unicode | `UNI:20013` |

## 常用按键

`ENTER BACKSPACE TAB ESCAPE DELETE HOME END UP DOWN LEFT RIGHT PAGEUP PAGEDOWN F1-F12`

## LED

- 未连接：慢闪
- 已连接：常亮
