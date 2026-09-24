from PIL import Image, ImageDraw, ImageFont
import os

BASE = r"C:\Users\halal\Documents\ChatGPT\标准pico W上的USB HID虚拟键盘\android\app\src\main\res"
FONT_BOLD = r"C:\Windows\Fonts\arialbd.ttf"

variants = [
    ("bt_full",    "BT",   (34, 211, 238),  False),
    ("bt_lite",    "BT",   (34, 211, 238),  True),
    ("pico_full",  "PICO", (52, 211, 153),  False),
    ("pico_lite",  "PICO", (52, 211, 153),  True),
    ("esp32_full", "ESP",  (251, 191, 36),  False),
    ("esp32_lite", "ESP",  (251, 191, 36),  True),
    ("yds3_full",  "YD",   (124, 58, 237),  False),
    ("yds3_lite",  "YD",   (124, 58, 237),  True),
]

densities = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]

def draw_icon(size, label, badge_rgb, lite):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size / 192.0  # scale factor

    # 深色圆角底 + 描边（与 App 暗色主题一致）
    radius = int(42 * s)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius,
                        fill=(15, 23, 42, 255), outline=(51, 65, 85, 255), width=max(1, int(2 * s)))
    # 顶部高光
    d.rounded_rectangle([int(10*s), int(8*s), size - int(10*s), int(70*s)], radius=int(28*s),
                        fill=(30, 41, 59, 140))

    # 顶部标签胶囊
    font_badge = ImageFont.truetype(FONT_BOLD, int(30 * s))
    tb = d.textbbox((0, 0), label, font=font_badge)
    tw, th = tb[2] - tb[0], tb[3] - tb[1]
    pad_x = int(16 * s)
    by0 = int(16 * s)
    by1 = by0 + int(42 * s)
    bx0 = (size - tw) / 2 - pad_x
    bx1 = (size + tw) / 2 + pad_x
    d.rounded_rectangle([bx0, by0, bx1, by1], radius=(by1 - by0) / 2, fill=badge_rgb + (255,))
    d.text(((size - tw) / 2 - tb[0], by0 + ((by1 - by0) - th) / 2 - tb[1]), label,
           font=font_badge, fill=(3, 20, 26, 255))

    # 键盘图形
    kw, kh = 122 * s, 74 * s
    kx, ky = (size - kw) / 2, 92 * s
    d.rounded_rectangle([kx, ky, kx + kw, ky + kh], radius=12 * s,
                        fill=(248, 250, 252, 255))
    # 键帽：3 行
    key_cols = 6
    key_w = (kw - 16 * s) / key_cols
    key_h = (kh - 22 * s) / 4
    for row in range(3):
        for col in range(key_cols):
            x0 = kx + 8 * s + col * key_w + 1.5 * s
            y0 = ky + 6 * s + row * (key_h + 3 * s)
            d.rounded_rectangle([x0, y0, x0 + key_w - 3 * s, y0 + key_h],
                                radius=2.5 * s, fill=(148, 163, 184, 255))
    # 空格键
    sw = key_w * 2.4
    sx = (size - sw) / 2
    sy = ky + 6 * s + 3 * (key_h + 3 * s)
    d.rounded_rectangle([sx, sy, sx + sw, sy + key_h], radius=2.5 * s, fill=(148, 163, 184, 255))

    # 简明版：底部 LITE 标记
    if lite:
        font_lite = ImageFont.truetype(FONT_BOLD, int(18 * s))
        txt = "LITE"
        tb2 = d.textbbox((0, 0), txt, font=font_lite)
        tw2, th2 = tb2[2] - tb2[0], tb2[3] - tb2[1]
        lx = (size - tw2) / 2
        ly = size - 34 * s
        d.rounded_rectangle([lx - 8 * s, ly - 4 * s, lx + tw2 + 8 * s, ly + th2 + 6 * s],
                            radius=9 * s, fill=(51, 65, 85, 255))
        d.text((lx - tb2[0], ly - tb2[1]), txt, font=font_lite, fill=(203, 213, 225, 255))
    return img

for name, label, color, lite in variants:
    for dens, px in densities:
        folder = os.path.join(BASE, "mipmap-" + dens)
        os.makedirs(folder, exist_ok=True)
        img = draw_icon(px, label, color, lite)
        img.save(os.path.join(folder, "ic_launcher_%s.png" % name))
        img.save(os.path.join(folder, "ic_launcher_%s_round.png" % name))
print("icons generated:", len(variants), "variants x", len(densities), "densities")
