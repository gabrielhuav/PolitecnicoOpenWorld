# PENDIENTES · HUELUM VS. GOYA — sesión 2026-07-16 (post-pull "Refactor SF 1..9")

> **Para la PRÓXIMA sesión de IA.** Contexto: leer 07 §HUELUM VS. GOYA + AUDIT_SF_MULTIPLAYER
> (banner "✅ ESTADO ACTUAL") ANTES de tocar nada. Convenciones: 09 (Read para verificar,
> CRLF, strings ES+EN, MVVM). Estado de cada punto al final de este doc.

## ① IA con DIFICULTAD (básica/normal/avanzada)

**Estado: ✅ IMPLEMENTADA (2026-07-16c) — verificada en el working tree tras el pull
"Refactor SF 1..9" (2026-07-16d).** El código VIVE en el árbol de trabajo SIN COMMITEAR
(`git status`: M en `SfModels.kt`, `StreetFighterState.kt`, `StreetFighterViewModel.kt`,
`StreetFighterScreen.kt`, `values/strings.xml`, `values-en/strings.xml`). **NO hay stash**
(la nota anterior era incorrecta). Verificado: enum `SfCpuDifficulty`, campo `cpuDifficulty`
(default NORMAL), `buildCpuInput` reparte a `basicCpuDecision`/`normalCpuDecision`/
`advancedCpuDecision` + `cpuAttack`, `selectCharacter`/`startBattle`/`restartBattle` pasan la
dificultad, `resetRound` la conserva (`s.copy`), `DifficultySelectOverlay` (flujo 4 pasos),
strings ES+EN con paridad, llaves balanceadas en los 4 .kt. Diseño en 07 §HUELUM VS. GOYA.
Falta SOLO: **Rebuild + prueba en dispositivo** (lista al final) y **commitear** los cambios.

## ② BUG · STUN-LOCK online/BT: machacar un botón mata sin defensa posible

**Reporte del dueño:** en BT o Multijugador, si el rival presiona la misma tecla
repetidamente te TRABA: cada golpe te pone en HURT, el siguiente llega antes de que
salgas y así hasta morir, sin poder hacer nada.

**Causa raíz (analizada):** (a) los ataques LIGEROS se re-disparan desde el frame 2
(quirk del JS portado a propósito) → cadencia altísima; (b) los estados HURT_* están en
`SF_HURT_STATES` (golpeable mientras te recuperas); (c) online el daño lo aplica el
RECEPTOR (`processNetDamage`) sin ventana de gracia entre golpes; (d) el pushback
(`slideVelocity`) no separa lo suficiente con luces repetidas.

**Diseño propuesto (offline Y online, simétrico — no toca el protocolo):** en el VM,
rastrear la cadena de golpes recibidos por peleador: `hitChainCount`/`lastHitReceivedMs`
(se resetea si el peleador salió de HURT_* o pasaron > ~1.5 s). Si un golpe conecta con
`hitChainCount >= 3`: (1) conceder INVULNERABILIDAD breve al levantarse
(`damageImmuneUntilMs ≈ 600 ms` — `applyAttackHit` y `processNetDamage` ignoran daño
mientras corre; el chip por bloqueo NO se ve afectado), y (2) pushback EXTRA al 4º golpe
(que el atacante quede fuera de rango de luces). Cada lado lo aplica a SU peleador (misma
autoridad del receptor que ya existe) → sin mensajes nuevos ni desync. Probar también
vs la IA AVANZADA (que castiga rápido) para calibrar los 600 ms.

**Archivos:** `StreetFighterViewModel.kt` (`applyAttackHit`, `processNetDamage`,
campos nuevos junto a `hurtFreezeUntilMs`; resetear en `resetInternals` y `resetRound`).

## ③ BUG · REVANCHA online: sincronización "más o menos"

**Reporte:** al terminar la partida, la revancha no sincroniza bien (a veces queda raro).

**Análisis (hipótesis, verificar con logs en 2 dispositivos):** la revancha es bilateral
(`REMATCH_ACCEPTED`) pero cada lado arranca con SU reloj al recibirla: no hay countdown
compartido como en el arranque normal (MAP_SELECTED → countdown 3-2-1 del server →
FIGHT_START). Posibles síntomas: marcador de rondas no reseteado igual, snapshots de la
pelea anterior en vuelo (la gracia `roundGraceUntilMs` cubre rondas, ¿cubre revancha?),
o un lado en RONDA 1 mientras el otro sigue en el menú de fin.

**Diseño propuesto:** rehacer la revancha COMO el arranque: al aceptarse bilateral, el
HOST (online: el server; BT/LAN: el peer host en `SfStreamPeer`) re-emite la secuencia
`FIGHT_START` (o un `REMATCH_START` con countdown) en vez de que cada lado haga reset
por su cuenta; ambos lados hacen `resetInternals()` + marcador a 0 + gracia activa AL
RECIBIRLO. Verificar que `matchOver`, `playerRoundWins/cpuRoundWins`, `roundNumber` y
`dispHp0/1` queden idénticos en ambos lados. Cubrir online (server relay: revisar case
REMATCH en `MultiplayerSF/server.js` — ⚠️ requiere redeploy si se toca) y BT/LAN
(`SfStreamPeer` genera localmente los mensajes del relay).

**Archivos:** `StreetFighterViewModel.kt` (manejo REMATCH_ACCEPTED), `SfStreamPeer.kt`,
`MultiplayerSF/server.js` (validar con `node --check`).

## ④ MEJORA · SERVIDOR LOCAL (Wi-Fi): descubrir la sala SOLO + pedir CÓDIGO

**Pedido del dueño:** en LAN no teclear la IP: que la sala local disponible aparezca
SOLITA en el menú; lo que SÍ debe pedir es el código.

**Diseño propuesto (autodescubrimiento por UDP broadcast, cero permisos nuevos):**
- **Host** (`CREAR SERVIDOR`): además del `ServerSocket` TCP (`SF_LAN_PORT=47645`),
  emite un BEACON UDP por broadcast cada ~1 s a un puerto fijo nuevo
  (`SF_LAN_DISCOVERY_PORT=47646`): payload JSON `{magic:"POW_SF_LAN", ip, name}`.
  Genera un **código de 4 letras** (como las salas online) que MUESTRA en pantalla
  (sustituye a "comparte esta IP"); el beacon NO lleva el código.
- **Invitado**: al abrir la sección LAN, escucha el broadcast ~5 s y pinta las salas
  halladas como TARJETAS (patrón `RoomCard` de la lista pública). Tocar una pide el
  **CÓDIGO** (campo de 4 letras) y conecta por TCP a la IP del beacon; el handshake
  HELLO→WELCOME (ya existe en `SfStreamPeer`) gana validación del código: HELLO lleva
  el código tecleado y el host responde WELCOME solo si coincide (si no → rechazo con
  mensaje, overlay REINTENTAR existente).
- **Fallback manual:** conservar el campo de IP (colapsado, "Unirse por IP…") — el
  aislamiento AP de redes públicas bloquea broadcast (mismo hint `sf_lan_error_hint`).
- **Play Store:** UDP local con INTERNET ya declarado → cero permisos nuevos, sin
  foreground service (el beacon vive con la Activity, `close()` al salir).

**Archivos:** `SfLanClient.kt` (+ beacon/discovery; quizá clase hermana `SfLanDiscovery`),
`SfStreamPeer.kt` (código en HELLO/WELCOME — ⚠️ BT usa la misma base: pasar código null/"BT"
para no romper BT), `StreetFighterViewModel.kt` (estado `lanRoomsFound`, intents),
`StreetFighterScreen.kt` (sección LAN del `OnlineMenuOverlay`), strings ES+EN nuevos.

## Qué probar en dispositivo (todo lo de hoy)

1. **Dificultad:** peleador → rival → "ELIGE LA DIFICULTAD" → mapa. BÁSICA = CPU pasiva
   sin poderes; NORMAL = la clásica; AVANZADA = bloquea/castiga/anti-aéreo/muchos poderes,
   casi imposible. Revancha conserva la dificultad. "← Cambiar peleador" regresa al paso 1.
   Online/BT/LAN sin paso de dificultad.
2. **(tras ②)** 2 dispositivos: machacar puño ligero arrimado al rival — la víctima debe
   poder escapar tras ~3 golpes (invulnerabilidad breve + empujón).
3. **(tras ③)** revancha online y BT: ambos lados arrancan a la vez, marcador 0-0, RONDA 1.
4. **(tras ④)** LAN: el invitado VE la sala sin teclear IP; el código equivocado rechaza;
   red con AP isolation → fallback por IP.

## Protocolo al terminar cada punto

Docs 07 (§HUELUM VS. GOYA) + AUDIT_SF_MULTIPLAYER (banner estado) + 08 si se toca el
server + README público raíz (EN **y** ES, Cambios Recientes) + borrar el punto de este
doc (y el doc entero cuando quede vacío). Servers: `node --check` + anotar si requiere
redeploy en Render.
