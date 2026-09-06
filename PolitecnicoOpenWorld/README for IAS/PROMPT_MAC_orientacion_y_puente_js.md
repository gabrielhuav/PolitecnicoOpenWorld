# 🍏 TRASPASO A LA MAC — orientación de iOS y la vuelta del puente JS

**Escrito el 2026-08-17 desde Windows (Opus 5).** Rama: `ios/verificacion-mac-1.0.0.17`.
Commits que hay que probar: `ae1f6aee` y `2497fb89`.

> **Lee esto entero antes de tocar Xcode.** Lo que viene son **dos costuras nuevas de iOS que
> NUNCA han corrido**: se escribieron y se compilaron desde Windows, donde no hay ni Xcode ni
> simulador. La mitad de Swift **no la ha visto un compilador todavía**.

---

## 0. Qué se hizo y por qué

### A · Los controles del mundo no eran iguales en iOS

El HUD del mundo se pintaba a **100 dp** en iOS y a **180 dp** (`ControllerBaseSize`) en Android y en
el modo pelea. La causa no era el tamaño: era que **iOS no forzaba horizontal** y dos controles de
180 dp no caben en los ~390 dp de un iPhone en vertical.

Se portó la regla de Android (`AppNavGraph.kt`: juego horizontal, menús libres) al mundo de iOS, y
el HUD volvió al tamaño de siempre. **La costura está partida en dos y hacen falta las dos:**

| Mitad | Archivo | Qué hace |
|---|---|---|
| **PEDIR** el giro | `shared/src/iosMain/…/platform/orientacion/OrientacionIos.kt` | `requestGeometryUpdate` + `setNeedsUpdateOfSupportedInterfaceOrientations()` |
| **DECLARAR** qué vale | `iosApp/POW/POWApp.swift` (`PowAppDelegate`) | `application(_:supportedInterfaceOrientationsFor:)` |
| Usarlo | `MapaMundoIos.kt` | `ForzarHorizontal()`, un `DisposableEffect` |

⚠️ **La mitad de Swift NO es pereza:** `supportedInterfaceOrientations` vive en una categoría de
ObjC y Kotlin/Native la expone como extensión → el compilador corta con `'…' overrides nothing`.
Está medido y documentado en `09 §KMP nº5` y `11 §4quater`. **No intentes rehacerlo en Kotlin.**

### B · El mapa ya puede hablar de vuelta (JS → Kotlin)

`PuenteMapaIos` solo iba Kotlin → JS. La vuelta es **`PuenteJsIos`** (`WKScriptMessageHandler`).
**El HTML compartido no se tocó**: sigue llamando a `window.Android.notifyX(...)`, y en iOS ese
objeto lo crea un `WKUserScript` (`PUENTE_JS_SHIM`) que reenvía a `webkit.messageHandlers`.

Hoy lo usa **el toque del mapa**, que coloca el marcador de destino igual que en Android.

---

## 1. Lo primero, y si esto falla para todo lo demás

```bash
cd /Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld/PolitecnicoOpenWorld
git pull
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # NO es opcional
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 :shared:iosSimulatorArm64Test
```

Luego abre Xcode y compila. **Lo más probable que falle es `POWApp.swift`**, que es Swift nuevo sin
compilar nunca. Errores que puede dar y qué significan:

- **`Cannot find 'OrientacionPow' in scope`** → el `object` de Kotlin no se exportó al framework.
  Comprueba que `import Shared` está y que el framework se regeneró (el `link…` de arriba).
- **`Value of type 'OrientacionPow' has no member 'esHorizontal'`** → el nombre cruzó distinto;
  mira el header generado (`Shared.h`) y ajusta el Swift, **no el Kotlin**.
- **Cualquier cosa con `UIInterfaceOrientationMask`** → el tipo es un option-set; a propósito el
  puente cruza un `Bool` y la traducción a `.landscape` / `.all` vive solo en Swift.

⚠️ **Repite `linkDebugFrameworkIosSimulatorArm64` cada vez que cambies Kotlin.** Xcode NO regenera
el framework solo y correrás el binario viejo sin enterarte.

---

## 2. Qué probar, en este orden

### 2.1 Orientación y controles (lo importante)

1. Abre la app → **menú principal**: debe poder estar en **vertical** (como hasta ahora).
2. MUNDO LIBRE (sale EN OBRAS y abre la vista previa del mapa) → **la pantalla debe girar sola a
   horizontal**.
3. **Gira el iPhone a vertical estando en el mapa: NO debe girar.** Si gira, el `PowAppDelegate` no
   se está consultando (mitad B) aunque la pantalla haya girado al entrar (mitad A).
4. Mira los controles: el joystick y el diamante A/B/X/Y tienen que verse **del mismo tamaño que en
   el modo pelea**. Compáralos abriendo TITULACIÓN POR COMBATE justo después.
5. **VOLVER al menú → debe poder girar a vertical otra vez.** Si se queda clavado en horizontal, el
   `onDispose` de `ForzarHorizontal` no corrió.
6. **Pulsa los botones donde SE VEN.** Si responden en otro sitio, alguien reintrodujo un
   `Modifier.scale` (la trampa de `11 §8bis nº5`).

### 2.2 La vuelta del puente

7. En el mapa, **toca cualquier punto**: debe aparecer el **marcador de destino** ahí.
8. Toca otro punto: debe moverse. (El JS apaga el modo tras cada toque y el Kotlin lo vuelve a
   encender; si solo funciona el primer toque, eso es lo que falló.)
9. Si no pasa nada, **el error es SILENCIOSO por diseño de WebKit**. Para verlo: Safari →
   Desarrollo → Simulador → inspecciona el `WKWebView` y mira la consola. Sospechosos, en orden:
   - el nombre del canal no coincide entre `PUENTE_JS_CANAL` y el shim (están en el mismo archivo
     justo para que no pase);
   - el `WKUserScript` no se inyectó al principio del documento;
   - `updateDestinationPlacingMode(true)` no llegó (el `DisposableEffect` de `MapaMundoIos`).

### 2.3 Que no se rompió lo de antes

10. El mundo sigue: mapa, caminar, bardas, **39 NPCs** moviéndose, niebla.
11. TITULACIÓN POR COMBATE: pelea completa, sin cambios.

---

## 3. Estado de lo demás (para que no te pille)

- **Android está verificado y verde**: 319 tests (125 `:app` + 194 `:shared`), detekt exit 0, y el
  mundo jugado ~35 min en el emulador el 08-16. No hace falta repetirlo en la Mac.
- 🔴 **2 ANR abiertos** en el renderer **OSM nativo de Android** tras ~13 min (stack de osmdroid +
  GC; **no** es la migración de concurrencia). Falta medirlo contra `main`. Es de Android, no toca
  esto, pero está en `_SESION_ACTUAL` §4 P0.
- ⚠️ **API ≤32 sin verificar** (no había imagen de sistema en la PC).
- ⏭️ **Lo siguiente del mundo en iOS**: la **caché de calles** (hoy pide a Overpass en cada arranque
  y se come 429) y la **fase 5** (`WorldMapViewModel` + sus 22 parciales + `WorldMapEnvironment`).
  Orden completo en `12_PLAN_MUNDO_ABIERTO_iOS.md`.

## 4. Si algo falla

Dilo y para; **no lo parchees para que compile**. Las dos costuras están documentadas con su causa
medida en `09 §KMP nº5`, `11 §4quater` y `12 §fase 6`. Y si tocas comportamiento, el doc va en el
MISMO commit (`09 §13`).
