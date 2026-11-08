#ifndef PACKET_PARSER_H
#define PACKET_PARSER_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Application-layer frame protocol (big-endian), mirroring BlePacketizer.kt:
 *
 *   +--------+------------+------------+------------------+
 *   | TYPE 1 | MESSAGE_ID | SEQ 2      | PAYLOAD  N       |
 *   | byte   | 2 bytes    | bytes      | bytes            |
 *   +--------+------------+------------+------------------+
 *
 *   TYPE = 0x01 START : payload = total UTF-8 length (4B) + total packet count (2B)
 *   TYPE = 0x02 DATA  : payload = UTF-8 chunk, SEQ is the chunk index (0-based)
 *   TYPE = 0x03 END   : payload empty
 *   TYPE = 0x04 CANCEL: payload empty
 *
 * The injected text is complete only after END has been received AND the
 * accumulated byte count matches the START-declared total length.
 */

#define PACKET_TYPE_START  0x01
#define PACKET_TYPE_DATA   0x02
#define PACKET_TYPE_END    0x03
#define PACKET_TYPE_CANCEL 0x04

#define PACKET_HEADER_SIZE 5   /* TYPE(1) + MESSAGE_ID(2) + SEQ(2) */
#define START_PAYLOAD_SIZE 6   /* total_len(4) + total_packets(2) */

typedef enum {
    RX_IDLE = 0,
    RX_STARTED,   /* START seen, collecting DATA chunks */
} rx_state_t;

typedef struct {
    rx_state_t state;
    uint16_t message_id;     /* current message id */
    uint32_t total_len;      /* declared by START (bytes) */
    uint16_t total_packets;  /* declared by START */
    uint16_t expected_seq;   /* next expected DATA seq */
    uint32_t received;       /* bytes accumulated so far */
    uint8_t *buffer;         /* caller-provided text buffer */
    uint32_t buffer_cap;     /* capacity of buffer */
    int overflow;            /* set when received > capacity */
} packet_parser_t;

/**
 * Reset parser to idle, discarding any partial message.
 */
void packet_parser_reset(packet_parser_t *p, uint8_t *buffer, uint32_t buffer_cap);

/**
 * Feed one raw BLE write (a single application-layer frame).
 *
 * Returns:
 *   0  -> message still in progress (or frame consumed, no complete text yet)
 *   1  -> a complete message is ready: *out_len = length of UTF-8 text in buffer
 *  -1  -> protocol error (invalid frame / length mismatch); parser auto-resets
 *  -2  -> CANCEL received; partial data discarded (not an error)
 */
int packet_parser_feed(packet_parser_t *p, const uint8_t *frame, uint16_t frame_len,
                       uint32_t *out_len);

#ifdef __cplusplus
}
#endif

#endif /* PACKET_PARSER_H */
