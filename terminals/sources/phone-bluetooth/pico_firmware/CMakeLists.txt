cmake_minimum_required(VERSION 3.13)

# Select the Pico W before the SDK toolchain is loaded.
set(PICO_BOARD pico_w CACHE STRING "Board type" FORCE)
set(PICO_PLATFORM rp2040 CACHE STRING "Platform" FORCE)
set(PICO_NO_PICOTOOL 1 CACHE BOOL "Do not require picotool for this build" FORCE)

include($ENV{PICO_SDK_PATH}/external/pico_sdk_import.cmake)

project(pico_ble_hid_keyboard C CXX ASM)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

pico_sdk_init()

add_executable(pico_ble_hid_keyboard
    main.c
    usb_hid.c
    usb_descriptors.c
    gbk_table.c
)

target_include_directories(pico_ble_hid_keyboard PRIVATE
    ${CMAKE_CURRENT_LIST_DIR}
)

target_link_libraries(pico_ble_hid_keyboard PRIVATE
    pico_stdlib
    pico_btstack_ble
    pico_btstack_cyw43
    pico_btstack_flash_bank
    pico_cyw43_arch_none
    tinyusb_device
    tinyusb_board
)

pico_btstack_make_gatt_header(pico_ble_hid_keyboard PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/hid_keyboard.gatt"
)

pico_enable_stdio_usb(pico_ble_hid_keyboard 0)
pico_enable_stdio_uart(pico_ble_hid_keyboard 0)

pico_add_extra_outputs(pico_ble_hid_keyboard)

find_package(Python3 REQUIRED COMPONENTS Interpreter)
add_custom_command(TARGET pico_ble_hid_keyboard POST_BUILD
    COMMAND ${Python3_EXECUTABLE}
            "${CMAKE_CURRENT_LIST_DIR}/../tools/elf2uf2.py"
            "$<TARGET_FILE:pico_ble_hid_keyboard>"
            "$<TARGET_FILE_DIR:pico_ble_hid_keyboard>/pico_ble_hid_keyboard.uf2"
    VERBATIM
)
