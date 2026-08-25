# BLE HID 虚拟键盘 - 快速参考

## 系统架构
```
[安卓手机] --BLE--> [Pico W] --USB HID--> [Windows 电脑]
```

## 快速开始

### 1. 刷写 Pico W
1. 按住 BOOTSEL，插入 USB
2. 拖拽 MicroPython `.uf2` 到 RPI-RP2
3. 等待自动弹出
4. 复制 `pico/` 下所有文件到 Pico

### 2. 编译 Android 应用
```bash
cd android/
./gradlew assembleDebug
```
或使用 Android Studio 打开项目并编译

### 3. 使用
1. Pico W 连接电脑 USB
2. 手机打开蓝牙和应用
3. 扫描并连接 Pico HID Keyboard
4. 输入文字并发送

## BLE 命令格式

| 命令 | 格式 | 示例 |
|------|------|------|
| 输入文本 | `TEXT:<text>` | `TEXT:你好世界` |
| 按键 | `KEY:<key>` | `KEY:ENTER` |
| 组合键 | `MOD:<mod>+<key>` | `MOD:CTRL+C` |
| Unicode | `UNI:<codepoint>` | `UNI:20013` |

## 常用按键

| 按键 | 命令 | 说明 |
|------|------|------|
| 回车 | `KEY:ENTER` | 回车键 |
| 退格 | `KEY:BACKSPACE` | 退格键 |
| 删除 | `KEY:DELETE` | 删除键 |
| 制表 | `KEY:TAB` | Tab 键 |
| 上/下/左/右 | `KEY:UP/DOWN/LEFT/RIGHT` | 方向键 |
| Home/End | `KEY:HOME/END` | 行首/行尾 |
| PageUp/Down | `KEY:PAGEUP/PAGEDOWN` | 翻页 |

## 常用组合键

| 组合键 | 命令 | 说明 |
|--------|------|------|
| 复制 | `MOD:CTRL+C` | 复制 |
| 粘贴 | `MOD:CTRL+V` | 粘贴 |
| 剪切 | `MOD:CTRL+X` | 剪切 |
| 全选 | `MOD:CTRL+A` | 全选 |
| 撤销 | `MOD:CTRL+Z` | 撤销 |
| 保存 | `MOD:CTRL+S` | 保存 |
| 切换窗口 | `MOD:ALT+TAB` | 切换窗口 |

## 中文输入

中文字符使用 **Alt+Numpad Unicode** 方法：
1. 按住 Left Alt
2. 输入 Unicode 码点的十进制数（小键盘）
3. 释放 Alt

示例：
- "中" = U+4E2D = 20013 → `UNI:20013`
- "你" = U+4F60 = 20320 → `UNI:20320`
- "好" = U+597D = 22909 → `UNI:22909`

## 文件结构

```
├── pico/                    # Pico W 固件
│   ├── boot.py             # 启动配置
│   ├── main.py             # 主程序
│   └── lib/
│       ├── ble_server.py   # BLE 服务器
│       ├── hid_keyboard.py # HID 键盘驱动
│       └── protocol.py     # 协议解析
├── android/                 # Android 应用
│   └── app/src/main/
│       ├── java/.../       # Kotlin 源码
│       └── res/            # 资源文件
└── docs/                    # 文档
    ├── PROTOCOL.md         # 协议规范
    └── SETUP.md            # 设置指南
```

## 故障排除

| 问题 | 解决方案 |
|------|----------|
| 扫描不到设备 | 检查 Pico 是否启动，手机蓝牙是否开启 |
| 连接后断开 | 缩短距离，避免障碍物 |
| 中文无法输入 | 确认 NumLock 开启，电脑支持 Alt+Numpad |
| 输入速度慢 | BLE 延迟正常，可优化参数 |

## 链接

- MicroPython: https://micropython.org/download/RPI_PICO_W/
- Android Studio: https://developer.android.com/studio
- USB HID 规范: https://usb.org/document-library/hid-usage-tables-14

---
版本: 1.0
更新: 2026-08-25
