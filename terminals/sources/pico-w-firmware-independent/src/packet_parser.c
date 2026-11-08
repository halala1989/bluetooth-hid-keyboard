#include "packet_parser.h"
#include <string.h>

static uint16_t be16(const uint8_t *b) {
    return (uint16_t)((b[0] << 8) | b[1]);
}

static uint32_t be32(const uint8_t *b) {
    return ((uint32_t)b[0] << 24) | ((uint32_t)b[1] << 16) |
           ((uint32_t)b[2] << 8) | (uint32_t)b[3];
}

void packet_parser_reset(packet_parser_t *p, uint8_t *buffer, uint32_t buffer_cap) {
    memset(p, 0, sizeof(*p));
    p->state = RX_IDLE;
    p->buffer = buffer;
    p->buffer_cap = buffer_cap;
    p->expected_seq = 0;
}

int packet_parser_feed(packet_parser_t *p, const uint8_t *frame, uint16_t frame_len,
                       uint32_t *out_len) {
    if (frame == NULL || frame_len < PACKET_HEADER_SIZE) {
        return -1;
    }

    const uint8_t type = frame[0];
    const uint16_t msg_id = be16(&frame[1]);
    const uint16_t seq = be16(&frame[3]);
    const uint8_t *payload = frame + PACKET_HEADER_SIZE;
    const uint16_t payload_len = frame_len - PACKET_HEADER_SIZE;

    switch (type) {
    case PACKET_TYPE_START: {
        // Restart regardless of prior state: a new transmission wins.
        if (payload_len != START_PAYLOAD_SIZE) {
            return -1;
        }
        const uint32_t total_len = be32(&payload[0]);
        const uint16_t total_packets = be16(&payload[4]);
        if (total_len > p->buffer_cap) {
            p->overflow = 1;
            packet_parser_reset(p, p->buffer, p->buffer_cap);
            return -1;
        }
        p->state = RX_STARTED;
        p->message_id = msg_id;
        p->total_len = total_len;
        p->total_packets = total_packets;
        p->received = 0;
        p->expected_seq = 0;
        p->overflow = 0;
        return 0;
    }

    case PACKET_TYPE_DATA: {
        if (p->state != RX_STARTED) {
            return -1; /* DATA without START */
        }
        if (msg_id != p->message_id) {
            return -1;
        }
        if (seq != p->expected_seq) {
            /* Out of order / duplicate: drop the whole message. */
            packet_parser_reset(p, p->buffer, p->buffer_cap);
            return -1;
        }
        if (p->received + payload_len > p->buffer_cap) {
            p->overflow = 1;
            packet_parser_reset(p, p->buffer, p->buffer_cap);
            return -1;
        }
        if (payload_len > 0) {
            memcpy(p->buffer + p->received, payload, payload_len);
            p->received += payload_len;
        }
        p->expected_seq++;
        return 0;
    }

    case PACKET_TYPE_END: {
        if (p->state != RX_STARTED) {
            return -1;
        }
        if (msg_id != p->message_id) {
            return -1;
        }
        /* Validate: received bytes must match the declared total length. */
        if (p->received != p->total_len) {
            packet_parser_reset(p, p->buffer, p->buffer_cap);
            return -1;
        }
        const uint32_t result_len = p->received;
        packet_parser_reset(p, p->buffer, p->buffer_cap);
        if (out_len) *out_len = result_len;
        return 1;
    }

    case PACKET_TYPE_CANCEL: {
        packet_parser_reset(p, p->buffer, p->buffer_cap);
        return -2;
    }

    default:
        return -1;
    }
}
