# PLAN DE MIGRACIÓN A KOTLIN MULTIPLATFORM (objetivo: iOS)

**Fecha:** 2026-07-27 · **Autor:** Opus 5 · **Última revisión:** 2026-07-30

> ## 📍 ESTADO REAL — 2026-07-30
>
> **El modo pelea corre ENTERO en iOS**, con menú principal, Ajustes, Coleccionables, cambio de
> idioma, modo desarrollador y guardado. Verificado en el simulador (iPhone 17 Pro, iOS 18.2).
>
> | Lo que este plan proponía | Cómo acabó |
> |---|---|
> | Alcance: el juego entero (decisión §nº2) | **Se hizo el alcance "solo SF primero"**, que era la recomendación. El mundo abierto queda como trabajo futuro. |
> | Kotlin 2.2.10 sin subir (decisión §nº4) | Está en **2.3.21**. Ahí se queda: **KSP no existe para 2.4**. |
> | Mapa: Leaflet en `WKWebView` (§4) | Verificado y **desconectado**: vive en `iosApp/POW/MapaWeb.swift` esperando al mundo abierto. |
> | Assets: 358 MB y On-Demand Resources (§8) | Al bundle solo entra lo de SF: **196 MB medidos**, contra el límite de 200 MB de Apple. ODR sigue pendiente si entra el mundo abierto. |
>
> **Dónde está lo vigente:** estado vivo en `_SESION_ACTUAL.md` · dónde tocar cada cosa en
> `11_SEPARACION_IOS_ANDROID.md` · el proyecto Xcode en `iosApp/README.md`.
> **Este documento se conserva por su §1–§8: las MEDIDAS y las decisiones**, no como lista de tareas.

> Todo lo que aquí se afirma con un número **viene de un comando que se ejecutó** (los comandos están
> en §1). Lo que es estimación, va marcado como **ESTIMADO**. Lo que no se verificó, se dice.

## ✅ DECISIONES DEL DUEÑO (2026-07-27)

| # | Decisión | Elegido |
|---|---|---|
| 1 | Mac / cuenta Apple | **Mac SÍ, cuenta Developer NO** → se puede compilar y probar en simulador y dispositivo propio; TestFlight/App Store quedan para cuando haya cuenta. |
| 2 | Alcance iOS | **El juego ENTERO**, mundo abierto incluido. |
| 3 | Mapa | **Opción A**: reutilizar el mapa web (Leaflet en `WKWebView`); osmdroid y Google nativo pasan a extras solo-Android. |
| 4 | Fase 1 | **Adelante, SIN subir Kotlin** (sigue en 2.2.10). |

> ⚠️ **Mi recomendación era el alcance "solo SF primero" y el dueño eligió el juego entero.**
> Es su decisión y el plan sigue adelante con ella; dejo constancia de que **mi reserva sigue en
> pie** y está argumentada en §0 y §8: los **358 MB de assets** contra el límite de **200 MB por
> datos móviles** obligan a **On-Demand Resources**, y `navigation-compose` multiplataforma **sigue
> en alpha**. Eso no impide empezar — las Fases 1–4 son idénticas en ambos alcances — pero
> **el punto de decisión antes de la Fase 7 sigue siendo el sitio correcto para reevaluarlo**, ya
> con SF funcionando en un iPhone y con datos en vez de estimaciones.

---

## 0. Resumen ejecutivo (si solo lees una sección, lee esta)

**Veredicto: SÍ vale la pena, pero NO como "migrar POW a iOS". Vale la pena como
"sacar *Huelum vs. Goya* en iOS", y dejar el mundo abierto para una segunda ola.**

Tres hallazgos cambian el planteamiento del prompt original:

1. **osmdroid NO es un bloqueador de 51 archivos. Son 6.**
   De los 51 archivos que tocan `org.osmdroid`, **45 solo usan `GeoPoint`**, que es una pareja de
   `double` (lat/lon). Medido: **419 usos de `.latitude` + 418 de `.longitude`** frente a **14 usos
   de geometría real** (`distanceToAsDouble` ×10, `destinationPoint` ×4). Esos 45 archivos se
   arreglan con **una `data class` propia en `commonMain` + 2 funciones de haversine**, y lo
   verifica el compilador. Solo **6 archivos** tocan la API de render de osmdroid (`views`,
   `tileprovider`, `config`, `events`).

2. **iOS ya tiene mapa, y no hay que escribirlo.** El proveedor **por defecto NO es osmdroid**: es
   `CARTO_VOYAGER`, que es **Leaflet dentro de un WebView**. 8 de los 10 proveedores del enum
   `MapProvider` son web. El generador de ese mapa (`WorldMapLeafletHtml.kt`, **929 líneas**) tiene
   **CERO imports** — es Kotlin puro que produce HTML/JS. Sus únicas ataduras a Android son **5
   strings `file:///android_asset/`** y un puente JS de **30 líneas** (`MapJsBridge.kt`).
   En iOS eso es un `WKWebView` + un `WKScriptMessageHandler`. **osmdroid y Google nativo se quedan
   como extras SOLO-Android** y el `when (mapProvider)` ya tiene la forma exacta para hacerlo.

3. **El prompt olvidó un bloqueador real: Gson.** Está en **20 archivos**, incluidos **5 del
   protocolo de red de SF**. Gson usa reflexión de la JVM y **no existe en iOS**. Hay que pasar a
   `kotlinx.serialization`. No es difícil, pero no estaba en la tabla y es trabajo obligatorio.

**La consecuencia práctica:** el camino barato a iOS **no pasa por el mapa**. El modo *Huelum vs.
Goya* son **15,329 líneas** (14,094 de la feature + 1,235 de dominio puro), **no toca osmdroid en
absoluto**, no necesita mapa, ya está aislado del resto del juego, y ya tiene la lógica pura
extraída y con tests. Es un juego completo y publicable por sí mismo.

**Lo que NO recomiendo:** portar el mundo abierto a iOS en la primera ola. No por el mapa (que está
resuelto), sino por los **358 MB de assets** contra el **límite de 200 MB de descarga por datos
móviles** de la App Store, y por la cantidad de superficie Android del mundo abierto.

---

## 1. Cómo se midió todo (para que se pueda reproducir/desmentir)

```bash
# Compilación y tests — baseline de producción
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd PolitecnicoOpenWorld\PolitecnicoOpenWorld
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

**Resultado VERIFICADO hoy (2026-07-27, sobre `main` = `603ea8b1`):**
`BUILD SUCCESSFUL in 1m 58s`. Conteo leído de los XML de resultados, no del log:

```
tests=131  skipped=0  failures=0  errors=0
```

Ese es el baseline que **cada fase tiene que dejar intacto**.

El resto de números salen de `grep`/`find`/`wc` sobre `app/src/main`, y de
`curl` a los `maven-metadata.xml` de Maven Central y Google Maven (§3).

> **detekt NO se ejecutó en esta sesión.** El baseline de 5 smells preexistentes que dice
> `_SESION_ACTUAL.md` **no lo he verificado yo**; lo doy por bueno pero no lo afirmo como medido.

### Tamaño del proyecto (medido)

| Métrica | Valor |
|---|---:|
| Archivos `.kt` en `app/src/main` | **250** |
| Líneas | **61,345** |
| Archivos de test | 19 (**131 tests**) — *foto del ANTES, Fase 0. Hoy son 312.* |
| Módulos Gradle | **1** (`:app`) |
| Assets | **358 MB** |

---

## 2. Acoplamiento REAL a Android (medido, y corregido respecto al prompt)

Confirmo la tabla del prompt archivo por archivo. **Todos sus números son correctos.** Lo que añado
es lo que faltaba y, sobre todo, **qué significa cada número** — que es donde cambia la decisión.

| API solo-Android | Archivos | Lo que la auditoría añade |
|---|---:|---|
| `org.osmdroid` | 51 | 🟢 **45 son solo `GeoPoint` (dato). Bloqueo real = 6 archivos.** Ver §4. |
| `android.content.Context` | 65 | 🟠 Real, pero la mayoría es para `context.assets` (**91 usos**) → un `expect` de carga de assets se lleva el grueso. |
| `android.graphics` | 41 | 🟠 Menos malo de lo que parece: ya hay **52 `.asImageBitmap()`**, o sea el dibujo ya habla Compose. Lo Android puro es el **decode+recorte**: `BitmapFactory.decodeStream` ×57, `Bitmap.createBitmap` ×28, `createScaledBitmap` ×8. |
| **`com.google.gson`** | **20** | 🔴 **FALTABA EN EL PROMPT.** Reflexión JVM → no existe en iOS. Obligatorio `kotlinx.serialization`. **5 de esos archivos son el protocolo de red de SF.** |
| `dagger.hilt` / `javax.inject` | 13 | 🟢 **Menos grave de lo que dice el prompt.** Ver §6: **el paso a Koin es OPCIONAL**, no obligatorio. |
| `SharedPreferences` | 10 | 🟡 Fácil. |
| `androidx.room` | 9 | 🟢 **Trivial: 394 líneas en total.** Entidades/DAOs simples. |
| `android.media` | 5 | 🟠 `SoundManager.kt` (485 líneas) es el núcleo. |
| `com.google.android.gms` | 4 | 🟠 Location + Firebase Auth. |
| `android.bluetooth` | 2 | 🔴 No portable. Ver §5. |
| `org.webrtc` | 1 | 🟠 `SfWebRtcClient.kt`, 453 líneas. |
| `android.net.wifi` | 2 | 🟠 `SfLanClient` 188 + `SfLanDiscovery` 138 líneas. |
| `okhttp3` | 2 | 🟢 Solo 2 archivos → Ktor. |
| `androidx.compose` | 86 | 🟢 **A favor:** es UI portable vía Compose Multiplatform, no un problema. |

**Dato que juega a favor y no estaba cuantificado: 57 de los 250 archivos (5,319 líneas) ya no
tienen NINGUNA referencia a Android/androidx/osmdroid/Hilt.** Ese es material que puede irse a
`commonMain` prácticamente tal cual.

---

## 3. Estado VERIFICADO de las librerías KMP (2026-07-27)

No lo di por sabido: cada versión sale de `maven-metadata.xml` del repositorio real, y en los casos
dudosos comprobé que **el artefacto de iOS existe de verdad** (un HTTP 200 sobre el `.pom` de
`iosarm64`, que es la única prueba de que el soporte KMP no es marketing).

| Librería | Última estable | Fecha del metadata | Sirve para | Veredicto |
|---|---|---|---|---|
| **Kotlin** | **2.4.10** | 2026-07-22 | — | 🟢 Proyecto en **2.2.10** → hay que subir. |
| **Compose Multiplatform** | **1.11.1** (beta: 1.12.0-beta02) | 2026-07-14 | UI compartida | 🟢 **iOS estable desde 1.8.0 (mayo 2025).** |
| **Room** | **2.8.4** (proyecto: 2.7.0) | — | BD | 🟢 **`room-runtime-iosarm64` 2.8.4 → HTTP 200. Confirmado.** |
| **androidx.sqlite bundled** | 2.6.0 | — | Driver de Room en iOS | 🟢 **`sqlite-bundled-iosarm64` → HTTP 200.** |
| **Ktor** | **3.5.1** | 2026-06-25 | Sustituye OkHttp | 🟢 Sano y activo. |
| **Koin** | **4.2.2** | 2026-06-15 | DI (si se hace) | 🟢 Sano y activo. |
| **kotlinx-coroutines** | **1.11.0** | 2026-05-08 | — | 🟢 |
| **kotlinx-serialization** | **1.11.0** | 2026-04-09 | **Sustituye Gson** | 🟢 Obligatorio. |
| **JetBrains lifecycle-viewmodel** | 2.11.0 | — | ViewModel en común | 🟢 Los VM se comparten. |
| **JetBrains navigation-compose** | 2.10.0-**alpha02** | — | Navegación | 🟡 **Sigue en alpha.** `AppNavGraph.kt` (1,175 líneas) depende de esto. |
| **multiplatform-settings** | **1.3.0** | **2024-11-29** | SharedPreferences | 🟡 **Sin publicar hace ~20 meses.** Funciona, pero ojo. |
| **DataStore (androidx)** | 1.1.7 estable | — | Alternativa a lo anterior | 🟢 **`datastore-preferences-core-iosarm64` → HTTP 200.** **Recomiendo ésta** sobre multiplatform-settings: es de Google y está viva. |
| **webrtc-kmp** (`com.shepeliev`) | 0.125.11 | **2025-09-08** | WebRTC común | 🔴 **~10 meses sin tocar y versión 0.x.** No apoyaría el online de iOS en esto sin un plan B. |
| **MapLibre Android** | 13.4.1 | 2026-07-23 | Mapa nativo | 🟢 Vivo — pero ver §4: **probablemente no hace falta**. |

**Dos avisos que importan:**

- **Navigation Compose multiplataforma sigue en alpha.** No es bloqueante para SF (que es una
  pantalla, no un grafo de navegación), sí lo sería para portar el juego entero.
- **`webrtc-kmp` es la pieza más frágil de toda la lista.** Es la única dependencia crítica que está
  en 0.x y parada.

---

## 4. LA DECISIÓN DEL MAPA (el bloqueador nº1 — y resulta que no lo es)

### Lo que se midió

```
51 archivos importan org.osmdroid
├── 45 archivos → SOLO org.osmdroid.util.GeoPoint          ← es un dato, no un mapa
└──  6 archivos → API de render (views/tileprovider/config/events)
      · NativeOsmMap.kt          1,458 líneas
      · WorldMapScreen.kt        1,466   (solo la rama `MapProvider.OSM`)
      · NativeOsmMapPrankedy.kt    137
      · NativeOsmMapFog.kt          52
      · RoomTileModuleProvider.kt   ~60   (caché de teselas compartida)
      · MainActivity.kt            182   (configuración de arranque de osmdroid)
```

Uso real de `GeoPoint` en esos 45 archivos:

```
419 × .latitude        ← 99.6% del uso es "una pareja de doubles"
418 × .longitude
 10 × .distanceToAsDouble   ← haversine, ~15 líneas de Kotlin puro
  4 × .destinationPoint     ← ídem
```

**Y el hallazgo que lo cambia todo:** `MapProvider.OSM` (osmdroid) **no es el proveedor por
defecto**. El default, en los tres sitios donde vive (`WorldMapState`, `SettingsState`,
`MainMenuState`), es **`CARTO_VOYAGER`** — Leaflet en un WebView. El enum tiene 10 proveedores y
**8 son web**. El `when (mapProvider)` ya delega en 3 ramas limpias:
`NativeOsmMap` / `GoogleMapLayer` / `WebMapLayer`.

`WorldMapLeafletHtml.kt` (929 líneas, el generador del mapa web) **no tiene ni un `import`**. Sus
únicas ataduras a Android son 5 constantes `file:///android_asset/…` y el puente
`MapJsBridge.kt` (30 líneas, 7 métodos `@JavascriptInterface`).

### Opciones comparadas

| | **A · Reutilizar el mapa web (WKWebView)** | **B · MapLibre** | **C · Mapa propio en Canvas de Compose** |
|---|---|---|---|
| Qué hay que escribir | `expect/actual` del WebView + puente JS (30 líneas) + base de assets | Renderer nuevo entero para las 2 plataformas | Renderer nuevo entero, tiles, proyección, caché |
| Reutiliza el trabajo hecho | **Sí: las 929 líneas de Leaflet intactas** | No | No |
| Paridad Android/iOS | **Total** (mismo HTML/JS) | Alta, pero es un 4º renderer que mantener | Total, pero todo por hacer |
| Rendimiento en gama baja | Ya es el default en Android; está afinado (09 §7/§8) | Mejor (GPU nativa) | Impredecible |
| Riesgo | **Bajo** | Medio | **Alto** |
| Esfuerzo **ESTIMADO** | **Bajo** | Alto | Muy alto |
| Toca los 45 archivos de `GeoPoint` | No (eso es independiente) | No | No |

**Nota sobre B:** MapLibre no es mala idea *en abstracto* — es la opción técnicamente más elegante
y elimina el WebView. Pero significa **tirar el renderer que ya funciona y es el default**, y
añadir un cuarto camino de render a un juego que ya tiene tres. No lo recomiendo **ahora**; sí lo
dejaría como sustituto futuro del osmdroid nativo, cuando iOS ya exista.

### ✅ Recomendación

**Opción A, con osmdroid degradado a "extra solo-Android".**

1. Crear `PowGeoPoint(latitude, longitude)` en `commonMain` (+ `distanceTo` y `destinationPoint`
   en Kotlin puro) y sustituir `org.osmdroid.util.GeoPoint` en los 45 archivos. Es mecánico y **lo
   verifica el compilador**. ⚠️ Hacerlo con **Refactor → Move/Rename de Android Studio**, nunca con
   Find&Replace — ver el gotcha crítico de 09 §0, que ya costó horas una vez.
2. `NativeOsmMap` + `GoogleMapLayer` se quedan en el source set **androidMain**. En iOS el enum
   simplemente no ofrece esos 2 proveedores (`isWebProvider` ya existe y hace justo esa distinción).
3. iOS estrena con el renderer web, que es **el que ya usan los jugadores de Android por defecto**.

**Lo que esto cuesta en honestidad:** iOS no tendría el mapa nativo de osmdroid ni Google nativo.
Dado que ninguno de los dos es el default en Android, es una pérdida menor. **Pero hay que
verificarlo en un iPhone real**: no he medido el rendimiento de Leaflet en `WKWebView`, y el mapa
web en Android sí tiene trampas de rendimiento documentadas (09 §7: el `#map-wrapper` y las esquinas
negras en WebViews viejas). **Es el riesgo nº1 de esta recomendación y hay que probarlo pronto.**

---

## 5. Lo que NO va a tener equivalente en iOS

### 🔴 5.1 · Multijugador por Bluetooth RFCOMM — **imposible, y sin sustituto real**

`SfBtClient.kt` (171 líneas) + su uso en `StreetFighterScreen.kt`.

**Corrección importante al prompt:** el prompt propone *"¿se sustituye por Multipeer Connectivity?"*.
La respuesta honesta es **no, no es un sustituto**:

- iOS no expone RFCOMM de Bluetooth Clásico a apps de terceros (solo el programa MFi).
- **Multipeer Connectivity ya no funciona por Bluetooth** — desde hace años su transporte P2P es
  **Wi-Fi peer-to-peer**, no BT.
- Y aunque funcionara: MC es **iOS↔iOS**. **Nunca** podría conectar un iPhone con un Android.

→ **Un iPhone y un Android no van a poder pelear en local por Bluetooth. Punto.** No es una
limitación que se pueda diseñar para esquivar.

**Propuesta:** el modo BT se queda **solo-Android** (menú oculto en iOS). Para "jugar el uno al lado
del otro" en iOS, el camino ya existente es **LAN por Wi-Fi** (§5.2) o el online. Añadir Multipeer
Connectivity sería un **cuarto transporte** que solo sirve iPhone↔iPhone: **no lo recomiendo salvo
que el dueño lo pida** — el coste no lo justifica frente a LAN, que ya funciona en ambos lados.

### 🟠 5.2 · Descubrimiento LAN por UDP multicast

`SfLanDiscovery.kt` (138) + `SfLanClient.kt` (188).

En iOS el multicast exige el entitlement **`com.apple.developer.networking.multicast`**, que Apple
concede **por solicitud** (hay que justificarlo). **No lo he verificado con una cuenta real y no sé
cuánto tarda ni si lo darían para este caso** — hay que asumirlo como riesgo abierto.

**Propuesta:** sustituir el descubrimiento por **Bonjour/NSNetService** (`NWBrowser`), que es la vía
nativa de iOS y **no necesita entitlement**. El transporte (sockets TCP + JSON) no cambia, solo cómo
se encuentran los rivales. En Android se puede dejar el multicast actual o pasar a NSD, que es su
equivalente. **Aquí sí hay que decidir**: mantener 2 mecanismos de descubrimiento, o unificar en
Bonjour/NSD en las dos plataformas.

### 🟠 5.3 · WebRTC

`SfWebRtcClient.kt` (453 líneas), sobre `io.github.webrtc-sdk:android`.

Como dice §3, **`webrtc-kmp` lleva ~10 meses parado y está en 0.x**. Dos caminos:

- **`expect/actual`** con la lib nativa de cada plataforma (`GoogleWebRTC`/`WebRTC.xcframework` en
  iOS). Más trabajo, pero sin depender de un wrapper abandonado. **Es lo que recomiendo.**
- **`webrtc-kmp`**: menos código, pero se hereda su mantenimiento.

**Y hay una salida barata que conviene tener presente:** el P2P por WebRTC **es una optimización,
no un requisito**. Por diseño (ver `_SESION_ACTUAL.md` §B) si el hole punching falla, **todo cae al
relay y el juego funciona igual**. Así que **iOS v1 puede salir sin WebRTC**, usando solo el relay,
y añadirlo después. Eso quita la dependencia más frágil del camino crítico.

### 🟠 5.4 · Otros

| Función | Situación en iOS | Propuesta |
|---|---|---|
| **Firebase Auth + Google Sign-In** | Firebase tiene SDK iOS, pero **no es KMP** | `expect/actual`. Y en iOS hay que valorar **Sign in with Apple**, que Apple **exige** si ofreces login social de terceros. **Riesgo de rechazo si se ignora.** |
| **Location (`gms`)** | No existe | `expect/actual` → `CoreLocation` |
| **`SoundManager` (SoundPool/MediaPlayer)** | No existe | `expect/actual` → `AVAudioEngine`/`AVAudioPlayer`. 485 líneas, una sola clase. |
| **`WebView`/`WebViewClient`** | `WKWebView` | `expect/actual` (es la base de la Opción A del mapa) |
| **Caché de teselas (`RoomTileModuleProvider`)** | Es un enganche de osmdroid | Solo-Android. El WebView cachea por su cuenta. |
| **Modo Diseñador** | Herramienta interna | Dejarlo solo-Android; no aporta nada al jugador de iOS. |

---

## 6. Dos correcciones a la estrategia del prompt (ahorran trabajo)

### 6.1 · Hilt → Koin **NO es obligatorio**

El prompt lo pone como Fase 3 obligatoria ("Hilt no tiene KMP y no lo va a tener"). Es cierto que
Hilt no soporta KMP, pero la conclusión no se sigue: **el módulo `shared` no necesita DI**. Puede
exponer constructores y funciones fábrica normales, y entonces:

- **Android sigue con Hilt** y las provee desde `di/AppModule.kt` — **cero cambios en las 9 VMs**.
- **iOS** las construye a mano o con Koin, en su propio código.

Esto elimina de la ruta crítica un refactor de 13 archivos que toca el cableado de **toda** la app
en producción. **Recomiendo NO migrar el DI** salvo que más adelante duela de verdad.

### 6.2 · El orden del prompt deja el riesgo para el final

El esbozo del prompt (datos → DI → UI → mapa → iOS) llega a "¿funciona esto en un iPhone?" en la
**Fase 6**. Eso es tarde: si el mapa web en `WKWebView` va mal, o si Compose MP no rinde con este
juego, quiero saberlo **en la fase 2**, no después de haber refactorizado media app.

Por eso el plan de §7 mete **un iPhone en marcha lo antes posible**, aunque sea con una pantalla
tonta.

---

## 7. Plan por fases

**ESTIMACIONES:** en *sesiones de trabajo* de una IA con compilador, comparables a las sesiones que
ya ha tenido este proyecto. **Son estimaciones, no medidas.** El multiplicador de incertidumbre
sube mucho a partir de la Fase 4 (nadie ha compilado este proyecto para iOS todavía).

| Fase | Qué | Esfuerzo | Qué se rompe / riesgo |
|---|---|---|---|
| **0** | *(esta)* Auditoría | ✅ hecha | Nada |
| **1** | **Andamiaje.** `:shared` KMP con `androidTarget()` + iOS. Mover `domain/models/streetfighter/` (**1,235 líneas**) y sus tests. `:app` lo consume. | 1–2 | **Bajo.** Si compila y los 131 tests siguen verdes, el andamiaje funciona. Ojo: subir Kotlin 2.2.10 → 2.4.x toca **todo** el build. **Ésa es la parte arriesgada de esta fase, no el mover archivos.** |
| **1.5** | 🆕 **Prueba de fuego en iPhone.** App iOS mínima que arranque Compose MP y abra el mapa Leaflet en `WKWebView`. **Desechable.** | 1–2 | **Ninguno** (no toca Android). **Valor: mata las 2 dudas grandes antes de invertir.** |
| **2** | **`GeoPoint` → `PowGeoPoint`** en los 45 archivos + haversine propio. | 1–2 | **Medio en volumen, bajo en riesgo:** 45 archivos, pero lo verifica el compilador. ⚠️ Con Refactor de AS, **no** Find&Replace (09 §0). |
| **3** | **Gson → kotlinx.serialization** (20 archivos). | 2–3 | 🔴 **El más peligroso de las fases tempranas.** Toca el **protocolo de red** y los **saves**. Un cambio sutil de formato JSON **rompe partidas guardadas de usuarios reales** o la compatibilidad con clientes viejos. Exige tests de round-trip antes de tocar nada. |
| **4** | **Datos.** Room 2.7 → 2.8 KMP, SharedPreferences → DataStore KMP, OkHttp → Ktor (2 archivos). | 2–3 | Bajo-medio. Room son 394 líneas. Migrar prefs **debe conservar los datos existentes**. |
| **5** | **SF a `commonMain`.** Las ~14,094 líneas de la feature; `expect/actual` para sprites (decode+recorte), audio y red. | 4–6 | 🔴 **Alto.** `StreetFighterViewModel.kt` son **6,219 líneas** y `StreetFighterScreen.kt` **4,029**. Aquí aparece el muro de `android.graphics`. |
| **6** | **App iOS de SF** — entrega de assets, firma, TestFlight. | 3–5 | Alto, pero **ya no toca Android**. Ver §8. |
| **—** | **🚩 PUNTO DE DECISIÓN: ¿se sigue con el mundo abierto?** | | Con SF publicado en iOS habrá **datos reales** en vez de estimaciones. |
| **7+** | Mundo abierto en iOS (mapa web, campaña, interiores, NPCs). | **8–15+** | Muy alto. **No lo estimo en serio desde aquí**: es más de la mitad del código y sería inventar un número. |

**Regla en todas las fases:** al terminar, `compileDebugKotlin testDebugUnitTest` → **131 verdes**.

### El orden importa

Las fases **1 a 4 no son "trabajo de iOS"**: dejan el Android existente **mejor** (menos acoplado,
JSON con tipos, datos multiplataforma) y son útiles **aunque el proyecto iOS se cancele**. El gasto
irrecuperable solo empieza en la Fase 5.

---

## 8. Assets: el problema que no se resuelve con código

**Medido hoy:** 358 MB en `app/src/main/assets`.

```
STREETFIGHTER 107 MB · SPRITES 106 · AUDIO 47 · BUILDINGS 31
INTERIORS 23 · TRANSIT 17 · STORY 16 · PLACES 9 · VIDEO 4 · resto ~5
```

Límites de la App Store (verificados, ver fuentes al final):

| Límite | Valor | Cómo queda POW |
|---|---|---|
| Tamaño máximo de app | 4 GB | 🟢 Sin problema |
| **Descarga por datos móviles** | **200 MB** | 🔴 **Se pasa con solo los sprites** |
| On-Demand Resources: por tag | 512 MB | 🟢 |
| ODR: total alojado | 20 GB | 🟢 |

**Lo bueno:** si iOS v1 es **solo SF**, el reparto ayuda mucho. `STREETFIGHTER` (107 MB) + la parte
de `AUDIO` que use SF cabe **por debajo o cerca de los 200 MB**, sin necesidad de ODR en el v1.
El mundo abierto (`SPRITES` 106 + `BUILDINGS` 31 + `INTERIORS` 23 + …) es lo que obliga a ODR.

**No he medido qué porción exacta de `AUDIO` (47 MB) pertenece a SF.** Hay que medirlo antes de
dar por hecho que el v1 cabe.

---

## 9. Veredicto honesto: ¿vale la pena?

**Sí, con este alcance. No con el alcance del prompt.**

**A favor (medido, no opinión):**
- El bloqueador nº1 **no era un bloqueador**: 45 de 51 archivos de osmdroid son un `data class`.
- **iOS hereda un mapa que ya funciona y ya es el default**, sin escribir un renderer.
- 57 archivos (5,319 líneas) ya son multiplataforma sin tocarlos.
- SF es un **juego completo, aislado, sin mapa y con la lógica pura ya extraída y testeada**.
- Compose MP en iOS lleva **estable desde mayo de 2025**; Room, Ktor, Koin y coroutines están sanos
  y con artefactos iOS comprobados.
- Las fases 1–4 mejoran el Android aunque iOS nunca salga.

**En contra (y no lo voy a suavizar):**
- **Gson en 20 archivos**, tocando red y partidas guardadas. Es la fase que **puede romperle el
  juego a usuarios reales**.
- **`webrtc-kmp` está parado y en 0.x** — mitigable saliendo sin P2P en v1.
- **Navigation multiplataforma sigue en alpha** — no molesta para SF, sí para el mundo abierto.
- **El multijugador local iPhone↔Android por Bluetooth es imposible.** Sin rodeos.
- **358 MB de assets** contra un límite de 200 MB por datos móviles.
- Es **1 desarrollador + IAs** manteniendo una app **en producción**. Cada fase es tiempo que no se
  dedica a la deuda ya conocida (los 29 clips de audio largos, las animaciones congeladas, los mapas
  de UAM que faltan).

**Lo que haría yo:** aprobar **Fases 1 y 1.5** y nada más. Son 2–4 sesiones, no comprometen a nada,
y contestan con hechos las dos preguntas que hoy solo tienen estimaciones: *¿el build KMP aguanta
este proyecto?* y *¿el mapa Leaflet va bien en un iPhone?*. Si la 1.5 sale bien, el resto del plan
se apoya en datos. Si sale mal, se ha perdido muy poco y se sabe pronto.

**Lo que NO haría:** empezar por la Fase 3 (Gson) ni por el mapa. Y no me comprometería a la Fase 7
(mundo abierto) hasta ver SF vivo en la App Store.

---

## 10. Contradicciones detectadas entre docs y código

Regla del repo: *si un doc contradice al código, manda el código.*

1. **`09_CONVENTIONS_GOTCHAS.md` §0 dice "5 archivos pasan de 1000 líneas; NINGUNO pasa de 1500
   (2026-06-22)". Es FALSO hoy.** Medido: **4 archivos pasan de 1500**, y el mayor es
   `StreetFighterViewModel.kt` con **6,219 líneas** (más de 4× el techo que declara el doc);
   `StreetFighterScreen.kt` tiene 4,029. La tabla es anterior al modo de pelea (PR #136).
   → **Ese doc necesita actualizarse**, y esos 2 archivos son el mayor riesgo técnico de la Fase 5.
2. El prompt de la migración **omite Gson** (20 archivos), que es un bloqueador duro de iOS.
3. El prompt marca Hilt→Koin como obligatorio; **no lo es** (§6.1).

*(No he verificado el baseline de detekt; no lo cuento como contradicción.)*

---

## 11. Lo que queda por decidir (las 4 grandes ya están resueltas arriba)

Estas **no bloquean** las fases 1–4, pero hay que contestarlas antes de la Fase 5:

1. **¿Bluetooth en iOS?** → Mi propuesta: se queda **solo-Android**; iOS juega en local por
   **LAN/online**. (Multipeer Connectivity **no** resuelve iPhone↔Android — §5.1.) **Sin confirmar.**
2. **¿Descubrimiento LAN?** → ¿Unificamos en Bonjour/NSD en ambas plataformas, o mantenemos
   multicast en Android + Bonjour en iOS? **Sin confirmar.**
3. **¿Sign in with Apple?** → Apple lo **exige** si ofreces login social de terceros (y hoy hay
   Google Sign-In). Es **riesgo de rechazo** en la App Store. **Sin confirmar.**
4. **Cuenta de Apple Developer ($99/año)** → no hace falta para desarrollar, sí para TestFlight y
   publicar. Decidir cuándo se compra.

---

## 12. FASE 1 — EJECUTADA el 2026-07-27 (verde)

### Qué se hizo

- **Módulo `:shared` KMP** (`shared/build.gradle.kts`), con **Kotlin 2.2.10 y AGP sin tocar**.
  Targets: `androidTarget` + **`iosArm64` + `iosSimulatorArm64` + `iosX64`**.
- **Movido a `commonMain`** (con `git mv`, conservando historial) el dominio PURO de SF:
  `SfAnimation`, `SfArcadeLadder`, `SfDamage`, `SfModels`, `SfPhysics`, `SfStageCatalog`,
  `SfStateMachine` — **7 archivos, 1,235 líneas**.
- **Movidos a `commonTest`** 6 archivos de test (**44 tests**), convertidos de JUnit4 a `kotlin.test`.
- **`:app` consume `:shared`** con `implementation(project(":shared"))`.
- **CI actualizado** (`pr-quality-gate.yml`): añadido el test de `:shared` (⚠️ desde la Fase 5 la
  tarea se llama **`testAndroidHostTest`**, no `testDebugUnitTest`) y el nuevo
  `--input` de detekt.

### Verificación MEDIDA (no supuesta)

```
:app    -> tests=87  skipped=0 failures=0 errors=0
:shared -> tests=44  skipped=0 failures=0 errors=0
                     ────────────────────────────
                     87 + 44 = 131  ✅ el baseline INTACTO
```

`:app:assembleDebug` → **BUILD SUCCESSFUL** (valida el grafo de Hilt completo y el merge de
manifiestos). **detekt con la invocación EXACTA de CI** (con `--build-upon-default-config` y
baseline, más el input nuevo) → **exit code 0, ningún issue nuevo**.

### Decisiones de implementación que conviene conocer

1. **Se conservó el paquete `ovh.gabrielhuav.pow.domain.models.streetfighter`** dentro de `:shared`.
   Por eso **NO hubo que cambiar ni un `import`** en los 250 archivos de `:app`. Es la razón de que
   un cambio de módulo saliera casi gratis.
2. **`SfArcadeCampaignAuditTest` NO se movió** (3 tests): usa `java.io.File` para auditar assets en
   disco, que no existe en `commonTest`. Se queda en `:app`, que es su sitio.
3. **iOS se declara pero no se compila aquí:** Gradle avisa
   `The following Kotlin/Native targets cannot be built on this machine and are disabled:
   iosArm64, iosSimulatorArm64, iosX64`. **Es esperado en Windows y no rompe nada** — en el Mac esos
   targets sí compilarán. Ese aviso es señal honesta de que **lo de iOS todavía NO está verificado**.

### 🆕 GOTCHA REAL que apareció (y que volverá en CADA fase)

Al sacar el dominio a otro módulo, **`:app` dejó de compilar con 7 errores**:

```
Smart cast to 'SfAttackStrength' is impossible, because 'special' is a
public API property declared in different module.
```

**Kotlin NO hace smart cast de propiedades públicas que vienen de OTRO módulo.** El idiom
`if (input.special != null && trySpecial(..., input.special, ...))` compilaba de sobra mientras todo
vivía en `:app`, y deja de hacerlo al cruzar la frontera de módulo. Se arregló en
`StreetFighterViewModel.kt` (7 sitios) con el equivalente exacto:

```kotlin
input.special?.let { trySpecial(sim, idx, it, now) } == true
```

Es idéntico en semántica (si es `null`, `?.let` da `null` y `== true` es `false`).
**Cuenta con que esto reaparezca** cada vez que se muevan modelos con campos nullable públicos a
`:shared` — sobre todo en la Fase 5 con `SfInput`/estados, y en la Fase 2 con los modelos del mapa.

### Lo que la Fase 1 demuestra (y lo que NO)

- ✅ **Demuestra** que el andamiaje KMP funciona en este proyecto, con el Kotlin actual, sin romper
  Android, sin tocar Hilt y sin tocar el build de release.
- ❌ **NO demuestra nada de iOS todavía.** Ningún byte se ha compilado para iOS. Eso es la
  **Fase 1.5** y **exige el Mac**.

### Siguiente paso recomendado: **Fase 1.5, en el Mac**

Antes de seguir metiendo código en `:shared`, conviene gastar una sesión en el Mac para: (a) que
`:shared` compile de verdad para iOS, y (b) el experimento desechable de Compose MP + el mapa
Leaflet en `WKWebView`. Es lo que convierte en hechos las dos suposiciones más caras del plan.

---

### Fuentes consultadas (estado de librerías y límites de Apple)

- [Compose Multiplatform para iOS es estable](https://medium.com/@rushabhprajapati20/compose-multiplatform-for-ios-is-now-stable-bf4e2fc35596) · [Compose MP: dos hitos](https://ailleron.com/insights/compose-multiplatform-marks-two-major-milestones/) · [¿KMP listo para producción en 2026?](https://www.kmpship.app/blog/is-kotlin-multiplatform-production-ready-2026)
- [Multipeer Connectivity en juegos (objc.io)](https://www.objc.io/issues/18-games/multipeer-connectivity-for-games/) · [Apple Developer Forums: MC no funciona por Bluetooth](https://developer.apple.com/forums/thread/715999) · [MC solo por Bluetooth](https://developer.apple.com/forums/thread/809565)
- [Límites de On-Demand Resources (Apple)](https://developer.apple.com/help/app-store-connect/reference/on-demand-resources-size-limits/) · [Límites de tamaño de app](https://adapty.io/glossary/app-size/) · [Apple sube el límite móvil a 150 MB (histórico)](https://m.gsmarena.com/apple_increases_its_app_store_download_limit_over_cellular_to_150mb-news-27364.php)
- Versiones: `maven-metadata.xml` de Maven Central (`repo1.maven.org`) y Google Maven
  (`dl.google.com/dl/android/maven2`), consultados el 2026-07-27.
