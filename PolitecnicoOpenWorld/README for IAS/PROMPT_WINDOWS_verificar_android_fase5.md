# 🤖 TRASPASO A WINDOWS — verificar Android tras la fase 5 (y los sprites del mundo)

**Escrito el 2026-08-20 desde el Mac (Opus 5).** Rama: `ios/verificacion-mac-1.0.0.17`.
Commits a probar: **`bf2d6797` (QA Changes 1/9)** y **`f2f62064` (QA Changes 2/9)**.

> ✅ **YA SE HIZO (2026-08-21, Windows, emulador):** el resultado está en
> **`RESULTADO_WINDOWS_verificar_android_fase5.md`**. Salió VERDE; quedaron sin jugar el carjack
> (§2.3-8) y el Modo Historia (§2.7-18), y la mano zombi (§2.1-3) resultó **inalcanzable** porque
> la función se eliminó del juego. Lee ese documento antes de repetir esto.

> ## ⚠️ Lee esto primero
>
> En el Mac se verificó **iOS**, y ahí todo lo de abajo está probado en el simulador. Lo que **NO**
> se ha podido probar es **Android jugando**: este Mac **no tiene AVD** (medido). Compila, pasa los
> 338 tests y detekt sale 0 — pero **compilar no es verificar**, y los fallos de esta tanda son del
> tipo que ningún test caza: un texto que sale en el idioma que no era, un aviso que no aparece, un
> coche de otro color.
>
> **Tu trabajo aquí es jugar el mundo abierto en Android media hora.** Es el paso 6 de la fase 5 en
> `12_PLAN_MUNDO_ABIERTO_iOS.md`, y es el que manda.

---

## 0. Qué se tocó de Android, y por qué debería seguir igual

Nada de esto pretende cambiar cómo se ve o se juega Android. Todo son movimientos para que iOS
pueda usar el mismo código. **Si notas cualquier diferencia respecto a como jugabas antes, es un
fallo mío, no una mejora.**

| Qué se movió | De dónde | A dónde | Riesgo real |
|---|---|---|---|
| Repintado de coches | `VehicleSpriteManager` | `TintadoVehiculo.kt` (`commonMain`) | Coches de otro color |
| Repintado de ropa/pelo | `CharacterSpriteManager` | `TintadoPersonaje.kt` (`commonMain`) | NPCs mal vestidos |
| Caché de calles | `:app/data/cache` | `commonMain` | TTL roto → re-descargas |
| 14 textos del mundo | `R.string` | `composeResources` | Texto en otro idioma o vacío |
| `context.assets` (4 sitios) | `Context` | `PowAssets` | **Bardas o navgraph que no cargan** |
| Medida de gama del aparato | `WorldMapViewModel` | `AndroidWorldMapEnvironment` | Más o menos NPCs |

---

## 1. Lo primero

```bash
cd PolitecnicoOpenWorld
git pull
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```

Deberías ver **125 tests en `:app`** y **213 en `:shared`** (total 338) con **0 fallos**.

⚠️ Dos correcciones medidas en Windows el 2026-08-21:
- La tarea de `:shared` **NO** se llama `testDebugUnitTest` (eso falla a los 28 s con
  `task 'testDebugUnitTest' not found in project ':shared'`): en el módulo KMP es
  **`testAndroidHostTest`**, o `allTests` si quieres los tres targets.
- Los 213 de `:shared` **sí corren en Windows** — son del target Android/JVM, no de iOS.
- El conteo se lee de los XML (`*/build/test-results/**/*.xml`), no del log.

Luego **instala en el emulador o en un teléfono y juega**. El orden de abajo va de lo más probable
a lo menos.

---

## 2. Qué probar, en este orden

### 2.1 Los 14 textos del mundo (LO MÁS PROBABLE que esté mal)

Pasaron de `R.string` a `composeResources`, y ahora se resuelven de forma **asíncrona** — entran un
frame después. **Pruébalo en ESPAÑOL y luego cambia el idioma a inglés en Ajustes y repite.**

1. Acércate a un **coleccionable** → debe decir *"PRESIONA X PARA RECOGER"*.
2. Acércate a una **puerta de ESCOM** → *"PRESIONA X PARA ENTRAR"*.
3. Acércate a la **mano zombi** → *"…ACTIVAR MODO ZOMBI"*, y con el modo puesto, *"…DESACTIVAR…"*.
4. Acércate a una **estación de Metro** → *"PRESIONA X PARA ENTRAR A ESTACIÓN <NOMBRE>"*.
   ⚠️ Fíjate en que **el nombre de la estación aparezca**: ese texto lleva un `%1$s` y es donde
   más fácil se pierde el argumento.
5. Lo mismo con **Metrobús**.

**Si un aviso sale vacío o en el idioma que no era**, el fallo está en
`shared/src/commonMain/composeResources/values*/strings.xml` o en el punto de llamada.
**Si no sale ninguno**, mira si `PowAssets` está instalado (§2.5).

### 2.2 🐛 El fallo que se ARREGLÓ — confirma que quedó bien

Este ya estaba roto **antes** de esta tanda, en Android, y salió al portar:

> El aviso *"🧟 ¡UNA HORDA SE ACERCA!"* se auto-limpiaba comparando el texto en pantalla contra el
> literal **en español**. Jugando en inglés esa comparación nunca daba `true`, así que **el aviso
> se quedaba pegado para siempre**.

6. **Pon el juego en inglés**, provoca una horda y espera ~4 s: el aviso **tiene que desaparecer
   solo**. Si se queda, el arreglo no sirvió.

### 2.3 Prankedy y el carjack (los dos casos delicados)

7. **Mata a Prankedy.** Debe salir *"🎭 ¡Prankedy cayó!…"* **y el diálogo de contratarlo tiene que
   cerrarse EN EL ACTO.** Si el diálogo tarda o parpadea, la bandera se está difiriendo junto al
   texto, que es justo lo que no debe pasar (ver `WorldMapAvisos.kt`).
8. **Métete en un coche y quédate casi parado con la policía encima** hasta que te bajen. Debe
   verse *"¡Te van a bajar del auto! ¡Acelera!"*. Ese texto se **precarga** al entrar al mundo; si
   sale **vacío**, la precarga no terminó a tiempo (`textoCarjack` en `WorldMapViewModel.init`).

### 2.4 Los sprites (que Android NO debería notar)

9. **Coches**: mira 5-6 coches distintos. Colores variados, y **las luces traseras rojas y los
   intermitentes ámbar SIN repintar**, rines y faros igual. Si todos salen del mismo color, o si
   las luces se tiñeron, el algoritmo compartido cambió algo.
10. **Peatones**: ropa de colores distintos, **la piel y la cara sin teñir**, el pelo puesto y de su
    color. Que **caminen** (la animación).
11. **Patrullas**: siguen sin repintarse (su asset ya viene coloreado). Si una patrulla sale de
    color, se está tintando algo que no debía.

### 2.5 Que los assets siguen cargando (riesgo silencioso)

Cuatro lecturas pasaron de `context.assets` a `PowAssets`. Si `PowAssets` no estuviera instalado,
**falla con un mensaje explícito** (no en silencio), pero comprueba el efecto:

12. **Las bardas del campus frenan** al caminar. Si las atraviesas, `exterior_collisions.json` no
    se está leyendo.
13. **Modo Diseñador**: los landmarks por defecto aparecen (`default_landmarks.json`).
14. **Modo Desarrollador → inyectar coche de prueba en ESCOM**: debe funcionar o decir su error por
    `Toast` (`escom_navgraph.json`).

### 2.6 La caché de calles

15. Entra al mundo, sal y vuelve a entrar. La segunda vez **no debería re-descargar Overpass**.
    En iOS se midió: `MISS → Descarga OK: 2512 ways → GUARDADO OK` y luego `HIT: 2512 ways`.
    En Android busca lo mismo en el log con la etiqueta `RoadNetworkCache`.

### 2.7 Que no se rompió lo de antes

16. Población de NPCs parecida a la de siempre (la medida de gama del aparato se mudó de sitio).
17. HUELUM VS. GOYA: pelea completa.
18. Modo Historia y las misiones, por encima.

---

## 3. Si algo falla

Dilo y para; **no lo parchees para que compile**. Y anota **en qué idioma** estabas: la mitad de
los riesgos de esta tanda solo se ven en inglés.

---

## 4. Estado del resto, para que no te pille

- ✅ **iOS está verificado en el simulador** (iPhone 17 Pro, iOS 26.5): mundo con coches pintados,
  peatones armados, caché de calles con HIT/MISS medido, orientación, puente JS en las dos
  direcciones, y la pelea a 60 FPS.
- 🟡 **La fase 5 está a medias y a propósito.** Hecho: los 14 textos, `WorldMapEnvironment`,
  `PowAssets`, y sacar `computeDeviceTierFactor` del ViewModel. **Falta**: `viewModelScope` (18) →
  `PowViewModel.scope`, `Log` (7) → `powLog`, `AndroidViewModel` → `PowViewModel`, Hilt → patrón
  Controller, `TileCache` y `WebSocketManager`, el SAF del Modo Diseñador, los repositorios de
  Metro/Metrobús (`R.raw` + `org.json`) y **mover los 43 archivos**. El detalle está en
  `12_PLAN_MUNDO_ABIERTO_iOS.md` §Fase 5.
- 🔴 **2 ANR abiertos** en el renderer **OSM nativo de Android** tras ~13 min. Es de antes y no lo
  toca esta tanda, pero sigue sin medirse contra `main`.
- ⚠️ **API ≤32 sin verificar.**

---

## 5. Cuando termines

Si sale verde, lo siguiente de la fase 5 son los pasos mecánicos de §4 — pero **el que manda es
haber jugado esto**. Si sale rojo, con decir qué viste y en qué idioma basta.
