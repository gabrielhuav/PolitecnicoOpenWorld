# PROMPT DE TRASPASO → Gemini 3.5 Flash (2026-07-18) — audio, empaquetado y pendientes

Estás ayudándome con "Politécnico Open World" (POW), un juego Android 2D top-down sobre mapas
reales (Kotlin + Jetpack Compose + MVVM estricto). La carpeta "README for IAS" es el contexto
COMPLETO del proyecto y reemplaza al código. El proyecto está en
"C:\Users\gabri\Documents\GitHub Desktop\PolitecnicoOpenWorld".

## Cómo trabajar (OBLIGATORIO)

- Lee primero: `README for IAS/00_INDEX.md`, `09_CONVENTIONS_GOTCHAS.md` (MVVM, CRLF, gotcha
  miembro-vs-extensión, protocolo de docs), y para lo de AUDIO: **`AUDIO_INVENTARIO_SF.md`**
  (mapa completo de voces por peleadór + guía) y `DISENO_ARCADE_SF_POW.md` (changelog 18j–18u).
- MVVM estricto: estado inmutable `_state.update { it.copy(...) }`; las Views solo observan.
- Strings de UI en español con PARIDAD ES+EN (`res/values` y `values-en`). Conserva CRLF en .kt.
- No puedes compilar; deja listo para "Rebuild" en Android Studio. Verifica con Read (no bash:
  el bash del sandbox puede servir copias truncadas de archivos recién editados).
- Producción: PR a `main` dispara el CI/CD (AAB firmado → Play alpha). Ver
  `pow-production-cicd-flow`. El código NUEVO debe pasar detekt (gate del PR).

## Sistema de VOCES (lo que se ha estado haciendo)

Vive en `features/streetfighter/viewmodel/StreetFighterViewModel.kt`:
- `SfVoiceLine(file, phrase)` + `SfVoicePack(intro, attack, hurt, power, win)` (listas de líneas).
- `sfVoicePacks: Map<SfFighterId, SfVoicePack>` = qué audio suena en cada evento por peleadór.
- Se reproducen por la ruta `special_*` (MediaPlayer en la Screen, `playSfSpecial`, apto para
  clips largos; 2026-07-18r RE-DISPARA si el mismo clip ya suena).
- Eventos y hooks: POWER=`emitSpecialVoice` (special/bonus) · WIN=`emitWinVoice` (VICTORY) ·
  HURT=`emitHurtVoice` (`applyAttackHit`, cooldown 2.6 s) · ATTACK=`emitAttackVoice`
  (`changeState` al golpear, cooldown 4.2 s) · INTRO=`emitIntroVoice` (1×/ronda en `tick`).
- Subtítulos: `setVoiceSubtitle` + `sfHudSanitize` (A-Z/0-9); render MULTILÍNEA (máx 3) + fuente
  chica en `StreetFighterScreen.kt` (bloque "Subtítulo del special").
- **Naming de archivos:** 1 variante = `special_<key>_<evento>.ogg`; N = `special_<key>_<evento>_<n>.ogg`.
  Para dar voz a un peleadór: suelta los `.ogg` en `assets/STREETFIGHTER/SOUNDS/` + entrada en
  `sfVoicePacks`. La auditoría del showcase (`auditFighterAssets`) valida que existan.
- `sfVoicelessFighters` = {Lázaro, Paramédico, Prankedy}: sin voz a propósito (especial=hadouken).
- Procesar audio: ffmpeg. Convención usada: `-c:a libvorbis -b:a 96k -ac 2`. Recortes con
  `-ss <inicio> -t <duración>`. Fuentes del dueño en `nuevoMaterial18JUL/Audios/`.
- Revisar audios: `tools/sf_audio_review.sh` (inventario; `--mp3` convierte a mp3 en
  `tools/_audio_review/`, que está en `.gitignore`).

## PENDIENTES DE AUDIO (lo que falta AHORA)

1. **🔴 Grito de ataque masculino por defecto — FALTA EL .ogg.** El sistema ya está cableado
   (`emitAttackVoice` → `maleGruntClip = "special_male_attack_grunt"`; `sfMaleFighters`; enemigo
   35 %, jugador 10 %). El fuente **"ZA ZA.mp3" (17 s) NO estaba** en la carpeta. Cuando el dueño
   lo suba: recortar **seg 8-10** (2 s) → `special_male_attack_grunt.ogg` en `SOUNDS/`. Suena solo
   para HOMBRES sin voz de ataque propia.
2. **🟡 Policías — verificar contenido.** El mapeo (attack/intro/win por género) quedó cableado y
   re-derivado del fuente; el dueño tenía duda porque los NOMBRES del fuente engañan (ver
   `AUDIO_INVENTARIO_SF.md` §POLICÍAS). Si al escuchar hay que corregir, es cambiar la línea del
   peleadór en `sfVoicePacks`.
3. **🟢 Más voces por peleadór** conforme el dueño suba audios (mismo patrón: procesar → pack → doc).

## OTROS PENDIENTES (no audio)

- **Panorámico de mapas:** `SF_BG_FRAMING` ya aplica a los 16 (zoom 1.30, FacMed 1.35). Falta el
  AFINADO por mapa en dispositivo (ver `PROMPT_panoramico_todos_los_mapas.md`).
- **QA fondos** (`QA_SF_STAGES_2026-07-18.md`): sombra Isla de las Muñecas (plataforma), video de
  día de FES Acatlán, y confirmar subtítulos multilínea en dispositivo.
- **Tamaño AAB (resuelto):** los atlas de fondo se pasaron de webp lossless a lossy q90
  (IMAGES 221→82 MB). Si aún no cabe, bajar a q85. NO re-inflar a lossless.

## Al terminar CUALQUIER cambio

- Protocolo docs 09: actualiza `AUDIO_INVENTARIO_SF.md` (mapa de voces) y/o `DISENO_ARCADE_SF_POW.md`
  (changelog), y `README.md` bilingüe (EN+ES) si es user-facing. Verifica CRLF y balance de llaves.
- Deja listo para Rebuild; ofrece commit/PR a `main` (producción).

Estilo: respuestas concisas y directas, sin relleno.
