# 🍏 14 — Instalar POW en un iPhone y en el simulador de iPhone

**Creado:** 2026-11-15 · **Medido en la Mac del dueño** con Xcode 27.2, Android Studio 2026.2.2 y el
plugin Kotlin Multiplatform. Todo lo que dice "MEDIDO" se corrió de verdad ese día.

> **Para quién es:** para una persona que nunca ha instalado una app de iOS, y también para una IA
> que trabaje en esta Mac desde la terminal. Cada paso tiene las dos versiones: **🧑 a mano** (Android
> Studio o Xcode) y **🤖 por terminal** (lo que hace Claude). Las dos acaban en lo mismo.
>
> **Qué NO cubre:** TestFlight y la App Store. Para eso hace falta la cuenta de pago de Apple
> (99 USD/año) y todavía no la hay. Ver `13_ASSETS_Y_TAMANO.md` para los límites de tienda.

---

## 0. Lo primero que hay que entender

### 0.1 Las tres formas de ver POW en iOS

| Dónde | Qué es | Para qué sirve | Qué NO te dice |
|---|---|---|---|
| **Simulador de iPhone** | Un iPhone "de mentira" en una ventana de la Mac | Probar rápido: pantallas, navegación, textos | Memoria real (no hay *jetsam*), el botón de silencio, el rendimiento real |
| **iPhone físico por cable** | Tu iPhone, conectado a la Mac | Probar de verdad: memoria, audio, tacto, velocidad | — |
| **TestFlight / App Store** | Instalar sin cable, en el teléfono de cualquiera | Repartirlo | ❌ **No disponible**: falta la cuenta de pago |

⚠️ **No hay forma de instalar en un iPhone que no esté conectado a ESTA Mac** (o a otra Mac con
Xcode). No es como Android, donde mandas un APK por WhatsApp. Sin cuenta de pago, el iPhone tiene
que pasar por el cable al menos una vez cada 7 días (ver §6).

### 0.2 ¿El simulador es de la Mac o de Claude?

**Es de Apple.** Viene dentro de Xcode: cualquier persona con Xcode lo tiene, sin instalar nada de
Claude. Se maneja con el comando `xcrun simctl` o, a mano, lanzando la app desde Android Studio o
Xcode. Hasta Xcode 26 tenía su propia app (**Simulator.app**); en Xcode 27 esa app ya no viene y
aparece **DeviceHub** en su lugar (ver §3.3).

La app de escritorio de Claude solo añade una **herramienta para manejarlo** ("iOS Simulator"):
muestra la pantalla del simulador en un panel junto al chat y le permite a Claude tocar, deslizar,
escribir y sacar capturas. Por debajo es el mismo simulador de Apple. **Si Claude no está, todo
esto se puede hacer a mano** (§3).

⚠️ **Es un SIMULADOR, no un EMULADOR.** No imita un procesador de iPhone: corre código de Mac
compilado "como si fuera iOS". Por eso:

- Kotlin se compila **dos veces**, una para cada cosa (`iosSimulatorArm64` y `iosArm64`, ver §2).
- Lo que va bien en el simulador **puede fallar en el iPhone**: memoria (el iPhone cierra la app si
  se pasa; el simulador no), el modo silencio (§7), y que los nombres de archivo distinguen
  mayúsculas en el iPhone y no en la Mac.

(El de Android sí es un emulador. Esa es la diferencia con el AVD `Pixel_9_API_36`.)

### 0.3 Las piezas

| Pieza | Dónde vive | Notas |
|---|---|---|
| Proyecto Xcode | `PolitecnicoOpenWorld/iosApp/POW.xcodeproj` | Bundle id `ovh.gabrielhuav.POW`, iOS 16.0 mínimo |
| Framework de Kotlin | `shared/build/bin/<destino>/debugFramework/Shared.framework` | ⚠️ **Xcode NO lo construye.** Lo hace Gradle (§2) |
| Equipo de firma | `FRNJ5FXYDY` = "Gabriel Hurtado Aviles (Personal Team)" | Gratis. **No está guardado en el proyecto** (§4.2) |
| Certificado | "Apple Development: …(Y6K8UTBN2R)" en el Llavero | Lo crea Xcode solo la primera vez |
| Esquema de Xcode | `iosApp.POW` (en esta Mac) | ⚠️ Cambia según la máquina, ver §8 |

---

## 1. Lo que hay que tener instalado (una sola vez)

| Cosa | Cómo se comprueba | Si falta |
|---|---|---|
| **Xcode** (MEDIDO: 27.2) | `xcodebuild -version` | App Store → Xcode |
| **Xcode apuntando a la app** | `xcode-select -p` → `/Applications/Xcode.app/Contents/Developer` | `sudo xcode-select -s /Applications/Xcode.app` (pide tu contraseña) |
| **Un simulador de iOS** (runtime) | `xcrun simctl list runtimes` (MEDIDO: 18.2, 26.5, 27.0) | Xcode → Settings → Components → iOS |
| **Tu Apple ID en Xcode** | Xcode → Settings → **Accounts** → aparece tu cuenta | Botón **+** → Apple ID. Con uno gratis basta |
| **Android Studio** + su JDK | `ls "/Applications/Android Studio.app/Contents/jbr/Contents/Home"` | El Gradle del proyecto usa ese JDK |
| **Plugins Kotlin Multiplatform + Kotlin Toolchain** (solo para 🧑 Android Studio) | Settings → Plugins → Installed: ambos habilitados. MEDIDO 2026-11-15: KMP `262.10968.183-AS` + Toolchain `262.10968.245` | Marketplace → instalar/actualizar ambos plugins de JetBrains → reiniciar. Si chocan, ver §7 |

**MEDIDO 2026-11-15:** tras actualizar KMP a `262.10968.183-AS` y reiniciar Android Studio,
ambos plugins cargaron habilitados y desapareció el conflicto de `intellij.amper.ios`.
KMP depende de Kotlin Toolchain: desactivar Toolchain también impide cargar KMP.

Desde Android Studio Rabbit 2 **2026.2.2**, JetBrains retiró la edición asistida, el resaltado
y la navegación de Swift del plugin. **Ejecutar apps iOS y depurar Kotlin sigue soportado**;
para trabajar en Swift usa Xcode. Ver el
[anuncio oficial](https://blog.jetbrains.com/kotlin/2026/10/discontinuing-swift-language-ide-support-in-the-kotlin-multiplatform-plugin/).

---

## 2. Paso común: compilar la parte de Kotlin (SIEMPRE antes de instalar)

⚠️⚠️ **Este es el paso que todo el mundo se salta.** El proyecto Xcode solo tiene Swift: el juego
entero está en Kotlin (`:shared`) y Xcode **no sabe compilarlo**. Si te lo saltas, Xcode dice
**`No such module 'Shared'`**. Si ya lo hiciste antes pero cambiaste Kotlin, peor: instala **la
versión vieja** sin avisar.

### 🤖 Por terminal

Desde la carpeta Gradle (`PolitecnicoOpenWorld/PolitecnicoOpenWorld`, la que tiene `gradlew`):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64
```

**Compila LOS DOS aunque solo vayas a usar el iPhone.** No es manía: la fase de Xcode que copia los
textos de la interfaz (`composeResources`) los busca **siempre en la carpeta del simulador**
(`assembledResources/iosSimulatorArm64Main`), también al compilar para iPhone. Si solo existe la
del iPhone, Xcode falla con `error: faltan los composeResources`.

MEDIDO 2026-11-15: **1 min 36 s los dos juntos**, `BUILD SUCCESSFUL`, 0 errores (salen avisos `w:`,
no pasa nada). La primera vez en una Mac sin caché, solo el del iPhone tardó **4 min 12 s**
(medido el 2026-08-14).

### 🧑 En Android Studio

Panel **Gradle** (el elefante, a la derecha) → botón **Execute Gradle Task** → escribe
`:shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64` → Enter.
O mejor: deja que lo haga solo cada vez que pulsas ▶ (§3.1 y §4.3).

---

## 3. 📱 El simulador de iPhone

### 3.1 🧑 Desde Android Studio (lo más cómodo)

1. Arriba, en el selector de configuraciones (al lado de ▶), elige **`iosApp.POW`**.
   Si no existe: Run → Edit Configurations → **+** → **iOS Application** → Xcode project file:
   `iosApp/POW.xcodeproj` → Scheme: el que salga (§8) → Configuration: **Debug**.
2. **Solo la primera vez — que compile Kotlin solo:** en esa misma ventana (Edit Configurations →
   `iosApp.POW`), abajo en **Before launch**, pulsa **+** → **Run Gradle task** → proyecto
   `PolitecnicoOpenWorld` → tarea `:shared:linkDebugFrameworkIosSimulatorArm64`. Repite con
   `:shared:linkDebugFrameworkIosArm64`. Arrástralas **por encima** de "Build".
   ⚠️ MEDIDO: las dos configuraciones `iosApp.POW` de esta Mac **no las tienen** (solo "Build").
   Sin ellas, Android Studio instala el Kotlin de la última vez que lo compilaste a mano.
3. En el selector de dispositivo (al lado de la configuración) elige un simulador, p. ej.
   **iPhone 17**.
4. Pulsa ▶. Se abre la ventana del simulador y arranca POW.

### 3.2 🧑 Desde Xcode

1. Haz el §2.
2. Abre `iosApp/POW.xcodeproj` (doble clic).
3. Arriba en el centro: esquema `iosApp.POW` → dispositivo **iPhone 17** (sección "iOS Simulators").
4. **⌘R** (Product → Run).

### 3.3 🧑 Solo abrir el simulador, sin compilar nada

⚠️ **Cambió con Xcode 27 (MEDIDO 2026-11-15 con Xcode 27.2):** ya **no existe** la app
`Simulator.app`, y `open -a Simulator` falla con *"Unable to find application named
'Simulator'"*. En su lugar Xcode trae **DeviceHub** (`Xcode.app/Contents/Applications/DeviceHub.app`,
id `com.apple.dt.Devices`). La primera vez que se abrió pidió **la contraseña de la Mac** para
"instalar software de Apple" (un componente de Xcode): eso lo acepta **la persona**, nunca una IA.

- Con Xcode 26 o anterior: Spotlight (⌘ espacio) → **Simulator** → File → Open Simulator → iPhone 17.
- Con Xcode 27: abre **DeviceHub** (Spotlight → DeviceHub). ⚠️ No está verificado todavía cómo
  se enciende un simulador desde ahí: el día de la medición se quedó en la ventana de contraseña.
  Lo seguro es lanzar POW desde Android Studio (§3.1) o Xcode (§3.2), que encienden el simulador
  y lo muestran solos.
- POW aparece en la pantalla de inicio del simulador si ya se instaló antes.

### 3.4 🤖 Por terminal (lo que hace Claude)

```bash
cd PolitecnicoOpenWorld/PolitecnicoOpenWorld
# 1. Kotlin (§2)
# 2. Encender un simulador
xcrun simctl boot "iPhone 17"            # "Unable to boot device in current state: Booted" = ya estaba encendido, no es error
#    (Para VER su pantalla, ver la nota de Xcode 27 en §3.3. Sin ventana también funciona todo.)
# 3. Compilar la app para el simulador (sin firma: el simulador no la pide)
xcodebuild -project iosApp/POW.xcodeproj -scheme iosApp.POW -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -derivedDataPath /tmp/pow-dd build
# 4. Instalar y abrir
xcrun simctl install booted /tmp/pow-dd/Build/Products/Debug-iphonesimulator/POW.app
xcrun simctl launch booted ovh.gabrielhuav.POW
# 5. Captura de pantalla (para comprobar que arrancó)
xcrun simctl io booted screenshot /tmp/pow-sim.png
```

MEDIDO 2026-11-15 (Xcode 27.2, iPhone 17 con iOS 27.0): `xcodebuild` **25 s** → `BUILD SUCCEEDED`;
`simctl launch` responde `ovh.gabrielhuav.POW: <número>` (el número es el proceso: arrancó) y sale
el menú principal.

- `-derivedDataPath` deja el `.app` en un sitio conocido. Una IA debe usar su carpeta temporal de
  sesión en vez de `/tmp`.
- Lista de simuladores disponibles: `xcrun simctl list devices available`.
- Apagarlo: `xcrun simctl shutdown booted`.
- Claude, en la app de escritorio, puede además abrir el panel en vivo (herramienta iOS Simulator →
  `attach`) para que el dueño vea lo mismo, y tocar/capturar con esa herramienta. MEDIDO: con Xcode
  27.2 ese panel muestra el simulador aunque no haya `Simulator.app`.

---

## 4. 📲 Un iPhone de verdad

### 4.1 Preparar el iPhone (una vez por teléfono)

Esto lo hace **la persona con el iPhone en la mano**; desde la Mac no se puede.

1. **Conéctalo por cable** a la Mac y **desbloquéalo**.
2. Si sale **"¿Confiar en esta computadora?"** → **Confiar** → código del iPhone.
3. **Empareja en Xcode:** Xcode → Window → **Devices and Simulators** → el iPhone aparece a la
   izquierda. Si pide algo, sigue lo que diga en pantalla. Este paso es el que hace que el iPhone
   salga luego en Android Studio.
4. **Modo de desarrollador:** en el iPhone, **Ajustes → Privacidad y seguridad → Modo de
   desarrollador** → activar → se **reinicia** → al volver, confirmar **Activar**.
   ⚠️ Esa opción **no aparece** hasta que el iPhone se ha conectado a Xcode una vez (paso 3).
   ⚠️ **No se puede activar desde la Mac** (no hay comando para eso).

### 4.2 Firma: el equipo NO está guardado en el proyecto, y es a propósito

`POW.xcodeproj` tiene `CODE_SIGN_STYLE = Automatic` **sin** `DEVELOPMENT_TEAM`. Así el repo no lleva
dentro la cuenta de nadie y cada quien firma con la suya. El equipo se pone **al compilar**:

- 🧑 en Android Studio, en la configuración (`Development team`, §4.3);
- 🤖 por terminal, como parámetro de `xcodebuild` (§4.5);
- 🧑 en Xcode, en Signing & Capabilities — ⚠️ **eso SÍ modifica `project.pbxproj`**. Si lo haces,
  **no lo subas a git** (descarta ese cambio en GitHub Desktop).

### 4.3 🧑 Desde Android Studio

1. Haz el §4.1 con el iPhone conectado.
2. Run → Edit Configurations → **`iosApp.POW`** → pestaña **Options** → **Development team**: elige
   "Gabriel Hurtado Aviles (Personal Team)". (MEDIDO: en esta Mac ya está puesto, `FRNJ5FXYDY`.)
3. **Before launch** → igual que en §3.1 paso 2 (las dos tareas de Gradle). Una vez y sirve para las dos cosas.
4. En el selector de dispositivo elige **el iPhone** (sale con su nombre, p. ej. "iPhone de Gabriel").
   Si no sale: está bloqueado, desconectado o sin emparejar (§4.1).
5. ▶. La primera vez en cada iPhone **instala pero no abre**: ve al §4.6.

### 4.4 🧑 Desde Xcode

1. §2 y §4.1.
2. Abre `iosApp/POW.xcodeproj` → selecciona el proyecto **POW** a la izquierda → target **POW** →
   **Signing & Capabilities** → Team: tu "(Personal Team)". (Ver el ⚠️ del §4.2.)
3. Arriba: esquema `iosApp.POW` → dispositivo: tu iPhone (sección "iOS Device").
4. **⌘R**.

### 4.5 🤖 Por terminal (lo que hace Claude)

```bash
cd PolitecnicoOpenWorld/PolitecnicoOpenWorld
# 1. Kotlin (§2), los dos destinos
# 2. ¿Qué iPhones ve la Mac?
xcrun devicectl list devices
#    Columna State: "available (paired)" = listo · "unavailable" = bloqueado o sin cable.
#    Con Xcode 27 la lista incluye también los simuladores (columna Reality = "simulated"), y la
#    columna Identifier del iPhone es su UDID ("00008120-… (UDID)"): ese es el <UDID> de abajo.
# 3. Compilar y firmar para ESE iPhone
xcodebuild -project iosApp/POW.xcodeproj -scheme iosApp.POW -configuration Debug \
  -destination 'platform=iOS,id=<UDID>' \
  -derivedDataPath /tmp/pow-dd \
  DEVELOPMENT_TEAM=FRNJ5FXYDY -allowProvisioningUpdates build
# 4. Instalar y abrir
xcrun devicectl device install app --device <UDID> /tmp/pow-dd/Build/Products/Debug-iphoneos/POW.app
xcrun devicectl device process launch --device <UDID> ovh.gabrielhuav.POW
```

- `DEVELOPMENT_TEAM=…` en la línea de comandos firma **sin tocar el proyecto** (§4.2).
- `-allowProvisioningUpdates` deja que Xcode registre el iPhone nuevo en la cuenta y genere el
  perfil. Sin él, un iPhone que nunca se usó falla al firmar.
- MEDIDO 2026-11-15: con `-destination 'generic/platform=iOS'` (sin iPhone conectado) compila y
  firma en **21 s** con `TeamIdentifier=FRNJ5FXYDY`. El perfil generado caduca **a los 7 días**.
  La instalación en un iPhone real se midió el 2026-10-06 (iPhone 15 y iPhone de Karla).

**iPhones conocidos (por si una IA necesita el UDID):**

| Nombre | Modelo | iOS | UDID |
|---|---|---|---|
| iPhone de Gabriel | iPhone 15 | 26.7.1 (oct.) | `00008120-00060C3C0E50A01E` |
| iPhone de Karla | iPhone 17 Pro | 27.0 (oct.) | `00008150-000C04E111F0401C` |
| iPhone (2) | iPhone 16 Pro | 26.6.2 (oct.) | sin anotar: el 2026-10-06 no tenía modo desarrollador |

La versión de iOS es la del 2026-10-06; puede haber cambiado. El UDID no cambia.

### 4.6 La primera vez en cada iPhone: "Desarrollador no confiable"

Es **normal** y pasa una vez por teléfono. iOS instala la app pero **no la deja abrir** hasta que
la persona confía en tu cuenta:

- 🧑 Al tocar POW sale **"Desarrollador no confiable"**. Ese aviso **solo tiene "Cancelar"**: no
  sirve para confiar. Hay que ir a:
  1. El iPhone **con internet** (Wi-Fi o datos): iOS pregunta a Apple antes de dejarte confiar.
  2. **Ajustes → General → VPN y gestión de dispositivos**.
  3. En **App de desarrollador**, toca la entrada de tu Apple ID.
  4. **Confiar en "Apple Development: …"** → **Confiar** otra vez en la ventana que sale.
  5. POW aparece como **Verificada**. Ya abre.
- 🤖 Por terminal se nota así: `devicectl … process launch` falla con
  `FBSOpenApplicationErrorDomain error 3` y *"its profile has not been explicitly trusted by the
  user"*. **Claude no puede resolverlo**: tiene que pedírselo a la persona y reintentar después.

---

## 5. Antes de dar algo por probado en iOS

1. ¿Compilaste Kotlin **después** del último cambio? (§2) Si no, estás probando lo viejo.
2. ¿Lo viste en un **iPhone**, no solo en el simulador? Memoria, audio y mayúsculas solo fallan allí.
3. Lo que dura menos de ~25 s en pantalla (avisos, animaciones) una IA **no llega a fotografiarlo**
   (es lo que tarda su ida y vuelta con el simulador). No lo des por roto sin más: súbele la
   duración en una compilación de prueba, o revisa el asset dentro del `.app` construido.
4. Las seis trampas que solo salen abriendo el simulador están en `11_SEPARACION_IOS_ANDROID.md` §8bis.

---

## 6. Límites de la cuenta gratuita

| Límite | Qué pasa | Qué hacer |
|---|---|---|
| **7 días** | La app deja de abrir en el iPhone (el perfil caduca) | Volver a instalar con el cable (§4.3 o §4.5) |
| **Pocas apps a la vez** por iPhone con la misma cuenta gratuita | Error al firmar al instalar una más | Borrar del iPhone otra app de prueba tuya |
| **Sin TestFlight ni App Store** | No se puede repartir a otros sin cable | Cuenta de pago (99 USD/año) |

---

## 7. Problemas conocidos y qué significan

| Lo que ves | Qué es | Solución |
|---|---|---|
| `No such module 'Shared'` | Falta el framework de Kotlin | §2 |
| `error: faltan los composeResources` | Solo compilaste el framework del iPhone | §2: compila **los dos** |
| `does not contain a scheme named "POW"` | El esquema tiene otro nombre en esta Mac | `xcodebuild -list -project iosApp/POW.xcodeproj` y usa el que salga (§8) |
| iPhone en **"unavailable"** | Bloqueado o sin cable | Desbloquearlo y dejar la pantalla encendida; pasa solo a "available (paired)" |
| iPhone no sale en Android Studio | Sin emparejar en Xcode o sin modo desarrollador | §4.1 |
| Conflicto de plugins: ambos declaran `intellij.amper.ios` | KMP `262.10968.143-AS` y Toolchain `262.10968.245` empaquetan el mismo módulo | Actualizar KMP a `262.10968.183-AS` y conservar Toolchain `.245`; reiniciar y comprobar ambos habilitados (§1). Pareja verificada en esta Mac el 2026-11-15 |
| Falla al firmar: no hay equipo | Falta `DEVELOPMENT_TEAM` | §4.2 |
| "Desarrollador no confiable" / `error 3` | Primera vez en ese iPhone | §4.6 |
| **El combate no suena en el iPhone** (en el simulador sí) | El iPhone está en **modo silencio**, y POW lo respeta **a propósito** (sesión de audio `Ambient`, ver `PowAudio.ios.kt`) | Quitar el silencio: botón de Acción o Centro de control. Subir volumen |
| La app abre y se cierra al llegar a una pantalla con texto | Faltan los `composeResources` en el bundle | §2 y ver `../iosApp/README.md` |
| La app dejó de abrir tras unos días | Caducó la firma gratuita | §6 |

---

## 8. Por qué el esquema se llama `iosApp.POW` (y en otra Mac puede que no)

El repo **no** trae ningún esquema compartido. Sin esquema, Xcode inventa uno con el nombre del
target: **`POW`**. Pero en esta Mac el plugin de Kotlin Multiplatform de Android Studio creó dos
esquemas propios, **`iosApp.POW`** e **`iosApp.POW (1)`**, y desde entonces el automático `POW` ya
no existe. Esos esquemas viven en `POW.xcodeproj/xcuserdata/`, que **está en `.gitignore`**: son
de esta Mac y de este usuario.

**Regla para una IA:** no asumas el nombre. Corre `xcodebuild -list -project iosApp/POW.xcodeproj`
y usa el primero de la lista "Schemes". El `(1)` sobra (se creó dos veces) y se puede borrar desde
Android Studio → Edit Configurations.

---

## 9. Desde Windows

**No se puede** instalar en iPhone ni abrir el simulador: las dos cosas exigen Xcode, y Xcode solo
existe en Mac. Desde Windows sí se puede comprobar que Kotlin compila para iOS
(`compileKotlinIosSimulatorArm64`, ver `SETUP_PC_NUEVA.md`), pero no verlo funcionar.

## 10. QA y capturas en iPhone real (medido 2026-11-15, PR #188)

La app se instaló y abrió en iPhone 15 con iOS 26.7.1. El dueño resolvió el error 3
confiando en el desarrollador desde el teléfono (§4.6). Ver el
[informe con alcance y pendientes](SF/QA_IPHONE_PR188_2026-11-15.md).

- En un worktree limpio, `xcodebuild -list` mostró **`POW`**; en la carpeta original
  con `xcuserdata` local aparece **`iosApp.POW`**. Comprueba siempre el esquema de ese checkout.
- Duplicación del iPhone permite ver y tocar POW con el teléfono bloqueado. Si pide
  desbloqueo, autorización o credenciales, el dueño completa el paso en su dispositivo.
- Con Duplicación activa y la pantalla física apagada, `devicectl` produjo capturas
  completamente negras. Comprueba los píxeles y el contenido antes de guardar evidencia.
  Una captura de la ventana de Duplicación debe identificarse como tal.
- El comando `screen-record` existe en la ayuda, pero este iPhone/iOS rechazó la
  capacidad de grabación. No repetirlo sin un cambio de dispositivo o estado.
- Un runner XCUITest externo cuenta como otra app firmada. La cuenta gratuita alcanzó
  el límite de **tres apps** antes de ejecutarlo. No borres otras apps: pide al dueño
  que libere un cupo si quiere continuar, avisando que desinstalar puede borrar datos locales.
- La confirmación de audio general no verifica cada clip del Charro. Comprobar por
  separado daño 1, especial y victoria; el daño elige entre tres variantes aleatorias.
  Mantener la sesión Ambient: respeta intencionalmente el modo silencio.
