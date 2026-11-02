"""Core DSP helpers for the MEIKON sound generator (numpy + scipy only).

Conventions
-----------
* Sample rate is fixed at 44.1 kHz.
* Mono signals are 1-D float arrays, stereo signals have shape (2, n).
* Every random decision goes through an explicit ``np.random.Generator`` so the
  whole sound set is reproducible bit-for-bit from fixed seeds.
* Loops are built *circularly*: FFT noise is periodic by construction, events
  are placed modulo the loop length, reverb is a circular convolution and IIR
  filters are run with a wrapped pre-roll (``circ``).  That makes loop seams
  sample-continuous without any crossfade.
"""
import zlib

import numpy as np
from scipy import signal
from scipy.ndimage import minimum_filter1d, uniform_filter1d

SR = 44100
TAU = 2.0 * np.pi


# --------------------------------------------------------------------------
# basics
# --------------------------------------------------------------------------
def ns(sec):
    """Seconds -> samples."""
    return int(round(sec * SR))


def tv(n):
    """Time vector (seconds) of n samples."""
    return np.arange(n) / SR


def rng_for(name, salt=0):
    """Deterministic generator from a string (crc32, not Python's salted hash)."""
    return np.random.default_rng((zlib.crc32(name.encode("utf-8")) * 1000003 + salt) & 0xFFFFFFFF)


def db(x):
    return 10.0 ** (x / 20.0)


def to_db(x):
    return 20.0 * np.log10(np.maximum(x, 1e-12))


def nchan(x):
    return 1 if x.ndim == 1 else x.shape[0]


def length(x):
    return x.shape[-1]


def stereo(x):
    return np.vstack([x, x]) if x.ndim == 1 else x


def mono(x):
    return x if x.ndim == 1 else x.mean(axis=0)


def pad_to(x, n):
    if length(x) >= n:
        return x[..., :n]
    pad = [(0, 0)] * (x.ndim - 1) + [(0, n - length(x))]
    return np.pad(x, pad)


def mix(*tracks):
    """Sum signals of different lengths / channel counts (mono is promoted)."""
    tracks = [t for t in tracks if t is not None]
    n = max(length(t) for t in tracks)
    st = any(t.ndim == 2 for t in tracks)
    out = np.zeros((2, n)) if st else np.zeros(n)
    for t in tracks:
        if st and t.ndim == 1:
            out[:, : length(t)] += t
        else:
            out[..., : length(t)] += t
    return out


def place(buf, x, t, gain=1.0, circular=False):
    """Add x into buf at time t (seconds). Supports mono->stereo promotion."""
    i0 = ns(t) if not isinstance(t, (int, np.integer)) else int(t)
    n = length(buf)
    if buf.ndim == 2 and x.ndim == 1:
        x = np.vstack([x, x])
    if circular:
        m = length(x)
        idx = (i0 + np.arange(m)) % n
        if buf.ndim == 1:
            np.add.at(buf, idx, x * gain)
        else:
            for c in range(buf.shape[0]):
                np.add.at(buf[c], idx, x[c] * gain)
        return buf
    if i0 >= n:
        return buf
    a = max(0, i0)
    b = min(n, i0 + length(x))
    if b <= a:
        return buf
    buf[..., a:b] += x[..., a - i0 : b - i0] * gain
    return buf


# --------------------------------------------------------------------------
# pitch
# --------------------------------------------------------------------------
_NOTE = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def midi(name):
    letter, rest = name[0].upper(), name[1:]
    acc = 0
    while rest and rest[0] in "#b":
        acc += 1 if rest[0] == "#" else -1
        rest = rest[1:]
    return 12 * (int(rest) + 1) + _NOTE[letter] + acc


def hz(name, cents=0.0):
    if isinstance(name, (int, float, np.floating)):
        return float(name) * 2 ** (cents / 1200)
    return 440.0 * 2 ** ((midi(name) - 69) / 12 + cents / 1200)


def cents(c):
    return 2.0 ** (np.asarray(c) / 1200.0)


# --------------------------------------------------------------------------
# envelopes
# --------------------------------------------------------------------------
def env_pts(n, pts, shape="lin"):
    """Piecewise envelope. pts = [(t_sec, value), ...]. shape 'lin' or 'db'
    (interpolate in dB; values are linear, 0 handled as -100 dB)."""
    ts = np.array([p[0] for p in pts], float) * SR
    vs = np.array([p[1] for p in pts], float)
    x = np.arange(n)
    if shape == "db":
        vd = to_db(np.maximum(vs, 1e-5))
        e = db(np.interp(x, ts, vd))
        e[e < 1.1e-5] = 0.0
        return e
    return np.interp(x, ts, vs)


def _tail(e, sec=0.012):
    """Force an envelope to reach zero at its end (no click when truncated)."""
    k = min(len(e) // 4, ns(sec))
    if k > 1:
        e[-k:] *= np.cos(np.linspace(0, np.pi / 2, k)) ** 2
    return e


def env_exp(n, tau, delay=0):
    t = np.arange(n) / SR
    e = np.exp(-np.maximum(t - delay, 0) / tau)
    return _tail(e)


def env_ad(n, attack, decay_tau, curve=1.0):
    """Attack (raised-cosine) then exponential decay."""
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-5), 0, 1)
    a = (0.5 - 0.5 * np.cos(np.pi * a)) ** curve
    d = np.exp(-np.maximum(t - attack, 0) / decay_tau)
    return _tail(a * d)


def env_adsr(n, a, d, s, r, hold=None):
    """Classic ADSR; release starts at n - r (or after hold seconds)."""
    t = np.arange(n) / SR
    rel_start = (n / SR - r) if hold is None else (a + d + hold)
    e = np.where(t < a, t / max(a, 1e-6), np.where(t < a + d, 1 - (1 - s) * (t - a) / max(d, 1e-6), s))
    rel = np.clip((t - rel_start) / max(r, 1e-6), 0, 1)
    e = e * (1 - rel)
    e[t >= rel_start + r] = 0
    return np.maximum(e, 0)


def raised_cos(n):
    return 0.5 - 0.5 * np.cos(np.pi * np.linspace(0, 1, n))


def fade(x, fin=0.003, fout=0.01):
    x = x.copy()
    a, b = ns(fin), ns(fout)
    n = length(x)
    if a > 0:
        a = min(a, n)
        x[..., :a] *= raised_cos(a)
    if b > 0:
        b = min(b, n)
        x[..., n - b :] *= raised_cos(b)[::-1]
    return x


def trim(x, thresh_db=-72.0, fout=0.05, min_len=0.0):
    """Cut the inaudible tail of a one-shot and fade the last bit."""
    a = np.abs(mono(x)) if x.ndim == 2 else np.abs(x)
    # smooth to avoid cutting inside a waveform cycle
    sm = uniform_filter1d(a, 441)
    idx = np.nonzero(sm > db(thresh_db) * max(a.max(), 1e-9))[0]
    end = idx[-1] + ns(fout) if len(idx) else length(x)
    end = max(end, ns(min_len))
    end = min(end, length(x))
    return fade(x[..., :end], 0.0, fout)


# --------------------------------------------------------------------------
# noise & control signals
# --------------------------------------------------------------------------
def white(n, rng):
    return rng.standard_normal(n)


def colored(n, rng, slope_db_oct=-3.0, lo=None, hi=None):
    """FFT-shaped noise (periodic with period n), unit std.
    slope 0 = white, -3 = pink, -6 = brown.  lo/hi = soft band limits (Hz)."""
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1.0 / SR)
    f[0] = f[1] if n > 1 else 1.0
    g = (f / 1000.0) ** (slope_db_oct / 6.0206)
    if lo is not None:
        g *= 1.0 / np.sqrt(1.0 + (lo / f) ** 4)
    if hi is not None:
        g *= 1.0 / np.sqrt(1.0 + (f / hi) ** 4)
    g[0] = 0.0
    y = np.fft.irfft(spec * g, n)
    s = np.std(y)
    return y / s if s > 0 else y


def pink(n, rng):
    return colored(n, rng, -3.0)


def brown(n, rng, lo=20.0):
    return colored(n, rng, -6.0, lo=lo)


def smooth(n, rng, rate, periodic=True):
    """Band-limited random control signal (unit std, zero mean).
    rate = highest frequency in Hz.  Periodic with period n."""
    m = n if periodic else int(n * 1.5) + 16
    f = np.fft.rfftfreq(m, 1.0 / SR)
    spec = np.zeros(len(f), complex)
    k = np.nonzero((f > 0) & (f <= rate))[0]
    if len(k) == 0:
        k = np.array([1])
    # 1/f-ish weighting gives more natural wandering
    w = 1.0 / np.sqrt(np.maximum(f[k], f[k[0]]))
    spec[k] = (rng.standard_normal(len(k)) + 1j * rng.standard_normal(len(k))) * w
    y = np.fft.irfft(spec, m)[:n]
    s = np.std(y)
    return y / s if s > 0 else y


def nb_noise(freq, bw, rng, periodic_fix=False):
    """Narrow-band noise centred on a (time-varying) frequency trajectory:
    complex low-pass noise heterodyned up.  bw = full bandwidth in Hz."""
    freq = np.asarray(freq, float)
    n = len(freq)
    z = rng.standard_normal(n) + 1j * rng.standard_normal(n)
    Z = np.fft.fft(z)
    f = np.fft.fftfreq(n, 1.0 / SR)
    Z *= 1.0 / (1.0 + (np.abs(f) / (bw / 2.0)) ** 4)
    z = np.fft.ifft(Z)
    ph = np.cumsum(freq) / SR
    if periodic_fix:
        ph = periodic_phase(freq)
    y = np.real(z * np.exp(1j * TAU * ph))
    s = np.std(y)
    return y / s if s > 0 else y


def periodic_phase(freq):
    """Phase (in cycles) of a frequency trajectory, nudged so that it completes
    an integer number of cycles over the buffer (for seamless loops)."""
    freq = np.asarray(freq, float)
    n = len(freq)
    tot = freq.sum() / SR
    k = max(1, round(tot))
    return np.cumsum(freq * (k / tot)) / SR


def loop_freq(f, loop_len_s):
    """Quantise a frequency so it fits an integer number of cycles in a loop."""
    return max(1, round(f * loop_len_s)) / loop_len_s


# --------------------------------------------------------------------------
# oscillators
# --------------------------------------------------------------------------
def phase_of(freq, n=None, phase0=0.0):
    if np.isscalar(freq):
        return phase0 + freq * np.arange(n) / SR
    return phase0 + np.cumsum(freq) / SR


def sine(freq, n=None, phase0=0.0):
    return np.sin(TAU * phase_of(freq, n, phase0))


def saw(freq, n=None, phase0=0.0, loop=False):
    """PolyBLEP band-limited sawtooth, freq scalar or per-sample array.
    loop=True nudges the frequency so the buffer holds whole cycles."""
    if np.isscalar(freq):
        freq = np.full(n, float(freq))
    dt = np.abs(freq) / SR
    if loop:
        tot = dt.sum()
        dt = dt * (max(1, round(tot)) / tot)
    ph = (phase0 + np.cumsum(dt)) % 1.0
    y = 2.0 * ph - 1.0
    m = ph < dt
    t = ph[m] / dt[m]
    y[m] -= t + t - t * t - 1.0
    m = ph > 1.0 - dt
    t = (ph[m] - 1.0) / dt[m]
    y[m] -= t * t + t + t + 1.0
    return y


def pulse(freq, width=0.5, n=None, phase0=0.0, loop=False):
    """PolyBLEP pulse wave (difference of two saws)."""
    a = saw(freq, n, phase0, loop)
    b = saw(freq, n, phase0 + width, loop)
    return 0.5 * (a - b)


def sine_loop(freq, n, phase0=0.0):
    """Sine whose (possibly time-varying) frequency is nudged to whole cycles."""
    if np.isscalar(freq):
        freq = np.full(n, float(freq))
    return np.sin(TAU * (phase0 + periodic_phase(freq)))


def additive(f0, partials, amps, n=None, phases=None, decays=None):
    """Sum of sinusoids at f0*ratio (inharmonic allowed)."""
    f0 = np.full(n, f0) if np.isscalar(f0) else np.asarray(f0)
    ph = np.cumsum(f0) / SR
    out = np.zeros(len(f0))
    t = np.arange(len(f0)) / SR
    for i, (r, a) in enumerate(zip(partials, amps)):
        p0 = 0.0 if phases is None else phases[i]
        comp = a * np.sin(TAU * (r * ph + p0))
        if decays is not None:
            comp *= np.exp(-t / decays[i])
        out += comp
    return out


# --------------------------------------------------------------------------
# filters
# --------------------------------------------------------------------------
def _butter(kind, f, order):
    nyq = SR / 2.0
    if np.isscalar(f):
        f = min(max(f, 1.0), nyq * 0.999)
    else:
        f = [min(max(v, 1.0), nyq * 0.999) for v in f]
    return signal.butter(order, f, btype=kind, fs=SR, output="sos")


def lowpass(x, f, order=2):
    return signal.sosfilt(_butter("lowpass", f, order), x, axis=-1)


def highpass(x, f, order=2):
    return signal.sosfilt(_butter("highpass", f, order), x, axis=-1)


def bandpass(x, lo, hi, order=2):
    return signal.sosfilt(_butter("bandpass", [lo, hi], order), x, axis=-1)


def lowpass0(x, f, order=2):
    """Zero-phase low-pass (for control signals)."""
    return signal.sosfiltfilt(_butter("lowpass", f, order), x, axis=-1)


def rbj(kind, f0, q=0.707, gain_db=0.0):
    """RBJ cookbook biquad coefficients (b, a)."""
    f0 = min(max(f0, 5.0), SR * 0.49)
    A = 10 ** (gain_db / 40.0)
    w = TAU * f0 / SR
    cw, sw = np.cos(w), np.sin(w)
    al = sw / (2 * q)
    if kind == "bp":  # constant 0 dB peak
        b = [al, 0, -al]
        a = [1 + al, -2 * cw, 1 - al]
    elif kind == "peak":
        b = [1 + al * A, -2 * cw, 1 - al * A]
        a = [1 + al / A, -2 * cw, 1 - al / A]
    elif kind == "notch":
        b = [1, -2 * cw, 1]
        a = [1 + al, -2 * cw, 1 - al]
    elif kind == "lowshelf":
        sa = 2 * np.sqrt(A) * al
        b = [A * ((A + 1) - (A - 1) * cw + sa), 2 * A * ((A - 1) - (A + 1) * cw), A * ((A + 1) - (A - 1) * cw - sa)]
        a = [(A + 1) + (A - 1) * cw + sa, -2 * ((A - 1) + (A + 1) * cw), (A + 1) + (A - 1) * cw - sa]
    elif kind == "highshelf":
        sa = 2 * np.sqrt(A) * al
        b = [A * ((A + 1) + (A - 1) * cw + sa), -2 * A * ((A - 1) + (A + 1) * cw), A * ((A + 1) + (A - 1) * cw - sa)]
        a = [(A + 1) - (A - 1) * cw + sa, 2 * ((A - 1) - (A + 1) * cw), (A + 1) - (A - 1) * cw - sa]
    elif kind == "lp":
        b = [(1 - cw) / 2, 1 - cw, (1 - cw) / 2]
        a = [1 + al, -2 * cw, 1 - al]
    elif kind == "hp":
        b = [(1 + cw) / 2, -(1 + cw), (1 + cw) / 2]
        a = [1 + al, -2 * cw, 1 - al]
    else:
        raise ValueError(kind)
    b = np.array(b) / a[0]
    a = np.array(a) / a[0]
    return b, a


def reson(x, f0, q, gain=1.0):
    b, a = rbj("bp", f0, q)
    return signal.lfilter(b, a, x, axis=-1) * gain


def peq(x, f0, q, gain_db):
    b, a = rbj("peak", f0, q, gain_db)
    return signal.lfilter(b, a, x, axis=-1)


def shelf(x, f0, gain_db, kind="low", q=0.707):
    b, a = rbj("lowshelf" if kind == "low" else "highshelf", f0, q, gain_db)
    return signal.lfilter(b, a, x, axis=-1)


def notch(x, f0, q):
    b, a = rbj("notch", f0, q)
    return signal.lfilter(b, a, x, axis=-1)


def resbank(x, modes):
    """Parallel resonator bank: modes = [(freq, q, gain), ...]."""
    out = np.zeros_like(x, dtype=float)
    for f, q, g in modes:
        if f < SR * 0.48:
            out += reson(x, f, q, g)
    return out


def modal_ir(modes, dur, rng=None):
    """Impulse response of damped sinusoidal modes [(freq, t60, amp), ...]."""
    n = ns(dur)
    t = np.arange(n) / SR
    ir = np.zeros(n)
    for f, t60, a in modes:
        if f >= SR * 0.48:
            continue
        ph = rng.uniform(0, TAU) if rng is not None else 0.0
        ir += a * np.exp(-6.9078 * t / t60) * np.sin(TAU * f * t + ph)
    return _tail(ir, 0.02)


def tv_filter(x, fc, q, kind="bp", block=64):
    """Time-varying biquad (block-wise coefficient update, TDF-II state carried).
    fc: per-sample cutoff trajectory (Hz); q scalar or trajectory."""
    n = len(x)
    out = np.zeros(n)
    zi = np.zeros(2)
    fc = np.broadcast_to(np.asarray(fc, float), (n,))
    qv = np.broadcast_to(np.asarray(q, float), (n,))
    for i in range(0, n, block):
        j = min(n, i + block)
        b, a = rbj(kind, float(fc[i]), float(qv[i]))
        out[i:j], zi = signal.lfilter(b, a, x[i:j], zi=zi)
    return out


def svf_bp(x, fc, q, block=32):
    """Alias kept for readability."""
    return tv_filter(x, fc, q, "bp", block)


def circ(fn, x, pre=None):
    """Run a causal filter on a periodic signal so the output stays periodic:
    wrap the end of the buffer in front as pre-roll and drop it afterwards."""
    n = length(x)
    pre = min(n, pre if pre is not None else ns(2.0))
    xx = np.concatenate([x[..., n - pre :], x], axis=-1)
    return fn(xx)[..., pre:]


def dc_block(x, f=18.0, circular=False):
    fn = lambda s: highpass(s, f, 2)
    y = circ(fn, x) if circular else fn(x)
    return y - y.mean(axis=-1, keepdims=True)


# --------------------------------------------------------------------------
# spectral shaping (time-varying zero-phase filtering in the STFT domain)
# --------------------------------------------------------------------------
def spectral_shape(x, env_fn, nfft=1024, hop=256):
    """env_fn(times_sec[frames], freqs_hz[bins]) -> gain matrix (bins, frames)."""
    n = len(x)
    f, t, Z = signal.stft(x, fs=SR, window="hann", nperseg=nfft, noverlap=nfft - hop, boundary="zeros", padded=True)
    G = env_fn(t, f)
    _, y = signal.istft(Z * G, fs=SR, window="hann", nperseg=nfft, noverlap=nfft - hop, boundary=True)
    y = y[:n]
    if len(y) < n:
        y = np.pad(y, (0, n - len(y)))
    return y


# --------------------------------------------------------------------------
# time / pitch manipulation
# --------------------------------------------------------------------------
def varispeed(x, rate, n_out=None):
    """Read x with a time-varying playback rate (pitch+time change).
    rate may be scalar or an array (per output sample)."""
    if np.isscalar(rate):
        n_out = n_out or int(length(x) / rate)
        pos = np.arange(n_out) * rate
    else:
        pos = np.concatenate([[0.0], np.cumsum(rate)[:-1]])
        if n_out is not None:
            pos = pos[:n_out]
    pos = pos[pos < length(x) - 1]
    if x.ndim == 1:
        return np.interp(pos, np.arange(length(x)), x)
    return np.vstack([np.interp(pos, np.arange(length(x)), c) for c in x])


def reverse(x):
    return x[..., ::-1].copy()


def place_end(buf, x, t_end, gain=1.0):
    """Place x so that it *ends* at t_end (crop its head if it is too long).
    Used for reversed swells that must peak exactly on a hit."""
    m = ns(t_end)
    L = length(x)
    if L > m:
        x = x[..., L - m:]
        start = 0
    else:
        start = m - L
    return place(buf, x, int(start), gain)


def delay(x, sec, fb=0.0, mix_=1.0):
    d = ns(sec)
    y = np.concatenate([np.zeros(x.shape[:-1] + (d,)), x], axis=-1)
    return y


# --------------------------------------------------------------------------
# reverb
# --------------------------------------------------------------------------
def make_ir(rt60=1.0, rng=None, dur=None, predelay=0.0, stereo=False, lp_hz=None, hp_hz=None,
            hf_ratio=0.45, lf_ratio=1.2, early=8, er_span=0.05, er_gain=0.6, onset=0.006):
    """Synthetic room impulse response.
    Multi-band exponentially decaying noise (different RT per band), smooth
    density build-up, a handful of early reflections and optional pre-delay.
    Each channel is normalised to unit energy (wet RMS ~= dry RMS for noise)."""
    rng = rng if rng is not None else np.random.default_rng(1)
    dur = dur if dur is not None else min(rt60 * 1.25 + predelay + 0.05, 9.0)
    n = ns(dur)
    t = np.arange(n) / SR
    chans = 2 if stereo else 1
    out = np.zeros((chans, n))
    pd = ns(predelay)
    bands = [(None, 250.0, rt60 * lf_ratio), (250.0, 1200.0, rt60),
             (1200.0, 4500.0, rt60 * (0.5 + 0.5 * hf_ratio)), (4500.0, None, rt60 * hf_ratio)]
    for c in range(chans):
        w = rng.standard_normal(n)
        acc = np.zeros(n)
        for lo, hi, rt in bands:
            if lo is None:
                b = lowpass(w, hi, 4)
            elif hi is None:
                b = highpass(w, lo, 4)
            else:
                b = bandpass(w, lo, hi, 3)
            acc += b * np.exp(-6.9078 * t / max(rt, 0.02))
        acc *= 1.0 - np.exp(-t / max(onset, 1e-4))
        acc = np.concatenate([np.zeros(pd), acc])[:n]
        # early reflections
        er = np.zeros(n)
        for k in range(early):
            tk = predelay * 0.5 + rng.uniform(0.002, er_span) * (0.4 + 0.6 * k / max(early, 1))
            ik = ns(tk)
            if ik < n:
                er[ik] += rng.choice([-1.0, 1.0]) * rng.uniform(0.4, 1.0) * (0.75 ** k)
        er = lowpass(er, 6000.0, 1)
        acc = acc / np.sqrt(np.sum(acc ** 2) + 1e-12)
        e_er = np.sqrt(np.sum(er ** 2) + 1e-12)
        acc = acc + er / e_er * er_gain * 0.5
        if lp_hz:
            acc = lowpass(acc, lp_hz, 2)
        if hp_hz:
            acc = highpass(acc, hp_hz, 2)
        acc /= np.sqrt(np.sum(acc ** 2) + 1e-12)
        out[c] = acc
    return out if stereo else out[0]


def convolve(x, ir, tail=True):
    """x mono/stereo, ir mono/stereo -> convolved (keeps tail if requested)."""
    n = length(x)
    m = length(ir)
    nout = n + m - 1 if tail else n
    if x.ndim == 1 and ir.ndim == 1:
        y = signal.fftconvolve(x, ir)
    elif x.ndim == 1:
        y = np.vstack([signal.fftconvolve(x, ir[c]) for c in range(ir.shape[0])])
    elif ir.ndim == 1:
        y = np.vstack([signal.fftconvolve(x[c], ir) for c in range(x.shape[0])])
    else:
        y = np.vstack([signal.fftconvolve(x[c], ir[c]) for c in range(x.shape[0])])
    return y[..., :nout]


def reverb(x, ir, wet=0.3, dry=1.0, tail=True):
    y = convolve(x, ir, tail) * wet
    if x.ndim == 1 and y.ndim == 2:
        x = np.vstack([x, x])
    return mix(y, x * dry)


def circ_convolve(x, ir):
    """Circular convolution (period = len(x)); IR longer than x is folded."""
    n = length(x)
    if length(ir) > n:
        m = length(ir)
        k = int(np.ceil(m / n))
        ir = pad_to(ir, k * n)
        ir = ir.reshape(ir.shape[:-1] + (k, n)).sum(axis=-2)
    X = np.fft.rfft(x, n, axis=-1)
    H = np.fft.rfft(ir, n, axis=-1)
    if x.ndim == 1 and ir.ndim == 2:
        X = X[None, :]
    return np.fft.irfft(X * H, n, axis=-1)


def wrap(buf, n):
    """Wrap everything beyond n samples back onto the start (overlap-add).
    Render note events into a buffer longer than the loop, then fold: notes
    and tails that cross the loop point continue seamlessly at the start."""
    out = buf[..., :n].copy()
    k = n
    while k < length(buf):
        seg = buf[..., k:k + n]
        out[..., :length(seg)] += seg
        k += n
    return out


def circ_reverb(x, ir, wet=0.3, dry=1.0):
    y = circ_convolve(x, ir) * wet
    if x.ndim == 1 and y.ndim == 2:
        x = np.vstack([x, x])
    return y + x * dry


# --------------------------------------------------------------------------
# stereo
# --------------------------------------------------------------------------
def pan(x, p):
    """Equal-power pan of a mono signal, p in [-1, 1] (scalar or array)."""
    a = (np.asarray(p) + 1.0) * np.pi / 4.0
    return np.vstack([x * np.cos(a), x * np.sin(a)])


def widen(x, rng, amount=0.012):
    """Mono -> wide stereo using short decorrelating delays."""
    d1 = ns(rng.uniform(0.0, amount))
    d2 = ns(rng.uniform(0.0, amount))
    l = np.concatenate([np.zeros(d1), x])
    r = np.concatenate([np.zeros(d2), x])
    n = max(len(l), len(r))
    return np.vstack([pad_to(l, n), pad_to(r, n)])


# --------------------------------------------------------------------------
# dynamics & distortion
# --------------------------------------------------------------------------
def sat(x, drive=2.0):
    return np.tanh(drive * x) / np.tanh(drive)


def asym_sat(x, drive=2.0, bias=0.2):
    y = np.tanh(drive * (x + bias)) - np.tanh(drive * bias)
    return y / (np.max(np.abs(y)) + 1e-12) * np.max(np.abs(x))


def fold(x, amount=1.5):
    """Sine wavefolder."""
    return np.sin(np.pi / 2 * amount * x)


def crush(x, bits=8, hold=1):
    q = 2.0 ** (bits - 1)
    y = np.round(x * q) / q
    if hold > 1:
        n = length(y)
        idx = (np.arange(n) // hold) * hold
        y = y[..., idx]
    return y


def limiter(x, ceiling_db=-1.0, window=0.004):
    """Offline zero-latency brickwall limiter.  Gain = moving average of a
    windowed minimum of the required gain, which is provably <= the required
    gain everywhere, so the output never exceeds the ceiling (then a final
    hard safety clip)."""
    c = db(ceiling_db)
    a = np.abs(x) if x.ndim == 1 else np.max(np.abs(x), axis=0)
    g = np.minimum(1.0, c / np.maximum(a, 1e-12))
    w = max(3, ns(window) | 1)
    gm = minimum_filter1d(g, w, mode="nearest")
    gs = uniform_filter1d(gm, w, mode="nearest")
    y = x * gs
    return np.clip(y, -c, c)


def compress(x, thresh_db=-18.0, ratio=4.0, window=0.02, makeup=True):
    """Simple offline RMS compressor (zero-phase smoothing)."""
    m = x if x.ndim == 1 else np.sqrt(np.mean(x ** 2, axis=0))
    env = np.sqrt(uniform_filter1d(m ** 2, max(3, ns(window))) + 1e-12)
    lvl = to_db(env)
    over = np.maximum(lvl - thresh_db, 0)
    gdb = -over * (1 - 1.0 / ratio)
    g = db(gdb)
    y = x * g
    if makeup:
        y *= db(-np.mean(gdb) * 0.5)
    return y


def maximize(x, target_rms_db, ceiling_db=-0.5, pre_window=0.03):
    """Make a signal as dense as requested: level the envelope with a slow
    limiter, then drive a peak-normalised tanh saturator until the RMS hits
    the target.  RMS is monotonic in the drive, so a bisection converges."""
    c = db(ceiling_db)
    x = x / (peak(x) + 1e-12)
    lev = limiter(x * db(9.0), -9.0, pre_window)
    lev = lev / (peak(lev) + 1e-12)

    def proc(g):
        return c * np.tanh(g * lev) / np.tanh(g)

    lo, hi = np.log(0.05), np.log(200.0)
    for _ in range(30):
        mid = 0.5 * (lo + hi)
        if rms_db(proc(np.exp(mid))) < target_rms_db:
            lo = mid
        else:
            hi = mid
    return proc(np.exp(0.5 * (lo + hi)))


# --------------------------------------------------------------------------
# levels
# --------------------------------------------------------------------------
def peak(x):
    return float(np.max(np.abs(x)))


def peak_db(x):
    return float(to_db(peak(x)))


def rms_db(x):
    return float(to_db(np.sqrt(np.mean(np.asarray(x, float) ** 2))))


def norm_peak(x, target_db=-3.0):
    p = peak(x)
    return x * (db(target_db) / p) if p > 0 else x


def norm_rms(x, target_db=-20.0):
    r = np.sqrt(np.mean(x ** 2))
    return x * (db(target_db) / r) if r > 0 else x


def norm_rms_capped(x, target_rms_db, max_peak_db=-1.0):
    """Normalise to an RMS target but never above a peak ceiling (limits if needed)."""
    y = norm_rms(x, target_rms_db)
    if peak_db(y) > max_peak_db:
        y = limiter(y, max_peak_db, 0.01)
    return y
