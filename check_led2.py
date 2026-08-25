import serial
import time

ser = serial.Serial('COM9', 115200, timeout=1)

# 中断
ser.write(b'\x03')
time.sleep(0.5)
ser.read(ser.in_waiting)

# 检查 LED
print("=== LED 状态检查 ===")
ser.write(b'from machine import Pin\r\n')
time.sleep(0.3)
ser.write(b'led = Pin("LED", Pin.OUT)\r\n')
time.sleep(0.3)
ser.write(b'print("LED:", led.value())\r\n')
time.sleep(0.3)

output = ser.read(ser.in_waiting)
print(output.decode('utf-8', errors='replace'))

# 检查连接状态
print("\n=== 连接状态 ===")
ser.write(b'print("conn_handle:", conn_handle)\r\n')
time.sleep(0.3)

output = ser.read(ser.in_waiting)
print(output.decode('utf-8', errors='replace'))

print("\n=== 完成 ===")
ser.close()
