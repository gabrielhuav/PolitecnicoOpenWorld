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

### 🔜 Fase 5 — `WorldMapViewModel` + sus 22 parciales (~5 000 líneas)

⚠️ **Los 22 parciales NO se pueden mover sueltos**: son funciones de extensión de la clase, así que
o se mueve la clase o no compila ninguno. Ver la regla del patrón parcial en `10 §4`.

El VM necesita Hilt y `Context` → **patrón Controller**, igual que `StreetFighterController`.

### 🔜 Fase 6 — La UI (~8 000 líneas) y el mapa

`WorldMapScreen` (1463) · `NativeOsmMap` (1458, **osmdroid: se queda en `:app` como extra
solo-Android**) · overlays.

El camino del mapa en iOS **ya está resuelto y verificado**: Leaflet en `WKWebView`, con el HTML
generado por el MISMO Kotlin que usa Android (`WorldMapLeafletHtml.kt`). Vive desconectado en
`iosApp/POW/MapaWeb.swift`.

⚠️ **Lo que falta ahí es el puente JS ↔ nativo.** En Android lo hace `MapJsBridge`; en iOS no hay
nada, así que hoy el mapa es de solo lectura: no recibe jugador, ni NPCs, ni landmarks.

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
