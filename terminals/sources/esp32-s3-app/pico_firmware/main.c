#include <ctype.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "pico/stdlib.h"
#include "pico/cyw43_arch.h"
#include "pico/time.h"
#include "hardware/sync.h"

#include "btstack.h"
#include "hid_keyboard.h"
#include "usb_hid.h"
#include "pico/btstack_flash_bank.h"
#include "btstack_tlv.h"
#include "btstack_tlv_flash_bank.h"
#include "hal_flash_bank.h"
#include "ble/le_device_db_tlv.h"

#define CMD_VALUE_HANDLE    ATT_CHARACTERISTIC_00001235_0000_1000_8000_00805F9B34FB_01_VALUE_HANDLE
#define NOTIFY_VALUE_HANDLE ATT_CHARACTERISTIC_00001236_0000_1000_8000_00805F9B34FB_01_VALUE_HANDLE
#define NOTIFY_CCCD_HANDLE  ATT_CHARACTERISTIC_00001236_0000_1000_8000_00805F9B34FB_01_CLIENT_CONFIGURATION_HANDLE

#define RX_BUFFER_SIZE 8192u
#define LINE_SIZE 1024u
#define NOTIFY_BUFFER_SIZE 64u

static hci_con_handle_t con_handle = HCI_CON_HANDLE_INVALID;
static bool notify_enabled = false;
static bool notify_pending = false;
static uint8_t notify_tx[NOTIFY_BUFFER_SIZE];
static uint16_t notify_tx_len = 0;

static uint8_t rx_buffer[RX_BUFFER_SIZE];
static volatile uint16_t rx_head = 0;
static volatile uint16_t rx_tail = 0;

static uint8_t line_buffer[LINE_SIZE + 1];
static uint16_t line_len = 0;
static bool line_overflow = false;

static uint8_t pending_line[LINE_SIZE + 1];
static uint16_t pending_len = 0;

static bool command_active = false;
static const char *command_result = "OK";

static btstack_packet_callback_registration_t hci_event_callback_registration;
static btstack_packet_callback_registration_t sm_event_callback_registration;

static const uint8_t adv_data[] = {
    0x02, BLUETOOTH_DATA_TYPE_FLAGS, 0x06,
    0x03, BLUETOOTH_DATA_TYPE_COMPLETE_LIST_OF_16_BIT_SERVICE_CLASS_UUIDS, 0x34, 0x12,
    0x12, BLUETOOTH_DATA_TYPE_COMPLETE_LOCAL_NAME,
    'P', 'i', 'c', 'o', ' ', 'H', 'I', 'D', ' ', 'K', 'e', 'y', 'b', 'o', 'a', 'r', 'd',
};

static void packet_handler(uint8_t packet_type, uint16_t channel, uint8_t *packet, uint16_t size);
static uint16_t att_read_callback(hci_con_handle_t handle, uint16_t att_handle, uint16_t offset,
                                 uint8_t *buffer, uint16_t buffer_size);
static int att_write_callback(hci_con_handle_t handle, uint16_t att_handle, uint16_t transaction_mode,
                              uint16_t offset, uint8_t *buffer, uint16_t buffer_size);

static void rx_push(const uint8_t *data, uint16_t len) {
    uint32_t flags = save_and_disable_interrupts();
    for (uint16_t i = 0; i < len; i++) {
        uint16_t next = (uint16_t)((rx_head + 1u) & (RX_BUFFER_SIZE - 1u));
        if (next != rx_tail) {
            rx_buffer[rx_head] = data[i];
            rx_head = next;
        }
    }
    restore_interrupts(flags);
}

static int rx_pop(void) {
    if (rx_head == rx_tail) return -1;
    uint8_t value = rx_buffer[rx_tail];
    rx_tail = (uint16_t)((rx_tail + 1u) & (RX_BUFFER_SIZE - 1u));
    return value;
}

static void send_response(const char *message) {
    size_t len = strlen(message);
    if (len > NOTIFY_BUFFER_SIZE - 1u) len = NOTIFY_BUFFER_SIZE - 1u;
    memcpy(notify_tx, message, len);
    notify_tx[len] = '\n';
    notify_tx_len = (uint16_t)(len + 1u);

    if (!notify_enabled || con_handle == HCI_CON_HANDLE_INVALID) return;

    uint8_t err = att_server_notify(con_handle, NOTIFY_VALUE_HANDLE, notify_tx, notify_tx_len);
    if (err != ERROR_CODE_SUCCESS) {
        notify_pending = true;
        att_server_request_can_send_now_event(con_handle);
    }
}

static void start_advertising(void) {
    uint16_t adv_int_min = 0x0030;
    uint16_t adv_int_max = 0x0030;
    uint8_t adv_type = 0;
    bd_addr_t null_addr;
    memset(null_addr, 0, sizeof(null_addr));

    gap_advertisements_set_params(adv_int_min, adv_int_max, adv_type, 0, null_addr, 0x07, 0x00);
    gap_advertisements_set_data(sizeof(adv_data), (uint8_t *)adv_data);
    gap_advertisements_enable(1);
}

static bool name_equals(const char *a, size_t a_len, const char *b) {
    size_t b_len = strlen(b);
    return a_len == b_len && strncasecmp(a, b, a_len) == 0;
}

static void start_command(uint8_t *data, uint16_t len) {
    if (command_active) return;

    data[len] = 0;
    char *msg = (char *)data;

    char *colon = strchr(msg, ':');
    const char *params = "";
    if (colon) {
        *colon = 0;
        params = colon + 1;
    }

    char *cmd = msg;
    while (*cmd == ' ') cmd++;
    for (char *p = cmd; *p; p++) *p = (char)toupper((uint8_t)*p);

    command_active = true;
    command_result = "OK";

    if (strcmp(cmd, "TEXT") == 0) {
        size_t text_len = strlen(params);
        if (text_len == 0 || !usb_hid_send_text((const uint8_t *)params, text_len)) {
            command_result = "ERR:TEXT_TOO_LARGE";
        }
    } else if (strcmp(cmd, "KEY") == 0) {
        uint8_t keycode = 0;
        if (usb_hid_keycode_from_name(params, strlen(params), &keycode)) {
            usb_hid_send_key(0, keycode);
        } else {
            command_result = "ERR:INVALID_KEY";
        }
    } else if (strcmp(cmd, "MOD") == 0) {
        char copy[160];
        strncpy(copy, params, sizeof(copy) - 1);
        copy[sizeof(copy) - 1] = 0;

        char *tokens[16];
        size_t count = 0;
        char *save = NULL;
        for (char *tok = strtok_r(copy, "+", &save); tok; tok = strtok_r(NULL, "+", &save)) {
            if (count < 16) tokens[count++] = tok;
        }

        if (count < 2) {
            command_result = "ERR:INVALID_MOD";
        } else {
            uint8_t modifiers = 0;
            bool ok = true;
            for (size_t i = 0; i + 1 < count; i++) {
                uint8_t one = 0;
                if (!usb_hid_modifier_from_name(tokens[i], strlen(tokens[i]), &one)) ok = false;
                modifiers |= one;
            }
            uint8_t keycode = 0;
            if (!ok || !usb_hid_keycode_from_name(tokens[count - 1], strlen(tokens[count - 1]), &keycode)) {
                command_result = "ERR:INVALID_MOD";
            } else {
                usb_hid_send_key(modifiers, keycode);
            }
        }
    } else if (strcmp(cmd, "UNI") == 0) {
        char *end = NULL;
        unsigned long codepoint = strtoul(params, &end, 0);
        if (end == params || *end != '\0' || codepoint > 0x10FFFFul) {
            command_result = "ERR:INVALID_CODEPOINT";
        } else {
            usb_hid_send_unicode((uint32_t)codepoint);
        }
    } else if (strcmp(cmd, "UMOD") == 0) {
        // Select Unicode input mode: 0=decimal, 1=hex, 2=GBK, 3=Alt+X
        char *end = NULL;
        long mode = strtol(params, &end, 10);
        if (end == params || *end != '\0' || !usb_hid_set_unicode_mode((int)mode)) {
            command_result = "ERR:INVALID_MODE";
        }
    } else if (strcmp(cmd, "SPEED") == 0) {
        // Typing speed: 1=slowest .. 10=fastest (default 5)
        char *end = NULL;
        long level = strtol(params, &end, 10);
        if (end == params || *end != '\0' || !usb_hid_set_speed((int)level)) {
            command_result = "ERR:INVALID_SPEED";
        }
    } else {
        command_result = "ERR:INVALID_CMD";
    }

    if (command_result[0] != 'O' || !usb_hid_busy()) {
        command_active = false;
        send_response(command_result);
    }
}

static void process_rx(void) {
    while (pending_len == 0) {
        int b = rx_pop();
        if (b < 0) break;

        if (b == '\n') {
            if (line_overflow) {
                send_response("ERR:OVERFLOW");
            } else if (line_len > 0) {
                if (command_active) {
                    memcpy(pending_line, line_buffer, line_len);
                    pending_len = line_len;
                } else {
                    start_command(line_buffer, line_len);
                }
            }
            line_len = 0;
            line_overflow = false;
        } else if (b != '\r') {
            if (line_len < LINE_SIZE) {
                line_buffer[line_len++] = (uint8_t)b;
            } else {
                line_overflow = true;
            }
        }
    }

    if (!command_active && pending_len > 0) {
        start_command(pending_line, pending_len);
        pending_len = 0;
    }

    if (command_active && !usb_hid_busy()) {
        command_active = false;
        send_response(usb_hid_result());
    }
}

static uint16_t att_read_callback(hci_con_handle_t handle, uint16_t att_handle, uint16_t offset,
                                 uint8_t *buffer, uint16_t buffer_size) {
    (void)handle;
    if (att_handle == NOTIFY_VALUE_HANDLE) {
        return att_read_callback_handle_blob(notify_tx, notify_tx_len, offset, buffer, buffer_size);
    }
    return 0;
}

static int att_write_callback(hci_con_handle_t handle, uint16_t att_handle, uint16_t transaction_mode,
                              uint16_t offset, uint8_t *buffer, uint16_t buffer_size) {
    (void)transaction_mode;
    (void)offset;

    if (att_handle == NOTIFY_CCCD_HANDLE) {
        notify_enabled = little_endian_read_16(buffer, 0) == GATT_CLIENT_CHARACTERISTICS_CONFIGURATION_NOTIFICATION;
        con_handle = handle;
        if (notify_enabled) send_response("STATUS:READY");
    } else if (att_handle == CMD_VALUE_HANDLE) {
        if (buffer_size > 0) rx_push(buffer, buffer_size);
    }
    return 0;
}

static void setup_tlv(void) {
    static btstack_tlv_flash_bank_t btstack_tlv_flash_bank_context;
    const hal_flash_bank_t *hal_flash_bank_impl = pico_flash_bank_instance();
    const btstack_tlv_t *btstack_tlv_impl = btstack_tlv_flash_bank_init_instance(
            &btstack_tlv_flash_bank_context,
            hal_flash_bank_impl,
            NULL);
    btstack_tlv_set_instance(btstack_tlv_impl, &btstack_tlv_flash_bank_context);
    le_device_db_tlv_configure(btstack_tlv_impl, &btstack_tlv_flash_bank_context);
}

static void packet_handler(uint8_t packet_type, uint16_t channel, uint8_t *packet, uint16_t size) {
    (void)channel;
    (void)size;

    if (packet_type != HCI_EVENT_PACKET) return;

    switch (hci_event_packet_get_type(packet)) {
        case BTSTACK_EVENT_STATE:
            if (btstack_event_state_get_state(packet) == HCI_STATE_WORKING) {
                start_advertising();
            }
            break;
        case HCI_EVENT_DISCONNECTION_COMPLETE:
            notify_enabled = false;
            notify_pending = false;
            con_handle = HCI_CON_HANDLE_INVALID;
            break;
        case SM_EVENT_JUST_WORKS_REQUEST:
            sm_just_works_confirm(sm_event_just_works_request_get_handle(packet));
            break;
        case ATT_EVENT_CAN_SEND_NOW:
            if (notify_pending) {
                notify_pending = false;
                att_server_notify(con_handle, NOTIFY_VALUE_HANDLE, notify_tx, notify_tx_len);
            }
            break;
        default:
            break;
    }
}

static void led_task(void) {
    static absolute_time_t next = 0;
    static bool led_on = false;
    if (!time_reached(next)) return;

    led_on = (con_handle != HCI_CON_HANDLE_INVALID) || !led_on;
    cyw43_arch_gpio_put(CYW43_WL_GPIO_LED_PIN, led_on);
    next = make_timeout_time_ms(con_handle != HCI_CON_HANDLE_INVALID ? 1000u : 250u);
}

int main(void) {
    usb_hid_early_init();

    if (cyw43_arch_init()) return -1;
    cyw43_arch_gpio_put(CYW43_WL_GPIO_LED_PIN, 0);

    usb_hid_init();

    l2cap_init();
    sm_init();
    // Just Works pairing without PIN; allow bonding so Android can pair successfully.
    sm_set_io_capabilities(IO_CAPABILITY_NO_INPUT_NO_OUTPUT);
    sm_set_authentication_requirements(SM_AUTHREQ_BONDING);
    setup_tlv();
    att_server_init(profile_data, att_read_callback, att_write_callback);

    hci_event_callback_registration.callback = &packet_handler;
    hci_add_event_handler(&hci_event_callback_registration);
    sm_event_callback_registration.callback = &packet_handler;
    sm_add_event_handler(&sm_event_callback_registration);
    att_server_register_packet_handler(packet_handler);

    hci_power_control(HCI_POWER_ON);

    while (true) {
        usb_hid_task();
        async_context_poll(cyw43_arch_async_context());
        process_rx();
        led_task();
        async_context_wait_for_work_until(cyw43_arch_async_context(), make_timeout_time_ms(1u));
    }
}
