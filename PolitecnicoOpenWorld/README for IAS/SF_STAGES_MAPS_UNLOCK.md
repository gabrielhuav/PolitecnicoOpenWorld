# Mapas, peleadór hogar y desbloqueos (HUELUM VS. GOYA)

> **Estado 2026-07-18h.** Fuente de verdad del código:
> - Escenarios: `domain/models/streetfighter/SfStageCatalog.kt`
> - Escalera: `SfArcadeLadder.kt` (18 peleadors dedicados; **no** Lázaro/alpha)
> - Persistencia: `data/repository/SfArcadeRepository.kt`
> - Selector: `ui/SfStageSelectOverlay.kt` (solo desbloqueados, salvo Modo Dev)

---

## 1. Mapas base (16 lugares × 3 luces = 48 fondos)

| # | Nombre | Día (archivo) |
|---|--------|----------------|
| 1 | ESCOM | `fondo_escom_anim.png` |
| 2 | Queso IPN | `fondo_queso_ipn_anim.png` |
| 3 | ESIME Azcapotzalco | `fondo_esime_azc_anim.png` |
| 4 | CECyT 9 | `fondo_cecyt_9_anim.png` |
| 5 | CECyT 2 | `fondo_cecyt_2_anim.png` |
| 6 | CU UNAM | `fondo_unam_biblioteca_cu_anim.png` |
| 7 | FES Acatlán | `fondo_fes_acatlan_anim.png` |
| 8 | FES Aragón | `fondo_fes_aragon_anim.png` |
| 9 | UAM Azcapotzalco | `fondo_uam_azcapo_anim.png` |
| 10 | UAM Cuajimalpa | `fondo_uam_cuajimalpa_anim.png` |
| 11 | Isla de las Muñecas | `fondo_islamunecas_anim.png` |
| 12 | Mictlán | `fondo_mictlan_anim.png` |
| 13 | Campos de Agave Jalisco | `fondo_campos_agave_jalisco_anim.png` |
| 14 | Facultad de Medicina | `fondo_facultad_medicina_anim.png` |
| 15 | Pirámide del Sol | `fondo_piramidesol_anim.png` |
| 16 | Zócalo | `fondo_zocalo_anim.png` |

Variantes: `_noche_1_anim` = noche · `_noche_2_anim` = apocalipsis.

**Arcade:** Fácil → día · Medio → noche · Difícil → apocalipsis  
(`SfStageCatalog.lightingForArcadeDifficulty` + `mapForRival`).

---

## 2. Asignación peleadór → mapa hogar (dueño, definitiva)

Solo peleadors **dedicados del arcade** (`ALL_PARTICIPANTS`).  
`homeStage()` en `SfStageCatalog.kt`.

| Peleador | Mapa hogar |
|----------|------------|
| ESCOMGIRL | ESCOM |
| ESCOMBOY | CECyT 9 |
| ROBOT | CECyT 2 |
| PRANKEDY | ESIME Azcapotzalco |
| REY_GRUPERO | **FES Aragón** |
| SENOR_TIENDA | Queso IPN |
| PAPARAZZI_1 | CU UNAM |
| PAPARAZZI_5 | UAM Azcapotzalco |
| PARAMEDICO_CRUZ_ROJA | Facultad de Medicina |
| CHARRO_NEGRO | Campos de Agave Jalisco |
| LA_LLORONA | Isla de las Muñecas |
| LA_TZITZIMIME | Pirámide del Sol |
| YOALLI_EHECATL | Mictlán |
| POLICIA_CDMX_HOMBRE | FES Acatlán |
| POLICIA_CDMX (mujer) | UAM Cuajimalpa |
| POLICIA_GRANADERO_HOMBRE | Zócalo |
| POLICIA_GRANADERO_MUJER | CU UNAM *(comparte con Paparazzi 1)* |
| LA_PRESIDENTA | Zócalo *(comparte con Granadero H)* |

**Compartidos:** CU UNAM (2), Zócalo (2). El resto 1:1.  
**Los 16 mapas tienen al menos un hogar.**

### NO arcade / incompletos (no asignar mapa “oficial”)

| Id | Estado |
|----|--------|
| LAZARO | `isAlpha` + `sharedSet` mundo; **no** en `ALL_PARTICIPANTS` |
| GRANADERO | alpha shared (usar `POLICIA_GRANADERO_*`) |
| PARAMEDICO | alpha shared (usar `PARAMEDICO_CRUZ_ROJA`) |

---

## 3. Desbloqueos (guardado local)

`SfArcadeRepository` (SharedPreferences `pow_sf_arcade`):

| Clave | Contenido |
|-------|-----------|
| `UNLOCKED_FIGHTERS` | set de `SfFighterId.name` |
| `UNLOCKED_MAPS` | set de archivos `fondo_*.png` (las 3 luces por escenario) |
| `LADDER_STEP` | progreso escalera |
| `ARCADE_SESSION_JSON` | pelea a medias (pause/minimize) |

**Reglas:**
1. Al **desbloquear peleadór** (`unlockFighter`) → se desbloquean **día+noche+apocalipsis** de su `homeStage`.
2. Al ganar pelea arcade → `unlockFighter(rival)` + `unlockMap(mapFile de la pelea)`.
3. Lectura de `unlockedMaps()` **migra** peleadors viejos → sus mapas (lazy).
4. Starters: ESCOMBOY/ESCOMGIRL/ROBOT + mapas ESCOM (familia completa).

### Dónde se usan los mapas desbloqueados

| Modo | Selector de mapa |
|------|------------------|
| Arcade | Automático (hogar del **rival** + luz por dificultad). No eliges mapa. |
| Práctica | Sí: solo desbloqueados (🔒 resto). Azar = solo desbloqueados. |
| IA vs IA | Azar entre desbloqueados (sin paso de mapa). |
| Multiplayer host (Render WS / BT / LAN) | Sí: solo desbloqueados; azar filtrado. |
| Modo Desarrollador | Todos (`unlockedMaps = null`). |

---

## 4. Escalera arcade (15 peleas)

Ver `SfArcadeLadder.build`:

1 Paramédico CR · 2–4 {Pap1, Pap5, Tienda} · 5–6 {Rey, Prankedy} · 7–10 policías ·  
11–12 {Charro, Llorona} · 13 Tzitzímime · 14 Yoalli · 15 Presidenta.

Flujo UI: **peleador → Fácil/Medio/Difícil** (`ArcadeDifficultyOverlay`) → pelea.

---

*Actualizado 2026-07-18h — asignación dueño + REY_GRUPERO → FES Aragón.*
