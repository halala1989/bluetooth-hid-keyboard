# 系统架构

## 1. 总览

Pico W Keyboard Injector 是一条 **Android → Pico W → Windows 主机** 的单向数据链：

```
Android App (BLE GATT Client)
   │  1. 将 UTF-8 文本分包为 START/DATA/END 帧
   │  2. 通过 TX characteristic 逐帧写入（串行，等 ack）
   ▼
Pico W (BTstack BLE Peripheral / GATT Server)
   │  3. att_write_callback 收到一帧 → packet_parser 重组
   │  4. 收到 END 且字节数匹配 → 得到完整 UTF-8 文本
   ▼
USB HID 键盘 (TinyUSB)  →  Windows 主机自动打字
```

关键设计决策：

- **字符转换放在固件侧**：Android 只负责编码和传输 UTF-8 字节，不做 Unicode → HID 映射；
  所有字符到 USB HID keycode 的转换由 `usb_hid.c` 完成。
- **应用层帧协议保证可靠性**：文本必须完整（END + 长度校验）才会注入，
  避免中途断连导致半截文本被"打"进主机。
- **BLE 与 USB 分时复用主循环**：单线程事件循环同时轮询 BTstack（CYW43 异步上下文）
  与 TinyUSB（tud_task）。

## 2. 模块划分

| 模块 | 文件 | 职责 |
|---|---|---|
| 主程序 | `src/main.c` | 初始化（CYW43/BTstack/SM/ATT/TinyUSB/parser）、广播、GATT 写回调、主循环 |
| 帧解析 | `src/packet_parser.c/.h` | 应用层帧协议解析、乱序/重复/长度校验、文本重组 |
| USB HID | `src/usb_hid.c/.h` | TinyUSB HID 键盘设备、ASCII→HID keycode 映射、按键注入 |
| GATT 定义 | `pico_w_keyboard.gatt` | 自定义 BLE GATT 服务/特征，编译期生成 `pico_w_keyboard.h` |
| BTstack 配置 | `btstack_config.h` | BLE-only 裁剪、缓冲区/连接池/流控参数 |
| TinyUSB 配置 | `src/tusb_config.h` | TinyUSB 设备角色、HID 类配置 |
| 构建 | `CMakeLists.txt` | CMake 工程、SDK 引入、GATT 头文件生成、产物输出 |
| CI | `.github/workflows/pico-build.yml` | GitHub Actions 交叉编译，产出 UF2/ELF/HEX |

## 3. 数据流详解

### 3.1 帧写入路径（BLE 侧）

1. Android `BleManager.sendText()` 调用 `BlePacketizer.packetize()` 把 UTF-8 文本切成
   `[START, DATA(0..n-1), END]` 帧序列，串行写入 TX characteristic（`WRITE_TYPE_DEFAULT`）。
2. Pico W 侧 BTstack 触发 `att_write_callback()`，`buffer != NULL` 时代表一帧真实数据。
3. `main.c` 调用 `packet_parser_feed(&parser, buffer, buffer_size, &text_len)`。
4. 返回 `1` 表示收到一条完整消息，随后调用 `usb_hid_inject_text(text_buffer, text_len)` 注入。

### 3.2 注入路径（USB 侧）

1. `usb_hid_inject_text()` 逐字节遍历 UTF-8 文本。
2. 多字节（≥0x80）字符当前被跳过（`usb_hid.c` 中按 UTF-8 首字节长度跳过）。
3. ASCII 字符查 `ascii_map[]` 得到 `{keycode, modifier}`。
4. `send_press_release()` 发送"按下 + 释放"两个 HID report（含 5ms 间隔），
   Windows 主机即收到一次按键。

## 4. 运行时调度

```
main()
 ├─ cyw43_arch_init()             # CYW43 驱动（含 BT）
 ├─ l2cap_init / sm_init          # BTstack 协议栈
 ├─ att_server_init               # 注册 GATT profile 与回调
 ├─ usb_hid_init()                # tusb_init()
 ├─ packet_parser_reset()         # 初始化解析器
 ├─ hci_power_control(ON)         # 启动 BLE
 └─ while(true)
     ├─ async_context_poll()      # BTstack BLE 事件
     └─ usb_hid_poll()            # tud_task() TinyUSB 事件
```

- BLE 收到完整消息时在写回调上下文中**同步**执行注入（期间阻塞主循环，但注入一条
  文本通常只需几十毫秒）。
- USB 按键上报必须等 `tud_hid_ready()` 为真（主机完成枚举）才发送。

## 5. 存储与内存

| 项 | 值 | 说明 |
|---|---|---|
| 文本重组缓冲区 | 2048 B（`TEXT_BUFFER_CAP`） | 单条消息最大 UTF-8 字节数 |
| 帧头 | 5 B（TYPE+MESSAGE_ID+SEQ） | 见 PROTOCOL.md |
| BTstack ACL 载荷 | 255 B | 支持单帧最大 240 B payload |
| MAX_ATT_MTU | 256 | 与 Android `DESIRED_MTU=247` 匹配 |
