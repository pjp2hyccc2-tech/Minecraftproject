"""WAV/OGG I/O through ffmpeg (libvorbis). Muxing is bit-exact so the same audio gives the same file."""
import os
import subprocess
import tempfile

import numpy as np
from scipy.io import wavfile

from .dsp import SR


def write_wav(path, audio):
    data = audio.T if audio.ndim == 2 else audio
    wavfile.write(path, SR, np.ascontiguousarray(data, dtype=np.float32))


def encode_ogg(audio, out_path, quality):
    """Encodes float audio ((2, n) stereo or (n,) mono) to OGG Vorbis at the given VBR quality."""
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "in.wav")
        write_wav(wav, audio)
        tmp_out = os.path.join(tmp, "out.ogg")
        cmd = ["ffmpeg", "-nostdin", "-y", "-hide_banner", "-loglevel", "error", "-i", wav,
               "-map_metadata", "-1", "-c:a", "libvorbis", "-q:a", f"{quality:g}",
               "-fflags", "+bitexact", "-flags:a", "+bitexact", tmp_out]
        subprocess.run(cmd, check=True)
        with open(tmp_out, "rb") as f:
            data = f.read()
    with open(out_path, "wb") as f:
        f.write(data)
    return len(data)


def decode_ogg(path, channels):
    cmd = ["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-i", path, "-f", "f32le",
           "-ac", str(channels), "-ar", str(SR), "-"]
    raw = subprocess.run(cmd, check=True, capture_output=True).stdout
    data = np.frombuffer(raw, dtype=np.float32).astype(np.float64)
    return data.reshape(-1, channels).T if channels > 1 else data
