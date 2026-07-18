#!/usr/bin/env python3
"""Auditoría rápida: JSON + PNG de cada peleador; rects src dentro de la hoja."""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "app" / "src" / "main" / "assets" / "STREETFIGHTER" / "DATA"
IMG = ROOT / "app" / "src" / "main" / "assets" / "STREETFIGHTER" / "IMAGES"
MODELS = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "ovh"
    / "gabrielhuav"
    / "pow"
    / "domain"
    / "models"
    / "streetfighter"
    / "SfModels.kt"
)


def main() -> int:
    text = MODELS.read_text(encoding="utf-8")
    pairs = re.findall(
        r'STREETFIGHTER/DATA/([a-z0-9_]+)\.json"[^"]*"STREETFIGHTER/IMAGES/([A-Za-z0-9_]+\.png)"',
        text,
    )
    # shared use RUNTIME - skip
    print(f"pares enum dedicados: {len(pairs)}")
    bad_any = False
    from PIL import Image

    for jn, imn in pairs:
        jp, ip = DATA / f"{jn}.json", IMG / imn
        if not jp.exists():
            print(f"MISS JSON {jn}")
            bad_any = True
            continue
        if not ip.exists():
            print(f"MISS IMG  {imn}")
            bad_any = True
            continue
        d = json.loads(jp.read_text(encoding="utf-8"))
        im = Image.open(ip)
        w, h = im.size
        oob = []
        for k, f in d["frames"].items():
            x, y, fw, fh = f["src"]
            if x < 0 or y < 0 or x + fw > w or y + fh > h:
                oob.append(k)
        miss_anim = []
        for ak, steps in d.get("animations", {}).items():
            for st in steps:
                fk = st[0] if isinstance(st, list) else st.get("frameKey")
                if fk and fk not in d["frames"]:
                    miss_anim.append(f"{ak}:{fk}")
        status = "OK"
        if oob or miss_anim:
            status = "FAIL"
            bad_any = True
        print(
            f"{status:4} {jn:22} {imn:28} {w}x{h} frames={len(d['frames'])} "
            f"oob={len(oob)} missAnim={len(miss_anim)}"
        )
    return 1 if bad_any else 0


if __name__ == "__main__":
    raise SystemExit(main())
