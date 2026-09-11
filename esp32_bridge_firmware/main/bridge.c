/*
 * 手机 <-> 板子 数据通道（与 Pico 方案协议一致）
 *
 *   Service  0x1234
 *   写特征   0x1235  (手机 -> 板子: TEXT/KEY/MOD/UNI/UMOD/SPEED，行尾 \n)
 *   通知特征 0x1236  (板子 -> 手机: OK / ERR:xxx / STATUS:READY)
 *
 * 数据先进入 PSRAM 里的大环形缓冲，再由独立任务按设定的速度“滴灌”成 HID 键盘报文，
 * 这样手机可以高速灌数据、电脑端不易丢键。
 */
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>
#include <stdbool.h>
#include <strings.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/semphr.h"

#include "esp_log.h"
#include "esp_heap_caps.h"

#include "host/ble_hs.h"
#include "host/ble_uuid.h"
#include "host/ble_hs_mbuf.h"
#include "host/ble_gatt.h"
#include "host/util/util.h"
#include "os/os_mbuf.h"

#include "tinyusb.h"
#include "class/hid/hid_device.h"

#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs_adv.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"
#include "nvs_flash.h"

static const char *TAG = "DATA_SVC";

/* ---------- 协议 UUID（与 Pico / Android App 一致） ---------- */
#define DATA_SVC_UUID      0x1234
#define DATA_CMD_UUID      0x1235
#define DATA_STATUS_UUID   0x1236

/* ---------- 缓冲 ---------- */
#define RX_BUF_PSRAM_SIZE  (512 * 1024)   /* PSRAM 大缓冲 */
#define RX_BUF_FALLBACK    (32 * 1024)    /* PSRAM 分配失败时退回内部 RAM */
#define MAX_CMD_LINE       1200

/* ---------- 打字节奏（与 Pico 固件一致的基准，按速度档缩放） ---------- */
#define T_KEY_DOWN_MS   15u
#define T_KEY_UP_MS     15u
#define T_CHAR_GAP_MS    8u
#define T_ALT_FINAL_MS  80u
#define T_MIN_MS         2u

/* USB HID usage */
#define MOD_LCTRL   0x01
#define MOD_LSHIFT  0x02
#define MOD_LALT    0x04
#define MOD_LGUI    0x08
#define MOD_RCTRL   0x10
#define MOD_RSHIFT  0x20
#define MOD_RALT    0x40
#define MOD_RGUI    0x80

#define KEY_A 0x04
#define KEY_X 0x1B
#define KEY_RETURN 0x28
#define KEY_ESC 0x29
#define KEY_BACKSPACE 0x2A
#define KEY_TAB 0x2B
#define KEY_SPACE 0x2C

typedef enum {
    UNI_MODE_DECIMAL = 0,
    UNI_MODE_HEX = 1,
    UNI_MODE_GBK = 2,
    UNI_MODE_ALTX = 3,
} unicode_mode_t;

static uint16_t s_status_val_handle = 0;
static uint16_t s_conn_handle = BLE_HS_CONN_HANDLE_NONE;
static struct ble_gap_event_listener s_gap_listener;

static void start_advertising(void);   // 前置声明（gap 回调里会用到）

static uint8_t s_unicode_mode = UNI_MODE_ALTX;
static uint8_t s_speed = 5;
static volatile bool s_hid_failed = false;   // 本次命令里 HID 报告发送是否失败（电脑没连时会出现）
static uint32_t s_scale = 1000;                 /* 千分比，速度 10 时 300 */

/* ---------- PSRAM 环形缓冲（单生产者 BLE 回调 / 单消费者任务） ---------- */
static uint8_t *s_rb = NULL;
static size_t s_rb_cap = 0;
static volatile size_t s_rb_head = 0;
static volatile size_t s_rb_tail = 0;
static SemaphoreHandle_t s_rb_mtx = NULL;

static size_t rb_push(const uint8_t *data, size_t len)
{
    if (!s_rb || len == 0) return 0;
    xSemaphoreTake(s_rb_mtx, portMAX_DELAY);
    size_t written = 0;
    while (written < len) {
        size_t next = (s_rb_head + 1) % s_rb_cap;
        if (next == s_rb_tail) break;           /* 满 */
        s_rb[s_rb_head] = data[written++];
        s_rb_head = next;
    }
    xSemaphoreGive(s_rb_mtx);
    return written;
}

static bool rb_pop(uint8_t *out)
{
    bool ok = false;
    xSemaphoreTake(s_rb_mtx, portMAX_DELAY);
    if (s_rb_head != s_rb_tail) {
        *out = s_rb[s_rb_tail];
        s_rb_tail = (s_rb_tail + 1) % s_rb_cap;
        ok = true;
    }
    xSemaphoreGive(s_rb_mtx);
    return ok;
}


/* ---------- 通知手机 ---------- */
static void notify_status(const char *msg)
{
    if (s_conn_handle == BLE_HS_CONN_HANDLE_NONE || s_status_val_handle == 0 || !msg) return;
    struct os_mbuf *om = ble_hs_mbuf_from_flat(msg, strlen(msg));
    if (!om) return;
    int rc = ble_gatts_notify_custom(s_conn_handle, s_status_val_handle, om);
    if (rc != 0) {
        ESP_LOGD(TAG, "notify failed rc=%d", rc);
    }
}

/* ---------- HID 打字 ---------- */
static uint32_t scaled(uint32_t ms)
{
    uint32_t v = (ms * s_scale) / 1000u;
    return v < T_MIN_MS ? T_MIN_MS : v;
}

static void hid_press(uint8_t modifier, uint8_t usage)
{
    /* USB HID（TinyUSB）：第一参数是 Report ID(1)，keycode 为 6 键数组，NULL 表示全松开 */
    uint8_t keycode[6] = {0};
    keycode[0] = usage;
    if (!tud_hid_keyboard_report(HID_ITF_PROTOCOL_KEYBOARD, modifier, keycode)) {
        s_hid_failed = true;
        ESP_LOGW(TAG, "USB HID report failed (PC 未就绪?)");
    }
    vTaskDelay(pdMS_TO_TICKS(scaled(T_KEY_DOWN_MS)));
    if (!tud_hid_keyboard_report(HID_ITF_PROTOCOL_KEYBOARD, 0, NULL)) {
        s_hid_failed = true;
    }
    vTaskDelay(pdMS_TO_TICKS(scaled(T_KEY_UP_MS + T_CHAR_GAP_MS)));
}
/* ASCII -> (modifier, usage)，不支持的返回 false */
static bool ascii_to_hid(char ch, uint8_t *mod, uint8_t *usage)
{
    *mod = 0; *usage = 0;
    if (ch >= 'a' && ch <= 'z') { *usage = (uint8_t)(KEY_A + (ch - 'a')); return true; }
    if (ch >= 'A' && ch <= 'Z') { *mod = MOD_LSHIFT; *usage = (uint8_t)(KEY_A + (ch - 'A')); return true; }
    if (ch >= '1' && ch <= '9') { *usage = (uint8_t)(0x1E + (ch - '1')); return true; }
    if (ch == '0') { *usage = 0x27; return true; }
    switch (ch) {
        case '\n': case '\r': *usage = KEY_RETURN; return true;
        case '\t': *usage = KEY_TAB; return true;
        case ' ':  *usage = KEY_SPACE; return true;
        case '-': *usage = 0x2D; return true;
        case '_': *mod = MOD_LSHIFT; *usage = 0x2D; return true;
        case '=': *usage = 0x2E; return true;
        case '+': *mod = MOD_LSHIFT; *usage = 0x2E; return true;
        case '[': *usage = 0x2F; return true;
        case '{': *mod = MOD_LSHIFT; *usage = 0x2F; return true;
        case ']': *usage = 0x30; return true;
        case '}': *mod = MOD_LSHIFT; *usage = 0x30; return true;
        case '\\': *usage = 0x31; return true;
        case '|': *mod = MOD_LSHIFT; *usage = 0x31; return true;
        case ';': *usage = 0x33; return true;
        case ':': *mod = MOD_LSHIFT; *usage = 0x33; return true;
        case '\'': *usage = 0x34; return true;
        case '"': *mod = MOD_LSHIFT; *usage = 0x34; return true;
        case '`': *usage = 0x35; return true;
        case '~': *mod = MOD_LSHIFT; *usage = 0x35; return true;
        case ',': *usage = 0x36; return true;
        case '<': *mod = MOD_LSHIFT; *usage = 0x36; return true;
        case '.': *usage = 0x37; return true;
        case '>': *mod = MOD_LSHIFT; *usage = 0x37; return true;
        case '/': *usage = 0x38; return true;
        case '?': *mod = MOD_LSHIFT; *usage = 0x38; return true;
        case '!': *mod = MOD_LSHIFT; *usage = 0x1E; return true;
        case '@': *mod = MOD_LSHIFT; *usage = 0x1F; return true;
        case '#': *mod = MOD_LSHIFT; *usage = 0x20; return true;
        case '$': *mod = MOD_LSHIFT; *usage = 0x21; return true;
        case '%': *mod = MOD_LSHIFT; *usage = 0x22; return true;
        case '^': *mod = MOD_LSHIFT; *usage = 0x23; return true;
        case '&': *mod = MOD_LSHIFT; *usage = 0x24; return true;
        case '*': *mod = MOD_LSHIFT; *usage = 0x25; return true;
        case '(': *mod = MOD_LSHIFT; *usage = 0x26; return true;
        case ')': *mod = MOD_LSHIFT; *usage = 0x27; return true;
        default: return false;
    }
}

/* 特殊按键名 -> usage */
static uint8_t key_from_name(const char *name)
{
    if (!strcasecmp(name, "ENTER") || !strcasecmp(name, "RETURN")) return KEY_RETURN;
    if (!strcasecmp(name, "ESC") || !strcasecmp(name, "ESCAPE")) return KEY_ESC;
    if (!strcasecmp(name, "BACKSPACE")) return KEY_BACKSPACE;
    if (!strcasecmp(name, "TAB")) return KEY_TAB;
    if (!strcasecmp(name, "SPACE")) return KEY_SPACE;
    if (!strcasecmp(name, "CAPSLOCK")) return 0x39;
    if (!strcasecmp(name, "DELETE") || !strcasecmp(name, "DEL")) return 0x4C;
    if (!strcasecmp(name, "INSERT")) return 0x49;
    if (!strcasecmp(name, "HOME")) return 0x4A;
    if (!strcasecmp(name, "END")) return 0x4D;
    if (!strcasecmp(name, "PAGEUP")) return 0x4B;
    if (!strcasecmp(name, "PAGEDOWN")) return 0x4E;
    if (!strcasecmp(name, "UP")) return 0x52;
    if (!strcasecmp(name, "DOWN")) return 0x51;
    if (!strcasecmp(name, "LEFT")) return 0x50;
    if (!strcasecmp(name, "RIGHT")) return 0x4F;
    if (!strcasecmp(name, "PRINTSCREEN")) return 0x46;
    if (!strcasecmp(name, "NUMLOCK")) return 0x53;
    if (!strcasecmp(name, "SCROLLLOCK")) return 0x47;
    if (!strcasecmp(name, "PAUSE")) return 0x48;
    if (!strcasecmp(name, "MINUS")) return 0x2D;
    if (!strcasecmp(name, "EQUALS")) return 0x2E;
    if (!strcasecmp(name, "COMMA")) return 0x36;
    if (!strcasecmp(name, "PERIOD")) return 0x37;
    if (!strcasecmp(name, "SLASH")) return 0x38;
    if (!strcasecmp(name, "BACKSLASH")) return 0x31;
    if (!strcasecmp(name, "SEMICOLON")) return 0x33;
    if (!strcasecmp(name, "APOSTROPHE")) return 0x34;
    if (!strcasecmp(name, "GRAVE")) return 0x35;
    if (!strcasecmp(name, "LEFTBRACKET")) return 0x2F;
    if (!strcasecmp(name, "RIGHTBRACKET")) return 0x30;
    if (name[0] == 'F' && name[1] >= '1' && name[1] <= '9') {
        int n = atoi(name + 1);
        if (n >= 1 && n <= 12) return (uint8_t)(0x3A + (n - 1));
    }
    if (!strncasecmp(name, "KP_", 3)) {
        const char *k = name + 3;
        if (k[0] >= '0' && k[0] <= '9' && k[1] == 0) {
            return (k[0] == '0') ? 0x62 : (uint8_t)(0x59 + (k[0] - '1'));
        }
        if (!strcasecmp(k, "DECIMAL")) return 0x63;
        if (!strcasecmp(k, "MULTIPLY")) return 0x55;
        if (!strcasecmp(k, "ADD")) return 0x57;
        if (!strcasecmp(k, "SUBTRACT")) return 0x56;
        if (!strcasecmp(k, "DIVIDE")) return 0x54;
        if (!strcasecmp(k, "ENTER")) return 0x58;
    }
    /* 单字符按键：A-Z / 0-9 */
    if (name[0] && name[1] == 0) {
        uint8_t mod = 0, usage = 0;
        if (ascii_to_hid(name[0], &mod, &usage)) return usage;
    }
    return 0;
}

static uint8_t modifiers_from_string(const char *name)
{
    if (!strcasecmp(name, "CTRL") || !strcasecmp(name, "CONTROL") || !strcasecmp(name, "LEFTCTRL")) return MOD_LCTRL;
    if (!strcasecmp(name, "RIGHTCTRL")) return MOD_RCTRL;
    if (!strcasecmp(name, "SHIFT") || !strcasecmp(name, "LEFTSHIFT")) return MOD_LSHIFT;
    if (!strcasecmp(name, "RIGHTSHIFT")) return MOD_RSHIFT;
    if (!strcasecmp(name, "ALT") || !strcasecmp(name, "LEFTALT")) return MOD_LALT;
    if (!strcasecmp(name, "RIGHTALT")) return MOD_RALT;
    if (!strcasecmp(name, "GUI") || !strcasecmp(name, "WIN") || !strcasecmp(name, "WINDOWS") || !strcasecmp(name, "LEFTGUI")) return MOD_LGUI;
    if (!strcasecmp(name, "RIGHTGUI")) return MOD_RGUI;
    return 0;
}

/* UTF-8 -> 码点；失败返回 0 并 *consumed=1 */
static uint32_t utf8_next(const uint8_t *s, size_t len, size_t *consumed)
{
    uint8_t c = s[0];
    if (c < 0x80) { *consumed = 1; return c; }
    if ((c & 0xE0) == 0xC0 && len >= 2) { *consumed = 2; return ((c & 0x1F) << 6) | (s[1] & 0x3F); }
    if ((c & 0xF0) == 0xE0 && len >= 3) { *consumed = 3; return ((c & 0x0F) << 12) | ((s[1] & 0x3F) << 6) | (s[2] & 0x3F); }
    if ((c & 0xF8) == 0xF0 && len >= 4) { *consumed = 4; return ((c & 0x07) << 18) | ((s[1] & 0x3F) << 12) | ((s[2] & 0x3F) << 6) | (s[3] & 0x3F); }
    *consumed = 1;
    return 0;
}

/* Alt+X：先打十六进制码（大写），再按 Alt+X */
static void type_altx(uint32_t codepoint)
{
    char hex[12];
    int n = snprintf(hex, sizeof(hex), "%X", (unsigned)codepoint);
    for (int i = 0; i < n; i++) {
        uint8_t mod = 0, usage = 0;
        if (ascii_to_hid(hex[i], &mod, &usage)) hid_press(mod, usage);
    }
    vTaskDelay(pdMS_TO_TICKS(scaled(T_CHAR_GAP_MS * 4)));
    hid_press(MOD_LALT, KEY_X);              /* Alt+X 转换 */
    vTaskDelay(pdMS_TO_TICKS(scaled(T_ALT_FINAL_MS)));
}

static void type_codepoint(uint32_t cp)
{
    if (cp == 0) return;
    if (cp < 0x80) {
        uint8_t mod = 0, usage = 0;
        if (ascii_to_hid((char)cp, &mod, &usage)) hid_press(mod, usage);
        return;
    }
    if (s_unicode_mode == UNI_MODE_ALTX) {
        type_altx(cp);
    } else {
        /* GBK / 十进制 / 十六进制小键盘模式后续移植（需要 gbk 码表） */
        notify_status("ERR:UNI_MODE_NOT_READY");
    }
}

static void handle_text(const char *payload)
{
    const uint8_t *p = (const uint8_t *)payload;
    size_t remain = strlen(payload);
    while (remain > 0) {
        size_t used = 0;
        uint32_t cp = utf8_next(p, remain, &used);
        type_codepoint(cp);
        p += used;
        remain -= used;
    }
}

static void handle_command(char *line)
{
    /* 去掉行尾 \r */
    size_t n = strlen(line);
    while (n > 0 && (line[n - 1] == '\r' || line[n - 1] == ' ')) line[--n] = 0;

    char *colon = strchr(line, ':');
    char *cmd = line;
    char *arg = "";
    if (colon) { *colon = 0; arg = colon + 1; }

    if (!strcasecmp(cmd, "TEXT")) {
        s_hid_failed = false;
        handle_text(arg);
        notify_status(s_hid_failed ? "ERR:HID_NOT_READY" : "OK");
    } else if (!strcasecmp(cmd, "KEY")) {
        uint8_t usage = key_from_name(arg);
        if (!usage) { notify_status("ERR:INVALID_KEY"); return; }
        s_hid_failed = false;
        hid_press(0, usage);
        notify_status(s_hid_failed ? "ERR:HID_NOT_READY" : "OK");
    } else if (!strcasecmp(cmd, "MOD")) {
        char buf[128];
        strncpy(buf, arg, sizeof(buf) - 1);
        buf[sizeof(buf) - 1] = 0;
        uint8_t mod = 0;
        char *last = NULL;
        char *tok = strtok(buf, "+");
        while (tok) {
            char *nxt = strtok(NULL, "+");
            if (nxt == NULL) { last = tok; break; }
            mod |= modifiers_from_string(tok);
            tok = nxt;
        }
        uint8_t usage = last ? key_from_name(last) : 0;
        if (!usage && last && strlen(last) == 1) {
            uint8_t m = 0;
            ascii_to_hid(last[0], &m, &usage);
            mod |= m;
        }
        if (!usage) { notify_status("ERR:INVALID_KEY"); return; }
        s_hid_failed = false;
        hid_press(mod, usage);
        notify_status(s_hid_failed ? "ERR:HID_NOT_READY" : "OK");
    } else if (!strcasecmp(cmd, "UNI")) {
        uint32_t cp = (uint32_t)strtoul(arg, NULL, 0);
        if (cp == 0 || cp > 0x10FFFF) { notify_status("ERR:INVALID_CODEPOINT"); return; }
        s_hid_failed = false;
        type_codepoint(cp);
        notify_status(s_hid_failed ? "ERR:HID_NOT_READY" : "OK");
    } else if (!strcasecmp(cmd, "UMOD")) {
        int mode = atoi(arg);
        if (mode < 0 || mode > 3) { notify_status("ERR:INVALID_MODE"); return; }
        s_unicode_mode = (uint8_t)mode;
        notify_status("OK");
    } else if (!strcasecmp(cmd, "SPEED")) {
        int level = atoi(arg);
        if (level < 1 || level > 10) { notify_status("ERR:INVALID_SPEED"); return; }
        static const uint32_t scales[10] = {2500, 2000, 1600, 1200, 1000, 800, 650, 500, 400, 300};
        s_speed = (uint8_t)level;
        s_scale = scales[level - 1];
        notify_status("OK");
    } else {
        notify_status("ERR:INVALID_CMD");
    }
}

/* ---------- 消费者任务：从 PSRAM 缓冲取命令并执行 ---------- */
static void data_task(void *arg)
{
    static char line[MAX_CMD_LINE];
    size_t len = 0;
    ESP_LOGI(TAG, "data task started, ring buffer %u bytes", (unsigned)s_rb_cap);
    while (1) {
        uint8_t ch;
        if (!rb_pop(&ch)) {
            vTaskDelay(pdMS_TO_TICKS(20)); // 注意：ESP-IDF 默认 tick=10ms，pdMS_TO_TICKS(5)=0 会让任务空转触发看门狗
            continue;
        }
        if (ch == '\n' || ch == '\r') {
            if (len > 0) {
                line[len] = 0;
                handle_command(line);
                len = 0;
            }
            continue;
        }
        if (len < sizeof(line) - 1) {
            line[len++] = (char)ch;
        } else {
            len = 0;
            notify_status("ERR:TEXT_TOO_LARGE");
        }
    }
}

/* ---------- BLE GATT ---------- */
static int gatt_cmd_access(uint16_t conn_handle, uint16_t attr_handle,
                           struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    if (ctxt->op != BLE_GATT_ACCESS_OP_WRITE_CHR) return BLE_ATT_ERR_UNLIKELY;
    uint16_t len = OS_MBUF_PKTLEN(ctxt->om);
    if (len == 0) return 0;
    uint8_t tmp[512];
    uint16_t to_copy = len > sizeof(tmp) ? sizeof(tmp) : len;
    uint16_t copied = 0;
    int rc = ble_hs_mbuf_to_flat(ctxt->om, tmp, to_copy, &copied);
    if (rc != 0) return BLE_ATT_ERR_UNLIKELY;
    s_conn_handle = conn_handle;
    size_t written = rb_push(tmp, copied);
    if (written < copied) notify_status("ERR:OVERFLOW");
    return 0;
}

static int gatt_status_access(uint16_t conn_handle, uint16_t attr_handle,
                              struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    static const char ready[] = "STATUS:READY";
    if (ctxt->op == BLE_GATT_ACCESS_OP_READ_CHR) {
        int rc = os_mbuf_append(ctxt->om, ready, sizeof(ready) - 1);
        return rc == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    return BLE_ATT_ERR_UNLIKELY;
}

static const struct ble_gatt_svc_def s_svcs[] = {
    {
        .type = BLE_GATT_SVC_TYPE_PRIMARY,
        .uuid = BLE_UUID16_DECLARE(DATA_SVC_UUID),
        .characteristics = (struct ble_gatt_chr_def[]) {
            {
                .uuid = BLE_UUID16_DECLARE(DATA_CMD_UUID),
                .access_cb = gatt_cmd_access,
                .flags = BLE_GATT_CHR_F_WRITE | BLE_GATT_CHR_F_WRITE_NO_RSP,
            },
            {
                .uuid = BLE_UUID16_DECLARE(DATA_STATUS_UUID),
                .access_cb = gatt_status_access,
                .val_handle = &s_status_val_handle,
                .flags = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_NOTIFY,
            },
            { 0 }
        },
    },
    { 0 }
};

static int gap_event_cb(struct ble_gap_event *event, void *arg)
{
    switch (event->type) {
    case BLE_GAP_EVENT_CONNECT:
        ESP_LOGI(TAG, "BLE connect status=%d handle=%d", event->connect.status, event->connect.conn_handle);
        break;
    case BLE_GAP_EVENT_DISCONNECT:
        if (event->disconnect.conn.conn_handle == s_conn_handle) {
            s_conn_handle = BLE_HS_CONN_HANDLE_NONE;
        }
        ESP_LOGI(TAG, "BLE disconnect reason=%d", event->disconnect.reason);
        start_advertising();
        break;
    case BLE_GAP_EVENT_SUBSCRIBE:
        if (event->subscribe.attr_handle == s_status_val_handle && event->subscribe.cur_notify) {
            s_conn_handle = event->subscribe.conn_handle;
            notify_status("STATUS:READY");
            ESP_LOGI(TAG, "phone subscribed status char, handle=%d", s_conn_handle);
        }
        break;
    default:
        break;
    }
    return 0;
}

/* ================= USB HID（TinyUSB）================= */
#define TUSB_DESC_TOTAL_LEN (TUD_CONFIG_DESC_LEN + TUD_HID_DESC_LEN)

static const uint8_t hid_report_descriptor[] = {
    TUD_HID_REPORT_DESC_KEYBOARD(HID_REPORT_ID(1))
};

static const char *hid_string_descriptor[5] = {
    (char[]){0x09, 0x04},              // 语言
    "PhoneKeyboard",                   // 厂商
    "ESP32-S3 Bridge Keyboard",        // 产品名
    "000001",                          // 序列号
    "USB HID Keyboard",                // HID 接口
};

static const uint8_t hid_configuration_descriptor[] = {
    TUD_CONFIG_DESCRIPTOR(1, 1, 0, TUSB_DESC_TOTAL_LEN, TUSB_DESC_CONFIG_ATT_REMOTE_WAKEUP, 100),
    TUD_HID_DESCRIPTOR(0, 4, false, sizeof(hid_report_descriptor), 0x81, 16, 10),
};

uint8_t const *tud_hid_descriptor_report_cb(uint8_t instance) { return hid_report_descriptor; }
uint16_t tud_hid_get_report_cb(uint8_t instance, uint8_t report_id, hid_report_type_t report_type,
                               uint8_t *buffer, uint16_t reqlen) { return 0; }
void tud_hid_set_report_cb(uint8_t instance, uint8_t report_id, hid_report_type_t report_type,
                           uint8_t const *buffer, uint16_t bufsize) {}

static void usb_hid_init(void)
{
    const tinyusb_config_t tusb_cfg = {
        .device_descriptor = NULL,
        .string_descriptor = hid_string_descriptor,
        .string_descriptor_count = sizeof(hid_string_descriptor) / sizeof(hid_string_descriptor[0]),
        .external_phy = false,
        .configuration_descriptor = hid_configuration_descriptor,
    };
    ESP_ERROR_CHECK(tinyusb_driver_install(&tusb_cfg));
    ESP_LOGI(TAG, "USB HID keyboard installed, waiting for mount...");
    while (!tud_mounted()) {
        vTaskDelay(pdMS_TO_TICKS(100));
    }
    ESP_LOGI(TAG, "USB HID mounted");
}

/* ================= BLE（NimBLE 数据通道）================= */
static void start_advertising(void)
{
    struct ble_gap_adv_params adv_params;
    struct ble_hs_adv_fields fields;
    int rc;

    memset(&fields, 0, sizeof(fields));
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    static const char *name = "ESP32-S3 Bridge";
    fields.name = (uint8_t *)name;
    fields.name_len = strlen(name);
    fields.name_is_complete = 1;
    static ble_uuid16_t uuid = BLE_UUID16_INIT(DATA_SVC_UUID);
    fields.uuids16 = &uuid;
    fields.num_uuids16 = 1;
    fields.uuids16_is_complete = 1;

    rc = ble_gap_adv_set_fields(&fields);
    if (rc != 0) { ESP_LOGE(TAG, "adv set fields failed: %d", rc); return; }

    memset(&adv_params, 0, sizeof(adv_params));
    adv_params.conn_mode = BLE_GAP_CONN_MODE_UND;
    adv_params.disc_mode = BLE_GAP_DISC_MODE_GEN;
    rc = ble_gap_adv_start(BLE_OWN_ADDR_PUBLIC, NULL, BLE_HS_FOREVER, &adv_params, gap_event_cb, NULL);
    if (rc != 0) { ESP_LOGE(TAG, "adv start failed: %d", rc); }
}

static void on_sync(void)
{
    uint8_t addr_type = 0;
    if (ble_hs_id_infer_auto(0, &addr_type) == 0) {
        ESP_LOGI(TAG, "BLE address type %d", addr_type);
    }
    start_advertising();
}

static void on_reset(int reason)
{
    ESP_LOGW(TAG, "BLE reset, reason=%d", reason);
}

static void host_task(void *param)
{
    nimble_port_run();
    nimble_port_freertos_deinit();
}

void app_main(void)
{
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);

    /* 1) USB HID 键盘：输出到电脑 */
    usb_hid_init();

    /* 2) PSRAM 大缓冲 */
    s_rb = (uint8_t *)heap_caps_malloc(RX_BUF_PSRAM_SIZE, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    s_rb_cap = RX_BUF_PSRAM_SIZE;
    if (!s_rb) {
        s_rb = (uint8_t *)malloc(RX_BUF_FALLBACK);
        s_rb_cap = RX_BUF_FALLBACK;
        ESP_LOGW(TAG, "PSRAM alloc failed, fallback to %d bytes", RX_BUF_FALLBACK);
    }
    if (!s_rb) { ESP_LOGE(TAG, "no mem for ring buffer"); return; }
    s_rb_mtx = xSemaphoreCreateMutex();
    if (!s_rb_mtx) { ESP_LOGE(TAG, "no mem for mutex"); return; }

    /* 3) BLE 数据通道（NimBLE）：接收手机命令 */
    ESP_ERROR_CHECK(nimble_port_init());
    ble_svc_gap_init();
    ble_svc_gatt_init();
    ble_svc_gap_device_name_set("ESP32-S3 Bridge");
    int rc = ble_gatts_count_cfg(s_svcs);
    if (rc != 0) { ESP_LOGE(TAG, "gatts_count_cfg: %d", rc); }
    rc = ble_gatts_add_svcs(s_svcs);
    if (rc != 0) { ESP_LOGE(TAG, "gatts_add_svcs: %d", rc); }
    ble_gap_event_listener_register(&s_gap_listener, gap_event_cb, NULL);
    ble_hs_cfg.sync_cb = on_sync;
    ble_hs_cfg.reset_cb = on_reset;
    nimble_port_freertos_init(host_task);

    /* 4) 命令解析 + 打字任务 */
    xTaskCreate(data_task, "data_task", 4096, NULL, 5, NULL);

    ESP_LOGI(TAG, "BRIDGE READY: USB HID out + BLE data in (0x1234/0x1235/0x1236), buffer %u KB",
             (unsigned)(s_rb_cap / 1024));
}