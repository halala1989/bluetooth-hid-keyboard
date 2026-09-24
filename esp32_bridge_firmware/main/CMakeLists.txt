idf_component_register(
    SRCS "bridge.c"
    INCLUDE_DIRS "."
    PRIV_REQUIRES nvs_flash bt driver
    )

# 允许 App/构建脚本为不同板型设置 BLE 与 USB 显示名。
if(NOT DEFINED BRIDGE_DEVICE_NAME)
    set(BRIDGE_DEVICE_NAME "ESP32-S3 Bridge")
endif()
if(NOT DEFINED BRIDGE_USB_PRODUCT_NAME)
    set(BRIDGE_USB_PRODUCT_NAME "${BRIDGE_DEVICE_NAME} Keyboard")
endif()

target_compile_definitions(${COMPONENT_LIB} PRIVATE
    BRIDGE_DEVICE_NAME="${BRIDGE_DEVICE_NAME}"
    BRIDGE_USB_PRODUCT_NAME="${BRIDGE_USB_PRODUCT_NAME}"
)
