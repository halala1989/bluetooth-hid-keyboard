#ifndef USB_HID_H
#define USB_HID_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void usb_hid_early_init(void);
void usb_hid_init(void);
void usb_hid_task(void);
bool usb_hid_mounted(void);
bool usb_hid_busy(void);
const char *usb_hid_result(void);

bool usb_hid_keycode_from_name(const char *name, size_t len, uint8_t *keycode);
bool usb_hid_modifier_from_name(const char *name, size_t len, uint8_t *modifier);

void usb_hid_send_key(uint8_t modifiers, uint8_t keycode);
void usb_hid_send_unicode(uint32_t codepoint);
bool usb_hid_set_unicode_mode(int mode);  /* 0=decimal(默认), 1=hex, 2=GBK */
bool usb_hid_set_speed(int level);         /* 输入速度 1=最慢 ... 10=最快，默认 5 */
bool usb_hid_send_text(const uint8_t *utf8, size_t len);

#ifdef __cplusplus
}
#endif

#endif
