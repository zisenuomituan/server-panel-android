"""生成 launcher 图标的 PNG 兜底资源（API < 26）。

纯标准库实现，不依赖 PIL / ImageMagick：
先在 4 倍分辨率下绘制，再做盒式降采样得到抗锯齿边缘。
"""

import math
import os
import struct
import zlib

BG = (13, 16, 19)
UNIT = (35, 44, 53)
DOT = (87, 192, 138)
BAR = (106, 166, 240)

SS = 4

RES = "/root/server-panel-android/app/src/main/res"
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def write_png(path, width, height, rgba):
    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)
        raw += rgba[y * stride:(y + 1) * stride]

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def blend(buf, size, x, y, color, alpha):
    if x < 0 or y < 0 or x >= size or y >= size:
        return
    i = (y * size + x) * 4
    sr, sg, sb = buf[i] / 255.0, buf[i + 1] / 255.0, buf[i + 2] / 255.0
    sa = buf[i + 3] / 255.0
    na = alpha + sa * (1 - alpha)
    if na <= 0:
        buf[i] = buf[i + 1] = buf[i + 2] = buf[i + 3] = 0
        return
    r, g, b = color[0] / 255.0, color[1] / 255.0, color[2] / 255.0
    buf[i] = int((r * alpha + sr * sa * (1 - alpha)) / na * 255 + 0.5)
    buf[i + 1] = int((g * alpha + sg * sa * (1 - alpha)) / na * 255 + 0.5)
    buf[i + 2] = int((b * alpha + sb * sa * (1 - alpha)) / na * 255 + 0.5)
    buf[i + 3] = int(na * 255 + 0.5)


def round_rect(buf, size, x0, y0, x1, y1, r, color):
    for y in range(max(0, int(y0) - 1), min(size, int(y1) + 2)):
        for x in range(max(0, int(x0) - 1), min(size, int(x1) + 2)):
            cx = min(max(x, x0 + r), x1 - r)
            cy = min(max(y, y0 + r), y1 - r)
            d = math.hypot(x - cx, y - cy)
            cov = min(1.0, max(0.0, r - d + 0.5))
            if cov > 0:
                blend(buf, size, x, y, color, cov)


def circle(buf, size, cx, cy, r, color):
    for y in range(max(0, int(cy - r) - 1), min(size, int(cy + r) + 2)):
        for x in range(max(0, int(cx - r) - 1), min(size, int(cx + r) + 2)):
            d = math.hypot(x - cx, y - cy)
            cov = min(1.0, max(0.0, r - d + 0.5))
            if cov > 0:
                blend(buf, size, x, y, color, cov)


def downscale(buf, src, dst):
    out = bytearray(dst * dst * 4)
    n = src / dst
    for y in range(dst):
        for x in range(dst):
            pr = pg = pb = pa = 0.0
            count = 0
            for sy in range(int(y * n), int((y + 1) * n)):
                for sx in range(int(x * n), int((x + 1) * n)):
                    i = (sy * src + sx) * 4
                    a = buf[i + 3] / 255.0
                    pr += buf[i] * a
                    pg += buf[i + 1] * a
                    pb += buf[i + 2] * a
                    pa += a
                    count += 1
            o = (y * dst + x) * 4
            if pa > 0:
                out[o] = int(pr / pa + 0.5)
                out[o + 1] = int(pg / pa + 0.5)
                out[o + 2] = int(pb / pa + 0.5)
                out[o + 3] = int(pa / count * 255 + 0.5)
    return out


def render(size, round_bg):
    src = size * SS
    buf = bytearray(src * src * 4)

    if round_bg:
        circle(buf, src, src / 2.0, src / 2.0, src * 0.5, BG)
    else:
        round_rect(buf, src, 0, 0, src - 1, src - 1, src * 0.22, BG)

    def u(v):
        return v * src

    round_rect(buf, src, u(0.20), u(0.26), u(0.80), u(0.44), u(0.045), UNIT)
    round_rect(buf, src, u(0.20), u(0.56), u(0.80), u(0.74), u(0.045), UNIT)
    circle(buf, src, u(0.315), u(0.35), u(0.05), DOT)
    circle(buf, src, u(0.315), u(0.65), u(0.05), DOT)
    round_rect(buf, src, u(0.42), u(0.325), u(0.70), u(0.375), u(0.025), BAR)
    round_rect(buf, src, u(0.42), u(0.625), u(0.70), u(0.675), u(0.025), BAR)

    return downscale(buf, src, size)


def main():
    for name, size in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-" + name)
        os.makedirs(folder, exist_ok=True)
        write_png(os.path.join(folder, "ic_launcher.png"), size, size, render(size, False))
        write_png(os.path.join(folder, "ic_launcher_round.png"), size, size, render(size, True))
        print("生成", folder, size)


if __name__ == "__main__":
    main()
