# SF character special voices (X / YouTube scrape → OGG)

> **Doc canónica (trabajo futuro, IAS):**  
> `PolitecnicoOpenWorld/README for IAS/SF_SPECIAL_VOICES_SFX.md`  
> (pipeline completo, recetas, gotchas, backlog). Este README es solo la chuleta de tools.

Pipeline to replace the single shared **`hadouken.ogg`** with **per-fighter special SFX**
(`special_<sf_fighter_id_lower>.ogg`).

## What was scraped (deep X pass 2026-07-18)

| Fighter | Sources |
|---------|---------|
| **PRANKEDY** | Local `nuevoMaterial17JUL/Prankedy*.mp3` + videos from **@Prankedyy** (bromas FIFAS, concierto, etc.) |
| **LA_PRESIDENTA** | Official **@Claudiashein** amplify videos (mañanera / sargazo / economía) |
| **YOALLI_EHECATL** | Same base as Presidenta, pitch +5 (forma nocturna) |
| **LA_LLORONA** | Horror clips (gritos / leyenda) from X |
| **CHARRO_NEGRO** | Leyenda mexicana video clips |
| **POLICIA_*** / **GRANADERO** | **@SSC_CDMX** institutional voiceovers |
| **PARAMEDICO_*** | **@CruzRoja_MX** / noticias Cruz Roja |
| **PAPARAZZI_*** | Local `PAPARAZZI 1!! BROMA.mp3` |
| **SENOR_TIENDA / REY_GRUPERO** | Prankedy material pitch-shifted (appear in his pranks) |
| Others | Related X audio + pitch / robotize transforms |

Catalog: `catalog_x_voices.json` (post IDs, CDN video URLs, priorities).

## Commands (from **repo root**)

```bash
# 1) Download X CDN videos + local MP3s → mono WAV under extracted/
python PolitecnicoOpenWorld/tools/scrape_sf_voices.py --max-per-fighter 3

# 2) YouTube audio (yt-dlp) — Prankedy, mañaneras, Llorona, SSC, Cruz Roja…
python PolitecnicoOpenWorld/tools/scrape_sf_voices_youtube.py --max-per-fighter 2

# 3) Blind pack (peak energy) — quick fallback
python PolitecnicoOpenWorld/tools/pack_sf_character_sfx.py --install

# 4) ★ RECOMENDADO: curación manual estilo pelea (frases/gritos + horror)
#    - Prankedy / Paparazzi: gritos de broma icónicos
#    - Llorona / Tzitzimime / Charro / Yoalli: FX tenebrosos (reverb, pitch)
#    - Policía: radio; Presidenta: speech; etc.
python PolitecnicoOpenWorld/tools/curate_sf_special_sfx.py --install
```

Requires **ffmpeg** + **yt-dlp** on PATH (`pip install yt-dlp`).

## Game wiring

- VM emits `special_<id>` on SPECIAL_1_* / BONUS_POWER_* / Presidenta metamorphosis.
- `StreetFighterScreen` loads all `special_*.ogg` + falls back to `hadouken` if missing.
- Assets: `app/src/main/assets/STREETFIGHTER/SOUNDS/special_*.ogg`

## Legal / ToS note

Clips are short, transformed SFX for a parody game. Celebrity / YouTuber voice rights and
X ToS still apply for commercial distribution — re-record or license if you ship publicly.
`raw/` and `extracted/` are working caches (large); prefer not committing them.
