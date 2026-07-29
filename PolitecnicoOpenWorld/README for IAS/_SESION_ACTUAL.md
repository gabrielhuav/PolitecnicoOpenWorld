# 🧠 MEMORIA COMPARTIDA ENTRE IAs — estado vivo del trabajo

> **Qué es esto.** Varias IAs distintas trabajan aquí y **ninguna recuerda nada**. Este archivo es
> el ÚNICO traspaso: lo que esté aquí lo sabrá la siguiente; lo que no, se pierde o se alucina.
>
> **Reglas duras:** ventana de **2 días** (lo más viejo se purga a `_ARCHIVO/`) · techo de **200
> líneas** (se poda, no se justifica) · aquí va **estado medido, lo que está a medias y las
> trampas caras**, NO diseño ni historia (eso va al doc del área) · **marca lo MEDIDO vs lo
> SUPUESTO** (este repo ya tuvo docs que mentían: "0 smells", "47 tests").
>
> **Al cerrar sesión:** purga lo viejo · reescribe tu sección (qué cambió, qué se verificó, **qué
> falta**) · actualiza PENDIENTE · comprueba las 200 líneas. Si te quedas sin tokens a media
> tarea, **actualiza ESTE archivo ANTES de parar**.

**Última actualización:** 2026-07-28 · Codex 5.6 (Windows) · rama `fase0-auditoria-kmp` · *purgar §3bis el 07-30*

> ➡️ **AHORA:** 1.0.0.14 en revisión en Play. **🥊🍏 LA PELEA DE SF CORRE, SE JUEGA Y SE GUARDA EN
> iOS** — verificada en el simulador (§3terdecies): ronda completa, persistencia al minimizar y al
> cerrar/reabrir. **218 tests, 0 fallos** en las dos plataformas. Falta: **que alguien la OIGA**,
> `MainMenuScreen` a `commonMain` (iOS aún no tiene menú) y la Fase 6.

## 🖥️ Rutas por PC

| PC | Raíz del PROYECTO (aquí están `gradlew` y `tools/`) |
|---|---|
| **Laptop** (referencia) | `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| **Escritorio** (MEDIDO 07-28) | `C:\Users\gabri\Documents\GitHub Desktop\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| **🍏 Mac** (iOS) | `/Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld/PolitecnicoOpenWorld` |

⚠️ **La carpeta es doble**: el proyecto Gradle está DENTRO del repo, en `PolitecnicoOpenWorld/`; las
rutas de los docs son **relativas a la raíz**. El GEN de sprites vive FUERA, en `..\newSFAssets\GEN_*`.
🆕 **PC nueva → `SETUP_PC_NUEVA.md`.** ⚠️ `gradle-wrapper.jar` NO viaja por git y sin él `gradlew` ni
arranca; `secrets.properties` tampoco. ⚠️ **La ruta del escritorio lleva ESPACIO**: entrecomilla siempre.

## 1. Organización y delegación

Mapa en **`00_INDEX.md`**. No te saltes **`10_ARQUITECTURA_SEPARACION.md`** (dónde vive cada cosa)
ni **`09_CONVENTIONS_GOTCHAS.md`**; y **`PLAYSTORE_formulario_seguridad_datos.md`** antes de tocar
la ficha. `SF/` = peleas · `MUNDO/` = mundo libre · `_ARCHIVO/` = histórico, NO tareas.
**Delegar:** alta dificultad → **Sol 5.6 / Fable 5** · media → **Opus 4.8/5** · baja → **Gemini 3.6**.
Pásales rutas absolutas, comando exacto, cómo se verifica, qué NO tocar y el `10_...` para orientarse.

## 3bis. Fases 0-4 — trampas VIVAS (detalle en `PLAN_MIGRACION_KMP.md` y `git log`)

- ⚠️ **NO cambies la ruta de la BD** (`filesDir/databases/pow_roads.db`) ni el driver de Android:
  se perdería caché y landmarks del Diseñador.
- ⚠️ **`PowJson` imita a Gson a propósito**; tocarlo rompe saves y clientes viejos EN SILENCIO.
  **`SfArcadeRepository`** es el ÚNICO con migración real de datos (`putStringSet` → JSON `_V2`).
- ⚠️ **`GeoPoint` = port LITERAL de osmdroid**: `x*x` ≠ `.pow(2)`, no lo "simplifiques". Y **Ktor
  sin `HttpTimeout`** con ping 25s/20s: evitan que se caiga la partida.
- ⚠️ **`dependencies { add("kspAndroid"…) }` va DESPUÉS de `kotlin { }`** en `shared/build.gradle.kts`.
- 🔴 **Dos bugs que el compilador NO ve:** `PowJson.encodeToString(x)` **PETA EN RUNTIME** si `x` no
  es serializable → con mapas usa `jsonOf`; y **kotlinx PETA si falta un campo sin default**.

## 3ter. iOS arrancó — el mapa Leaflet corre en el simulador (cerrado)

`WorldMapLeafletHtml.kt` en `:shared`; la app iOS está en `iosApp/` (SwiftUI + `WKWebView`).
- ⚠️ **El runtime del simulador lo instala XCODE** (`xcodebuild -downloadPlatform iOS`), no un DMG a
  mano: queda en cuarentena y los tests mueren con `Abort trap` (134). **Genera el framework ANTES
  de abrir Xcode** o sale `No such module 'Shared'`. Y `gradle-wrapper.jar` no viaja por git
  (→ `SETUP_PC_NUEVA.md`).

## 3quater-septies. Kotlin 2.3.21 · AGP 9 · refactor · motor (cerrados; trampas VIVAS)

- ⚠️⚠️ **NO SE PUEDE SUBIR A KOTLIN 2.4: KSP NO EXISTE para 2.4** y aquí es obligatorio (Hilt +
  Room). **2.3.21 es el techo.** No pierdas una sesión intentándolo.
- ⚠️ **AGP 9 obliga a `android.builtInKotlin=true`** (`:app` ya no aplica `kotlin-android`) y a
  `com.android.kotlin.multiplatform.library` en `:shared`. Y los tests de `:shared` son
  **`testAndroidHostTest`**, no `testDebugUnitTest`.
- ⚠️ **Patrón PARCIAL:** los CAMPOS se quedan en la clase; **NUNCA recrees ahí una función de un
  parcial** (gana la clase EN SILENCIO) — `10_ARQUITECTURA_SEPARACION.md` §8. Y **dos constantes
  inventadas** (`DRAIN_PER_SEC`=200f, `+ STAGE_PADDING`) ya las fijan tests.
- 🆕 **GUARDA** `tools/check_kmp_test_names.sh` en el `pr-quality-gate`: los nombres de test con
  `(` `)` `,` compilan en la JVM y rompen Native. **Documentarlo NO bastó: pasó 3 veces.**

## 3octies. 🍏 iOS = SOLO el modo pelea (decisión del dueño)

El menú de iOS muestra **AJUSTES, COLECCIONABLES y HUELUM VS. GOYA**; Mundo Libre, Historia y
Multijugador se esconden. El catálogo vive en **`PowModos.kt`** (`:shared`) y es la ÚNICA fuente de
verdad: `PowModosTest` se pone rojo si alguien añade un modo a iOS sin confirmarlo en el simulador.
⚠️ **Esconder el botón NO porta el modo.**

## 3nonies. 🍏🥊 Compose MP + el muro de `android.graphics` (cerrado; trampas VIVAS)

**⚡ Kotlin/Native compila los klibs de iOS DESDE WINDOWS** (`compileKotlinIosSimulatorArm64`): el
type-check de iOS no exige Mac. ⚠️ Pero **`linkDebugFramework…` MIENTE** fuera de un Mac: dice BUILD
SUCCESSFUL y no crea nada.
- 🎁 Los gráficos NO necesitaron `expect/actual`: `ImageBitmap`/`Canvas`/`readPixels` ya son
  multiplataforma. ⚠️ Única excepción, **`decodificarReducido`**: sin `inSampleSize` sería
  REGRESIÓN DE MEMORIA en Android.
- ⚠️⚠️ **`iosX64` FUERA**: Compose MP 1.11.1 no publica para ese target y rompe TODOS los source
  sets con `Unresolved platforms: [iosX64]`, un error que no menciona a Compose.
- ⚠️ **NO se usó el `lifecycle-viewmodel` KMP oficial**: su única versión con iOS exige
  `compileSdk 37`. ⚠️ `@Synchronized`/`LruCache` no existen en común → `PowCerrojo` + LRU a mano.

## 3decies. 🔊 El audio de SF, multiplataforma — y las reglas de voz con red

`android.media` fuera de SF. Faltaba el **aviso de fin de clip** (`PowClip.alTerminar`): sin él el
mapa de voces no se vacía y el peleador acaba mudo.
- ⚠️⚠️ **El `delegate` de `AVAudioPlayer` es una referencia DÉBIL:** si no se guarda en un campo del
  clip, el aviso **no llega nunca**. ⚠️ **`SoundPool.play` abre un flujo NUEVO cada vez** y
  **`AVAudioPlayer.duration` viene en SEGUNDOS** (Android da ms): sin ×1000, subtítulos de 1 ms.
- 🎁 **Las 4 reglas de interrupción salieron a `SfVocesReglas` con 14 tests.** ✅ **`SfArcadeRepository`
  ya no usa `org.json`**, con 2 tests que fijan el snapshot V1 y el `"null"` literal.

## 3undecies-duodecies. 🍏 Compose MP en iOS: assets, strings y las 5 pantallas (cerrado)

Compose MP corre en el simulador; los 107 MB de assets se leen de `<bundle>/assets/STREETFIGHTER/`.
Tutorial, selector de escenario (con miniaturas reales), menú de modos y overlay de carga: **vistos
y correctos**. **Trampas que siguen VIVAS:**
- ⚠️⚠️ **Los `composeResources` NO iban al bundle → la app se CERRABA** con `stringResource`
  (Compose los carga en corrutina: sin error ni log, solo un `.ips`). Causa: `link*Framework*` los
  genera para TEST pero **no para Main**. Lo arregla el `dependsOn` de `shared/build.gradle.kts`
  + la copia de la fase Xcode. **NO lo quites.**
- ⚠️⚠️ **Compose ABORTA si falta `CADisableMinimumFrameDurationOnPhone`** en el `Info.plist`, y
  `INFOPLIST_KEY_…` NO sirve para esa clave. Detalle en `iosApp/README.md`.
- ⚠️ **Assets: NO por "folder reference" azul.** El proyecto usa `PBXFileSystemSynchronizedRootGroup`
  (Xcode 16), que aplasta subcarpetas. Va por fase `rsync` + `ENABLE_USER_SCRIPT_SANDBOXING = NO`.
- 🆕 `SfEscaparate` = índice navegable y detector de assets/audio/recursos. **Se borra cuando exista
  la navegación real.**

## 3terdecies. 🥊🍏 LA PELEA CORRE EN iOS — verificada en el simulador (Mac, 07-28)

**El modo pelea es JUGABLE en iOS.** Motor offline (`StreetFighterViewModel` + 7 parciales) en
`commonMain`; BT/LAN/WebRTC siguen Android-only. **VISTO en el simulador:** selección de peleador
con sprites y candado → dificultad → **pelea real** (escenario, barras, KO, daño, derribo) →
**ronda completa hasta `PERDISTE`**.
- ✅ **PERSISTENCIA COMPLETA, la cadena entera:** al mandar la app al fondo escribe el snapshot en
  `NSUserDefaults` (`playerId`, paso 1/15, escalera de 15 rivales, `mapFile`, `cpuRoundWins`,
  `paused:true`); al volver sale **PAUSA + Continuar** con el marcador intacto; y tras **cerrar y
  reabrir** ofrece *"Hay una pelea de arcade a medias, ¿retomar?"* → CONTINUAR reanuda de verdad.
- ⚠️ **Lo que NO puedo verificar yo: OÍR.** El audio carga (`.m4a` y `.mp3` dan clip no nulo y
  `reproduciendo=true`), pero que suene bien —mezcla, cortes de voz, música— **lo tiene que
  escuchar una persona**. Es lo único del guion que queda sin firmar.
- 📐 **Observación:** la pelea gira a **horizontal** y los overlays de pausa/resultado vuelven a
  vertical. Funciona, pero conviene comparar con Android antes de darlo por bueno.

## 3quaterdecies. 🧭 Menú principal en `commonMain` + el patrón de separación, escrito

`MainMenuScreen` (632 líneas) bajó a `:shared`. **Port real, no un `git mv`**: usaba
`hiltViewModel()`, `LocalContext`, `SettingsRepository`, `BuildConfig`, `AuthManager` y
`LocalConfiguration`. **MEDIDO: 218 tests, 0 fallos; `assembleDebug` OK; compila para iOS.**
- 🆕 **`10_ARQUITECTURA_SEPARACION.md` §2bis** documenta los **4 mecanismos** de separación
  (expect/actual · fuente instalable · Environment · **Controller**), la regla para elegir y lo que
  NO se hace nunca. Es lo que faltaba para que alguien menos experto toque `:shared` sin romperlo.
- El menú es el **ejemplo canónico del Controller**: UNA pantalla; lo de Android entra como lambda
  (`onMultiplayer`) y como **slot** (`chipDeCuenta`).
- ✅ **PRE-ALPHA/BETA fuera de iOS** (`versionName` null, `mostrarInsignias` false); Android idéntico.
  ⚠️ El controller devuelve solo el NÚMERO: el texto envolvente sigue en `Res.string.menu_version`,
  traducido — formatearlo allí habría perdido el inglés.
- ⚠️ **Falta VERLO en el simulador**: compila, pero aún no está enlazado a ninguna navegación.

## 4. PENDIENTE — por prioridad

### 🔴 P0 · Antes/durante el release
1. **Probar el multijugador en dispositivo:** que los 2 jugadores **oigan** lo mismo (tag
   `SF-NET`). BT y LAN son lo que hay que validar sí o sí.
2. **Redeploy de `MultiplayerSF/` en Render** para activar el P2P (no bloquea el release).
   Con 2 teléfonos en redes distintas, buscar en logcat `SF-RTC`: `DataChannel → OPEN`.
3. **🍏 TERMINAR SF EN iOS.** La pelea ya está verificada (§3terdecies) y el menú portado
   (§3quaterdecies). Reparto acordado, y el ORDEN importa porque hay dependencia:
   - **Windows/Sol PRIMERO** → `PROMPT_SOL_ajustes_y_coleccionables.md`: `SettingsRepository` a
     `:shared`, Ajustes (~1 524 líneas, pártelas) y Coleccionables (378). **Y verificar en el
     emulador que Android no se rompió** — en el Mac NO hay AVD, eso solo se puede hacer allí.
   - **Mac DESPUÉS** → navegación real de iOS (menú → Ajustes/Coleccionables/SF), verlo en el
     simulador y **borrar `SfEscaparate`**. No se puede escribir antes: necesita esas pantallas.
   🔑 El desbloqueo por Modo Desarrollador **saldrá solo**: `KEY_DEVELOPER_MODE = "DEVELOPER_MODE"`
   es la MISMA clave que `IosStreetFighterEnvironment` ya lee de `NSUserDefaults`.
   ⚪ Mundo abierto (exteriores e interiores) = **trabajo futuro**, fuera de iOS por decisión del dueño.

### 🟠 P1 · AUDIO (activo) — ver `SF/PROMPT_traspaso_audio_subtitulos.md`
**29 clips demasiado largos** y **5 fuera de −16 ±2 LUFS** → **Gemini 3.6** (con los segundos del
dueño). **Faltan `attack`/`hurt`** en 6 peleadores → el dueño graba. ⚠️ **No repitas** el resumen
que dice "100 % normalizados y sin faltantes": está medido y es **falso** (de los 82 `.m4a` solo
**69 son voz**; los SFX globales no se normalizan como voces).

### 🟢 P2 · Animaciones congeladas (el arte se repite, **no es bug de código**) · 🔵 P2b motor
`stun-1==stun-2==stun-3` en los 18; `bonus-7/8/9/10` estáticos en `lapresidenta`; `run-4==run-5` en
4; `forwards-3==forwards-4` y `throw-2==throw-3` en 3; `super-4==super-5` en 2.
**P2b:** sigue la fase 2c (`SfEngine` + modos como estrategia), en `SF/PLAN_refactor_motor_compartido.md`.

### ⚪ P3 · Bloqueado en el dueño / deuda conocida
- **Mapas UAM Azcapotzalco y Cuajimalpa:** faltan vídeos → `tools/build_map_backgrounds.py`. Y el
  **arte V2 de La Presidenta + metamorfosis**: croma en `tools\_para_corregir\` sin importar.
- **detekt NO está a 0:** 5 smells preexistentes. Varios docs dicen "0 smells" y es **falso**.
- **Paparazzi 5** con audio de Paparazzi 1; **Señor de la tienda** con un tramo de Prankedy;
  **La Tzitzimime** mal recortada. Los 3 requieren al dueño.

## 5. Verificación antes de cerrar CUALQUIER sesión

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```
En Windows `.\gradlew.bat`. En el Mac, antes: `export JAVA_HOME="/Applications/Android
Studio.app/Contents/jbr/Contents/Home"`, y además `:shared:iosSimulatorArm64Test` +
`:shared:linkDebugFrameworkIosSimulatorArm64`. ⚠️ La tarea de `:shared` es **`testAndroidHostTest`**
(cambió con AGP 9). ⚠️ **Son 218: 114 `:app` + 104 `:shared`**; los "190"/"184"/"178"/"47" de
docs viejos son **stale**. ⚠️ Si tocas `commonTest`, pasa **`bash tools/check_kmp_test_names.sh`**.

**detekt — EXACTAMENTE la invocación de CI** (desde la raíz del repo):
```bash
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
```
Debe salir **exit 0**. ⚠️ Un doc viejo decía "NO uses `--build-upon-default-config`": era de antes
del baseline. **CI SÍ lo usa.** ⚠️ El input incluye `:shared`, o no se analiza el módulo compartido.
`git status` debe mostrar **solo** lo que tocaste. Y **actualiza este archivo** antes de terminar.
