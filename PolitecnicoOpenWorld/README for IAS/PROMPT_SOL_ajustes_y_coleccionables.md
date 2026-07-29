# ⚙️ PROMPT para Sol 5.6 (Windows) — Ajustes y Coleccionables a `commonMain`

**Creado:** 2026-07-29 · Por: Opus 5 desde el **Mac** · Rama: `fase0-auditoria-kmp`

> **Cómo usar este archivo.** Todo lo que hay debajo de la línea es el prompt: cópialo entero.

**Por qué se delega:** son ~1 900 líneas mecánicas con 99 strings, y el patrón ya está resuelto y
documentado. Windows type-checkea iOS y —lo importante— **tiene emulador Android para demostrar que
no hay regresión**; en el Mac no hay ningún AVD. La navegación de iOS y la verificación visual se
quedan para el Mac, porque **no puede escribirse hasta que estas pantallas existan en `commonMain`**.

---

Sigo con POW. Repo (¡carpeta doble!):
`C:\Users\gabri\Documents\GitHub Desktop\PolitecnicoOpenWorld\PolitecnicoOpenWorld`
Rama `fase0-auditoria-kmp`. **`git pull` antes de nada**: viene trabajo del Mac.

## LEE ESTO PRIMERO, ES LO QUE EVITA QUE ROMPAS ALGO

**`README for IAS/10_ARQUITECTURA_SEPARACION.md` §2bis.** Explica los **cuatro mecanismos** para
separar iOS de Android y cuál usar. Aquí vas a usar el **nº 4 (Controller)** casi siempre.

El ejemplo canónico ya está hecho y funcionando: `MainMenuScreen` en `commonMain` +
`MainMenuController` + `AndroidMainMenuController`. **Cópialo, no inventes otro patrón.**

## ESTADO — verificado en el simulador, no lo rehagas

La pelea de SF **corre, se juega y se guarda en iOS**: ronda completa, audio (`.m4a`), y persistencia
al minimizar y al cerrar/reabrir. `MainMenuScreen` ya está en `commonMain` y compila para iOS.
**218 tests (114 `:app` + 104 `:shared`), 0 fallos.**

⚠️ **NO toques:** `iosApp/`, el `Info.plist`, la fase `rsync`, el `dependsOn` de
`assemble*MainResources`, ni `SfArcadeRepository`. Kotlin se queda en **2.3.21**; `iosX64` fuera.

## TAREA 1 — `SettingsRepository` a `:shared` (hazlo PRIMERO, lo desbloquea todo)

**227 líneas y UN SOLO import de Android** (`android.content.Context`). Por dentro ya usa
`multiplatform-settings`, así que es casi portable tal cual.

⚠️ **En Android tiene que seguir envolviendo el MISMO fichero de preferencias.** Si cambia, los
jugadores pierden ajustes y nombre. Eso ya está resuelto por `SharedPreferencesSettings`: consérvalo.

🔑 **El dato que lo conecta todo:** su clave `KEY_DEVELOPER_MODE = "DEVELOPER_MODE"` es **la misma**
que `IosStreetFighterEnvironment` ya lee de `NSUserDefaults`. O sea: **en cuanto Ajustes funcione en
iOS, el desbloqueo de todos los peleadores por Modo Desarrollador funciona solo.** No escribas
lógica nueva de desbloqueo — ya existe en `StreetFighterViewModel.devUnlockAll()`.

## TAREA 2 — Ajustes a `commonMain` (~1 524 líneas, 94 strings, 5 `Context`)

| Fichero | Líneas | Strings | `Context` |
|---|---:|---:|---:|
| `SettingsSections.kt` | 742 | 65 | 3 |
| `SettingsScreen.kt` | 290 | 5 | 0 |
| `ControlsTutorial.kt` | 250 | 18 | 2 |
| `SettingsViewModel.kt` | 168 | 0 | 0 |
| `SettingsState.kt` | 51 | 0 | 0 |
| `SettingsCategory.kt` | 23 | 6 | 0 |

⚠️ **`SettingsSections.kt` son 742 líneas: pártelo al bajarlo.** El dueño pidió explícitamente
"código nivel senior, sin clases enormes". Una sección por fichero
(`SettingsSectionAudio.kt`, `…Interfaz.kt`, …) y un fichero que las orqueste.

⚠️ **Ajustes tiene opciones del MUNDO ABIERTO** (proveedor de mapa, widgets de caché/FPS/zoom…) que
**en iOS no existen**. NO las borres ni bifurques la UI: **gátealas con el catálogo**, igual que el
menú hace con `PowModo.MUNDO_LIBRE.disponible()`. Si hace falta una entrada nueva en `PowModos`,
añádela **con su test**.

## TAREA 3 — Coleccionables a `commonMain` (378 líneas, 5 strings, 3 `Context`)

`CollectiblesScreen` + `CollectiblesViewModel`. Lee de Room, que ya es multiplataforma.
Usa `PowAssets` para las imágenes (nada de `context.assets`).

## EL PATRÓN, en concreto

```
androidx.compose.ui.res.stringResource  →  org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.R            →  import ovh.gabrielhuav.pow.shared.recursos.Res
                                            import ovh.gabrielhuav.pow.shared.recursos.*
R.string.loQueSea                       →  Res.string.loQueSea
LocalConfiguration (orientación)        →  BoxWithConstraints { maxWidth > maxHeight }
hiltViewModel() / LocalContext          →  un Controller inyectado (patrón 4)
```

Si te falta una clave de string: añádela a **los dos** `shared/src/commonMain/composeResources/
values/strings.xml` y `values-en/strings.xml`, y **bórrala de `app/src/main/res/values*`**. No puede
haber dos fuentes de verdad.

**Mueve con `git mv` CONSERVANDO EL PAQUETE**: así `:app` no cambia ni un import.

## CÓMO SE VERIFICA — y esto es lo que más importa de tu sesión

```bash
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
.\gradlew.bat :shared:compileKotlinIosSimulatorArm64
bash tools/check_kmp_test_names.sh
```
Esperado: **114 + 104 = 218 tests, 0 fallos.**

**Y EN EL EMULADOR ANDROID, que es tu ventaja y aquí nadie más la tiene:**
1. Abre **Ajustes** y recorre **todas** las categorías: que no falte ningún texto ni sección.
2. Cambia idioma a inglés y vuelve: los 99 strings migrados deben seguir traducidos.
3. Activa **Modo Desarrollador** → entra a Huelum vs. Goya → **todos los peleadores desbloqueados**.
4. Desactívalo → vuelven a salir bloqueados.
5. Abre **Coleccionables**: que se vean las imágenes y el progreso.
6. Sal y vuelve a entrar: los ajustes **persisten** (esa es la prueba de que no se rompió el fichero
   de preferencias).

⚠️ **`linkDebugFrameworkIosSimulatorArm64` NO vale como prueba en Windows**: dice BUILD SUCCESSFUL y
no produce nada. Solo el Mac lo enlaza de verdad.

## LO QUE **NO** HACES EN ESTA SESIÓN

- **Nada de navegación de iOS.** Eso es del Mac, y no se puede escribir hasta que estas pantallas
  estén en `commonMain`.
- **No borres `SfEscaparate.kt`.** Es el banco de pruebas del Mac; se borra cuando exista la
  navegación real.
- No portes BT/LAN/WebRTC ni el mundo abierto (queda como trabajo futuro, decisión del dueño).

Al terminar: actualiza `_SESION_ACTUAL.md` (**techo 200 líneas**), deja **medido** qué pasó y di
exactamente qué falta. `git pull` inmediatamente antes de cada push.
