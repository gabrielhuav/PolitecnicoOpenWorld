# 🍏 PROMPT MAC — Prueba final de iOS y subida de Titulación por Combate a la App Store

> **Para:** Claude Code en la Mac del dueño (o el dueño, a mano).
> **Escrito:** 2026-11-21, desde Windows, en el PR #189. Windows NO compila iOS: nada de lo que este PR
> cambió en `iosApp/` se ha compilado todavía. **Lee todo antes de empezar.**
> **Al terminar:** deja `RESULTADO_MAC_appstore_final.md` y borra este archivo (los traspasos ejecutados
> se retiran; ver `00_INDEX.md`).

---

## 0. Contexto (lo mínimo)

- **iOS se publica solo con Titulación por Combate** (+ Coleccionables y Ajustes). Mundo abierto, Modo
  Historia, zombis y todo el multijugador son **exclusivos de Android**; su código no se borra. Regla del
  dueño: `09_CONVENTIONS_GOTCHAS.md` § "Qué puede enseñar iOS". Lo decide `PowModos.kt`
  (`MODOS_EN_OBRAS_VISIBLES = false`, que `PowModosTest` exige, y `sfMultijugadorDisponible()`).
- En iOS ya no se pintan las insignias PRE-ALPHA/BETA ni la versión del menú (decisión del dueño,
  2026-07-28; `MainMenuController.versionName`/`mostrarInsignias`).
- **Última prueba en iPhone: 2026-11-16** (PR #188, iPhone 15, iOS 26.7.1;
  `SF/QA_IPHONE_PR188_2026-11-15.md`). Desde entonces cambió código compartido que corre en iOS:
  - #188: interruptor de la App Store apagado y MULTIJUGADOR del modo pelea oculto en iOS (2026-11-21),
    voces nuevas del Charro Negro;
  - #183: voces de La Tzitzimime;
  - #189: 19 archivos de `shared/src/commonMain` (colisiones de coches, techos del campus, etc.). Son del
    mundo abierto, pero **compilan en iOS**: cualquier API de JVM ahí rompe Kotlin/Native.
- **Lo que el PR #189 ya dejó en `iosApp/` (sin compilar):**
  - `POW/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png`: ícono de 1024×1024 sin transparencia. Es
    el logo del launcher de Android (`mipmap-xxxhdpi/ic_launcher_foreground.png`) sobre su fondo negro.
    Antes el set no tenía imagen y la App Store lo rechaza.
  - `POW/PrivacyInfo.xcprivacy`: manifiesto de privacidad con `UserDefaults` (razón `CA92.1`), sin rastreo
    ni datos recolectados. La carpeta `POW/` está sincronizada con el proyecto (Xcode 16,
    `PBXFileSystemSynchronizedRootGroup`), así que entra sola al bundle.
  - `Info.plist`: `ITSAppUsesNonExemptEncryption = NO` (solo HTTPS del sistema, exento).
- **Proyecto:** `iosApp/POW.xcodeproj`, bundle id **`ovh.gabrielhuav.POW`**, versión **1.0 (1)**, iOS 16.0
  mínimo, nombre en la pantalla de inicio "POW", `TARGETED_DEVICE_FAMILY = "1,2"` (iPhone **y iPad**).
- **Cuenta Apple Developer Program (99 USD/año): según el doc 14 todavía no existe.** Sin ella se puede
  probar en el iPhone con la cuenta gratuita, pero **no hay TestFlight ni App Store**.

## 1. Reglas (no negociables)

1. **No unas PRs ni hagas push a `main`.** Trabaja en la rama `ios/appstore-final` y abre un PR al terminar.
2. **No escribas contraseñas, Apple ID ni códigos.** El dueño inicia sesión en Xcode y en App Store
   Connect, elige el equipo de firma y pulsa los botones de enviar. Tú preparas, guías y verificas.
3. **No toques las políticas de Play** (`account-deletion.html`, `PLAYSTORE_formulario_seguridad_datos.md`
   ni la rama `gh-pages`). Si la política no cubre iOS, díselo al dueño; no la edites.
4. No cambies la versión de Android (`app/build.gradle.kts`, `distribution/whatsnew/`).
5. Lo que el doc 14 marca como MEDIDO se respeta; si algo falla, **para y reporta la salida exacta**.
6. Antes de cambiar algo de la sección 6 (decisiones), **pregúntale al dueño**.

## 2. Preparar

```bash
cd <carpeta del repo>
git fetch origin && git checkout main && git pull
git log --oneline -5                     # debe incluir el merge del PR #189
test -f PolitecnicoOpenWorld/iosApp/POW/PrivacyInfo.xcprivacy && echo "PR 189 presente"
git switch -c ios/appstore-final
```

Si el PR #189 aún no está unido: `git fetch origin pull/189/head:pr-189 && git switch pr-189` y
dilo en el resultado (pruebas la rama del PR, no `main`). Herramientas: doc 14 §1 (Xcode, runtime del
simulador, JDK 21).

## 3. Kotlin: frameworks y tests de iOS

```bash
cd PolitecnicoOpenWorld
./gradlew :shared:iosSimulatorArm64Test :shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64
bash tools/check_kmp_test_names.sh
```

- **Esperado:** `BUILD SUCCESSFUL` y **358 tests de `:shared` en iOS, 0 fallos**: los mismos que la JVM
  (348 medidos el 2026-11-16, más 2 de `PowModosTest` y 8 de `VehicleCollisionQaTest`). Si salen menos,
  compara las clases contra `shared/build/test-results/` y reporta cuáles faltan.
- **Si Kotlin/Native no compila**, casi siempre es una API de JVM en `commonMain`: `java.*`,
  `String.format`, `System.currentTimeMillis()`, `synchronized`. Ver `09` § KMP. Arréglalo en la rama,
  vuelve a correr los tests de Android (`./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest`)
  y documenta la causa.

## 4. La app: simulador e iPhone

Comandos exactos: doc 14 **§3.4** (simulador) y **§4.5** (iPhone). **No asumas el esquema:**
`xcodebuild -list -project iosApp/POW.xcodeproj`. Si el combate no suena, revisa el modo silencio (doc 14).

Recorrido (captura cada punto en `pr-screenshots/appstore-final-2026-11/`):

1. **Ícono:** en la pantalla de inicio sale el logo POW, no un ícono en blanco.
2. **Menú principal:** solo TITULACIÓN POR COMBATE, COLECCIONABLES y AJUSTES. Sin EN OBRAS, PRE-ALPHA,
   BETA ni versión, y ningún botón que no haga nada (Guideline 2.1).
3. **Titulación por Combate:** entra en horizontal; el menú NO muestra MULTIJUGADOR. Juega una pelea
   completa de Arcade que termine con un proyectil (regresión de #188) y abre Práctica, Combos y
   Tutorial, Examen Extraordinario, IA vs IA y Récords. Escucha las voces del Charro Negro y de La
   Tzitzimime.
4. **Coleccionables:** abre la galería, haz zoom y vuelve.
5. **Ajustes:** cambia el idioma ES↔EN y comprueba los textos. Mira el Modo Desarrollador (sección 6a).
6. Pausa, vuelve al menú, **cierra y reabre la app**: los ajustes y récords persisten (UserDefaults).
7. **Modo avión:** la app funciona entera sin red.
8. **Permisos:** no pide ubicación, cámara, micrófono, fotos ni rastreo. No hay inicio de sesión ni
   cuentas (por eso no aplica el borrado de cuenta de la Guideline 5.1.1(v)).
9. **iPad:** si el proyecto sigue con iPad (sección 6b), repite 2–7 en un simulador de iPad.

## 5. El bundle que irá a la tienda

Compila **Release** (para el simulador basta sin firma; ver doc 14 §3.4 cambiando `-configuration Release`)
y comprueba sobre el `POW.app` resultante:

```bash
ls POW.app/PrivacyInfo.xcprivacy                                  # el manifiesto entró al bundle
plutil -p POW.app/Info.plist | grep -E "ITSAppUsesNonExemptEncryption|CFBundleShortVersionString|CFBundleVersion"
xcrun assetutil --info POW.app/Assets.car | grep -i -m3 appicon     # el ícono se compiló
otool -L POW.app/POW | grep -i -E "firebase|analytics|crash" || echo "sin SDKs de analítica"
```

Con la cuenta de pago ya en Xcode: **Product → Archive**, y en el Organizer **Validate App** y
**Generate Privacy Report**. Anota cualquier aviso. Si Apple pide otra categoría de API de razón
requerida (aviso ITMS-91053), agrégala a `PrivacyInfo.xcprivacy` con su razón y repite.

## 6. Decisiones del dueño (pregunta antes de cambiar)

a. **Modo Desarrollador en la build de la App Store.** Hoy el interruptor está visible para cualquiera
   en Ajustes: desbloquea todos los peleadores y escenarios (también los extras de desarrollo, como
   Planetario IPN y CET 1) y muestra herramientas de QA (hitboxes, FPS). **Recomendación:** esconderlo en
   Release de iOS y dejarlo en Debug para las pruebas; por ejemplo, una regla en `PowModos.kt` con un
   `expect/actual` que en iOS use `kotlin.native.Platform.isDebugBinary`, y que
   `IosStreetFighterEnvironment.developerMode` también lo respete. Con test en `PowModosTest`.
b. **iPad.** Con `TARGETED_DEVICE_FAMILY = "1,2"`, App Store Connect pide capturas de iPad y el revisor
   puede probar en iPad. Toda la QA hasta hoy fue en iPhone. **Recomendación para la 1.0:** solo iPhone
   (`TARGETED_DEVICE_FAMILY = 1` en Debug y Release), salvo que el punto 9 de la sección 4 salga bien.
c. **Personajes que representen a personas reales.** Si algún peleador retrata a una persona real (por
   ejemplo **La Presidenta**, jefa del Arcade, o Rey Grupero), App Review puede objetarlo (Guideline
   1.1.1; la sátira política suele estar exenta, pero lo decide Apple). Opciones del dueño: dejarlos y
   explicarlo en las notas para el revisor, o esconderlos en iOS.
d. **Ficha:** nombre en la tienda, subtítulo, categoría (Juegos), precio, clasificación por edad (el
   cuestionario de violencia: es violencia de caricatura/fantasía) y si el nombre bajo el ícono sigue
   siendo "POW" (`CFBundleDisplayName`).
e. **Ícono:** el que dejó el PR #189 es el logo del launcher de Android. Si el dueño quiere otro, se
   cambia `AppIcon-1024.png` (1024×1024, sin transparencia).

## 7. App Store Connect (lo hace el dueño; tú lo guías)

1. Inscribirse en el **Apple Developer Program** (99 USD/año).
2. En App Store Connect, crear la app con el bundle id **`ovh.gabrielhuav.POW`**.
3. En Xcode, **Signing & Capabilities → Team** = la cuenta de pago. El equipo NO se guarda en el
   proyecto, a propósito (doc 14 §4.2); no lo commitees.
4. Versión **1.0 (1)**. Cada subida necesita un `CURRENT_PROJECT_VERSION` nuevo (2, 3…).
5. **Archive → Validate → Distribute (App Store Connect)** → **TestFlight** interno → instalar desde
   TestFlight en el iPhone y repetir el recorrido corto (puntos 1–3 de la sección 4).
6. Ficha:
   - Descripción ES/EN y palabras clave.
   - URL de soporte.
   - **URL de política de privacidad:** reusar
     `https://gabrielhuav.github.io/PolitecnicoOpenWorld/policy_en_es.html`. Revisa que cubra iOS; si no,
     avisa al dueño (regla 3).
   - **Capturas:** App Store Connect exige capturas de iPhone de pantalla grande (6.9″/6.5″). Las del
     iPhone 15 (1179×2556) no valen como tamaño principal, así que tómalas en un simulador de ese tamaño.
     Si queda iPad, también capturas de iPad.
7. **Privacidad de la app:** "No se recopilan datos" (coincide con el manifiesto). Cumplimiento de
   exportación: ya lo responde la clave del `Info.plist`.
8. Enviar a revisión **solo cuando el dueño lo diga**.

## 8. Qué entregar

- `README for IAS/RESULTADO_MAC_appstore_final.md` con:
  - los comandos y sus salidas clave;
  - el número de tests;
  - qué pasó en cada punto de la sección 4, con su captura;
  - lo que decidió el dueño en la sección 6;
  - lo que falta para la App Store.
- Capturas en `pr-screenshots/appstore-final-2026-11/`.
- PR desde `ios/appstore-final` con los cambios: decisiones aplicadas, manifiesto ampliado y docs (`00`,
  `09`, `11`, `iosApp/README.md`, `_SESION_ACTUAL.md`). Borra este prompt en ese mismo PR.
- Un mensaje corto al dueño: **¿lista para subir? sí/no**, y por qué.
