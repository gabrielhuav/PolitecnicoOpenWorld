# Inventario de AUDIO de HUELUM VS. GOYA + reducción de tamaño AAB (2026-07-18r)

> Referencia rápida: qué audio tiene cada peleadór, cómo se reproduce, qué es global y qué se
> puede borrar. Script: `tools/sf_audio_review.sh` (inventario; `--mp3` convierte las voces a mp3
> para escucharlas). Todo vive en `app/src/main/assets/STREETFIGHTER/SOUNDS/`.

## ⚠️ El peso del AAB NO era el audio — eran los FONDOS (resuelto)

- **IMAGES 221 MB → 82 MB** re-encodeando los 48 atlas `fondo_*_anim.webp` de **webp LOSSLESS a
  LOSSY q90** (se ven prácticamente igual; los fondos toleran lossy). Total STREETFIGHTER
  235 MB → **97 MB** (~138 MB menos). **Esto es lo que desbloquea la subida a Play.**
  Si algún fondo se ve con artefactos, re-generar SOLO ese con el pipeline
  (`build_map_backgrounds.py`) o subir su calidad; el resto queda en q90.
- Audio (menor pero hecho): borrados **45 `amb_*.ogg`** (4.5 MB, ambiente NO usado por el juego)
  y `prankedy-persecucion.mp3` (2.2 MB, música redundante; `musicFile` ahora = `prankedy_lobby.mp3`).
  SOUNDS 21 MB → **14 MB**.

## Cómo se reproduce el audio (para saber dónde tocar)

- **SFX cortos** (golpear/impacto/land/hadouken): `SoundPool`, precargados por `theme.soundKeys`
  en la Screen. Son GLOBALES: los comparten TODOS los peleadores. **NO borrar.**
- **Voces/clips largos** (`special_*`): `MediaPlayer` vía `playSfSpecial` en la Screen. Se
  disparan emitiendo una clave `special_*` desde el VM. 🆕 (2026-07-18r) ahora **re-disparan**
  (si el mismo clip ya suena, se corta y arranca de nuevo — antes se ignoraba).
- **Música:** `MediaPlayer` aparte; lobby en el selector, batalla por dificultad (ver SfTheme
  `lobbyMusic`/`battleMusic`).

## GLOBALES (compartidos por todos — el dueño dijo que están bien)

`light-attack`, `medium-attack`, `heavy-attack` (golpear) · `light/medium/heavy-punch-hit`,
`light/medium/heavy-kick-hit` (recibir golpe) · `land` · `hadouken` (fallback de special sin voz).

## Voz por PELEADÓR

Fuente de verdad: `sfVoicePacks` (packs por evento con frase) + fallback `special_<id>.ogg`
(voz que suena en su PODER especial). Eventos: intro / attack / hurt / power / win.

**MAPEO FINAL (2026-07-18s)** — evento → archivo. Los `pol_*` llevan FRASE (subtítulo); el resto no.

| Peleadór (id) | Evento → archivo `.ogg` | Poder especial (fallback) |
|---|---|---|
| **Policía CDMX** (mujer, `POLICIA_CDMX`) | attack ×2 = `special_pol_m_attack_1/2` · win = `special_pol_m_win` | `special_policia_cdmx` |
| **Policía CDMX Hombre** (`POLICIA_CDMX_HOMBRE`) | intro = `special_pol_h_intro` · attack = `special_pol_h_attack` · win = `special_pol_h_win` | `special_policia_cdmx_hombre` |
| **Granadero Hombre** (`POLICIA_GRANADERO_HOMBRE`) | intro/attack = policía hombre · win = **diana** `special_granadero` | `special_policia_granadero_hombre` |
| **Granadera** (`POLICIA_GRANADERO_MUJER`) | attack = policía mujer · win = **diana** `special_granadero` | `special_policia_granadero_mujer` |
| **Granadero** (`GRANADERO`) | = Granadero Hombre · win = `special_granadero` | `special_granadero` |
| **Paparazzi 1** (`PAPARAZZI_1`) | power (su ataque ESPECIAL) = `special_paparazzi_5` · hurt ×3 = `special_papz1_hurt_1/2/3` | (usa el power) |
| **La Llorona** (`LA_LLORONA`) | power = `special_llorona_power` · attack = `special_llorona_attack` (seg 7-11 del fuente) · hurt = `special_llorona_hurt` | (usa el power) |
| **La Tzitzimime** (`LA_TZITZIMIME`) | attack (golpe normal) = `special_la_tzitzimime_attack` (90% de duración del original) | (no tiene; fallback hadouken) |
| **Rey Grupero** (`REY_GRUPERO`) | intro = `special_rey_grupero` | `special_rey_grupero` |
| **Paramédico Cruz Roja** (`PARAMEDICO_CRUZ_ROJA`) | win = `special_paramedico_cruz_roja` · power = `special_paramedico_cruz_roja_power` (electricidad a 1.5x) | (no tiene; fallback hadouken) |
| **Charro Negro** (`CHARRO_NEGRO`) | attack ×2 = `special_charro_attack_1/2` · hurt ×3 = `special_charro_hurt_1/2/3` | `special_charro_negro` |
| **Señor de la Tienda** (`SENOR_TIENDA`) | attack ×2 = `special_senor_tienda_attack_1/2` · hurt ×2 = `special_senor_tienda_hurt_1` / `_2` (puerquito) · win = `special_senor_tienda_win` | `special_senor_tienda` |
| **Robot** (`ROBOT`) | win = `special_robot_win` (del fuente `victory ROBOT.mp3`) | `special_robot` (su special) |
| **La Presidenta** (`LA_PRESIDENTA`) | win = `special_la_presidenta_win` (recortado el primer segundo) | (no tiene; fallback hadouken) |
| **Escomboy / Escomgirl** (`ESCOMBOY`/`ESCOMGIRL`) | (sin pack de voces) | `special_escomboy/girl` (efecto electricidad a 1.5x de velocidad) |
| Yoalli, ESCOMSTUDENT, Paparazzi 5 | (sin pack) | `special_<id>` (su special) |
| **Lázaro, Paramédico, Prankedy** | SIN VOZ (borrados) → su especial suena hadouken | — (en `sfVoicelessFighters`) |

**Naming:** 1 variante = `special_<key>_<evento>.ogg`; N variantes = `special_<key>_<evento>_<n>.ogg`.
Nuevo peleadór con voz: suelta los `.ogg` con ese naming + entrada en `sfVoicePacks` (VM). El
**special_<id>.ogg** también suena en el PODER especial (fallback) para packs sin `power`.

## ✅ POLICÍAS — verificado contra el fuente (2026-07-18t)

La confusión era por los NOMBRES del fuente (`nuevoMaterial18JUL/Audios/Policias/`), no por el
mapeo. Re-derivado del fuente y CONFIRMADO que coincide con lo pedido:
- **Hombre:** intro = "Intro Policia HOMBRE"; attack = "Policia HOMBRE Power" ("Buenas joven…");
  win = "Win Policia HOMBRE" (recortada 1ª mitad muda).
- **Mujer:** attack (2 frases) = "…GOLPEADA, DIVIDIR EN 2" ("Tu denuncia…" / "¿Sabes cuántas
  tengo?"); win = "Ataque MUJER POLICIA" ("Al decidir ser policía…").
- ⚠️ Ojo naming del fuente (a propósito, no es error): la "GOLPEADA" se usa como **ataque** y
  "Ataque MUJER" como **victoria** (swap que pidió el dueño). "Policia Mujer WIN.mp3" quedó
  **SIN usar** por ese swap. Los `special_policia_*.ogg` (272 KB los granaderos) solo son
  fallback del poder especial → recortables si urge tamaño.

## 🆕 Grito de ataque MASCULINO por defecto (2026-07-18u)

Sonido de ataque NORMAL (no especial) que suena AL AZAR cuando un peleadór **HOMBRE** golpea y
**NO tiene voz de ataque propia** (`sfMaleFighters` en el VM; excluye Robot y las mujeres).
Probabilidad por rol: **enemigo (índice 1) 35 %**, **jugador (índice 0) 10 %** (para no saturar
tu propia voz). Archivo: **`special_male_attack_grunt.ogg`**.

✅ **RESUELTO (2026-07-19):** el fuente **"ZA ZA.mp3" (17 s)** fue proveído por el dueño; se recortó el **seg 8-10** (2 s) y se guardó como `special_male_attack_grunt.ogg` en `SOUNDS/`.

## Cambios de audio 2026-07-19

- **Señor de la Tienda (pack COMPLETO):** attack ×2 (`special_senor_tienda_attack_1/2`), hurt ×2 (`special_senor_tienda_hurt_1` y `_2` ("puerquito" de `Puerquito.mp3`)), y win (`special_senor_tienda_win`).
- **Grito masculino por defecto:** recortado seg 8-10 de `ZA ZA.mp3` y guardado como `special_male_attack_grunt.ogg` en `SOUNDS/`.
- **Escomboy y Escomgirl:** sus especiales de voz se eliminan y se reemplazan por un efecto de sonido de electricidad (`Electricity Blast...mp3`) acelerado a 1.5x de velocidad.
- **Robot:** se agrega su audio de victoria `special_robot_win.ogg` (`victory ROBOT.mp3`).
- **La Presidenta:** se elimina su especial de voz y se convierte en su audio de victoria `special_la_presidenta_win.ogg` recortando el primer segundo de silencio.
- **La Tzitzimime:** se recorta el último 10% del audio original de su especial (duración 2.25s) y se convierte exclusivamente en su sonido de golpe normal (`special_la_tzitzimime_attack.ogg`).
- **Paramédico Cruz Roja:** su especial de voz ahora se convierte en su audio de victoria con subtítulo y su especial de poder es el efecto de electricidad acelerado a 1.5x (`special_paramedico_cruz_roja_power.ogg`).
- **Policía CDMX Mujer:** se recortó la pista de ataque `special_pol_m_attack_1.ogg` para durar únicamente los primeros 2.3 segundos.
- **Interrupción de Sonidos al Ser Golpeado:** se modificó el reproductor de audios y el SoundPool en `StreetFighterScreen.kt` para que no se traslapen/repitan sonidos de golpes ni las voces de daño del mismo personaje. Si ya se están reproduciendo, se interrumpen y reinician desde el principio inmediatamente.
- `tools/_audio_review/` regenerado completamente con las conversiones a mp3 de todos los cambios.

## Cambios de audio 2026-07-18u

- **Charro Negro: pack** attack ×2 (`special_charro_attack_1/2`, del fuente 0-4 y 3-7) + hurt ×3
  (`special_charro_hurt_1/2/3`, tercios de 3 s). Su `special_charro_negro` sigue como fallback del poder.
- **Grito masculino por defecto** (arriba) — cableado; falta subir el .ogg del "ZA ZA".
- `tools/_audio_review/` regenerado con TODAS las voces actuales en mp3 (para escuchar).

## Cambios de audio 2026-07-18t

- **La Llorona: pack COMPLETO** con audios dedicados del dueño: `special_llorona_power` (Special),
  `special_llorona_attack` (recorte seg 7-11 del fuente de 11 s), `special_llorona_hurt`. Se borró
  el viejo `special_la_llorona.ogg` (reemplazado).
- **Policías verificados** contra el fuente (ver sección arriba): quedan como estaban, correctos.

## Cambios de audio 2026-07-18s (para el empaquetado)

BORRADOS: `special_gr_win` (granadero win usa `special_granadero`), `special_lazaro`,
`special_paramedico`, `special_prankedy`, `special_paparazzi_1` (dup de `_5`),
`special_papz1_attack_1/2`. REDERIVADOS: `special_papz1_hurt_3` = últimos 3 s del viejo
`papz1_attack_2` (daño). REASIGNADOS a evento: Llorona/Tzitzimime → attack; Rey Grupero → intro;
Paramédico CR → win; Paparazzi 1 power → `special_paparazzi_5`; granadero win → `special_granadero`.

## Qué se puede BORRAR para bajar tamaño

- ✅ `amb_*.ogg` (45, 4.5 MB) — nunca los carga el juego.
- ✅ `prankedy-persecucion.mp3` (2.2 MB) — redundante con lobby/battle.
- ✅ `special_gr_win`, `special_lazaro`, `special_paramedico`, `special_prankedy`,
  `special_paparazzi_1`, `special_papz1_attack_1/2` (2026-07-18s).
- Candidatos si urge más: recortar `special_granadero*.ogg` / `special_policia_granadero_*.ogg`
  (272 KB c/u, 27.5 s → la diana no necesita tanto); `prankedy_battle_*.mp3` a 80 kbps.
- **El grueso REAL era IMAGES** (atlas de fondo): ya bajados de LOSSLESS a lossy q90 (221→82 MB).
  Si aún no cabe el AAB, bajar los atlas a q85 o reducir cuadros por atlas.
