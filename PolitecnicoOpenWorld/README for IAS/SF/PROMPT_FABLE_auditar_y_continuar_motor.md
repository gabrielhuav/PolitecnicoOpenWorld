# PROMPT · Fable 5 — auditar la Fase 1 del motor y continuar (Fases 2-5)

> Rutas RELATIVAS al repo (ver `_SESION_ACTUAL.md` §Rutas por PC). El plan completo:
> `SF/PLAN_refactor_motor_compartido.md`. **La Fase 1 (red de seguridad) ya está HECHA por Opus.**

## Parte A · AUDITAR la Fase 1 (antes de construir encima)

Opus extrajo la lógica PURA del `StreetFighterViewModel` a `domain/models/streetfighter/` y la dejó
con ~30 tests de caracterización. El VM la usa por ALIAS/delegación (comportamiento idéntico).
Commits: `SF motor Fase 1a`…`1e`. Piezas + tests:

- `SfStateMachine` (tabla `validFrom` + `canEnter`) — `SfStateMachineTest`
- `SfDamage` (base, `ATTACK_META`, chip, `resolvedDamage`) — `SfDamageTest`
- `SfPhysics` (cinemática de un tick) — `SfPhysicsTest`
- `sfUsableBonusPowerCount` — `SfBonusPowerTest`

**Tu auditoría (NO reescribas, VERIFICA):**
1. Que la extracción sea **idéntica** al VM original: `git show` de cada commit `SF motor Fase 1x` y
   confirma que las tablas/fórmulas movidas no cambiaron valores (solo cambiaron de sitio + alias).
2. `.\gradlew.bat compileDebugKotlin testDebugUnitTest` → verde.
3. **Juega los 6 modos** (Arcade, VS, IA vs IA, Showcase, Tutorial, Multiplayer): daño, transiciones,
   bloqueo/chip, combos, poderes bonus deben sentirse **exactamente igual** que antes.
4. Reporta hallazgos; si algo difiere, es bug de la extracción (Opus).

## Parte B · CONTINUAR (Fases 2-5) — cada pieza ya tiene su test de la Fase 1 como red

### Fase 2 · Extraer el simulador puro `SfEngine`
Sacar del VM una clase `SfEngine` **sin Android/Context/StateFlow**: entra `(estado, inputs, dt)`,
sale `estado`. Hazlo **incremental** (una porción del tick por commit), reusando las piezas puras que
ya existen (`SfStateMachine`, `SfDamage`, `SfPhysics`). El VM se queda con MVVM (StateFlow, intents,
repos, audio). Tras cada porción: compilar + tests + **jugar los 6 modos**.

### Fase 3 · Modos como estrategia (no banderas)
Sustituir las 7 banderas (`showcaseMode`, `aiVsAi`, `arcadeActive`, `tutorialActive`, `gauntletActive`,
`isOnline`, `audioShowcase`) por `SfGameMode` con implementaciones. **Meta del dueño: un modo nuevo =
una clase nueva, sin tocar el motor.** ⚠️ **Multiplayer:** valida que el snapshot/sync
(`applyRemoteSnapshot`, `rs.meter`/`rs.state`) siga consistente en cada paso.

### Fase 4 · Gama baja MEDIDA (en dispositivo real)
Liberar el atlas del rival al salir; reutilizar `Bitmap` entre rondas; saltar efectos caros en LOW
(`SfDeviceTier`); `SfPerfTest` con techo de RAM por peleador. **Mide antes/después, no a ojo.**
(La Llorona ya no carga el 3er atlas ALPHA — ver `SF/IMPORT_lallorona_*`; eso ya ayudó.)

### Fase 5 · Cierre de calidad (→ delegable a Gemini)
detekt a 0 (hoy 5 smells preexistentes: `CachingWebViewClient`, `NpcAiManager`, `RoadRouter`,
`CatSpriteManager` ×2), partir `StreetFighterScreen.kt` por overlay, KDoc del motor.

## Regla (de §5 del plan)
Tras CADA fase: `.\gradlew.bat compileDebugKotlin testDebugUnitTest` **y** probar a mano los 6 modos.
Si una fase no se puede validar en los 6, no está terminada. Actualiza `_SESION_ACTUAL.md`.
