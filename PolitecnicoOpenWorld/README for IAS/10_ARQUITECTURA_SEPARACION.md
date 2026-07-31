# 10 · CÓMO ESTÁ SEPARADO EL CÓDIGO (y dónde tocar cada cosa)

> **Para quién es este documento.** Para cualquier IA o persona que vaya a **cambiar algo** en POW,
> incluidas las IAs pequeñas (Gemini 3.6 Flash y parecidas). Su único objetivo es que puedas
> responder **"¿en qué archivo toco esto?"** sin leerte 60.000 líneas.
>
> Si solo puedes leer dos documentos, que sean **este** y `09_CONVENTIONS_GOTCHAS.md`.

---

## 1. La regla de oro: un archivo = una pregunta

El proyecto tenía archivos de 6.000 líneas. Ya no. Hoy **cada archivo responde a UNA pregunta**, y
esa pregunta está escrita en su **cabecera**, en las primeras 10 líneas.

> **Antes de tocar nada: abre el archivo y lee su cabecera.** Ahí está para qué sirve, qué NO debe
> entrar, y las trampas que ya costaron caro. No es decoración: es la parte más útil del archivo.

---

## 2. Los dos módulos

| Módulo | Qué es | Regla |
|---|---|---|
| **`:shared`** | Kotlin Multiplatform: Android **+ iOS** | ⚠️ **NADA de Android aquí.** Ni `android.*`, ni `androidx.*` que no sea multiplataforma, ni osmdroid, ni Gson. Si algo necesita plataforma, va por `expect/actual`. |
| **`:app`** | La app Android | Puede usar todo lo de Android. Consume `:shared`. |

**Qué vive ya en `:shared`** (101 archivos, 20 576 líneas medidas el 07-30): el dominio puro de las
peleas (`SfStateMachine`, `SfDamage`, `SfPhysics`…), el punto lat/lon (`GeoPoint`), el JSON
(`PowJson`, `jsonOf`), la base de datos (Room), los ajustes (`Settings`), el WebSocket (Ktor), el
generador del mapa Leaflet **y ya también las PANTALLAS**: menú principal, Ajustes, Coleccionables
y la pelea entera, en Compose Multiplatform.

**Cómo saber dónde poner una clase nueva:** ¿la necesitaría también un iPhone? Si sí y no toca
Android → `:shared`. Si no estás seguro → `:app` (moverla después es fácil; sacar Android de
`:shared` no).

---

## 2bis. 🍏 Cómo se separa iOS de Android (LÉELO ANTES DE TOCAR `:shared`)

> 📘 **Si vas a tocar código de las dos plataformas, el documento largo es
> [`11_SEPARACION_IOS_ANDROID.md`](11_SEPARACION_IOS_ANDROID.md)**: árbol de decisión, las 10
> costuras que existen hoy, qué se queda en `:app`, gama baja y las trampas que solo se ven en el
> simulador. Esta sección es el **resumen**: los cuatro mecanismos y cuándo usa cada uno.

`:shared` no puede ver Android. Cuando algo SÍ necesita la plataforma hay **cuatro mecanismos**, y
elegir mal es lo que ensucia el módulo. Van de menos a más potencia: **usa el primero que te sirva.**

| # | Mecanismo | Cuándo | Ejemplo real |
|---|---|---|---|
| 1 | **`expect/actual`** | Una primitiva pequeña y sin estado | `decodificarReducido`, `plataformaActual`, `PowViewModel` |
| 2 | **Fuente instalable** | Un servicio global que la app instala al arrancar | `PowAssets`, `PowAudio` |
| 3 | **Environment** | Un puñado de datos/almacenes que necesita UN ViewModel | `StreetFighterEnvironment` |
| 4 | **Controller** | Una PANTALLA de `commonMain` que necesitaba Hilt/`Context` | `StreetFighterController`, `MainMenuController` |

### 1. `expect/actual` — para una pieza pequeña
```kotlin
// commonMain
expect fun decodificarReducido(bytes: ByteArray, reduccion: Int): ImageBitmap
```
⚠️ **No lo uses para clases grandes.** Un `expect class` con 20 miembros obliga a escribir DOS
implementaciones completas y a mantenerlas sincronizadas a mano.

### 2. Fuente instalable — un servicio para toda la app
Una `interface` en `commonMain`, un `object` que delega, y cada app instala su implementación al
arrancar. Así el código común llama a `PowAssets.bytes(...)` sin saber si detrás hay un
`AssetManager` o un `NSBundle`.
```kotlin
PowAssets.instalar(AssetsDeAndroid(this))   // PowApplication.onCreate()
PowAssets.instalar(AssetsDeBundle())        // lado iOS
```
⚠️ **Si se olvida el `instalar`, falla en RUNTIME, no al compilar.** Es el precio de este patrón.

### 3. Environment — lo que necesita un ViewModel
```kotlin
interface StreetFighterEnvironment {
    val developerMode: Boolean get() = false   // el default hace que iOS no tenga que implementarlo
    val arcade: SfArcadeStore
}
```
El VM vive en `commonMain` y recibe el entorno por constructor. **Pon defaults sensatos**: así una
plataforma nueva solo implementa lo que de verdad le importa.

### 4. Controller — para una PANTALLA
Es la inversión de dependencia para UI. La pantalla declara **qué necesita**, no **de dónde sale**:

```
StreetFighterScreenCommon(controller)          ← commonMain, no sabe de Hilt ni de Context
   ├── AndroidStreetFighterController          ← :app, envuelve el VM de Hilt
   └── OfflineStreetFighterController          ← commonMain/iOS, sin red
```

**Por qué así y no `expect/actual` de la pantalla:** habría dos copias de la UI que se desincronizan
en silencio. Con el controller **hay UNA pantalla** y dos formas de alimentarla.

### La regla para decidir, en una línea
> ¿Es un dato o una función suelta? → **1**. ¿Un servicio de toda la app? → **2**.
> ¿Lo necesita un ViewModel? → **3**. ¿Lo necesita una pantalla? → **4**.

### Lo que NO se hace nunca
- ❌ **Copiar una pantalla a `iosMain`.** Si hay dos copias, en un mes divergen.
- ❌ **Un `if (esIOS)` dentro de `commonMain`.** Las diferencias de plataforma van en el `actual` o
  en el controller. La ÚNICA excepción viva es `PowModo.disponible()`, que es *catálogo de negocio*
  (qué modos ofrece cada tienda), no una rama técnica — y está testeada.
- ❌ **Meter `android.*` en `commonMain` "temporalmente".** No compila para iOS y bloquea a todos.

### Cómo se apaga un modo en iOS
No se borra código ni se bifurca la UI: se pregunta al catálogo.
```kotlin
if (PowModo.MUNDO_LIBRE.disponible()) { /* el botón solo existe donde el modo corre */ }
```
`PowModos.kt` (`:shared`) es la ÚNICA fuente de verdad, y `PowModosTest` se pone rojo si alguien
añade un modo a iOS sin confirmarlo en el simulador.

---

## 3. MVVM: quién puede hablar con quién

```
   View (Compose)  ─────►  ViewModel  ─────►  Repository  ─────►  Room / red / assets
        │                      │
        │  collectAsState()    │  _state.update { it.copy(...) }
        └──────────────────────┘
```

**Las tres reglas que no se rompen:**

1. **La View NUNCA toca un repositorio ni un DAO.** Solo `collectAsState()` y llamar funciones del
   ViewModel. Si una pantalla necesita un dato, se lo pide al VM; no va a buscarlo.
2. **El estado es inmutable.** Siempre `_state.update { it.copy(campo = ...) }`. Nunca mutar.
3. **El ViewModel no conoce Compose.** Nada de `@Composable`, `Modifier` ni `Color` en el VM.

---

## 4. El patrón PARCIAL (esto es lo que más se usa aquí)

Los ViewModel grandes están partidos en **parciales**: archivos aparte, del **mismo paquete**, con
**funciones de extensión** del ViewModel.

```kotlin
// StreetFighterNet.kt  ← un parcial
internal fun StreetFighterViewModel.startOnline(create: Boolean) { ... }
```

**Por qué así y no con clases nuevas:** el estado (`_state` y los campos de simulación) tiene que
seguir siendo UNO solo. Partirlo en objetos separados obligaría a sincronizarlos, que es peor.

### Las 4 reglas del patrón parcial

1. **Los CAMPOS se quedan siempre en la clase.** En el parcial solo va la LÓGICA. Si mueves un
   campo, deja de ser el mismo estado.
2. **Un `private` que necesite un parcial pasa a `internal`.** No a `public`.
3. **⚠️ NUNCA recrees en la clase una función que ya está en un parcial.** Quedarían "gemelas" y
   **gana la de la clase EN SILENCIO** — el parcial deja de ejecutarse y no falla nada. Este bug ya
   se pagó caro en este repo (ver `09 §0`).
4. **Desde otro paquete, cada extensión se importa una a una.** Por eso `StreetFighterScreen.kt`
   tiene ~37 imports de `...viewmodel.loQueSea`. Es normal, no lo "limpies".

### Lo que NO se puede mover a un parcial

Una extensión declarada **dentro** de la clase sobre otro tipo, p. ej.
`private fun SfInput.hasAttackOrSpecial()`. Tiene **dos receptores** (el VM + el `SfInput`) y
Kotlin no admite eso fuera de la clase. **Se queda como miembro.** Está avisado en el código.

---

## 5. Mapa: ¿dónde toco X?

### 🥊 Modo pelea — "Huelum vs. Goya" (`features/streetfighter/`)

| Quiero cambiar… | Archivo |
|---|---|
| Qué puede hacer un peleador en cada estado, añadir un movimiento | `viewmodel/StreetFighterMaquinaEstados.kt` |
| Cuánto daño hace algo, bloqueo, combos, parry, KO | `viewmodel/StreetFighterCombate.kt` |
| Cómo decide la CPU, dificultad, sus combos | `viewmodel/StreetFighterCpuAi.kt` |
| Multijugador (online, Bluetooth, LAN, P2P) | `viewmodel/StreetFighterNet.kt` |
| La escalera arcade, dificultad por escalón, partida a medias | `viewmodel/StreetFighterArcade.kt` |
| Cuándo suena una voz y su subtítulo | `viewmodel/StreetFighterVoces.kt` |
| El tutorial de controles | `viewmodel/StreetFighterTutorial.kt` |
| IA vs IA, gauntlet, escaparate, auditorías de assets | `viewmodel/StreetFighterGauntlet.kt` |
| El reloj de juego, el tick, el estado observable | `viewmodel/StreetFighterViewModel.kt` |
| **Lo que se DIBUJA en la pelea** (fondo, sprites, hitboxes, FPS) | `ui/SfSceneRenderer.kt` |
| Menú de modos, selección de peleador, dificultad, resultado | `ui/SfMenuOverlays.kt` |
| Pantallas de conexión (online / BT / LAN) | `ui/SfOnlineOverlays.kt` |
| La raíz de la pantalla y su orquestación | `ui/StreetFighterScreen.kt` |

### 🧭 Navegación: cada plataforma la suya

| Quiero cambiar… | Archivo |
|---|---|
| A qué pantallas se llega **en Android** | `[app] AppNavGraph.kt` |
| A qué pantallas se llega **en iOS** | `[shared/iosMain] PowAppIos.kt` |
| Qué **modos** ofrece cada plataforma | `[shared] domain/platform/PowModos.kt` |

⚠️ **Añadir un modo a `modosDe(IOS)` NO lo porta**: solo deja de esconder el botón. `PowModosTest`
se pone rojo a propósito para obligar a confirmarlo en el simulador antes.

### 🌎 Mundo abierto (`features/map_exterior/`)

El `WorldMapViewModel` ya estaba partido en parciales (`WorldMapCombat.kt`, `WorldMapTeleport.kt`,
`WorldMapCampaign.kt`, `WorldMapRouting.kt`…). **Mismo patrón, mismas reglas.**

| Quiero cambiar… | Archivo |
|---|---|
| El mapa que se ve por defecto (Leaflet en WebView) | `[shared] ui/WorldMapLeafletHtml.kt` |
| El mapa nativo osmdroid (extra solo-Android) | `ui/NativeOsmMap.kt` |
| IA de NPCs, tráfico, policía | `domain/models/ai/NpcAiManager*.kt`, `PoliceManager.kt` |

### 🧟 Interiores y zombis (`features/interiores/`)

| Quiero cambiar… | Archivo |
|---|---|
| La barra del Modo Diseñador | `zombies/ui/ZombieDesignerToolbar.kt` |
| Sprites de suelo, oclusores, cámara | `zombies/ui/ZombieSceneParts.kt` |
| La pantalla del minijuego | `zombies/ui/ZombieGameScreen.kt` |

---

## 6. Antes de dar por buena tu tarea

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```

**Debe salir: `BUILD SUCCESSFUL` y 228 tests (114 en `:app` + 114 en `:shared`), 0 fallos.**
En Windows es `.\gradlew.bat`. ⚠️ La tarea de `:shared` se llama **`testAndroidHostTest`**, no
`testDebugUnitTest`.

Y el análisis estático (mismo comando que CI):

```bash
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml \
  --build-upon-default-config \
  --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin \
  --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
```

---

## 6bis. 🍏 Si escribes tests en `commonTest`, ojo con el NOMBRE

Kotlin/Native **rechaza** `(`, `)` y `,` dentro de los backticks de un nombre de función:

```kotlin
fun `la barra SUBE de golpe (no se anima)`() {}   // ❌ compila en JVM, ROMPE iOS
fun `la barra SUBE de golpe - no se anima`() {}   // ✅
```

Compila en Android y revienta en el Mac con *"Name contains illegal characters"*. Como en
Windows/Linux no se compila iOS, **era invisible hasta abrir un Mac**. Ya pasó dos veces — la
segunda con el gotcha ya escrito, porque nada lo impedía.

Ahora lo caza CI antes de mergear, y lo puedes correr tú en 2 segundos:

```bash
bash tools/check_kmp_test_names.sh
```

---

## 7. Los seis errores que más caro salen aquí

1. **Recrear en la clase una función que vive en un parcial.** Gana la de la clase, en silencio.
2. **Meter algo de Android en `:shared`.** Compila en Android y rompe iOS, que no se compila aquí.
3. **`PowJson.encodeToString(unMapa)`.** **Compila y revienta en RUNTIME.** Con mapas se usa
   `jsonOf` / `jsonArrayOf`. Hay un test que lo fija.
4. **Añadir un campo a un modelo de red o de guardado SIN valor por defecto.** kotlinx lanza
   excepción si falta en el JSON → crashea a los jugadores con versión antigua o con partidas
   viejas. **Todo campo nuevo lleva default.**
5. **Añadir un estado al enum de peleador por en medio.** Los estados viajan por red como
   `enum.name`: van **AL FINAL** o se rompe la compatibilidad con clientes viejos.
6. **Meter allocations en código por frame** (render, `computeOccluders`, `updateNpcs`). Ver
   `09 §6`: el juego tiene que ir en gama baja.

---

## 8. Si vas a partir otro archivo grande

Quedan estos por encima de 1.000 líneas (**medido el 2026-07-30**; nueve archivos de 336):

| Módulo | Archivo | Líneas |
|---|---|---:|
| `:shared` | `features/streetfighter/viewmodel/StreetFighterViewModel.kt` | 2320 |
| `:shared` | `features/streetfighter/ui/StreetFighterScreen.kt` | 1771 |
| `:app` | `features/map_exterior/viewmodel/WorldMapViewModel.kt` | 1596 |
| `:app` | `features/map_exterior/ui/WorldMapScreen.kt` | 1463 |
| `:app` | `features/map_exterior/ui/NativeOsmMap.kt` | 1458 |
| `:app` | `features/interiores/zombies/ui/ZombieGameScreen.kt` | 1343 |
| `:app` | `AppNavGraph.kt` | 1167 |
| `:app` | `features/interiores/zombies/viewmodel/ZombieInteriorViewModel.kt` | 1165 |
| `:shared` | `features/streetfighter/ui/SfSceneRenderer.kt` | 1089 |

💡 **Los siete de `:app` son mundo abierto, zombis y navegación de Android.** Partirlos no ayuda a
iOS: allí esos modos no existen. Los dos de `:shared` sí los paga todo el mundo.

**La receta que funcionó** (y las trampas que ya se pagaron):

1. Busca un bloque **contiguo y cohesivo** (todo lo de un tema). No elijas "las funciones más
   grandes": elige **un dominio**.
2. Comprueba que el rango **no contiene campos** ni extensiones de doble receptor. Si los hay,
   parte el rango y déjalos en la clase.
3. Muévelo, convierte `private fun x(` en `internal fun ViewModel.x(` y **dedenta 4 espacios**.
4. Compila. El compilador te da la lista EXACTA de qué `private` hay que subir a `internal` y qué
   constantes hay que cualificar (`ViewModel.LA_CONSTANTE`).
5. **Compila y corre TODOS los tests después de CADA extracción**, no al final.

⚠️ **Tres trampas reales de este refactor:**
- Un archivo puede estar en **LF o en CRLF**; si tu script asume uno, el corte sale mal y te
  quedan dos archivos de 20 líneas.
- Cortar por número de línea puede **partir un KDoc por la mitad** (`/**` en un archivo y el
  cuerpo en otro) → "Expecting a top level declaration".
- Un `inline fun` **pierde el receptor** si tu transformación solo busca `fun `.
