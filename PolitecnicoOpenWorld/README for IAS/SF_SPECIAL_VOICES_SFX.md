# SFX de especiales por personaje (voces) — HUELUM VS. GOYA

> **Actualizado 2026-07-18 (sesión Grok / voces v2).**  
> **No empieces de cero:** las fuentes YT **ya están en disco** y los specials **nuevos** están en
> `tools/sf_voice_scrape/out_diarized/` (aún **no** copiados a assets salvo que el dueño lo apruebe).
> Los `special_*.ogg` en el APK pueden ser la generación **anterior** (curate 0.9–1.15 s).
>
> **Handoff Fable:** `_ARCHIVO/PROMPT_traspaso_Fable_2026-07-18_voces.md` — la **prioridad** de esa
> sesión es **IA pelea + Showcase** (prompt original primero); este doc es **contexto secundario**
> de voces (no re-scrape). Deepfake lab: **no implementado**.

---

## 0. Estado honesto (leer antes de tocar nada)

| Capa | Estado |
|------|--------|
| **APK assets** `STREETFIGHTER/SOUNDS/special_*.ogg` | 21 archivos (pack/curate viejo). **No borrar** hasta instalar la pasada diarizada. |
| **Nuevos specials diarizados** | `tools/sf_voice_scrape/out_diarized/<FIGHTER>/special_<id>.ogg` + `meta.json` |
| **Catálogo i18n frases** | `app/.../STREETFIGHTER/DATA/special_phrases.json` + fuente editable `tools/sf_voice_scrape/special_phrases_catalog.json` |
| **Subtítulos special** | **CABLEADO** en código: `SfSpecialPhrases.kt`, `emitSpecialVoice` en VM, draw HUD en Screen |
| **Deepfake / voice lab avanzado** | **NO implementado** (pedido del dueño; pendiente). Solo diarización + recortes + SFX sintético en genéricos. |
| **Lázaro** | **Excluido** de frases special (sin implementar special de voz propio). |

### Convención de nombre (runtime)

```
special_<SfFighterId.name.lowercase()>.ogg
```

Emisión: `StreetFighterViewModel.specialSfxKey` / `emitSpecialVoice`  
Fallback: `hadouken.ogg`

---

## 1. Reglas de negocio (dueño)

1. **Famosos con scrape real** (voz del personaje, no relleno):  
   Prankedy, Paparazzi 1, Paparazzi 5, Señor tienda, Rey grupero, Llorona, Charro (nahual),  
   Policías CDMX H/M, Granaderos (banda), Cruz Roja.
2. **Genéricos** (Tzitzimime, Yoalli, estudiantes, robot, paramédico genérico):  
   SFX / textura; **frases INVENTADAS** (no STT del vídeo).
3. **Diarización:** separar hablantes → ranking por **tiempo de habla**.  
   - Rank 0 = principal (Prankedy).  
   - Rank 1 = segundo (Paparazzi / Señor tienda).  
   - Pitch alto = mujer (Llorona / Presidenta / Policía CDMX mujer).
4. **No** empaquetar el vídeo completo: solo burst 1.5–6 s (banda/SFX puede ser ~3 s).
5. **No** reutilizar Pap1 como Pap5 (Pap5 = solo `AEmVeK88HIs` cuando exista en disco).

---

## 2. Fuentes YouTube YA DESCARGADAS (no re-scrapear a ciegas)

Raíz tools: `PolitecnicoOpenWorld/tools/sf_voice_scrape/`  
WAV largos en **`raw_yt/`** (gitignored normalmente; **en la máquina del dueño ya están**).

| Peleador(es) | Video ID | URL | Archivo local |
|--------------|----------|-----|----------------|
| **SENOR_TIENDA** + **PRANKEDY** | `FdUblky8bV4` | https://www.youtube.com/watch?v=FdUblky8bV4 | `raw_yt/TIENDA_bestof_FdUblky8bV4.wav` (~66 MB) best-of |
| **REY_GRUPERO** | `VPu6zFKcb7Y` | https://www.youtube.com/watch?v=VPu6zFKcb7Y | `raw_yt/REY_GRUPERO_VPu6zFKcb7Y.wav` |
| **CHARRO_NEGRO** | `0bYg6FmCjhE` | https://www.youtube.com/watch?v=0bYg6FmCjhE | `raw_yt/CHARRO_NEGRO_0bYg6FmCjhE.wav` |
| **CHARRO_NEGRO** | `xlYlM4WgqYU` | https://www.youtube.com/watch?v=xlYlM4WgqYU | `raw_yt/CHARRO_NEGRO_xlYlM4WgqYU.wav` |
| **CHARRO_NEGRO** (nahual) | `DK5ZgKoM8Xw` | https://www.youtube.com/watch?v=DK5ZgKoM8Xw | `raw_yt/CHARRO_NEGRO_DK5ZgKoM8Xw.wav` (best pick actual) |
| **GRANADEROS** (×3 ids) | `5fN8KeZ2JLA` | https://www.youtube.com/watch?v=5fN8KeZ2JLA | `raw_yt/GRANADEROS_3_diana_5fN8KeZ2JLA.wav` (3 de diana / banda) |
| **POLICIA_CDMX** + **_HOMBRE** | `ASgdaRKHon8` | https://www.youtube.com/watch?v=ASgdaRKHon8 | `raw_yt/POLICIA_CDMX_ASgdaRKHon8.wav` (mujer pitch↑ / hombre pitch↓) |
| **PARAMEDICO_CRUZ_ROJA** | `l89qiD3aqVQ` | https://www.youtube.com/watch?v=l89qiD3aqVQ | `raw_yt/PARAMEDICO_CRUZ_ROJA_l89qiD3aqVQ.wav` — **solo t≥60s** |
| **YOALLI** (textura) | `Ye4PRm-kebc`, `d1M7uqfsofs` | YT | `raw_yt/YOALLI_EHECATL_*.wav` |
| **PAPARAZZI_1** | local + clips | `nuevoMaterial17JUL/PAPARAZZI 1!! BROMA.mp3` | + `raw_yt/PAPARAZZI_1_*` |
| **PAPARAZZI_5** | `AEmVeK88HIs` | https://www.youtube.com/watch?v=AEmVeK88HIs | ⚠️ **AGE-GATE** — **NO** en disco aún. Script: `tools/process_paparazzi5.py`. Drop: `nuevoMaterial17JUL/PAPARAZZI_5_AEmVeK88HIs.mp3` |

### Locales útiles

- `nuevoMaterial17JUL/Prankedy*.mp3`, `PAPARAZZI 1!! BROMA.mp3`
- `tools/sf_voice_scrape/raw_curated/` (llorona_ay, senor_tienda legacy, prankedy_taco/tank)

---

## 3. Specials nuevos generados (`out_diarized/`)

Cada carpeta: `special_<id>.ogg` + a menudo `special_voice.wav` + `meta.json`.

| FIGHTER | Fuente usada (meta) | Notas |
|---------|---------------------|--------|
| SENOR_TIENDA | best-of FdUblky8bV4 rank1 | Prioridad del dueño |
| PRANKEDY | best-of FdUblky8bV4 rank0 | |
| REY_GRUPERO | VPu6zFKcb7Y | |
| CHARRO_NEGRO | DK5ZgKoM8Xw (mejor score) | Nahual SFX |
| GRANADERO | 3 de diana 5fN8KeZ2JLA | Banda; no policía normal |
| POLICIA_GRANADERO_HOMBRE | idem + pitch −1 | |
| POLICIA_GRANADERO_MUJER | idem + pitch +1.5 | |
| POLICIA_CDMX | ASgdaRKHon8 **mujer** | pitch ~225 |
| POLICIA_CDMX_HOMBRE | ASgdaRKHon8 **hombre** | pitch ~151; clip corto |
| PARAMEDICO_CRUZ_ROJA | l89qiD3aqVQ **t≥60** | t0≈94s |
| LA_LLORONA | diarize pitch alto | |
| LA_PRESIDENTA | live/clip pitch alto | **verificar Claudia (mujer)** a oído |
| LA_TZITZIMIME | generic SFX + frase inventada | NO scrape de diálogo |
| YOALLI_EHECATL | textura YT / synth + frase inventada | NO scrape de diálogo |
| PAPARAZZI_1 | diarize rank1 | |
| PAPARAZZI_5 | ⚠️ puede ser basura/Pap1 hasta bajar AEmVeK88HIs | |

Review MP3 para oír:  
`tools/sf_voice_scrape/REVIEW_MP3_TODOS/` (regenerar con pack).

---

## 4. Código Kotlin (subtítulos + SFX) — YA HECHO

| Archivo | Qué |
|---------|-----|
| `features/streetfighter/data/SfSpecialPhrases.kt` | Carga `special_phrases.json`; ES/EN/HUD |
| `StreetFighterViewModel.kt` | `emitSpecialVoice(id, now)` → SFX + `specialSubtitleHud` |
| `StreetFighterState.kt` | `specialSubtitleHud`, `specialSubtitleUntilMs` |
| `StreetFighterScreen.kt` | Dibuja subtítulo abajo con **fuente arcade** `drawFontText` |
| Lázaro | `emitSpecialVoice` cae a `hadouken` sin frase |

### Instalar specials nuevos en el APK (solo con OK del dueño)

```bash
# Desde repo root
# Copia out_diarized → assets (ejemplo PowerShell):
Get-ChildItem PolitecnicoOpenWorld/tools/sf_voice_scrape/out_diarized -Recurse -Filter special_*.ogg |
  Copy-Item -Destination PolitecnicoOpenWorld/app/src/main/assets/STREETFIGHTER/SOUNDS/ -Force
```

**No** copiar `special_lazaro` si se quiere mantener exclusión de voz.

---

## 5. Tools (pipeline actual)

| Script | Rol |
|--------|-----|
| `tools/diarize_special_voices.py` | VAD + clustering + rank hablantes (famosos) |
| `tools/process_charro_nahual.py` | Charro: 0bYg / xlYl / DK5 |
| `tools/process_paparazzi5.py` | Pap5 oficial AEmVeK88HIs (age-gate) |
| `tools/process_generic_sfx.py` | Tzitzimime / Yoalli SFX + frases inventadas |
| `tools/build_special_phrases_pack.py` | Catálogo → `DATA/special_phrases.json` + REVIEW MP3 |
| `tools/convert_all_voice_sources.py` | Inventario/convert mono WAV + clips |
| `tools/export_complete_phrases.py` | Frases completas (pre-diarize) |
| `tools/curate_sf_special_sfx.py` | **Legacy** pack corto 0.9–1.15 s |
| `tools/voice_lab.py` | Lab ligero; **no** deepfake completo |
| `tools/sf_voice_scrape/special_phrases_catalog.json` | Fuente de frases ES/EN |

### Regenerar review + JSON

```bash
python PolitecnicoOpenWorld/tools/build_special_phrases_pack.py
```

### Deepfake / voice lab (PENDIENTE — pedido dueño, no hecho)

Objetivo futuro: clonar/sintetizar voz por peleadór cuando falte fuente limpia (Pap5 age-gate,
Presidenta dudosa, etc.). **No** está en el código actual. Si Fable/Claude toca esto: planear
aparte; no bloquear IA/showcase.

---

## 6. Frases (catálogo) — ES original / EN traducción

Editables en `special_phrases_catalog.json`. Runtime: `special_phrases.json`.

Ejemplos:
- Llorona: «¡Ay, mis hijos!»  
- Señor: «¡Fuera de mi tienda!»  
- Charro: «¡El nahual despierta!» (inventada sobre SFX)  
- Tzitzimime: «¡Devoren el cielo!» (**inventada**, no del YT)  
- Yoalli: «¡Soplo del anahuac!» (**inventada**)  
- Cruz Roja: «¡Urgencias!»  
- Granadero: «¡Toque de diana!» (banda)

---

## 7. Gotchas YouTube (esta máquina)

- Muchos vídeos bajan con:  
  `yt-dlp --js-runtimes node --cookies-from-browser firefox …`
- **Age-gate** (Pap5 `AEmVeK88HIs`): hace falta cuenta YouTube logueada en cookies Chrome  
  (cerrar Chrome si DPAPI falla) o drop manual del MP3.
- No re-bajar 66 MB del best-of tienda si ya existe `TIENDA_bestof_FdUblky8bV4.wav`.

---

## 8. Backlog P0 (voces)

1. **Pap5 oficial** `AEmVeK88HIs` → drop + `process_paparazzi5.py`  
2. **Instalar** `out_diarized` → assets (aprobación dueño)  
3. Validar a oído: Señor vs Prankedy, Presidenta mujer, Policía hombre corto  
4. **Deepfake lab** (pedido, no hecho)  
5. Reemplazar golpes/land/hadouken residual (copyright) — fuera de este pipeline

---

## 9. Protocolo docs

Al cambiar voces/código de subtítulos: actualizar **este archivo** + mención en **07** §HUELUM  
+ línea en **00_INDEX**. Ver 09.
