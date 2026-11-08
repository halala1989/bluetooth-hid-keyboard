# Android 端协议实现说明

> 完整协议规范见固件仓库 `pico-w-firmware/docs/PROTOCOL.md`。本文档聚焦 Android 端
> `BlePacketizer.kt` / `BleManager.kt` 的实现细节，两端严格对齐同一份字节格式。

## 1. 帧格式（大端序）

```
+--------+------------+------------+--------------+
| TYPE 1 | MESSAGE_ID | SEQ 2      | PAYLOAD  N   |
| byte   | 2 bytes    | bytes      | bytes        |
+--------+------------+------------+--------------+
```

帧头固定 5 字节（`BlePacketizer.HEADER_SIZE`），对应固件 `PACKET_HEADER_SIZE`。

| 字段 | 长度 | 说明 |
|---|---|---|
| TYPE | 1 B | `0x01` START / `0x02` DATA / `0x03` END / `0x04` CANCEL |
| MESSAGE_ID | 2 B | 每次 `sendText` 递增（`messageIdCounter & 0xFFFF`） |
| SEQ | 2 B | DATA 帧块号，从 0 递增 |
| PAYLOAD | 0~240 B | 见各类型 |

## 2. 帧类型

| 值 | 名称 | PAYLOAD | Android 端构造 |
|---|---|---|---|
| `0x01` | START | 总长度 4B + 总包数 2B | `packetize()` 开头，`ByteBuffer.putInt(totalLen).putShort(totalPkts)` |
| `0x02` | DATA | UTF-8 分片 | 按 `maxDataPayload` 切片，`copyOfRange` |
| `0x03` | END | 空 | 序列末尾 |
| `0x04` | CANCEL | 空 | `cancel()` 构造单帧 |

## 3. 分包逻辑（BlePacketizer.packetize）

```
utf8 = text.toByteArray(UTF_8)
chunkSize = maxDataPayload.coerceIn(1, 240)
totalChunks = ceil(utf8.size / chunkSize)

frames = [START(totalLen=utf8.size, totalPkts=totalChunks)]
frames += DATA(i, utf8[i*chunkSize .. (i+1)*chunkSize))  for i in 0 until totalChunks
frames += END
```

- `maxDataPayload` 由调用方 `BleManager.currentMaxPayload()` 根据协商后 MTU 计算：
  `(negotiatedMtu - 3) - 5`，再 `coerceAtMost(240).coerceAtLeast(1)`。
- 空文本不产生 DATA 帧；`sendText` 已在上层拦截空字符串。

## 4. 发送流程（BleManager）

1. `sendText()` 校验：已连接 + Service 就绪 + 非发送中 + 非空。
2. 生成新 `messageId`，整条消息帧序列入队，`_sendState = Sending(0, totalPackets)`。
3. `writeNext()`：取队首帧 → `gatt.writeCharacteristic(characteristic, bytes, WRITE_TYPE_DEFAULT)`
   （Android 13+ 用三参重载，旧版走 `characteristic.value = bytes` 两参重载）。
4. `onCharacteristicWrite(status)`：
   - 非 `GATT_SUCCESS` → 清队列，`SendState.Failed(status)`。
   - `cancelRequested` → 只 drain，不出成功态。
   - 队首出队；队列空 → `SendState.Success(totalBytes, totalPackets)`；否则继续 `writeNext()`。

## 5. 与固件的对齐要点

| 项 | Android 端 | 固件端 |
|---|---|---|
| Service UUID | `19B10000-E8F2-537E-4F6C-D104768A1214` | 同一 UUID |
| TX Char UUID | `19B10001-E8F2-537E-4F6C-D104768A1214` | 同一 UUID |
| 帧头 | `HEADER_SIZE = 5` | `PACKET_HEADER_SIZE = 5` |
| 最大 DATA payload | ≤ 240 B | ACL 载荷支持 240 B |
| 完整性判定 | 依赖固件；Android 只保证全帧写入成功 | END + 长度校验通过才注入 |

## 6. 限制与扩展

- 单条消息上限由固件重组缓冲区决定（当前 2048 B）；超长文本需 App 端分段多次发送。
- 无链路层重传：写失败即中止本次消息，不会自动重试。
- 帧格式扩展（如增加新 TYPE）需两端同步修改 `BlePacketizer.kt` 与 `packet_parser.c`。

---
