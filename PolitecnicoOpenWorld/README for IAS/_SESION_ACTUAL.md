# 🧠 MEMORIA COMPARTIDA ENTRE IAs — estado vivo del trabajo

> ## Qué es este archivo
>
> El proyecto lo trabajan **varias IAs distintas** (Opus, Fable, Sol, Gemini…) que **no
> comparten memoria entre sí ni entre sesiones**. Cada una empieza de cero. Este archivo es el
> **único punto de traspaso**: lo que esté aquí es lo que sabrá la siguiente; lo que no, se
> pierde y se reinventa (o se alucina).
>
> Por eso vale más que un changelog: es **memoria operativa**. No cuenta la historia del
> proyecto — cuenta **en qué estado quedó todo y qué sigue**.
>
> ## Reglas de mantenimiento (obligatorias)
>
> 1. **Ventana de 2 DÍAS como máximo.** Solo vive aquí el trabajo de la sesión actual y, como
>    mucho, el de la anterior si sigue siendo relevante. **Todo lo que pase de 2 días se PURGA**
>    a `_ARCHIVO/HISTORIAL_sesiones_<AAAA-MM-DD>.md`, con un enlace desde aquí si hace falta.
> 2. **Techo de 200 líneas.** Es lo primero que se lee en CADA sesión: cada KB de más se paga en
>    tokens siempre. Si crece, se poda — no se justifica, se poda.
> 3. **Qué NO va aquí:** detalle de diseño (→ `SF/DISENO_ARCADE_SF_POW.md` o el doc del área),
>    historia de cómo se llegó a algo, ni nada que el código o `git log` ya digan.
> 4. **Qué SÍ va aquí:** estado real medido, lo que está a medias, lo que está BLOQUEADO y en
>    quién, las trampas que costaron caro, y los datos que contradicen a otros docs.
> 5. **Marca lo MEDIDO vs lo SUPUESTO.** Varios docs de este repo afirmaban cosas falsas
>    ("0 smells", "47 tests", "AAB 434 MiB"). Si lo verificaste, dilo; si no, dilo también.
>
> ## Cómo cerrar una sesión (haz esto ANTES de quedarte sin tokens)
>
> 1. Purga a `_ARCHIVO/` lo que ya pasó de la ventana de 2 días.
> 2. Reescribe la sección de sesión con lo tuyo: qué cambió, qué se verificó y **qué falta**.
> 3. Actualiza **PENDIENTE por prioridad**: lo que quede a medias tiene que estar ahí o se pierde.
> 4. Comprueba que sigues bajo las 200 líneas.
>
> **Regla de oro:** si te quedas sin tokens a media tarea, actualiza ESTE archivo ANTES de parar.

**Última actualización:** 2026-07-27 · Opus 5 · rama `fase0-auditoria-kmp`
**Ventana viva:** 2026-07-26 → 2026-07-27 · *purgar a `_ARCHIVO/` a partir del 2026-07-28*

> ➡️ **AHORA:** 1.0.0.14 en revisión en Play. **Fases 0 y 1 de KMP/iOS HECHAS y en verde**
> (`PLAN_MIGRACION_KMP.md`). Existe el módulo **`:shared`**. **Siguiente: Fase 1.5 EN EL MAC.**

## 🖥️ Rutas por PC

| PC | Raíz del PROYECTO (aquí están `gradlew.bat` y `tools/`) |
|---|---|
| **Laptop** (referencia) | `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| **Escritorio** | *distinta — COMPLETAR con la real* |

Solo cambia el prefijo absoluto: todas las rutas de los docs son **relativas a la raíz**.
El GEN de sprites vive FUERA del repo en `..\newSFAssets\GEN_*`.

## 1. Organización

```
README for IAS/
  _SESION_ACTUAL.md      <- ESTE archivo. Empieza aquí SIEMPRE.
  00_INDEX.md            índice / mapa de archivos
  01_ARCHITECTURE.md     arquitectura compartida
  02_DATA_LAYER.md       Room, DAOs, repos, red
  07_OTHER_FEATURES.md   menú, ajustes, coleccionables
  09_CONVENTIONS_GOTCHAS.md   ⚠️ OBLIGATORIO antes de tocar código
  PLAYSTORE_formulario_seguridad_datos.md  🛡️ ANTES de tocar la ficha o subir versión
  MUNDO/                 🌎 mundo libre POW
  SF/                    🥊 "Huelum vs. Goya" (empieza por SF/00_SF_INDEX.md)
  _ARCHIVO/              histórico YA EJECUTADO. Referencia, NO tareas.
```

## 2. A quién delegar

| Dificultad | IA | Cuándo |
|---|---|---|
| **Alta** | **Sol 5.6** · **Fable 5** | Refactors grandes, varios módulos, sistemas nuevos, algo que ya falló dos veces, slicer/packer. |
| **Media** | **Opus 4.8 / 5** | Features acotadas, auditorías, `tools/`, bug localizado, un solo módulo. |
| **Baja** | **Gemini 3.6** | Regenerar assets con pipeline existente, recortes de audio con instrucciones exactas, aplicar CSV, tareas repetitivas. |

**Antes de delegar:** rutas absolutas, comando exacto, cómo se verifica, y qué NO tocar.

## 3. Sesión 2026-07-26 (Opus 5) — audio en red + P2P + puerta de Play

**Compilado, 131 tests verdes, detekt sin issues nuevos, `bundleRelease` OK.**

### A · Audio del rival sincronizado (BT + LAN + online)
**Causa:** `applyRemoteSnapshot` asigna `sim.p1.state` DIRECTO, saltándose `changeState()`, que
es donde vive TODO el audio → el rival peleaba **mudo**. **Trampa:** los packs eligen con
`.random()`, así que disparar el audio localmente habría sonado un clip DISTINTO en cada lado.
- **VIAJAN** (campo `audio` nuevo y opcional en `SfNetMsg`): voces de ataque/dolor/poder/
  victoria/derrota/intro/metamorfosis + chasquido del **parry**.
- **SE DERIVAN** del `state` (`emitRemoteStateSfx`): whoosh, aterrizaje, mareo.
- **NO se tocan** los impactos `*-hit`: ya sonaban en ambos lados.
- ✅ **El relay NO necesita redeploy por esto:** hace `{...msg}` (server.js:314).

### B · P2P por WebRTC (Render = "GameRanger")
`SfWebRtcClient` **decora** al relay: camino caliente (`PLAYER_STATE`/`PLAYER_DAMAGE`/`PLAYER_READY`)
DIRECTO; el plano de control (salas, selección, revancha, `ROUND_ENDED`/`MATCH_ENDED`) va por el
relay, que los AGREGA o DIFUNDE. **Sin TURN**: si falla el hole punching (~20-30%) cae al relay.
- ⚠️ `MultiplayerSF/server.js` requiere REDEPLOY (4 casos `SIGNAL_*`), pero **la app es SEGURA
  de subir ANTES**: con el server viejo los `SIGNAL_*` se ignoran y todo sigue por el relay.
- **Peso medido:** AAB **368.83 → 390.07 MiB** (+21.2). Límite 500 → margen ~110 MiB.
  ⚠️ NO poner `abiFilters`: quitaría x86_64, que es el del emulador (AVD "Nexus").

### C · Auditoría pre-producción — 4 defectos REALES corregidos
1. **Pérdida de daño:** DataChannel NO fiable + `PLAYER_DAMAGE` evento ÚNICO → **fiable y ordenado**.
2. **⚡ Tirón en gama baja:** `PeerConnectionFactory.initialize()` (11 MB nativos) corría en
   **Main** → a hilo de trabajo, con buffer de señalización.
3. **Carrera en PARTIDA RÁPIDA:** el server manda `OPPONENT_JOINED` al host ANTES que
   `ROOM_JOINED` al invitado → nuevo `SIGNAL_READY`.
4. **`@Volatile`** en `channel`/`peer`/`factory` (se escriben en hilos de WebRTC).

### D · CI: puerta de cumplimiento de Play (job `play-compliance`)
Bloquea la subida si se repite el rechazo de 2026-07-22: existe `gh-pages`, las **2 URLs de
políticas dan 200** con el **correo correcto**, y están las **notas de versión** ES+EN.

### E · Gatillos L1/L2/R1/R2 opcionales (mundo + LOS 5 INTERIORES)
Ajustes → Interfaz, **OFF por defecto** (en el mundo aún no tienen acción; `onPress` vacío a
propósito). `ui/components/NeonButton.kt`. Apagada, el HUD queda idéntico.
- ⚠️ **TRAMPA que costó una iteración:** el `LaunchedEffect` de `AppNavGraph` que refresca el
  mundo tenía como claves SOLO `controlType`/`controlsScale`/`swapControls`, y el interruptor vive
  en **Interfaz** → nunca se relanzaba. Si añades otro ajuste que el mundo lea en vivo,
  **agrégalo a esas claves**. (Los interiores lo leen al crear su VM; ahí basta con eso.)

## 3bis. Sesión 2026-07-27 (Opus 5) — KMP/iOS: Fase 0 + Fase 1

**Datos, opciones y fases: `PLAN_MIGRACION_KMP.md`.** Aquí solo lo que no puede perderse:
- **osmdroid NO bloquea 51 archivos, sino 6** (los otros 45 solo usan `GeoPoint`). Y **el mapa por
  defecto YA es Leaflet en WebView**, no osmdroid → iOS hereda mapa sin escribir renderer.
- **🔴 Gson en 20 archivos (5 = protocolo de red de SF)**: reflexión JVM, NO existe en iOS. Faltaba
  en todas las tablas previas. Es la fase que puede romper saves/red de usuarios REALES.
- **Hilt→Koin NO es obligatorio** (`:shared` no necesita DI; Android sigue con Hilt).

**DECISIONES del dueño:** alcance = **juego ENTERO** (yo recomendaba SF primero; consta en el plan).
Mapa = **Opción A**. **Tiene Mac**, no cuenta Developer. Fase 1 **sin subir Kotlin**.

**FASE 1 HECHA y VERDE.** Módulo **`:shared`** (Kotlin 2.2.10 y AGP intactos), `androidTarget` + 3
targets iOS. Con `git mv`: dominio puro de SF (7 archivos, 1235 líneas) → `commonMain`; 6 tests →
`commonTest` (JUnit4 → `kotlin.test`). **Se conservó el paquete** → cero imports que tocar en `:app`.
- **MEDIDO: `:app` 87 + `:shared` 44 = 131 tests, 0 fallos.** `assembleDebug` OK. detekt exit 0.
- ⚠️ **`SfArcadeCampaignAuditTest` NO se movió** (usa `java.io.File`): se queda en `:app`.
- ⚠️ **CI actualizado** (`pr-quality-gate.yml`): corre `:shared:testDebugUnitTest` **y** el input
  nuevo de detekt. Sin eso CI habría corrido solo 87 tests **sin avisar**.
- 🆕 **GOTCHA (ya en 09 §12): el smart cast NO cruza módulos** → `x.campo?.let { usa(it) } == true`.
- ❌ **De iOS NADA verificado:** en Windows Gradle desactiva los targets iOS (aviso esperado).

## 4. PENDIENTE — por prioridad

### 🔴 P0 · Antes/durante el release
1. **Probar el multijugador en dispositivo:** que los 2 jugadores **oigan** lo mismo (tag
   `SF-NET`). BT y LAN son lo que hay que validar sí o sí.
2. **Redeploy de `MultiplayerSF/` en Render** para activar el P2P (no bloquea el release).
   Con 2 teléfonos en redes distintas, buscar en logcat `SF-RTC`: `DataChannel → OPEN`.
3. **🍏 FASE 1.5 EN EL MAC** (siguiente paso de KMP): compilar `:shared` para iOS de verdad y
   probar Compose MP + el mapa Leaflet en `WKWebView`. **Es lo que convierte en hechos las 2
   suposiciones más caras del plan.** Sin esto, no meter más código en `:shared`.

### 🟠 P1 · AUDIO (trabajo activo)
Ver `SF/PROMPT_traspaso_audio_subtitulos.md`:
- **29 clips demasiado largos** para su evento → **Gemini 3.6** con segundos exactos del dueño.
- **5 clips fuera de −16 ±2 LUFS** → **Gemini 3.6**.
- **Faltan `attack`/`hurt`** en 6 peleadores → el dueño graba.

⚠️ **No repitas** el resumen que dice "100 % normalizados y sin faltantes": está medido y es
**falso**. De los 80 `.ogg` solo **69 son voz**; 2 no pueden normalizarse sin comprimir.

### ✅ P2 · Separación SF ↔ mundo abierto — **HECHA** (verificado 2026-07-27)
`features/streetfighter` ya **no importa ninguna otra feature** (0 imports; la única mención a
`map_exterior` es un comentario-lápida). Los widgets viven en el paquete neutral `ui/components/`.

### 🟢 P2 · Animaciones congeladas (el arte se repite, no es bug de código)
`stun-1==stun-2==stun-3` en los 18; `bonus-7/8/9/10` estáticos en `lapresidenta`;
`run-4==run-5` en 4; `forwards-3==forwards-4` en 3; `throw-2==throw-3` en 3;
`super-4==super-5` en `charronegro` y `senortienda`.

### 🔵 P2b · Motor compartido — Fase 1 hecha y auditada; Fase 2a+2b hechas; sigue 2c
Siguiente: núcleo de `updateStageConstraints` (empuje de pushboxes) → `SfPhysics`; luego el
esqueleto `SfEngine` y modos como estrategia. **Exige sesión CON compilador.** Plan completo:
`SF/PLAN_refactor_motor_compartido.md`. Historial: `_ARCHIVO/HISTORIAL_sesiones_2026-07-22.md`.

### ⚪ P3 · Bloqueado en el dueño / deuda conocida
- **Mapas UAM Azcapotzalco y Cuajimalpa:** faltan vídeos nuevos → `tools/build_map_backgrounds.py`.
- **Arte V2 de La Presidenta + metamorfosis nuevas:** croma en `tools\_para_corregir\` sin importar.
- **detekt NO está a 0:** 5 smells preexistentes (`CachingWebViewClient`, `NpcAiManager`,
  `RoadRouter`, `CatSpriteManager` ×2). Varios docs dicen "0 smells" y es **falso**.
- `07_OTHER_FEATURES.md` (87 KB) mezcla menú/ajustes con SF; su parte de SF debería migrar a `SF/`.
- **Paparazzi 5** tiene un audio que es de Paparazzi 1; **Señor de la tienda** tiene un tramo con
  voz de Prankedy; **La Tzitzimime** mal recortada. Los 3 requieren al dueño.

## 5. Verificación antes de cerrar CUALQUIER sesión

```bash
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

```bash
..\detekt-cli-1.23.8\bin\detekt-cli.bat --config "config\detekt\detekt.yml" --input "app\src\main\java"
```

⚠️ **NO** uses `--build-upon-default-config` en detekt: sube el conteo a 16 con reglas que el
repo no adoptó. El baseline correcto son **5 smells preexistentes**.
⚠️ **`testDebugUnitTest` son 131 tests** (docs viejos decían 47: dato stale).

`git status` debe mostrar **solo** lo que tocaste. Y **actualiza este archivo** antes de terminar.
