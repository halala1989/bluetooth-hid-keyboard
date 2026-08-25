# main.py - Pico W 主程序
# BLE HID 虚拟键盘固件

import time
import gc
from machine import Pin, Timer

# 导入自定义模块
from lib.ble_server import BleServer
from lib.hid_keyboard import HidKeyboard
from lib.protocol import ProtocolParser

# 全局状态
led = Pin("LED", Pin.OUT)
ble_server = None
keyboard = None
protocol = None
connected = False
typing = False

def led_blink(times=3, interval=100):
    """LED 闪烁指示"""
    for _ in range(times):
        led.value(1)
        time.sleep_ms(interval)
        led.value(0)
        time.sleep_ms(interval)

def on_connect():
    """BLE 连接回调"""
    global connected
    connected = True
    print("BLE connected")
    led_blink(2, 200)

def on_disconnect():
    """BLE 断开回调"""
    global connected
    connected = False
    print("BLE disconnected")
    led_blink(5, 100)

def on_command(cmd, params):
    """处理接收到的命令"""
    global typing
    
    if typing:
        return "ERR:BUSY"
    
    typing = True
    led.value(1)
    
    try:
        if cmd == "TEXT":
            # 输入文本
            result = keyboard.type_text(params)
        elif cmd == "KEY":
            # 按键操作
            result = keyboard.press_key(params)
        elif cmd == "MOD":
            # 组合键
            parts = params.split("+")
            if len(parts) < 2:
                return "ERR:INVALID_MOD"
            modifiers = parts[:-1]
            key = parts[-1]
            result = keyboard.press_combo(modifiers, key)
        elif cmd == "UNI":
            # Unicode 字符
            codepoint = int(params)
            result = keyboard.type_unicode(codepoint)
        else:
            result = "ERR:INVALID_CMD"
    except Exception as e:
        result = f"ERR:{str(e)}"
    finally:
        typing = False
        led.value(0)
    
    return result

def main():
    """主函数"""
    global ble_server, keyboard, protocol
    
    print("=== BLE HID Keyboard ===")
    print("Initializing...")
    
    # 初始化 USB HID 键盘
    keyboard = HidKeyboard()
    print("USB HID keyboard ready")
    
    # 初始化协议解析器
    protocol = ProtocolParser()
    print("Protocol parser ready")
    
    # 初始化 BLE 服务器
    ble_server = BleServer(
        on_connect=on_connect,
        on_disconnect=on_disconnect,
        on_command=on_command
    )
    print("BLE server ready")
    
    # 启动 BLE 广播
    ble_server.start()
    print("BLE advertising...")
    
    # 启动指示
    led_blink(3, 100)
    
    print("=== Ready ===")
    print("Waiting for BLE connection...")
    
    # 主循环
    while True:
        # 处理 BLE 事件
        ble_server.process()
        
        # 内存管理
        gc.collect()
        
        # 低功耗等待
        time.sleep_ms(10)

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"Error: {e}")
        led_blink(10, 50)
