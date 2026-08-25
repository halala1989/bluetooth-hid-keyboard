import serial
import time

ser = serial.Serial('COM9', 115200, timeout=1)

# 中断
ser.write(b'\x03')
time.sleep(0.5)
ser.read(ser.in_waiting)

# 尝试导入 main
print("=== 尝试导入 main ===")
ser.write(b'import main\r\n')
time.sleep(5)

# 读取输出
output = ser.read(ser.in_waiting)
print(output.decode('utf-8', errors='replace'))

print("\n=== 完成 ===")
ser.close()
