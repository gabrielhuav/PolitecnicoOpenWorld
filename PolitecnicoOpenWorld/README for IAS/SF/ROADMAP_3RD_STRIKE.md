# ROADMAP · "TITULACIÓN POR COMBATE" hacia SF III: 3rd Strike (2026-07-25)

> Ideas del dueño para acercar el modo pelea a 3rd Strike. Este archivo es la LISTA VIVA de lo
> pendiente por implementar (con notas de diseño para que una IA lo continúe). Lo YA hecho vive en
> `DISENO_ARCADE_SF_POW.md`. Convenciones: 09 (MVVM, estado inmutable, strings ES+EN con paridad).
>
> **Estado clave de esta pasada (2026-07-25, Opus 4.8):**
> - ✅ **HECHO:** toggle de **subtítulos de voz** (Ajustes → Interfaz, **default APAGADO**) — antes
>   `voiceSubtitlesEnabled=true` hardcodeado; ahora lo lee `SettingsRepository.getShowVoiceSubtitles`.
> - ✅ **HECHO** (pasadas previas de hoy): IA hace FATALITY con medidor lleno; sistema de
>   CALIFICACIÓN E..MS; contador de FPS del combate; joystick con respuesta inmediata; súper que
>   persiste entre rondas; grado de victoria; orden de jefes invertido + metamorfosis Yoalli→Presidenta;
>   rebalance de IA del arcade.
> - ✅ **HECHO (2026-07-25):** **Wall Splat + anti-infinito de esquina** (§3) — primer corte.
> - ⛔ **PENDIENTE** (este doc): combos largos + special multi-golpe tipo Chun-Li, Desperation
>   Moves, Bonus Stages.

---

## 1. ⛔ Combos LARGOS + special multi-golpe tipo Chun-Li (con partículas por CÓDIGO)

**Pedido:** hoy la IA (y el jugador) apenas encadenan 2-3 golpes. Falta poder hacer combos largos y
un **special que suelte muchas patadas/puños rapidísimos** (como el Hyakuretsukyaku de Chun-Li).
Para no depender de assets nuevos, la RÁFAGA se adorna con **partículas dibujadas por código**.

**Estado actual del sistema de combos:** data-driven en `assets/STREETFIGHTER/DATA/combos.json` →
`SfCombos.kt`. La IA encola rutas (`queueCombo`/`nextComboInput` en el VM) y el tutorial las valida.
El contador "N GOLPES" y el escalado de daño ya existen (`comboHits`, `SfDamage.resolvedDamage`).

**Diseño propuesto:**
- **Special "ráfaga" (rapid-fire):** nuevo estado, p. ej. reutilizar un `BONUS_POWER_*` libre por
  peleador, o un estado dedicado `RAPID_KICK`/`RAPID_PUNCH`. En su ventana activa, aplica **varios
  hits** (p. ej. 1 hit cada ~4 frames × N) reusando `applyAttackHit` con daño bajo por golpe y
  escalado de combo; al final, un golpe que EMPUJA. Se dispara con un comando propio (p. ej.
  ↓↘→ + patada repetida, o mantener el botón). Cargar `superMeter` como cualquier golpe.
- **Partículas por código (sin assets):** un pool de partículas en el estado UI (o efímero en la
  Screen) — chispas/estelas dibujadas con `drawCircle`/`drawLine`/`drawRect` en la Canvas, con
  color por fuerza y vida corta (~150 ms). Emitir 2-4 por hit de la ráfaga. Barato en gama baja
  (formas simples, sin bitmaps). Ver `drawScene`/`SfHitSplash` como molde de "efímero por tick".
- **Combos más largos:** ampliar `combos.json` con rutas de 4-6 golpes (chain → special → super) y
  subir la ventana `RAPID_HIT_WINDOW_MS` o el tope de `comboHits` si hace falta. La IA ya ejecuta
  rutas del catálogo; con rutas más largas hará combos más largos (gateado por `comboBias`/dificultad).
- **⚠️ Balance:** los combos largos NO deben ser infinitos — ver §3 (Wall Splat / anti-infinito).

**Archivos:** `SfModels.kt` (estado nuevo si aplica), `StreetFighterViewModel.kt` (`applyAttackHit`,
ráfaga, partículas o su emisión), `StreetFighterScreen.kt` (`drawScene` → dibujo de partículas),
`assets/STREETFIGHTER/DATA/combos.json`, `SfCombos.kt`, strings si hay tutorial.

## 2. ⛔ Desperation Moves (ataques de desesperación a poca vida)

**Pedido:** faltan los "Desperation Moves" clásicos.

**Diseño propuesto:** un movimiento potente (o versión mejorada del súper) que SOLO se habilita con
**vida baja** (p. ej. ≤25% HP, análogo al umbral de metamorfosis `HEALTH_MAX/4`) y medidor lleno.
- Reusar `SUPER_ART`/`FATALITY` con un **buff condicional** (más daño / propiedades de armadura) si
  `hitPoints <= threshold`, o un estado nuevo `DESPERATION` por peleador (los que tengan arte).
- La IA lo usaría vía la lógica de súper/fatality ya existente (`maybeFatalityInput`/`cpuNewMove`)
  añadiendo la condición de vida baja para priorizarlo.
- Feedback visual: aura/flash por código (mismo sistema de partículas de §1).

**Archivos:** `StreetFighterViewModel.kt` (condición de vida baja en trySuper/tryFatality + IA),
`SfDamage.kt` (daño buffeado), `StreetFighterScreen.kt` (aura).

## 3. ✅ (2026-07-25, primer corte) Wall Splat / Corner Bounce (rebote LIMITADO, JUSTO para ambos)

> **Implementado** en `applyAttackHit` (`StreetFighterViewModel.kt`): un golpe **HEAVY** (o `SWEEP`)
> sin bloquear con el rival EMPUJADO contra la pared lo hace **rebotar al centro** (`WALL_SPLAT_BOUNCE_PX`),
> quedando a rango para continuar el combo. **UNA sola vez por combo** (`wallSplatUsed[]`, se reinicia
> al empezar un combo nuevo y en el escape), y el escape de 3 golpes existente sigue acotando el combo
> → **sin infinitos**. **Simétrico** (índice genérico). Anti-"trabe": en el escape de esquina se
> **empuja al ATACANTE** hacia atrás (antes el empuje del defensor se lo comía la pared → quedaba
> atrapado). ⚠️ Solo offline/local (online el daño viaja por `PLAYER_DAMAGE`); afinar `WALL_SPLAT_ZONE`/
> `BOUNCE_PX` tras jugarlo. Diseño original abajo.



**Pedido:** hoy en la esquina "te traban" con combos casi infinitos. Introducir un **rebote limitado
contra la pared** al conectar ciertos golpes pesados: abre rutas de combo nuevas y castiga el mal
posicionamiento de forma vistosa, **pero SIEMPRE justo para los 2 jugadores** y sin permitir infinitos.

**Estado actual:** ya hay un anti-infinito parcial: `comboEscapeUntilMs` + `RAPID_HITS_BEFORE_ESCAPE`
(tras N golpes rápidos, el defensor sale del hit-stun). La esquina hoy funciona igual que el centro
(`clampToStage`/`SfPhysics`).

**Diseño propuesto (simétrico = justo):**
- **Wall splat:** al recibir un golpe PESADO designado (p. ej. HEAVY o `SWEEP`/lanzamiento) estando
  **pegado a la pared** (`isNearStageCorner`), el defensor "se aplasta" un instante (pose HURT +
  rebote corto hacia el centro) → ventana de combo controlada.
- **Límite anti-infinito (clave):** escalar el hit-stun/daño hacia abajo con `comboHits` (ya escala
  el daño) Y forzar el `comboEscapeUntilMs` tras el splat, de modo que el rebote dé **1 oportunidad**
  de continuación, no un bucle. Un contador de "splats por combo" (máx 1) evita re-aplastar.
- **Simetría:** aplicar la MISMA regla a ambos índices (0 y 1); nada dependiente de "quién es el
  jugador". Los tests de caracterización deben cubrir ambos lados.

**Archivos:** `SfPhysics.kt` / `clampToStage` (detección de pared), `StreetFighterViewModel.kt`
(`applyAttackHit` → splat + guard anti-infinito), `SfConstants.kt` (umbrales), tests en
`SfPhysicsTest`/`SfDamageTest`.

## 4. ⛔ NO IMPLEMENTAR (solo documentado por decisión del dueño) — Bonus Stages

Rondas de bonificación clásicas:
- **"Destruir el automóvil"** (Car Crash): un coche destructible; el jugador lo rompe a golpes antes
  de que se acabe el tiempo. Necesitaría un asset de coche por fases de daño (o formas por código) y
  un mini-modo sin rival.
- **"Parry Rally":** oleada de proyectiles/golpes que el jugador debe **parear** en cadena (usa el
  PARRY ya existente); puntaje por racha. Casi todo reutilizable (parry + partículas §1).

> **Decisión del dueño (2026-07-25): NO implementar aún.** Queda documentado aquí como idea futura.
> Encaje: un `SfBonusStage` (modo aparte, sin `SfArcadeLadder`), intercalado cada N peleas del arcade.

---

## Prioridad sugerida (cuando el dueño lo pida)

1. **Wall Splat + anti-infinito (§3)** — es también un fix de balance ("que te traben"), no solo una
   feature; protege la experiencia competitiva.
2. **Combos largos + ráfaga con partículas (§1)** — alto impacto visual, reusa mucho.
3. **Desperation Moves (§2)** — pequeño, reusa súper/fatality.
4. **Bonus Stages (§4)** — diferido por el dueño.
