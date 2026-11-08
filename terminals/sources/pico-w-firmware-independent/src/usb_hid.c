#include "tusb.h"
#include "usb_hid.h"
#include "hardware/timer.h"

// ---------------------------------------------------------------------------
// USB descriptors
// ---------------------------------------------------------------------------

enum {
    STRID_LANGID = 0,
    STRID_MANUFACTURER,
    STRID_PRODUCT,
    STRID_SERIAL,
};

static tusb_desc_device_t const desc_device = {
    .bLength = sizeof(tusb_desc_device_t),
    .bDescriptorType = TUSB_DESC_DEVICE,
    .bcdUSB = 0x0200,
    .bDeviceClass = 0x00,
    .bDeviceSubClass = 0x00,
    .bDeviceProtocol = 0x00,
    .bMaxPacketSize0 = CFG_TUD_ENDPOINT0_SIZE,
    .idVendor = 0xCafe,
    .idProduct = 0x0001,
    .bcdDevice = 0x0100,
    .iManufacturer = STRID_MANUFACTURER,
    .iProduct = STRID_PRODUCT,
    .iSerialNumber = STRID_SERIAL,
    .bNumConfigurations = 1,
};

#define EPNUM_HID 0x81

static uint8_t const desc_hid_report[] = {
    0x05, 0x01, // Usage Page (Generic Desktop)
    0x09, 0x06, // Usage (Keyboard)
    0xA1, 0x01, // Collection (Application)
    0x05, 0x07, //   Usage Page (Keyboard)
    0x19, 0xE0, //   Usage Minimum (0xE0)
    0x29, 0xE7, //   Usage Maximum (0xE7)
    0x15, 0x00, //   Logical Minimum (0)
    0x25, 0x01, //   Logical Maximum (1)
    0x75, 0x01, //   Report Size (1)
    0x95, 0x08, //   Report Count (8)
    0x81, 0x02, //   Input (Data, Variable, Absolute) -> modifier byte
    0x95, 0x01, //   Report Count (1)
    0x75, 0x08, //   Report Size (8)
    0x81, 0x01, //   Input (Constant) -> reserved byte
    0x95, 0x06, //   Report Count (6)
    0x75, 0x08, //   Report Size (8)
    0x15, 0x00, //   Logical Minimum (0)
    0x25, 0x65, //   Logical Maximum (101)
    0x05, 0x07, //   Usage Page (Keyboard)
    0x19, 0x00, //   Usage Minimum (0)
    0x29, 0x65, //   Usage Maximum (101)
    0x81, 0x00, //   Input (Data, Array) -> keycodes
    0xC0,       // End Collection
};

static uint8_t const desc_configuration[] = {
    // Configuration descriptor
    TUD_CONFIG_DESCRIPTOR(1, 1, 0, TUD_CONFIG_DESC_LEN + TUD_HID_DESC_LEN, 0x00, 100),
    // Interface 0, HID keyboard (boot protocol supported)
    TUD_HID_DESCRIPTOR(0, 0, HID_ITF_PROTOCOL_KEYBOARD, sizeof(desc_hid_report), EPNUM_HID,
                       CFG_TUD_HID_EP_BUFSIZE, 10),
};

// ---------------------------------------------------------------------------
// ASCII -> HID keycode map
// ---------------------------------------------------------------------------

typedef struct {
    uint8_t keycode;
    uint8_t modifier; // 0 or KEYBOARD_MODIFIER_LEFTSHIFT
} ascii_map_t;

#define K_CTRL 0x01
#define K_SHIFT 0x02
#define K_ALT 0x04
#define K_GUI 0x08

// Map for ASCII 0x00..0x7F. Unsupported chars have keycode 0.
static const ascii_map_t ascii_map[128] = {
    // 0x00 - 0x07
    {0,0}, {0,0}, {0,0}, {0,0}, {0,0}, {0,0}, {0,0}, {0,0},
    // 0x08 backspace, 0x09 tab, 0x0A LF, 0x0B, 0x0C, 0x0D CR, 0x0E, 0x0F
    {0x2A,0}, {0x2B,0}, {0x28,0}, {0,0}, {0,0}, {0x28,0}, {0,0}, {0,0},
    // 0x10 - 0x17
    {0,0}, {0,0}, {0,0}, {0,0}, {0,0}, {0,0}, {0,0}, {0,0},
    // 0x18 - 0x1F
    {0,0}, {0,0}, {0,0}, {0,0}, {0,0}, {0,0}, {0,0}, {0,0},
    // 0x20 space, 0x21 !, 0x22 ", 0x23 #, 0x24 $, 0x25 %, 0x26 &, 0x27 '
    {0x2C,0}, {0x1E,K_SHIFT}, {0x34,K_SHIFT}, {0x20,K_SHIFT}, {0x21,K_SHIFT},
    {0x22,K_SHIFT}, {0x24,K_SHIFT}, {0x34,0},
    // 0x28 (, 0x29 ), 0x2A *, 0x2B +, 0x2C ,, 0x2D -, 0x2E ., 0x2F /
    {0x26,K_SHIFT}, {0x27,K_SHIFT}, {0x25,K_SHIFT}, {0x2E,K_SHIFT},
    {0x36,0}, {0x2D,0}, {0x37,0}, {0x38,0},
    // 0x30 0 - 0x39 9
    {0x27,0}, {0x1E,0}, {0x1F,0}, {0x20,0}, {0x21,0}, {0x22,0},
    {0x23,0}, {0x24,0}, {0x25,0}, {0x26,0},
    // 0x3A :, 0x3B ;, 0x3C <, 0x3D =, 0x3E >, 0x3F ?
    {0x33,K_SHIFT}, {0x33,0}, {0x36,K_SHIFT}, {0x2E,0}, {0x37,K_SHIFT}, {0x38,K_SHIFT},
    // 0x40 @, 0x41 A - 0x5A Z
    {0x1F,K_SHIFT},
    {0x04,K_SHIFT},{0x05,K_SHIFT},{0x06,K_SHIFT},{0x07,K_SHIFT},{0x08,K_SHIFT},
    {0x09,K_SHIFT},{0x0A,K_SHIFT},{0x0B,K_SHIFT},{0x0C,K_SHIFT},{0x0D,K_SHIFT},
    {0x0E,K_SHIFT},{0x0F,K_SHIFT},{0x10,K_SHIFT},{0x11,K_SHIFT},{0x12,K_SHIFT},
    {0x13,K_SHIFT},{0x14,K_SHIFT},{0x15,K_SHIFT},{0x16,K_SHIFT},{0x17,K_SHIFT},
    {0x18,K_SHIFT},{0x19,K_SHIFT},{0x1A,K_SHIFT},{0x1B,K_SHIFT},{0x1C,K_SHIFT},
    {0x1D,K_SHIFT},
    // 0x5B [, 0x5C \, 0x5D ], 0x5E ^, 0x5F _, 0x60 `
    {0x2F,0}, {0x31,0}, {0x30,0}, {0x23,K_SHIFT}, {0x2D,K_SHIFT}, {0x35,0},
    // 0x61 a - 0x7A z
    {0x04,0},{0x05,0},{0x06,0},{0x07,0},{0x08,0},{0x09,0},{0x0A,0},{0x0B,0},
    {0x0C,0},{0x0D,0},{0x0E,0},{0x0F,0},{0x10,0},{0x11,0},{0x12,0},{0x13,0},
    {0x14,0},{0x15,0},{0x16,0},{0x17,0},{0x18,0},{0x19,0},{0x1A,0},{0x1B,0},
    {0x1C,0},{0x1D,0},
    // 0x7B {, 0x7C |, 0x7D }, 0x7E ~, 0x7F DEL
    {0x2F,K_SHIFT}, {0x31,K_SHIFT}, {0x30,K_SHIFT}, {0x35,K_SHIFT}, {0x4C,0},
};

static void send_press_release(uint8_t modifier, uint8_t keycode) {
    uint8_t report[6] = {keycode, 0, 0, 0, 0, 0};
    if (!tud_hid_ready()) return;
    tud_hid_keyboard_report(0, modifier, report);
    // Give the host time to see the press.
    sleep_us(5000);
    if (!tud_hid_ready()) return;
    tud_hid_keyboard_report(0, 0, report); // release with empty modifier
    uint8_t empty[6] = {0, 0, 0, 0, 0, 0};
    if (!tud_hid_ready()) return;
    tud_hid_keyboard_report(0, 0, empty);
    sleep_us(5000);
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

void usb_hid_init(void) {
    tusb_init();
}

void usb_hid_poll(void) {
    tud_task();
}

bool usb_hid_ready(void) {
    return tud_hid_ready();
}

uint32_t usb_hid_inject_text(const uint8_t *text, uint32_t len) {
    if (text == NULL) return 0;
    uint32_t typed = 0;
    uint32_t i = 0;
    while (i < len) {
        const uint8_t c = text[i];
        // Skip multi-byte UTF-8 sequences (non-ASCII) for now.
        if (c >= 0x80) {
            uint32_t extra;
            if ((c & 0xE0) == 0xC0) extra = 1;
            else if ((c & 0xF0) == 0xE0) extra = 2;
            else if ((c & 0xF8) == 0xF0) extra = 3;
            else extra = 0;
            i += 1 + extra;
            continue;
        }
        const ascii_map_t *m = &ascii_map[c];
        if (m->keycode != 0) {
            send_press_release(m->modifier, m->keycode);
            typed++;
        }
        i++;
    }
    return typed;
}

// ---------------------------------------------------------------------------
// TinyUSB callbacks
// ---------------------------------------------------------------------------

uint8_t const *tud_descriptor_device_cb(void) {
    return (uint8_t const *)&desc_device;
}

uint8_t const *tud_hid_descriptor_report_cb(uint8_t instance) {
    (void)instance;
    return desc_hid_report;
}

uint8_t const *tud_descriptor_configuration_cb(uint8_t index) {
    (void)index;
    return desc_configuration;
}

static uint16_t _desc_str[32 + 1];

uint16_t const *tud_descriptor_string_cb(uint8_t index, uint16_t langid) {
    (void)langid;
    uint8_t chr_count = 0;

    switch (index) {
    case STRID_LANGID:
        _desc_str[1] = 0x0409; // English (US)
        chr_count = 1;
        break;
    case STRID_MANUFACTURER:
        memcpy(&_desc_str[1], "Pico W", 6 * 2);
        chr_count = 6;
        break;
    case STRID_PRODUCT:
        memcpy(&_desc_str[1], "Pico W Keyboard Injector", 24 * 2);
        chr_count = 24;
        break;
    case STRID_SERIAL:
        memcpy(&_desc_str[1], "20260819", 8 * 2);
        chr_count = 8;
        break;
    default:
        return NULL;
    }

    _desc_str[0] = (uint16_t)((TUSB_DESC_STRING << 8) | (2 * chr_count + 2));
    return _desc_str;
}

// LED output reports from the host (caps/scroll/num lock) - unused.
void tud_hid_set_report_cb(uint8_t instance, uint8_t report_id,
                           hid_report_type_t report_type, uint8_t const *buffer,
                           uint16_t bufsize) {
    (void)instance; (void)report_id; (void)report_type; (void)buffer; (void)bufsize;
}

uint16_t tud_hid_get_report_cb(uint8_t instance, uint8_t report_id,
                               hid_report_type_t report_type, uint8_t *buffer,
                               uint16_t reqlen) {
    (void)instance; (void)report_id; (void)report_type; (void)buffer; (void)reqlen;
    return 0;
}
