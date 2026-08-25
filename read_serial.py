import serial
import time
import sys

# 连接串口
ser = serial.Serial('COM9', 115200, timeout=1)
print("Connected to COM9")

# 发送回车激活终端
ser.write(b'\r\n')
time.sleep(0.5)

# 读取输出
print("Reading output...")
print("-" * 50)

start_time = time.time()
while time.time() - start_time < 10:  # 读取 10 秒
    if ser.in_waiting:
        data = ser.read(ser.in_waiting)
        try:
            text = data.decode('utf-8', errors='replace')
            print(text, end='')
        except:
            print(f"[Binary: {data.hex()}]")
    time.sleep(0.1)

print("\n" + "-" * 50)
print("Done")

# 关闭串口
ser.close()
