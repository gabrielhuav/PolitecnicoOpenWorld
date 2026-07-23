# PLAN · Motor compartido entre modos + gama baja + calidad senior

> **Estado: FASE 1 HECHA** (2026-07-22, Opus 4.8) · Fases 2-5 pendientes (auditará Fable).
> Escrito tras MEDIR el código, no tras leerlo por encima. Ver §1.

## ✅ Fase 1 COMPLETA (2026-07-22) — red de seguridad, riesgo cero, comportamiento idéntico

Se extrajo la LÓGICA PURA del `StreetFighterViewModel` a `domain/models/streetfighter/` (sin
Android, testeable en JVM). El VM la referencia por ALIAS/delegación, así que **el juego no cambió**:

| Pieza pura | Qué salió del VM | Tests |
|---|---|---|
| `SfStateMachine` | tabla `validFrom` + sub-listas + `canEnter` | `SfStateMachineTest` |
| `SfDamage` | daño base, `ATTACK_META`, chip, `resolvedDamage` (bloqueo/combo) | `SfDamageTest` |
| `SfPhysics` | cinemática de un tick (posición + slide) | `SfPhysicsTest` |
| `sfUsableBonusPowerCount` | conteo de poderes lanzables | `SfBonusPowerTest` |

**~30 tests de caracterización, todos verdes** (antes: 0 tests de motor). `compileDebugKotlin +
testDebugUnitTest` OK. Cada extracción es un commit `SF motor Fase 1x` para auditar por separado.

**Cómo seguir (Fases 2-5) sobre esto:** cada pieza que se mueva al `SfEngine` puro ya tiene su test
de caracterización; si una fase la rompe, el test lo detecta. Ese era el objetivo de la Fase 1.

---

## 1. Diagnóstico medido

| Métrica | Valor | Lectura |
|---|---|---|
| `StreetFighterViewModel.kt` | **5 271 líneas** | Un solo archivo con TODO el motor |
| `StreetFighterScreen.kt` | **3 435 líneas** | Render + overlays + input |
| Esos dos sobre el total del feature | **8 706 de 11 463 (76 %)** | El resto son piezas ya bien separadas |
| Funciones públicas del VM | **65** | Superficie enorme para una sola clase |
| Banderas de modo | **7** | `gauntletActive`, `showcaseMode`, `aiVsAi`, `tutorialActive`, `arcadeActive`, `isOnline`, `audioShowcase` |
| Consultas a esas banderas | **104**, repartidas por todo el VM | Cada modo nuevo obliga a tocar el archivo entero |
| **Tests del motor de pelea** | **0** | `SfArcadeCampaignAuditTest` audita ASSETS, no lógica |

**La buena noticia:** los modos **ya comparten un solo motor**. No hay duplicación que eliminar,
así que el `.aab` no baja por ahí — el peso está en los atlas (88.6 MB de IMAGES), no en el código.

**La mala:** lo comparten mediante `if (showcaseMode)` esparcidos por 5 271 líneas. Añadir un
modo hoy significa entender y tocar todo el archivo. Eso es lo que hay que arreglar.

## 2. Por qué NO se empezó en esta sesión

Refactorizar el motor que usan Arcade, IA vs IA, Showcase, Tutorial, VS y Multiplayer
**sin una sola prueba que lo cubra** es exactamente el escenario que ya costó una regresión
en este proyecto (la sesión que re-recortó las 520 hojas "por si acaso").

Con 0 tests, cualquier extracción se valida solo "jugando a ver si se rompe". No es aceptable
para el núcleo del modo.

**Orden correcto: primero la red de seguridad, después mover cosas.**

## 3. Plan por fases (cada una entrega valor y se puede parar ahí)

### Fase 1 · Red de seguridad (SIN tocar producción) — riesgo CERO ✅ HECHA (ver arriba)

Tests de caracterización del motor actual: se escribe lo que HOY hace, no lo que debería.

- `SfSimTest`: física de un tick — gravedad, suelo (`STAGE_FLOOR`), empuje, límites del ring.
- `SfStateMachineTest`: `changeState` — transiciones válidas desde cada estado, guarda
  `hasAnim`, y que ningún estado quede sin salida (el bug de "congelado" ya visto).
- `SfDamageTest`: `damageForAttack` en normal, súper, agarre y fatality. **Ya hubo un bug real
  aquí en multiplayer**: el daño no viajaba.
- `SfBonusPowerTest`: `usableBonusPowerCount` y el cursor que salta poderes sin animación.

Estas pruebas son las que **detectan** si una fase posterior rompe algo.

### Fase 2 · Extraer el simulador puro

Sacar de la ViewModel una clase `SfEngine` **sin Android, sin Context, sin StateFlow**:
entra `(estado, inputs, dt)` y sale `estado`. Determinista y testeable en JVM pura.

La ViewModel se queda con lo que le toca en MVVM: exponer `StateFlow`, recibir intents de la
UI y hablar con los repos. Hoy hace además de motor de físicas, de IA y de reproductor de audio.

### Fase 3 · Los modos como estrategia, no como banderas

Sustituir las 7 banderas por un `SfGameMode` con implementaciones (`ArcadeMode`, `VsMode`,
`AiVsAiMode`, `ShowcaseMode`, `TutorialMode`, `OnlineMode`). Cada uno decide: quién controla a
cada peleador, si corre el reloj, si hay daño, qué pasa al terminar la ronda.

**Resultado buscado por el dueño:** un modo nuevo = una clase nueva, sin tocar el motor.

### Fase 4 · Gama baja de verdad, con medición

Lo que YA existe: `SfDeviceTier` (LOW ≤2.2 GB / MID ≤4.2 / HIGH) y atlas submuestreados a ½
en LOW (73 → 18 MB de RAM por peleador).

Lo que falta, **midiendo antes y después** (no a ojo):
- Liberar el atlas del rival al salir de la pelea (hoy viven mientras dure el proceso).
- Reutilizar los `Bitmap` entre rondas en vez de redecodificar.
- Saltar los efectos caros (partículas, sombras) en LOW.
- Un `SfPerfTest` que falle si la RAM por peleador supera un techo.

### Fase 5 · Cierre de calidad

- detekt a 0 (**hoy hay 5 smells preexistentes**, y varios docs afirman falsamente que hay 0).
- Partir `StreetFighterScreen.kt` (3 435 líneas) por overlay.
- KDoc en la superficie pública del motor.

## 4. A quién delegar

Según la tabla de `../_SESION_ACTUAL.md`, esto es **dificultad ALTA**: toca el núcleo que usan
todos los modos.

| Fase | Quién | Por qué |
|---|---|---|
| 1 (tests) | **Opus 4.8** | Riesgo cero, es pura adición; no necesita el contexto completo del motor |
| 2 y 3 (extracción) | **Sol 5.6 / Fable 5** | Cambio estructural en 5 271 líneas con 6 modos dependiendo de él |
| 4 (gama baja) | **Sol 5.6 / Fable 5** | Requiere medir en dispositivo real |
| 5 (detekt, KDoc) | **Gemini 3.6** | Mecánico y bien acotado |

## 5. Regla para quien lo ejecute

Después de CADA fase:

```bash
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Y **probar a mano los 6 modos**: Arcade, VS, IA vs IA, Showcase, Tutorial y Multiplayer. Si una
fase no se puede validar en los 6, no está terminada.
