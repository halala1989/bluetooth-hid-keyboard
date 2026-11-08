#ifndef _TUSB_CONFIG_H_
#define _TUSB_CONFIG_H_

#define CFG_TUSB_MCU OPT_MCU_RP2040
#define CFG_TUSB_OS OPT_OS_PICO

#ifndef CFG_TUSB_MEM_ALIGN
#define CFG_TUSB_MEM_ALIGN __attribute__((aligned(4)))
#endif

#ifndef CFG_TUSB_MEM_SECTION
#define CFG_TUSB_MEM_SECTION __attribute__((section(".data")))
#endif

#define CFG_TUSB_DEBUG 0
#define CFG_TUSB_RHPORT0_MODE (OPT_MODE_DEVICE | OPT_MODE_FULL_SPEED)

#define CFG_TUD_ENABLED 1
#define CFG_TUD_HID 1
#define CFG_TUD_CDC 0
#define CFG_TUD_MSC 0
#define CFG_TUD_MIDI 0
#define CFG_TUD_VENDOR 0

#define CFG_TUD_HID_EP_BUFSIZE 64
#define CFG_TUD_HID_EP_IN_BUFSIZE 64
#define CFG_TUD_HID_EP_OUT_BUFSIZE 64

#endif /* _TUSB_CONFIG_H_ */
