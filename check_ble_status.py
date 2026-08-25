import serial
import time

ser = serial.Serial('COM9', 115200, timeout=1)

# 中断
ser.write(b'\x03')
time.sleep(0.5)
ser.read(ser.in_waiting)

# 检查 BLE 状态
print("=== BLE 状态检查 ===")
ser.write(b'import bluetooth\r\n')
time.sleep(0.3)
ser.write(b'ble = bluetooth.BLE()\r\n')
time.sleep(0.3)
ser.write(b'print("BLE active:", ble.active())\r\n')
time.sleep(0.3)

output = ser.read(ser.in_waiting)
print(output.decode('utf-8', errors='replace'))

# 检查 MAC 地址
print("\n=== MAC 地址 ===")
ser.write(b'mac = ble.config("mac")\r\n')
time.sleep(0.3)
ser.write(b'print("MAC:", mac)\r\n')
time.sleep(0.3)

output = ser.read(ser.in_waiting)
print(output.decode('utf-8', errors='replace'))

# 重新启动广播
print("\n=== 重新启动广播 ===")
ser.write(b'ble.active(False)\r\n')
time.sleep(0.5)
ser.write(b'ble.active(True)\r\n')
time.sleep(0.5)

# 使用更简单的广播数据
ser.write(b'name = b"Pico HID"\r\n')
time.sleep(0.2)
ser.write(b'adv = bytes([0x02, 0x01, 0x06, 0x09, 0x09]) + name\r\n')
time.sleep(0.2)
ser.write(b'ble.gap_advertise(100, adv)\r\n')
time.sleep(0.5)

output = ser.read(ser.in_waiting)
print(output.decode('utf-8', errors='replace'))

print("\n=== 广播已启动 ===")
print("设备名称: Pico HID")

ser.close()
