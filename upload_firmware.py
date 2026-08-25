import serial
import time
import os

ser = serial.Serial('COM9', 115200, timeout=2)

def send_cmd(cmd, wait=0.5):
    """发送命令并读取响应"""
    ser.write((cmd + '\r\n').encode())
    time.sleep(wait)
    response = ser.read(ser.in_waiting)
    return response.decode('utf-8', errors='replace')

def send_file(filename, content):
    """通过串口发送文件到 Pico W"""
    print(f"Uploading {filename}...")
    
    # 使用 base64 编码避免特殊字符问题
    import base64
    encoded = base64.b64encode(content.encode()).decode()
    
    # 分块发送
    chunk_size = 64
    chunks = [encoded[i:i+chunk_size] for i in range(0, len(encoded), chunk_size)]
    
    # 创建文件
    send_cmd(f"f = open('{filename}', 'w')")
    
    for chunk in chunks:
        send_cmd(f"f.write('{chunk}')")
    
    send_cmd("f.close()")
    print(f"  Done: {len(content)} bytes")

# 发送 Ctrl+C 和 Ctrl+D
ser.write(b'\x03\x04')
time.sleep(2)
ser.read(ser.in_waiting)

# 创建 lib 目录
print("Creating lib directory...")
send_cmd("import os")
send_cmd("try: os.mkdir('lib')")
send_cmd("except: pass")

# 读取并上传 main.py
print("\n=== Uploading main.py ===")
with open(r'C:\Users\halal\Documents\ChatGPT\标准pico W上的USB HID虚拟键盘\pico\main.py', 'r', encoding='utf-8') as f:
    main_content = f.read()
send_file('main.py', main_content)

# 读取并上传 boot.py
print("\n=== Uploading boot.py ===")
with open(r'C:\Users\halal\Documents\ChatGPT\标准pico W上的USB HID虚拟键盘\pico\boot.py', 'r', encoding='utf-8') as f:
    boot_content = f.read()
send_file('boot.py', boot_content)

# 验证文件
print("\n=== Verifying files ===")
print(send_cmd("os.listdir()"))
print(send_cmd("os.listdir('lib')"))

# 软重启
print("\n=== Rebooting ===")
ser.write(b'\x04')
time.sleep(3)

# 读取启动输出
output = ser.read(ser.in_waiting)
print(output.decode('utf-8', errors='replace'))

ser.close()
print("\n=== Upload complete! ===")
