"""Decorations of the later forms of the bosses (their phases): glowing veins and cracks, emissive cores and crystals.
Used by the boss rigs for their '<id>_p<n>' looks, which the game picks by phase (ERBoss.lookFor)."""


def veins(p, parts, color, glow, index=0, seed=0):
    """A zigzag vein down every side face of the parts, painted in `color` and glowing in `glow`."""
    for part in parts:
        for fc in p.all_faces(part, index=index, sides_only=True):
            if fc.w <= 0 or fc.h <= 0:
                continue
            gfc = p.face(part, fc.face, index=index, glow=True)
            x = (len(part) * 3 + seed) % fc.w
            for y in range(fc.h):
                fc.px(x, y, color)
                gfc.px(x, y, glow)
                x += 1 if (y * 5 + len(part) + seed) % 3 == 0 else (-1 if (y * 7 + seed) % 4 == 0 else 0)
                x = max(0, min(fc.w - 1, x))


def cracks(p, parts, color, glow, every=40, index=0):
    """Short glowing cracks spread over the side faces (about one per `every` pixels)."""
    for part in parts:
        for fc in p.all_faces(part, index=index, sides_only=True):
            if fc.w <= 0 or fc.h <= 0:
                continue
            gfc = p.face(part, fc.face, index=index, glow=True)
            for k in range(max(1, fc.w * fc.h // every)):
                x = (k * 7 + len(part) * 3) % fc.w
                y = (k * 11 + len(part) + len(fc.face)) % fc.h
                for step in range(3):
                    if 0 <= x < fc.w and 0 <= y < fc.h:
                        fc.px(x, y, color)
                        gfc.px(x, y, glow)
                    x += 1 if (k + step) % 2 == 0 else -1
                    y += 1


def glow_faces(p, part, color, index=0, every=1):
    """Makes the faces of one cube of a part glow (cores, crystals); `every` > 1 leaves a pattern."""
    for fc in p.all_faces(part, index=index, glow=True):
        for y in range(fc.h):
            for x in range(fc.w):
                if (x + y) % every == 0:
                    fc.px(x, y, color)


def glow_bright(p, part, threshold=200, index=0):
    """Copies the bright pixels of a cube (magma cracks, hot spots) into the glow texture."""
    for side in ('front', 'right', 'back', 'left', 'top', 'bottom'):
        fc = p.face(part, side, index=index)
        gfc = p.face(part, side, index=index, glow=True)
        for y in range(fc.h):
            for x in range(fc.w):
                c = fc.get(x, y)
                if max(c) >= threshold:
                    gfc.px(x, y, c)
