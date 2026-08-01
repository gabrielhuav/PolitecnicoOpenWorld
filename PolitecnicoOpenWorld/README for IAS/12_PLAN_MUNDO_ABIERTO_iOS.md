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

### 🟡 Fase 3 — Los gestores de IA (EN CURSO: 3 de 6 hechos, 07-30)

| Gestor | Líneas | Estado |
|---|---:|---|
| `PoliceManager` | 404 | ✅ en `commonMain`, **con 10 tests nuevos** |
| `CampaignEscortPolice` | 402 | ✅ en `commonMain` |
| `PrankedyManager` | 624 | ✅ en `commonMain` |
| `NpcAiManager` | 988 | 🔜 `CopyOnWriteArrayList` + `AtomicReference` |
| `NpcAiManagerTraffic` | 565 | 🔜 ⚠️ **parcial de `NpcAiManager`: van juntos** |
| `NpcAiManagerMovement` | 224 | 🔜 igual, parcial |

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

#### Lo que falta para cerrar la fase

`NpcAiManager` y sus dos parciales usan además `CopyOnWriteArrayList` y `AtomicReference`.
**Escribe tests de sus reglas ANTES de tocarlo** (como se hizo con `PoliceManager`) y, si puedes,
**juega media hora el mundo abierto en Android** al terminar: el tráfico y los peatones no los caza
ningún test.

### 🔜 Fase 4 — `R.string` → `composeResources` (50 archivos)

Mecánico y sin riesgo, pero largo. La receta ya está probada: es lo que se hizo con Ajustes
(`SettingsSections`). Se puede repartir por features.

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
> ### ⚠️ `Modifier.scale()` NO sirve para encoger un control
>
> Android fuerza horizontal en el mundo abierto; **iOS no**, así que en vertical los controles a
> 180 dp cada uno no caben (360 dp de 390). El primer intento fue `Modifier.scale(0.55f)` y
> **los botones dejaron de responder**: `scale` transforma el DIBUJO, no el área táctil, así que el
> control quedaba donde ya no se veía.
>
> La solución es que los controles acepten un **tamaño real**: `JoystickController(tamano = …)`,
> `ActionButtonsController(tamano = …)`, `ActionButton(tamano = …)`. Por dentro, el stick y los
> botones son **proporcionales al diámetro**, así que encogerlos no cambia el tacto. En iOS van a
> **100 dp**. Android no cambia: el valor por defecto sigue siendo `ControllerBaseSize`.
>
> ### Los botones que aún no hacen nada, lo dicen
>
> A/B/X/Y muestran *"Botón X: pendiente del ViewModel"* durante 4 s. Un botón mudo parece una app
> rota; uno que explica por qué, no.

#### Lo que queda de la fase: el ViewModel (~5 000 líneas)

> ## 🔗 LA CADENA DE BLOQUEO — medida el 2026-07-31
>
> Se intentó mover `WorldMapState.kt` (335 líneas, el estado del VM) y **falló por UN símbolo**.
> Esto es lo que hay de verdad, y explica por qué la fase 4 va ANTES que la 5:
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
> ### Orden correcto para quien siga, CON EMULATOR DELANTE
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
> ⚠️ **Lo que falta para que sea jugable:**
> - **NPCs, policía, coleccionables, landmarks.** Hay función JS para todos (`updateNpcs`,
>   `updatePolice`, `updateCollectibles`, `updateLandmarks`); quien las alimenta es el
>   `WorldMapViewModel`, que sigue en `:app` — **fase 5**.
> - ~~**Colisiones**~~ ✅ **HECHO (07-31)**: `cargarColisionesExteriores()` +
>   `chocaAlMoverse()` en `commonMain`, con **8 tests**. Lee `assets/CONFIG/exterior_collisions.json`
>   por `PowAssets`, así que sirve a las dos plataformas sin arrastrar el ViewModel.
>   ⚠️ Comprueba el **TRAYECTO**, no solo el destino: si no, un paso largo atraviesa la barda de un
>   salto. Hay un test que lo fija.
>   ⚠️ Si el JSON falta devuelve configuración **vacía** (se puede atravesar todo) en vez de lanzar:
>   un mundo sin bardas es mejor que un crash al entrar. Por eso `CONFIG/` se añadió al `rsync`
>   del bundle iOS (80 KB).
> - **La VUELTA del puente (JS → Kotlin)**: en Android es `@JavascriptInterface`; en iOS haría falta
>   `WKScriptMessageHandler`. Sin ella el mapa no avisa de toques ni de arrastres.
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
