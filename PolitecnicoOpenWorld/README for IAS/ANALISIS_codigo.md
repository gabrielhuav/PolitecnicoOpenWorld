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
> - Sigue **pendiente/opcional** (sin tocar): base común Metro⇄Metrobús (la mayor duplicación restante),
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

### 2.2 Metro ⇄ Metrobús (duplicación estructural, NO accidental)
`MetroInteriorViewModel` (700) y `MetrobusInteriorViewModel` (655) comparten ~mucha mecánica
(rejilla, waypoints, diseñador de matrices, navegación). Igual `MetroStationInteriorScreen` (669) ⇄
`MetrobusStationInteriorScreen` (589), y `MetroMapOverlay` (459) ⇄ `MetrobusMapOverlay`. **Oportunidad
de medio/alto esfuerzo:** extraer una base común (`TransitInteriorViewModel` / `TransitStationScreen`
parametrizada por sistema). **Riesgo alto** (toca dos features de transporte, requiere compilar y
probar ambos sistemas). Solo si vas a tocar transporte a fondo; hoy NO es urgente.

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
| 5 | (Opcional) Base común Metro⇄Metrobús (`TransitInteriorViewModel`) | Medio | Alto | Alto |
| 6 | (Opcional) Extraer `NavHost` de `MainActivity` a `AppNavGraph.kt` | Bajo | Medio | Medio |
| — | Micro-opts perf adicionales | — | — | **Sin candidatas seguras nuevas** |

Lo de prioridad 4–6 es OPCIONAL y requiere compilador → no se hizo de madrugada.
