# 🧠 MEMORIA COMPARTIDA ENTRE IAs — estado vivo

> Único traspaso entre IAs. Ventana de 2 días; máximo 200 líneas. Aquí va estado medido,
> trabajo abierto y trampas caras. Diseño e historia viven en los documentos de cada área.

**Última actualización:** 2026-07-30 · Opus 5 (Mac) · rama `perf-gama-baja-coleccionables`

> ➡️ **AHORA:** **SF corre entero en iOS** (menú, Ajustes, Coleccionables, pelea, idioma, modo
> desarrollador, guardado) y el **mundo abierto va por la fase 2 de 8** (§2ter, plan en el doc 12).
> 🌎 **En iOS ya se CAMINA por el mapa**: jugador, cámara que lo sigue y niebla de guerra. Faltan
> NPCs, coleccionables y colisiones — los alimenta el `WorldMapViewModel`, que sigue en `:app`.
> **Siguiente:** fase 5 (ese ViewModel) y **adelgazar el AAB** — 402 MB contra 500 de Play (doc 13).
> **413 tests.**

## 🖥️ Rutas por PC

| PC | Raíz del proyecto (`gradlew` y `tools/`) |
|---|---|
| Laptop | `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| Escritorio (MEDIDO 07-29) | `C:\Users\gabri\Documents\GitHub Desktop\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| Mac | `/Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld/PolitecnicoOpenWorld` |

La carpeta es doble. Los docs usan rutas relativas a esta raíz. El GEN de sprites vive fuera,
en `..\newSFAssets\GEN_*`. En Windows la ruta del escritorio lleva espacio: entrecomillarla.
PC nueva: `SETUP_PC_NUEVA.md`; `gradle-wrapper.jar` y `secrets.properties` no viajan por Git.

## 1. Reglas vivas

- Leer `00_INDEX.md`, `09_CONVENTIONS_GOTCHAS.md` y `10_ARQUITECTURA_SEPARACION.md`.
  **Si el cambio toca las DOS plataformas, `11_SEPARACION_IOS_ANDROID.md` es obligatorio** —
  ahí están el árbol de decisión, las 10 costuras y las trampas que solo se ven en el simulador.
- `PowJson` imita Gson a propósito. `encodeToString(Map)` compila y falla en runtime: usar
  `jsonOf`/`jsonArrayOf`. No cambiar saves ni la ruta Android de la BD.
- `SfArcadeRepository` conserva la migración `putStringSet` → JSON `_V2`, el `"null"` literal
  de `mapFile` y el borrado de clave al escribir null. Ya no usa `org.json`; tiene tests V1.
- Kotlin queda en **2.3.21**: KSP no existe para 2.4. AGP 9 usa
  `android.builtInKotlin=true`; tests shared = `testAndroidHostTest`.
- `iosX64` queda fuera: Compose MP 1.11.1 no publica ese target.
- Los nombres de tests de `commonTest` no pueden contener `(`, `)` o `,`; ejecutar
  `bash tools/check_kmp_test_names.sh`.
- En parciales, los campos viven en la clase y nunca se repite allí una función del parcial:
  ganaría la clase en silencio. Ver `10_ARQUITECTURA_SEPARACION.md` **§4**.

## 2. iOS — estado medido

- Assets en el bundle: `assets/STREETFIGHTER/` y `assets/SPRITES/COLLECTIBLES/`. **Del resto de
  `SPRITES/` (104 MB) no entra nada**: es del mundo abierto, que en iOS no existe.
- No quitar el `dependsOn("assemble*MainResources")` de `shared/build.gradle.kts`: sin él faltan
  `composeResources` de Main y la app se cierra desde una corrutina.
- No tocar `Info.plist`, fase `rsync`, `ENABLE_USER_SCRIPT_SANDBOXING = NO` ni la ruta del bundle.
- `CADisableMinimumFrameDurationOnPhone` debe seguir en `Info.plist`.
- Pelea real verificada en simulador: selección, ronda completa, audio, pausa, minimizar,
  cerrar/reabrir y reanudar snapshot de `NSUserDefaults`.
- Offline común; BT/LAN/WebRTC siguen Android-only. iOS **JUEGA** Ajustes, Coleccionables y SF, y
  desde el 07-30 **pinta** además los 3 modos del mundo marcados EN OBRAS. `PowModos.kt` manda:
  `disponible()` = se juega · `sePinta()` = aparece el botón. No los confundas.
- **Sin insignias PREALPHA/BETA**: `IosMainMenuController` devuelve `mostrarInsignias = false` y
  `versionName = null` porque la App Store las rechaza. En Android se quedan.

## 2bis. 🍏 Trampas de iOS que solo se ven en el simulador

Las cinco del 07-30 (idioma, assets fuera del bundle, atlas mal pintado, `\'` sin des-escapar,
`systemBarsPadding`) están explicadas con su causa medida en **`11_SEPARACION_IOS_ANDROID.md` §8bis**.
El detalle de aquella sesión se purgó a `_ARCHIVO/HISTORIAL_sesiones_2026-07-30.md`.

## 2ter. 🌎 Mundo abierto a iOS: fases 1, 2, 3 (parcial) y el MAPA

Plan completo, medido, en **`12_PLAN_MUNDO_ABIERTO_iOS.md`**. Resumen:

- ✅ **Fase 1 — menús idénticos.** iOS pinta ya los 6 botones; MUNDO LIBRE / MODO HISTORIA /
  MULTIJUGADOR salen **EN OBRAS** y avisan en vez de navegar. Verificado en simulador.
  ⚠️ **`MODOS_EN_OBRAS_VISIBLES = false` en `PowModos.kt` antes de firmar para la App Store**
  (Apple rechaza funciones anunciadas que no funcionan). Es lo único que hay que tocar; 7 tests
  nuevos lo fijan, uno comprueba el propio interruptor.
  ⚠️ **`disponible()` NO cambió**: sigue siendo "¿se puede jugar?" y es lo que gatea Ajustes.
  Lo nuevo son `enObras()` y `sePinta()`, y `sePinta()` es **solo para pintar**.
- ✅ **Fase 2 — dominio puro a `commonMain`**: 22 archivos, ~1 500 líneas. El paquete es idéntico
  en los dos módulos, así que **ningún import cambió**. Dos arreglos que son el patrón a repetir:
  `java.lang.Math` → `kotlin.math` (mismos valores) y `loadCalibration(Context)` → `PowAssets`
  (⚠️ tenía una llamada en `ZombieGameScreen.kt`). `java.util.UUID` → `kotlin.uuid.Uuid`.
- 🟡 **Fase 3 — gestores de IA: 3 de 6 hechos.** `PoliceManager` (404, **con 10 tests nuevos**),
  `CampaignEscortPolice` (402) y `PrankedyManager` (624) ya corren en iOS.
  🔐 La clave fue **`PowMapaConcurrente`** (mapa + `PowCerrojo`), que sustituye a
  `ConcurrentHashMap` **conservando la semántica**: migrar es cambiar el tipo y 4 nombres de método,
  no rehacer ~50 accesos a mano donde el compilador no avisa si te dejas uno. 9 tests de semántica
  en las dos plataformas + 5 de carreras con hilos de verdad en `:app`.
  🐞 Al tipar la API, el compilador sacó un **crash latente**: se buscaba con `policeCarId`
  (`String?`) y **`ConcurrentHashMap.get(null)` lanza NPE**. Corregido.
  ⚠️ **Falta `NpcAiManager` (988) y sus 2 parciales**: usan además `CopyOnWriteArrayList` y
  `AtomicReference`. Escribe tests ANTES y **juega el mundo en Android** al terminar — en el Mac no
  hay AVD (medido) y el tráfico no lo caza ningún test.
- 🌎 **Fase 6 arrancada: EL MAPA SE VE EN iOS.** `MapaMundoIos.kt` mete el `WKWebView` en Compose con
  **`UIKitView`** y le carga `buildHtml(...)`, la MISMA función que Android. Verificado en simulador:
  teselas reales sobre ESCOM, arrastre, pinch-zoom y hasta la niebla del juego.
  MUNDO LIBRE sigue **EN OBRAS** pero su botón abre la vista previa: lo decide
  `MainMenuController.mundoTieneVistaPrevia` (solo `true` en iOS), así que la pantalla del menú no
  sabe en qué plataforma corre. `MODOS_EN_OBRAS_VISIBLES = false` lo sigue apagando todo.
  ⚠️ Trampas de `UIKitView`: exige `@OptIn(ExperimentalForeignApi)` **y** `import kotlinx.cinterop.readValue`.
- 🚶 **Y YA SE CAMINA.** `PuenteMapaIos.kt` (Kotlin → JS) llama a las mismas funciones que Android:
  `updatePlayerMarker`, `updateMapView`, `setPlayerFog`. Con un pad de dirección el jugador se mueve,
  la cámara lo sigue y la niebla se abre. Movimiento = `GeoPoint.desplazado()`, puro y con 7 tests.
  ⚠️ Lleva `cos(latitud)` en el eje este **a propósito**: sin él se correría más rápido en horizontal.
  ⚠️ Cada llamada JS va con `if (typeof f === 'function')`: si el HTML aún no cargó, `WKWebView`
  **se traga el ReferenceError sin log** y el mapa se queda quieto sin que nadie sepa por qué.
  ⚠️ **Falta la VUELTA del puente** (JS → Kotlin): en Android es `@JavascriptInterface`, en iOS
  haría falta `WKScriptMessageHandler`. Y faltan NPCs/coleccionables/colisiones: los alimenta el
  `WorldMapViewModel` (fase 5).
### 🗜️ Tamaño — límites VERIFICADOS en la fuente, y una corrección

⚠️ **Me equivoqué antes:** dije que el mundo no cabía en iOS por los 200 MB. **Falso.** El tope
duro del App Store son **4 GB**; los 200 MB son un aviso de datos móviles que el usuario desactiva
desde iOS 13. **Quien aprieta es Google Play: 500 MB de módulo base**, y el AAB va por **416 MB**.
ODR en iOS es deseable, **no bloqueador**. Todo en **`13_ASSETS_Y_TAMANO.md`** con fuentes.

- ✅ **BGM 24→16 bits: 37,8 → 25,2 MB (−12,6).** Eran másters de estudio crudos. No cambia lo que se
  oye: la salida de Android es de 16 bits, así que ya se recortaban en runtime.
- 🔜 **142 PNG → WebP (~60 MB)** y **3 BGM → Ogg (~23 MB)**: dejarían el AAB en ~330 MB. En el Mac
  **no hay `ffmpeg` ni `cwebp`** (medido). Script listo: `tools/optimizar_assets_produccion.sh`.
- 🎧 **Regla del audio:** el formato lo decide QUIÉN lo usa. **Ogg** para lo solo-Android (pesa
  menos y **empalma sin hueco en los bucles**, que AAC no); **`.m4a`** para lo compartido, porque
  **iOS no lee Ogg**. Los 85 `.m4a` de la pelea son 7,6 MB entre todos: duplicarlos no sale a cuenta.
- El CI **avisa a 450 MB** y publica el desglose por carpeta en cada run. Mover código a `:shared`
  no engorda el AAB (el dex son 6,8 MB); **duplicar assets sí** — se quedan en `app/`.

## 4. PENDIENTE — prioridad

### 🔴 P0

1. **Windows:** confirmar en emulador que Android sigue igual tras los arreglos de §2bis. Tocan
   `commonMain`, así que Android hereda los tres: retrato del peleador en la tarjeta, `\'` en
   inglés y `systemBarsPadding` en la ✕. **Ninguno se ha visto en Android todavía.**
2. **Decisión del dueño — On-Demand Resources sí o no.** Bloquea la fase 4 en adelante del mundo
   abierto en iOS: con el mundo el bundle son **399 MB** contra los **200 MB por datos móviles** de
   Apple. Sin ODR, la app solo se baja por Wi-Fi. Datos en el doc 12 §0.
3. **Fase 3 del mundo (gestores de IA).** Hacerla **en una máquina con emulador**: cambia
   concurrencia de juego vivo, tiene 0 tests y en el Mac no hay AVD. Receta en el doc 12 §2.
4. Probar multijugador en dos dispositivos: ambos deben oír lo mismo (`SF-NET`).
5. Redeploy `MultiplayerSF/` en Render; con redes distintas buscar `SF-RTC: DataChannel → OPEN`.

### 🟠 P1 · audio

`SF/PROMPT_traspaso_audio_subtitulos.md`: 29 clips largos y 5 fuera de −16 ±2 LUFS;
faltan `attack`/`hurt` en 6 peleadores. Los SFX globales no se normalizan como voces.

### 🟢 P2 · motor y arte

- Continuar fase 2c (`SfEngine` + modos como estrategia):
  `SF/PLAN_refactor_motor_compartido.md`.
- Arte diferido de La Presidenta (fatality V2 y metamorfosis): requiere importar fuentes de
  `tools/_para_corregir/`, extender packer y reempacar solo a ella.
- Animaciones congeladas: `stun-1==2==3` en 18; otras repeticiones están medidas y requieren arte.

### ⚪ P3 · dueño/deuda

- Mapas UAM Azcapotzalco/Cuajimalpa: faltan vídeos.
- Paparazzi 5 tiene audio de Paparazzi 1; Señor tienda contiene un tramo de Prankedy;
  Tzitzimime requiere recorte humano. Ver prompt Gemini.
- detekt mantiene 5 smells preexistentes; cualquier doc que diga 0 está desactualizado.

## 5. Verificación al cerrar

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```

Windows: `.\gradlew.bat`. Mac: fijar el JBR de Android Studio y añadir
`:shared:iosSimulatorArm64Test :shared:linkDebugFrameworkIosSimulatorArm64`.
Esperado: **228 = 114 app + 114 shared**, 0 fallos (MEDIDO en Mac el 07-30 con
`:app:testDebugUnitTest` + `:shared:iosSimulatorArm64Test`). Si se toca `commonTest`, ejecutar
el guard de nombres.

Detekt CI desde la raíz exterior:

```bash
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
```

Debe dar exit 0. El input incluye `shared`. Antes de push: `git status`, actualizar este archivo
y hacer `git pull` inmediatamente antes del push.
