"""Developer helper: render sprites side by side, upscaled, to a PNG for eyeballing.

usage: python3 tools/art/quickview.py <out.png> <scale> <module.func[:arg,...]> ...
"""
import importlib
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def grid(images, scale, cols, bg=(58, 58, 68, 255), checker=True, pad=4):
    w = max(i.width for i in images) * scale
    h = max(i.height for i in images) * scale
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * (w + pad) + pad, rows * (h + pad) + pad), bg)
    d = ImageDraw.Draw(sheet)
    for k, im in enumerate(images):
        x = pad + (k % cols) * (w + pad)
        y = pad + (k // cols) * (h + pad)
        if checker:
            cs = max(scale * 2, 8)
            for yy in range(0, h, cs):
                for xx in range(0, w, cs):
                    c = (74, 74, 86, 255) if ((xx // cs + yy // cs) % 2) else (66, 66, 78, 255)
                    d.rectangle([x + xx, y + yy, x + min(w, xx + cs) - 1, y + min(h, yy + cs) - 1], fill=c)
        up = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        sheet.alpha_composite(up, (x, y))
    return sheet


if __name__ == "__main__":
    out, scale, cols = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    ims = []
    for spec in sys.argv[4:]:
        mod, _, rest = spec.partition(".")
        fn, _, args = rest.partition(":")
        f = getattr(importlib.import_module(mod), fn)
        for a in (args.split(",") if args else [None]):
            r = f(int(a)) if a is not None else f()
            ims.append(r.image() if hasattr(r, "image") else r)
    grid(ims, scale, cols).save(out)
