# Propuesta de reorganización de `app/src/main/assets`

> ## ✅ EJECUTADO Y VERIFICADO (2026-06-20) — reorg prácticamente COMPLETA
> Verificación global: **0 referencias a prefijos viejos** en código; rutas nuevas existen en disco; CRLF intacto.
> - **Fase 0 (huérfanos):** a `app/src/main/_ORPHAN_ASSETS/` (no van al APK): `INTERIORES/`, `Police/policeV2_run/`, `assetsNPC/Police/`.
> - **AUDIO:** `sonidos`→`AUDIO`, `AudiosViñetas`→`AUDIO/COMIC`, `instrumentalfondo`→`AUDIO/BGM`.
> - **TRANSIT:** `metro_cdmx`→`TRANSIT/METRO`, `metrobusCDMX`→`TRANSIT/METROBUS` (incl. icon/inside/metro1-2/map/video/waypoints).
> - **SPRITES/:** `MAIN`→`SPRITES/PLAYER`, `assetsNPC`→`SPRITES/NPC`, `Police/police_run`→`SPRITES/POLICE`,
>   `VEHICLES`→`SPRITES/VEHICLES`, `ZOMBIES_MOD/z_walk`→`SPRITES/ZOMBIE` (+`zombie_hand.webp`), `collectibles`→`SPRITES/COLLECTIBLES`.
> - **INTERIORS:** `ZOMBIES_MOD/interiors`→`INTERIORS/ESCOM_APOCALYPSE`.
> - **BUILDINGS:** `ZOMBIES_MOD/BUILDINGS_Z/building_escom_zombie.webp`→`BUILDINGS/`.
> - **VIDEO:** `ZOMBIES_MOD/load_zombie_mod.mp4`→`VIDEO/`.
> - **CONFIG:** `buildings_catalog.json`, `default_landmarks.json`, `exterior_collisions.json`, `navgraphs/`→`CONFIG/`.
>   Catálogos JSON verificados: solo referencian BUILDINGS/DOORS/PLACES (no movidos) → sin cambios.
>
> ## ⏳ PENDIENTE (menor)
> - **Iconos** `ic_npc_car.svg`/`ic_npc_person.svg`: se DEJARON en la raíz. Riesgo: `NpcType.drawable`
>   se consume en varios renderers y el `.svg` se concatena en JS (Leaflet). Mover requiere tocar todos los consumidores.
> - **`collision_matrices.json` / `waypoints.json`**: se quedan en la raíz (comparten `FILE_NAME` con la copia de usuario en filesDir).
> - **Carpetas vacías** `ZOMBIES_MOD/` y `Police/`: el sandbox no pudo borrarlas (permisos) → bórralas en Android Studio.
> - **Nombres de archivo** con acentos/espacios/typos (`polocia2.mp3`, `botella cayendo.mp3`, `Baño_*`): limpieza de archivos individuales = pase futuro.
> ⚠️ **Las rutas de assets fallan en RUNTIME, no en compilación** → prueba la app (metro/metrobús, sprites jugador/NPC/policía/zombi, coleccionables, apocalipsis, audio, cómic).
>
> ---
>
> **Estado original: PROPUESTA.** Confírmame el alcance y la ejecuto por fases.
> ⚠️ Cada ruta de asset es un **string en el código/JSON**. Renombrar/mover una carpeta
> obliga a actualizar TODAS sus referencias. No puedo compilar, así que se hará en
> **lotes verificables** (grep de referencias → mover → actualizar strings → verificar).

## 1. Lo que encontré (confirmado con evidencia)

**A. Carpetas DUPLICADAS / huérfanas (candidatas a BORRAR):**
- **`INTERIORES/` (español) está huérfana.** `INTERIORS/` (inglés) es la viva (3 refs en código).
  El interior de la FES se carga de `BUILDINGS/FES_Ar/FES_Arg_int.webp` (ver `EscomBuildings.kt:79`,
  `ZombieRoomCatalog.kt:144`), **no** de `INTERIORES/FES_Ar/`. `INTERIORES/CentralAu/` (Autobuses, Baño)
  tiene **0 referencias**. → `INTERIORES/` entera parece basura/duplicado.
- **Policía triplicada:** `Police/police_run/` (6 frames, **la viva** — `PoliceNpcSpriteManager.RUN_FOLDER`),
  `Police/policeV2_run/` (25 frames, **0 refs**), `assetsNPC/Police/` (6 frames + `police_shot.webp`, **0 refs**).
- **PNG "fuente" enviados junto al `.webp`** (inflan el APK): 36 `.png`, p. ej. `INTERIORES/*/*_.png`,
  `ZOMBIES_MOD/z_walk/Z1.png..Z5D.png` (8 sueltos junto a `z_walk_*.webp`).

**B. Nombres heredados / inconsistentes:**
- **`ZOMBIES_MOD/`** = nombre viejo ("mod zombi"). Hoy es el **apocalipsis de Interiores**
  (`za_*.webp` = variantes apocalípticas de las salas ESCOM; `building_escom_zombie.webp`;
  `z_walk_*` sprites de zombi; `zombie_hand.webp`; `load_zombie_mod.mp4`). 7 refs.
- **`assetsNPC/`** = prefijo "assets" redundante (ya estamos en `assets/`). Vivo: `Prankedy/`, `hair/`,
  `npc_walk_1/` (6 refs), `other_player/` (2 refs).
- **`MAIN/`** = en realidad son sprites del **jugador** (escomboy/girl, lazaro, robot). Nombre vago.

**C. Convenciones mezcladas:**
- Mayúsculas (`BUILDINGS`,`VEHICLES`,`DOORS`,`STORY`,`CAMPAIGN`,`PLACES`,`MAIN`) vs camelCase
  (`assetsNPC`,`metrobusCDMX`) vs snake (`metro_cdmx`).
- Español vs inglés: `INTERIORES` vs `INTERIORS`, `sonidos/`, `sonidos/AudiosViñetas`,
  `sonidos/instrumentalfondo`, `Baño_izq.webp`. (Acentos/ñ en nombres de archivo = riesgo en algunos build tools.)
- `metro_cdmx` vs `metrobusCDMX` (mismo dominio, distinto estilo).

## 2. Convención propuesta
Carpetas de assets en **`MAYUSCULAS_SNAKE`** (sigue a la mayoría: BUILDINGS, VEHICLES…), nombres de
archivo en **`snake_case` ASCII** (sin acentos/ñ). Todo en **inglés** para uniformar.

## 3. Estructura destino propuesta

```
assets/
  CONFIG/            ← (NUEVO) buildings_catalog.json, default_landmarks.json,
                       collision_matrices.json, exterior_collisions.json, waypoints.json
  SPRITES/
    PLAYER/          ← (era MAIN/) escomboy*, escomgirl*, lazaro*, robot*
    NPC/             ← (era assetsNPC/) npc_walk_1, hair, other_player, Prankedy
    POLICE/          ← (era Police/police_run/) + police_shot.webp
    ZOMBIE/          ← (era ZOMBIES_MOD/z_walk/) solo los z_walk_*.webp
    VEHICLES/        ← (era VEHICLES/)
    ICONS/           ← (NUEVO) ic_npc_car.svg, ic_npc_person.svg
  BUILDINGS/         ← IPN, FES_Ar, UAM_Azc, CENTRAL, BAR, ESTADIO_AZT
                       + building_escom_zombie.webp (de ZOMBIES_MOD/BUILDINGS_Z/)
  INTERIORS/
    ESCOM/           ← z_*.webp (salas normales; ya está)
    ESCOM_APOCALYPSE/← za_*.webp (era ZOMBIES_MOD/interiors/)
    ENCB/            ← (ya está)
    FES/             ← (a decidir: hoy el fondo FES vive en BUILDINGS/FES_Ar/)
  DOORS/  PLACES/  STORY/  CAMPAIGN/  COLLECTIBLES/
  TRANSIT/
    METRO/           ← (era metro_cdmx/)
    METROBUS/        ← (era metrobusCDMX/)
  AUDIO/             ← (era sonidos/)
    COMIC/           ← (era AudiosViñetas/)
    BGM/             ← (era instrumentalfondo/)
  VIDEO/             ← (NUEVO) load_zombie_mod.mp4, zombie_hand.webp, etc.
```

## 4. Plan por fases (de menor a mayor riesgo)

**Fase 0 — Borrar huérfanos (destructivo; requiere tu OK explícito):**
`INTERIORES/`, `Police/policeV2_run/`, `assetsNPC/Police/`, y los `.png` que tengan gemelo `.webp`.
*(Antes de borrar verifico 0 referencias de cada uno.)*

**Fase 1 — Renombres de bajo riesgo (pocas refs):** `metro_cdmx`→`TRANSIT/METRO`,
`metrobusCDMX`→`TRANSIT/METROBUS`, `sonidos`→`AUDIO` (+ subcarpetas), agrupar JSON en `CONFIG/`,
iconos sueltos a `SPRITES/ICONS/`.

**Fase 2 — Renombres semánticos:** `ZOMBIES_MOD`→ repartir (interiors→`INTERIORS/ESCOM_APOCALYPSE`,
z_walk→`SPRITES/ZOMBIE`, building→`BUILDINGS`, video→`VIDEO`), `assetsNPC`→`SPRITES/NPC`,
`MAIN`→`SPRITES/PLAYER`, `Police`→`SPRITES/POLICE`.

**Fase 3 — Unificar `INTERIORS`** y mover `VEHICLES`/`BUILDINGS` bajo el esquema final.

Cada fase: actualizo los strings de ruta en `.kt` y `.json`, conservo CRLF, y verifico que no
queden referencias a la ruta vieja antes de pasar a la siguiente.

## 5. Archivos de código que tocaré (referencias de rutas detectadas)
`PoliceNpcSpriteManager`, `PrankedySpriteManager`, `CharacterSpriteManager`/`PlayerSkin`,
`NpcAiManager` (npc_walk_1/hair), `EscomBuildings`, `ZombieRoomCatalog`, `SchoolCatalog`,
`SoundManager` (sonidos), catálogos JSON (`buildings_catalog.json`, `default_landmarks.json`, etc.),
y los managers de metro/metrobús. (Lista exacta por archivo en cada fase.)
