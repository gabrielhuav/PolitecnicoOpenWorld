# CAMPAIGN · 01 · Misión 1 — "El origen del brote" / Mission 1 — "Outbreak origin"

> **Logline:** Una broma de Prankedy en la **ENCB** crea por accidente un compuesto infeccioso. El jugador
> baja a investigar, encuentra a Prankedy atrapado, lo **escolta** de vuelta a la **ESCOM** mientras la
> policía aparece, y se **mete a la ESCOM** para ponerse a salvo. Es la **chispa** de la simulación de
> infección: el paciente cero y las primeras señales de que algo va muy mal.

> Fuente: verificado contra `Mission1.kt`, `MissionCatalog.kt`, `StoryComicCatalog.kt`, `strings.xml`,
> `ZombieRoomCatalog`/`05`, `07_OTHER_FEATURES.md`. Los textos citados son los reales del juego.

---

## 1. Prólogo / Prologue

Se muestra en la pantalla de **MODO HISTORIA** (`story_mode`), encabezado **"PRÓLOGO"**:

> *"Cierto día, en la ENCB, Prankedy llegó haciendo de las suyas y, por una broma, terminó accidentalmente
> en mitad de un experimento que provocó la creación de un compuesto desconocido e infeccioso, capaz de
> penetrar el látex. El error pasa desapercibido… y el tiempo pasa."*

Este compuesto es el **origen del brote zombi**. La frase "el error pasa desapercibido… y el tiempo pasa"
es deliberada: la infección incuba sin que nadie lo note (coherente con la simulación).

---

## 2. La historia, en orden de juego / Story beat-by-beat

### 2.1 · Intro como cómic (paneles `IntroPOW1..8`)

Al pulsar **NUEVA PARTIDA** → cómic horizontal (`StoryComicCatalog.forSchool`). Unos sujetos quieren
agarrar a alguien para una broma y la cosa se descontrola; Prankedy acaba huyendo y escondiéndose.
Diálogos reales por panel:

1. *"Este es un buen lugar. Agarremos a este wey para la broma"*
2. *"¡Chin! Ya valió"*
3. *"A ver perro, contéstame. ¿Qué te pasa?"*
4. *"Córrele gordo. No te irás a ninguna parte."*
5. *"Inche viejo. Por aquí puedo perderlo"*
6. *(panel sin texto)*
7. *"¿No esta vacío? No importa, me tengo que esconder"*
8. *"¡Llévense al perro! A ver si muy salsa."*

En el **último panel** aparece el botón **INICIAR** → guarda partida, fija spawn + objetivo y entra al
**primer interior jugable: el Lobby de la ENCB** (ruta `encb_lobby`).

### 2.2 · Exploración de la ENCB — "Investiga qué pasó"

Cadena **LINEAL de 4 salas** (todas tipo `LOBBY`, **sin zombis**, flujo "atrapado": ninguna sale al mapa
entre medias). Fondos en `INTERIORS/ENCB/`. Banner superior **"Objetivo: Investiga qué pasó"**:

```
encb_lobby → encb_salon1 → encb_lab1 → encb_lab2
```

- Cada sala tiene **UNA puerta de avance** (waypoint X → `goToRoom(next)`).
- **`encb_lab1`** = el laboratorio del accidente (se ve el **derrame verde corrosivo** y el sombrero de
  Prankedy). Aquí está el **puzzle de la llave**: se siembran 5 llaves (`spawnLab1Keys`), recoges una al
  inventario (1 slot); la **prueba** es en `encb_lab2` desde el inventario (la correcta marca
  `lab1KeyFound=true`). La puerta del fondo de `lab2` está **gateada**: no avanzas sin la llave correcta.

### 2.3 · Outro / 2ª parte de la intro (paneles `IntroPOW9..11`)

El waypoint final de `encb_lab2` cierra la exploración → cómic `ENCB_OUTRO`. Alguien aparece en la
penumbra; es **Prankedy**, atrapado, pidiendo ayuda:

1. *"¿Quién es?..."*
2. *"Relax, relax."*
3. *"Me metí en un pedo y necesito tu ayuda. No sé salir de aquí, ayúdame."*

### 2.4 · Entrada al mundo abierto (checkpoint)

Al terminar el outro, `MainActivity` hace `setStorySpawn(MISSION1_SPAWN)` y entra al mapa global. Ese
punto es el **CHECKPOINT de la Misión 1**: si fallas la escolta, reapareces **aquí** (no en la posición
guardada). `setStorySpawn` activa `inCampaign=true` y enciende a Prankedy acompañante en el vecindario ENCB.

---

## 3. Los 4 objetivos / The four objectives

Definidos en `mission1/Mission1.kt` (`Mission1.objectives`, en orden). El **Widget de Objetivos**
(arriba-centro) muestra título + distancia; el game loop (`checkObjectiveProgress`, solo si `inCampaign`)
marca cumplido al entrar en `arriveRadiusMeters`.

| # | id | Título (ES) | Descripción | Mecánica |
|---|---|---|---|---|
| 1 | `ir_encb` | **Ve a la ENCB** | *"Dirígete a la ENCB para investigar el origen del brote."* | Llegada por coords (ENCB). |
| 2 | `escoltar_prankedy` | **Lleva a Prankedy a la puerta de la ESCOM** | *"Protege a Prankedy y escóltalo hasta la PUERTA de la ESCOM."* | Acompañante (fase `HIRED`) + línea GPS verde. |
| 3 | `ingresar_escom` | **Ingresa a la ESCOM** | *"¡Te persiguen! Entra por la puerta de la ESCOM para ponerte a salvo."* | Persecución policía; se cumple al ENTRAR (no por cercanía, `arriveRadius=0`). |
| 4 | `buscar_pistas_escom` | **Busca pistas en la ESCOM** | *"Explora la ESCOM en busca de pistas."* | Objetivo de interiores (exploración, sin destino). |

### Mecánicas clave de la escolta (objetivos 2–3)

- **Prankedy ACOMPAÑANTE (fase `HIRED`):** te **sigue** sin atacarte (`p_walk`/`p_run`). Solo en campaña y
  en el vecindario ENCB (`maybeSpawnPrankedyCompanion`). Se **sube al coche** contigo y se
  **teletransporta** contigo.
- **Línea GPS de campaña (ENCB → ESCOM):** ruta **verde vivo `#00E676`** calculada con A* sobre la red
  vial; "la ruta a seguir" hasta el "lugar seguro" (ESCOM). Desaparece a ~100 m de la ESCOM.
- **Coche obligado a pie cerca de la ESCOM:** a ≤50 m de `ESCOM_FORCEWALK` y con objetivo escolta/ingreso,
  el coche **solo da reversa** → te obliga a bajarte y entrar **a pie** por la puerta.
- **Morir en misión = MISIÓN FALLIDA:** si te matan con objetivo `ESCOLTAR_PRANKEDY`/`INGRESAR_ESCOM`, no
  hay respawn normal: WASTED breve → **"MISIÓN FALLIDA"** → **REINTENTAR** recarga el último checkpoint
  (Prankedy vuelve contigo).
- **Llegada a la ESCOM = cómic + persecución (`mission2Intro`, `IntroPOW12..15`):** al llegar con Prankedy
  a la puerta se dispara el cómic; Prankedy se despide (**"Ahí nos vemos"**) y **entra/huye**, la
  **policía lo persigue** y luego va por **ti** (salen del lado contrario a la puerta). Debes **ingresar a
  la ESCOM** para ponerte a salvo.

---

## 4. Cómo encaja en la fantasía / How it fits the simulation

La Misión 1 es la **Etapa 0** de la infección: el incidente **aislado y desapercibido**. Aún no hay
zombis sueltos en la calle — solo el paciente cero (Prankedy, contaminado por su propia broma), el
laboratorio comprometido, y la **primera presencia policial**. El jugador termina la misión **a salvo
dentro de la ESCOM**, con la orden de **buscar pistas** — lo que conecta directamente con el primer
rumor zombi (Misión 2).

---

## 5. Datos técnicos / Technical data

**Coordenadas (constantes en `Mission1.kt`, X=lon, Y=lat):**

- `ESCOM_DOOR` = (19.50490, -99.14674) — puerta norte de la ESCOM; destino de escolta/ingreso.
- `MISSION1_SPAWN` = (19.50102, -99.14421) — entrada al mapa global tras el outro = **checkpoint**.
- `ESCOM_FORCEWALK` = (19.50500, -99.14596), radio **50 m** — zona "solo reversa".
- `IR_ENCB` target = (19.498600, -99.148900) *(coords ENCB aproximadas, ajustables)*.
- `MISSION2_POLICE_SPAWN` = (19.50488, -99.14569) y `CROWD_SPAWN` = (19.50512, -99.14625) — policía/multitud
  de la fase de persecución (en `WorldMapCampaignPolice`).

**Salas (`ZombieRoomCatalog`):** `ENCB_LOBBY_ID="encb_lobby"`, `encb_salon1`, `encb_lab1`, `encb_lab2`
(`ENCB_STORY_ROOM_IDS`). Fondos `INTERIORS/ENCB/ENCB_{lobby,salon1,lab1,lab2}.webp`. Matriz de colisión de
`encb_lab1` en `assets/collision_matrices.json` → `rooms.encb_lab1` (69 filas × 70 cols).

**Cómics (`StoryComicCatalog`):** intro `IntroPOW1..8`; outro `ENCB_OUTRO_ID` = `IntroPOW9..11`; llegada
`MISSION2_INTRO_ID` = `IntroPOW12..15` (panel 15 cambia según skin vía `comicSuffix`).

**Strings (objetivos):** `obj_ir_encb_*`, `obj_escoltar_prankedy_*`, `obj_ingresar_escom_*`,
`obj_buscar_pistas_escom_*` (ES+EN en `res/values*/strings.xml`).

---

## 6. Gancho a la Misión 2 / Hook to Mission 2

La Misión 1 deja al jugador **dentro de la ESCOM** con el objetivo activo **"Busca pistas en la ESCOM"**
y la **policía persiguiéndolo**. La Misión 2 arranca justo ahí: esconderse de la policía, escuchar el
**rumor zombi** de la ENCB/Zacatenco, presenciar el **primer brote público**, y recuperar la **mochila de
Prankedy**. Ver `02_MISSION_2.md` (en diseño).
