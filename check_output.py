import serial
import time

ser = serial.Serial('COM9', 115200, timeout=1)

# 发送回车
ser.write(b'\r\n')
time.sleep(0.5)

# 读取输出
print("=== Pico W 输出 ===")
start = time.time()
while time.time() - start < 5:
    if ser.in_waiting:
        data = ser.read(ser.in_waiting)
        print(data.decode('utf-8', errors='replace'), end='')
    time.sleep(0.1)

print("\n=== 完成 ===")
ser.close()
