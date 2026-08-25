import serial
import time

ser = serial.Serial('COM9', 115200, timeout=1)

# 中断
ser.write(b'\x03')
time.sleep(0.5)
ser.read(ser.in_waiting)

# 检查 BLE 广播
print("=== 检查 BLE 广播 ===")
ser.write(b'import bluetooth\r\n')
time.sleep(0.3)
ser.write(b'ble = bluetooth.BLE()\r\n')
time.sleep(0.3)

# 检查是否正在广播
ser.write(b'print("BLE active:", ble.active())\r\n')
time.sleep(0.3)

output = ser.read(ser.in_waiting)
print(output.decode('utf-8', errors='replace'))

# 重新启动广播
print("\n=== 重新启动广播 ===")
ser.write(b'ble.active(False)\r\n')
time.sleep(0.3)
ser.write(b'ble.active(True)\r\n')
time.sleep(0.3)

# 设置广播数据
ser.write(b'name = b"Pico HID Keyboard"\r\n')
time.sleep(0.2)
ser.write(b'adv = bytes([0x02, 0x01, 0x06]) + bytes([0x03, 0x03, 0x34, 0x12]) + bytes([len(name) + 1, 0x09]) + name\r\n')
time.sleep(0.2)
ser.write(b'ble.gap_advertise(100, adv)\r\n')
time.sleep(0.5)

output = ser.read(ser.in_waiting)
print(output.decode('utf-8', errors='replace'))

print("\n=== 广播已启动 ===")
print("请在手机上搜索 'Pico HID Keyboard'")

ser.close()
