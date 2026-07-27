# 🧠 MEMORIA DE SESIÓN — estado vivo del trabajo

> **CORTO A PROPÓSITO (objetivo: < 200 líneas).** Es lo primero que lee una IA nueva, así que
> cada KB de más se paga en TODAS las sesiones. El detalle histórico va a `_ARCHIVO/`, y el de
> diseño a `SF/DISENO_ARCADE_SF_POW.md`. **Si crece, se poda.**
>
> **Regla de oro:** si te quedas sin tokens a media tarea, actualiza ESTE archivo ANTES de parar.

**Última actualización:** 2026-07-26 · Opus 5 · rama `fix-multiplayer`

> ➡️ **AHORA:** preparando el **release a Play** con el multijugador arreglado. El siguiente
> cambio grande será de **arquitectura** (tocará mucho código), así que conviene publicar antes.

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
`SfWebRtcClient` **decora** al relay: camino caliente (`PLAYER_STATE`/`PLAYER_DAMAGE`/
`PLAYER_READY`) DIRECTO; plano de control (salas, selección, revancha, `ROUND_ENDED`/
`MATCH_ENDED`) por el relay, porque el server los AGREGA o los DIFUNDE. **Sin TURN**: si el
hole punching falla (~20-30%) cae solo al relay → **gratis de por vida**.
- ⚠️ `MultiplayerSF/server.js` requiere REDEPLOY (4 casos `SIGNAL_*`), pero **la app es SEGURA
  de subir ANTES**: con el server viejo los `SIGNAL_*` se ignoran y todo sigue por el relay.
- **Peso medido:** AAB **368.83 → 390.07 MiB** (+21.2). Límite 500 → margen ~110 MiB.
  ⚠️ NO poner `abiFilters`: quitaría x86_64, que es el del emulador (AVD "Nexus").

### C · Auditoría pre-producción — 4 defectos REALES corregidos
1. **Pérdida de daño:** el DataChannel estaba NO fiable; `PLAYER_DAMAGE` es evento ÚNICO →
   canal **fiable y ordenado**.
2. **⚡ Tirón en gama baja:** `PeerConnectionFactory.initialize()` (11 MB nativos) corría en
   **Main** → movido a hilo de trabajo, con buffer de señalización.
3. **Carrera en PARTIDA RÁPIDA:** el server manda `OPPONENT_JOINED` al host ANTES que
   `ROOM_JOINED` al invitado → nuevo `SIGNAL_READY`.
4. **`@Volatile`** en `channel`/`peer`/`factory` (se escriben en hilos de WebRTC).

### D · CI: puerta de cumplimiento de Play (job `play-compliance`)
Bloquea la subida si se repite un rechazo de 2026-07-22: comprueba que **existe `gh-pages`**,
que las **2 URLs de políticas dan 200** (no 404), que traen el **correo correcto** y no el
equivocado, y que están las **notas de versión** ES+EN. `playstore-closed-testing` depende de él.

## 4. PENDIENTE — por prioridad

### 🔴 P0 · Antes/durante el release
1. **Probar el multijugador en dispositivo:** que los 2 jugadores **oigan** lo mismo (tag
   `SF-NET`). BT y LAN son lo que hay que validar sí o sí.
2. **Redeploy de `MultiplayerSF/` en Render** para activar el P2P (no bloquea el release).
   Con 2 teléfonos en redes distintas, buscar en logcat `SF-RTC`: `DataChannel → OPEN`.

### 🟠 P1 · AUDIO (trabajo activo)
Ver `SF/PROMPT_traspaso_audio_subtitulos.md`:
- **29 clips demasiado largos** para su evento → **Gemini 3.6** con segundos exactos del dueño.
- **5 clips fuera de −16 ±2 LUFS** → **Gemini 3.6**.
- **Faltan `attack`/`hurt`** en 6 peleadores → el dueño graba.

⚠️ **No repitas** el resumen que dice "100 % normalizados y sin faltantes": está medido y es
**falso**. De los 80 `.ogg` solo **69 son voz**; 2 no pueden normalizarse sin comprimir.

### 🟡 P2 · Separación SF ↔ mundo abierto (medido 2026-07-26)
**Casi limpia.** SF solo importa de fuera: `R`, `BuildConfig`, la capa de datos compartida
(`SfArcadeRepository`, `SettingsRepository`, `AuthManager`) y su propio `domain.models.
streetfighter`. **La única fuga real:** 6 imports de 3 widgets de UI que viven en el mundo
abierto — `PowButton` (×4), `JoystickController`, `ActionButton`, todos en
`features/map_exterior/ui/components/`. En sentido contrario solo `CollectiblesScreen` mira a SF.
→ **Arreglo: mover esos 3 composables a un paquete de UI neutral.** Hacerlo CON el refactor de
arquitectura, no antes de un release (es mecánico y lo verifica el compilador).

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
