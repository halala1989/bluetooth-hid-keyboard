# boot.py - Pico W 启动配置
# BLE HID 虚拟键盘

import machine
import gc

# 启用垃圾回收
gc.collect()

# 配置 USB HID
# 注意：usb_hid 需要在 main.py 中初始化
# boot.py 只做基本配置

print("BLE HID Keyboard - boot.py")
print("Free memory:", gc.mem_free())
