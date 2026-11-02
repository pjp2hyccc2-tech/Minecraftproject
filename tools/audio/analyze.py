"""Verification helpers: decode OGG files, measure levels (sample / true peak,
RMS, BS.1770 loudness, crest), spectral centroid, roughness (30-150 Hz
envelope modulation), binaural carrier pairs, loop seams, and render
spectrogram PNGs.  The generator's author cannot listen, so everything is
checked analytically."""
import subprocess

import numpy as np
from scipy import signal as sg

import dsp

SR = 44100
FFMPEG = "/usr/local/bin/ffmpeg"


def decode(path, channels=None):
    """Decode any audio file to float64 array of shape (ch, n)."""
    probe = subprocess.run([FFMPEG, "-hide_banner", "-i", path], capture_output=True, text=True).stderr
    ch = channels
    if ch is None:
        ch = 2 if ("stereo" in probe) else 1
    raw = subprocess.run([FFMPEG, "-loglevel", "error", "-i", path, "-f", "f32le", "-ac", str(ch),
                          "-ar", str(SR), "pipe:1"], capture_output=True, check=True).stdout
    x = np.frombuffer(raw, np.float32).astype(np.float64)
    return x.reshape(-1, ch).T


def centroid(x):
    m = np.atleast_2d(x).mean(axis=0)
    f, P = sg.welch(m, SR, nperseg=4096)
    return float(np.sum(f * P) / (np.sum(P) + 1e-20))


def roughness(x, lo=300.0, hi=8000.0):
    """RMS amplitude-modulation depth in the 30-150 Hz 'roughness' band
    (Arnal et al. 2015: the band that makes screams alarming), measured on the
    loudest 1.5 s:  sqrt(P_env[30..150 Hz]) / mean(env).
    Calibration: Gaussian noise ~0.10, steady tones ~0.0, a 70 Hz AM of depth
    0.5 ~0.3, screams with subharmonics / chaos 0.25-0.5."""
    m = np.atleast_2d(x).mean(axis=0)
    b = dsp.bandpass(m, lo, hi, 2)
    env = np.abs(sg.hilbert(b))
    w = min(len(env), int(1.5 * SR))
    if len(env) > w:
        e2 = np.convolve(env ** 2, np.ones(4410) / 4410, mode="same")
        c = int(np.argmax(e2))
        a = max(0, min(len(env) - w, c - w // 2))
        env = env[a:a + w]
    mu = env.mean() + 1e-20
    e0 = env - mu
    f, P = sg.periodogram(e0 * np.hanning(len(e0)), SR)
    P = P / (np.mean(np.hanning(len(e0)) ** 2))
    df = f[1] - f[0]
    band = np.sum(P[(f >= 30) & (f <= 150)]) * df
    return float(np.sqrt(band) / mu)


def binaural_pairs(x, fmax=1200.0, min_db=-60.0, prom_db=15.0):
    """Detect binaural carrier pairs in a stereo signal: a *tonal* peak
    (>= prom_db above the local spectral median) in one ear that is >= 15 dB
    weaker in the other ear, whose tonal partner in the other ear lies 2-10 Hz
    away.  Returns [(f_left, f_right, delta_hz, level_db_rel_max)]."""
    x = np.atleast_2d(x)
    if x.shape[0] < 2:
        return []
    n = x.shape[1]
    win = np.hanning(n)
    XL = np.abs(np.fft.rfft(x[0] * win))
    XR = np.abs(np.fft.rfft(x[1] * win))
    f = np.fft.rfftfreq(n, 1.0 / SR)
    df = f[1] - f[0]
    k = int(fmax / df)
    from scipy.ndimage import median_filter
    w = max(5, int(30.0 / df) | 1)
    medL = median_filter(XL[:k], size=w) + 1e-20
    medR = median_filter(XR[:k], size=w) + 1e-20
    ref = max(XL[:k].max(), XR[:k].max())
    out = []
    for A, B, mA, mB, side in ((XL, XR, medL, medR, "L"), (XR, XL, medR, medL, "R")):
        pk, _ = sg.find_peaks(A[:k], height=ref * dsp.db(min_db), distance=max(1, int(1.0 / df)))
        for p in pk:
            if A[p] < mA[p] * dsp.db(prom_db) or B[p] > A[p] * dsp.db(-15):
                continue
            lo, hi = max(0, int(p - 10 / df)), min(k, int(p + 10 / df))
            q = lo + int(np.argmax(B[lo:hi]))
            d = abs(f[q] - f[p])
            if 2.0 <= d <= 10.0 and B[q] > mB[q] * dsp.db(prom_db) and A[q] < B[q] * dsp.db(-15):
                fl, fr = (f[p], f[q]) if side == "L" else (f[q], f[p])
                item = (round(float(fl), 2), round(float(fr), 2), round(float(abs(fr - fl)), 2),
                        round(float(dsp.to_db(max(A[p], B[q]) / ref)), 1))
                if all(abs(item[0] - o[0]) > 0.5 or abs(item[1] - o[1]) > 0.5 for o in out):
                    out.append(item)
    return sorted(out, key=lambda t: t[3], reverse=True)


def stats(x):
    x = np.atleast_2d(x)
    pk = np.max(np.abs(x))
    rms = np.sqrt(np.mean(x ** 2))
    return {
        "dur": x.shape[1] / SR,
        "ch": x.shape[0],
        "peak_db": 20 * np.log10(max(pk, 1e-12)),
        "rms_db": 20 * np.log10(max(rms, 1e-12)),
        "dc": float(np.max(np.abs(x.mean(axis=1)))),
    }


def full_stats(x):
    s = stats(x)
    s["tp_db"] = dsp.true_peak_db(x)
    s["lufs"] = dsp.loudness(x)
    s["mmax"] = dsp.momentary_max(x)
    s["crest"] = s["peak_db"] - s["rms_db"]
    s["centroid"] = centroid(x)
    s["rough"] = roughness(x)
    return s


def seam_score(x, w=64):
    """(jump_ratio, hf_ratio) of the wrap-around point (1 = like anywhere else)."""
    x = np.atleast_2d(x)
    n = x.shape[1]
    d = np.abs(np.diff(x, axis=1))
    med = np.median(d, axis=1) + 1e-9
    jump = np.max(np.abs(x[:, 0] - x[:, -1]) / med)

    def hf_energy(seg):
        return np.sum(np.diff(seg, n=2, axis=1) ** 2)

    rng = np.random.default_rng(0)
    ref = [hf_energy(x[:, p - w: p + w]) for p in rng.integers(w, n - w, 64)]
    seam = hf_energy(np.concatenate([x[:, -w:], x[:, :w]], axis=1))
    return float(jump), float(seam / (np.median(ref) + 1e-12))


def seam_rank(x, w=64, positions=400):
    """Percentile rank (0-100) of the click energy around the loop seam among
    the same measure at evenly spaced interior positions, and its ratio to the
    median."""
    x = np.atleast_2d(x)
    n = x.shape[1]

    def hf(seg):
        return float(np.sum(np.diff(seg, n=2, axis=1) ** 2))

    seam = hf(np.concatenate([x[:, -w:], x[:, :w]], axis=1))
    ref = np.array([hf(x[:, p - w:p + w]) for p in np.linspace(w, n - w, positions).astype(int)])
    return 100.0 * np.mean(ref < seam), seam / (np.median(ref) + 1e-12)


def seam_local(x, w=32, k=16):
    """Click detector for a loop seam, relative to its own surroundings:
    (hf_ratio, step_ratio).  hf_ratio = 2nd-difference energy of the 2w
    samples straddling the wrap point / median of the same measure over the
    k windows just before the end and k just after the start; step_ratio =
    |x[0] - x[-1]| / median sample step in those regions.  ~1 = the seam is as
    smooth as the material around it; a click shows as >> 3."""
    x = np.atleast_2d(x)
    n = x.shape[1]
    y = np.concatenate([x[:, n - (k + 1) * 2 * w:], x[:, :(k + 1) * 2 * w]], axis=1)
    c = y.shape[1] // 2

    def hf(seg):
        return float(np.sum(np.diff(seg, n=2, axis=1) ** 2))

    seam = hf(y[:, c - w:c + w])
    ref = [hf(y[:, c - w + j * 2 * w:c + w + j * 2 * w]) for j in list(range(-k, 0)) + list(range(1, k + 1))]
    steps = np.abs(np.diff(y, axis=1))
    med = np.median(np.concatenate([steps[:, :c - 2], steps[:, c + 1:]], axis=1), axis=1) + 1e-12
    step = float(np.max(np.abs(x[:, 0] - x[:, -1]) / med))
    return seam / (np.median(ref) + 1e-20), step


def spectrogram_png(x, path, title="", fmax=12000, log=False, nfft=2048, hop=256, dyn=80, tmax=None):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    x = np.atleast_2d(x)
    if tmax:
        x = x[:, : int(tmax * SR)]
    m = x.mean(axis=0)
    f, t, S = sg.stft(m, fs=SR, nperseg=nfft, noverlap=nfft - hop, window="hann")
    P = 20 * np.log10(np.abs(S) + 1e-9)
    P -= P.max()
    fig, axes = plt.subplots(2, 1, figsize=(14, 7), gridspec_kw={"height_ratios": [1, 3]}, sharex=True)
    tt = np.arange(x.shape[1]) / SR
    for c in range(x.shape[0]):
        axes[0].plot(tt, x[c], lw=0.4, alpha=0.8)
    axes[0].set_ylim(-1, 1)
    axes[0].set_title(title)
    k = f <= fmax
    axes[1].pcolormesh(t, f[k], P[k], vmin=-dyn, vmax=0, cmap="magma", shading="auto")
    if log:
        axes[1].set_yscale("symlog", linthresh=200)
        axes[1].set_ylim(20, fmax)
    axes[1].set_ylabel("Hz")
    axes[1].set_xlabel("s")
    fig.tight_layout()
    fig.savefig(path, dpi=70)
    plt.close(fig)
