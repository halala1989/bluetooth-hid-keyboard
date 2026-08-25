import serial
import time

ser = serial.Serial('COM9', 115200, timeout=1)

# 中断
ser.write(b'\x03')
time.sleep(0.5)
ser.read(ser.in_waiting)

# 运行 main.py
print("=== 运行 main.py ===")
ser.write(b'exec(open("main.py").read())\r\n')

# 等待并读取输出
start = time.time()
while time.time() - start < 10:
    if ser.in_waiting:
        data = ser.read(ser.in_waiting)
        print(data.decode('utf-8', errors='replace'), end='')
    time.sleep(0.1)

print("\n=== 完成 ===")
ser.close()
