# Nuevo personaje "Estudiante" + arreglos de combate, zoom, estacionamiento y objetivos ESCOM

## Resumen
Agrega la 4ª skin jugable (**Estudiante**, `escomboy`) y agrupa varios arreglos de
jugabilidad del mapa exterior y de la campaña del Modo Historia (combate cuerpo a cuerpo,
zoom al jugador, autos del estacionamiento, Prankedy, objetivos dentro de la ESCOM y la
mano del modo zombi). Todo respeta MVVM (estado inmutable, las Views solo observan) y queda
listo para **Rebuild Project** (no se compiló).

## Cambios

### Personaje
- **Nueva skin `escomboy` ("Estudiante")** en `PlayerSkin` — carpetas `MAIN/escomboy{Idle|Walk|Run|Special}/`, frames 16/25/16/16, `comicSuffix = "Boy"` (cae al panel por defecto/hombre mientras no existan `IntroPOW*Boy.webp`). Aparece sola en el selector "Cambiar Skin" y se guarda/restaura por `.name`.
- **Tamaño corregido** — su personaje ocupa ~41 % del lienzo 256² (vs ~0.6–0.94 del resto) y se veía pequeño, sobre todo al correr. Se añadió `renderScale: Float = 1f` a `PlayerSkin`, aplicado en el dimensionado a pie de `PlayerCharacter`; `escomboy = 1.8f`.

### Combate (mapa abierto)
- **Alcance del golpe cuerpo a cuerpo** `WorldMapViewModel.ATTACK_RADIUS`: `0.00022` (~24 m) → `0.00008` (~9 m). Hay que estar pegado al enemigo para acertar.

### Cámara
- **"Hacer zoom en el jugador" ya no rebota** — `zoomToPlayer()` ahora fija también `targetZoomLevel = 22.0`; antes `updateAutoZoom()` arrastraba el zoom de vuelta al valor anterior.

### Estacionamiento
- **Recarga al salir del interior de la ESCOM** — un lote vacío-pero-poblado (`parkedAlive == 0` aún marcado en `populatedLandmarks`) se repuebla de inmediato, saltándose `LANDMARK_REPOPULATE_COOLDOWN_MS` (`NpcAiManager`).
- **Ocultos a zoom bajo** — los autos estacionados (`navState == PARKED`) no se dibujan con `uiState.zoomLevel < 18.5` en `NativeOsmMap` (se veían apretados/raros).

### Campaña / Modo Historia
- **Prankedy deja de acompañarte al entrar a la ESCOM** — en `handleInteraction` (puerta ESCOM) se desactiva el acompañante (fase `HIRED`), así no te sigue al volver al mapa (Misión 1 terminada).
- **Objetivos dentro del interior de la ESCOM** — el `ObjectivesWidget` ahora se muestra en el lobby; tras la Misión 1 (`INGRESAR_ESCOM` cumplida) aparece el nuevo objetivo **"Busca pistas en la ESCOM"** (`MissionCatalog.BUSCAR_PISTAS_ESCOM`, pasado a `ZombieGameScreen` como `interiorObjective`). El objetivo exterior se queda en "Ingresa a la ESCOM, Cumplido".

### Modo Desarrollador
- **La mano del modo zombi** del lobby de la ESCOM solo se muestra con Modo Desarrollador activado (Interfaz).

## Archivos tocados
- `features/map_exterior/ui/components/PlayerSkin.kt` — entrada `escomboy` + campo `renderScale`.
- `features/map_exterior/ui/components/PlayerCharacter.kt` — aplica `skin.renderScale`.
- `features/map_exterior/viewmodel/WorldMapViewModel.kt` — `ATTACK_RADIUS`, `zoomToPlayer()`, desactivación de Prankedy al entrar a la ESCOM.
- `features/map_exterior/ui/NativeOsmMap.kt` — ocultar autos `PARKED` a zoom bajo.
- `domain/models/ai/NpcAiManager.kt` — repoblado inmediato del lote vacío-pero-poblado.
- `domain/models/CampaignMission.kt` — nuevo objetivo `BUSCAR_PISTAS_ESCOM`.
- `features/interiores/zombies/ui/ZombieGameScreen.kt` — param `interiorObjective`, render del widget, mano del modo zombi gateada a Modo Desarrollador.
- `MainActivity.kt` — calcula y pasa `interiorObjective` al entrar al lobby de la ESCOM tras la Misión 1.
- `README for IAS/README.md` (EN+ES), `04_MAP_EXTERIOR.md`, `09_CONVENTIONS_GOTCHAS.md` — protocolo de docs.

## Notas
- **Gotcha miembro vs extensión:** el game loop vivo es el **miembro** de `WorldMapViewModel.kt`; `WorldMapGameLoop.kt` está muerto. Se editaron los miembros.
- Sin assets `IntroPOW*Boy.webp`: el cómic de `escomboy` usa los paneles por defecto (hombre). Si se quieren propios, crear `IntroPOW9Boy/10Boy/11Boy/15Boy.webp`.
- Pendiente de verificar en Android Studio (no se pudo compilar aquí).

## Cómo probar
1. Selector "Cambiar Skin": elegir **Estudiante**; verificar tamaño a pie/al correr.
2. Golpear policías/NPCs: solo conecta de cerca (~9 m).
3. "Hacer zoom en el jugador" tras un zoom out: el zoom se queda en 22.
4. Entrar y salir del interior de la ESCOM: los autos del estacionamiento reaparecen.
5. Alejar el zoom (<18.5): los autos estacionados desaparecen.
6. Misión 1 → entrar a la ESCOM: Prankedy ya no te sigue al volver; dentro se ve "Busca pistas en la ESCOM".
7. Mano del modo zombi: visible solo con Modo Desarrollador (Interfaz) activado.
