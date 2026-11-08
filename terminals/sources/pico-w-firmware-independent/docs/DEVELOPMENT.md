# 开发指南（DEVELOPMENT）

本文档面向想修改、扩展本固件工程的开发者。阅读前请先看
`docs/ARCHITECTURE.md` 与 `docs/PROTOCOL.md`。

## 1. 代码导航

| 想改什么 | 去哪个文件 |
|---|---|
| 广播名称 / 连接参数 / 安全配置 | `src/main.c` |
| 字符到按键映射、注入节奏 | `src/usb_hid.c` |
| 帧解析、完整性校验 | `src/packet_parser.c` |
| GATT 服务 / 特征 / UUID | `pico_w_keyboard.gatt` |
| BTstack 缓冲与功能裁剪 | `btstack_config.h` |
| USB 描述符 / 报告描述符 | `src/tusb_config.h` |

## 2. 常用修改点

### 2.1 修改广播名

`src/main.c` 中：

```c
static const char * const adv_name = "PICO-W-KEYBOARD";
```

改后重新构建烧录即可。

### 2.2 增加字符支持

当前 `usb_hid.c` 仅支持 ASCII 可打印字符。扩展多字节字符（中文/emoji）需要：

1. 在 `usb_hid.c` 增加 UTF-8 → Unicode 解码；
2. 建立 Unicode 码点 → `{keycode, modifier}` 映射表；
3. 处理 Windows 主机的键盘布局差异（中文输入法下 HID keycode 的语义）。

### 2.3 调整单条消息大小上限

修改 `src/packet_parser.h`（或 `.c`）中的 `TEXT_BUFFER_CAP`（当前 2048）。
注意同时评估 RP2040 内存（264 KB）与 BTstack 缓冲配置。

### 2.4 修改 GATT 特征属性

编辑 `pico_w_keyboard.gatt`，重新构建（编译期自动重新生成头文件）。

## 3. 调试技巧

- **串口日志**：`main.c` 中的 `printf` 默认走 USB CDC 或 UART，
  若 USB 已被 HID 占用，建议用 UART0（GP0/GP1）接 TTL 串口查看；
- **BTstack HCI 日志**：在 `btstack_config.h` 打开 `ENABLE_LOG_INFO` /
  `ENABLE_LOG_DEBUG` 观察 HCI/ATT 流程；
- **USB 验证**：`tud_hid_ready()` 为 false 表示主机未枚举完成，
  可检查 USB 连接与 `tusb_config.h` 描述符。

## 4. 代码规范

- 命名：函数/变量 `snake_case`，宏 `UPPER_SNAKE_CASE`，类型 `PascalCase`；
- 头文件加 include guard（`#ifndef ... #define ... #endif`）；
- 提交信息遵循 Conventional Commits（`feat:` / `fix:` / `docs:` ...）。

## 5. 提交流程

```bash
git add -A
git commit -m "feat: support unicode input"
git push origin main   # 触发 CI 构建
```

CI 通过后在 Actions 页面下载新的 UF2 产物。

## 6. 下一步建议

- [ ] 中文字符支持（Unicode → HID 映射）
- [ ] 链路层 ACK/重传（提升远距离可靠性）
- [ ] Android 端 UI 增强（历史文本、常用短语）
- [ ] 多键盘布局支持（US / DE / FR ...）
