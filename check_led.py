import serial
import time

ser = serial.Serial('COM9', 115200, timeout=1)

# 中断
ser.write(b'\x03')
time.sleep(0.5)
ser.read(ser.in_waiting)

# 检查 LED 状态
print("=== 检查 LED 状态 ===")
ser.write(b'from machine import Pin\r\n')
time.sleep(0.5)
ser.write(b'led = Pin("LED", Pin.OUT)\r\n')
time.sleep(0.5)
ser.write(b'print("LED value:", led.value())\r\n')
time.sleep(0.5)

output = ser.read(ser.in_waiting)
print(output.decode('utf-8', errors='replace'))

# 检查 BLE 状态
print("\n=== 检查 BLE 状态 ===")
ser.write(b'import bluetooth\r\n')
time.sleep(0.5)
ser.write(b'ble = bluetooth.BLE()\r\n')
time.sleep(0.5)
ser.write(b'print("BLE active:", ble.active())\r\n')
time.sleep(0.5)

output = ser.read(ser.in_waiting)
print(output.decode('utf-8', errors='replace'))

print("\n=== 完成 ===")
ser.close()
