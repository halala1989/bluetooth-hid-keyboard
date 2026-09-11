# CONTINUE · ESP32-S3 桥接方案（给下一个 AI 的交接文档）

> 最后更新：2026-09-12
> 相关分支：`esp32-s3-app`（当前主线）
> 更早的「手机自己当蓝牙键盘」方案见 `docs/CONTINUE_DEV.md`（分支 `phone-keyboard`）

---

## 0. 一句话现状

**已经跑通**：手机 App --BLE--> ESP32-S3 板子（PSRAM 缓冲 + 控速）--USB HID--> 电脑打字。
实测：BLE 发 `123`，电脑记事本收到 `123`；单字符 `1` 不狂打、不卡键。

架构（关键，不要再走回 BLE HID）：

```
手机 App  --BLE(GATT 1234/1235/1236)-->  板子  --USB HID(TinyUSB)-->  电脑
```

为什么不用「BLE HID 键盘」直接连电脑：Windows 侧会反复连上又断（reason 531），
所以改成 **USB 当键盘 + BLE 只做数据通道**，稳定可靠。

---

## 1. 硬件事实（非常重要，踩过坑）

- 板子：ESP32-S3 Super Mini（QFN56 rev v0.2），Wi-Fi + BT5 LE，**4MB Flash + 2MB PSRAM**
- **USB 口二选一**：ESP32-S3 的 USB-Serial/JTAG（串口/调试/下载）与 USB-OTG（HID）共用同一组 USB 引脚。
  - 跑 HID 固件时：USB = 键盘 → **COM 串口消失**
  - 要烧录/看日志：按住 **BOOT(GPIO0)** 再插 USB（或按 RESET）→ 进下载模式 → COM 口重新出现
- **这块板子的硬件特性**：烧录完成后**不会自动进入运行模式**，会停在 `waiting for download`；
  必须**拔插一次 USB**（不按任何键）才会正常运行。每次烧录都这样，别误判为固件问题。
- 设备 ID：
  - 下载/串口模式：`VID_303A&PID_1001`（USB-Serial/JTAG，本机 COM10）
  - USB HID 模式：`VID_303A&PID_4004`（TinyUSB，本机识别为 HID Keyboard Device）
- 本机 IDF：`C:\Users\halal\esp\v5.2.2\esp-idf`（export.ps1），工具链在 `~/.espressif`
- **工程路径含中文会导致 ldgen 失败**：编译时必须把 build 目录放到纯英文路径（下面命令已处理）

---

## 2. 仓库分支说明

| 分支 | 用途 | 备注 |
|---|---|---|
| `esp32-s3-app` | **当前主线**：ESP32-S3 桥接 App + 桥接固件 | 本文件所在分支 |
| `phone-keyboard` | 手机自己当蓝牙键盘 + 大模型（旧主线） | versionCode 21 |
| `plain-bluetooth` | 普通蓝牙版存档（无 ESP32 桥接） | versionCode 16 |

---

## 3. 固件工程

| 目录 | 说明 | 状态 |
|---|---|---|
| `esp32_bridge_firmware/` | **当前使用**：USB HID 输出 + BLE 数据通道 | ✅ 可用 |
| `esp32_usb_hid_test/` | 最小 USB HID 测试（每 60 秒依次打 1..9,0） | 验证用，可保留 |
| `esp32_firmware/` | 旧方案：BLE HID 键盘（NimBLE + esp_hid） | ❌ 弃用（Windows 闪断），仅作参考 |

### 3.1 桥接固件要点（`esp32_bridge_firmware/main/bridge.c`）

- **BLE（NimBLE）GATT Server**（只做数据通道，不做 HID）：
  - Service `00001234-...`；写特征 `00001235-...`（手机→板子，命令以 `\n` 结束）；通知特征 `00001236-...`（板子→手机）
  - 设备名 **`ESP32-S3 Bridge`**，广播里带 0x1234
  - 支持命令：`TEXT:` `KEY:` `MOD:` `UNI:` `UMOD:` `SPEED:` `STOP` `DEBUG`
- **USB HID（TinyUSB）**：键盘 Report ID = 1，`tud_hid_keyboard_report(HID_ITF_PROTOCOL_KEYBOARD, modifier, keycode)` 输出
- **PSRAM 512KB 环形缓冲** + `data_task` 按速度档（1-10）"滴灌"成按键报文
- **必须保留的四个修复**（改代码时不要删）：
  1. `hid_send_report()` 用 **`tud_hid_n_ready(0)`** 等"端点空闲 + 未挂起"再发（只用 `tud_ready()` 不够！）
  2. **卡键看门狗**：`s_key_down` 标记 + 保活任务每 200ms 重试"松开所有键"
  3. **USB 保活**：每 5 秒发一个空键盘报文，防止 Windows 空闲挂起（挂起后 `tud_ready()=false`，发送必失败）
  4. **急停**：按一下 BOOT(GPIO0) → `clear_pending_input()`：清空缓冲 + 强制松开所有键；协议里 `STOP` 同效
- **日志**：控制台改成 UART0（USB 让给 HID），所以 HID 模式下**看不到串口日志**；
  需要诊断时用 BLE 发 `DEBUG`，会回 `DBG:mount=… susp=… ready=… hidrdy=…`

---

## 4. Android App 要点（`esp32-s3-app` 分支）

- 包名 **`com.hidble.esp32keyboard`**，应用名 **「ESP32 蓝牙键盘」**（与普通蓝牙版 `com.hidble.phonekeyboard` 可同时安装）
- 连接管理页：只有「外接键盘板」卡片（扫描/连接/断开）；扫描会同时列出**手机系统里已配对**的板子（标记"已配对/可直接连"）
- 主界面顶部状态：`外接板已连接 / 未连接外接板`（只看板子，**不再要求手机连电脑**）
- 「发送到键盘」：**只发大模型最近一次发言**（自动去掉 `AI：` 前缀），优先走外接板并自动重连
- 长文本发送：`BoardBleManager.sendText()` 会**按行拆分**（每行 `TEXT:`，行间 `KEY:ENTER`），
  并按 **UTF-8 字节**切块（≤800B），避免固件 1200 字节行缓冲溢出 / 换行导致 `ERR:INVALID_CMD`
- 对话区：**聊天气泡**（我的靠右绿色、AI 靠左深灰，宽度自适应不占满）；`llmOutput` 隐藏但仍是数据源
- **版本规则（用户要求）**：每次更新都要 **versionCode +1**，且 **APK 文件名带版本号**
  例：`Esp32BluetoothKeyboard-v28.apk`（同时更新 `Esp32BluetoothKeyboard-debug.apk` 为最新）

---

## 5. 构建与烧录（本机实测命令）

```powershell
# 载入 ESP-IDF
. $env:USERPROFILE\esp\v5.2.2\esp-idf\export.ps1

# ── 烧录桥接固件（板子必须先按住 BOOT 插 USB，进入下载模式出现 COM10）──
cd <repo>\esp32_bridge_firmware
idf.py -B C:\esp32_bridge_build build
idf.py -B C:\esp32_bridge_build -p COM10 flash
# 烧完后：松开 BOOT → 拔插一次 USB（不按键）→ 板子进入运行模式

# ── Android App ──
$env:JAVA_HOME="D:\jdk17\jdk-17.0.20+8"; $env:ANDROID_HOME="D:\Android"; $env:ANDROID_SDK_ROOT="D:\Android"
cd <repo>\android; .\gradlew.bat assembleDebug
# 产物：android\app\build\outputs\apk\debug\app-debug.apk
# 复制为：Esp32BluetoothKeyboard-v<versionCode>.apk
```

---

## 6. 常见问题排查

| 现象 | 原因 / 处理 |
|---|---|
| `ERR:HID_NOT_READY` | USB 被挂起或端点忙；先发 `DEBUG` 看 `susp/ready`；确认保活任务在跑 |
| `ERR:INVALID_CMD` | 文本带换行未拆分（App 已修）；或发的命令没有 `xxx:` 前缀 |
| 某个键狂打不停 | 松开报文失败 → 已由 `tud_hid_n_ready` + 看门狗修复；应急按 **BOOT 急停** |
| 烧录后不运行、COM 口还在 | **拔插 USB**（或按 RESET）退出下载模式（板子特性） |
| 手机扫不到板子 | 板子是否在下载模式（COM 口还在）；是否上电；App 是否授予"附近的设备"权限 |
| 手机连不上板子 | 用连接页里"已配对/可直接连"的条目；或先在手机蓝牙设置里取消连接再试 |
| Windows 蓝牙里还有旧的 ESP32-S3 Keyboard | 已不用 BLE HID，可在系统里删掉；现在只认 USB 键盘 |

---

## 7. 待办 / 下一步

- [ ] **GBK 模式移植**：目前中文只支持 Alt+X（`UNI_MODE_ALTX`）；另一台电脑用 GBK，
      需要把 `pico_firmware/gbk_table.c`（540KB 映射表）移植进桥接固件，实现 `UMOD:2`
- [ ] App 端加 `STOP` 按钮（协议已支持），配合板子 BOOT 急停
- [ ] 真机长时间测试：长文本、连续多轮、电脑休眠/唤醒后能否继续、USB 拔插恢复
- [ ] 可选：气泡加复制/时间戳；"编辑最近一条 AI 回复"入口（当前气泡只读）
- [ ] 可选：去掉 `DEBUG` 与保活日志噪音；给固件加版本号打印

---

## 8. 相关文档

- `docs/CONTINUE_DEV.md`：手机自己当蓝牙键盘方案（分支 `phone-keyboard`）
- `docs/BRANCHES.md`：两个 App 的包名/分支/APK 命名约定
- `esp32_bridge_firmware/README.md`：桥接固件说明
- `esp32_usb_hid_test/README.md`：USB HID 最小测试（含"每分钟打数字"验证记录）
- `docs/LLM_PROVIDERS.md`：App 里大模型提供方（含火山 Agent Plan / 文献检索）
---

## 9. 后续开发目标（Roadmap，2026-09-12 整理）

> 完整的三版本并行方案见 `docs/PARALLEL_DEVELOPMENT.md`。

### P0 · 必做（下一阶段主线）

1. **三版本并行架构落地**（一次改动三端生效）
   - 一份 Android 代码 + Gradle product flavors：`bt` / `esp32` / `pico`
   - 抽 `OutputTransport` 接口：`PhoneHidTransport`（bt）/ `BoardTransport`（esp32+pico 共用）
   - 建 `shared_firmware/`：`protocol.h` + `typing_engine.c` + `gbk_table.c`，两个固件共用
   - 迁移 6 步详见 `docs/PARALLEL_DEVELOPMENT.md` 第 4 节
2. **GBK 中文模式移植**
   - 目前桥接固件只支持 `UNI_MODE_ALTX`（Alt+X）；另一台电脑用 **GBK 机内码**
   - 把 `pico_firmware/gbk_table.c`（约 540KB）与 `usb_hid.c` 里的 GBK 逻辑移到 `shared_firmware/`
   - 桥接固件实现 `UMOD:2`（GBK）与 `UMOD:0/1`（十进制/十六进制小键盘）
3. **App 端"停止/清空"按钮**
   - 协议已支持 `STOP`（清空缓冲 + 强制松开），App 的 `BoardTransport` 加一个按钮调用即可

### P1 · 重要

4. **长时真机测试清单**
   - 长文本（数千字）连续发送；多轮对话连续发送
   - 电脑休眠/唤醒后继续发送；USB 拔插后自动恢复
   - 手机切后台/锁屏期间板子继续打字
   - 不同速度档（1-10）在电脑端的实际表现与丢键率
5. **Pico 版 App 建立**
   - 新建 flavor `pico`（`com.hidble.picokeyboard`，应用名"Pico 蓝牙键盘"）
   - 复用 `BoardTransport`（Pico 与 ESP32 协议一致），仅默认设备名提示不同
   - 用现有 `pico_firmware/` 固件联调
6. **固件健壮性**
   - BLE 断线自动重连、连接多个手机时的行为
   - USB 挂起/恢复策略再优化（当前靠 5 秒空报文保活）
   - 给固件打版本号，`DEBUG` 命令输出固件版本

### P2 · 可选/优化

7. **气泡界面增强**：长按复制、时间戳、编辑"最近一条 AI 回复"后重新发送
8. **Token 加密**：API Key/检索 Key 改用 `EncryptedSharedPreferences`（当前明文）
9. **清理调试代码**：正式版去掉 `DEBUG` 命令与保活日志噪音；
   `esp32_usb_hid_test/` 保留为验证工具，不参与正式发布
10. **医学预设同步**：门诊病历/书面化/普通模式等预设在三版本中统一维护
11. **APK 发布规范**：三版本各自 `versionCode +1`，文件名带版本号
    （`PhoneBluetoothKeyboard-vXX.apk` / `Esp32BluetoothKeyboard-vXX.apk` / `PicoBluetoothKeyboard-vXX.apk`）

### 维护规则（避免再分叉）

- 通用功能只改 `src/main`（三端自动生效）
- 新增差异只加在 flavor 配置 / `OutputTransport` 实现 / 各平台固件目录
- 协议改动先改 `shared_firmware/protocol.h`，App 侧同步改 `BoardTransport`
- 每次发布三个 flavor 都要编译；更新本文件与 `docs/PARALLEL_DEVELOPMENT.md`