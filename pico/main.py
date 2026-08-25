# main.py - Pico HID Keyboard
import time
import gc
from machine import Pin

# LED 初始化
led = Pin("LED", Pin.OUT)
led.value(1)
time.sleep(500)

print("")
print("=== Pico HID Keyboard v1.0 ===")
print("")

# 全局变量
conn_handle = None
rx_buffer = b""

# 内存检查
gc.collect()
print(f"Free memory: {gc.mem_free()} bytes")

# BLE 初始化
print("Initializing BLE...")
try:
    import bluetooth
    
    ble = bluetooth.BLE()
    SERVICE_UUID = 0x1234
    CHAR_UUID = 0x1235
    
    handles = ble.gatts_register_services((
        (bluetooth.UUID(SERVICE_UUID), (
            (bluetooth.UUID(CHAR_UUID), bluetooth.FLAG_WRITE | bluetooth.FLAG_NOTIFY),
        )),
    ))
    
    char_handle = handles[0][0]
    print(f"GATT registered, handle: {char_handle}")
    
    # BLE IRQ
    def ble_irq(event, data):
        global conn_handle, rx_buffer
        
        if event == 1:  # CONNECT
            conn_handle = data[0]
            print(f"Connected: {conn_handle}")
            
        elif event == 2:  # DISCONNECT
            conn_handle = None
            print("Disconnected")
            ble.gap_advertise(100, adv_data)
            
        elif event == 3:  # WRITE
            conn, handle = data
            if handle == char_handle:
                value = ble.gatts_read(handle)
                rx_buffer += value
                if b"\n" in rx_buffer:
                    lines = rx_buffer.split(b"\n")
                    rx_buffer = lines[-1]
                    for line in lines[:-1]:
                        if line:
                            process_command(line.decode("utf-8").strip())
    
    ble.irq(ble_irq)
    
    name = b"Pico HID Keyboard"
    adv_data = (
        bytes([0x02, 0x01, 0x06]) +
        bytes([0x03, 0x03, 0x34, 0x12]) +
        bytes([len(name) + 1, 0x09]) + name
    )
    
    ble.active(True)
    ble.gap_advertise(100, adv_data)
    print("BLE advertising started!")
    
except Exception as e:
    print(f"BLE error: {e}")
    ble = None

# USB HID 初始化
print("Initializing USB HID...")
kb_device = None
try:
    import usb_hid
    
    KEYBOARD_DESC = bytes([
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01,
        0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7,
        0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
        0x05, 0x08, 0x19, 0x01, 0x29, 0x05,
        0x75, 0x01, 0x95, 0x05, 0x91, 0x02,
        0x95, 0x01, 0x75, 0x03, 0x91, 0x01,
        0x05, 0x07, 0x19, 0x00, 0x29, 0xFF,
        0x15, 0x00, 0x26, 0xFF, 0x00, 0x75, 0x08, 0x95, 0x06, 0x81, 0x00,
        0xC0
    ])
    
    kb_device = usb_hid.Device(
        report_descriptor=KEYBOARD_DESC,
        subclass=1,
        protocol=1,
    )
    print("USB HID ready!")
    
except Exception as e:
    print(f"USB HID error: {e}")

# 按键映射
KEYS = {
    'A': 4, 'B': 5, 'C': 6, 'D': 7, 'E': 8, 'F': 9, 'G': 10,
    'H': 11, 'I': 12, 'J': 13, 'K': 14, 'L': 15, 'M': 16,
    'N': 17, 'O': 18, 'P': 19, 'Q': 20, 'R': 21, 'S': 22,
    'T': 23, 'U': 24, 'V': 25, 'W': 26, 'X': 27, 'Y': 28, 'Z': 29,
    '1': 30, '2': 31, '3': 32, '4': 33, '5': 34, '6': 35,
    '7': 36, '8': 37, '9': 38, '0': 39,
    'ENTER': 40, 'BACKSPACE': 42, 'TAB': 43, 'SPACE': 44, ' ': 44,
    'ESC': 41, 'DELETE': 76, 'HOME': 74, 'END': 75,
    'UP': 82, 'DOWN': 81, 'LEFT': 80, 'RIGHT': 79,
    'F1': 58, 'F2': 59, 'F3': 60, 'F4': 61, 'F5': 62, 'F6': 63,
    'F7': 64, 'F8': 65, 'F9': 66, 'F10': 67, 'F11': 68, 'F12': 69,
}

MODS = {'CTRL': 1, 'SHIFT': 2, 'ALT': 4, 'GUI': 8}

NUMPAD = {
    '0': 98, '1': 89, '2': 90, '3': 91, '4': 92,
    '5': 93, '6': 94, '7': 95, '8': 96, '9': 97,
}

def send_key(mod=0, key=0):
    if kb_device:
        try:
            kb_device.write(bytes([mod, 0, key, 0, 0, 0, 0, 0]))
            time.sleep_ms(10)
            kb_device.write(bytes(8))
        except:
            pass

def type_unicode(codepoint):
    decimal = str(codepoint)
    send_key(4, 0)
    time.sleep_ms(20)
    for d in decimal:
        if d in NUMPAD:
            send_key(4, NUMPAD[d])
            time.sleep_ms(10)
            send_key(4, 0)
            time.sleep_ms(10)
    send_key(0, 0)
    time.sleep_ms(50)

def process_command(msg):
    print(f"CMD: {msg}")
    if not msg:
        return
    
    if ':' in msg:
        cmd, params = msg.split(':', 1)
    else:
        cmd, params = msg, ""
    
    cmd = cmd.upper()
    
    if cmd == "TEXT":
        for c in params:
            if c == '\n':
                send_key(0, 40)
            elif ord(c) < 128:
                key = KEYS.get(c.upper(), KEYS.get(c))
                if key:
                    mod = 2 if c.isupper() else 0
                    send_key(mod, key)
                else:
                    special = {'!': (2,30), '@': (2,31), '#': (2,32), '$': (2,33),
                               '%': (2,34), '^': (2,35), '&': (2,36), '*': (2,37),
                               '(': (2,38), ')': (2,39), '_': (2,45), '+': (2,46),
                               '{': (2,47), '}': (2,48), '|': (2,49), ':': (2,51),
                               '"': (2,52), '~': (2,53), '<': (2,54), '>': (2,55),
                               '?': (2,56)}
                    if c in special:
                        m, k = special[c]
                        send_key(m, k)
            else:
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

# 启动完成
print("")
print("=== READY! ===")
print("Waiting for BLE connection...")
print("")

for i in range(3):
    led.value(0)
    time.sleep(100)
    led.value(1)
    time.sleep(100)

# 主循环
while True:
    if conn_handle is not None:
        led.value(1)
        time.sleep_ms(100)
    else:
        led.value(1)
        time.sleep_ms(50)
        led.value(0)
        time.sleep_ms(950)
    gc.collect()
