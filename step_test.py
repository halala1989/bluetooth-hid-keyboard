import serial
import time

ser = serial.Serial('COM9', 115200, timeout=2)

def send_and_read(cmd, wait=1):
    ser.write((cmd + '\r\n').encode())
    time.sleep(wait)
    return ser.read(ser.in_waiting).decode('utf-8', errors='replace')

# 中断
ser.write(b'\x03')
time.sleep(0.5)
ser.read(ser.in_waiting)

print("=== 逐步执行测试 ===")

# 测试 1: LED
print("\n--- 测试 1: LED ---")
print(send_and_read("from machine import Pin"))
print(send_and_read("led = Pin('LED', Pin.OUT)"))
print(send_and_read("led.value(1)"))
print(send_and_read("led.value(0)"))

# 测试 2: BLE
print("\n--- 测试 2: BLE ---")
print(send_and_read("import bluetooth"))
print(send_and_read("ble = bluetooth.BLE()"))
print(send_and_read("ble.active(True)"))

# 测试 3: GATT
print("\n--- 测试 3: GATT ---")
print(send_and_read("SERVICE_UUID = 0x1234"))
print(send_and_read("CHAR_UUID = 0x1235"))
print(send_and_read("handles = ble.gatts_register_services(((bluetooth.UUID(SERVICE_UUID), ((bluetooth.UUID(CHAR_UUID), bluetooth.FLAG_WRITE | bluetooth.FLAG_NOTIFY),)),))"))
print(send_and_read("print('GATT OK:', handles)"))

# 测试 4: 广播
print("\n--- 测试 4: 广播 ---")
print(send_and_read("name = b'Pico HID Keyboard'"))
print(send_and_read("adv = bytes([0x02, 0x01, 0x06]) + bytes([0x03, 0x03, 0x34, 0x12]) + bytes([len(name) + 1, 0x09]) + name"))
print(send_and_read("ble.gap_advertise(100, adv)"))
print(send_and_read("print('Advertising started!')"))

print("\n=== 测试完成 ===")
ser.close()
