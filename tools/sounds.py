"""Synthesized sound effects (all original), encoded to mono OGG Vorbis with ffmpeg."""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100


def env_adsr(n, a=0.01, d=0.1, s=0.6, r=0.2):
    t = np.arange(n) / SR
    total = n / SR
    e = np.ones(n) * s
    e[t < a] = t[t < a] / a
    m = (t >= a) & (t < a + d)
    e[m] = 1 - (1 - s) * (t[m] - a) / d
    rr = t > total - r
    e[rr] *= np.clip((total - t[rr]) / r, 0, 1)
    return e


def bell(freq, dur, amp=0.5, ratio=3.5, index=2.0, decay=2.5):
    n = int(SR * dur)
    t = np.arange(n) / SR
    mod = np.sin(2 * np.pi * freq * ratio * t) * index * np.exp(-t * decay * 1.5)
    s = np.sin(2 * np.pi * freq * t + mod) * np.exp(-t * decay)
    s += 0.3 * np.sin(2 * np.pi * freq * 2 * t) * np.exp(-t * decay * 2)
    return amp * s


def place(buf, sig, at):
    i = int(at * SR)
    j = min(len(buf), i + len(sig))
    buf[i:j] += sig[: j - i]


def lowpass(x, cutoff):
    # simple one-pole
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y


def lowpass_fast(x, cutoff):
    # FFT brickwall-ish low pass (fast for long signals)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X[f > cutoff] *= np.exp(-(f[f > cutoff] - cutoff) / (cutoff * 0.25))
    return np.fft.irfft(X, len(x))


def bandpass_fast(x, lo, hi):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X[(f < lo) | (f > hi)] = 0
    return np.fft.irfft(X, len(x))


def echo(x, delay=0.18, fb=0.35, n=4):
    y = x.copy()
    d = int(delay * SR)
    for k in range(1, n + 1):
        y[d * k:] += x[: len(x) - d * k] * (fb ** k)
    return y


def norm(x, peak=0.85):
    m = np.max(np.abs(x)) or 1
    return x / m * peak


def loop_fade(x, fade=0.25):
    """crossfade end into start so the sound loops seamlessly"""
    f = int(fade * SR)
    out = x[: len(x) - f].copy()
    ramp = np.linspace(0, 1, f)
    out[:f] = out[:f] * ramp + x[len(x) - f:] * (1 - ramp)
    return out


NOTE = {n: 440 * 2 ** ((i - 9) / 12) for i, n in enumerate(['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'])}


def hz(name, octave):
    return NOTE[name] * 2 ** (octave - 4)


def s_melody():
    # an original, bright station departure jingle (~7 s)
    seq = [('E', 5), ('G', 5), ('C', 6), ('B', 5), ('G', 5), ('A', 5), ('B', 5), ('D', 6), ('C', 6), ('G', 5), ('E', 5), ('G', 5),
           ('F', 5), ('A', 5), ('C', 6), ('E', 6), ('D', 6), ('B', 5), ('G', 5), ('B', 5), ('C', 6)]
    buf = np.zeros(int(SR * 7.5))
    t = 0.0
    for i, (n, o) in enumerate(seq):
        d = 0.42 if i == len(seq) - 1 else 0.3
        place(buf, bell(hz(n, o), 1.4, 0.45, ratio=2.0, index=1.2, decay=2.2), t)
        if i % 4 == 0:
            place(buf, bell(hz(n, o - 1), 1.6, 0.25, ratio=1.0, index=0.6, decay=1.8), t)
        t += d
    return norm(echo(buf, 0.21, 0.25, 3))


def s_door_chime():
    buf = np.zeros(int(SR * 2.0))
    for i, (n, o) in enumerate([('A', 5), ('F#', 5), ('A', 5), ('F#', 5)]):
        place(buf, bell(hz(n, o), 0.9, 0.5, ratio=1.0, index=0.3, decay=3.5), i * 0.32)
    return norm(buf)


def s_train_run():
    n = int(SR * 4.25)
    t = np.arange(n) / SR
    rng = np.random.default_rng(5)
    noise = rng.standard_normal(n)
    rumble = lowpass_fast(noise, 180) * 3.0
    hiss = bandpass_fast(noise, 800, 2600) * 0.25
    motor = 0.25 * np.sin(2 * np.pi * 96 * t + 0.5 * np.sin(2 * np.pi * 0.7 * t)) + 0.12 * np.sin(2 * np.pi * 192 * t)
    s = rumble + hiss * (0.8 + 0.2 * np.sin(2 * np.pi * 0.5 * t)) + motor
    return norm(loop_fade(s, 0.25), 0.7)


def clack(at, buf, amp=1.0):
    n = int(SR * 0.09)
    t = np.arange(n) / SR
    rng = np.random.default_rng(int(at * 1000))
    s = (rng.standard_normal(n) * 0.6 + np.sin(2 * np.pi * 140 * t)) * np.exp(-t * 55)
    place(buf, lowpass_fast(s, 1800) * amp, at)


def s_train_joint():
    buf = np.zeros(int(SR * 0.9))
    clack(0.0, buf, 1.0)
    clack(0.11, buf, 0.8)
    clack(0.52, buf, 0.95)
    clack(0.63, buf, 0.75)
    return norm(buf, 0.8)


def s_bus_run():
    n = int(SR * 3.25)
    t = np.arange(n) / SR
    rng = np.random.default_rng(9)
    f0 = 46
    saw = sum(np.sin(2 * np.pi * f0 * k * t) / k for k in range(1, 9))
    s = saw * (0.55 + 0.1 * np.sin(2 * np.pi * 8 * t)) + lowpass_fast(rng.standard_normal(n), 300) * 1.5
    return norm(loop_fade(lowpass_fast(s, 900), 0.25), 0.7)


def s_bus_bell():
    buf = np.zeros(int(SR * 1.3))
    place(buf, bell(hz('E', 6), 1.0, 0.5, ratio=1.0, index=0.2, decay=4), 0)
    place(buf, bell(hz('C', 6), 1.0, 0.5, ratio=1.0, index=0.2, decay=4), 0.38)
    return norm(buf)


def s_evening_chime():
    # original pentatonic village chime, played from a distant loudspeaker (~13 s)
    seq = [('G', 5, 1), ('A', 5, 1), ('G', 5, 1), ('E', 5, 1), ('D', 5, 1), ('E', 5, 1), ('G', 5, 2),
           ('A', 5, 1), ('C', 6, 1), ('A', 5, 1), ('G', 5, 1), ('E', 5, 1), ('D', 5, 1), ('C', 5, 2),
           ('D', 5, 1), ('E', 5, 1), ('G', 5, 1), ('E', 5, 1), ('D', 5, 1), ('C', 5, 3)]
    buf = np.zeros(int(SR * 14.0))
    t = 0.2
    for n, o, d in seq:
        place(buf, bell(hz(n, o), 2.2, 0.4, ratio=1.0, index=0.5, decay=1.2), t)
        t += 0.42 * d
    buf = echo(buf, 0.33, 0.4, 5)
    return norm(lowpass_fast(buf, 3000))


def s_cicada():
    # Minmin-zemi like: "miin-min-min-min..." (~5 s)
    n = int(SR * 5.0)
    t = np.arange(n) / SR
    rng = np.random.default_rng(3)
    car = bandpass_fast(rng.standard_normal(n), 3600, 6200)
    tone = np.sin(2 * np.pi * (4200 + 300 * np.sin(2 * np.pi * 2.6 * t)) * t)
    s = 0.6 * car + 0.4 * tone
    phrase = 0.5 + 0.5 * np.sin(2 * np.pi * 2.6 * t - np.pi / 2)
    amp = np.clip(phrase ** 1.5, 0, 1) * env_adsr(n, 0.4, 0.3, 1.0, 0.8)
    return norm(s * amp * (0.8 + 0.2 * np.sin(2 * np.pi * 120 * t)), 0.6)


def s_higurashi():
    # "kana-kana-kana..." falling trill (~4 s)
    buf = np.zeros(int(SR * 4.2))
    rng = np.random.default_rng(4)
    for k in range(26):
        at = 0.1 + k * 0.14
        n = int(SR * 0.12)
        t = np.arange(n) / SR
        f = 4800 - k * 38
        s = np.sin(2 * np.pi * f * t + 0.6 * np.sin(2 * np.pi * 45 * t)) * np.exp(-t * 18) * (1 - k / 32)
        place(buf, s, at)
    buf += bandpass_fast(rng.standard_normal(len(buf)), 4000, 5000) * 0.05
    return norm(echo(buf, 0.27, 0.3, 3), 0.55)


def s_frogs():
    buf = np.zeros(int(SR * 4.5))
    rng = np.random.default_rng(6)
    for k in range(14):
        at = rng.uniform(0, 4.0)
        n = int(SR * 0.16)
        t = np.arange(n) / SR
        f = rng.uniform(380, 520)
        s = np.sign(np.sin(2 * np.pi * f * t)) * 0.5 * (0.5 + 0.5 * np.sin(2 * np.pi * 32 * t)) * np.exp(-t * 14)
        place(buf, lowpass_fast(s, 1600), at)
        place(buf, lowpass_fast(s, 1600) * 0.8, at + 0.19)
    return norm(echo(buf, 0.2, 0.25, 2), 0.5)


def s_waves():
    n = int(SR * 7.0)
    t = np.arange(n) / SR
    rng = np.random.default_rng(8)
    noise = rng.standard_normal(n)
    swell = np.clip(np.sin(2 * np.pi * t / 7.0) ** 2, 0, 1)
    s = lowpass_fast(noise, 900) * (0.3 + 0.9 * swell) + bandpass_fast(noise, 1500, 5000) * 0.25 * swell ** 3
    return norm(s * env_adsr(n, 0.8, 0.5, 1.0, 1.5), 0.6)


def s_talk():
    n = int(SR * 0.07)
    t = np.arange(n) / SR
    s = (np.sin(2 * np.pi * 620 * t) + 0.4 * np.sin(2 * np.pi * 1240 * t)) * np.exp(-t * 45)
    return norm(s, 0.7)


def s_coin():
    buf = np.zeros(int(SR * 0.6))
    place(buf, bell(2637, 0.5, 0.5, ratio=2.76, index=1.5, decay=9), 0.0)
    place(buf, bell(3136, 0.5, 0.5, ratio=2.76, index=1.5, decay=9), 0.07)
    return norm(buf, 0.7)


def s_ticket_gate():
    buf = np.zeros(int(SR * 0.4))
    for at in (0.0, 0.14):
        n = int(SR * 0.09)
        t = np.arange(n) / SR
        place(buf, np.sin(2 * np.pi * 2093 * t) * env_adsr(n, 0.005, 0.02, 0.8, 0.03), at)
    return norm(buf, 0.6)


SOUNDS = {
    'melody': (s_melody, '発車メロディ', 'Departure melody'),
    'door_chime': (s_door_chime, 'ドアチャイム', 'Door chime'),
    'train_run': (s_train_run, '電車の走行音', 'Train running'),
    'train_joint': (s_train_joint, 'ガタンゴトン', 'Rail joints'),
    'bus_run': (s_bus_run, 'バスのエンジン', 'Bus engine'),
    'bus_bell': (s_bus_bell, '降車チャイム', 'Bus stop chime'),
    'evening_chime': (s_evening_chime, '夕方のチャイム', 'Evening village chime'),
    'cicada': (s_cicada, 'セミの声', 'Cicadas'),
    'higurashi': (s_higurashi, 'ヒグラシの声', 'Evening cicadas'),
    'frogs': (s_frogs, 'カエルの声', 'Frogs'),
    'waves': (s_waves, '波の音', 'Waves'),
    'talk': (s_talk, '話し声', 'Talking'),
    'coin': (s_coin, 'お金の音', 'Coins'),
    'ticket_gate': (s_ticket_gate, '改札の音', 'Ticket gate'),
}


def write_ogg(sig, path):
    pcm = (np.clip(sig, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
        tmp = f.name
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '4', path], check=True)
    os.unlink(tmp)
