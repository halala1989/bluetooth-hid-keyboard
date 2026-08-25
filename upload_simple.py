import serial
import time
import base64

ser = serial.Serial('COM9', 115200, timeout=2)

def send_cmd(cmd, wait=0.3):
    ser.write((cmd + '\r\n').encode())
    time.sleep(wait)
    return ser.read(ser.in_waiting).decode('utf-8', errors='replace')

# 中断并重启
ser.write(b'\x03\x04')
time.sleep(2)
ser.read(ser.in_waiting)

# 读取 main.py 内容
with open(r'C:\Users\halal\Documents\ChatGPT\标准pico W上的USB HID虚拟键盘\pico\main.py', 'r', encoding='utf-8') as f:
    content = f.read()

print(f"main.py size: {len(content)} bytes")

# 使用 base64 编码上传
encoded = base64.b64encode(content.encode()).decode()

print("Uploading main.py...")

# 分块上传
chunk_size = 128
chunks = [encoded[i:i+chunk_size] for i in range(0, len(encoded), chunk_size)]

# 创建文件并写入
send_cmd("import ubinascii")
send_cmd("f = open('main.py', 'wb')")

for i, chunk in enumerate(chunks):
    send_cmd(f"f.write(ubinascii.a2b_base64('{chunk}'))")
    if i % 10 == 0:
        print(f"  Progress: {i}/{len(chunks)}")

send_cmd("f.close()")
print("Upload complete!")

# 验证
print("\nVerifying...")
print(send_cmd("import os"))
print(send_cmd("print(os.listdir())"))

# 重启
print("\nRebooting...")
ser.write(b'\x04')
time.sleep(3)

# 读取输出
output = ser.read(ser.in_waiting)
print(output.decode('utf-8', errors='replace'))

ser.close()
