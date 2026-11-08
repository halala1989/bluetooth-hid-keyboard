# lib/hid_keyboard.py - USB HID 键盘驱动 (修复版)
# 实现 USB HID 键盘功能

import time

# 尝试导入 usb_hid，如果失败则使用模拟模式
try:
    import usb_hid
    USB_HID_AVAILABLE = True
except ImportError:
    USB_HID_AVAILABLE = False
    print("Warning: usb_hid not available, running in test mode")

# USB HID 键盘报告描述符 (标准 6-key rollover)
KEYBOARD_REPORT_DESCRIPTOR = bytes([
    0x05, 0x01,  # Usage Page (Generic Desktop)
    0x09, 0x06,  # Usage (Keyboard)
    0xA1, 0x01,  # Collection (Application)
    
    # 修饰键（8位）
    0x05, 0x07,  # Usage Page (Key Codes)
    0x19, 0xE0,  # Usage Minimum (224) = Left Control
    0x29, 0xE7,  # Usage Maximum (231) = Right GUI
    0x15, 0x00,  # Logical Minimum (0)
    0x25, 0x01,  # Logical Maximum (1)
    0x75, 0x01,  # Report Size (1)
    0x95, 0x08,  # Report Count (8)
    0x81, 0x02,  # Input (Data, Variable, Absolute)
    
    # 保留字节
    0x95, 0x01,  # Report Count (1)
    0x75, 0x08,  # Report Size (8)
    0x81, 0x01,  # Input (Constant) - 修复: 使用 0x01 而不是 0x03
    
    # LED 输出报告
    0x05, 0x08,  # Usage Page (LEDs)
    0x19, 0x01,  # Usage Minimum (1)
    0x29, 0x05,  # Usage Maximum (5)
    0x75, 0x01,  # Report Size (1)
    0x95, 0x05,  # Report Count (5)
    0x91, 0x02,  # Output (Data, Variable, Absolute)
    
    # LED 填充
    0x95, 0x01,  # Report Count (1)
    0x75, 0x03,  # Report Size (3)
    0x91, 0x01,  # Output (Constant) - 修复: 使用 0x01
    
    # 按键数组（6个同时按键）
    0x05, 0x07,  # Usage Page (Key Codes) - 修复: 应该是 0x07
    0x19, 0x00,  # Usage Minimum (0)
    0x29, 0xFF,  # Usage Maximum (255)
    0x15, 0x00,  # Logical Minimum (0)
    0x26, 0xFF, 0x00,  # Logical Maximum (255)
    0x75, 0x08,  # Report Size (8)
    0x95, 0x06,  # Report Count (6)
    0x81, 0x00,  # Input (Data, Array)
    
    0xC0  # End Collection
])

# 键盘码映射
KEY_CODES = {
    'A': 0x04, 'B': 0x05, 'C': 0x06, 'D': 0x07, 'E': 0x08,
    'F': 0x09, 'G': 0x0A, 'H': 0x0B, 'I': 0x0C, 'J': 0x0D,
    'K': 0x0E, 'L': 0x0F, 'M': 0x10, 'N': 0x11, 'O': 0x12,
    'P': 0x13, 'Q': 0x14, 'R': 0x15, 'S': 0x16, 'T': 0x17,
    'U': 0x18, 'V': 0x19, 'W': 0x1A, 'X': 0x1B, 'Y': 0x1C,
    'Z': 0x1D,
    
    '1': 0x1E, '2': 0x1F, '3': 0x20, '4': 0x21, '5': 0x22,
    '6': 0x23, '7': 0x24, '8': 0x25, '9': 0x26, '0': 0x27,
    
    'ENTER': 0x28, 'RETURN': 0x28,
    'ESCAPE': 0x29, 'ESC': 0x29,
    'BACKSPACE': 0x2A, 'BACK': 0x2A,
    'TAB': 0x2B,
    'SPACE': 0x2C, ' ': 0x2C,
    'MINUS': 0x2D, '-': 0x2D,
    'EQUALS': 0x2E, '=': 0x2E,
    'LEFTBRACKET': 0x2F, '[': 0x2F,
    'RIGHTBRACKET': 0x30, ']': 0x30,
    'BACKSLASH': 0x31, '\\': 0x31,
    'SEMICOLON': 0x33, ';': 0x33,
    'APOSTROPHE': 0x34, "'": 0x34,
    'GRAVE': 0x35, '`': 0x35,
    'COMMA': 0x36, ',': 0x36,
    'PERIOD': 0x37, '.': 0x37,
    'SLASH': 0x38, '/': 0x38,
    'CAPSLOCK': 0x39,
    
    'F1': 0x3A, 'F2': 0x3B, 'F3': 0x3C, 'F4': 0x3D,
    'F5': 0x3E, 'F6': 0x3F, 'F7': 0x40, 'F8': 0x41,
    'F9': 0x42, 'F10': 0x43, 'F11': 0x44, 'F12': 0x45,
    
    'PRINTSCREEN': 0x46, 'SCROLLLOCK': 0x47, 'PAUSE': 0x48,
    'INSERT': 0x49, 'HOME': 0x4A, 'PAGEUP': 0x4B,
    'DELETE': 0x4C, 'END': 0x4D, 'PAGEDOWN': 0x4E,
    'RIGHT': 0x4F, 'LEFT': 0x50, 'DOWN': 0x51, 'UP': 0x52,
    'NUMLOCK': 0x53, 'MENU': 0x65,
    
    'KP_0': 0x62, 'KP_1': 0x59, 'KP_2': 0x5A, 'KP_3': 0x5B,
    'KP_4': 0x5C, 'KP_5': 0x5D, 'KP_6': 0x5E, 'KP_7': 0x5F,
    'KP_8': 0x60, 'KP_9': 0x61,
}

MODIFIER_CODES = {
    'CTRL': 0x01, 'CONTROL': 0x01,
    'SHIFT': 0x02,
    'ALT': 0x04,
    'GUI': 0x08, 'WIN': 0x08,
    'LEFTCTRL': 0x01, 'LEFTSHIFT': 0x02, 'LEFTALT': 0x04, 'LEFTGUI': 0x08,
    'RIGHTCTRL': 0x10, 'RIGHTSHIFT': 0x20, 'RIGHTALT': 0x40, 'RIGHTGUI': 0x80,
}

NUMPAD_CODES = {
    '0': 0x62, '1': 0x59, '2': 0x5A, '3': 0x5B, '4': 0x5C,
    '5': 0x5D, '6': 0x5E, '7': 0x5F, '8': 0x60, '9': 0x61,
}


class HidKeyboard:
    """USB HID 键盘"""
    
    def __init__(self):
        """初始化 USB HID 键盘"""
        self.device = None
        self.test_mode = not USB_HID_AVAILABLE
        
        if USB_HID_AVAILABLE:
            try:
                # MicroPython USB HID 初始化
                self.device = usb_hid.Device(
                    report_descriptor=KEYBOARD_REPORT_DESCRIPTOR,
                    subclass=1,
                    protocol=1,
                )
                print("USB HID keyboard device created")
            except Exception as e:
                print(f"USB HID init error: {e}")
                self.test_mode = True
        else:
            print("Running in test mode (no USB HID)")
    
    def _send_report(self, report):
        """发送 HID 报告"""
        if self.test_mode:
            print(f"TEST: Send report {[hex(b) for b in report]}")
            return
        
        try:
            self.device.write(report)
        except Exception as e:
            print(f"Report send error: {e}")
    
    def _key_to_code(self, key):
        """将按键名称转换为 HID 码"""
        key_upper = key.upper()
        if key_upper in KEY_CODES:
            return KEY_CODES[key_upper]
        if len(key) == 1 and key in KEY_CODES:
            return KEY_CODES[key]
        return None
    
    def _get_shift_for_char(self, char):
        """检查字符是否需要 Shift 键"""
        shift_chars = '!@#$%^&*()_+{}|:"~<>?'
        return char in shift_chars
    
    def _char_to_hid(self, char):
        """将 ASCII 字符转换为 HID 码和修饰键"""
        # 直接匹配
        if char in KEY_CODES:
            return 0x00, KEY_CODES[char]
        
        # 小写字母
        if char.isalpha() and char.islower():
            return 0x00, KEY_CODES.get(char.upper())
        
        # 大写字母 - 需要 Shift
        if char.isupper():
            return 0x02, KEY_CODES.get(char)
        
        # 特殊字符
        shift_map = {
            '!': '1', '@': '2', '#': '3', '$': '4', '%': '5',
            '^': '6', '&': '7', '*': '8', '(': '9', ')': '0',
            '_': '-', '+': '=', '{': '[', '}': ']', '|': '\\',
            ':': ';', '"': "'", '~': '`', '<': ',', '>': '.', '?': '/',
        }
        if char in shift_map:
            base = shift_map[char]
            if base in KEY_CODES:
                return 0x02, KEY_CODES[base]
        
        return None, None
    
    def press_key(self, key):
        """按下并释放单个按键"""
        key_code = self._key_to_code(key)
        if key_code is None:
            return f"ERR:INVALID_KEY:{key}"
        
        report = bytearray(8)
        report[2] = key_code
        self._send_report(report)
        
        time.sleep_ms(10)
        
        report = bytearray(8)
        self._send_report(report)
        
        return "OK"
    
    def press_combo(self, modifiers, key):
        """按下组合键"""
        mod_mask = 0
        for mod in modifiers:
            mod_upper = mod.upper()
            if mod_upper in MODIFIER_CODES:
                mod_mask |= MODIFIER_CODES[mod_upper]
            else:
                return f"ERR:INVALID_MOD:{mod}"
        
        key_code = self._key_to_code(key)
        if key_code is None:
            return f"ERR:INVALID_KEY:{key}"
        
        report = bytearray(8)
        report[0] = mod_mask
        report[2] = key_code
        self._send_report(report)
        
        time.sleep_ms(10)
        
        report = bytearray(8)
        self._send_report(report)
        
        return "OK"
    
    def type_char(self, char):
        """输入单个 ASCII 字符"""
        mod, key = self._char_to_hid(char)
        if key is None:
            return False
        
        report = bytearray(8)
        report[0] = mod
        report[2] = key
        self._send_report(report)
        
        time.sleep_ms(10)
        
        report = bytearray(8)
        self._send_report(report)
        
        return True
    
    def type_text(self, text):
        """输入文本字符串"""
        for char in text:
            if char == '\n':
                self.press_key("ENTER")
            elif ord(char) < 128:
                if not self.type_char(char):
                    return f"ERR:UNSUPPORTED_CHAR:{char}"
            else:
                # Unicode 字符使用 Alt+Numpad
                self.type_unicode(ord(char))
            
            time.sleep_ms(5)
        
        return "OK"
    
    def type_unicode(self, codepoint):
        """使用 Alt+Numpad 方法输入 Unicode 字符"""
        decimal_str = str(codepoint)
        
        # 按住 Left Alt
        report = bytearray(8)
        report[0] = 0x04  # Left Alt
        self._send_report(report)
        time.sleep_ms(20)
        
        # 输入十进制数字
        for digit in decimal_str:
            if digit not in NUMPAD_CODES:
                return "ERR:INVALID_DIGIT"
            
            numpad_code = NUMPAD_CODES[digit]
            
            # 按下数字键
            report = bytearray(8)
            report[0] = 0x04  # Left Alt
            report[2] = numpad_code
            self._send_report(report)
            time.sleep_ms(10)
            
            # 释放数字键（保持 Alt）
            report = bytearray(8)
            report[0] = 0x04  # Left Alt
            report[2] = 0x00
            self._send_report(report)
            time.sleep_ms(10)
        
        # 释放 Alt
        report = bytearray(8)
        self._send_report(report)
        time.sleep_ms(50)
        
        return "OK"
