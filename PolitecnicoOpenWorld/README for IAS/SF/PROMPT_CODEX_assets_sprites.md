# Orden de trabajo · Auditar y regenerar sprites de TITULACIÓN POR COMBATE

> **Para quién es:** para un agente con acceso al repo y capacidad de generar imágenes.
> No hay que subirle nada a mano: todo lo que necesita ya está en estas carpetas.
> Complementa `ASSETS_STREETFIGHTER_MIGRACION.md`, que explica el porqué del pipeline.

---

## 1. Lo primero: mide, no supongas

```bash
cd PolitecnicoOpenWorld
python tools/audit_sf_fighters.py
```

Sale un tablero de los 18 peleadores dedicados y dos secciones. **PROBLEMAS DUROS** = el juego
se ve mal o se cae (rects fuera de la hoja, animaciones que citan cuadros inexistentes, cuadros
100 % transparentes). **AVISOS** = arte incompleta o desperdiciada. El script devuelve 0 si no
hay duros; con `--strict` los avisos también fallan.

La auditoría ya descuenta tres cosas que **no** son defectos y que confunden si no se saben:

- La animación `stun` no está en ningún JSON **a propósito**: `SfFrameCatalog` la sintetiza de
  los cuadros `stun-1/2/3` al cargar.
- Los cuadros `proj-fly-*` y `proj-hit-*` los dibuja el renderer de proyectiles **por nombre**,
  sin pasar por una animación, así que no son huérfanos.
- Los `BONUS_POWER_*` que faltan solo cuentan hasta el `bonusPowerCount` que declara ese
  peleador en `SfModels.kt`. Los 15 peleadores sin poderes extra no deben nada.

## 2. Dónde está cada cosa

| Qué | Ruta |
|---|---|
| Hojas SIN recortar (las que se generan) | `newSFAssets/<Carpeta>/<Personaje>_NN_Grupo_Grupo.png` |
| Cuadros ya recortados (intermedios) | `newSFAssets/GEN_<char>_intermedio/<char>/` |
| Hojas finales que consume el juego | `PolitecnicoOpenWorld/app/src/main/assets/STREETFIGHTER/IMAGES/` |
| Descriptores (rects, cajas, timings) | `.../STREETFIGHTER/DATA/<char>.json` |
| Quién es quién | `shared/.../domain/models/streetfighter/SfModels.kt` (enum `SfFighterId`) |

⚠️ Los intermedios **no van dentro de `assets/`** (regla 09 §12). El driver ya los manda a
`newSFAssets/GEN_<char>_intermedio/`.

## 3. El ciclo completo, en un comando

```bash
python tools/build_sf_character.py <Carpeta> <char> "<Titulo>"
```

Encadena rebanar las 29 hojas (la **01 primero**, obligatorio) → empaquetar → WebP → auditar,
y se detiene en cuanto algo falla. Añade `--dry-run` para ver los comandos sin ejecutarlos, y
`--solo-hojas 1,10` para rehacer solo unas hojas.

**Por qué la 01 va primero:** fija la escala del personaje en `<gen>/<char>/_scale.json` y todas
las demás se calibran contra ella. Si se procesa otra antes, el personaje cambia de tamaño al
cambiar de acción.

## 4. Formato de la hoja que hay que generar

Medido en las hojas actuales, y es lo que `slice_sf_chroma_sheets.py` espera:

- **1672 × 941 px, RGB.**
- **Fondo verde croma puro `#00FF00`**, uniforme. **Ningún verde dentro de la figura** — ni ropa,
  ni ojos, ni efectos: ese color es la máscara de recorte.
- **Dos grupos de pose por hoja, separados en bandas horizontales.**
- Personaje **siempre mirando a la DERECHA**. El motor espeja solo.
- **Mismo zoom que la hoja 01** de ese personaje.
- Sin marcas de agua, sin texto extra, sin sombra pintada bajo los pies.
- Nombre: `<Personaje>_NN_Grupo_Grupo.png`, p. ej. `Prankedy_09_HeavyKick_HurtHead.png`.

El slicer normaliza después a 256×256 con los pies en (128, 224) y calibra la altura por tipo de
pose, así que la escala fina no hay que clavarla. Lo que sí importa es que el personaje sea
reconociblemente el mismo entre cuadros: misma paleta, misma ropa, mismas proporciones.

Usa las hojas de `newSFAssets/Prankedy/` como plantilla de composición y las de
`newSFAssets/<otro>/` como referencia de estilo.

## 5. Las 29 hojas y cuántos cuadros lleva cada grupo

```
01 Idle(6) + Idle Turn(4)              16 Handgun Ready(5) + Handgun Aim(5)
02 Crouch(9) + Crouch Turn(4)          17 Rifle Ready(6) + Rifle Aim(6)
03 Caminar(6) + Correr(8)              18 Idle Relaxed(6) + Talk(4)
04 Jump Start(2) + Jump Land(3)        19 Walk Backward(6) + Handgun Walk(6)
05 Jump Up(6) + Jump Forward(7)        20 Dash(4) + Backdash(4)
06 Jump Backward(7) + Light Punch(4)   21 Block Alto(4) + Block Bajo(4)
07 Medium Punch(6) + Heavy Punch(6)    22 Parry Alto(3) + Parry Bajo(3)
08 Light Kick(6) + Medium Kick(5)      23 Crouch Punch(4) + Crouch Kick(4)
09 Heavy Kick(6) + Hurt Head(14)       24 Crouch Heavy Punch(5) + Barrida(6)
10 Hurt Body(13) + Stun(3)             25 Air Punch(4) + Air Kick(4)
11 Special Light(5) + Special Med(5)   26 Patada Larga(7) + Overhead(5)
12 Special Heavy(5) + Projectile(5)    27 Agarre y Lanzamiento(8) + Taunt(6)
13 Victory(6) + KO(6)                  28 Ser Lanzado(6) + Levantarse(6)
14 Medium/Heavy Punch refinados        29 Super Art(8) + Daño Agachado(4)
15 Medium/Heavy Kick refinados
```

La tabla viva está en `SHEETS` dentro de `tools/slice_sf_chroma_sheets.py`; si cambia, manda esa.

## 6. Lo que hay pendiente hoy (medido 2026-08-03)

Sin problemas duros en los 18. Cuatro avisos, y **solo dos necesitan arte nueva**:

| Peleador | Qué pasa | Cómo se arregla |
|---|---|---|
| `prankedy` | `stun-1/2/3` son idénticos píxel a píxel: el mareo se ve congelado | regenerar **hoja 10** (Hurt Body + Stun) |
| `reygrupero` | `stun-2 == stun-3` | regenerar **hoja 10** |
| `robot` | `stun-2 == stun-3` | regenerar **hoja 10** |
| `lapresidenta` | 16 cuadros empaquetados que ninguna animación usa: `bonus-2-2..bonus-6-4`. Los poderes `bonusPower2`–`bonusPower6` animan en 3 pasos (`bonus-N-1 → bonus-N-5 → bonus-N-5`) y se saltan los cuadros 2, 3 y 4, **que ya están en la hoja**. Los poderes 1 y 7–11 sí usan los cinco | **no hace falta arte**: editar `DATA/lapresidenta.json` para que esas cinco animaciones usen los cinco cuadros, como las otras |
| `latzitzimime` | declara 5 poderes bonus pero no tiene animación para `BONUS_POWER_3` | generar ese poder o bajar su `bonusPowerCount` |

Para los tres del mareo:

```bash
python tools/build_sf_character.py Prankedy prankedy "Prankedy" --solo-hojas 10
```

(y lo equivalente para `ReyGrupero` y `ESCOMROBOT`/`robot`). Los tres cuadros de `Stun` tienen que
ser **tres poses distintas** — tambaleo con las estrellas girando, no el mismo dibujo repetido.

## 7. Cuándo está hecho

- `python tools/audit_sf_fighters.py` termina **sin problemas duros**.
- El peleador tocado **no suma avisos nuevos** respecto a la tabla de arriba.
- El atlas quedó en WebP (`atlas_to_webp.py` conserva los píxeles exactos; lo verifica por hash).
- **No se commiteó nada dentro de `assets/STREETFIGHTER/GEN/`.**
- No se tocó lógica del motor. Cambiar assets **no** debe requerir tocar Kotlin, salvo dar de alta
  un peleador nuevo en `SfFighterId`.

## 8. Presupuesto de tamaño

El AAB va en **343.5 MB** contra un tope duro de **500 MB** que el CI comprueba y que hace fallar
el release. Un peleador pesa ~2 MB ya en WebP, así que caben varios personajes nuevos — pero el
número hay que volver a mirarlo, no suponerlo: el job `playstore-closed-testing` imprime el peso
por carpeta en el resumen.
