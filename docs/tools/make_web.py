import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DOCS = os.path.dirname(HERE)
OUT = os.path.join(DOCS, "assets", "web")
LOGO_WIDTH = 620
LOGO_CANVAS = 1000
PAIR_WIDTH = 1400
GAP = 16


def banner(name):
    return Image.open(os.path.join(DOCS, "banners", name)).convert("RGBA")


def logo():
    src = Image.open(os.path.join(DOCS, "assets", "logo.png")).convert("RGBA")
    height = round(src.height * LOGO_WIDTH / src.width)
    small = src.resize((LOGO_WIDTH, height), Image.LANCZOS)
    canvas = Image.new("RGBA", (LOGO_CANVAS, height), (0, 0, 0, 0))
    canvas.alpha_composite(small, ((LOGO_CANVAS - LOGO_WIDTH) // 2, 0))
    canvas.save(os.path.join(OUT, "logo.png"))
    print("logo.png", canvas.size)


def pair(name, left, right):
    a, b = banner(left), banner(right)
    height = round((PAIR_WIDTH - GAP) / (a.width / a.height + b.width / b.height))
    a = a.resize((round(a.width * height / a.height), height), Image.LANCZOS)
    b = b.resize((round(b.width * height / b.height), height), Image.LANCZOS)
    canvas = Image.new("RGBA", (a.width + GAP + b.width, height), (0, 0, 0, 0))
    canvas.alpha_composite(a, (0, 0))
    canvas.alpha_composite(b, (a.width + GAP, 0))
    canvas.save(os.path.join(OUT, name), quality=88, method=6)
    print(name, canvas.size)


os.makedirs(OUT, exist_ok=True)
logo()
pair("oil.webp", "oil.webp", "oil_pocket.webp")
pair("quarry.webp", "query.webp", "scanner.webp")
