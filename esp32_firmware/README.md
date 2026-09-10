# ESP32-S3 Super Mini · BLE HID 键盘固件（移植自 Pico 方案）

## 一、这块板子实测规格（2026-09-10）

- 芯片：ESP32-S3 (QFN56) rev v0.2
- 无线：Wi-Fi + BT 5 (LE)
- 核心：双核 + LP 核，240MHz
- 存储：**4MB Flash (XMC) + 2MB PSRAM (AP_3v3)**
- 串口：ESP32-S3 原生 USB-Serial/JTAG（本机 COM10，VID_303A:PID_1001）
- MAC：28:84:85:6D:74:50（BLE 地址末位 +2 = 28:84:85:6D:74:52）

## 二、目标 / 与 Pico 方案的关系

把 `pico_firmware/`（Pico W + BTstack，C）那套「BLE HID 键盘 + 手机发命令」的能力移植到 ESP32-S3：

1. **对电脑**：板子作为 BLE HID 键盘被配对使用（电脑蓝牙里显示 **"ESP32-S3 Keyboard"**）。
2. **对手机**：板子同时作为 BLE GATT Server，暴露与 Pico 相同的自定义服务，手机 App 无需改协议：
   - Service  `00001234-0000-1000-8000-00805f9b34fb`
   - Command  `00001235-0000-1000-8000-00805f9b34fb`（Write，收手机命令）
   - Status   `00001236-0000-1000-8000-00805f9b34fb`（Notify，回 OK/ERR/STATUS）
   - 命令格式：`TEXT:` `KEY:` `MOD:` `UNI:` `UMOD:` `SPEED:`，行尾 `\n`
3. **大缓冲**：用板载 2MB PSRAM 做输入缓冲（Pico 只有 264KB SRAM），手机可快速把长文本/整段病历灌进来，
   固件按可配置速度稳定「滴灌」成 HID 报文，减少丢键。
4. 中文输入逻辑（Alt+X / GBK 机内码 / 十六进制 / 十进制）沿用 `pico_firmware/usb_hid.c` 的按键序列思路，
   码表可复用 `pico_firmware/gbk_table.c`。

## 三、当前进度（2026-09-10）

- [x] 工程骨架：基于 ESP-IDF 官方 `examples/bluetooth/esp_hid_device`（Apache-2.0），
      配置为 **NimBLE + 键盘角色**（`CONFIG_EXAMPLE_KBD_ENABLE=y`）。
- [x] 目标 ESP32-S3、4MB Flash、2MB PSRAM 已启用（开机日志实测 PSRAM 内存测试 OK）。
- [x] 编译、烧录到 COM10 成功；开机稳定不重启。
- [x] 电脑 BLE 扫描能发现 **"ESP32-S3 Keyboard"**，广播含标准 HID 服务 `00001812`。
- [x] 无屏幕配对：改为 Just Works（`BLE_SM_IO_CAP_NO_IO` + `sm_mitm=0`），Windows 可直接配对。
- [x] 自定义服务 1234/1235/1236 + `TEXT/KEY/MOD/UNI/UMOD/SPEED` 命令解析（固件自检通过，待重新配对后端到端验证）
- [x] PSRAM 512KB 环形缓冲 + 按速度档控速输出（数据先入缓冲，独立任务“滴灌”成 HID 报文）
- [~] Unicode 已支持 Alt+X 模式（默认，Win11 记事本/Word 可用）；GBK/十六进制小键盘模式待移植（gbk_table.c）
- [ ] 手机 App 侧接入（或直接复用 master 分支的 Pico App）

## 四、构建与烧录（本机实测）

```powershell
# 1) 载入 ESP-IDF 环境（本机已装 IDF v5.2.2 + 工具链）
. $env:USERPROFILE\esp\v5.2.2\esp-idf\export.ps1

# 2) 首次编译前，先打 IDF 的 HID 任务栈补丁（见下方“踩坑”）
powershell -ExecutionPolicy Bypass -File <repo>\esp32_firmware\patch_idf_nimble_hid_stack.ps1

# 3) 设定目标芯片（只需一次）
cd <repo>\esp32_firmware
idf.py set-target esp32s3

# 4) 编译（注意：build 目录必须放在纯英文路径，否则中文路径会导致 ldgen 失败）
idf.py -B C:\esp32_fw_build build

# 5) 烧录（串口按实际改，本机 COM10）
idf.py -B C:\esp32_fw_build -p COM10 flash
```

## 五、踩坑与修复（重要）

1. **中文路径导致编译失败**：工程路径含中文时，`ldgen` 调用 objdump 读不到中文路径下的 `libxtensa.a`。
   → 解决：用 `idf.py -B C:\esp32_fw_build` 把 **build 输出目录** 放到纯英文路径（源码路径可保持中文）。
2. **NimBLE HID 事件任务栈溢出 → 板子反复重启，广播扫不到**：
   日志 `***ERROR*** A stack overflow in task ble_hidd_events has been detected.`
   IDF v5.2.2 的 `components/esp_hid/src/nimble_hidd.c` 里该任务栈硬编码 2048 字节。
   → 解决：运行 `patch_idf_nimble_hid_stack.ps1` 改成 6144 字节后重新编译烧录。
   （换电脑/换 IDF 版本时都要再打一次这个补丁。）
3. **无屏幕开发板如何配对**：官方示例默认 `BLE_SM_IO_CAP_DISP_ONLY` + MITM（需要显示配对码），
   这块板没有屏幕，Windows 会卡在输入配对码。
   → 解决：改为 `BLE_SM_IO_CAP_NO_IO`（Just Works）+ `sm_mitm = 0`，Windows 直接配对。
4. **Flash 大小**：工程默认按 2MB 编译，实际板子是 4MB。
   → 已在 `sdkconfig.defaults` 固定 `CONFIG_ESPTOOLPY_FLASHSIZE_4MB=y`。

## 六、说明

- 固件基于 Espressif ESP-IDF 官方示例修改（Apache-2.0），后续在 `main/` 加入本项目的自定义服务与输入缓冲逻辑。
- ESP32-S3 **没有蓝牙经典（BR/EDR）**，只有 BLE；原 Pico 方案本来就是 BLE HID，所以不受影响。