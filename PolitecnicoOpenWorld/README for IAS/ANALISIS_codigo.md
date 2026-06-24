# ANÁLISIS de código — POW (informe, 2026-06-21)

> Informe para que **tú decidas**. Generado leyendo las tablas "Key files" (04/05/06/07), el VM y sus
> parciales, y midiendo tamaños reales. Prioridades al final.
>
> ### ⚠️ NOTA DE ESTADO (actualizado 2026-06-21 tarde) — varias "prioridades" de abajo YA SE HICIERON:
> - **✅ De-dup de gemelos del VM: COMPLETA** (era la prioridad 1). 8 pares resueltos (5 limpios, 2
>   fusionados, 1 revertido por la cadena de routing). VM **2467 → 2114**. Ver `09 §12`.
> - **✅ i18n player-facing: COMPLETA** (incl. transit + diseñadores de matrices). Solo queda
>   `CampaignObjective.title/description` → `@StringRes` (prioridad 4, opcional).
> - **✅ Audio del game loop activado** (caminar/correr/coche/zombi) + fix de la carga async de SoundPool.
> - **✅ Base común Metro⇄Metrobús — VM + State + config HECHO (2026-06-22):** ver §2.2 y "Re-análisis".
>   Sigue **pendiente/opcional**: parametrizar UNA pantalla/overlay por config (hoy siguen 2 archivos),
>   extraer NavHost de MainActivity, y micro-opts perf (no hay candidatas seguras nuevas).
> - Tamaños actuales: solo 5 archivos >1000 (VM 2114, NativeOsmMap 1460, WorldMapScreen 1326,
>   MainActivity 1064, ZombieGameScreen 1035). El detalle de abajo conserva el análisis original.

---

## 1. Clases grandes (tamaño real medido hoy)

| Archivo | Líneas | ¿Separar? | Veredicto |
|---------|-------:|-----------|-----------|
| `WorldMapViewModel.kt` | **2467** | **SÍ** | Único > 1500. La reducción ya está PREPARADA: de-dup de 8 gemelos → ver `09 §12`. Aplicada, baja a < 1500. **Es la única separación que vale la pena.** |
| `NativeOsmMap.kt` | 1460 | No | Renderer Canvas nativo cohesivo (sprites/emoji/fog). Ya se extrajo `Prankedy`/`Fog`. Partirlo más fragmenta el hot-path de dibujo. **Dejar.** |
| `WorldMapScreen.kt` | 1326 | No | Ya delega en `Overlays/Controls/Google/Web`. Lo que queda es el `Composable` raíz + cableado. **Dejar.** |
| `MainActivity.kt` | 956 | Opcional | Es el grafo de navegación + orientación + diálogos Activity-scoped. Se *podría* extraer el `NavHost` a un `AppNavGraph.kt`, pero el acoplamiento con `worldMapViewModel` Activity-scoped lo hace arriesgado sin compilador. **Dejar por ahora.** |
| `ZombieGameScreen.kt` | 948 | No | Pantalla de supervivencia + HUD designer. Cohesivo. **Dejar.** |
| `ZombieInteriorViewModel.kt` | 933 | No | Motor del interior zombi; ya tiene capa `ZombieCombat/Tick/Designer`. **Dejar.** |
| `NpcAiManager.kt` | 884 | No | Ya se extrajo `Movement`/`Traffic`. **Dejar.** |
| `WorldMapLeafletHtml.kt` | 844 | No | Es el HTML/JS del mapa Leaflet embebido (string gigante). No es "lógica" Kotlin separable; tocarlo arriesga el contrato cliente⇄WebView. **Dejar.** |
| `SettingsScreen.kt` | 824 | Opcional | Muchas sub-secciones (`*Settings`). Si crece más, extraer cada categoría a su archivo. Hoy está bien. |
| `MetroInteriorViewModel.kt` (700) / `MetrobusInteriorViewModel.kt` (655) | — | Posible | **Casi gemelos** (metro vs metrobús). Ver §2. |

**Conclusión:** el objetivo "< 1500 salvo el VM" YA se cumple. Solo el VM necesita reducción y ya
está preparada (de-dup). NO partir nada por estética.

---

## 2. Código duplicado / muerto

### 2.1 Gemelos miembro-vs-extensión del VM (LA duplicación principal) — **preparado**
8 funciones existen como miembro `private` (vivo) Y como extensión homónima (muerta y divergida).
Es la mayor fuente de líneas muertas del VM. Análisis byte-a-byte y cuerpos sincronizados listos en
**`09 §12`**. Aplicar 1 par/compilación. (No es "código muerto inofensivo": varias
extensiones traen lógica VIEJA con bugs ya corregidos en el miembro.)

### 2.2 Metro ⇄ Metrobús — **VM/State/config UNIFICADOS (2026-06-22)** ✅ (pantallas pendientes)
**HECHO:** `MetroInteriorViewModel`(700) y `MetrobusInteriorViewModel`(655), antes gemelos, se fusionaron en
**`TransitInteriorViewModel`** (lógica una sola vez), con **`TransitInteriorState`** (estado neutro + alias de
compat) y **`TransitSystemConfig`** (`TransitSystems.METRO`/`.METROBUS`: assets, prefs, repo, spawn, offset de
torniquete, strings, branding). Los modelos implementan la interfaz **`TransitStation`**. Los 4 archivos viejos
(2 VM + 2 State) son tombstones. **Añadir transporte = nueva config + assets + ruta** (no se duplica lógica).
Cambio dirigido por config, comportamiento idéntico. *(Hecho a ciegas sin compilador con commit de seguridad;
verificar en el primer Rebuild — riesgo concentrado en imports/EOL de los 4 archivos nuevos.)*

**PENDIENTE (opcional, medio esfuerzo):** las PANTALLAS y OVERLAYS siguen separadas
(`MetroStationInteriorScreen`(669) ⇄ `MetrobusStationInteriorScreen`(589); `MetroMapOverlay`(459) ⇄
`MetrobusMapOverlay`) porque su RENDER difiere (metro=vídeo+animación vertical; metrobús=gradiente+horizontal).
La `TransitSystemConfig` YA lleva esos parámetros (color, eje, overlayType, vídeo/gradiente, sprites, vehicleAsset)
→ se podría colapsar a UNA `TransitStationScreen(config)` + UN `TransitMapOverlay(config)`. Riesgo medio (Compose +
animación). No urgente.

### 2.3 Otros
* **TODO/FIXME/HACK: 0** en todo el módulo. Limpio.
* `updateNpcsState`, `applyRoadNetwork`, `startHealthBarTimer` ya de-dup (no rehacer).
* Los strings de diseñador de interiores están duplicados como literales en metro/metrobús aunque
  existen claves `int_*` reutilizables (ver §4).

---

## 3. MVVM — adherencia (buena, con matices)

La regla "Views solo `collectAsState()` + intenciones; nunca tocan repos/DAOs" se respeta casi siempre.
Los accesos a repos desde `ui/` que encontré NO son violaciones reales en su mayoría:

* `SettingsRepository(context).getDeveloperMode()/getPlayerSkin()` leído **una vez al entrar** en
  `StoryIntroScreen`, `ZombieGameScreen`, `MainMenuScreen`, `WorldMapScreen` → **patrón SANCIONADO**
  por 09 (developerMode/skin/name se leen así). OK.
* `SaveSlotsDialog` referencia `SaveGameRepository.AUTO_SLOTS` (constante `companion`) y recibe los
  datos por `summariesProvider` (lambda desde `MainActivity`). OK.
* **Único matiz real:** `StoryIntroScreen` (editor de cómic, Modo Desarrollador) crea
  `StoryLayoutRepository(context)` y hace `repo.save/saveAll` **directo desde la View**. Es una
  herramienta dev, pero técnicamente la View escribe a un repo. *Mejora opcional:* mover a un VM
  (`StoryEditorViewModel`). Bajo valor (dev-only).

`_state.update { it.copy(...) }` y estado inmutable: se respetan en los VMs revisados.

---

## 4. i18n — estado tras la Fase 1 de esta sesión (lo migrado HOY)

**Migrado a `stringResource`/`getString` (ES base + EN paridad 1:1, verificado):**
* `map_exterior`: misión fallida (`wm_mission_failed/_retry_mission/_exit_to_menu`), tip carjack,
  guardar partida, widget de objetivo (`wm_objective_*`, `wm_dist_km/_m`), descripción completa de
  Prankedy (`wm_prankedy_desc_full`), panel Debug Interiores (`dbg_*`), labels ANCHO/ALTO del diseñador.
* `campaign`: `StoryModeScreen` (slot), `StoryIntroScreen` (back/skip/start + hints `story_tap_*` +
  editor de cómic `story_ed_*`).
* `interiores` (player-facing): ZombieHud (`zhud_inv_*`, `cd_key`), ZombieGameScreen (prompt de llave,
  objetivo ENCB, guardar), InteriorScreenBase (`cd_exit`), InteriorPlayerViews (`cd_player/_remote`).
* `MainActivity`: diálogos guardar/cargar (`save_dialog_*`, `save_toast_saved`).
* Total claves NUEVAS añadidas esta sesión: ~57, todas en `values/` y `values-en/`.

**PENDIENTE de i18n (cola, NO migrado — sin riesgo de compilar, mecánico):**
* **Transit (player-facing core):** `MetrobusMapOverlay` (título, "SISTEMA METROBÚS · SELECCIONA TU
  DESTINO", "Seleccionar Estación de Metrobús", "Buscar estación", "Cerrar"→`common_close`,
  "Cancelar"→`menu_cancel`), `MetrobusStationInteriorScreen` (header `METROBÚS · $stationName`,
  `PRESIONA X PARA …`→`int_press_x_door`), `MetroStationInteriorScreen` ("Salir"→`cd_exit`).
* **Diseñadores de matrices (Modo Desarrollador):** los grids ANCHO/ALTO/COL/FIL + hints en
  `MetroStationInteriorScreen`, `MetrobusStationInteriorScreen`, `ZombieGameScreen` (899/937). **Muchas
  claves `int_*` YA EXISTEN para reutilizar** (`int_lock_map`, `int_edit_map`, `int_designer_mode`,
  `int_move_map`, `int_drag_door`, `int_designer_room`, `int_station_name`, `int_save_unsaved`,
  `int_press_x_door`, `int_size_grid`, `int_waypoint_size`, `int_touch_door`). Faltan claves para
  ANCHO/ALTO/COL/FIL (signo `−` U+2212) y "Salir"/"Diseñador" botón.
* **Nombres propios (NO migrar, por convención 09):** skins `PlayerSkin` (Lázaro, Robot Estudiantx…),
  "Shine CTO", "PRANKEDY". `displayName` técnicos se dejan.
* **NO son UI (no migrar):** logs, TAGs, rutas de assets, tipos de mensaje de red, claves de caché de
  sprites, unidades (`km/h`, `HP`), comparaciones por nombre de dato (`"Objeto Misterioso ESCOM"`).
* **Modelos con texto (decisión pendiente):** `CampaignObjective.title/.description` son strings en el
  modelo (los pinta `ObjectivesWidget`). Para i18n completo habría que pasarlos a `@StringRes` y
  resolver en la View (toca `MissionCatalog`/`Mission1` + call-sites) → **cambio de medio esfuerzo,
  requiere compilar**; lo dejé sin tocar.

---

## 5. Rendimiento gama baja

La pasada segura ya está hecha (09 §6: fog sin alloc/frame, `screenDensity` cacheado, `cachedWaysFiltered`).
Riesgos NO resueltos detectados, **ninguno crítico**:
* Diseñadores de matrices (metro/metrobús/zombi) re-crean listas por interacción, pero son **dev-only**
  (no afectan al jugador). No tocar.
* `WorldMapLeafletHtml`/`WorldMapScreenWeb`: el proveedor WEB hace muchos `evaluateJavascript` por
  frame (template strings). La opt de plantillas JS ya se **descartó por insegura** en 09 §6 — no re-proponer.
* `ObjectivesWidget` recalcula distancia por recomposición (trig). Coste trivial (1 NPC-less cálculo);
  no vale la pena.

**Recomendación:** no hay micro-opts seguras nuevas con ROI claro más allá de lo ya hecho. La Fase 5
(micro-opts) queda **sin acciones**: ver §7.

---

## 6. Seguridad (resumen; detalle en `REVISION_repo.md`)
* ✅ Keystore `llave_pow.jks` **NUNCA commiteado** (verificado en los 155 commits / 11 ramas).
* ✅ `.gitignore` cubre `*.jks`, `google-services.json`, `secrets.properties`, `*.env`, `build/`, `.idea/`.
* ✅ Sin secretos hardcodeados en `Multiplayer/` ni `MultiplayerInteriores/` (usan `process.env` + verificación de token Firebase).

---

## 7. Mejoras priorizadas (valor / esfuerzo)

| Prioridad | Acción | Valor | Esfuerzo | Riesgo |
|-----------|--------|-------|----------|--------|
| **1** | Aplicar de-dup del VM (`09 §12`, 1 par/compilación) → VM < 1500 | Alto | Medio | Medio (mitigado: 1 par/ciclo) |
| **2** | Terminar i18n de transit player-facing (metro/metrobús headers + selección) | Medio-alto | Bajo | Bajo |
| **3** | Terminar i18n de diseñadores (reusando claves `int_*` existentes) | Bajo | Bajo-medio | Bajo |
| 4 | `CampaignObjective` → `@StringRes` (i18n del objetivo de campaña) | Medio | Medio | Medio (toca modelo) |
| ~~5~~ | ✅ **HECHO (2026-06-22)** Base común Metro⇄Metrobús: VM+State+config unificados (§2.2). Falta (opcional) colapsar las 2 pantallas/overlays en una por config. | Medio | Alto | Alto |
| 6 | (Opcional) Extraer `NavHost` de `MainActivity` a `AppNavGraph.kt` | Bajo | Medio | Medio |
| — | Micro-opts perf adicionales | — | — | **Sin candidatas seguras nuevas** |

Lo de prioridad 4–6 es OPCIONAL y requiere compilador → no se hizo de madrugada.

---

## 8. RE-ANÁLISIS (2026-06-22, tras la unificación de transporte)

**Tamaños reales hoy (tras refactor 2026-06-22):** 190 archivos `.kt`. Top: `WorldMapViewModel` (**1426**, era ~2129),
`NativeOsmMap` (1444), `WorldMapScreen` (1320), `MainActivity` (1058), `ZombieGameScreen` (1038),
`ZombieInteriorViewModel` (915), `NpcAiManager` (884), `SettingsScreen` (859), `WorldMapLeafletHtml` (838),
`TransitInteriorViewModel` (677). **Ya NINGÚN archivo > 1500** (el VM bajó de ~2129 a **1426**; ver abajo y 09 §0/§12).

**Qué se acaba de resolver:** la mayor duplicación estructural restante (Metro⇄Metrobús, VM+State) →
unificada en `TransitInteriorViewModel`/`TransitInteriorState`/`TransitSystemConfig` (§2.2). Verificado: las
4 pantallas/overlays compilan contra el VM/State unificados (todos los `state.*`/`viewModel.*`/`::` usados
existen como miembro neutro o alias), MainActivity sin cambios (firmas intactas), sin referencias colgantes a
los tipos viejos.

**Lo que QUEDA por separar/optimizar (prioridad valor/esfuerzo/riesgo):**

| # | Acción | Valor | Esfuerzo | Riesgo | Nota |
|---|--------|-------|----------|--------|------|
| A 🔮 | **TRABAJO FUTURO (pospuesto 2026-06-23, decisión del dueño).** Colapsar las 2 **pantallas** de transporte (`Metro/MetrobusStationInteriorScreen`, 691+610) en UNA `TransitStationScreen(config)` + los 2 **overlays** en uno. | Medio | Alto | Medio-alto | **NO hacer aún:** se agregarán más sistemas (Suburbano/Mexibús…) y el **Metrobús todavía no funciona bien**; colapsar DESPUÉS de estabilizarlo y tener más sistemas (si no, se unifica alrededor de una implementación con bugs). |
| ~~B~~ | ✅ **HECHO (2026-06-23)** Retirados los 14 **alias de compat** de `TransitInteriorState`/VM; usos en las 4 pantallas/overlays renombrados a neutros (`isVehicle1Animating`/`showTransitMap`/`allStations`/`closeTransitMap`/`onVehicle1AnimationFinished`); **borrados los 4 tombstones**. | Bajo | Bajo | Bajo | Hecho SIN A (pantallas siguen separadas, solo nombres neutros). |
| ~~C~~ | ✅ **HECHO (2026-06-22)** `WorldMapViewModel` **2129 → 1426** (<1500): de-dup de los 4 gemelos restantes (`checkCollectibleProximity`/`trySpawningCollectible`/`isInsideEscom`/`startMovementAction`) + 4 parciales nuevos (`WorldMapInteractions`/`Health`/`EscomItems`/`Movement.kt`). | Medio | Medio | Medio | Lo grande que queda (`startGameLoop` ~490) llama a la cadena de routing NO-TOCAR → no se extrajo. |
| ~~D~~ | ✅ **HECHO (2026-06-23)** `NavHost` + orquestación → **`AppNavGraph.kt`** (944 líneas). `MainActivity` **1077→252**. Recibe `activity`+ViewModels/authManager/campaignRepository como params; `this@MainActivity`→`activity`. | Bajo | Medio | Medio | — |
| ~~E~~ | ✅ **HECHO (2026-06-23)** Las 7 secciones → **`SettingsSections.kt`** (`internal`, 628 líneas). `SettingsScreen` **859→276**. | Bajo | Bajo | Bajo | — |
| ~~F~~ | ✅ **HECHO (2026-06-23)** `CampaignObjective.title/description`→`@StringRes` (8 claves ES+EN). Transporte: prompt metrobús + 10 `msg*` de `TransitSystemConfig`→`@StringRes` (10 claves) con `getLocalizedString` locale-aware nuevo en `TransitInteriorViewModel`. | Medio | Bajo-medio | Bajo | — |
| — | Micro-opts perf | — | — | — | Sin candidatas seguras nuevas (09 §6). |

**Recomendación (actualizada 2026-06-23):** **B, C, D, E, F HECHOS.** **A queda como 🔮 TRABAJO FUTURO** (decisión del
dueño): colapsar las 2 pantallas/overlays de transporte en una por config se hará MÁS ADELANTE, cuando el **Metrobús
funcione bien** y se hayan agregado más sistemas de transporte (Suburbano/Mexibús…) — no conviene unificar ahora alrededor
de una implementación con bugs. **No partir nada por estética.** ⚠️ Ver el GOTCHA del sandbox (09 §12) al tocar archivos grandes.

> ⚠️ Recordatorio del entorno: los 4 archivos nuevos (`TransitStation`, `TransitSystemConfig`,
> `TransitInteriorState`, `TransitInteriorViewModel`) se escribieron con fin de línea LF (los editados
> conservan CRLF). Kotlin compila ambos; si quieres uniformar a CRLF, normalízalos en Android Studio.

---

## 9. 🔮 ROADMAP a nivel "producción mantenida por seniors" (TRABAJO FUTURO)

> Decisión del dueño (2026-06-23): se harán TODAS estas mejoras para llevar el código a nivel de producción
> senior, como **trabajo futuro** (no bloquean nada hoy). Orden sugerido por valor/riesgo:

1. **Suite de tests automatizados** (la brecha más grande). Empezar por LÓGICA PURA con JUnit: routing/snap-to-road
   (distancias, nearestPointOnNetwork), save/load (SaveGameRepository round-trip), combate (daño/cooldowns),
   MissionCatalog/checkObjectiveProgress, colisiones. Luego UI con Compose UI Test (flujos de Modo Historia).
   **Gate de merge:** los tests deben pasar en cada PR.
2. **Descomponer el god-object `WorldMapViewModel`** (1426 líneas, un solo `_state` gigante + ~25 parciales que son
   EXTENSIONES de la MISMA clase → bajó el TAMAÑO pero NO el acoplamiento). Migrar a managers/use-cases con sub-estado
   propio (CombatManager, RoutingUseCase, CollectiblesManager, CampaignManager…; NpcAiManager/PoliceManager ya lo son),
   inyectados; el VM solo orquesta y compone sub-estados.
3. **Retirar el patrón miembro-vs-extensión gemela** como técnica de separación (es un code smell que causó bugs reales).
   No crear más gemelos; de-duplicar la cadena de routing "NO-TOCAR" (updateDestinationRoute/calculateRouteOnNetwork/
   nearbyRoadNodes/rebuildRoadNodeGrid) **con tests de respaldo** (hoy es frágil por falta de red de seguridad).
4. **Inyección de dependencias** (Hilt o Koin) en vez de `ViewModelProvider.Factory` manuales en todos lados.
5. **CI/CD completo** — ⏳ PARCIAL: ya se hace deploy a **Play Store prueba cerrada al APROBAR un PR** (workflow
   `android-release.yml`, ver 08 §3). Falta: correr **tests + lint (ktlint/detekt) en cada PR** como gate de merge + cobertura.
6. **Separación REAL (no parches) de los archivos grandes restantes**: `NativeOsmMap` (1498) y `WorldMapScreen` (1459)
   por responsabilidad / sub-composables con su propio estado, no por "extensiones de la misma clase".
7. **Item A** (colapsar pantallas/overlays de transporte por config) — ya marcado future work (§8); encaja aquí.
8. **Higiene**: normalizar EOL a CRLF consistente (hoy hay archivos LF), sacar magic numbers a constantes/config, y subir KDoc.

### AVANCE NOCHE 2026-06-24 (sesion autonoma; ver `CHANGELOG_NOCHE.md` en la raiz del repo)

> Que del roadmap de arriba quedo HECHO (seguro) vs en PLAN (riesgoso, requiere compilador):
> - **#1 Tests automatizados - INICIADO (seguro):** 3 suites JUnit de LOGICA PURA
>   (`CalculateLocalCoordinatesUseCaseTest`, `MissionCatalogTest`, `TransitSystemsTest`; 22 tests). NO
>   ejecutadas aqui (sin Android SDK); confirmar con `testDebugUnitTest`. Falta el grueso
>   (routing/save/combate/Compose UI Test).
> - **#2 Descomponer god-object - EN PLAN:** `PLAN_descomponer_WorldMapViewModel.md`.
> - **#3 Retirar gemelo + de-dup routing - EN PLAN:** `PLAN_dedup_routing.md` (tests de respaldo primero).
> - **#4 DI (Hilt) - EN PLAN:** `PLAN_DI_hilt.md`.
> - **#5 CI lint+tests como gate - HECHO (aditivo):** `detekt` (`config/detekt/detekt.yml`) + workflow
>   `.github/workflows/pr-quality-gate.yml` (testDebugUnitTest + detekt CLI en cada PR ABIERTO). NO toca
>   `android-release.yml`. Pendiente: baseline de detekt y (opcional) wiring del plugin Gradle (staged).
> - **#6 Separar archivos grandes restantes - sin cambios** (requiere compilador).
> - **#7 Item A (colapsar pantallas/overlays de transporte) - sigue FUTURO** (ver §2.2/§8).
> - **#8 Higiene - HECHO en su mayoria:** EOL CRLF normalizado (6 .kt LF->CRLF; 194/194 CRLF puros) +
>   `.editorconfig` en la raiz. KDoc de clase a `NpcAiManager` + 1 magic-number a const
>   (`DEFAULT_VALID_MARGIN`); resto de KDoc/const = checklist en el changelog (hacer en AS).

**Definición de "listo":** tests verdes en CI como gate de merge, VM sin god-object, sin gemelos, DI, y deploy
automatizado con calidad (lint+tests). Ahí el codebase pasa de "proyecto fuerte de un dev hábil" a "producción senior".
