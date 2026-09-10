#pragma once

#include "esp_err.h"
#include "esp_hidd.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 初始化「手机 -> 板子」数据通道（与 Pico 方案协议一致）：
 *   Service  0x1234
 *   写特征   0x1235（手机下发 TEXT/KEY/MOD/UNI/UMOD/SPEED 命令，行尾 \n）
 *   通知特征 0x1236（回 OK / ERR:xxx / STATUS:READY）
 *
 * 必须在 esp_hidd_dev_init() 之后、esp_nimble_enable()（NimBLE 启动）之前调用，
 * 这样自定义服务才能和 HID 服务一起注册。
 *
 * @param hid_dev 由 esp_hidd_dev_init() 得到的 HID 设备句柄，用于发送键盘报文
 */
esp_err_t data_service_init(esp_hidd_dev_t *hid_dev);

#ifdef __cplusplus
}
#endif