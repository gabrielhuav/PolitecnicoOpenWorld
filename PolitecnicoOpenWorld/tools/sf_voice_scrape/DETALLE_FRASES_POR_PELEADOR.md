# Detalle por peleadór: qué frase, de dónde, qué hay cortado ahora

> **Importante:** lo que hay en `candidates/` son **picos de voz (~1.1 s)** candidatos,  
> **no** subtítulos con la frase literal verificada por oído/STT.  
> YouTube canónico **no se pudo bajar** en esta PC (bot + cookies DPAPI).  
> Por eso muchos peleadors usan material **local** o **extracted** de scrapeos previos.  
> **Nada de esto se instaló en el APK** hasta tu aprobación.

Convención de paths: relativos a `tools/sf_voice_scrape/`.

---

## Resumen rápido

| Peleador | Frase / intención | Fuente **objetivo** (canon) | Qué hay **ahora** en candidates |
|----------|-------------------|-----------------------------|----------------------------------|
| PRANKEDY | Gritos/risas de broma callejera + tanque | Paparazzi series + tanque + locales | 9 picos de `Prankedy0/2/3.mp3` locales |
| PAPARAZZI_1 | Energía **Paparazzi 1** | YT `7Ug2kyQeRHQ` (~8.1M) | 3 picos de `PAPARAZZI 1!! BROMA.mp3` **local** |
| PAPARAZZI_5 | Energía **Paparazzi 5** (distinto) | YT `AEmVeK88HIs` (~5.5M) | **0 del vídeo 5** (YT bloqueado) |
| SENOR_TIENDA | Regaños/reacciones del señor | Playlist canónica (11+ vídeos) | **0 del canon** (YT bloqueado) |
| REY_GRUPERO | Fiesta/calle (sin serie propia) | Derivado | 3 picos reutilizando Prankedy3 local |
| LA_PRESIDENTA | Voz mañanera / discurso | Clips X oficiales | 6 picos de WAVs X previos |
| LA_LLORONA | “Ay mis hijos” / lamento | YT `ZL0LtvysugY` | 6 picos ~t=12.7s (zona lamento) |
| LA_TZITZIMIME | Lamento monstruo | Mismo pool Llorona + pitch luego | 6 picos (misma base Llorona) |
| YOALLI | Lamento místico | Llorona base | 6 picos |
| CHARRO_NEGRO | Narración leyenda | Draw My Life + clips | 6 picos extracted |
| PARAMEDICO_CRUZ_ROJA | Voz institucional CR | YT Cruz Roja + X | 6 picos extracted |
| POLICIA_* / GRANADEROS | Boletín / radio SSC | X SSC previos | 6–9 picos extracted |
| ESCOMBOY/GIRL/ROBOT | Sin voz real canónica | Derivados | picos genéricos / pitch |

---

## 1. PRANKEDY

### Frases / momentos definidos (catálogo v2)

| phrase_id | Qué se busca | Fuentes canónicas (prioridad) |
|-----------|--------------|-------------------------------|
| `prank_paparazzi_energy` | Voz de Edy en modo broma callejera / risas / “¡ey!” | `7Ug2kyQeRHQ` Pap1, `6dfQvumn5Ts` Pap20, `XFdRAR8CmZ4` taco, locales Prankedy0/2 |
| `prank_tanque_gas` | Momento pesado tanque de gas | `zreqHXngBAw`, `hRB7ryS4Spg`, local Prankedy3 |
| `prank_drive_negocios` | Otras bromas virales | `S4miJwOryIo` drive-thru, `wjdu4NofGTE` negocios |

### Qué hay cortado ahora
| Archivo candidato (ej.) | Fuente real | t≈ | Nota |
|-------------------------|-------------|-----|------|
| `candidates/PRANKEDY/prank_paparazzi_energy/local_Prankedy0Actual__peak0_t43.8s_…` | `nuevoMaterial17JUL/Prankedy0Actual.mp3` | 43.8s | Pico de voz, **no** STT de frase |
| `…/local_Prankedy2__peak0_t39.2s_…` | Prankedy2.mp3 | 39.2s | Idem |
| `…/prank_tanque_gas/local_Prankedy3__peak0_t214.1s_…` | Prankedy3.mp3 | 214s | Idem |

**No es** “la frase exacta del tanque de gas del reupload” hasta bajar esos YT.

---

## 2. PAPARAZZI_1

| phrase_id | Intención | Fuente canónica |
|-----------|-----------|-----------------|
| `pap1_official` | Audio de la broma **Paparazzi 1** (serie original) | **YT `7Ug2kyQeRHQ`** (~8.1M views) |

### Cortado ahora
| Candidato | Fuente | t≈ | score |
|-----------|--------|-----|-------|
| `local_PAPARAZZI 1!! BROMA__peak0_t34.1s` | `nuevoMaterial17JUL/PAPARAZZI 1!! BROMA.mp3` | **34.1s** | **0.45** (mejor de todos) |
| peak1 | mismo MP3 | 141s | 0.40 |
| peak2 | mismo MP3 | 134s | 0.29 |

El MP3 local es casi seguro la misma broma Pap1 o muy cercana. El YT oficial no se bajó.

---

## 3. PAPARAZZI_5  ⚠️

| phrase_id | Intención | Fuente canónica |
|-----------|-----------|-----------------|
| `pap5_official` | Audio de **Paparazzi 5** (otro vídeo, otra broma) | **YT `AEmVeK88HIs`** (~5.5M) |
| backup | Pap 16 / 20 | `ufjkSjGWJlc`, `6dfQvumn5Ts` |

### Cortado ahora
**Ningún candidato propio del vídeo 5.**  
Antes se reusaba Pap1 con pitch — **eso está mal** y la v2 lo prohíbe en el catálogo.

**Acción pendiente:** bajar a mano `AEmVeK88HIs` → `nuevoMaterial17JUL/PAPARAZZI_5_OFFICIAL.mp3` o cookies YT.

---

## 4. SENOR_TIENDA  ⚠️ (serie larga)

Playlist oficial canal Prankedy  
`https://www.youtube.com/playlist?list=PLnYLFE_wg_9_G6W2h55Er6W2SkDVsTD40`:

| # | ID | Título | Views |
|---|-----|--------|-------|
| 1 | `y8IkFV3ly-w` | PRANK ON THE OWNER OF STORE 1!! | ~8.2M |
| 2 | `BWjLyUJtSgE` | BROMA AL SEÑOR DE LA TIENDA 2!! | **~10M** |
| 3 | `AmDjgLtcqDk` | BROMA AL SEÑOR DE LA TIENDA 3!! | ~8.2M |
| 4 | **`gzXw_4kfZBo`** | **VISITANDO… (el más icónico)** | **~24M** |
| 5 | `2USZ8cH9Vjg` | ADIÓS / GOODBYE TO THE STORE GUY | **~17M** |
| 6 | `0z8RQXE_2NY` | DEMANDO AL SEÑOR DE LA TIENDA | ~15M |
| 7 | `_Lvp7unX0o4` | CRAZY SELLER PRANK | ~6.7M |
| 8 | `DZcNCEQH7OY` | THE FAKE COMMERCIAL | ~8.7M |
| 9 | `uZL4Q7wYNJI` | ROBANDO AL SEÑOR DE LA TIENDA | ~8.5M |
| 10 | `96pMFpyVM4Q` | Report / cierran tienda | ~4.4M |
| 11 | `UEJ0eO9m2eo` | Vacié la tienda (retorno 2025) | alto |

**Frase/intención para pelea:** regaños, “¡fuera!”, gritos de molestia del señor (no la narración de Prankedy).  
Ideal: STT o oído humano sobre esos 11 vídeos → recortes de **su** voz.

### Cortado ahora
**0 del canon** (YT bloqueado). Cualquier `special_senor_tienda` viejo en assets era pitch de Prankedy, no el señor.

---

## 5. REY_GRUPERO

| phrase_id | Intención | Fuente |
|-----------|-----------|--------|
| `grupero_fiesta` | Grito “fiesta/grupero” (sin serie YouTube propia del personaje POW) | Temporal: Prankedy3 local |

**No hay** vídeo “Rey Grupero oficial”. Opciones: (a) material propio que grabes, (b) voice lab pitch+FX, (c) no voz real.

Candidatos actuales: 3 picos de Prankedy3 (débiles como “canon”).

---

## 6. LA_PRESIDENTA

| phrase_id | Intención | Fuente |
|-----------|-----------|--------|
| `presidenta_voz_oficial` | Fragmento de **voz real** de Sheinbaum (mañanera/anuncio) | Clips X previos `LA_PRESIDENTA_e181ce2526d8.wav` etc. |

Candidatos: picos t≈42–62s en esos WAV (speech, rms~0.08–0.16).  
**No** se hizo deepfake; es audio real corto.

---

## 7. LA_LLORONA / TZITZIMIME / YOALLI

| Peleador | Frase | Fuente |
|----------|-------|--------|
| LA_LLORONA | Lamento tipo “Ay mis hijos” | `ZL0LtvysugY` + extracted; picos **t≈12.7s** |
| LA_TZITZIMIME | Mismo lamento más grave (pitch en curate) | Misma base Llorona |
| YOALLI | Lamento místico (no discurso político) | Misma base Llorona |

---

## 8. CHARRO_NEGRO

Narración / atmósfera de leyenda → extracted CHARRO (X/YT previos). Picos t≈40s, 96s, 145s.

---

## 9. PARAMÉDICOS / POLICÍAS

Voz institucional (Cruz Roja / SSC), no catchphrase de meme. Fuentes: extracted de scrapeos X previos + YT CR fallido.

---

## 10. ESCOMBOY / ESCOMGIRL / ROBOT / alphas

Sin voz pública canónica del personaje POW → solo **placeholders** derivados.  
Robot: speech + robotize en lab. Lázaro/Granadero genéricos: no arcade.

---

# ¿Deepfake de audio? ¿Script que junte / reconozca / tunee?

## Sí se puede un “voice lab” (recomendado y ya tiene sentido)

Pipeline Python legítimo (sin clonar a una persona real sin permiso):

| Paso | Técnica | Uso en POW |
|------|---------|------------|
| 1. Juntar | concat / crossfade de varios candidatos | Variantes de special |
| 2. Reconocer / localizar | **VAD** + opcional **Whisper STT** (buscar “tienda”, “ay mis hijos”) | Encontrar la frase en un vídeo largo |
| 3. Aislar | highpass/lowpass, noise reduce (no.spectralgate / RNNoise) | Limpiar calle |
| 4. Tunear | pitch, formant light, compresión, reverb horror/radio | Diferenciar Pap5 vs Pap1, Tzitzimime, Robot |
| 5. Export | OGG vorbis 1 s pelea | `special_*.ogg` |

Script: `tools/voice_lab.py` (preview packs en `candidates/lab/`).

## Deepfake / voice clone (RVC, Tortoise, ElevenLabs, etc.)

| | |
|--|--|
| **Técnicamente** | Sí: entrenar con horas de voz → generar frases nuevas |
| **En este repo** | **No implementado** a propósito |
| **Por qué** | Clonar a Prankedy, al señor de la tienda o a la Presidenta **sin licencia** es alto riesgo legal (voz/imagen) y no es “scrapeo de clip corto” |
| **Cuándo sí** | Voces **ficticias** (ESCOM, robot, llorona sintética) o con **permiso escrito** del dueño de la voz |

Si quieres un lab de **síntesis para personajes inventados**, se puede añadir después (espeak/piper/Coqui) — no es deepfake de famosos.

---

## Qué falta para que el detalle sea “frase exacta”

1. Bajar (manual o cookies YT) al menos:  
   - Pap5 `AEmVeK88HIs`  
   - Tienda `gzXw_4kfZBo` + 2–3 más de la tabla  
2. Correr `voice_lab.py --stt` (Whisper) sobre esos WAV → timestamps de palabras.  
3. Tú apruebas frase + archivo → install.

Hasta entonces este documento es la verdad de **qué se pretendía** vs **qué hay en disco**.
