"""GUI textures: R18 logo, rank emblems, kill stamps, screen blood, vignette, terminal panel, mod list logo."""
import math
import os
import random

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter

from common import REPO, font, jp_font, save, FONT_DEJAVU_BOLD, FONT_SANS_BLACK

KILL_WORDS = {
    'generic': '惨殺', 'throw': '投殺', 'spikes': '串刺し', 'saw': '両断',
    'mixer': 'ミンチ', 'press': '圧殺', 'guillotine': '斬首',
}
RANKS = ['D', 'C', 'B', 'A', 'S', 'SS', 'SSS']
RANK_KANJI = ['退屈', '微笑', '愉快', '歓喜', '狂喜', '恍惚', '殺人喜']
RANK_RGB = [(150, 150, 150), (120, 200, 255), (120, 255, 120), (255, 224, 70), (255, 140, 40), (255, 48, 48), (255, 16, 96)]


def text_mask(text, fnt, size, pad=10, stroke=0):
    d = ImageDraw.Draw(Image.new('L', (1, 1)))
    bbox = d.textbbox((0, 0), text, font=fnt, stroke_width=stroke)
    w, h = bbox[2] - bbox[0] + pad * 2, bbox[3] - bbox[1] + pad * 2
    m = Image.new('L', (w, h), 0)
    ImageDraw.Draw(m).text((pad - bbox[0], pad - bbox[1]), text, font=fnt, fill=255, stroke_width=stroke, stroke_fill=255)
    return m


def roughen(mask, seed, amount=0.35, scale=3):
    r = np.random.RandomState(seed)
    a = np.array(mask).astype(np.float32) / 255.0
    noise = r.rand(*a.shape)
    noise = np.array(Image.fromarray((noise * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(scale))).astype(np.float32) / 255.0
    edge = np.array(mask.filter(ImageFilter.GaussianBlur(2))).astype(np.float32) / 255.0
    out = np.where((edge > 0.05) & (edge < 0.95), (edge + (noise - 0.5) * amount * 2) > 0.5, a > 0.5)
    return Image.fromarray((out * 255).astype(np.uint8))


def add_drips(mask, seed, count, max_len, width=(2, 5)):
    r = random.Random(seed)
    a = np.array(mask)
    h, w = a.shape
    d = ImageDraw.Draw(mask)
    cols = [x for x in range(w) if a[:, x].max() > 0]
    for i in range(count):
        if not cols:
            break
        x = r.choice(cols)
        ys = np.nonzero(a[:, x])[0]
        y0 = int(ys.max())
        ln = r.randint(max_len // 4, max_len)
        wd = r.randint(*width)
        d.rectangle([x - wd // 2, y0 - 2, x + wd // 2, min(h - 1, y0 + ln)], fill=255)
        d.ellipse([x - wd, min(h - 1, y0 + ln) - wd, x + wd, min(h - 1, y0 + ln) + wd], fill=255)
    return mask


def colorize(mask, top_rgb, bot_rgb, seed=0, gloss=True):
    w, h = mask.size
    a = np.zeros((h, w, 4), dtype=np.float32)
    t = np.linspace(0, 1, h)[:, None]
    for i in range(3):
        a[..., i] = top_rgb[i] + (bot_rgb[i] - top_rgb[i]) * t
    r = np.random.RandomState(seed)
    n = np.array(Image.fromarray((r.rand(h, w) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.5))).astype(np.float32) / 255.0
    a[..., :3] *= (0.85 + 0.3 * n[..., None])
    if gloss:
        hl = np.array(mask.filter(ImageFilter.GaussianBlur(3))).astype(np.float32) / 255.0
        a[..., :3] += (hl[..., None] > 0.9) * 25
    a[..., 3] = np.array(mask)
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA')


def outline(img, rgb, radius):
    alpha = img.split()[3]
    grown = alpha.filter(ImageFilter.MaxFilter(radius * 2 + 1))
    base = Image.new('RGBA', img.size, rgb + (0,))
    base.putalpha(grown)
    base.alpha_composite(img)
    return base


def fit(img, w, h):
    s = min(w / img.width, h / img.height)
    return img.resize((max(1, int(img.width * s)), max(1, int(img.height * s))), Image.LANCZOS)


def blood_title(text, size, seed, drips=26, drip_len=60):
    m = text_mask(text, jp_font(size), size, pad=24)
    m = roughen(m, seed, 0.35, 2)
    m = add_drips(m, seed + 1, drips, drip_len)
    m = roughen(m, seed + 2, 0.2, 1)
    img = colorize(m, (215, 20, 24), (95, 0, 6), seed)
    return outline(img, (20, 0, 0), 3)


def r18_logo():
    W, H = 512, 256
    out = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    title = fit(blood_title('殺人喜', 170, 7, drips=34, drip_len=70), 430, 200)
    out.alpha_composite(title, ((W - title.width) // 2 - 34, 0))
    sub = Image.new('RGBA', (W, 40), (0, 0, 0, 0))
    d = ImageDraw.Draw(sub)
    f = font(FONT_DEJAVU_BOLD, 26)
    txt = '- S A T S U J I N K I -'
    tw = d.textlength(txt, font=f)
    d.text(((W - tw) / 2, 4), txt, font=f, fill=(230, 220, 220, 255), stroke_width=2, stroke_fill=(40, 0, 0, 255))
    out.alpha_composite(sub, (0, H - 46))
    # R18 badge
    b = Image.new('RGBA', (96, 96), (0, 0, 0, 0))
    d = ImageDraw.Draw(b)
    d.ellipse([4, 4, 91, 91], fill=(200, 0, 10, 255), outline=(255, 255, 255, 255), width=5)
    f2 = font(FONT_DEJAVU_BOLD, 34)
    tw = d.textlength('R18', font=f2)
    d.text(((96 - tw) / 2, 26), 'R18', font=f2, fill=(255, 255, 255, 255))
    out.alpha_composite(b.rotate(-14, resample=Image.BICUBIC), (W - 104, 6))
    save(out, 'gui/r18_logo.png')
    return out


def mod_logo(r18):
    W, H = 512, 160
    out = Image.new('RGBA', (W, H), (12, 0, 0, 255))
    g = np.zeros((H, W, 4), dtype=np.uint8)
    for y in range(H):
        g[y, :, 0] = 12 + int(30 * (1 - y / H))
        g[y, :, 3] = 255
    out = Image.fromarray(g, 'RGBA')
    title = fit(blood_title('殺人喜', 130, 11, drips=22, drip_len=40), 300, 150)
    out.alpha_composite(title, (16, (H - title.height) // 2))
    d = ImageDraw.Draw(out)
    f = font(FONT_DEJAVU_BOLD, 26)
    d.text((318, 40), 'SATSUJINKI', font=f, fill=(235, 225, 225, 255), stroke_width=2, stroke_fill=(60, 0, 0, 255))
    f2 = font(FONT_DEJAVU_BOLD, 22)
    d.rounded_rectangle([320, 88, 398, 120], radius=6, fill=(200, 0, 10, 255), outline=(255, 255, 255, 255), width=3)
    d.text((333, 91), 'R18', font=f2, fill=(255, 255, 255, 255))
    path = os.path.join(REPO, 'src', 'main', 'resources', 'satsujinki_logo.png')
    out.save(path)


def drips_band():
    W, H = 256, 128
    m = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(m)
    r = random.Random(5)
    d.rectangle([0, 0, W, 10], fill=255)
    for x in range(0, W, 3):
        d.ellipse([x - 4, 4 + r.randint(0, 6), x + 4, 14 + r.randint(0, 8)], fill=255)
    for i in range(30):
        x = r.randint(0, W - 1)
        ln = r.randint(12, H - 14)
        wd = r.randint(2, 6)
        d.rectangle([x - wd // 2, 6, x + wd // 2, ln], fill=255)
        d.ellipse([x - wd, ln - wd, x + wd, ln + wd + 2], fill=255)
    m = roughen(m, 8, 0.2, 1)
    img = colorize(m, (190, 10, 16), (110, 0, 6), 3)
    save(img, 'gui/blood_drips.png')


def rank_emblems():
    for i, (txt, kanji, rgb) in enumerate(zip(RANKS, RANK_KANJI, RANK_RGB)):
        W, H = 128, 64
        out = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        # brush stroke behind for A and up
        if i >= 3:
            m = Image.new('L', (W, H), 0)
            d = ImageDraw.Draw(m)
            r = random.Random(20 + i)
            pts = [(6, 44), (30, 30), (70, 22), (122, 14)]
            for k in range(26):
                yy = r.randint(-7, 7)
                d.line([(p[0], p[1] + yy) for p in pts], fill=255, width=r.randint(2, 5))
            m = roughen(m, 30 + i, 0.3, 1)
            stroke = colorize(m, (120, 0, 8), (60, 0, 4), i, gloss=False)
            out.alpha_composite(stroke)
        # watermark kanji
        wm = text_mask(kanji, jp_font(30, True), 30, pad=2)
        wm = fit(Image.merge('RGBA', [wm, wm, wm, wm.point(lambda v: v * 0.35)]), 120, 26)
        tint = Image.new('RGBA', wm.size, (0, 0, 0, 0))
        tint.paste((rgb[0] // 2, rgb[1] // 2, rgb[2] // 2, 255), mask=wm.split()[3])
        out.alpha_composite(tint, (W - wm.width - 2, H - wm.height))
        # letters, italic skew
        fs = 64 if len(txt) == 1 else (54 if len(txt) == 2 else 44)
        m = text_mask(txt, font(FONT_SANS_BLACK, fs), fs, pad=8)
        m = m.transform(m.size, Image.AFFINE, (1, 0.28, -12, 0, 1, 0), resample=Image.BICUBIC)
        letters = colorize(m, tuple(min(255, c + 60) for c in rgb), tuple(int(c * 0.55) for c in rgb), i)
        letters = outline(letters, (10, 0, 0), 3)
        letters = fit(letters, 124, 60)
        out.alpha_composite(letters, ((W - letters.width) // 2, (H - letters.height) // 2 - 2))
        if i >= 5:
            r = random.Random(50 + i)
            d = ImageDraw.Draw(out)
            for k in range(14):
                x, y = r.randint(0, W), r.randint(0, H)
                rr = r.randint(1, 4)
                d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(170, 0, 10, 255))
        save(out, f'gui/rank_{i}.png')


def stamps():
    for key, word in KILL_WORDS.items():
        W, H = 128, 64
        m = Image.new('L', (W, H), 0)
        d = ImageDraw.Draw(m)
        d.rectangle([3, 3, W - 4, H - 4], outline=255, width=5)
        d.rectangle([9, 9, W - 10, H - 10], outline=255, width=1)
        size = 44 if len(word) <= 2 else 34
        tm = text_mask(word, jp_font(size, True), size, pad=2)
        tm = fit(tm, W - 24, H - 20)
        m.paste(255, ((W - tm.width) // 2, (H - tm.height) // 2), tm)
        # ink gaps
        r = np.random.RandomState(hash(key) % 1000)
        a = np.array(m).astype(np.float32)
        holes = np.array(Image.fromarray((r.rand(H, W) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))).astype(np.float32)
        a[holes > 192] = 0
        m = roughen(Image.fromarray(a.astype(np.uint8)), 3, 0.25, 1)
        img = colorize(m, (230, 20, 30), (170, 0, 12), 4, gloss=False)
        save(img, f'gui/stamp_{key}.png')


def screen_blood():
    for v in range(4):
        W = H = 256
        a = np.zeros((H, W, 4), dtype=np.float32)
        img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        r = random.Random(900 + v)
        layer = Image.new('L', (W, H), 0)
        d = ImageDraw.Draw(layer)
        for k in range(10 + v * 3):
            edge = r.choice(['l', 'r', 't', 'b', 'c'])
            if edge == 'l':
                x, y = r.randint(-10, 40), r.randint(0, H)
            elif edge == 'r':
                x, y = r.randint(W - 40, W + 10), r.randint(0, H)
            elif edge == 't':
                x, y = r.randint(0, W), r.randint(-10, 40)
            elif edge == 'b':
                x, y = r.randint(0, W), r.randint(H - 40, H + 10)
            else:
                x, y = r.choice([(r.randint(0, 60), r.randint(0, 60)), (r.randint(196, 256), r.randint(0, 60)),
                                 (r.randint(0, 60), r.randint(196, 256)), (r.randint(196, 256), r.randint(196, 256))])
            rad = r.randint(8, 30)
            d.ellipse([x - rad, y - rad, x + rad, y + rad], fill=r.randint(150, 230))
            for s in range(12):
                ang = r.random() * math.tau
                dist = rad * (1.1 + r.random() * 1.6)
                rr = r.randint(2, 6)
                sx, sy = x + math.cos(ang) * dist, y + math.sin(ang) * dist
                d.ellipse([sx - rr, sy - rr, sx + rr, sy + rr], fill=r.randint(150, 230))
            for s in range(r.randint(1, 3)):
                dx = x + r.randint(-rad // 2, rad // 2)
                ln = r.randint(20, 90)
                wd = r.randint(2, 5)
                d.rectangle([dx - wd // 2, y, dx + wd // 2, y + ln], fill=r.randint(150, 220))
                d.ellipse([dx - wd, y + ln - wd, dx + wd, y + ln + wd], fill=200)
        layer = layer.filter(ImageFilter.GaussianBlur(1.2))
        al = np.array(layer).astype(np.float32)
        n = np.random.RandomState(v).rand(H, W)
        a[..., 0] = 120 + 70 * n
        a[..., 1] = 4 + 8 * n
        a[..., 2] = 8 + 8 * n
        a[..., 3] = al
        save(Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA'), f'gui/blood_screen_{v}.png')


def vignette():
    W = H = 256
    y, x = np.mgrid[0:H, 0:W]
    d = np.sqrt(((x - W / 2 + 0.5) / (W / 2)) ** 2 + ((y - H / 2 + 0.5) / (H / 2)) ** 2) / math.sqrt(2)
    t = np.clip((d - 0.42) / 0.58, 0, 1)
    al = (t * t * (3 - 2 * t)) * 255
    a = np.zeros((H, W, 4), dtype=np.uint8)
    a[..., :3] = 255
    a[..., 3] = al.astype(np.uint8)
    save(Image.fromarray(a, 'RGBA'), 'gui/vignette.png')


def terminal():
    W, H = 256, 256
    a = np.zeros((H, W, 4), dtype=np.float32)
    PW, PH = 256, 200
    n = np.random.RandomState(4).rand(PH, PW)
    n = np.array(Image.fromarray((n * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1))).astype(np.float32) / 255.0
    base = np.array([30, 32, 36], dtype=np.float32)
    a[:PH, :PW, :3] = base * (0.85 + 0.3 * n[..., None])
    a[:PH, :PW, 3] = 245
    img = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA')
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, PW - 1, PH - 1], outline=(150, 10, 14, 255), width=2)
    d.rectangle([3, 3, PW - 4, PH - 4], outline=(60, 62, 68, 255), width=1)
    d.rectangle([2, 2, PW - 3, 19], fill=(18, 6, 8, 245))
    d.line([2, 19, PW - 3, 19], fill=(200, 16, 20, 255), width=1)
    d.rectangle([6, 44, PW - 7, PH - 30], fill=(14, 10, 12, 235), outline=(70, 20, 22, 255))
    for yy in range(46, PH - 31, 2):
        d.line([7, yy, PW - 8, yy], fill=(22, 14, 16, 235))
    for (x, y) in ((6, 23), (PW - 9, 23), (6, PH - 10), (PW - 9, PH - 10)):
        d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=(150, 156, 164, 255))
    for i in range(0, PW - 8, 10):
        d.polygon([(4 + i, PH - 26), (9 + i, PH - 26), (4 + i, PH - 21)], fill=(200, 160, 30, 255))
    r = random.Random(3)
    for k in range(6):
        x, y = r.randint(10, PW - 10), r.randint(PH - 40, PH - 6)
        rr = r.randint(1, 3)
        d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(120, 6, 10, 255))
    save(img, 'gui/terminal.png')


def main():
    r18 = r18_logo()
    mod_logo(r18)
    drips_band()
    rank_emblems()
    stamps()
    screen_blood()
    vignette()
    terminal()
    print('gui done')


if __name__ == '__main__':
    main()
