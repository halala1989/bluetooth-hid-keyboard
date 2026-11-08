#ifndef USB_HID_H
#define USB_HID_H

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * TinyUSB HID keyboard device: exposes the Pico W as a USB keyboard to the
 * host (e.g. a Windows PC) so received characters can be injected as keystrokes.
 */

void usb_hid_init(void);

/** Call periodically (in the main loop) to service the USB stack. */
void usb_hid_poll(void);

/** True when the host has enumerated and the HID report is ready. */
bool usb_hid_ready(void);

/**
 * Inject a UTF-8 byte stream as keystrokes.
 * Non-ASCII (multi-byte) characters are skipped for now.
 * Returns the number of keys actually pressed.
 */
uint32_t usb_hid_inject_text(const uint8_t *text, uint32_t len);

#ifdef __cplusplus
}
#endif

#endif /* USB_HID_H */
