# 应用层帧协议（BLE 传输协议）

Android App 与 Pico W 固件之间通过自定义应用层帧协议在 BLE GATT 上传输 UTF-8 文本。
协议目标是保证**消息完整性**与**可校验性**，允许在无线信道上安全传输任意长度的文本。
本文档与固件 `src/packet_parser.c/.h` 及 Android 端 `BlePacketizer.kt` 严格对齐。

## 1. 帧格式

所有字段均为**大端序（Big-Endian）**，单帧最长 255 字节（受 BTstack ACL 载荷限制）。

```
 0       1               3               5
+--------+---------------+---------------+----------------------------------+
|  TYPE  |   MESSAGE_ID  |      SEQ      |              PAYLOAD              |
|  1 B   |    2 B        |    2 B        |              N B                 |
+--------+---------------+---------------+----------------------------------+
```

| 字段 | 长度 | 说明 |
|---|---|---|
| TYPE | 1 B | 帧类型，见下表 |
| MESSAGE_ID | 2 B | 消息标识，同一逻辑消息的所有帧共享 |
| SEQ | 2 B | 帧序号（块号），DATA 从 0 开始递增 |
| PAYLOAD | 0~240 B | 随帧类型而定 |

帧头固定 5 字节（`PACKET_HEADER_SIZE`）。

## 2. 帧类型（TYPE）

| 值 | 名称 | PAYLOAD | 语义 |
|---|---|---|---|
| `0x01` | START | 固定 6 B：总长度 total_len(4B) + 总包数 total_packets(2B) | 一条消息的第一帧，声明总字节数 |
| `0x02` | DATA | UTF-8 分片（0~240 B） | 中间数据帧，SEQ 为块号 |
| `0x03` | END | 空 | 消息最后一帧；收到后做完整性校验 |
| `0x04` | CANCEL | 空 | 取消/中止当前消息传输 |

### 字段规则

- **MESSAGE_ID**：每次发送新文本时由 Android 生成并递增；固件只接受与当前
  `message_id` 相同的帧。新的 START 帧到来时，视为新消息开始（覆盖未完成旧消息）。
- **SEQ**：DATA 帧从 0 开始严格 +1 递增；固件对 SEQ 做连续性校验，乱序/重复/缺帧
  判定协议错误，丢弃并复位解析器。

## 3. 传输示例

发送文本 `Hello`（5 字节，需 1 个 DATA 分片，共 2 帧）：

```
START  TYPE=0x01  MSG_ID=0x0001  SEQ=0x0000  PAYLOAD=total_len=0x00000005, total_packets=0x0001
DATA   TYPE=0x02  MSG_ID=0x0001  SEQ=0x0000  PAYLOAD="Hello"
END    TYPE=0x03  MSG_ID=0x0001  SEQ=0x0001  PAYLOAD=(empty)
```

固件收到 END 时累计字节数 == 5 == START 声明总长度 → 校验通过 → 注入文本。

若文本为 500 字节（每帧 240 B 分片，需要 3 个 DATA 分片，共 4 帧）：

```
START  MSG_ID=0x0001  PAYLOAD=total_len=500, total_packets=0x0003
DATA   MSG_ID=0x0001  SEQ=0x0000  PAYLOAD=前 240 字节
DATA   MSG_ID=0x0001  SEQ=0x0001  PAYLOAD=中间 240 字节
DATA   MSG_ID=0x0001  SEQ=0x0002  PAYLOAD=最后 20 字节
END    MSG_ID=0x0001  PAYLOAD=(empty)
```

## 4. 完整性校验（固件侧）

`packet_parser_feed()` 在任一条件不满足时判定协议错误（返回 -1），**丢弃整条消息**
并复位解析器：

1. 首帧必须是 START（DATA/END 前无 START 直接报错）；
2. 帧 `message_id` 必须与当前消息一致；
3. DATA 帧 `seq` 必须严格连续（`expected_seq`，从 0 开始）；
4. 累计 payload 字节数不得超过缓冲区上限（`buffer_cap`，默认 2048 B）；
5. END 到达时，累计字节数必须等于 START 声明的 `total_len`。

校验通过后 `packet_parser_feed()` 返回 1，调用方把重组后的文本交给 `usb_hid_inject_text()`。
收到 CANCEL 返回 -2（丢弃部分数据，不算错误）。

## 5. BLE GATT 映射

| 项 | 值 |
|---|---|
| Service UUID | `19B10000-E8F2-537E-4F6C-D104768A1214` |
| TX characteristic UUID | `19B10001-E8F2-537E-4F6C-D104768A1214` |
| 属性 | Write（`WRITE_TYPE_DEFAULT` / Write Without Response 均可） |
| 最大单帧 | 240 B（MTU 协商后 `negotiatedMtu - 3`） |
| 广播名 | `PICO-W-KEYBOARD` |

## 6. Android 侧实现要点

- `BlePacketizer.packetize()`：UTF-8 编码 → 按 `currentMaxPayload` 分片 →
  生成 START（携带总长/总包数）→ DATA×N → END；
- `BleManager.sendText()`：请求 `DESIRED_MTU=247`，协商成功后
  `currentMaxPayload = negotiatedMtu - 3`（≤240）；
- 帧通过**串行写队列**逐帧发送：上一帧 `onCharacteristicWrite` 回调确认后再发下一帧，
  避免并发写乱序。

## 7. 限制与扩展

- 单条消息上限由 `buffer_cap` 决定（当前 2048 B），超长文本请分段发送；
- 当前无链路层 ACK/NACK 与重传，靠 SEQ 连续性校验丢弃乱序/丢帧，断连会中止本次消息；
- 若要支持多字节字符（中文等），需在 `usb_hid.c` 增加 Unicode → HID keycode 映射
  （当前仅支持 ASCII 可打印字符）。
