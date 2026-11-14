"""按 Material 3 的音调（CIE L*）体系，从品牌种子色推导完整明/暗两套色板。

方法：种子色转 CIE Lab，固定色相，按角色需要的 L* 取音调，超出 sRGB 色域时二分降饱和度。
Material 3 的 tone 定义就是 L*（HCT 的 T），这里用 CIE Lab 近似：小色度处与 HCT 的差别可忽略。
输出前对关键 on/容器 配对做 WCAG 对比度校验。
"""

import math

SEED = "#D85A76"


def hex_to_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))


def srgb_to_linear(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def linear_to_srgb(c):
    return 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055


def rgb_to_xyz(rgb):
    r, g, b = (srgb_to_linear(c) for c in rgb)
    return (
        r * 0.4124564 + g * 0.3575761 + b * 0.1804375,
        r * 0.2126729 + g * 0.7151522 + b * 0.0721750,
        r * 0.0193339 + g * 0.1191920 + b * 0.9503041,
    )


WP = (0.95047, 1.0, 1.08883)


def xyz_to_lab(xyz):
    def f(t):
        return t ** (1 / 3) if t > 216 / 24389 else (841 / 108) * t + 4 / 29
    fx, fy, fz = (f(xyz[i] / WP[i]) for i in range(3))
    return (116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))


def lab_to_xyz(lab):
    L, a, b = lab
    fy = (L + 16) / 116
    fx = fy + a / 500
    fz = fy - b / 200

    def finv(t):
        return t ** 3 if t ** 3 > 216 / 24389 else (t - 4 / 29) * 108 / 841
    return (finv(fx) * WP[0], finv(fy) * WP[1], finv(fz) * WP[2])


def xyz_to_rgb(xyz):
    x, y, z = xyz
    r = x * 3.2404542 + y * -1.5371385 + z * -0.4985314
    g = x * -0.9692660 + y * 1.8760108 + z * 0.0415560
    b = x * 0.0556434 + y * -0.2040259 + z * 1.0572252
    return tuple(linear_to_srgb(v) for v in (r, g, b))


def in_gamut(rgb, eps=1e-4):
    return all(-eps <= c <= 1 + eps for c in rgb)


def to_hex(rgb):
    return "#" + "".join(f"{round(max(0.0, min(1.0, c)) * 255):02X}" for c in rgb)


def lab_to_hex(lab):
    return to_hex(xyz_to_rgb(lab_to_xyz(lab)))


def tone(hue_deg, chroma, L):
    """固定色相/色度，取目标 L*；不可表示时逐步降饱和。"""
    hue = math.radians(hue_deg)
    c = chroma
    while c >= 0:
        cand = (L, math.cos(hue) * c, math.sin(hue) * c)
        if in_gamut(xyz_to_rgb(lab_to_xyz(cand))):
            return lab_to_hex(cand)
        c -= 0.5
    return lab_to_hex((L, 0.0, 0.0))


def luminance(rgb):
    r, g, b = (srgb_to_linear(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(hex_a, hex_b):
    la, lb = luminance(hex_to_rgb(hex_a)), luminance(hex_to_rgb(hex_b))
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


seed_lab = xyz_to_lab(rgb_to_xyz(hex_to_rgb(SEED)))
hue = math.degrees(math.atan2(seed_lab[2], seed_lab[1]))
chroma = math.hypot(seed_lab[1], seed_lab[2])
print(f"seed {SEED}: L*={seed_lab[0]:.1f} C={chroma:.1f} h={hue:.0f}deg")

P = lambda L: tone(hue, chroma, L)
S = lambda L: tone(hue, 16, L)
T = lambda L: tone(hue + 60, 28, L)
E = lambda L: tone(28, 60, L)
N = lambda L: tone(hue, 4, L)
NV = lambda L: tone(hue, 8, L)

light = {
    "primary": P(40), "onPrimary": P(100), "primaryContainer": P(90), "onPrimaryContainer": P(10),
    "secondary": S(40), "onSecondary": S(100), "secondaryContainer": S(90), "onSecondaryContainer": S(10),
    "tertiary": T(40), "onTertiary": T(100), "tertiaryContainer": T(90), "onTertiaryContainer": T(10),
    "error": E(40), "onError": E(100), "errorContainer": E(90), "onErrorContainer": E(10),
    "background": N(99), "onBackground": N(10), "surface": N(99), "onSurface": N(10),
    "surfaceVariant": NV(90), "onSurfaceVariant": NV(30),
    "outline": NV(50), "outlineVariant": NV(80),
    "inverseSurface": N(20), "inverseOnSurface": N(95), "inversePrimary": P(80),
    "scrim": N(0), "surfaceBright": N(98), "surfaceDim": N(87),
    "surfaceContainerLowest": N(100), "surfaceContainerLow": N(96),
    "surfaceContainer": N(94), "surfaceContainerHigh": N(92), "surfaceContainerHighest": N(90),
}

dark = {
    "primary": P(80), "onPrimary": P(20), "primaryContainer": P(30), "onPrimaryContainer": P(90),
    "secondary": S(80), "onSecondary": S(20), "secondaryContainer": S(30), "onSecondaryContainer": S(90),
    "tertiary": T(80), "onTertiary": T(20), "tertiaryContainer": T(30), "onTertiaryContainer": T(90),
    "error": E(80), "onError": E(20), "errorContainer": E(30), "onErrorContainer": E(90),
    "background": N(0), "onBackground": N(90), "surface": N(0), "onSurface": N(90),
    "surfaceVariant": NV(30), "onSurfaceVariant": NV(80),
    "outline": NV(60), "outlineVariant": NV(30),
    "inverseSurface": N(90), "inverseOnSurface": N(20), "inversePrimary": P(40),
    "scrim": N(0), "surfaceBright": N(24), "surfaceDim": N(6),
    "surfaceContainerLowest": N(4), "surfaceContainerLow": N(8),
    "surfaceContainer": N(12), "surfaceContainerHigh": N(17), "surfaceContainerHighest": N(22),
}

pairs = [
    ("primary", "onPrimary"), ("primaryContainer", "onPrimaryContainer"),
    ("secondaryContainer", "onSecondaryContainer"), ("tertiaryContainer", "onTertiaryContainer"),
    ("errorContainer", "onErrorContainer"), ("surface", "onSurface"),
    ("surfaceVariant", "onSurfaceVariant"), ("surfaceContainerHigh", "onSurface"),
    ("surfaceContainer", "onSurfaceVariant"), ("surface", "error"),
    ("surfaceContainerLow", "onSurface"), ("surface", "primary"),
]

for name, roles in (("LIGHT", light), ("DARK", dark)):
    print(f"\n=== {name}")
    for k, v in roles.items():
        print(f"    {k} = 0xFF{v[1:]},")
    print(f"--- {name} contrast")
    for fg, bg in pairs:
        c = contrast(roles[fg], roles[bg])
        flag = "AA " if c >= 4.5 else ("UI " if c >= 3 else "LOW")
        print(f"    {flag} {c:5.2f}:1  {fg} on {bg}")
