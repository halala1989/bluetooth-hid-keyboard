package com.example.picowkeyboard.ble

import java.nio.ByteBuffer

/**
 * Application-layer BLE frame types agreed with the Pico W firmware.
 *
 * Wire layout of every frame (big-endian):
 *
 *   +--------+------------+------------+------------------+
 *   | TYPE 1 | MESSAGE_ID | SEQ 2      | PAYLOAD  N       |
 *   | byte   | 2 bytes    | bytes      | bytes            |
 *   +--------+------------+------------+------------------+
 *
 *   TYPE = 0x01 START   : payload = total UTF-8 length (4B) + total packet count (2B)
 *   TYPE = 0x02 DATA    : payload = UTF-8 chunk, SEQ is the chunk index (0-based)
 *   TYPE = 0x03 END     : payload empty
 *   TYPE = 0x04 CANCEL  : payload empty
 *
 * The Pico W only starts injecting characters after it has received END.
 */
object BlePacketizer {

    const val TYPE_START: Byte = 0x01
    const val TYPE_DATA: Byte = 0x02
    const val TYPE_END: Byte = 0x03
    const val TYPE_CANCEL: Byte = 0x04

    /** Fixed frame header size: TYPE(1) + MESSAGE_ID(2) + SEQ(2). */
    const val HEADER_SIZE: Int = 5

    /** Payload size of a START frame: total length(4) + total packet count(2). */
    private const val START_PAYLOAD_SIZE: Int = 6

    /** Max safe payload we ever emit per DATA frame regardless of negotiated MTU. */
    private const val MAX_DATA_PAYLOAD = 240

    data class Packet(
        val type: Byte,
        val messageId: Int,
        val seq: Int = 0,
        val payload: ByteArray = ByteArray(0),
    ) {
        fun toBytes(): ByteArray {
            val size = HEADER_SIZE + payload.size
            val buf = ByteBuffer.allocate(size)
            buf.put(type)
            buf.putShort(messageId.toShort())
            buf.putShort(seq.toShort())
            buf.put(payload)
            return buf.array()
        }

        fun describe(): String = when (type) {
            TYPE_START -> "START id=$messageId"
            TYPE_DATA -> "DATA  id=$messageId seq=$seq (${payload.size}B)"
            TYPE_END -> "END   id=$messageId"
            TYPE_CANCEL -> "CANCEL id=$messageId"
            else -> "UNKNOWN type=${type.toInt() and 0xFF}"
        }
    }

    /**
     * Splits [text] into UTF-8 bytes and produces the complete frame sequence
     * [START, DATA(0..n-1), END] for one message.
     *
     * @param maxDataPayload maximum bytes allowed in a single DATA payload
     *                       (derived from the negotiated MTU by the caller).
     */
    fun packetize(text: String, messageId: Int, maxDataPayload: Int): List<Packet> {
        val utf8 = text.toByteArray(Charsets.UTF_8)
        val chunkSize = maxDataPayload.coerceIn(1, MAX_DATA_PAYLOAD)
        val totalChunks = if (utf8.isEmpty()) 0 else (utf8.size + chunkSize - 1) / chunkSize

        val startPayload = ByteBuffer.allocate(START_PAYLOAD_SIZE)
            .putInt(utf8.size)
            .putShort(totalChunks.toShort())
            .array()

        val packets = ArrayList<Packet>(totalChunks + 2)
        packets.add(Packet(TYPE_START, messageId, 0, startPayload))

        var offset = 0
        var seq = 0
        while (offset < utf8.size) {
            val end = minOf(offset + chunkSize, utf8.size)
            packets.add(Packet(TYPE_DATA, messageId, seq, utf8.copyOfRange(offset, end)))
            offset = end
            seq++
        }

        packets.add(Packet(TYPE_END, messageId))
        return packets
    }

    /** Builds an empty message with a single END frame (useful for flushing). */
    fun emptyMessage(messageId: Int): List<Packet> = listOf(Packet(TYPE_END, messageId))

    /** Builds a CANCEL frame for [messageId]. */
    fun cancel(messageId: Int): Packet = Packet(TYPE_CANCEL, messageId)
}
