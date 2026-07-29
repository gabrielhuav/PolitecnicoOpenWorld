# 🥊 PROMPT para Sol 5.6 (Windows) — bajar el ViewModel de combate a `commonMain`

**Creado:** 2026-07-28 · Por: Opus 5 desde el **Mac** · Rama: `fase0-auditoria-kmp`

> **Cómo usar este archivo.** Todo lo que hay debajo de la línea es el prompt: cópialo entero.

**Por qué se delega:** es lo último que separa a iOS de una pelea real, son 6 433 líneas mecánicas
y **no hace falta Mac**: se type-checkea entero con `:shared:compileKotlinIosSimulatorArm64`.

---

Sigo con POW. Repo (¡carpeta doble!):
`C:\Users\gabri\Documents\GitHub Desktop\PolitecnicoOpenWorld\PolitecnicoOpenWorld`
Rama `fase0-auditoria-kmp`. **`git pull` antes de nada**: viene trabajo del Mac.

Lee `README for IAS/_SESION_ACTUAL.md`, sobre todo **§3duodecies** y PENDIENTE.

## LO QUE YA FUNCIONA EN iOS — verificado en el simulador, no lo rehagas

Las 7 pantallas están en `commonMain` y **4 de ellas se VEN en el simulador**: tutorial, selector de
escenario (con las miniaturas reales), menú de modos y overlay de carga. Los assets (107 MB) y el
audio (`.m4a` y `.mp3`) cargan. **218 tests, 0 fallos, en Android y en iOS.**

⚠️ **NO toques `iosApp/`, el `Info.plist`, la fase de `rsync` ni el `dependsOn` de
`assemble*MainResources` en `shared/build.gradle.kts`.** Eso es del Mac y está afinado: sin ese
`dependsOn`, la app arranca y **se cierra** al abrir cualquier pantalla.

## LA TAREA: `StreetFighterViewModel` y sus 8 parciales → `commonMain`

`StreetFighterScreenCommon` pide un `StreetFighterController` y solo existe el de Android. **Un
controlador falso NO cuenta como pelea portada.** Lo que hay que bajar:

| Fichero | Líneas |
|---|---:|
| `StreetFighterViewModel.kt` | 2278 |
| `StreetFighterCpuAi.kt` | 794 |
| `StreetFighterNet.kt` | 800 ← **NO se porta** (BT/WebRTC; iOS no lleva multijugador) |
| `StreetFighterMaquinaEstados.kt` | 745 |
| `StreetFighterGauntlet.kt` | 540 |
| `StreetFighterCombate.kt` | 502 |
| `StreetFighterArcade.kt` | 287 |
| `StreetFighterVoces.kt` | 275 |
| `StreetFighterTutorial.kt` | 212 |

### Las ataduras a Android son POCAS y están medidas

| Atadura | Usos | Qué hacer |
|---|---:|---|
| `Context`/`appContext` | 30 / 26 | Ver el desglose de abajo: la mayoría se van solas |
| `SystemClock.elapsedRealtime()` | 11 (3 llamadas) | Reloj monotónico compartido. **NO uses el reloj de pared**: se mueve al cambiar la hora y rompería la simulación |
| `android.util.Log` | 4 | Un log mínimo en `:shared`, o quítalos |
| `@Inject` / `@HiltViewModel` | 3 | Se quedan **solo** en el `actual`/fábrica de Android |

**A dónde va cada `appContext` (medido):**

- `SettingsRepository(appContext)` — **5 usos.** Vive en `:app` (227 líneas, **1 solo import de
  Android**). Ya existe `multiplatform-settings` en `:shared`: es el candidato natural a bajar
  primero, y solo.
- `SfBtClient` / `SfLanDiscovery` / `SfLanClient` — **7 usos.** Todos en `StreetFighterNet.kt`, que
  **NO se porta**. No los toques.
- `SfVoicePhrases.load` / `SfSpecialPhrases.load` / `SfCombos.basics` / `.universal` — **4 usos.**
  Estos leen assets: cambia `Context` por **`PowAssets`**, que ya existe y funciona en iOS.
- `appContext.isSfLowEnd()` — **1 uso.** `SfDeviceTier.kt` es de Android. Hace falta un
  `expect/actual` (en iOS puedes devolver `false` de momento; déjalo anotado).
- `getExternalFilesDir(null) ?: filesDir` en `StreetFighterGauntlet.kt:313` — **1 uso.** Es para
  volcar un informe. Decide: `expect/actual` de "directorio de datos", o dejar esa función solo en
  Android. **Escribe cuál elegiste y por qué.**

### Reglas del port

- **`git mv` conservando el paquete**, como en los pasos anteriores: así `:app` no cambia imports.
- **Un commit por fichero o por bloque lógico.** Los 6 433 no van de una tacada.
- Usa lo que ya existe y **no lo reinventes**: `PowAssets`, `PowImagen`, `PowAudio`,
  `PowViewModel.scope`, `PowCerrojo`, `PowJsonLectura`.
- ⚠️ **Patrón PARCIAL:** los CAMPOS se quedan en la clase; **NUNCA recrees en la clase una función
  de un parcial** — gana la clase EN SILENCIO.
- **No copies lógica de combate a `iosMain` ni escribas un segundo motor.**

## SI TE SOBRA TIEMPO: `MainMenuScreen` a `commonMain`

632 líneas, 26 strings, **cero `Context`**. Es lo que hace que el menú de iOS se parezca al de
Android; hoy en iOS **no hay menú**. La regla de qué modos se esconden ya la decide
`PowModos.disponible()` — no inventes otra.

## CÓMO SE VERIFICA (los tres, siempre)

```bash
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
.\gradlew.bat :shared:compileKotlinIosSimulatorArm64
bash tools/check_kmp_test_names.sh
```
Esperado hoy: **114 + 104 = 218 tests, 0 fallos**.

⚠️ **`:shared:linkDebugFrameworkIosSimulatorArm64` NO vale como prueba en Windows**: dice BUILD
SUCCESSFUL y no produce nada.
⚠️ **Prueba una pelea entera en el emulador** tras cada bloque. El ViewModel es el corazón del
combate y los tests no ven daño, cámaras, ni audio.

## LO QUE NO SE TOCA

- Kotlin **2.3.21** (KSP no existe para 2.4). `iosX64` fuera.
- La ruta de la BD, las opciones de `PowJson`, `SfArcadeRepository`, `SfVocesReglas`.
- `SfOnlineOverlays` y `StreetFighterNet`: Android-only a propósito.

Al terminar: actualiza `_SESION_ACTUAL.md` (**techo 200 líneas**), deja **medido** qué pasó y di
exactamente qué falta. Si no llegas al final, **no marques SF como terminado**: di en qué fichero te
quedaste. `git pull` inmediatamente antes de cada push.
