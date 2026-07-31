# 🍏 `iosApp` — la app iOS de POW

**Actualizado:** 2026-07-30 · Todos los números están **medidos**, no estimados.

**Esto ya es el juego.** Arranca en el menú principal y desde ahí se llega a **Ajustes**,
**Coleccionables** y **Huelum vs. Goya** (el modo pelea, completo: escalera arcade, audio,
guardado y modo desarrollador). Verificado en el simulador el 2026-07-30.

**Lo que NO está en iOS y es a propósito:** mundo abierto, interiores, Modo Historia y todo el
multijugador. No es que falte tiempo — está explicado en
[`README for IAS/11_SEPARACION_IOS_ANDROID.md`](../README%20for%20IAS/11_SEPARACION_IOS_ANDROID.md) §5.

---

## Cómo compilarlo

El proyecto Xcode **NO** construye por sí solo el framework de Kotlin: hay que generarlo antes,
desde la raíz Gradle (`PolitecnicoOpenWorld/`, la carpeta de arriba):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Eso deja el framework en `shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework`, que
es exactamente donde apunta `FRAMEWORK_SEARCH_PATHS`. Después se abre `POW.xcodeproj` y ⌘R.

> Si Xcode dice **`No such module 'Shared'`**, falta ese paso de Gradle. No es un problema del
> proyecto Xcode.

⚠️ **Ese comando hay que repetirlo cada vez que cambies Kotlin.** Xcode no se entera solo.

---

## Cómo está montado — tres archivos y ya

| Archivo | Qué hace |
|---|---|
| `POW/POWApp.swift` | Punto de entrada SwiftUI. Abre una ventana y mete `ContentView`. |
| `POW/ContentView.swift` | El puente Swift ↔ Compose: **un solo** `UIViewController`, a pantalla completa. |
| `POW/MapaWeb.swift` | El mapa Leaflet en `WKWebView`. **Hoy no lo usa nadie** — ver abajo. |

### La regla que importa

> **Para añadir una pantalla a iOS NO se toca este proyecto Xcode.**
> Se toca `shared/src/iosMain/…/PowAppIos.kt`.

Toda la navegación vive en Kotlin. Si se repartiera entre SwiftUI y Compose habría **dos árboles**
que mantener en sincronía, y al volver de una pantalla quedaría un host encima del otro. Con un
host único, cambiar de pantalla es cambiar una variable.

Qué modos aparecen tampoco se decide aquí: lo dice `PowModos.kt`, el catálogo compartido.

---

## Qué entra en el bundle (y qué no)

Lo copia la fase **"Assets de SF al bundle"** del proyecto Xcode, con `rsync`:

| Origen | Destino en el bundle | Peso |
|---|---|---:|
| `app/src/main/assets/STREETFIGHTER/` | `assets/STREETFIGHTER/` | 109 MB |
| `app/src/main/assets/SPRITES/COLLECTIBLES/` | `assets/SPRITES/COLLECTIBLES/` | 736 KB |
| `composeResources` generados por Gradle | `compose-resources/` | 368 KB |

**Del resto de `SPRITES/` (104 MB: NPC, PLAYER, VEHICLES, ZOMBIE, POLICE) no entra nada**, porque
es del mundo abierto y en iOS no existe. El `.app` de debug pesa **196 MB**.

⚠️ **Dos trampas que ya costaron caro:**

1. **Si una imagen sale como placeholder gris, mira PRIMERO si el archivo está en el bundle.**
   Pasó con los coleccionables: la carpeta simplemente no se copiaba. `PowAssets` en iOS lee de
   `<bundle>/assets/…`, así que lo que no copie el `rsync` no existe.
2. **`compose-resources` no lo genera la tarea de link por sí sola.** La genera para los tests y
   **no para Main**. Por eso `shared/build.gradle.kts` tiene un `dependsOn("assemble*MainResources")`.
   Sin él la app compila, arranca y **se cierra en cuanto una pantalla llama a `stringResource`**:
   Compose los carga en una corrutina y nadie recoge la excepción.

---

## Cosas del proyecto Xcode que no se tocan

| Ajuste | Por qué |
|---|---|
| `ENABLE_USER_SCRIPT_SANDBOXING = NO` | Sin esto el `rsync` falla con `Sandbox: rsync deny(1) file-write-create`. |
| `Info.plist` a mano, fuera de `POW/` | Compose Multiplatform aborta al arrancar (`PlistSanityCheck`) si falta `CADisableMinimumFrameDurationOnPhone`. Y si el plist vive dentro de `POW/`, choca con Copy Bundle Resources. |
| `FRAMEWORK_SEARCH_PATHS` condicionado por SDK | Simulador y dispositivo real cogen cada uno su framework. |

El proyecto salió de la plantilla vacía de Xcode y se renombró, en lugar de escribir un
`project.pbxproj` a mano. Usa `PBXFileSystemSynchronizedRootGroup` (Xcode 16), así que **un `.swift`
nuevo en `POW/` se recoge solo**: no hay que editar listas de ficheros a mano.

---

## `MapaWeb.swift`: código vivo que hoy no usa nadie

Es el mapa Leaflet del juego dentro de un `WKWebView`, con los assets servidos por el esquema
propio `pow-asset://`. **Está desconectado a propósito**: el mundo abierto es trabajo futuro en iOS.

Se conserva porque ya está verificado y demuestra lo que hacía falta demostrar: el HTML del mapa
**no se duplica**. `WorldMapLeafletHtmlKt.buildHtml(...)` es la MISMA función Kotlin que usa
`WorldMapScreenWeb.kt` en Android; el prefijo de assets se parametrizó (`assetBaseUrl`) en vez de
bifurcar el HTML, porque en iOS `file:///android_asset/` no existe.

El día que el mundo abierto llegue a iOS, esto se engancha desde `PowAppIos.kt`. Hasta entonces
**no hace falta tocarlo**.

---

## Lo que falta

1. **El framework no se construye solo.** Falta una fase de script que llame a Gradle desde Xcode;
   hoy hay que acordarse a mano.
2. **Sin firma ni App Store.** No hay cuenta de Apple Developer todavía. Cuando la haya, ojo con el
   límite de **200 MB por datos móviles**: el bundle ya va por 196 MB solo con SF.
3. **~12 literales en español** en la UI compartida de SF no pasan por `composeResources`: con la
   app en inglés, el diálogo "Continuar pelea" sale en español.
