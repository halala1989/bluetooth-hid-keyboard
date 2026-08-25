import serial
import time

ser = serial.Serial('COM9', 115200, timeout=1)

# 中断
ser.write(b'\x03')
time.sleep(0.5)
ser.read(ser.in_waiting)

# 检查 LED 闪烁
print("=== 检查 LED 闪烁 ===")
for i in range(5):
    ser.write(b'from machine import Pin; led = Pin("LED", Pin.OUT); print(led.value())\r\n')
    time.sleep(0.5)
    output = ser.read(ser.in_waiting)
    # 提取 LED 值
    lines = output.decode('utf-8', errors='replace').split('\n')
    for line in lines:
        if line.strip() in ['0', '1']:
            print(f"LED [{i}]: {line.strip()}")
    time.sleep(0.5)

print("\n=== 完成 ===")
ser.close()
