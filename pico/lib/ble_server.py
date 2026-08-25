# lib/ble_server.py - BLE GATT 服务器 (修复版)
# 正确实现 MicroPython BLE GATT 服务

import bluetooth
import struct
from micropython import const

# IRQ 事件常量
IRQ_CENTRAL_CONNECT = const(1)
IRQ_CENTRAL_DISCONNECT = const(2)
IRQ_GATTS_WRITE = const(3)

# BLE 广播标志
_ADV_TYPE_FLAGS = const(0x01)
_ADV_TYPE_UUID16_COMPLETE = const(0x03)
_ADV_TYPE_NAME_COMPLETE = const(0x09)

# 自定义服务 UUID (16-bit)
SERVICE_UUID = const(0x1234)
CMD_CHAR_UUID = const(0x1235)
NOTIFY_CHAR_UUID = const(0x1236)

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
        
        # 设置 IRQ 处理器
        self.ble.irq(self._irq_handler)
        
        # 生成 UUID 对象
        self.service_uuid = bluetooth.UUID(SERVICE_UUID)
        self.cmd_uuid = bluetooth.UUID(CMD_CHAR_UUID)
        self.notify_uuid = bluetooth.UUID(NOTIFY_CHAR_UUID)
        
        # 定义 GATT 服务
        self._setup_gatt()
    
    def _setup_gatt(self):
        """设置 GATT 服务结构"""
        # 定义特征值
        self.cmd_char = (
            self.cmd_uuid,
            bluetooth.FLAG_WRITE | bluetooth.FLAG_READ,
        )
        
        self.notify_char = (
            self.notify_uuid,
            bluetooth.FLAG_READ | bluetooth.FLAG_NOTIFY,
        )
        
        # 定义服务
        self.service = (
            self.service_uuid,
            (self.cmd_char, self.notify_char),
        )
        
        # 注册服务
        self.services = (self.service,)
        
        # 注册 GATT 服务并获取 handles
        handles = self.ble.gatts_register_services(self.services)
        
        # 保存 handles
        self.cmd_handle = handles[0][0]  # CMD 特征值 handle
        self.notify_handle = handles[0][1]  # NOTIFY 特征值 handle
        
        print(f"GATT registered: cmd={self.cmd_handle}, notify={self.notify_handle}")
    
    def _irq_handler(self, event, data):
        """BLE 中断处理"""
        if event == IRQ_CENTRAL_CONNECT:
            conn_handle = data[0]
            self.connections.add(conn_handle)
            print(f"BLE connected: {conn_handle}")
            if self.on_connect:
                self.on_connect()
                
        elif event == IRQ_CENTRAL_DISCONNECT:
            conn_handle = data[0]
            self.connections.discard(conn_handle)
            print(f"BLE disconnected: {conn_handle}")
            if self.on_disconnect:
                self.on_disconnect()
            # 重新开始广播
            self.start()
            
        elif event == IRQ_GATTS_WRITE:
            conn_handle, value_handle = data
            if value_handle == self.cmd_handle:
                value = self.ble.gatts_read(value_handle)
                self._handle_write(conn_handle, value)
    
    def _handle_write(self, conn_handle, value):
        """处理写入事件"""
        self.rx_buffer += value
        
        # 检查是否收到完整消息（以换行符结尾）
        if b"\n" in self.rx_buffer:
            messages = self.rx_buffer.split(b"\n")
            self.rx_buffer = messages[-1]  # 保留未完成的消息
            
            for msg in messages[:-1]:
                if msg:
                    try:
                        decoded = msg.decode("utf-8").strip()
                        if decoded:
                            self._process_message(conn_handle, decoded)
                    except Exception as e:
                        print(f"Decode error: {e}")
    
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
                # 写入 notify 特征值并通知
                self.ble.gatts_write(self.notify_handle, data)
                self.ble.gatts_notify(conn_handle, self.notify_handle)
                print(f"Sent: {response}")
            except Exception as e:
                print(f"Send error: {e}")
    
    def start(self):
        """启动 BLE 广播"""
        self.ble.active(True)
        
        # 构建广播数据
        name = b"Pico HID Keyboard"
        
        # 广播包：Flags + UUID16 Service + Complete Name
        payload = (
            struct.pack("BB", 0x02, _ADV_TYPE_FLAGS) + b"\x06" +
            struct.pack("BB", 4, _ADV_TYPE_UUID16_COMPLETE) + struct.pack("<H", SERVICE_UUID) +
            struct.pack("BB", len(name) + 1, _ADV_TYPE_NAME_COMPLETE) + name
        )
        
        # 扫描响应数据
        scan_response = b""
        
        self.ble.gap_advertise(100, payload, scan_response)
        print("BLE advertising started")
    
    def stop(self):
        """停止 BLE"""
        self.ble.active(False)
        print("BLE stopped")
    
    def process(self):
        """处理 BLE 事件（在主循环中调用）"""
        pass
    
    def is_connected(self):
        """检查是否有设备连接"""
        return len(self.connections) > 0
