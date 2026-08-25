import serial
import time

ser = serial.Serial('COM9', 115200, timeout=2)

# 中断当前程序
ser.write(b'\x03')
time.sleep(0.5)
ser.read(ser.in_waiting)

# 手动运行 main.py
print("=== 运行 main.py ===")
ser.write(b'exec(open("main.py").read())\r\n')
time.sleep(5)

# 读取输出
output = ser.read(ser.in_waiting)
print(output.decode('utf-8', errors='replace'))

print("\n=== 完成 ===")
ser.close()
