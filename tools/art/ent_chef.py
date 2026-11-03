"""料理長 the chef (権藤 剛, 52) - 0.8 x 2.2. Head chef of HIDAMARI, died in a grease fire in the kitchen in 1996 and
still chops at his board. A big man in a stained, scorched double-breasted chef coat and a tall toque, the apron
charred and burnt through at the hem, houndstooth trousers, clogs; face and hands burnt black and cracked like
charcoal, embers glowing in the cracks and in the eyes (eyes layer); a heavy cleaver.
Poses: 0 CHOP, 1 STOVE, 2 PLATE, 3 LOOK, 4 WALK, 5 CHASE, 6 IDLE."""
import math

import numpy as np

from common import C
from ent_model import Spec, Part, Cube
from ent_lib import card, Painter, pal_arr, P

BURN = ['#050404', '#0b0908', '#120e0c', '#1a1411', '#241b17', '#30241e', '#3f3027']
EMBER_BASE = ['#2a0c06', '#4a1406', '#6e2008', '#94320c']
COAT = ['#57534b', '#726d62', '#8e887b', '#a7a092', '#bcb5a5', '#cdc7b7', '#dcd7c9']
SOOT = ['#141210', '#221f1b', '#332e28', '#463f37']
GREASE = ['#5e4a2c', '#7a6239', '#95794a']
CHECK = ['#141416', '#1f1f22', '#3a3a3e', '#4d4d52']
CLOG = ['#0a0a0b', '#141416', '#1f1f22', '#2c2c30']
STEEL = ['#2c2f33', '#43474c', '#5f646a', '#7e848a', '#a2a7ac', '#c8ccd0']
RUST = ['#3e2416', '#5a331d', '#774426']
WOOD = ['#22170f', '#33241a', '#463224', '#5a412e']


def voronoi_cracks(Pp, seed, cell=1.6):
    """Distance (in pixels) to the nearest edge of a 3D Voronoi tessellation: small = crack."""
    rng = np.random.default_rng(seed)
    pts = Pp / cell
    base = np.floor(pts).astype(int)
    d1 = np.full(pts.shape[:-1], 1e9)
    d2 = np.full(pts.shape[:-1], 1e9)
    table = rng.random((16, 16, 16, 3))
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                c = base + np.array([dx, dy, dz])
                j = table[c[..., 0] % 16, c[..., 1] % 16, c[..., 2] % 16]
                fp = c + j
                d = np.linalg.norm(pts - fp, axis=-1)
                d2 = np.where(d < d1, d1, np.minimum(d2, d))
                d1 = np.minimum(d1, d)
    return (d2 - d1) * cell


class Chef(Spec):
    id = 'chef'
    java_name = 'ChefModel'
    module = 'ent_chef'
    tex_w = 64
    tex_h = 64
    height = 2.25
    poses = [('CHOP', 0), ('STOVE', 1), ('PLATE', 2), ('LOOK', 3), ('WALK', 4), ('CHASE', 5), ('IDLE', 6)]
    doc = """料理長 (the chef, 権藤 剛): died in the 1996 grease fire and still chops at his board. A big man in a
    stained, scorched chef coat and a tall toque, the apron burnt through at the hem; face and hands burnt black
    and cracked, embers glowing in the cracks and the eyes (eyes layer, plus a flare card that flickers and blazes
    while he hunts); a heavy cleaver in the right hand.
    Pose ids: 0 CHOP (back to the room, the cleaver arm chopping a 10-tick rhythm, impact at tick 7 of each cycle
    of ageInTicks), 1 STOVE (leaning in over the flames, the left hand working the pan), 2 PLATE (at the pass, head
    bowed, arranging; taps the pass bell at pose tick 100), 3 LOOK (everything freezes, the cleaver held up, the
    head turns slowly over the shoulder - about 160 degrees - toward the side of netHeadYaw), 4 WALK (heavy),
    5 CHASE (charge, cleaver raised high, roaring), 6 IDLE (wheezing, shoulders heaving)."""

    def build(self):
        r = self.root
        body = Part('body', (0, 10.5, 0))
        body.cube(Cube((-4.5, -9.5, -2.5), (9, 9.5, 5), 'coat', tag='torso'))
        body.cube(Cube((-3.5, -6.0, -3.5), (7, 4.5, 1), 'coat', tag='belly', faces=('top', 'bottom', 'right', 'front', 'left')))
        apron = card('apron', (0, -2.0, -3.6), (-4.0, 0, 0), (8, 9, 0), 'apron')
        neck = Part('neck', (0, -9.5, 0), cubes=[Cube((-1.75, -1, -1.75), (3.5, 1, 3.5), 'burn', tag='neck',
                                                      faces=('right', 'front', 'left', 'back'))])
        head = Part('head', (0, -1, 0), cubes=[Cube((-3.5, -7, -3.5), (7, 7, 7), 'burn', tag='head',
                                                     faces=('bottom', 'right', 'front', 'left', 'back'))])
        band = Part('toque_band', (0, -7, 0), cubes=[Cube((-3.75, -1.5, -3.75), (7.5, 1.5, 7.5), 'toque', tag='band',
                                                           faces=('right', 'front', 'left', 'back'))])
        puff = Part('toque_puff', (0, -1.5, 0), rot=(0, 0, 0.0),
                    cubes=[Cube((-4.25, -4, -4.25), (8.5, 4, 8.5), 'toque', tag='puff')])
        band.add(puff)
        mouth = card('mouth', (0, -2.0, -3.58), (-2.5, 0, 0), (5, 2, 0), 'mouth')
        jaw = card('jaw', (0, -1.0, -3.65), (-2.5, 0, 0), (5, 1, 0), 'jaw')
        flare = card('flare', (0, -7, -3.62), (-3.5, 0, 0), (7, 7, 0), 'flare')
        head.add(band, mouth, jaw, flare)
        neck.add(head)
        arms = {}
        for side, pre in ((-1, 'right'), (1, 'left')):
            arm = Part(f'{pre}_arm', (side * 6.1, -8.5, 0), cubes=[Cube((-1.5, -0.5, -1.5), (3, 6, 3), 'coat', tag=f'{pre}_upper')])
            fore = Part(f'{pre}_forearm', (0, 5.5, 0), cubes=[Cube((-1.5, 0, -1.5), (3, 5, 3), 'coat', tag=f'{pre}_fore',
                                                                     faces=('right', 'front', 'left', 'back', 'bottom'))])
            hand = Part(f'{pre}_hand', (0, 5, 0), cubes=[Cube((-1.25, 0, -1), (2.5, 3, 2), 'burn', tag=f'{pre}_hand',
                                                                faces=('right', 'front', 'left', 'back', 'bottom'))])
            fore.add(hand)
            arm.add(fore)
            arms[pre] = (arm, hand)
        cleaver = Part('cleaver', (0, 0, 0), cubes=[
            Cube((-0.25, 1.0, -2.0), (0.5, 1.0, 3.5), 'wood', tag='handle'),
            Cube((-0.25, 0.5, -6.0), (0.5, 3.0, 4.0), 'steel', tag='blade')])
        arms['right'][1].add(cleaver)
        body.add(apron, neck, arms['right'][0], arms['left'][0])
        legs = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            leg = Part(f'{pre}_leg', (side * 2.0, 10.5, 0), cubes=[Cube((-1.75, 0, -1.75), (3.5, 6.5, 3.5), 'pants', tag=f'{pre}_thigh',
                                                                         faces=('right', 'front', 'left', 'back', 'bottom'))])
            shin = Part(f'{pre}_shin', (0, 6.5, 0), cubes=[Cube((-1.5, 0, -1.5), (3, 6, 3), 'pants', tag=f'{pre}_shin',
                                                               faces=('right', 'front', 'left', 'back')),
                                                          Cube((-1.5, 6, -3.5), (3, 1, 5), 'clog', tag=f'{pre}_foot')])
            leg.add(shin)
            legs.append(leg)
        r.add(body, *legs)

    # ================================================================================================ texture
    def paint(self):
        pt = Painter(self, 9931)
        F, EF, nz = pt.F, pt.EF, pt.nz

        def burnt(tag, seed, glow=1.0, cell=2.1, which=None):
            """Charcoal skin: dark plates split by thin cracks; only some cracks still hold embers (eyes layer)."""
            fs = F(tag)
            efs = EF(tag)
            for n, f in fs.items():
                if f.w <= 0 or f.h <= 0 or (which and n not in which):
                    continue
                Pp = f.positions()
                cr = voronoi_cracks(Pp, seed, cell)
                cr2 = voronoi_cracks(Pp * 1.9 + 3.3, seed + 7, cell)
                hot = pt.n3c(Pp * 1.2 + seed)
                for y in range(f.h):
                    for x in range(f.w):
                        d = cr[y, x]
                        v = 2.4 + pt.n3b(Pp[y, x][None])[0] * 1.1 + pt.n3(Pp[y, x][None])[0] * 0.7
                        if n == 'bottom':
                            v -= 1.0
                        if d < 0.11:
                            h = hot[y, x]
                            if h > 0.12:
                                f.set(x, y, P(EMBER_BASE, 0.5 + h * 2.0))
                                k = min(1.0, (h - 0.12) * 1.6 + 0.25) * glow
                                e = np.array([255 * min(1, k * 1.15), 40 + 110 * k, 10 + 25 * k, 255], np.float32)
                                efs[n].set(x, y, e)
                            else:
                                f.set(x, y, C('#070404'))
                        elif cr2[y, x] < 0.07:
                            f.set(x, y, P(BURN, v - 1.6))
                        else:
                            f.set(x, y, P(BURN, v))
            return fs

        # ---------------------------------------------------------------- coat (stained, scorched)
        def coat_fn(Pp, nrm, n):
            soot = np.clip(pt.n3c(Pp * 0.9 + 5.0) * 0.9 + np.clip((Pp[..., 1] - 5) / 6, 0, 1) * 0.9 - 0.55, 0, 2)
            return -soot * 1.4
        for tag in ('torso', 'belly', 'right_upper', 'left_upper', 'right_fore', 'left_fore'):
            fs = pt.base(tag, COAT, 4.6, amp=0.5, fine=0.4, top=0.5, fn=coat_fn)
            for n, f in fs.items():
                Pp = f.positions()
                g = pt.n3(Pp * 2.2 + 40.0)
                for y in range(f.h):
                    for x in range(f.w):
                        if g[y, x] > 0.62:                    # a few grease stains
                            f.set(x, y, P(GREASE, 0.8 + (g[y, x] - 0.62) * 6 + nz(f, x, y, 0.3, 0.2)))
        fs = F('torso')
        f = fs['front']                                       # 18 x 19: double breasted, mandarin collar
        for y in range(2, f.h - 1):
            for bx in (5, 12):
                if y % 4 == 2:
                    f.set(bx, y, C('#1d1a17'))
                    f.set(bx, y + 1, C('#36312b'))
            f.dl(13, y, -0.06)                                # the overlap edge of the double breast
        for x in range(f.w):
            f.set(x, 0, P(COAT, 5))
            f.set(x, 1, P(COAT, 3))
        for x in (7, 8, 9, 10):
            f.set(x, 0, P(BURN, 2))                           # the burnt throat in the collar opening
            f.set(x, 1, P(BURN, 1))
        for n in ('front', 'back', 'left', 'right'):          # scorched hem: charred band, ragged
            fc = fs[n]
            for x in range(fc.w):
                h = 1 + int((math.sin(x * 1.7 + len(n)) + 1) * 1.2)
                for y in range(fc.h - h, fc.h):
                    fc.set(x, y, P(SOOT, 0.8 + nz(fc, x, y, 0.6, 0.3)))
        for pre in ('right', 'left'):                         # sleeves rolled to the forearm, cuffs charred
            fs2 = F(f'{pre}_fore')
            for n in ('front', 'back', 'left', 'right'):
                fc = fs2[n]                                   # 6 x 10
                for y in range(fc.h):
                    for x in range(fc.w):
                        if y >= 4:
                            fc.set(x, y, P(BURN, 2.4 + nz(fc, x, y, 0.8, 0.5)))
                        elif y == 3:
                            fc.set(x, y, P(SOOT, 1.0))
                        elif y == 2:
                            fc.set(x, y, P(COAT, 1.5))
            burnt(f'{pre}_fore', 300 + len(pre), glow=0.8, which=None)
            for n in ('front', 'back', 'left', 'right'):
                fc = fs2[n]
                for y in range(0, 4):
                    for x in range(fc.w):
                        fc.set(x, y, P(COAT, 4.2 + nz(fc, x, y, 0.6, 0.3)) if y < 2 else (P(COAT, 1.6) if y == 2 else P(SOOT, 1.2)))
                ef = EF(f'{pre}_fore')[n]
                for y in range(0, 4):
                    for x in range(fc.w):
                        ef.clear(x, y)
            fs2['bottom'].fill(P(BURN, 1))
        # belly front: the coat bulges; buttons continue
        f = F('belly')['front']
        for y in range(f.h):
            for bx in (3, 10):
                if y % 4 == 1:
                    f.set(bx, y, C('#1d1a17'))
        # ---------------------------------------------------------------- apron: grimy white, charred and burnt through
        f = F('apron')['front']                               # 16 x 18
        for y in range(f.h):
            for x in range(f.w):
                pp = f.positions()[y, x][None]
                v = 4.4 + pt.n3(pp)[0] * 0.6 + pt.n3b(pp)[0] * 0.4 - y * 0.06
                fold = math.sin(x * 0.9 + pt.n3(pp * 0.5)[0])
                v += fold * 0.4
                burn = y / f.h * 1.6 + pt.n3c(pp * 1.6 + 9.0)[0] * 0.9 - 0.75
                if burn > 0.6:
                    if burn > 1.05 and y > f.h * 0.55:
                        f.clear(x, y)                         # burnt away
                        continue
                    f.set(x, y, P(SOOT, 0.4 + (1.05 - burn) * 2))
                elif burn > 0.35:
                    f.set(x, y, P(GREASE, 0.3 + (0.6 - burn) * 3))
                else:
                    f.set(x, y, P(COAT, v))
        for x in range(f.w):                                  # waist band
            if f.get(x, 0)[3] > 0:
                f.set(x, 0, P(COAT, 3))
        ef = EF('apron')['front']                             # a few embers still alive in the burnt edge
        for y in range(1, f.h - 1):
            for x in range(f.w):
                if f.get(x, y)[3] > 0 and f.get(x, y + 1)[3] == 0 and (x * 3 + y) % 4 == 0:
                    ef.set(x, y, '#a83a10')
        # ---------------------------------------------------------------- trousers: houndstooth, clogs
        for pre in ('right', 'left'):
            for tag in (f'{pre}_thigh', f'{pre}_shin'):
                for n, fc in F(tag).items():
                    for y in range(fc.h):
                        for x in range(fc.w):
                            ht = ((x // 2 + y // 2) % 2 == 0) ^ (((x + y) % 4) == 0)
                            v = (2.3 if ht else 0.6) + nz(fc, x, y, 0.3, 0.2)
                            if y >= fc.h - 2 and tag.endswith('shin'):
                                v = min(v, 1.2)
                            fc.set(x, y, P(CHECK, v))
            for n, fc in F(f'{pre}_foot').items():
                for y in range(fc.h):
                    for x in range(fc.w):
                        fc.set(x, y, P(CLOG, 1.6 + nz(fc, x, y, 0.5, 0.3) + (0.8 if n == 'top' else 0)))
        # ---------------------------------------------------------------- burnt skin: head, neck, hands
        burnt('head', 101, glow=1.0, cell=1.4)
        burnt('neck', 102, glow=0.7)
        burnt('right_hand', 103, glow=0.9, cell=1.2)
        burnt('left_hand', 104, glow=0.9, cell=1.2)
        fs = F('head')
        f = fs['front']                                       # 14 x 14: the face
        ef = EF('head')['front']
        face = [
            '..............',
            '..............',
            '..............',
            '.###......###.',
            '#  ##....##  #',
            '#OOO#....#OOO#',
            '#OEO#....#OEO#',
            '.###..nn..###.',
            '......nn......',
            '.....#..#.....',
            '.mtTtTtTtTtm..',
            '..mtTtTtTtm...',
            '...mmmmmmm....',
            '..............',
        ]
        leg = {'#': (BURN, 0.6), 'O': (EMBER_BASE, 1.0), 'E': (EMBER_BASE, 3.0), 'n': (BURN, 0.3), 'm': (BURN, 0.2),
               't': C('#8a8270'), 'T': C('#6a6355'), ' ': (BURN, 1.2)}
        pt.pattern(f, face, leg, jitter=False)
        for y in range(5, 7):
            for x in (1, 2, 3, 10, 11, 12):
                ef.set(x, y, '#c2400c')
        ef.set(2, 6, '#ffd070')
        ef.set(11, 6, '#ffd070')
        ef.set(2, 5, '#ff8a24')
        ef.set(11, 5, '#ff8a24')
        for (x, y) in ((6, 7), (7, 8)):                        # the nose hole breathes embers
            ef.set(x, y, '#7a2408')
        for n in ('right', 'left'):                           # ears burnt to nubs
            fc = fs[n]
            for (ex, ey) in ((6, 6), (7, 6), (6, 7), (7, 7), (6, 8)):
                x = (fc.w - 1 - ex) if n == 'right' else ex
                fc.set(x, ey, P(BURN, 1.0))
        # ---------------------------------------------------------------- toque: pleated, sooty, singed
        for tag in ('band', 'puff'):
            fs = F(tag)
            for n, fc in fs.items():
                Pp = fc.positions()
                for y in range(fc.h):
                    for x in range(fc.w):
                        pleat = x % 3 if n in ('front', 'back', 'left', 'right') else 1
                        v = 4.7 + pt.n3(Pp[y, x][None])[0] * 0.4 + (0.5 if pleat == 1 else (-1.1 if pleat == 0 else -0.2))
                        if tag == 'band':
                            v -= 1.3 + (fc.h - 1 - y) * 0.2
                        if n == 'top':
                            v = 5.0 + pt.n3(Pp[y, x][None])[0] * 0.5
                        if n == 'bottom':
                            v = 2.6
                        sing = pt.n3c(Pp[y, x][None] * 2.0 + 77.0)[0]
                        if sing > 0.5:
                            fc.set(x, y, P(SOOT, 2.5 - (sing - 0.5) * 5))
                        else:
                            fc.set(x, y, P(COAT, v))
            if tag == 'puff':
                for n in ('front', 'back', 'left', 'right'):
                    fc = fs[n]
                    for x in range(fc.w):                     # the puff gathers at the bottom
                        fc.dl(x, fc.h - 1, -0.08)
                        if x % 2 == 0:
                            fc.dl(x, 0, 0.05)
        # ---------------------------------------------------------------- the mouth when he roars + flare
        f = F('mouth')['front']                               # 10 x 4
        for y in range(f.h):
            for x in range(f.w):
                cx = abs((x + 0.5) / f.w - 0.5) * 2
                if cx < 0.98 - 0.15 * y:
                    f.set(x, y, C('#0a0302') if 0 < y else C('#6a6355'))
        efm = EF('mouth')['front']
        for x in range(2, 8):
            efm.set(x, 3, '#ff6a1a' if x % 2 else '#c2400c')  # the throat glows
            efm.set(x, 2, '#5a1806')
        j = F('jaw')['front']                                 # 10 x 2
        jl = {'m': (BURN, 0.2), 't': C('#8a8270'), 'T': C('#6a6355'), '#': (BURN, 1.6)}
        pt.pattern(j, ['mtTtTtTtTm', '##########'], jl, jitter=False)
        fl = EF('flare')['front']                             # 14 x 14: brighter embers over the face cracks
        Pp = F('flare')['front'].positions()
        cr = voronoi_cracks(Pp + np.array([0.0, 0.0, 0.05]), 101, 1.4)
        for y in range(fl.h):
            for x in range(fl.w):
                if cr[y, x] < 0.2 and (x * 5 + y * 3) % 7 < 4:
                    fl.set(x, y, '#ff9a3a')
        for (x, y) in ((2, 6), (11, 6), (2, 5), (11, 5), (1, 6), (12, 6), (3, 6), (10, 6)):
            fl.set(x, y, '#fff0b0')
        # ---------------------------------------------------------------- cleaver
        fs = F('blade')
        for n, fc in fs.items():
            for y in range(fc.h):
                for x in range(fc.w):
                    pp = fc.positions()[y, x][None]
                    v = 2.6 + pt.n3b(pp)[0] * 0.8
                    rust = pt.n3(pp * 3.0 + 11.0)[0]
                    if rust > 0.35:
                        fc.set(x, y, P(RUST, (rust - 0.35) * 5))
                    else:
                        fc.set(x, y, P(STEEL, v))
            if n in ('right', 'left'):                        # 8 x 6: the edge bright along the bottom
                for x in range(fc.w):
                    fc.set(x, fc.h - 1, P(STEEL, 5))
                    fc.dl(x, fc.h - 2, 0.05)
                fc.set(1 if n == 'right' else fc.w - 2, 1, C('#111214'))       # the hanging hole
        for n, fc in fs.items():
            if n == 'bottom':
                fc.fill(P(STEEL, 5))
        for n, fc in F('handle').items():
            for y in range(fc.h):
                for x in range(fc.w):
                    fc.set(x, y, P(WOOD, 1.5 + nz(fc, x, y, 0.6, 0.4)))
        EF('blade')['right'].set(3, 5, '#2a2e32')
        return pt.img, pt.eyes

    # ================================================================================================ animation
    anim = '''
float breath = Mth.sin(t * 0.08F);
float wheeze = Math.max(0.0F, Mth.sin(t * 0.08F)) * Math.max(0.0F, Mth.sin(t * 0.43F));
float jit = glitch * noise(t * 1.6F);
float jit2 = glitch * noise(t * 2.2F + 30.0F);
float look = Mth.clamp(yaw, -1.2F, 1.2F);
float lookP = Mth.clamp(pitch, -0.5F, 0.6F);
// embers: the flare card flickers on now and then; always on while he hunts
this.flare.visible = noise(t * 0.35F + 3.0F) > 0.55F;
this.mouth.visible = false;
this.jaw.visible = false;
// the toque sags a little to one side
this.toquePuff.zRot = 0.06F;
this.toquePuff.xRot = -0.04F;

if (pose == CHOP || pose == LOOK) {
    // facing the board: the left hand holds the food, the right chops a 10-tick rhythm
    float cyc = (t % 10.0F) / 10.0F;
    float raise = cyc < 0.62F ? easeOut(cyc / 0.62F) : 1.0F - easeIn((cyc - 0.62F) / 0.11F);
    if (cyc >= 0.73F) {
        raise = -0.06F * Mth.sin((cyc - 0.73F) / 0.27F * 3.14159F);
    }
    if (pose == LOOK) {
        raise = 1.0F;                       // frozen at the top of the swing
    }
    this.body.xRot = 0.22F + raise * 0.04F;
    this.body.yRot = -0.08F * raise;
    this.neck.xRot = 0.25F;
    this.head.xRot = 0.3F;
    this.leftArm.xRot = -0.744F;
    this.leftArm.zRot = 0.281F;
    this.leftArm.yRot = 0.175F;
    this.leftForearm.xRot = -0.384F;
    this.leftHand.xRot = -0.545F;
    this.rightArm.xRot = Mth.lerp(raise, -0.378F, -2.250F);
    this.rightArm.zRot = Mth.lerp(raise, 0.090F, 0.126F);
    this.rightArm.yRot = Mth.lerp(raise, -0.392F, -0.125F);
    this.rightForearm.xRot = Mth.lerp(raise, -1.620F, -2.102F);
    this.rightHand.xRot = Mth.lerp(raise, 1.600F, 1.379F);
    if (pose == LOOK) {
        // the body stays dead still; only the head comes round, slowly, much too far, over the shoulder
        float side = yaw < -0.05F ? -1.0F : 1.0F;
        float turn = ease(pt / 34.0F);
        float stp = jerky(pt / 34.0F, 5.0F);
        float tt = Mth.lerp(0.35F, turn, stp);
        this.neck.yRot = side * 0.4F * tt;
        this.head.yRot = side * 2.4F * tt;
        this.head.xRot = 0.3F - 0.45F * tt;
        this.neck.xRot = 0.25F - 0.25F * tt;
        this.head.zRot = side * 0.12F * tt + jit * 0.3F;
        this.flare.visible = pt > 26.0F && noise(t * 0.8F) > -0.2F;
        blend = 5.0F;
    }
} else if (pose == STOVE) {
    // leaning in over the flames: the left hand works a pan, the cleaver hangs, the face down to the fire
    float sw = Mth.sin(t * 0.18F);
    this.body.xRot = 0.42F + breath * 0.015F;
    this.neck.xRot = 0.2F;
    this.head.xRot = 0.35F + Mth.sin(t * 0.05F) * 0.05F;
    this.head.yRot = 0.15F;
    this.leftArm.xRot = -1.05F + sw * 0.08F;
    this.leftArm.zRot = -0.12F;
    this.leftForearm.xRot = -0.65F;
    this.leftHand.xRot = -0.2F + sw * 0.15F;
    this.rightArm.xRot = -0.45F;
    this.rightArm.zRot = 0.12F;
    this.rightForearm.xRot = -0.55F;
    this.flare.visible = noise(t * 0.5F) > 0.2F;
} else if (pose == PLATE) {
    // at the pass, facing the dining room: head bowed over the plate, small precise movements; the bell at 100
    float a1 = Mth.sin(t * 0.21F);
    float a2 = Mth.sin(t * 0.13F + 1.0F);
    float bell = span(pt, 96.0F, 99.0F) * (1.0F - span(pt, 101.0F, 106.0F));
    this.body.xRot = 0.2F;
    this.neck.xRot = 0.18F;
    this.head.xRot = 0.32F + lookP * 0.2F;
    this.head.yRot = a2 * 0.05F;
    this.leftArm.xRot = -0.992F + a1 * 0.05F - bell * 0.25F;
    this.leftArm.zRot = 0.524F;
    this.leftArm.yRot = 0.182F;
    this.leftForearm.xRot = -0.027F + a2 * 0.08F;
    this.leftHand.xRot = -0.391F + a1 * 0.1F + bell * 0.5F;
    this.rightArm.xRot = -0.457F;
    this.rightArm.zRot = -0.524F;
    this.rightArm.yRot = -0.061F;
    this.rightForearm.xRot = -1.263F;
    this.rightHand.xRot = 1.500F;
} else if (pose == WALK) {
    float a = Math.min(1.0F, limbSwingAmount * 1.4F);
    float c = limbSwing * 0.7F;
    float s = Mth.sin(c);
    this.rightLeg.xRot = s * 0.5F * a;
    this.leftLeg.xRot = -s * 0.5F * a;
    this.rightShin.xRot = Math.max(0.0F, Mth.sin(c - 1.1F)) * 0.6F * a;
    this.leftShin.xRot = Math.max(0.0F, -Mth.sin(c - 1.1F)) * 0.6F * a;
    this.body.y = 10.5F + Math.abs(Mth.cos(c)) * 0.5F * a;
    this.rightLeg.y = this.body.y;
    this.leftLeg.y = this.body.y;
    this.body.zRot = s * 0.05F * a;
    this.body.yRot = -s * 0.07F * a;
    this.body.xRot = 0.12F;
    this.rightArm.xRot = -s * 0.3F * a;
    this.leftArm.xRot = s * 0.35F * a;
    this.rightArm.zRot = 0.08F;
    this.leftArm.zRot = -0.08F;
    this.rightForearm.xRot = -0.35F;
    this.leftForearm.xRot = -0.2F;
    this.neck.xRot = 0.15F;
    this.head.xRot = 0.15F + lookP * 0.4F;
    this.head.yRot = look * 0.5F;
} else if (pose == CHASE) {
    // charging, the cleaver raised high, roaring
    float a = Math.min(1.0F, limbSwingAmount * 1.2F);
    float c = limbSwing * 0.85F;
    float s = Mth.sin(c);
    this.rightLeg.xRot = s * 0.8F * a - 0.1F;
    this.leftLeg.xRot = -s * 0.8F * a - 0.1F;
    this.rightShin.xRot = Math.max(0.0F, Mth.sin(c - 1.0F)) * 1.0F * a + 0.1F;
    this.leftShin.xRot = Math.max(0.0F, -Mth.sin(c - 1.0F)) * 1.0F * a + 0.1F;
    this.body.y = 11.0F + Math.abs(Mth.cos(c)) * 0.9F * a;
    this.rightLeg.y = this.body.y;
    this.leftLeg.y = this.body.y;
    this.body.xRot = 0.35F;
    this.body.yRot = -s * 0.1F * a;
    this.rightArm.xRot = -2.75F + jit * 0.3F;
    this.rightArm.zRot = -0.15F;
    this.rightForearm.xRot = -1.1F + Mth.sin(t * 0.5F) * 0.15F;
    this.rightHand.xRot = -0.6F;
    this.leftArm.xRot = -0.9F + s * 0.5F * a;
    this.leftArm.zRot = -0.2F;
    this.leftForearm.xRot = -0.9F;
    this.leftHand.xRot = -0.3F;
    this.neck.xRot = -0.15F;
    this.head.xRot = -0.25F + lookP * 0.5F;
    this.head.yRot = look * 0.6F;
    float o = 0.75F + Mth.sin(t * 0.37F) * 0.2F;
    this.mouth.visible = true;
    this.jaw.visible = true;
    this.mouth.yScale = o;
    this.jaw.y = this.jaw.y + 2.0F * o;
    this.flare.visible = true;
    blend = 4.0F;
} else {
    // IDLE: wheezing, the shoulders heaving, the cleaver hanging heavy
    this.body.xRot = 0.1F + breath * 0.02F + wheeze * 0.03F;
    this.body.y = 10.5F - wheeze * 0.25F;
    this.neck.xRot = 0.2F + wheeze * 0.05F;
    this.head.xRot = 0.25F + Mth.sin(t * 0.031F) * 0.06F;
    this.head.zRot = Mth.sin(t * 0.017F) * 0.08F + twitch(t, 71.0F, 9.0F) * 0.25F;
    this.head.yRot = look * 0.4F;
    this.rightArm.xRot = 0.05F;
    this.rightArm.zRot = 0.1F + wheeze * 0.04F;
    this.leftArm.zRot = -0.1F - wheeze * 0.04F;
    this.rightForearm.xRot = -0.3F;
    this.leftForearm.xRot = -0.25F;
}
this.head.zRot = this.head.zRot + jit * 0.3F;
this.head.xRot = this.head.xRot + jit2 * 0.2F;
this.rightArm.zRot = this.rightArm.zRot + jit2 * 0.15F;
'''
    post = '''
// the apron hangs from the waist and swings a little with the legs
this.apron.xRot = -this.body.xRot * 0.85F + (this.leftLeg.xRot + this.rightLeg.xRot) * -0.25F
    + Math.min(this.leftLeg.xRot, this.rightLeg.xRot) * 0.35F;
'''


SPEC = Chef


import ent_scene as SC  # noqa: E402
POSE_SCENES = {0: SC.kitchen(board=True), 1: SC.kitchen(board=True), 2: SC.kitchen(pass_window=True), 3: SC.kitchen(board=True), 4: SC.kitchen(), 5: SC.kitchen(), 6: SC.kitchen()}
WALKING = (4, 5)
NAKED_ALPHA = 0.75


# ================================================================================================ previews
def previews(b, ER, rows_sel=None):
    import ent_scene as SC
    rows = []
    kb = SC.kitchen(board=True)
    kp = SC.kitchen(pass_window=True)
    k0 = SC.kitchen()
    ims = []
    for (yv, lab) in ((180, 'back (the pass)'), (150, '3/4 back'), (90, 'side'), (0, 'front')):
        ims.append((lab, b.view(0, 40, yaw_view=yv, scene=kb)))
    for tt in (0, 3, 6, 7, 8):
        ims.append((f'side t{tt}', b.view([(0, 0)], 300 + tt, yaw_view=70, scene=kb)))
    rows.append(('CHOP (0): back to the room, chopping a 10-tick rhythm (impact at 7)', ims))
    ims = []
    for ptv in (0, 8, 16, 24, 34, 50):
        ims.append((f'pt {ptv}', b.view([(0, 0), (100, 3)], 100 + ptv, yaw_view=175, head=lambda tt: (40.0, 0.0), scene=kb)))
    ims.append(('close pt 50', b.view([(0, 0), (100, 3)], 150, yaw_view=165, pitch_view=4, head=lambda tt: (40.0, 0.0),
                                      size=(190, 190), ortho=0.5, center_y=1.85, scene=kb)))
    rows.append(('LOOK (3): the chopping stops, the head turns slowly over the shoulder, ~160 degrees', ims))
    ims = []
    for (yv, lab) in ((90, 'side'), (35, '3/4'), (0, 'front')):
        ims.append((lab, b.view(1, 40, yaw_view=yv, scene=kb)))
    rows.append(('STOVE (1): leaning in over the flames', ims))
    ims = []
    for (yv, lab, ptv) in ((0, 'front pt 40', 40), (30, '3/4 pt 60', 60), (0, 'bell pt 100', 100), (80, 'side', 40)):
        ims.append((lab, b.view(2, ptv, yaw_view=yv, pitch_view=0, scene=kp)))
    ims.append(('through the pass', b.view(2, 40, size=(240, 270), eye=(0.2, 1.6, 2.6), target=(0, 1.55, 0), scene=kp, fov=50)))
    rows.append(('PLATE (2): at the pass, head bowed, arranging (photo window through the pass)', ims))
    walk = lambda tt: (tt * 0.5, 0.85)                         # noqa: E731
    ims = []
    for tt in (0, 5, 10):
        ims.append((f'WALK side t{tt}', b.view([(0, 4)], 100 + tt, yaw_view=90, limb=walk, scene=k0)))
    ims.append(('WALK front', b.view([(0, 4)], 104, yaw_view=10, limb=walk, scene=k0)))
    run = lambda tt: (tt * 0.8, 1.0)                           # noqa: E731
    for tt in (0, 4):
        ims.append((f'CHASE side t{tt}', b.view([(0, 5)], 200 + tt, yaw_view=90, limb=run, scene=k0)))
    ims.append(('CHASE front', b.view([(0, 5)], 202, yaw_view=-20, limb=run, scene=k0)))
    rows.append(('WALK (4) / CHASE (5): cleaver raised, roaring, embers blazing', ims))
    ims = []
    for (yv, lab, tt) in ((0, 'front', 60), (40, '3/4', 60), (20, 'twitch', 71 * 3 - 9 + 1)):
        ims.append((lab, b.view(6, tt, yaw_view=yv, scene=k0)))
    ims.append(('face close', b.view(6, 60, yaw_view=0, pitch_view=0, size=(190, 190), ortho=0.5, center_y=1.8, scene=k0)))
    ims.append(('CHASE close', b.view([(0, 5)], 202, yaw_view=0, pitch_view=0, size=(190, 190), ortho=0.5, center_y=1.8,
                                      limb=run, scene=k0)))
    rows.append(('IDLE (6): wheezing; face close-ups', ims))
    ims = []
    ims.append(('naked eye a=0.75: CHOP', b.view(0, 40, size=(300, 260), eye=(-0.5, 1.62, -4.5), target=(0, 1.3, 0),
                                                night=True, alpha=0.75, eyes_gain=1.0, scene=kb, fov=50)))
    ims.append(('naked eye: LOOK', b.view([(0, 0), (100, 3)], 150, size=(300, 260), eye=(-0.5, 1.62, -4.5),
                                          target=(0, 1.5, 0), night=True, alpha=0.75, eyes_gain=1.0, head=lambda tt: (40.0, 0.0), scene=kb,
                                          fov=50)))
    ims.append(('finder: CHASE', b.view([(0, 5)], 202, size=(300, 260), eye=(0.3, 1.62, 3.5), target=(0, 1.4, 0),
                                        finder=True, limb=run, scene=k0, fov=50)))
    rows.append(('night: through the pass behind him / hunting', ims))
    return rows
