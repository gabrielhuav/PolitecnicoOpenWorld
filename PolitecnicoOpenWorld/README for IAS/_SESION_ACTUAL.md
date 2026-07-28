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

**Última actualización:** 2026-07-28 · Opus 5 · rama `fase0-auditoria-kmp` · *purgar §3bis el 07-30*

> ➡️ **AHORA:** 1.0.0.14 en revisión en Play. KMP: **Fases 0-4 COMPLETAS y verdes (184 tests).**
> 🍏 iOS arranca (§3ter) · Kotlin 2.3.21 (§3quater) · refactor (§3quinquies) · motor compartido
> (§3sexies) · auditado en Mac + guarda de CI (§3septies) · **iOS = solo modo pelea** (§3octies).
> **Siguiente: `PLAN_SF_EN_iOS.md` — se ejecuta EN EL MAC, paso a paso.**

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

## 3ter. iOS arrancó + Fase 1.5 (Mac) — el mapa Leaflet corre en el simulador

`WorldMapLeafletHtml.kt` vive en `:shared`; `:shared` produce un framework (`baseName="Shared"`)
y la app iOS está en `iosApp/` (SwiftUI + `WKWebView`).
- ⚠️ **El runtime del simulador lo instala XCODE** (`xcodebuild -downloadPlatform iOS`), no un DMG
  a mano: el DMG queda en cuarentena y los tests mueren con `Abort trap` (134).
- ⚠️ **Genera el framework ANTES de abrir Xcode** (`:shared:linkDebugFrameworkIosSimulatorArm64`)
  o sale `No such module 'Shared'`.
- ⚠️ **`gradle-wrapper.jar` está en `.gitignore` → NO viene por git.** Al regenerarlo, revierte
  `gradlew`/`.bat`/`.properties` y quédate solo con el `.jar`.
- 🔴 **Deuda:** las rutas de assets ya están parametrizadas, pero el handler de iOS devuelve 404
  (los assets no se empaquetan hasta la Fase 6).

## 3quater. ⬆️ Kotlin 2.3.21 + DSL de AGP 9 (cerrado; trampas que siguen VIVAS)

Kotlin **2.2.10 → 2.3.21** · KSP **2.3.10** · Hilt **2.60.1** · Ktor **3.5.1** · Compose BOM **2026.06**.
- ⚠️⚠️ **NO SE PUEDE SUBIR A KOTLIN 2.4: KSP NO EXISTE para 2.4** (medido: 0 versiones). Y aquí KSP
  es obligatorio (Hilt + Room). **2.3.21 es el techo real.** No pierdas una sesión intentándolo.
- ⚠️ **Lo caro fue el DSL de AGP 9**, no Kotlin: con `newDsl=false` el script NO compila; con
  `true`, `kotlin-android` es incompatible. Única vía: **`android.builtInKotlin=true`** (`:app` ya
  NO aplica `kotlin-android`; `kotlinOptions` → `compilerOptions`), y `:shared` con
  **`com.android.kotlin.multiplatform.library`** (AGP 9 ya no admite `com.android.library` con KMP).
- ⚠️⚠️ **Los tests de `:shared` son `testAndroidHostTest`**, NO `testDebugUnitTest` (cambió con AGP 9).
- ✅ iOS re-verificado en el Mac tras el salto: el `.klib` se regenera entero y sigue verde.

## 3quinquies. Refactor de TAMAÑO — detalle en `10_ARQUITECTURA_SEPARACION.md`

`StreetFighterViewModel` **6220 → 2299** (8 parciales) · `StreetFighterScreen` **4029 → 1902** ·
`ZombieGameScreen` **1664 → 1343**. Verificado tras CADA extracción.
- ⚠️ **Patrón PARCIAL:** los CAMPOS se quedan en la clase; `private` → `internal` solo lo justo;
  **NUNCA recrees en la clase una función de un parcial** (gana la clase EN SILENCIO). Y no se
  puede mover una extensión sobre otro tipo declarada dentro de la clase (doble receptor).
- ⚠️ Trampas del extractor (en `10` §8): **LF vs CRLF**, **KDoc partido**, **`inline fun` que
  pierde el receptor**, **contar llaves falla con cuerpos-expresión** (`fun f() = …`) y los
  **819 imports muertos** que hubo que limpiar aparte.

## 3sexies. Motor compartido en `:shared` CON TESTS

`SfPhysics.resolvePushboxes` · `SfCamera.follow` · `SfSplashes.advance` · `SfHealthBar.rollUp`.
Vivían dentro del VM de Android: **0 tests y 0 iOS**. Probado además en el emulador (pelea real).
- ⚠️ **DOS constantes que me inventé y habrían cambiado el juego EN SILENCIO** (cazadas al
  contrastar contra el original): `DRAIN_PER_SEC` es **200f** (puse 60f) y el tope de cámara lleva
  **`+ STAGE_PADDING`**. Ahora las fijan tests, porque el compilador no las ve.
- 📌 **`:shared` sigue siendo ~5% del código.** La lógica de pelea son extensiones de un ViewModel
  de Android (`applyAttackHit` toca **29 campos del VM**). Sacar eso es la Fase 5.

## 3septies. iOS AUDITADO (Mac) + guarda para no repetir el viaje

**✅ Los 66 tests de `:shared` PASAN en el simulador de iOS**, incluidos los 17 nuevos del motor
compartido (`SfPushboxesTest` 7, `SfSimulationTest` 10). El framework enlaza y la app iOS sigue
pintando el mapa. Android: 112 + 66 = 178, 0 fallos. **El motor compartido cruza a Kotlin/Native
sin tocar una sola aserción** — o sea la extracción fue correcta, no solo compilable.
- ⚠️ **Único fallo: 4 nombres de test con `(` `)` `,`** — Kotlin/Native los rechaza y la JVM no.
  Era el gotcha nº4 de 09 §🍏 KMP/iOS, **ya documentado, y aun así volvió a pasar**.
- 🆕 **Por eso ahora hay una GUARDA mecánica**: `tools/check_kmp_test_names.sh`, en el
  `pr-quality-gate`. Corre en Linux en 2 segundos y convierte un viaje a la Mac en un fallo de CI.
  Verificada contra los 4 nombres reales que fallaron. **Documentar no bastó; esto sí.**

## 3octies. 🍏 iOS = SOLO el modo pelea (decisión del dueño, 07-27)

En iOS el menú principal muestra **únicamente AJUSTES, COLECCIONABLES y HUELUM VS. GOYA**. Mundo
Libre, Modo Historia y Multijugador se **esconden**. **MEDIDO: `:app` 112 + `:shared` 72 = 184
tests, 0 fallos; detekt exit 0; PROBADO en el emulador — el menú de Android sigue idéntico.**
- El catálogo vive en **`PowModos.kt`** (`:shared`, dominio). **Lo único `expect/actual` es
  `plataformaActual`**; qué modos hay es lógica PURA → **la regla de iOS se testea desde Android**,
  sin Mac. `PowModosTest` (6) se pone rojo si alguien añade un modo a iOS: obliga a confirmar que
  FUNCIONA en el simulador, no solo que el botón aparece. La UI solo pregunta `PowModo.X.disponible()`.
- ⚠️ **Esconder el botón NO porta el modo.** Que el menú de iOS liste "Huelum vs. Goya" no lo hace
  jugable allí: **SF son 14 618 líneas y siguen atadas a Android** (9 archivos `context.assets`,
  6 `android.graphics`, 3 `android.media`, 4 ViewModel, +107 MB de assets sin empaquetar).
- 🆕 **`PLAN_SF_EN_iOS.md`** = el desglose medido y el orden de ataque (6 pasos, cada uno
  verificable en el simulador). **Se ejecuta EN EL MAC**: todo lo que queda es Compose
  Multiplatform, assets y audio de iOS, y **nada de eso compila en Windows**.
- 🆕 La guarda de nombres de test **cazó 2 comas en `PowModosTest` a las 2 h de existir**, en
  Windows y en 2 segundos. Mismo error que ya tenía gotcha escrito.

## 4. PENDIENTE — por prioridad

### 🔴 P0 · Antes/durante el release
1. **Probar el multijugador en dispositivo:** que los 2 jugadores **oigan** lo mismo (tag
   `SF-NET`). BT y LAN son lo que hay que validar sí o sí.
2. **Redeploy de `MultiplayerSF/` en Render** para activar el P2P (no bloquea el release).
   Con 2 teléfonos en redes distintas, buscar en logcat `SF-RTC`: `DataChannel → OPEN`.
3. **🍏 FASE 5 (2/2) — que el modo pelea CORRA en iOS. Guion completo en `PLAN_SF_EN_iOS.md`.**
   Ya está hecho el **catálogo de modos** (§3octies): el menú de iOS ya sale recortado. Falta lo
   caro: Compose MP, `expect/actual` de assets/audio/gráficos, el ViewModel multiplataforma y
   empaquetar 107 MB. **SESIÓN DE MAC, sin excepción** — nada de esto compila en Windows.
   Empieza por los **pasos 1-4**: valen aunque el 5 (la UI) se retrase.

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
⚠️ **Son 184 tests: 112 `:app` + 72 `:shared`** (contando los XML de `build/test-results`).
Los "178", "131" y "47" de docs viejos son **stale**.
⚠️ Si tocas `shared/src/commonTest`, pasa antes **`bash tools/check_kmp_test_names.sh`**.

**detekt — usa EXACTAMENTE la invocación de CI** (desde la raíz del repo):
```bash
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
```
Debe salir **exit 0**. ⚠️ Un doc viejo decía "NO uses `--build-upon-default-config`": eso era de
antes del baseline. **CI SÍ lo usa**, con el baseline, que es lo que perdona la deuda vieja.
⚠️ El input incluye `:shared`: sin él, el módulo compartido no se analiza.

`git status` debe mostrar **solo** lo que tocaste. Y **actualiza este archivo** antes de terminar.
