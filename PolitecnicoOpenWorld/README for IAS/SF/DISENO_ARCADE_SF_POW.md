# DISEÑO · MODO ARCADE "HUELUM VS. GOYA" — versión POW completa (2026-07-16)

> **Estado: release candidate 1/9 (2026-07-18).** Decisiones cerradas con el dueño; audio,
> arte pendiente de esta pasada y auditoría completa de campaña implementados. Leer antes:
> 07 §HUELUM VS. GOYA + `AUDIT_SF_MULTIPLAYER.md`. Convenciones: 09
> (MVVM, estado inmutable con `_state.update { it.copy(...) }`, strings ES+EN con paridad,
> CRLF, Read para verificar). Los BUGS del modo (stun-lock, revancha, servidor LAN) viven en
> `PENDIENTES_SF_2026-07-16.md` y NO dependen de esto.

## Cambios 2026-08-29/30 (Claude Sonnet 5) — CONTRAATAQUE + DERRIBO CON PODER (motor + arte + pipeline: 18/18 completos)

Dos movimientos nuevos de la familia Agarre/Parry, diseñados desde la mecánica antes de tocar
arte (siguiendo cómo se agregó OVERHEAD en 2026-07-21). Detalle de diseño + implementación en
`SF/PROMPT_hoja30_contraataque_derribo.md` y en el mensaje de PR/commit correspondiente.

| Movimiento | Cómo se hace | Regla |
|---|---|---|
| **Contraataque** | botón **L3**, ventana activa 200ms (más corta que el parry) | si conecta un golpe rival, se anula ENTERO y pasa DIRECTO a un `THROW` gratis (reutiliza el arte de Agarre→Lanzamiento) con daño de bonus (34, entre agarre 26 y súper 45). Si falla, recuperación más larga que el parry. |
| **Derribo con Poder** | botón **R3**, exige medidor ≥ 40/100 y rango de agarre | agarre especial: intento telegrafiado (4 cuadros, el doble que el agarre normal de 2) → remate propio (`POWER_THROW`, 8 cuadros) con más daño (42) y más empuje que el agarre normal. El medidor se gasta al intentarlo, conecte o falle. |

- **Motor (`:shared`) TERMINADO y verificado**: `SfFighterState.COUNTER/POWER_GRAB/POWER_THROW`,
  `SfStateMachine.VALID_FROM`, `runStateHandler`, `applyCounterThrow`/`applyPowerThrow` en
  `StreetFighterCombate.kt`, IA en `StreetFighterCpuAi.kt` (contraataque = alternativa arriesgada
  al parry; derribo con poder preferido sobre el agarre normal cuando sobra medidor sin competir
  con la prioridad de la súper). Botones L3 (Esmeralda)/R3 (Rubí) en `StreetFighterScreen.kt`,
  usando la paleta que ya estaba reservada ahí para esta expansión ("Alt 3 · Gema/Elementos").
  Gate por movimiento (`playerHasCounterMove`/`playerHasPowerThrowMove`), no por todo el moveset
  3rd Strike — así el botón no aparece "muerto" en un personaje sin ESA hoja en concreto.
  `./gradlew :app:testDebugUnitTest :shared:testAndroidHostTest` en verde, detekt exit 0,
  `check_kmp_test_names.sh` OK. Sin arte, `hasAnim` bloquea los 3 estados nuevos: cero regresión.
- **Arte (hoja 30, 18 personajes) TERMINADO** — 4 rondas de corrección (cuadros pegados → rival
  dibujado → agarre reinterpretado como poder a distancia → Fila 3 corta por 1 cuadro en 3
  personajes). Detalle de las 4 en `SF/PROMPT_hoja30_contraataque_derribo.md` y en
  `SF/00_SF_INDEX.md` §"reglas que más caro han salido" (6-8).
- **Pipeline Python (`slice_sf_chroma_sheets.py`/`pack_sf_character.py`) TERMINADO** — 2 bugs
  reales de fusión de filas corregidos en `merge_fragments`/`maybe_split`.
- **Los 18 personajes están recortados + empacados** en `app/src/main/assets/STREETFIGHTER/`;
  motor reverificado en verde (`gradle test`, detekt, nombres de test) tras el empaquetado
  final. Nada de esto se ha commiteado. Detalle completo en `_SESION_ACTUAL.md`.

## Cambios 2026-07-25 (Opus 4.8) — súper que persiste entre rondas + GRADO de victoria

> ⚠️ **SIN COMPILAR en esta sesión** (falta `gradle-wrapper.jar` y Gradle 9.5). Rebuild +
> `testDebugUnitTest` + dispositivo pendientes. Detalle de red en `AUDIT_SF_MULTIPLAYER.md` (2026-07-25).

### 🔋 La barra de PODER (súper) PERSISTE entre rondas (decisión del dueño)

Como en el SF original: si llenas el medidor de súper en la ronda 1, sigue lleno en la 2 y la 3.
Antes se reiniciaba a 0 cada ronda porque `resetRound` reconstruía los peleadores desde el `base`
(`StreetFighterState()`, súper=0) conservando solo `id`/`metamorphosed`. Fix (VM `resetRound`):
`p0`/`p1` ahora copian también `superMeter = s.player.superMeter` / `s.cpu.superMeter`. `superReady`
es derivado (`superMeter >= MAX`) → se mantiene solo. El **mareo/STUN (`dizzyMeter`) NO se conserva**
(queda en 0 del `base`) → "la barra de stun sí se regenera", como pidió el dueño. El KO no vacía el
súper (solo lo consumen SUPER_ART/FATALITY en `changeState`), así que ambos lados lo llevan a la
ronda siguiente.

### 🏅 GRADO de victoria estilo SF III (PERFECT / COMBO / SUPER / TIME)

Al ganar una ronda se calcula su grado y se pinta con la fuente arcade **bajo la barra del ganador
durante el intro de la ronda siguiente** (como el SF original). Enum `SfRoundOutcome` (en el paquete
viewmodel). `endRound(sim, winnerIdx, now, outcome)` guarda `pendingRoundOutcome(+Winner)`;
`resetRound` lo vuelca a `state.roundResultLabel`/`roundResultWinnerIdx` y `drawHud` lo dibuja si
`showRoundIntro`. Cómputo (`computeRoundOutcome`): **TIME** (timeout) · **PERFECT** (HP del ganador al
máximo — computable en cualquier lado) · **SUPER** (el golpe de KO fue SUPER_ART/FATALITY) · **COMBO**
(`comboHits[ganador] >= COMBO_DISPLAY_MIN`) · si no, **NORMAL** (sin etiqueta). Offline/arcade = grado
exacto; **online** viaja en el `outcome` de `ROUND_ENDED` (degrada a NORMAL si falta). El KO del
COMBATE (MATCH_ENDED) no aplica: no hay ronda siguiente donde mostrarlo.

### 🥊 Orden de jefes INVERTIDO + metamorfosis Yoalli → La Presidenta (decisión del dueño)

Los dos jefes finales del arcade cambian de orden y la metamorfosis automática se INVIERTE:

- **Orden** (`SfArcadeLadder.build`): antes 14 Yoalli · 15 La Presidenta (FINAL). Ahora
  **14 La Presidenta · 15 YOALLI EHÉCATL (FINAL)**. `isBoss`/`isFinal` se calculan por índice, no
  por id, así que el resto del sistema (banners, desbloqueos, dificultad) sigue igual.
- **Metamorfosis** (`tryYoalliMetamorphosis`, antes `tryPresidentaMetamorphosis`): ahora es
  **YOALLI** quien a ≤1/4 de vida, en la ronda 1, lanza `BONUS_POWER_10` y se transforma en
  **LA PRESIDENTA con la VIDA LLENA** (segunda vida). Antes era La Presidenta → Yoalli con
  `BONUS_POWER_11`. `completeYoalliMetamorphosis` pasa de "conserva HP" a **vida llena**. La
  dirección vieja (`completePresidentaMetamorphosis` / P11) queda por simetría pero INACTIVA en
  gameplay. `sfUsableBonusPowerCount` ahora excluye también el P10 de Yoalli (era lanzable). El
  showcase y su conteo de pasos (`showcaseTotalSteps`) fuerzan la metamorfosis de Yoalli. La Screen
  ya precargaba ambos atlas en las dos direcciones, sin cambios.

### 🤖 Rebalance de la IA del arcade (decisión del dueño: "muy fácil, no escala")

- **Sube un escalón por encima de la etiqueta** (`SfArcadeLadder.difficultyForStep`): "Fácil" ya no
  usa BASICA (reaccionaba en ~1 s). Curva por pelea: 1-4 **+1**, 5-9 **+1**, 10-12 **+2**, jefes
  13-14 **+2**, FINAL 15 **+3** (tope PESADILLA). Así Fácil recorre NORMAL→AVANZADA→PESADILLA,
  Medio AVANZADA→PESADILLA y Difícil se juega en PESADILLA. La **iluminación** del mapa sigue la
  dificultad ELEGIDA (`lightingForArcadeDifficulty`: día/noche/apocalipsis), **desacoplada** de este
  bump. El VS/Práctica NO cambia (usa los 4 tiers explícitos, incluida BASICA para casual).
- **Rampa de intensidad más alta** (`intensityForStep`): arranca en **0.35** (antes 0.20) → las
  primeras peleas ya no se sienten lentas; sigue llegando a 1.0 en la final.
- Tests de caracterización (`SfArcadeCampaignAuditTest`, `SfBonusPowerTest`) actualizados a los
  nuevos valores. ⚠️ **Balance afinado por razonamiento, sin dispositivo**: si Difícil (PESADILLA
  desde temprano) resulta frustrante, bajar el `bump` de las primeras peleas.

### 💀 La IA ahora SÍ hace el FATALITY (con el medidor lleno) + 🏆 Calificación + 🎞️ FPS

- **Fatality de la IA (`maybeFatalityInput`):** el comando es "súper EN CARRERA"; antes la IA "nunca"
  lo lograba porque al correr hacia el rival entraba en rango de CLINCH y abortaba, o quedaba fuera
  del rango de dash. Ahora, con el medidor lleno, la IA se **COMPROMETE** (rival ATURDIDO = sí o sí;
  si no, azar que escala con la dificultad) y **completa** la secuencia dash → RUN → súper dentro de
  `FATALITY_INTENT_MS`, evaluada ANTES del clinch y con el watchdog/anti-walk-loop **desactivados**
  mientras dura (si no, lo abortaban). Se auto-cancela al soltarlo o si la interrumpen. El súper
  normal queda como respaldo.
- **🏆 Sistema de calificación (E..MS) estilo SF III:** mide el desempeño del JUGADOR a lo largo del
  COMBATE (daño hecho − recibido, parries, combo más largo, súpers/fatalities, variedad de golpes,
  rondas perfectas → `computeMatchGrade`) y muestra la nota (`SfGradeBadge`) en el menú de fin **solo
  si ganó**. Contadores por combate (`resetInternals`); solo con humano (no IA-vs-IA/showcase/tutorial).
  ⚠️ Umbrales afinados por razonamiento — ajustar tras jugar.
- **🎞️ Contador de FPS del combate:** Ajustes → Interfaz → "Mostrar FPS (modo pelea)" (junto a
  hitboxes). `SettingsRepository.getShowSfFps` → `SfFpsOverlay` (mide cuadros REALES con
  `withFrameNanos`). Análogo al del mundo abierto.

### 🕹️ Joystick con RESPUESTA INMEDIATA en la pelea (bug de controles "no instantáneos")

El `JoystickController` (compartido) usaba `detectDragGestures`: un **TAP puro se IGNORABA** y
tocar-y-mantener no registraba nada hasta cruzar el *touch-slop* → los controles se sentían
"pegados/lageados", el **agacharse** el más notorio (el ↓ no respondía al instante). Fix
(`GameControllers.kt`): nuevo flag **`respondToTouchDown`** (default `false` = arrastre de siempre,
mundo abierto/interiores/zombis SIN cambios) que la Screen del SF activa. Con él, la deflexión se
toma de la **posición del toque respecto al centro** y se dispara **YA** en el `awaitFirstDown`
(joystick virtual estándar), con la misma zona muerta (28%) y el bucle de 33 fps para el HOLD. Los
botones de ataque ya eran inmediatos (`detectHoldEvent` → `awaitFirstDown`).

### 🛡️ (2026-07-26) BLOQUEO "atrapado" al defender (segunda parte del bug de controles)

El dueño reportó que al **hacerse para atrás para defender** los controles seguían sin responder.
**Causa REAL:** los estados `BLOCK_HIGH`/`BLOCK_LOW` (se entra al bloquear un golpe cubriéndose) SOLO
salían si soltabas la dirección **Y** terminaba la animación, y **no aceptaban ninguna otra acción**;
peor aún, `BLOCK_HIGH`/`BLOCK_LOW` **no figuraban como orígenes válidos** en la tabla `validFrom`, así
que hasta el `→ IDLE` fallaba en `changeState` → **quedabas literalmente atrapado en la pose de
bloqueo**. Fix (`StreetFighterViewModel.runStateHandler` + `SfStateMachine.VALID_FROM`): la guardia
ahora **rebota AL INSTANTE** al neutro correspondiente (WALK_BACKWARD si sostienes atrás / CROUCH si
sostienes ↓ / IDLE-CROUCH_UP al soltar), estados totalmente responsivos que **vuelven a bloquear** si
te pegan otra vez. Se añadieron `BLOCK_HIGH→{IDLE,WALK_BACKWARD,CROUCH_DOWN}` y
`BLOCK_LOW→{CROUCH,CROUCH_UP}` a `validFrom`. El **blockstun real** lo sigue dando `hurtFreezeUntilMs`
durante el golpe, no la animación (así que no se pierde el bloqueo). Sin tocar tests (no había
aserciones de transiciones DESDE bloqueo).

## Cambios 2026-07-21f (Opus 4.8) — La Llorona: crouchTurn con `flipX`, hoja 09 regenerada, re-pack

Cierra los tres recortes malos que quedaban de La Llorona (auditados por el dueño en
`tools/_audit_sheets/lallorona_TODO.png`). **Solo se re-recortaron las hojas 09 y 29**; los
otros 17 peleadores no se tocaron (`git diff --stat` lo confirma: nada fuera de La Llorona).

### 1. `crouchTurn` — no era recorte, era ESPEJO

Los tres cuadros salían con la orientación invertida. No hace falta re-recortar la hoja 02:
se marcan a mano en el `_frame_meta.json` de la staging y la cadena ya existente los espeja:

```
GEN/lallorona/_frame_meta.json  {"crouch-turn-1|2|3": {"flipX": true}}
  → tools/pack_sf_character.py:624        (copia flipX al entry del JSON)
  → DATA/lallorona.json                   ("flipX": true en los 3 cuadros)
  → SfFrameCatalog.kt:87                  (lo lee al construir SfFrameDef)
  → StreetFighterScreen.kt:3336           (drawDirection = direction.opposite())
```

Es el mismo mecanismo genérico que ya usan Señor Tienda, Paramédico, Charro Negro y
Prankedy; **no se tocó ni una línea de Kotlin**. Revertirlo es borrar 3 claves del JSON.

### 2. `hurtHead` / `hit-face-1` — hoja 09 regenerada (⚠️ ojo con el conteo)

La hoja 09 anterior traía **14 poses de HURT HEAD SOLAPADAS**. El slicer las fundía en 8
blobs y uno medía **478 px = 4 figuras metidas en `hit-face-1`** (el PNG de la staging pesaba
54 KB frente a los ~15 KB de sus vecinos: ese peso es el síntoma barato de detectar).

La hoja regenerada (`newSFAssets/LaLlorona9 New.png` → instalada como
`LaLlorona_09_HeavyKick_HurtHead.png`, con la vieja guardada en `.BAK.png`) trae la fila
HURT HEAD **bien separada, pero con solo 3 poses** en vez de 14.

> ⚠️ **Trampa:** `SHEETS` en `slice_sf_chroma_sheets.py` describe el formato que comparten
> los 18 peleadores. Bajar ahí el `14` a `3` habría roto la hoja 09 de los otros 17. Y
> dejarlo en 14 con solo 3 figuras es igual de malo: `maybe_split` persigue los 14 blobs y
> **trocea cada figura en rebanadas verticales** (daba `HURT HEAD 11/14`).
>
> Por eso se añadió `SHEET_OVERRIDES = {("lallorona", 9): {"HURT HEAD": 3}}`: excepción por
> `(personaje, hoja)` que no toca la tabla global. Con ella: `HEAVY KICK 6/6 + HURT HEAD 3/3 OK`.

**Consecuencia asumida (decisión del dueño):** la animación consume 4 cuadros y el arte da 3,
así que `pick()` repite el central → **`hit-face-2` y `hit-face-3` son el mismo pixel** (rects
distintos en el atlas, contenido idéntico). Lee bien como animación de daño (impacto → pico →
pico sostenido → recuperación) y es muchísimo mejor que el `hit-face-1` con 4 figuras, pero
**si algún día se regenera la hoja 09 con 4+ poses separadas, actualizar el override y
quitar la duplicación.** La fila HEAVY KICK de esa misma hoja sí trae arte nueva y sale 6/6.

### 3. `superArt` `super-4/5/6` — ya estaba arreglado en la staging, faltaba empaquetar

La hoja 29 corta limpia con el slicer actual (`SUPER ART 8/8 + DANO AGACHADO 4/4 OK`).
Re-recortarla dio salida **byte-idéntica** a lo que ya había en la staging: el arreglo vivía
ahí desde antes y lo único que faltaba era el `pack_sf_character.py`. El atlas commiteado era
la versión BUENA revertida, por eso el fix no se veía en el juego.

### 4. Tarea 2 — `super-7` (blob 06 de la hoja 29) en otros peleadores: NO es fallo de recorte

Verificado abriendo las hojas 29 fuente. El artista dibujó ese cuadro distinto según el
personaje; el recorte es correcto en los tres casos:

| Peleador | `super-7` | Veredicto |
|---|---|---|
| **ESCOMBOY** | **EFECTO PURO** — explosión sin personaje | El arte viene así; el personaje reaparece en `super-8`. No tocar. |
| **PoliciaMasculinoCDMX** | CON personaje (puño en alto en el estallido azul) | Correcto. |
| **ReyGrupero** | CON personaje (brazos abiertos en el estallido dorado) | Correcto. |

Mismo caso que los `bonusPower` de La Presidenta: un cuadro de efecto puro es arte válido,
no un recorte roto.

### Verificación hecha

- `HEAVY KICK 6/6 + HURT HEAD 3/3 OK` y `SUPER ART 8/8 + DANO AGACHADO 4/4 OK` (dry-run `--list`).
- Revisión visual cuadro a cuadro del atlas YA empaquetado (recortando por `src` del JSON y
  aplicando `flipX`), no solo de los PNG intermedios.
- `DATA/lallorona.json`: `crouch-turn-1/2/3` con `"flipX": true`; `hit-face-1..4` con rects
  distintos; 228 cuadros; `proj-*` intactos (**la hoja 12 no se tocó**, así que no hizo falta
  re-aplicar `tools/fix_llorona_projectile.py`).
- `git diff --stat`: solo `LaLlorona.webp`, `lallorona.json`, `slice_sf_chroma_sheets.py`,
  la staging de La Llorona y la hoja 09. Ningún otro peleador.

## Cambios 2026-07-21e (Fable) — repaso de cierre: 4 huecos detectados y tapados

Repaso de la lista completa del dueño contra lo implementado. Cuatro cosas NO estaban:

1. **El tutorial nunca enseñaba la SÚPER.** `canPerform` exigía el medidor lleno y el
   currículum se filtraba con él al ARRANCAR (medidor a 0), así que la lección `b_super`,
   el combo `super_remate` y **5 combos de firma** que rematan con súper se caían de la
   lista sin avisar. Separado en `hasArtFor` (solo arte, lo que usa el tutorial) y
   `canPerform` (arte + recurso, lo que usa la IA). El medidor se llena durante la propia
   lección pegándole al muñeco.
2. **El FATALITY no salía en la hoja de combos.** Estaba en `basics` y la hoja lista
   `universal + signature`. Movido a `universal` → ahora aparece en la hoja Y en el
   tutorial (el currículum es `basics + universal + signature`).
3. **Faltaba el combo épico explícito.** Añadido `epico_carrera`
   (dash → correr → patada larga → especial), además del fatality que ya cruzaba de lado.
4. **Coleccionables no usaba las letras del SF** (requisito literal del dueño). Nuevo
   `SfBitmapText` (`features/streetfighter/ui/SfBitmapText.kt`): pinta con la fuente ARCADE
   del HUD (`sf_hud_pow.png`) fuera del Canvas de combate. No es un `Typeface` sino un
   atlas de recortes por carácter, así que se dibuja glifo a glifo; `sfFontSanitize` quita
   acentos/signos porque la fuente solo tiene A-Z, 0-9 y espacio.

**MP completado:** el `superMeter` que quedaba pendiente ya se sincroniza (`meter` en
`SfNetMsg` + las 3 implementaciones del transporte: interfaz, WS y BT/LAN). Es OPCIONAL en
el protocolo: un cliente viejo no lo manda y el receptor conserva el valor que ya tenía.

## Cambios 2026-07-21d (Fable) — FATALITY, coleccionables de peleador, WebP y audit MP

### 💀 FATALITY / "poder súper especial" (18/18 peleadores)

**No espera arte nueva:** es una SECUENCIA CINEMÁTICA compuesta con cuadros que cada
peleador YA tiene (`fatality_animation` en el packer): concentración (`super-1..3`) →
ejecución (`super-4..6`) → su poder propio (`bonus-1-*` o `special-*`) → remate
(`super-7/8`) → pose (`victory-*`), con ritmo lento-rápido-sostenido. Se puede AUDITAR sin
abrir el juego en `tools/_audit_sheets/_FATALITIES.png`.

- **Comando propio, en cualquier momento** (decisión del dueño): **súper EN CARRERA** con
  el medidor lleno → doble toque adelante (dash) + seguir adelante (run) + botón S. De ahí
  el "correr + golpe + poder".
- **Daño 70**, consume el medidor entero y **DERRIBA** (entra en `knockdownStates`).
- **Remate espectacular:** al terminar, el atacante **CRUZA al otro lado del rival**
  (`crossToOtherSide`) y ambos quedan encarándose — el giro lo da la propia cinemática.
- **IA:** lo prepara en dos tiempos (arranca a correr y, ya corriendo, lo suelta), solo en
  dificultades altas. **Tutorial:** lección `b_fatality` con su receta.

### 🏆 Coleccionable de PELEADOR = recompensa del arcade en DIFÍCIL

Antes Difícil **no daba nada**. Ahora, además del peleador y su mapa, otorga el
**coleccionable del rival vencido** (Menú principal → Coleccionables → pestaña
**PELEADORES**).

- Se identifican por PREFIJO de id (`fighter_<ID>`) → **sin migración de Room**.
- `ensureFighterCollectibles()` es idempotente y corre también en partidas viejas, así que
  quien ya lleve tiempo jugando también los recibe.
- El retrato se recorta del ATLAS de combate con `BitmapRegionDecoder` (solo la celda
  256×256 del idle): **cero arte extra en el APK y barato en gama baja** — no carga el
  atlas entero, que llega a 2560×7680.
- "VER HISTORIA" → **"Próximamente"** (placeholder acordado).

### 📦 WebP lossless: −13.4 MB

Los 18 atlas de peleador pasaron de PNG a **WebP lossless** (`tools/atlas_to_webp.py`).
IMAGES: **102 → 88.6 MB**. La verificación compara el **alfa exacto + el RGB de los píxeles
visibles**: WebP normaliza el RGB bajo los píxeles transparentes, así que un hash del buffer
RGBA crudo daba falso negativo (la primera pasada abortó por eso, y bien). `SfFighterId
.spriteAsset` y el packer ya apuntan a `.webp`.

### 🌐 Auditoría de MULTIJUGADOR — 2 bugs REALES corregidos

Lo bueno: los estados nuevos **ya viajaban** (van como `enum.name` y el receptor usa
`runCatching { valueOf() }.getOrNull() ?: estadoAnterior`, así que un cliente viejo que
reciba `FATALITY` no crashea). Lo que estaba MAL:

1. **El daño de los movimientos nuevos no viajaba bien.** `sendDamage` mandaba siempre
   `strength.damage`, así que EN LÍNEA un fatality pegaba 28 (fuerte normal) en vez de 70,
   y un agarre 12 en vez de 26. Se extrajo `damageForAttack(attacker, strength)` y ahora lo
   usan tanto el cálculo local como el aviso por red.
2. Mismo problema con la SÚPER (45 → se enviaba 28).

⚠️ **Pendiente de MP:** el `superMeter` NO se sincroniza, así que la barra dorada del rival
se ve vacía en línea (cosmético; el daño ya es correcto). Requiere ampliar `SfNetMsg`.

### 🔎 Repaso de los spreadsheets originales

Todo lo de las hojas 01-29 está implementado salvo las poses de ARMA (handgun/rifle, 22
cuadros en `_extra/`), que son del mundo abierto. **Hallazgo pendiente:** La Tzitzimime
tiene **5 bonus powers** implementados pero **3 hojas grok** (hasta 9) y Yoalli **10 con 5
hojas** → puede haber poderes sin recortar. Requiere inspeccionar el layout de filas de cada
hoja grok a mano; no lo toqué para no romper los que ya funcionan.

### 🗂️ Hojas de AUDITORÍA (`tools/sf_audit_sheets.py` → `tools/_audit_sheets/`)

- `<char>_TODO.png` (×18): TODAS las animaciones del peleador, una fila cada una, con el
  nombre de cada cuadro. Es el inventario REAL: sale de los atlas y JSON del juego.
- `_FATALITIES.png`: la secuencia del fatality de los 18, para juzgar si "sale bien".
- `_RESUMEN.png`: el roster completo de un vistazo.

## Cambios 2026-07-21c (Fable) — tutorial paso a paso, poses recuperadas y GAMA BAJA

### ⚡ GAMA BAJA — regresión de RAM que introdujeron las hojas nuevas (CRÍTICO)

Al empacar las hojas 20-29 los atlas pasaron de 2560×4608 a **2560×7680**. Se decodificaban
en **ARGB_8888 sin opciones**: ~**73 MB de RAM por peleador** (×2 en pantalla, ×3 con el
placeholder ALPHA). Eso es OOM asegurado en gama baja, y además muchas GPU antiguas ni
aceptan texturas de ese tamaño.

**Fix:** `SfSharedSheets.sheetFor(context, id, sampleSize)` acepta submuestreo y la Screen
usa `sampleSize = 2` cuando `isLowEndDevice()`. Los atlas bajan a 1280×3840 (~18 MB, **4×
menos**). Las coordenadas del JSON se dividen por el mismo factor con `sheetScale` en
`drawSpriteAnchored`/`drawFighter` (solo afecta al RECORTE; el tamaño de DESTINO no cambia,
así que el sprite se ve igual de grande, solo más suave). No se puede usar RGB_565: los
sprites necesitan alfa.

⚠️ **Tamaño en disco:** IMAGES pasó de ~82 MB a **102 MB** con todas las poses nuevas. Con
el AAB en 438 MiB y 40.84 MB de margen bajo el límite de Play, esto se come la mitad del
colchón. Palanca medida y NO aplicada: convertir los atlas de peleador a **WebP lossless**
ahorra ~25 % (3.42 → 2.56 MB en La Presidenta, píxeles idénticos), pero WebP decodifica más
lento que PNG y eso penaliza justo a la gama baja que acabamos de arreglar. Decidir con el
dueño antes del próximo release.

### 🏃 Poses que se recortaban y se TIRABAN

`correr` (8 cuadros, hoja 03), `idle-relaxed` (6) y `talk` (4, hoja 18) se recortaban a
`_extra/` y no llegaban al juego. Ahora son estados:
- **`RUN`**: se entra sosteniendo ADELANTE al terminar un dash (dash-run de 3rd Strike),
  a 320 px/s (entre caminar 180 y dash 430). Se puede **saltar y atacar desde la carrera**
  (`RUN` está en `attackValidFrom` y en el origen de `JUMP_START`).
- **`IDLE_RELAXED` / `TALK`**: poses sin guardia para intro de ronda y variantes de burla.

Las poses de arma (handgun/rifle, 22 cuadros) siguen en `_extra/`: son del mundo abierto,
no del modo pelea.

### 🎚️ Dificultad POR PERSONAJE que escala con el nivel

La IA es compartida, pero `CpuStyle` (specialBias/pressureBias, ahora + **comboBias**) ya
no es estático: `cpuStyleForLevel` **acentúa el perfil** con `cpuIntensity` (0.20→1.0 según
el escalón). Un zoner lanza cada vez más poderes, un rusher presiona y encadena combos cada
vez más. En VS (`cpuIntensity` = 0) el perfil queda prácticamente en su base, así que las
peleas sueltas no cambian.

### 🎓 Tutorial PASO A PASO con identificadores visuales

- **21 lecciones básicas nuevas** (bloque `basics` de `combos.json`): una por movimiento —
  caminar, agacharse, saltar, cada puño, patada, bloqueo, dash, correr, backdash, parry,
  agarre, barrida, antiaéreo, overhead, patada larga, aéreo, especial, súper y burla. El
  currículum (`SfCombos.curriculum`) enseña PRIMERO los básicos y luego los combos.
- **Identificadores visuales:** cada paso es un chip del **color del botón real** (X azul,
  Y amarillo, B rojo, A verde, P cian, G naranja, S dorado, direcciones azul-violeta), con
  ✓ al acertar y borde blanco en el paso actual.
- **Feedback de ERROR:** si ejecutas otro movimiento reconocible, sale un cartel rojo
  "ESO NO ERA" con **lo que hiciste → lo que tocaba** (`reportTutorialMistake`, con
  cooldown de 1.5 s para no saturar). Antes te quedabas adivinando por qué no avanzaba.

## Cambios 2026-07-21b (Fable) — COMBOS data-driven, TUTORIAL interactivo y fix del menú

### 🥊 Catálogo de COMBOS (`assets/STREETFIGHTER/DATA/combos.json`)

Data-driven: ampliar combos NO exige tocar código. **10 universales** (todos comparten el
moveset) + **1 de FIRMA por personaje** (18; rematan con su especial o su súper). Los 3 sin
firma (GRANADERO, LAZARO, PARAMEDICO) son los de hoja compartida y usan los universales.
Loader: `features/streetfighter/data/SfCombos.kt` (`SfCombo`, `SfComboAction`).

Cada combo trae `steps` (acciones), `level` 1-4 (dificultad), y nombre/pista ES+EN. Lo leen
**dos** consumidores, con la MISMA traducción acción→input (`inputForAction` en el VM), que
es la única fuente de verdad de "cómo se hace" cada movimiento:

- **La IA** encola la ruta y la ejecuta en orden (`queueCombo`/`nextComboInput`). Elige el
  combo de FIRMA o uno universal de su nivel según `cpuIntensity` (fácil = hasta nivel 2,
  difícil = hasta 4), y solo rutas que el peleador PUEDE ejecutar (`canPerform` comprueba
  arte y medidor). La ruta caduca a los 2.2 s (`COMBO_ROUTE_TIMEOUT_MS`) para no insistir
  con pasos que ya no aplican.
- **El tutorial** valida cada paso con `stateForAction`.

### 🎓 TUTORIAL INTERACTIVO + hoja de combos

Entrada nueva **"COMBOS Y TUTORIAL"** en `SfModeMenuOverlay`, al mismo nivel que Práctica e
IA vs IA y **siempre visible** (no es herramienta de QA: es cómo se aprende el modo).

1. **Elegir peleador** entre los DESBLOQUEADOS (reusa `CharacterSelectOverlay`).
2. **Hoja de combos** (`SfComboSheetOverlay.kt`): tabla de TODOS los controles de combate
   (mover, puños, patadas, bloqueo alto/bajo, dash, parry, agarre, barrida, overhead,
   especial, súper) + la lista de combos con su receta paso a paso y su pista.
3. **PROBAR → tutorial guiado** (`SfTutorialOverlay.kt` + estado `tutorial*` en
   `StreetFighterState`): lecciones **con validación** — la pantalla pide un movimiento y
   solo avanza cuando el jugador lo ejecuta de verdad (se compara el ESTADO real del
   peleador). Marca los pasos acertados (✓), muestra "¡BIEN!"/"¡COMBO COMPLETO!", el
   progreso "LECCIÓN n/N", y tiene REPETIR / SALTAR / volver.

**Muñeco inerte** (decisión del dueño): en tutorial la CPU no recibe input (`buildCpuInput`
devuelve vacío), **el muñeco no pierde vida** y **el reloj no corre** — una lección no se
puede perder por KO ni por tiempo. El jugador SÍ carga medidor al pegarle, para poder
practicar la súper del catálogo.

### 🐞 Fix del menú principal (etiquetas ALPHA/BETA descolocadas, reportado en S24)

`WithCornerBadge` sacaba la etiqueta con `offset(x = 8, y = -8)`: ese desplazamiento
**vertical negativo** la mandaba por ARRIBA del botón, invadiendo la fila anterior — con la
fuente del sistema en grande la etiqueta crece y aparecía pegada al botón de arriba (el
"otro renglón" del reporte). Además ni el rótulo del botón ni la etiqueta limitaban líneas,
así que podían partirse en dos dentro de un botón de ALTO FIJO (56/76 dp) y recortarse.
Ahora: la etiqueta solo se desplaza en **horizontal** (hacia el margen lateral que siempre
existe porque los botones ocupan 85-92 % del ancho), el `Box` fija su ancho con
`fillMaxWidth()` para tener una esquina de referencia estable, y todos los textos llevan
`maxLines = 1` + `softWrap = false`. ⚠️ No pude reproducirlo en un S24 real: el arreglo es
estructural (no depende de la densidad ni del tamaño de fuente), **falta confirmarlo en el
dispositivo del dueño**.

## Cambios 2026-07-21 (Fable) — MOVESET 3rd Strike COMPLETO + arreglos de assets

**Assets (hojas 20-29, tandas 5-8):** 179/180 hojas recortadas y empacadas para los 18
peleadores. Flujo completo y repetible en **`FLUJO_ASSETS_SF.md`** (herramientas nuevas:
`sf_identify_new_sheets.py`, `slice_new_sheets_batch.py`, `fix_llorona_projectile.py`).
17/18 con las 21 animaciones nuevas; La Llorona sin `longKick`/`overhead` (falta su hoja 26).

**Motor — 21 estados nuevos en `SfFighterState`** (`jsKey` = animación del JSON), todos
protegidos por `hasAnim`: quien no tenga el arte NUNCA entra al estado.

| Movimiento | Cómo se hace | Regla |
|---|---|---|
| **Dash / Backdash** | doble toque adelante/atrás | velocidad alta, lo corta su animación |
| **Bloqueo alto/bajo** | atrás (de pie) / atrás+abajo | daño /4, ahora con POSE de guardia visible |
| **Parry alto/bajo** | botón **P** (bajo si agachado) | ventana 260 ms: anula el golpe ENTERO y deja al atacante vendido 320 ms |
| **Golpes agachado** | agachado + puño/patada | encadenan entre sí (cancel bajo) |
| **Antiaéreo** | agachado + puño fuerte | |
| **Barrida** | agachado + patada fuerte | DERRIBA (THROWN → GET_UP) |
| **Aéreos** | puño/patada en el salto | UNO por salto |
| **Patada larga** | adelante + patada fuerte | su normal de mayor alcance |
| **Overhead** | adelante + puño medio | **rompe la guardia baja** |
| **Agarre → lanzamiento** | botón **G** pegado al rival | atraviesa el bloqueo; daño fijo 26 |
| **Burla** | botón **T** | |
| **Super Art** | botón **S** con medidor lleno | consume el medidor, daño 45, derriba |

- **Medidor de súper** (`SfFighter.superMeter`, 0-100): +8 al pegar, +5 al recibir, +2 al
  bloquear. Barra en el HUD bajo cada nombre (dorada al llenarse). Solo se pinta para
  peleadores con `superArt`.
- **Derribado = INVULNERABLE** mientras está en el suelo y se levanta (como el arcade).
- **IA:** `cpuNewMove` usa el arsenal nuevo — súper de cerca, barrida para castigar,
  agarre a quien se cubre, overhead contra guardia baja, patada larga en footsies, parry en
  dificultades altas y dash para cerrar hueco. Devuelve null si el peleador no tiene el arte
  → la IA de siempre queda intacta.
- **Placeholder ALPHA:** si falta una hoja, el movimiento **igual se juega** con el arte del
  estudiante del mismo género en silueta negra pixelada + rótulo "ALPHA" (fuente del HUD).
- **Controles:** columna nueva de botones (T/P/G/S) que solo aparece si el peleador tiene el
  moveset; el hint de controles cambia al de los movimientos nuevos. Strings ES+EN.
- **Verificado:** `compileDebugKotlin` OK, `testDebugUnitTest` OK, detekt 0 smells.
  **Falta probar en dispositivo** (afinar ventanas de parry/dash con el dueño).

**Arreglos de assets de esta pasada:**
- **La Llorona:** su especial "se lanzaba a sí misma" — su hoja 12 es una rejilla 4×3 y el
  slicer metió 2 cuadros de su CUERPO como `proj-*`. Ahora sus 5 cuadros de proyectil son
  los efectos reales (cadena, zarpazo, orbe, estela).
- **La Presidenta:** `bonusPower1-6` la hacían desaparecer 3 cuadros (eran guiones con el
  proyectil, no animación suya) y sus efectos (mazo, libro, bolsa de dinero) no se usaban.

## Cambios 2026-07-20 (Fable) — COMBOS 3rd Strike + audit de audio + QA visual de assets

- **🥊 COMBOS estilo SF III 3rd Strike (primer corte del P1 "Combate SF original"):**
  - **Chain cancel:** un golpe normal que CONECTA (attackStruck) se cancela en el siguiente
    de mayor fuerza — ligero→medio→fuerte, puño o patada (`tryChainCancel` en el VM). En
    whiff NO hay cancel (recuperación completa, como el arcade).
  - **Special cancel:** cualquier golpe normal conectado cancela en el ESPECIAL (respeta
    cooldown y tope de proyectiles). `specialValidFrom`/`attackValidFrom` se ampliaron con
    los estados de golpe; el gate real es `tryChainCancel` (ningún otro handler pide
    golpe→golpe), así que NO se puede encadenar sin conectar.
  - **Contador de combo en HUD:** "N GOLPES"/"N HITS" (string `sf_combo_hits`, paridad
    ES+EN) del lado del atacante, desde 2 golpes; campos nuevos `comboCount`/`comboPlayerId`
    en `StreetFighterState` (el VM llena/expira con `RAPID_HIT_WINDOW_MS`; la View solo pinta).
  - **Escalado de daño:** −10% por golpe encadenado, piso 50% (`COMBO_DAMAGE_SCALE_*`) —
    los combos no son letales gratis. El anti-bucle previo (COMBO_ESCAPE) sigue intacto.
  - Aplica offline (VS/arcade/IA vs IA); online el HP del rival es autoridad remota (sin
    contador local). Falta: probar en dispositivo (Rebuild) y afinar ventanas con el dueño.
- **🔊 Audit MP3→OGG retomado:** nueva tool `tools/sf_audio_audit.py` (mapeo real desde
  `sfVoicePacks`, duración, pitch, transcripción Whisper, regenera `_audio_review/*.mp3`) →
  reporte `tools/sf_audio_audit_report.md`. **Frase win de La Presidenta corregida** en el VM
  («Porque patria se escribe con A de mujer»). Los 4 clips escom* quedan FLAGGED para oído
  del dueño (gritos sin habla; escomboy_attack dura 10.7 s). Ver `AUDIO_INVENTARIO_SF.md`.
- **👁️ QA visual de assets:** nueva tool `tools/sf_contact_sheet.py` (hoja de contacto con
  1 frame por animación) + hojas generadas de los 17 peleadores en `tools/_contact_sheets/`.
  La Llorona: sus frames EMPACADOS están todos de frente (el "de espaldas" reportado debe
  ser de runtime/espejado o de otra anim → confirmar en dispositivo con la hoja a la mano).
- **🗺️ Mapas UAM AZC/Cuajimalpa:** los atlas actuales YA salen de los videos más recientes
  en disco; actualizar = BLOQUEADO en videos nuevos del dueño (ver QA_SF_STAGES).

## Cambios 2026-07-19 (Antigravity) — Sombra Isla de las Muñecas y QA de Voces

- **Sombra Isla de las Muñecas:** Implementada la plataforma de madera flotante y la sombra más prolongada (1.8x) para evitar que los peleadores parezcan flotar sobre el agua en el mapa Isla de las Muñecas.
- **QA Voces Policías:** Verificado que el mapeo y los archivos de audio coinciden con el diseño.
- **Voz Masculina por Defecto ("ZA ZA"):** Se recortaron los segundos 8-10 del fuente `ZA ZA.mp3` y se guardó como `special_male_attack_grunt.ogg` en `SOUNDS/`.
- **Señor de la Tienda (pack completo):** Se agregaron audios de ataque normal x2, daño x2 (incluyendo "puerquito" con subtítulos) y victoria.
- **Escomboy y Escomgirl:** Se eliminaron sus audios de voz especiales y se reemplazaron por un efecto de electricidad acelerado a 1.5x.
- **Robot (Escomrobot):** Se añadió su audio de victoria `special_robot_win.ogg`.
- **La Presidenta:** Se eliminó su especial de voz y se convirtió en su audio de victoria `special_la_presidenta_win.ogg` recortando el primer segundo de silencio.
- **La Tzitzimime:** Se eliminó su especial de voz y se convirtió en su golpe normal `special_la_tzitzimime_attack.ogg` recortando el último 10% de su duración (dejando 2.25s).
- **Paramédico Cruz Roja:** Se configuró su especial de voz como su audio de victoria (`special_paramedico_cruz_roja.ogg` con subtítulo) y se añadió el efecto de electricidad a 1.5x como su especial de poder (`special_paramedico_cruz_roja_power.ogg`).
- **Policía CDMX Mujer:** Se re-asignó el audio recortado de 2.3 segundos como su sonido al recibir daño (`special_pol_m_hurt.ogg`), se dejó la frase única como su ataque normal (`special_pol_m_attack.ogg`), y se configuró la pista oficial `special_policia_cdmx.ogg` como su audio de victoria.
- **Interrupción de Sonidos (Anti-Overlapping):** Se modificó el reproductor de audios y SoundPool en `StreetFighterScreen.kt` para que no se traslapen los sonidos de golpes ni las voces del mismo peleador al recibir golpes sucesivos, interrumpiendo la reproducción anterior y reiniciándola desde el principio.

## Fix 2026-07-18g (Claude) — regresiones de IA/input

- `isAnimationCompleted` (VM) ahora da por terminada la animación al llegar al último frame,
  no solo con el frame `-1`. Corrige que el JUGADOR quedara atascado en ARCADE (estudiantes
  ALPHA sin `-1`) y que la CPU se congelara. No afecta animaciones bien formadas.
- Watchdog ofensivo de la CPU cubre cualquier distancia (antes solo `<150 px`): fuerza
  acercarse (lejos) o atacar/clinch (en rango) → IA vs IA ya no "camina y se mira".
- Agresividad fina de AVANZADA/PESADILLA: afinar contra grabación IA vs IA (no a ciegas).

## Fix 2026-07-18h (Claude) — red de seguridad + autojuego (gauntlet)

- `watchStuck`: desatasca estados transitorios cuya animación no termina (asset sin frame -1) →
  arregla "se pegan y no se mueven"; registra el asset roto. `watchStalemate`: detecta peleas
  sin daño >12 s y pica a la IA. Log en logcat `SF-DIAG` + `diagnosticsReport()`.
- Autojuego (gauntlet): `startGauntletRoundRobin` (todos vs todos) y `startGauntletArcade`
  (escalera en orden rotando peleador). Encadena peleas IA vs IA (tope 60 s c/u), progreso +
  DETENER, y al final escribe un .txt en getExternalFilesDir + reporte en pantalla. Sirve para
  cazar assets rotos: cuando salga el reporte, corregir en la siguiente pasada.

## Fix 2026-07-19b (Fable) — el HURT "no sonaba"

La interrupción anti-traslape (`getFighterPrefix` en `playSfSpecial`) agrupa attack+hurt del mismo
peleadór bajo un prefijo; al recuperarse y contraatacar, su `attack` cortaba su propio `hurt` casi
al instante. Fix: un HURT en curso NO se corta por un ATAQUE del mismo peleadór (solo otro HURT lo
reinicia). Se inicializaron los arreglos de tiempos a 0L para evitar un desbordamiento numérico (overflow) de Long.MIN_VALUE que silenciaba los quejidos, se eliminó el cooldown de hurt (cooldown = 0s) para interrupción inmediata en combos, y se garantizó el play completo (sin interrupciones) de los ataques especiales. **Coverage:** solo 6 peleadores tienen hurt (Charro,
Paparazzi1, Llorona, Señor Tienda, Policía Mujer + Granadera); el resto solo suena el SFX de
impacto global. Detalle en `AUDIO_INVENTARIO_SF.md`.

## Cambios 2026-07-18r/s (Fable) — re-mapeo de voces + tamaño AAB (ver AUDIO_INVENTARIO_SF.md)

- **Voces re-disparan** (`playSfSpecial`): si el mismo clip ya suena, se corta y re-lanza (antes se
  ignoraba → "no se repetía" al re-atacar).
- **Re-mapeo de audios del dueño (18s):** Llorona/Tzitzimime → golpe normal (attack); Rey Grupero →
  intro; Paramédico Cruz Roja → win; Paparazzi 1 power = `special_paparazzi_5`, daño ×3; granadero
  win = `special_granadero` (diana). BORRADOS: `special_gr_win/lazaro/paramedico/prankedy/
  paparazzi_1/papz1_attack_1/2`. `sfVoicelessFighters` = {Lázaro, Paramédico, Prankedy} (especial
  suena hadouken; auditoría no los marca). **POLICÍAS: el dueño duda del contenido vs nombre →
  pendiente escuchar y confirmar.** Mapeo COMPLETO y guía de empaquetado: `AUDIO_INVENTARIO_SF.md`.
- **Tamaño AAB (bloqueaba la subida):** el peso era IMAGES (atlas de fondo), no el audio. Los 48
  `fondo_*_anim.webp` se pasaron de webp LOSSLESS a LOSSY q90 → **221→82 MB** (total SF 235→97 MB).
  Audio: borrados `amb_*.ogg` (4.5 MB) y `prankedy-persecucion.mp3` (musicFile→`prankedy_lobby.mp3`).

## Cambios 2026-07-18q (Fable) — FRASES por evento + subtítulos multilínea + diana granadero

Sistema de voz reescrito a **líneas con FRASE** (`SfVoiceLine(file, phrase)`; cada evento es una
lista, se elige al azar y muestra subtítulo). Mapeo FINAL (corrección del dueño):
- **HOMBRE (policía + granadero comparten intro/attack; WIN difiere):**
  - intro `special_pol_h_intro` = "¡Está prohibido beber en la vía pública!"
  - attack `special_pol_h_attack` = "Buenas joven, ¿si sabe porque lo detuvimos?" (antes era su "power")
  - win normal `special_pol_h_win` = "¿Se cree más chingón que nosotros o qué joven?"
  - **win granadero** `special_gr_win` = **"3 de diana"** (bugle, sin subtítulo; de `special_granadero.ogg`
    recortado 10 s — antes estaba mal en su ataque).
- **MUJER (policía + granadera comparten attack; WIN difiere):**
  - attack (2 frases al azar) `special_pol_m_attack_1/2` = "Tu denuncia me hace lo que el viento a
    Juárez" / "¿Sabes cuántas tengo?" (venían de "golpeada", ahora son su ATAQUE)
  - win normal `special_pol_m_win` = "Al decidir ser policía me comprometí como mujer" (era "Ataque MUJER")
  - **win granadera** `special_gr_win` = "3 de diana" (misma diana).
  - (La mujer YA NO tiene hurt: la golpeada pasó a ataque y el "ataque" a victoria.)
- **Subtítulos:** ahora **multilínea (máx 3)** con word-wrap ~24 chars y **fuente más chica**
  (`sizeMul` 0.85→0.6, apilados desde abajo) → las frases largas ya no se salen de pantalla.
  Todas las frases se sanitizan a A-Z/0-9 (fuente pixel del HUD) con `sfHudSanitize`.
- Paparazzi 1 sin cambios (attack×2, hurt×2, sin frases). Auditoría del showcase verifica cada
  archivo `.ogg` de cada línea del pack.

## Cambios 2026-07-18o/p (Fable) — PACK DE VOCES por evento (policía + Paparazzi 1)

Sistema GENÉRICO de voz por peleadór (`SfVoicePack` + `sfVoicePacks: Map<SfFighterId, SfVoicePack>`),
material del dueño (`nuevoMaterial18JUL/Audios/`). Eventos: **intro, attack, hurt, power, win**
(cada uno con N variantes). Se reproducen por la ruta `special_*` (MediaPlayer en la Screen, apto
para clips largos). Naming: 1 variante = `special_<key>_<ev>.ogg`; N = `special_<key>_<ev>_<n>.ogg`.

- **Policías (comparten pack POR GÉNERO, normal + granadero):** `pol_h` (HOMBRE:
  `POLICIA_CDMX_HOMBRE`/`POLICIA_GRANADERO_HOMBRE`/`GRANADERO`) = intro+power+win; `pol_m` (MUJER:
  `POLICIA_CDMX`/`POLICIA_GRANADERO_MUJER`) = win + hurt×2, **SIN power** (ajuste dueño 2026-07-18p):
  hurt_1 = primeros 3 s de "golpeada mujer"; hurt_2 = primer 1 s de "Ataque mujer" (ese audio NO va
  en el special, es reacción de daño → su special usa el fallback genérico). "win hombre" recortada
  la 1ª mitad muda. **Las voces COMPLEMENTAN los SFX genéricos de golpe (suenan los 2), no los
  reemplazan** (el `<strength>-<type>-hit` se emite igual en `applyAttackHit`).
- **Paparazzi 1** (`PAPARAZZI_1`, key `papz1`): attack×2 (grito al golpear) + hurt×2 (recibir/STUN).
  ("Golpear" 15 s dividido en 2; "recibir golpe/STUN" = hurt_1; "STUN últimos 3 s" = hurt_2.)
- **Hooks:** POWER = `emitSpecialVoice`; WIN = `emitWinVoice` (VICTORY real+showcase); HURT =
  `emitHurtVoice` en `applyAttackHit` (cooldown 2.6 s/índice; showcase demo sin cooldown); ATTACK =
  `emitAttackVoice` en `changeState` al entrar a golpe (cooldown 4.2 s/índice); INTRO =
  `emitIntroVoice` 1×/ronda (guard `introVoiceSent`, hook en `tick`). Resets en
  `resetInternals`/`resetRound` (introVoiceSent, lastHurt/AttackVoiceMs).
- Interpretación (el dueño puede corregir): "Ataque"/"Golpear" = grito ofensivo; donde el pack no
  tenga un evento, no suena. La auditoría del showcase verifica TODOS los eventos/variantes del pack.

## Cambios 2026-07-18n (Fable) — IA vs IA con desnivel + verificación dificultad estilo 3rd Strike

- **IA vs IA "se esquivan y nadie gana" → DESNIVEL ALEATORIO:** `startAiVsAi` ahora baja la
  dificultad a UNO de los dos al azar (1–2 escalones bajo PESADILLA, piso NORMAL) vía nuevo
  `cpuDiffOverride[2]`; `buildCpuInput` usa esa dificultad POR ÍNDICE solo en IA vs IA. Así el
  más fuerte conecta y GANA; se re-aleatoriza en cada pelea/revancha/gauntlet (el ganador varía).
  Se limpia en `resetInternals`; fuera de IA vs IA no cambia nada (usa la dificultad global).
- **✅ Verificación dificultad estilo "SF III: 3rd Strike" (ya implementada):** el arcade YA tiene
  dificultad variable OCULTA + calibrada a la elegida:
  - `SfArcadeLadder.intensityForStep(index,total)` = rampa 0.20→1.0 según avanzas (rank oculto).
  - `SfArcadeLadder.difficultyForStep(base,step)` = sube el TIER (+1 en jefes/≥10, +2 en la final)
    sobre la dificultad ELEGIDA como base (Fácil/Medio/Difícil).
  - `cpuIntensity` calibra cadencia de decisión y agresividad/bloqueo en `buildCpuInput`/
    `smartCpuDecision`. Resultado: se endurece al avanzar Y respeta la base elegida — igual que 3rd Strike.

## Cambios 2026-07-18m (Fable) — desbloqueo por dificultad, dev-tools, flechas P1/P2, dificultad sin escenario

- **Desbloqueo por dificultad ELEGIDA** (`handleArcadeMatchEnd`, key = `arcadeChosenDifficulty`):
  FÁCIL (BASICA) → SOLO el mapa del rival; MEDIO (NORMAL) → el PELEADÓR + su mapa (mínimo para
  tener al personaje); DIFÍCIL (AVANZADA/apocalíptica) → NADA por ahora (próx.: animaciones/
  poderes). La escalera siempre avanza al ganar. Antes: cualquier victoria desbloqueaba peleadór+mapa.
- **"ELIGE DIFICULTAD" sin escenario:** los strings `sf_arcade_diff_*_desc` + `_maps_hint` ya NO
  dicen día/noche/apocalíptica; ahora describen el AI y el desbloqueo (ES+EN).
- **Flechas P1/P2 en el selector:** `SelectArrowHeader` + params `allyId`/`showPickArrow` en
  `CharacterSelectOverlay`. Al elegir al P2/rival (práctica + IA vs IA): flecha AZUL "P1 ▼" sobre
  el ya elegido y flecha ROJA "P2 ▼" sobre el resaltado (además de la animación del card).
- **Autojuego/Showcase = SOLO Modo Desarrollador:** los 4 botones (todos vs todos, 9 campañas,
  showcase animaciones/sonidos, showcase audios) se envuelven en `if (devMode)` dentro de
  `SfModeMenuOverlay` (param `devMode = viewModel.devUnlockAll()`), bajo header
  `sf_dev_tools_header`. Los usuarios normales solo ven Arcade/Práctica/IA vs IA/Multijugador.

## Fix 2026-07-18j (Fable) — IA variada + showcase completo con auditoría estática

- IA "quietos/mismo ataque": `cpuLastOffenseMs` ahora se alimenta del ESTADO real (atacando),
  no de la intención (los inputs descartados por cooldown/validFrom/HURT contaban como
  ofensiva); con pasividad >2× límite el golpe es OBLIGATORIO en rango (≤~2 s sin acción).
  `cpuClinchBreak` en IA vs IA con ROLES asimétricos (índice+tiempo: uno golpea, otro se
  separa) — antes ambos rodaban la misma tabla y quedaban pegados. `variedCpuAttack`
  (no repite la firma fuerza×tipo anterior) sustituye a `randomCpuAttack`; special lejano
  con fuerza al azar.
- Showcase COMPLETO: `showcaseExtraStates` (giros, 6 HURT, KO, VICTORY) + metamorfosis de
  La Presidenta, aplicados con `forceShowcaseState` (bypass validFrom; reporta anims
  inexistentes). `showcaseStepMs`=2000 (> stuckLimitMs), cap por pasos
  (`gauntletFightCapCurMs`), timer congelado y golpes sin daño en showcase (solo SFX/splash).
- Auditoría ESTÁTICA: `auditFighterAssets` (anims faltantes/vacías por `SfFighterState.jsKey`,
  frames rotos, `special_<id>.ogg`) + `auditThemeSounds` (SFX del tema + música) → mismo
  reporte .txt/overlay. Detalle en 07 §HUELUM VS. GOYA.

## Fix 2026-07-18k (Fable) — showcase v2 (feedback del dueño en dispositivo)

- Ritmo: avance automático al terminar la animación (ambos IDLE + paso disparado + ≥400 ms) y
  botón "Saltar animación" (`skipShowcaseStep`, `sf_showcase_skip`, `state.showcaseRunning`).
- Fix salto perdido: los one-shot esperan IDLE para disparar (`showcaseInput(now, SfFighter)`).
- Mapas: cada pelea del autojuego en el mapa HOGAR del peleadór (`state.gauntletMapFile`;
  showcase=día, gauntlet=apocalipsis); la Screen lo prioriza en `effectiveBgFile`.
- Audio: pasos forzados con .ogg reutilizados (HURT→hit por fuerza, KO→heavy-kick-hit,
  VICTORY/metamorfosis→voz `special_<id>.ogg`, solo idx 0); VICTORY con voz también en pelea
  real. El estado de audio pendiente de esta pasada queda superado por Release 1/9 abajo.

## Release 2026-07-18 · Launch Relase 1/9 (Sol) — audio/arte/IA cerrados

- Audio regenerado desde las fuentes locales para los 21 ids, solo español, duraciones por
  contenido (2.37–13.8 s hablado; banda Granadero completa 27.5 s). `MediaPlayer` reproduce los
  specials largos completos; `SoundPool` conserva efectos breves. Presidenta auténtica 8.3 s,
  Lázaro incluido y Robot ficticio sintetizado. Whisper verifica texto/idioma de cada voz.
- Pipeline reproducible: `transcribe_sf_voice_sources.py` → `sf_audio_cuts_v3.json` →
  `build_sf_audio_v3.py` → `verify_sf_audio_content.py`; reportes v3 con hashes, 21/21 PASS.
  No se re-scrapeó YouTube ni se borraron `raw_yt/`/`out_diarized/`.
- La Llorona: HURT repetidos regenerados con 3–4 poses únicas. Yoalli: `bonusPower10` invierte
  la metamorfosis Presidenta P11 y convierte Yoalli→Presidenta conservando HP.
- Auditoría IA ejecutable: 9 campañas completas, 135 peleas, dificultad/intensidad/mapa reales,
  aceleradas solo en el bot QA. Test puro: 600 configuraciones aleatorias + presencia de assets.
- CI: errores `ComplexCondition`/`UnusedParameter` corregidos; detekt bloqueante local PASS;
  debug APK, unit tests y release AAB compilan con AGP/Gradle actuales. Versión 1.0.0.12.

## Hotfix de entrega 2026-07-18 — límite Play, skip real y AAB manual

- El primer upload de 1.0.0.12 llegó con firma/versionCode válidos, pero Play rechazó el módulo
  `base` por superar 500 MB comprimidos.
- Los 48 atlas de escenario pasaron de PNG a WebP lossless exacto; se verificó hash RGBA idéntico
  por archivo. Los 29 fondos fijos heredados sin referencias runtime se movieron a
  `_ORPHAN_ASSETS/STREETFIGHTER/legacy_static_backgrounds` (preservados, fuera del AAB).
- Resultado real de `bundleRelease`: AAB **434.47 MiB**, `base` comprimido **433.84 MiB
  (454.91 MB)**, margen preventivo **45.09 MB** bajo el límite decimal. Las 21 voces y los
  WAV generales no se recomprimieron.
- `skipShowcaseStep` marca completado el bloque del peleador y su timer; el tick siguiente
  encadena al siguiente peleador sin esperar el resto del guion.
- CI migra a Actions Node 24, `tracks: alpha`, notas ES/EN y validación 500 MB. El AAB firmado
  se guarda como artefacto; `manual-play-upload` permite la entrega manual sin doble publicación.

## Hotfix 2026-07-18 · fondos realmente animados + QA acelerado + La Llorona

- Pipeline de mapas: 15 cuadros fuente repartidos en 5 s, playback 6 fps y ping-pong de 28
  pasos. Matriz cerrada 16×3: día, noche y noche tenebrosa; logo POW aplicado por fotograma.
  Los auxiliares se escriben en `additional_assets/`, fuera del proyecto Android interno.
- Showcase: salto de animación (mismo personaje), salto de personaje, velocidad 1×/2×/4×,
  repetir voz y showcase audio-only de los 21 OGG con duración real.
- La Llorona: la hoja 09 fusionaba 14 figuras HURT HEAD en siete blobs. El slicer calcula el
  ancho esperado de pose, separa 14/14 y selecciona cuatro cuadros completos; el validador
  rechaza cuerpos HURT anormalmente anchos. Atlas final: 123 frames, 30 animaciones.
- Verificación release AGP 9.3/Gradle 9.5/JBR: AAB 438.52 MiB, `base` 459.16 MB comprimidos,
  margen 40.84 MB bajo Play. `additional_assets` tiene cero entradas en el bundle.

## Objetivo

Modo arcade estilo Street Fighter, 100% POW: quitar copyright, **todos los personajes y
mapas bloqueados**, se desbloquean **al derrotar rivales** en una escalera de dificultad
creciente, terminando en un **penúltimo jefe** y un **jefe final** (análogos a Urien/Gill).

## Roster — estado REAL (fuente: enum `SfFighterId` + flag `isAlpha`)

**① Copyright — QUITAR del enum:** `RYU`, `KEN`. Eliminar de `SfFighterId`,
`selectableFighters`, `sanitizeNetFighter`, `classicFightersUnlocked` y sus assets debug.

**② POW bien implementados (arte dedicado, SIN badge ALPHA) — 8. Forman la ESCALERA:**
Prankedy · El Señor de la Tienda · Paparazzi 1 · Paparazzi 5 · Rey Grupero · Policía CDMX ·
Policía CDMX (Hombre) · Paramédico Cruz Roja.

**③ POW ALPHA (poses de plantilla, badge "ALPHA") — 6.** Cambio de plan: `ESCOMBOY`,
`ESCOMGIRL`, `ROBOT` y `GRANADERO` ahora SÍ entran a la escalera como PLACEHOLDER (arte
pendiente). Siguen fuera: `LAZARO` y `PARAMEDICO` (ALPHA). Arte final:
`_ARCHIVO/GUIA_generacion_assets_SF.md` (archivada 2026-07-20; el flujo vigente es
`GUIA_regeneracion_sprites_croma.md` + `PROMPT_SOL56_TANDAS_NUEVAS.md`).

**④ Personajes a ELIMINAR del todo:** `REY_BROMAS` y `PEPE_REY` (skins ya COMENTADOS en
`features/map_exterior/ui/components/PlayerSkin.kt`) + su comentario en `SfModels.kt`.
⚠️ NO tocar la narrativa de `Mission2.kt`: ahí "Rey de las Bromas" es un TÍTULO de la
historia (Rey Grupero compite por él), no el personaje.

> ⚠️ El roster CRECERÁ ("estamos por implementar más personajes"). Todo data-driven.

## DECISIONES CERRADAS (2026-07-16, con el dueño)

1. **Persistencia: LOCAL (SharedPreferences)**, mínima. El proyecto solo tiene **Firebase
   Auth (Google Sign-In)** — NO hay Firestore/Realtime DB y no se guardan contraseñas. El
   progreso del arcade se guarda en el dispositivo como la campaña. (Nube = futuro opcional.)
2. **Inicio: desbloqueados SOLO los 3 estudiantes** `ESCOMBOY` / `ESCOMGIRL` / `ROBOT`; el
   jugador elige UNO de ellos. Todo lo demás con candado. (Prankedy ya NO es inicial: pasa a
   ser el JEFE FINAL.)
3. **El jugador ELIGE su peleador** de entre los DESBLOQUEADOS (estilo SF).
4. **Escalera FIJA de 10 peleas** (ver sección "ESCALERA" abajo). **Semifinal = Rey Grupero**,
   **FINAL = Prankedy**. (Cambió respecto a la versión anterior: antes Rey Grupero era el
   final; ahora es la semifinal y Prankedy es el jefe final.)
5. **Dificultad: HÍBRIDA.** El jugador elige una base (BASICA/NORMAL/AVANZADA, reusa el enum
   `SfCpuDifficulty` ya implementado) y la curva SUBE hacia los jefes.
6. **Al PERDER: retrocede 1 pelea** (repite la anterior y vuelve a avanzar). No reinicia toda
   la escalera.
7. **Mapas (6): desbloqueo LIGADO AL RIVAL** — vencer a un rival desbloquea SU mapa asociado.
   FIJOS: **Queso IPN** = mapa inicial ya desbloqueado / primera pelea; **CU UNAM** = mapa del
   **jefe final (Prankedy)**. Los intermedios se desbloquean según salga cada rival
   (aleatorio). Candado **🔒 en la esquina superior** de los mapas bloqueados en el selector
   (motiva a seguir jugando).
8. **Mapa "Ciudad Universitaria UNAM" = renombrar "Biblioteca UNAM"** (asset runtime
   `fondo_unam_biblioteca_cu_anim.webp`; el PNG fijo histórico quedó archivado). Está en CU.

## ESCALERA — 11 peleas (CERRADA con el dueño 2026-07-16)

Implementada en `domain/models/streetfighter/SfArcadeLadder.kt` (`build(player, rng)`).
El jugador elige uno de {ESCOMBOY, ESCOMGIRL, ROBOT}. Luego:

1-2. Los **otros 2 estudiantes** que NO elegiste — orden ALEATORIO.
3-5. **Paramédico Cruz Roja**, **El Señor de la Tienda**, **Paparazzi 1** — orden ALEATORIO.
6-9. **ORDEN FIJO:** Policía CDMX (Hombre) → Policía CDMX (Mujer=`POLICIA_CDMX`) →
     Granadero → Granadero. *(Son 4 peleas → escalera de 11.)*
10.  **SEMIFINAL: Rey Grupero.**
11.  **FINAL: Prankedy.**

**Placeholders (decisión del dueño):** los escalones 8 y 9 usan el ÚNICO `GRANADERO` ALPHA
como marcador de "Granadero Hombre" y "Granadero Mujer" hasta que el dueño cree esos
spreadsheets (ya en proceso). ESCOMBOY/ESCOMGIRL/ROBOT también son ALPHA (poses de
plantilla) pero jugables. Cuando lleguen los assets, se sustituyen SIN tocar la lógica.
Bien hechos ya: Prankedy, Señor Tienda, Paparazzi 1, Paparazzi 5, Rey Grupero, Policía CDMX,
Policía CDMX (Hombre), Paramédico Cruz Roja. (Paparazzi 5 NO está en la escalera.)

## Mapas — estado actual (`SfTheme.kt` → `fullBackgrounds`, 6)

ESCOM · Queso IPN · ESIME Azcapotzalco · CECyT 9 · CECyT 2 · Biblioteca UNAM→**CU UNAM**.
Los locked salen con candado; el fondo del muelle SF queda de fallback.

## Cambios técnicos (archivos)

- `domain/models/streetfighter/SfModels.kt`: quitar RYU/KEN del enum; limpiar comentario
  REY_BROMAS/PEPE.
- `features/map_exterior/ui/components/PlayerSkin.kt`: borrar el bloque comentado
  REY_BROMAS/PEPE_REY.
- `features/streetfighter/data/SfTheme.kt`: renombrar "Biblioteca UNAM" → "Ciudad
  Universitaria UNAM".
- **Nuevo** `data/repository/SfArcadeRepository.kt`: SharedPreferences `pow_sf_arcade` —
  set de peleadores desbloqueados, set de mapas desbloqueados, índice de escalón (progreso).
  API tipo: `unlockedFighters()`, `unlockFighter(id)`, `unlockedMaps()`, `unlockMap(file)`,
  `ladderStep()`/`setLadderStep(n)`, `reset()`. Modelo: `CampaignRepository` (07 §Historia).
- `features/streetfighter/viewmodel/StreetFighterState.kt`: modo arcade, escalón actual,
  rival actual, sets desbloqueados, base de dificultad, flags fin/ending.
- `features/streetfighter/viewmodel/StreetFighterViewModel.kt`: montar la escalera
  data-driven (pesos → rival aleatorio; jefes fijos), avanzar al ganar, retroceder 1 al
  perder, curva de dificultad híbrida, desbloquear peleador+mapa del rival vencido y
  persistir, `selectableFighters` = solo desbloqueados fuera del arcade. Arcade SOLO offline.
- `features/streetfighter/ui/StreetFighterScreen.kt`: entrada "ARCADE", selector con
  **candados** (peleadores y mapas, 🔒 esquina superior), secuencia de rivales, pantalla de
  continue/retroceso y ENDING al ganar la final.
- `res/values/strings.xml` + `res/values-en/strings.xml`: strings nuevos, paridad ES+EN.

## PENDIENTE de que lo dé el dueño

- **Assets:** SOLO falta **ROBOT** (sigue ALPHA `sf_template`+`RUNTIME/Robot.png`). ✅ Ya con arte
  dedicado y VERIFICADOS (2026-07-17, pull "Refactor SF 4-5/9"): ESCOMBOY, ESCOMGIRL,
  `POLICIA_GRANADERO_HOMBRE` y `POLICIA_GRANADERO_MUJER` (JSON+PNG propios, sin `isAlpha`). La
  escalera ya los usa (escalones 8-9 = los Granadero reales; el placeholder `GRANADERO` salió).
  ⚠️ Ojo: `escomboy/escomgirl/policiagranaderomujer.json` son byte-idénticos entre sí (comparten
  la caja de Rey Grupero) → revisar en dispositivo que hurt/hit-boxes calcen con cada sprite.
- **Asociación mapa ↔ rival** de los escalones 2-10 (qué mapa desbloquea cada rival). Fijos:
  escalón 1 = Queso IPN; escalón 11 (final, Prankedy) = CU UNAM. Intermedios hoy `null` (TBD).
- Detalle del ENDING (pantalla/texto/recompensa) al ganar la final.

## Estado de implementación

- [x] Quitar REY_BROMAS/PEPE (skins comentados) — hecho 2026-07-16 (PlayerSkin.kt + comentario SfModels.kt; Mission2 intacto).
- [x] Renombrar Biblioteca UNAM → CU UNAM — hecho (SfTheme.kt, nombre visible).
- [x] `SfArcadeRepository` (persistencia local, archivo aislado) — hecho (`data/repository/SfArcadeRepository.kt`; defaults: ESCOMBOY/ESCOMGIRL/ROBOT + mapa Queso). Falta CABLEARLO al VM.
- [x] `SfArcadeLadder` (modelo de 11 peleas, aislado) — hecho (`domain/models/streetfighter/SfArcadeLadder.kt`).
- [x] Quitar RYU/KEN del enum — hecho 2026-07-17. Fuera de `SfFighterId`, `selectableFighters`,
  `selectCharacter` (default) y `sanitizeNetFighter` (borrada); `classicFightersUnlocked` +
  import `SettingsRepository` eliminados. Parse de red defensivo (`valueOf`→null→PRANKEDY).
- [x] IA POR FASES — hecho 2026-07-17. Campo `cpuIntensity` 0f..1f: en el arcade sube 0→1 con el
  avance de la escalera (pelea 1 = 0, final = 1) y acelera la cadencia de decisión (hasta ~45%)
  + sube agresividad/bloqueo/poderes. En VS es 0 → comportamiento idéntico al de siempre.
- [x] Estado + VM — hecho 2026-07-17. `StreetFighterState` (campos arcade + enum `SfArcadeOutcome`).
  VM: `startArcade`/`startArcadeStep`/`arcadeContinue`/`arcadeRetry`/`arcadeExit`/`handleArcadeMatchEnd`
  + `arcadeDifficulty` (híbrida) + hook en `endRound`. `selectableFighters()` pasó a FUNCIÓN y
  devuelve solo DESBLOQUEADOS; `lockedFighters()`/`unlockedMaps()` para los candados.
- [x] Screen — hecho 2026-07-17. Botón ARCADE en el selector; setup (peleador desbloqueado +
  dificultad base); `ArcadeResultOverlay` (GANASTE/PERDISTE/CAMPEÓN) que reemplaza el menú de
  fin en arcade; fondo desde `arcadeMapFile`; candado 🔒 en `CharacterCard` y `StageCard`.
  + strings ES+EN (`sf_arcade_*`, `sf_back`).

## Añadidos 2026-07-17d (implementado)

- **La Llorona INTEGRADA:** pipeline corrido (19 hojas croma → `slice_sf_chroma_sheets.py` →
  `pack_sf_character.py lallorona LaLlorona`) → `IMAGES/LaLlorona.png` + `DATA/lallorona.json`
  (incluye proyectil de la hoja 12, que era JPG → sprite algo tosco; regenerar como PNG para
  nitidez). Enum `LA_LLORONA`. Escalera **15 peleas**: bloque 11-12 = {Charro, Llorona} azar.
- **Copyright SF fuera del HUD:** `hud.png`→`sf_hud_pow.png` (fuente+barra+KO+timer), `decals.png`
  →`sf_decals_pow.png`, `Ken.png`(fireball) y `kenstage.png` eliminados. Los 3 SF + 5 fuente
  `ChatGPT*.png` se movieron a `newSFAssets/_hud_src/`. Falta reemplazar sonidos (`hadouken.ogg`
  + golpes). Atlas armados por script (chroma-key + recorte + normalización).
- **Botón menú principal** (`MainMenuScreen.FeaturedStreetFighterButton`): destacado + animado
  (pulso, brillo dorado que barre, borde/sombra que laten), tag `◆ MODO COMBATE ◆`.
- **Menú de modos POW** al entrar (`SfModeMenuOverlay`): ARCADE (principal) / PRÁCTICA /
  **IA VS IA** / MULTIJUGADOR. Arcade = SOLO eliges peleador (dificultad FIJA, `startArcade(playerId)` sin base).
- **Dificultad arcade (2026-07-18g):** el jugador elige **Fácil / Medio / Difícil** al
  arrancar (`ArcadeDifficultyOverlay` → `startArcade(id, difficulty)`). Mapas = **hogar del
  rival** (`SfStageCatalog.homeStage` + `mapForRival`) + iluminación:
  Fácil→día, Medio→noche_1, Difícil→noche_2. IA base = elegida; jefes/final +1/+2 ordinal.
  `cpuIntensity` piso 0.20 → 1.0 en la final.
- **Tabla peleadór→mapa (dueño, 2026-07-18h):** ver **`SF_STAGES_MAPS_UNLOCK.md`**.
  REY_GRUPERO → FES Aragón; ESCOMGIRL → ESCOM; etc. Desbloquear peleadór desbloquea las 3
  luces de su mapa (`SfArcadeRepository.unlockFighter` → práctica + MP host BT/LAN/Render).
- **🆕 IA VS IA (2026-07-18):** CPU vs CPU a PESADILLA + `cpuIntensity = 1f`. Controles
  ocultos, solo "Salir". **⚠️ REGRESIÓN reportada:** a menudo dejan de pelear; ver
  `_ARCHIVO/PROMPT_traspaso_IA_CPU_2026-07-18.md` (prioridad siguiente sesión).
- **🆕 Fondos cap 2048 + thumbs (2026-07-18):** atlas ≤2048 (gama baja), frames ~480×270
  crop-to-fill, ~28 frames ping-pong, `_thumb.png` ~256 px. Tool `build_map_backgrounds.py`.
- **🆕 Selector de mapa (`SfStageSelectOverlay.kt`, 2026-07-18b):** preview **estático** en
  todas las tarjetas; **solo el focused** anima (un frame del atlas, no el filmstrip);
  confirmar con botón. Lógica separada del Canvas de combate.

## FIX 2026-07-17f · La Llorona: proyectil sin recortar (RESUELTO)

**Síntoma:** al lanzar su poder especial se veía un asset enorme sin recortar (solo ella).
**Causa raíz:** su hoja croma `_12` (SPECIAL HEAVY/PROJECTILE) llegó como **JPG**; el croma sucio
hacía que el slicer detectara un blob gigante y lo tomara como `proj-fly-1` (bbox 141×232 y 18.7%
de la celda, vs ~50-90 px y 1-4% en los demás personajes). Además los 2 sprites de la **columna 0**
del bloque de proyectiles salían **fusionados verticalmente** en un solo blob (x=258, 165×396).
**Fix aplicado** sobre `newSFAssets/LaLlorona/LaLlorona_12_SpecialHeavy_Extra.png`:
1. Snap del croma a verde puro (`g>90 && g>r*1.25 && g>b*1.25` → `#00FF00`) para limpiar el JPEG.
2. Enmascarar con croma la franja izquierda `x<245` (elemento espurio).
3. Cortar con croma la franja `y 528-582, x 245-440` para SEPARAR los dos sprites de la columna 0.
4. Re-slice de la hoja 12 + `pack_sf_character.py lallorona LaLlorona` + sacar `GEN/` de assets.
**Resultado:** `proj-fly-1/2` y `proj-hit-1/2/3` quedan en 52-56 px y 1.5-3.1% de la celda (igual
que Charro/Rey Grupero). ⚠️ Si se regenera la hoja 12, hacerlo en **PNG con croma limpio**.

## PENDIENTE — siguiente sesión

> ✅ Hechos (2026-07-17e…18h): arcade por defecto; bloqueados ???; rival visible; mapas×3;
> Fácil/Medio/Difícil; peleadór→mapa dueño; unlock peleadór→mapa; specials 21/21.
> Parches parciales de movimiento/IA (IDLE_TURN, stale approach) — **no suficientes**.

### 🔥 P0 — IA de pelea (traspaso Claude 4.8)

Ver **`_ARCHIVO/PROMPT_traspaso_IA_CPU_2026-07-18.md`**.

1. Jugador a veces **no se mueve** en arcade (input / estados / push).
2. **IA vs IA** deja de pelear (solo camina).
3. CPU **menos agresiva** que antes del anti-spam (upgrade real, no solo rates al azar).
4. Mantener: 1 fireball activa, sin muro de proyectiles, sin salir de stage.

### P1 — Combate “SF original” (grande, después de P0)

Más motions/combos/cancels/fluidez. **✅ Primer corte 2026-07-20:** chain/special cancels +
contador de combo + escalado de daño (ver §Cambios 2026-07-20). Lo que sigue depende de los
ASSETS nuevos (tandas 5–8 con Sol 5.6): dash/backdash, bloqueo/parry dedicado, ataques
agachado/aéreos, patada larga, agarres, super arts → cada uno necesitará estados nuevos en
`SfFighterState` + cajas en los JSON. Definir ventanas finas con el dueño en dispositivo.

## Añadidos 2026-07-17c

- **Escalera NUEVA (14 peleas; 15 con La Llorona)** en `SfArcadeLadder`: 1 Paramédico CR · 2-4
  {Paparazzi1, Paparazzi5, Señor Tienda} azar · 5-6 {Rey Grupero, Prankedy} azar · 7-10 policías
  (fijo) · 11 Charro Negro *(+ La Llorona azar cuando tenga assets → sube a 15)* · 12 Tzitzímime
  · 13 Yoalli Ehécatl · **14 La Presidenta (FINAL, PESADILLA)**. Los estudiantes YA NO son
  enemigos. `arcadeDifficulty`: final = PESADILLA, jefes (Tzitzímime/Yoalli) = AVANZADA, resto
  base +1 en la 2ª mitad.
  > ⚠️ **SUPERADO 2026-07-25 (ver entrada arriba):** el orden de los 2 jefes finales se INVIRTIÓ
  > (14 La Presidenta · **15 YOALLI FINAL**, que se metamorfosea en La Presidenta) y la IA se
  > rebalanceó (sube un escalón sobre la etiqueta + rampa más marcada). **El código manda.**
- **Copyright:** `fireballImage` (Ken.png) ELIMINADO (todos tienen `proj-*` propios);
  **`kenstage.png`** quitado de `imageFiles` + `drawScene` (⚠️ **borrar el archivo físico
  `assets/STREETFIGHTER/IMAGES/kenstage.png`**, no se pudo desde la sesión). Pendiente del dueño:
  reemplazar la fuente + 3 piezas del HUD (barra/KO/timer) y el `hadouken.ogg`; sonidos de golpe
  se quedan con nota. La Llorona: faltan assets empacados (hoja croma _12 = SPECIAL HEAVY).

## Añadidos 2026-07-17b

- **Dificultad `PESADILLA`** (4ª): combos casi constantes, esquiva, castiga; el FINAL del arcade
  (Prankedy) la usa (rampa `arcadeDifficulty`: final +2, semifinal/2ª mitad +1). Opción en el
  selector + strings `sf_diff_nightmare(_desc)` ES+EN.
- **Modo Desarrollador** (`devUnlockAll()` = `getDeveloperMode()`): desbloquea TODOS los
  personajes y mapas (sin candados) en selector y práctica.
- **Modos:** ARCADE = campaña con desbloqueos; el flujo peleador→rival→**dificultad**→mapa es
  PRÁCTICA y **NO desbloquea nada** (solo `handleArcadeMatchEnd` desbloquea, y solo en arcade).
- **Escalera 8-9** ya usa los Granadero reales (`POLICIA_GRANADERO_HOMBRE/MUJER`).
- **Preview del selector** más lento + anima IDLE + `walkForwards`.

## ⚠️ Implicaciones / límites del build actual (para la próxima sesión)

- **Roster libre (VS y online) ahora = SOLO desbloqueados.** Al inicio son los 3 estudiantes;
  el resto se gana en el arcade. Los NO participantes del arcade (Paparazzi 5, Lázaro,
  Paramédico ALPHA) hoy quedan sin vía de desbloqueo → invisibles en VS hasta sumarlos al
  esquema. Modo Desarrollador sigue añadiendo RYU/KEN.
- **Desbloqueo de MAPAS mínimo:** como la asociación mapa↔rival intermedia sigue pendiente,
  hoy solo se desbloquea Queso (default) y CU UNAM (al ganar la final). Definir el resto.
- **Verificado:** CRLF, llaves balanceadas en los 4 .kt, strings ES+EN con paridad, refs OK.
  Falta SOLO Rebuild + prueba en dispositivo.

## Protocolo al implementar (09)

Docs 07 (§HUELUM VS. GOYA) + este doc (marcar avance / borrarlo al terminar) + README
público raíz (EN **y** ES). Arcade es offline → NO toca red. Verificar con Read, balance de
llaves y CRLF. Listo para Rebuild.

## Fix 18l — IA compartida, aterrizaje y anti-bucle (2026-07-18)

- **Causa raíz del pegado/timeout:** `updateStageConstraints` limita `y` exactamente a
  `STAGE_FLOOR`, pero el handler de `JUMP_UP/FORWARD/BACKWARD` solo aterrizaba con `y > floor`.
  El peleador quedaba para siempre en `JUMP_*` aunque visualmente estuviera abajo. Ahora aterriza
  con `y >= floor && velocityY >= 0`, y los tres estados aéreos están cubiertos por `watchStuck`.
- `buildCpuInput` usa un solo motor en VS, Arcade, IA vs IA y Autoplay. `repairCpuFacing` se ejecuta
  para cualquier CPU antes de leer `forward/backward`; dificultad controla cadencia/defensa y
  `CpuStyle` solo sesga presión o poderes según el personaje.
- La colisión cuerpo a cuerpo continúa buscando BODY/LEGS si HEAD no traslapa. La CPU no intenta
  golpes cortos fuera de `CPU_MELEE_DIST`; tras pasividad fuerza acercamiento real.
- Variedad/justicia: memoria de 3 golpes, cooldown separado para special/bonus, defensa reactiva y
  ventana `COMBO_ESCAPE_MS` tras 3 impactos rápidos para impedir cadenas de poder sin salida.
- Auditoría: cada estancamiento y ronda decidida por tiempo es un problema explícito; el `.txt`
  incluye rondas por KO y por tiempo. Validación principal: **Autoplay everyone vs everyone**
  (18×17 = 306 combates) en emulador, seguida por las 9 campañas por dificultad.

## Cambios 2026-07-22 (Fable 5) — SUBTÍTULOS ENCENDIDOS + track EN + tutorial

- **Subtítulos de voz ACTIVOS** (`voiceSubtitlesEnabled = true`, VM). Fix del delimitador `|`:
  `sfHudSanitize` lo convertía en espacio y la Screen partía solo por palabras. Ahora
  `setVoiceSubtitle` sanea CADA tramo y re-une con `|`; el bloque "Subtítulo del special" de
  `StreetFighterScreen` dibuja **una línea por tramo** y ENCADENA el word-wrap ~24 chars dentro
  del tramo (techo de seguridad 9 líneas — la frase más larga, win de Policía CDMX Mujer, produce 9).
- **Track `en` de `voice_phrases.json` COMPLETO:** ~33 campos que seguían en español se
  tradujeron conservando tramos `|` y censuras (`c...`→`b...`); gritos/onomatopeyas
  (`Grrr`, `Kiaa`, `Jajaja`, `ZA-ZA`…) se quedan igual. Los `es` y `draft` NO se tocaron.
  Verificado: 64 `es` curados, 0 `en` con rasgos ES, 0 tramos desiguales, CRLF intacto.
- **Tutorial (`combos.json`, solo datos):** +`b_crouchchain` (puño bajo→patada baja; crouchPunch
  no se enseñaba en ningún básico) y +`b_meter` (el medidor sube al conectar Y al recibir;
  súper y FATALITY lo consumen ENTERO — verificado en el VM, rama SUPER_ART/FATALITY de
  changeState). El `_readme` ahora lista walkForward/run/blockHigh/fatality. La lección
  FATALITY ya existía (universal `fatality`, level 4) — el plan que la daba por faltante
  estaba desactualizado. **Pendiente (requiere motor):** bloqueo BAJO y parry BAJO no tienen
  `SfComboAction` ni mapeo en `stateForAction` → sin lección posible solo con datos.

## Cambios 2026-07-22b (Fable 5) — crash Llorona, carga en IO, STUN, metamorfosis, intro, tutorial

Plan ejecutado: `PROMPT_FABLE5_stun_crash_optimizacion.md`. **Sin compilar en la sesión**
(pendiente Rebuild + 6 modos, checklist en `_SESION_ACTUAL.md` §🧪).

- **Crash La Llorona (P0, blindaje estático):** su JSON no trae `longKick`/`overhead` (única
  peleadora ALPHA) → su pelea sumaba un **3er atlas a resolución completa** (~63 MB, EscomGirl)
  con `SfSharedSheets.sheetFor` lanzando `error()`/OOM SIN runCatching en la composición.
  Ahora: cada atlas va en `runCatching` (si falla se omite y `drawFighter` degrada a la primera
  hoja) y el ALPHA se decodifica SIEMPRE a media resolución con escala propia
  (`AlphaFallback.sheetScale`; es silueta negra — pérdida visual nula). Atlas/JSON verificados:
  228 frames, rects en rango, fondos islamunecas presentes. Falta logcat que confirme.
- **Carga de pelea a Dispatchers.IO (`SfFightAssets`):** atlas de peleadores + ALPHA + escaneo
  de alturas salen del hilo de UI y corren bajo el overlay CARGANDO (el Canvas no dibuja hasta
  tenerlos). La clave del `LaunchedEffect` es **`fightIds`, un Set con ambas identidades de la
  metamorfosis** → transformarse a media pelea ya NO recompone/redecodifica nada (antes el
  remember con `cpuId` re-decodificaba TODO al cambiar el id: la "trabada" al transformarse).
- **MAREO/STUN (dureza moderada, todos los modos):** `SfFighterState.STUN("stun")` — **al
  FINAL del enum** (viaja como `enum.name` con parse defensivo → retro-compatible) —,
  `SfFighter.dizzyMeter` (sube al RECIBIR con tope `DIZZY_HIT_CAP=25`, decae tras 1.5 s
  vía `applyMeterDecay`), lleno → STUN 2 s: congelado (`runStateHandler` ignora inputs),
  pose `stun-3` con la anim **"stun" SINTETIZADA en `SfFrameCatalog.parse`** (los 18 JSON +
  template traen frames stun-1/2/3 pero ninguna anim), estrellitas procedurales (Canvas,
  órbita elíptica 3×120°) y barra naranja/roja bajo la de súper. Golpear al mareado lo saca
  (STUN ∈ SF_HURT_STATES); al entrar el medidor se vacía (sin bucles). Online: solo el
  peleador LOCAL calcula mareo (el remoto se pisa por snapshot; su STUN llega por `state`;
  su `dizzyMeter` no viaja — deuda cosmética documentada).
- **Medidor de súper:** halo dorado + relleno pulsante (~8 Hz) al llenarse; decaimiento LENTO
  (4/s tras 4 s sin conectar, `superKeepMs` se refresca al conectar golpe/agarre/parry) —
  **la barra LLENA no decae** (la súper cargada no se pierde sola).
- **Metamorfosis entre rondas (decisión del dueño):** SOLO round 1 (`roundNumber == 1` en
  `tryPresidentaMetamorphosis`), al transformarse **VIDA LLENA** (antes 50%), y PERSISTE
  (`resetRound` copia `metamorphosed` y conserva el id — antes volvía a Presidenta con el
  flag perdido).
- **Intro del policía hombre:** `playSfSpecial` ya no permite que otra voz del mismo peleador
  corte un clip `_intro`, y mientras la intro suena las voces nuevas de ese peleador se saltan.
- **Navegación:** `lastLaunchedMode` en la Screen — al volver de una pelea se restaura el
  selector del MISMO modo (arcade/práctica/IA vs IA/hoja de combos; gauntlet/showcase → menú).
  Antes el `LaunchedEffect(inCharacterSelect)` forzaba SIEMPRE el selector de Arcade.
- **Tutorial:** etiquetas de `actionLabel` referidas a los CONTROLES ACTUALES (nombran botón
  X/Y/B/A/P/G/T/S y gesto de joystick; ⚠️ chipColor y sfButtonForLabel dependen de sus
  substrings), el botón del paso actual PULSA con aro amarillo (`SfTutorialButtonGlow` en
  `FighterXboxButtons`/`FighterNewMoveButtons`, deducido de la etiqueta con
  `sfButtonForLabel`), y el panel se INVIRTIÓ: chips de botones ARRIBA, título/pista ABAJO.

## Cambios 2026-07-22c (Fable 5) — Fase 1 del motor AUDITADA + Fase 2a/2b

- **Auditoría de la Fase 1 (Opus) APROBADA.** `validFrom` del VM previo a `Fase 1a` vs
  `SfStateMachine.VALID_FROM` comparadas COMO DATOS (parser + diff de conjuntos): 67 destinos,
  5 sub-listas y `knockdownStates` idénticos. `1b-1e` verificados espejo a espejo (daño base,
  chip, ATTACK_META ×21, bonus usable, física de tick, resolvedDamage con las mismas
  constantes). Alias vigentes en el VM, 0 copias residuales. Conteo real de tests: **32 nuevos
  + 3 previos = 35** (caracterización con literales, no tautologías).
- **Fase 2a — `SfAnimation` (nuevo objeto puro):** `frameIndex` (wrap a 0), `frameTimerMs`
  (delay×FRAME_TIME_MS), `shouldAdvance` (delay<=0 = FREEZE/TRANSITION no avanza) e
  `isCompleted` (terminador −1 O último frame — el fix 2026-07-18 de hojas sin −1, ahora
  documentado en su KDoc). El VM conserva sus tres funciones como envoltorios que pasan
  `animOf(f)` → mismos call sites, comportamiento idéntico. +8 tests (`SfAnimationTest`).
- **Fase 2b — `SfPhysics.clampToStage`:** espejo exacto de `clampFighterToStage` (rescate
  NaN/∞ → centro/piso, coerce X a `STAGE_X_MIN/MAX`, Y con tope de aire
  `STAGE_AIR_CEILING = 220f` ahora nombrado). `STAGE_X_MIN/MAX` se movieron del companion del
  VM a `SfConstants` (el companion conserva ALIAS → 5 usos internos intactos). +4 tests.
- ⚠️ **Validación pendiente del dueño** (sesión sin SDK): Rebuild + `testDebugUnitTest`
  (esperados **47**) + detekt (baseline 5) + jugar los 6 modos.
- **Siguiente:** Fase 2c (empuje de pushboxes/viewport de `updateStageConstraints` a SfPhysics
  con firma pura) → esqueleto `SfEngine` → Fase 3 (modos como estrategia). Las dos últimas
  requieren compilador a la mano (tocan audio/red/StateFlow).
