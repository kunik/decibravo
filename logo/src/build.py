#!/usr/bin/env python3
"""Збирає всі файли лого DeciBravo з геометрії, описаної в цьому скрипті.

Запуск:   python3 logo/src/build.py            # пише в logo/
          python3 logo/src/build.py /інша/тека # пише в іншу теку

Потрібен Inkscape 1.x у PATH: він переводить обведення й текст у криві та
експортує PNG. Шрифт напису лежить поруч у fonts/ і в систему не
встановлюється: скрипт показує його Inkscape через тимчасовий fontconfig.

Що виходить:
  decibravo-mark-*.svg          знак «Долоні»
  decibravo-logo-*.svg          знак + напис, горизонтально
  decibravo-logo-stacked-*.svg  знак + напис, вертикально
  decibravo-wordmark-*.svg      лише напис
  decibravo-icon-*.svg          іконка застосунку на плитці
  png/                          растрові експорти
  android/res/                  адаптивна іконка Android
  decibravo-preview.png         оглядовий аркуш

Одиниці знака: зовнішній радіус кола літери 44, висота стійки 130,
базова лінія y=0, вісь симетрії x=100.
"""
import math
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.path.dirname(HERE)
FONTS = os.path.join(HERE, "fonts")

YELLOW, WHITE, BLACK = "#FFD60A", "#FFFFFF", "#000000"
COLORS = [YELLOW, WHITE, BLACK]
# схема: (літери знака, промені, «Deci», «Bravo»)
SCHEMES = {
    "color": (YELLOW, WHITE, WHITE, YELLOW),   # для темного тла
    "black": (BLACK, BLACK, BLACK, BLACK),     # для світлого й жовтого тла
    "white": (WHITE, WHITE, WHITE, WHITE),     # для фото й кольорових плашок
}

# ---------------------------------------------------------------- знак
STROKE = 26          # товщина літер
RAY_STROKE = 11      # товщина променів
RAY_ANGLES = (-64, -32, 0, 32, 64)
MARK_X0, MARK_X1, MARK_TOP = 6, 194, -191.5   # габарити знака
PAD = 16             # вільне поле навколо


def letters_path():
    """Літери d і b: два кола та дві стійки, що стоять поруч, як долоні."""
    ring = lambda c: f"M{c+31},-44 a31,31 0 1 0 -62,0 a31,31 0 1 0 62,0"
    half = STROKE / 2
    return f"{ring(50)} {ring(150)} M81,{-half:g} V{-130+half:g} M119,{-half:g} V{-130+half:g}"


def rays_path():
    """П'ять променів оплеску над стійками."""
    out = []
    for a in RAY_ANGLES:
        r = math.radians(a)
        out.append(f"M{100+22*math.sin(r):.2f},{-142-22*math.cos(r):.2f} "
                   f"L{100+44*math.sin(r):.2f},{-142-44*math.cos(r):.2f}")
    return " ".join(out)


def mark(c_letters, c_rays):
    return (f'<path d="{letters_path()}" fill="none" stroke="{c_letters}" stroke-width="{STROKE}" stroke-linecap="round"/>'
            f'<path d="{rays_path()}" fill="none" stroke="{c_rays}" stroke-width="{RAY_STROKE}" stroke-linecap="round"/>')


def mark_at(x, y, scale, c_letters, c_rays):
    return f'<g transform="translate({x:.3f},{y:.3f}) scale({scale:.5f})">{mark(c_letters, c_rays)}</g>'


# ---------------------------------------------------------------- напис
FONT_FAMILY, FONT_WEIGHT = "Barlow Condensed", 700
CAP = 0.7            # висота великих літер шрифту в частках кегля
TRACKING = 0.012     # розрядка в частках кегля
CAP_HORIZONTAL = 100 # висота великих літер у горизонтальній версії
CAP_STACKED = 62     # у вертикальній
CAP_WORDMARK = 130   # у самому написі


# ---------------------------------------------------------------- Inkscape
TO_OUTLINE = ("select-all:all;object-to-path;" + "select-all:groups;selection-ungroup;" * 3 +
              "select-all:all;object-stroke-to-path;select-all:all;path-union;"
              "export-plain-svg;export-filename:{o};export-do")
TEXT_TO_PATH = "select-all:all;object-to-path;export-plain-svg;export-filename:{o};export-do"
ELEMENT = re.compile(r'<(?:path|circle)\b[^>]*/>')


class Inkscape:
    def __init__(self, tmp):
        self.bin = shutil.which("inkscape")
        if not self.bin:
            sys.exit("Не знайдено Inkscape у PATH (потрібна версія 1.x).")
        self.tmp, self.n = tmp, 0
        conf = os.path.join(tmp, "fonts.conf")
        cache = os.path.join(tmp, "fc-cache")
        os.makedirs(cache)
        with open(conf, "w") as f:
            f.write('<?xml version="1.0"?>\n<!DOCTYPE fontconfig SYSTEM "fonts.dtd">\n'
                    f'<fontconfig><dir>{FONTS}</dir><cachedir>{cache}</cachedir></fontconfig>\n')
        self.env = dict(os.environ, FONTCONFIG_FILE=conf)

    def actions(self, source, actions):
        self.n += 1
        i = os.path.join(self.tmp, f"in{self.n}.svg")
        o = os.path.join(self.tmp, f"out{self.n}.svg")
        with open(i, "w") as f:
            f.write(source)
        subprocess.run([self.bin, i, "--actions=" + actions.format(o=o)], capture_output=True, env=self.env)
        if not os.path.exists(o):
            sys.exit(f"Inkscape не створив файл для {i}")
        return o

    def bboxes(self, path):
        q = subprocess.run([self.bin, "--query-all", path], capture_output=True, text=True, env=self.env).stdout
        return [tuple(map(float, line.split(",")[1:])) for line in q.splitlines()]

    def png(self, svg, png, **size):
        args = [self.bin, svg, "-o", png]
        for k, v in size.items():
            args += [f"-{k}", str(v)]
        subprocess.run(args, capture_output=True, env=self.env)


def head(w, h):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w:g} {h:g}" width="{w:g}" height="{h:g}">'


def path_d(tag):
    return re.sub(r'\s+', ' ', re.search(r'\sd="([^"]+)"', tag, flags=re.S).group(1)).strip()


def stroke_layers(ink, w, h, body):
    """Обведення -> одна залита крива на кожен колір."""
    layers = []
    for c in [c for c in COLORS if c in body]:
        others = [o for o in COLORS if o != c]
        layer = ELEMENT.sub(lambda m: "" if any(o in m.group(0) for o in others) else m.group(0), body)
        out = open(ink.actions(head(w, h) + layer + "</svg>", TO_OUTLINE)).read()
        paths = re.findall(r'<path\b[^>]*>', out, flags=re.S)
        assert len(paths) == 1 and "transform" not in out, (c, len(paths))
        layers.append((c, path_d(paths[0])))
    return layers


def text_source(w, h, x, baseline, size, c1, c2):
    return (head(w, h) +
            f'<text x="{x:.3f}" y="{baseline:.3f}" font-family="{FONT_FAMILY}" font-weight="{FONT_WEIGHT}" '
            f'font-size="{size:.3f}" letter-spacing="{TRACKING*size:.3f}">'
            f'<tspan fill="{c1}">Deci</tspan><tspan fill="{c2}">Bravo</tspan></text></svg>')


def text_layers(ink, w, h, x, baseline, size, c1, c2):
    """Напис -> криві, по одній на колір."""
    out = open(ink.actions(text_source(w, h, x, baseline, size, c1, c2), TEXT_TO_PATH)).read()
    assert "<text" not in out and "transform" not in out, "текст не перевівся в криві: перевірте шрифт у fonts/"
    layers = []
    for tag in re.findall(r'<path\b[^>]*>', out, flags=re.S):
        m = re.search(r'fill:(#[0-9a-fA-F]{6})', tag)
        layers.append((m.group(1).upper() if m else BLACK, path_d(tag)))
    assert 1 <= len(layers) <= 2 and all(c in (c1, c2) for c, _ in layers)
    return layers


def measure_text(ink):
    """Лівий відступ і ширина напису в частках кегля."""
    out = ink.actions(text_source(4000, 2000, 500, 1200, 1000, BLACK, BLACK), TEXT_TO_PATH)
    boxes = [b for b in ink.bboxes(out) if b[2] < 3999]
    x0 = min(b[0] for b in boxes)
    x1 = max(b[0] + b[2] for b in boxes)
    return (x0 - 500) / 1000, (x1 - x0) / 1000


def write_svg(name, w, h, layers, background=""):
    out = [head(w, h)[:-1] + ' role="img" aria-label="DeciBravo">', "<title>DeciBravo</title>"]
    if background:
        out.append(background)
    for c in COLORS:
        out += [f'<path fill="{c}" d="{d}"/>' for cc, d in layers if cc == c]
    out.append("</svg>\n")
    with open(os.path.join(OUT, name + ".svg"), "w") as f:
        f.write("\n".join(out))


# ---------------------------------------------------------------- збирання
def build_svgs(ink):
    bearing, width = measure_text(ink)
    mark_w, mark_h = MARK_X1 - MARK_X0, -MARK_TOP
    base = PAD + mark_h                      # базова лінія знака
    for key, (c_letters, c_rays, c_deci, c_bravo) in SCHEMES.items():
        # знак
        w, h = mark_w + 2 * PAD, mark_h + 2 * PAD
        write_svg(f"decibravo-mark-{key}", w, h,
                  stroke_layers(ink, w, h, mark_at(PAD - MARK_X0, base, 1, c_letters, c_rays)))

        # горизонтально: напис по центру висоти літер знака
        size = CAP_HORIZONTAL / CAP
        x = PAD + mark_w + 30
        w, h = x + width * size + PAD, mark_h + 2 * PAD
        write_svg(f"decibravo-logo-{key}", w, h,
                  stroke_layers(ink, w, h, mark_at(PAD - MARK_X0, base, 1, c_letters, c_rays)) +
                  text_layers(ink, w, h, x - bearing * size, base - 65 + CAP_HORIZONTAL / 2, size, c_deci, c_bravo))

        # вертикально: напис під знаком по центру
        size = CAP_STACKED / CAP
        w = max(width * size, mark_w) + 2 * PAD
        h = base + 30 + CAP_STACKED + PAD
        tx = PAD - bearing * size + (w - 2 * PAD - width * size) / 2
        write_svg(f"decibravo-logo-stacked-{key}", w, h,
                  stroke_layers(ink, w, h, mark_at(w / 2 - 100, base, 1, c_letters, c_rays)) +
                  text_layers(ink, w, h, tx, base + 30 + CAP_STACKED, size, c_deci, c_bravo))

        # лише напис
        size = CAP_WORDMARK / CAP
        w, h = width * size + 2 * PAD, CAP_WORDMARK + 2 * PAD
        write_svg(f"decibravo-wordmark-{key}", w, h,
                  text_layers(ink, w, h, PAD - bearing * size, PAD + CAP_WORDMARK, size, c_deci, c_bravo))

    # іконка 1024: знак займає 60 % ширини плитки
    s = 0.60 * 1024 / mark_w
    for key, bg, c_letters, c_rays in (("black", BLACK, YELLOW, WHITE), ("yellow", YELLOW, BLACK, BLACK)):
        write_svg(f"decibravo-icon-{key}", 1024, 1024,
                  stroke_layers(ink, 1024, 1024, mark_at(512 - s * 100, 512 + s * mark_h / 2, s, c_letters, c_rays)),
                  background=f'<rect width="1024" height="1024" rx="230" fill="{bg}"/>')


def build_pngs(ink):
    d = os.path.join(OUT, "png")
    os.makedirs(d, exist_ok=True)
    src = lambda n: os.path.join(OUT, n + ".svg")
    ink.png(src("decibravo-icon-black"), os.path.join(d, "decibravo-icon-black-1024.png"), w=1024)
    ink.png(src("decibravo-icon-black"), os.path.join(d, "decibravo-icon-black-512.png"), w=512)
    ink.png(src("decibravo-icon-yellow"), os.path.join(d, "decibravo-icon-yellow-1024.png"), w=1024)
    for key in SCHEMES:
        ink.png(src(f"decibravo-logo-{key}"), os.path.join(d, f"decibravo-logo-{key}-2000.png"), w=2000)
    ink.png(src("decibravo-mark-color"), os.path.join(d, "decibravo-mark-color-1024.png"), h=1024)


# Адаптивна іконка Android: полотно 108 dp, безпечна зона — коло 66 dp.
# Найменше коло навколо знака має центр (100, -83.7) і радіус 107.9.
ANDROID_SCALE = 33 / 107.9
ANDROID_TX = 54 - ANDROID_SCALE * 100
ANDROID_TY = 54 + ANDROID_SCALE * 83.7


def android_vector(c_letters, c_rays):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:scaleX="{ANDROID_SCALE:.4f}"
        android:scaleY="{ANDROID_SCALE:.4f}"
        android:translateX="{ANDROID_TX:.3f}"
        android:translateY="{ANDROID_TY:.3f}">
        <path
            android:pathData="{letters_path()}"
            android:strokeColor="{c_letters}"
            android:strokeWidth="{STROKE}"
            android:strokeLineCap="round" />
        <path
            android:pathData="{rays_path()}"
            android:strokeColor="{c_rays}"
            android:strokeWidth="{RAY_STROKE}"
            android:strokeLineCap="round" />
    </group>
</vector>
'''


def build_android():
    res = os.path.join(OUT, "android", "res")
    files = {
        "drawable/ic_launcher_foreground.xml": android_vector(YELLOW, WHITE),
        "drawable/ic_launcher_monochrome.xml": android_vector(WHITE, WHITE),
        "values/ic_launcher_background.xml": '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#000000</color>
</resources>
''',
    }
    adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''
    files["mipmap-anydpi-v26/ic_launcher.xml"] = adaptive
    files["mipmap-anydpi-v26/ic_launcher_round.xml"] = adaptive
    for rel, text in files.items():
        path = os.path.join(res, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            f.write(text)


def build_preview(ink):
    """Оглядовий аркуш: усі версії на чорному, білому, жовтому й сірому тлі."""
    def embed(name, x, y, h):
        s = open(os.path.join(OUT, name + ".svg")).read()
        vb = re.search(r'viewBox="([^"]+)"', s).group(1)
        vw, vh = map(float, vb.split()[2:])
        inner = re.sub(r'^<svg[^>]*>|</svg>\s*$', '', s.strip())
        return f'<svg x="{x}" y="{y}" width="{h*vw/vh:.1f}" height="{h:.1f}" viewBox="{vb}">{inner}</svg>'

    o = ['<rect width="1500" height="1050" fill="#000"/>',
         '<rect x="750" width="750" height="700" fill="#fff"/>',
         f'<rect y="700" width="750" height="350" fill="{YELLOW}"/>',
         '<rect x="750" y="700" width="750" height="350" fill="#2b2b2b"/>']
    for key, x0 in (("color", 0), ("black", 750)):
        o += [embed(f"decibravo-logo-{key}", x0 + 40, 30, 180),
              embed(f"decibravo-logo-stacked-{key}", x0 + 60, 250, 230),
              embed(f"decibravo-mark-{key}", x0 + 500, 250, 200),
              embed(f"decibravo-wordmark-{key}", x0 + 60, 540, 110)]
    o += [embed("decibravo-logo-black", 40, 730, 150),
          embed("decibravo-icon-black", 60, 900, 120), embed("decibravo-icon-yellow", 200, 900, 120),
          embed("decibravo-icon-black", 340, 936, 48), embed("decibravo-icon-black", 400, 960, 24),
          embed("decibravo-logo-white", 790, 730, 150),
          embed("decibravo-icon-black", 810, 900, 120), embed("decibravo-icon-yellow", 950, 900, 120)]
    # іконка Android із безпечною зоною (66 dp) і найменшою маскою (72 dp)
    t = f"translate({ANDROID_TX:.3f},{ANDROID_TY:.3f}) scale({ANDROID_SCALE:.4f})"
    o.append('<svg x="1120" y="880" width="150" height="150" viewBox="0 0 108 108"><rect width="108" height="108" fill="#000"/>'
             f'<g transform="{t}">{mark(YELLOW, WHITE)}</g>'
             '<circle cx="54" cy="54" r="33" fill="none" stroke="#f0f" stroke-width="0.4"/>'
             '<circle cx="54" cy="54" r="36" fill="none" stroke="#0ff" stroke-width="0.4"/></svg>')
    sheet = os.path.join(ink.tmp, "preview.svg")
    with open(sheet, "w") as f:
        f.write(head(1500, 1050) + "".join(o) + "</svg>")
    ink.png(sheet, os.path.join(OUT, "decibravo-preview.png"), w=1800)


def main():
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        ink = Inkscape(tmp)
        build_svgs(ink)
        build_pngs(ink)
        build_android()
        build_preview(ink)
    print("Готово:", OUT)


if __name__ == "__main__":
    main()
