# lib/ble_simple.py - 最简化 BLE 服务器
# 基于 MicroPython 官方示例

import bluetooth
import struct
import time
from micropython import const

# IRQ 事件
IRQ_CENTRAL_CONNECT = const(1)
IRQ_CENTRAL_DISCONNECT = const(2)
IRQ_GATTS_WRITE = const(3)

class BLEKeyboard:
    def __init__(self):
        self.ble = bluetooth.BLE()
        self.conn_handle = None
        self.rx_buffer = b""
        
        # 回调
        self.on_receive = None
        
    def start(self, name="Pico HID"):
        """启动 BLE 广播"""
        print(f"Starting BLE as: {name}")
        
        # 注册 GATT 服务
        # 使用简单的自定义服务
        SERVICE_UUID = 0x1234
        CHAR_UUID = 0x1235
        
        # 注册服务
        handles = self.ble.gatts_register_services((
            (bluetooth.UUID(SERVICE_UUID), (
                (bluetooth.UUID(CHAR_UUID), bluetooth.FLAG_WRITE | bluetooth.FLAG_NOTIFY),
            )),
        ))
        
        self.char_handle = handles[0][0]
        print(f"GATT registered, handle: {self.char_handle}")
        
        # 设置 IRQ
        self.ble.irq(self._irq)
        
        # 启动广播
        payload = bytes([
            0x02, 0x01, 0x06,  # Flags
            0x03, 0x03, 0x34, 0x12,  # Service UUID 0x1234 (little endian)
        ]) + bytes([len(name) + 1, 0x09]) + name.encode()
        
        self.ble.active(True)
        self.ble.gap_advertise(100, payload)
        
        print("BLE advertising started")
        
    def _irq(self, event, data):
        if event == IRQ_CENTRAL_CONNECT:
            self.conn_handle = data[0]
            print(f"Connected: {self.conn_handle}")
            
        elif event == IRQ_CENTRAL_DISCONNECT:
            self.conn_handle = None
            print("Disconnected")
            # 重新开始广播
            self.ble.gap_advertise(100, self.ble.gap_advertise()[0])
            
        elif event == IRQ_GATTS_WRITE:
            conn_handle, value_handle = data
            if value_handle == self.char_handle:
                value = self.ble.gatts_read(value_handle)
                self._on_data(value)
                
    def _on_data(self, data):
        self.rx_buffer += data
        if b"\n" in self.rx_buffer:
            lines = self.rx_buffer.split(b"\n")
            self.rx_buffer = lines[-1]
            for line in lines[:-1]:
                if line and self.on_receive:
                    try:
                        msg = line.decode("utf-8").strip()
                        if msg:
                            print(f"Received: {msg}")
                            self.on_receive(msg)
                    except:
                        pass
                        
    def send(self, data):
        """发送数据"""
        if self.conn_handle is not None:
            try:
                self.ble.gatts_write(self.char_handle, data.encode())
                self.ble.gatts_notify(self.conn_handle, self.char_handle)
                return True
            except Exception as e:
                print(f"Send error: {e}")
        return False
        
    def is_connected(self):
        return self.conn_handle is not None
