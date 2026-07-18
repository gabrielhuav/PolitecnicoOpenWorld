# PROMPT DE TRASPASO → Claude 4.8 — Arreglar y MEJORAR la IA de pelea

> Pega **desde la sección «PROMPT»** (abajo) como primer mensaje en la nueva conversación Claude.
> Este archivo es el original archivado; el estado vivo también está en 00/07/DISENO/SF_STAGES.

---

## PROMPT (copiar desde aquí)

```
Estás en el repo Android "Politécnico Open World" (POW). Rama de trabajo reciente: feat/new-routes-for-NPCs-and-actions (o la que esté checked out).

## Rutas
- Repo: carpeta PolitecnicoOpenWorld (GitHub Desktop o similar del usuario)
- Android: PolitecnicoOpenWorld/PolitecnicoOpenWorld/
- Contexto IAs (LEE PRIMERO): PolitecnicoOpenWorld/PolitecnicoOpenWorld/README for IAS/
  - 00_INDEX.md (estado vivo)
  - 07_OTHER_FEATURES.md §HUELUM VS. GOYA
  - DISENO_ARCADE_SF_POW.md
  - SF_STAGES_MAPS_UNLOCK.md (mapas + desbloqueos — NO es tu foco principal)
  - 09_CONVENTIONS_GOTCHAS.md (MVVM, docs, CRLF)

## Tu ÚNICA prioridad esta sesión
ARREGLAR y MEJORAR la IA del minijuego de pelea "HUELUM VS. GOYA" (código streetfighter).

El dueño reporta un DOWNGRADE: tras cambios anti-spam de proyectiles / clamp / IA vs IA:
1) En ARCADE a veces el JUGADOR NO SE PUEDE MOVER (input muerto / trabado).
2) En IA VS IA a menudo DEJAN DE PELEAR (caminan, se miran, no se golpean).
3) La CPU se siente menos agresiva / menos inteligente que antes (peor pelea cuerpo a cuerpo).

### Archivos clave (léelos con tools, no inventes)
- features/streetfighter/viewmodel/StreetFighterViewModel.kt
  - buildCpuInput / basic|normal|advanced|pesadillaCpuDecision
  - buildPlayerInput / handleCommonNeutral / IDLE_TURN
  - trySpecial / specialCooldown / MAX_ACTIVE_FIREBALLS
  - updateStageConstraints / clampFighterToStage / pushableStates
  - startAiVsAi / startArcade / cpuIntensity
- features/streetfighter/viewmodel/StreetFighterState.kt (aiVsAi, cpuDifficulty, arcade*)
- domain/models/streetfighter/SfModels.kt (SfCpuDifficulty, SfFighterState, validFrom en VM)
- ui/StreetFighterScreen.kt (flujo arcade Fácil/Medio/Difícil, IA vs IA, controles ocultos)

### Contexto de cambios YA hechos (no deshacer a ciegas)
- Anti-spam: cooldown special ~700ms, IA vs IA ~650ms; máx 1 fireball activa por peleadór; tope global 4.
- Clamp de stage para no salir de pantalla.
- **2026-07-18i (Grok, pre-traspaso):** IA reescrita en gran parte:
  - `cpuMoveTowardFlags` / `cpuRetreatFlags` (mundo, no solo “forward” de cara)
  - `cpuClinchBreak` si dist < 58 (separar al pegarse; crítico IA vs IA)
  - `smartCpuDecision` unifica AVANZADA/PESADILLA (footsies, block, anti-air, punish)
  - watchdog ofensivo, desync P0/P1, attackValidFrom incluye IDLE_TURN/JUMP_LAND
  - IDLE_TURN puede pasar a ataque directo
- Si el dueño aún ve torpeza, PARTIR de 18i y refinar (no borrar clinch break / world-space move).
- Arcade Fácil/Medio/Difícil + mapas: ver SF_STAGES_MAPS_UNLOCK.md — NO reabrir salvo bug.

### Objetivos de calidad de IA (lo que el dueño quiere)
A) VS humano (práctica + arcade):
   - Fácil (BASICA): sigue siendo aprendible (lenta, poco poder).
   - Medio (NORMAL): pelea real, se acerca, golpea, special ocasional.
   - Difícil (AVANZADA) y jefes/PESADILLA: AMENAZANTES — bloquean, castigan recovery, anti-air,
     presión melee, specials con sentido (no muro, no “solo caminar”).
B) IA vs IA (PESADILLA + intensity 1):
   - Siempre se ve pelea: golpes, saltos, specials ocasionales, persecución.
   - NUNCA se quedan 10+ s sin atacar o solo caminando uno al lado del otro.
   - No spamear proyectiles hasta llenar la pantalla (respeta 1 activo / cooldowns razonables).
C) Jugador humano:
   - Joystick + botones SIEMPRE responden en pelea (salvo banner ronda / hitfreeze / pause / KO).
   - No quedarse atrapado en IDLE_TURN, HURT, WALK push, o cooldowns que coman el input.

### Método de trabajo
1. Reproduce mentalmente / lee el tick: updateFighter → runStateHandler → buildCpuInput.
2. Busca bugs concretos: validFrom bloqueando walks/attacks; cpuHold solo forward; decision delays
   enormes; special fail silencioso; battleEnded/roundIntro stuck; direction/forward invertidos;
   both CPUs always blocking; isAnimationCompleted false forever en shared/alpha.
3. Mejora decisiones con reglas claras (prioridad):
   - incoming fireball → jump/block
   - foe airborne close → anti-air
   - foe in recovery close → punish heavy
   - close range → attack >> walk
   - mid → approach + occasional attack/special
   - far → approach + rare special
   - corner → leave corner toward foe (nunca acampar spameando)
4. Añade watchdog robusto para IA vs IA (y opcionalmente CPU): si no hay cambio de estado de ataque
   en X ms a distancia de pelea, forzar randomCpuAttack / approach+attack.
5. NO toques multiplayer net code salvo que rompa offline.
6. NO regeneres assets ni reasignes mapas.
7. Conserva MVVM (09), strings ES+EN si añades UI, CRLF en .kt.
8. Al final actualiza 07_OTHER_FEATURES.md §HUELUM (bullet IA) y DISENO_ARCADE si cambia comportamiento.

### Entregable
- Diffs en VM (y Screen solo si hace falta input UI).
- Resumen: causas del downgrade + qué se mejoró.
- Cómo probar: práctica NORMAL/AVANZADA, arcade Fácil, IA vs IA 2–3 pares (Prankedy vs Rey, policías, jefes).
- Listo para Rebuild (no puedes compilar en este entorno si no hay gradle wrapper jar).

Empieza leyendo buildCpuInput + pesadillaCpuDecision + el tick cuando aiVsAi=true y el path de input del jugador.
```

---

## Notas para el humano (no pegar a Claude)

- Si Claude pide código: `StreetFighterViewModel.kt` es el monstruo principal (~2.5k+ líneas).
- Tras su fix: Rebuild + prueba en dispositivo real (emulador no es obligatorio).
- Mapas/desbloqueos ya cerrados en esta sesión Grok — no reabrir salvo bug.
