# 📦 HISTORIAL — sesiones del 2026-07-22 (movido de `_SESION_ACTUAL.md` el 2026-07-26)

> **Esto es HISTÓRICO, no son tareas.** Se sacó de `_SESION_ACTUAL.md` porque ese archivo debe
> mantenerse por debajo de 200 líneas (es lo primero que lee una IA nueva y cada KB cuesta
> tokens en TODAS las sesiones). Consúltalo solo si necesitas arqueología de esas sesiones.
> El detalle de diseño vive en `SF/DISENO_ARCADE_SF_POW.md`.

## Sesión 2026-07-22 NOCHE (Fable 5) — motor Fase 1 auditada + Fase 2a/2b

1. **✅ Parte A · AUDITORÍA de la Fase 1 (Opus): APROBADA.**
   - `validFrom` comparada **como datos** (script) entre el VM previo al commit `Fase 1a` y
     `SfStateMachine`: **67 destinos, 5 sub-listas y knockdownStates IDÉNTICOS**.
   - `Fase 1b-1e`: espejos exactos línea a línea (forAttack, CHIP_ATTACK_STATES, ATTACK_META
     21 entradas, sfUsableBonusPowerCount, SfPhysics.step, resolvedDamage + constantes de
     combo con los mismos valores). `Fase 1f` solo añade tests.
   - VM actual: alias en su sitio, **0 copias residuales**, árbol limpio.
   - Conteo MEDIDO: **32 tests nuevos** + 3 previos = **35**.
2. **Parte B · Fase 2 INICIADA (2 incrementos espejo):**
   - **2a `SfAnimation` (nuevo, puro):** frameIndex (wrap), frameTimerMs, shouldAdvance
     (delay<=0 = FREEZE/TRANSITION), isCompleted (fix último-frame-sin-−1). El VM conserva
     `withAnimationFrame`/`updateAnimation`/`isAnimationCompleted` como ENVOLTORIOS. +8 tests.
   - **2b `SfPhysics.clampToStage`:** espejo de `clampFighterToStage` (NaN/∞ → centro/piso,
     coerce X/Y, tope de aire `STAGE_AIR_CEILING=220f`); `STAGE_X_MIN/MAX` movidos del
     companion del VM a `SfConstants` (alias conservados). +4 tests.

### ¿Está COMPLETA la separación del motor? (medido 2026-07-22 noche)

**NO.** Medido, no supuesto: `SfEngine`/`SfGameMode` **NO existen**; `StreetFighterViewModel.kt`
tenía **5 643 líneas**; las **7 banderas de modo se consultaban ~142 veces** (showcase 23,
online 62, gauntlet 18, audioShowcase 15, aiVsAi 11, arcade 8, tutorial 5); las 10 funciones
núcleo del tick seguían privadas.

**PERO no bloquea producción.** Es un refactor INTERNO de mantenibilidad, no de correctitud.
El `.aab` NO baja con el refactor (el peso son los atlas).

## Cierre de lanzamiento 2026-07-22 noche (Opus 4.8) — detekt + auto-versión

- **Gate de detekt falló con 9 issues NUEVOS del refactor.** CORREGIDOS: (1) `SfConstants.
  STAGE_X_MIN/MAX` y alias → `const val`; (2) 4 alias muertos borrados (`specialValidFrom`,
  `neutralGround`, `crouchAttackValidFrom`, `airAttackValidFrom`); (3) `cpuNewMove` perdió 3
  params sin uso (`sim`, `selfIndex`, `now`).
- **versionName AUTOMATIZADO:** el YAML lee la versión de `build.gradle.kts` (fuente única).
  Job `bump-version` sube el patch +1 y lo commitea a `main` con `[skip ci]`.

## Sesión 2026-07-22 PM (Fable 5) — stun, crash Llorona, optimización

1. **🔴 Crash La Llorona (P0):** a su JSON le faltaban SOLO `longKick`/`overhead` → única que
   activaba el camino ALPHA (3er atlas en RAM). Blindado: cada atlas en `runCatching` y el
   atlas ALPHA siempre a media resolución (~63→16 MB). **Resuelto de raíz** después al
   importarse su arte de patada larga + overhead.
2. **🟠 Gama baja/carga:** todo lo pesado de una pelea se decodifica en `Dispatchers.IO` bajo el
   overlay CARGANDO (`SfFightAssets` + `fightIds`). `fightIds` es un SET con ambas identidades
   de la metamorfosis: transformarse a media pelea ya no re-decodifica nada.
3. **🟡 MAREO/STUN + medidor:** `SfFighterState.STUN` (AL FINAL del enum: viaja como `enum.name`,
   retro-compatible) + `dizzyMeter` (sube al RECIBIR, tope 25/golpe, decae tras 1.5 s; lleno →
   STUN 2 s con pose `stun-3` + estrellitas procedurales). Anim "stun" SINTETIZADA en
   `SfFrameCatalog`. Online: solo el peleador LOCAL; el `dizzyMeter` remoto NO viaja (cosmético).
4. **🟢 Metamorfosis:** SOLO round 1; arranca con VIDA LLENA; PERSISTE entre rondas.
5. **🔵 Intro del policía:** un clip `_intro` ya no lo corta otra voz del mismo peleador.
6. **🟣 Navegación:** al salir de una pelea se vuelve al selector del MISMO modo
   (`lastLaunchedMode`); etiquetas del tutorial con los controles reales; botón del paso PULSA.

## Sesión 2026-07-22 AM — subtítulos ON

- `voiceSubtitlesEnabled = true`; `voice_phrases.json` es la verdad (64 `es` curados).
  El delimitador `|` ya no se pierde: se sanea POR TRAMO y se dibuja una línea por tramo.
- Track `en` completo (~33 campos traducidos).
- Tutorial: +2 lecciones básicas en `combos.json` (`b_crouchchain`, `b_meter`).
