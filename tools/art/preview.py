"""Labelled, upscaled contact sheet of everything the generators produce."""
from __future__ import annotations

import os

from PIL import Image, ImageDraw, ImageFont

BG = (34, 34, 42, 255)
CELL_BG = (58, 58, 70, 255)
CELL_BG2 = (66, 66, 80, 255)
TEXT = (235, 235, 245, 255)
SUB = (170, 170, 190, 255)
TIER_NAMES = ["1 iron", "2 steel", "3 desert", "4 frost", "5 dragon", "6 celestial", "7 abyss"]


def _font(size):
    return ImageFont.load_default(size=size)


def checker(w, h, cs=8):
    im = Image.new("RGBA", (w, h), CELL_BG)
    d = ImageDraw.Draw(im)
    for y in range(0, h, cs):
        for x in range(0, w, cs):
            if (x // cs + y // cs) % 2:
                d.rectangle([x, y, x + cs - 1, y + cs - 1], fill=CELL_BG2)
    return im


def cell(img: Image.Image, scale: int, box: int | None = None) -> Image.Image:
    up = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
    bw = box or up.width
    bh = box or up.height
    out = checker(bw, bh)
    out.alpha_composite(up, ((bw - up.width) // 2, (bh - up.height) // 2))
    return out


class Section:
    def __init__(self, title: str, width: int):
        self.title = title
        self.width = width
        self.items: list[tuple[Image.Image, int, int]] = []
        self.h = 34

    def add(self, im: Image.Image, x: int, y: int):
        self.items.append((im, x, 34 + y))
        self.h = max(self.h, 34 + y + im.height)

    def text(self, s: str, x: int, y: int, size: int = 13, color=SUB):
        f = _font(size)
        tmp = Image.new("RGBA", (max(1, int(f.getlength(s)) + 4), size + 6), (0, 0, 0, 0))
        ImageDraw.Draw(tmp).text((0, 0), s, fill=color, font=f)
        self.add(tmp, x, y)

    def render(self) -> Image.Image:
        im = Image.new("RGBA", (self.width, self.h + 16), BG)
        d = ImageDraw.Draw(im)
        d.text((12, 8), self.title, fill=TEXT, font=_font(18))
        d.line([(12, 30), (self.width - 12, 30)], fill=(90, 90, 110, 255), width=1)
        for item, x, y in self.items:
            im.alpha_composite(item, (x, y))
        return im


def grid_section(title, width, rows, col_labels, row_labels, scale, box, label_w=110, gap=6):
    s = Section(title, width)
    for j, cl in enumerate(col_labels):
        s.text(cl, label_w + j * (box + gap), 0)
    for i, (rl, ims) in enumerate(zip(row_labels, rows)):
        y = 20 + i * (box + gap)
        s.text(rl, 12, y + box // 2 - 8, 13, TEXT)
        for j, im in enumerate(ims):
            s.add(cell(im, scale, box), label_w + j * (box + gap), y)
    return s


def flow_section(title, width, named_images, scale, box, gap=8, label=True, label_h=16, x0=12):
    s = Section(title, width)
    x, y = x0, 0
    for name, im in named_images:
        if x + box > width - 12:
            x = x0
            y += box + gap + (label_h if label else 0)
        s.add(cell(im, scale, box), x, y)
        if label:
            short = name if len(name) <= 18 else name[:17] + "."
            s.text(short, x, y + box + 1, 10)
        x += box + gap
    return s


def stack(sections, path, section_dir=None):
    ims = [s.render() for s in sections]
    w = max(i.width for i in ims)
    h = sum(i.height for i in ims)
    sheet = Image.new("RGBA", (w, h), BG)
    y = 0
    for i, im in enumerate(ims):
        sheet.alpha_composite(im, (0, y))
        y += im.height
        if section_dir:
            os.makedirs(section_dir, exist_ok=True)
            im.save(os.path.join(section_dir, f"section_{i:02d}.png"))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sheet.save(path, optimize=True)
    return sheet
