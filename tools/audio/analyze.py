"""Verification helpers: decode OGG files, measure levels, check loop seams and
render spectrogram PNGs (the generator's author cannot listen, so everything is
checked analytically)."""
import subprocess

import numpy as np

SR = 44100
FFMPEG = "/usr/local/bin/ffmpeg"


def decode(path, channels=None):
    """Decode any audio file to float32 array of shape (ch, n)."""
    probe = subprocess.run([FFMPEG, "-hide_banner", "-i", path], capture_output=True, text=True).stderr
    ch = channels
    if ch is None:
        ch = 2 if ("stereo" in probe) else 1
    raw = subprocess.run([FFMPEG, "-loglevel", "error", "-i", path, "-f", "f32le", "-ac", str(ch),
                          "-ar", str(SR), "pipe:1"], capture_output=True, check=True).stdout
    x = np.frombuffer(raw, np.float32).astype(np.float64)
    return x.reshape(-1, ch).T


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


def seam_score(x, w=64):
    """How discontinuous is the wrap-around point compared with the rest?
    Returns (jump_ratio, hf_ratio):
      jump_ratio = |x[0]-x[-1]| / median(|diff|) (per channel max)  ~1 is perfect
      hf_ratio   = high-pass energy in a window around the seam relative to the
                   median of the same measure at 64 random positions."""
    x = np.atleast_2d(x)
    n = x.shape[1]
    d = np.abs(np.diff(x, axis=1))
    med = np.median(d, axis=1) + 1e-9
    jump = np.max(np.abs(x[:, 0] - x[:, -1]) / med)

    def hf_energy(seg):
        hp = np.diff(seg, n=2, axis=1)
        return np.sum(hp ** 2)

    ref = []
    rng = np.random.default_rng(0)
    for p in rng.integers(w, n - w, 64):
        ref.append(hf_energy(x[:, p - w : p + w]))
    seam = hf_energy(np.concatenate([x[:, -w:], x[:, :w]], axis=1))
    return float(jump), float(seam / (np.median(ref) + 1e-12))


def spectrogram_png(x, path, title="", fmax=12000, log=False, nfft=2048, hop=256, dyn=80):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from scipy import signal as sg

    x = np.atleast_2d(x)
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
        axes[1].set_ylim(30, fmax)
    axes[1].set_ylabel("Hz")
    axes[1].set_xlabel("s")
    fig.tight_layout()
    fig.savefig(path, dpi=70)
    plt.close(fig)
