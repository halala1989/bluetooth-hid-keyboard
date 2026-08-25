# BLE 通信协议

## GATT

| 项目 | UUID | 属性 |
|---|---|---|
| Service | `00001234-0000-1000-8000-00805f9b34fb` | Primary Service |
| Command | `00001235-0000-1000-8000-00805f9b34fb` | Write |
| Status | `00001236-0000-1000-8000-00805f9b34fb` | Read / Notify |

Android 客户端会请求 MTU 247 并自动按 MTU 分包。每条命令以 `\n` 结束。

## 命令格式

```text
COMMAND:ARGUMENT
```

### TEXT

输入 UTF-8 文本。

```text
TEXT:Hello World
TEXT:你好，世界
TEXT:abc123 测试
```

- ASCII 字符直接转换为按键。
- 非 ASCII 字符通过 Windows 内置 Unicode 输入：默认 `输入十六进制码后按 Alt+X`（Win11 记事本/写字板/Word 等 RichEdit 应用；无需注册表、无需 NumLock）。
- `\n` 转换为回车键。
- 中文输入模式可用 `UMOD` 切换（见下）。

### KEY

按下并释放一个特殊按键。

```text
KEY:ENTER
KEY:BACKSPACE
KEY:UP
```

支持：

```text
ENTER, ESC, ESCAPE, BACKSPACE, TAB, SPACE,
CAPSLOCK, NUMLOCK, SCROLLLOCK, PRINTSCREEN, PAUSE,
INSERT, HOME, PAGEUP, DELETE, END, PAGEDOWN,
UP, DOWN, LEFT, RIGHT, MENU, APP,
F1...F12,
A...Z, 0...9
```

### MOD

发送组合键，多个修饰键用 `+`，最后一个为普通键。

```text
MOD:CTRL+C
MOD:CTRL+SHIFT+LEFT
MOD:ALT+TAB
MOD:GUI+D
```

支持修饰键：

```text
CTRL, CONTROL, LEFTCTRL, RIGHTCTRL,
SHIFT, LEFTSHIFT, RIGHTSHIFT,
ALT, LEFTALT, RIGHTALT,
GUI, WIN, WINDOWS, LEFTGUI, RIGHTGUI
```

### UNI

直接输入一个 Unicode 码点。参数可以是十进制，也支持 `0x` 十六进制。

```text
UNI:20013       # 中
UNI:0x4E2D      # 中
```

### UMOD

设置 Unicode 输入模式。

```text
UMOD:3        # 输入十六进制码 + Alt+X（默认；Win11 记事本/写字板/Word/OneNote 等 RichEdit 应用，无需注册表）
UMOD:1        # Alt + Numpad+ + 十六进制（需目标机 EnableHexNumpad=1 且 NumLock 开启；Win11 记事本无效——小键盘+会被记事本拦截）
UMOD:0        # Alt + 0 + 十进制 Unicode 码点（RichEdit 应用；无需注册表）
UMOD:2        # Alt + 小键盘十进制 GBK 机内码（仅中文版 Windows）
```

切换模式后对后续 TEXT/UNI 命令生效。

## 响应

状态特征值会发送：

```text
OK
ERR:INVALID_CMD
ERR:INVALID_KEY
ERR:INVALID_MOD
ERR:INVALID_CODEPOINT
ERR:TEXT_TOO_LARGE
ERR:OVERFLOW
STATUS:READY
```

## 长度限制

- 单行命令最大约 1024 字节。
- Android App 已自动处理 BLE 分包。
- 若输入长文本，建议分段发送，等收到上一段 `OK` 后再发下一段。
