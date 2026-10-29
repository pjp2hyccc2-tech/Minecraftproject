"""Helpers for authoring keyframe animations procedurally.

All helpers sample a function at `steps` evenly spaced times (CATMULLROM keys), producing smooth,
organic motion (breathing, tail sways, wing beats) without hand-writing every key."""
import math


def sample(anim, bone, chan, fn, steps=None, t0=0.0, t1=None):
    """fn(t) -> (x,y,z). Adds keys over [t0, t1] (defaults to the whole animation)."""
    t1 = anim.length if t1 is None else t1
    steps = steps or max(4, int(round((t1 - t0) / 0.1)))
    keys = []
    for i in range(steps + 1):
        t = t0 + (t1 - t0) * i / steps
        keys.append((t, fn(t)))
    getattr(anim, {'rotation': 'rot', 'position': 'pos', 'scale': 'scale'}[chan])(bone, keys)
    return anim


def sine(anim, bone, chan, amp=(0, 0, 0), period=None, phase=0.0, offset=(0, 0, 0), steps=None):
    """offset + amp * sin(2*pi*(t/period) + phase). Period defaults to the animation length (seamless loop)."""
    period = period or anim.length
    return sample(anim, bone, chan, lambda t: tuple(o + a * math.sin(2 * math.pi * t / period + phase) for a, o in zip(amp, offset)), steps)


def chain_wave(anim, bones, amp=(0, 10, 0), period=None, phase_step=0.6, growth=1.0, chan='rotation', phase=0.0, steps=None):
    """Travelling wave down a bone chain (tails, necks, tentacles): each bone lags behind the previous."""
    for i, b in enumerate(bones):
        g = growth ** i
        sine(anim, b, chan, tuple(a * g for a in amp), period, phase - i * phase_step, steps=steps)
    return anim


def poses(anim, bone, chan, keys):
    """keys: list of (t, (x,y,z)) -- explicit poses, smoothed with catmull-rom."""
    getattr(anim, {'rotation': 'rot', 'position': 'pos', 'scale': 'scale'}[chan])(bone, keys)
    return anim


def ease_in_out(x):
    return 0.5 - 0.5 * math.cos(math.pi * max(0.0, min(1.0, x)))


def strike(anim, bone, chan, rest, windup, hit, t_wind, t_hit, t_recover, t_end=None, overshoot=0.15):
    """Classic attack curve: rest -> windup (anticipation) -> hit (fast) -> slight overshoot -> rest."""
    t_end = anim.length if t_end is None else t_end
    over = tuple(h + (h - w) * overshoot for h, w in zip(hit, windup))
    keys = [(0.0, rest), (t_wind, windup), (t_hit, hit), (min(t_end, t_hit + (t_recover - t_hit) * 0.3), over), (t_recover, hit), (t_end, rest)]
    return poses(anim, bone, chan, keys)


def hold(anim, bone, chan, value):
    """Constant offset for the whole animation (2 keys)."""
    return poses(anim, bone, chan, [(0.0, value), (anim.length, value)])
