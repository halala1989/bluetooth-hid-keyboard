# main.py - Pico W 完整可工作版本
# 简化初始化，确保启动成功

import time
import gc
from machine import Pin

# ============== 第一阶段：基本初始化 ==============
# LED 立即点亮，表示代码开始运行
led = Pin("LED", Pin.OUT)
led.value(1)

print("")
print("====================================")
print("  Pico HID Keyboard v1.0")
print("====================================")
print("")

# 等待系统稳定
time.sleep(1000)

# 内存检查
gc.collect()
mem = gc.mem_free()
print(f"Free memory: {mem} bytes")

if mem < 50000:
    print("WARNING: Low memory!")

# ============== 第二阶段：BLE 初始化 ==============
print("")
print("Initializing BLE...")

try:
    import bluetooth
    
    # 创建 BLE 对象
    ble = bluetooth.BLE()
    print("BLE object created")
    
    # 定义 UUID
    SERVICE_UUID = 0x1234
    CHAR_UUID = 0x1235
    
    # 注册 GATT 服务
    handles = ble.gatts_register_services((
        (bluetooth.UUID(SERVICE_UUID), (
            (bluetooth.UUID(CHAR_UUID), bluetooth.FLAG_WRITE | bluetooth.FLAG_NOTIFY),
        )),
    ))
    
    char_handle = handles[0][0]
    print(f"GATT service registered, handle: {char_handle}")
    
    # 连接状态
    conn_handle = None
    rx_buffer = b""
    
    # BLE IRQ 处理器
    def ble_irq(event, data):
        global conn_handle, rx_buffer
        
        if event == 1:  # CONNECT
            conn_handle = data[0]
            print(f"Client connected: {conn_handle}")
            led.value(1)
            
        elif event == 2:  # DISCONNECT
            conn_handle = None
            print("Client disconnected")
            # 重新广播
            ble.gap_advertise(100, adv_data)
            
        elif event == 3:  # WRITE
            nonlocal rx_buffer
            conn, handle = data
            if handle == char_handle:
                value = ble.gatts_read(handle)
                rx_buffer += value
                # 处理完整消息
                if b"\n" in rx_buffer:
                    lines = rx_buffer.split(b"\n")
                    rx_buffer = lines[-1]
                    for line in lines[:-1]:
                        if line:
                            process_command(line.decode("utf-8").strip())
    
    # 设置 IRQ
    ble.irq(ble_irq)
    
    # 准备广播数据
    name = b"Pico HID Keyboard"
    adv_data = (
        bytes([0x02, 0x01, 0x06]) +  # Flags
        bytes([0x03, 0x03, 0x34, 0x12]) +  # Service UUID
        bytes([len(name) + 1, 0x09]) + name  # Name
    )
    
    # 启动 BLE
    ble.active(True)
    ble.gap_advertise(100, adv_data)
    
    print("BLE advertising started!")
    print(f"Device name: Pico HID Keyboard")
    
except Exception as e:
    print(f"BLE FAILED: {e}")
    import sys
    sys.print_exception(e)
    ble = None

# ============== 第三阶段：USB HID 初始化 ==============
print("")
print("Initializing USB HID...")

kb_device = None

try:
    import usb_hid
    
    # 标准键盘报告描述符
    KEYBOARD_DESCRIPTOR = bytes([
        0x05, 0x01,  # Usage Page (Generic Desktop)
        0x09, 0x06,  # Usage (Keyboard)
        0xA1, 0x01,  # Collection (Application)
        0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7,  # Modifier keys
        0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01,  # Reserved byte
        0x05, 0x08, 0x19, 0x01, 0x29, 0x05,  # LED output
        0x75, 0x01, 0x95, 0x05, 0x91, 0x02,
        0x95, 0x01, 0x75, 0x03, 0x91, 0x01,
        0x05, 0x07, 0x19, 0x00, 0x29, 0xFF,  # Key array
        0x15, 0x00, 0x26, 0xFF, 0x00, 0x75, 0x08, 0x95, 0x06, 0x81, 0x00,
        0xC0
    ])
    
    kb_device = usb_hid.Device(
        report_descriptor=KEYBOARD_DESCRIPTOR,
        subclass=1,
        protocol=1,
    )
    
    print("USB HID device created!")
    
except Exception as e:
    print(f"USB HID FAILED: {e}")
    kb_device = None

# ============== 第四阶段：按键映射 ==============
# 键盘码
KEYS = {
    'A': 4, 'B': 5, 'C': 6, 'D': 7, 'E': 8, 'F': 9, 'G': 10,
    'H': 11, 'I': 12, 'J': 13, 'K': 14, 'L': 15, 'M': 16,
    'N': 17, 'O': 18, 'P': 19, 'Q': 20, 'R': 21, 'S': 22,
    'T': 23, 'U': 24, 'V': 25, 'W': 26, 'X': 27, 'Y': 28, 'Z': 29,
    '1': 30, '2': 31, '3': 32, '4': 33, '5': 34, '6': 35,
    '7': 36, '8': 37, '9': 38, '0': 39,
    'ENTER': 40, 'RETURN': 40, 'ESC': 41, 'BACKSPACE': 42,
    'TAB': 43, 'SPACE': 44, ' ': 44,
    'MINUS': 45, 'EQUALS': 46, '[': 47, ']': 48, '\\': 49,
    ';': 51, "'": 52, '`': 53, ',': 54, '.': 55, '/': 56,
    'CAPSLOCK': 57,
    'F1': 58, 'F2': 59, 'F3': 60, 'F4': 61, 'F5': 62, 'F6': 63,
    'F7': 64, 'F8': 65, 'F9': 66, 'F10': 67, 'F11': 68, 'F12': 69,
    'PRINT': 70, 'SCROLL': 71, 'PAUSE': 72,
    'INSERT': 73, 'HOME': 74, 'PAGEUP': 75, 'DELETE': 76,
    'END': 76, 'PAGEDOWN': 77,
    'RIGHT': 79, 'LEFT': 80, 'DOWN': 81, 'UP': 82,
    'KP0': 98, 'KP1': 89, 'KP2': 90, 'KP3': 91, 'KP4': 92,
    'KP5': 93, 'KP6': 94, 'KP7': 95, 'KP8': 96, 'KP9': 97,
}

# 修饰键
MODS = {
    'CTRL': 1, 'SHIFT': 2, 'ALT': 4, 'GUI': 8,
}

# 小键盘码
NUMPAD = {
    '0': 98, '1': 89, '2': 90, '3': 91, '4': 92,
    '5': 93, '6': 94, '7': 95, '8': 96, '9': 97,
}

# ============== 第五阶段：命令处理 ==============
def send_key(mod=0, key=0):
    """发送按键"""
    if kb_device:
        try:
            report = bytes([mod, 0, key, 0, 0, 0, 0, 0])
            kb_device.write(report)
            time.sleep_ms(10)
            kb_device.write(bytes(8))
            return True
        except Exception as e:
            print(f"Key error: {e}")
    return False

def type_unicode(codepoint):
    """Alt+Numpad 输入 Unicode"""
    decimal = str(codepoint)
    
    # 按住 Alt
    send_key(4, 0)
    time.sleep_ms(20)
    
    for d in decimal:
        if d in NUMPAD:
            # 按下数字
            send_key(4, NUMPAD[d])
            time.sleep_ms(10)
            # 释放数字（保持 Alt）
            send_key(4, 0)
            time.sleep_ms(10)
    
    # 释放 Alt
    send_key(0, 0)
    time.sleep_ms(50)

def process_command(msg):
    """处理收到的命令"""
    print(f"Processing: {msg}")
    
    if not msg:
        return
    
    # 解析命令
    if ':' in msg:
        cmd, params = msg.split(':', 1)
    else:
        cmd = msg
        params = ""
    
    cmd = cmd.upper()
    
    # 执行命令
    if cmd == "TEXT":
        # 输入文本
        for c in params:
            if c == '\n':
                send_key(0, KEYS.get('ENTER', 40))
            elif ord(c) < 128:
                # ASCII 字符
                key = KEYS.get(c.upper(), KEYS.get(c))
                if key:
                    mod = 2 if c.isupper() or c in '!@#$%^&*()_+{}|:"~<>?' else 0
                    send_key(mod, key)
                else:
                    # 特殊字符映射
                    special = {
                        '!': (2, 30), '@': (2, 31), '#': (2, 32),
                        '$': (2, 33), '%': (2, 34), '^': (2, 35),
                        '&': (2, 36), '*': (2, 37), '(': (2, 38),
                        ')': (2, 39), '_': (2, 45), '+': (2, 46),
                        '{': (2, 47), '}': (2, 48), '|': (2, 49),
                        ':': (2, 51), '"': (2, 52), '~': (2, 53),
                        '<': (2, 54), '>': (2, 55), '?': (2, 56),
                    }
                    if c in special:
                        mod, key = special[c]
                        send_key(mod, key)
            else:
                # Unicode 字符
                type_unicode(ord(c))
            time.sleep_ms(5)
    
    elif cmd == "KEY":
        key = params.upper()
        if key in KEYS:
            send_key(0, KEYS[key])
    
    elif cmd == "MOD":
        parts = params.upper().split('+')
        if len(parts) >= 2:
            mod = 0
            for p in parts[:-1]:
                mod |= MODS.get(p, 0)
            key = parts[-1]
            if key in KEYS:
                send_key(mod, KEYS[key])

# ============== 第六阶段：主循环 ==============
print("")
print("====================================")
print("  READY!")
print("====================================")
print("Waiting for BLE connection...")
print("")

# 启动完成闪烁
for i in range(3):
    led.value(0)
    time.sleep(100)
    led.value(1)
    time.sleep(100)

# 主循环
while True:
    # 连接状态指示
    if conn_handle is not None:
        led.value(1)
        time.sleep_ms(100)
    else:
        # 未连接时慢闪
        led.value(1)
        time.sleep_ms(50)
        led.value(0)
        time.sleep_ms(950)
    
    gc.collect()

