cmake_minimum_required(VERSION 3.13)

include(pico_sdk_import.cmake)

project(pico_w_keyboard_firmware C CXX ASM)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

pico_sdk_init()

add_executable(pico_w_keyboard_firmware
    src/main.c
    src/packet_parser.c
    src/usb_hid.c
)

target_include_directories(pico_w_keyboard_firmware PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    ${CMAKE_CURRENT_BINARY_DIR}
)

# Console over UART only; stdio USB must stay OFF so TinyUSB can act as HID.
pico_enable_stdio_uart(pico_w_keyboard_firmware ON)
pico_enable_stdio_usb(pico_w_keyboard_firmware OFF)

target_link_libraries(pico_w_keyboard_firmware
    pico_stdlib
    pico_cyw43_arch_none
    pico_btstack_cyw43
    pico_btstack_ble
    tinyusb_device
    hardware_timer
)

# Generate pico_w_keyboard.h (profile_data + ATT_*_HANDLE macros) from the .gatt file.
pico_btstack_make_gatt_header(pico_w_keyboard_firmware PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/pico_w_keyboard.gatt")

pico_add_extra_outputs(pico_w_keyboard_firmware)
