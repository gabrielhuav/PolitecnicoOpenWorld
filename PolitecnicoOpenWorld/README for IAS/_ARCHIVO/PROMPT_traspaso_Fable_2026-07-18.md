# PROMPT DE TRASPASO → Fable 5 — IA de pelea + Showcase de assets (HUELUM VS. GOYA)

> Pega **desde la sección «PROMPT»** como primer mensaje de la nueva conversación.
> Estado vivo también en 00/07/DISENO. Este archivo es el original archivado.

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

## Reglas (OBLIGATORIAS)
- MVVM ESTRICTO. Estado inmutable: _state.update { it.copy(...) }. Las Views solo observan y
  emiten intenciones; nunca tocan repos/DAOs.
- Comentarios/strings de UI en ESPAÑOL; strings en res/values y values-en con PARIDAD ES+EN.
- Gotcha miembro-vs-extensión: gana el MIEMBRO; edita el miembro y verifica ambos.
- Conserva CRLF en los .kt; verifica balance de llaves/paréntesis por archivo. No compilas:
  deja listo para "Rebuild". No toques red/online salvo que rompa offline. No regeneres assets.

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
  startGauntletArcade (escalera en orden rotando peleador) y startShowcase (recorre por script
  las animaciones+sonidos de cada peleador). maybeAdvanceGauntlet encadena peleas (tope 60 s),
  finishGauntlet escribe un .txt en getExternalFilesDir y muestra GauntletReportOverlay.
- CharacterSelectOverlay: botón CONFIRMAR (string sf_confirm).

## TU MISIÓN (lo que el dueño reporta que sigue mal)
1) ARCADE IA vs IA (y IA vs IA en general): los peleadores se QUEDAN QUIETOS / se traban /
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

## Archivos clave (léelos con tools; NO inventes)
- features/streetfighter/viewmodel/StreetFighterViewModel.kt
  (buildCpuInput, smartCpuDecision, normalCpuDecision, basicCpuDecision, cpuClinchBreak,
   cpuMoveTowardFlags/cpuRetreatFlags, watchStuck, watchStalemate, showcaseInput,
   startGauntlet*/startShowcase/maybeAdvanceGauntlet/finishGauntlet, tick, updateFighter,
   changeState, isAnimationCompleted, mustCompleteStates)
- features/streetfighter/viewmodel/StreetFighterState.kt (aiVsAi, gauntlet*, arcade*)
- domain/models/streetfighter/SfModels.kt (SfCpuDifficulty, SfFighterState, SfFighterId)
- domain/models/streetfighter/SfArcadeLadder.kt (ALL_PARTICIPANTS, Step.rival, build)
- features/streetfighter/data/SfFrameCatalog.kt (cómo se cargan animaciones por peleador)
- ui/StreetFighterScreen.kt (SfModeMenuOverlay, CharacterSelectOverlay, GauntletReportOverlay)

## Entregable
- Diffs en VM (y Screen/State/strings solo si hace falta), listos para Rebuild.
- Resumen: causa del "quietos/mismo ataque" + qué mejoraste; cobertura nueva del showcase.
- Cómo probar: menú de modos → Autojuego/Showcase; y arcade IA vs IA con 3-4 pares.
- Al terminar: protocolo docs 09 (07 §HUELUM + DISENO). Verifica CRLF, llaves, gotcha miembro-ext.

Empieza leyendo buildCpuInput + smartCpuDecision + cpuClinchBreak (para lo de "mismo ataque"),
y showcaseInput + SfFrameCatalog (para cubrir todas las animaciones/sonidos).
```

---

## Notas para el humano (no pegar a Fable)
- StreetFighterViewModel.kt es el archivo grande (~3k líneas). Si Fable pide un .kt, dáselo.
- Tras su fix: Rebuild + prueba en dispositivo. El .txt del showcase queda en la carpeta de
  archivos de la app (Android/data/<pkg>/files/); también sale en Logcat con el tag SF-DIAG.
