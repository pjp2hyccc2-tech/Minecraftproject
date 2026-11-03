"""doll.* - the thing that used to be a stuffed rabbit (mono, positional), and the woman's breath.

The doll is small, soaked and full of rice: nothing it does is hard or loud except the knife.  Its steps are soft
wet thumps with rice shifting inside; it drags itself fast on its arms with its legs trailing; it drips; the kitchen
knife (bigger than its arm) ticks and scrapes along behind it.  When the salt water hits it, it shrieks."""
import numpy as np

import dsp
import instruments as ins
import kit as K
import voice as V
from dsp import SR, TAU, ns

KNIFE = [(1850, 0.35, 0.8), (3300, 0.25, 1.0), (4900, 0.18, 0.6), (6800, 0.12, 0.35), (980, 0.3, 0.3)]
WOOD_FRAME = [(310, 0.06, 1.0), (620, 0.05, 0.8), (1040, 0.04, 0.6), (1700, 0.03, 0.4), (2600, 0.02, 0.25)]


def _pull(rng, floor="tatami", vel=1.0, wet=1.0):
    """One arm pull of the crawling doll: the soggy paw slapping down, the body dragged a hand's length, rice shifting."""
    y = np.zeros(ns(0.42))
    pw = K.plush_thump(rng, weight=0.45 * vel, wet=wet, floor=floor, dur=0.25)
    K.place(y, K.nrm(pw) * 0.7, 0.0)
    d = rng.uniform(0.16, 0.24)
    m = ns(d)
    e = K.env_shape(m, [(0, 0), (d * 0.25, 1.0), (d * 0.7, 0.7), (d, 0)])
    dr = K.fabric(d, rng, e, wet=wet * 0.8, coarse=0.9 if floor == "tatami" else 0.5, bright=1.1 if floor == "tatami" else 0.9)
    K.place(y, K.nrm(dr) * 0.55 * vel, 0.05)
    rc = K.rice_inside(d, rng, e * 0.7, muffle=0.75)
    K.place(y, K.nrm(rc) * 0.2 * vel, 0.06)
    return y


def step(rng, i=1):
    """One stiff marionette step: the soaked foot set down (no heel, no hardness), rice settling, water pressed out;
    sometimes a drip falls after."""
    floor = {1: "tatami", 2: "tatami", 3: "wood", 4: "wood"}[i]
    y = np.zeros(ns(0.7))
    sc = K.fabric(0.08, rng, K.env_shape(ns(0.08), [(0, 0), (0.03, 1.0), (0.08, 0)]), wet=0.8, coarse=0.5)
    K.place(y, K.nrm(sc) * 0.12, 0.0)  # the foot lifted / swung stiffly
    th = K.plush_thump(rng, weight=[0.9, 1.0, 0.85, 1.0][i - 1], wet=1.0, floor=floor, dur=0.5)
    K.place(y, K.nrm(th), 0.07)
    if i in (2, 4):
        K.place(y, K.drop_on(rng, "tatami" if floor == "tatami" else "wood", 0.18), 0.07 + rng.uniform(0.25, 0.4))
    return K.fin(K.rev(y, rng, 0.12, 0.04), 50)


def crawl(rng, i=1):
    """A burst of fast crawling: arm over arm, the body slithering, the legs trailing behind with a wet hiss."""
    floor = {1: "tatami", 2: "wood", 3: "tatami"}[i]
    pulls = {1: 3, 2: 3, 3: 5}[i]
    per = {1: 0.27, 2: 0.25, 3: 0.21}[i]
    dur = pulls * per + 0.6
    n = ns(dur)
    y = np.zeros(n)
    for k in range(pulls):
        K.place(y, _pull(rng, floor, vel=rng.uniform(0.8, 1.0) * (1.0 if k else 0.85)), 0.02 + k * per * rng.uniform(0.92, 1.08))
    e = K.env_shape(n, [(0, 0), (0.1, 1.0), (pulls * per, 0.9), (pulls * per + 0.25, 0), (dur, 0)])
    legs = K.fabric(dur, rng, e * 0.6, wet=0.9, coarse=0.4)
    y += K.nrm(legs) * 0.25
    return K.fin(K.rev(y, rng, 0.12, 0.04), 50)


def drip(rng, i=1):
    """Water dripping off the doll: 1 onto tatami, 2 onto the floorboards, 3 into the tub / a puddle."""
    dur = 1.6
    y = np.zeros(ns(dur))
    t = 0.02
    for k in range({1: 3, 2: 3, 3: 4}[i]):
        if i == 1:
            d = K.drop_on(rng, "tatami", rng.uniform(0.6, 1.0))
        elif i == 2:
            d = K.drop_on(rng, "wood", rng.uniform(0.6, 1.0))
        else:
            d = K.drip(rng, rng.uniform(600, 1100), rng.uniform(0.6, 1.0))
        K.place(y, d, t)
        t += rng.uniform(0.25, 0.5)
    return K.fin(K.rev(y, rng, 0.2, 0.06), 150)


def rice(rng, i=1):
    """The rice inside it shifting: 1 the head turning slowly, 2 a shift and grains spilling from the wound onto
    the floor, 3 the body slumping and settling."""
    if i == 1:
        d = 1.1
        e = K.env_shape(ns(d), [(0, 0), (0.3, 0.7), (0.75, 1.0), (d, 0)], 1.2)
        y = K.nrm(K.rice_inside(d, rng, e, muffle=0.65)) + K.nrm(K.fabric(d, rng, e * 0.6, wet=0.8, coarse=0.4)) * 0.25
    elif i == 2:
        d = 0.6
        e = K.env_shape(ns(d), [(0, 0), (0.05, 1.0), (d, 0)], 1.5)
        y = np.zeros(ns(1.4))
        K.place(y, K.nrm(K.rice_inside(d, rng, e, muffle=0.6)), 0.0)
        sp = K.rice_pour(0.6, rng, 900 * K.env_shape(ns(0.6), [(0, 0), (0.05, 1.0), (0.6, 0)], 2.0) + 1, into="wood")
        K.place(y, K.nrm(sp) * 0.5, 0.18)
    else:
        d = 0.9
        e = K.env_shape(ns(d), [(0, 0), (0.04, 1.0), (0.3, 0.5), (d, 0)], 1.4)
        y = np.zeros(ns(1.2))
        K.place(y, K.nrm(K.plush_thump(rng, weight=0.5, wet=1.0, floor="tatami")) * 0.5, 0.0)
        K.place(y, K.nrm(K.rice_inside(d, rng, e, muffle=0.7)), 0.01)
    return K.fin(K.rev(y, rng, 0.12, 0.04), 120)


def _drag_scrape(dur, rng, floor):
    """The knife's tip / spine dragged over the floor."""
    n = ns(dur)
    t = np.arange(n) / SR
    speed = np.clip(0.6 + 0.4 * dsp.smooth(n, rng, 1.5, periodic=False), 0.15, 1.2)
    e = K.env_shape(n, [(0, 0), (0.05, 1.0), (dur - 0.1, 0.9), (dur, 0)]) * speed
    if floor == "wood":  # stick-slip on varnished planks: a scratchy rasp, skipping on the grain
        rate = 300 + 900 * speed
        x = ins.friction_pulses(dur, rng, rate, e, jitter=0.6)
        rasp = dsp.resbank(x, [(f, 10, a) for f, T, a in KNIFE]) + dsp.resbank(x, [(1300, 4, 0.6), (2400, 5, 0.4)])
        skips = np.zeros(n)
        for _ in range(int(dur * 3)):
            K.place(skips, ins.modal_hit(rng, KNIFE, size=1.0, hardness=1.0, vel=rng.uniform(0.3, 0.8), dur=0.25, noise=0.2),
                    rng.uniform(0.05, dur - 0.05))
        hiss = dsp.bandpass(rng.standard_normal(n), 2500, 9000, 2) * e
        y = K.nrm(rasp) + K.nrm(skips) * 0.35 + K.rmsn(hiss) * 0.06
    else:  # tatami: the tip catching the rush weave every few millimetres - a buzzing, ticking rasp
        rate = 60 + 70 * speed
        x = ins.friction_pulses(dur, rng, rate, e, jitter=0.12, width=0.0003)
        tick = dsp.resbank(x, [(f, 14, a) for f, T, a in KNIFE])
        rush = dsp.bandpass(rng.standard_normal(n), 1200, 7000, 2) * e * (0.6 + 0.4 * np.abs(np.sin(TAU * 7 * t)))
        y = K.nrm(tick) + K.rmsn(rush) * 0.12
    return y


def knife_drag(rng, i=1):
    """The kitchen knife dragged along behind it: 1 over the floorboards, 2 over tatami."""
    dur = 1.7
    y = _drag_scrape(dur, rng, "wood" if i == 1 else "tatami")
    y = np.concatenate([np.zeros(ns(0.02)), y, np.zeros(ns(0.3))])
    return K.fin(K.rev(y, rng, 0.15, 0.05), 200)


def knife_stab(rng, i=1):
    """The knife driven through the closet's fusuma from outside: the taut paper bursts, the blade bites the
    wooden lattice, is worked loose (wood creaking, paper tearing) and pulled back out."""
    dur = 1.7
    n = ns(dur)
    y = np.zeros(n)
    t0 = 0.03
    m = ns(0.12)
    tt = np.arange(m) / SR
    membrane = np.sin(TAU * np.cumsum(260 + 180 * np.exp(-tt / 0.008)) / SR) * dsp.env_ad(m, 0.0005, 0.018)
    K.place(y, membrane * 0.8, t0)  # the paper's drum thump
    pop = K.burst(rng, 0.02, 400, 12000, 0.0002, 0.003)
    K.place(y, K.nrm(pop) * 1.0, t0)
    tear = K.fibres_tear(0.08, rng, rate=9000, env=K.env_shape(ns(0.08), [(0, 1.0), (0.08, 0)], 1.3), bright=1.1)
    K.place(y, K.nrm(tear) * 0.7, t0 + 0.002)
    bite = ins.modal_hit(rng, WOOD_FRAME, size=0.9, hardness=0.85, vel=1.0, dur=0.35, noise=0.35)
    K.place(y, K.nrm(bite) * 0.9, t0 + 0.035)
    ring = ins.modal_hit(rng, KNIFE, size=1.0, hardness=1.0, vel=1.0, dur=0.5, noise=0.1)
    K.place(y, K.nrm(ring) * 0.18, t0 + 0.036)
    for k in range(2):  # worked loose
        cr = K.creak(rng, 0.18, pitch=1.4, rate=(50, 120), q=1.2)
        K.place(y, K.nrm(cr) * 0.25, t0 + 0.35 + k * 0.25)
        tr = K.fibres_tear(0.12, rng, rate=2500, env=K.env_shape(ns(0.12), [(0, 0), (0.03, 1.0), (0.12, 0)]), bright=1.0)
        K.place(y, K.nrm(tr) * 0.25, t0 + 0.37 + k * 0.25)
    out = 0.32  # pulled out: steel sliding on the wood, the torn paper flapping back
    m = ns(out)
    x = ins.friction_pulses(out, rng, np.linspace(1500, 500, m), K.env_shape(m, [(0, 0), (0.02, 1.0), (out, 0)]), jitter=0.4)
    sl = dsp.resbank(x, [(f, 14, a) for f, T, a in KNIFE])
    K.place(y, K.nrm(sl) * 0.25, t0 + 0.95)
    flap = K.burst(rng, 0.05, 300, 5000, 0.002, 0.01)
    K.place(y, K.nrm(flap) * 0.2, t0 + 1.25)
    return K.fin(K.rev(y, rng, 0.2, 0.05), 60)


def shriek(rng, i=1):
    """The salt water hits it: an inhuman shriek - a child's scream with a second throat in it, chaotic, breaking -
    and a hiss like water thrown on a hot plate; it collapses into a gurgle."""
    dur = 2.4
    n = ns(dur)
    chaos = [(0, 3.92), (0.12, 3.92), (0.16, 3.3), (0.55, 3.3), (0.6, 3.95), (1.2, 3.9), (1.25, 3.55), (1.6, 3.96), (dur, 3.96)]
    s1, _ = V.scream(dur, rng, [(0, 900), (0.03, 1650), (0.4, 1850), (1.0, 1700), (1.6, 1400), (2.0, 700), (dur, 300)],
                     [(0, 0), (0.008, 1.0), (1.4, 0.9), (1.9, 0.5), (dur, 0)],
                     vowel_pts=[(0, "a"), (0.6, "i"), (1.3, "a"), (dur, "u")], chaos_pts=chaos,
                     jumps=[(0.58, 4, None), (1.22, -5, None)], breath=0.55, rasp=1.2, rough=(0.75, 66, 0.55),
                     scale=1.24, oq=0.38, closure=0.6,
                     biphon=dict(ratio=[(0, 0.71), (dur, 0.66)], amp=[(0, 0.0), (0.4, 0.0), (0.6, 0.5), (1.5, 0.6), (dur, 0.3)]))
    y = K.nrm(dsp.pad_to(s1, n))
    sz_m = ns(1.6)  # the salt: sizzling on it
    se = K.env_shape(sz_m, [(0, 0), (0.01, 1.0), (0.4, 0.6), (1.6, 0)], 1.3)
    sizzle = dsp.colored(sz_m, rng, 0.0, lo=2500, hi=12000) * se * (1 + 0.7 * dsp.smooth(sz_m, rng, 25.0, periodic=False))
    crk = ins.crackle(1.6, rng, 900, env=se, hp=2500, decay=0.0003)
    K.place(y, K.rmsn(sizzle) * 0.08 + K.nrm(crk) * 0.25, 0.0)
    gm = ns(0.8)  # collapsing: water in it, a wet gurgle
    g = K.bubbles(0.8, rng, 300, (0.6, 3.0), env=K.env_shape(gm, [(0, 0), (0.1, 1.0), (0.8, 0)]))
    K.place(y, K.nrm(g) * 0.3, 1.9)
    y = dsp.highpass(y, 140, 2)
    return K.fin(K.rev(y, rng, 0.3, 0.06), 140)


def breath(rng, i=1):
    """At the fusuma: 1 slow wet breaths - a child's breathing slowed down, water rattling in it; 2 sniffing for you."""
    if i == 1:
        y = np.zeros(ns(4.6))
        for t0, kind, d in ((0.05, "in", 1.3), (1.5, "out", 1.8), (3.5, "in", 1.0)):
            b = V.breath(rng, kind, d, table=V.VF, scale=1.22, voiced=0.1 if kind == "out" else 0.0, f0=300, wet=1.2,
                         rasp=0.35, tremor=0.2)
            K.place(y, b, t0)
        y = dsp.varispeed(y, 0.82)  # too slow
        g = K.bubbles(len(y) / SR, rng, 50, (0.8, 2.5))
        y = y + K.nrm(g) * np.std(y) * 1.2
    else:
        y = np.zeros(ns(2.4))
        t = 0.05
        for k in range(5):  # sniffs: short nasal inhales
            d = rng.uniform(0.07, 0.11)
            sn = V.breath(rng, "in", d, table=V.VF, scale=1.22, vowel_in="m")
            sn = dsp.bandpass(sn, 400, 5000, 2)
            K.place(y, sn * rng.uniform(0.7, 1.0), t)
            t += d + rng.uniform(0.06, 0.14)
        ex = V.breath(rng, "out", 0.7, table=V.VF, scale=1.22, wet=1.0, rasp=0.2)
        K.place(y, ex * 0.8, t + 0.25)
        rc = K.rice_inside(0.5, rng, K.env_shape(ns(0.5), [(0, 0), (0.1, 1.0), (0.5, 0)]), muffle=0.7)
        K.place(y, K.nrm(rc) * np.std(y) * 2.0, t + 0.4)
    y = dsp.lowpass(y, 5000, 2)
    return K.fin(dsp.close_mic(dsp.highpass(y, 120, 2), 3.0, 2.0, 0.0), 120)


def drag_loop(rng, i=1):
    """It drags itself across the floor (loop): arm over arm, about two pulls a second, the wet body slithering, the
    legs trailing, now and then the knife's tip ticking on the floor behind it."""
    L = 12.0
    n = ns(L)
    y = np.zeros(n)
    t = 0.0
    k = 0
    while t < L - 1e-3:
        floor = "tatami" if (t // 6) % 2 == 0 else "wood"  # half on tatami, half on the boards
        K.place(y, _pull(rng, floor, vel=rng.uniform(0.75, 1.0)), t, L=L)
        t += rng.uniform(0.42, 0.52) if k % 7 != 6 else rng.uniform(0.8, 1.0)  # a pause now and then
        k += 1
    e = 0.55 + 0.45 * K.norm01(dsp.smooth(n, rng, 1.2))
    legs = K.fabric(L, rng, e * 0.6, wet=0.9, coarse=0.4)
    legs = K.cr(legs, lambda v: dsp.lowpass(v, 4500, 2))
    y += K.nrm(legs) * 0.25
    for t0 in (1.7, 4.9, 8.2, 10.6):  # the knife behind it
        sc = _drag_scrape(rng.uniform(0.3, 0.6), rng, "tatami" if t0 < 6 else "wood")
        K.place(y, K.nrm(sc) * 0.2, t0, L=L)
    return dsp.dc_block(y, circular=True)


def woman_breath(rng, i=1):
    """A woman breathing right at the camera's microphone: a long, wet, rasping breath out, a click of the tongue, a
    slow breath in."""
    y = np.zeros(ns(4.2))
    ex = V.breath(rng, "out", 2.0, table=V.VF, scale=0.98, voiced=0.12, f0=170, wet=0.8, rasp=0.4, tremor=0.25)
    K.place(y, ex, 0.05)
    K.place(y, V.mouth_clicks(0.4, rng, rate=4.0) * np.std(ex) * 3.0, 2.0)
    inh = V.breath(rng, "in", 1.5, table=V.VF, scale=0.98, tremor=0.3, hiss=0.15)
    K.place(y, inh * 0.8, 2.45)
    y = dsp.close_mic(dsp.highpass(y, 100, 2), 5.0, 3.0, 2.0)
    return K.fin(y, 100)
