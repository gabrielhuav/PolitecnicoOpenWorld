# SFX de especiales por personaje (voces) — HUELUM VS. GOYA

> **Estado (2026-07-18):** **21/21** peleadores del roster (`SfFighterId`) tienen
> `special_<id>.ogg` **funcional** (decodificable, no silencio, ~0.9–1.15 s, peak usable).
> `hadouken.ogg` queda solo como **fallback** si falta un asset.
>
> **Objetivo original:** dejar de usar un único “hadouken” genérico para todos y dar a cada
> personaje un grito/frase/lamento de especial propio, obtenido por scrapeo multi-fuente
> (X/Twitter, YouTube, material local) y **curación** estilo juego de pelea.

---

## 1. Qué hay en el APK (runtime)

| Ruta | Contenido |
|------|-----------|
| `app/src/main/assets/STREETFIGHTER/SOUNDS/special_*.ogg` | **21** clips de especial por peleadór |
| `…/SOUNDS/hadouken.ogg` | Fallback legacy (copyright residual del clon; prioridad media de reemplazo total) |
| `…/SOUNDS/*-attack.ogg`, `*-hit.ogg`, `land.ogg` | Golpes/aterrizaje **aún legacy** (no tocados por este pipeline) |
| `…/SOUNDS/prankedy-persecucion.mp3` | Música de pelea (no es SFX de especial) |
| `…/SOUNDS/amb_*.ogg` | Ambientes por mapa (no son specials) |

### Convención de nombre

```
special_<SfFighterId.name.lowercase()>.ogg
```

Ejemplos:

| `SfFighterId` | Archivo |
|---------------|---------|
| `PRANKEDY` | `special_prankedy.ogg` |
| `LA_PRESIDENTA` | `special_la_presidenta.ogg` |
| `PAPARAZZI_1` | `special_paparazzi_1.ogg` |
| `PARAMEDICO_CRUZ_ROJA` | `special_paramedico_cruz_roja.ogg` |
| `YOALLI_EHECATL` | `special_yoalli_ehecatl.ogg` |

**Importante:** el `name` del enum en Kotlin es la fuente de verdad (`id.name.lowercase()`).
Si se renombra un id en `SfModels.kt`, hay que renombrar el `.ogg` o el sonido cae a `hadouken`.

---

## 2. Cableado en código (no re-hardcodear `"hadouken"`)

| Capa | Archivo | Comportamiento |
|------|---------|----------------|
| Emisión | `StreetFighterViewModel.kt` | `specialSfxKey(id) = "special_${id.name.lowercase()}"` en **SPECIAL_1_***, **BONUS_POWER_*** y metamorfosis Presidenta |
| Carga | `StreetFighterScreen.kt` | Precarga `theme.soundKeys` + **todos** `special_*` del roster (`SfFighterId.entries`); `SoundPool` maxStreams=8 |
| Playback | `StreetFighterScreen.kt` | `soundIds[key] ?: soundIds["hadouken"]` |
| Tema | `SfTheme.kt` / `SF_CLASSIC_THEME` | `soundKeys` base (golpes/land/hadouken); specials se cargan aparte |

No hace falta listar los 21 specials en `soundKeys` del tema: la View los descubre por enum + `openFd` con `runCatching` (si falta el archivo, se omite y el collect usa fallback).

### Cuándo se oye

1. Jugador o CPU entra a `SPECIAL_1_LIGHT/MEDIUM/HEAVY` (combo ↓↘→ + puño).
2. Cualquier `BONUS_POWER_1`…`11` (botón P / IA).
3. Metamorfosis **La Presidenta → Yoalli** (umbral de vida): emite el special de la Presidenta al iniciar la animación; luego Yoalli usa el suyo en sus especiales.

---

## 3. Pipeline offline (tools) — orden recomendado

Todo se ejecuta desde la **raíz del repo** (donde está `nuevoMaterial17JUL/` y `PolitecnicoOpenWorld/`).

```text
[fuentes]  X CDN + YouTube (yt-dlp) + MP3 locales
    ↓
scrape_sf_voices.py              → raw/ + extracted/*.wav + manifest.json
scrape_sf_voices_youtube.py      → raw_yt/ + extracted/*_yt_*.wav (merge manifest)
    ↓
pack_sf_character_sfx.py         → out/ (pack “ciego” por pico de energía)  [opcional]
    ↓
curate_sf_special_sfx.py --install   ★ RECOMENDADO
    ↓
assets/STREETFIGHTER/SOUNDS/special_*.ogg
```

| Script | Ruta | Rol |
|--------|------|-----|
| `scrape_sf_voices.py` | `PolitecnicoOpenWorld/tools/` | Descarga catálogo **X** + copia locales; extrae mono WAV 44.1 kHz |
| `scrape_sf_voices_youtube.py` | idem | Descarga **YouTube** con **yt-dlp**; fusiona entradas en `manifest.json` |
| `pack_sf_character_sfx.py` | idem | Pack automático (pico RMS + pitch/robotize); útil como fallback |
| **`curate_sf_special_sfx.py`** | idem | **Curación pelea**: recetas por personaje, estilos FX, timestamps, speech-aware window |
| README corto tools | `tools/sf_voice_scrape/README.md` | Comandos rápidos del cache de scrapeo |

### Dependencias

- **ffmpeg** en PATH (extraer / FX / ogg vorbis).
- **yt-dlp** (`pip install -U yt-dlp`) para YouTube.
- **Python 3.10+**.
- No se necesita API key de Twitter/X: se usan URLs CDN públicas del catálogo (obtenidas con búsqueda X en sesión de IA).

### Regenerar e instalar (flujo día a día)

```bash
# Solo re-curar e instalar con lo ya scrapeado (rápido):
python PolitecnicoOpenWorld/tools/curate_sf_special_sfx.py --install

# Scrapeo de nuevo (si hay URLs nuevas en catálogos):
python PolitecnicoOpenWorld/tools/scrape_sf_voices.py --max-per-fighter 3
python PolitecnicoOpenWorld/tools/scrape_sf_voices_youtube.py --max-per-fighter 2
python PolitecnicoOpenWorld/tools/curate_sf_special_sfx.py --install
```

Flags útiles:

```bash
python …/scrape_sf_voices.py --fighters PRANKEDY,LA_PRESIDENTA
python …/scrape_sf_voices_youtube.py --fighters LA_LLORONA,CHARRO_NEGRO
```

---

## 4. Catálogos y cache (no commitear lo pesado)

| Ruta bajo `tools/sf_voice_scrape/` | Qué es | ¿Git? |
|-----------------------------------|--------|-------|
| `catalog_x_voices.json` | Posts X, URLs `video.twimg.com`, prioridades, pitch | **Sí** (pequeño) |
| `catalog_youtube_voices.json` | URLs YT / `ytsearch…`, secciones, prioridades | **Sí** |
| `manifest.json` | Resultado del último scrape (paths locales a WAV) | Opcional / regenerable |
| `raw/`, `raw_yt/`, `raw_curated/` | Videos/audio crudos (cientos de MB) | **No** (`.gitignore`) |
| `extracted/`, `curated_wav/` | WAV mono de trabajo | **No** |
| `out/`, `out_curated/` | OGG empaquetados intermedios | **No** (el que importa está en assets) |
| `curate_report.json` | Estilo/pitch/nota por peleadór de la última curación | Opcional |
| `functional_verify.json` | Última verificación “audible” 21/21 | Opcional |
| `.gitignore` | Ignora caches pesados | **Sí** |

**Material local del dueño (prioridad alta para Prankedy/Paparazzi):**

- Repo root: `nuevoMaterial17JUL/Prankedy*.mp3`, `PAPARAZZI 1!! BROMA.mp3`
- No van al APK enteros: solo el recorte curado ~1 s en `special_*.ogg`.

---

## 5. Curación: estilos FX y recetas

La curación **no** es “el frame más ruidoso del archivo”. Prioriza:

1. Fuentes con **voz** real (bromas, speech, lamentos).
2. Ventana ~**0.95–1.15 s** (longitud de special de pelea).
3. Detector speech-aware (RMS + ZCR + energía media) cuando `start=None`.
4. FX por arquetipo vía ffmpeg:

| `style` | Uso | Efecto (resumen) |
|---------|-----|------------------|
| `shout` | Prankedy, Paparazzi, Tienda, Rey, ESCOM, Lázaro | Compresión fuerte, highpass, slapback corto, volumen alto |
| `speech` | La Presidenta | Compresión suave, banda de voz, sin “efecto monstruo” |
| `horror` | Llorona, Tzitzimime, Yoalli | Reverb/echo largo, vibrato, lowpass, tenebroso |
| `metal` | Charro Negro | Eco grave, oscuro |
| `radio` | Policías / Granaderos | Banda estrecha tipo radio, compresión |
| `medic` | Paramédicos / Cruz Roja | Claro, urgente |
| `robot` | Robot | Tremolo + banda media |

Las recetas viven en código en `curate_sf_special_sfx.py` → `build_recipes()`:
`start` fijo (segundos) o `None` (auto), `pitch` en semitonos, `alt_keys` de fallback de fuente.

### Tabla actual (curación 2026-07-18)

| Peleador | Estilo | Pitch | Nota de curación (intención) |
|----------|--------|-------|------------------------------|
| PRANKEDY | shout | 0 | Grito/reacción de broma viral (YT full + locales) |
| PAPARAZZI_1 | shout | 0 | Frase/grito de `PAPARAZZI 1!! BROMA` (local icónico) |
| PAPARAZZI_5 | shout | −2.5 | Otra ventana de la misma broma, más grave |
| SENOR_TIENDA | shout | −4 | Universo Prankedy, voz grave |
| REY_GRUPERO | shout | −3 | Grito grave estilo grupero |
| LA_PRESIDENTA | speech | 0 | Voz real (clips X @Claudiashein; YT mañanera a veces salió **muda**) |
| YOALLI_EHECATL | horror | +4 | Lamento místico (base Llorona procesada; no speech político) |
| LA_LLORONA | horror | −1 | Lamento tipo “Ay mis hijos” |
| LA_TZITZIMIME | horror | −8 | Mismo lamento, monstruoso y más grave |
| CHARRO_NEGRO | metal | −2.5 | Voz/ambiente de leyenda oscura |
| POLICIA_* / GRANADERO | radio | ± | Voz institucional / operativo tipo radio |
| PARAMEDICO_* | medic | 0/+1 | Institucional Cruz Roja / urgente |
| ESCOMBOY / ESCOMGIRL | shout | +2 / +4 | Gritos “estudiantiles” (pitch) |
| ROBOT | robot | 0 | Voz procesada (tremolo/banda) |
| LAZARO | shout | −1 | Grito de pelea genérico POW |

Ver detalle vivo en `tools/sf_voice_scrape/curate_report.json` tras cada `--install`.

---

## 6. Fuentes usadas (scrapeo profundo)

### X / Twitter (CDN `video.twimg.com`)

Catálogo: `catalog_x_voices.json`.

- **@Prankedyy** — bromas, reacciones.
- **@Claudiashein** — clips con voz (sargazo, mañanera corta, economía).
- **@SSC_CDMX** — boletines / institucionales (policía).
- **Cruz Roja** / noticias — paramédicos.
- Clips de **La Llorona** / **Charro Negro** (leyenda / horror).

Búsqueda hecha con herramientas X de la sesión de IA; URLs guardadas en el JSON (reproducible sin API).

### YouTube (`yt-dlp`)

Catálogo: `catalog_youtube_voices.json`.

- Canal **Prankedy** (bromas largas; se usa ventana de voz, no el track completo).
- Reuploads / búsquedas: tanque de gas, señor de la tienda, paparazzi.
- Audio corto Llorona (“Ay mis hijos”).
- Narraciones Charro Negro.
- Institucionales SSC / Cruz Roja.
- `ytsearchN:…` como red de seguridad.

**Lección aprendida:** `--download-sections` de yt-dlp a veces produjo **WAV en silencio**
en mañaneras largas oficiales. Preferir:

1. Descarga `bestaudio` completa (o clip corto de noticias).
2. Corte con **ffmpeg** `-ss` / `-t` *después*.
3. O usar clips X que ya se validaron con RMS > 0.

### Local (repo)

`nuevoMaterial17JUL/`:

- `Prankedy0Actual.mp3` … `Prankedy6Actual.mp3`
- `PAPARAZZI 1!! BROMA.mp3`

Alta prioridad para Paparazzi y material Prankedy del dueño.

---

## 7. Verificación de “funcional”

Checklist que debe pasar cualquier mejora futura:

```text
Para cada SfFighterId:
  [ ] Existe assets/.../special_<id_lower>.ogg
  [ ] size ≳ 4 KB
  [ ] ffmpeg decodifica a PCM
  [ ] duración ~0.5–2.0 s (ideal ~1.0 s)
  [ ] rms ≳ 0.01 y peak ≳ 0.05 (no silencio)
  [ ] al pelear se oye al lanzar especial (no solo hadouken)
```

Última corrida automatizada (2026-07-18): **21/21 OK** →
`tools/sf_voice_scrape/functional_verify.json`.

Snippet de verificación rápida (raíz del repo):

```bash
# Re-curar + instalar
python PolitecnicoOpenWorld/tools/curate_sf_special_sfx.py --install

# Escuchar un ogg (Windows / ffplay si está)
ffplay -autoexit PolitecnicoOpenWorld/app/src/main/assets/STREETFIGHTER/SOUNDS/special_prankedy.ogg
```

En dispositivo: modo pelea → personaje → especial (↓↘→ + puño). Comparar P1 vs P2 con ids distintos.

---

## 8. Cómo mejorar en el futuro (backlog priorizado)

### P0 — Calidad de frase (contenido)

1. **Timestamps manuales** por broma icónica de Prankedy (p. ej. frase exacta del tanque de gas / “señor de la tienda”) fijados en `build_recipes()` en lugar de auto-window.
2. **Clips cortos de Presidenta** con frase reconocible (“buenos días”, anuncio) validados a oído; evitar mañaneras de 2 h con audio mudo en descarga.
3. **Paparazzi:** si el dueño graba o aporta más MP3 de “¡foto!” / flash, sustituir ventana local.
4. **ESCOM boy/girl:** hoy son pitch de material genérico; ideal voces propias o takes del dueño.

### P1 — Fantasmas / tenebrosos

5. Llorona/Charro/Tzitzimime: grabar o licenciar **efectos propios** (CC0) + capa de reverb, para no depender de un único audio YT.
6. Capas: lamento + whoosh + sub-bass corto al inicio del special (dos capas mezcladas en el pack).

### P2 — Pipeline / legal / limpieza copyright

7. Sustituir **golpes** y **land** legacy (`light-attack.ogg`…, `hadouken.ogg` residual) por SFX propios/CC0 (ver `ASSETS_STREETFIGHTER_MIGRACION.md`).
8. **Derechos:** voces de celebridades/YouTubers en un juego comercial pueden requerir licencia o regrabación. Documentar atribución y preferir material del dueño / CC0 / síntesis propia antes de Play Store.
9. No commitear `raw*` / `extracted*` (ya en `.gitignore` del folder).
10. Añadir test o script CI opcional: “todos los `SfFighterId` tienen special ogg no vacío”.

### P3 — Gameplay audio

11. Variantes por fuerza: `special_<id>_l/m/h.ogg` (hoy una sola clave).
12. SFX distinto en **KO / victoria** por personaje.
13. Duck de música al special (bajar `prankedy-persecucion` 200 ms).

---

## 9. Cómo añadir un peleadór nuevo

1. Añadir `SfFighterId.FOO` en `SfModels.kt` (sheet, JSON, etc.).
2. Conseguir audio (local / YT / X) y registrar en `catalog_*` o copiar a `nuevoMaterial17JUL/`.
3. Añadir receta en `curate_sf_special_sfx.py` → `build_recipes()`:
   - `add("FOO", "source_key", start|None, dur, "shout|horror|…", pitch, "nota")`
4. Si hace falta, mapear `source_key` en `prepare_sources()`.
5. `python …/curate_sf_special_sfx.py --install`
6. Confirmar `special_foo.ogg` en assets y pelear una ronda.
7. Actualizar **esta doc** y la tabla de 07 si el roster creció.

La View **no** requiere cambio de código si el id está en el enum (precarga `SfFighterId.entries`).

---

## 10. Gotchas (leer antes de tocar)

1. **Nombre de archivo = enum name lower** — no usar `displayName` ni `shortName`.
2. **YouTube 403 / audio mudo** — reintentar formato `bestaudio`, otro video, o clip X validado con RMS.
3. **No empaquetar MP3 largos en el APK** — solo el OGG corto; el scrapeo es offline.
4. **No mezclar música de persecución con special** — `prankedy-persecucion.mp3` es BGM.
5. **Pack ciego vs curate** — si alguien corre solo `pack_sf_character_sfx.py`, puede **pisar** la curación. Preferir siempre `curate_… --install` al final.
6. **Gama baja** — SoundPool sigue activo; los specials son ligeros (~10–15 KB). No desactivarlos en low-end.
7. **IA vs IA** — ambos emiten specials; maxStreams=8 evita drop de voces al solaparse.

---

## 11. Relación con otros docs

| Doc | Relación |
|-----|----------|
| `07_OTHER_FEATURES.md` | Resumen vivo del modo pelea + bullet de specials por personaje |
| `ASSETS_STREETFIGHTER_MIGRACION.md` | Checklist copyright assets; specials ya OK, golpes/land pendientes |
| `GUIA_generacion_assets_SF.md` | Generación de sprites; menciona claves de sonido del tema |
| `00_INDEX.md` | Índice: enlace a este archivo |
| `tools/sf_voice_scrape/README.md` | Comandos cortos del cache de tools |

---

## 12. Historial breve

| Fecha | Qué |
|-------|-----|
| 2026-07-18 | Scrapeo X profundo + locales; pack inicial 21 specials; cableado VM/View |
| 2026-07-18 | Ampliar a YouTube (yt-dlp); merge manifest; lección audio mudo en sections |
| 2026-07-18 | **Curación pelea** (`curate_sf_special_sfx.py`): estilos shout/horror/radio/… |
| 2026-07-18 | Verificación funcional **21/21**; este documento en README for IAS |

---

*Última actualización: 2026-07-18 — pipeline de voces de especial POW (HUELUM VS. GOYA).*
