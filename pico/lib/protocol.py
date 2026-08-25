# lib/protocol.py - BLE 通信协议解析器
# 解析手机发送的命令

class ProtocolParser:
    """BLE HID 键盘通信协议解析器"""
    
    # 支持的命令类型
    VALID_COMMANDS = ["TEXT", "KEY", "MOD", "UNI"]
    
    # 支持的按键名称
    VALID_KEYS = [
        "ENTER", "BACKSPACE", "DELETE", "TAB", "ESCAPE", "ESC",
        "CAPSLOCK", "NUMLOCK", "SCROLLLOCK",
        "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
        "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END", "PAGEUP", "PAGEDOWN",
        "INSERT", "PRINTSCREEN", "PAUSE", "MENU", "APP",
        "SPACE", "MINUS", "EQUALS", "LEFTBRACKET", "RIGHTBRACKET",
        "BACKSLASH", "SEMICOLON", "APOSTROPHE", "GRAVE", "COMMA", "PERIOD", "SLASH",
        "KP_0", "KP_1", "KP_2", "KP_3", "KP_4", "KP_5", "KP_6", "KP_7", "KP_8", "KP_9",
        "KP_DECIMAL", "KP_MULTIPLY", "KP_ADD", "KP_SUBTRACT", "KP_DIVIDE", "KP_ENTER",
    ]
    
    # 支持的修饰键
    VALID_MODIFIERS = ["CTRL", "CONTROL", "SHIFT", "ALT", "GUI", "WIN", "COMMAND",
                       "LEFTCTRL", "LEFTSHIFT", "LEFTALT", "LEFTGUI",
                       "RIGHTCTRL", "RIGHTSHIFT", "RIGHTALT", "RIGHTGUI"]
    
    def __init__(self):
        pass
    
    def parse(self, message):
        """解析消息
        
        Args:
            message: 原始消息字符串
            
        Returns:
            tuple: (command_type, params, error)
        """
        # 移除首尾空白
        message = message.strip()
        
        if not message:
            return None, None, "EMPTY_MESSAGE"
        
        # 分割命令和参数
        if ":" in message:
            cmd, params = message.split(":", 1)
        else:
            cmd = message
            params = ""
        
        cmd = cmd.upper().strip()
        
        # 验证命令类型
        if cmd not in self.VALID_COMMANDS:
            return None, None, f"INVALID_CMD:{cmd}"
        
        # 验证参数
        if cmd == "KEY":
            if params.upper() not in self.VALID_KEYS:
                return None, None, f"INVALID_KEY:{params}"
        elif cmd == "MOD":
            result = self._validate_mod(params)
            if result is not None:
                return None, None, result
        elif cmd == "UNI":
            try:
                codepoint = int(params)
                if codepoint < 0 or codepoint > 0x10FFFF:
                    return None, None, f"INVALID_CODEPOINT:{params}"
            except ValueError:
                return None, None, f"INVALID_CODEPOINT:{params}"
        
        return cmd, params, None
    
    def _validate_mod(self, params):
        """验证组合键参数"""
        parts = params.split("+")
        
        if len(parts) < 2:
            return "INVALID_MOD_FORMAT"
        
        # 最后一个是按键，前面的是修饰键
        key = parts[-1].upper()
        if key not in self.VALID_KEYS:
            return f"INVALID_KEY:{key}"
        
        # 验证修饰键
        for mod in parts[:-1]:
            if mod.upper() not in self.VALID_MODIFIERS:
                return f"INVALID_MOD:{mod}"
        
        return None
    
    def format_response(self, success, message=""):
        """格式化响应"""
        if success:
            return "OK" if not message else f"OK:{message}"
        else:
            return f"ERR:{message}"
    
    def format_status(self, status):
        """格式化状态消息"""
        return f"STATUS:{status}"
