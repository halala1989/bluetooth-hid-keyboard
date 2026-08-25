# main.py - Pico W 简化版主程序
# 分步初始化，便于调试

import time
import gc
from machine import Pin

# 第一步：LED 指示 - 确保代码开始运行
led = Pin("LED", Pin.OUT)
led.value(1)  # 立即点亮 LED
time.sleep(500)  # 等待 500ms
led.value(0)

print("=== Pico HID Keyboard ===")
print("Step 1: LED test OK")

# 第二步：内存检查
gc.collect()
print(f"Step 2: Free memory: {gc.mem_free()} bytes")

# 第三步：尝试初始化 USB HID
print("Step 3: Initializing USB HID...")
try:
    import usb_hid
    from lib.hid_keyboard import HidKeyboard, KEYBOARD_REPORT_DESCRIPTOR
    
    # 创建 HID 设备
    kb = HidKeyboard()
    print("Step 3: USB HID OK")
    led.value(1)
    time.sleep(200)
    led.value(0)
except Exception as e:
    print(f"Step 3: USB HID FAILED: {e}")
    print("Continuing without USB HID...")
    kb = None

# 第四步：初始化 BLE
print("Step 4: Initializing BLE...")
try:
    import bluetooth
    from lib.ble_server import BleServer
    
    # 创建 BLE 服务器
    ble = BleServer()
    print("Step 4: BLE server created")
    
    # 启动广播
    ble.start()
    print("Step 4: BLE advertising started")
    
    led.value(1)
    time.sleep(200)
    led.value(0)
except Exception as e:
    print(f"Step 4: BLE FAILED: {e}")
    import sys
    sys.print_exception(e)
    ble = None

# 第五步：主循环
print("Step 5: Entering main loop...")

# 启动完成指示
for i in range(3):
    led.value(1)
    time.sleep(100)
    led.value(0)
    time.sleep(100)

print("=== Ready ===")

# 简单的命令缓冲区
cmd_buffer = b""

while True:
    # LED 状态指示
    if ble and ble.is_connected():
        led.value(1)  # 连接时常亮
    else:
        # 未连接时每秒闪烁一次
        led.value(1)
        time.sleep(50)
        led.value(0)
        time.sleep(950)
    
    # 内存管理
    gc.collect()
