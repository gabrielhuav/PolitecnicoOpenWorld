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

**Última actualización:** 2026-07-27 · Opus 5 · rama `fase0-auditoria-kmp` · *purgar §3 el 07-28*

> ➡️ **AHORA:** 1.0.0.14 en revisión en Play. KMP: **Fases 0-4 COMPLETAS y verdes.**
> 🍏 iOS arranca (§3ter) · Kotlin 2.3.21 (§3quater) · refactor de tamaño (§3quinquies) ·
> **motor compartido avanzando** (§3sexies: 178 tests). Siguiente: **auditar en el Mac**.

## 🖥️ Rutas por PC

| PC | Raíz del PROYECTO (aquí están `gradlew` y `tools/`) |
|---|---|
| **Laptop** (referencia) | `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| **Escritorio** | *distinta — COMPLETAR con la real* |
| **🍏 Mac** (iOS) | `/Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld/PolitecnicoOpenWorld` |

⚠️ **La carpeta es doble**: el proyecto Gradle está DENTRO del repo, en `PolitecnicoOpenWorld/`.
Solo cambia el prefijo: las rutas de los docs son **relativas a la raíz**. El GEN de sprites vive
FUERA del repo en `..\newSFAssets\GEN_*`.

## 1. Organización

Mapa completo en **`00_INDEX.md`**. No te saltes **`10_ARQUITECTURA_SEPARACION.md`** (dónde vive
cada cosa) ni **`09_CONVENTIONS_GOTCHAS.md`**; y **`PLAYSTORE_formulario_seguridad_datos.md`**
antes de tocar la ficha. `SF/` = peleas · `MUNDO/` = mundo libre · `_ARCHIVO/` = histórico, NO tareas.

## 2. A quién delegar

**Alta** (refactors grandes, sistemas nuevos, lo que ya falló dos veces) → **Sol 5.6 / Fable 5** ·
**Media** (feature acotada, auditoría, un solo módulo) → **Opus 4.8/5** · **Baja** (regenerar
assets con pipeline existente, tareas repetitivas) → **Gemini 3.6**.
**Antes de delegar:** rutas absolutas, comando exacto, cómo se verifica y qué NO tocar.
🆕 Para que una IA pequeña se oriente sola, pásale **`10_ARQUITECTURA_SEPARACION.md`**.

## 3. Sesión 2026-07-26 — PURGADA (salió en la 1.0.0.14)

Detalle en `_ARCHIVO/HISTORIAL_sesiones_2026-07-26.md`. ⚠️ Lo único VIVO de ahí está en los P0 de
§4 (probar multijugador + redeploy Render).

## 3bis. Fases 0-4 COMPLETAS + auditoría (detalle en `PLAN_MIGRACION_KMP.md` y `git log`)

`:shared`; `GeoPoint` propio (osmdroid: 51 → 7 archivos); Gson fuera con `PowJson`; Fase 4 =
`multiplatform-settings` + Room 2.8.4 KMP + Ktor. **Trampas VIVAS:**
- ⚠️ **NO cambies la ruta de la BD** (`filesDir/databases/pow_roads.db`) ni el driver de Android
  (el bundled es solo para iOS): se perdería caché y landmarks del Diseñador.
- ⚠️ **`PowJson` imita a Gson a propósito**; tocarlo rompe saves y clientes viejos EN SILENCIO.
  **`SfArcadeRepository`** es el ÚNICO con migración real de datos (`putStringSet` → JSON `_V2`).
- ⚠️ **`GeoPoint` = port LITERAL de osmdroid**: `x*x` ≠ `.pow(2)`, no lo "simplifiques".
  **Ktor sin `HttpTimeout`** y ping 25s/20s: evitan que se caiga la partida.
- ⚠️ **`dependencies { add("kspAndroid"…) }` va DESPUÉS de `kotlin { }`** en `shared/build.gradle.kts`.

### 🔴 AUDITORÍA fases 1-4 — 12 bugs que el compilador NO ve (corregidos)
(1) `PowJson.encodeToString(x)` COMPILA y **PETA EN RUNTIME** si `x` no es serializable → con
mapas usa `jsonOf`/`jsonArrayOf`. (2) **kotlinx PETA si falta un campo sin default** → **todo
campo nuevo lleva default**. Los fijan `PayloadsSeSerializanEnRuntimeTest` y
`ModelosToleranJsonIncompletoTest`.

## 3ter. Sesión 2026-07-27 (Opus 5, EN EL MAC) — 🍏 iOS arranca + Fase 1.5

**✅ `:shared` compila, enlaza y sus 49 tests PASAN en el simulador**; Room generó su `actual` sin
tocar nada. **✅ El mapa Leaflet del juego CORRE en iOS** (visto en pantalla, con pinch-zoom):
`WorldMapLeafletHtml.kt` vive en `:shared`, `:shared` produce un framework (`baseName="Shared"`)
y la app iOS está en `iosApp/` (SwiftUI + `WKWebView`).
- ⚠️ **El runtime del simulador lo instala XCODE** (`xcodebuild -downloadPlatform iOS`), no un DMG
  a mano: el DMG queda en cuarentena y los tests mueren con `Abort trap` (134), que NO parece un
  problema de permisos.
- ⚠️ **Genera el framework ANTES de abrir Xcode** (`:shared:linkDebugFrameworkIosSimulatorArm64`)
  o sale `No such module 'Shared'`.
- ⚠️ **`gradle-wrapper.jar` está en `.gitignore` → NO viene por git.** Se regenera con el Gradle
  cacheado; eso reescribe `gradlew`/`.bat`/`.properties` → **revierte esos 3, quédate con el .jar**.
- ⚠️ **Qué NO prueba este hito:** los 49 tests son **dominio puro**; NO tocan Room, Ktor ni
  Settings en iOS. Y el motor Darwin de Ktor sigue sin ejercitarse.
- 🔴 **Deuda:** las 5 rutas `file:///android_asset/` ya están parametrizadas, pero el handler
  de iOS devuelve 404 (los assets no se empaquetan hasta la Fase 6).

## 3quater. Sesión 2026-07-27 (Opus 5, Windows) — ⬆️ Kotlin 2.3.21 + DSL de AGP 9

**MEDIDO: `:app` 112 + `:shared` 49 = 161 tests, 0 fallos; `assembleDebug` y
`compileReleaseKotlin` OK; detekt exit 0.**
Kotlin **2.2.10 → 2.3.21** · KSP 2.3.2 → **2.3.10** · Hilt 2.57.1 → **2.60.1** ·
Ktor 3.3.3 → **3.5.1** (desbloqueado) · serialization → 1.11.0 · Compose BOM 2024.09 → **2026.06**.
- ⚠️⚠️ **NO SE PUEDE SUBIR A KOTLIN 2.4: KSP NO EXISTE para 2.4** (medido: 0 versiones en esa
  línea; la última, 2.3.10, se construye contra 2.3.20). Y aquí KSP es obligatorio (Hilt + Room).
  **2.3.21 es el techo real.** No pierdas una sesión intentándolo.
- ⚠️ **Lo caro no fue Kotlin, fue el DSL de AGP 9.** Con Kotlin 2.3 lo que era aviso pasa a ERROR:
  con `newDsl=false` el script NO compila; con `newDsl=true` `kotlin-android` es incompatible.
  Única vía: **Kotlin INTEGRADO** (`android.builtInKotlin=true`, `:app` ya NO aplica
  `kotlin-android`, `kotlinOptions` → `compilerOptions`). Y `:shared` pasa de
  `com.android.library` a **`com.android.kotlin.multiplatform.library`** (AGP 9 ya no admite la
  primera junto a KMP), con el target dentro de `kotlin { android { … } }`.
- ⚠️⚠️ **LA TAREA DE TESTS DE `:shared` CAMBIÓ DE NOMBRE:** `testDebugUnitTest` →
  **`testAndroidHostTest`**. Con el viejo, Gradle dice 'task not found'. Ya corregido en CI.
- ✅ **iOS RE-VERIFICADO en el Mac tras el salto** (era el riesgo abierto): el `.klib` se regenera
  entero con 2.3.21 y aun así **49 tests, 0 fallos**; framework relinkado y el mapa igual en el
  simulador. **El cambio de plugin de `:shared` no rompió iOS.**

## 3quinquies. Refactor de TAMAÑO (Windows) — detalle en `10_ARQUITECTURA_SEPARACION.md`

`StreetFighterViewModel` **6220 → 2299** en 8 parciales por dominio · `StreetFighterScreen`
**4029 → 1902** · `ZombieGameScreen` **1664 → 1343**. Verificado tras CADA extracción.
- ⚠️ **Patrón PARCIAL:** los CAMPOS se quedan en la clase; `private` → `internal` solo lo que el
  parcial necesite; **NUNCA recrees en la clase una función de un parcial** (gana la clase EN
  SILENCIO). NO se puede mover una extensión declarada dentro de la clase sobre otro tipo
  (doble receptor), p. ej. `SfInput.hasAttackOrSpecial`.
- ⚠️ Trampas del extractor, en `10` §8: **LF vs CRLF**, **KDoc partido** y el **`inline fun` que
  pierde el receptor**. Y mi extractor dejó **819 imports muertos** que hubo que limpiar aparte.

## 3sexies. Motor compartido — 4 piezas mas a `:shared` CON TESTS (Windows)

**MEDIDO: `:app` 112 + `:shared` 66 = 178 tests, 0 fallos** (`:shared` iba por 49). Ademas
**PROBADO EN EL EMULADOR** (AVD Nexus): arranca, menu, seleccion de peleador y PELEA con ronda 1,
cero errores de POW en logcat. `assembleDebug` + `compileReleaseKotlin` + detekt exit 0.
- `SfPhysics.resolvePushboxes` (empuje entre peleadores) · `SfCamera.follow` · `SfSplashes.advance`
  · `SfHealthBar.rollUp`. Todas vivian dentro del VM de Android: **0 tests y 0 iOS**.
- ⚠️ **DOS constantes que me invente y habrian cambiado el juego EN SILENCIO** (cazadas al
  contrastar contra el original): `DRAIN_PER_SEC` es **200f** (puse 60f) y el tope de camara
  lleva **`+ STAGE_PADDING`**. Ahora las fijan tests, porque el compilador no las ve.
- ⚠️ **Delimitar funciones contando llaves FALLA con cuerpos-expresion** (`fun f() = ...`): se
  come las funciones siguientes. Para esas, reemplazo por texto exacto.
- 📌 **Lo que NO cambio:** `:shared` sigue siendo **~5%** del codigo. La logica de pelea sigue
  escrita como extensiones de un ViewModel de Android; `applyAttackHit` solo toca **29 campos del
  VM**. Sacar eso es la Fase 5 y son varias sesiones.

## 4. PENDIENTE — por prioridad

### 🔴 P0 · Antes/durante el release
1. **Probar el multijugador en dispositivo:** que los 2 jugadores **oigan** lo mismo (tag
   `SF-NET`). BT y LAN son lo que hay que validar sí o sí.
2. **Redeploy de `MultiplayerSF/` en Render** para activar el P2P (no bloquea el release).
   Con 2 teléfonos en redes distintas, buscar en logcat `SF-RTC`: `DataChannel → OPEN`.
3. **🍏 FASE 5 (2/2) — mover la UI a `commonMain` con Compose Multiplatform.** Lo ÚNICO que queda
   entre el mapa y un juego jugable en iOS. ✅ Ya están hechos **la subida de Kotlin** (§3quater),
   **la re-verificación de iOS** y **las 2 deudas de la Fase 1.5** (§3quinquies): la red de
   seguridad existe en las DOS plataformas, así que esto ya no se hace a ciegas.
   ⚠️ El bulto son las ~14 000 líneas de `StreetFighterViewModel`/`StreetFighterScreen` y el muro
   de `android.graphics`. **Exige el compilador de iOS delante** → sesión de Mac.

### 🟠 P1 · AUDIO (trabajo activo) — ver `SF/PROMPT_traspaso_audio_subtitulos.md`
**29 clips demasiado largos** para su evento y **5 fuera de −16 ±2 LUFS** → **Gemini 3.6** (con los
segundos exactos del dueño). **Faltan `attack`/`hurt`** en 6 peleadores → el dueño graba.
⚠️ **No repitas** el resumen que dice "100 % normalizados y sin faltantes": está medido y es
**falso**. De los 80 `.ogg` solo **69 son voz**; 2 no pueden normalizarse sin comprimir.

### 🟢 P2 · Animaciones congeladas (el arte se repite, **no es bug de código**)
`stun-1==stun-2==stun-3` en los 18; `bonus-7/8/9/10` estáticos en `lapresidenta`; `run-4==run-5` en
4; `forwards-3==forwards-4` en 3; `throw-2==throw-3` en 3; `super-4==super-5` en 2.

### 🔵 P2b · Motor compartido — Fases 1, 2a y 2b hechas; sigue **2c**
Siguiente: núcleo de `updateStageConstraints` (empuje de pushboxes) → `SfPhysics`; luego el
esqueleto `SfEngine` y modos como estrategia. **Exige sesión CON compilador.** Plan:
`SF/PLAN_refactor_motor_compartido.md`.

### ⚪ P3 · Bloqueado en el dueño / deuda conocida
- **Mapas UAM Azcapotzalco y Cuajimalpa:** faltan vídeos nuevos → `tools/build_map_backgrounds.py`.
- **Arte V2 de La Presidenta + metamorfosis nuevas:** croma en `tools\_para_corregir\` sin importar.
- **detekt NO está a 0:** 5 smells preexistentes (`CachingWebViewClient`, `NpcAiManager`,
  `RoadRouter`, `CatSpriteManager` ×2). Varios docs dicen "0 smells" y es **falso**.
- `07_OTHER_FEATURES.md` (87 KB) mezcla menú/ajustes con SF; su parte de SF debería migrar a `SF/`.
- **Paparazzi 5** tiene audio de Paparazzi 1; **Señor de la tienda** tiene un tramo con voz de
  Prankedy; **La Tzitzimime** mal recortada. Los 3 requieren al dueño.

## 5. Verificación antes de cerrar CUALQUIER sesión

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```
En Windows `.\gradlew.bat`. En el Mac, antes:
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
⚠️ La tarea de `:shared` es **`testAndroidHostTest`**, NO `testDebugUnitTest` (cambió con AGP 9).
⚠️ **Son 178 tests: 112 `:app` + 66 `:shared`** (contando los XML de `build/test-results`).
Los "131" y los "47" de docs viejos son **stale**.

**detekt — usa EXACTAMENTE la invocación de CI** (desde la raíz del repo):
```bash
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
```
Debe salir **exit 0**. ⚠️ Un doc viejo decía "NO uses `--build-upon-default-config`": eso era de
antes del baseline. **CI SÍ lo usa**, con el baseline, que es lo que perdona la deuda vieja.
⚠️ El input incluye `:shared`: sin él, el módulo compartido no se analiza.

`git status` debe mostrar **solo** lo que tocaste. Y **actualiza este archivo** antes de terminar.
