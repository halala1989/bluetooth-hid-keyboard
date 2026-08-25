# 工作进度记录

最后更新：2026-08-25

## 当前状态：可用

- Pico W 固件（Pico SDK C）+ BLE GATT + USB HID 键盘：可编译、可烧录（UF2 512B/块校验通过）。
- Android 客户端：深色 UI、扫描/连接/输入、中文输入模式切换，可编译出 APK。
- **Win11 自带记事本输入中文已可用**：默认 Alt+X 模式（输入十六进制码后按 Alt+X 转换），无需注册表、无需 NumLock。
- 目标机不安装任何程序、不监控屏幕。

## 中文输入 4 种模式（UMOD）

| 模式 | 命令 | 说明 |
|---|---|---|
| Alt+X（默认） | UMOD:3 | 十六进制码 + Alt+X；RichEdit 应用（Win11 记事本/写字板/Word/OneNote/Outlook）；无需注册表/NumLock |
| 十六进制 | UMOD:1 | Alt+小键盘++十六进制；需 EnableHexNumpad 注册表 + NumLock；Win11 记事本无效（小键盘+被记事本拦截） |
| 十进制 | UMOD:0 | Alt+0+十进制码点；RichEdit 应用 |
| GBK | UMOD:2 | Alt+小键盘十进制 GBK 机内码；仅中文版 Windows |

## 已验证结论 / 踩坑记录

1. Alt 码输入必须用**小键盘键位**（HID KP_0..KP_9）；主键盘数字/字母会被当作 Alt 快捷键 → 弹菜单。已修复。
2. 十六进制模式的字母 A-F 用主键盘字母键（不带 Shift）。
3. Win11 自带记事本会拦截 `Alt + 小键盘 +`（弹出链接编辑器），所以十六进制模式在记事本无效；记事本支持 Alt+X。
4. 自动开关 NumLock 的逻辑有副作用（会把 NumLock 关掉、导致输入翻倍），已移除；现在固件不干预 NumLock。
5. 目标机若是英文版 Windows，GBK 机内码无效；十进制仅在 RichEdit 应用有效。

## 已知问题 / 待办

- [ ] **ChatGPT 等浏览器对话框无法用 Alt+X 输入**（非 RichEdit）。候选方案：
  - 浏览器里切“十六进制”模式（需 EnableHexNumpad+NumLock，需真机验证浏览器是否支持）。
  - 微软拼音 IME 的 `vuc` 方法：输入 `vuc`+十六进制+空格 → 任意支持 IME 的应用（含浏览器）都能输入 Unicode。需目标机装了微软拼音且处于中文模式。
- [ ] **微软拼音输入法下按 Shift 切换中/英文**：增加一个设置/按钮，让固件发一次 Shift 把 IME 切到英文模式（避免拼音候选窗拦截 ASCII/十六进制码）。目前固件不支持发送“单独 Shift”，需新增。
- [ ] **Android 客户端增强**（用户已提，后续开发）：
  - 增加上述 Shift/IME 设置。
  - 更完善的输入体验、模式提示、历史等。

## 构建产物（最新）

- 固件：firmware/pico_ble_hid_keyboard.uf2
  - SHA256: 1F0A29CF088F714B6195C50518AB0F32DF10EC1F1CEBBFE8A61A361724CA5051
  - UF2：2172 块 × 512B，魔术值通过
- APK：PicoBleHidKeyboard-debug.apk
  - SHA256: B6E9A74E289D68FD31BB2190670BFBC77E7A8B4452643D6761020EEA91E6E16B

## 构建方法

- 固件：`& .\build_firmware.ps1`（需 PICO_SDK_PATH=D:\pico\pico-sdk，MinGW/ninja 在 PATH）
- APK：`cd android; .\gradlew.bat assembleDebug`（JAVA_HOME=D:\jdk17\jdk-17.0.20+8, ANDROID_HOME=D:\Android）

## 相关文档

- docs/PROTOCOL.md（BLE 协议与 UMOD 命令）
- docs/SETUP.md
