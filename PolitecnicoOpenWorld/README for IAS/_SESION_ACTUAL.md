# 🧠 MEMORIA COMPARTIDA ENTRE IAs — estado vivo

> Único traspaso entre IAs. Ventana de 2 días; máximo 200 líneas. Aquí va estado medido,
> trabajo abierto y trampas caras. Diseño e historia viven en los documentos de cada área.

**Última actualización:** 2026-08-16 · Opus 5 (**Windows, con emulador**) · rama `ios/verificacion-mac-1.0.0.17`

> ➡️ **08-16: lo que la Mac dejó COMPILANDO ya está PROBADO EN ANDROID.** Esa rama cerró fases 3 y 4
> y movió `NpcAiManager` + `OverpassRepository` a `commonMain`, pero en el Mac no hay AVD. Esta
> sesión lo abrió en el emulador (`Nexus`, API 35), que es donde se ven los fallos que ningún test
> caza. **319 tests = 125 app + 194 shared**, 0 fallos, y **detekt exit 0** (medido en Windows).
>
> ✅ **Títulos de misión desde `composeResources`** (fase 4): correctos en el widget de OBJETIVO y en
> el registro, **en español Y en inglés**, misiones 1-3 + las 2 secundarias, con el idioma del texto
> migrado y el de los `R.string` de alrededor SIEMPRE de acuerdo. El apóstrofe de EN sale bien.
> ✅ **Mundo abierto ~35 min**, en los DOS renderers (web y **OSM nativo**): peatones, tráfico,
> aparcar, despawn por distancia, niebla, WASTED y respawn. Cero excepciones.
> ✅ **Calles por Ktor en RELEASE con R8**: borrada `pow_roads.db`, el APK minificado descargó
> **2512 ways / 11 946 nodos** y los guardó. Los `-keep` del `HttpClientEngineContainer` bastan.
> ✅ **Marcador de destino y proyectil de Prankedy** en el OSM nativo (los dos archivos del gotcha
> de smart cast, 09 §12): waypoint colocado con ruta de 3 puntos, e impacto de la lata aplicando daño.
> ✅ **Migración de datos**: `install -r -d` sobre una partida de 1.0.0.14 conservó los 3 slots.
>
> ➡️ **08-17: los controles del mundo en iOS ya son IDÉNTICOS a los de Android.** El problema no era
> el tamaño sino que iOS no forzaba horizontal: se portó la regla de `AppNavGraph` con una costura
> nueva (doc 11 §4quater) y el HUD volvió a `ControllerBaseSize`. **Compila en los dos targets de
> iOS y Android sigue en 319/0.** ⚠️ **La mitad Swift (`PowAppDelegate`) NO se ha compilado nunca**:
> desde Windows no hay Xcode. Es lo primero que hay que abrir en el Mac.
>
> 🔴 **ABIERTO — 2 ANR en el renderer OSM nativo** tras ~13 min. Detalle abajo en §4 P0.
> ⚠️ **Sin verificar: API ≤32.** No hay imagen de sistema ≤32 instalada ni `cmdline-tools` en esta
> PC (los 5 AVD son API 35/36). La costura de idioma de API 24-32 sigue SIN probar.
>
> Lo de iOS (Skia, `systemBarsPadding` a 54 pt, 39 NPCs en el simulador) se midió en el Mac el
> 08-14/15 y sigue vigente; los 194 de `:shared` corren igual en JVM y en `iosSimulatorArm64`.

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
  ahí están el árbol de decisión, las 11 costuras y las trampas que solo se ven en el simulador.
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
- 📱 **El dueño ya tiene un iPhone físico (08-14).** El framework de DISPOSITIVO
  (`:shared:linkDebugFrameworkIosArm64`) enlaza — medido ese día, nunca se había compilado — y cae
  donde lo busca `FRAMEWORK_SEARCH_PATHS[sdk=iphoneos*]`. Falta solo elegir cuenta en Signing
  (`DEVELOPMENT_TEAM` vacío); un **Apple ID gratis** sirve para el propio iPhone, los 99 USD son
  para TestFlight. Detalle en `iosApp/README.md`.
  ⚠️ **Los números de memoria del simulador NO valen para el dispositivo**: ahí no hay jetsam. El
  pico de los atlas (§decodificar) solo se puede juzgar en el iPhone.
- **Decodificar imágenes en iOS = Skia** (`decodificarReducido`): `Image.makeFromEncoded` +
  `scalePixels` sobre un `Bitmap` con `allocN32Pixels`. El buffer grande lo posee la `Image` y se
  cierra a mano → se libera SIN esperar al GC de Kotlin/Native. ⚠️ **No es el `inSampleSize` de
  Android y no hay forma de que lo sea desde Skiko**: el pico de memoria es el mismo, lo que baja
  es cuánto dura. Los atlas son **WebP** (ya no PNG). Verificado en el simulador el 2026-08-14.

## 2bis. 🍏 Trampas de iOS que solo se ven en el simulador

Las cinco del 07-30 (idioma, assets fuera del bundle, atlas mal pintado, `\'` sin des-escapar,
`systemBarsPadding`) están explicadas con su causa medida en **`11_SEPARACION_IOS_ANDROID.md` §8bis**.
El detalle de aquella sesión se purgó a `_ARCHIVO/HISTORIAL_sesiones_2026-07-30.md`.

## 2ter. 🌎 Mundo abierto a iOS — fases 1, 2, 3 y 4 hechas · 5 parcial · 6 con NPCs

**Plan, medidas y orden de ataque: `12_PLAN_MUNDO_ABIERTO_iOS.md`.** Aquí solo lo que hay que
saber sin abrirlo:

- ✅ **1 · Menús idénticos.** iOS pinta los 6 botones; los 3 del mundo salen **EN OBRAS**.
  ⚠️ **`MODOS_EN_OBRAS_VISIBLES = false` antes de firmar para la App Store.** 7 tests lo fijan.
  ⚠️ `disponible()` NO cambió (= "¿se juega?"). Lo nuevo es `sePinta()`, **solo para pintar**.
- ✅ **2 · Dominio puro a `commonMain`**: 22 archivos. El paquete es idéntico → ningún import cambió.
  Patrón a repetir: `java.lang.Math` → `kotlin.math`, `Context` → `PowAssets`, `UUID` → `kotlin.uuid`.
- ✅ **3 · Gestores de IA: LOS 6.** `NpcAiManager` + sus 2 parciales bajaron el 08-15, con
  **7 tests nuevos**. Costuras creadas: `PowListaConcurrente`, `PowConjuntoConcurrente`, `PowRef`,
  `ahoraMs()`, `colorArgb`, `powLog`. Dos trampas que NO daban error y están en 09:
  🔴 el reloj tenía que ser **de época** (`fearUntil` la compara el VM, que sigue en `:app`), y
  🔴 el `synchronized(lista)` externo **ya no protege** → `drenar()` para leer-y-vaciar.
  La receta de los 3 primeros sigue valiendo: **`PowMapaConcurrente`** conserva la semántica de
  `ConcurrentHashMap`, así que migrar es cambiar el tipo y 4 nombres, no rehacer ~50 accesos donde
  el compilador no avisa si te dejas uno. 🐞 Al tipar esa API salió un crash latente
  (`ConcurrentHashMap.get(null)` lanza NPE, se buscaba con `policeCarId: String?`). Corregido.
- 🟡 **5 · Solo el HUD.** `JoystickController` + `ActionButtonsController` de `commonMain`: **los
  MISMOS controles que Android**. A/B/X/Y avisan "pendiente del ViewModel".
  ✅ **08-17: y ahora del MISMO TAMAÑO.** iOS ya fuerza horizontal en el mundo (doc 11 §4quater), así
  que el HUD usa `ControllerBaseSize` (180 dp) como Android y como SF; se borró el encogido a 100 dp.
  🔴 ⚠️ **`Modifier.scale()` NO encoge un control: los botones dejan de responder.** Transforma el
  dibujo, no el área táctil. Si hay que cambiar tamaño se pasa `tamano` real, que sí es proporcional.
  🔗 **El VM está BLOQUEADO por la fase 4**: `WorldMapState` → `CampaignObjective` →
  `@StringRes Int` → 42 strings sin migrar. Cadena y orden de ataque en el doc 12.
- 🟢 **6 · El mapa se ve, se CAMINA, las bardas frenan y HAY NPCs (08-15).** El mismo
  `NpcAiManager` de Android corre en iOS: `OverpassRepository` (ya multiplataforma, Ktor + PowJson)
  baja las calles y un tick a 30 Hz mueve **39 NPCs medidos** en el simulador.
  ⚠️ Se lee `getServerNpcs().toList()`, **no el flujo `npcs`** (ese es solo de red) y **no la lista
  viva** (Compose no recompondría). ⚠️ Zoom **17**: por debajo de 16.5 el JS no pinta NPCs.
  ⚠️ **Falta la caché de calles**: iOS pide a Overpass en cada arranque y come 429. Es lo siguiente.
  Antes de esto (07-31) el mapa ya se veía y se caminaba; `UIKitView` mete el `WKWebView` en
  Compose; `PuenteMapaIos` (Kotlin → JS) llama a las mismas funciones que Android.
  ⚠️ `UIKitView` exige `@OptIn(ExperimentalForeignApi)` **y** `import kotlinx.cinterop.readValue`.
  ⚠️ Cada llamada JS va con `if (typeof f === 'function')`: si el HTML aún no cargó, `WKWebView`
  **se traga el ReferenceError sin log** y el mapa se queda quieto sin que nadie sepa por qué.
  ⚠️ Falta la **VUELTA** del puente (JS → Kotlin): haría falta `WKScriptMessageHandler`.

## 3bis. ✅ Android de 1.0.0.15/16: verificado y PUBLICADO — solo lo que sigue vigente

Detalle de aquellas sesiones: `PROMPT_SOL_release_hoy_y_webp.md` y el commit `016e7406`.

- **Cómo se prueba un release aquí:** instalar el build de `main`, HACER partida, y encima
  `adb install -r -d` el de la rama. **Instalar limpio no prueba migración** — saves, prefs,
  sesión de arcade `_V2` y `pow_roads.db` sobrevivieron.
- ✅ **Idioma de `:shared` en Android 13+:** `LocaleManager.applicationLocales` +
  `android:localeConfig` alimentan a la vez `R.string` y Compose Resources; API 24-32 conserva el
  `Context` envuelto. iOS mantiene su costura `AppleLanguages` + reconstrucción del árbol.
- ✅ **142 PNG → WebP:** 87.3 → 40.8 MB. AAB local 353.15 MB, bajo el aviso de 450 y el tope de 500.
  `tools/.local/` ignorado: el codec `cwebp` no se versiona.

## 4. PENDIENTE — prioridad

### 🔴 P0

1. ✅ **1.0.0.15/16 publicados** (1.0.0.16 en prueba cerrada; `main` va por 1.0.0.17).
   ✅ **iOS de 1.0.0.16 verificado en el Mac el 08-14** (Skia + `systemBarsPadding`, ver cabecera).
2. **Decisión del dueño — On-Demand Resources sí o no.** Bloquea la fase 4 en adelante del mundo
   abierto en iOS: con el mundo el bundle son **399 MB** contra los **200 MB por datos móviles** de
   Apple. Sin ODR, la app solo se baja por Wi-Fi. Datos en el doc 12 §0.
3. ✅ **Fase 3 del mundo (gestores de IA): CERRADA y jugada en el emulador el 08-16.** Detalle en
   el doc 12 §fase 3.
   🔴 **2 ANR ABIERTOS en el renderer OSM NATIVO** (no en el web), tras ~13 min de juego seguido en
   el emulador. Los dos con el **mismo stack**: `NativeOsmMap` (lambda de update, ~30 Hz) →
   `view.overlays.add` → `CopyOnWriteArrayList.copyOf` → `WaitForGcToComplete`, con el heap Java a
   **141/163 MB (13 % libre)** y 1,05 GB de PSS. **No lo causa la migración de concurrencia**:
   ese `CopyOnWriteArrayList` es el de **osmdroid**, no `PowListaConcurrente`, y en los dos volcados
   de ANR **no aparece ni una** de `PowListaConcurrente`/`PowCerrojo`/`PowRef`/`NpcAiManager`; el
   diff de esta rama en `NativeOsmMap.kt` son 3 líneas de null-check. **Falta medirlo contra `main`
   para atribuirlo del todo.** 🐞 Sospechoso encontrado de paso, PREEXISTENTE y **también en
   release**: `TileCache.kt` hace **5 `Log.d` por tesela sin gatear por `BuildConfig.DEBUG`**, con
   el SHA-256 interpolado — **31 412 líneas en 13 min** (~40/s) de basura que alimenta justo al GC
   que aparece en el stack.
4. **NPC fantasma: NO se puede probar con un solo cliente.** `pendingDespawns.drenar()` solo corre
   dentro de `webSocketManager?.let { … isServerDelegatedHost }` → **hace falta multijugador**.
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

🍏 **En Windows añade `.\gradlew.bat :shared:compileKotlinIosSimulatorArm64`** — MEDIDO el 08-16:
compila de verdad (217 archivos / 7,7 MB de klib, `iosMain` incluido), **sin** el aviso de "targets
disabled" que afirmaba `PLAN_MIGRACION_KMP.md` §12. Esa contradicción con `11 §7` ya está corregida:
manda el 11. Enlazar el framework sigue siendo solo del Mac.

Windows: `.\gradlew.bat`. Mac: fijar el JBR de Android Studio y añadir
`:shared:iosSimulatorArm64Test :shared:linkDebugFrameworkIosSimulatorArm64`.
Esperado Android: **319 = 125 app + 194 shared**, 0 fallos (medido en **Windows** el 2026-08-16,
leyendo los XML de `test-results`, no el log).
En Mac conservar además `:shared:iosSimulatorArm64Test` (los mismos 194). Si se toca `commonTest`,
ejecutar el guard. Para abrir la app: `linkDebugFrameworkIosSimulatorArm64` y luego ⌘R en Xcode —
Xcode NO regenera el framework solo, y sin ese paso corres el binario viejo sin enterarte.

Detekt CI desde la raíz exterior:

```bash
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
```

Debe dar exit 0. El input incluye `shared`. Antes de push: `git status`, actualizar este archivo
y hacer `git pull` inmediatamente antes del push.
