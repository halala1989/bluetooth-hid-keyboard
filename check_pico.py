import serial
import time

ser = serial.Serial('COM9', 115200, timeout=1)

def send_cmd(cmd, wait=1):
    """发送命令并读取响应"""
    ser.write((cmd + '\r\n').encode())
    time.sleep(wait)
    response = ser.read(ser.in_waiting)
    return response.decode('utf-8', errors='replace')

# 发送 Ctrl+C 中断当前程序
ser.write(b'\x03')
time.sleep(0.5)

# 发送 Ctrl+D 软重启
ser.write(b'\x04')
time.sleep(2)

# 读取启动输出
print("=== Pico W 启动输出 ===")
output = ser.read(ser.in_waiting)
print(output.decode('utf-8', errors='replace'))

# 列出文件
print("\n=== 文件列表 ===")
print(send_cmd("import os"))
print(send_cmd("os.listdir()"))

# 检查 main.py 是否存在
print("\n=== 检查 main.py ===")
print(send_cmd("try:"))
print(send_cmd("    f = open('main.py')"))
print(send_cmd("    print('main.py exists')"))
print(send_cmd("    f.close()"))
print(send_cmd("except:"))
print(send_cmd("    print('main.py NOT found')"))

ser.close()
