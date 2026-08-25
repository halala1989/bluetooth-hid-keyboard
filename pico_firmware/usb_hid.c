#include "usb_hid.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "bsp/board_api.h"
#include "tusb.h"

#include "gbk_table.h"

enum {
    KEY_A = 0x04, KEY_Z = 0x1D,
    KEY_1 = 0x1E, KEY_9 = 0x26, KEY_0 = 0x27,
    KEY_X = 0x1B,
    KEY_RETURN = 0x28, KEY_ESCAPE = 0x29, KEY_BACKSPACE = 0x2A, KEY_TAB = 0x2B,
    KEY_SPACE = 0x2C, KEY_MINUS = 0x2D, KEY_EQUAL = 0x2E,
    KEY_LBRACKET = 0x2F, KEY_RBRACKET = 0x30, KEY_BACKSLASH = 0x31,
    KEY_SEMICOLON = 0x33, KEY_APOSTROPHE = 0x34, KEY_GRAVE = 0x35,
    KEY_COMMA = 0x36, KEY_PERIOD = 0x37, KEY_SLASH = 0x38,
    KEY_CAPS_LOCK = 0x39,
    KEY_F1 = 0x3A, KEY_F12 = 0x45,
    KEY_PRINT_SCREEN = 0x46, KEY_SCROLL_LOCK = 0x47, KEY_PAUSE = 0x48,
    KEY_INSERT = 0x49, KEY_HOME = 0x4A, KEY_PAGE_UP = 0x4B,
    KEY_DELETE = 0x4C, KEY_END = 0x4D, KEY_PAGE_DOWN = 0x4E,
    KEY_RIGHT = 0x4F, KEY_LEFT = 0x50, KEY_DOWN = 0x51, KEY_UP = 0x52,
    KEY_NUM_LOCK = 0x53,
    KEY_KP_PLUS = 0x57,
    KEY_KP_1 = 0x59, KEY_KP_2 = 0x5A, KEY_KP_3 = 0x5B,
    KEY_KP_4 = 0x5C, KEY_KP_5 = 0x5D, KEY_KP_6 = 0x5E,
    KEY_KP_7 = 0x5F, KEY_KP_8 = 0x60, KEY_KP_9 = 0x61,
    KEY_KP_0 = 0x62,
    KEY_MENU = 0x65,
};

/* HID keypad usage ids are NOT contiguous, so use an explicit table.
   Windows only treats *numpad* key usages as Alt-code entry; top-row
   digits would act as ordinary Alt shortcuts and pop menus. */
static const uint8_t numpad_digit_keys[10] = {
    KEY_KP_0, KEY_KP_1, KEY_KP_2, KEY_KP_3, KEY_KP_4,
    KEY_KP_5, KEY_KP_6, KEY_KP_7, KEY_KP_8, KEY_KP_9,
};

#define KEY_DOWN_MS 15u
#define KEY_UP_MS 15u
#define CHAR_GAP_MS 8u
#define ALT_DOWN_MS 40u
#define ALT_FINAL_MS 80u
#define TEXT_BUFFER_SIZE 1024u

typedef enum {
    HID_IDLE = 0,
    HID_SEND_REPORT,
    HID_SIMPLE_RELEASE,
    HID_CHAR_RELEASE,
    HID_UNICODE_PLUS_PRESS,
    HID_UNICODE_PLUS_RELEASE,
    HID_UNICODE_DIGIT_PRESS,
    HID_UNICODE_DIGIT_RELEASE,
    HID_UNICODE_FINISH,
    HID_ALTX_DIGIT_PRESS,
    HID_ALTX_DIGIT_RELEASE,
    HID_ALTX_X_PRESS,
    HID_ALTX_X_RELEASE,
} hid_state_t;

typedef enum {
    OP_NONE = 0,
    OP_KEY,
    OP_UNICODE,
    OP_TEXT,
} hid_op_t;

static hid_state_t state = HID_IDLE;
static hid_state_t next_state;
static hid_op_t op = OP_NONE;
static const char *completion_result = "OK";
static uint8_t pending_report[8];
static uint32_t report_delay_ms;
static absolute_time_t next_action;

static uint8_t op_modifier;
static uint8_t op_key;
static uint32_t op_codepoint;
static uint8_t text_buffer[TEXT_BUFFER_SIZE];
static uint16_t text_len;
static uint16_t text_pos;

typedef enum {
    UNICODE_MODE_DECIMAL = 0, /* Alt + 0 + decimal Unicode codepoint on numpad (RichEdit apps) */
    UNICODE_MODE_HEX     = 1, /* Alt + Numpad+ + hex (needs EnableHexNumpad registry + NumLock ON; NOT in Win11 Notepad) */
    UNICODE_MODE_GBK     = 2, /* Alt + decimal GBK machine code on numpad (Chinese Windows only) */
    UNICODE_MODE_ALTX    = 3, /* type hex code then Alt+X (Win11 Notepad/Word/WordPad/OneNote; no registry, no NumLock) */
} unicode_mode_t;

static unicode_mode_t unicode_mode = UNICODE_MODE_ALTX;
static char unicode_digits[12];
static uint8_t unicode_len;
static uint8_t unicode_index;

static uint8_t keyboard_leds = 0;
static bool mounted = false;

static void queue_report(uint8_t modifier, uint8_t keycode, uint32_t delay_ms, hid_state_t after) {
    memset(pending_report, 0, sizeof(pending_report));
    pending_report[0] = modifier;
    pending_report[2] = keycode;
    report_delay_ms = delay_ms;
    next_state = after;
    state = HID_SEND_REPORT;
    next_action = get_absolute_time();
}

static bool ascii_to_hid(char ch, uint8_t *modifier, uint8_t *keycode) {
    bool caps_lock = (keyboard_leds & 0x02u) != 0;
    *modifier = 0;

    if (ch >= 'a' && ch <= 'z') {
        *keycode = (uint8_t)(KEY_A + (ch - 'a'));
        if (caps_lock) *modifier = KEYBOARD_MODIFIER_LEFTSHIFT;
        return true;
    }
    if (ch >= 'A' && ch <= 'Z') {
        *keycode = (uint8_t)(KEY_A + (ch - 'A'));
        if (!caps_lock) *modifier = KEYBOARD_MODIFIER_LEFTSHIFT;
        return true;
    }
    if (ch >= '1' && ch <= '9') {
        *keycode = (uint8_t)(KEY_1 + (ch - '1'));
        return true;
    }

    switch (ch) {
        case '0': *keycode = KEY_0; return true;
        case ' ': *keycode = KEY_SPACE; return true;
        case '-': *keycode = KEY_MINUS; return true;
        case '=': *keycode = KEY_EQUAL; return true;
        case '[': *keycode = KEY_LBRACKET; return true;
        case ']': *keycode = KEY_RBRACKET; return true;
        case '\\': *keycode = KEY_BACKSLASH; return true;
        case ';': *keycode = KEY_SEMICOLON; return true;
        case '\'': *keycode = KEY_APOSTROPHE; return true;
        case '`': *keycode = KEY_GRAVE; return true;
        case ',': *keycode = KEY_COMMA; return true;
        case '.': *keycode = KEY_PERIOD; return true;
        case '/': *keycode = KEY_SLASH; return true;
        case '\t': *keycode = KEY_TAB; return true;
        case '\b': *keycode = KEY_BACKSPACE; return true;

        case '!': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_1; return true;
        case '@': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_1 + 1; return true;
        case '#': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_1 + 2; return true;
        case '$': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_1 + 3; return true;
        case '%': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_1 + 4; return true;
        case '^': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_1 + 5; return true;
        case '&': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_1 + 6; return true;
        case '*': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_1 + 7; return true;
        case '(': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_1 + 8; return true;
        case ')': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_0; return true;
        case '_': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_MINUS; return true;
        case '+': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_EQUAL; return true;
        case '{': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_LBRACKET; return true;
        case '}': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_RBRACKET; return true;
        case '|': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_BACKSLASH; return true;
        case ':': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_SEMICOLON; return true;
        case '"': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_APOSTROPHE; return true;
        case '~': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_GRAVE; return true;
        case '<': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_COMMA; return true;
        case '>': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_PERIOD; return true;
        case '?': *modifier = KEYBOARD_MODIFIER_LEFTSHIFT; *keycode = KEY_SLASH; return true;
        default: return false;
    }
}

static bool name_equals(const char *a, size_t a_len, const char *b) {
    size_t b_len = strlen(b);
    return a_len == b_len && strncasecmp(a, b, a_len) == 0;
}

bool usb_hid_keycode_from_name(const char *name, size_t len, uint8_t *keycode) {
    if (len == 1) {
        char ch = (char)toupper((unsigned char)name[0]);
        if (ch >= 'A' && ch <= 'Z') {
            *keycode = (uint8_t)(KEY_A + (ch - 'A'));
            return true;
        }
        if (ch >= '1' && ch <= '9') {
            *keycode = (uint8_t)(KEY_1 + (ch - '1'));
            return true;
        }
        if (ch == '0') {
            *keycode = KEY_0;
            return true;
        }
    }

    struct { const char *name; uint8_t code; } table[] = {
        {"ENTER", KEY_RETURN}, {"RETURN", KEY_RETURN}, {"ESC", KEY_ESCAPE}, {"ESCAPE", KEY_ESCAPE},
        {"BACKSPACE", KEY_BACKSPACE}, {"BACK", KEY_BACKSPACE}, {"TAB", KEY_TAB}, {"SPACE", KEY_SPACE},
        {"CAPSLOCK", KEY_CAPS_LOCK}, {"NUMLOCK", KEY_NUM_LOCK}, {"SCROLLLOCK", KEY_SCROLL_LOCK},
        {"PRINTSCREEN", KEY_PRINT_SCREEN}, {"PAUSE", KEY_PAUSE}, {"INSERT", KEY_INSERT},
        {"HOME", KEY_HOME}, {"PAGEUP", KEY_PAGE_UP}, {"PAGEDOWN", KEY_PAGE_DOWN},
        {"DELETE", KEY_DELETE}, {"DEL", KEY_DELETE}, {"END", KEY_END},
        {"UP", KEY_UP}, {"DOWN", KEY_DOWN}, {"LEFT", KEY_LEFT}, {"RIGHT", KEY_RIGHT},
        {"MENU", KEY_MENU}, {"APP", KEY_MENU},
        {"F1", KEY_F1}, {"F2", KEY_F1 + 1}, {"F3", KEY_F1 + 2}, {"F4", KEY_F1 + 3},
        {"F5", KEY_F1 + 4}, {"F6", KEY_F1 + 5}, {"F7", KEY_F1 + 6}, {"F8", KEY_F1 + 7},
        {"F9", KEY_F1 + 8}, {"F10", KEY_F1 + 9}, {"F11", KEY_F1 + 10}, {"F12", KEY_F1 + 11},
    };

    for (size_t i = 0; i < sizeof(table) / sizeof(table[0]); i++) {
        if (name_equals(name, len, table[i].name)) {
            *keycode = table[i].code;
            return true;
        }
    }
    return false;
}

bool usb_hid_modifier_from_name(const char *name, size_t len, uint8_t *modifier) {
    struct { const char *name; uint8_t mask; } table[] = {
        {"CTRL", KEYBOARD_MODIFIER_LEFTCTRL}, {"CONTROL", KEYBOARD_MODIFIER_LEFTCTRL},
        {"LEFTCTRL", KEYBOARD_MODIFIER_LEFTCTRL}, {"RIGHTCTRL", KEYBOARD_MODIFIER_RIGHTCTRL},
        {"SHIFT", KEYBOARD_MODIFIER_LEFTSHIFT}, {"LEFTSHIFT", KEYBOARD_MODIFIER_LEFTSHIFT},
        {"RIGHTSHIFT", KEYBOARD_MODIFIER_RIGHTSHIFT},
        {"ALT", KEYBOARD_MODIFIER_LEFTALT}, {"LEFTALT", KEYBOARD_MODIFIER_LEFTALT},
        {"RIGHTALT", KEYBOARD_MODIFIER_RIGHTALT},
        {"GUI", KEYBOARD_MODIFIER_LEFTGUI}, {"WIN", KEYBOARD_MODIFIER_LEFTGUI},
        {"WINDOWS", KEYBOARD_MODIFIER_LEFTGUI}, {"LEFTGUI", KEYBOARD_MODIFIER_LEFTGUI},
        {"RIGHTGUI", KEYBOARD_MODIFIER_RIGHTGUI},
    };

    for (size_t i = 0; i < sizeof(table) / sizeof(table[0]); i++) {
        if (name_equals(name, len, table[i].name)) {
            *modifier = table[i].mask;
            return true;
        }
    }
    return false;
}

static bool utf8_decode(const uint8_t *s, size_t len, size_t *consumed, uint32_t *codepoint) {
    if (len == 0) return false;
    uint8_t first = s[0];

    if (first < 0x80) {
        *codepoint = first;
        *consumed = 1;
        return true;
    }

    size_t extra;
    uint32_t value;
    if ((first & 0xE0u) == 0xC0u) { extra = 1; value = first & 0x1Fu; }
    else if ((first & 0xF0u) == 0xE0u) { extra = 2; value = first & 0x0Fu; }
    else if ((first & 0xF8u) == 0xF0u) { extra = 3; value = first & 0x07u; }
    else return false;

    if (len <= extra) return false;
    for (size_t i = 1; i <= extra; i++) {
        if ((s[i] & 0xC0u) != 0x80u) return false;
        value = (value << 6) | (s[i] & 0x3Fu);
    }

    *codepoint = value;
    *consumed = extra + 1u;
    return true;
}

static void begin_unicode_sequence(void);

static void start_unicode(uint32_t codepoint) {
    int n;
    if (unicode_mode == UNICODE_MODE_HEX || unicode_mode == UNICODE_MODE_ALTX) {
        /* HEX: Alt + Numpad+ + hex digits (EnableHexNumpad method).
           ALTX: plain hex code text followed by Alt+X. */
        n = snprintf(unicode_digits, sizeof(unicode_digits), "%X", codepoint);
    } else if (unicode_mode == UNICODE_MODE_GBK) {
        uint16_t gbk = gbk_from_unicode(codepoint);
        n = (gbk != 0xFFFFu)
            ? snprintf(unicode_digits, sizeof(unicode_digits), "%u", (unsigned)gbk)
            : snprintf(unicode_digits, sizeof(unicode_digits), "0%u", codepoint);
    } else {
        /* Decimal Unicode needs a leading '0' (Alt+0+decimal), otherwise
           Windows wraps values > 255 into the ANSI code page (mod 256). */
        n = snprintf(unicode_digits, sizeof(unicode_digits), "0%u", codepoint);
    }

    if (n <= 0 || n >= (int)sizeof(unicode_digits)) {
        completion_result = "ERR:INVALID_CODEPOINT";
        op = OP_NONE;
        state = HID_IDLE;
        return;
    }
    unicode_len = (uint8_t)n;
    unicode_index = 0;
    begin_unicode_sequence();
}

static void begin_unicode_sequence(void) {
    if (unicode_mode == UNICODE_MODE_ALTX) {
        /* Type the hex code as plain characters, then Alt+X converts it
           (Win11 Notepad / Word / WordPad / OneNote / Outlook). */
        queue_report(0, 0, CHAR_GAP_MS, HID_ALTX_DIGIT_PRESS);
        return;
    }
    /* Hold Left Alt, then type the digits on the NUMpad. Windows consumes
       numpad digits while Alt is held as an Alt-code; top-row keys would be
       treated as ordinary Alt shortcuts (e.g. Alt+E opens the Edit menu). */
    if (unicode_mode == UNICODE_MODE_HEX) {
        queue_report(KEYBOARD_MODIFIER_LEFTALT, 0, ALT_DOWN_MS, HID_UNICODE_PLUS_PRESS);
    } else {
        queue_report(KEYBOARD_MODIFIER_LEFTALT, 0, ALT_DOWN_MS, HID_UNICODE_DIGIT_PRESS);
    }
}

static void start_next_action(void) {
    if (op == OP_KEY) {
        op = OP_NONE;
        queue_report(op_modifier, op_key, KEY_DOWN_MS, HID_SIMPLE_RELEASE);
        return;
    }

    if (op == OP_UNICODE) {
        op = OP_NONE;
        start_unicode(op_codepoint);
        return;
    }

    if (op == OP_TEXT) {
        if (text_pos >= text_len) {
            op = OP_NONE;
            state = HID_IDLE;
            return;
        }

        size_t consumed;
        uint32_t codepoint;
        if (!utf8_decode(text_buffer + text_pos, text_len - text_pos, &consumed, &codepoint)) {
            completion_result = "ERR:INVALID_UTF8";
            op = OP_NONE;
            state = HID_IDLE;
            return;
        }
        text_pos = (uint16_t)(text_pos + consumed);

        if (codepoint == '\r') return;
        if (codepoint == '\n') {
            queue_report(0, KEY_RETURN, KEY_DOWN_MS, HID_CHAR_RELEASE);
            return;
        }

        if (codepoint < 0x80u) {
            uint8_t mod = 0, key = 0;
            if (ascii_to_hid((char)codepoint, &mod, &key)) {
                queue_report(mod, key, KEY_DOWN_MS, HID_CHAR_RELEASE);
            } else {
                op_codepoint = codepoint;
                start_unicode(codepoint);
            }
        } else {
            start_unicode(codepoint);
        }
        return;
    }

    state = HID_IDLE;
}

static void hid_task_internal(void) {
    if (state == HID_IDLE) {
        if (op != OP_NONE) start_next_action();
        return;
    }

    if (!time_reached(next_action)) return;

    switch (state) {
        case HID_SEND_REPORT:
            if (!tud_hid_ready()) return;
            tud_hid_keyboard_report(0, pending_report[0], &pending_report[2]);
            state = next_state;
            next_action = make_timeout_time_ms(report_delay_ms);
            break;
        case HID_SIMPLE_RELEASE:
            queue_report(0, 0, KEY_UP_MS, HID_IDLE);
            break;
        case HID_CHAR_RELEASE:
            queue_report(0, 0, CHAR_GAP_MS, HID_IDLE);
            break;
        case HID_UNICODE_PLUS_PRESS:
            queue_report(KEYBOARD_MODIFIER_LEFTALT, KEY_KP_PLUS, KEY_DOWN_MS, HID_UNICODE_PLUS_RELEASE);
            break;
        case HID_UNICODE_PLUS_RELEASE:
            queue_report(KEYBOARD_MODIFIER_LEFTALT, 0, KEY_UP_MS, HID_UNICODE_DIGIT_PRESS);
            break;
        case HID_UNICODE_DIGIT_PRESS: {
            char ch = unicode_digits[unicode_index];
            uint8_t key;
            if (ch >= '0' && ch <= '9') {
                key = numpad_digit_keys[(uint8_t)(ch - '0')];
            } else if (ch >= 'A' && ch <= 'F') {
                key = (uint8_t)(KEY_A + (ch - 'A'));
            } else if (ch >= 'a' && ch <= 'f') {
                key = (uint8_t)(KEY_A + (ch - 'a'));
            } else {
                completion_result = "ERR:INVALID_CODEPOINT";
                state = HID_IDLE;
                break;
            }
            queue_report(KEYBOARD_MODIFIER_LEFTALT, key, KEY_DOWN_MS, HID_UNICODE_DIGIT_RELEASE);
            break;
        }
        case HID_UNICODE_DIGIT_RELEASE:
            if (unicode_index + 1u < unicode_len) {
                queue_report(KEYBOARD_MODIFIER_LEFTALT, 0, KEY_UP_MS, HID_UNICODE_DIGIT_PRESS);
                unicode_index++;
            } else {
                queue_report(KEYBOARD_MODIFIER_LEFTALT, 0, KEY_UP_MS, HID_UNICODE_FINISH);
            }
            break;
        case HID_UNICODE_FINISH:
            queue_report(0, 0, ALT_FINAL_MS, HID_IDLE);
            break;
        case HID_ALTX_DIGIT_PRESS: {
            char ch = unicode_digits[unicode_index];
            uint8_t mod = 0, key = 0;
            if (!ascii_to_hid(ch, &mod, &key)) {
                completion_result = "ERR:INVALID_CODEPOINT";
                state = HID_IDLE;
                break;
            }
            queue_report(mod, key, KEY_DOWN_MS, HID_ALTX_DIGIT_RELEASE);
            break;
        }
        case HID_ALTX_DIGIT_RELEASE:
            if (unicode_index + 1u < unicode_len) {
                queue_report(0, 0, CHAR_GAP_MS, HID_ALTX_DIGIT_PRESS);
                unicode_index++;
            } else {
                queue_report(0, 0, CHAR_GAP_MS, HID_ALTX_X_PRESS);
            }
            break;
        case HID_ALTX_X_PRESS:
            queue_report(KEYBOARD_MODIFIER_LEFTALT, KEY_X, KEY_DOWN_MS, HID_ALTX_X_RELEASE);
            break;
        case HID_ALTX_X_RELEASE:
            queue_report(0, 0, ALT_FINAL_MS, HID_IDLE);
            break;
        default:
            state = HID_IDLE;
            break;
    }
}

void usb_hid_early_init(void) {
    board_init();
}

void usb_hid_init(void) {
    const tusb_rhport_init_t rh_init = {
        .role = TUSB_ROLE_DEVICE,
        .speed = TUSB_SPEED_FULL,
    };
    tud_rhport_init(BOARD_TUD_RHPORT, &rh_init);
    board_init_after_tusb();
}

void usb_hid_task(void) {
    tud_task();
    hid_task_internal();
}

bool usb_hid_mounted(void) {
    return mounted;
}

bool usb_hid_busy(void) {
    return op != OP_NONE || state != HID_IDLE;
}

const char *usb_hid_result(void) {
    return completion_result;
}

void usb_hid_send_key(uint8_t modifiers, uint8_t keycode) {
    if (usb_hid_busy()) return;
    op = OP_KEY;
    completion_result = "OK";
    op_modifier = modifiers;
    op_key = keycode;
    state = HID_IDLE;
}

void usb_hid_send_unicode(uint32_t codepoint) {
    if (usb_hid_busy()) return;
    op = OP_UNICODE;
    completion_result = "OK";
    op_codepoint = codepoint;
    state = HID_IDLE;
}

bool usb_hid_set_unicode_mode(int mode) {
    if (usb_hid_busy()) return false;
    if (mode < (int)UNICODE_MODE_DECIMAL || mode > (int)UNICODE_MODE_ALTX) return false;
    unicode_mode = (unicode_mode_t)mode;
    return true;
}

bool usb_hid_send_text(const uint8_t *utf8, size_t len) {
    if (usb_hid_busy() || len == 0 || len > TEXT_BUFFER_SIZE) return false;
    memcpy(text_buffer, utf8, len);
    text_len = (uint16_t)len;
    text_pos = 0;
    op = OP_TEXT;
    completion_result = "OK";
    state = HID_IDLE;
    return true;
}

uint16_t tud_hid_get_report_cb(uint8_t instance, uint8_t report_id, hid_report_type_t report_type,
                               uint8_t *buffer, uint16_t reqlen) {
    (void)instance; (void)report_id; (void)report_type; (void)buffer; (void)reqlen;
    return 0;
}

void tud_hid_set_report_cb(uint8_t instance, uint8_t report_id, hid_report_type_t report_type,
                           uint8_t const *buffer, uint16_t bufsize) {
    (void)instance; (void)report_id;
    if (report_type == HID_REPORT_TYPE_OUTPUT && bufsize > 0) {
        keyboard_leds = buffer[0];
    }
}

void tud_hid_set_protocol_cb(uint8_t instance, uint8_t protocol) {
    (void)instance; (void)protocol;
}

bool tud_hid_set_idle_cb(uint8_t instance, uint8_t idle_rate) {
    (void)instance; (void)idle_rate;
    return true;
}

void tud_mount_cb(void) { mounted = true; }
void tud_umount_cb(void) { mounted = false; }
void tud_suspend_cb(bool remote_wakeup_en) { (void)remote_wakeup_en; }
void tud_resume_cb(void) { }
