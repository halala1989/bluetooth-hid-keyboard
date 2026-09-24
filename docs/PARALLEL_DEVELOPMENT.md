# CONTInue · 多版本并行开发方案（蓝牙直连 / ESP32 / Pico / YD-ESP32-S3）

> 目标：**多个硬件目标并行开发；任何通用改动一次修改，所有 App 端同时生效。**
> 本文档说明如何做到这一点，以及从当前"多分支复制"状态迁移过来的步骤。

---

## 1. 维护中的目标

| 目标 | App 包名 | App 名称 | 数据链路 |
|---|---|---|---|
| `bt`（普通蓝牙） | `com.hidble.phonekeyboard` | 手机蓝牙键盘 | 手机自己当 BLE HID 键盘 → 电脑 |
| `esp32` | `com.hidble.esp32keyboard` | ESP32 蓝牙键盘 | 手机 --BLE--> ESP32 板子 --USB HID--> 电脑 |
| `pico` | `com.hidble.picokeyboard` | Pico 蓝牙键盘 | 手机 --BLE--> Pico 板子 --BLE HID--> 电脑 |
| `yds3` | `com.hidble.ydesp32s3keyboard` | YD-ESP32-S3 键盘 | 手机 --BLE--> YD N16R8 板子 --USB HID--> 电脑 |

所有 App 可同时安装（包名不同）。

---

## 2. 核心原则：一份 Android 代码 + 多个 Gradle flavor

### 2.1 目录/构建结构

```
android/app/
  src/main/                 ← 全部共享代码（改一次，三个版本同时生效）
    java/.../MainActivity.kt, LlmClient.kt, LlmLiterature.kt, 气泡界面…
    java/.../transport/     ← 只有"输出通道"抽象
        OutputTransport.kt      （接口：sendText / key / speed / unicodeMode …）
        PhoneHidTransport.kt    （bt 用：现有 HidDeviceManager + TypingEngine）
        BoardTransport.kt       （esp32/pico 共用：现有 BoardBleManager，协议 1234/1235/1236）
  build.gradle              ← product flavors 定义包名/应用名/默认参数
```

`build.gradle` 关键配置（示意）：

```gradle
android {
    flavorDimensions "target"
    productFlavors {
        bt {
            dimension "target"
            applicationId "com.hidble.phonekeyboard"
            resValue "string", "app_name", "手机蓝牙键盘"
            buildConfigField "String", "TARGET_MODE", '"bt"'
        }
        esp32 {
            dimension "target"
            applicationId "com.hidble.esp32keyboard"
            resValue "string", "app_name", "ESP32 蓝牙键盘"
            buildConfigField "String", "TARGET_MODE", '"board"'
            buildConfigField "String", "BOARD_NAME_HINT", '"ESP32"'
        }
        pico {
            dimension "target"
            applicationId "com.hidble.picokeyboard"
            resValue "string", "app_name", "Pico 蓝牙键盘"
            buildConfigField "String", "TARGET_MODE", '"board"'
            buildConfigField "String", "BOARD_NAME_HINT", '"Pico"'
        }
    }
}
buildFeatures { buildConfig true }
```

- `TARGET_MODE == "bt"`：显示"手机模拟蓝牙键盘"卡片，输出走 `PhoneHidTransport`
- `TARGET_MODE == "board"`：显示"外接键盘板"卡片，输出走 `BoardTransport`
- 扫描时用 `BOARD_NAME_HINT`（ESP32 版找含 "ESP32" 的板子；Pico 版找含 "Pico" 的板子）

### 2.2 共享范围（这些以后只改一份）

- 大模型对话（`LlmClient` / 流式 / 停止输出 / 后台保活）
- 提示词预设、历史对话、文献检索、设置页
- 聊天气泡界面、长文本按行拆分/字节切块
- 版本规则：**每个 flavor 各自 versionCode + APK 文件名带版本号**
  （例：`Esp32BluetoothKeyboard-v28.apk`、`PicoBluetoothKeyboard-v1.apk`、`PhoneBluetoothKeyboard-v22.apk`）

### 2.3 只分叉两小块

1. **包名/应用名/默认设备名**：Gradle flavor 配置
2. **输出通道**：`OutputTransport` 的两个实现
   - `PhoneHidTransport`：现 `HidDeviceManager` + `TypingEngine`（bt）
   - `BoardTransport`：现 `BoardBleManager`（esp32 与 pico **完全共用**，二者协议一致）

---

## 3. 固件端共享方案

ESP32 与 Pico 是两套不同的芯片/框架，无法共享全部 C 代码，但可以共享核心逻辑：

```
shared_firmware/
  protocol.h        ← 命令/协议常量（TEXT/KEY/MOD/UNI/UMOD/SPEED，UUID 1234/1235/1236）
  typing_engine.c/h ← 打字逻辑（ASCII→HID、Alt+X 中文、速度档缩放）
  gbk_table.c/h     ← GBK 码表（约 540KB，两平台共用）
```

- ESP32 侧：`esp32_bridge_firmware/`（NimBLE 收数据 + TinyUSB 输出 USB HID）
- Pico 侧：`pico_firmware/`（BTstack，Pico 自己作为 BLE HID 键盘）
- 两边都 `#include "../shared_firmware/..."`，改协议/打字逻辑只改一处

---

## 4. 从当前状态迁移的步骤（建议顺序）

1. **建一个统一分支**：`multi-target`（从 `esp32-s3-app` 拉出，因为它最新、功能最全）
2. **合并 bt 的界面代码**：把 `phone-keyboard` 分支里"手机模拟蓝牙键盘 + 已配对设备"那张卡片
   合并回来，用 `TARGET_MODE` 控制显示
3. **抽 `OutputTransport` 接口**，把现有两条发送路径分别包成两个实现
4. **加 Gradle flavors**（第 2.1 节），确保 `assembleBtDebug` / `assembleEsp32Debug` / `assemblePicoDebug` 都能出包
5. **抽 `shared_firmware/`**：把 `pico_firmware/usb_hid.c` 的打字逻辑与 `gbk_table.c` 提取成共享模块，
   ESP32 桥接固件改为引用
6. 三个 APK + 两个固件都编译通过后，把 `multi-target` 设为新主线；旧分支保留归档

> 迁移完成前，`esp32-s3-app` / `phone-keyboard` / `plain-bluetooth` 保持现状可用。

---

## 5. 今后改动的规矩（避免再分叉）

- **通用功能**（对话、提示词、文献、界面…）→ 只改 `src/main`，一次生效三端
- **新增差异** → 只加在 flavor 配置 / `OutputTransport` 实现 / 各自固件目录里
- **协议改动**（命令格式、UUID）→ 先改 `shared_firmware/protocol.h`，App 侧同步改 `BoardTransport`
- **每次发布**：所有目标 flavor 都要编译；APK 文件名带各自版本号；更新本文件与 `docs/CONTINUE_ESP32.md`

---

## 6. 相关文档

- `docs/CONTINUE_ESP32.md`：ESP32 桥接固件 + App 详细交接（硬件坑、构建烧录、排查）
- `docs/CONTINUE_DEV.md`：手机自己当蓝牙键盘方案（旧主线）
- `docs/BRANCHES.md`：分支/包名/APK 命名约定
- `esp32_bridge_firmware/README.md`、`esp32_usb_hid_test/README.md`、`pico_firmware/`（Pico 固件）
---

## 7. 已落地：6 个构建变体（2026-09-21）

分支 **`multi-target-6`** 已用 Gradle **双维度 flavor** 实现「3 目标 × 2 版本 = 6 个 APK」：

| 变体（flavor） | APK 文件名 | 包名 | 应用名 |
|---|---|---|---|
| `btFull` | `PhoneBluetoothKeyboard-v29.apk` | `com.hidble.phonekeyboard` | 手机蓝牙键盘 |
| `btLite` | `PhoneBluetoothKeyboard-Lite-v29.apk` | `com.hidble.phonekeyboard.lite` | 手机蓝牙键盘 简明版 |
| `picoFull` | `PicoBluetoothKeyboard-v29.apk` | `com.hidble.picokeyboard` | Pico 蓝牙键盘 |
| `picoLite` | `PicoBluetoothKeyboard-Lite-v29.apk` | `com.hidble.picokeyboard.lite` | Pico 蓝牙键盘 简明版 |
| `esp32Full` | `Esp32BluetoothKeyboard-v29.apk` | `com.hidble.esp32keyboard` | ESP32 蓝牙键盘 |
| `esp32Lite` | `Esp32BluetoothKeyboard-Lite-v29.apk` | `com.hidble.esp32keyboard.lite` | ESP32 蓝牙键盘 简明版 |

6 个包名互不相同，**可同时安装**。

### 代码里的两个开关

- `BuildConfig.TARGET_MODE`：`"bt"`（手机自己当蓝牙 HID 键盘）/ `"board"`（Pico 或 ESP32 外接板）
- `BuildConfig.LITE`：`true` = 简明版（隐藏大模型卡片），`false` = 完整版
- `BuildConfig.BOARD_HINT`：`"Pico"` 或 `"ESP32"`（连接页/提示文案与默认设备名匹配用）

### 简明版包含什么

只保留：**输入文本框 → 发送到键盘**、**常用语**、**输入速度**、**中文输入模式**；
隐藏大模型对话卡片（其余功能随 LLM 卡片一起去掉）。

### 构建原有 6 个 APK

```powershell
$env:JAVA_HOME="D:\jdk17\jdk-17.0.20+8"; $env:ANDROID_HOME="D:\Android"; $env:ANDROID_SDK_ROOT="D:\Android"
cd <repo>\android
.\gradlew.bat assembleDebug --no-parallel     # 全部 6 个变体
# 产物目录：app/build/outputs/apk/<target><Edition>/debug/app-<...>.apk
# 复制为仓库根目录的规范文件名（见上表）
```

---

## 8. 已落地：第四目标 YD-ESP32-S3（2026-09-24）

新增 Gradle target `yds3`，与原 `esp32` 共用 BLE 数据协议和 USB HID 固件逻辑，
但使用独立包名、应用名、图标以及 N16R8 专用固件配置。

| 项目 | 值 |
|---|---|
| 板子丝印 | `YD-ESP32-23-2022-V1.3 v2356` |
| 模组 | `ESP32-S3-WROOM-1 N16R8` |
| 存储 | 16 MB QIO Flash + 8 MB OPI PSRAM |
| USB 口 | 1 个 FT232RQ 串口/烧录口 + 1 个 ESP32-S3 原生 USB/HID 口 |
| App target | `yds3Full` / `yds3Lite` |
| 包名 | `com.hidble.ydesp32s3keyboard[.lite]` |
| 应用名 | `YD-ESP32-S3 键盘[ 简明版]` |
| 固件配置 | `esp32_bridge_firmware/sdkconfig.yds3.defaults` |
| 固件构建 | `.\build_yds3_firmware.ps1` |

详细烧录说明见 `docs/YD_ESP32_S3.md`。

> 注意：`android/gradle.properties` 已加 `org.gradle.jvmargs=-Xmx4096m` 与 `org.gradle.parallel=false`，
> 否则 6 个变体并行编译会 OOM（Kotlin 编译器 Java heap 不足）。

---

## 8. UI 美化（2026-09-21，v30，用 ui-ux-pro-max skill）

按 `ui-ux-pro-max` skill 的针对性检索结论（极简/瑞士风 + 医疗暗色 + 48dp 触控 + 8dp 网格）统一调整：

- **配色**（`res/values/colors.xml`，医疗暗色：青 + 健康绿）
  - 主色 `#0891B2`（青）/ 强调 `#059669`（健康绿）/ 危险 `#F87171`
  - 背景 `#0F172A`、卡片 `#111827`、描边 `#334155`、输入 `#0B1220`
  - 文字 `#F8FAFC` / `#CBD5E1` / `#94A3B8`（对比度更高）
- **圆角与描边**：卡片 16dp、日志/列表 12dp、气泡 16dp（带 1dp 描边）
- **排版层级**：新增统一样式 `SectionTitle`（16sp 粗体）/ `HelperText`（12sp 次要色 + 行距 3dp）/ `FieldLabel`（14sp）
- **触控**：发送按钮 52dp、常用语/下拉 48dp（满足 Android 48dp 最小触控），间距按 8dp 网格
- 聊天气泡：我的（右，青绿 `#134E4A`）/ AI（左，`#1E293B`），宽度自适应不占满

改动都在 `src/main`，**6 个变体同时生效**；v30 的 6 个 APK 已构建。

---

## 9. 6 套差异化图标（2026-09-21，v32）

6 个变体共用同一底图（深色圆角 + 键盘图形），**顶部标签区分目标、底部 LITE 标记区分版本**：

| 变体 | 图标资源 | 顶部标签 | 颜色 | LITE 角标 |
|---|---|---|---|---|
| `btFull` | `ic_launcher_bt_full` | BT | 青 `#22D3EE` | 无 |
| `btLite` | `ic_launcher_bt_lite` | BT | 青 `#22D3EE` | 有 |
| `picoFull` | `ic_launcher_pico_full` | PICO | 绿 `#34D399` | 无 |
| `picoLite` | `ic_launcher_pico_lite` | PICO | 绿 `#34D399` | 有 |
| `esp32Full` | `ic_launcher_esp32_full` | ESP | 琥珀 `#FBBF24` | 无 |
| `esp32Lite` | `ic_launcher_esp32_lite` | ESP | 琥珀 `#FBBF24` | 有 |

- Manifest 用占位符 `android:icon="${appIcon}"` / `roundIcon`，由 `build.gradle` 的变体循环按
  `"@mipmap/ic_launcher_" + target + "_" + edition` 注入。
- 图标 PNG 生成脚本：`tools/gen_launcher_icons.py`（Pillow；改颜色/文字后重跑即可，
  输出到 `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/`）。

---

## 10. 速度档位解锁（2026-09-21，v33）

现象：GBK 模式下 7 档往上没区别 —— 因为 `TypingEngine.MIN_DELAY_MS = 10ms`
把所有延迟都夹到 10ms（蓝牙每份报告约 10ms 的物理下限）。

按用户要求放宽（`android/.../TypingEngine.kt`）：

| 参数 | 原来 | 现在 |
|---|---|---|
| `MIN_DELAY_MS`（通用最小报告间隔） | 10ms | **2ms** |
| `CHAR_GAP_MS`（字符间隔） | 0 | **6**（随档位缩放） |
| `SPEED_SCALES` | `3078,2539,2078,1616,1231,885,600,385,200,62` | **`3078,2539,2078,1616,1231,885,450,260,120,30`** |
| `ALT_MIN_DELAY_MS`（Alt 码松开前最小停顿，新增） | — | **8ms**（防止高速档末位数字来不及转换） |

效果：7 档稍快；**8/9/10 各上移一档**，且不再被 10ms 夹住（10 档最激进）。
GBK/Alt 码转换前保留 8ms 最小停顿，降低"不转换/乱码"概率。

⚠️ 注意：蓝牙每份 HID 报告约 10ms，低于该值只是把报告堆进缓冲，**8-10 档可能丢字/残留修饰键**；
已保留发送失败重试与长文本分段缓冲。属用户要求的高速测试档，若丢字明显请回落到 5-7 档。
