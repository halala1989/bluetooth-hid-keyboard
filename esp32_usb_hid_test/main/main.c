/*
 * ESP32-S3 Super Mini · USB HID 键盘最小测试（不启用蓝牙）
 *
 * 行为：插到电脑 USB 口后，板子作为有线 USB 键盘出现；
 *       开机 3 秒后打第一个数字 '1'，之后每隔 60 秒依次打 '2'、'3'…'9'、'0'、'1'…
 * 用途：完全不依赖蓝牙/手机，验证「板子 -> 电脑」HID 输出链路。
 */
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "tinyusb.h"
#include "class/hid/hid_device.h"

static const char *TAG = "USB_HID_TEST";

/************* TinyUSB 描述符 *************/
#define TUSB_DESC_TOTAL_LEN (TUD_CONFIG_DESC_LEN + TUD_HID_DESC_LEN)

// 只做键盘：Report ID = 1
static const uint8_t hid_report_descriptor[] = {
    TUD_HID_REPORT_DESC_KEYBOARD(HID_REPORT_ID(1))
};

static const char *hid_string_descriptor[5] = {
    (char[]){0x09, 0x04},              // 0: 语言 ID（英文）
    "PhoneKeyboard",                   // 1: 厂商
    "ESP32-S3 USB Keyboard Test",      // 2: 产品名
    "000001",                          // 3: 序列号
    "USB HID Keyboard",                // 4: HID 接口名
};

static const uint8_t hid_configuration_descriptor[] = {
    TUD_CONFIG_DESCRIPTOR(1, 1, 0, TUSB_DESC_TOTAL_LEN, TUSB_DESC_CONFIG_ATT_REMOTE_WAKEUP, 100),
    TUD_HID_DESCRIPTOR(0, 4, false, sizeof(hid_report_descriptor), 0x81, 16, 10),
};

/************* TinyUSB 回调 *************/
uint8_t const *tud_hid_descriptor_report_cb(uint8_t instance)
{
    return hid_report_descriptor;
}

uint16_t tud_hid_get_report_cb(uint8_t instance, uint8_t report_id, hid_report_type_t report_type,
                               uint8_t *buffer, uint16_t reqlen)
{
    return 0;
}

void tud_hid_set_report_cb(uint8_t instance, uint8_t report_id, hid_report_type_t report_type,
                           uint8_t const *buffer, uint16_t bufsize)
{
}

/************* 主程序 *************/
// USB HID 数字键 usage：'1'..'9' = 0x1E..0x26，'0' = 0x27
static const uint8_t digit_usage[10] = {
    0x27, // 0
    0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, // 1..9
};

void app_main(void)
{
    const tinyusb_config_t tusb_cfg = {
        .device_descriptor = NULL,
        .string_descriptor = hid_string_descriptor,
        .string_descriptor_count = sizeof(hid_string_descriptor) / sizeof(hid_string_descriptor[0]),
        .external_phy = false,
        .configuration_descriptor = hid_configuration_descriptor,
    };
    ESP_ERROR_CHECK(tinyusb_driver_install(&tusb_cfg));
    ESP_LOGI(TAG, "USB HID keyboard ready");

    vTaskDelay(pdMS_TO_TICKS(3000)); // 等电脑枚举完成

    int idx = 1; // 从 '1' 开始
    while (1) {
        if (tud_mounted()) {
            uint8_t keycode[6] = { digit_usage[idx] };
            ESP_LOGI(TAG, "type digit %d (usage 0x%02X)", idx, digit_usage[idx]);
            tud_hid_keyboard_report(1, 0, keycode);  // 按下
            vTaskDelay(pdMS_TO_TICKS(50));
            tud_hid_keyboard_report(1, 0, NULL);     // 松开
            idx = (idx >= 9) ? 0 : (idx + 1);        // 1..9,0,1..
        } else {
            ESP_LOGW(TAG, "USB not mounted yet, waiting...");
        }
        vTaskDelay(pdMS_TO_TICKS(60000));
    }
}