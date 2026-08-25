# lib/ble_server.py - BLE GATT 服务器
# 基于 MicroPython bluetooth 模块的 BLE 服务器实现

import bluetooth
import struct
import time
from micropython import const

# BLE 常量
_ADV_TYPE_FLAGS = const(0x01)
_ADV_TYPE_UUID16_COMPLETE = const(0x03)
_ADV_TYPE_UUID128_COMPLETE = const(0x07)
_ADV_TYPE_NAME_COMPLETE = const(0x09)
_ADV_TYPE_MANUFACTURER = const(0xFF)

# 自定义 UUID（128位）
_SERVICE_UUID = const(0x1234)
_CHAR_CMD_UUID = const(0x1235)  # 命令写入特征值
_CHAR_NOTIFY_UUID = const(0x1236)  # 状态通知特征值

class BleServer:
    """BLE GATT 服务器"""
    
    def __init__(self, on_connect=None, on_disconnect=None, on_command=None):
        self.ble = bluetooth.BLE()
        self.ble.active(False)
        
        self.on_connect = on_connect
        self.on_disconnect = on_disconnect
        self.on_command = on_command
        
        self.connections = set()
        self.rx_buffer = b""
        self.tx_buffer = b""
        
        # 配置 BLE
        self.ble.config(name="Pico HID Keyboard")
        self.ble.irq(self._irq_handler)
        
        # 构建服务数据
        self._setup广告服务()
        self._setup_gatt()
    
    def _setup广告服务(self):
        """设置广播数据"""
        name = b"Pico HID Keyboard"
        
        # 广播数据
        self.advertising = (
            struct.pack("BB", 0x02, _ADV_TYPE_FLAGS) + b"\x06" +
            struct.pack("BB", len(name) + 1, _ADV_TYPE_NAME_COMPLETE) + name
        )
    
    def _setup_gatt(self):
        """设置 GATT 服务"""
        # GATT 服务定义
        # 服务 UUID: 0x1234
        # 特征值 UUID: 0x1235 (Write), 0x1236 (Notify)
        
        self.gatt = bytearray(
            b'\x05\x18'  # 通用访问服务 UUID
            b'\x01\x28\x05\x18'  # 服务声明
            b'\x0a\x28\x01\x01\x00\x02\x00'  # 特征值声明
            b'\x05\x2a\x01\x00'  # 设备名称特征值
            b'\x06\x29\x02\x00'  # 客户端配置描述符
        )
    
    def _irq_handler(self, event, data):
        """BLE 中断处理"""
        if event == 1:  # IRQ_CENTRAL_CONNECT
            conn_handle = data[0]
            self.connections.add(conn_handle)
            print(f"BLE connected: {conn_handle}")
            if self.on_connect:
                self.on_connect()
                
        elif event == 2:  # IRQ_CENTRAL_DISCONNECT
            conn_handle = data[0]
            self.connections.discard(conn_handle)
            print(f"BLE disconnected: {conn_handle}")
            if self.on_disconnect:
                self.on_disconnect()
            # 重新开始广播
            self.start()
            
        elif event == 3:  # IRQ_GATTS_WRITE
            conn_handle, value_handle = data
            value = self.ble.gatts_read(value_handle)
            self._handle_write(conn_handle, value_handle, value)
    
    def _handle_write(self, conn_handle, value_handle, value):
        """处理写入事件"""
        self.rx_buffer += value
        
        # 检查是否收到完整消息（以换行符结尾）
        if b"\n" in self.rx_buffer:
            messages = self.rx_buffer.split(b"\n")
            self.rx_buffer = messages[-1]  # 保留未完成的消息
            
            for msg in messages[:-1]:
                if msg:
                    self._process_message(conn_handle, msg.decode("utf-8"))
    
    def _process_message(self, conn_handle, message):
        """处理接收到的消息"""
        print(f"Received: {message}")
        
        # 解析命令
        if ":" in message:
            cmd, params = message.split(":", 1)
        else:
            cmd = message
            params = ""
        
        # 执行命令
        if self.on_command:
            result = self.on_command(cmd.upper(), params)
        else:
            result = "OK"
        
        # 发送响应
        self.send_response(conn_handle, result)
    
    def send_response(self, conn_handle, response):
        """发送响应"""
        data = (response + "\n").encode("utf-8")
        if conn_handle in self.connections:
            try:
                # 通过 Notify 发送
                self.ble.gatts_notify(conn_handle, _CHAR_NOTIFY_UUID, data)
            except Exception as e:
                print(f"Send error: {e}")
    
    def start(self):
        """启动 BLE 广播"""
        self.ble.active(True)
        self.ble.gap_advertise(100, self.advertising)
        print("BLE advertising started")
    
    def stop(self):
        """停止 BLE"""
        self.ble.active(False)
        print("BLE stopped")
    
    def process(self):
        """处理 BLE 事件（在主循环中调用）"""
        # 事件已在 irq_handler 中处理
        # 此方法可用于额外处理
        pass
    
    def is_connected(self):
        """检查是否有设备连接"""
        return len(self.connections) > 0
