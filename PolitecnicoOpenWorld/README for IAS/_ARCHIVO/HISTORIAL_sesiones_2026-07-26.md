# Historial — sesión 2026-07-26 (Opus 5)

> Purgado de `_SESION_ACTUAL.md` el 2026-07-27 por la ventana de 2 días.
> Es el trabajo que salió en la **release 1.0.0.14**. Referencia, NO tareas.

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

