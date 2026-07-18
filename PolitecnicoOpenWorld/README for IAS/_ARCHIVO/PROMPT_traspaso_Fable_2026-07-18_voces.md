# PROMPT DE TRASPASO → Fable — PRIORIDAD: IA pelea + Showcase; luego contexto voces

> Pega **desde la sección «PROMPT»** como primer mensaje.
> **La misión de pelea (quietos / mismo ataque / showcase) va PRIMERO y es la prioridad.**
> El bloque de voces es **contexto de fondo** (no re-scrapear; no deepfake inventado).
> Docs vivos: 00, 07 §HUELUM, DISENO, 09, **SF_SPECIAL_VOICES_SFX.md**.

---

## PROMPT (copiar desde aquí)

```
Estás en el repo Android "Politécnico Open World" (POW). Rama reciente:
feat/new-routes-for-NPCs-and-actions (o la que esté checked out).

## Rutas
- Android: PolitecnicoOpenWorld/PolitecnicoOpenWorld/
- Contexto IAs (LEE PRIMERO): PolitecnicoOpenWorld/PolitecnicoOpenWorld/README for IAS/
  - 00_INDEX.md, 07_OTHER_FEATURES.md §HUELUM VS. GOYA, DISENO_ARCADE_SF_POW.md,
    09_CONVENTIONS_GOTCHAS.md (MVVM, docs, CRLF).
  - Voces (solo referencia, no es la prioridad): SF_SPECIAL_VOICES_SFX.md

## Reglas (OBLIGATORIAS)
- MVVM ESTRICTO. Estado inmutable: _state.update { it.copy(...) }. Las Views solo observan y
  emiten intenciones; nunca tocan repos/DAOs.
- Comentarios/strings de UI en ESPAÑOL; strings en res/values y values-en con PARIDAD ES+EN.
- Gotcha miembro-vs-extensión: gana el MIEMBRO; edita el miembro y verifica ambos.
- Conserva CRLF en los .kt; verifica balance de llaves/paréntesis por archivo. No compilas:
  deja listo para "Rebuild". No toques red/online salvo que rompa offline. No regeneres assets
  (sprites). No borres raw_yt/ ni out_diarized/ de voces.

## Contexto: qué YA se hizo (no lo deshagas)
Todo en features/streetfighter/. Cambios recientes (Claude, 2026-07-18):
- isAnimationCompleted: una animación también se da por terminada al llegar al ÚLTIMO frame
  (no solo con el frame -1) → arregla peleadores atascados con hojas ALPHA/compartidas.
- Watchdog ofensivo de la CPU (buildCpuInput): a cualquier distancia, si no hay ofensiva, fuerza
  acercarse (lejos) o atacar/clinch (cerca).
- watchStuck (updateFighter): desatasca estados transitorios cuya animación no termina y lo
  registra. watchStalemate (tick): detecta peleas sin daño >12 s y pica a la IA.
  logAssetIssue → logcat tag "SF-DIAG" + diagnosticsReport().
- AUTOJUEGO (gauntlet) en el menú de modos: startGauntletRoundRobin (todos vs todos),
  startGauntletArcade (escalera en orden rotando peleadór) y startShowcase (recorre por script
  las animaciones+sonidos de cada peleadór). maybeAdvanceGauntlet encadena peleas (tope 60 s),
  finishGauntlet escribe un .txt en getExternalFilesDir y muestra GauntletReportOverlay.
- CharacterSelectOverlay: botón CONFIRMAR (string sf_confirm).
- IA 18i: clinch break, smartCpuDecision, movimiento world-space (aún puede fallar en device).

###############################################################################
## PRIORIDAD ABSOLUTA — TU MISIÓN (lo que el dueño reporta que sigue mal)
###############################################################################

1) ARCADE IA vs IA (y IA vs IA en general): los peleadors se QUEDAN QUIETOS / se traban /
   repiten el MISMO ataque. Hay que lograr pelea real y VARIADA:
   - variar golpes/patadas y specials (no repetir el mismo), mezclar rangos, saltos, castigos;
   - romper de verdad el clinch (a veces quedan pegados sin separarse ni golpear);
   - garantizar que NUNCA se queden >~2 s sin acción a rango de pelea.
   Toca: buildCpuInput + smartCpuDecision/normalCpuDecision + cpuClinchBreak + watchdog en el VM.

2) SHOWCASE completo (es lo que más le interesa al dueño): que cada personaje recorra TODAS sus
   animaciones y reproduzca TODOS sus sonidos, para ver/oír que los assets están bien:
   - hoy showcaseInput (VM) solo cubre lo alcanzable por input (caminar, saltar, agachar, 6
     golpes, special L/M/F, poderes). FALTA cubrir HURT_*, KO, IDLE_TURN/CROUCH_TURN, VICTORY,
     y la metamorfosis de La Presidenta.
   - añade una AUDITORÍA ESTÁTICA por personaje: recorre las claves de animación esperadas
     (SfFighterState / los jsKey del JSON) y registra las que FALTEN o estén vacías; y verifica
     la existencia de TODOS los .ogg/SFX del personaje (no solo special_<id>.ogg).
   - deja el reporte claro (personaje + qué animación/sonido falta) en el .txt y en pantalla.

3) Verifica en dispositivo (emulador no obligatorio) y afina la sensación de la IA con la
   grabación del showcase / IA vs IA (no a ciegas).

## Archivos clave pelea (léelos con tools; NO inventes)
- features/streetfighter/viewmodel/StreetFighterViewModel.kt
  (buildCpuInput, smartCpuDecision, normalCpuDecision, basicCpuDecision, cpuClinchBreak,
   cpuMoveTowardFlags/cpuRetreatFlags, watchStuck, watchStalemate, showcaseInput,
   startGauntlet*/startShowcase/maybeAdvanceGauntlet/finishGauntlet, tick, updateFighter,
   changeState, isAnimationCompleted, mustCompleteStates)
- features/streetfighter/viewmodel/StreetFighterState.kt (aiVsAi, gauntlet*, arcade*)
- domain/models/streetfighter/SfModels.kt (SfCpuDifficulty, SfFighterState, SfFighterId)
- domain/models/streetfighter/SfArcadeLadder.kt (ALL_PARTICIPANTS, Step.rival, build)
- features/streetfighter/data/SfFrameCatalog.kt (cómo se cargan animaciones por peleadór)
- ui/StreetFighterScreen.kt (SfModeMenuOverlay, CharacterSelectOverlay, GauntletReportOverlay)

## Entregable (misión pelea)
- Diffs en VM (y Screen/State/strings solo si hace falta), listos para Rebuild.
- Resumen: causa del "quietos/mismo ataque" + qué mejoraste; cobertura nueva del showcase.
- Cómo probar: menú de modos → Autojuego/Showcase; y arcade IA vs IA con 3-4 pares.
- Al terminar: protocolo docs 09 (07 §HUELUM + DISENO). Verifica CRLF, llaves, gotcha miembro-ext.

Empieza leyendo buildCpuInput + smartCpuDecision + cpuClinchBreak (para lo de "mismo ataque"),
y showcaseInput + SfFrameCatalog (para cubrir todas las animaciones/sonidos).

###############################################################################
## CONTEXTO SECUNDARIO — VOCES / SFX special (NO es la prioridad de esta sesión)
###############################################################################
# Solo para no borrar trabajo previo ni re-scrapear YouTube de cero.
# Deepfake/voice lab avanzado: NO implementado. No digas que está hecho.
# Detalle completo: README for IAS/SF_SPECIAL_VOICES_SFX.md

### Código voces ya cableado (no deshacer)
- SfSpecialPhrases.kt + assets/STREETFIGHTER/DATA/special_phrases.json (ES/EN/HUD)
- VM emitSpecialVoice en SPECIAL_1_*, BONUS_POWER_*, meta Presidenta; Lázaro → hadouken
- State specialSubtitleHud / specialSubtitleUntilMs; Screen drawFontText subtítulo

### Dónde están las fuentes y specials NUEVOS (máquina del dueño)
- RAW YT: PolitecnicoOpenWorld/tools/sf_voice_scrape/raw_yt/
- Specials diarizados (aún NO necesariamente en assets):
  tools/sf_voice_scrape/out_diarized/<FIGHTER>/special_*.ogg + meta.json
- Review MP3: tools/sf_voice_scrape/REVIEW_MP3_TODOS/
- Catálogo editable: tools/sf_voice_scrape/special_phrases_catalog.json
- Tools: diarize_special_voices.py, process_charro_nahual.py, process_paparazzi5.py,
  process_generic_sfx.py, build_special_phrases_pack.py

### Links YT + archivos YA descargados (no re-bajar si el archivo existe)

| Peleador | YouTube | Archivo local |
|----------|---------|---------------|
| SENOR_TIENDA + PRANKEDY | https://www.youtube.com/watch?v=FdUblky8bV4 | raw_yt/TIENDA_bestof_FdUblky8bV4.wav (~66MB). Diarize: rank0=Prankedy, rank1=Señor |
| REY_GRUPERO | https://www.youtube.com/watch?v=VPu6zFKcb7Y | raw_yt/REY_GRUPERO_VPu6zFKcb7Y.wav |
| CHARRO_NEGRO | https://www.youtube.com/watch?v=0bYg6FmCjhE | raw_yt/CHARRO_NEGRO_0bYg6FmCjhE.wav |
| CHARRO_NEGRO | https://www.youtube.com/watch?v=xlYlM4WgqYU | raw_yt/CHARRO_NEGRO_xlYlM4WgqYU.wav |
| CHARRO_NEGRO (nahual) | https://www.youtube.com/watch?v=DK5ZgKoM8Xw | raw_yt/CHARRO_NEGRO_DK5ZgKoM8Xw.wav (pick actual) |
| GRANADEROS (×3 ids) | https://www.youtube.com/watch?v=5fN8KeZ2JLA | raw_yt/GRANADEROS_3_diana_5fN8KeZ2JLA.wav (3 de diana; solo granaderos) |
| POLICIA_CDMX / _HOMBRE | https://www.youtube.com/watch?v=ASgdaRKHon8 | raw_yt/POLICIA_CDMX_ASgdaRKHon8.wav (♀ pitch↑ / ♂ pitch↓) |
| PARAMEDICO_CRUZ_ROJA | https://www.youtube.com/watch?v=l89qiD3aqVQ | raw_yt/PARAMEDICO_CRUZ_ROJA_l89qiD3aqVQ.wav — solo audio t≥60s |
| YOALLI (SFX genérico) | Ye4PRm-kebc, d1M7uqfsofs | raw_yt/YOALLI_EHECATL_*.wav; frase inventada |
| TZITZIMIME | genérico SFX | frase inventada; no scrape de diálogo |
| PAPARAZZI_1 | local | nuevoMaterial17JUL/PAPARAZZI 1!! BROMA.mp3 |
| PAPARAZZI_5 | https://www.youtube.com/watch?v=AEmVeK88HIs | **PENDIENTE age-gate**. Drop: nuevoMaterial17JUL/PAPARAZZI_5_AEmVeK88HIs.mp3 → process_paparazzi5.py |

### Si el showcase audita sonidos
- Comprueba special_<id>.ogg en assets/STREETFIGHTER/SOUNDS/ (pack APK actual).
- Opcional: contrastar con out_diarized/ (generación nueva no instalada hasta OK del dueño).
- NO re-scrapees YouTube ni borres raw_yt. Deepfake lab: NO hecho.

### Orden de trabajo recomendado
1) PRIMERO misión pelea (IA + showcase + auditoría anim/SFX).
2) Solo si sobra tiempo / dueño pide: instalar out_diarized → assets o Pap5 manual.
```

---

## Notas para el humano
- Misión pelea pura (sin bloque voces): `PROMPT_traspaso_IA_CPU_2026-07-18.md`
- Este archivo = **mismo prompt de pelea primero** + apéndice voces para no perder scrape.
- Doc vivo de voces: `../SF_SPECIAL_VOICES_SFX.md`
