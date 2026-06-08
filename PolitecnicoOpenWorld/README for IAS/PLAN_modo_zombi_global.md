# PLAN — Modo Zombi Global (apocalipsis en el mapa) / Global Zombie Mode

> Plan de implementación para el "modo apocalipsis" en el **mapa global** (open world), distinto
> del minijuego (`features/zombie_minigame/`). Sigue MVVM y las convenciones del archivo `09`.
> **Decisiones tomadas:** Multijugador desde ya · El jugador participa (lo atacan y pelea) ·
> Con contagio (humano muerto → zombi, con tope) · **Sin autos en v1**.

---

## 0. Resumen ejecutivo / TL;DR

**ES:** Los zombis son **un `NpcType.ZOMBIE` nuevo** simulado por el **Zone Host** dentro de
`NpcAiManager` (igual que los NPCs civiles), y se **replican con el `NPC_BATCH_UPDATE` que ya existe**
(ya transporta `npcType`, `health`, `isDying`). El **toggle global** del modo viaja en un mensaje nuevo
`ZOMBIE_MODE_SET` que el servidor solo relaya. El daño al jugador y el golpe/atropello del jugador
reusan los sistemas que ya hay (`applyNpcContactDamage`, `performPlayerAttack`, `runOverNpcs`). El
contagio lo aplica el Host al matar a un humano. **Casi todo es reuso**; lo nuevo es: 1 tipo de NPC,
1 sub-modo en `NpcAiManager`, 1 mensaje de red, 1 mano interactuable en ESCOM, 1 botón de salida y el
render del sprite zombi en mapa.

**EN:** Zombies are a new `NpcType.ZOMBIE` simulated by the **Zone Host** in `NpcAiManager` (like
civilian NPCs) and replicated through the **existing `NPC_BATCH_UPDATE`** (already carries
`npcType`/`health`/`isDying`). The global toggle rides a new `ZOMBIE_MODE_SET` message the server just
relays. Player damage and player melee/run-over reuse existing systems. Conversion is applied by the
Host on human death. Mostly reuse.

---

## 1. Respuestas a tus dudas (colisiones) / Answers about collisions

**ES — Matrices de colisión:** En el mapa global **no hay matriz de colisión** (las matrices solo
existen en el minijuego e interiores). El movimiento se valida con la **red de calles**
(`getNearestPointOnNetwork` + el índice espacial `Seg`/grid, `WorldMapRouting.kt`). **Plan:** los
zombis **NO** usarán la red de calles para perseguir; usarán **persecución directa fuera de la calle**,
exactamente como ya hace `NpcAiManager.moveAggroNpc` (se mueve en línea recta hacia el objetivo,
ignorando el grafo de calles). Esto da el efecto "cine": los zombis cortan camino. No se añade matriz
de colisión en el global.

**ES — Colisiones actuales:** Tienes razón: hoy los NPCs **no colisionan físicamente** entre sí ni
contigo (se superponen). Lo que percibes como "colisión" es el **daño por contacto / aggro**
(`applyNpcContactDamage` te quita vida si un NPC agresivo en embestida está a tu alcance; cada cliente
lo aplica a SU jugador). **Plan:** NO añadimos físicas de colisión (sería un sistema nuevo y caro). El
apocalipsis se siente con tres mecánicas baratas que ya encajan:
1. **Daño por mordida** (contacto) zombi→humano y zombi→jugador (reusa la lógica de contacto/aggro).
2. **Huida** de humanos del zombi más cercano (reusa el sistema de miedo `fearUntil`/`triggerFear`).
3. **Contagio** al morir el humano (el Host le cambia el `type` a ZOMBIE).

**EN:** Global map has **no collision matrix** (only the minigame/interiors do); movement is validated
against the **road network** (`getNearestPointOnNetwork` + `Seg`/grid spatial index). Zombies will use
**off-road direct pursuit** like `moveAggroNpc` (straight line toward target, ignoring the road graph).
NPCs don't physically collide today (they overlap); what feels like collision is **contact/aggro
damage**. We will NOT add collision physics — the apocalypse is built from contact-bite damage, flee,
and conversion, all reusing existing systems.

---

## 2. Modelo multijugador (autoridad) / Multiplayer authority model

**Patrón elegido: Zone-Host** (el que ya usan los NPCs civiles), NO servidor-autoritativo.

| Qué | Quién lo hace | Cómo viaja en red |
|---|---|---|
| Simular zombis (mover, morder, contagiar) | **Zone Host** (cliente) en `NpcAiManager` | `NPC_BATCH_UPDATE` (ya existe; lleva `npcType`, `health`, `isDying`) |
| Activar/desactivar el modo | Cualquier jugador (toggle) | **`ZOMBIE_MODE_SET` (NUEVO, global)** → todos activan el apocalipsis |
| Daño que recibe el jugador | **Cada cliente al SUYO** | la vida ya viaja en `PLAUER_UPDATE`/`PLAYER_UPDATE` |
| Adopción de zombis huérfanos | Host más cercano | ya funciona (mismo pipeline que NPCs) |

**Por qué Zone-Host y no servidor-autoritativo:** `Multiplayer/server.js` **por diseño no simula**
NPCs (no tiene pathfinding; solo arbitra Host y relaya — ver `08_SERVERS.md`). Meter simulación de
zombis ahí sería un rediseño mayor. Con Zone-Host reusamos AOI, throttle de Host, rate-limit y
**adopción** sin tocar la lógica de simulación del servidor. **El único cambio de servidor** es relayar
`ZOMBIE_MODE_SET` (global) — ~5 líneas.

> El `npcType` ya viaja como string en `MultiplayerNpc` y el servidor lo conserva con `{...npc}`, así que
> **el tipo `"ZOMBIE"` fluye sin cambios de servidor**. Solo confirmar que el saneamiento no lo filtra.

---

## 3. La Mano Zombi Global + Toggle + Botón de salida

**Estado actual relevante (verificado en código):**
- La interacción con objetos del mapa entra por **`WorldMapViewModel.handleInteraction()`** (botón X).
- La "mano" del minijuego (`"Objeto Misterioso ESCOM"`) sigue existiendo en `handleInteraction()`, pero
  hoy el acceso al lobby del minijuego es por **puertas físicas** (`ESCOM_DOOR`), y `spawnEscomItems`
  está **desactivado** (deja `_escomItems` vacío). → Hay espacio limpio para añadir una mano nueva.
- ESCOM se detecta con `isInsideEscom(lat, lon)` (`ESCOM_BASE_LAT/LON`, `ESCOM_OFFSET=0.001`).

**Plan de la mano (fija en ESCOM, no aleatoria):**
1. Constante nueva: `GLOBAL_ZOMBIE_HAND_ID = "global_zombie_hand"`, `GLOBAL_ZOMBIE_HAND_NAME =
   "Mano del Apocalipsis"`, coord fija dentro de ESCOM (p. ej. `19.50456, -99.14674` o ajustada con
   Modo Diseñador). Asset de mano (reusar el de la mano zombi del minijuego o uno dedicado).
2. Spawnearla como `ActiveCollectible` en un flujo dedicado (`_globalZombieHand`) o dentro de
   `_escomItems`, **solo cuando el jugador está dentro de ESCOM** (misma guarda que la lógica existente
   en `WorldMapGameLoop`/`spawnEscomItems`). Aparece siempre en el mismo punto.
3. En `checkCollectibleProximity` añadir el prompt: `"PRESIONA X PARA ACTIVAR/DESACTIVAR MODO ZOMBI"`
   (texto según `globalZombieMode`).
4. En `handleInteraction()` añadir rama:
   ```kotlin
   nearby.id == GLOBAL_ZOMBIE_HAND_ID -> toggleGlobalZombieMode()
   ```

**Toggle:** `toggleGlobalZombieMode()` invierte `globalZombieMode`. Re-interactuar con la mano
(o cualquier mano global) lo apaga. Es idempotente y se sincroniza por red.

**Botón de salida (requisito explícito):** además de la mano, un **botón en el HUD** visible solo cuando
`globalZombieMode == true` (en `WorldMapScreen.kt` / `GameControllers.kt`), que llama a
`exitGlobalZombieMode()` (= `toggleGlobalZombieMode()` forzando `false`). Así puedes salir aunque no
estés en ESCOM junto a la mano.

---

## 4. Comportamiento del modo (la "carne") — `NpcAiManager.kt`

Añadir un **sub-modo apocalipsis** al manager (corre en el Host):

```kotlin
var globalZombieMode: Boolean = false        // set desde el game loop cada tick
// Constantes nuevas (afinar):
ZOMBIE_SPEED_MULT  = 3.5f      // un poco más lento que AGGRO_SPEED_MULT(5) → puedes huir a pie
ZOMBIE_VISION      = 0.0009    // ~100 m: a qué distancia el zombi detecta humano/jugador
ZOMBIE_CONTACT_DIST= 0.00003   // ~3 m: distancia de mordida
ZOMBIE_BITE_DAMAGE = 8f        // daño por mordida (a humano y a jugador)
ZOMBIE_BITE_COOLDOWN_MS = 1000L
HUMAN_CONVERT_DELAY_MS  = 1500L // tiempo "muriendo" antes de levantarse como zombi
MAX_ZOMBIES        = 25        // tope para rendimiento (cuenta dentro del presupuesto de 40 NPCs)
INITIAL_ZOMBIE_SEED= 4        // zombis al activar el modo
```

**Al ACTIVAR (`globalZombieMode = true`):**
- Sembrar `INITIAL_ZOMBIE_SEED` zombis cerca del jugador/ESCOM (spawn en el anillo lejano, como
  `spawnNpcOnRoad` pero `type = ZOMBIE`).
- `carPopulationRatio = 0f` → dejan de spawnear autos nuevos (decisión: **sin autos en v1**). Los autos
  existentes pueden quedarse o despawnear suavemente (recomendado: dejar de spawnear y que se vayan por
  el despawn por distancia normal).
- Subir un poco la población de humanos para tener "víctimas".

**Cada tick (en `updateNpcs`, rama nueva cuando `globalZombieMode`):**
- **Zombis** → `moveZombieNpc(npc)`: generaliza `moveAggroNpc` para perseguir al **objetivo más cercano
  entre {humanos PERSON, jugador}** dentro de `ZOMBIE_VISION` (no solo al jugador). Persecución directa
  fuera de la calle. Si no hay objetivo → deambular lento.
- **Mordida zombi→humano:** si un zombi está a `ZOMBIE_CONTACT_DIST` de un humano → `health -=
  ZOMBIE_BITE_DAMAGE` (con cooldown). Si el humano llega a 0 → marcar `isDying` y, tras
  `HUMAN_CONVERT_DELAY_MS`, **convertir**: `npc.copy(type = ZOMBIE, health = 100, isDying = false)`
  si `nº zombis < MAX_ZOMBIES` (si está al tope, el humano simplemente muere/despawnea).
- **Humanos huyen del zombi más cercano:** reusar el sistema de miedo. Por tick, para cada humano con un
  zombi dentro de `FEAR_RADIUS`, marcar `fearUntil`/`fearFrom*` apuntando al zombi (ya existe la lógica
  de huida en `moveNpc` vía `applyPendingFear`). En apocalipsis **todos** los humanos huyen (no solo
  COWARD).

**Al DESACTIVAR (`globalZombieMode = false`):**
- Quitar todos los ZOMBIE (despawn) o, opcional, "curarlos" de vuelta a PERSON. Recomendado: despawn
  limpio (emite `NPC_DESTROY` por el pipeline normal).
- Restaurar `carPopulationRatio` y la población normal.

---

## 5. El jugador en el apocalipsis — `WorldMapViewModel.kt`

Reuso de sistemas existentes (extender, no reescribir):
- **Te muerden:** `applyNpcContactDamage(location)` (game loop, no host-gated, cada cliente al suyo) →
  incluir `NpcType.ZOMBIE` como fuente de daño por contacto (los zombis siempre "aggro" hacia ti).
- **Golpe B mata zombis:** `performPlayerAttack()` ya golpea NPCs/policías en `ATTACK_RADIUS` → incluir
  zombis en el set golpeable (bajan `health`, mueren a 0). Reusa `triggerFear` (los humanos cercanos
  también huyen de tu pelea).
- **Atropello:** `runOverNpcs(loc, speed)` ya daña peatones → incluir zombis (sí puedes seguir
  conduciendo tu propio auto aunque no spawneen autos nuevos).
- **Muerte:** `triggerWastedSequence()` ya respawnea ~80 m, limpia combate. Sin cambios (el respawn no
  apaga el modo zombi; sigues en apocalipsis).

---

## 6. Render del sprite zombi en el mapa

- **Native (`NativeOsmMap.kt` + `WorldMapDrawingUtils.kt`/`NpcRenderWrapper.kt`):** rama para
  `NpcType.ZOMBIE` que dibuja el sprite zombi (reusar `ZombieSpriteManager` del minijuego o exponer un
  `MapZombieSprite`; cachear por tamaño/frame en `nativeDrawableCache` LRU — ver `09`). Barra de vida ya
  se dibuja para NPCs con `health`.
- **Web (`WorldMapLeafletHtml.kt::updateNpcs`):** `updateNpcs` solo dibuja imágenes para `type`
  `"CAR"`/`"MODULAR"`; el resto cae a SVG. → Serializar el zombi como **base64 `type="MODULAR"`**
  (tamaño persona), igual que se hace con `POLICE_COP`. (Ver gotcha §8 de `09`.)
- Liberar la caché de sprites zombi en `MainActivity.onTrimMemory` (como Character/Vehicle/Police).

---

## 7. Cambios de red / Wire protocol

**Nuevo mensaje (global):**
```json
{ "type": "ZOMBIE_MODE_SET", "active": true }
```
- Cliente: en `toggleGlobalZombieMode()` → `webSocketManager.sendMessage(gson.toJson(...))`.
- Cliente: en `handleMultiplayerMessage` → al recibir `ZOMBIE_MODE_SET`, setear `globalZombieMode` y
  disparar el setup/teardown local del apocalipsis.
- **Servidor `Multiplayer/server.js`:** añadir el `type` al switch y `broadcastAll(...)` (global). ~5
  líneas. Mantener saneamiento/rate-limit.

**Sin mensaje nuevo para zombis:** viajan en `NPC_BATCH_UPDATE` con `npcType="ZOMBIE"` (+ health,
isDying), que el servidor ya conserva. Confirmar que el saneamiento de `npcType` (si lo hay) acepta el
nuevo valor.

---

## 8. Archivos a tocar / Files to touch (mapa de cambios)

| Archivo | Cambio |
|---|---|
| `domain/models/NpcType.kt` | + `ZOMBIE("ic_npc_person"|asset zombi)` |
| `domain/models/Npc.kt` | (opcional) campos `zombieSince`/`isConverting` si hacen falta para anim |
| `domain/models/ai/NpcAiManager.kt` | `globalZombieMode`, constantes §4, `moveZombieNpc`, mordida, contagio, huida de zombi, seed inicial, `carPopulationRatio=0` |
| `features/map_exterior/viewmodel/WorldMapState.kt` | + `globalZombieMode: Boolean = false` |
| `.../viewmodel/WorldMapViewModel.kt` | `toggleGlobalZombieMode()`, `exitGlobalZombieMode()`, set `npcAiManager.globalZombieMode` en game loop, extender `applyNpcContactDamage`/`performPlayerAttack`/`runOverNpcs` para ZOMBIE, broadcast `ZOMBIE_MODE_SET` |
| `.../viewmodel/WorldMapMultiplayer.kt` | parsear `ZOMBIE_MODE_SET`; asegurar render de `npcType="ZOMBIE"` |
| `.../viewmodel/WorldMapEscom.kt` / collectibles | spawnear la mano global fija en ESCOM; constantes id/nombre/coord/asset |
| `.../viewmodel/WorldMapCollectiblesLogic.kt` | prompt de la mano global en `checkCollectibleProximity` |
| `.../ui/WorldMapScreen.kt` + `ui/components/GameControllers.kt` | botón HUD "Salir del modo zombi" cuando `globalZombieMode` |
| `.../ui/NativeOsmMap.kt`, `WorldMapDrawingUtils.kt`, `NpcRenderWrapper.kt` | render sprite zombi (native) |
| `.../ui/WorldMapLeafletHtml.kt` | zombi como base64 `type="MODULAR"` (web) |
| `.../ui/components/` (sprite) | reusar/mover `ZombieSpriteManager` para el mapa + `clearCaches()` |
| `MainActivity.kt` | liberar caché sprite zombi en `onTrimMemory` |
| `Multiplayer/server.js` | relayar `ZOMBIE_MODE_SET` (global) |
| **Docs** | `README.md` (EN+ES), `plan.artifact.md`, y `README for IAS` (03, 04, 08, 09) — ver `09` §13 |

---

## 9. Rendimiento (gama baja) / Low-end performance

- **Tope `MAX_ZOMBIES`** y los zombis cuentan dentro del presupuesto de 40 NPCs (no sumar otra
  población grande encima).
- Persecución directa = **sin pathfinding** (barata). Huida reusa el sistema de miedo ya existente.
- Checks acotados por `simRadius`/`ZOMBIE_VISION` con cortes tempranos (mismo estilo que el resto del
  manager). Conversión: chequear con throttle, no en cada sub-iteración.
- Caché de sprite zombi: LRU por tamaño/frame + `clearCaches()` en `onTrimMemory` (no regresar a mapa
  ilimitado — ver `09` §6).

---

## 10. Orden de implementación sugerido / Suggested order

1. `NpcType.ZOMBIE` + render del sprite (native y web) con un zombi de prueba estático.
2. `globalZombieMode` en estado + `toggle/exit` + mano en ESCOM + botón HUD (single-player, sin red).
3. `NpcAiManager`: seed, `moveZombieNpc` (persecución), huida de humanos.
4. Mordida + contagio (Host) + tope.
5. Daño/golpe/atropello del jugador (extender los 3 sistemas existentes).
6. Multijugador: `ZOMBIE_MODE_SET` (cliente+servidor) + verificar replicación de zombis vía
   `NPC_BATCH_UPDATE` + adopción.
7. Pulido: FX, balance de constantes, sin autos (carRatio=0), QA en gama baja.
8. **Actualizar los 3 docs** (definición de "hecho", `09` §13).

---

## 11. Riesgos / decisiones abiertas

- **Mano vs puertas:** confirmar el asset/posición exacta de la mano global (se ajusta con Modo
  Diseñador). Debe distinguirse visualmente de la entrada al minijuego.
- **Autos existentes al activar:** decidir si se quedan (drivables) o despawnean. Recomendado: dejar de
  spawnear y que se vayan por distancia (mínimo esfuerzo, coherente con "sin autos en v1").
- **Adopción de zombis entre Hosts:** ya funciona por el pipeline de NPCs, pero conviene QA con 2+
  jugadores en zonas distintas (AOI).
- **Sincronía del toggle:** si dos jugadores togglean casi a la vez, el último gana (estado booleano
  global simple). Aceptable para v1.
```
