# BLE HID 虚拟键盘通信协议

## 概述

本协议定义了安卓手机与 Pico W 之间的蓝牙低功耗（BLE）通信格式。所有消息均为 UTF-8 编码的文本字符串。

## BLE 服务配置

### 服务 UUID
```
12345678-1234-5678-1234-56789abcdef0
```

### 特征值 UUID
| 特征值 | UUID | 属性 | 说明 |
|--------|------|------|------|
| 命令写入 | `12345678-1234-5678-1234-56789abcdef1` | Write | 手机 → Pico，发送命令 |
| 状态通知 | `12345678-1234-5678-1234-56789abcdef2` | Notify | Pico → 手机，返回状态 |

## 消息格式

所有消息以换行符 `\n` 结束，格式为：
```
<命令类型>:<参数>
```

## 命令类型

### 1. TEXT - 输入文本

发送文本字符串，逐字符输入。

```
TEXT:你好世界123
TEXT:Hello World
TEXT:Hello 你好
```

**特殊字符处理：**
- ASCII 字符（0x20-0x7E）：直接发送键盘码
- 中文/Unicode 字符：使用 Alt+Numpad 方法输入
- 换行符 `\n`：转换为 Enter 键

### 2. KEY - 按键操作

发送单个特殊按键。

```
KEY:ENTER          # 回车键
KEY:BACKSPACE      # 退格键
KEY:DELETE          # 删除键
KEY:TAB            # 制表键
KEY:ESCAPE         # Escape 键
KEY:CAPSLOCK       # 大写锁定键
KEY:NUMLOCK        # 数字锁定键
KEY:SCROLLLOCK     # 滚动锁定键

# 功能键
KEY:F1
KEY:F2
KEY:F3
KEY:F4
KEY:F5
KEY:F6
KEY:F7
KEY:F8
KEY:F9
KEY:F10
KEY:F11
KEY:F12

# 光标控制键
KEY:UP             # 上箭头
KEY:DOWN           # 下箭头
KEY:LEFT           # 左箭头
KEY:RIGHT          # 右箭头
KEY:HOME           # Home 键
KEY:END            # End 键
KEY:PAGEUP         # Page Up 键
KEY:PAGEDOWN       # Page Down 键

# 导航键
KEY:INSERT         # Insert 键
KEY:PRINTSCREEN    # Print Screen 键
KEY:PAUSE          # Pause/Break 键
KEY:MENU           # Application/Menu 键
```

### 3. MOD - 组合键

发送修饰键+普通键的组合。

```
MOD:CTRL+C         # 复制
MOD:CTRL+V         # 粘贴
MOD:CTRL+X         # 剪切
MOD:CTRL+A         # 全选
MOD:CTRL+Z         # 撤销
MOD:CTRL+S         # 保存
MOD:CTRL+Y         # 重做

MOD:ALT+TAB        # 切换窗口
MOD:ALT+F4         # 关闭窗口
MOD:ALT+SPACE      # 打开窗口菜单

MOD:SHIFT+LEFT     # Shift+左箭头（选中文本）
MOD:CTRL+SHIFT+LEFT  # Ctrl+Shift+左箭头（选中单词）
```

**修饰键支持：**
- `CTRL` - Control 键
- `ALT` - Alt 键
- `SHIFT` - Shift 键
- `GUI` - Windows/Command 键

**组合格式：**
- `MOD:修饰键+按键` - 单个修饰键
- `MOD:修饰键1+修饰键2+按键` - 多个修饰键

### 4. UNI - Unicode 字符

直接发送 Unicode 码点（十进制），使用 Alt+Numpad 方法输入。

```
UNI:20013          # 中文"中"（U+4E2D）
UNI:22909          # 中文"你"（U+4F60）
UNI:22905          # 中文"好"（U+597D）
```

**适用场景：**
- 已知 Unicode 码点时直接发送
- 批量输入中文字符
- 特殊符号输入

## 响应格式

Pico W 在收到命令后，会通过 Notify 特征值发送响应：

### 成功响应
```
OK
```

### 错误响应
```
ERR:<错误描述>
```

### 状态响应
```
STATUS:CONNECTED     # BLE 已连接
STATUS:TYPING        # 正在输入
STATUS:READY         # 就绪，可接受新命令
```

## 数据包大小

- BLE MTU 默认 20 字节（可协商更大）
- 单条消息最大长度：256 字节
- 长文本会自动分包处理

## 示例流程

1. **输入中文文本**：
   ```
   手机 → Pico: TEXT:你好世界\n
   Pico → 手机: OK
   ```

2. **复制粘贴操作**：
   ```
   手机 → Pico: MOD:CTRL+A\n
   Pico → 手机: OK
   手机 → Pico: MOD:CTRL+C\n
   Pico → 手机: OK
   手机 → Pico: KEY:RIGHT\n
   Pico → 手机: OK
   手机 → Pico: MOD:CTRL+V\n
   Pico → 手机: OK
   ```

3. **混合输入**：
   ```
   手机 → Pico: TEXT:Hello 你好\n
   Pico → 手机: OK
   ```

## 错误处理

| 错误码 | 说明 | 处理建议 |
|--------|------|----------|
| `INVALID_CMD` | 未知命令类型 | 检查命令格式 |
| `INVALID_KEY` | 无效按键名称 | 参考支持的按键列表 |
| `INVALID_MOD` | 无效修饰键 | 参考支持的修饰键列表 |
| `OVERFLOW` | 消息过长 | 缩短消息长度 |
| `BUSY` | 设备正忙 | 等待 READY 状态后重试 |
