# CHECKPOINT 2026-07-04 + PROMPT para la SIGUIENTE SESIÓN (Opus 4.8 / otra cuenta)

> **Para qué:** retomar el trabajo EXACTAMENTE donde quedó esta sesión (Fable 5). Contiene
> (A) el checkpoint de TODO lo implementado hoy, (B) las advertencias/estado, y (C) el PROMPT
> listo para copiar/pegar con las tareas siguientes ya especificadas.

---

## A. CHECKPOINT — qué se implementó en esta sesión (2026-07-03 → 07-04)

Todo está documentado en detalle en los docs actualizados; aquí el índice:

1. **MISIÓN 2 · "El rumor"** (✅, `CAMPAIGN/02_MISSION_2.md`): 5 fases (esconderse de la policía /
   rumor con subtítulos / primer brote público con radio / plática con Prankedy (REY GRUPERO) /
   salón `escom_salon_m2` con LATA APESTOSA 🥫💨 y mochila 🎒). Archivos clave:
   `mission2/Mission2.kt`, `WorldMapMission2.kt`, overlay de subtítulos (`storyConvo*` en
   `WorldMapState` + `WorldMapScreenOverlays`), sala en `ZombieRoomCatalog`, evacuación en
   `ZombieAmbientNpcs`, persistencia `GameSaveData.mission2Phase`.
2. **RENOMBRE GLOBAL** `mission2*` → **`mission1Chase*`** (la persecución final ERA de la Misión 1):
   funciones/campos/constantes/cómic `MISSION1_CHASE_INTRO_ID`/ruta nav `story_mission1_chase`.
   Grep de `mission2Chase|pendingMission2Intro|MISSION2_` debe dar 0 en código (ver 09 §12).
3. **MISIÓN 3 · "Regreso a la ENCB"** (✅, `CAMPAIGN/03_MISSION_3.md`): viaje → cordón de 6
   granaderos con SIGILO (detección 21 m / 2.5 s = fallida) + 3 paparazzi → asalto interior (cadena
   ENCB sembrada con zombis vía `mission3Assault`, evidencia 🧪 en `encb_lab1`, auto-salida) →
   recompensa **`hasFirearm`** (modo RANGED con candado 🔒 en campaña hasta conseguirla). Archivos:
   `mission3/Mission3.kt`, `WorldMapMission3.kt`, navegación por `mission3EnterEncb`
   (WorldMapScreenOverlays) con histéresis de re-entrada.
4. **REGISTRO/SELECTOR DE MISIONES estilo Witcher** (✅): diálogo R7 ELIMINADO (la escolta encadena
   directo con el cómic + chase). Nuevo `Opciones → "Misiones"` en el MAPA GLOBAL:
   `MissionCatalog.missions` (+ `CampaignMissionInfo`, `missionIdForObjective`),
   `WorldMapMissionLog.kt` (`selectCampaignMission`/`unfollowActiveMission`/`missionLogStatus`/
   `markMissionCompleted`), UI `ui/components/MissionLogDialog.kt`, estado
   `showMissionLog`/`completedMissions` (persistida). Las misiones 2/3 SOLO corren si se siguen
   (`isMission2/3StoryActive` exige objetivo con su prefijo). M1 se marca completada al cumplir
   INGRESAR_ESCOM.
5. **Mochila desbloquea INVENTARIO** (✅): `ZombieGameState.inventoryUnlockedSlots` (1 → 4 al
   recogerla; sesiones futuras vía `mission2Phase>=DONE` desde AppNavGraph). ZombieHud dinámico.
6. **Vida universitaria + ANTI-ATASCO de NPCs ambientales** (✅, `ZombieAmbientNpcs.kt`): modos
   WANDER/MEETING/TALK/WALK_TOGETHER, burbujas con frases `amb_phrase_1..10` + `amb_phrase_bye`
   (ES+EN), decisiones de pareja DETERMINISTAS por `pairSeed` (regla: nunca escribir al partner).
   Anti-atasco: checkpoint `stuckX/Y/SinceMs` (sin avance >6 px por 1.6 s → nueva dirección,
   re-armable).
7. **CONTROL UNIFICADO Xbox** (✅): el diamante de conducción ya no es PS4; es el MISMO
   `ActionButton` A/B/X/Y (`VehicleActionButtonsController`; Y=salir/tp, A=gas, B=freno, X=freno
   de mano). `Ps4Button`/`Ps4ActionButtonsController` eliminados.
8. **FIX coches estacionados del lobby ESCOM** (✅, `ParkedCarsLayer.kt`): los sprites de coche son
   FRAMES DIRECCIONALES → el interior ahora pide el MISMO frame del ángulo que el exterior (antes
   frame 0 + `Modifier.rotate` = autos chuecos). NO reintroducir `.rotate(facing)`.
9. **REY_GRUPERO verificado** (✅ jugable, assets 3/8/8/6 OK); `PEPE_REY`/`REY_BROMAS` comentados.
10. **Placeholders emoji** (mientras no haya assets): lata 🥫+💨, mochila 🎒, evidencia 🧪.
    Assets deseados: `CAMPAIGN/ITEMS/stink_can.webp`, `prankedy_backpack.webp`,
    `INTERIORS/ESCOM/ESCOM_salon.webp` (el salón M2 reusa `ENCB_salon1.webp`).
11. **Tests**: `MissionCatalogTest` ganó 2 tests de la Misión 2 (orden/prefijo m2_ y radios 0).
12. **Docs sincronizados**: `CAMPAIGN/00..03`, `00_INDEX`, `04`, `05`, `07`, `09` (gotchas nuevos),
    `NPC_SPRITES_PIPELINE`, README público (EN+ES, Cambios Recientes).

## B. ADVERTENCIAS / ESTADO

- **NADA se ha compilado**: falta `Rebuild Project` en Android Studio. Si salen errores, arreglar
  y reportar (la sesión no tuvo compilador). Los archivos NUEVOS quedaron en LF (compila igual).
- Guardados VIEJOS compatibles (campos primitivos nuevos + coalesce de `completedMissions` NULL).
- Los granaderos de la M3 usan sprite de policía 👮 (fase exterior premade PENDIENTE).

---

## C. PROMPT PARA LA SIGUIENTE SESIÓN (copiar/pegar tal cual)

```
Lee la carpeta `README for IAS` (contexto COMPLETO del proyecto POW) y en especial
`CHECKPOINT_2026-07-04_siguiente_sesion.md` (estado actual). Sigue MVVM y TODAS las
convenciones/gotchas del archivo 09 (miembro vs extensión, CRLF, verificación con Read
—no bash— de archivos editados, protocolo de docs al terminar, strings ES+EN, no puedes
compilar: entrega listo para Rebuild).

TAREAS (en este orden):

1. "Elegir personaje" del MAPA GLOBAL solo en MODO DESARROLLADOR:
   - En `WorldMapScreen.kt` (menú Opciones), el ítem `wm_opt_change_skin` ("Elegir personaje",
     que abre el selector de skin del exterior) debe mostrarse SOLO con `developerMode`.
   - El de INTERIORES ("Elegir personaje" en ZombieGameScreen, primer ítem de su menú de
     Opciones) se queda como está.

2. Botón "Misiones" TAMBIÉN en INTERIORES:
   - Hoy el registro de misiones (`MissionLogDialog`) solo existe en el mapa global.
   - Añade el ítem "Misiones" (`R.string.wm_opt_missions`) al menú de Opciones de
     `ZombieGameScreen`. Recomendación de arquitectura: hospedar el diálogo a nivel
     Activity/AppNavGraph (mismo patrón que `SaveSlotsDialog`/`showSaveDialog`), pasándole
     el `worldMapViewModel` (Activity-scoped, ya accesible en AppNavGraph); así un solo
     MissionLogDialog sirve para mapa e interiores sin acoplar el VM de interiores al del mundo.
   - OJO: si desde un interior sigues una misión de exterior, al salir al mapa debe verse el 🎯.

3. REJUGAR misiones completadas (desde el registro):
   - En `MissionLogDialog`, las misiones ✔ COMPLETADAS ganan botón "REJUGAR".
   - Rejugar re-arranca ESA misión (startMission2Story/startMission3Story; para la M1 decidir el
     punto de re-entrada: sugerido `setCampaignObjective(MissionCatalog.first)` + setStorySpawn
     al inicio de campaña SOLO en modo rejugar).
   - REGLA DURA: rejugar NO debe afectar el progreso guardado: `completedMissions` NO se
     des-marca (markMissionCompleted ya es idempotente), `hasFirearm`/slots NO se pierden, y al
     terminar la repetición no debe duplicar recompensas ni romper `mission2Phase/mission3Phase`
     persistidos (sugerencia: campo transitorio `replayingMissionId` en el VM, NO persistido; al
     terminar el replay restaurar la fase a DONE). Documentar el diseño en 09 + CAMPAIGN/00.

4. MODO DESARROLLADOR en el registro de misiones:
   - Con `developerMode` activo: TODAS las misiones son seleccionables aunque estén 🔒
     bloqueadas (saltarse `requiresMissionId`), p. ej. poder jugar la "misión 99" directo.
   - Añadir botón "TP al objetivo" (solo dev) por misión: teletransporta CERCA del objetivo
     actual/primero de esa misión — usa la extensión existente `teleportTo(lat, lon)`
     (WorldMapTeleport.kt) con las coords del `CampaignObjective` (targetLat/Lon con un offset
     de ~30-50 m para no caer encima del trigger).
   - `developerMode` se lee como en WorldMapScreen (`SettingsRepository(context).getDeveloperMode()`
     con remember al entrar; ver 09).

AL TERMINAR: aplica el PROTOCOLO DE DOCS del 09 (docs 00-09 + CAMPAIGN + README público EN+ES),
strings nuevos en values/ y values-en/ con paridad, y dime qué probar en el dispositivo.
```
