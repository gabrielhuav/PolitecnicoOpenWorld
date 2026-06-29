# NPC Sprites — Pipeline + ESTÁNDAR (NPCs ambientales de interior)

> **Para qué:** cómo recortar las hojas (sprite sheets) de NPCs y meterlos al juego como
> **NPCs ambientales que deambulan en la ESCOM**, manteniendo el ESTÁNDAR para que **todos midan
> igual** y se animen suaves. Pensado para trabajarse en **otra PC** (otra sesión de Cowork)
> agregando más docentes/estudiantes/genéricos mientras en la principal se hace la Misión 2.

---

## 1. El ESTÁNDAR (NO CAMBIAR)

Todos los NPCs ambientales se normalizan a un **lienzo fijo** con la figura escalada a una
**altura común** y **alineada igual** (centrada, pies abajo). Esto es lo que los hace verse del
mismo tamaño y que el caminado no "salte".

| Constante | Valor | Qué es |
|---|---|---|
| `CW × CH` | **193 × 249** px | Lienzo fijo de cada frame |
| `STD_BODY_H` | **200** px | Alto al que se escala la figura de CAMINAR |
| `PAD` | **18** | Margen; los pies se anclan a `CH - nh - PAD/2` |
| `walkBodyFraction` | **0.803** | `STD_BODY_H / CH` → va TAL CUAL en la entrada `PlayerSkin` |

- Cada personaje se escala con **un solo factor** (`200 / alto_medio_de_caminar`) aplicado a
  **todas** sus animaciones (preserva proporciones de poses).
- ⚠️ El estándar es **FIJO**: los NPCs nuevos se **escalan a él**. **NO recalcular** el lienzo
  por personaje (si no, dejan de ser uniformes).

---

## 2. Estructura de carpetas (assets)

```
app/src/main/assets/SPRITES/NPC/NPCS/
  NPCSIPN/Ipn1 .. Ipn6/                 ← estudiantes IPN (exclusivos ESCOM)
      Walk/   ipnK_w_<N>.webp
      Run/    ipnK_r_<N>.webp
      Special/ipnK_s_<N>.webp           (Special = "fight"/golpe)
      Idle/   ipnK_i_1.webp             (copia del walk 1)
      spreadsheet/                      ← hojas FUENTE (PNG) de ese personaje
  Doc1/   (doc1_*)                      ← docente
  Random1/(rnd1_*)                      ← NPC genérico (interior + futuro exterior)
```

**Convención de nombres:** prefijo único por personaje (`ipn1_`, `doc1_`, `rnd1_`); archivos
`<prefijo><w|r|s|i>_<N>.webp`; subcarpetas exactas `Walk/Run/Special/Idle`. La ruta que arma
`PlayerSkin` es `${basePath}${skinFolder}<Walk|Run|Special|Idle>/${skinPrefix}<w|r|s|i>_<N>.webp`.

---

## 3. Generar las hojas (ChatGPT)

- **Un personaje por imagen.** NO una hoja con varios personajes: el modelo la redibuja
  inconsistente (ya pasó, quedó "fatal").
- **3 hojas por personaje:** *walk*, *run*, *fight* (a veces el run sale nombrado **"WRun"** — no
  importa, el recortador lo reconoce).
- Grid limpio con **hueco claro** entre frames, **fondo transparente**, personaje **mirando a la
  DERECHA**, y **ciclo de caminar que ALTERNE piernas** (izq → juntas → der → juntas). Si solo mueve
  un pie, el caminado se ve mal.
- Prompt listo: `tools/PROMPT_sprites_chatgpt.md` (usar la **Opción B – personaje completo**).
- Deja las 3 hojas en `…/NPCS/<ruta del personaje>/spreadsheet/` (cada personaje en **su** carpeta;
  si pones dos en una, el recortador puede filtrar por nombre, p. ej. `"ipn 8-"`).

---

## 4. Recortar (script reutilizable)

**`tools/slice_npc_standard.py`** hace todo el estándar (detecta figuras, escala, alinea, guarda):

```bash
pip install pillow numpy scipy
python tools/slice_npc_standard.py <sheets_dir> <name_filter> <out_dir> <prefix>
# ej (1 personaje en su carpeta):
python tools/slice_npc_standard.py \
  app/src/main/assets/SPRITES/NPC/NPCS/NPCSIPN/Ipn7/spreadsheet "" \
  app/src/main/assets/SPRITES/NPC/NPCS/NPCSIPN/Ipn7 ipn7_
```

`<name_filter>`: subcadena para elegir el personaje cuando hay varios en la carpeta (`""` = sin
filtro). Al terminar **imprime los frame counts + walkBodyFraction** listos para la entrada
`PlayerSkin`. (Verifica abriendo unos frames: pies alineados, piernas alternando.)

---

## 5. Registrar el NPC en el código (3 archivos)

**(a) `features/map_exterior/ui/components/PlayerSkin.kt`** — añadir una entrada al enum:

```kotlin
    IPN_7(                                   // o DOC_2, RND_2, etc.
        displayName = "Estudiante IPN 7",
        skinFolder  = "Ipn7/",
        skinPrefix  = "ipn7_",
        basePath    = "SPRITES/NPC/NPCS/NPCSIPN/",   // IPN; docentes/genéricos = "SPRITES/NPC/NPCS/"
        idleFrames = 1, walkFrames = W, runFrames = R, specialFrames = S,   // del script
        walkBodyFraction = 0.803f                     // SIEMPRE 0.803 (el estándar)
    ),
```

**(b) `features/interiores/zombies/viewmodel/ZombieAmbientNpcs.kt`** — agregar la skin a
`AMBIENT_SKINS` (= pool del interior de la ESCOM):

```kotlin
private val AMBIENT_SKINS: List<PlayerSkin> =
    listOf(
        PlayerSkin.IPN_1, ..., PlayerSkin.IPN_6,
        PlayerSkin.RND_1, PlayerSkin.DOC_1, PlayerSkin.IPN_7   // ← nuevo
    )
```

**(c) `features/map_exterior/ui/SkinSelectorDialog.kt`** — agregar la skin a `devOnlySkins`
para que **NO sea seleccionable** por el jugador (es NPC, no skin del jugador).

> Eso es todo: al entrar a la ESCOM, `loadRoom` llama `spawnAmbientNpcs` y aparecen NPCs random
> del pool deambulando.

---

## 6. Render e IA (ya implementado — contexto)

- **`InteriorNpcView`** (`features/interiores/core/ui/InteriorPlayerViews.kt`): dibuja un NPC por
  su `PlayerSkin`, **sin audio ni nombre**. Normaliza tamaño con `walkBodyFraction`.
  - **Ritmo ADAPTATIVO:** `delay = (760 / maxFrames).coerceIn(40,140)` → el ciclo dura ~igual con 2
    o con 25 frames (no va en cámara lenta).
  - **Fallback "caminar = correr":** si la skin tiene más frames de correr que de caminar, el
    caminado usa los de correr (para skins viejas con walk corto). Con sheets completos no aplica.
- **Spawn / deambular** (`ZombieAmbientNpcs.kt`): `spawnAmbientNpcs(room)` en `loadRoom`,
  `stepAmbientNpcs(s, room, now)` en `tickOffline`. Caminan a un punto caminable, pausan, repiten;
  respetan la matriz de colisión. **Gate:** `AMBIENT_ROOM_IDS` (lobby ESCOM) y **solo offline**.
  `AmbientNpc` = estado inmutable (x,y,skin,facing,action,target). `AMBIENT_COUNT` = nº por sala.

---

## 7. Categorías y dónde van

| Categoría | Carpeta | Dónde aparece |
|---|---|---|
| Estudiantes IPN | `NPCS/NPCSIPN/IpnN` | Interior ESCOM |
| Docente(s) | `NPCS/DocN` | Interior ESCOM |
| Genérico / random | `NPCS/RandomN` | Interior ESCOM **+ (PENDIENTE) mapa global** |

> **Fase exterior (pendiente):** el mundo abierto usa **otro sistema** (`NpcAiManager` +
> `CharacterSpriteManager`, que ENSAMBLA cuerpos con tinte+pelo). Meter modelos **premade** (sprite
> completo) como los de aquí requiere añadir una **ruta de render premade** en el exterior. No está
> hecho todavía.

---

## 8. Gotchas (IMPORTANTE en otra PC)

- **CRLF:** los `.kt`/`.json` fuente usan **CRLF**. Al editar, **conservar** el fin de línea
  (editar por bytes o asegurarse de no convertir a LF).
- **El mount de Cowork bloquea MOVER y BORRAR** (`Permission denied` en `mv`/`rm`). Para borrar:
  usar la herramienta **`allow_cowork_file_delete`** (pide permiso al usuario una vez) y luego `rm`.
  Para "mover": **copiar** (`cp -r`) + borrar el viejo con permiso.
- **Hoja de run nombrada "WRun":** el detector matchea `run` dentro de `wrun`. OK.
- **Dos personajes en una sola carpeta `spreadsheet`:** pasó (Ipn4 quedó en `Ipn3/spreadsheet`,
  Ipn5 en `Ipn6/spreadsheet`). Filtra por nombre (`"ipn 4-"`, etc.) o pon cada quién en SU carpeta.
- **Balance de llaves/paréntesis:** tras editar `PlayerSkin.kt`, verificar `(` `)` y `{` `}` por
  archivo (las entradas del enum se cierran con `),`).
- **No tocar el estándar** (193×249 / 200 / 0.803). Nuevos NPCs se escalan a él.

---

## 9. Estado actual (al escribir esto)

- **Pool del interior (`AMBIENT_SKINS`)** = `IPN_1..6` + `RND_1` + `DOC_1` = **8 modelos**, todos
  al estándar, caminando con ciclo completo.
- `PEPE_REY` y `REY_BROMAS` están **comentados** en `PlayerSkin.kt` (no seleccionables); assets
  conservados.
- **Pendiente:** más docentes/estudiantes/genéricos (mismo flujo de este doc), **Fase exterior**
  (NPCs premade en el mapa global) y la **reescritura de la Misión 2**.
