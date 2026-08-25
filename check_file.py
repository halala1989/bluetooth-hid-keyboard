import serial
import time

ser = serial.Serial('COM9', 115200, timeout=2)

# 中断
ser.write(b'\x03')
time.sleep(0.5)
ser.read(ser.in_waiting)

# 读取 main.py 内容
print("=== main.py 内容 ===")
ser.write(b'print(open("main.py").read()[:500])\r\n')
time.sleep(2)

output = ser.read(ser.in_waiting)
print(output.decode('utf-8', errors='replace'))

print("\n=== 完成 ===")
ser.close()
