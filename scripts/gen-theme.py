"""从种子色推导整套 Material 3 色板，并生成 `app/src/main/java/app/missu/ui/theme/Palettes.kt`。

方法：种子色转 CIE Lab，**固定色相、按角色所需的 L* 取音调**，超出 sRGB 色域时二分降饱和度。
Material 3 的 tone 定义就是 L*（HCT 的 T），小色度处 Lab 与 HCT 的差别可忽略，所以这里不引入
color-utilities 依赖。

本项目比官方配方固定的三件事，改色相时一并保持：
  1. 深色 `primary` 钉 tone 55（= 品牌粉本身的 L\*），压白字 3.7:1——够图形/大字，不够正文；
  2. 中性表面用**色度 2**（留一丝来源色相但基本是灰），色度 4 会明显偏粉；
  3. 深色底取 tone 6，纯黑另由 `withPureBlack()` 覆盖。

换色相时只换 hue：tone 阶梯与各家族色度都不变，因此**每套色板的对比度与品牌色板逐位相同**，
不需要为每套重新验算对比度，只需要验"色相本身好不好看"。

用法（一次性的，产物已入库，不参与构建）：
    python scripts/gen-theme.py
脚本会把品牌色板的产物与 `BRAND_SNAPSHOT` 逐项比对，不一致就报错退出——那是有人手改了
Palettes.kt 却没回改公式，或者公式漂了。
"""

import math
import os
import sys

# ---- 色源 -------------------------------------------------------------------
# 第一项是品牌色（默认），其余只提供色相；色度统一取品牌色的色度，这样饱和度观感一致。
SEEDS = [
    ("BRAND", "#D85A76", "品牌粉（默认）"),
    ("VIOLET", "#8E24AA", "紫色"),
    ("BLUE", "#1565C0", "蓝色"),
    ("TEAL", "#00796B", "青绿"),
    ("AMBER", "#E08A2B", "琥珀"),
]

OUT_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "main", "java", "app", "missu", "ui", "theme", "Palettes.kt",
)

# 家族 → (色相偏移, 色度)。P 的色度在运行时用品牌色的色度替换。
FAMILIES = {
    "P": (0, None),      # primary：品牌色本身
    "S": (0, 16),        # secondary：同色相、低饱和
    "T": (60, 28),       # tertiary：+60° 对比强调
    "E": (None, 60),     # error：钉住红色，不跟着色相走
    "N": (0, 2),         # neutral：表面
    "NV": (0, 5),        # neutral-variant：分隔线与次要文字
}
ERROR_HUE = 28

# 深色 primary 的音调固定成品牌粉的 L*（见文件头第 1 条），onPrimary 固定白字。
DARK_PRIMARY_TONE = 55.3
DARK_PRIMARY_ON = "#FFFFFF"

# ---- 颜色数学 ---------------------------------------------------------------


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


# ---- 角色表 -----------------------------------------------------------------
# 顺序即 Kotlin 里的输出顺序，和 lightColorScheme/darkColorScheme 的参数顺序一致。
LIGHT_SPEC = [
    ("primary", "P", 40), ("onPrimary", "P", 100),
    ("primaryContainer", "P", 90), ("onPrimaryContainer", "P", 10),
    ("secondary", "S", 40), ("onSecondary", "S", 100),
    ("secondaryContainer", "S", 90), ("onSecondaryContainer", "S", 10),
    ("tertiary", "T", 40), ("onTertiary", "T", 100),
    ("tertiaryContainer", "T", 90), ("onTertiaryContainer", "T", 10),
    ("error", "E", 40), ("onError", "E", 100),
    ("errorContainer", "E", 90), ("onErrorContainer", "E", 10),
    ("background", "N", 99), ("onBackground", "N", 10),
    ("surface", "N", 99), ("onSurface", "N", 10),
    ("surfaceVariant", "NV", 90), ("onSurfaceVariant", "NV", 30),
    ("outline", "NV", 50), ("outlineVariant", "NV", 80),
    ("inverseSurface", "N", 20), ("inverseOnSurface", "N", 95), ("inversePrimary", "P", 80),
    ("scrim", "N", 0), ("surfaceBright", "N", 98), ("surfaceDim", "N", 87),
    ("surfaceContainerLowest", "N", 100), ("surfaceContainerLow", "N", 96),
    ("surfaceContainer", "N", 94), ("surfaceContainerHigh", "N", 92),
    ("surfaceContainerHighest", "N", 90),
]

DARK_SPEC = [
    ("primary", "P", DARK_PRIMARY_TONE), ("onPrimary", "WHITE", 0),
    ("primaryContainer", "P", 30), ("onPrimaryContainer", "P", 90),
    ("secondary", "S", 80), ("onSecondary", "S", 20),
    ("secondaryContainer", "S", 30), ("onSecondaryContainer", "S", 90),
    ("tertiary", "T", 80), ("onTertiary", "T", 20),
    ("tertiaryContainer", "T", 30), ("onTertiaryContainer", "T", 90),
    ("error", "E", 80), ("onError", "E", 20),
    ("errorContainer", "E", 30), ("onErrorContainer", "E", 90),
    ("background", "N", 6), ("onBackground", "N", 90),
    ("surface", "N", 6), ("onSurface", "N", 90),
    ("surfaceVariant", "NV", 30), ("onSurfaceVariant", "NV", 80),
    ("outline", "NV", 60), ("outlineVariant", "NV", 30),
    ("inverseSurface", "N", 90), ("inverseOnSurface", "N", 20), ("inversePrimary", "P", 40),
    ("scrim", "N", 0), ("surfaceBright", "N", 24), ("surfaceDim", "N", 6),
    ("surfaceContainerLowest", "N", 4), ("surfaceContainerLow", "N", 10),
    ("surfaceContainer", "N", 12), ("surfaceContainerHigh", "N", 17),
    ("surfaceContainerHighest", "N", 22),
]

# 品牌色板的历史产物：公式一旦漂了，这里立刻炸。值必须与 Palettes.kt 里的 Brand* 一致。
BRAND_SNAPSHOT = {
    "LIGHT": {
        "primary": "#AA2F51", "onPrimary": "#FFFFFF",
        "primaryContainer": "#FED9DE", "onPrimaryContainer": "#3F0115",
        "secondary": "#79555A", "onSecondary": "#FFFFFF",
        "secondaryContainer": "#FFD9DE", "onSecondaryContainer": "#301218",
        "tertiary": "#795833", "onTertiary": "#FFFFFF",
        "tertiaryContainer": "#FFDCBC", "onTertiaryContainer": "#281801",
        "error": "#B02C34", "onError": "#FFFFFF",
        "errorContainer": "#FEDAD7", "onErrorContainer": "#410100",
        "background": "#FEFBFC", "onBackground": "#1E1B1B",
        "surface": "#FEFBFC", "onSurface": "#1E1B1B",
        "surfaceVariant": "#EDDFE1", "onSurfaceVariant": "#4F4445",
        "outline": "#807476", "outlineVariant": "#D1C3C5",
        "inverseSurface": "#342F30", "inverseOnSurface": "#F5EFF0", "inversePrimary": "#FEB2BD",
        "scrim": "#000000", "surfaceBright": "#FEF8F9", "surfaceDim": "#DED9D9",
        "surfaceContainerLowest": "#FFFFFF", "surfaceContainerLow": "#F8F2F3",
        "surfaceContainer": "#F2ECED", "surfaceContainerHigh": "#ECE7E7",
        "surfaceContainerHighest": "#E7E1E2",
    },
    "DARK": {
        "primary": "#D85A76", "onPrimary": "#FFFFFF",
        "primaryContainer": "#8D0B3A", "onPrimaryContainer": "#FED9DE",
        "secondary": "#E6BCC1", "onSecondary": "#47272D",
        "secondaryContainer": "#5F3D43", "onSecondaryContainer": "#FFD9DE",
        "tertiary": "#E9BF96", "onTertiary": "#462A06",
        "tertiaryContainer": "#5F401D", "onTertiaryContainer": "#FFDCBC",
        "error": "#FFB4AD", "onError": "#680113",
        "errorContainer": "#92031E", "onErrorContainer": "#FEDAD7",
        "background": "#171213", "onBackground": "#E7E1E2",
        "surface": "#171213", "onSurface": "#E7E1E2",
        "surfaceVariant": "#4F4445", "onSurfaceVariant": "#D1C3C5",
        "outline": "#9A8E8F", "outlineVariant": "#4F4445",
        "inverseSurface": "#E7E1E2", "inverseOnSurface": "#342F30", "inversePrimary": "#AA2F51",
        "scrim": "#000000", "surfaceBright": "#3C3839", "surfaceDim": "#171213",
        "surfaceContainerLowest": "#120D0D", "surfaceContainerLow": "#1E1B1B",
        "surfaceContainer": "#221F1F", "surfaceContainerHigh": "#2D2929",
        "surfaceContainerHighest": "#383434",
    },
}

CONTRAST_PAIRS = [
    ("primary", "onPrimary"), ("primaryContainer", "onPrimaryContainer"),
    ("secondaryContainer", "onSecondaryContainer"), ("tertiaryContainer", "onTertiaryContainer"),
    ("errorContainer", "onErrorContainer"), ("surface", "onSurface"),
    ("surfaceVariant", "onSurfaceVariant"), ("surfaceContainerHigh", "onSurface"),
    ("surfaceContainer", "onSurfaceVariant"), ("surface", "error"),
    ("surfaceContainerLow", "onSurface"), ("surface", "primary"),
]


def build_scheme(seed_hex, spec, primary_chroma):
    seed_lab = xyz_to_lab(rgb_to_xyz(hex_to_rgb(seed_hex)))
    hue = math.degrees(math.atan2(seed_lab[2], seed_lab[1]))
    out = {}
    for name, family, L in spec:
        if family == "WHITE":
            out[name] = DARK_PRIMARY_ON
            continue
        offset, chroma = FAMILIES[family]
        if family == "E":
            out[name] = tone(ERROR_HUE, chroma, L)
        else:
            out[name] = tone(hue + offset, primary_chroma if chroma is None else chroma, L)
    return out


def kotlin_name(seed_key, mode):
    return seed_key.capitalize() + ("Light" if mode == "LIGHT" else "Dark")


def emit_kotlin(palettes):
    """palettes: [(key, seed, note, {"LIGHT": {...}, "DARK": {...}})]"""
    lines = []
    lines.append("package app.missu.ui.theme")
    lines.append("")
    lines.append("import androidx.compose.material3.ColorScheme")
    lines.append("import androidx.compose.material3.darkColorScheme")
    lines.append("import androidx.compose.material3.lightColorScheme")
    lines.append("import androidx.compose.ui.graphics.Color")
    lines.append("import app.missu.data.store.ColorSource")
    lines.append("")
    lines.append("/*")
    lines.append(" * 全部色板。**由 `scripts/gen-theme.py` 生成，不要手改**：改色相/音调请改脚本再重跑。")
    lines.append(" *")
    lines.append(" * NOTE: 五套色板是同一个公式换色相推出来的——tone 阶梯与各家族色度完全相同，所以")
    lines.append(" * 每套的对比度与品牌色板逐位一致。深色 `primary` 钉在品牌粉的 L*（tone 55）上，")
    lines.append(" * 压白字 3.7:1：够图形与大字的 3:1，不够正文级 4.5:1，所以这个色只用于图形强调。")
    lines.append(" * 浅色 `primary` 取 tone 40，白字 6.5:1。")
    lines.append(" *")
    lines.append(" * NOTE: 中性表面用色度 2——留一丝来源色相但基本是灰，观感与官方基线（深色 surface")
    lines.append(" * `#141218`，通道差 6）同一量级；色度取 4 时会明显偏粉，是试过才知道的坑。")
    lines.append(" * NOTE: 深色底取 neutral tone 6，不是纯黑：OLED 上黑底与亮像素相邻会拖影，纯黑也留不出")
    lines.append(" * 「更亮的容器」这一档。想省电的人可在设置里切「纯黑背景」，见 [withPureBlack]。")
    lines.append(" */")
    for key, seed, note, schemes in palettes:
        lines.append(f"/** {note}，种子 `{seed}`。 */")
        for mode, fn in (("LIGHT", "lightColorScheme"), ("DARK", "darkColorScheme")):
            lines.append(f"private val {kotlin_name(key, mode)} = {fn}(")
            for name, value in schemes[mode].items():
                lines.append(f"    {name} = Color(0xFF{value[1:]}),")
            lines.append(")")
            lines.append("")
    lines.append("/**")
    lines.append(" * 色源 → 静态色板。")
    lines.append(" *")
    lines.append(" * NOTE: 壁纸取色没有静态色板（由系统按壁纸生成，见 `resolveColorScheme`）；这一支")
    lines.append(" * 只是兜底，取品牌色板。低版本没有系统色板，设置页也不会给出那块色块。")
    lines.append(" */")
    lines.append("internal fun ColorSource.staticColorScheme(darkTheme: Boolean): ColorScheme = when (this) {")
    lines.append("    ColorSource.WALLPAPER -> if (darkTheme) BrandDark else BrandLight")
    for key, seed, note, schemes in palettes:
        lines.append(f"    ColorSource.{key} -> if (darkTheme) {kotlin_name(key, 'DARK')} else {kotlin_name(key, 'LIGHT')}")
    lines.append("}")
    lines.append("")
    lines.append("/**")
    lines.append(" * 纯黑背景：只换表面那几档，强调色与容器梯度顺序不变。")
    lines.append(" *")
    lines.append(" * NOTE: 这几档色值对每套色板都通用——它们是色度 2 的近黑，来源色相在 tone < 10 处已经")
    lines.append(" * 看不出来（通道差 1~2）。纯黑上容器梯度整体下移两档，否则容器和底色差太小。")
    lines.append(" */")
    lines.append("internal fun ColorScheme.withPureBlack(): ColorScheme = copy(")
    lines.append("    background = Color(0xFF000000),")
    lines.append("    surface = Color(0xFF000000),")
    lines.append("    surfaceDim = Color(0xFF0D0606),")
    lines.append("    surfaceContainerLowest = Color(0xFF0D0606),")
    lines.append("    surfaceContainerLow = Color(0xFF171213),")
    lines.append("    surfaceContainer = Color(0xFF1E1B1B),")
    lines.append("    surfaceContainerHigh = Color(0xFF292525),")
    lines.append("    surfaceContainerHighest = Color(0xFF342F30),")
    lines.append(")")
    lines.append("")
    return "\n".join(lines)


def main():
    brand_lab = xyz_to_lab(rgb_to_xyz(hex_to_rgb(SEEDS[0][1])))
    brand_chroma = math.hypot(brand_lab[1], brand_lab[2])
    print(f"品牌种子 {SEEDS[0][1]}: L*={brand_lab[0]:.1f} C={brand_chroma:.1f} "
          f"h={math.degrees(math.atan2(brand_lab[2], brand_lab[1])):.0f}deg")

    palettes = []
    for key, seed, note in SEEDS:
        schemes = {
            "LIGHT": build_scheme(seed, LIGHT_SPEC, brand_chroma),
            "DARK": build_scheme(seed, DARK_SPEC, brand_chroma),
        }
        # 种子本身就在深色 primary 的音调上时直接用种子，省一次 Lab 往返（品牌粉正是这种情况，
        # 否则会得到 #D75A76 这种偏 1 的结果）。
        seed_l = xyz_to_lab(rgb_to_xyz(hex_to_rgb(seed)))[0]
        if abs(seed_l - DARK_PRIMARY_TONE) <= 0.5:
            schemes["DARK"]["primary"] = seed.upper()
        palettes.append((key, seed, note, schemes))

    # 公式自检：品牌色板必须与历史产物逐项一致（只允许 ±1 的取整差）。
    ok = True
    for mode in ("LIGHT", "DARK"):
        expected = BRAND_SNAPSHOT[mode]
        actual = palettes[0][3][mode]
        for name, want in expected.items():
            got = actual[name]
            if got != want:
                # 允许单通道 1 的取整误差（Lab 往返）。
                drift = max(abs(int(got[i:i + 2], 16) - int(want[i:i + 2], 16)) for i in (1, 3, 5))
                if drift > 1:
                    print(f"!! 品牌色板漂移 {mode}.{name}: 期望 {want}, 实际 {got}")
                    ok = False
    if not ok:
        sys.exit("品牌色板与快照不一致，先确认是公式该改还是快照该更新。")

    for key, seed, note, schemes in palettes:
        print(f"\n=== {key} ({seed}) {note}")
        for mode in ("LIGHT", "DARK"):
            roles = schemes[mode]
            worst = min(
                (contrast(roles[fg], roles[bg]), f"{fg}/{bg}")
                for fg, bg in CONTRAST_PAIRS
            )
            primary_white = contrast(roles["primary"], "#FFFFFF")
            print(f"  {mode:5s} 最低配对对比度 {worst[0]:5.2f}:1 ({worst[1]})"
                  f"  primary vs 白 {primary_white:.2f}:1")

    text = emit_kotlin(palettes)
    with open(OUT_PATH, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print(f"\n已写入 {os.path.normpath(OUT_PATH)}")


if __name__ == "__main__":
    main()
