# CAMPAIGN · 00 · Visión de la Campaña / Campaign Overview

> **ES:** Esta carpeta `CAMPAIGN/` documenta **de qué trata la campaña (Modo Historia)** de *Politécnico
> Open World (POW)*: la fantasía central, su lógica interna y la historia misión por misión. Es el
> "guion" del juego; los detalles de IMPLEMENTACIÓN (clases, estado, archivos) viven en los docs `00–09`
> (sobre todo `07_OTHER_FEATURES.md` = campaña, `05_ZOMBIE_MINIGAME.md` = interiores, `03_DOMAIN_MODELS.md`
> = Prankedy). Aquí mandamos el QUÉ y el PORQUÉ narrativo; allá, el CÓMO técnico.
>
> **EN:** This `CAMPAIGN/` folder documents **what the Story Mode campaign is about**: the core fantasy,
> its internal logic and the story mission by mission. It is the game's "script"; implementation details
> live in `00–09` (mainly `07` campaign, `05` interiors, `03` Prankedy).

---

## 1. La fantasía central / Core fantasy

**POW es una SIMULACIÓN de cómo ocurriría una infección zombi**, jugada como un **mundo abierto estilo
GTA sobre mapas reales** (Politécnico / Zacatenco / CDMX). El mundo abierto es el **sandbox donde la
infección sucede** — es el "extra" sobre el que se monta la simulación, no el fin en sí mismo.

> **Regla rectora (no negociable):** **TODO debe ser lógico DENTRO de la fantasía.** Nada de elementos
> arbitrarios o sin sentido. Cada mecánica, NPC, misión y objeto debe tener una razón diegética: si algo
> pasa, es porque en una infección real pasaría así.

El juego **NO** es "unas misiones de zombis al principio y luego otra cosa". Los zombis son el **eje**:

- **Mundo abierto (GTA):** la ciudad **antes/durante** el brote — conducir, peatones, policía, nivel de
  búsqueda (*wanted level*). Es el escenario vivo donde se siente la infección.
- **Campaña (Modo Historia):** la historia del **paciente cero** — cómo empieza y escala el brote.
- **Interiores (minijuego zombi):** la infección **extendiéndose edificio por edificio** (supervivencia,
  combate, hordas).

---

## 2. La lógica unificadora / Unifying logic

Una "simulación de infección" da una **espina de escalada** automática y creíble: a más infección, más
respuesta de las autoridades. Esa escalada es el hilo conductor de la campaña y **mapea directamente sobre
los 9 NPCs ya recortados** (ver `tools/PROMPT_sprites_chatgpt.md` + `slice_new_character_sprites.py`):

| Etapa de la infección | Respuesta lógica | NPC(s) disponibles |
|---|---|---|
| Incidente aislado / rumor | Policía local + prensa husmeando | `PoliciaCDMX`, `PaparazziN1`, `PaparazziN5` |
| Brotes públicos / violencia | Refuerzos, contención | `PoliciaCDMX` (más), `Granaderos` (antimotines) |
| Heridos / contagio masivo | Emergencias médicas | `Paramedico` |
| Colapso | Fuerza mayor *(futuro)* | *(ejército — pendiente)* |
| Historia / origen | Personajes de la trama | `PrankedyPlayable`, `ReyBromas`, `PepeRey`, `SenorTienda` |

**El origen:** Prankedy, haciendo una broma en la **ENCB**, provoca por accidente un **compuesto
infeccioso capaz de penetrar el látex** (los guantes de laboratorio). Ese es el paciente cero / foco del
brote. Toda la campaña parte de ahí.

> **Reglas de contagio a FIJAR (north-star, mantener consistentes en todo el juego):** vector de
> transmisión (mordida/contacto), si hay incubación, y si el **jugador** puede infectarse. Una vez
> definidas, deben respetarse en mundo abierto e interiores por igual (coherencia = la regla rectora).

---

## 3. Estructura / Structure

Desde el menú principal hay dos entradas (ver `07_OTHER_FEATURES.md`):

- **MUNDO LIBRE** (`menu_start_game`) — sandbox open world, **sin campaña** (`inCampaign=false`), spawn por
  defecto en ESCOM. Para experimentar la ciudad/infección libremente.
- **MODO HISTORIA** (`menu_load_game` → ruta `story_mode`) — la **campaña** narrada y guionizada
  (`inCampaign=true`), **offline/local** (no usa el servidor multijugador). Guardado completo en JSON con
  5 slots (`SaveGameRepository`).

### Cómo se cuenta la historia / Storytelling tools

1. **Cómics** (`StoryComicCatalog` + `StoryIntroScreen`): paneles horizontales en `assets/STORY/INTRO/`.
   Reutilizables por `sequenceId` (intro, outro, llegadas). Es la vía **barata** para narrativa pesada
   (diálogos, cinemáticas) — preferirla para cutscenes.
2. **Salas jugables** (motor de interiores `ZombieInteriorViewModel`, ver `05`): exploración, puzzles,
   combate, banner de objetivo.
3. **Mapa global** (`WorldMapViewModel`, ver `04`): escoltas, persecuciones, objetivos por coordenadas,
   línea GPS de campaña.

---

## 4. Índice de misiones / Mission index

| # | Archivo | Título | Estado |
|---|---|---|---|
| 1 | `01_MISSION_1.md` | De la ESCOM a la ENCB · escolta de Prankedy · ingreso a la ESCOM | ✅ Implementada |
| 2 | `02_MISSION_2.md` *(pendiente)* | Esconderse en la ESCOM, rumor zombi, primer brote público, la mochila de Prankedy | 🟡 En diseño |

> **⚠️ Nota de nomenclatura:** en el CÓDIGO, los 4 objetivos de la Misión 1 viven todos en
> `Mission1.objectives`, pero la fase de persecución/ingreso aparece comentada como "Misión 2"
> (`MISSION2_POLICE_SPAWN`, cómic `mission2Intro` = `IntroPOW12..15`). Esa "Misión 2 interna" es
> mecánica, no una misión nueva. La **Misión 2 de campaña** (la del rumor y la mochila) sería la
> siguiente; decidir si se numera 2 o 3 antes de tocar `MissionCatalog`.

---

## 5. Archivos de referencia / Reference files

- `domain/models/campaign/MissionCatalog.kt` — agregador público de objetivos/constantes.
- `domain/models/campaign/mission1/Mission1.kt` — objetivos y constantes de la Misión 1.
- `domain/models/campaign/CampaignObjective.kt` — modelo de objetivo (`titleRes`/`descriptionRes`/coords).
- `domain/models/campaign/StoryComicCatalog.kt` — paneles de cómic (intro/outro/llegada).
- `features/main_menu/...` — `StoryModeScreen`, `StoryIntroScreen`, `StoryModeViewModel`.
- `data/repository/` — `CampaignRepository` (qué escuela) + `SaveGameRepository` (estado completo).
- Docs técnicos: `07_OTHER_FEATURES.md` (campaña), `05_ZOMBIE_MINIGAME.md` (interiores), `03_DOMAIN_MODELS.md`
  (Prankedy / fase `HIRED`), `04_MAP_EXTERIOR.md` (escolta/policía/GPS).
