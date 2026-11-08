# Pico W BLE → USB HID 键盘

本项目在 **Raspberry Pi Pico W** 上实现一个标准 USB HID 键盘。Pico W 通过 USB 连接 Windows 10/11 电脑，通过 BLE 连接 Android 手机；手机输入的文本/按键通过 Pico W 转换为 USB HID 报告发送给电脑。

```
Android App  --BLE GATT-->  Pico W  --USB HID Keyboard-->  Windows PC
```

目标电脑端**不需要安装任何程序、驱动或服务**，也不读取/监控屏幕。

## 中文/Unicode 输入方式（重要）

USB HID 键盘标准只能发送按键，不能直接发送 Unicode/汉字，因此固件模拟 Windows 内置的几种 Alt 码输入方式。固件支持 4 种模式，App 里可一键切换（UMOD 命令）：

| 模式 | 命令 | 原理 | 适用 |
|---|---|---|---|
| **Alt+X（默认）** | `UMOD:3` | 输入十六进制码点后按 `Alt+X` 转换 | **Win11 记事本**、写字板、Word、OneNote、Outlook 等 RichEdit 应用；**无需注册表、无需 NumLock** |
| 十六进制 | `UMOD:1` | `Alt + 小键盘+ + 十六进制码` | 浏览器/聊天等大多数应用；需 `EnableHexNumpad` 注册表 + NumLock；**Win11 记事本无效**（小键盘 + 会被记事本拦截） |
| 十进制 | `UMOD:0` | `Alt + 0 + 十进制码点` | RichEdit 应用；无需注册表 |
| GBK | `UMOD:2` | `Alt + 小键盘十进制 GBK 机内码` | 仅中文版 Windows |

> **当前验证结果**：Win11 自带记事本 + Alt+X 模式可以正常输入中文；ChatGPT 等浏览器对话框不支持 Alt+X（非 RichEdit），需在浏览器里切换到“十六进制”模式（需注册表+NumLock），或后续采用微软拼音 `vuc` 方案（见 docs/PROGRESS.md）。

开启十六进制模式所需注册表（可选，只影响模式 1）：

```powershell
reg add "HKCU\Control Panel\Input Method" /v EnableHexNumpad /t REG_SZ /d 1 /f
```

这不是安装程序，只是打开 Windows 自带的 Unicode 十六进制输入功能。修改后需注销/重启一次。

## 目录结构

```
pico_firmware/          # Pico SDK C 固件（推荐，已可编译）
  ├─ CMakeLists.txt
  ├─ main.c             # BLE GATT、命令分发
  ├─ usb_hid.c/.h       # USB HID 键盘与输入状态机（Alt+X/十六进制/十进制/GBK）
  ├─ gbk_table.c/.h     # Unicode→GBK 表（用 tools/gen_gbk_table.py 生成）
  ├─ usb_descriptors.c  # USB HID 描述符
  ├─ tusb_config.h / btstack_config.h / hid_keyboard.gatt
firmware/
  └─ pico_ble_hid_keyboard.uf2  # 预编译固件
android/                # Android Kotlin 客户端（深色 UI，含中文输入模式切换、输入速度调速、常用语句保存）
docs/                   # 协议、设置、工作进度
tools/
  ├─ elf2uf2.py        # ELF→UF2 脚本（512 字节/块，魔术值已校验）
  ├─ gen_gbk_table.py  # 生成 gbk_table.c/.h
  └─ verify_uf2.py      # 验证 UF2 块结构/魔术值
```

> `pico/` 目录是早期 MicroPython 试验代码，仅作历史参考。

## 快速使用

### 1. 刷写 Pico W

1. 按住 Pico W 的 `BOOTSEL`，插入电脑 USB。
2. 出现 `RPI-RP2` 盘符后，把 `firmware/pico_ble_hid_keyboard.uf2` 复制进去。
3. 等待设备自动重启。Windows 会识别为一个 USB HID 键盘。

### 2. 安装 Android App

用 Android Studio 打开 `android/` 连接手机编译安装；或自行生成 debug APK：

```powershell
cd android
.\gradlew.bat assembleDebug
```

APK 输出路径：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

### 3. 连接和输入

1. 把 Pico W 插到目标电脑。
2. 手机打开蓝牙和本应用。
3. 点“扫描设备”，选择 `Pico HID Keyboard`。
4. 在文本框输入汉字/英文/数字，点发送；中文输入模式默认 Alt+X（记事本/Word 等用），浏览器等切“十六进制”。
5. **输入速度**：输入框下方“速度”文本框填 `1-10`（数字越大越快，默认 5），点“应用”立即生效并自动保存；连接设备后会自动应用已保存的速度（需先刷入含 SPEED 命令的固件）。
6. **常用语句**：点“常用语”可添加/编辑/删除常用语句（自动保存），点选一条即可插入到输入框，再点发送。

未连接时 Pico W LED 慢闪，BLE 连接后常亮。

## 从源码编译固件

需要：

- ARM GCC 工具链（`arm-none-eabi-gcc`）
- CMake + Ninja
- Pico SDK，并设置 `PICO_SDK_PATH=D:\pico\pico-sdk`
- 一个主机 C/C++ 编译器用于编译 `pioasm`（例如 MinGW-w64）

Windows PowerShell 例子（也可直接运行 `build_firmware.ps1`）：

```powershell
& .\build_firmware.ps1
```

生成文件位于：

```text
firmware/pico_ble_hid_keyboard.uf2
```

## BLE 协议

详见 [docs/PROTOCOL.md](docs/PROTOCOL.md)。

## 版本与回滚

每次发布按版本号递增归档（当前 v11），方便随时回滚：

- 产物：`releases/vNN/`（APK + UF2 + RELEASE_NOTES.md），最新版另存于仓库根目录。
- 代码：每次发布打同名 git tag（`git tag v10`、`git tag v11` …）。
- 回滚：`git checkout v10` 回到旧代码；或直接用 `releases/v10/` 里的产物装回去。
- 详细约定见 `releases/README.md`。

## License

MIT
