# 🌎🍏 Portar el MUNDO ABIERTO a iOS — plan medido

**Creado:** 2026-07-30 · Todo número de aquí **sale de un comando que se ejecutó**.

> **Para quién es:** para quien continúe la migración. Dice qué está hecho, qué falta, **en qué
> orden** y —lo más importante— **cuál es el bloqueador que no se resuelve con código**.
>
> El equivalente para el modo pelea (ya terminado) es `_ARCHIVO/PLAN_SF_EN_iOS.md`.

---

## 0. El resumen, si solo lees una sección

**El mundo abierto NO cabe en iOS tal como está hoy, y no es un problema de código.**

| | Medido |
|---|---:|
| Bundle iOS actual (solo SF) | **196 MB** |
| Assets que sumaría el mundo | **+203 MB** |
| Bundle iOS con el mundo | **399 MB** |
| Límite de Apple por **datos móviles** | **200 MB** |

Con 399 MB la app **solo se puede descargar por Wi-Fi**. Eso no impide publicar, pero es una
decisión de producto que hay que tomar **antes** de escribir más código, porque cambia la
arquitectura: la salida es **On-Demand Resources** (bajar los assets del mundo la primera vez que
se entra), y eso obliga a que todo acceso a assets del mundo pase por una capa asíncrona que hoy
no existe.

**Recomendación:** decidir ODR sí/no antes de la Fase 4. Las fases 1–3 valen igual en los dos casos.

---

## 1. Cuánto es "el mundo abierto" (medido 2026-07-30)

| Paquete | Archivos | Líneas |
|---|---:|---:|
| `features/map_exterior` (exteriores) | 80 | 18 766 |
| `features/interiores` (interiores + zombis + metro) | 44 | 11 536 |
| **Total a portar** | **124** | **30 302** |

Para comparar: **el modo pelea entero fueron ~12 400 líneas**. El mundo abierto es **2,4 veces
más grande**, y encima trae los tres bloqueadores que SF no tenía (osmdroid, `R.string`, ODR).

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

**19 archivos, ~1 100 líneas** movidos. El paquete es idéntico en los dos módulos, así que
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

### 🔜 Fase 3 — Los gestores de IA (SIGUIENTE, ~3 200 líneas)

`NpcAiManager` (988) · `PrankedyManager` (624) · `NpcAiManagerTraffic` (565) · `PoliceManager` (404)
· `CampaignEscortPolice` (402) · `NpcAiManagerMovement` (224).

Lo que hay que sustituir, todo con equivalente multiplataforma:

| Solo-JVM | Multiplataforma |
|---|---|
| `ConcurrentHashMap`, `CopyOnWriteArrayList` | mapa/lista normal + `PowCerrojo` |
| `AtomicReference`, `@Synchronized` | `PowCerrojo` |
| `System.currentTimeMillis()` | `kotlin.time.Clock` (o pasar `now` por parámetro, que ya se hace en varios sitios) |
| `java.util.UUID` | `kotlin.uuid.Uuid` (ver `Npc.nuevoIdDeNpc`) |

> ## 🛑 POR QUÉ ESTA FASE NO SE HIZO EL 07-30, aunque el resto sí
>
> **No tiene ni un solo test** (medido: 0 archivos de test la mencionan) y **cambia concurrencia
> de código de juego vivo**: tráfico, policía, peatones. Un `ConcurrentHashMap` mal sustituido no
> falla al compilar ni en los 234 tests — falla como un tirón raro o un crash a los diez minutos
> de partida.
>
> **Y en el Mac no hay ningún AVD** (medido: `emulator -list-avds` vacío), así que no se puede
> jugar Android para comprobarlo.
>
> **Hazla en una máquina con emulador**, y en este orden: (1) escribe tests de los managers
> ANTES de tocarlos, (2) sustituye, (3) juega el mundo abierto en Android media hora.

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

### 🔜 Fase 8 — Assets: la decisión de §0

On-Demand Resources, o publicar una app de 399 MB que solo se baja por Wi-Fi.

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

⚠️ **Mover código de `:app` a `:shared` NO engorda el AAB** (el `dex` entero son 6,8 MB). Lo que sí
lo engordaría es **duplicar assets**: si algún día `:shared` empaqueta sus propios recursos del
mundo, acabarían dos veces en el AAB. Los assets se quedan en `app/src/main/assets/`.
