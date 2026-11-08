# 工作进度记录

最后更新：2026-08-26

## 当前状态：可用（独立产品线 phone-keyboard，v1.0）

### v1.2（2026-08-26）修复电脑搜不到

- 现象：打开开关（注册 HID 后）电脑完全搜不到手机——安卓注册成 HID 键盘后会关闭蓝牙“可见性”。
- 修复：注册成功后自动触发 `ACTION_REQUEST_DISCOVERABLE`（系统“对附近设备可见”授权，默认 5 分钟），点允许后电脑即可搜索到“手机蓝牙键盘”。

### v1.1（2026-08-26）底部一键开关

- 底部改为「模拟蓝牙键盘」开关：打开即自动请求开启蓝牙并自动注册成蓝牙键盘，电脑端搜索“手机蓝牙键盘”配对即可。
- 关闭开关 = 停止模拟键盘并断开连接；已配对设备列表保留在开关下方。

### v1.0（2026-08-26）手机直接模拟蓝牙键盘（独立产品线）

- 从主线拆出独立分支 `phone-keyboard`，版本从 **1.0** 开始编号（不与 Pico W 的 v10/v11 混用）。
- **不再需要 Pico W**：改用 Android 9+ 的 `BluetoothHidDevice` API，手机注册成标准蓝牙键盘，电脑蓝牙直接配对打字。
- 应用重命名：包名 `com.hidble.phonekeyboard`（旧版 `com.hidble.keyboard`，可同时安装）、应用名“手机蓝牙键盘”、versionCode 1 / versionName "1.0"。
- 中文输入 4 种模式（Alt+X/十六进制/十进制/GBK）与速度调速逻辑 1:1 移植到手机端（`TypingEngine.kt`）。
- 界面沿袭旧版：输入置顶（含速度/常用语），连接区在底部（启动键盘/断开/已配对设备）。
- 删除 BLE 扫描与固件通信（`BleManager.kt` 移除）；minSdk 28。

### 2026-08-26 新增（v11，Pico W 方案，主线 master）

- **输入速度调速**：新增 `SPEED` 命令（1=最慢 … 10=最快，默认 5），固件按等级整体缩放按键/字符/Alt 输入延迟；Android 输入框旁新增“速度”文本框 + “应用”按钮，可立即生效并自动保存，连接后自动下发。
- **常用语句**：Android 输入区新增“常用语”按钮，支持添加/编辑/删除常用语句（SharedPreferences 持久化），点选插入输入框。
- **界面重排**：文本输入（含发送）移到最顶部；扫描设备/断开连接/发现设备移到最底部。

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
  - **大模型润色**（规划中）：输入文本框连接自建 LLM API，把一段长话自动分段、去口水词、整理成书面语言后再发送。

## 构建产物（最新，本产品线）

- APK：PhoneBluetoothKeyboard-debug.apk（手机蓝牙键盘，无 Pico W；versionCode 3 / versionName "1.2"，minSdk 28）
  - SHA256: 258710EFC61E37B6BAF078FC1FF6C7D7632AB540FF4C02B545EC0F76A87EBAF4
- 固件：本产品线不需要（历史 Pico W UF2 在 master 分支 releases/v10、v11）

## 版本发布（本产品线按 1.0 开始递增，便于回滚）

- `releases/v1.0/`：手机直接模拟蓝牙键盘（独立产品线），git tag `v1.0`
- `releases/v1.1/`：底部一键开关（自动开蓝牙+模拟键盘），git tag `v1.1`
- `releases/v1.2/`：修复电脑搜不到（注册后自动请求可见性），git tag `v1.2`
- Pico W 产品线在 `master` 分支（v10/v11），与本品互不影响
- 约定详见 `releases/README.md`

## 构建方法

- APK：`cd android; .\gradlew.bat assembleDebug`（JAVA_HOME=D:\jdk17\jdk-17.0.20+8, ANDROID_HOME=D:\Android）
- 固件（仅 v10/v11 历史方案）：`& .\build_firmware.ps1`（需 PICO_SDK_PATH=D:\pico\pico-sdk，MinGW/ninja 在 PATH）

## 相关文档

- docs/PROTOCOL.md（历史 BLE 协议；v1.0 手机方案无协议命令，中文模式直接映射为 Alt 码按键序列）
- docs/SETUP.md
