# 🍏 `iosApp` — la app iOS de POW

**Actualizado:** 2026-11-21 · Todos los números están **medidos**, no estimados.

**Esto ya es el juego.** Arranca en el menú principal y desde ahí se llega a **Ajustes**,
**Coleccionables** y **Titulación por Combate** (el modo pelea, completo: escalera arcade, audio,
guardado y modo desarrollador). Verificado en el simulador el 2026-07-30.

**Lo que NO está en iOS y es a propósito:** mundo abierto, interiores, Modo Historia y todo el
multijugador. No es que falte tiempo — está explicado en
[`README for IAS/11_SEPARACION_IOS_ANDROID.md`](../README%20for%20IAS/11_SEPARACION_IOS_ANDROID.md) §5.

**🍏 Esta es la versión que va a la App Store (decisión del dueño, 2026-11-21).** El menú no pinta
nada en obras (`MODOS_EN_OBRAS_VISIBLES = false`) y el botón MULTIJUGADOR del modo pelea tampoco se
pinta en iOS (`sfMultijugadorDisponible()`). Lo exclusivo de Android sigue en el código.

---

## Cómo compilarlo

**PR #188, iPhone 15 real / iOS 26.7.1:** instalación, práctica y audio observados; Titulación por Combate entra en horizontal automática, con selectores/controles en zona segura, fondo que cubre el Canvas y arreglo de proyectiles. Ambos frameworks, Xcode e instalación finales: PASS; **849 ejecuciones de tests sin fallos** (153 app + 348 shared JVM + 348 shared iOS). Galería revisada: 60 fondos, 19 peleadores y 61 PNG originales. Charro/Charro IA termina **2–0**, visible hasta 180 s (método PASS 208,254 s); dueño confirmó audio. Limpieza PASS 9,748 s: subtítulos OFF, hitboxes/FPS OFF, DevMode ON y menú; solo nuestro runner retirado.

Por elección del dueño, el README principal muestra siempre visibles 9 capturas (tres peleas en sus tres luces) y guarda las 52 restantes en **18 desplegables cerrados de hasta tres** (la galería de `pr-screenshots/iphone15-pr188/README.md` conserva las 61 en 21); la descripción del PR también contiene los 61 originales en 21 grupos cerrados (20 de tres y uno de una), con encabezados, resúmenes, pies de foto y prosa QA en inglés. Se añadieron 50 adjuntos y se conservaron las 11 URL previas. El comentario de QA en inglés enlaza esa descripción y conserva los ocho ZIP, sin imágenes duplicadas. Los desplegables controlan visibilidad y **no garantizan que la descarga espere al clic**. Lazy permanece en la fuente, aunque GitHub lo filtra; el visor HTML y las páginas Markdown de tres quedan como alternativas locales opcionales. Este ajuste no repitió builds ni pruebas. El dueño autorizó commit y push al mismo PR #188 el 2026-11-16. El commit de correcciones 79e1eb94 está publicado en la rama del PR #188. README, documentación y galería se incorporan con el commit de esta entrega. No se autoriza merge ni se creó otro hosting. [Informe físico y límites](../README%20for%20IAS/SF/QA_IPHONE_PR188_2026-11-15.md).

**Verificado 2026-11-15:** build Debug, control táctil, práctica y Arcade en iPhone 17 / iOS 27.0. [Informe con alcance y evidencia](../README%20for%20IAS/SF/QA_ANDROID_IOS_2026-11-15.md).

El proyecto Xcode **NO** construye por sí solo el framework de Kotlin: hay que generarlo antes,
desde la raíz Gradle (`PolitecnicoOpenWorld/`, la carpeta de arriba):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64
```

Se generan **los dos destinos incluso al usar solo uno**: la fase de Xcode copia los textos desde
`assembledResources/iosSimulatorArm64Main` también al construir para iPhone. Ver guía 14, §2.

El framework del simulador queda en `shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework`, que
es exactamente donde apunta `FRAMEWORK_SEARCH_PATHS`. Después se abre `POW.xcodeproj` y ⌘R.

> Si Xcode dice **`No such module 'Shared'`**, falta ese paso de Gradle. No es un problema del
> proyecto Xcode.

⚠️ **Ese comando hay que repetirlo cada vez que cambies Kotlin.** Xcode no se entera solo.

### En un iPhone de verdad

> 📲 **La receta completa, paso a paso (Android Studio, Xcode y terminal; simulador e iPhone), está
> en [`README for IAS/14_INSTALAR_EN_IPHONE_Y_SIMULADOR.md`](../README%20for%20IAS/14_INSTALAR_EN_IPHONE_Y_SIMULADOR.md).**
> Esta sección solo explica el porqué.

Es **otro** framework, y el proyecto ya está preparado para elegirlo solo
(`FRAMEWORK_SEARCH_PATHS[sdk=iphoneos*]`):

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64
```

**MEDIDO el 2026-08-14: enlaza bien (4m 12s la primera vez)** y deja
`shared/build/bin/iosArm64/debugFramework/Shared.framework`. Tarda mucho más que el del simulador
porque Kotlin/Native compila de verdad para ARM en vez de reutilizar el caché del host.

⚠️ **La firma no va en el proyecto, y es a propósito:** el target no tiene `DEVELOPMENT_TEAM` (está en
`CODE_SIGN_STYLE = Automatic`, sin equipo) para que el repo no lleve la cuenta de nadie. El equipo se
pone al compilar: en la configuración `iosApp.POW` de Android Studio (campo *Development team*) o con
`DEVELOPMENT_TEAM=<equipo> -allowProvisioningUpdates` en `xcodebuild`. Si lo eliges en Xcode → Signing
& Capabilities, eso modifica `project.pbxproj`: no lo subas. **Con un Apple ID gratis basta para correrlo
en tu propio iPhone** (el perfil caduca a los 7 días y hay que reinstalar); la cuenta de 99 USD/año
solo hace falta para TestFlight y la App Store. `IPHONEOS_DEPLOYMENT_TARGET` es **16.0**.

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
