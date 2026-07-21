# TRASPASO · Audio y subtítulos de HUELUM VS. GOYA (2026-07-21)

> Pégale esto completo a la sesión nueva. Trabaja **LOCALMENTE** en Windows/PowerShell,
> con acceso de escritura a git. Nada de nube ni contenedores.

## Rutas

| Qué | Dónde |
|---|---|
| Repo raíz | `C:\Users\gabri\Documents\GitHub Desktop\PolitecnicoOpenWorld` |
| Proyecto Android (aquí está `.\gradlew.bat` y `tools\`) | `...\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| OGG que lee el juego | `app\src\main\assets\STREETFIGHTER\SOUNDS\` |
| MP3 para escuchar en PC | `tools\_audio_review\` |
| **Audit completo (LÉELO PRIMERO)** | `README for IAS\AUDIT_VOCES_SUBTITULOS.md` |
| Hoja de trabajo del dueño | `tools\_audio_review\_SUBTITULOS_AUDIT.csv` |
| Subtítulo por clip | `app\src\main\assets\STREETFIGHTER\DATA\voice_phrases.json` |
| Subtítulo del special | `app\src\main\assets\STREETFIGHTER\DATA\special_phrases.json` |
| detekt | `..\detekt-cli-1.23.8\bin\detekt-cli.bat` |

Convenciones obligatorias: `README for IAS\09_CONVENTIONS_GOTCHAS.md`
(MVVM, estado inmutable `_state.update { it.copy(...) }`, **strings UI en ES+EN con
paridad `values`/`values-en`**, **CRLF en `.kt`**).

---

## ⚠️ LAS 4 TRAMPAS (leer antes de ejecutar nada)

**1. `sf_audio_audit.py` SOLO se ejecuta con el Python del venv.**

```bash
..\.codex-tmp\pow-audio-venv\Scripts\python.exe tools\sf_audio_audit.py
```

El Python del sistema **no tiene `faster-whisper`**. Si lo corres con el del sistema, la
columna de transcripción del report sale VACÍA, y como
`build_voice_phrases_catalog.py` **saca los drafts DE ESE REPORT**, la siguiente
regeneración **borra los 53 drafts** ya obtenidos. Verificado: el venv sí lo tiene
(`faster_whisper OK`, Python 3.12.13).

**2. `tools\_audio_review\` está en `.gitignore`.** El CSV con las correcciones del dueño
**no viaja por git**. En cuanto el dueño llene una tanda, vuélcala YA a
`voice_phrases.json` (ese sí se versiona) con `tools\sf_apply_subtitle_fixes.py`, o se
pierde al cambiar de máquina.

**3. `voiceSubtitlesEnabled = false`** en `StreetFighterViewModel.kt`. **NO lo pongas en
`true`** hasta que haya paridad ES+EN completa en `voice_phrases.json`. Ahora mismo hay
7 `es` y 7 `en` de 69 clips.

**4. `audioKey` de `special_phrases.json` es un campo MUERTO** (se parsea en
`SfSpecialPhrases.kt:89` pero no lo lee nadie). Por eso las 5 referencias a OGG
inexistentes (`special_prankedy.ogg`, `special_lazaro.ogg`, `special_paramedico.ogg`,
`special_paparazzi_1.ogg`, `special_policia_cdmx_hombre.ogg`) **no rompen nada** —
esos audios se borraron a propósito. No "arregles" eso borrando peleadores del JSON:
`startAudioShowcase` filtra por `it in specialPhrases` y cambiarías el showcase.
`subtitleMs` sí se usa (ritmo del showcase, `StreetFighterViewModel.kt:500`).

---

## Estado real (medido, no asumido)

80 OGG = **69 con voz** + **11 SFX globales** (`hadouken`, `light-punch-hit`, `land`…,
que NO llevan subtítulo y van a propósito por debajo de −16 LUFS para no tapar la voz).

| Métrica | Valor |
|---|---|
| Clips con voz | 69 |
| Con subtítulo `es` curado | **7** |
| Sin subtítulo `es` | **62** |
| Con draft de Whisper para curar | 53 |
| Sin draft (probable grito → `-`) | 16 |
| Demasiado largos para su evento | **29** |
| Fuera de −16 ±2 LUFS | **5** |

> Un reporte anterior afirmaba "100% normalizados a −16 LUFS y sin faltantes". **Es falso**:
> hay 5 voces fuera de rango y 7 peleadores sin `attack` o `hurt`. Mide, no asumas.

---

## TAREAS

### A. Recortar los 29 clips largos

Un grito de golpe de 4 s se solapa con el siguiente ataque. Límite: `attack`/`hurt` ≤ 2.5 s,
`lowHp` ≤ 3 s. Los eventos narrativos (`intro`/`win`/`loss`/`power`) sí pueden ser largos.

Los peores: `special_escomboy_attack` (**10.70 s**), `special_prankedy_lowhp` (8.60 s),
`special_papz1_hurt_1` (7.00 s), `special_prankedy_attack_1` (6.91 s),
`special_prankedy_attack_4` (5.30 s), `special_charro_attack_3` (5.10 s).
**Lista completa con duración exacta: `README for IAS\AUDIT_VOCES_SUBTITULOS.md`.**

⚠️ **No recortes por tu cuenta**: el dueño dice qué segundo conservar. Precedente válido:
`special_pol_m_attack` se partió en `_1` (seg 0-1) y `_2` (seg 1-3).

### B. Normalizar 5 clips a −16 LUFS

`special_la_tzitzimime_hurt` (−12.5), `special_charro_attack_1` (−13.2),
`special_escomgirl_hurt` (−13.8), `special_charro_attack_2` (−18.8),
`special_rey_grupero` (−19.6).

### C. Audio que FALTA GRABAR

Eventos básicos ausentes (se notan en cada pelea):

| Peleador | Falta |
|---|---|
| `PARAMEDICO_CRUZ_ROJA` | `attack` **y** `hurt` |
| `PAPARAZZI_1` | `attack` |
| `ESCOMBOY` | `hurt` |
| `GRANADERO` | `hurt` |
| `POLICIA_CDMX_HOMBRE` | `hurt` |
| `POLICIA_GRANADERO_HOMBRE` | `hurt` |

Opcionales (mejoran, no bloquean): `LA_LLORONA` no tiene `win`/`loss`/`intro`;
`CHARRO_NEGRO`, `PAPARAZZI_5` y `YOALLI_EHECATL` no tienen `win`.

Convención de nombre: `special_<peleador>_<evento>[_<n>].ogg` en `SOUNDS\`. Al añadir uno,
**hay que mapearlo** en `sfVoicePacks` (`StreetFighterViewModel.kt`, ~línea 130-285) o no
sonará nunca.

### D. Curar los 62 subtítulos (requiere el OÍDO del dueño)

Bucle de trabajo:

```bash
python tools\sf_voice_subtitle_audit.py
```

→ regenera el CSV y `AUDIT_VOCES_SUBTITULOS.md`. El dueño escucha
`tools\_audio_review\<clip>.mp3` y rellena SOLO `ES_CORREGIDO` / `EN_CORREGIDO` / `NOTAS`.
Un `-` significa "este clip no lleva subtítulo" (grito puro). Luego:

```bash
python tools\sf_apply_subtitle_fixes.py --dry-run
```

y sin `--dry-run` para aplicar. **Nunca copies el `draft` de Whisper al campo `es`**: es
referencia y está lleno de alucinaciones (el propio catálogo filtra "amara.org",
"suscríbete"…). Solo vale lo que confirme el dueño de oído.

---

## Ya hecho (no rehacer)

- **Bug de i18n corregido**: `emitSpecialVoice` usaba `phrase.phraseEs` fijo, así que
  `phrase_en` no se mostraba nunca ni con el juego en inglés. Ahora usa
  `textForLang(Locale.getDefault().language)`, igual que `emitVoiceLines`.
- **Paridad ES+EN** de los 7 clips ya curados (es=7, en=7).
- **Herramientas nuevas**: `tools\sf_voice_subtitle_audit.py` (mide y cruza, SOLO LECTURA:
  no toca el report ni los drafts) y `tools\sf_apply_subtitle_fixes.py`.

## Verificación antes de cerrar

```bash
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

```bash
..\detekt-cli-1.23.8\bin\detekt-cli.bat --config "config\detekt\detekt.yml" --input "app\src\main\java"
```

⚠️ detekt tiene **5 smells PREEXISTENTES** (`CachingWebViewClient`, `NpcAiManager`,
`RoadRouter`, `CatSpriteManager` ×2). Varios docs afirman "0 smells" y **es falso**. No los
cuentes como tuyos, pero tampoco repitas la afirmación.
**NO** uses `--build-upon-default-config`: sube el conteo a 16 porque añade reglas que el
repo no adoptó.

Al terminar, actualiza `README for IAS\00_INDEX.md`, `07_OTHER_FEATURES.md` y
`AUDIO_INVENTARIO_SF.md`, y confirma que `git status` solo muestre lo que tocaste a propósito.
