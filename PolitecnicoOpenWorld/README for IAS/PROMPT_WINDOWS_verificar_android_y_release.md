# 🤖 PROMPT Windows — verificar que Android sigue igual, y publicar

**Creado:** 2026-07-31 (Mac) · **Rama:** `perf-gama-baja-coleccionables` · **Todo va en ESTA rama.**

---

## Lo que tienes delante

Esta rama lleva **80 commits** por delante de `main`: es **la migración entera a Kotlin
Multiplatform**. El modo pelea corre ya en iOS, y el mundo abierto va por la mitad.

**Nadie ha jugado Android desde que empezó todo esto.** En el Mac **no hay AVD** (medido:
`emulator -list-avds` vacío), así que la verificación en Android **no se ha hecho**. Ese es tu
trabajo, y es lo único que separa esto de un release.

> ⚠️ **La regla que manda:** *no romper Android*. Es la versión que la gente juega hoy. Si algo
> falla, **para y repórtalo**; no lo parchees a lo rápido para que compile.

---

## 0. Arranque

```bash
git checkout perf-gama-baja-coleccionables
git pull
```

Lee, en este orden: `_SESION_ACTUAL.md` · `11_SEPARACION_IOS_ANDROID.md` ·
`12_PLAN_MUNDO_ABIERTO_iOS.md` · `13_ASSETS_Y_TAMANO.md`.

⚠️ La ruta del escritorio **lleva un espacio**: entrecomíllala.

### La red de seguridad, primero

```bash
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```

**Debe dar 274 tests** (119 en `:app` + 155 en `:shared`), **0 fallos**. En el Mac también corren
155 en iOS, pero eso ya está verificado.

```bash
bash tools/check_kmp_test_names.sh
```

---

## 1. 🔴 LO QUE MÁS RIESGO TIENE: los datos guardados

Estos cambios tocan **lo que el jugador ya tiene guardado**. Un fallo aquí no es un crash: es un
jugador que **pierde su progreso**, y eso no se recupera.

**Prueba con una instalación que YA tenga partida** (instala la versión de Play Store primero, juega
un poco, y luego actualiza con la de esta rama por encima). Instalar limpio **no prueba nada de esto**.

| Qué cambió | Qué comprobar |
|---|---|
| **Gson → kotlinx.serialization** | Partidas de Modo Historia guardadas **siguen cargando** |
| **SharedPreferences → multiplatform-settings** | Ajustes, idioma y Modo Desarrollador **siguen puestos** tras actualizar |
| **Room KMP** | Landmarks y caché de teselas **siguen ahí** (el mapa no vuelve a descargar todo) |
| **OkHttp → Ktor** | Multijugador conecta; las teselas del mapa bajan |
| **`SfArcadeRepository`** | Una **escalera arcade a medias** se retoma donde iba |
| **Coleccionables** | Los que ya tenías **siguen conseguidos** y con su arte |

⚠️ Lo que NO puede haber cambiado, y conviene confirmar en el propio archivo:
- BD en `filesDir/databases/pow_roads.db`
- Preferencias en `pow_game_settings`
- Claves `APP_LANGUAGE` y `DEVELOPER_MODE`

---

## 2. 🟠 Lo que se movió a `commonMain` y hay que ver funcionando

Es el mismo código para las dos plataformas ahora. En iOS está verificado; **en Android no**.

- **Modo pelea completo**: elegir peleador, ronda entera, audio, pausa, minimizar y volver.
  ⚠️ **El audio cambió de formato**: 82 archivos `.ogg` → `.m4a`. Escucha voces y golpes.
  ⚠️ **La música de fondo pasó de 24 a 16 bits.** No debería notarse (la salida de Android es de
  16 bits de todas formas), pero **escúchala**.
- **Ajustes**: las 6 categorías abren, Español ↔ English recrea bien, Modo Desarrollador desbloquea
  los 18 peleadores.
- **Coleccionables**: pestañas OBJETOS y PELEADORES, con su arte.
- **Menú principal**: en Android tienen que salir **los 6 botones de siempre**, con sus insignias
  PRE-ALPHA/BETA. ⚠️ Si aparece alguno marcado **EN OBRAS**, es un bug: eso es solo de iOS.

### Los 3 gestores de IA — 🔴 **lo más delicado de jugar**

`PoliceManager`, `CampaignEscortPolice` y `PrankedyManager` pasaron a `commonMain` y su
`ConcurrentHashMap` se cambió por `PowMapaConcurrente`. **Tiene 14 tests**, pero la concurrencia
real solo se ve jugando:

> **Juega el mundo abierto media hora seguida.** Sube el nivel de búsqueda hasta que salgan varias
> patrullas, déjalas perseguirte, súbete a una, bájate, aléjate. Busca **tirones, policías clavados
> o patrullas que desaparecen**. Si pasa algo raro, anota qué hacías: es justo el tipo de fallo que
> no sale en un test.

⚠️ Y hay un **arreglo de un crash latente** que conviene confirmar: se buscaba una patrulla con una
clave que podía ser `null`, y `ConcurrentHashMap.get(null)` lanza NPE. Ahora, si un policía no tiene
patrulla a la que volver, **se retira**. Comprueba que ningún policía se queda congelado.

### Interiores

Se quitó el parámetro `onClaimCollectiblePressed`, que estaba **declarado y sin usar**, de 5
llamadas. Comprueba que el botón de interactuar sigue funcionando en: **metro, metrobús, ShineCTO
y el minijuego zombi**.

---

## 3. 🟢 Lo que NO deberías notar

Casi todo lo de iOS es aditivo. Estos archivos **no existen para Android**: `MapaMundoIos`,
`HudMundoIos`, `PuenteMapaIos`, `IosMainMenuController`, `IdiomaIos`.

En `PowModos.kt` hay `MODOS_EN_OBRAS_VISIBLES = true`, pero **`modosEnObrasDe(ANDROID)` devuelve
vacío**: en Android no cambia nada, y hay un test que lo fija.

---

## 4. 📦 El release, cuando lo de arriba esté en verde

**No lo lances antes.** Si algo del §1 o del §2 falla, eso va primero.

1. **Sube el `versionName`**. Está en `app/build.gradle.kts` línea 26, hoy `"1.0.0.14"`.
   El `versionCode` lo calcula el CI (`1000 + GITHUB_RUN_NUMBER`); no lo toques.
2. **Notas de la versión en ES y EN** — el workflow **falla si faltan**.
3. **Revisa `PLAYSTORE_formulario_seguridad_datos.md`** antes de enviar.
4. Merge a `main` (o lanza el workflow desde esta rama, como prefieras) y deja que el CI construya
   el AAB firmado.

### El tamaño

**MEDIDO en el Mac: 402 MB.** El tope de Google Play para el módulo base son **500 MB**, y ahí está
puesta la barrera del CI. El workflow ahora **avisa a los 450** y publica el desglose por carpeta en
el resumen del run.

Quedan **~98 MB de margen**, que no es mucho. En `13_ASSETS_Y_TAMANO.md` está el plan para bajarlo a
~330 MB, y **necesita esta máquina** porque en el Mac no hay `ffmpeg` ni `cwebp`:

```bash
bash tools/optimizar_assets_produccion.sh --dry-run   # dice cuánto ahorraría
bash tools/optimizar_assets_produccion.sh             # lo aplica
```

| Tarea | Ahorro | Riesgo |
|---|---:|---|
| **142 PNG → WebP** | ~60 MB | Bajo. Ya se usa WebP en 1789 archivos. ⚠️ Hay que cambiar las rutas en Kotlin. |
| **3 BGM → Ogg** | ~23 MB | Medio. ⚠️ **Hay que ESCUCHAR el bucle** y cambiar 3 rutas en `SoundManager.kt`. |

⚠️ **Ninguna de las dos la comprueba un test.** Puedes dejarlo para después del release.

---

## 5. Lo que sigue en iOS (para que sepas dónde encaja tu trabajo)

Estado real en `12_PLAN_MUNDO_ABIERTO_iOS.md`. Resumen:

| Fase | Estado |
|---|---|
| 1 · Paridad de menús | ✅ |
| 2 · Dominio puro a `commonMain` | ✅ 22 archivos |
| 3 · Gestores de IA | 🟡 3 de 6 (falta `NpcAiManager` + 2 parciales) |
| **4 · `R.string` → `composeResources`** | 🔜 **bloquea la 5** |
| 5 · `WorldMapViewModel` | 🟡 solo el HUD |
| 6 · Mapa | 🟡 se ve, se camina, las bardas frenan |
| 7 · Interiores | 🔜 |

**El siguiente paso real es la fase 4**, y aquí es donde tú puedes ayudar de verdad: son
**42 strings de misión** a `composeResources` y **17 consumidores** de `CampaignObjective`. Es
mecánico y el compilador te guía, **pero el fallo típico es silencioso** —una misión enseña el texto
equivocado— y solo se ve jugando. Por eso va en la máquina que tiene emulador, no en el Mac.

El orden exacto, en 6 pasos, está en el doc 12 §Fase 5.

---

## Al terminar

Actualiza `_SESION_ACTUAL.md` (**techo 200 líneas**, purga a `_ARCHIVO/` lo que pase de 2 días) con:
qué probaste, qué falló y qué versión se publicó. `git pull` antes de cada push.
