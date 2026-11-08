/**
 * Pico W Keyboard Injector - firmware main.
 *
 * Architecture:
 *   Android (BLE GATT Client)
 *       |  writes application-layer frames (see packet_parser.h)
 *       v
 *   Pico W (BTstack BLE peripheral / GATT server)
 *       |  packet_parser reconstructs the UTF-8 text
 *       v
 *   USB HID keyboard  ->  Windows host types the characters
 *
 * Uses BTstack (via pico-sdk pico_btstack_ble) for BLE and TinyUSB for the
 * USB HID device role.
 */

#include <stdio.h>
#include <string.h>
#include "pico/stdlib.h"
#include "pico/cyw43_arch.h"
#include "hardware/timer.h"
#include "btstack.h"
#include "pico/btstack_cyw43.h"

// Generated from pico_w_keyboard.gatt by pico_btstack_make_gatt_header.
#include "pico_w_keyboard.h"

#include "usb_hid.h"
#include "packet_parser.h"

// ---------------------------------------------------------------------------
// Text reconstruction buffer
// ---------------------------------------------------------------------------
#define TEXT_BUFFER_CAP 2048
static uint8_t text_buffer[TEXT_BUFFER_CAP];
static packet_parser_t parser;

// ---------------------------------------------------------------------------
// BLE / GATT
// ---------------------------------------------------------------------------
static btstack_packet_callback_registration_t hci_event_callback_registration;

// TX characteristic value handle; generated from pico_w_keyboard.gatt.
#define ATT_TX_VALUE_HANDLE \
    ATT_CHARACTERISTIC_19B10001_E8F2_537E_4F6C_D104768A1214_TX_VALUE_HANDLE
static uint16_t att_tx_handle = ATT_TX_VALUE_HANDLE;

// Advertising: name "PICO-W-KEYBOARD" (matches the Android DEVICE_NAME_FILTER).
// AD structure: [len][0x01 flags], [len][0x09 complete local name]
static const uint8_t adv_data[] = {
    0x02, 0x01, 0x06,
    0x10, 0x09, 'P', 'I', 'C', 'O', '-', 'W', '-', 'K', 'E', 'Y', 'B', 'O', 'A', 'R', 'D',
};

static void hci_packet_handler(uint8_t packet_type, uint16_t channel,
                               uint8_t *packet, uint16_t size) {
    (void)channel;
    (void)size;
    if (packet_type != HCI_EVENT_PACKET) {
        return;
    }
    // Connection open/close handling is optional for the injector.
    switch (hci_event_packet_get_type(packet)) {
    case BTSTACK_EVENT_STATE:
        if (btstack_event_state_get_state(packet) == HCI_STATE_WORKING) {
            // Stack is up: configure and enable advertisements.
            uint16_t adv_int_min = 100;
            uint16_t adv_int_max = 100;
            uint8_t adv_type = 0;
            bd_addr_t null_addr = {0, 0, 0, 0, 0, 0};
            gap_advertisements_set_params(adv_int_min, adv_int_max, adv_type,
                                          0, null_addr, 0x07, 0x00);
            gap_advertisements_set_data(sizeof(adv_data), (uint8_t *)adv_data);
            gap_advertisements_enable(1);
            printf("Advertising as PICO-W-KEYBOARD\n");
        }
        break;
    default:
        break;
    }
}

/**
 * Called by BTstack when the BLE client reads or writes an attribute.
 * We only care about writes to the TX characteristic: each write is one
 * application-layer frame (START / DATA / END / CANCEL).
 */
static uint16_t att_write_callback(hci_con_handle_t con_handle,
                                   uint16_t att_handle,
                                   uint16_t transaction_mode,
                                   uint16_t offset,
                                   uint8_t *buffer,
                                   uint16_t buffer_size) {
    (void)con_handle;
    (void)transaction_mode;

    if (att_handle != att_tx_handle) {
        return 0;
    }

    if (buffer == NULL) {
        // Prepare phase (write with response): accept any size up to the
        // frame buffer. Real data follows with buffer != NULL.
        return TEXT_BUFFER_CAP + PACKET_HEADER_SIZE;
    }

    // A frame is delivered in one shot here (our MTU >= frame size).
    if (offset != 0) {
        // Fragmented writes are not expected with this protocol; ignore.
        return 0;
    }

    uint32_t text_len = 0;
    int rc = packet_parser_feed(&parser, buffer, buffer_size, &text_len);
    if (rc < 0) {
        printf("frame error rc=%d (size=%u)\n", rc, buffer_size);
        return 0;
    }

    if (rc == 1) {
        // A complete UTF-8 message is ready -> inject it via USB HID.
        printf("injecting %u bytes\n", text_len);
        cyw43_arch_gpio_put(CYW43_WL_GPIO_LED_PIN, 1);
        usb_hid_inject_text(text_buffer, text_len);
        cyw43_arch_gpio_put(CYW43_WL_GPIO_LED_PIN, 0);
    }
    return 0;
}

static uint16_t att_read_callback(hci_con_handle_t con_handle,
                                  uint16_t att_handle,
                                  uint16_t offset,
                                  uint8_t *buffer,
                                  uint16_t buffer_size) {
    (void)con_handle;
    (void)att_handle;
    (void)offset;
    (void)buffer;
    (void)buffer_size;
    return 0; // nothing to read
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------
int main(void) {
    stdio_init_all();

    // Initialises CYW43 driver architecture; enables BT (CYW43_ENABLE_BLUETOOTH).
    if (cyw43_arch_init()) {
        printf("cyw43_arch_init failed\n");
        return -1;
    }

    // BTstack: run loop + HCI are set up by pico_btstack_cyw43.
    l2cap_init();
    sm_init();
    sm_set_io_capabilities(SSP_IO_CAPABILITY_NO_INPUT_NO_OUTPUT);
    sm_set_authentication_requirements(0); // no bonding

    att_server_init(profile_data, att_read_callback, att_write_callback);

    hci_event_callback_registration.callback = &hci_packet_handler;
    hci_add_event_handler(&hci_event_callback_registration);

    printf("TX characteristic handle = 0x%04X\n", att_tx_handle);

    // USB HID device (keyboard)
    usb_hid_init();

    // Parser
    packet_parser_reset(&parser, text_buffer, TEXT_BUFFER_CAP);

    hci_power_control(HCI_POWER_ON);
    printf("Pico W Keyboard Injector ready\n");

    // Main loop: service CYW43 async context (BTstack BLE) and TinyUSB (USB HID).
    while (true) {
        async_context_poll(cyw43_arch_async_context());
        usb_hid_poll();
    }
}
