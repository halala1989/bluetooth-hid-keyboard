idf_component_register(
    SRCS "bridge.c" "gbk_table.c"
    INCLUDE_DIRS "."
    PRIV_REQUIRES nvs_flash bt driver
    )

# 允许 App/构建脚本为不同板型设置 BLE 与 USB 显示名。
if(NOT DEFINED BRIDGE_DEVICE_NAME)
    set(BRIDGE_DEVICE_NAME "ESP32蓝牙键盘终端")
endif()
if(NOT DEFINED BRIDGE_USB_PRODUCT_NAME)
    set(BRIDGE_USB_PRODUCT_NAME "ESP32蓝牙键盘终端")
endif()
if(NOT DEFINED BRIDGE_SERIAL_NUMBER)
    set(BRIDGE_SERIAL_NUMBER "0001")
endif()

target_compile_definitions(${COMPONENT_LIB} PRIVATE
    BRIDGE_DEVICE_NAME="${BRIDGE_DEVICE_NAME}"
    BRIDGE_USB_PRODUCT_NAME="${BRIDGE_USB_PRODUCT_NAME}"
    BRIDGE_SERIAL_NUMBER="${BRIDGE_SERIAL_NUMBER}"
)
