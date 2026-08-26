# 手机蓝牙键盘（Android → 蓝牙 HID → 电脑）

> 📦 **GitHub 仓库**：https://github.com/halala1989/bluetooth-hid-keyboard
> 🌿 **两条产品线**：`phone-keyboard`（默认分支，手机蓝牙键盘 v1.x）｜ `master`（Pico W 方案 v10/v11）
> 🔁 **换电脑迁移**：`git clone https://github.com/halala1989/bluetooth-hid-keyboard.git` 后见 `docs/MIGRATION.md`；
> 📖 开发历史/对话记录整理见 `docs/HISTORY.md`

**独立产品线 v1.2**（分支 `phone-keyboard`）：手机直接把自己注册成标准蓝牙键盘
（Android 9+ 系统的 `BluetoothHidDevice`），电脑蓝牙里添加“手机蓝牙键盘”配对后即可打字，**不依赖 Pico W**。
App 里的中文输入、速度、常用语等体验沿袭旧版。

```
Android App  --Bluetooth HID Keyboard-->  Windows PC
```

目标电脑端**不需要安装任何程序、驱动或服务**，也不读取/监控屏幕。

> 本应用与旧版互不覆盖：包名 `com.hidble.phonekeyboard`、应用名“手机蓝牙键盘”，可和旧 Pico W 版（`com.hidble.keyboard`）同时安装。
> 旧 Pico W 产品线在 `master` 分支（v10/v11），固件源码保留在 `pico_firmware/`，仅作参考。

## 中文/Unicode 输入方式（重要）

蓝牙键盘只能发送标准按键，不能直接发送 Unicode/汉字，因此 App 在手机端模拟 Windows 内置的几种 Alt 码输入方式
（逻辑原样移植自 v11 固件）。支持 4 种模式，App 里可一键切换：

| 模式 | 原理 | 适用 |
|---|---|---|
| **Alt+X（默认）** | 输入十六进制码点后按 `Alt+X` 转换 | **Win11 记事本**、写字板、Word、OneNote、Outlook 等 RichEdit 应用；**无需注册表、无需 NumLock** |
| 十六进制 | `Alt + 小键盘+ + 十六进制码` | 浏览器/聊天等大多数应用；需 `EnableHexNumpad` 注册表 + NumLock；**Win11 记事本无效**（小键盘 + 会被记事本拦截） |
| 十进制 | `Alt + 0 + 十进制码点` | RichEdit 应用；无需注册表 |
| GBK | `Alt + 小键盘十进制 GBK 机内码` | 仅中文版 Windows |

> **验证结果（Pico W 方案时验证）**：Win11 自带记事本 + Alt+X 模式可正常输入中文；ChatGPT 等浏览器对话框不支持 Alt+X（非 RichEdit），需在浏览器里切换“十六进制”模式（需注册表+NumLock）。

开启十六进制模式所需注册表（可选，只影响十六进制模式）：

```powershell
reg add "HKCU\Control Panel\Input Method" /v EnableHexNumpad /t REG_SZ /d 1 /f
```

这不是安装程序，只是打开 Windows 自带的 Unicode 十六进制输入功能。修改后需注销/重启一次。

## 目录结构

```
android/                # Android Kotlin 客户端（本版本主体）
  ├─ MainActivity.kt    # 界面与逻辑（输入/速度/常用语/连接）
  ├─ HidDeviceManager.kt# 手机注册为蓝牙键盘（BluetoothHidDevice）
  ├─ TypingEngine.kt    # 文本/按键/中文 Alt 码输入引擎（1:1 移植自 v11 固件）
  └─ HidProtocol.kt     # 上层封装
pico_firmware/          # 历史 Pico W 固件（v10/v11 方案，已不再需要）
firmware/               # 历史预编译固件 UF2（已不再需要）
releases/               # 版本化发布归档（本产品线 v1.0, v1.1, ...）
docs/                   # 协议、设置、工作进度
tools/                  # 构建/校验脚本
```

## 快速使用

### 1. 安装 Android App

用 Android Studio 打开 `android/` 连接手机编译安装；或直接用 `releases/v1.0/PhoneBluetoothKeyboard-v1.0.apk`（要求 Android 9+）：

```powershell
cd android
.\gradlew.bat assembleDebug
```

APK 输出路径：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

### 2. 连接电脑（一键开关）

1. 打开本 App，点底部「**模拟蓝牙键盘**」开关。
2. 系统弹出“开启蓝牙”授权，点允许；随后弹出“**对附近设备可见**”，也点允许（否则电脑搜不到）。
3. 电脑：设置 → 蓝牙 → 添加设备，搜索“**手机蓝牙键盘**”并配对（手机上确认配对码）。
4. 首次配对后，若电脑未自动连接，可点底部“已配对设备”里的电脑手动连接。关闭开关即可停止。

### 3. 输入

1. 顶部文本框输入汉字/英文/数字，点发送；中文输入模式默认 Alt+X（记事本/Word 等用），浏览器等切“十六进制”或“GBK”。
2. **输入速度**：文本框下方“速度”填 `1-10`（数字越大越快，默认 5），点“应用”立即生效并自动保存。
3. **常用语句**：点“常用语”可添加/编辑/删除常用语句（自动保存），点选一条插入输入框，再点发送。

## 从源码构建

- APK：见上方 `cd android; .\gradlew.bat assembleDebug`。
- Pico W 固件（仅 v10/v11 历史方案需要）：`build_firmware.ps1`，详见 `releases/v10|v11` 发布说明。

## 版本与回滚

本产品线从 **v1.0** 开始递增归档（当前 v1.2）：

- 产物：`releases/vNN/`（APK + 发布说明），最新版另存于仓库根目录 `PhoneBluetoothKeyboard-debug.apk`。
- 代码：每次发布打同名 git tag（`git tag v1.0`、`git tag v1.1`、`git tag v1.2` …）。
- 回滚：`git checkout v1.0` 回到本产品线旧版；切 `git checkout master` 回到 Pico W 产品线（v10/v11）。
- 详细约定见 `releases/README.md`。

## License

MIT
