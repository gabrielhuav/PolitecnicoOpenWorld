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

## Lo que falta (y no es poco)

1. **Solo simulador.** `FRAMEWORK_SEARCH_PATHS` apunta a `iosSimulatorArm64`. Para dispositivo real
   hace falta el de `iosArm64` y, lo suyo, una fase de script que llame a Gradle sola.
2. 🔴 **Los overlays con assets NO van a funcionar.** El HTML tiene 5 rutas
   `file:///android_asset/…` cableadas (landmarks, coleccionables, iconos de metro/metrobús y
   sprites de NPC). En iOS ese esquema no existe. Hace falta un `WKURLSchemeHandler` que sirva los
   assets desde el bundle, o reescribir esas rutas. **El mapa base se ve porque esta app no le
   inyecta datos todavía**; en cuanto se le pasen landmarks o NPCs, saldrán imágenes rotas.
3. **No hay puente JS ↔ nativo.** En Android el diálogo lo hace `MapJsBridge`; aquí no hay nada, así
   que el mapa es de solo lectura.
4. **No hay juego.** Ni menús, ni combates, ni controles: eso es la Fase 5 (Compose Multiplatform,
   que exige subir Kotlin) y la Fase 6.
