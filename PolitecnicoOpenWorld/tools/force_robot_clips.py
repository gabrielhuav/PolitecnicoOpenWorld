#!/usr/bin/env python3
"""Force-extract speech clips for ROBOT with lower thresholds."""
from __future__ import annotations

import math
import struct
import wave
from pathlib import Path

SCRAPE = Path(__file__).resolve().parent / "sf_voice_scrape"
SRC = SCRAPE / "converted" / "ROBOT" / "sources"
CLIPS = SCRAPE / "converted" / "ROBOT" / "clips"
CLIPS.mkdir(parents=True, exist_ok=True)


def load(p: Path):
    with wave.open(str(p), "rb") as w:
        ch, sw, rate, frames = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        raw = w.readframes(frames)
    if sw != 2:
        print(f"skip {p.name}: sampwidth={sw}")
        return None, None
    n = frames * ch
    samples = list(struct.unpack("<" + "h" * n, raw[: n * 2]))
    if ch > 1:
        samples = [samples[i] for i in range(0, len(samples), ch)]
    return samples, rate


def windows(samples, rate, win=0.05):
    step = max(1, int(rate * win))
    out = []
    for i in range(0, len(samples) - step, step):
        chunk = samples[i : i + step]
        s = sum(x * x for x in chunk) / len(chunk)
        out.append((i / rate, math.sqrt(s) / 32768.0))
    return out


def main():
    total = 0
    for p in sorted(SRC.glob("*.wav")):
        samples, rate = load(p)
        if not samples:
            print(f"empty {p.name}")
            continue
        wins = windows(samples, rate)
        top = sorted(wins, key=lambda x: -x[1])[:12]
        print(f"\n{p.name} n={len(samples)} rate={rate} top={[round(t[1], 4) for t in top[:6]]}")
        used = []
        for t, r in top:
            if r < 0.008:
                continue
            if any(abs(t - u) < 1.2 for u in used):
                continue
            used.append(t)
            if len(used) > 4:
                break
            start = max(0, int((t - 0.1) * rate))
            end = min(len(samples), start + int(1.3 * rate))
            chunk = samples[start:end]
            if len(chunk) < rate // 4:
                continue
            outp = CLIPS / f"force_{p.stem}__t{t:.1f}_r{r:.3f}.wav"
            with wave.open(str(outp), "wb") as w:
                w.setnchannels(1)
                w.setsampwidth(2)
                w.setframerate(rate)
                w.writeframes(struct.pack("<" + "h" * len(chunk), *chunk))
            print(f"  wrote {outp.name} rms={r:.3f}")
            total += 1
    print(f"\nTotal clips: {total}")


if __name__ == "__main__":
    main()
