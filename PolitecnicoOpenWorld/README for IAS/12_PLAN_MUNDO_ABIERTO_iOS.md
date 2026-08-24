# 🌎🍏 Portar el MUNDO ABIERTO a iOS — plan medido

**Creado:** 2026-07-30 · Todo número de aquí **sale de un comando que se ejecutó**.

> **Para quién es:** para quien continúe la migración. Dice qué está hecho, qué falta y **en qué
> orden**. El peso de los assets y los límites de cada tienda están en su propio documento:
> **[`13_ASSETS_Y_TAMANO.md`](13_ASSETS_Y_TAMANO.md)**.
>
> El equivalente para el modo pelea (ya terminado) es `_ARCHIVO/PLAN_SF_EN_iOS.md`.

---

## 0. El resumen, si solo lees una sección

**El mundo abierto son 30 302 líneas — 2,4 veces el modo pelea. Eso es el trabajo real.**

| | Medido |
|---|---:|
| Bundle iOS actual (solo SF) | **196 MB** |
| Assets que sumaría el mundo | **+203 MB** |
| Bundle iOS con el mundo | **399 MB** |
| Límite duro del App Store | **4 GB** ✅ cabe de sobra |
| Aviso de Apple por datos móviles | 200 MB (⚠️ solo un aviso) |
| **Límite de Google Play (módulo base)** | **500 MB** ← el que aprieta |

> ### ⚠️ CORRECCIÓN (07-30, más tarde): el tamaño NO bloquea iOS
>
> La primera versión de este documento decía que el mundo "no cabe" en iOS por los 200 MB. **Era
> falso.** El límite duro del App Store son **4 GB**; los 200 MB son un aviso de descarga por datos
> móviles que, **desde iOS 13, el usuario puede desactivar**. Con 399 MB estaríamos al 10 % del
> límite.
>
> **Quien aprieta de verdad es Google Play, con 500 MB de módulo base**, y ahí el AAB ya va por
> **416 MB**. Números, límites y fuentes en **[`13_ASSETS_Y_TAMANO.md`](13_ASSETS_Y_TAMANO.md)**.
>
> On-Demand Resources sigue siendo *deseable* en iOS (mejora la conversión de instalación), pero
> **no es un requisito para publicar** ni bloquea ninguna fase. **La ruta crítica es el código.**

---

## 1. Cuánto es "el mundo abierto" (medido 2026-07-30)

| Paquete | Archivos | Líneas |
|---|---:|---:|
| `features/map_exterior` (exteriores) | 80 | 18 766 |
| `features/interiores` (interiores + zombis + metro) | 44 | 11 536 |
| **Total a portar** | **124** | **30 302** |

Para comparar: **el modo pelea entero fueron ~12 400 líneas**. El mundo abierto es **2,4 veces
más grande**, y encima trae dos bloqueadores que SF no tenía: **osmdroid** y **`R.string`**.

### Bloqueadores contados

| Bloqueador | Archivos | Por qué duele |
|---|---:|---|
| `R.string` / `R.drawable` | **50** | `R` es de Android. Hay que migrarlos a `composeResources`, como se hizo con Ajustes |
| Hilt (`@HiltViewModel`, `@Inject`) | **13** | Solo Android. Se resuelve con el patrón Controller (§4 del doc 11) |
| `org.osmdroid` | **7** | Solo Android. **Ya hay salida**: el mapa Leaflet en WebView, verificado en iOS |

---

## 2. Fases

### ✅ Fase 1 — Paridad de menús (HECHA, verificada en simulador 07-30)

Los dos menús pintan lo mismo. En iOS, MUNDO LIBRE / MODO HISTORIA / MULTIJUGADOR salen marcados
**EN OBRAS** y, al pulsarlos, **avisan en vez de navegar**.

- Catálogo: `PowModos.kt` → `modosEnObrasDe()`, `enObras()`, `sePinta()`.
- UI: `MainMenuBotonDeModo.kt` (el botón sabe si su modo está en obras).
- **Se apaga entero con `MODOS_EN_OBRAS_VISIBLES = false`.** ⚠️ Hay que hacerlo **antes de firmar
  para la App Store**: Apple rechaza funciones anunciadas que no funcionan (Guideline 2.1).
- 7 tests nuevos lo fijan, incluido uno que comprueba el propio interruptor.

⚠️ **`disponible()` NO cambió.** Sigue respondiendo "¿se puede jugar?", que es lo que consultan
Ajustes y la navegación. Un modo en obras es `sePinta() == true` y `disponible() == false`.

### ✅ Fase 2 — El dominio puro a `commonMain` (HECHA, 07-30)

**22 archivos, ~1 500 líneas** movidos (19 de modelos + `RoadRouter`,
`CalculateLocalCoordinatesUseCase` y `KeyDrop`). El paquete es idéntico en los dos módulos, así que
**ningún import cambió en ninguna parte**.

Movido: `map/` (MapWay, MapNode, NpcType, Metro/Metrobus/TransitStation, ShineCTOLocation,
TeleportCatalog, InteriorEntryCatalog, ExteriorCollisionsConfig, Landmark, EscomBuildings,
CampusParkingCatalog, Npc, CharacterVisualConfig), `ai/` (PrankedyState, LocalNavModels),
`campaign/` (SchoolCatalog, StoryComicCatalog).

Dos cosas hubo que tocar, y las dos son el patrón a repetir:

1. **`java.lang.Math` no existe en Kotlin/Native** → `kotlin.math`. Mismos valores (Double IEEE-754),
   la geometría no cambia ni un metro. `Math.toRadians(x)` es `x * PI / 180.0`.
2. **`CampusParkingCatalog.loadCalibration` recibía un `Context`** → ahora lee por `PowAssets`.
   En Android es el mismo `AssetManager` y el mismo archivo.
   ⚠️ También hubo que actualizar su única llamada, en `ZombieGameScreen.kt`.

### ✅ Fase 3 — Los gestores de IA (COMPLETA, 2026-08-15)

| Gestor | Líneas | Estado |
|---|---:|---|
| `PoliceManager` | 404 | ✅ en `commonMain`, **con 10 tests nuevos** |
| `CampaignEscortPolice` | 402 | ✅ en `commonMain` |
| `PrankedyManager` | 624 | ✅ en `commonMain` |
| `NpcAiManager` | 988 | ✅ en `commonMain`, **con 7 tests nuevos** |
| `NpcAiManagerTraffic` | 565 | ✅ (fueron los tres juntos, como estaba previsto) |
| `NpcAiManagerMovement` | 224 | ✅ |

#### Las costuras que hubo que crear (y por qué no valía nada más simple)

| Lo que había | Lo que hay | La razón, en corto |
|---|---|---|
| `CopyOnWriteArrayList` | **`PowListaConcurrente`** | Implementa `MutableList` → los ~60 call-sites no se tocan. `iterator()` recorre una COPIA, igual que el original. |
| `ConcurrentHashMap.newKeySet()` | **`PowConjuntoConcurrente`** | Mismo patrón, para `populatedLandmarks`. |
| `AtomicReference` | **`PowRef`** | Métodos `get`/`set` a propósito: sustituir es cambiar el tipo. |
| `System.currentTimeMillis()` | **`ahoraMs()`** (`PowReloj`) | ⚠️ **De ÉPOCA, no monótono.** Ver abajo. |
| `android.graphics.Color.rgb` | **`colorArgb`** | Mismos bits ARGB; `Npc.carColor` sigue siendo `Int`. |
| `android.util.Log` | **`powLog`** (`expect/actual`) | logcat en Android, `println` en iOS. |

> ### ⚠️ El reloj tenía que ser DE ÉPOCA, y por poco no se ve
>
> Lo natural era `TimeSource.Monotonic`, que es lo que ya hacía `PoliceManager`. **Habría roto el
> juego en silencio.** `NpcAiManager` escribe `Npc.fearUntil` y `aggroUntil`, y quien las compara
> es el `WorldMapViewModel`, que sigue en `:app` con `System.currentTimeMillis()`. Dos relojes con
> orígenes distintos = `aggroUntil` de cinco mil contra un `now` de un billón y medio: **todos los
> NPCs pierden miedo y agresión al instante**. No lo caza el compilador ni un test; se ve jugando.
> `kotlin.time.Clock` da el mismo valor que `System.currentTimeMillis()` en las dos plataformas.
> Hay un test que lo fija (`fearUntil queda en el futuro cercano del reloj de epoca`).

> ### ⚠️ `synchronized(lista)` desde fuera ya NO protege nada
>
> `pendingDespawns` y `pendingPoliceShots` se drenaban desde `:app` con
> `synchronized(lista) { toList(); clear() }`. Ese candado **no excluye al cerrojo interno** de
> `PowListaConcurrente`, así que compila y deja una ventana por la que se pierden despawns — y un
> despawn perdido no se ve aquí, se ve en los OTROS clientes, con un NPC fantasma. Por eso existe
> **`drenar()`**, que lee y vacía sin soltar el cerrojo. Los 5 call-sites de `:app` ya lo usan.

> ### 🐞 Por qué esta clase tenía 0 tests
>
> Al escribir la red de seguridad ANTES de migrar (como pide esta fase), los 7 tests fallaron con
> `ExceptionInInitializerError`: `CAR_COLORS` llamaba a `android.graphics.Color.rgb(...)` en un
> inicializador de campo, y en un test JVM sin Robolectric eso **lanza**. O sea: la clase no se
> podía ni construir en un test. Al pasar a `colorArgb` sí se puede, así que la red existe desde
> la migración y no antes. También hubo que poner `isReturnDefaultValues = true` en los host tests
> de `:shared`, porque el `actual` Android de `powLog` toca `android.util.Log`.

#### 🔐 La herramienta que hizo esto seguro: `PowMapaConcurrente`

`ConcurrentHashMap` no existe en Kotlin/Native, y **no era decorativo**: MEDIDO, a `PoliceManager`
se le llama desde `Dispatchers.Default` (bucle), `IO` (red) y `Main` (toques). Las carreras son
reales.

En vez de envolver a mano ~50 accesos por archivo —donde el compilador **no avisa si te dejas uno**—
se creó `PowMapaConcurrente` (mapa + `PowCerrojo`) con la misma semántica. Así la migración es
**un cambio de tipo**, no una redecisión por cada uso:

```
ConcurrentHashMap<K,V>()  →  PowMapaConcurrente<K,V>()
.values → .valores   .keys → .claves   .remove() → .quitar()
.clear() → .limpiar()   .isEmpty() → .estaVacio()   mapa[k] igual
```

Tiene **9 tests de semántica** en `commonTest` (las dos plataformas) y **5 de carreras con hilos de
verdad** en `:app`. ⚠️ Se diferencia en dos cosas de `ConcurrentHashMap`, y están documentadas en su
cabecera: **un solo cerrojo** (bien: son decenas de entradas, no millones) y **`valores`/`claves`
devuelven una COPIA** (más seguro: recorrerlas nunca lanza `ConcurrentModificationException`).

#### 🐞 Un crash latente que salió solo

Al pasar de `ConcurrentHashMap` (tipos de plataforma, acepta `null`) a una API tipada, el compilador
señaló que `PoliceManager` buscaba con `unit.policeCarId`, que es `String?`. **`ConcurrentHashMap.get(null)`
lanza NPE.** Nunca saltó porque en la práctica siempre venía con valor, pero era un crash esperando.
Corregido: sin patrulla a la que volver, el policía se retira — que es lo que ya hacía esa rama.

#### ✅ La fase quedó CERRADA en el emulador (2026-08-16, Windows)

`NpcAiManager` y sus dos parciales usaban además `CopyOnWriteArrayList` y `AtomicReference`. Se
escribieron los tests de sus reglas ANTES de tocarlo (como con `PoliceManager`) y **el mundo se jugó
en el emulador**, que es lo que ningún test caza:

- **~35 min de mundo abierto** en el AVD `Nexus` (API 35), con los DOS renderers (web CARTO y
  **OSM nativo**): peatones y tráfico siguen apareciendo, moviéndose, aparcando y despawneando por
  distancia. Medido en 12 ciclos de recorrido + 48 pulsaciones de entrar/salir de coche. **Ningún
  coche fantasma, ninguna excepción, ningún crash.**
- ⚠️ **Lo que este cliente NO puede probar y hay que decir:** `pendingDespawns.drenar()` vive dentro
  de `webSocketManager?.let { … isServerDelegatedHost }` (`WorldMapViewModel` ~1063), o sea **solo
  se ejecuta como HOST de multijugador**. En un jugador la lista ni se drena ni se limpia — igual
  que antes de la migración, no es una regresión, pero significa que **el NPC fantasma solo se
  puede ver con dos clientes y el servidor del mundo**. Lo que SÍ corre offline es
  `pendingPoliceShots.drenar()` (mismo VM, fuera del bloque de red).

### 🟡 Fase 4 — `R.string` → `composeResources` · **LA CAMPAÑA YA ESTÁ (08-14)**

Mecánico y sin riesgo, pero largo. La receta ya está probada: es lo que se hizo con Ajustes
(`SettingsSections`). Se puede repartir por features.

> ## ✅ Los 42 strings de campaña, hechos — que eran el ESLABÓN de la cadena
>
> Se movieron los 42 (ES **y** EN) a `composeResources` y se **borraron de `res/`**: una sola
> fuente de verdad, porque no los usaba nadie más (medido: 0 usos fuera de
> `domain/models/campaign/`). Con ellos bajaron a `commonMain` los **6 archivos de campaña**
> (`CampaignObjective`, `MissionCatalog`, `Mission1/2/3`, `SideMissions`) conservando el paquete
> → **ningún import de `:app` cambió**.
>
> `CampaignObjective.titleRes` y `CampaignMissionInfo.titleRes` pasaron de `@StringRes Int` a
> **`StringResource`**. Consumidores: 4 de UI (alias `stringResourceComun`, porque esos dos
> archivos aún mezclan `R.string` propios) y 5 del ViewModel.
>
> ### ⚠️ Lo que este cambio enseña y vale para los otros 44 archivos
>
> **Fuera de un `@Composable` no hay forma SÍNCRONA de resolver un `StringResource`.** Compose
> Resources solo ofrece `getString`, que es `suspend`; `getLocalizedString(R.string.x)` sí era
> síncrono porque bajaba a `Context`. En el VM eso se resuelve con `WorldMapAvisos.kt`, y la
> regla es la que está escrita en su cabecera: **la BANDERA se pone ya y el TEXTO llega un frame
> después**. Si `objectiveDone` viajara dentro del `launch`, el tick que la lee vería el valor
> viejo y la misión avanzaría tarde. **No metas banderas en ese helper.**
>
> ⚠️ Al copiar los valores hay que **quitar el `\'`**: `composeResources` no des-escapa (doc 11
> §8bis nº2). Se hizo en el paso de migración.

### 🟡 Fase 5 — `WorldMapViewModel` + sus 22 parciales · **UI DE CONTROLES HECHA (07-31)**

> ## ✅ El HUD del mundo ya está en iOS, y es el MISMO que Android
>
> `HudMundoIos.kt` monta **`JoystickController` + `ActionButtonsController`**, los dos de
> `commonMain`: mismos colores, mismas letras, disposición Xbox (Y arriba · X izq · B der · A abajo)
> y el mismo tacto. **No hay una copia para iOS.**
>
> Para conseguirlo se movieron a `commonMain`:
> - `Direction` y `GameAction` → `ControlesMundo.kt` *(el resto de `WorldMapMultiplayerModels.kt`
>   se queda en `:app`: es de red y el multijugador no se porta)*.
> - `ActionButtonsController` (el diamante). De paso se quitó `onClaimCollectiblePressed`, que
>   estaba declarado y **no lo usaba nadie** — había 5 llamadas pasándolo en balde.
>
> ### ✅ 2026-08-17: los controles ya son IDÉNTICOS — se arregló la ORIENTACIÓN, no el tamaño
>
> Hasta esa fecha iOS pintaba el HUD a **100 dp** porque no forzaba horizontal (Android sí), y dos
> controles de 180 dp no caben en los ~390 dp de un iPhone en vertical. Es decir: **los controles
> NO eran iguales**, que es justo lo que este HUD promete.
>
> Se arregló por la causa, no por el síntoma: iOS ahora **fuerza horizontal en el mundo** con
> `ForzarHorizontal()` (doc 11 §4quater) y el HUD volvió a `ControllerBaseSize`, sin pasar `tamano`.
> El tombstone está en `HudMundoIos.kt`: **no recrear `TAMANO_VERTICAL`**; si algún día se ve
> apretado, lo que falla es el bloqueo de orientación.
>
> ### ⚠️ Sigue vigente: `Modifier.scale()` NO sirve para encoger un control
>
> El primer intento (07-31) fue `Modifier.scale(0.55f)` y **los botones dejaron de responder**:
> `scale` transforma el DIBUJO, no el área táctil, así que el control quedaba donde ya no se veía.
> Si alguna vez hay que cambiar el tamaño de verdad, los controles aceptan un **tamaño real**:
> `JoystickController(tamano = …)`, `ActionButtonsController(tamano = …)`, `ActionButton(tamano = …)`;
> por dentro el stick y los botones son **proporcionales al diámetro**, así que el tacto no cambia.
>
> ### Los botones que aún no hacen nada, lo dicen
>
> A/B/X/Y muestran *"Botón X: pendiente del ViewModel"* durante 4 s. Un botón mudo parece una app
> rota; uno que explica por qué, no.

#### Lo que queda de la fase: el ViewModel (~5 000 líneas)

> ## ✅ LA CADENA DE BLOQUEO — ROTA el 2026-08-14
>
> El eslabón (`CampaignObjective` → `@StringRes Int` → 42 strings) ya está en `commonMain`; ver
> la fase 4. Lo de abajo se conserva porque explica **por qué** la fase 4 iba antes que la 5.
>
> Se intentó mover `WorldMapState.kt` (335 líneas, el estado del VM) y **falló por UN símbolo**.
> Esto es lo que había, y explica por qué la fase 4 va ANTES que la 5:
>
> ```
> WorldMapState  ──necesita──►  CampaignObjective
>                                     │
>                                     └── @StringRes val titleRes: Int   ← R.string: no existe en commonMain
>                                              │
>                                              └── lo definen Mission1/2/3, SideMissions, MissionCatalog
>                                                       │
>                                                       └── 42 strings, y NINGUNO está en composeResources
> ```
>
> **La buena noticia:** todo lo DEMÁS que necesita `WorldMapState` ya está en `commonMain` (GeoPoint,
> Npc, Landmark, CarModel, InteriorBuilding, PlayerAction, PlayerSkin, ControlType,
> PrankedyAnimState, ActiveCollectible). Falta ese único eslabón.
>
> ### El acoplamiento del VM es MENOR de lo que parecía
>
> `WorldMapViewModel.kt` (1596 líneas) tiene solo **4 imports de plataforma**, y uno ni cuenta:
>
> | Import | Cuánto duele |
> |---|---|
> | `androidx.compose.ui.graphics.toArgb` | 🟢 **Nada**: es Compose Multiplatform |
> | `androidx.lifecycle.viewModelScope` | 🟢 **Ya resuelto**: existe `PowViewModel` (expect/actual) |
> | `android.util.Log` | 🟡 Trivial de sustituir |
> | `android.content.Context` | 🟠 16 usos, pero solo **3 cosas**: `SoundManager`, `getLocalizedString` y cargar colisiones — y **lo de colisiones YA está portado** (`cargarColisionesExteriores()`) |
>
> Más `@HiltViewModel` con 5 dependencias y `TileCache` (solo-Android: intercepta las teselas del
> WebView). Todo eso es exactamente para lo que existe el patrón **Environment** (doc 10 §2bis,
> mecanismo 3), como `StreetFighterEnvironment`.
>
> ### ⚠️ Por qué NO se hizo el 07-31
>
> El eslabón que falta son **42 strings de misión** a `composeResources` y **17 archivos** que
> consumen `CampaignObjective`. Es mecánico, **pero el fallo típico es silencioso**: una misión
> muestra el texto equivocado. Eso no lo caza ningún test ni el compilador — **se ve jugando**, y en
> este Mac **no hay AVD** (medido).
>
> **Se descartó el atajo** de quitar `@StringRes` y dejar `titleRes: Int` en `commonMain`. Habría
> compilado y Android no cambiaría, pero mete un id de recurso de Android en el módulo compartido:
> justo lo que prohíbe `10 §2bis`. Habría parecido progreso sin serlo.
>
> ### ✅ 2026-08-20: pasos 1-4 HECHOS. Queda el 5 y el 6.

**Los 14 textos del mundo ya están en `composeResources`** (ES + EN) y los 17 puntos de llamada
convertidos. `getLocalizedString(resId, ...)` **ya no existe** en el mundo abierto: hay un tombstone
en `WorldMapViewModel.kt`. Con eso cae el último `R.string` del ViewModel.

Cómo se convirtió cada caso, que no es mecánico del todo:

| Caso | Qué se hizo |
|---|---|
| Ya estaba dentro de una corrutina (7 de coleccionables, horda) | `getString(Res.string.x)` directo |
| Solo publica `interactionPrompt` (teletransporte) | Se metió dentro del `launch` que ya había |
| Publica texto **y una bandera** (Prankedy) | **La bandera va síncrona; solo el texto se difiere** |
| Lo pide el game loop **en cada tick** (carjack) | Se **precarga una vez** en el `init` (`textoCarjack`) |
| `Toast` de Android (3, en ESCOM) | Pasan por `WorldMapEnvironment.avisar()` |

🐛 **Y se arregló un fallo latente de Android por el camino:** el aviso de la horda se auto-limpiaba
comparando con el literal **en español**, así que jugando en inglés no se borraba nunca. Ahora se
compara con el texto ya resuelto.

**`WorldMapEnvironment` existe** (`commonMain`) con su `AndroidWorldMapEnvironment` y su binding de
Hilt. Es deliberadamente pequeño —`factorDeGama`, `avisar`, `guardarTexto`, `leerTexto`— porque al
medirlo **la mayoría de los 16 usos de `Context` eran `context.assets.open(...)`**, y para eso ya
estaba `PowAssets`. `computeDeviceTierFactor` se mudó al lado Android.

Cómo quedó el acoplamiento del mundo con la plataforma:

| Símbolo | Antes | Ahora |
|---|---|---|
| `R.string` en el VM | 17 llamadas | **0** |
| `context.assets` | 4 | **0** (→ `PowAssets`) |
| `android.content.Context` | 6 imports | 7, pero **solo** en el Modo Diseñador (SAF), el guardado y el entorno |

⏭️ **Lo que queda de la fase 5**, en orden: `viewModelScope` (18) → `PowViewModel.scope`;
`android.util.Log` (7) → `powLog`; `AndroidViewModel` → `PowViewModel`; Hilt → patrón Controller;
`TileCache` y `WebSocketManager` (los dos solo-Android) al entorno o fuera; el SAF del Modo
Diseñador a `guardarTexto`/`leerTexto` (la costura ya existe, falta engancharla);
`MetroRepository`/`MetrobusRepository` (usan `R.raw` + `org.json`); y por último **mover los 43
archivos**.

✅ **El paso 6 YA SE HIZO** (2026-08-21, Windows, AVD `Nexus` API 35, en español **y** en inglés).
Salió **verde: ninguna regresión**. El informe completo, con lo que quedó sin jugar y las recetas
para repetirlo, está en **`RESULTADO_WINDOWS_verificar_android_fase5.md`**. Titulares:

- El aviso de la horda **ya se auto-limpia en inglés** (medido con 60 sondas en ~6 min).
- Los textos con `%1$s` (metro) y la puerta ESCOM salen bien **en los dos idiomas**.
- Coches, peatones y patrullas sin diferencias; caché `MISS → GUARDADO → HIT` con los mismos
  **2512 ways** que iOS; población **39** como iOS; bardas y `escom_navgraph` cargando.
- 🔴 Sin jugar: **carjack** (§2.3-8) y **Modo Historia** (§2.7-18).
- 🔴 **`wm_press_x_(de)activate_zombie` son inalcanzables**: `spawnEscomItems` borra la mano del
  apocalipsis a propósito. Son 2 de los 14 textos y hoy son código muerto.

### Orden correcto para quien siga, CON EMULATOR DELANTE
>
> 1. Los 42 strings de campaña (ES + EN) a `composeResources`.
> 2. `CampaignObjective.titleRes: Int` → `StringResource`, y detrás los 17 consumidores (el
>    compilador los va listando).
> 3. `WorldMapState.kt` → `commonMain`: ya no tendrá nada que lo ate.
> 4. `WorldMapEnvironment` para `SoundManager` + `getLocalizedString` + `TileCache`.
> 5. La clase y sus **22 parciales, todos de una vez** — no se pueden separar (`10 §4`).
> 6. **Jugar el mundo abierto en Android media hora.**

⚠️ **Los 22 parciales NO se pueden mover sueltos**: son funciones de extensión de la clase, así que
o se mueve la clase o no compila ninguno. Ver la regla del patrón parcial en `10 §4`.

El VM necesita Hilt y `Context` → **patrón Controller**, igual que `StreetFighterController`.

### 🟡 Fase 6 — La UI (~8 000 líneas) y el mapa · **EL MAPA YA SE VE (07-30)**

> ## ✅ El mapa corre en iOS y es interactivo
>
> `MapaMundoIos.kt` (iosMain) mete el `WKWebView` dentro de Compose con **`UIKitView`**, y le carga
> el HTML de **`buildHtml(...)`** — la MISMA función de `commonMain` que usa Android. Verificado en
> el simulador: teselas de OSM reales sobre ESCOM/Zacatenco, **arrastre y pinch-zoom**, y hasta el
> efecto de niebla del juego.
>
> **Cómo se llega:** MUNDO LIBRE sigue marcado **EN OBRAS**, pero su botón abre la vista previa en
> vez del aviso. Lo decide `MainMenuController.mundoTieneVistaPrevia` (`true` solo en iOS), así que
> **la pantalla del menú no sabe en qué plataforma corre**. El interruptor
> `MODOS_EN_OBRAS_VISIBLES = false` lo sigue apagando todo de una vez.
>
> ### ✅ Y YA SE CAMINA (07-31)
>
> `PuenteMapaIos.kt` es el puente **Kotlin → JS**: llama a las MISMAS funciones que Android
> (`updatePlayerMarker`, `updateMapView`, `setPlayerFog`). Con un pad de dirección, el jugador se
> mueve, **la cámara lo sigue y la niebla de guerra se abre a su paso**. Verificado en simulador.
>
> El movimiento es `GeoPoint.desplazado(metrosNorte, metrosEste)` — matemática pura en `commonMain`,
> con **7 tests**. ⚠️ Lleva el `cos(latitud)` en el eje este a propósito: sin él, el jugador correría
> más rápido en horizontal que en vertical y no se notaría hasta caminar en diagonal.
>
> ## ✅ Y YA HAY NPCs, TRÁFICO Y VIDA (2026-08-15) — verificado en el simulador
>
> `MapaMundoIos` monta el **mismo `NpcAiManager` que Android**: baja las calles con
> `OverpassRepository` (ya multiplataforma), le da un tick a ~30 Hz y empuja el resultado por
> `PuenteMapaIos.actualizarNpcs`. **Medido en el simulador: 39 NPCs** —peatones y coches— con
> spawn y despawn por distancia mientras el jugador camina.
>
> ### Las tres trampas de este enganche, las tres invisibles
>
> 1. **`getServerNpcs()`, NO el flujo `npcs`.** El `StateFlow` `npcs` solo lo escribe
>    `setRemoteNpcs`, o sea los NPCs que llegan por RED. Los que simula la IA de este cliente
>    viven en `serverNpcs`. Leyendo el flujo el mundo sale VACÍO para siempre y no hay ni un error:
>    el tick corre, spawnea y mueve, y la pantalla recibe una lista vacía.
> 2. **`getServerNpcs()` devuelve la lista VIVA, no una foto** → hay que hacerle `.toList()` antes
>    de meterla en un estado de Compose. Si no, es siempre la misma referencia, Compose no ve
>    cambio y **el contador se queda clavado** (decía "2" con 25 NPCs en pantalla).
> 3. **El `type` que entiende el JS no es el `NpcType` del juego.** `CAR`/`MODULAR` caen en un
>    respaldo de EMOJI (🚗/🧍) cuando no hay sprite; cualquier otro valor intenta cargar
>    `SPRITES/ICONS/<x>.svg`, que **no está en el bundle de iOS** → se pinta el "?" de imagen rota.
>    Se manda siempre `CAR`/`MODULAR` sin `imageKey` hasta que los sprites viajen al bundle.
>
> ⚠️ **Y el zoom del mundo en iOS es 17, no 16, por obligación:** `updateNpcs` del HTML no dibuja
> NI UN NPC por debajo de **16.5** (`isZoomedIn`). A 16 se manda todo bien y no aparece nada.
>
> ⚠️ **Falta la caché de calles.** Android guarda la red en Room (celdas de 2 km, TTL 7 días);
> iOS pide a Overpass en cada arranque y **se come 429 con facilidad** (pasó en la 2ª prueba). Hay
> reintento con espera creciente y el cartel lo dice en pantalla, pero **la caché es lo siguiente**.
>
> ⚠️ **Lo que falta para que sea jugable:**
> - **Policía, coleccionables, landmarks.** Hay función JS para todos (`updatePolice`,
>   `updateCollectibles`, `updateLandmarks`); quien las alimenta es el
>   `WorldMapViewModel`, que sigue en `:app` — **fase 5**.
> - ~~**Colisiones**~~ ✅ **HECHO (07-31)**: `cargarColisionesExteriores()` +
>   `chocaAlMoverse()` en `commonMain`, con **8 tests**. Lee `assets/CONFIG/exterior_collisions.json`
>   por `PowAssets`, así que sirve a las dos plataformas sin arrastrar el ViewModel.
>   ⚠️ Comprueba el **TRAYECTO**, no solo el destino: si no, un paso largo atraviesa la barda de un
>   salto. Hay un test que lo fija.
>   ⚠️ Si el JSON falta devuelve configuración **vacía** (se puede atravesar todo) en vez de lanzar:
>   un mundo sin bardas es mejor que un crash al entrar. Por eso `CONFIG/` se añadió al `rsync`
>   del bundle iOS (80 KB).
> - ~~**La VUELTA del puente (JS → Kotlin)**~~ ✅ **HECHA (2026-08-17)**: `PuenteJsIos` implementa
>   `WKScriptMessageHandlerProtocol`. Hoy la usa el **toque del mapa**, que coloca el marcador de
>   destino igual que en Android; zoom y arrastre ya llegan y solo falta a quién dárselos (fase 5).
>   🔑 **El HTML compartido NO se tocó.** Sigue llamando a `window.Android.notifyX(...)`, y en iOS
>   ese objeto lo crea un `WKUserScript` (`PUENTE_JS_SHIM`) que reenvía a
>   `webkit.messageHandlers`. Nada de `if (iOS)` dentro del mapa.
>   ⚠️ **El mensaje viaja como STRING** (`fn|arg|arg`), no como objeto: un objeto JS llega como
>   `NSDictionary` con `NSNumber` dentro y ahí es donde aparecen las sorpresas de tipos.
>   ⚠️ **`notifyMapClick` solo se dispara si el modo "colocar destino" está encendido**
>   (`updateDestinationPlacingMode`), y el JS lo apaga tras cada toque → hay que re-encenderlo.
>   Mismo contrato que Android; si se olvida, los toques se pierden **sin ningún error**.
>   ⚠️ Las constantes van a **nivel de archivo y no en un `companion`**: `PuenteJsIos` hereda de
>   `NSObject` y Kotlin/Native corta con *"Fields are not supported for Companion of subclass of
>   ObjC type"*.
>
> ⚠️ Los assets del mundo tampoco están en el bundle de iOS (solo SF y coleccionables). Hoy da
> igual —sin inyección de datos el HTML no pide ni una imagen—, pero en cuanto haya puente habrá
> que registrar un manejador del esquema `pow-asset://`. Ya está escrito y verificado en
> `iosApp/POW/MapaWeb.swift`.

Lo que sigue faltando de la fase:

`WorldMapScreen` (1463) · `NativeOsmMap` (1458, **osmdroid: se queda en `:app` como extra
solo-Android**) · overlays · **y el puente JS ↔ nativo**, que es lo que de verdad falta.

### 🔜 Fase 7 — Interiores y zombis (11 536 líneas)

Depende de las fases 3–6. `CollisionGrid` y los catálogos de sala son casi puros.

### ✅ Fase 8bis — Sprites del mundo en iOS (HECHA, 2026-08-20)

El mapa de iOS ya pinta **coches y peatones de verdad**, no emoji, y con el MISMO cálculo que
Android — que bajó a `commonMain` en el mismo commit:

| Qué | Dónde vive ahora | Quién lo usa |
|---|---|---|
| Repintado de carrocería | `TintadoVehiculo.kt` (`commonMain`) | `VehicleSpriteManager` (Android) + `TintadoWebIos` (iOS) |
| Repintado de ropa y pelo + composición | `TintadoPersonaje.kt` (`commonMain`) | `CharacterSpriteManager` (Android) + `PersonajeWebIos` (iOS) |
| Caché de calles de Overpass | `RoadNetworkCache` (`commonMain`) | `WorldMapViewModel` (Android) + `MapaMundoIos` (iOS) |

**18 tests nuevos** en `commonTest` cubren los dos repintados, que antes no tenían ninguno en
ninguna plataforma.

⚠️ **La diferencia de transporte, que no es un atajo:** Android compone el bitmap y lo manda al
WebView como base64 porque **no puede servirle ficheros**; iOS sí puede (`AssetsWebIos`), así que
mete una URL en `imgCache` y genera el PNG del otro lado. El cálculo es el mismo; lo que cambia es
por dónde viaja el resultado.

Coste en el bundle: **2 MB** (`ICONS` 8 KB + `VEHICLES` 1,6 MB + `npc_walk_1` 356 KB + `hair`
68 KB). No hacen falta los 70 MB de `SPRITES/NPC`: el spawner compartido viste a todos los NPCs con
un cuerpo y cinco peinados.

⏭️ **Lo que queda del apartado visual:** policía, coleccionables y landmarks — ya hay funciones JS
para los tres (`updatePolice`, `updateCollectibles`, `updateLandmarks`), y quien las alimenta es el
`WorldMapViewModel`, o sea la **fase 5**.

---

### 🔜 Fase 8 — Assets (opcional, NO bloquea)

On-Demand Resources en iOS, para no pedir 399 MB de golpe. **No es requisito para publicar**
(§0): el límite del App Store son 4 GB. Es una mejora de conversión de instalación.

Lo que **sí** es urgente es el peso del **AAB de Android**, que va por 416 MB contra el tope de
500 MB de Play. Plan de adelgazamiento medido en **[`13_ASSETS_Y_TAMANO.md`](13_ASSETS_Y_TAMANO.md)**:
142 PNG → WebP (~60 MB) y la música de fondo → Ogg (~23 MB).

---

## 3. Tamaño del AAB de Android — vigilado

**MEDIDO 2026-07-30 con `:app:bundleRelease`: 416 MB.** Tope del CI: 500 MB → **quedan 84 MB**.

| Carpeta | MB | % |
|---|---:|---:|
| `assets/STREETFIGHTER` | 112,2 | 27,0 % |
| `assets/SPRITES` | 105,7 | 25,4 % |
| `assets/AUDIO` | 45,8 | 11,0 % |
| `assets/BUILDINGS` | 31,3 | 7,5 % |
| resto | 121 | 29 % |

El workflow ahora **avisa a los 450 MB** y publica este desglose en el resumen del run, para que
cuando crezca se sepa qué carpeta lo hizo crecer sin tener que reproducirlo en local.

📘 **El plan de adelgazamiento, con los límites de cada tienda verificados en la fuente, está en
[`13_ASSETS_Y_TAMANO.md`](13_ASSETS_Y_TAMANO.md).** Los 500 MB del CI no son un número inventado:
es exactamente el tope de módulo base de Google Play.

⚠️ **Mover código de `:app` a `:shared` NO engorda el AAB** (el `dex` entero son 6,8 MB). Lo que sí
lo engordaría es **duplicar assets**: si algún día `:shared` empaqueta sus propios recursos del
mundo, acabarían dos veces en el AAB. Los assets se quedan en `app/src/main/assets/`.
