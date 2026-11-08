"""Signal processing building blocks. Arrays are float64; stereo is shape (2, n).

Everything that has memory (filters, reverb, chorus, delays, limiter) has a loop-aware variant: a looping piece is
processed as if it had been playing forever, so the last sample flows into the first without a seam.
"""
import numpy as np
from scipy import signal
from scipy.ndimage import maximum_filter1d, minimum_filter1d, uniform_filter1d

SR = 44100
TAU = 2.0 * np.pi


def db_to_gain(db):
    return 10.0 ** (db / 20.0)


def gain_to_db(g):
    return 20.0 * np.log10(np.maximum(g, 1e-12))


def rng_for(*keys):
    """Deterministic generator per name (stable across runs and Python versions)."""
    h = 2166136261
    for key in keys:
        for ch in str(key).encode("utf-8"):
            h = ((h ^ ch) * 16777619) & 0xFFFFFFFF
    return np.random.default_rng(h)


def mtof(m):
    return 440.0 * 2.0 ** ((np.asarray(m, dtype=float) - 69.0) / 12.0)


# --------------------------------------------------------------------------------------------- envelopes

def adsr(gate, attack, decay, sustain, release, sr=SR, attack_shape="smooth"):
    """Envelope of a note held for `gate` seconds, including its release tail (release starts at the gate level).

    With decay > 0 the attack peaks at 1 and decays to `sustain`; with decay == 0 it rises straight to `sustain`.
    """
    ng = max(1, int(round(gate * sr)))
    nr = max(1, int(round(release * sr)))
    t = np.arange(ng) / sr
    if attack > 0:
        x = np.clip(t / attack, 0.0, 1.0)
        if attack_shape == "smooth":
            att = np.sin(x * np.pi / 2) ** 2
        elif attack_shape == "exp":
            att = (1.0 - np.exp(-5.0 * x)) / (1.0 - np.exp(-5.0))
        else:
            att = x
    else:
        att = np.ones(ng)
    if decay > 0:
        after = sustain + (1.0 - sustain) * np.exp(-np.maximum(t - attack, 0.0) / (decay / 4.0))
        env = np.where(t < attack, att, after)
    else:
        env = np.where(t < attack, att * sustain, sustain)
    last = env[-1]
    tr = np.arange(nr) / nr
    k = 4.0
    rel = last * (np.exp(-k * tr) - np.exp(-k)) / (1.0 - np.exp(-k))
    return np.concatenate([env, rel])


def fade(x, fade_in=0.0, fade_out=0.0, sr=SR):
    y = np.array(x, dtype=float, copy=True)
    n = y.shape[-1]
    if fade_in > 0:
        k = min(n, int(fade_in * sr))
        y[..., :k] *= np.sin(np.linspace(0, np.pi / 2, k)) ** 2
    if fade_out > 0:
        k = min(n, int(fade_out * sr))
        y[..., n - k:] *= np.cos(np.linspace(0, np.pi / 2, k)) ** 2
    return y


def smooth_random(n, rate_hz, rng, sr=SR):
    """Band-limited random curve in [-1, 1] (cubic interpolation of random points)."""
    points = max(4, int(np.ceil(n / sr * rate_hz)) + 4)
    vals = rng.uniform(-1.0, 1.0, points)
    xs = np.arange(points) * (sr / rate_hz)
    from scipy.interpolate import CubicSpline
    cs = CubicSpline(xs, vals)
    out = cs(np.arange(n))
    return np.clip(out, -1.5, 1.5) / 1.2


def periodic_random(n, cycles, rng, harmonics=6):
    """Smooth random curve that repeats exactly every n samples (for looping beds), roughly in [-1, 1]."""
    t = np.arange(n) / n
    out = np.zeros(n)
    for h in range(1, harmonics + 1):
        k = cycles * h
        out += rng.normal() / h * np.sin(TAU * k * t + rng.uniform(0, TAU))
    return out / (np.max(np.abs(out)) + 1e-12)


# --------------------------------------------------------------------------------------------- filters (RBJ)

def _biquad(kind, f, q, gain_db, sr):
    f = min(max(f, 5.0), sr * 0.49)
    a_ = 10 ** (gain_db / 40.0)
    w0 = TAU * f / sr
    cw, sw = np.cos(w0), np.sin(w0)
    alpha = sw / (2 * q)
    if kind == "lowpass":
        b = [(1 - cw) / 2, 1 - cw, (1 - cw) / 2]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "highpass":
        b = [(1 + cw) / 2, -(1 + cw), (1 + cw) / 2]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "bandpass":  # constant 0 dB peak gain
        b = [alpha, 0, -alpha]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "peak":
        b = [1 + alpha * a_, -2 * cw, 1 - alpha * a_]
        a = [1 + alpha / a_, -2 * cw, 1 - alpha / a_]
    elif kind == "lowshelf":
        sq = 2 * np.sqrt(a_) * alpha
        b = [a_ * ((a_ + 1) - (a_ - 1) * cw + sq), 2 * a_ * ((a_ - 1) - (a_ + 1) * cw),
             a_ * ((a_ + 1) - (a_ - 1) * cw - sq)]
        a = [(a_ + 1) + (a_ - 1) * cw + sq, -2 * ((a_ - 1) + (a_ + 1) * cw), (a_ + 1) + (a_ - 1) * cw - sq]
    elif kind == "highshelf":
        sq = 2 * np.sqrt(a_) * alpha
        b = [a_ * ((a_ + 1) + (a_ - 1) * cw + sq), -2 * a_ * ((a_ - 1) + (a_ + 1) * cw),
             a_ * ((a_ + 1) + (a_ - 1) * cw - sq)]
        a = [(a_ + 1) - (a_ - 1) * cw + sq, 2 * ((a_ - 1) - (a_ + 1) * cw), (a_ + 1) - (a_ - 1) * cw - sq]
    else:
        raise ValueError(kind)
    b = np.array(b) / a[0]
    a = np.array(a) / a[0]
    return b, a


def biquad(x, kind, f, q=0.707, gain_db=0.0, sr=SR):
    b, a = _biquad(kind, f, q, gain_db, sr)
    return signal.lfilter(b, a, x, axis=-1)


def lowpass(x, f, q=0.707, sr=SR):
    return biquad(x, "lowpass", f, q, sr=sr)


def highpass(x, f, q=0.707, sr=SR):
    return biquad(x, "highpass", f, q, sr=sr)


def bandpass(x, f, q=1.0, sr=SR):
    return biquad(x, "bandpass", f, q, sr=sr)


def butter(x, kind, f, order=4, sr=SR):
    if isinstance(f, (tuple, list)):
        wn = [min(v, sr * 0.49) / (sr / 2) for v in f]
    else:
        wn = min(f, sr * 0.49) / (sr / 2)
    sos = signal.butter(order, wn, btype=kind, output="sos")
    return signal.sosfilt(sos, x, axis=-1)


def eq_chain(x, bands, sr=SR):
    """bands: list of (kind, f, q, gain_db)."""
    y = x
    for kind, f, q, g in bands:
        y = biquad(y, kind, f, q, g, sr)
    return y


def one_pole_lp(x, f, sr=SR):
    a = np.exp(-TAU * f / sr)
    return signal.lfilter([1 - a], [1, -a], x, axis=-1)


def circular(process, x, warm, loop=True):
    """Runs a stateful process on a loop as if it had been playing forever (warm-up from the loop's own end)."""
    if not loop:
        return process(x)
    n = x.shape[-1]
    warm = min(warm, n)
    ext = np.concatenate([x[..., n - warm:], x], axis=-1)
    return process(ext)[..., warm:]


# --------------------------------------------------------------------------------------------- stereo

def pan_gains(pan):
    """Constant power; pan in [-1, 1]."""
    p = (np.clip(pan, -1, 1) + 1) * np.pi / 4
    return np.cos(p), np.sin(p)


def to_stereo(mono, pan=0.0):
    gl, gr = pan_gains(pan)
    return np.stack([mono * gl, mono * gr])


def width(stereo, amount):
    """amount 0 = mono, 1 = unchanged, >1 wider."""
    mid = 0.5 * (stereo[0] + stereo[1])
    side = 0.5 * (stereo[0] - stereo[1]) * amount
    return np.stack([mid + side, mid - side])


# --------------------------------------------------------------------------------------------- convolution

def convolve_stereo(x, ir_l, ir_r, loop=True, cross=0.25):
    """Stereo reverb: each output channel convolves a mix of the inputs with its own impulse response."""
    n = x.shape[-1]
    inl = (1 - cross) * x[0] + cross * x[1]
    inr = (1 - cross) * x[1] + cross * x[0]
    out = []
    for sig, ir in ((inl, ir_l), (inr, ir_r)):
        y = signal.fftconvolve(sig, ir)
        if loop:
            res = y[:n].copy()
            tail = y[n:]
            while tail.size:
                k = min(n, tail.size)
                res[:k] += tail[:k]
                tail = tail[k:]
            out.append(res)
        else:
            out.append(y[:n])
    return np.stack(out)


def make_ir(rt60=2.2, length=None, predelay=0.02, low_rt=None, high_rt=None, early=12, early_span=0.07,
            density_ms=0.0, brightness=6000.0, seed="hall", sr=SR):
    """Synthetic stereo room: early reflections plus a decorrelated noise tail decaying per band."""
    rng = rng_for("ir", seed)
    low_rt = low_rt or rt60 * 1.15
    high_rt = high_rt or rt60 * 0.55
    length = length or rt60 * 1.1 + predelay
    n = int(length * sr)
    t = np.arange(n) / sr
    irs = []
    for ch in range(2):
        noise = rng.normal(size=n)
        lo = butter(noise, "lowpass", 350, 2, sr)
        hi = butter(noise, "highpass", 3500, 2, sr)
        mid = noise - lo - hi
        tt = np.maximum(t - predelay, 0)
        tail = (lo * np.exp(-6.9 * tt / low_rt) + mid * np.exp(-6.9 * tt / rt60) + hi * np.exp(-6.9 * tt / high_rt))
        build = np.clip((t - predelay) / 0.035, 0, 1) ** 1.5
        tail *= build
        tail = one_pole_lp(tail, brightness, sr)
        er = np.zeros(n)
        for _ in range(early):
            d = predelay * 0.4 + rng.uniform(0.004, early_span)
            i = int(d * sr)
            if i < n:
                er[i] += rng.choice([-1, 1]) * rng.uniform(0.35, 1.0) * np.exp(-d / 0.08)
        er = one_pole_lp(er, 7000, sr)
        ir = tail / np.sqrt(np.sum(tail ** 2) + 1e-12) + 0.6 * er / np.sqrt(np.sum(er ** 2) + 1e-12)
        ir[-int(0.05 * sr):] *= np.linspace(1, 0, int(0.05 * sr))
        irs.append(ir)
    norm = np.sqrt((np.sum(irs[0] ** 2) + np.sum(irs[1] ** 2)) / 2)
    return irs[0] / norm, irs[1] / norm


def delay_ir(delay_s, feedback=0.4, taps=6, pingpong=True, damp=4500.0, sr=SR):
    n = int(delay_s * (taps + 1) * sr) + 8
    l = np.zeros(n)
    r = np.zeros(n)
    for k in range(1, taps + 1):
        i = int(round(k * delay_s * sr))
        g = feedback ** (k - 1)
        if pingpong:
            (l if k % 2 else r)[i] += g
        else:
            l[i] += g
            r[i] += g
    return one_pole_lp(l, damp, sr), one_pole_lp(r, damp, sr)


# --------------------------------------------------------------------------------------------- modulation

def frac_read(x, pos, loop=True):
    """Reads x at fractional sample positions (linear interpolation; wraps for loops)."""
    n = x.shape[-1]
    i0 = np.floor(pos).astype(np.int64)
    fr = pos - i0
    if loop:
        i0 %= n
        i1 = (i0 + 1) % n
    else:
        i0 = np.clip(i0, 0, n - 1)
        i1 = np.clip(i0 + 1, 0, n - 1)
    return x[..., i0] * (1 - fr) + x[..., i1] * fr


def chorus(x, loop=True, voices=3, base_ms=14.0, depth_ms=3.5, rate=0.3, mix=0.45, spread=0.8, seed="chorus",
           period=None, sr=SR):
    """Ensemble chorus (stereo in, stereo out). LFO rates are snapped to whole cycles per `period` seconds
    (default: the signal length), so a looping piece modulates identically on every pass."""
    rng = rng_for("chorus", seed)
    n = x.shape[-1]
    dur = period or n / sr
    mono = 0.5 * (x[0] + x[1])
    wet = np.zeros_like(x)
    idx = np.arange(n, dtype=float)
    for v in range(voices):
        r = rate * (0.8 + 0.45 * v / max(1, voices - 1))
        r = max(1, round(r * dur)) / dur
        ph = rng.uniform(0, TAU)
        d = (base_ms + depth_ms * np.sin(TAU * r * idx / sr + ph)) * sr / 1000.0
        tap = frac_read(mono, idx - d, loop)
        pan = spread * (-1 + 2 * v / max(1, voices - 1)) if voices > 1 else 0.0
        gl, gr = pan_gains(pan)
        wet[0] += tap * gl
        wet[1] += tap * gr
    wet /= np.sqrt(voices)
    return (1 - mix) * x + mix * wet


# --------------------------------------------------------------------------------------------- dynamics

def compressor(x, threshold_db=-18.0, ratio=3.0, attack=0.01, release=0.15, makeup_db=0.0, knee_db=6.0,
               loop=True, sr=SR):
    """Feed-forward RMS compressor. Detector and gain smoothing are linear one-pole filters, so a looping signal is
    processed exactly as its steady state (warm-up from the loop's own end)."""

    def run(sig):
        power = sig ** 2 if sig.ndim == 1 else np.max(sig ** 2, axis=0)
        tau = 0.5 * (attack + release)
        a = np.exp(-1.0 / (tau * sr))
        env = signal.lfilter([1 - a], [1, -a], power)
        over = 10 * np.log10(env + 1e-12) - threshold_db
        gr = np.where(over <= -knee_db / 2, 0.0,
                      np.where(over >= knee_db / 2, over * (1 - 1 / ratio),
                               (1 - 1 / ratio) * (over + knee_db / 2) ** 2 / (2 * knee_db)))
        a2 = np.exp(-1.0 / (attack * sr))
        gr = signal.lfilter([1 - a2], [1, -a2], gr)
        return sig * db_to_gain(makeup_db - gr)

    return circular(run, x, int(sr * 3), loop)


def true_peak(x, oversample=4):
    if x.ndim == 1:
        x = x[None, :]
    up = signal.resample_poly(x, oversample, 1, axis=-1)
    return float(np.max(np.abs(up)))


def _peak_envelope(sig, loop):
    """Per-sample true-peak estimate (4x oversampled, circular for loops)."""
    n = sig.shape[-1]
    if loop:
        pad = 64
        ext = np.concatenate([sig[..., -pad:], sig, sig[..., :pad]], axis=-1)
        up = signal.resample_poly(ext, 4, 1, axis=-1)
        peak = np.max(np.abs(up), axis=0) if up.ndim == 2 else np.abs(up)
        peak = maximum_filter1d(peak, size=5)[::4][pad:pad + n]
    else:
        up = signal.resample_poly(sig, 4, 1, axis=-1)
        peak = np.max(np.abs(up), axis=0) if up.ndim == 2 else np.abs(up)
        peak = maximum_filter1d(peak, size=5)[::4][:n]
    return peak


def limiter(x, ceiling_db=-1.0, lookahead=0.015, hold=0.06, loop=True, sr=SR):
    """True-peak limiter, offline and symmetric: the gain at every sample is the mean of window minima that all
    include that sample's own requirement, so no peak exceeds the ceiling. Reduction ramps in over `lookahead`,
    holds for `hold` and ramps out; with wrap-around windows a loop is limited exactly as its steady state."""
    ceiling = db_to_gain(ceiling_db)
    peak = _peak_envelope(x, loop)
    red = gain_to_db(np.minimum(1.0, ceiling / np.maximum(peak, 1e-9)))
    la = max(1, int(lookahead * sr))
    rh = max(1, int(hold * sr))
    size = rh + la + 1
    mode = "wrap" if loop else "nearest"
    # window [n - rh, n + la]
    origin = rh - size // 2
    m = minimum_filter1d(red, size=size, origin=origin, mode=mode)
    w = 2 * la + 1
    g = uniform_filter1d(m, size=w, mode=mode)
    y = x * db_to_gain(np.minimum(g, 0.0))
    tp = true_peak(y)
    if tp > ceiling:
        y *= ceiling / tp
    return y


def soft_clip(x, drive=1.0):
    return np.tanh(x * drive) / np.tanh(drive)


# --------------------------------------------------------------------------------------------- loudness

def _k_weighting(sr=SR):
    """BS.1770 K-weighting at any sample rate (libebur128 bilinear design; matches the 48 kHz reference)."""
    f0, g, q = 1681.974450955533, 3.999843853973347, 0.7071752369554196
    k = np.tan(np.pi * f0 / sr)
    vh = 10.0 ** (g / 20.0)
    vb = vh ** 0.4996667741545416
    a0 = 1.0 + k / q + k * k
    b1 = np.array([(vh + vb * k / q + k * k) / a0, 2.0 * (k * k - vh) / a0, (vh - vb * k / q + k * k) / a0])
    a1 = np.array([1.0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0])
    f0, q = 38.13547087602444, 0.5003270373238773
    k = np.tan(np.pi * f0 / sr)
    d = 1.0 + k / q + k * k
    b2 = np.array([1.0, -2.0, 1.0])
    a2 = np.array([1.0, 2.0 * (k * k - 1.0) / d, (1.0 - k / q + k * k) / d])
    return (b1, a1), (b2, a2)


def loudness(x, sr=SR):
    """Integrated loudness (ITU-R BS.1770-4, LUFS) of a mono or stereo signal."""
    if x.ndim == 1:
        x = x[None, :]
    (b1, a1), (b2, a2) = _k_weighting(sr)
    y = signal.lfilter(b2, a2, signal.lfilter(b1, a1, x, axis=-1), axis=-1)
    block = int(0.4 * sr)
    hop = int(0.1 * sr)
    n = y.shape[-1]
    if n < block:
        z = np.mean(y ** 2, axis=-1).sum()
        return -0.691 + 10 * np.log10(z + 1e-15)
    starts = np.arange(0, n - block + 1, hop)
    sq = np.cumsum(np.pad(y ** 2, ((0, 0), (1, 0))), axis=-1)
    z = ((sq[:, starts + block] - sq[:, starts]) / block).sum(axis=0)
    lk = -0.691 + 10 * np.log10(z + 1e-15)
    gated = z[lk > -70]
    if gated.size == 0:
        return -70.0
    rel = -0.691 + 10 * np.log10(np.mean(gated)) - 10
    gated = z[(lk > -70) & (lk > rel)]
    return -0.691 + 10 * np.log10(np.mean(gated))


def short_term_max(x, sr=SR):
    """Maximum short-term (3 s) loudness."""
    if x.ndim == 1:
        x = x[None, :]
    (b1, a1), (b2, a2) = _k_weighting(sr)
    y = signal.lfilter(b2, a2, signal.lfilter(b1, a1, x, axis=-1), axis=-1)
    block = min(int(3 * sr), y.shape[-1])
    sq = np.cumsum(np.pad(y ** 2, ((0, 0), (1, 0))), axis=-1)
    starts = np.arange(0, y.shape[-1] - block + 1, int(0.1 * sr))
    z = ((sq[:, starts + block] - sq[:, starts]) / block).sum(axis=0)
    return float(np.max(-0.691 + 10 * np.log10(z + 1e-15)))


def master(x, target_lufs=-14.0, ceiling_db=-1.0, loop=True, sr=SR, iterations=3):
    """Normalises to the target loudness and limits true peaks to the ceiling (loop aware)."""
    y = circular(lambda s: highpass(s, 24.0, 0.6, sr), x, int(sr * 0.5), loop)
    for _ in range(iterations):
        lu = loudness(y, sr)
        y = y * db_to_gain(target_lufs - lu)
        y = limiter(y, ceiling_db, loop=loop, sr=sr)
    lu = loudness(y, sr)
    y = y * db_to_gain(target_lufs - lu)
    tp = true_peak(y)
    if tp > db_to_gain(ceiling_db):
        y *= db_to_gain(ceiling_db) / tp
    return y
