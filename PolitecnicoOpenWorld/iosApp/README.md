# 🍏 `iosApp` — app iOS de POW (Fase 1.5)

**⚠️ Esto NO es el juego.** Es una app de UNA pantalla cuyo único propósito era contestar la
pregunta más cara del plan de migración: *¿puede iOS reutilizar el mapa Leaflet del juego tal cual,
dentro de un `WKWebView`, sin escribir un renderer de mapas nativo?*

**Respuesta, verificada en el simulador el 2026-07-27: SÍ.** El mapa carga, se ve idéntico al de
Android y responde a pinch-zoom y arrastre.

## Cómo compilarlo

El proyecto **NO** construye por sí solo el framework de Kotlin: hay que generarlo antes, desde la
raíz del proyecto Gradle (`PolitecnicoOpenWorld/`, la carpeta de arriba):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Eso deja el framework en `shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework`, que
es exactamente donde apunta `FRAMEWORK_SEARCH_PATHS` del proyecto. Después ya se abre `POW.xcodeproj`
y se ejecuta con ⌘R.

> Si Xcode dice **`No such module 'Shared'`**, es que falta ese paso de Gradle. No es un problema
> del proyecto Xcode.

## Cómo está montado

| | |
|---|---|
| `POW/POWApp.swift` | Punto de entrada SwiftUI. |
| `POW/ContentView.swift` | La pantalla **y** el `MapWebView`. Están juntos A PROPÓSITO: añadir un `.swift` nuevo obliga a editar a mano las listas de ficheros del `project.pbxproj`, que es donde estos proyectos se corrompen. Cuando se añadan ficheros desde Xcode, se puede separar. |

El HTML del mapa **no se duplica**: `ContentView` llama a `WorldMapLeafletHtmlKt.buildHtml(...)`, que
es la MISMA función Kotlin que usa `WorldMapScreenWeb.kt` en Android. Solo existe una copia del mapa.

El proyecto salió de la plantilla vacía de Xcode y se renombró, en lugar de escribir un
`project.pbxproj` a mano.

## Assets: `pow-asset://`

El HTML del mapa es COMPARTIDO con Android, donde las imágenes cuelgan de `file:///android_asset/`
— un esquema que en iOS **no existe**. En vez de bifurcar el HTML, el prefijo se parametrizó:
`buildHtml(assetBaseUrl:)`, con el valor de Android por defecto para que producción no cambie.
iOS pasa `pow-asset:///` y lo resuelve `PowAssetSchemeHandler` (en `ContentView.swift`).

⚠️ **Hoy ese handler devuelve 404 para todo, y es lo correcto:** los 358 MB de assets aún no se
empaquetan en el bundle iOS — eso es la Fase 6, con On-Demand Resources por el límite de 200 MB por
datos móviles. Lo que está resuelto es **el camino**, no el contenido. Como la app todavía no
inyecta datos en el mapa, **servir un asset de verdad no se ha probado de punta a punta.**

## Lo que falta (y no es poco)

1. **El framework no se construye solo.** Falta una fase de script que llame a Gradle desde Xcode.
   Hoy hay que acordarse de lanzarlo a mano (ver arriba). Los `FRAMEWORK_SEARCH_PATHS` **sí** están
   condicionados por SDK, así que simulador y dispositivo real cogen cada uno el suyo.
2. **No hay puente JS ↔ nativo.** En Android el diálogo lo hace `MapJsBridge`; aquí no hay nada, así
   que el mapa es de solo lectura: no recibe jugador, ni NPCs, ni landmarks.
3. **No hay juego.** Ni menús, ni combates, ni controles: eso es la Fase 5 (mover la UI a
   Compose Multiplatform) y la Fase 6.
