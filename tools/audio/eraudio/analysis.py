"""Objective checks that stand in for listening: levels, clipping, loop seams, spectra, plus spectrogram plots."""
import os

import numpy as np
from scipy import signal

from . import dsp
from .dsp import SR


def seam_report(x):
    """How the loop point compares with the rest of the piece (values near or below 1 mean 'nothing special').

    jump: |x[0] - x[-1]| relative to the 99.9th percentile of all sample-to-sample steps.
    hf: high-frequency energy (above 5 kHz) in a 3 ms window across the seam relative to the 99th percentile of
        the same measure everywhere else (clicks show up as high-frequency bursts).
    """
    if x.ndim == 1:
        x = x[None, :]
    n = x.shape[-1]
    steps = np.abs(np.diff(x, axis=-1, append=x[:, :1]))
    jump = float(np.max(steps[:, -1]) / (np.percentile(steps, 99.9) + 1e-12))
    hp = dsp.circular(lambda s: dsp.butter(s, "highpass", 5000, 4), x, SR // 2, True)
    e = np.sum(hp ** 2, axis=0)
    w = int(0.003 * SR)
    c = np.cumsum(np.concatenate([e[-w:], e, e[:w]]))
    win = (c[2 * w:] - c[:-2 * w])[: n]          # window centred on each sample
    seam = float(win[0])
    ref = float(np.percentile(win, 99)) + 1e-12
    return {"jump": round(jump, 3), "hf": round(seam / ref, 3)}


def codec_edges(original, decoded):
    """Decoding error in the first/last 20 ms relative to the error over the whole file (the codec must not damage
    the loop point more than any other place)."""
    if original.ndim == 1:
        original = original[None, :]
        decoded = decoded[None, :]
    n = min(original.shape[-1], decoded.shape[-1])
    err = decoded[:, :n] - original[:, :n]
    w = int(0.02 * SR)
    overall = np.sqrt(np.mean(err ** 2)) + 1e-12
    start = np.sqrt(np.mean(err[:, :w] ** 2)) / overall
    end = np.sqrt(np.mean(err[:, -w:] ** 2)) / overall
    sig = np.sqrt(np.mean(original[:, :n] ** 2)) + 1e-12
    edge_sig = np.sqrt(np.mean(np.concatenate([original[:, :w], original[:, -w:]], axis=1) ** 2)) / sig
    return {"start": round(float(start), 2), "end": round(float(end), 2), "edge_level": round(float(edge_sig), 2),
            "snr_db": round(float(20 * np.log10(sig / overall)), 1)}


def octave_balance(x):
    mono = x.mean(axis=0) if x.ndim == 2 else x
    f, p = signal.welch(mono, SR, nperseg=min(8192, mono.size))
    bands = [(20, 60), (60, 250), (250, 1000), (1000, 4000), (4000, 10000), (10000, 20000)]
    tot = np.sum(p) + 1e-20
    return {f"{lo}-{hi}": round(float(10 * np.log10(np.sum(p[(f >= lo) & (f < hi)]) / tot + 1e-12)), 1)
            for lo, hi in bands}


def measure(x, loop):
    out = {
        "seconds": round(x.shape[-1] / SR, 2),
        "lufs": round(dsp.loudness(x), 2),
        "short_term_max": round(dsp.short_term_max(x), 2),
        "sample_peak_db": round(float(dsp.gain_to_db(np.max(np.abs(x)))), 2),
        "true_peak_db": round(float(dsp.gain_to_db(dsp.true_peak(x))), 2),
        "bands_db": octave_balance(x),
    }
    if x.ndim == 2:
        mid = 0.5 * (x[0] + x[1])
        side = 0.5 * (x[0] - x[1])
        out["side_to_mid_db"] = round(float(10 * np.log10(np.sum(side ** 2) / (np.sum(mid ** 2) + 1e-20) + 1e-12)), 1)
    if loop:
        out["seam"] = seam_report(x)
    return out


def plot(name, x, out_dir, loop):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    os.makedirs(out_dir, exist_ok=True)
    mono = x.mean(axis=0) if x.ndim == 2 else x
    fig, axes = plt.subplots(2, 1, figsize=(14, 7), gridspec_kw={"height_ratios": [3, 1]})
    nfft = 4096 if mono.size > SR * 5 else 1024
    f, t, s = signal.spectrogram(mono, SR, nperseg=nfft, noverlap=nfft // 2)
    s_db = 10 * np.log10(s + 1e-14)
    vmax = np.percentile(s_db, 99.5)
    axes[0].pcolormesh(t, f, s_db, vmin=vmax - 80, vmax=vmax, shading="auto", cmap="magma")
    axes[0].set_yscale("symlog", linthresh=200)
    axes[0].set_ylim(30, 20000)
    axes[0].set_ylabel("Hz")
    axes[0].set_title(f"{name}  ({'loop' if loop else 'one-shot'}, {x.shape[-1] / SR:.1f} s)")
    hop = max(1, int(0.05 * SR))
    frames = mono[: (mono.size // hop) * hop].reshape(-1, hop)
    rms = 20 * np.log10(np.sqrt(np.mean(frames ** 2, axis=1)) + 1e-9)
    pk = 20 * np.log10(np.max(np.abs(frames), axis=1) + 1e-9)
    tt = np.arange(rms.size) * hop / SR
    axes[1].plot(tt, pk, lw=0.6, label="peak")
    axes[1].plot(tt, rms, lw=0.8, label="rms")
    axes[1].set_ylim(-60, 1)
    axes[1].set_xlim(0, mono.size / SR)
    axes[1].legend(loc="lower right")
    axes[1].set_xlabel("s")
    fig.tight_layout()
    path = os.path.join(out_dir, name.replace("/", "_") + ".png")
    fig.savefig(path, dpi=70)
    plt.close(fig)
    return path
