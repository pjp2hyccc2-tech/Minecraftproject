"""ritual.* - the procedure, done alone at the low table at 3 a.m. (mono, positional, intimate; the player's hands
are half a metre from the ears, so everything is close and dry: the acoustics engine adds the room).

  cut          dressmaker's shears through the plush: blade on blade, the pile shearing, the tips closing
  stuffing     polyester fibrefill pulled out in clumps
  rice         rice poured from the scoop into the opening: dull into cloth, crisper as it fills, strays on the wood
  clip         a lever nail clipper, three snaps
  sew          needle through the fabric, the thread drawn after it, pulled tight
  thread_wind  the red thread wound round and round, the rice inside shifting, a knot
  splash       the doll dropped into the full deep tub; the soaked plush breathes out its air
  stab         the kitchen knife into a waterlogged plush full of rice: the violence of the night
  salt_pour    salt into a glass of water, stirred
  spit         salt water spat out (stereo: your own mouth)
  burn_ignite  the lighter, then the fur catching
  burn_loop    the doll burning: roar, crackle, the wet cloth hissing, rice grains bursting"""
import numpy as np

import dsp
import instruments as ins
import kit as K
import voice as V
from dsp import SR, TAU, ns

BLADE = [(2650, 0.09, 1.0), (4150, 0.07, 0.8), (5850, 0.05, 0.5), (7700, 0.035, 0.3), (1450, 0.08, 0.35)]
KNIFE = [(1850, 0.35, 0.8), (3300, 0.25, 1.0), (4900, 0.18, 0.6), (6800, 0.12, 0.35), (980, 0.3, 0.3)]
GLASS = [(2480, 0.5, 1.0), (4050, 0.35, 0.55), (6150, 0.22, 0.35), (8600, 0.12, 0.2)]


def _slide(dur, rng, rate_pts, amp_pts, modes=BLADE, bright=1.0):
    """Steel sliding on steel: stick-slip pulses through the blades' modes plus friction hiss."""
    n = ns(dur)
    rate = dsp.env_pts(n, rate_pts) * (1 + 0.15 * dsp.smooth(n, rng, 30.0, periodic=False))
    amp = dsp.env_pts(n, amp_pts)
    x = ins.friction_pulses(dur, rng, rate, amp, jitter=0.35)
    y = dsp.resbank(x, [(f * bright, 18, a) for f, T, a in modes])
    hiss = dsp.bandpass(rng.standard_normal(n), 3000, 11000, 2) * amp
    return K.nrm(y) + K.rmsn(hiss) * 0.08


# --------------------------------------------------------------------------
def cut(rng, i=1):
    """Four snips along the seam of the plush, the hand shifting the fabric between them, the cut falling open."""
    dur = 3.1
    y = np.zeros(ns(dur))
    t = 0.05
    for k in range(4):
        close = rng.uniform(0.15, 0.2)
        m = ns(close)
        e = K.env_shape(m, [(0, 0), (close * 0.15, 1.0), (close * 0.85, 0.9), (close, 0)])
        sl = _slide(close, rng, [(0, 900), (close, 2600)], [(0, 0), (0.01, 1.0), (close * 0.8, 0.7), (close, 0)])
        K.place(y, sl * 0.35, t)
        # the pile shearing: a dense crunch of fibres travelling with the cutting point
        sh = K.fibres_tear(close, rng, rate=3400, env=e ** 1.2, bright=0.9)
        sh = dsp.lowpass(sh, 7000, 2)
        K.place(y, K.nrm(sh) * 0.9, t + 0.01)
        thick = K.fabric(close, rng, e, coarse=1.4, bright=0.8)
        K.place(y, K.nrm(thick) * 0.25, t)
        # the tips meet
        tip = ins.modal_hit(rng, BLADE, size=0.9, hardness=1.0, vel=0.6, dur=0.18, noise=0.25)
        K.place(y, K.nrm(tip) * 0.32, t + close - 0.005)
        # opening again: a lighter slide back
        op = rng.uniform(0.11, 0.15)
        K.place(y, _slide(op, rng, [(0, 1600), (op, 700)], [(0, 0), (0.01, 0.5), (op, 0)]) * 0.14, t + close + 0.07)
        # the other hand shifts the plush along
        sd = rng.uniform(0.2, 0.3)
        K.place(y, K.fabric(sd, rng, K.env_shape(ns(sd), [(0, 0), (sd * 0.4, 1.0), (sd, 0)]), coarse=0.8) * 0.12,
                t + close + 0.12)
        t += close + rng.uniform(0.42, 0.55)
    # the cut falls open: stuffing bulges out with a soft, airy puff
    d = 0.45
    K.place(y, K.stuffing(d, rng, K.env_shape(ns(d), [(0, 0), (0.05, 1.0), (d, 0)], 1.5)) * 0.06, t + 0.02)
    return K.fin(K.rev(y, rng, 0.2, 0.05), 60)


def stuffing(rng, i=1):
    """Three clumps of fibrefill pulled out of the cut: fibres hissing and squeaking as they slide free."""
    dur = 3.4
    y = np.zeros(ns(dur))
    t = 0.05
    for k, d in enumerate((0.75, 0.6, 0.95)):
        m = ns(d)
        e = K.env_shape(m, [(0, 0), (d * 0.2, 0.6), (d * 0.7, 1.0), (d * 0.82, 0.4), (d, 0)], 1.2)
        st = K.stuffing(d, rng, e)
        K.place(y, st * (0.8 + 0.2 * k), t)
        edge = K.fabric(d, rng, e * 0.7, coarse=0.7, bright=0.9)  # the cut's edges rub on the fingers
        K.place(y, K.nrm(edge) * 0.12, t)
        # the clump comes free: a soft tearing release, then it is dropped onto the table
        rel = K.fibres_tear(0.08, rng, rate=2600, env=K.env_shape(ns(0.08), [(0, 1.0), (0.08, 0)]), bright=0.8)
        K.place(y, K.nrm(rel) * 0.3, t + d * 0.75)
        drop = K.burst(rng, 0.06, 200, 1500, 0.004, 0.012)
        K.place(y, K.nrm(drop) * 0.12, t + d + 0.12)
        t += d + rng.uniform(0.22, 0.35)
    return K.fin(K.rev(y, rng, 0.2, 0.05), 80)


def rice(rng, i=1):
    """Rice poured from the scoop into the plush: the grains land dull in the empty cloth, crisper as it fills; a
    few strays bounce on the low table."""
    dur = 3.8
    n = ns(dur)
    pour = 2.8
    rate = dsp.env_pts(n, [(0, 0), (0.12, 0.0), (0.3, 1.0), (1.9, 0.9), (2.5, 0.5), (pour, 0.05), (pour + 0.15, 0), (dur, 0)])
    rate = rate * (1 + 0.25 * dsp.smooth(n, rng, 6.0, periodic=False))
    rate = np.clip(rate, 0, None)
    full = dsp.env_pts(n, [(0, 0), (0.4, 0.0), (pour, 1.0), (dur, 1.0)])  # how full the cloth is
    into_cloth = K.rice_pour(dur, rng, 9000 * rate * (1 - 0.8 * full) + 1.0, into="fabric")
    into_rice = K.rice_pour(dur, rng, 9000 * rate * (0.2 + 0.8 * full) + 1.0, into="rice", bright=0.9)
    y = K.rmsn(into_cloth) * 0.7 + K.rmsn(into_rice) * 0.5
    hush = dsp.colored(n, rng, -1.0, lo=1500, hi=9000) * rate  # the stream of grains through the air
    y += K.rmsn(hush) * 0.12
    body = K.fabric(dur, rng, rate * 0.5, coarse=0.5, bright=0.7)  # the plush's skin shifting as it swells
    y += K.nrm(body) * 0.12
    y = dsp.lowpass(y, 9000, 2)
    for _ in range(9):  # strays on the low table
        t0 = rng.uniform(0.4, pour + 0.2)
        g = K.rice_pour(0.05, rng, np.full(ns(0.05), 60.0), into="wood")
        K.place(y, K.nrm(g) * rng.uniform(0.15, 0.4), t0)
        if rng.random() < 0.5:  # a bounce
            K.place(y, K.nrm(K.rice_pour(0.03, rng, np.full(ns(0.03), 60.0), into="wood")) * 0.12, t0 + rng.uniform(0.04, 0.09))
    return K.fin(K.rev(y, rng, 0.2, 0.05), 100)


def clip(rng, i=1):
    """A lever nail clipper: press, the crisp 'pachin' of a nail cut through, a fragment ticking on the floor, the
    lever springing back.  Three times."""
    dur = 2.6
    y = np.zeros(ns(dur))
    t = 0.12
    for k in range(3):
        pr = K.creak(rng, 0.12, pitch=2.6, rate=(60, 140), q=1.5)  # the lever pressed, the spring loading
        K.place(y, K.nrm(pr) * 0.06, t - 0.11)
        snap = ins.modal_hit(rng, [(3150, 0.05, 1.0), (4750, 0.04, 0.8), (6900, 0.03, 0.5), (9100, 0.02, 0.3), (2100, 0.05, 0.3)],
                             size=1.0, hardness=1.0, vel=1.0, dur=0.15, noise=0.6)
        crack = K.burst(rng, 0.006, 1800, 12000, 0.0001, 0.0009)  # the keratin breaking
        K.place(y, K.nrm(snap) * 0.85 + 0.0, t)
        K.place(y, K.nrm(crack) * 0.7, t + 0.0007)
        if k != 1:  # the cut-off piece lands
            K.place(y, K.drop_on(rng, "wood", 0.08), t + rng.uniform(0.18, 0.32))
        rel = ins.modal_hit(rng, [(2600, 0.04, 1.0), (4200, 0.03, 0.6), (1300, 0.05, 0.4)], hardness=0.8, vel=0.35, dur=0.1, noise=0.4)
        K.place(y, K.nrm(rel) * 0.22, t + rng.uniform(0.09, 0.13))
        t += rng.uniform(0.68, 0.82)
    return K.fin(K.rev(y, rng, 0.15, 0.04), 200)


def sew(rng, i=1):
    """Two stitches: the needle pops through the plush, is pulled out, the thread drawn after it and pulled tight."""
    dur = 3.4
    y = np.zeros(ns(dur))
    t = 0.08
    for k in range(2):
        pop = K.burst(rng, 0.012, 1200, 9000, 0.0003, 0.0018)  # the point through the fabric
        K.place(y, K.nrm(pop) * 0.35, t)
        K.place(y, K.fabric(0.12, rng, K.env_shape(ns(0.12), [(0, 1.0), (0.12, 0)]), coarse=0.6) * 0.06, t)
        nd = 0.18  # the needle pulled out: a thin steel squeak
        sq = ins.creak(nd, rng, [(0, 400), (nd, 900)], [(0, 0), (0.01, 0.8), (nd, 0)],
                       modes=[(5200, 30, 1.0), (7600, 30, 0.5)], jitter=0.3)
        K.place(y, K.nrm(sq) * 0.08, t + 0.15)
        td = rng.uniform(0.85, 1.05)  # the thread drawn through the hole: a long, thin rasp, faster, then tight
        m = ns(td)
        rate = dsp.env_pts(m, [(0, 250), (td * 0.6, 1500), (td, 600)])
        amp = K.env_shape(m, [(0, 0), (0.06, 0.6), (td * 0.7, 1.0), (td - 0.05, 0.4), (td, 0)])
        x = ins.friction_pulses(td, rng, rate, amp, jitter=0.5)
        th = dsp.resbank(x, [(3100, 6, 1.0), (4700, 7, 0.7), (6900, 7, 0.4), (2000, 5, 0.4)])
        th = K.nrm(th) + K.rmsn(dsp.bandpass(rng.standard_normal(m), 2500, 9000, 2) * amp) * 0.15
        K.place(y, th * 0.3, t + 0.35)
        tug = K.creak(rng, 0.09, pitch=1.6, rate=(80, 160), q=1.2)  # pulled tight
        K.place(y, K.nrm(tug) * 0.12, t + 0.35 + td - 0.02)
        t += 0.35 + td + rng.uniform(0.35, 0.5)
    return K.fin(K.rev(y, rng, 0.15, 0.04), 200)


def thread_wind(rng, i=1):
    """The red thread wound round the doll, five turns, tighter each time; the rice inside shifts; a knot."""
    dur = 4.0
    n = ns(dur)
    y = np.zeros(n)
    t = 0.08
    for k in range(5):
        d = rng.uniform(0.48, 0.58)
        m = ns(d)
        e = K.env_shape(m, [(0, 0), (d * 0.2, 1.0), (d * 0.8, 0.8), (d, 0)])
        sweep = 2800 + 1800 * np.linspace(0, 1, m) * (1 if k % 2 == 0 else -1) + 1200
        x = dsp.bandpass(rng.standard_normal(m), 1500, 9000, 2) * e
        x = dsp.tv_filter(x, sweep, 2.0, "bp", block=128)
        K.place(y, K.rmsn(x) * 0.12 * (0.8 + 0.1 * k), t)
        rc = K.rice_inside(d, rng, e * (0.4 + 0.12 * k), muffle=0.7)  # the body squeezed a little more each turn
        K.place(y, K.nrm(rc) * 0.18, t + 0.04)
        K.place(y, K.fabric(d, rng, e * 0.6, coarse=0.6, bright=0.8) * 0.08, t)
        t += d + rng.uniform(0.02, 0.08)
    for k in range(2):  # the knot pulled, twice
        tug = K.creak(rng, 0.1, pitch=1.5, rate=(90, 170), q=1.3)
        K.place(y, K.nrm(tug) * 0.25, t + 0.1 + k * 0.22)
        K.place(y, K.nrm(K.rice_inside(0.15, rng, K.env_shape(ns(0.15), [(0, 1.0), (0.15, 0)]), muffle=0.6)) * 0.12, t + 0.1 + k * 0.22)
    return K.fin(K.rev(y, rng, 0.15, 0.04), 120)


def splash(rng, i=1):
    """The rice-heavy doll dropped into the full deep tub: the plop and its cavity's bloop, spray, waves slapping the
    tiles, then the soaked plush breathing its air out in bubbles."""
    dur = 4.2
    n = ns(dur)
    y = np.zeros(n)
    sp = K.splash(rng, size=1.0, dur=2.2, bright=0.9)
    K.place(y, K.nrm(sp), 0.02)
    m = ns(3.2)
    e = K.env_shape(m, [(0, 0), (0.2, 1.0), (1.6, 0.5), (3.2, 0)], 1.2)
    gl = K.bubbles(3.2, rng, 260, (0.6, 3.5), env=e)  # air leaving the fibres and the rice
    big = K.bubbles(3.2, rng, 22, (4.0, 9.0), env=e ** 2)  # a few big glugs
    K.place(y, K.nrm(gl) * 0.22 + K.nrm(big) * 0.3, 0.25)
    sl = K.slosh(2.6, rng, [(0.35, 1.0), (0.95, 0.7), (1.6, 0.45), (2.2, 0.25)], bright=0.5)
    K.place(y, K.nrm(sl) * 0.35, 0.1)
    for t0 in (1.1, 2.3, 3.4):  # your wet hand dripping back into the tub
        K.place(y, K.drip(rng, rng.uniform(600, 950), 0.15), t0)
    y = dsp.highpass(y, 45, 2)
    return K.fin(K.rev(y, rng, 0.75, 0.12, hf=0.75), 45)


def stab(rng, i=1):
    """The kitchen knife driven into a waterlogged plush full of rice, through it into the tub's floor.  A quick
    rush of the arm, a wet pillowy blow, wet fabric tearing, the blade grinding through packed rice with a squelch
    of water forced out, the tip knocking the tub through the doll, a slap of displaced water."""
    dur = 2.0
    n = ns(dur)
    y = np.zeros(n)
    t0 = 0.09
    # the arm coming down
    m = ns(0.1)
    sw = dsp.bandpass(rng.standard_normal(m), 400, 3000, 2) * K.env_shape(m, [(0, 0), (0.08, 1.0), (0.1, 0)], 2.0)
    K.place(y, K.rmsn(sw) * 0.04, t0 - 0.095)
    # the blow: a heavy wet thump (hand and handle driving into the soaked body)
    th = K.plush_thump(rng, weight=1.6, wet=1.0, floor="tile", dur=0.5)
    K.place(y, K.nrm(th) * 0.9, t0)
    m = ns(0.35)
    tt = np.arange(m) / SR
    sub = np.sin(TAU * np.cumsum(75 + 70 * np.exp(-tt / 0.015)) / SR) * dsp.env_ad(m, 0.002, 0.032)
    K.place(y, np.tanh(2.2 * sub) * 0.35, t0)
    flesh = dsp.bandpass(rng.standard_normal(m), 140, 650, 2) * dsp.env_ad(m, 0.0015, 0.022)  # a fleshy, wet blow
    K.place(y, K.nrm(flesh) * 0.75, t0 + 0.001)
    # wet fabric puncture
    tear = K.fibres_tear(0.07, rng, rate=6000, env=K.env_shape(ns(0.07), [(0, 1.0), (0.07, 0)], 1.5), bright=0.75)
    tear = dsp.lowpass(tear, 5500, 2)
    K.place(y, K.nrm(tear) * 0.55, t0 + 0.002)
    # the blade through packed rice: a dense grinding crunch, a few grains cracking loud
    gd = 0.32
    gm = ns(gd)
    ge = K.env_shape(gm, [(0, 0), (0.008, 1.0), (0.12, 0.8), (gd, 0)], 1.3)
    grind = K.rice_pour(gd, rng, 26000 * ge + 10, into="rice", bright=0.85)
    grind = dsp.lowpass(grind, 7500, 2)
    K.place(y, K.nrm(grind) * 0.6, t0 + 0.012)
    for k in range(6):
        cr = K.burst(rng, 0.004, 1500, 9000, 0.0001, 0.0006)
        K.place(y, K.nrm(cr) * rng.uniform(0.25, 0.5), t0 + 0.015 + rng.uniform(0, 0.16))
    scrape = _slide(0.22, rng, [(0, 1600), (0.22, 500)], [(0, 0), (0.01, 1.0), (0.22, 0)], modes=KNIFE, bright=0.9)
    K.place(y, dsp.lowpass(scrape, 6000, 2) * 0.12, t0 + 0.02)
    # water squeezed out of the fibres: a gush of small bubbles, a squirt
    sq_m = ns(0.5)
    sqe = K.env_shape(sq_m, [(0, 0), (0.01, 1.0), (0.12, 0.6), (0.5, 0)], 1.5)
    gush = K.bubbles(0.5, rng, 1600, (0.5, 2.2), env=sqe)
    squirt = dsp.bandpass(rng.standard_normal(sq_m), 900, 4500, 2) * sqe ** 2
    K.place(y, K.nrm(gush) * 0.5 + K.rmsn(squirt) * 0.05, t0 + 0.01)
    # the tip meets the tub's floor through the doll: a muffled knock of the tub
    knock = ins.modal_hit(rng, [(310, 0.12, 1.0), (590, 0.09, 0.7), (980, 0.06, 0.4), (1650, 0.04, 0.2)], hardness=0.7,
                          vel=1.0, dur=0.5, noise=0.2)
    K.place(y, dsp.lowpass(K.nrm(knock), 2200, 2) * 0.45, t0 + 0.07)
    # the blade's steel rings for an instant before the wet cloth chokes it
    ring = ins.modal_hit(rng, KNIFE, size=1.0, hardness=1.0, vel=1.0, dur=0.3, noise=0.1)
    ring *= np.exp(-np.arange(len(ring)) / (0.03 * SR))
    K.place(y, K.nrm(ring) * 0.12, t0 + 0.07)
    # the water it displaced slaps the tub walls, falls back
    sl = K.slosh(1.3, rng, [(0.0, 1.0), (0.4, 0.55), (0.75, 0.3)], bright=0.6)
    K.place(y, K.nrm(sl) * 0.4, t0 + 0.06)
    spl = K.splash(rng, size=0.5, dur=1.0, bright=0.8)
    K.place(y, K.nrm(spl) * 0.3, t0 + 0.01)
    y = dsp.highpass(y, 35, 2)
    y = np.tanh(1.6 * K.nrm(y)) / np.tanh(1.6)  # a little weight in the hit
    return K.fin(K.rev(y, rng, 0.7, 0.08, hf=0.7), 35)


def salt_pour(rng, i=1):
    """Salt shaken into a glass of water: a fine hiss of grains on the surface, a few on the rim; then stirred."""
    dur = 3.0
    n = ns(dur)
    y = np.zeros(n)
    pd = 1.3
    pm = ns(pd)
    e = K.env_shape(pm, [(0, 0), (0.08, 1.0), (pd * 0.7, 0.8), (pd, 0)])
    e = e * (0.75 + 0.25 * np.abs(np.sin(TAU * 3.2 * np.arange(pm) / SR)))  # the shaker's rhythm
    fizz = dsp.colored(pm, rng, 0.0, lo=2500, hi=12000) * e
    grains = K.stream(pd, rng, 3500, amp_env=e, pareto=3.0)
    grains = K.grain_bank(grains, [(4500, 2.0, 1.0), (7500, 2.0, 0.6), (2600, 1.5, 0.4)], decay=0.0004)
    plinks = K.bubbles(pd, rng, 300, (0.3, 0.9), env=e)
    K.place(y, K.rmsn(fizz) * 0.12 + K.rmsn(grains) * 0.3 + K.nrm(plinks) * 0.25, 0.05)
    for _ in range(5):  # grains on the glass rim
        g = ins.modal_hit(rng, GLASS, size=0.6, hardness=1.0, vel=rng.uniform(0.05, 0.15), dur=0.1, noise=0.5)
        K.place(y, g, rng.uniform(0.1, pd))
    t = pd + 0.35
    for k in range(4):  # stirred with the spoon: the spoon ticks the glass, the water swirls
        g = ins.modal_hit(rng, GLASS, size=1.0, hardness=1.0, vel=rng.uniform(0.25, 0.45), dur=0.5, noise=0.2)
        K.place(y, K.nrm(g) * rng.uniform(0.3, 0.55), t)
        t += rng.uniform(0.22, 0.32)
    sw = K.slosh(1.2, rng, [(0.0, 0.4), (0.4, 0.3)], level=0.5, bright=1.0)
    K.place(y, dsp.highpass(sw, 600, 2) * 0.25, pd + 0.3)
    return K.fin(K.rev(y, rng, 0.15, 0.04), 200)


def spit(rng, i=1):
    """Salt water spat at the doll: lips burst, the spray, the patter of it landing ahead; a breath after.  Stereo:
    the mouth inside your head, the splatter in front."""
    dur = 1.8
    n = ns(dur)
    mouth = np.zeros(n)
    m = ns(0.06)
    puff = dsp.lowpass(rng.standard_normal(m), 260, 2) * dsp.env_ad(m, 0.003, 0.015)
    K.place(mouth, puff * 1.2, 0.02)
    K.place(mouth, K.burst(rng, 0.02, 600, 6000, 0.0005, 0.004) * 0.8, 0.02)  # the lips part
    sd = 0.34
    sm = ns(sd)
    se = K.env_shape(sm, [(0, 0), (0.01, 1.0), (0.12, 0.7), (sd, 0)], 1.2)
    spray = dsp.colored(sm, rng, -0.5, lo=900, hi=11000) * se
    drops = K.stream(sd, rng, 5000, amp_env=se, pareto=2.4)
    drops = K.grain_bank(drops, [(2400, 2.0, 1.0), (4200, 2.5, 0.7), (6800, 2.0, 0.4)], decay=0.0008)
    wet = K.bubbles(sd, rng, 900, (0.3, 1.2), env=se)
    K.place(mouth, K.rmsn(spray) * 0.25 + K.rmsn(drops) * 0.25 + K.nrm(wet) * 0.2, 0.03)
    mouth = dsp.close_mic(dsp.highpass(mouth, 80, 2), 3.0, 2.0, 1.0)
    pat = np.zeros(n)  # landing on the doll and the floor
    pm = ns(0.45)
    pe = K.env_shape(pm, [(0, 0), (0.02, 1.0), (0.45, 0)], 2.0)
    patter = K.stream(0.45, rng, 1400, amp_env=pe, pareto=2.2)
    patter = K.grain_bank(patter, [(1400, 1.5, 1.0), (2600, 2.0, 0.6), (4800, 2.0, 0.3)], decay=0.0012)
    K.place(pat, dsp.lowpass(K.nrm(patter), 7000, 2) * 0.35, 0.12)
    br = V.breath(rng, "in", 0.5, table=V.VF, scale=1.0, tremor=0.3)  # the breath you can take again
    K.place(mouth, br * np.std(mouth[mouth != 0]) / (np.std(br) + 1e-9) * 0.6, 0.85)
    out = np.vstack([mouth, mouth]) + np.vstack([pat, np.roll(pat, ns(0.0007))])
    return dsp.trim(out, -62)


def _lighter(rng, catch=True, strikes=2):
    """A disposable lighter: the flint wheel scraped (sparks), on the last strike the gas catching with a soft puff."""
    y = np.zeros(ns(1.2 + 0.35 * strikes))
    t = 0.03
    for k in range(strikes):
        d = rng.uniform(0.07, 0.1)
        m = ns(d)
        rate = dsp.env_pts(m, [(0, 900), (d, 400)])
        x = ins.friction_pulses(d, rng, rate, K.env_shape(m, [(0, 0), (0.005, 1.0), (d, 0)]), jitter=0.5)
        wheel = dsp.resbank(x, [(2900, 8, 1.0), (4600, 9, 0.7), (7100, 9, 0.4)])
        K.place(y, K.nrm(wheel) * 0.55, t)
        sparks = ins.crackle(0.12, rng, 400, hp=4000, decay=0.0002)
        K.place(y, K.nrm(sparks) * 0.25, t + 0.02)
        t += d + rng.uniform(0.25, 0.4)
    if catch:
        t -= rng.uniform(0.25, 0.4)
        m = ns(0.8)
        e = K.env_shape(m, [(0, 0), (0.02, 1.0), (0.12, 0.45), (0.8, 0.3)])
        gas = dsp.colored(m, rng, -1.0, lo=300, hi=6000) * e
        gas *= 1 + 0.4 * dsp.smooth(m, rng, 18.0, periodic=False)
        pf = dsp.lowpass(rng.standard_normal(ns(0.05)), 500, 2) * dsp.env_ad(ns(0.05), 0.003, 0.01)
        K.place(y, K.rmsn(gas) * 0.12, t + 0.02)
        K.place(y, pf * 0.6, t + 0.02)
    return y


def burn_ignite(rng, i=1):
    """The lighter (one failed strike, then it catches), the flame held to the wet fur... then the synthetic pile
    catches all at once with a rushing whoosh, and the crackling starts."""
    dur = 4.0
    n = ns(dur)
    y = np.zeros(n)
    lt = _lighter(rng, catch=True, strikes=2)
    K.place(y, K.nrm(lt) * 0.55, 0.0)
    t_catch = 1.75
    m = ns(1.0)  # the wet cloth hissing under the flame before it takes
    hs = dsp.colored(m, rng, 0.0, lo=2500, hi=9000) * K.env_shape(m, [(0, 0), (0.3, 0.5), (1.0, 0.8)])
    K.place(y, K.rmsn(hs) * 0.05, t_catch - 0.85)
    wd = dur - t_catch  # whoosh: the pile flashes over, then burns on as a low roar
    wm = ns(wd)
    we = K.env_shape(wm, [(0, 0), (0.15, 1.0), (0.45, 0.75), (1.2, 0.38), (wd - 0.5, 0.3), (wd, 0)])
    rush = dsp.colored(wm, rng, -2.5, lo=60, hi=7000)
    rush = dsp.tv_filter(rush, 600 + 4000 * we, 0.8, "lp", block=128) * we
    roar = dsp.lowpass(rng.standard_normal(wm), 300, 2) * we * (1 + 0.5 * dsp.smooth(wm, rng, 9.0, periodic=False))
    K.place(y, K.rmsn(rush) * 0.45 + K.rmsn(roar) * 0.35, t_catch)
    cm = ns(dur - t_catch)
    ce = K.env_shape(cm, [(0, 0), (0.2, 1.0), (dur - t_catch, 0.8)])
    crk = ins.crackle(dur - t_catch, rng, 220, env=ce, hp=1200, decay=0.0006)
    K.place(y, K.nrm(crk) * 0.4, t_catch + 0.05)
    sz = dsp.colored(cm, rng, 0.0, lo=3000, hi=10000) * ce * (1 + 0.6 * dsp.smooth(cm, rng, 14.0, periodic=False))
    K.place(y, K.rmsn(sz) * 0.06, t_catch + 0.1)
    return K.fin(K.rev(y, rng, 0.25, 0.05), 50)


def burn_loop(rng, i=1):
    """The doll burning in the sink: the flames' low turbulent roar, the fabric crackling, the soaked cloth hissing
    and spitting steam, rice grains bursting in the heat, melting fibre dripping."""
    L = 8.0
    n = ns(L)
    flick = 1 + 0.35 * dsp.smooth(n, rng, 7.0) + 0.2 * dsp.smooth(n, rng, 1.2)
    roar = dsp.colored(n, rng, -4.0, lo=50, hi=2500) * flick
    crk = K.stream(L, rng, 260, amp_env=np.clip(flick, 0.2, None), pareto=1.9)
    crk = K.grain_bank(crk, [(1700, 1.0, 1.0), (3300, 1.2, 0.7), (6000, 1.2, 0.4)], decay=0.0005, L=L)
    hiss = dsp.colored(n, rng, 0.0, lo=2500, hi=11000) * (0.6 + 0.4 * K.norm01(dsp.smooth(n, rng, 2.5)))
    steam = K.bubbles(L, rng, 400, (0.25, 0.8), L=L)
    pops = np.zeros(n)
    t = rng.uniform(0, 0.3)
    while t < L:  # rice bursting
        p = ins.modal_hit(rng, [(2200, 0.012, 1.0), (3900, 0.008, 0.7), (1100, 0.015, 0.4)], hardness=1.0,
                          vel=rng.uniform(0.4, 1.0), dur=0.05, noise=0.7)
        K.place(pops, p, t, L=L)
        K.place(pops, K.burst(rng, 0.03, 900, 6000, 0.001, 0.006) * 0.3, t + 0.002, L=L)
        t += rng.exponential(0.45) + 0.08
    drips = np.zeros(n)
    for _ in range(5):  # melting fibre dripping and sizzling
        d = rng.uniform(0.15, 0.3)
        m = ns(d)
        sz = dsp.colored(m, rng, 0.0, lo=3000, hi=9000) * K.env_shape(m, [(0, 0), (0.005, 1.0), (d, 0)], 2.0)
        K.place(drips, sz, rng.uniform(0, L), L=L)
    y = (K.rmsn(roar) * 0.5 + K.nrm(crk) * 0.45 + K.rmsn(hiss) * 0.07 + K.nrm(steam) * 0.12 + K.nrm(pops) * 0.35
         + K.rmsn(drips) * 0.05)
    return dsp.dc_block(y, circular=True)
